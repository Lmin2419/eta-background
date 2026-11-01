# 正式定时任务投递与自动写回证据（C002）

- 记录时间：2026-11-01T13:25Z
- 固定业务 head：`9204329cffd78e392bf45d73104866701f131a1c`
- 任务：`schedule-43f97cf5-0425-46a1-8af3-53242b98fb52`（Eta 交接每小时检查）
- 平台：DSH Desktop `0.2.1-rc.5` 的 Automation/Schedule（Host `@deepseek-ai/dsh-schedule`）
- 绑定 Session：`session-bef6c961-7c2e-42d7-8d4d-7eb630b098db`
- 频率：`every_seconds=3600`；状态 `active`
- 下一次（记录时）：`2026-11-01T14:02:03.314Z`

> 时点说明：C002 revision 2 的 `revised_at` 是 `2026-11-01T13:21:50Z`，晚于 13:02 的本次自然触发；所以 13:02 的 Session 不可能读取 revision 2，只能看到当时的 C002 早期文件。C002 revision 2 由 dsh 当前手动会话在 13:26Z 执行；不能把 13:02 运行说成已执行 revision 2。

## 1. 21:02 自然触发

`schedule/catalog` 的 `lastDelivery`：

```json
{
  "scheduledAt": "2026-11-01T13:02:03.314Z",
  "deliveredAt": "2026-11-01T13:02:03.352Z",
  "messageId": "06ff1ec3-c4bb-4933-80e1-c02dee869d8d"
}
```

结论：**自然触发确实投递**；不是未触发，也不是手动 sleep。

## 2. 绑定 Session 收到后做了什么

Session `session-bef6c961-7c2e-42d7-8d4d-7eb630b098db`：

- 第 5 轮由调度消息启动；启动时 `running=true`，随后 `running=false`。
- 工具调用：
  - `mcp__github__get_file_contents`（`etax/AGENT_HANDOFF.md`，`ref=coordination/eta-integration`）
    - 返回文本：`successfully downloaded text file (SHA: 360dc2c08b21b0ecd186bfe0dc462b3d28addc59)`
    - 同时返回：`[embedded resource unsupported; raw resource data remains available to programmatic callers]`
    - **没有把文件正文交给模型**；模型只看到 SHA。
  - `mcp__github__list_commits`（该文件/该分支）
    - 看到 C002 相关提交 `f69c58d`（2026-11-01T12:30:09Z）和 `21ecee3`（12:29:05Z）。
- 随后模型请求进入重试：
  - `llm/retry`：`Request timed out.`，`code=TIMEOUT`，最多 5 次；
  - 最终 `turn/end`：`reason.kind=error`，`error.code=TIMEOUT`；
  - 没有最终 assistant 文本，没有调用任何写工具，没有产生反馈提交。

结论：**Session 收到调度消息并实际尝试读取，但没有完成 C002 文档工作，也没有自动写回**。直接原因有两层：GitHub 文件资源在客户端未回显正文；随后模型请求超时（5 次重试后 turn 失败）。不能把这次失败说成“没有新指令”或“已自动写回”。

## 3. 是否仍有执行实例

记录时 `running=false`，没有正在运行的执行实例；没有并发启动新轮次。正式任务仍为唯一 active 任务，下一次自然触发为 `14:02:03.314Z`。没有为调查重复创建任务。

## 4. 定时 Session 是否具备 GitHub 写入工具

该 Session 的工具清单中出现：

- `mcp__github__create_or_update_file`
- `mcp__github__push_files`
- `mcp__github__get_file_contents`
- `mcp__github__get_commit`
- `mcp__github__search_code`

因此**写工具已挂载**。另外，dsh 在同一 `session-bef6...` 上手动发起了一次**工具验证**（不是自然定时触发）：

- 请求只调用 `mcp__github__create_or_update_file`，创建 `etax/coordination/WRITE_TOOL_CHECK.md`；
- Session 返回 `WRITE_TOOL_OK commit=16af31479ddfec7597f0b6d23881dfb8d7e388f3`；
- 文件内容已通过 API 核对（62 bytes），随后用 API 删除，删除提交 `84e8ecd8e04860cafa4723a4bacf5b93d2d7f07f`。

结论：**GitHub 写入工具确实可用**（同一 Session、手动提示）。但自然 13:02 轮没有调用写工具、没有写回；`push=true` 或工具存在仍不能代替自然定时写回成功。正式自动交接的失败原因是正文未回显 + 模型 `TIMEOUT`，不是写工具缺失。

另外，该 Session 的 `pwsh -Command` 三次返回 `exit code 3221225794`（`0xC0000142 STATUS_DLL_INIT_FAILED`），因此不能依赖 PowerShell/`git` 写回；文档读取/写入只能走 MCP。此环境限制不是 Android/真机失败，但它使“完整自动交接”尚未成立。

## 5. 手动执行与定时执行的区别

- **手动执行**：dsh 在当前会话通过 GitHub Contents API 完成了 C002 清单/设计修订和本反馈提交；这些提交的 SHA 可在仓库查询。
- **定时执行**：仅证明 21:02 自然投递、Session 启动并尝试读取；没有成功写回。该差异必须保留，不能用手动提交冒充定时提交。
- C002 文档修订本身已完成；正式定时任务的自动写回仍待解决读取正文和模型超时问题后验证。

## 6. 建议的最小修复（未实施）

1. 定时读取不要依赖 `get_file_contents` 的内嵌资源回显；改用能返回正文的 GitHub 工具/接口（例如 `get_commit` 的 patch，或 MCP 客户端支持资源读取后显式读取）。
2. 写回前使用 `get_file_contents` 的 SHA 与 `create_or_update_file` 当前 SHA；若正文不可读，必须记录 `BLOCKED_ACCESS`，不得用旧内容覆盖。
3. 不新增第二套调度器或重复任务；修复后只复用原任务，等待下一次自然触发验证。
4. 需要本地 shell 的后续任务仍受 PowerShell `0xC0000142` 限制；本轮不要求修 Host/系统，但未来编译/真机执行前必须解决或明确阻塞。
