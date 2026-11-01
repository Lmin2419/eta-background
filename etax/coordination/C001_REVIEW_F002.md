# C001 / F001–F002 交接验收：材料可读，仍需文档修订

- 评审日期：2026-11-01（UTC）
- 固定业务 head：`9204329cffd78e392bf45d73104866701f131a1c`
- 审阅的协作 head：`5dd0dbc62ad514f58a289fe6089b288412bcd015`
- 反馈：C001 revision 2，feedback_revision 2。
- 结论：材料提交与范围控制通过；清单和第 3 轮草稿需要修订；定时自动写回尚未独立验证；真机仍未执行。后续指令 C002 仅允许文档和证据工作。

## 实际核验

1. 已通过 GitHub API 读取交接文档、`REGRESSION_CHECKLIST_9204329.md` 和 `ROUND3_DESIGN_DRAFT.md` 全文，不是仅看报告摘要。
2. 业务 PR #45 仍为 Draft/open、未合并，head 仍是固定候选；协作分支对固定候选的差异只有上述两份附件和交接文档，没有业务代码变化。
3. 真机清单仍将 20 项实际结果列为未执行。候选 APK 身份与已核验记录一致；本轮没有重新下载、安装、运行 APK，没有重跑 CI。
4. 已读取固定 head 的 `TaskScreenSession.kt`、`RuntimeRunRegistry.kt`、`AgentRuntimeSession.kt`、`AgentRuntimeWire.kt`、`AgentRunController.kt`、`AgentRuntimeRunExecutor.kt`、`AgentExecutionService.kt` 等相关源码，核对草稿中的事实与状态边界。
5. DSH 两次一次性任务的运行时间、返回文本、正式任务 ID 和 PowerShell 错误目前来自 dsh 回执。评审环境无法读取其本地 schedule catalog 或 Session 原始日志，不能称独立验证过定时执行链路。
6. 手动写回 GitHub 已有远端提交证明；`push=true` 只能说明权限报告，不能证明正式定时 Session 已成功自动写回。首次自然定时触发的端到端回执仍待补。

## A. 真机清单修订

### A1：第 20 项不得要求主动触发软重启

清单的操作步骤写着“长按电源键触发软重启（仅记录）”。“仅记录”没有消除主动触发动作，与交接边界冲突。

改为：仅整理用户已有现象和已存在的脱敏日志，不要求再次长按复现，不远程操作设备。标记为独立未解决观察项，不作为常规回归必做动作，也不能因未主动复现而填通过。

### A2：区分原生工具契约与旧 screen_* 契约

固定源码：
- [TaskScreenSession.kt:75](https://github.com/Lmin2419/eta-background/blob/9204329cffd78e392bf45d73104866701f131a1c/app/src/main/kotlin/io/github/mangi/eta/agent/runtime/TaskScreenSession.kt#L75)：节点输出使用 `package`，不是底层 dump 的 `pkg`。
- [TaskScreenSession.kt:587](https://github.com/Lmin2419/eta-background/blob/9204329cffd78e392bf45d73104866701f131a1c/app/src/main/kotlin/io/github/mangi/eta/agent/runtime/TaskScreenSession.kt#L587)：原生后台 `type_text` 根据 `index` 找节点，再由适配器传 `view_id` 给底层 `screen_text`；直接向原生工具传 `view_id` 不能作为已支持的选框步骤。

修订清单第 2–4 项：分别标明调用的工具名、必要参数和观察字段。每次会改变界面的输入之后重新观察，不复用已经失效的 observation；截图坐标附同一观察的 frame_id。第 13 项继续单独验证旧 screen_*，不要混写两套参数。

### A3：让观察与不确定结果用例可判定

- 第 5 项：除静态等待外，补充“产生新 frame，但节点/包名/显示几何不变，原已发布截图坐标仍可使用”的验证意图；没有实际发生 frame 变化则标记该子项未覆盖。不能把 frame 必须不变作为唯一通过条件。
- 第 9 项：旋转物理手机不必然改变虚拟显示。必须记录目标 `displayId/width/height/rotation` 确实变化；未变化时该项未覆盖，不判失败或通过。不能修改全局显示配置来凑结果。
- 第 12 项：给出已有能力可执行的、无破坏的步骤及可观察返回；不能通过卡死系统/阻塞 ActivityManager 制造超时。若当前候选无法安全稳定构造 `operationMayHaveRun` 场景，如实标记未覆盖及原因，不能用普通 wait 超时或自动化替身算真机输入不确定性通过。
- 旧观察输入若无法从当前 App 工具调用中实际复现，标记未执行，不把正常重新观察后的成功当成旧观察拒绝证据。

## B. 第 3 轮设计草稿修订（不实施）

### B1：复用现有终态仲裁，不能把先到的 RunFinished 当终态真相

[AgentRuntimeSession.kt:149](https://github.com/Lmin2419/eta-background/blob/9204329cffd78e392bf45d73104866701f131a1c/app/src/main/kotlin/io/github/mangi/eta/agent/runtime/AgentRuntimeSession.kt#L149) 已有 RUNNING → COMMITTING → TERMINAL，以及 complete/cancel 竞争和结果发布顺序。草稿声称现有系统“没有单写者”过于宽泛。

将“终态来源：RunFinished、RunFailed、取消原因、RunResult”改成明确的权威选择和发布时序：既有 Session 的提交/取消仲裁与已发布结果决定最终状态，普通事件只是进度/候选信息，不能先到先赢。不要新增第二套能独立裁定成功/取消的状态机或新的持久化库。说明与既有归档、结果投递、会话占用释放顺序的衔接。

### B2：序列号需有明确来源与重连语义

说明谁分配 sequence、在哪个同步边界内生成、消费者如何去重、进程/服务重启后如何区别旧实例/新实例（如 generation/epoch 或等价的现有身份）。

不能把事件抵达新 Store 时才递增的编号当成“已识别源头乱序”。如果暂不需要第二套序列，优先利用现有有序发布和订阅边界。通知展示/划掉状态应留在展示侧，不应混入决定任务执行终态的领域状态。

### B3：状态迁移必须反映现有行为

- [RuntimeRunRegistry.kt:113](https://github.com/Lmin2419/eta-background/blob/9204329cffd78e392bf45d73104866701f131a1c/app/src/main/kotlin/io/github/mangi/eta/agent/runtime/RuntimeRunRegistry.kt#L113)：快照优先级是 Committing、Paused、Queued、Preparing、WaitingForGui、Running；后台准备完成时仍可排队，不能强制每个读者都观察到 Queued → Preparing → Running。
- [AgentRuntimeWire.kt:187](https://github.com/Lmin2419/eta-background/blob/9204329cffd78e392bf45d73104866701f131a1c/app/src/main/kotlin/io/github/mangi/eta/agent/runtime/AgentRuntimeWire.kt#L187)：Paused/Committing 已是 RunPhase 枚举，不是仅在快照中的额外分支。
- 补全排队/准备中取消、GUI 等待、暂停前阶段及恢复、提交与取消竞争；恢复不能一律强制 RUNNING。拒绝 admission 不应在图里产生一个回到 QUEUED 的已接纳运行。
- 区分单次工具结果不确定、整个 run 的最终结果、向用户请求决定三个层次。模型正常返回不等于用户目标完全完成；单次工具超时也不自动等于整个 run 终结。
- 第 3 轮先统一现有事实与各界面投影；新的“等待你/部分完成”业务语义按原计划留到第 4 轮。本轮草稿可保留扩展点，但不能默默把第 4 轮提前实施。

## C. 定时交接证据与已知限制

- 接受 dsh 已报告真实平台机制，且明确电脑与 DSH Desktop/Host 需保持运行；不要求新建服务或重复建测试任务。
- 下一次既有正式任务自然触发后，让该 Session 按 C002 读取、完成允许的文档工作并写回反馈。回执记录：正式任务 ID、实际触发时间、读取到的指令/修订、结果 commit SHA、脱敏的 catalog lastDelivery/Session 摘录及所用读写工具。不要提交完整提示词、凭据路径或本地认证内容。
- 把手动写入与定时 Session 写入分别记录；不能仅凭仓库 push 权限推断自动写回成功。若尚未自然触发，保持待验证。
- PowerShell `0xC0000142 STATUS_DLL_INIT_FAILED` 是 dsh 报告的新执行环境限制，不是 Android/真机失败。当前用 GitHub MCP 完成文档工作即可；本轮不要求修复 Host/系统，也不能因为文档可读就宣称未来编译能力可用。
- 不要求用户再次搬运报告。真机缺设备继续记录为已知阻塞，C002 本身不依赖连接手机。
