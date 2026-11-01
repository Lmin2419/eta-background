#!/usr/bin/env bash
# Cross-compile libagent_hdc.so (the in-app HDC client) for arm64-v8a with the native toolchain of the public
# OpenHarmony 7.0 SDK, and put it in entry/libs/arm64-v8a/. DevEco packs prebuilt libraries from libs/, so the
# user's DevEco does not build C++ and needs no OpenSSL.
#
#   bash scripts/fetch-ohos-sdk.sh --with-native          # once; same pinned download as the other checks
#   OHOS_SDK_NATIVE=~/.cache/ohos-sdk-26/native bash scripts/build-native-ohos.sh
#
# Sources are pinned in scripts/native-sources.json (SHA-256 verified by scripts/fetch-native.py) and patched by
# scripts/patch-hdc.py. Output: the library, its SHA-256 and BUILD-INFO.md next to it.
set -euo pipefail
PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT"
NATIVE="${OHOS_SDK_NATIVE:-$HOME/.cache/ohos-sdk-26/native}"
TOOLCHAIN="$NATIVE/build/cmake/ohos.toolchain.cmake"
LLVM="$NATIVE/llvm/bin"
[ -f "$TOOLCHAIN" ] && [ -x "$LLVM/clang" ] || { echo "OpenHarmony native SDK not found at $NATIVE. Run: bash scripts/fetch-ohos-sdk.sh --with-native" >&2; exit 2; }
command -v cmake >/dev/null || { echo "cmake is required" >&2; exit 2; }
JOBS="${JOBS:-$(nproc)}"
OUT="$PROJECT/entry/libs/arm64-v8a"
BUILD="$PROJECT/native-build/arm64-v8a"

python3 scripts/fetch-native.py
python3 scripts/patch-hdc.py

# OpenSSL: static, position independent, built with the SDK clang for aarch64-linux-ohos.
SSL="$PROJECT/vendor/openssl-openssl-3.0.16"
if [ ! -f "$SSL/libssl.a" ] || [ ! -f "$SSL/libcrypto.a" ]; then
  echo "Building OpenSSL 3.0.16 for aarch64-linux-ohos…"
  (
    cd "$SSL"
    export CC="$LLVM/clang --target=aarch64-linux-ohos --sysroot=$NATIVE/sysroot"
    export AR="$LLVM/llvm-ar" RANLIB="$LLVM/llvm-ranlib"
    ./Configure linux-aarch64 no-shared no-tests no-engine no-dso -fPIC > "$PROJECT/vendor/openssl-config.log" 2>&1
    make -j"$JOBS" build_libs > "$PROJECT/vendor/openssl-build.log" 2>&1
  ) || { echo "OpenSSL build failed; see vendor/openssl-config.log and vendor/openssl-build.log" >&2; exit 1; }
fi

echo "Building libagent_hdc.so…"
rm -rf "${BUILD:?}"; mkdir -p "$(dirname "$BUILD")"
cmake -S entry/src/main/cpp -B "$BUILD" -G "Unix Makefiles" \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DOHOS_ARCH=arm64-v8a -DOHOS_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release > "$BUILD.cmake.log" 2>&1 || { cat "$BUILD.cmake.log" >&2; exit 1; }
cmake --build "$BUILD" -j"$JOBS" > "$BUILD.build.log" 2>&1 || { tail -40 "$BUILD.build.log" >&2; exit 1; }

mkdir -p "$OUT"
"$LLVM/llvm-strip" --strip-unneeded -o "$OUT/libagent_hdc.so" "$BUILD/libagent_hdc.so"

# The library may only depend on libraries that exist on the device; the C++ runtime is linked statically.
NEEDED="$("$LLVM/llvm-readelf" -d "$OUT/libagent_hdc.so" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p' | sort | tr '\n' ' ')"
for lib in $NEEDED; do
  case "$lib" in libc.so|libm.so|libdl.so|libuv.so|libace_napi.z.so|libhilog_ndk.z.so) ;;
    *) echo "Unexpected runtime dependency: $lib" >&2; exit 1 ;;
  esac
done
"$LLVM/llvm-readelf" -h "$OUT/libagent_hdc.so" | grep -q 'AArch64' || { echo "Not an AArch64 library" >&2; exit 1; }

SHA="$(sha256sum "$OUT/libagent_hdc.so" | cut -d' ' -f1)"
echo "$SHA  libagent_hdc.so" > "$OUT/libagent_hdc.so.sha256"
SDK_VERSION="$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$NATIVE/oh-uni-package.json" | head -1)"
{
  echo "# libagent_hdc.so"
  echo
  echo "Built by \`scripts/build-native-ohos.sh\`. Do not edit by hand; rebuild with the script."
  echo
  echo "| Item | Value |"
  echo "| --- | --- |"
  echo "| SHA-256 | \`$SHA\` |"
  echo "| Architecture | arm64-v8a (aarch64-linux-ohos) |"
  echo "| Toolchain | OpenHarmony SDK native $SDK_VERSION, $("$LLVM/clang" --version | head -1) |"
  echo "| C++ runtime | static (\`OHOS_STL=c++_static\`) |"
  echo "| Runtime dependencies | $NEEDED|"
  echo "| Sources | \`scripts/native-sources.json\` (HDC OpenHarmony-6.0 \`10e867e\`, OpenSSL 3.0.16, lz4 1.10.0, securec \`61baa7a\`), patched by \`scripts/patch-hdc.py\` and \`scripts/no-usb.patch\` |"
  echo "| Exports | \`start(privateDirectory)\`, \`command(command, target, timeoutMs)\`, \`cancel()\`; typings in \`entry/src/main/cpp/types/libagent_hdc\` |"
  echo
  echo "Licenses: HDC Apache-2.0; securec MulanPSL-2.0; OpenSSL Apache-2.0; lz4 BSD-2-Clause. See THIRD_PARTY_NOTICES.md."
} > "$OUT/BUILD-INFO.md"
echo "Done: $OUT/libagent_hdc.so ($SHA)"
echo "Runtime dependencies: $NEEDED"
