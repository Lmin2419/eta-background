# L1 · 对话逻辑（0.9.3）

固定 Eta 基准 `3b1794d70881c3c849b53d7778dd2bfdf0a46cc5`。纯 TypeScript 移植，不改变页面、不发网络请求、不初始化运行任务。

## 来源与实现

| Eta 源文件（ui/app/） | 鸿蒙 core/eta/ | 保留行为 | 上游单测 |
| --- | --- | --- | --- |
| AgentMessageQueueReducer.kt | agentMessageQueueReducer.ts | FIFO、插话置顶、撤回合并草稿和附件、停止后保留队列 | 5 |
| AgentConversationRevisionReducer.kt | agentConversationRevisionReducer.ts | 按用户轮次裁剪消息与历史、压缩尾部对齐、优先原始 journal | 6 |
| AgentRunEventCoalescer.kt | agentRunEventCoalescer.ts | 仅合并同 run / round / kind / index 的相邻增量 | 3 |
| AgentRuntimeHistoryReducer.kt | agentRuntimeHistoryReducer.ts | run 幂等提交、snapshot 后续批次与未消费补充、保留 journal | 5 |
| AssistantConversationSeed.kt | assistantConversationSeed.ts | 提示词同时进入展示、history、journal；恢复不重复追加 | 2 |
| ConversationTimeLabels.kt | conversationTimeLabels.ts | 今天/昨天/近六日/同年/跨年；按日历日期而非 24 小时分组 | 6 |
| ConversationMarkdownExporter.kt | conversationMarkdownExporter.ts | 消息顺序、思考折叠、工具/通知引用、文件引用、图片数量、文件名清理 | 9 |

`model/conversation.ts` 仅移植本任务需要的状态与消息 DTO；`model/agentFileReferencePrompt.ts` 隔离导出器依赖的路径提示 format / parse，不读取文件、不启用终端工具。

## 单测与验证

共 **36 项 Eta 单测逐条翻译、0 跳过**；名称、路径与逐项映射见 [eta-l1-tests.json](eta-l1-tests.json)。保留英文测试名以便和 Kotlin 对照。原有 213 项保留，共 **249 项通过，0 失败**。

四项检查：`npm run check`、`npm test`、`npm run check:ets`、`npm run check:res` 通过。ArkTS 0 错误，原有 29 警告；restool 1,833 项资源通过，无警告。**ArkTS 检查通过 / 未生成 HAP**。本任务纯 TS 尚未被 UI 导入，逻辑由 TypeScript 与 36 项源测试覆盖；现有 ArkTS 入口及资源检查无回归。

第一轮测试发现 Node Intl 默认短日期并不等于 Java 的显式 `M月d日` 模式，已修正测试的日期适配器按传入模式格式化；不修改 Eta 预期值。生产逻辑不依赖 Node Intl。

## 与 Eta 的差异和待用户确认

- Java Calendar / DateFormat 通过 `ConversationDateFormat` 接口隔离；测试由 Node Intl 提供本地日历日期，E07 接入鸿蒙国际化 API。选择：默认保留可注入适配器；另选为把平台依赖写进纯逻辑。默认方案保持源逻辑可测且避免 Java / Node API 进入鸿蒙代码。
- Kotlin Pair 改为命名的 `{ state, item }`；sealed message 改为带 kind 的最小 DTO。转换规则与默认值不变，不迁移角色/模型运行依赖。
- 根据 ETA_PORT，导出文件名前缀 `Eta-` 改为 `随身助手-`，3 项文件名测试只调整此前缀。其余断言保留原预期。
- Labels 由调用方注入，尚未接入页面；日期文案同样由调用方提供。无新增权限或鸿蒙接口。

## 真机（待测）

L1 无独立真机检查点。E04c 接入时验证停止后队列不继续执行、插话优先、编辑后消息与历史一致；E07 接入时验证时间分组和导出。当前没有声明真机通过。
