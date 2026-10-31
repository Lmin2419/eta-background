#!/usr/bin/env bash
# Headless UI tests on the OpenHarmony Linux Previewer.
#   npm run test:ui                      # all scenarios in tests/ui/
#   npm run test:ui -- tests/ui/02-drawer.json
# Report (screenshots, inspector trees, previewer log, summary.md): dist/ui-report/
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
PROJECT="$(cd "$HERE/../.." && pwd)"
export OHOS_SDK_ETS="${OHOS_SDK_ETS:-$HOME/.cache/ohos-sdk-26/ets}"
RUNTIME="$OHOS_SDK_ETS/../previewer-runtime"
OUT="${UI_TEST_OUT:-$PROJECT/dist/ui-preview}"
REPORT="${UI_TEST_REPORT:-$PROJECT/dist/ui-report}"
[ -f "$RUNTIME/env.sh" ] || bash "$HERE/setup.sh"
# shellcheck disable=SC1091
source "$RUNTIME/env.sh"
python3 -c "import websockets" 2>/dev/null || { echo "Python package 'websockets' is required: pip install -r scripts/ui-preview/requirements.txt" >&2; exit 2; }

echo "Building preview…"
rm -rf "$REPORT"; mkdir -p "$REPORT"
node "$HERE/build-preview.cjs" "$OUT" pages/FoundationPreview | tee "$REPORT/build.txt"

# The Ark runtime only accepts app paths under the sandbox prefix; the directory name must end in .abc.
ABC_DIR=/data/storage/el1/bundle/entry/ets/modules.abc
if ! mkdir -p "$ABC_DIR" 2>/dev/null || [ ! -w "$ABC_DIR" ]; then
  sudo mkdir -p "$ABC_DIR" && sudo chown -R "$(id -u):$(id -g)" /data/storage || { echo "Cannot write $ABC_DIR (needs root or sudo)." >&2; exit 2; }
fi
rm -f "${ABC_DIR:?}"/*; cp "$OUT/ets/modules.abc" "$ABC_DIR/"
export PREVIEW_ABC_DIR="$ABC_DIR"

if [ -z "${DISPLAY:-}" ]; then
  command -v Xvfb >/dev/null || { echo "Xvfb is required (apt-get install xvfb)." >&2; exit 2; }
  Xvfb :97 -screen 0 1280x2000x24 >/dev/null 2>&1 & XVFB=$!
  trap 'kill $XVFB 2>/dev/null || true' EXIT
  export DISPLAY=:97; sleep 1
fi

if [ "$#" -gt 0 ]; then scenarios=("$@"); else scenarios=("$PROJECT"/tests/ui/*.json); fi
failed=0
{ echo "# UI preview test report"; echo; echo "| Scenario | Result |"; echo "| --- | --- |"; } > "$REPORT/summary.md"
for scenario in "${scenarios[@]}"; do
  name="$(basename "$scenario" .json)"
  echo "== $name"
  if timeout 300 python3 "$HERE/uitest.py" "$OUT" "$scenario" "$REPORT"; then echo "| $name | pass |" >> "$REPORT/summary.md"
  else echo "| $name | **FAIL** |" >> "$REPORT/summary.md"; failed=1; fi
done
echo; cat "$REPORT/summary.md"
echo "Screenshots and logs: $REPORT"
exit $failed
