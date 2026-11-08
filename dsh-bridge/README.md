# dsh-bridge：把电脑上的 DeepSeek Harness 同步到胖小胖

`dsh-bridge` 是一个 DeepSeek Harness（DSH）插件。它运行在电脑上的 DSH 进程里，直接调用 DSH 自己的会话服务，然后在 `127.0.0.1:7788` 上提供一条 WebSocket 连接。手机上的胖小胖通过你的公网服务器（frp 中转）连到这里。

```
胖小胖 ──wss──▶ 服务器 Caddy(:443, TLS) ──▶ frps ══隧道══ frpc ──▶ 127.0.0.1:7788 dsh-bridge（DSH 进程内）
```

- **会话由桌面执行**：Agent、模型、工具、MCP、Skills 都跑在电脑上，手机只负责查看和发送指令，能力和桌面完全一致。
- **同步快**：全程一条长连接。模型输出的 token 约每 30ms 合并推送一次；断线重连后按序号只补缺失的差量；手机网络切换时立即重连。
- **审批**：工具需要授权时，手机和桌面网页会同时收到；任意一边先回答就生效，另一边的提示自动撤回。

协议细节见 [PROTOCOL.md](PROTOCOL.md)。

## 1. 电脑：安装插件

需要 Node.js 22 或更高版本，以及已经能正常使用的 DSH（`npx @deepseek-ai/dsh web` 或 `dsh web`）。

```sh
git clone https://github.com/Lmin2419/eta-background.git
dsh plugin --profile web add ./eta-background/dsh-bridge
dsh web
```

插件没有任何第三方依赖，`add` 时 pnpm 只会把这个目录链接进 web profile。之后每次运行 `dsh web`，插件都会随之启动。

- 首次启动时，插件生成一个随机 token，写入 `~/.dsh-bridge/token`（文件权限 600）。手机端需要填这个值：`cat ~/.dsh-bridge/token`。
- 也可以用环境变量 `DSH_BRIDGE_TOKEN` 指定 token，或在 profile 的 `cordis.patch.yml` 里改监听端口：

```yaml
- id: dsh-bridge
  config:
    host: 127.0.0.1
    port: 7788
```

验证插件是否已启动：`curl http://127.0.0.1:7788/v1/health`，应返回 `{"ok":true,"protocol":1}`。

> 只在电脑上用 DSH 桌面版（Desktop 应用）的话，需要在桌面版使用的 profile 中执行同样的 `dsh plugin --profile <名称> add`。

## 2. 服务器：frps + Caddy

frp 只负责转发 TCP，TLS 由服务器上的 Caddy 终止（它会自动申请证书）。示例中假设域名 `desk.example.com` 已解析到服务器。

`/etc/frp/frps.toml`：

```toml
bindPort = 7000
auth.method = "token"
auth.token = "换成一个长随机串"
# 桥接端口只在服务器本机可见，由 Caddy 反代出去
proxyBindAddr = "127.0.0.1"
```

`/etc/caddy/Caddyfile`：

```caddyfile
desk.example.com {
    reverse_proxy 127.0.0.1:17788
}
```

Caddy 自动处理 WebSocket 升级，不需要额外配置。防火墙只需放行 80、443（证书和 HTTPS）以及 7000（frpc 连接）。17788 不要对外开放。

## 3. 电脑：frpc

`frpc.toml`：

```toml
serverAddr = "服务器 IP"
serverPort = 7000
auth.method = "token"
auth.token = "与 frps 相同"
transport.tls.enable = true
# 一条复用连接，减少握手延迟
transport.tcpMux = true

[[proxies]]
name = "dsh-bridge"
type = "tcp"
localIP = "127.0.0.1"
localPort = 7788
remotePort = 17788
```

启动 `frpc -c frpc.toml`，然后在任意设备上验证：`curl https://desk.example.com/v1/health`。

## 4. 手机：胖小胖

设置 → 电脑 → 右上角齿轮：

- 地址：`https://desk.example.com`（也可以只写域名）
- Token：`~/.dsh-bridge/token` 的内容（App 用 Android Keystore 加密保存）

保存后，会话列表会按 DSH 工作区分组展示。点进会话就是胖小胖原有的聊天界面：输入栏、排队与插队、停止、模型和推理强度切换、图片附件都能直接用，执行在电脑上进行。

## 安全说明

这条连接能让电脑执行命令，等同于远程 shell，请注意：

- 插件只监听 `127.0.0.1`，外部访问必须经过 frp 和 Caddy；
- 手机到服务器全程走 HTTPS/WSS；frpc 到 frps 之间开启了 `transport.tls`；
- token 长度 43 字符（256 位随机数），比较时使用常量时间算法，错误 token 会被记录；
- 想更换 token：删除 `~/.dsh-bridge/token` 后重启 DSH，再在手机上重新填写。

## 开发

```sh
cd dsh-bridge
npm test        # node --test，19 个用例
```

`lib/translate.js` 把 DSH 会话事件转换成手机端使用的条目；`lib/bridge.js` 负责连接、订阅与审批；`lib/ws.js` 是一个最小的 RFC 6455 服务端实现。
