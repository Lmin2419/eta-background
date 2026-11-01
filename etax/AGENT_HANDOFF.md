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

- instruction_id: C004
- instruction_revision: 4
- issued_at: 2026-11-01T15:28:29Z
- revised_at: 2026-11-01T16:22:21Z
- status: REVIEW_READY
- target: ChatGPT
- current_plan: [当前实施方案](coordination/INTEGRATION_PLAN_CURRENT.md)；后续在同一文件修订，不再生成重复方案
- mode: 用户已要求由 ChatGPT 重新接手；手动推进，不恢复每小时自动检查
- candidate_head: 2ada1360dd56a72de46cd57dc627e149c37efd9a
- previous_candidate: 9204329cffd78e392bf45d73104866701f131a1c；U001–U003 只按原测试上下文保留，不自动算作新 APK 全项通过
- follows: C004 revision 3；U001–U003 保留原测试上下文
- known_device_results: 手机独立测试可用，U001–U003 可见场景已确认；文档上方“已确认基线”的无设备状态属于早期历史，最新结果见修订清单与本节。
- scope: 按用户最新取舍调整上游整合：统一任务状态、小窗预览和接管、参考上游息屏策略及电源键入口；保留当前引擎和输入隔离
- executor_handoff: dsh 后续读到本文时，不再执行已被本条取代的 C003 工作或修复定时链路，不新建业务提交；保留既有反馈与证据。ChatGPT 未直接访问或停止 dsh Host，不能宣称其定时器已关闭。

### 用户最新取舍

1. 前几个版本已经具备的通知、流光、继续指令、双任务等只作为保留基线，不再列为本次新开发成果。
2. 第 3 轮统一任务状态、第 4 轮小窗预览及用户接管继续推进。主要借鉴上游可展开的小画面、点按/滑动交互与动效。
3. 保留本分支单个 VDrive、每任务屏幕和观察隔离、两个后台任务、同 App 串行、现有流体云和每任务通知；不照搬上游单例 activeDisplayId 管理来覆盖这些能力。
4. 应用分身专门适配移出本轮范围。
5. 用户重新提出息屏持续执行，授权方向改为参考上游实现。先前“息屏开发暂停”不再作为永久产品限制；当前候选仍实际执行息屏暂停，尚未改代码或启用新行为。不得把取消暂停等同于锁屏联网已解决。
6. 电源键与 LSP 路径以实际源码对比为依据。相同文件不重复覆盖，不以拷贝上游宣称软重启已修复；不扩大系统 Hook 范围或主动触发软重启测试。

### 已核实的上游与本分支差异

上游源码对比基线为 v3.4.0 / e90922a4490389f524a10c6acebbcca336883935；下面列出的对比基于原候选 9204329。当前业务候选见本节 candidate_head。

- 上游 VirtualDisplayStageCard.kt 已有微缩画面、展开视窗、点按/长按/滑动及全屏入口；VirtualDisplayController 维护单个活动显示屏和预览快照。
- 上游预览 UI 更新按展开约 30ms、收起约 160ms 节流；onFrameAvailable 仍执行 Bitmap 解码/复制，不能仅据发布说明称其整条链路零拷贝。只借鉴交互与按可见性调节刷新，按本引擎能力设计帧传输。
- 上游触摸回调先请求接管/暂停，随即分发手势；本分支整合时必须等待目标任务已暂停、在途操作已退出后才接受手动输入，恢复前重新观察。
- 上游 handoffToMainDisplay 在迁移尝试后固定等待 260ms 并释放显示屏；保留本分支窗口就绪及旧屏为空的确认流程，动画不作为释放成功证明。
- 两边 PowerHooks.kt 的 blob 均为 fe55ccdffe447d1914edbe791e19aeb0af00ea00；AssistantManager.kt 均为 0a4dfc4d7583a773933134dec4b215777822fa61。SystemServerHooks 的上游新增项是 VirtualDisplaySystemHooks，并非已查明的电源键崩溃补丁。
- 本分支 VDriveWorker.restoreMainFocus 有针对主屏 IME/焦点被虚拟显示影响的条件恢复处理；上游 RootShellDeviceController.pressKey 在虚拟屏无 Root 路径仍可能调用全局无障碍按键。这是可审查的风险路径，不能据此唯一确定用户键盘收起事件的原因。
- 上游 Runtime 没有本分支 ACTION_SCREEN_OFF 即暂停全部活动任务的处理；上游虚拟显示标志包含 OWN_DISPLAY_GROUP、ALWAYS_UNLOCKED、OWN_FOCUS、STEAL_TOP_FOCUS_DISABLED。本分支已有独立显示组/焦点标志但未启用 ALWAYS_UNLOCKED。已查上游 Runtime、虚拟显示与 Manifest 未发现专门的息屏网络保活机制；keepScreenOn 也不能等同于深度休眠联网保障。

### 通知内三级预览：用户新增的具体交互（2026-11-01T15:30:34Z）

用户希望把上游胶囊的小手机/小画面交互融合到现有通知，不再增加常驻悬浮胶囊。目标顺序：

1. **收起**：现有任务通知显示状态，右侧是小手机形状的图标/缩略画面；不额外占一条通知。
2. **展开**：原任务通知展示小屏画面预览和任务状态。此时查看不暂停 AI；点预览只放大，不把这一点击穿透给后台 App。
3. **再点画面**：进入更大的预览/操作视图，再按既定暂停确认流程手动接管，或选择打开原 App 到主屏。关闭大预览不会取消任务。
4. 两个任务各自绑定通知、画面及控制；完成卡片保留“打开 ETA 会话 / 打开 App / 继续指令”三个独立按钮，不用第四个按钮挤掉现有入口。

**目标与已证实的平台能力分开：**

- 已读本分支 AgentExecutionService.notification：当前使用 Notification.ProgressStyle，并请求 promoted ongoing；现有 contentIntent 的运行/完成跳转与三个 action 分离。未来预览入口须使用各任务唯一身份，不能直接复用当前“打开 App”按钮的 PendingIntent。
- Android 官方 Live Updates 要求标准或指定样式，明确不允许 customContentView/RemoteViews；支持列表没有 BigPictureStyle。不能为了嵌入大预览直接替换成自定义通知而默认流体云仍存在。
- 普通通知允许大图或自定义收起/展开布局，但这不证明 ColorOS 流体云的紧凑胶囊也允许任意右侧图片/交互画布。系统决定原生布局、位置及展开行为，setLargeIcon 等只作为待验证的标准接口候选。
- 此次未取得支持任意画面嵌入 ColorOS 流体云的厂商接口证据；不据此断言所有厂商路径都不可能，也不能承诺已能实现完整三级原生通知。
- 第 4 轮先验证通知样式能力：保证现有流体云与每任务通知不重复、不丢失、三个完成入口及内联回复可用，再确认右侧缩略图和展开预览实际如何呈现。静态/低频预览与连续可交互画面分开，不通过高频重发系统通知模拟视频。
- 若原生通知不能承载展开预览，候选兼容方式是通知入口打开临时的 App 预览面板，再点放大；它属于应用承接，不能宣传为“画面全部嵌入原生通知”。需要调整用户交互目标时向用户展示实际差异，不静默替换需求或新增系统 Hook。
- 保持当前模型观察帧不可变、主屏输入法隔离及锁屏通知隐私；通知展示不得反复生成新的模型观察或把预览点按误注入后台任务。

参考：[Android Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update)、[自定义通知](https://developer.android.com/develop/ui/views/notifications/custom-notification)、[展开通知](https://developer.android.com/develop/ui/views/notifications/expanded)。本条是交互要求与可行性核对，不是已实现记录。

### 具体整合顺序与完成标准

用户已确认修改方案并继续。完整可执行范围、组件接线、通知能力验证、接管状态和交付标准见 [INTEGRATION_PLAN_CURRENT.md](coordination/INTEGRATION_PLAN_CURRENT.md)，以下为摘要。原业务分支计划保留基线；第三至第五轮的最新要求以该方案及用户后续指令为准。用户已发出“开工”指令，已实施下方第 2 轮衔接返修；既有阶段门槛不伪造通过。

1. 收尾第 2 轮尚未覆盖的技术核验；U001–U003 已确认的手机场景不重复。保留原验收门槛，不把功能取舍更新写成技术验收通过。
2. 第 3 轮复用 AgentRuntimeSession.complete/cancel 的结果权威，统一会话、通知及后续预览的只读状态；不新建第二套终态库，不新增设计报告循环。
3. 第 4 轮把上游的视窗与动效适配到本分支任务：
   - 采用上方用户指定的“通知收起小手机 → 展开小预览 → 再点放大”目标，先核验原生通知/流体云能力；不重建常驻胶囊、不发重复卡片。受限时明确区分原生通知展示和 App 预览承接，不把降级方案当作需求已完整实现。
   - 两项任务状态仍各自展示，展开的小窗明确绑定选定 runId/sessionId/displayId；关闭/切换预览不得停止或切换另一个任务。
   - 默认只看画面，不抢键盘焦点；接管时只暂停本任务，等在途动作停止后开放点按/长按/滑动；继续交给 AI 前重新观察。
   - 预览读取独立的显示帧，不覆盖模型已发布的 observation_id/frame_id，不通过高频 screen_shot 调用不断写文件或改变模型坐标依据。窗口关闭时停止额外预览刷新，避免为两个隐藏小窗持续编码。
   - 全屏接管继续迁移原 App 页面，使用现有交还确认；保留完成卡片三个入口及通知内继续指令。
   - 手机主屏输入文字时，后台启动/滚动/输入、仅打开或关闭预览不得主动收起主屏键盘；用户明确进入 ETA 文字输入或全屏切 App 的操作另行区分。
4. 息屏执行单独适配上游差异，复用现有 Runtime/VDrive，不再另造常驻保活系统。分开验证息屏后虚拟屏能否观察/输入、模型请求能否联网、两任务是否隔离、取消与亮屏恢复是否正常；网络或显示不可用时保留明确状态，不盲目重放不确定操作，不通过点亮主屏或循环请求冒充成功。
5. 电源键排查集中在入口接线、包名/组件识别、相关差异和实际崩溃证据；已相同的 PowerHooks/AssistantManager 保留，不把上游虚拟屏权限 Hook 整套引入来试错。

### 开工后的第 2 轮衔接返修 — 2026-11-01T16:22:21Z

- 用户发出“开工”后，实际检出 PR head 9204329，发现并修复混用新旧工具时的观察生命周期缺口；[提交 2ada136](https://github.com/Lmin2419/eta-background/commit/2ada1360dd56a72de46cd57dc627e149c37efd9a)，6 个文件，+208/−10。
- 旧 screen_* 输入、重新打开或结束屏幕之前先失效本任务的原生观察，覆盖操作成功、结果不确定、非 JSON 及抛异常路径；只读查询不改已发布 frame_id。screen_state 等返回丢屏/系统重启/接管或不确定结果时也统一失效。
- 后台节点和截图观察要求明确、非主屏且外层/几何一致的 displayId；拒绝缺失、主屏 0 和矛盾身份，不放宽坐标或观察保护。
- 新增 8 项测试。修复前 9 项路由测试中 6 项失败；修复后定向 48 项全部通过。Linux 全量 2,159 项：0 失败、0 错误、1 跳过。12 项接线与 8 项真实 shell 引擎部署回归通过。Lint 和 Debug 构建通过；Lint 为 0 错误、386 Warning、1 Hint。
- 初次引擎测试因当前命令 PATH 不含已有 JDK/mksh/BusyBox/Toybox 而失败；配置已有工具后 8 项通过，没有跳过用例或修改测试以掩盖环境错误。
- [原候选手机清单](coordination/REGRESSION_CHECKLIST_9204329.md)已同步 U001–U003，取消 USB/ADB 前置要求，纠正用 BACK 验证静态帧的错误步骤；没有把未取得的内部证据填通过。用户已确认场景不重复测试。
- 本次没有设备操作或新 APK 真机结果。剩余设备门槛主要为新/旧工具路径及观察合同、同 App 串行、旋转/恢复/接管、输入结果不确定等；已询问完整安装版本用于关联既有结果，未获得独立核验。
- 仅修复第 2 轮基础入口；第 3 轮统一状态、第 4 轮通知预览和接管、第五轮息屏适配仍待推进。依据当前方案保留阶段门槛，不合并、不发布、不改包名/签名/版本号、不恢复定时，也不要求 dsh 继续实施。
- 该 head 的 [PR Check](https://github.com/Lmin2419/eta-background/actions/runs/41325658764) 与 [Etax Build](https://github.com/Lmin2419/eta-background/actions/runs/41325654694) 已触发，2026-11-01T16:22Z 查询仍在运行；上述通过结论为本地 Linux 实际结果，不代填 CI 结论。

### 当前边界

本次业务 head 已更新为 2ada136，第 2 轮衔接返修已实施。第 3–4 轮、小窗和息屏适配尚未实现。第 2 轮剩余验收未通过前不直接实施第 3 轮；不合并 main、不发布、不换下载、不改包名/签名、不远程安装或操作手机。软重启及整机冷启动白屏均不能宣称已修复。

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
- last_reviewed_code_head: 2ada1360dd56a72de46cd57dc627e149c37efd9a
- last_reviewed_coordination_head: 88758f7f353e43be174964f1afa4ff7e9c32104c
- reviewed_at: 2026-11-01T16:22:21Z
- decision: 第 2 轮衔接返修 2ada136 已提交并通过本地全量测试、Lint 与构建；CI 运行中；真机门槛仍未全部通过，第 3 轮未实施
- evidence: 已实际读取原业务源码、对照返回结构，执行修复前/后回归并核对远端 2ada136 的 6 个文件 diff；详细验证与 CI 链接见当前指令。历史 C001/C002 证据保留如下。
- device_gate: 部分手机可见功能已获用户实测通过（U001/U002/U003）；通知内继续、双任务通知同时运行、停止一个不影响另一个均已确认；已确认场景不重复测试，完整第 2 轮验收仍未完成
- next_phase_permission: 第 3 轮设计方向通过；业务实现待第 2 轮真机验收，不再要求额外设计报告
- issued_next_instruction: C004 revision 4（第 2 轮衔接返修已提交；ChatGPT 手动接手；dsh 不新增实施）

### 开工与基础返修交付 — 2026-11-01T16:22:21Z

- 第 2 轮新增 2ada136，修复旧工具输入后的原生观察失效和后台 displayId 校验；源码、更新说明、定向及全量验证已完成，详见当前指令。
- 本轮不重新验收全部 20 项，也不继承旧 APK 的全部实测；已经取得的 U001–U003 结果一次同步到清单，去掉 ADB 前置并改正第 5 项。
- 当前实施方案已标记实际进度；第 3–5 轮功能未实施、未合并、未发布。下一步只补剩余技术证据和改动路径验证，不重写设计报告、不恢复定时。

### 当前实施方案整理完成 — 2026-11-01T15:36:23Z

- 用户确认通知三级交互方向并要求修改方案、继续推进；已将第三至第五轮整理为 [当前实施方案](coordination/INTEGRATION_PLAN_CURRENT.md)。
- 明确统一状态的接线位置、通知原生承载验证、预览/手动控制/移到主屏三者区别、结束后保留页面的所有权、帧缓存与模型观察隔离、息屏适配及电源键排查的验收标准。
- 应用分身专项已移出范围；旧版已有能力只列保留基线。U001–U003 不重测；缺少的技术证据不因接受方案而填通过。
- PR head 仍为 9204329；方案只写协作分支，无新增业务实现、合并、发布或设备操作。下一步按该方案 A→B→C→D→E 推进，不再要求重写第三轮设计报告。

### 通知预览交互细化 — 2026-11-01T15:30:34Z

- 用户将第 4 轮交互明确为通知收起时右侧小手机标志、展开通知显示小屏预览、再点放大；已记录到 C004 revision 2。
- 实际核对本分支通知构造及 Android 官方 Live Updates、自定义/展开通知文档。现有 ProgressStyle 与任意自定义布局不能直接等价替换；流体云保留和三级预览能否同时实现仍需平台能力验证。
- 没有新增业务提交、安装测试或重新开启定时；未把设计目标标成已经实现。

### 用户重新接手与整合范围调整 — 2026-11-01T15:28:29Z

- 用户要求由 ChatGPT 接手，并明确第三/四轮必要；重点借鉴上游小窗预览、交互接管及息屏实现，取消本轮应用分身专项适配。保留后台版不干扰主屏输入法的体验要求。
- 已实际读取上游最新发布、main/树、预览、Runtime、输入、虚拟显示及系统 Hook 源码，并与业务 head 9204329 对照；主要事实与验收要求见 C004。不能将上游实现方向当成本分支已完成的新功能。
- PR #45 仍为同一 Draft/open 候选，U001–U003 不失效；没有重跑构建或改业务代码。ChatGPT 每小时检查保持关闭；外部 dsh 调度未直接操作。
- C003 revision 5 的执行工作被 C004 接替；既有 dsh 反馈和历史原样保留。

### U003：停止一个任务不影响另一个 — 记录于 2026-11-01T14:55:10Z

- 用户原文：“然后你让我做这个测试，我已经通过了。我停止掉另一个，另一个也没卡住任务，都正常。”
- 对应 U002 中两张任务通知同时运行后，停止其中一个任务、观察另一个是否继续的测试。用户现已确认停止动作完成，另一个任务没有卡住且正常运行；用例 8 的本次可见取消隔离场景记为用户实测通过，不再重复。
- 用户没有明确指出最终停止的是计算器还是设置任务，也没有提交 runId、displayId、工具明细或日志；不补造这些信息，不额外断言另一个任务的最终完成时间，不据此认定全部内部隔离与观察协议已验证。
- 本次实际读取 PR #45：仍为 Draft/open、未合并，head 仍为 9204329cffd78e392bf45d73104866701f131a1c；测试继续按该候选上下文关联，已装 APK 身份仍无独立读取证据。没有业务代码变更，无需重跑构建。
- dsh 下次读取后将 U001–U003 对应结果一次同步到已有清单即可，保留其余未覆盖项；不新写阶段报告，不让用户重新转发，也不把执行反馈历史中的“当时未执行”改成伪造的 dsh 实测。第 3 轮业务实现门槛不因此自动解除。
- 本次同时核实 ChatGPT 每小时任务 is_enabled=false、next_run_time=null，并同步下方调度信息。未改动 dsh 的外部任务。

### U002：通知内继续成功；双任务通知同时运行 — 记录于 2026-11-01T14:46:51Z

- 用户原文：“第一项没问题，成功。第二项我照你说的做了，是在通知里面都显示运行中 然后你说再停止计算机任务，我没懂什么意思。”
- 第一项是从完成通知直接“继续指令”，要求在计算器里把前次结果再加 10（预期 5545）。用户确认成功，用例 15 的这次可见操作记为用户实测通过，不要求重复。
- 第二项为两个不同会话分别后台计算 200＋300 和查看设置“显示与亮度”，各保持任务 90 秒。用户确认两张通知同时显示运行中；这证明可见并行状态出现，不证明各自 display/观察/输入隔离的内部条件已经核验。
- 用户尚未执行或至少尚未确认“停止计算器任务”，用例 8 取消隔离仍未执行；另一任务是否最终完成也未得到结果。不能把“第一项成功”套用于全部第二项。
- 已澄清：展开计算 200＋300 的那张任务通知，点它自己的停止按钮，让设置任务继续。固定源码 AgentExecutionService 为单任务卡片绑定 ACTION_STOP_RUN + runId，总服务通知是另一路径；只操作单任务卡片。卡片已完成时不再测试该次取消。
- 证据来源为用户手机观察，未取得工具明细或日志。dsh 引用 U002 同步已有清单即可，不代造测试、不重做已确认的通知内继续。

### U001：用户手机实测反馈 — 记录于 2026-11-01T14:39:07Z

- 反馈原文：“你说的这些都没问题，都正常。”
- 对应上一条给用户的测试：固定候选下载说明后，从 ETA 图标启动，要求后台打开计算器计算 123×45，发送后返回桌面；检查后台不中断/不抢主屏、结果 5535、完成通知“打开 ETA 会话”到对应记录、“打开 App”后计算器能正常显示并点击。
- 验收：上述可见功能在用户本次手机场景下通过，证据类型为用户观察。用例 16/17 的本次场景通过；不能推广为全部应用、所有技术协议或 20 项全部通过。
- 版本说明：报告按刚提供的候选 9204329 测试上下文记录；用户未单独提供版本页/包哈希，评审没有独立读取已装包。未收到工具明细，无法判断实际采用原生 GUI 还是旧 screen_* 路径。
- 待补本组没有覆盖的通知内继续指令、双任务实际重叠/取消隔离；其他原清单技术项状态不变。第 3 轮业务实现尚未放行。
- 本记录由用户直接反馈形成，未修改 dsh 的执行反馈或虚构其测试。dsh 可引用本条同步清单，用户无需再转发一次。

### 用户设备条件更新 — 2026-11-01T14:16:57Z

- 用户确认能在手机安装软件，无法连接运行 dsh 的电脑。这是具备手机独立测试条件，不是“没有设备”；未声称已安装固定候选或完成任何测试。
- 已核验构建 run 41277041172 的工件 13072382013 仍未过期，归属 head 9204329。无需新构建或改发布链接。
- 当前指令调整为 C003 revision 2：先做可观察的手机测试，再按工具详情核验技术项。缺 ADB 只影响依赖该证据的项目，不阻塞其他真机结果；证据不足仍如实记录。
- 执行反馈区仍是 dsh 的原回执，未代写其新结果。后续 dsh 应依据本条用户事实更新设备条件说明，不能把“已准备手机”写成“已通过”。

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

- requested_cadence: 原每小时检查；用户已取消本侧定时检查
- automation_status: 已停用；实际查询 is_enabled=false，next_run_time=null（2026-11-01T14:55Z 核实）
- automation_id: 6ae70a9730d081919646705ba469f1c2
- behavior: 后续由用户消息或明确手动请求触发读取与评审；不再每小时触发，不自行恢复定时。无变化不提交、不重复通知。
- notify_user_when: 达到阶段里程碑、出现新风险或确实需要设备/权限/产品决策；相同阻塞不重复催促。
- external_agent_limit: 用户已要求 ChatGPT 接手；C004 取代 C003，不再修复或依赖 dsh 定时交接。未直接操作 dsh Host，其任务是否仍启用未知，不能声称已关闭；dsh 后续读到 C004 时只保留已有证据、不新增实施。
