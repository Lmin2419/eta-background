#!/system/bin/sh
# Passive, single-run capture. Requires root already held by THIS shell.
# Does not obtain root, activate hooks, press keys, change settings or reboot.
# Usage: sh capture-power-key.sh [seconds: 5..300, default 180] [new output dir]
# Logs can contain private information: keep them on the phone; redact excerpts.
# A system failure can interrupt capture; this is evidence collection, not a fix.

set -f
umask 077
duration=${1:-180}
case "$duration" in ''|*[!0-9]*) printf '%s\n' 'ERROR: duration must be 5..300 seconds'; exit 2;; esac
if [ "${#duration}" -gt 3 ]; then
    printf '%s\n' 'ERROR: duration must be 5..300 seconds'; exit 2
fi
while [ "${duration#0}" != "$duration" ]; do duration=${duration#0}; done
duration=${duration:-0}
if [ "$duration" -lt 5 ] || [ "$duration" -gt 300 ]; then
    printf '%s\n' 'ERROR: duration must be 5..300 seconds'; exit 2
fi
if [ "$(id -u)" != 0 ]; then
    printf '%s\n' 'BLOCKED_ROOT: prepare only; do not reactivate or acquire root automatically.'
    exit 3
fi
for capture_command in logcat pidof timeout tail date getprop; do
    if ! command -v "$capture_command" >/dev/null 2>&1; then
        printf 'BLOCKED_TOOL: %s\n' "$capture_command"; exit 4
    fi
done

# starttime guards cleanup against accidentally signalling a reused PID.
process_start() {
    [ -r "/proc/$1/stat" ] || return 1
    IFS= read -r process_stat < "/proc/$1/stat" || return 1
    process_fields=${process_stat##*) }
    set -- $process_fields
    [ "$#" -ge 20 ] || return 1
    case "$1" in Z|X) return 1;; esac
    shift 19
    printf '%s' "$1"
}
identity() {
    identity_pids=$(pidof "$1" 2>/dev/null)
    for identity_pid in $identity_pids; do
        identity_start=$(process_start "$identity_pid") || continue
        printf '%s:%s ' "$identity_pid" "$identity_start"
    done
}
uptime_seconds() {
    IFS=' ' read -r capture_uptime capture_idle < /proc/uptime
    printf '%s' "${capture_uptime%%.*}"
}

initial_system=$(identity system_server)
if [ -z "$initial_system" ]; then
    printf '%s\n' 'BLOCKED_SYSTEM: cannot identify system_server; nothing started.'; exit 4
fi
output=${2:-/sdcard/Download/eta-power-$(date +%Y%m%d-%H%M%S)-$$}
case "$output" in /*) ;; *) printf '%s\n' 'ERROR: output must be an absolute new directory'; exit 2;; esac
if ! mkdir "$output"; then
    printf '%s\n' 'ERROR: output must be new and writable; existing evidence is never replaced.'; exit 4
fi

record() {
    printf '%s uptime=%s %s\n' "$(date +%Y-%m-%dT%H:%M:%S%z)" "$(uptime_seconds)" "$*" >> "$output/timeline.txt"
}
snapshot_vector() {
    snapshot_phase=$1
    # Only the known framework log directories; never scan app data or all storage.
    set +f
    timeout -k 1 2 ls -1t /data/adb/lspd/log/verbose_*.log /data/adb/vector/log/verbose_*.log \
        > "$output/vector-sources.txt" 2>/dev/null
    set -f
    snapshot_count=0
    while IFS= read -r snapshot_source; do
        [ -f "$snapshot_source" ] || continue
        snapshot_count=$((snapshot_count + 1))
        printf '%s\n' "$snapshot_source" > "$output/vector-$snapshot_phase-$snapshot_count.source"
        if ! timeout -k 1 2 tail -c 131072 "$snapshot_source" > "$output/vector-$snapshot_phase-$snapshot_count.log" 2>/dev/null; then
            record "VECTOR_SNAPSHOT_INCOMPLETE phase=$snapshot_phase slot=$snapshot_count"
        fi
        [ "$snapshot_count" -ge 2 ] && break
    done < "$output/vector-sources.txt"
    [ "$snapshot_count" -gt 0 ] || record "VECTOR_LOG_UNAVAILABLE phase=$snapshot_phase"
}

collector_pid=
collector_start=
stop_reason=EXIT_BEFORE_READY
collector_is_owned() {
    [ -n "$collector_pid" ] && [ -n "$collector_start" ] &&
        [ "$(process_start "$collector_pid")" = "$collector_start" ]
}
cleanup() {
    trap - 0 INT TERM HUP
    # Only the exact logcat child started below, never system_server/vectord/apps.
    if collector_is_owned; then
        kill -TERM "$collector_pid" 2>/dev/null
        sleep 1
        if collector_is_owned; then kill -KILL "$collector_pid" 2>/dev/null; fi
    fi
    record "CAPTURE_STOP reason=$stop_reason"
    printf 'CAPTURE_STOP reason=%s output=%s\n' "$stop_reason" "$output"
}
trap cleanup 0
trap 'stop_reason=USER_INTERRUPTED; exit 130' INT TERM HUP

record "CAPTURE_START seconds=$duration system_server=$initial_system"
for capture_property in ro.product.model ro.build.version.release ro.build.version.sdk ro.build.fingerprint; do
    printf '%s=%s\n' "$capture_property" "$(getprop "$capture_property")" >> "$output/device.txt"
done
snapshot_vector start
initial_vector=$(identity vectord)
record "PROCESS_BASELINE system_server=$initial_system vectord=$initial_vector"

# 8 files of 512 KiB, plus a bounded stderr file. Never clear/resize logcat buffers.
(
    ulimit -f 4096 || exit 5
    exec logcat -b main -b system -b crash -b events -v threadtime -T 100 \
        -f "$output/system.log" -r 512 -n 7
) 2> "$output/collector.err" &
collector_pid=$!
collector_start=$(process_start "$collector_pid")
sleep 1
if ! collector_is_owned || ! kill -0 "$collector_pid" 2>/dev/null || [ ! -s "$output/system.log" ]; then
    stop_reason=LOGCAT_NOT_READY
    exit 5
fi

started=$(uptime_seconds)
deadline=$((started + duration))
next_snapshot=$((started + 10))
previous_vector=$initial_vector
stop_reason=DURATION_REACHED
record CAPTURE_READY
printf 'CAPTURE_READY seconds=%s output=%s\n' "$duration" "$output"
printf '%s\n' 'No activation or key action has been performed. Wait for the user; do not automate a reproduction.'

while [ "$(uptime_seconds)" -lt "$deadline" ]; do
    current_system=$(identity system_server)
    current_vector=$(identity vectord)
    if [ "$current_vector" != "$previous_vector" ]; then
        record "VECTOR_PROCESS_CHANGE previous=$previous_vector current=$current_vector"
        previous_vector=$current_vector
        # 1-second sampling cannot establish order for changes in the same interval.
    fi
    if [ "$current_system" != "$initial_system" ]; then
        record "SYSTEM_SERVER_CHANGE previous=$initial_system current=$current_system"
        stop_reason=SYSTEM_SERVER_CHANGED_OR_GONE
        break
    fi
    if ! collector_is_owned || ! kill -0 "$collector_pid" 2>/dev/null; then
        stop_reason=LOGCAT_EXITED
        break
    fi
    now=$(uptime_seconds)
    if [ "$now" -ge "$next_snapshot" ]; then
        snapshot_vector current
        next_snapshot=$((now + 10))
    fi
    sleep 1
done
snapshot_vector end
# Missing logs, a lost collector or process changes do not by themselves identify a cause.
exit 0
