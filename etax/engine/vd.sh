#!/system/bin/sh
# Authenticated, identity-checked lifecycle for ETA's resident engine only.
set -eu
D=/data/local/tmp/eta
PORT=8733
DEX=$D/vdrive.dex
LOG=$D/vdrive.log
umask 077
mkdir -p "$D"
chmod 700 "$D"
if [ "${ETA_ENGINE_LOCKED:-0}" != 1 ]; then
  : >> "$D/engine.lock"
  (
    flock -n 0 || { echo 'ETA_ENGINE_ERROR=DEPLOY_BUSY'; exit 75; }
    export ETA_ENGINE_LOCKED=1
    sh "$0" "$@"
  ) < "$D/engine.lock"
  exit $?
fi

# During upgrade this script and dex live in the incoming transaction directory,
# so even an e34 installation can use the new, Binder-free bind probe.
CONTROL_DEX=$(dirname "$0")/vdrive.dex
wait_port_free() {
  [ -s "$CONTROL_DEX" ] || { echo 'ETA_ENGINE_ERROR=CONTROL_PAYLOAD_MISSING'; return 1; }
  CLASSPATH="$CONTROL_DEX" /system/bin/app_process /system/bin EngineControl await-port "$PORT" 10000
}

is_engine_pid() {
  directory=/proc/$1
  [ -r "$directory/cmdline" ] || return 1
  # comm is a mutable thread name (and may be truncated). Check UID/argv instead;
  # shell builtins keep scanning non-root processes cheap.
  owner=''
  [ -r "$directory/status" ] || return 1
  while read -r field real effective rest; do
    if [ "$field" = 'Uid:' ]; then owner=$real:$effective; break; fi
  done < "$directory/status"
  [ "$owner" = '0:0' ] || return 1
  command=$(tr '\000' '\n' < "$directory/cmdline" 2>/dev/null) || return 1
  # Do not mistake a shell/diagnostic command mentioning the engine for the engine.
  first=$(printf '%s\n' "$command" | head -1)
  case "${first##*/}" in
    eta-vdrive-8733) return 0 ;;
    VDrive|app_process|app_process32|app_process64) ;;
    *) return 1 ;;
  esac
  printf '%s\n' "$command" | grep -qx 'VDrive' || return 1
  if printf '%s\n' "$command" | grep -qx -- "--tcp=$PORT"; then return 0; fi
  # Some app_process versions overwrite argv. Attribute the legacy process by the
  # actual listening socket, never by the class name alone (test engines may coexist).
  # Android's Java loopback socket can appear as IPv4-mapped IPv6 in tcp6.
  # There may also be multiple listening sockets; match each owned inode.
  port_hex=$(printf '%04X' "$PORT")
  inodes=$(
    for table in /proc/net/tcp /proc/net/tcp6; do
      [ ! -r "$table" ] || awk -v port=":$port_hex" '$2 ~ (port "$") && $4 == "0A" {print $10}' "$table"
    done
  )
  [ -n "$inodes" ] || return 1
  for descriptor in "$directory"/fd/*; do
    socket=$(readlink "$descriptor" 2>/dev/null) || continue
    for inode in $inodes; do
      [ "$socket" = "socket:[$inode]" ] && return 0
    done
  done
  return 1
}

engine_pids() {
  for directory in /proc/[0-9]*; do
    process=${directory##*/}
    if is_engine_pid "$process"; then printf '%s\n' "$process"; fi
  done
}

identity() {
  # Field 22, after the possibly-space-containing comm field.
  sed 's/.*) //' "/proc/$1/stat" 2>/dev/null | awk '{print $20}'
}

rpc() {
  token=$(cat "$D/engine.token" 2>/dev/null || true)
  printf '{"cmd":"%s","auth":"%s"}\n' "$1" "$token" | nc -w 2 127.0.0.1 "$PORT" 2>/dev/null | head -1
}

stop_engine() {
  records=''
  for process in $(engine_pids); do
    started=$(identity "$process")
    [ -n "$started" ] && records="$records $process:$started"
  done
  # No recognized PID is not evidence that the listening port is available.
  [ -n "$records" ] || { wait_port_free; return $?; }
  rpc quit >/dev/null || true
  for record in $records; do
    process=${record%:*}; started=${record#*:}
    count=0
    while [ "$(identity "$process")" = "$started" ] && [ "$count" -lt 15 ]; do
      sleep 0.2
      count=$((count + 1))
    done
    if [ "$(identity "$process")" = "$started" ] && is_engine_pid "$process"; then
      kill -9 "$process" 2>/dev/null || true
    fi
    count=0
    while [ "$(identity "$process")" = "$started" ] && [ "$count" -lt 15 ]; do
      sleep 0.2
      count=$((count + 1))
    done
    [ "$(identity "$process")" != "$started" ] || { echo 'ETA_ENGINE_ERROR=STOP_FAILED'; return 1; }
  done
  [ -z "$(engine_pids)" ] || { echo 'ETA_ENGINE_ERROR=STOP_FAILED'; return 1; }
  wait_port_free
}

healthy_status() {
  response=$(rpc status) || return 1
  printf '%s' "$response" | grep -q '"healthy":true' || return 1
  printf '%s' "$response" | grep -q '"ok":true' || return 1
  wanted=$(cat "$D/vdrive.version")
  got=$(printf '%s' "$response" | sed -n 's/.*"build":"\([^"]*\)".*/\1/p')
  [ "$got" = "$wanted" ] || return 1
  got_hash=$(printf '%s' "$response" | sed -n 's/.*"dexSha256":"\([^" ]*\)".*/\1/p')
  [ "$got_hash" = "$(sha256sum "$DEX" | cut -d' ' -f1)" ] || return 1
  process=$(printf '%s' "$response" | sed -n 's/.*"pid":\([0-9]*\).*/\1/p')
  [ -n "$process" ] || return 1
  is_engine_pid "$process" || return 1
  printf '%s\n' "$response"
}

case "${1:-}" in
  stop) stop_engine; echo 'vdrive stopped'; exit 0 ;;
  status) healthy_status; exit $? ;;
esac

[ -s "$DEX" ] && [ -s "$D/vdrive.version" ] || { echo 'ETA_ENGINE_ERROR=CONTROL_PAYLOAD_MISSING'; exit 1; }
[ -f "$D/engine.token" ] || { echo 'ETA_ENGINE_ERROR=TOKEN_MISSING'; exit 78; }
if [ "$(wc -c < "$D/engine.token")" -gt 65 ] ||
   ! LC_ALL=C grep -aEq '^[0-9a-f]{64}$' "$D/engine.token"; then
  echo 'ETA_ENGINE_ERROR=TOKEN_INVALID'; exit 78
fi
stop_engine
for number in 2 1; do
  [ ! -f "$LOG.$number" ] || mv -f "$LOG.$number" "$LOG.$((number + 1))"
done
[ ! -f "$LOG" ] || mv -f "$LOG" "$LOG.1"
# The resident engine must inherit neither the lock's stdin nor its ownership flag.
(
  unset ETA_ENGINE_LOCKED
  exec setsid nohup env CLASSPATH="$DEX" /system/bin/app_process /system/bin --nice-name=eta-vdrive-8733 VDrive --tcp="$PORT" --no-mcp </dev/null >"$LOG" 2>&1
) &
started_pid=$!
started_at=$(identity "$started_pid")
count=0
startup_deadline=$(( $(cut -d. -f1 /proc/uptime) + 20 ))
while [ "$count" -lt 35 ] && [ "$(cut -d. -f1 /proc/uptime)" -lt "$startup_deadline" ]; do
  if response=$(healthy_status); then
    got=$(printf '%s' "$response" | sed -n 's/.*"pid":\([0-9]*\).*/\1/p')
    if [ "$got" = "$started_pid" ] && [ "$(identity "$started_pid")" = "$started_at" ]; then
      printf '%s %s\n' "$started_pid" "$started_at" > "$D/vdrive.pid"
      echo "vdrive ready pid=$got build=$(cat "$D/vdrive.version")"
      exit 0
    fi
  fi
  [ "$(identity "$started_pid")" = "$started_at" ] || break
  sleep 0.2
  count=$((count + 1))
done
grep '^ETA_ENGINE_ERROR=' "$LOG" 2>/dev/null || true
stop_engine || true
echo 'ETA_ENGINE_ERROR=START_FAILED'
exit 1
