# E07b1 · 会话存储逻辑与端口（0.9.19）

base：`harmony/round3-plan@9d4fc17`；分支 `harmony/e07b1-store-ports`。依据用户第三轮已确认的方案 A。固定 Eta 源 `3b1794d`，只改 harmony-agent/。**正式入口和旧数据格式未切换**，E07b3 需真机检查点 ①–④ 通过。

## 逐项对照

| Eta 来源 / 行为 | 本次移植 | 状态 |
| --- | --- | --- |
| ConversationEntities / ConversationDao | 会话元数据、消息行、checkpoint、selection 与分块的纯 TS 事务端口 | A 逻辑；RDB 留 E07b2 |
| ChunkedTextDao / ChunkedTextInputStream | 16,384 UTF-16 字符一块，避开代理对，引用长度/顺序/缺块检查；单块读取 | A |
| AgentConversationStore.load | 索引只含 256 字预览；仅选中会话读取最近 128 条，其他会话不恢复正文/修订 | A |
| Store.loadConversation / earlier / all | 完整 history/journal、向前分页、按页完整导出；不读取 legacy history 大列；未知 effort 回退 | A |
| Store.saveChanges | 元数据与 checkpoint 变化检测、对齐前缀、只重写变化后缀；离屏角色修订按 ID 更新；缺目标回滚 | A |
| AgentConversationPersistence | 串行保存；事务提交后才推进基线；adopt/forget；流式占位留下的排序空洞首次重排 | A |
| ConversationSearchRepository | 跨块字面匹配、Unicode 大小写折叠、工具摘要和本地化语义通知；不把完整正文带回侧栏 | A |
| loadCharacterSummary | 流式跳过大角色卡/用户设定，只保留名称与头像；卸载释放正文与修订 | A；JsonReader 改 TS 有界扫描器 |
| AgentAppState 会话加载 | 选择版本、翻页版本、失败重试、预取去重、释放旧页、保存后卸载非活动会话 | A 逻辑；新 session 不接正式控制器 |
| AgentAppState 结果与删除 | 复用 L1/L4 reducer 和 RunStore；结果按会话归属；等待保存后删除；迟到结果不复活；队列保存后委托执行端口 | A 逻辑；无后台服务/通知执行声明 |
| 会话内容 codec、角色问候宏 | durable checkpoint 子集与有界递归宏展开；卡片解码、当前时间等由调用方端口提供 | 本任务所需依赖；不是完整 E08 角色功能 |

## 单测与检查

**44 项 Eta 原测试逻辑移植、0 项跳过**：AgentConversationStoreTest 17、AgentConversationPersistenceTest 12、AgentAppStateConversationLoadingTest 15。原输入规模保留：60 会话、600 条分页、120,000 字展示正文、20 × 20,000 字历史、2,500,000 字被忽略旧列、60,000 字跨块搜索、长角色卡和修订内容。测试没有为了通过而缩短 fixture。

另有 **4 项新边界测试**：代理对/字面引用前缀、损坏分块/尾随 JSON、事务最终提交失败重试、调用方可变消息不污染保存基线。累计 **588 测试通过 / 0 失败 / runner skip 0**。

`npm run check`、`npm test`、`npm run check:ets`、`npm run check:res` 全部通过。ArkTS **0 error / 77 warning**，8 条既有 $$ 误报仍按原规则处理；资源 **1,935 / 0 warning**。**ArkTS 检查通过 / 未生成 HAP**。

## 平台替代与差异

- 本任务卡明确要求内存 DAO：Robolectric/Room 改为原子内存事务；SQL 审计/拒绝插入触发器改为提交可回滚的变更日志/故障注入。44 项指相同场景的功能断言通过，**不是 Android 测试框架或鸿蒙 RDB 实测通过**。
- Android 通知 checkpoint、外部归档、队列执行是显式注入端口，loading 测试用固定数据驱动。归档会话 ID fixture 沿用 `eta_voice:game` 的 SHA-256 前 12 字节算法；未接通知栏、服务、HDC、模型工具。
- CharacterCardCodec/完整角色入口仍归 E08；此处由宏值端口提供角色名和用户信息，复用原有角色 reducer。新增可选 DTO 字段兼容 L1–L4，没有改现有聊天执行。
- Android JsonReader 改为流式 TS 扫描；无效 JSON/缺块拒绝读取。输入嵌套深度 256 和字面量 128 字符设防属于适配器边界，不限正常正文/角色卡总长。
- 搜索 cursor 改为有界 128 行分页，同一读事务内逐块匹配；不会把 `%`/`_` 当 SQL 通配符。
- 原 Kotlin 值对象在 TS 端口进入保存基线前深复制，防止调用方原地编辑造成“看起来没变化”而漏写。
- **不删除旧数据**：新 Store 的删除只针对未来新库的 DAO；当前未配置真实数据库、没有从旧库读取或写入，也没有切换入口。E07b2 将使用独立数据库与迁移标记，旧快照原样保留。

## 新接口、权限与待用户确认

纯 TS，本次无新鸿蒙接口、无新增权限。signingConfigs 为空。Required Notice: Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0。

小决定默认：可变 JS 对象复制后保存；读取流保留一个分块；运行恢复用显式端口。均为保持原行为的实现选择，不修改已确认的方案 A。无新阻塞，无需重复确认。

## 真机步骤 · 待测

1. 在累计分支编译安装，执行此前 ①–④；当前存储行为应仍由旧入口承担。
2. 本任务没有启用新存储开关，不通过界面冒充新库验证。新库故障/迁移步骤留 E07b2 记录，正式切换留 E07b3。
3. 若出现编译报错或页面问题，按第三轮规则优先修复最早出问题的分支，再同步下游。

## 原用例账本

### AgentConversationStoreTest（17）

- `repeatedSavePreservesRoleBindingRevisionsPendingRewriteAndOriginalJournal`：内存端口通过。
- `saveAndLoadPreservesConversations`：内存端口通过。
- `saveAndLoadPreservesSemanticSystemNoticesWithoutTranslatedContent`：内存端口通过。
- `conversationModelBindingRoundTripsAndChangeAloneIsPersisted`：内存端口通过。
- `unknownStoredEffortFallsBackToDefault`：内存端口通过。
- `initialLoadKeepsOnlyTheSelectedMessageWindowAndEarlierPagesRemainComplete`：内存端口通过。
- `unopenedConversationOnlyReadsItsBoundedPreview`：内存端口通过。
- `unloadedCharacterKeepsItsIdentityWithoutRetainingTheCard`：内存端口通过。
- `searchFindsUnloadedChunkBoundariesAndTreatsSqlWildcardsLiterally`：内存端口通过。
- `searchRetainsUnicodeCaseFoldingAndQueriesSpanningManyChunks`：内存端口通过。
- `searchIncludesMatchingLocalizedNoticeCodesFromEarlierMessages`：内存端口通过。
- `savePreservesCompleteContextAndDisplayedMessages`：内存端口通过。
- `loadIgnoresLegacyHistoryColumnAndFallsBackToMessageRows`：内存端口通过。
- `loadKeepsDatabaseEmptyUntilFirstMessageIsSent`：内存端口通过。
- `characterGreetingIsLocalAndOrdinaryNewConversationReturnsToEta`：内存端口通过。
- `creatingConversationKeepsEmptyStateOutOfHistoryAndDatabase`：内存端口通过。
- `savingEmptySnapshotClearsPreviouslyPersistedConversations`：内存端口通过。

### AgentConversationPersistenceTest（12）

- `searchSuggestionsStayInLoadedConversationAndNeverReachMessageStorage`：内存端口通过。
- `appendingOnlyWritesTheChangedConversationAndNewMessages`：内存端口通过。
- `selectionAndDraftChangesDoNotRewriteStoredContent`：内存端口通过。
- `contextCompressionDoesNotRewriteTheUnchangedJournalOrMessages`：内存端口通过。
- `aFailedTransactionRollsBackAndTheSameWriterCanRetry`：内存端口通过。
- `deletingAConversationCleansItsChunksAndPreservesOthers`：内存端口通过。
- `restartAfterSavingAroundAStreamingPlaceholderKeepsEveryMessage`：内存端口通过。
- `appendingAndDeletingWithinATailWindowNeverRewritesTheUnloadedPrefix`：内存端口通过。
- `renamingAnUnloadedConversationPreservesItsMessagesAndContext`：内存端口通过。
- `adoptingAnEarlierPageDoesNotRewriteItsUnchangedRows`：内存端口通过。
- `offscreenRoleplayRevisionsUpdateStoredRowsWithoutLoadingTheirMessageWindow`：内存端口通过。
- `aNewOffscreenRevisionWithAMissingTargetRollsBackWithoutRejectingOldMetadata`：内存端口通过。

### AgentAppStateConversationLoadingTest（15）

- `notificationReplyPromptIsVisibleFromCheckpointBeforeAResultExists`：内存端口通过。
- `notificationContinuationImportsTheAssistantArchiveBeforeSelectingItsChat`：内存端口通过。
- `fastConversationSwitchesKeepTheLastSelectionAndRestoreEveryBody`：内存端口通过。
- `deletingTheSelectedConversationLoadsTheNextConversation`：内存端口通过。
- `deletingAnUnloadedConversationKeepsOtherStoredConversationsIntact`：内存端口通过。
- `loadingAnEarlierPageThenDeletingATurnPreservesTheEarlierPrefix`：内存端口通过。
- `searchAndExportIncludeMessagesOutsideTheLoadedWindow`：内存端口通过。
- `repeatedPrefetchRequestsLoadOnlyOnePage`：内存端口通过。
- `switchingConversationCancelsAnOutstandingHistoryRead`：内存端口通过。
- `failedPageKeepsTheCurrentWindowAndCanBeRetried`：内存端口通过。
- `returningToTheLatestMessagesReleasesOldPagesWithoutDeletingHistory`：内存端口通过。
- `scrollingBackToHistoryCancelsPageReleaseWithoutCancellingTheNextRead`：内存端口通过。
- `concurrentResultsAndStopRemainBoundToTheirConversations`：内存端口通过。
- `deletingAnActiveConversationWaitsForItsResultAndKeepsOtherRuns`：内存端口通过。
- `backgroundQueueContinuesWithItsCompletedHistoryWhileAnotherConversationRuns`：内存端口通过。

