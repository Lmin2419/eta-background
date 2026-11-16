#!/usr/bin/env bash
# 电脑端（macOS / Linux）：把 dsh-bridge 插件装进 DeepSeek Harness 桌面版。
#
#   bash install-plugin.sh [插件目录]
#
# 桌面版的插件只能在应用完全退出时修改：先退出桌面版，再用它自带的 dsh 命令安装。
set -euo pipefail

say() { printf '\033[36m==> %s\033[0m\n' "$*"; }
fail() { printf '\033[31mxx  %s\033[0m\n' "$*" >&2; exit 1; }

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
plugin="$(cd "${1:-$here/../../dsh-bridge}" && pwd)"
[ -f "$plugin/package.json" ] || fail "找不到插件：$plugin"

dsh_home="${DSH_HOME:-$HOME/.dsh}"
profile="$dsh_home/profiles/desktop/package.json"
[ -f "$profile" ] || fail "没有找到桌面版的 profile。请先打开一次 DeepSeek Harness 桌面版，再完全退出后重试。"

dsh=""
for candidate in \
  "/Applications/DeepSeek Harness.app/Contents/Resources/runtime/cli/bin/dsh" \
  "$HOME/Applications/DeepSeek Harness.app/Contents/Resources/runtime/cli/bin/dsh"; do
  if [ -x "$candidate" ]; then dsh="$candidate"; break; fi
done
if [ -z "$dsh" ] && command -v dsh >/dev/null 2>&1; then dsh="$(command -v dsh)"; fi
[ -n "$dsh" ] || fail "没有找到桌面版自带的 dsh 命令。可在桌面版菜单「管理 dsh 命令…」中安装后重试。"

if pgrep -f "DeepSeek Harness" >/dev/null 2>&1; then
  fail "桌面版正在运行。请完全退出 DeepSeek Harness（菜单 → 退出）后重试。"
fi
if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:7788 -sTCP:LISTEN >/dev/null 2>&1; then
  fail "端口 7788 已被占用（多半是旧方式启动的 dsh web）。请先结束它：lsof -nP -iTCP:7788 -sTCP:LISTEN"
fi

if grep -q '"dsh-bridge"' "$profile"; then
  say "移除旧的 dsh-bridge"
  "$dsh" plugin --profile desktop remove dsh-bridge
fi
say "安装 $plugin"
"$dsh" plugin --profile desktop add "$plugin"
grep -q '"dsh-bridge"' "$profile" || fail "profile 里没有出现 dsh-bridge，安装没有生效"
# 0.5.0 起插件自己管理 frpc：删除旧版注册的 launchd / systemd 服务。
if [ -f "$HOME/Library/LaunchAgents/com.pangxiaopang.frpc.plist" ]; then
  launchctl unload "$HOME/Library/LaunchAgents/com.pangxiaopang.frpc.plist" 2>/dev/null || true
  rm -f "$HOME/Library/LaunchAgents/com.pangxiaopang.frpc.plist"; say "已删除旧的 frpc launchd 服务"
fi
if command -v systemctl >/dev/null 2>&1 && systemctl --user cat pangxiaopang-frpc.service >/dev/null 2>&1; then
  systemctl --user disable --now pangxiaopang-frpc.service >/dev/null 2>&1 || true
  rm -f "$HOME/.config/systemd/user/pangxiaopang-frpc.service"; say "已删除旧的 frpc systemd 服务"
fi
[ -f "$HOME/.dsh-bridge/token" ] || say "注意：没有 ~/.dsh-bridge/token，请重新运行 setup-frpc.sh 写入 token。"
say "完成。打开 DeepSeek Harness 桌面版后执行：curl http://127.0.0.1:7788/v1/health"
say "应返回 runtime 为 desktop。"
