# E07b · 会话存储对照与迁移决定（草稿阻塞）

基于 E07a / PR #21 / `024f2fd`，分支 `harmony/e07b-conversation-store`。本分支只形成审查材料，不更换用户数据格式，不宣称 E07 完成。

## 阻塞事实

E07 的任务卡写“AgentConversationStoreTest 等其余相关单测在这里移植”，没有明确是否在 M1 UI 移植期间同时替换 0.9 的持久化模型。逐项读完 Eta Store、Persistence 和原测试后，发现不只是平台 API 翻译：

| 固定 Eta 原测试 / 源码 | 当前鸿蒙实现 | 影响 |
| --- | --- | --- |
| initialLoadKeepsOnlyTheSelectedMessageWindowAndEarlierPagesRemainComplete：60 个会话，只载入选中会话最后 128 条，其他为有界预览 | `CHAT_LIMITS.conversations=50`；整个 0.9 JSON 快照先读入内存，再做页面窗口 | 60 会话原测试无法照抄通过；UI 窗口通过不代表数据库按页读取 |
| savePreservesCompleteContextAndDisplayedMessages：单用户消息 120,000 字，20 条各 20,000 字完整上下文，保存后等值 | 单请求 8000、单回复 32000、全会话文档 2,000,000 字符；模型上下文另限 48000/20 轮 | 不能为了“通过”缩短测试 fixture 或默默截断数据；仅提高常量仍不解决数据库布局 |
| 保存 history/journal 检查点与 displayed messages，各自完整 | 当前主数据是 user/reply 轮次；工具和状态字段不等于 Eta 完整消息行 | 需要新的持久化 DTO、索引和版本迁移 |
| repeatedSavePreservesRoleBindingRevisionsPendingRewriteAndOriginalJournal、unloadedCharacter、characterGreeting | L4 角色 reducer 已移植，但普通 0.9 会话库没有角色卡/候选/重写元数据 | 该选择会影响 E08 角色及运行恢复，不能把角色数据丢掉后声称原测试通过 |
| Room 事务后才推进增量保存基线、未对齐 sort_index 首次重排 | 当前是 RDB 文档表中的原子 JSON 快照 | 需要鸿蒙 RDB 事务端口、分块文本恢复、增量保存与失败回滚测试 |

依据用户规则：**“遇到会影响后续的决定时，PR 保持草稿、写明阻塞原因，跳到不依赖它的下一项”**，以及 `AGENTS.md` / `ETA_PORT.md` 对较大差异不得自行改设计的要求，E07b 保留草稿。不是等待审批运行普通命令；是需要确认 M1 是否包含数据格式切换及其验收范围。未以虚假三次重试代替设计决定。E05/E06 不依赖新的会话数据模型，从 E07a 继续。

## 建议选择 A：完整迁移 Eta 的存储模型

1. 保留原 0.9 快照的原文副本与版本号；迁移采用独立版本标记，成功前不覆盖旧记录。
2. 在 core/eta 定义纯 TypeScript ConversationDao / 事务端口；按 Eta 的 conversation metadata、message rows、history/journal checkpoint、selection 和 chunked text 划分。
3. 用鸿蒙 RDB 实现事务端口。事务成功才更新 AgentConversationPersistence 保存基线；失败不推进基线、不清空内存内容。
4. 逐会话映射旧稳定 ID、标题、模型绑定、用户请求、正文、思考、状态和时间；旧设备步骤保持历史记录，不注册为可执行工具，不重放。
5. 对旧 `running` 仍执行中断恢复。未提交队列不转为待自动执行任务；草稿留原草稿库，API Key 继续只在 ASSET。
6. 首次迁移验证会话数、消息数、逐段正文哈希和选中 ID；缺失/损坏记录阻止切换并提供诊断，不静默删除。
7. 新读路径：元数据索引 + 256 字预览；选中会话按 128 条窗口读取；搜索跨分块边界按字面匹配；导出按页流式读完整历史。
8. 新写路径：保留稳定消息 ID；按可对齐前缀更新后缀；未对齐 sort_index 先重写整段；offscreen 角色修订按 ID 更新。
9. 原 17 项 Store 测试、Persistence 对应测试逐条翻译；增加迁移幂等、半途写失败、重启恢复、旧数据损坏、长文本完整性测试。
10. 在独立真机检查点验证：装旧版本写若干会话 → 升级 → 核对历史/模型/导出 → 注入迁移失败 → 原文件仍完整。确认前保留回退读取入口，不能回退后重放任务。
11. E08 角色、备份及后续运行恢复统一使用该存储端口；不维护两个可写的会话真相源。

建议拆成 E07b1 逻辑/端口与原测试、E07b2 RDB 适配和旧数据迁移、E07b3 控制器切换与真机迁移验收。每笔四项检查，HAP/真机结论单列。

## 备选 B：M1 暂时保留 0.9 存储约束

继续发布文本聊天 UI，同时在 ROADMAP / ETA_PORT 明确降低该阶段数据规模和历史模型的对齐范围；将完整 Store/迁移单列后续任务。角色等需要完整新模型的页面继续受 B 类限制。不能将这条路线描述为 E07 原测试全部通过。

**待用户确认：选择 A（建议）或 B。夜间不替用户改变验收标准，当前不执行数据切换。**

## 17 项原 Store 测试逐项账本

E07a 503 项测试中的 9 项 `logic:` 用例只验证适配边界。以下“部分”不计为完整原测试；Room/鸿蒙 RDB 集成均未验收。

| 原用例 | 当前覆盖 | 剩余 |
| --- | --- | --- |
| repeatedSavePreservesRoleBindingRevisionsPendingRewriteAndOriginalJournal | L4 reducer 已测 | Store 往返与原 journal，待新 schema |
| saveAndLoadPreservesConversations | 0.9 文本轮次恢复已测 | 完整 Eta 消息 DTO / usage / effort 往返 |
| saveAndLoadPreservesSemanticSystemNoticesWithoutTranslatedContent | E04b 状态投影已测 | 持久化 semantic code 原测试 |
| conversationModelBindingRoundTripsAndChangeAloneIsPersisted | 部分：9 项 logic 之一 | Eta metadata 与增量基线 |
| unknownStoredEffortFallsBackToDefault | 未移植 | 新 schema / reasoning effort 默认 |
| initialLoadKeepsOnlyTheSelectedMessageWindowAndEarlierPagesRemainComplete | 部分：128 条页面窗口 | 60 会话 + 数据库有界读取 |
| unopenedConversationOnlyReadsItsBoundedPreview | 部分：256 字逻辑投影 | 不能读取完整大正文/角色卡的 DAO 断言 |
| unloadedCharacterKeepsItsIdentityWithoutRetainingTheCard | 未接存储 | 角色摘要端口与卸载 |
| searchFindsUnloadedChunkBoundariesAndTreatsSqlWildcardsLiterally | 部分：字面匹配 | 分块数据库查询 |
| searchRetainsUnicodeCaseFoldingAndQueriesSpanningManyChunks | 部分：Unicode / 60000 字查询 | 分块读与真实数据存取 |
| searchIncludesMatchingLocalizedNoticeCodesFromEarlierMessages | 部分：notice code 匹配 | 数据库旧消息检索 |
| savePreservesCompleteContextAndDisplayedMessages | 未移植 | 大消息/完整 checkpoint，不能缩短 fixture |
| loadIgnoresLegacyHistoryColumnAndFallsBackToMessageRows | 未移植 | Eta 历史 schema 与 fallback |
| loadKeepsDatabaseEmptyUntilFirstMessageIsSent | 部分：0.9 空会话策略 | 新 Store 的原始空库 |
| characterGreetingIsLocalAndOrdinaryNewConversationReturnsToEta | 未接角色入口 | E08 与新 Store 联调 |
| creatingConversationKeepsEmptyStateOutOfHistoryAndDatabase | 部分：0.9 空会话策略 | Eta Store/AppState 整体链路 |
| savingEmptySnapshotClearsPreviouslyPersistedConversations | 部分：0.9 删除后空集合 | Eta snapshot 事务路径 |

本草稿新增完整移植测试 **0**；**17 项尚未完整移植/验收**，其中 9 项有逻辑替代，原因如上。没有新增权限、接口、签名。新增生产代码 **0**；仍为 E07a / 0.9.16。草稿文档检查沿用四项完整检查：503 测试、0 ArkTS error、1,864 资源；**未生成 HAP**。本分支不合并进后续 UI 分支。
