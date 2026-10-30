# L4a：运行消息投影（0.9.7）

基于 L2b，来源 Eta `3b1794d` 的 `ui/app/AgentRunMessageProjector.kt` 及最小运行事件 DTO。Copyright © 2026 蛮吉 (Mangi-11).，PolyForm Noncommercial 1.0.0。

L4 主模块、结果恢复、角色修订依赖和测试合计约 2,600 行手写代码，拆为两个 PR；本部分只迁移消息投影。已有 L1 历史 reducer 继续复用，不整体移植 Android Runtime/Binder。

| Eta 元素 | 鸿蒙实现 | 状态 |
| --- | --- | --- |
| AssistantBlockStart/Delta/End | 按 run/round/index 保留正文、思考和工具的事件顺序 | A，纯逻辑 |
| Usage / SearchSuggestions | 用量不重启流式；精确绑定块、按轮限额、重复事件返回原数组 | A，纯逻辑 |
| Tool / HostedTool / SubagentProgress | 精确工具 ID、显式成功/旧事件回退、未知中断状态、结果摘要上限 | A，纯逻辑 |
| ModelRetry / applyResult | 重试失败片段保留，最终结果只补全最后一次重试之后的回答 | A，纯逻辑 |
| resetForReplay | 仅清当前 run 轨迹及明确要求重放的补充输入，保留旧 handoff 独有输入 | A，纯逻辑 |
| ContextCompaction | 同一标记去重，成功保留 token 信息，失败/中断更新原标记 | A，纯逻辑 |
| AgentPendingResultRecovery / RoleplayConversationReducer | L4b | 本 PR 尚未交付 |

## 验证

`AgentRunMessageProjectorTest.kt` 的 **18 项原测试完整移植、0 跳过**，见 `eta-l4a-tests.json`。四项检查通过：`npm run check`；`npm test` **401 通过、0 失败/跳过**；`npm run check:ets` **0 error**；`npm run check:res` **1,833 资源、0 警告**。

**ArkTS 检查通过 / 未生成 HAP**。尚未接入生产 UI/网络，不能据此声称真实模型事件或工具执行已通过。无新增鸿蒙接口、权限、日志、签名。最终编译以用户 DevEco 为准。

## 与 Eta 的差异 / 待用户确认

- SystemClock.elapsedRealtime 用必传单调时钟函数隔离，避免错误使用可回拨的系统日期；E04c 接入时由平台提供。
- 只保留投影使用的事件字段，Android Intent/Binder、日志方法及无关图片字节统计不迁移；不改变投影的分支和结果。
- 搜索建议只作为内存展示数据，不执行 HTML、不注册任何手机工具。
- 本 PR 增加角色修订状态 DTO，为 L4b 依赖作准备，不启用角色页面。选项是完整移植 Android Runtime 或最小 DTO；默认最小 DTO，与任务卡一致。
- 此处对重试事件的显示不代表新增自动重试策略；现有聊天仍遵守不自动重发。

## 真机步骤（待测）

L4 无独立真机检查点。E04c 接入后验证：停止保留正文；重试时前次半截回答不被覆盖；同一结果恢复两次不重复；工具没有明确结果时显示未知；压缩通知不重复。当前仅纯逻辑验证，不执行手机动作。
