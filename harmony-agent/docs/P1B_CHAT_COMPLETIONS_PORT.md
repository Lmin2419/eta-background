# P1b：Chat Completions 对齐（0.9.22）

固定来源 `app/src/main/kotlin/io/github/mangi/eta/agent/model/`，Eta `3b1794d`，保留 Required Notice。正式 `OpenAIClient` 的 Chat 路径改用 Eta 请求构造、消息投影、SSE 事件和工具调用 ID 逻辑。无新权限、无新鸿蒙 API，旧存储未切换。

## 逐项对照

| Eta 来源 / 行为 | 鸿蒙实现 | 状态 |
| --- | --- | --- |
| OpenAiChatCompletionsProvider 请求、推理、用量尾帧、结束条件 | core/eta/protocol/openAiChatCompletionsProvider.ts；Foundation 桥接 | A（代码/自动验证，真机待测） |
| OpenAiRequestMessages 系统消息合并、内部字段剔除 | openAiRequestMessages.ts；先配对修复再合并 | A |
| ChatToolCallHistory 只修复完整且不歧义的相邻批次 | chatToolCallHistory.ts；深拷贝，不改源历史 | A |
| 原历史 / 请求投影 / 本轮调用 ID 去重 | 完整保留已有 ID；缺失/碰撞生成唯一 ID；事件和结果一致 | A |
| ProviderReasoning / Headers / URLs / SSE / Failure | 复用 P1a；OpenRouter 地址推断、禁用旧 usage 选项 | A |
| 非聊天 purpose | 清除自定义请求体及工具，防止压缩请求继承工具 | A |
| SSE 完成后不继续读取 | `[DONE]` 终止回调；允许 finish_reason 后的用量尾帧 | A |
| 认证订阅、OAuth 重授权 | 无入口，不发订阅请求 | C |

## 移植测试账本

| Eta 原测试文件 | 原测试数 | 完整逻辑移植 | 跳过 |
| --- | ---: | ---: | ---: |
| OpenAiChatCompletionsProviderTest | 14 | 14 | 0 |
| OpenAiChatToolCallIdsTest | 7 | 7 | 0 |
| ChatToolCallHistoryTest（依赖，任务卡外补齐） | 9 | 9 | 0 |
| 合计 | 30 | 30 | 0 |

原 HTTP fixture 用 Node 真 HTTP/SSE 服务替代 JVM HttpServer；保留分片、早到思考、用量尾帧、闩锁、不完整结束、错误、跨轮 ID 和全部 20 组畸形批次。错误用例按 AGENTS 的隐私要求改为校验错误分类和不回显服务正文。另增 5 项接入回归：全部 UTF-16 切分点、非聊天 purpose、DONE 后停止读、非流式/JSON 兼容、凭据换行及错误正文保护。既有 M0 测试验证先持久化、停止留内容、不自动重发和诊断。

## 与 Eta 的差异 / 待用户确认

1. 保留 0.9 的显式非流式开关，以及请求流式但服务返回普通 JSON 的单次请求兼容；都通过同一 Eta 消息事件解析，不重试 HTTP，不更改用户保存的偏好。Eta 原类始终请求 SSE。
2. 正式壳层当前没有注册工具，适配器在工具列表为空时省略 `tools` 和 `tool_choice`；纯 Eta 请求构造器保留原行为。M0 的文本模式约束不变。
3. 继续使用 P1a 的固定错误文案和分类，不把服务商返回的消息/头/正文放入 UI、诊断；HTTP 产品标识为 Harmony-Agent。
4. 缺失工具 ID 使用每响应随机前缀加单调序号；不依赖 Android UUID。历史修复 ID 的规则和序号与 Eta 相同。ID 仅用于配对，不作为认证材料。
5. 当前壳层仍使用旧版累积文本回调、存储容量限制及停止策略；未恢复设备工具、HDC、后台服务。完整新存储切换留 E07b3。

上述兼容点不改变 Eta 的默认流式路径，保留为待用户确认的小决定。无页面重新设计。

## 自动验证

- `npm run check`：通过。
- `npm test`：698 项通过 / 0 失败 / 0 跳过。
- `npm run check:ets`：0 error，92 warning，8 项既有双向绑定误报由原脚本忽略。
- `npm run check:res`：1,935 项资源通过。
- **ArkTS 检查通过 / 未生成 HAP**。没有调用真实模型 API；不代表真机通过。

## 真机步骤（均待测）

1. DevEco 构建 0.9.22，保留现有签名配置；已有会话/服务仍在。
2. 使用用户自己的 OpenAI API Key，新建或编辑 Chat Completions 服务，测试连接 → 获取模型；确认当前草稿和聊天选中模型未被测试改写。
3. 开启流式，发送有较长回答的问题，观察思考/正文增量；生成中停止，切换会话后返回，确认已收到内容保留，不自动重发。
4. 开启/关闭流式各发送一次；网络中断时显示失败，不把半截工具调用作为成功结果。
5. 改用 Responses 重复一轮，确认 P1a 仍工作。所有结果以用户设备和服务商实际响应为准。
