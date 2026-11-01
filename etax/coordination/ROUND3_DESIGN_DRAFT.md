# 第 3 轮设计草稿：统一任务状态（C002 修订）

> 状态：仅设计，未实施；不修改业务代码、业务 head 或 PR #45。  
> 依据：`9204329cffd78e392bf45d73104866701f131a1c` 的第 1–2 轮代码与 `C001_REVIEW_F002.md`。  
> 本轮目标：**复用既有 `AgentRuntimeSession.complete/cancel` 终态权威与有序发布**，把会话、通知和完成入口统一到同一份只读投影；不新增第二套终态仲裁、不新增持久化库。

## 1. 现有事实与权威

| 事实 | 现有实现 | 在新方案中的角色 |
| --- | --- | --- |
| run 终态 | `AgentRuntimeSession.complete()` / `cancel()`：锁内原子竞争 `RUNNING → COMMITTING/TERMINAL`，普通 `RunFinished`/`RunFailed` 只是事件 | **唯一终态权威**；先成功提交/取消的结果决定最终结果，不能被先到的事件替代 |
| 结果发布顺序 | `complete()` 在 `beforePublish` 后置 `TERMINAL`，先调用 `beforeTerminalPublish(result)`，再通过订阅者 `resultSink` 发布，再清理 replay/subscribers；`cancel()` 同序 | 通知/会话/完成页只在收到已发布结果后显示终态，不能提前把进度当结果 |
| 终态竞争 | `complete()` 仅在 `RUNNING` 成功，`cancel()` 也仅在 `RUNNING` 成功；晚到者返回 false | 迟到终态/取消幂等丢弃；不新增“先到先赢”的 Store |
| 运行阶段 | `AgentRuntimeWire.RunPhase` 已含 `Queued/Preparing/Running/WaitingForGui/Paused/Committing`；`RuntimeRunRegistry.snapshot()` 优先级为 Committing、Paused、Queued、Preparing、WaitingForGui、Running | 作为**投影输入**，不是第二套终态 |
| 结果/归档 | `RunResult`、`AgentRunArchiveStore`、`AgentRuntimeResultStore`、结果投递 | 现有结果事实；投影只读，不复制出新的持久化状态库 |
| 通知/卡片 | `TaskNotificationState` 的 `completed/paused/status`，`AgentExecutionService.cards` | 改为读取投影；通知可见性/划掉状态留在展示侧 |
| 屏幕位置 | `TaskScreenSession.location`、观察/接管状态 | 只描述操作位置与观察有效性，不定义 run 成功/失败 |

**结论：** 原草稿“现有系统没有单写者、需要新建 TaskRunStateStore 并另发序列”的表述过宽且方向错误。第 3 轮只增加一个**从现有权威事实派生的只读投影**；`AgentRuntimeSession` 继续拥有终态裁定权。

## 2. 最小方案：只读 TaskRunProjection

建议新增内部只读投影（名称暂定，不创建文件）：

```text
TaskRunProjection(
  runId, conversationId, operation,
  phase,                 // 直接映射现有 RunPhase
  pauseReason,           // SCREEN_OFF / APP_TAKEOVER / USER；来自 controller
  terminal,              // 仅在已发布 RunResult 后出现
  reasonCode,            // 稳定错误码/原因，不保存展示文案
  visibility             // 展示侧使用，不参与终态
)
```

- 输入只有既有事实：`RuntimeRunRegistry.Run.snapshot()`、`AgentRuntimeSession` 的已发布结果、`TaskScreenSession` 的位置/观察状态。
- 不新增终态集合、不新增独立持久化、不在投影层重新判断 `ok/error`。
- 通知、会话状态、完成卡片、完成页、浮层从投影读取同一 `phase/terminal/reasonCode`；各视图只做本地化和按钮可用性映射。
- 展示侧可以缓存“已展示/已划掉”的状态，但**不能**写回或改变 run 终态。

### 终态发布与消费顺序

1. `AgentRuntimeSession.complete(result)` 在锁内把 `RUNNING` 变为 `COMMITTING`；失败者（包括取消竞争）返回 false。
2. 完成 `beforePublish`（checkpoint/归档等非致命副作用）后置 `TERMINAL`。
3. 先执行 `beforeTerminalPublish(result)`（释放会话占用等），再向结果订阅者发布 `RunResult`。
4. 投影看到 `RunResult` 后发布 `terminal`；通知/卡片/完成页只能在此之后显示完成/失败。
5. `cancel(reason)` 在锁内竞争成功时生成失败结果并走同一发布顺序；`COMMITTING` 之后的取消不能覆盖已提交结果。

因此，**`RunFinished`、`RunFailed`、GUI 状态、工具结果都只是进度/候选信息**；不得用“先收到的 RunFinished”替代 `complete/cancel` 的终态。

## 3. 序列、去重与重连

优先**不引入第二套序号**：

- 进程内顺序由既有 `AgentRuntimeSession` 锁、订阅者发布顺序和 Session 事件顺序保证；调度/通知消费者在收到 `RunResult` 后只处理一次。
- 去重键使用既有身份：`runId` + `conversationId`；同一 run 的迟到 `RunResult` 直接丢弃，不覆盖已发布终态。
- 若投影展示需要版本号，只在**权威发布边界**生成：`AgentRuntimeSession` 锁内、终态提交时递增；同时携带 runId 与 Host/Session 实例代次（现有 `runId` 已能区分新旧运行；进程重启使用现有持久化结果/归档，不强称活动状态存活）。
- 消费者按 `(runId, instanceEpoch, seq)` 去重；`instanceEpoch` 只用于区分进程/服务重启前后的旧回调，不能由投影 Store 自己递增后当作源头顺序。
- 重连：`AgentRuntimeSession.attach` 只在非 TERMINAL 时重放并追加订阅；已 TERMINAL 时返回 false，客户端从结果存储读取终态。通知/卡片重连不能把旧实例的“运行中”恢复成成功。

## 4. 状态迁移（按现有行为）

`RuntimeRunRegistry.snapshot()` 的优先级必须保留；不能强制所有读者观察 `QUEUED → PREPARING → RUNNING`，后台准备完成时仍可能显示 Queued。

```text
admission rejected/duplicate -> 不产生已接纳 run 的 QUEUED 状态
QUEUED -> PREPARING -> RUNNING
                    -> WAITING_FOR_GUI (仍属 RUNNING 的等待子阶段)
RUNNING/WAITING_FOR_GUI/PREPARING -> PAUSED(SCREEN_OFF|APP_TAKEOVER|USER)
PAUSED -> 原来的未终结阶段（RUNNING 或 WAITING_FOR_GUI），恢复不一律强制 RUNNING
RUNNING/PAUSED/WAITING_FOR_GUI -> TERMINAL(COMPLETED|FAILED|STOPPED)
COMMITTING -> TERMINAL(已发布 RunResult)
```

补充规则：

- **排队/准备中取消**：只有已接纳 run 且既有取消路径实际生效时才进入 `STOPPED`；未进入 run 的 admission 拒绝不伪造 `QUEUED`，也不产生结果。
- **GUI 等待**：是 RUNNING 的子阶段；取消可进入 `STOPPED`，恢复后仍在等待或回到 RUNNING，取决于现有 `guiStatus`。
- **暂停前阶段**：记录暂停发生在 Queued/Preparing/Running/WaitingForGui 的哪一阶段；恢复回到该阶段可恢复的状态，不一律 RUNNING。
- **提交与取消竞争**：`COMMITTING` 后取消不能覆盖已提交结果；`complete/cancel` 只有一方成功。通知收到已发布结果后才终止。
- **Paused/Committing**：二者已经是 `RunPhase` 枚举；投影只读取，不另造枚举。
- **用户决定**：单次工具 `TIMEOUT`/`operationMayHaveRun` 是**工具层不确定**；整个 run 是否终结由 `complete/cancel` 决定；需要用户确认是产品层语义，留到第 4 轮。模型正常返回也不等于用户目标完全完成，但本轮不新增“部分完成”终态。

## 5. 拟改文件（实施时再定，不新增独立终态库）

| 文件 | 最小改动 |
| --- | --- |
| `RuntimeRunRegistry.kt` | 保留 `snapshot()` 优先级；暴露只读投影所需的现有字段 |
| `AgentRuntimeSession.kt` | 保持 complete/cancel 权威；如需投影版本，只在锁内终态提交处提供；不改变发布顺序 |
| `AgentRuntimeWire.kt` | 复用现有 `RunPhase`/`RunResult`；不新增第二套终态枚举 |
| `AgentRuntimeRunExecutor.kt` | 终态仍交给 `session.complete`；进度事件不直接写终态 |
| `AgentExecutionService.kt` | 通知/卡片读取投影；移除独立 `completed`/`paused` 作为任务真值 |
| `AgentOverlayState.kt` / `AgentOverlayText.kt` | 保留为展示投影；不参与终态裁定 |
| `TaskScreenSession.kt` | 只上报位置/观察失效；不定义任务终态 |
| 新增只读投影（名称暂定） | 优先无状态计算；若需缓存，只缓存权威事实的派生值，不新增持久化库 |
| 测试 | 终态竞争、发布顺序、重连、阶段投影和跨表面一致性 |

**不新增：** 第二套独立 `TerminalArbiter`、独立 SQLite/文件状态库、由通知层维护的 `completed` 真值。

## 6. 兼容风险与边界

- 现有 `AgentOverlayStatus` 被多个 UI 使用；保留类型，用投影适配，避免一次改完。
- 通知 ID、前台通知、完成卡片保留/划掉语义、`TaskNotificationDismissReceiver` 必须保持。
- 暂停/恢复、继续指令、两任务限制、同 App 串行、息屏暂停不能被投影绕过；投影不是新的控制通道。
- 进程/服务重启后，活动 run 不能凭旧通知恢复为成功；必须有已发布 `RunResult`/归档证据。
- 第 3 轮只统一现有事实与各界面投影；第 4 轮才允许新增“等待你/部分完成”等业务语义，本设计只保留扩展点。
- 不改变 VDrive 引擎、部署认证、系统 Hook、任务调度、包名、签名和版本号。

## 7. 有意义的测试

1. `complete/cancel` 竞争：只有一方成功；晚到 `RunFinished`/`RunFailed` 不覆盖已发布终态。
2. 发布顺序：`beforeTerminalPublish` → resultSink → 投影 terminal；通知/卡片不早于结果。
3. 快照优先级：Committing/Paused/Queued/Preparing/WaitingForGui/Running 的既有优先级不变；后台准备完成仍可 Queued。
4. 阶段迁移：Queued/Preparing/Running/WaitingForGui 暂停与恢复、排队/准备取消、提交竞争。
5. 单次工具 `TIMEOUT/operationMayHaveRun`：不自动结束 run；run 终态仍由 `complete/cancel` 决定。
6. 重连/重启：attach 对 TERMINAL 返回 false；旧实例回调不覆盖新结果；活动状态不伪装成功。
7. 跨表面一致：同一 run 的通知、会话、完成卡片、完成页读取同一 terminal/reason。
8. 展示侧隔离：划掉通知/完成卡片不会改变 run 终态；恢复后不重复完成通知。
9. 第 4 轮扩展点：只验证可插入“等待你/部分完成”的位置，不在本轮实现或提前下放业务语义。

## 8. 验收门槛

- 设计评审通过前不进入第 3 轮业务实现。
- 第 2 轮 20 项真机结果未验收前，不把本设计当成真机结论。
- 任何实施必须另开评审指令和业务 head；本文件不改变 `9204329...`。
