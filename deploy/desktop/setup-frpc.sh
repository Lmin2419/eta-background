#!/usr/bin/env bash
# 电脑端：安装 frpc，把 dsh-bridge(127.0.0.1:7788) 映射到中转服务器，并写入配对 token。
#
#   bash setup-frpc.sh 'pxd://服务器IP:7000?frp=…&token=…&port=17788'
#
# 配对串由服务器上的 deploy/server/setup.sh 打印。支持 macOS 与 Linux；
# 安装到 ~/.dsh-bridge/，并注册为登录后自动运行的后台服务。
set -euo pipefail

FRP_VERSION="0.72.0"
sha_for() {
  case "$1" in
    darwin_amd64) echo 60439dc6bf794c4182a1a4cac147d642f839d55201607208979eb23aecd09be9 ;;
    darwin_arm64) echo 6e8576c5b6b9914a86850f6ff4fb81c5a09e706f91f70f64d5f398558bdc9db8 ;;
    linux_amd64)  echo daf719b32ca2778770bb92955e231a07414d754378b9af328aa2642862479804 ;;
    linux_arm64)  echo 02180b857480302fd934e9ddbd50fe2f73229f99062f88756abc6de9df5e95cf ;;
    *) return 1 ;;
  esac
}
HOME_DIR="${DSH_BRIDGE_HOME:-$HOME/.dsh-bridge}"
BRIDGE_LOCAL_PORT="${BRIDGE_LOCAL_PORT:-7788}"


say() { printf '\033[1;36m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m错误：\033[0m%s\n' "$*" >&2; exit 1; }

CODE="${1:-}"
[[ "$CODE" == pxd://* ]] || die "用法：bash $0 'pxd://…'（配对串由服务器部署脚本打印）"
rest="${CODE#pxd://}"
hostport="${rest%%\?*}"
query="${rest#*\?}"
SERVER="${hostport%:*}"
SERVER_PORT="${hostport##*:}"
param() { printf '%s' "$query" | tr '&' '\n' | sed -n "s/^$1=//p" | head -n1; }
FRP_TOKEN="$(param frp)"
BRIDGE_TOKEN="$(param token)"
REMOTE_PORT="$(param port)"
REMOTE_PORT="${REMOTE_PORT:-17788}"
[[ -n "$SERVER" && -n "$SERVER_PORT" && -n "$FRP_TOKEN" && -n "$BRIDGE_TOKEN" ]] || die "配对串不完整"

case "$(uname -s)" in
  Darwin) OS=darwin ;;
  Linux) OS=linux ;;
  *) die "此脚本只支持 macOS 和 Linux，Windows 请用 setup-frpc.ps1" ;;
esac
case "$(uname -m)" in
  x86_64|amd64) ARCH=amd64 ;;
  arm64|aarch64) ARCH=arm64 ;;
  *) die "不支持的 CPU 架构：$(uname -m)" ;;
esac
sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }

mkdir -p "$HOME_DIR/bin"
chmod 700 "$HOME_DIR"

# ---- frpc --------------------------------------------------------------------
FRPC="$HOME_DIR/bin/frpc"
if [[ -x "$FRPC" ]] && "$FRPC" --version 2>/dev/null | grep -qx "$FRP_VERSION"; then
  say "frpc $FRP_VERSION 已安装"
else
  name="frp_${FRP_VERSION}_${OS}_${ARCH}"
  want="$(sha_for "${OS}_${ARCH}")"
  tmp="$(mktemp -d)"
  archive="$tmp/$name.tar.gz"
  official="https://github.com/fatedier/frp/releases/download/v${FRP_VERSION}/${name}.tar.gz"
  urls=()
  [[ -n "${FRP_MIRROR:-}" ]] && urls+=("${FRP_MIRROR%/}/$official")
  urls+=("$official" "https://ghfast.top/$official" "https://gh-proxy.com/$official")
  ok=0
  if [[ -n "${FRP_ARCHIVE:-}" ]]; then cp "$FRP_ARCHIVE" "$archive"; ok=1; fi
  if [[ $ok -eq 0 ]]; then
    for url in "${urls[@]}"; do
      say "下载 frpc：$url"
      if curl -fL --connect-timeout 10 --max-time 300 --speed-limit 102400 --speed-time 15 -o "$archive" "$url" && [[ "$(sha256 "$archive")" == "$want" ]]; then
        ok=1; break
      fi
      say "该地址失败或校验不符，换下一个"
    done
  fi
  [[ $ok -eq 1 ]] || die "frpc 下载失败。可手动下载 ${name}.tar.gz 后用 FRP_ARCHIVE=/路径 重新运行"
  [[ "$(sha256 "$archive")" == "$want" ]] || die "frpc 安装包 SHA-256 校验失败"
  tar -xzf "$archive" -C "$tmp"
  install -m 0755 "$tmp/$name/frpc" "$FRPC"
  rm -rf "$tmp"
  [[ "$OS" == darwin ]] && xattr -d com.apple.quarantine "$FRPC" 2>/dev/null || true
  say "frpc 已安装到 $FRPC"
fi

# ---- 配置与 token --------------------------------------------------------------
cat > "$HOME_DIR/frpc.toml" <<EOF
# 由 setup-frpc.sh 生成
serverAddr = "${SERVER}"
serverPort = ${SERVER_PORT}
# 服务器暂时不可达时持续重试，而不是退出
loginFailExit = false
auth.method = "token"
auth.token = "${FRP_TOKEN}"
transport.tls.enable = true
transport.poolCount = 1
transport.heartbeatInterval = 10
transport.heartbeatTimeout = 30
log.to = "console"
log.level = "info"

[[proxies]]
name = "dsh-bridge"
type = "tcp"
localIP = "127.0.0.1"
localPort = ${BRIDGE_LOCAL_PORT}
remotePort = ${REMOTE_PORT}
EOF
chmod 600 "$HOME_DIR/frpc.toml"
printf '%s\n' "$BRIDGE_TOKEN" > "$HOME_DIR/token"
chmod 600 "$HOME_DIR/token"
"$FRPC" verify -c "$HOME_DIR/frpc.toml" >/dev/null
say "已写入 $HOME_DIR/frpc.toml 与 dsh-bridge token（$HOME_DIR/token）"

# ---- 旧的后台服务 -----------------------------------------------------------
# dsh-bridge 0.5.0 起 frpc 由插件启动和停止（随 DSH 开关），这里只清理旧版注册的服务。
plist="$HOME/Library/LaunchAgents/com.pangxiaopang.frpc.plist"
if [[ -f "$plist" ]]; then
  launchctl unload "$plist" 2>/dev/null || true
  rm -f "$plist"
  say "已删除旧的 launchd 服务 com.pangxiaopang.frpc"
fi
if command -v systemctl >/dev/null && systemctl --user cat pangxiaopang-frpc.service >/dev/null 2>&1; then
  systemctl --user disable --now pangxiaopang-frpc.service >/dev/null 2>&1 || true
  rm -f "$HOME/.config/systemd/user/pangxiaopang-frpc.service"
  systemctl --user daemon-reload || true
  say "已删除旧的 systemd 用户服务 pangxiaopang-frpc"
fi
pkill -f "$FRPC -c" 2>/dev/null || true
say "frpc 将由 dsh-bridge 插件在 DSH 运行时自动启动"

cat <<EOF

完成。接下来：
  1. 完全退出并重新打开 DeepSeek Harness 桌面版（只用网页版时重启 dsh web），让 dsh-bridge 读取新的 token
  2. 检查：curl -s http://127.0.0.1:${BRIDGE_LOCAL_PORT}/v1/health 应返回 {"ok":true,...}
  3. 手机上粘贴服务器脚本打印的 pxp:// 配对码
EOF
