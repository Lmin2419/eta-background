#!/system/bin/sh
# Install one engine dex: stop -> backup -> swap -> start -> verify build.
#
#   sh install_vdrive.sh <dex> [expected-build]
#
# Fails (and restores the previous dex) when the engine does not come back with the
# expected build stamp. No automatic "probably fine" path.
set -u

# These scripts drive the phone (app_process / pm / nc). Running them in the
# Linux guest silently half-applies a deployment, so refuse up front.
if [ ! -x /system/bin/app_process ]; then
  echo "this script must run in the Android environment, not the Linux guest"; exit 1
fi

D=/data/local/tmp/eta
SRC=${1:-$D/vdrive.dex.new}
WANT=${2:-}
LIVE=$D/vdrive.dex
PORT=8733

[ -f "$SRC" ] || { echo "missing dex: $SRC"; exit 1; }
[ -f "$LIVE" ] || { echo "missing live dex: $LIVE"; exit 1; }

OLD=$(sha256sum "$LIVE" | cut -c1-16)
NEW=$(sha256sum "$SRC" | cut -c1-16)
echo "live=$OLD ($(stat -c%s $LIVE)B)  new=$NEW ($(stat -c%s $SRC)B)"
if [ "$OLD" = "$NEW" ]; then echo "identical, nothing to do"; exit 0; fi

BK=$D/vdrive.dex.bak-$OLD
[ -f "$BK" ] || cp -f "$LIVE" "$BK"
echo "backup: $BK"

sh "$D/vd.sh" stop || { echo "stop failed"; exit 1; }

cp -f "$SRC" "$D/.vdrive.dex.new"
mv -f "$D/.vdrive.dex.new" "$LIVE"
echo "installed $(sha256sum "$LIVE" | cut -c1-16)"
[ -n "$WANT" ] && printf '%s' "$WANT" > "$D/vdrive.version"

if ! sh "$D/vd.sh"; then
  echo "!!! engine did not start, restoring $BK"
  cp -f "$BK" "$LIVE"
  sh "$D/vd.sh"
  exit 1
fi

GOT=$(printf '{"cmd":"status"}\n' | nc -w 2 127.0.0.1 $PORT \
      | sed -n 's/.*"build":"\([^"]*\)".*/\1/p')
echo "running build=$GOT"
if [ -n "$WANT" ] && [ "$GOT" != "$WANT" ]; then
  echo "!!! expected build $WANT, got $GOT -> rolling back"
  sh "$D/vd.sh" stop
  cp -f "$BK" "$LIVE"
  sh "$D/vd.sh"
  exit 1
fi
echo "engine install ok (build=$GOT)"
echo "--- pairing ---"
sh "$D/check_pair.sh" || echo "!!! 引擎与已安装 APK 不配套（如果是回滚，忽略这一行）"
