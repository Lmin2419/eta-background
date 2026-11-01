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

- instruction_id: C003
- instruction_revision: 1
- issued_at: 2026-11-01T13:56:39Z
- status: READY
- target: dsh
- scope: 加速收尾；真机准备/结果优先，自动交接单独验证；不改业务代码、不远程安装或操作手机
- candidate_head: 9204329cffd78e392bf45d73104866701f131a1c
- follows: C002 revision 2 / feedback_revision 3
- user_priority: 用户明确要求加速；减少文档循环，不再把定时交接修复作为真机工作的前置条件

### 已作出的评审决定

- C002 的主体文档修订及故障说明已读到，接受其如实报告阻塞；第 3 轮采用既有 Session 权威加只读投影的设计方向通过，不再要求重写设计报告。实施仍以第 2 轮真机验收为前提。
- 正式投递、正文展示故障、模型 TIMEOUT 分开记录。它们先后发生，不证明正文问题必然造成模型超时。手动写工具测试有实际提交/删除记录；自然定时写回仍未成功。
- 仅余清单第 5 项需要纠正：不能用 BACK 或其他输入制造新帧后再要求旧观察有效；成功输入本来就会使旧观察失效。本轮不因这一个文案点开启新一轮设计返修。

### 一、直接推进真机验收，不等待定时系统修好

用户正在被询问能否连接手机并现场配合。无明确设备条件时保持已知阻塞，不重复催促；条件具备后按下列分组连续执行原清单，结果直接写回，不让用户逐条搬运报告。dsh 负责指导及整理证据，手机动作由用户在场从 App 内发起；本指令不授权远程安装、点击或修改设备设置。

| 组 | 原用例 | 执行方式 |
| --- | --- | --- |
| A | 1–5、13 | 前台入口 → 后台节点 → 文本输入 → 截图坐标 → 静态等待/被动新帧 → 旧工具兼容，尽量复用同一个无敏感内容的测试页面 |
| B | 6–8 | 两个 App 并行 → 同 App 串行/明确拒绝 → 取消隔离，复用这组任务 |
| C | 9–12 | 目标显示真实旋转、暂停恢复、用户接管、输入结果不确定；仅按既有能力安全执行，不安全或不可构造的子项单列未覆盖 |
| D | 14–17 | 在上述任务结束时一起检查完成通知、通知内继续、打开 ETA 会话、打开 App |
| E | 18–19 | 启动失败和明确不支持的后台能力；第 20 项仅补已有软重启资料，不主动复现 |

- 分组减少重复建任务和报告，不减少验收项；未覆盖仍未覆盖，不算通过。个别用例不能安全构造时先记录，继续其他独立用例，不停掉整轮。
- 所有原生输入都携带同次 observation_id，截图坐标还要同次 frame_id；每次改变页面的输入之后重新观察。
- 第 5 项分开测：A. 原观察保持至少 70 秒后输入；B. 只发生自然重绘、节点/包名/显示几何不变时，旧已发布截图仍可用。两段各自重新建立起始观察；期间不得输入 BACK、tap、滑动、暂停/恢复/接管，也不得调用会替换本任务 observation 的新 observe_screen。必须有无副作用的可读证据证明新 frame，无法获得时仅 B 标未覆盖。把原清单 BACK 示例删除即可，无需另写报告。
- 遇真实失败保留一次复现和脱敏工具返回；不自动重放不确定输入、不关闭校验。
- 一组结果准备好即可提交，评审可逐组进行；不必等所有报告排版完成。使用现有清单和反馈，不新建长篇阶段总结。

### 二、自动交接只做最小闭环验证，与真机工作分开

1. 保留现有唯一正式任务，不再调查已经确认的“有没有触发”，不重建调度器，不反复试 PowerShell，不修 Host/系统。
2. 利用当前手动会话实际可用的读取能力，为定时 Session 选出一个**正文会作为文本交给模型**的路径：
   - 若平台支持程序化工具调用/读取 MCP 资源，提取原始工具结果内的文本资源（例如实际存在的 content[].resource.text），显式作为 text 返回；不能只保留 downloaded/SHA 提示。
   - 或用已安装且可用的 HTTP/网页读取工具读取 GitHub 原始文件正文。可先解析协作分支当前 commit，再读取该 commit 对应的完整 raw 文件，避免缓存或分支竞态。raw 入口：<https://raw.githubusercontent.com/Lmin2419/eta-background/refs/heads/coordination/eta-integration/etax/AGENT_HANDOFF.md>。
   - 上述均不可用时如实写明缺失能力，不假造工具或增加新的服务。不要把 get_commit 的 patch、search_code 的片段当成完整文档，更不能据此重写全文。
3. 先在同一个绑定 Session 验证：实际工具返回中能读到完整“当前指令”及边界，报告读取到的指令/修订、源 commit/blob SHA 与正文片段。读不到正文就止于读取失败，不执行猜出的指令，不写回旧文件。
4. 成功后仅更新原正式任务的提示为这条已验证的读取路径。下一次自然触发按最新文档完成一次实际反馈写入；写前重新读取最新全文及 SHA，冲突重读并保留其他人的段落。
5. 验证只写实际反馈或既有 POLLING_EVIDENCE.md，不再创建/删除 WRITE_TOOL_CHECK 等测试文件。手动验证与自然触发明确分开；若又有模型 TIMEOUT，记录发生在哪一步、是否已写入，然后停止该次重试，不默认更换模型、凭据或网络设置。
6. 文档读写链路是否跑通不决定真机组能否继续。正式会话本地 shell/构建能力未验证，只在未来实际需要它时作为独立环境阻塞处理。

### 完成与等待

- 收到本指令即可执行不依赖手机的最小工作，不再次询问“是否接受回执/是否继续”。
- 不再重写第 3 轮草稿；实施时再核对字段映射细节。无新结果不生成新的反馈或空提交。
- 执行反馈区分设备状态与交接状态，新增证据才递增 feedback_revision。正文读取失败不冒充“无新指令”。
- 没有手机时完成上述最小工作即可等待设备；不能用额外文档、重复单测或绕过阶段门槛代替真机验证。真机结果未验收前，第 3 轮业务实现仍不放行。
- 不修改业务代码、main、PR 元数据、版本、包名、签名；不发布、不合并、不启用息屏执行，保留所有已有边界。

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

- last_reviewed_feedback_revision: 3
- last_reviewed_instruction: C002
- last_reviewed_instruction_revision: 2
- last_reviewed_code_head: 9204329cffd78e392bf45d73104866701f131a1c
- last_reviewed_coordination_head: 8cd4469c8df63d22023b18472fdb2876cc734a1f
- reviewed_at: 2026-11-01T13:56:39Z
- decision: C002 主体修订与阻塞说明接受；第 3 轮设计方向通过；清单第 5 项按 C003 纠正；真机与自动交接分开推进
- evidence: 最新清单、设计、POLLING_EVIDENCE.md 已实际读取；写工具测试提交及删除已核实；前次问题见 [C001 评审](coordination/C001_REVIEW_F002.md)
- device_gate: 未通过，真机仍未执行；缺设备不是通过，既有软重启不要求主动复现
- next_phase_permission: 第 3 轮设计方向通过；业务实现待第 2 轮真机验收，不再要求额外设计报告
- issued_next_instruction: C003 revision 1

### C002/F003 验收与加速决定 — 2026-11-01T13:56:39Z

- 已读取三份最新材料。主体问题已修正；设计转为复用 Session 的完成/取消仲裁，不再新建终态库。这个方向接受。
- 清单第 5 项还有可确认的错误：BACK 经过 `TaskScreenSession.mutateAndInvalidate`，成功即失效旧观察；用它制造新帧会把合法保护误判为故障。C003 给出替代步骤，删掉原示例即可，不启动新一轮设计审查。
- 已核实提交 `16af31479ddfec7597f0b6d23881dfb8d7e388f3` 新增 WRITE_TOOL_CHECK，`84e8ecd8e04860cafa4723a4bacf5b93d2d7f07f` 删除。证明仓库发生过写操作；是否来自同一个 Session 仍据 dsh 日志摘录，不冒充直接读取 Host。
- POLLING_EVIDENCE 的摘录说明 13:02 自然投递成功、文件只显示 SHA/embedded resource unsupported，随后模型重试 TIMEOUT、没有写工具调用。正文展示故障与模型超时均需记录，时间相邻不能证明前者必然导致后者。C002 revision 2 发布晚于该次投递，本次接受这一时点说明；未要求它在过去执行未来指令。
- 对固定业务 head 的协作差异仅为五份文档；PR 仍 Draft/open、未合并，业务 SHA 未变。没有重跑 CI、构建或操作手机。
- 用户明确要求加速。将真机用例分组连续进行，边测边评审；定时链路单独做最小验证，不再作为设备测试前置。真机尚无结果的事实和阶段门槛保持不变。

### C001/F002 历史验收

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
- external_agent_limit: dsh 日志摘录已定位自然投递后正文未交给模型，随后 TIMEOUT；手动写工具测试有实际提交，自动写回仍未成功。C003 只做文本读取与自然写回的最小验证，与真机工作分开；电脑/Host 仍需运行，本地 shell/构建能力未验证。
