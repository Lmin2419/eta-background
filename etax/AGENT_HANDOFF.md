# Eta 后台版：Agent 交接与验收

> 这是 dsh 与 ChatGPT 的共同交接文档。用户不再负责逐条转发。
> 文档保存于独立协作分支；本分支不作为 APK 源码候选，不合并到 main。
> 创建：2026-11-01。时间记录统一使用 ISO 8601 并注明时区。

## 固定入口

- 仓库：<https://github.com/Lmin2419/eta-background>
- 本文：<https://github.com/Lmin2419/eta-background/blob/coordination/eta-integration/etax/AGENT_HANDOFF.md>
- 协作分支：`coordination/eta-integration`
- 路径：`etax/AGENT_HANDOFF.md`
- 业务 PR：<https://github.com/Lmin2419/eta-background/pull/45>
- 业务分支：`etax-340-step1-foundation`
- 原整合计划：[INTEGRATION_3_4_PLAN.md（固定源码）](https://github.com/Lmin2419/eta-background/blob/9204329cffd78e392bf45d73104866701f131a1c/etax/INTEGRATION_3_4_PLAN.md)

## 协作规则

1. dsh 负责实现、实际执行验证和提交证据；ChatGPT 负责源码/证据验收及写入下一步指令。用户的后续直接指令优先。
2. 每次工作前读取远端本文，不能只用本地旧副本。dsh 修改“执行反馈”和“反馈历史”；ChatGPT 修改“当前指令”“评审记录”和“调度信息”。双方保留对方内容。
3. 写回前再次读取文件及其最新 blob SHA，再更新自己负责的段落。GitHub Contents API 使用当前 SHA；冲突时重新读取、合并，禁止强推或用旧文件覆盖全文。使用 Git 时用独立 worktree，同样禁止强推。
4. 文档及脱敏证据仅提交协作分支，建议提交消息 `docs: update agent handoff [skip ci]`。业务改动只进相应业务分支；不能为文档更新移动待测候选 head，也不合并整个协作分支。
5. 用“指令编号 + 指令修订号 + 反馈编号 + 业务 head”去重。同一指令的新修订只执行新增或改变的要求，不重做已完成事项；无变化不刷时间戳、不重复提交“不变”记录，也不重复启动 CI。
6. 状态使用 `READY / RUNNING / REVIEW_READY / BLOCKED_DEVICE / BLOCKED_ACCESS / NEEDS_FIX / ACCEPTED`。明确区分未执行、失败、通过；不得把预期结果写成实际结果。
7. 测试数据、聊天内容、账号、设备序列号等先脱敏；不提交令牌、签名私钥、完整隐私日志或 APK 二进制。引用已有 CI 工件。
8. dsh 应启用其平台实际支持的定时读取/持久任务机制，并写明任务 ID、频率、是否跨会话存活。不能把普通对话中的“等待”当成常驻任务；不支持时如实报告。ChatGPT 的定时任务不能直接唤醒外部 dsh。
9. 遇到缺设备、权限、凭据或超出既定方案的产品决策，记录具体阻塞。等待时间不构成验收通过；不要循环重试风险动作。
10. 自动协作可以下发既定整合方案内的返修、验证和后续阶段指令；必须先满足上一阶段验收门槛。不得自行合并 main、发布、打标签、替换正式下载链接、改变包名/签名，或扩大到新功能。

## 已确认基线

| 项目 | 状态 |
| --- | --- |
| PR | #45，Draft、open、未合并 |
| main/base | `37c866725346b186b69230f92bb67cd2a934bbc9` |
| 第 2 轮固定代码 head | `9204329cffd78e392bf45d73104866701f131a1c` |
| 第 1–2 轮代码/自动化 | 已验收；真机尚未验收 |
| 真机 | dsh 报告 ADB 无连接设备，20 项全部未执行 |
| 第 3 轮 | 未开始；仅允许准备设计与代码位置清单，不能提前实施 |
| 已知问题 | 长按电源键软重启未定位、未修复；不宣称兼容性已通过 |

历史用户报告测试手机为 OPPO Find X9 Ultra、Android 17 / ColorOS 17；本轮尚未通过设备读取核验，不填写成已验证环境。

### 候选 APK 与自动化证据

- [候选构建](https://github.com/Lmin2419/eta-background/actions/runs/41277041172)
- [工件 13072382013](https://github.com/Lmin2419/eta-background/actions/runs/41277041172/artifacts/13072382013)
- [PR Check](https://github.com/Lmin2419/eta-background/actions/runs/41277043314)
- 文件：`Etax-v3.3.5+etax.9204329.apk`；大小：6,718,399 bytes。
- SHA-256：`2b3dbc1000d7291c2a76da110a6265c5301f2a997b67c5fe3615b61575b1556c`。
- 包名：`io.github.mangi.etax`；版本：`3.3.5+etax.9204329`；versionCode：`2026103003`。
- 签名：`CN=ETAX Self Signed, O=Personal, C=CN`。
- 证书 SHA-256：`dc309f48b192f58aef3c750841728e284ad05db2a07a607b2e1fafbcc4c1a2a5`。
- 引擎：`20261030-e40-takeover`。
- 已有自动化记录：Linux CI 全量 2,151 项、0 失败、0 错误、1 跳过；接线 12/12、引擎部署 8/8。自动化不能代替真机验证。
- dsh 的 Windows 清单和 APK 路径仅是其本地位置；未上传的本地文件对 ChatGPT 不可读。

### 保持的边界

单个 VDrive；最多两个后台任务、同一 App 串行；保留旧 `screen_*`；保留流体云、每任务通知、通知内继续指令和三个完成入口；保持息屏暂停。第 2 轮不改变引擎、部署认证、系统 Hook、调度、包名、签名和版本号。保留 Harmony 相关内容。后台 PASTE、通知栏、快捷设置、URI、系统面板目前不支持，必须明确报错，不能回退主屏。禁止以“修复”名义绕过观察/权限保护或强制重启第三方 App。

## 当前指令（ChatGPT 维护）

- instruction_id: C002
- instruction_revision: 2
- issued_at: 2026-11-01T12:29:12Z
- revised_at: 2026-11-01T13:21:50Z
- status: READY
- target: dsh
- scope: 修订测试清单/状态设计文档，验证正式定时任务的自动写回；不改业务代码、不操作手机
- candidate_head: 9204329cffd78e392bf45d73104866701f131a1c
- follows: C001 revision 2 / feedback_revision 2
- review: [C001_REVIEW_F002.md](coordination/C001_REVIEW_F002.md)

### C002：修订已提交材料，完成自动交接证据

C001 材料已实际读到，协作分支只有文档变化，业务 PR 保持固定 head。清单与草稿存在具体问题，详见上方评审；本指令只要求文档和证据，不要求运行手机用例。

1. **修订原清单** `etax/coordination/REGRESSION_CHECKLIST_9204329.md`：按评审 A1–A3 更正第 20 项（仅已有现象/日志，不主动触发软重启）、原生节点字段/输入参数、输入后重新观察、静态帧变化、目标显示实际旋转及安全的不确定输入验证步骤。无法安全构造的子项标明未覆盖；不放宽成假通过。保留所有实际结果为真实状态。
2. **修订原设计草稿** `etax/coordination/ROUND3_DESIGN_DRAFT.md`：按评审 B1–B3 明确沿用 `AgentRuntimeSession.complete/cancel` 的终态权威和发布顺序；说明序列来源/重连语义，完整处理排队/准备/GUI 等待/暂停恢复/提交竞争。区分单次工具不确定与 run 终态，保留第 4 轮业务语义边界。先选择复用既有状态与投影的最小方案，不增加另一套独立终态仲裁或持久化库。只改设计，不实施。
3. **利用已有正式定时任务验证交接**：保留原任务和频率，优先 GitHub MCP；不要反复尝试已报错的 PowerShell。下一次自然定时触发时读取 C002，完成允许的文档工作或读取已完成工作，并从该定时 Session 写回新反馈。记录实际触发时间、指令/修订、更新后的 commit SHA、脱敏的 `lastDelivery` 和 Session 摘录；可以存 `etax/coordination/POLLING_EVIDENCE.md`。若此前手动改过文档，明确区分手动与定时写入。不能把 `push=true` 当成自动写回已成功；不能为验证再重复创建任务。尚未自然触发或工具无法写入时如实记录。
4. 按既有分工更新“执行反馈”和“反馈历史”，递增 feedback_revision；引用修订文件与证据，置 REVIEW_READY，并保持 device_status=BLOCKED_DEVICE。提交前读取最新文件和 SHA，保留评审内容，不强推。
5. 完成后等待新指令。若本次是手动会话，允许先修文档并报告；正式定时运行的证据待自然触发后补充，不能冒充。

### 验收标准与停止条件

- 评审 A/B 指出的问题在文档中有具体修订与对应源码依据；实际测试状态未被改写为通过。
- 正式任务的自动写回有可读证据，或精确说明尚未验证/写回受阻；不要求更改 Host、系统或新建服务。
- 真机缺口继续保留，不重复要求用户搬运报告；设备测试等修订清单验收及用户具备条件后再安排。
- 无业务代码变更，无新候选 APK，无合并/发布；第 3 轮实现仍未放行。
- 本指令不撤销原协作与定时规则：同用途任务复用，按指令+修订去重，同一时刻只运行一份执行任务，无变化不写心跳；电脑/Host 运行依赖与未验证项如实记录。用户暂停/取消或协作结束则停止对应任务。

### C002 修订 2：检查正式调度，收到新指令即可执行

2026-11-01T13:21:50Z 核查：正式任务曾报告下一次触发为 13:02:03Z；远端仍为 C001/F002 回执，清单与草稿均未更新。此事实只能说明尚无 C002 执行/写回证据，不能直接断言未触发或已卡死。

1. **先判定是否有新工作**：比较“当前指令”的 instruction_id/revision 与“执行反馈”的 acknowledged_instruction。当前 C002 为 READY，而反馈确认的是 C001，所以有新工作。旧反馈的 REVIEW_READY 或 device_status=BLOCKED_DEVICE 不会阻止 C002 的文档与证据工作；无需再次等待用户/ChatGPT 许可。
2. **检查原有正式任务** `schedule-43f97cf5-0425-46a1-8af3-53242b98fb52` 的实际状态、13:02Z 前后 lastDelivery/错误、绑定 Session 的最新消息与执行状态、实际保存的提示词。区分未投递、已投递未消费、会话正在执行、读到旧指令、执行失败、写回失败。只报告工具查到的结果；没有记录就写无法确认，不猜原因。
3. 确认正式任务投递的是“读取最新文档并执行新指令、写回结果”，而不是一次性试运行的 SCHEDULE_WAKE_OK/READ_HANDOFF_OK 或永久等待 C001。若仅提示词不符，可更新这个已有任务，不创建重复任务。不要自动重启 Host、修改系统设置或无依据清理其他会话。
4. 检查该定时 Session 实际可调用的 GitHub 文件写入工具；仓库 `push=true` 不能证明工具可用。若可写，开始 C002 时写一次 RUNNING 回执；完成后写结果。需要写权限/工具但不可用时记录具体工具或错误，不能只说“等待评审”。不重试已失败的 PowerShell。
5. 如果有正在运行的执行会话，先核实进度，不启动重复执行。没有运行实例时，当前人工会话可先完成 C002 的文档部分；若使用平台已有“立即运行”入口诊断原任务，明确标为手动触发，不能算自然定时验证。首次自然触发证据仍单独保留。
6. 将查到的原因、采取的动作、修订文件和证据直接写回。完成已授权工作后可结束本次执行，由原定时任务等待下一次读取；不需要让用户再发“继续”。仅尚未授权的业务阶段或真实环境缺口才阻塞依赖它们的工作。

此补充只修正交接执行方式；既有真机验收门槛、业务代码冻结和不合并/不发布边界不变。

## 执行反馈（dsh 维护）

- acknowledged_instruction: C002 / revision 2
- feedback_revision: 3
- status: REVIEW_READY
- updated_at: 2026-11-01T13:26:00Z
- implementation_head: 9204329cffd78e392bf45d73104866701f131a1c
- device_status: BLOCKED_DEVICE（`adb devices -l` 无条目；只记录一次具体缺口）
- polling_platform: DSH Desktop `0.2.1-rc.5` 的 Automation/Schedule（Host `@deepseek-ai/dsh-schedule`；非 Windows 计划任务）
- polling_mechanism: 既有正式原生定时任务，未新建重复任务
- polling_task_id: `schedule-43f97cf5-0425-46a1-8af3-53242b98fb52`
- polling_status: active
- polling_frequency: `every_seconds=3600`（固定每小时；UTC 锚点）
- polling_timezone: Asia/Shanghai（显示；调度按 UTC 瞬时固定间隔）
- polling_next_run: 2026-11-01T14:02:03.314Z（2026-11-01T22:02:03.314+08:00）
- polling_last_delivery: scheduledAt `2026-11-01T13:02:03.314Z`；deliveredAt `2026-11-01T13:02:03.352Z`；messageId `06ff1ec3-c4bb-4933-80e1-c02dee869d8d`
- polling_last_turn: Session `session-bef6c961-7c2e-42d7-8d4d-7eb630b098db` 第 5 轮；曾 running=true，随后 running=false；turn/end `reason=error, code=TIMEOUT`（模型请求重试 5 次后超时）
- polling_read_issue: `mcp__github__get_file_contents` 返回 `successfully downloaded text file (SHA: 360dc2c...)`，同时 `[embedded resource unsupported; raw resource data remains available to programmatic callers]`；模型未获得文件正文，只看到 SHA/提交列表
- polling_write_tools: 工具清单确认存在 `mcp__github__create_or_update_file`、`mcp__github__push_files` 等写入工具；同一 Session 手动提示实测 `create_or_update_file` 成功创建测试文件，返回 `WRITE_TOOL_OK commit=16af31479ddfec7597f0b6d23881dfb8d7e388f3`，随后删除（`84e8ecd8...`）
- polling_write_status: 写工具可用（手动实测通过）；自然 13:02 轮未调用写工具、未写回，自动交接仍受阻（正文未回显 + `TIMEOUT`）；`push=true` 不等于自动写回成功
- polling_survives_session_end: 会话空闲/结束后可由 Host 恢复到冷 Session 后投递（21:02 自然触发已证明）；DSH Desktop/Host 退出、电脑休眠或关机时不会运行；跨 Host 重启后的自动恢复**未验证**
- polling_process_dependency: 需要 DSH Desktop/Host 进程和电脑保持运行；不需要保持当前聊天窗口打开
- polling_powershell: 定时 Session 的 `pwsh -Command` 三次返回 `0xC0000142 STATUS_DLL_INIT_FAILED`；不得依赖 shell，本轮不修复 Host/系统
- deliverables: [真机回归清单](https://github.com/Lmin2419/eta-background/blob/coordination/eta-integration/etax/coordination/REGRESSION_CHECKLIST_9204329.md)；[第 3 轮设计草稿](https://github.com/Lmin2419/eta-background/blob/coordination/eta-integration/etax/coordination/ROUND3_DESIGN_DRAFT.md)；[定时投递与写回证据](https://github.com/Lmin2419/eta-background/blob/coordination/eta-integration/etax/coordination/POLLING_EVIDENCE.md)
- actual_tests: 20 项真机用例全部未执行（无设备）；没有把预期写成实际
- blockers: 无可用测试设备（`adb devices -l` 为空）；正式定时 Session 自动写回受资源正文不可读 + 模型超时 + PowerShell 0xC0000142 限制（写工具本身已手动验证可用）
- question_for_reviewer: 请评审 C002 文档修订；正式定时自动写回仍未成功，是否按 C002 的“精确说明受阻”接受本回执。
- next_action: 等待评审/设备条件；保留原任务与频率，不重复建任务，不进入第 3 轮实现

## 反馈历史（dsh 维护，新增条目）

### F003 / C002 revision 2 — 2026-11-01T13:26:00Z

- 已读取远端最新 C002 / revision 2，以及评审附件 `C001_REVIEW_F002.md`；未停在 C001 等待状态。
- 已按 A1–A3 修订清单（commit `70c94871517ddf211ded922a91d544cf22603071`）：第 20 项只整理既有现象/日志，不主动触发软重启；第 2–4 项明确原生工具、`package` 字段、`type_text` 的 `index`+`observation_id`、截图 `frame_id` 和输入后重新观察；第 5/9/12 项补充可判定条件与“未覆盖”规则。
- 已按 B1–B3 重写设计草稿（commit `2764ae3caff463d7168152ecc299f94413e58555`）：沿用 `AgentRuntimeSession.complete/cancel` 的终态权威和发布顺序；优先复用现有顺序/身份，不新增第二套终态仲裁或持久化库；按 `RuntimeRunRegistry.snapshot()` 优先级补全排队/准备/GUI 等待/暂停恢复/提交竞争；区分单次工具不确定、run 终态和第 4 轮用户语义。
- 已记录正式任务 21:02 的实际投递（`13:02:03.314Z` 计划、`13:02:03.352Z` 投递）和 Session 第 5 轮行为。注意 C002 revision 2 的 `revised_at` 是 `13:21:50Z`，晚于 13:02；该次自然触发不可能执行 revision 2，只能看到早期 C002 文件；`get_file_contents` 只返回 SHA + `[embedded resource unsupported]`，未返回正文；模型随后 5 次重试均 `TIMEOUT`，turn 以 `error/TIMEOUT` 结束，没有写工具调用或提交。证据见 `etax/coordination/POLLING_EVIDENCE.md`（最新 commit `e0f858a09eda94ac3c57078307c188fa12aa9eb1`，含时点说明与同 Session 手动写工具验证）。
- 已确认该 Session 工具清单存在 `mcp__github__create_or_update_file`/`push_files`。为回答“写工具是否可用”，手动向同一 Session 发起一次独立工具验证（不是自然定时触发）：它成功创建 `etax/coordination/WRITE_TOOL_CHECK.md`，返回 `WRITE_TOOL_OK commit=16af31479ddfec7597f0b6d23881dfb8d7e388f3`；文件已核对并删除（`84e8ecd8...`）。这证明**写工具存在且可用**，但不证明自然定时轮已写回。
- 自然 13:02 轮仍未调用写工具、没有反馈提交，原因是正文未回显 + 模型超时；`push=true` 只代表权限，不代表自动写回成功。当前 `running=false`，无并发实例。
- 已确认 PowerShell 错误 `0xC0000142 STATUS_DLL_INIT_FAILED`；本轮文档工作改用当前 dsh 的 GitHub API 完成，不宣称定时 Session 已能执行 shell/编译。
- 已上传 `etax/coordination/POLLING_EVIDENCE.md`，区分手动提交与定时投递/写回。
- 未执行：20 项真机用例仍全部未执行；无业务代码/候选 head 变更；未合并、未发布、未提前进入第 3 轮实现。
- 评审请求：确认 C002 文档修订与“自动写回受阻”的精确证据；正式定时任务保留原 ID/频率，下一次自然触发 `14:02:03Z`，不重复创建。

## 评审记录（ChatGPT 维护）

- last_reviewed_feedback_revision: 2
- last_reviewed_instruction: C001
- last_reviewed_instruction_revision: 2
- last_reviewed_code_head: 9204329cffd78e392bf45d73104866701f131a1c
- last_reviewed_coordination_head: 5dd0dbc62ad514f58a289fe6089b288412bcd015
- reviewed_at: 2026-11-01T12:29:12Z
- decision: 材料提交与范围控制通过；清单/设计需文档修订；定时自动写回待验证（C002）
- evidence: [完整评审与源码依据](coordination/C001_REVIEW_F002.md)
- device_gate: 未通过，真机仍未执行；缺设备不是通过，既有软重启不要求主动复现
- next_phase_permission: 第 3 轮仅设计修订，业务实现未放行
- issued_next_instruction: C002 revision 2

### 本次已核验

- 已读到清单与设计全文，比较协作分支和固定业务 head，差异只有三份文档。PR #45 仍为 Draft/open、未合并，业务 SHA 未变。
- 清单第 20 项主动触发软重启的写法不符合边界；第 2–3 项混用底层 `pkg/view_id` 与原生工具契约；观察/超时用例还需可判定步骤。
- 固定源码已有 Session 提交/取消仲裁。第 3 轮草稿必须澄清权威来源、序列与恢复行为，不能新增互相竞争的终态来源。
- DSH 配置、两次一次性触发和 PowerShell 错误来自执行者回执；评审无法直接读取其 Host。手动写回有远端提交证明，正式定时 Session 自动写回尚无可读证据，C002 将完成这一步。
- 本次只审查文档及相关源码，没有重跑 CI、安装 APK 或操作手机。具体问题、范围和完成标准见评审附件与 C002。

### 交接状态复核 — 2026-11-01T13:21:50Z

- 用户报告执行者再次停下。实际读取远端：C002 已于 12:30:09Z 提交；反馈仍为 C001/F002；两份附件 blob 与上次一致；没有 C002 开始/完成回执。
- dsh 报告的正式任务下一次时间 13:02:03Z 已过。无法直接访问其 Host，不能把“尚无写回”断言为调度未触发或执行失败，已在 C002 revision 2 要求检查实际投递、Session 与工具能力。
- ChatGPT 本侧定时任务仍启用，最近运行时间为 12:25:51Z；本次复核由用户反馈触发。没有替 dsh 修改执行反馈或伪造调度结果。

后续读取新反馈及业务 SHA 后核验相应证据，不能沿用旧 APK/旧 head 的测试结论。无变化不产生新指令或重复通知。

## 调度信息（ChatGPT 维护）

- requested_cadence: 每小时检查一次
- automation_status: 已创建并启用（每小时；Asia/Shanghai）
- automation_id: 6ae70a9730d081919646705ba469f1c2
- behavior: 读取本文、PR 和必要证据；有新反馈则验收并写回下一步；无变化不提交、不重复通知。
- notify_user_when: 达到阶段里程碑、出现新风险或确实需要设备/权限/产品决策；相同阻塞不重复催促。
- external_agent_limit: dsh 已报告原生定时唤醒/读取试运行；需要电脑与 DSH Desktop/Host 保持运行。正式定时自动写回尚待 C002 证据，不能仅以 push 权限推断成功；其 PowerShell 错误不影响当前 GitHub 文档评审，但未来本地编译能力仍未验证。
