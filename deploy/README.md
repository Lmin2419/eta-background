# 部署：服务器 frps + 电脑 frpc + 手机配对

整条链路：

```
胖小胖 ──wss:8443（自签证书，App 按指纹校验）──▶ 服务器 nginx ──▶ 127.0.0.1:17788
                                                                  ▲ frps 映射
电脑 dsh-bridge(127.0.0.1:7788) ◀── frpc ══TLS:7000══▶ 服务器 frps
```

内置服务器：`8.163.249.159`，App 已默认填好这个地址。证书指纹和 token 只通过配对码传给手机，不写进源码和 APK：仓库和 Release 都是公开的，token 一旦泄露，别人就能控制你的电脑。

## 1. 服务器（Ubuntu，执行一次）

把仓库里的 `deploy/server/setup.sh` 传到服务器上执行，或者在服务器上直接克隆仓库：

```sh
git clone https://github.com/Lmin2419/eta-background.git
sudo bash eta-background/deploy/server/setup.sh
```

脚本做了这些事：

- 安装 frp v0.72.0（先试 GitHub，失败自动换国内镜像，安装前用内置的官方 SHA-256 校验）、nginx、openssl、qrencode；
- 生成 frp token、dsh-bridge token，以及一张 10 年有效期的自签证书（只用于这个 IP）；
- frps 监听 7000，强制 TLS；映射出来的 17788 端口只绑定在服务器本机；
- nginx 监听 8443，终止 TLS 后把 WebSocket 反代到 17788；
- 用 systemd 管理 frps 并设置开机自启；如果开启了 ufw，同时放行 7000 和 8443；
- 打印电脑配对串（`pxd://…`）、手机配对码（`pxp://…`）和配对码的二维码。

**还需要在云控制台的安全组里放行 TCP 7000 和 8443**，这一步脚本做不了。

再次运行脚本不会更换 token 或证书，只刷新配置并重新打印配对信息（也保存在 `/etc/pangxiaopang/pairing.txt`）。需要全部重新生成时执行 `sudo bash setup.sh --rotate`，之后电脑和手机都要重新配对。

常用排查：

```sh
systemctl status frps          # frps 状态
journalctl -u frps -n 50       # 能看到电脑的 frpc 是否登录成功
curl -k https://127.0.0.1:8443/v1/health   # 电脑在线时返回 {"ok":true,...}，离线时返回 502
```

## 2. 电脑

先装好 DeepSeek Harness 和插件（见 [dsh-bridge/README.md](../dsh-bridge/README.md)），然后用服务器打印的 `pxd://` 配对串执行：

```sh
# macOS / Linux
bash eta-background/deploy/desktop/setup-frpc.sh 'pxd://8.163.249.159:7000?frp=…&token=…&port=17788'
```

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File eta-background\deploy\desktop\setup-frpc.ps1 'pxd://8.163.249.159:7000?frp=…&token=…&port=17788'
```

脚本会安装并校验 frpc，写入 `~/.dsh-bridge/frpc.toml` 和 dsh-bridge 的 token（`~/.dsh-bridge/token`），然后注册为登录后自动运行的后台服务（macOS 用 launchd，Linux 用 systemd 用户服务，Windows 用计划任务）。完成后**重启一次 `dsh web`**，让插件读取新的 token。

## 3. 手机

胖小胖 → 设置 → 电脑 → 右上角齿轮 → 复制 `pxp://` 配对码后点「粘贴并连接」。服务器终端里的二维码可以用手机相机或系统扫码识别，识别后复制文字即可。

连上后，设置页会显示证书指纹的前几位。如果之后提示「服务器证书指纹与配对码不一致」，要么服务器执行过 `--rotate`，要么连接被人劫持了。确认是前者后，重新粘贴新的配对码即可。
