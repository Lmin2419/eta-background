#!/usr/bin/env bash
# The self-check before a PR is handed over: runs all five checks and writes a summary to paste into
# the PR ("自动验证" section).
#
#   npm run verify                 # all five checks
#   npm run verify -- --no-ui      # skip the UI tests when the previewer cannot run here (CI still runs them)
#
# Output: dist/verify/summary.md, plus one log per check in dist/verify/. Exit code 1 when any check fails.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
PROJECT="$(cd "$HERE/.." && pwd)"
cd "$PROJECT"
export OHOS_SDK_ETS="${OHOS_SDK_ETS:-$HOME/.cache/ohos-sdk-26/ets}"
OUT="$PROJECT/dist/verify"
WITH_UI=1
[ "${1:-}" = "--no-ui" ] && WITH_UI=0
rm -rf "$OUT"; mkdir -p "$OUT"

rows=()
failed=0
check() {
  local name="$1" script="$2" log="$OUT/$2.log" detail
  echo "== npm run $script"
  if npm run --silent "$script" > "$log" 2>&1; then status="通过"; else status="**失败**"; failed=1; fi
  case "$script" in
    test) detail="$(awk '/^# tests /{t=$3} /^# pass /{p=$3} /^# fail /{f=$3} /^# skipped /{s=$3} END{printf "%s 项，通过 %s，失败 %s，跳过 %s", t, p, f, s}' "$log")" ;;
    check:ets) detail="$(grep -m1 '^ArkTS check' "$log" | sed 's/^ArkTS check with //')" ;;
    check:res) detail="$(grep -m1 '^Resource check' "$log" | sed 's/^Resource check with //')" ;;
    test:ui) detail="$(awk '/\| pass/{p++} /\*\*FAIL\*\*/{f++} END{printf "%d 个场景通过，%d 个失败", p, f}' "$PROJECT/dist/ui-report/summary.md" 2>/dev/null)" ;;
    *) detail="$(grep -c 'error TS' "$log") 个类型错误" ;;
  esac
  echo "   $status  $detail"
  [ "$status" = "通过" ] || tail -n 30 "$log" | sed 's/^/   | /'
  rows+=("| \`npm run $script\` | $status | $detail |")
}

check "类型检查" check
check "单元测试" test
check "ArkTS 检查" check:ets
check "资源检查" check:res
if [ "$WITH_UI" = 1 ]; then check "界面测试" test:ui
else rows+=("| \`npm run test:ui\` | 本地未运行 | 以 PR 上 CI 的 \`Harmony / UI preview tests\` 结果为准 |"); fi

dirty=""
[ -n "$(git status --porcelain -- . 2>/dev/null)" ] && dirty="（工作区有未提交的改动：提交后结果可能不同，请提交后重新运行）"
{
  echo "### 自动验证（\`npm run verify\`）"
  echo
  echo "提交 \`$(git rev-parse --short HEAD 2>/dev/null)\` · $(date '+%Y-%m-%d %H:%M')$dirty"
  echo
  echo "| 检查 | 结果 | 摘要 |"
  echo "| --- | --- | --- |"
  printf '%s\n' "${rows[@]}"
  echo
  echo "HAP：未生成（需要 DevEco，最终以用户编译为准）。"
  if [ "$WITH_UI" = 1 ] && [ -f "$PROJECT/dist/ui-report/summary.md" ]; then
    echo
    echo "<details><summary>界面测试明细</summary>"
    echo
    sed '1,2d' "$PROJECT/dist/ui-report/summary.md"
    echo
    echo "</details>"
  fi
} > "$OUT/summary.md"
echo
cat "$OUT/summary.md"
echo
echo "粘贴到 PR 的内容：$OUT/summary.md；每项日志：$OUT/*.log"
exit $failed
