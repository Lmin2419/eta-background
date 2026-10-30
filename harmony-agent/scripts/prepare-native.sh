#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"
: "${HARMONY_CLT_HOME:?Set HARMONY_CLT_HOME to your HarmonyOS Command Line Tools directory}"
SDK_NATIVE="${DEVECO_SDK_HOME:-$HARMONY_CLT_HOME/sdk}/default/openharmony/native"
test -x "$SDK_NATIVE/llvm/bin/aarch64-unknown-linux-ohos-clang" || { echo 'The Linux ARM64 HarmonyOS cross compiler is missing.' >&2; exit 1; }
python3 scripts/fetch-native.py
python3 scripts/patch-hdc.py
SSL_DIR="$PROJECT_ROOT/vendor/openssl-openssl-3.0.16"
if [ ! -f "$SSL_DIR/libssl.a" ] || [ ! -f "$SSL_DIR/libcrypto.a" ]; then
  (
    cd "$SSL_DIR"
    export CC="$SDK_NATIVE/llvm/bin/aarch64-unknown-linux-ohos-clang"
    export AR="$SDK_NATIVE/llvm/bin/llvm-ar"
    export RANLIB="$SDK_NATIVE/llvm/bin/llvm-ranlib"
    ./Configure linux-aarch64 no-shared no-tests -fPIC > "$PROJECT_ROOT/vendor/openssl-config.log" 2>&1
    make -j"${JOBS:-4}" build_libs > "$PROJECT_ROOT/vendor/openssl-build.log" 2>&1
  )
fi
echo 'Native sources and OpenSSL are ready.'
