# 第 3 轮设计草稿：统一任务状态

> 状态：仅设计，未实施；不修改业务代码、业务 head 或 PR #45。  
> 依据：`9204329cffd78e392bf45d73104866701f131a1c` 的第 1–2 轮代码与既有回归。  
> 目的：为第 3 轮“会话、通知和后续预览读取同一份运行状态”列出候选方案，不提前实现。

## 1. 现有状态来源

| 来源 | 当前内容 | 问题/责任 |
| --- | --- | --- |
| `AgentRuntimeWire.RunPhase` | `Queued`、`Preparing`、`Running`、`WaitingForGui`，以及快照中的 `Paused`/`Committing` 分支 | 只描述当前运行阶段，不包含终态结果语义 |
| `RuntimeRunRegistry.Run.snapshot()` | 由 `queued`、`preparing`、`controller.isPaused`、`guiStatus`、`session.isCommitting` 推导阶段 | 状态由多处字段推导，读侧容易各自解释 |
| `AgentRunController` | `paused`、`pausedForAppTakeover`、`resumeGeneration`、`cancelled`、`cancelReason` | 暂停/取消的真实执行状态；没有统一的展示状态 |
| `AgentRuntimeRunExecutor` / `AgentRuntimeSession` | 发出 `RunStarted`、`RunFinished`、`RunFailed` 等事件；返回 `AgentRuntimeWire.RunResult(ok, content, error)` | 终态事实来自运行器与会话日志，但通知/卡片还会另行推导 |
| `AgentOverlayState` / `AgentOverlayStatus` | UI 展示状态（`Queued`、`Paused`、`ResultReady`、`RunFailed`、`Stopped`、`RunningTool` 等） | 面向浮层的投影，不是跨表面唯一状态 |
| `TaskNotificationState` + `AgentExecutionService` | `runId`、`conversationId`、`status`、`streamPreview`、`paused`、`completed`、`targetPackage`；维护 `cards`、`completedRuns` | 通知/完成卡片独立保存 `completed`/`paused`，可能与运行器终态或会话状态短暂不一致 |
| `TaskScreenSession` | `location`（前台/后台）、`observation`、后台阶段回调 | 只负责屏幕位置与观察，不应成为任务完成状态来源 |
| `AgentRunArchiveStore` / `AgentRuntimeResultStore` / `AgentResultMailbox` | 归档运行、完成结果、跨进程结果 | 持久化/投递事实，不是统一状态机 |
| `CompletedAppActivity`、通知操作、`NotificationReplyRequest` | 完成页、继续指令、打开会话/App 的入口 | 各入口目前按通知/结果状态自行判断可用性 |

结论：当前“运行阶段”“浮层文案”“通知完成布尔值”“持久化结果”是多个事实来源；它们都能描述任务，但没有一个**带版本的单写者**。

## 2. 拟引入的统一领域模型（只设计）

建议新增内部 `TaskRunStateStore`（名称暂定，不创建文件）和不可变快照：

```text
TaskRunState(
  runId, conversationId, operation,
  phase: QUEUED | PREPARING | RUNNING | PAUSED | COMMITTING | TERMINAL,
  location: FOREGROUND | BACKGROUND,
  terminal: COMPLETED | PARTIAL | FAILED | STOPPED | UNCERTAIN | null,
  pauseReason: SCREEN_OFF | APP_TAKEOVER | USER | null,
  visibility: NONE | NOTIFICATION | CARD | BOTH,
  sequence, updatedAt,
  reason: String?  // 稳定错误码，不直接存展示文案
)
```

- **唯一写者**：`TaskRunStateStore` 只接受既有接缝发出的事件；`AgentRuntimeRunExecutor` 不直接改通知，`AgentExecutionService` 不自行推断 `completed`。
- **单一序列号**：每个 run 的状态快照带单调递增 `sequence`；迟到回调的 `sequence` 小于当前值则丢弃。终态不可被后续事件改写（只允许相同终态的幂等重放）。
- **展示层只投影**：通知、会话页、完成页、浮层、任务卡片都把同一快照映射为本地化文案、图标、按钮和进度；不各自维护 `completed`/`paused` 布尔值。
- **持久化**：终态和必要的可见性写入现有运行归档/结果存储；服务重启先恢复终态，不把未知恢复成“正在运行”。活动状态在进程死亡后不能假装成功。

## 3. 事件接缝

| 事件 | 现有来源 | 状态效果 |
| --- | --- | --- |
| Admission accepted/duplicate/rejected | `RuntimeRunRegistry.admit` | `QUEUED`；被拒绝不创建状态 |
| Preparing/Ready | `Run.prepared`、`claimNext` | `PREPARING` / `RUNNING` |
| GUI wait | 现有 `guiStatus` / `GuiControlCoordinator` | `RUNNING` 的等待子阶段；不改终态 |
| Pause/resume | `AgentRunController.pause/resume` | `PAUSED` 且记录原因；恢复回到 `RUNNING` |
| Background screen phase | `TaskScreenSession` 回调 | `location=BACKGROUND`；只影响操作位置，不伪装完成 |
| Tool progress | `AgentEvent.ToolStarted/ToolFinished` | 进度/详情字段；不改变主阶段 |
| Terminal | `RunFinished`、`RunFailed`、取消原因、`RunResult` | 原子写入终态；后续事件只做同值幂等 |
| Notification action | 现有暂停/恢复/停止/继续 | 转为领域命令，再由唯一写者更新；通知不自行改 `completed` |
| Archive/restore | 结果存储、`CompletedAppActivity` | 读终态；没有终态时标为未知/可恢复，不猜成功 |

## 4. 状态迁移

```text
QUEUED -> PREPARING -> RUNNING -> TERMINAL(COMPLETED|PARTIAL|FAILED|STOPPED|UNCERTAIN)
  ^          |            |
  |          v            v
  +--- rejected      PAUSED (SCREEN_OFF|APP_TAKEOVER|USER)
                         |
                         +--> RUNNING (resume)
                         +--> TERMINAL(STOPPED) (cancel while paused)
COMMITTING -> TERMINAL
```

- `QUEUED -> RUNNING` 不可跳过 `PREPARING`（除非已有准备完成的等价证据）。
- `PAUSED` 必须带原因；无原因不进入 `PAUSED`。
- `TERMINAL` 后禁止回退；重复终态只允许同值幂等。
- `UNCERTAIN` 只用于结果无法确认的超时/取消，不得显示“完成”。
- `PARTIAL` 用于用户可见的部分完成；不得由单个工具成功自动推断。

## 5. 拟改文件（第 3 轮实施时再定）

| 文件 | 计划 |
| --- | --- |
| `AgentRuntimeWire.kt` | 增加稳定的运行快照/终态字段映射；不改变 wire 兼容必填字段 |
| `RuntimeRunRegistry.kt` | 把 `snapshot()` 的阶段推导收敛到统一状态接缝 |
| `AgentRunController.kt` | 暂停/恢复/取消事件增加稳定原因与序列 |
| `AgentRuntimeRunExecutor.kt` | 终态只发一次领域事件；避免通知侧重复推导 |
| `AgentExecutionService.kt` | 通知/卡片改为读取统一快照；移除独立 `completed`/`paused` 真值 |
| `AgentOverlayText.kt` / `AgentOverlayState.kt` | 保留为展示投影；状态类型不直接暴露给通知 |
| `TaskScreenSession.kt` | 只上报位置/观察失效，不定义任务终态 |
| 新增 `TaskRunStateStore.kt`（暂定） | 唯一写者、序列号、幂等、终态持久化 |
| 新增/调整测试 | 状态机、跨表面一致性、恢复和竞态 |

## 6. 兼容风险

- 现有 `AgentOverlayStatus` 被浮层、通知、完成卡片和本地化文案共用；先保留类型，用适配器从新快照投影，避免一次改完所有 UI。
- 通知 ID、前台通知和完成卡片的保留/清理规则不能因统一状态而改变；用户已看到的完成卡片不能被晚到状态覆盖。
- `TaskNotificationDismissReceiver` 的“划掉后不再弹”语义必须保留。
- 暂停期间的通知操作、继续指令、恢复前重新观察、两任务限制和息屏暂停策略不能被状态机绕过。
- 进程死亡/服务重启后，活动运行不能凭旧通知恢复为成功；必须有结果/归档证据才显示完成。
- 不改变 VDrive 引擎、部署认证、系统 Hook、任务调度、包名、签名和版本号。

## 7. 有意义的测试

1. 纯状态机：合法迁移全覆盖，非法迁移拒绝；终态不可回退；重复终态幂等。
2. 迟到/乱序事件：旧序列被丢弃；终态后到达的 `RunFinished`/`RunFailed` 不覆盖。
3. 跨表面一致性：同一 run 的通知、会话状态、完成卡片、完成页读取相同终态与原因。
4. 暂停/恢复/接管：暂停原因、恢复后仍 RUNNING、暂停中取消进入 STOPPED、旧观察不能输入。
5. 队列与并发：QUEUED 不显示完成；两个任务互不覆盖；取消 A 不影响 B。
6. 进程/服务恢复：活动状态不伪装成功；有终态证据时可恢复；通知不重复。
7. 超时/不确定：`UNCERTAIN` 不显示完成，通知提供明确原因。
8. 通知入口：继续指令、打开会话、打开 App 只在快照允许时可用；划掉完成通知的语义不变。
9. 前台/后台切换：位置变化不改变终态；后台 `NO_FRAME`/几何缺失仍按第 2 轮规则返回明确错误。
10. 属性/随机测试：随机事件序列下终态唯一、序列单调、没有“完成后又运行”的状态。

## 8. 验收门槛

- 设计评审通过前不进入第 3 轮业务实现。
- 第 2 轮 20 项真机结果未验收前，不把第 3 轮设计当成真机结论。
- 任何实施必须另开评审指令和业务 head；本文件不改变 `9204329...`。
