# L3 · 聊天列表与侧栏行为（0.9.4）

Eta 基准 `3b1794d70881c3c849b53d7778dd2bfdf0a46cc5`。全部放在 `core/eta/`，当前不接入 UI。

| Eta 来源 | 鸿蒙实现 | 完整移植的原测试 |
| --- | --- | --- |
| AgentChatBody.kt：底部跟随决策、平滑步进、初始定位 | agentChatScrollPolicy.ts | 13 |
| ChatHistoryPaging.kt：预取需求与阅读锚点 | chatHistoryPaging.ts | HistoryScrollDemand 6 |
| ConversationPaneState.kt | conversationPaneState.ts，先 distinct 再过滤 null 的稳定锚点观察器 | 0；平台测试见下 |
| ConversationPaneMotion.kt | conversationPaneMotion.ts | 4 |
| AgentChatBody.kt：toTimelineEntries | agentChatTimelineEntries.ts，复用未变实例，分页前插保留 key，拆组不重复 key | 4 |
| AgentWorkSteps.kt | agentWorkSteps.ts，连续同名工具分组、摘要、结果行 | 9 |
| AgentChatBody.kt：resolveFinalResultMessageIds | agentChatFinalResult.ts，运行中只保留已结束轮次的结果 | 7 |
| ChatConversationKey.kt | chatConversationKey.ts，会话状态边界 | 3 |

## 原测试移植与跳过

**46 项原测试完整移植；3 项平台测试未完整移植；另写 3 项对应逻辑测试，不计入 46。** 映射和逐项原因见 [eta-l3-tests.json](eta-l3-tests.json)。

| 未完整移植的原测试 | 原因与已覆盖部分 |
| --- | --- |
| ChatHistoryPagingTest.actualUpwardScrollLoadsOnePageAndPreservesTheVisibleMessageOffset | 原测试用 Robolectric Activity、Compose LazyColumn 实际测量。纯 TS 验证一次上翻只请求一页、前插恢复相同消息 key 和偏移参数；不声称证明真实像素不跳动 |
| ConversationPaneStateTest.interruptedOpeningResynchronizesClosedAndCanOpenAgain | 原测试依赖 Compose 锚点拖拽、协程取消、帧时钟。纯 TS 验证运动时先观察 null、拖拽结束回原锚点仍重新同步、之后可再次打开；未模拟真实动画取消 |
| ConversationPaneStateTest.interruptedClosingResynchronizesOpenAndCanCloseAgain | 同上，覆盖关闭被中断后同步回打开状态，再次关闭的状态序列 |

Node 测试结果 **298 项通过、0 失败、0 跳过**；这里的“0 跳过”仅指实际运行的 Node 用例，不抹去上述 3 项未完整迁移的平台测试。

## 检查与范围

`npm run check`、`npm test`、`npm run check:ets`、`npm run check:res` 全部通过。ArkTS 0 错误，29 项既有警告；restool 1,833 项资源通过。**ArkTS 检查通过 / 未生成 HAP**。纯 TS 未接入生产入口，TypeScript 与翻译单测覆盖逻辑；平台渲染仍待测。没有新增权限、鸿蒙 API 或设备执行入口。

## 与 Eta 的差异 / 待用户确认

- Compose snapshotFlow、LaunchedEffect、NestedScrollConnection 改为显式的 viewport / scroll / configuration 输入与回调接口；按源代码的时机保留需求消费、锚点恢复和释放历史页的决定。默认在 E04b 用 ArkUI 列表事件驱动；另一选项是把这些规则直接写进页面，不采用后者以保留可测性。
- 侧栏观察器只提供稳定锚点同步，动画与手势由 E03 实现；不把模拟状态序列当作原生动画测试。
- 工具摘要里的“失败 / 完成 / code=”保留为 Runtime 协议格式解析常量，并非新增页面文案。
- 没有改动 Eta 的滚动、分组或最终结果规则。

## 真机（待测）

- 检查点 ① / E03：侧栏打开过程中反向拖回、关闭过程中反向拖开，松手后按钮与可见状态一致，下一次仍能操作。
- 检查点 ② / E04b：流式向下跟随，上翻后不拉回；历史前插一页时首条可见消息和偏移不变；失败只显式重试；切换会话不复用上一会话的滚动状态。

以上没有真机结果，当前无需等待用户，按夜间队列进入 L2。
