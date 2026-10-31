# E05b：默认思考与自动压缩（0.9.23）

Eta 固定来源 `3b1794d`；保留 Required Notice。设置中两行现已接入独立、串行且持久化的偏好。正式聊天在请求开始时取快照；设置变化不会改写已经发出的请求。默认值均为 true，与 Prefs 一致。

## 逐项对照

| Eta 来源 | 鸿蒙实现 | 状态 |
| --- | --- | --- |
| SettingsScreen / Prefs 两个开关、顺序、文案与默认值 | 原 ArkUI 设置行启用，控制器投影 runtimePreferences | A，真机待测 |
| ReasoningCapabilityResolver | 官方精确 ID、系列、远端能力、用户覆盖优先级与安全默认值 | A |
| RuntimeConfigRepository / AgentAppState | 发送时解析所选模型的能力、推理档位、默认思考与已知窗口 | A；完整模型能力编辑在 E06b |
| AgentRuntimeRequestConfigResolver | 本地连接身份、入口工具开关、推理归一、归档元数据 | 纯逻辑 A；外部语音/OAuth 入口 C，未启用 |
| AgentContextSession | 真实输入 token 达已知窗口 85% 才自动压缩；先保存候选再换上下文 | A |
| AgentContextCompactor / Summarizer | 一次摘要请求、完整工具批次边界、保留最新问题、敏感内容/图片/opaque 剔除、上限 12,000 字 | A |
| SignedToolContext | 尚待回传的 Anthropic/Gemini 签名上下文禁止压缩 | A 逻辑；协议接入在 P1c/P1d |
| AgentContextSnapshot | 独立检查点、完整计数、保留增量 transcript | A 逻辑；0.9 适配见差异 |
| AgentLoop 文本模式 | 当前正式纯聊天路径使用 TextContextRunner；失败不重试、不补执行工具 | A |

## 存储与失败边界

没有执行 E07b3。`assistant.db` 原会话仍是正式存储；新 RDB 没有启动。旧会话只增加可选 `modelCheckpoint`，原轮次/正文/导出内容全部保留。检查点绑定来源轮次 ID 与当前运行 ID；只有绑定的轮次完整成功后才可用于下一轮。修订、删除、停止或来源不匹配时回到原历史。较早历史已按 0.9 预算省略的数量随检查点保留，不声称摘要涵盖未发送的历史。

摘要截断、太长、工具调用、签名锁定、没有缩小或保存失败都不能替换原上下文。最终回答已经成功时，摘要失败不把该回答改判为模型失败。取消仍停止整个当前运行，并保留已经收到的答案，不自动续发。检查点只存稳定文本 DTO，不保存模型的 opaque 状态、屏幕上下文或推理正文；保存前按现有规则隐去凭据。

## 与 Eta 的差异 / 待用户确认

- 在 E07b3 之前用 0.9 可选检查点字段承接“先保存后替换”，保留 20 轮/48,000 字符的旧历史入口预算和当前输出限制。新 Eta 存储、完整长上下文容量尚未切换。迁移原始证据仍包含整个旧快照；E07b3 切换时需要处理此可选检查点。
- 最终摘要失败时，原始会话足以重建后续上下文；0.9 没有 Eta 的最终 outbox，不另存失败候选。不会把内存 fallback 写成“压缩成功”。
- 正式入口仅文本聊天，不注册工具/角色/语音/OAuth；相应逻辑通过显式 Provider/持久化端口验证。未知模型不猜测思考能力；可禁用性与强制思考严格按来源能力校验。E06b 将补齐内置模型能力和编辑入口。
- 修复诊断 HTTP 包装器对流式“终止读取”返回值和 HTTP 状态的透传，保证 P1a/P1b 的终态行为在正式入口也生效。
- UI 保持原行的图标、颜色、尺寸、文案，未新增视觉设计。

## 测试账本

| 原测试 | 完整逻辑移植 | 暂缓 | 原因 |
| --- | ---: | ---: | --- |
| ReasoningCapabilityResolverTest | 12 | 0 | 保留完整模型集合 |
| AgentRuntimeRequestConfigResolverTest | 7 | 0 | JSON wire DTO 替代 Android 运行入口；无真实 OAuth/语音调用 |
| AgentContextCompactionTest | 16 | 0 | Provider 回调替代平台客户端；原 160,000 字符历史、68,000 字符回答等 fixture 未缩短 |
| AgentCompactionProviderTest | 0 | 3 | 两项跨三协议断言和一项 Anthropic 角色深度断言依赖队列后续 P1c；届时补齐，不伪装为已移植 |
| 合计 | 35 | 3 | 3 项延期，不是自动测试失败 |

另加 8 项集成验证：偏好保存/失败/恢复、实际 Responses 推理参数、独立检查点及重启/下一轮、保存失败、取消摘要、修订失效、诊断终态透传、85% 门限/关闭开关。原 M0 和协议回归继续执行。

四项检查通过：`npm run check`；`npm test` **741 通过 / 0 失败 / 0 跳过**；`npm run check:ets` **0 error / 92 warning / 8 既有误报忽略**；`npm run check:res` **1,935 资源通过**。

**ArkTS 检查通过 / 未生成 HAP**。新增权限：无。新增鸿蒙 API：无（沿用原 RDB 文档存储）。没有调用用户模型，真机均待测。

## 真机步骤（待测）

1. 升级后开关两项默认启用；修改、重启，确认值保留，当前服务及会话不变。
2. 用有明确思考能力的 Responses 模型测试思考开/关。强制思考模型不能选择 Off，应按 Eta 显示能力错误；未知模型不发送猜测参数。
3. 配置已知上下文窗口，累积对话至服务报告输入用量达到 85%；出现整理状态后原历史仍可查看/导出，下一轮可使用摘要。
4. 关闭自动压缩重复同样请求，不新增摘要 HTTP；生成过程中改开关，只影响下一次运行。
5. 整理期间断网、停止或制造存储失败：原历史保留，不自动重发；已完整回答在普通摘要失败后仍标成功。
6. 整理后修订较早轮次，确认不再使用包含被修订内容的旧检查点；记录 DevEco 编译结果及截图。

## P1c 补充（0.9.25）

此前延期的 AgentCompactionProviderTest 3 项已在 `tests/eta/agentCompactionProvider.test.ts` 完成并通过，覆盖两种目的 × Chat/Responses/Anthropic 以及三种角色深度投影。使用原源码生成的完整 57 工具 fixture，未缩减目录。E05b 原测试累计 38 项，无剩余跨协议跳过项；平台真机状态仍为待测。详见 [P1c](P1C_ANTHROPIC_PORT.md)。
