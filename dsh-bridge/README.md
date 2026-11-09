# dsh-bridge：把电脑上的 DeepSeek Harness 同步到胖小胖

`dsh-bridge` 是一个 DeepSeek Harness（DSH）插件。它运行在电脑上的 DSH 进程里，直接调用 DSH 自己的会话服务，然后在 `127.0.0.1:7788` 上提供一条 WebSocket 连接。手机上的胖小胖通过你的公网服务器（frp 中转）连到这里。

```
胖小胖 ──wss──▶ 服务器 nginx(:8443, TLS) ──▶ frps ══隧道══ frpc ──▶ 127.0.0.1:7788 dsh-bridge（DSH 进程内）
```

- **会话由桌面执行**：Agent、模型、工具、MCP、Skills 都跑在电脑上，手机只负责查看和发送指令，能力和桌面完全一致。
- **同步快**：全程一条长连接。模型输出的 token 约每 30ms 合并推送一次；断线重连后按序号只补缺失的差量；手机网络切换时立即重连。
- **审批**：工具需要授权时，手机和桌面网页会同时收到；任意一边先回答就生效，另一边的提示自动撤回。

协议细节见 [PROTOCOL.md](PROTOCOL.md)。

## 1. 电脑：安装插件（DSH 桌面版）

插件运行在 DeepSeek Harness 桌面版自己的进程里，读到的会话、工作区、归档与桌面侧栏完全一致。桌面版的插件只能在应用**完全退出**时修改（从任务栏托盘退出，关窗口不算）。

**Windows**（在仓库目录执行）：

```powershell
git clone https://github.com/Lmin2419/eta-background.git
powershell -ExecutionPolicy Bypass -File eta-background\deploy\desktop\install-plugin.ps1 -Start
```

脚本会：确认桌面版 profile（`~/.dsh/profiles/desktop`）已初始化；找到桌面版自带的 `dsh` 命令（`…\resources\runtime\cli\bin\dsh.cmd`）；检查桌面版已退出、7788 端口没有被旧的 `dsh web` 占用；以链接方式安装插件；`-Start` 时启动桌面版并等到健康检查通过。桌面版或旧进程仍在运行时加 `-Force` 直接结束它们。

**macOS**：`bash eta-background/deploy/desktop/install-plugin.sh`

也可以手动安装：桌面版 → 侧栏「插件」→「安装第三方插件」→ 填 `dsh-bridge` 目录的绝对路径。

验证：`curl http://127.0.0.1:7788/v1/health`（Windows PowerShell 里写 `curl.exe`）应返回 `{"ok":true,"protocol":1,"version":"0.3.0","runtime":"desktop"}`。`runtime` 为 `node` 说明回应来自另外启动的 `dsh` 进程，而不是桌面版。

### 总开关

桌面版侧栏「插件」→ dsh-bridge 的开关就是电脑端的总开关：关掉后插件不再监听，手机连不上；打开后恢复。手机端另有自己的开关（设置 → 电脑）。

### 更新插件

插件以链接方式安装，更新只需拉取代码后重启桌面版：

```sh
cd eta-background && git pull
# 从托盘完全退出 DeepSeek Harness，再打开
curl http://127.0.0.1:7788/v1/health   # version 应为新版本
```

### 只用网页版（dsh web）时

```sh
dsh plugin --profile web add ./eta-background/dsh-bridge
dsh web
```

不要同时运行装了插件的 `dsh web` 和桌面版：两边会抢 7788 端口，而且是两个独立进程，手机看到的会话会不全。

### 其他说明

- token 保存在 `~/.dsh-bridge/token`（文件权限 600）。使用部署脚本时，脚本会写入服务器生成的 token；不使用脚本时，插件首次启动会自动生成一个随机 token。
- 也可以用环境变量 `DSH_BRIDGE_TOKEN` 指定 token，或在 profile 的 `cordis.patch.yml` 里改监听端口：

```yaml
- id: dsh-bridge
  name: dsh-bridge
  config:
    host: 127.0.0.1
    port: 7788
```

## 2. 服务器、电脑 frpc 与手机配对

用一键脚本完成，步骤见 [deploy/README.md](../deploy/README.md)：

1. 服务器：`sudo bash deploy/server/setup.sh`，会安装 frps 和 nginx（自签证书），并打印配对信息；
2. 电脑：`bash deploy/desktop/setup-frpc.sh 'pxd://…'`（Windows 用 `setup-frpc.ps1`），会安装 frpc 并写入 token；
3. 手机：设置 → 电脑 → 粘贴 `pxp://` 配对码。

App 内置了中转服务器 `8.163.249.159:8443`。配对码里带有证书指纹，App 只信任这一把公钥，因此服务器只有 IP、使用自签证书也能安全连接。

### 有域名时（可选）

服务器有域名时，也可以用 Caddy 自动申请公网证书，代替自签证书：

```caddyfile
desk.example.com {
    reverse_proxy 127.0.0.1:17788
}
```

frps 和 frpc 的配置与脚本生成的相同。手机端在「手动填写」里输入 `https://desk.example.com` 和 token 即可，不需要证书指纹。

## 安全说明

这条连接能让电脑执行命令，等同于远程 shell，请注意：

- 插件只监听 `127.0.0.1`，外部访问必须经过 frp 和 Caddy；
- 手机到服务器全程走 WSS，App 按配对码里的指纹固定服务器公钥；frpc 到 frps 之间强制 TLS，frps 映射的端口只绑定在服务器本机；
- token 长度 43 字符（256 位随机数），比较时使用常量时间算法，错误 token 会被记录；
- 想更换 token 和证书：在服务器上执行 `sudo bash setup.sh --rotate`，然后重新配对电脑和手机。

## 开发

```sh
cd dsh-bridge
npm test        # node --test，31 个用例
```

`lib/translate.js` 把 DSH 会话事件转换成手机端使用的条目；`lib/bridge.js` 负责连接、订阅与审批；`lib/ws.js` 是一个最小的 RFC 6455 服务端实现。
