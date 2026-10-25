#!/system/bin/sh
# Restart the resident vdrive engine (uid 0, app_process) on 127.0.0.1:8733.
#
# Only the instance that owns 8733 is touched: a throw-away test engine on 8736 must
# survive a live restart. There is exactly one dex this script runs ($D/vdrive.dex);
# installing a new engine is an explicit step (install_vdrive.sh).
set -u

D=/data/local/tmp/eta
LOG=$D/vdrive.log
PREV=$D/vdrive.prev.log
DEX=$D/vdrive.dex
PORT=8733

[ -f "$DEX" ] || { echo "missing engine dex: $DEX"; exit 1; }

engine_pids() {
  ps -A | grep -F "app_process /system/bin VDrive" | grep -F "tcp=$PORT" \
    | grep -v grep | awk '{print $1}'
}

graceful_stop() {
  [ -z "$(engine_pids)" ] && return 0
  printf '{"cmd":"quit"}\n' | nc -w 3 127.0.0.1 $PORT >/dev/null 2>&1
  i=0
  while [ $i -lt 25 ]; do
    [ -z "$(engine_pids)" ] && return 0
    sleep 0.2
    i=$((i + 1))
  done
  return 0
}

case "${1:-}" in
  stop)
    [ -f "$LOG" ] && mv -f "$LOG" "$PREV"
    graceful_stop
    for p in $(engine_pids); do kill -9 "$p" 2>/dev/null; done
    echo "vdrive stopped"
    exit 0
    ;;
  status)
    pids=$(engine_pids)
    if [ -z "$pids" ]; then echo "not running"; exit 1; fi
    out=$(printf '{"cmd":"status"}\n' | nc -w 2 127.0.0.1 $PORT | head -1)
    if [ -z "$out" ]; then echo "process $pids but no answer on $PORT"; exit 1; fi
    echo "$out"
    exit 0
    ;;
esac

[ -f "$LOG" ] && mv -f "$LOG" "$PREV"

graceful_stop
for p in $(engine_pids); do kill -9 "$p" 2>/dev/null; done
sleep 0.4

start_once() {
  setsid nohup env CLASSPATH=$DEX /system/bin/app_process /system/bin VDrive --tcp=$PORT > "$LOG" 2>&1 &
  i=0
  while [ $i -lt 50 ]; do
    if printf '{"cmd":"ping"}\n' | nc -w 1 127.0.0.1 $PORT 2>/dev/null | grep -q '"pong":true'; then
      return 0
    fi
    sleep 0.2
    i=$((i + 1))
  done
  return 1
}

attempt=1
while [ $attempt -le 3 ]; do
  if start_once; then
    stamp=$(printf '{"cmd":"status"}\n' | nc -w 2 127.0.0.1 $PORT \
            | sed -n 's/.*"build":"\([^"]*\)".*/\1/p')
    echo "vdrive up (dex=$DEX, attempt $attempt, build=${stamp:-unknown})"
    exit 0
  fi
  echo "start attempt $attempt failed:"
  tail -3 "$LOG" 2>/dev/null
  for p in $(engine_pids); do kill -9 "$p" 2>/dev/null; done
  [ $attempt -lt 3 ] && sleep 3
  attempt=$((attempt + 1))
done
echo "vdrive failed after 3 attempts:"
cat "$LOG" 2>/dev/null
exit 1
