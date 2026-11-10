# dsh-bridge 协议 v1

一条 WebSocket 长连接承载全部请求与推送，帧内容都是 UTF-8 JSON。

- 地址：`wss://<你的域名>/v1/ws`（插件本身只监听 `ws://127.0.0.1:7788/v1/ws`，TLS 由服务器上的 Caddy 终止）
- 认证：握手请求头 `Authorization: Bearer <token>`，不对返回 401
- 心跳：服务端默认每 15 秒发一次 WebSocket ping，连续 3 个间隔没收到任何数据就断开；客户端可以用 `client.heartbeat` 调整自己连接的间隔（10–120 秒），后台时放长以省电
- 健康检查：`GET /v1/health` → `{"ok":true,"protocol":1,"version":"0.4.0","runtime":"desktop","dsh":{"version":"0.2.1-rc.2","api":["control","page","projections"]}}`（无需认证，用于确认穿透通了；`version` 是插件版本，`runtime` 为 `desktop` 表示运行在 DSH 桌面版里、`node` 表示独立的 `dsh` 进程；`dsh.api` 是这个 DSH 提供的会话接口，0.2.2 起多出 `events`、`state`、`observeProjections`）
- 兼容：插件 0.4.0 支持 DSH 0.2.1 和 0.2.2。所有会话的投影变化都来自全局 `control()` 流（两种帧格式都认），历史分页优先用 `events`，没有时用 `page`

## 请求 / 响应

客户端发 `{"id":"<任意唯一串>","op":"<操作名>", ...参数}`，服务端回：

```json
{"t":"res","id":"<同一个 id>","ok":true,"data":{...}}
{"t":"res","id":"<同一个 id>","ok":false,"error":{"code":"...","message":"..."}}
```

| op | 参数 | data |
|---|---|---|
| `hello` | – | `{server, protocol, version, host, platform, runtime, dsh:{version, api}, approvals:[Approval], questions:[Question], features}` |
| `client.heartbeat` | `intervalMs` | `{intervalMs}`（实际生效值） |
| `ping` | – | `{now}` |
| `sessions.list` | – | `{sessions:[SessionRow], workspaces:[{id,title,path}]}`（从 0.3.0 起归档会话也返回，带 `archived`） |
| `sessions.search` | `query` | `{items:[{sessionId,snippet}], hasMore}` |
| `sessions.create` | `workspaceId?` 或 `cwd?`、`agentPreset?` | `{sessionId}` |
| `sessions.prompt` | `sessionId`、`text`、`mode?:"queue"\|"steer"`、`requestId?`、`images?:[{mediaType,data(base64),name?}]`、`timeZone?` | `{accepted, requestId}` |
| `sessions.cancel` | `sessionId` | `{accepted}` |
| `sessions.rename` | `sessionId`、`title` | `{title, seq}` |
| `sessions.queue` | `sessionId`、`itemId` 或 `requestId`（发送时的请求号，用于刚发出、还不知道条目 id 的消息）、`action:"remove"\|"steer"\|{edit:text}` | `{accepted}`（`steer` 后条目带 `steering:true` 留在队列里，直到下一步被模型取走） |
| `sessions.follow` | `sessionId`、`afterSeq?`、`parentId?`、`mode?` | `{following:true}`，随后推送 `snapshot` 和增量。带 `parentId` 时按子代理地址订阅（`mode` 取自 `sessions.subagents`） |
| `sessions.unfollow` | `sessionId` | `{following:false}` |
| `sessions.older` | `sessionId`、`beforeSeq`、`parentId?`、`mode?` | `{items:[Item], hasMore, cursor}` |
| `sessions.subagents` | `sessionId` | `{children:[Subagent], more}`（最多 60 个，`more` 表示还有更多） |
| `subagents.prompt` | `childSessionId`、`text`、`mode?:"queue"\|"steer"`、`requestId?`、`timeZone?` | `{accepted, requestId}`；只有 `continuable` 子代理能接收，桌面缺少 subagents 服务时返回 `unavailable` |
| `subagents.interrupt` | `childSessionId` | `{accepted}` |
| `models.list` | – | `{default:{provider,model}, groups:[{id,name,models:[{id,name,reasoning?}]}], failures}` |
| `sessions.selectModel` | `sessionId`、`provider`、`model`、`reasoningEffort?` | `{selected}` |
| `sessions.archive` / `sessions.unarchive` / `sessions.pin` / `sessions.unpin` | `sessionId` | `{archived, pinned}`；同时向所有连接推送 `session-flags`。错误码：`busy`（运行中不能归档）、`archived`（已归档不能置顶）、`not-found` |
| `question.answer` | `questionId`、`answers:[{id, selected:[label], custom?}]` | `{accepted}`；回答一个正在等待的提问，未作答的问题按跳过处理 |
| `question.reply` | `sessionId`、`callId`、`answers` | `{accepted}`；回答已转为「稍后回答」的限时提问（`questions` 投影里 `state:"continued"`），需要会话处于活动状态，否则返回 `not-live` |
| `approval.answer` | `approvalId`、`outcome:"allowed-once"\|"rejected"\|"cancelled"` | `{accepted}` |

`SessionRow`：`{id, title|null, updatedAt, running, blank, live, cwd?, parentId?, subagent?, workspaceId?, pinned?, archived?}`。`blank` 是桌面预建的空白「新对话」，桌面侧栏不显示；`archived` 的会话从 0.3.0 起也会返回，由手机放进「已归档」

`Subagent`：`{id, mode:"one-shot"\|"continuable"\|"external"\|"unknown", label?, name?, createdAt, title?, running?, usage?, timing?}`（`usage`/`timing` 结构同下方统计字段；`external` 子代理只有前几项）

## 推送

| t | 字段 | 说明 |
|---|---|---|
| `snapshot` | `sessionId, cursor, hasMore, resumed, items, title, model, queue, cwd, live, stats?, usage?, pressure?, subagents?, timing?` | `follow` 后的首屏。`resumed:true` 时 `items` 只含 `afterSeq` 之后的条目，客户端在原列表后追加；否则整体替换 |
| `item` | `sessionId, item` | 一条持久事件（见下） |
| `live` | `op:"start", sessionId, attemptId, turn, step` | 模型开始输出 |
| `live` | `op:"delta", sessionId, attemptId, deltas:[{index, kind:"text"\|"reasoning"\|"tool", text, id?, name?}]` | 增量文本，约 30ms 合并一次；`text` 是追加内容 |
| `live` | `op:"end", sessionId, attemptId, committed, seq?` | 输出结束；`committed` 时 `seq` 对应的 `assistant` 条目会（或已经）以 `item` 推送，拿到后删掉临时行 |
| `projection` | `sessionId, seq, title?, model?, queue?, stats?, usage?, pressure?, subagents?, timing?` | 标题、模型选择、待处理队列、统计变化 |
| `status` | `sessionId, running` | 运行状态（所有连接都会收到） |
| `session` | `session:SessionRow` | 会话出现或 Agent 上下线 |
| `activity` | `sessionId, updatedAt` | 用户发了新消息 |
| `error` | `sessionId, message` | 轮次外的错误 |
| `approval` | `approval:Approval` | 需要审批 |
| `approval-done` | `approvalId, outcome` | 审批已结束（手机、桌面或超时任一方） |
| `question` | `question:Question` | Agent 提问（`ask_user_question`、计划审阅、提问形式的授权）；手机和桌面同时显示，先答者生效 |
| `question-done` | `questionId, outcome:"answered"\|"timeout"\|"cancelled"` | 提问已结束；`timeout` 表示限时等待结束，问题转为可稍后回答 |
| `turn` | `sessionId, turn, reason, message?, text, time` | 任一普通会话（不含子代理）的一轮结束；`text` 是该轮最后一段回复（最多 600 字），用于完成提醒 |
| `session-flags` | `sessionId, archived, pinned` | 会话被归档、取消归档、置顶或取消置顶 |
| `workspaces` | `workspaces:[{id,title,path,sessionIds}], archived, pinned` | 电脑上的工作区登记变化（新建、改名、排序、会话归属、归档、置顶），整份替换 |
| `projection`（未订阅的会话） | `sessionId, seq, title` | 任一会话的标题变化（自动生成或改名）推给所有连接，列表随之更新 |
| `follow-error` / `follow-end` | `sessionId, error?` | 订阅失败或结束，客户端应重新 `follow` |

`Approval`：`{id, sessionId, toolName, callId?, reason?, displayReason?:{en, zh?}, createdAt}`

队列条目：`{id, text, steering, rpcId?, ...}`

`Question`：`{id, sessionId, callId?, timed, createdAt, questions:[{id, question, detail?, header?, options:[{label, description?}], multiSelect, intent?}]}`。`intent.kind` 为 `plan-review` 时 `detail` 是待审阅的计划（Markdown），`intent.approve` 是批准选项；为 `approval` 时 `intent.approve`/`reject`/`always?` 是三个操作对应的选项

统计字段（直接转发 DSH 投影，字段名不变）：

| 字段 | DSH 投影 | 内容 |
|---|---|---|
| `stats` | `sessionStats` | `{turns, steps, llmMs, toolMs, ttftMs, ttftSteps, decodeMs, decodeTokens}` |
| `usage` | `tokenUsage` | `{uncachedInputTokens, outputTokens, cacheReadTokens, cacheWriteTokens}`；缓存命中率 = cacheRead ÷ (uncached + cacheRead + cacheWrite) |
| `pressure` | `contextPressure` | `{pressureTokens, projectedTokens, contextWindow}` |
| `subagents` | `subagentCatalog` | `[{id, mode, label, name, createdAt}]` |
| `timing` | `subagentTiming` | `{settledMs, active?:{since, through}, lastTurnCompleted?}` |
| `questions` | `userQuestions` | `{active:[{callId, state:"open"\|"continued", questions}]}`：仍可回答的限时提问 |

## 条目 Item

所有条目都带 `seq`（会话内单调递增）和 `time`（毫秒）。

| k | 字段 |
|---|---|
| `user` | `id, text, images, files:[name], source:"user"\|"retry", rpcId?` |
| `trigger` | `id, kind, text, summary?, stopReason?, sender?`：非用户输入的消息（子代理结束、代理间消息、定时任务、目标、团队、webhook 等），`text` 截断到 4000 字 |
| `assistant` | `id, turn, step, blocks:[{type:"reasoning"\|"text",text} \| {type:"tool",id,name,args}], model?:{provider,model}, firstTokenAt?, usage?:{input,output,total,cacheRead,cacheWrite,reasoning}, interrupted?` |
| `tool_result` | `callId, isError, text, truncated?, images?, error?:{code,reason}` |
| `turn_start` | `turn` |
| `step_start` | `turn, step` |
| `tool_call` | `callId, name`（用于计算工具耗时） |
| `turn_end` | `turn, reason:"completed"\|"aborted"\|"error"\|..., message?, cause?` |
| `notice` | `code:"retry"\|"compaction_start"\|"compaction", text, ...` |
| `title` | `title` |

DSH 注入给模型的上下文消息（`runtime-context`、`time-context` 等）不推送。
