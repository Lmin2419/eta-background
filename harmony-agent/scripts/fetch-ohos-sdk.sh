#!/usr/bin/env bash
# Download the ArkTS (ets) and toolchains components of the public OpenHarmony 7.0 Release SDK, API 26.
# No Huawei account is needed. The archive is verified against a pinned SHA-256 before use.
#
#   bash scripts/fetch-ohos-sdk.sh [target-dir]      default: ~/.cache/ohos-sdk-26
#   export OHOS_SDK_ETS=<target-dir>/ets && npm run check:ets && npm run check:res
#
# Only the two Linux zips (ets for the ArkTS checker, toolchains for restool) are extracted from the
# 3.4 GB archive while it streams, so disk use stays small.
set -euo pipefail

URL="https://mirrors.huaweicloud.com/openharmony/os/7.0-Release/ohos-sdk-windows_linux-public.tar.gz"
SHA256="130a438cbcddd46a2a5b09ee9925339cddf4cca99223329b860f96a5c72f338f"
ETS_MEMBER="ohos-sdk/linux/ets-linux-x64-26.0.0.38-Beta.zip"
TOOLS_MEMBER="ohos-sdk/linux/toolchains-linux-x64-26.0.0.38-Beta.zip"
TARGET="${1:-${HOME}/.cache/ohos-sdk-26}"

if [ -f "$TARGET/ets/oh-uni-package.json" ] && [ -x "$TARGET/toolchains/restool" ]; then
  echo "SDK already present: $TARGET/ets"
  exit 0
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
echo "Downloading $URL (3.4 GB, only the ArkTS and toolchains parts are kept)…"
curl -fsSL --retry 3 --retry-delay 5 "$URL" \
  | tee >(sha256sum | cut -d' ' -f1 > "$WORK/actual.sha256") \
  | tar xz -C "$WORK" "$ETS_MEMBER" "$TOOLS_MEMBER"
# Wait for the checksum process substitution to flush.
for _ in $(seq 1 60); do [ -s "$WORK/actual.sha256" ] && break; sleep 1; done
ACTUAL="$(cat "$WORK/actual.sha256")"
if [ "$ACTUAL" != "$SHA256" ]; then
  echo "SHA-256 mismatch: expected $SHA256, got $ACTUAL" >&2
  exit 1
fi
mkdir -p "$TARGET"
unzip -q -o "$WORK/$ETS_MEMBER" -d "$TARGET"
unzip -q -o "$WORK/$TOOLS_MEMBER" -d "$TARGET"
echo "SDK ready: $TARGET/ets (API $(node -p "require('$TARGET/ets/oh-uni-package.json').apiVersion"))"
