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
5. 用“指令编号 + 反馈编号 + 业务 head”去重。相同指令不重复执行；无变化不刷时间戳、不重复提交“不变”记录，也不重复启动 CI。
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

- instruction_id: C001
- issued_at: 2026-11-01T09:44:40Z
- status: READY
- target: dsh
- scope: 第 2 轮真机验证交接；不修改业务代码
- candidate_head: 9204329cffd78e392bf45d73104866701f131a1c

### C001：建立自动交接并提交可读取的测试材料

按顺序执行：

1. 读取本文，在“执行反馈”确认收到 C001。配置平台原生定时读取（若支持，建议每小时一次）；记录机制、任务 ID 和是否会在对话结束后继续。避免另外部署不必要的常驻服务。
2. 将已生成的 `REGRESSION_CHECKLIST.md` 脱敏后上传至协作分支 `etax/coordination/REGRESSION_CHECKLIST_9204329.md`。保留 20 项具体步骤、预期、实际、错误码、证据引用及“未执行”状态；不能只贴 Windows 本地路径。
3. 提交一份简短设备就绪说明（可写在清单开头）：候选 APK 身份、如何确认设备连接/系统版本/已装版本、用户需要完成的最少动作、哪些步骤需要用户在场。不要为了检测连接自行安装 APK、改设备设置或触发已知软重启路径。
4. 无设备时标记 BLOCKED_DEVICE，仅记录一次具体缺口。可准备 `etax/coordination/ROUND3_DESIGN_DRAFT.md`：围绕统一任务状态列出现有状态来源、拟改文件、状态迁移、兼容风险及有意义的测试；只做设计，不实施、不改 PR 业务 head。这份设计完成后不要循环扩大范围。
5. 用户提供实际设备测试条件或结果后，基于固定候选逐项填写真实结果，保留原始错误和脱敏证据。凡安装、设备操作需要其配合的，明确最少步骤，不伪装已自动完成。测试优先从 App 内进入；长按电源键只记录未解决问题，不作为当前常规回归的强制触发动作。
6. 材料有新增后递增 feedback_revision，置 REVIEW_READY（同时在 device_status 保留设备阻塞），等待 ChatGPT 写入下一编号指令。不要要求用户搬运报告。
7. 第 2 轮设备结果未验收前，禁止进入第 3 轮业务实现。若发现候选缺陷，先记录复现；由评审指令明确返修范围和新候选，不能把旧 APK 的结果算给新 head。

完成标准：本文有真实执行回执和轮询能力说明；远端可读取完整清单与证据；设备缺口明确；没有新业务提交、合并或发布。

## 执行反馈（dsh 维护）

- acknowledged_instruction: 尚未确认
- feedback_revision: 0
- status: 尚未开始 C001
- updated_at: —
- implementation_head: 9204329cffd78e392bf45d73104866701f131a1c
- device_status: BLOCKED_DEVICE（依据用户转交的 dsh 报告）
- polling_mechanism: 待 dsh 核实
- polling_task_id: —
- polling_survives_session_end: 未知
- deliverables: 待上传测试清单
- actual_tests: 20 项未执行（无设备）
- blockers: 尚无可连接测试设备
- question_for_reviewer: —
- next_action: 执行 C001

每次新增反馈补充：已做事项、变更文件/提交、验证命令和实际结果、证据链接、未执行项与原因。请勿修改“评审记录”来给自己验收通过。

## 反馈历史（dsh 维护，新增条目）

尚无执行回执。

## 评审记录（ChatGPT 维护）

- last_reviewed_feedback_revision: 0
- last_reviewed_instruction: C001
- last_reviewed_code_head: 9204329cffd78e392bf45d73104866701f131a1c
- decision: 代码与自动化通过；真机待验证；协作交接待 dsh 回执
- evidence: 2026-11-01 已重新读取 PR #45，确认其 head、Draft 状态和候选资料与用户交付一致。
- device_gate: 未通过，因未执行；不是测试失败，也不是可忽略项
- next_phase_permission: 第 3 轮仅设计准备，业务实现未放行

后续每次评审写明反馈编号、实际审查的业务 SHA、证据、通过/返修/阻塞原因及下一指令。读取到新 head 后重新审查差异与相关验证，不能沿用旧 head 的通过结论。

## 调度信息（ChatGPT 维护）

- requested_cadence: 每小时检查一次
- automation_status: 已创建并启用（每小时；Asia/Shanghai）
- automation_id: 6ae70a9730d081919646705ba469f1c2
- behavior: 读取本文、PR 和必要证据；有新反馈则验收并写回下一步；无变化不提交、不重复通知。
- notify_user_when: 达到阶段里程碑、出现新风险或确实需要设备/权限/产品决策；相同阻塞不重复催促。
- external_agent_limit: 定时读取不等于 dsh 自动被唤醒；需以执行反馈中的实际轮询机制为准。
