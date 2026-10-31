# E08c MCP 服务器移植（0.9.30）

来源固定为 `3b1794d70881c3c849b53d7778dd2bfdf0a46cc5`。保留 Required Notice: Copyright © 2026 蛮吉 (Mangi-11)，PolyForm Noncommercial 1.0.0；应用内品牌为“随身助手”。

分支 `harmony/e08c-mcp` 从最后通过四项检查的 `harmony/e08b-skills`（PR #34）建立。PR #35 的 Skills 页面第三次检查仍有 ArkTS 错误，保持草稿，本分支不依赖、不包含它。

## 范围与逐项对照

| Eta 来源/元素 | 鸿蒙实现 | 分级 |
| --- | --- | --- |
| `ui/screens/mcp/McpServersScreen.kt` 列表顶栏、添加按钮、数量标题、空态、服务器行 | `ui/eta/screens/McpServersScreen.ets`；顺序、文案、图标一致，行摘要显示启用数/工具总数 | A：代码完成，待真机 |
| 添加弹窗：名称、URL、可选 Bearer；先发现目录再保存 | `McpDialogs.ets` / `McpUiStore`，独立草稿、错误保留、连接中禁用输入；按钮固定、表单最多 520vp | A：代码完成，待真机 |
| 详情：刷新、服务器开关、身份认证、工具数量、逐工具开关、删除 | 同一屏幕的详情投影；源码分组顺序和摘要回退规则 | A：代码完成，待真机 |
| 非只读工具启用确认 | 两项原文插值、破坏性确认样式；关闭/只读启用直接保存 | A：代码完成，待真机 |
| Token 更新、留空移除认证；删除确认 | 原生弹窗，保存成功才关闭/返回；失败留当前编辑 | A：代码完成，待真机 |
| `data/model/McpServer.kt` / `data/db/McpEntities.kt` | `core/eta/mcp/mcpTypes.ts`；完整缓存字段，忽略旧 unavailableReason，不另加 schema 可用性过滤 | A |
| `McpServerRepository.kt` / `McpSecretStore.kt` | 单一串行写队列；现有 RDB 独立文档 `eta_mcp_servers`，Token 仅 ASSET，失败按原逻辑补偿 | A：平台待测 |
| `McpHttpClient.kt`、`McpToolHeaders.kt`、`McpServerManager.kt` | 现代无状态协议、兼容回退、旧版协商、session 404 一次重建、JSON/SSE、工具目录分页/TTL、参数头编码；HTTP 平台端口 | A：自动测试通过，服务/设备待测 |
| `McpRunContext.kt` 的 activeTools | 仅纯列表筛选，不因 schema 关键字禁用工具 | A |
| MCP 真正作为模型工具调用、结果上下文注入 | 本轮不接；页首“已保存；鸿蒙版对话暂未使用此功能” | 后续独立任务 |

## 与 Eta 的差异

1. Compose/Room/OkHttp/Android 密钥存储改为 ArkUI/RDB/NetworkKit/ASSET。没有把 Android 可执行代码移入鸿蒙，没有启动旧 AppRuntime 或后台服务。
2. 按已确认决定采用鸿蒙原生弹窗和 Toast；输入框用原生标签/密码框，URI 键盘对应 `InputType.URL`。窗口、键盘避让、字体/圆角/动画尚未真机截图对照。
3. UI 状态来自 `McpUiStore`；页面只投影快照。弹窗打开期间以相同 32ms 投影节奏更新，退出清理计时器，不维护第二份草稿。离页、关闭、改地址取消请求并作废迟到结果；不会把迟到目录写入新草稿。
4. Token 受 ASSET 的 **1024 UTF-8 字节**上限限制，拒绝换行。URL 不接受内嵌密码，Token 应放专用字段。网络错误不回显服务器正文、Authorization 或密钥；补充三条固定平台错误文案，由资源脚本生成。
5. HTTP 禁止自动重定向，响应上限 1 MiB、单请求 60 秒、API 23 以下显式失败；源规则仍为至多 8 页、128 个工具、标题 160 字符、描述 2000 字符。JS 时间戳/TTL 在安全整数范围饱和。`x-mcp-header` 递归深度限制 64；未新增 schema 可用性过滤。
6. 普通开关与刷新写入期间禁用重复提交。风险确认、Token、删除保存失败保留弹窗；不显示“已经保存”。这是存储错误的适配保护。
7. RDB 单文档原子写；ASSET 与 RDB 不是同一事务，沿用 Eta 的补偿顺序。不能把异常掉电下的跨服务原子性写成已证明；真机需检查 Token 更新/删除/重启。无旧会话迁移或清除。
8. 纯 `callTool` 保留是为了翻译原协议测试；正式入口只调用目录发现，不注册模型工具，开关保存不等于工具已经参与对话。

## 单测清单

完整翻译 **14 项**，不缩短关键输入或网络时序断言：

- `McpProtocolValidationTest` **12/12**：现代元数据/路由头、HTTP 回退、旧结果形状回退、旧版本协商、现代错误不回退、多行 SSE 最终事件提前结束、工具调用 session 404、目录 session 404、立即取消、URL、schema 原样保留、参数镜像头。使用真实 loopback HTTP 服务，不把 SSE/取消测试改成只检查预制结果。
- `McpEntitiesTest` **1/1**：旧 `unavailableReason` 不禁用缓存工具。
- `McpRunContextTest` **1/3**：启用工具不被 schema 关键字过滤。
- 延期/未移植 **2 项**：`snapshotUsesFrozenTokenAndReportsTruncatedOrUnsupportedContent`、`closedExecutorDoesNotStartFirstRemoteCall`。两项验证完整模型 executor 的冻结 Token、结果截断及执行器生命周期；本轮不接模型工具，未把部分端口测试冒充通过这两项。

新增 **18 项**适配/集成：无认证、Key 不入 RDB、失败回滚、ASSET 错误、字节上限、串行开关、离页取消、失败留草稿、成功新增默认工具关闭、风险确认、刷新/TTL、清除 Token/失败删除、迟到会话释放、分页上限、有界响应/ID、损坏文件、详情返回/删除路由、切换对话取消目录。

第三次整套检查通过：`npm run check` 成功；`npm test` 919 通过 / 0 失败 / 0 runner skip；`npm run check:ets` 0 error、99 warning（8 条既有 `$$this` 误报按既有规则排除）；`npm run check:res` restool 通过、1,946 资源。延期 2 项未纳入 runner，不能把 runner skip 为 0 理解为所有 Eta 测试均已移植。没有生成 HAP，没有真机或真实远程 MCP 服务测试结论。

## 权限与接口

新增权限：**无**。签名配置为空，已有 INTERNET/VIBRATE 不变。

本任务使用的鸿蒙接口：NetworkKit `http.createHttp`、`requestInStream`、`headersReceive/dataReceive/dataEnd`、`destroy`、POST/DELETE 和禁用重定向；ASSET `add/query/remove`、`ConflictResolution.OVERWRITE`、设备首次解锁可读、禁止同步；`util.TextEncoder/TextDecoder/generateRandomUUID`；已有 RuntimeDatabase 文档事务；ArkUI `CustomDialogController`、`InputType.URL/Password`、`getPromptAction().showToast`。均需用户 DevEco 最终编译和真机验证。

## 待用户确认与验收

- 确认上面的平台差异，尤其 Token 上限、固定错误文案和原生弹窗/输入框表现。没有改变原页面信息结构。
- 按 `device-runs/TEMPLATE.md` 的 E08c 步骤走通新增→目录→刷新→开关→Token→删除。全为**待测**。
- 与 Eta 对照深浅色、折叠/展开、横竖屏、大字体和键盘弹出时按钮是否可达。没有以静态检查代替截图。
