# P1a：OpenAI Responses 与公共协议部件（0.9.21）

基于 `harmony/e07b2-rdb-migration`，分支 `harmony/p1a-responses`。来源固定 `app/.../agent/model/ @ 3b1794d`，不是继续扩展旧版简化的 Responses 编码。正式客户端在 Responses 模式走新实现；Chat Completions 留 P1b 替换。

## 逐项对照

| Eta 来源 | 鸿蒙实现 | 状态 |
| --- | --- | --- |
| `ResponsesRequestBuilder`、`OpenAiRequestMessages.responsesInstructions` | system/developer 指令合并，typed 输入、图像和 function_call；deep copy，不修改原历史 | A |
| 请求保护字段 | model/input/instructions/stream/store 最后写入，store=false，移除 previous_response_id；工具扁平化、strict=false | A |
| `OpenAiResponsesProvider` | 增量 text、reasoning、summary、工具参数、hosted 工具事件、完整 usage；终态权威修正、空终态的合法增量回退 | A 协议；真机待测 |
| 内容块身份与顺序 | item/output/part/family 匹配；done/terminal 不重复结束，不把 typed reasoning 与旧别名重复拼接 | A 协议测试 |
| `ProviderSseReader` | 注释、多行、CR/LF 跨块、EOF 最后一帧、取消、消费异常；终态返回 false 关闭传输 | A；NetworkKit 待测 |
| `ProviderReasoning` | Responses / Chat / Anthropic 参数映射和能力约束；23 项原测试 | A 纯逻辑；设置接入留 E05b/E06b |
| `ProviderRequestHeaders` / `CustomHeaderFilter` | 大小写覆盖、禁止认证覆盖、OpenCode 会话 UUID v3、固定会话路由 | A；订阅实际认证 C |
| `ProviderUrls` / `ProviderMessageContent` / `ProviderParameterDiagnostics` | URL 拼接、文本块提取、只输出已知错误码和公开参数位置 | A |
| `RequestBodyMerge` / `CitationFormatter` | 递归合并、数组替换；引用去重、偏移和无效偏移来源列表 | A |
| `ResponsesEphemeralState` / `CodexResponsesRequest` | 同次 run opaque items 保留；稳定 codec 白名单丢弃；纯 Codex 请求约束测试 | A 编码逻辑；登录/认证/账号 UI 为 C |
| E06 协议入口 | Responses 从禁用变为可选；模型选择、测试后获取目录、聊天接入；表单与已保存配置仍独立 | A 代码；检查点 ④ 待测 |
| `ProviderRequest.purpose` | 压缩/重写等非聊天请求清除自定义 body 与工具，关闭 hosted search | A 纯逻辑，实际压缩留 E05b |

## 测试清单与计数

| Eta 原文件 | 移植 | 跳过 | 说明 |
| --- | ---: | ---: | --- |
| OpenAiResponsesProviderTest | 11 | 0 | 保留原事件顺序、终态、工具目录、引用和全部断言；Node HTTP 服务替代 JVM HttpServer |
| CodexResponsesRequestTest | 5 | 0 | 仅编码、解码，不接入订阅登录；API Key 请求不受 Codex 约束 |
| ProviderReasoningTest | 23 | 0 | 保留全部模型别名、努力级别、预算、拒绝条件 |
| ProviderSseReaderTest | 4 | 0 | 拉取端口替代 InputStream；关闭/取消/同一个消费异常对象均保留 |
| ProviderRequestHeadersTest | 7 | 0 | 默认 HTTP 产品标识按品牌改为 Harmony-Agent，其余断言保留；未注册 OAuth 功能 |
| ProviderUrlsTest | 1 | 0 | 7 个 URL 断言全部保留 |
| **合计** | **51** | **0** | Kotlin → TS 行为测试，不等同鸿蒙网络真机测试 |

另 10 项适配回归：Java UUID/MD5 字节等价、SSE 所有切分点、错误脱敏、非聊天目的与持久化白名单、真实客户端与 ephemeral 输入、先保存再 HTTP、停止保存、写入失败/断流不重试、独立表单与目录、协议模型过滤。HTTP 原测试中，服务端等待“首个推理增量已交付”才发送剩余事件；等待客户端 Completed 才关闭连接，排除到 EOF 才输出或才完成的假流式。

## 与 Eta 的差异与待用户确认

1. OkHttp / InputStream 换为 NetworkKit 与可取消分块端口。已有 API 23 门槛、禁止重定向和 2MB 网络响应上限继续保留。`onChunk` 返回 false 表示合法终态；在已获得成功状态码后结束请求，不等 dataEnd。手机上的回调时序仍待测。
2. 按 AGENTS 日志规则，**不展示服务端原始 error.message/type/code**。保留 Eta 的错误分类、context overflow、认证/额度判断和有限参数诊断；返回固定文案，不把服务端回显的正文带入诊断。retryable 只作为分类，不自动重发。
3. 生产仍走 0.9 会话仓库和累计正文/思考回调，保留先落盘、停止留内容、返回前台手动发起等规则；32K 单轮保存上限仍在。协议层的全部分块事件已移植，旧运行桥显示累计 text/thinking，并未宣称新 Store 或完整多块持久化已切换。E07b3 不在本轮。
4. Eta Responses 固定 `stream=true`。Responses 详情中的旧流式开关显示开启且不可关闭，Chat Completions 保留原设置。不发送伪造的非流式 Responses 请求。
5. HTTP User-Agent 使用 ASCII `Harmony-Agent`；界面名称仍“随身助手”。纯 Codex header 编码保留技术标识 `originator=eta`，但真实订阅 HTTP 会拒绝执行，登录入口隐藏。这是协议标识，不是应用文案。
6. OpenCode 的会话头按 Java MD5 UUID v3 字节规则实现，仅用作会话路由 ID。正式聊天使用会话 ID，临时测试客户端生成独立 ID；其他主机没有该头。
7. Responses 已允许连接/目录/聊天；默认思考开关、模型能力编辑与自动压缩仍等待 E05b/E06b。没有猜测未知模型的 reasoning 能力，也没有新增设备工具、hosted 搜索设置或账号入口。
8. 通用 `OpenAiRequestMessages` 的 Chat 分支和历史 tool-call ID 修复在 P1b；当前仅 Responses 分支及其完整依赖。旧 `core/protocols.ts` 导出暂留作历史兼容，正式 Responses 客户端不再调用它。

## 自动验证与接口

- `npm run check`：通过；`npm test`：**663 通过 / 0 失败 / 0 跳过**。
- `npm run check:ets`：**0 error / 92 warning**，8 项既有 `$$this` 误报；没有新增忽略。
- `npm run check:res`：**1,935 资源通过 / 0 warning**。
- **ArkTS 检查通过 / 未生成 HAP**；最终编译以 DevEco 为准。未使用用户 Key，没有真实模型服务或真机通过结论。
- 新增权限：**无**；签名为空。新增鸿蒙 API：**无**，调整既有 `http.requestInStream/dataReceive/dataEnd/HttpRequest.destroy` 生命周期，以及 `util.TextDecoder` 的既有流式解码调用。

## 真机步骤（全部待测）

1. DevEco 编译安装，已有 Chat 服务/Key/会话保持原绑定；Responses 在端点菜单可选，Anthropic/Gemini 仍禁用。
2. 用户配置自己的 OpenAI API 根地址、Key 和模型，选择 Responses；测试连接应完成文本测试并获取目录。测试/获取/取消不能清空输入、保存草稿或切换聊天模型。
3. 选择已保存 Responses 模型，发送请求；检查正文和模型公开返回的 reasoning 增量可见。服务端有正常终态而保持连接时，应及时结束。
4. 生成中停止，当前正文保留；切后台停止；重开 App 不自动重发。断网/缺终态显示未完成，不能把部分回复认作成功。
5. 再用 Chat Completions 做测试连接 → 目录 → 聊天 → 停止，验证原链路在 P1b 前没有回退。
6. 截图比较端点菜单、模型选择器、增量消息、停止状态和诊断；错误记录不得包含完整正文、Key 或服务端回显。保持 ①–④ 检查点为待测。
