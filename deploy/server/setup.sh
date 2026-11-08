#!/usr/bin/env bash
# 胖小胖 ⇄ DeepSeek Harness 中转服务器一键部署（Ubuntu 20.04+，root 运行）。
#
#   sudo bash setup.sh            # 首次部署；再次运行只会刷新配置并重新打印配对信息
#   sudo bash setup.sh --rotate   # 重新生成全部 token 与证书（手机和电脑需重新配对）
#
# 部署内容：
#   frps   :7000  电脑上的 frpc 连这里（强制 TLS + token）
#   nginx  :8443  手机连这里（自签证书，App 用证书指纹校验），反代到 127.0.0.1:17788
#   17788  只绑定在本机回环，由 frps 把电脑上的 dsh-bridge(127.0.0.1:7788) 映射过来
#
# 可用环境变量覆盖：PUBLIC_IP BRIDGE_PORT FRP_PORT STATE_DIR FRP_ARCHIVE FRP_MIRROR NO_SYSTEMD=1
set -euo pipefail

FRP_VERSION="0.72.0"
declare -A FRP_SHA256=(
  [linux_amd64]="daf719b32ca2778770bb92955e231a07414d754378b9af328aa2642862479804"
  [linux_arm64]="02180b857480302fd934e9ddbd50fe2f73229f99062f88756abc6de9df5e95cf"
)
PUBLIC_IP="${PUBLIC_IP:-8.163.249.159}"
BRIDGE_PORT="${BRIDGE_PORT:-8443}"
FRP_PORT="${FRP_PORT:-7000}"
REMOTE_PORT="${REMOTE_PORT:-17788}"
STATE_DIR="${STATE_DIR:-/etc/pangxiaopang}"
NO_SYSTEMD="${NO_SYSTEMD:-0}"
ROTATE=0
[[ "${1:-}" == "--rotate" ]] && ROTATE=1

say() { printf '\033[1;36m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m错误：\033[0m%s\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "请用 root 运行：sudo bash $0"
command -v apt-get >/dev/null || die "此脚本只支持 Ubuntu/Debian（需要 apt-get）"

case "$(uname -m)" in
  x86_64|amd64) ARCH=amd64 ;;
  aarch64|arm64) ARCH=arm64 ;;
  *) die "不支持的 CPU 架构：$(uname -m)" ;;
esac

port_in_use() { ss -ltnH "( sport = :$1 )" 2>/dev/null | grep -q .; }

# ---- 1. 依赖 ---------------------------------------------------------------
say "安装 nginx / openssl / qrencode"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq nginx openssl curl ca-certificates qrencode >/dev/null

# ---- 2. frps ---------------------------------------------------------------
install_frps() {
  local name="frp_${FRP_VERSION}_linux_${ARCH}"
  local want="${FRP_SHA256[linux_${ARCH}]}"
  if [[ -x /usr/local/bin/frps ]] && /usr/local/bin/frps --version 2>/dev/null | grep -qx "$FRP_VERSION"; then
    say "frps $FRP_VERSION 已安装"
    return
  fi
  local tmp; tmp="$(mktemp -d)"
  local archive="$tmp/$name.tar.gz"
  if [[ -n "${FRP_ARCHIVE:-}" ]]; then
    cp "$FRP_ARCHIVE" "$archive"
  else
    local official="https://github.com/fatedier/frp/releases/download/v${FRP_VERSION}/${name}.tar.gz"
    local urls=("$official")
    [[ -n "${FRP_MIRROR:-}" ]] && urls=("${FRP_MIRROR%/}/$official" "${urls[@]}")
    urls+=("https://ghfast.top/$official" "https://gh-proxy.com/$official")
    local ok=0
    for url in "${urls[@]}"; do
      say "下载 frp：$url"
      if curl -fL --connect-timeout 10 --max-time 300 --speed-limit 102400 --speed-time 15 -o "$archive" "$url" && \
         [[ "$(sha256sum "$archive" | cut -d' ' -f1)" == "$want" ]]; then
        ok=1; break
      fi
      say "该地址失败或校验不符，换下一个"
    done
    [[ $ok -eq 1 ]] || die "frp 下载失败。可手动下载 ${name}.tar.gz 后用 FRP_ARCHIVE=/路径 重新运行"
  fi
  [[ "$(sha256sum "$archive" | cut -d' ' -f1)" == "$want" ]] || die "frp 安装包 SHA-256 校验失败"
  tar -xzf "$archive" -C "$tmp"
  install -m 0755 "$tmp/$name/frps" /usr/local/bin/frps
  rm -rf "$tmp"
  say "frps $(/usr/local/bin/frps --version) 已安装到 /usr/local/bin/frps"
}
install_frps

# ---- 3. 密钥与证书（只生成一次，--rotate 时重建） --------------------------
mkdir -p "$STATE_DIR"
chmod 700 "$STATE_DIR"
gen_token() { openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n'; }
if [[ $ROTATE -eq 1 ]]; then
  say "重新生成 token 与证书"
  rm -f "$STATE_DIR"/{frp.token,bridge.token,bridge.crt,bridge.key}
fi
[[ -s "$STATE_DIR/frp.token" ]] || { gen_token > "$STATE_DIR/frp.token"; }
[[ -s "$STATE_DIR/bridge.token" ]] || { gen_token > "$STATE_DIR/bridge.token"; }
if [[ ! -s "$STATE_DIR/bridge.crt" ]]; then
  say "生成自签证书（IP: $PUBLIC_IP，有效期 10 年）"
  openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 3650 \
    -subj "/CN=pangxiaopang-bridge" -addext "subjectAltName=IP:${PUBLIC_IP}" \
    -keyout "$STATE_DIR/bridge.key" -out "$STATE_DIR/bridge.crt" 2>/dev/null
fi
chmod 600 "$STATE_DIR"/*
FRP_TOKEN="$(cat "$STATE_DIR/frp.token")"
BRIDGE_TOKEN="$(cat "$STATE_DIR/bridge.token")"
# App 校验的是证书公钥的 SHA-256（base64url），换证书公钥不变时也不受影响。
PIN="$(openssl x509 -in "$STATE_DIR/bridge.crt" -pubkey -noout | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')"

# ---- 4. frps 配置 ------------------------------------------------------------
cat > "$STATE_DIR/frps.toml" <<EOF
# 由 setup.sh 生成，重新运行脚本会覆盖
bindAddr = "0.0.0.0"
bindPort = ${FRP_PORT}
# 映射出来的端口只在本机可见，外部必须经过 nginx 的 TLS
proxyBindAddr = "127.0.0.1"
allowPorts = [{ single = ${REMOTE_PORT} }]
auth.method = "token"
auth.token = "${FRP_TOKEN}"
transport.tls.force = true
transport.maxPoolCount = 5
log.to = "console"
log.level = "info"
EOF
chmod 600 "$STATE_DIR/frps.toml"

# ---- 5. nginx：TLS 终止 + WebSocket 反代 -----------------------------------
NGINX_CONF=/etc/nginx/conf.d/pangxiaopang-bridge.conf
if port_in_use "$BRIDGE_PORT" && ! grep -qs "pangxiaopang" "$NGINX_CONF"; then
  die "端口 $BRIDGE_PORT 已被其他程序占用，可用 BRIDGE_PORT=其他端口 重新运行"
fi
LISTEN6=""
if [[ -s /proc/net/if_inet6 ]]; then
  LISTEN6="listen [::]:${BRIDGE_PORT} ssl;"
else
  # 内核关闭了 IPv6 时，nginx 自带的 default 站点（listen [::]:80）会让 nginx 整体起不来。
  for site in /etc/nginx/sites-enabled/*; do
    [[ -f "$site" ]] && grep -q 'listen \[::\]' "$site" || continue
    backup="$STATE_DIR/nginx-$(basename "$site").bak"
    [[ -e "$backup" ]] || cp -L "$site" "$backup"
    sed -i 's/^\(\s*\)listen \[::\]/\1# listen [::]/' "$(readlink -f "$site")"
    say "系统未启用 IPv6，已注释 $site 中的 [::] 监听（原文件备份：$backup）"
  done
fi
cat > "$NGINX_CONF" <<EOF
# 由 pangxiaopang setup.sh 生成
server {
    listen ${BRIDGE_PORT} ssl;
    ${LISTEN6}
    server_name _;
    ssl_certificate     ${STATE_DIR}/bridge.crt;
    ssl_certificate_key ${STATE_DIR}/bridge.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:pxp:1m;
    ssl_session_timeout 1d;
    access_log off;

    location / {
        proxy_pass http://127.0.0.1:${REMOTE_PORT};
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-For \$remote_addr;
        proxy_buffering off;
        proxy_read_timeout 1h;
        proxy_send_timeout 1h;
        tcp_nodelay on;
    }
}
EOF
nginx -t -q

# ---- 6. 服务 ---------------------------------------------------------------
if [[ "$NO_SYSTEMD" == "1" ]]; then
  say "NO_SYSTEMD=1：跳过 systemd，请自行运行 /usr/local/bin/frps -c $STATE_DIR/frps.toml 和 nginx"
else
  cat > /etc/systemd/system/frps.service <<EOF
[Unit]
Description=frps for pangxiaopang desktop bridge
After=network-online.target
Wants=network-online.target

[Service]
ExecStart=/usr/local/bin/frps -c ${STATE_DIR}/frps.toml
Restart=always
RestartSec=3
LimitNOFILE=65536
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
EOF
  systemctl daemon-reload
  systemctl enable --now frps >/dev/null 2>&1
  systemctl restart frps
  systemctl enable --now nginx >/dev/null 2>&1
  systemctl reload nginx
  if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
    ufw allow "${FRP_PORT}/tcp" >/dev/null
    ufw allow "${BRIDGE_PORT}/tcp" >/dev/null
    say "已在 ufw 放行 ${FRP_PORT}/tcp 与 ${BRIDGE_PORT}/tcp"
  fi
  sleep 1
  systemctl is-active --quiet frps || die "frps 未能启动，查看：journalctl -u frps -n 50"
fi

# ---- 7. 输出配对信息 ---------------------------------------------------------
DESKTOP_CODE="pxd://${PUBLIC_IP}:${FRP_PORT}?frp=${FRP_TOKEN}&token=${BRIDGE_TOKEN}&port=${REMOTE_PORT}"
PHONE_CODE="pxp://${PUBLIC_IP}:${BRIDGE_PORT}?pin=${PIN}&token=${BRIDGE_TOKEN}"
cat > "$STATE_DIR/pairing.txt" <<EOF
desktop=${DESKTOP_CODE}
phone=${PHONE_CODE}
EOF
chmod 600 "$STATE_DIR/pairing.txt"

cat <<EOF

────────────────────────────────────────────────────────────────
 部署完成
────────────────────────────────────────────────────────────────
 ① 阿里云/腾讯云控制台 → 安全组：放行 TCP ${FRP_PORT}（电脑连）和 ${BRIDGE_PORT}（手机连）

 ② 电脑（已装好 DeepSeek Harness 与 dsh-bridge 插件）执行：
    macOS / Linux：
      bash eta-background/deploy/desktop/setup-frpc.sh '${DESKTOP_CODE}'
    Windows PowerShell：
      powershell -ExecutionPolicy Bypass -File eta-background\\deploy\\desktop\\setup-frpc.ps1 '${DESKTOP_CODE}'
    然后重启 dsh web。

 ③ 手机：胖小胖 → 设置 → 电脑 → 齿轮 → 粘贴配对码（或扫下方二维码后复制）：
    ${PHONE_CODE}

 证书指纹（App 会核对）：${PIN}
 以上信息保存在 ${STATE_DIR}/pairing.txt，再次运行本脚本也会重新打印。
 配对码含有能控制你电脑的 token，不要发到群聊或公开的地方。
────────────────────────────────────────────────────────────────
EOF
if command -v qrencode >/dev/null; then
  echo " 手机配对码二维码："
  qrencode -t ansiutf8 -m 1 "$PHONE_CODE"
fi
