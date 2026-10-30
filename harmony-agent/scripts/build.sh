#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"
: "${HARMONY_CLT_HOME:?Set HARMONY_CLT_HOME to your HarmonyOS Command Line Tools directory}"
export DEVECO_SDK_HOME="${DEVECO_SDK_HOME:-$HARMONY_CLT_HOME/sdk}"
export PATH="$HARMONY_CLT_HOME/tool/node/bin:$HARMONY_CLT_HOME/bin:$PATH"
# 0.9.x 的正式入口不加载 HDC 原生库，entry 不再编译 C++（见 docs/ROADMAP.md T01）。
# M2（T10）接回手机操作时，原生库以预编译 entry/libs/arm64-v8a/libagent_hdc.so 的方式入库；
# 重新生成该库时运行 scripts/prepare-native.sh 并单独用 CMake 构建 entry/src/main/cpp。
ohpm install
hvigorw --mode module -p product=default -p module=entry@default assembleHap --no-daemon
mkdir -p dist
RELEASE_VERSION="$(node -p "JSON.parse(require('fs').readFileSync('package.json','utf8')).version")"
cp entry/build/default/outputs/default/entry-default-unsigned.hap "dist/harmony-agent-${RELEASE_VERSION}-unsigned.hap"
python3 scripts/verify-hap.py "dist/harmony-agent-${RELEASE_VERSION}-unsigned.hap"
