#!/usr/bin/env bash
# Prepare the OpenHarmony Linux Previewer for headless UI tests. Nothing in the SDK is modified:
# everything goes into <sdk>/previewer-runtime.
#
#   bash scripts/fetch-ohos-sdk.sh --with-previewer
#   bash scripts/ui-preview/setup.sh            # uses $OHOS_SDK_ETS/.. or ~/.cache/ohos-sdk-26
#
# Linux compatibility steps, each verified against SDK 26.0.0.38:
# - libhilog.so / libshared_libz.so are not shipped under these names; alias them.
# - Components are dlopen'ed as lib*.z.so; alias every shipped lib*.so.
# - libcurl_shared.so needs OpenHiTLS/brotli/c-ares libraries that are not shipped; use the system
#   libcurl (same API). The previewer has RPATH=$ORIGIN, so this happens in an overlay bin directory.
# - Three libraries export separate AppExecFwk::EventRunner copies; an LD_AUDIT library makes each
#   bind its own (generated from the ELF symbol tables, no fixed offsets).
# - The previewer passes page paths without the module prefix; a small LD_PRELOAD shim adds it.
# - The overlay keeps the SDK layout (bin/ next to resources/), which the theme manager relies on.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SDK="$(cd "${OHOS_SDK_ETS:-$HOME/.cache/ohos-sdk-26/ets}/.." && pwd)"
BIN="$SDK/previewer/common/bin"
RUNTIME="$SDK/previewer-runtime"
[ -x "$BIN/Previewer" ] || { echo "Previewer not found in $SDK. Run: bash scripts/fetch-ohos-sdk.sh --with-previewer" >&2; exit 2; }
LIBCURL="$(ls /usr/lib/x86_64-linux-gnu/libcurl.so.4 /usr/lib64/libcurl.so.4 2>/dev/null | head -1 || true)"
[ -n "$LIBCURL" ] || { echo "System libcurl.so.4 not found (install libcurl4)." >&2; exit 2; }
for tool in gcc g++ python3 readelf; do command -v "$tool" >/dev/null || { echo "$tool is required." >&2; exit 2; }; done

rm -rf "$RUNTIME"; mkdir -p "$RUNTIME/bin" "$RUNTIME/libs"
# Overlay bin: real copy of the executable (so $ORIGIN is the overlay), symlinks for the rest.
cp "$BIN/Previewer" "$RUNTIME/bin/Previewer"
# System resources (theme colors, symbols) are found next to bin/; without them the theme manager crashes.
ln -s "$SDK/previewer/common/resources" "$RUNTIME/resources"
for entry in "$BIN"/*; do
  name="$(basename "$entry")"
  [ "$name" = Previewer ] && continue
  if [ "$name" = libcurl_shared.so ]; then ln -s "$LIBCURL" "$RUNTIME/bin/$name"; else ln -s "$entry" "$RUNTIME/bin/$name"; fi
done
ln -s "$BIN/libhilog_linux.so" "$RUNTIME/libs/libhilog.so"
ln -s "$(readlink -f /lib/x86_64-linux-gnu/libz.so.1 2>/dev/null || readlink -f /usr/lib/x86_64-linux-gnu/libz.so.1)" "$RUNTIME/libs/libshared_libz.so"
for lib in "$RUNTIME"/bin/lib*.so; do
  name="$(basename "$lib" .so)"; [ -e "$RUNTIME/libs/$name.z.so" ] || ln -s "$lib" "$RUNTIME/libs/$name.z.so"
done
python3 "$HERE/gen_audit.py" "$BIN" "$RUNTIME/audit.c" >/dev/null
gcc -shared -fPIC -O2 -o "$RUNTIME/audit.so" "$RUNTIME/audit.c"
g++ -shared -fPIC -O2 -o "$RUNTIME/preview_paths.so" "$HERE/preview_paths.cpp" -ldl
cat > "$RUNTIME/env.sh" <<ENV
export PREVIEWER_BIN="$RUNTIME/bin"
export PREVIEWER_LIBS="$RUNTIME/libs"
export PREVIEWER_AUDIT="$RUNTIME/audit.so"
export PREVIEWER_SHIM="$RUNTIME/preview_paths.so"
ENV
echo "Previewer runtime ready: $RUNTIME"
