#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"
: "${HARMONY_CLT_HOME:?Set HARMONY_CLT_HOME to your HarmonyOS Command Line Tools directory}"
export DEVECO_SDK_HOME="${DEVECO_SDK_HOME:-$HARMONY_CLT_HOME/sdk}"
export PATH="$HARMONY_CLT_HOME/tool/node/bin:$HARMONY_CLT_HOME/bin:$PATH"
bash scripts/prepare-native.sh
ohpm install
hvigorw --mode module -p product=default -p module=entry@default assembleHap --no-daemon
mkdir -p dist
RELEASE_VERSION="$(node -p "JSON.parse(require('fs').readFileSync('package.json','utf8')).version")"
cp entry/build/default/outputs/default/entry-default-unsigned.hap "dist/harmony-agent-${RELEASE_VERSION}-unsigned.hap"
python3 scripts/verify-hap.py "dist/harmony-agent-${RELEASE_VERSION}-unsigned.hap"
