# 可选网页增强运行时

`fetch_url` 继续优先使用 APK 内的轻量 HTTP 读取。只有检测到需要升级的网页，才会调用这里的 Scrapling 适配器；普通抓取不会安装依赖或启动 Python。安装入口复用 Linux 环境设置，不需要配置 MCP 服务。

## 组件与范围

| 组件 | 内容 | 实际可用条件 |
| --- | --- | --- |
| `web_http` | 独立 Python 环境、Scrapling 0.5、curl_cffi 0.17.0、Patchright 1.64.4、Playwright 1.64.1 | 已配置 Debian、显式安装依赖、实际 ABI 上导入成功 |
| `web_browser` | Chromium 与 Debian 动态库，单独按需下载 | 前项可用，浏览器在本机开启沙箱、TLS 校验的条件下实际启动成功 |

Scrapling 的 HTTP 路径也导入 Patchright 类型，因此安装的依赖比纯解析器大得多。APK 仅携带约数 KB 的适配脚本，不内嵌 Python、浏览器或实验环境目录。Alpine 的 musl 兼容性未验证，当前两个安装项仅支持 Debian。

安装标记只代表依赖安装完成，不代表 ARM64、PRoot 或当前 ROM 已通过实际浏览器验证。浏览器不得通过 `--no-sandbox` 或忽略证书错误来假装可用。此版本保留用户设备验证门槛；云端 x86_64 测试不能替代手机结果。

## 执行与资源

- App 与专用 Linux 子进程通过 JSON 行通信，不开放监听端口；URL 放在 stdin，不拼接到 shell 命令。
- 只支持只读 HTTP/HTTPS GET 和能力探测，无任意脚本、Shell 或登录信息导入接口。
- 使用任务自己的匿名会话；不读取系统浏览器 Cookie、用户资料目录或账号令牌。
- HTTP 子进程按需启动、同任务复用，空闲 30 秒后退出。增强浏览器最多一个请求占用，完成后回收；正常 WebView 浏览器仍使用其原有会话复用。
- 一个读取最多一次升级，不把 HTTP、指纹请求、浏览器连续无界尝试。SDK 的默认三次请求改为一次。
- App 墙钟预算是最终硬边界。Python 信号计时只辅助尽早返回错误，SDK 可能吞掉信号异常，不能单独依赖它。App 取消/超时只终止本任务拥有的进程树；结果不明时不重放请求。
- HTTP 接收上限 2 MiB、返回正文上限 300,000 字符、IPC 行上限 2 MiB。截断有独立标记。页面失败的原始正文、异常 URL、请求头、Cookie 不写日志。
- `CLOUDFLARE` 路由才设置 `solve_cloudflare=True`。仍返回挑战页或未取得正文时，由上层按挑战未解决处理；不把 HTTP 200、绿色测试框或成功启动浏览器当作绕过成功。

Scrapling 上游：<https://github.com/D4Vinci/Scrapling>，作者 D4Vinci，MIT 许可证。项目未修改或复制其源码；使用已固定版本的公开 Python API。为保留 Chromium 沙箱，适配器对 0.5 的浏览器启动选项作显式约束，升级依赖时应重新检验。

## 检验

标准库协议测试：

```sh
python3 -m unittest discover -s etax/web/tests -v
```

安装过固定依赖的 Linux Python 可额外执行真实 Fetcher + 本地 HTTP fixture：

```sh
ETA_SCRAPLING_PYTHON=/path/to/python python3 -m unittest discover -s etax/web/tests -v
```

覆盖：凭据 URL 拒绝、挑战分类、错误正文隔离、单次请求/TLS 校验、流式大小限制、真实超时和 JSON 行交接。`ScraplingProtocolTest` 覆盖错请求响应、不完整/过大结果、UTF-8 及错误码约束。

这些测试不证明真实 Cloudflare 通过率，也不证明 Android ARM64 浏览器可运行。手机验收须分别记录 HTTP 导入、HTTP 抓取、浏览器启动和真实挑战正文读取的结果。

本轮云端 x86_64 实测记录：

- 固定依赖下 10 项 Python 测试通过，其中 2 项通过真实 Scrapling 与本地 HTTP fixture 验证；其余为协议和配置约束。
- `https://example.com` 经生产适配器读取，HTTP 200、正文包含 `Example Domain`，HTTP 阶段约 429 ms；单次样本，不代表手机速度。
- 已安装的固定 Chromium 上执行真实 `about:blank` 探测，沙箱保持开启，HTTP/浏览器能力探测通过，约 3.5 秒。
- `https://www.scrapingcourse.com/cloudflare-challenge` 启用 solver 后，在 35 秒预算内仍无协议结果；宿主按截止时间终止其独立进程组。未取得目标正文，不能算挑战通过；随后确认该实验浏览器路径下无残留进程。这验证了宿主硬超时的必要性，未证明 Android 的进程回收效果。
