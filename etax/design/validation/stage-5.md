# 阶段 5 · 设置重组验证

## 基线与范围

- 基线 `72277b355b6044c2810e507395cac32ed0edd7cc` / `2026110605`。阶段 4 [CI 42138100737](https://github.com/Lmin2419/eta-background/actions/runs/42138100737) 的测试与 Lint、签名构建、发布三个 job 全部 success 后才开始本阶段；全量 2522 tests / 0 failure / 0 error / 1 skipped，13 项引擎部署测试通过。
- 阶段 4 独立版本：[2026110605 / 72277b3](https://github.com/Lmin2419/eta-background/releases/tag/etax-2026110605-72277b3)，APK SHA-256 `aa397346aadc1fe44ee04cace2735e9ebb7ee9e9ee78bbdd1bd8e1f9ee74f15c`。
- 本阶段版本 `2026110606` / `3.3.5`，引擎仍为 `20261105-e49-parallel-observe`。按 04 阶段 5、03 S4 / S9；不进入阶段 6，不改业务、运行时、通知调度、引擎或持久存储格式。
- 保留 Claude 的首页真实手指滚动修复与模型胶囊 compactModelName / pillName、中间省略；本阶段没有改其生产实现。

## 旧设置项逐项迁移（42 / 42）

基于改版前 `SettingsScreen.kt` 的每一个叶子设置项核对。旧行号属于基线提交；展示条件不变，不能把当前设备不支持而隐藏的项计为删除。既有语言入口虽然未列在 S4 分组例子中，仍迁入个性化。以下所有路由、原回调、偏好键、Root/框架条件均保留。

| # | 改版前设置项 | 改版后位置 | 原能力与条件 |
| --- | --- | --- | --- |
| 1 | 模型提供商（旧 L279） | 模型与对话 | AppRoute.ModelProviders；原条件不变 |
| 2 | 默认启用思考（旧 L292） | 模型与对话 | AGENT_THINKING_ENABLED；原条件不变 |
| 3 | 自动压缩上下文（旧 L307） | 模型与对话 | AGENT_AUTO_COMPACTION_ENABLED；原条件不变 |
| 4 | 记忆（旧 L317） | 模型与对话 | AppRoute.Memory；原条件不变 |
| 5 | Subagent（旧 L329） | 模型与对话 | AppRoute.Subagents；原条件不变 |
| 6 | route_skills（旧 L341） | 能力 | AppRoute.Skills；原条件不变 |
| 7 | MCP 服务器（旧 L353） | 能力 | AppRoute.McpServers；原条件不变 |
| 8 | 角色（旧 L365） | 能力 | AppRoute.Characters；原条件不变 |
| 9 | settings_tools_list（旧 L382） | 能力 | AppRoute.Tools；原条件不变 |
| 10 | 启用网页搜索、读取与浏览器（旧 L389） | 能力 | AGENT_BROWSER_TOOLS；原条件不变 |
| 11 | 网页搜索与读取（旧 L399） | 能力 | AppRoute.WebToolsSettings；原条件不变 |
| 12 | 启用设备直达工具（旧 L411） | 能力 | AGENT_DEVICE_DIRECT_TOOLS；原条件不变 |
| 13 | 允许读取敏感设备信息（旧 L421） | 能力 | AGENT_DEVICE_SENSITIVE_READ_TOOLS；原条件不变 |
| 14 | 允许敏感设备操作（旧 L431） | 能力 | AGENT_DEVICE_SENSITIVE_ACTION_TOOLS；原条件不变 |
| 15 | 启用终端/文件工具（旧 L441） | 能力 | AGENT_TERMINAL_TOOLS；原条件不变 |
| 16 | Linux 工具环境（旧 L451） | 能力 | AppRoute.LinuxEnvironment；原条件不变 |
| 17 | capability_enhancements（旧 L466） | 权限与增强 | AppRoute.SystemEnhance；原条件不变 |
| 18 | 数字助理应用（旧 L478） | 系统助手 | 原 ACTION_VOICE_INPUT_SETTINGS；原条件不变 |
| 19 | 原生助手配置（旧 L489） | 系统助手 | 原配置/刷新/恢复/确认界面；原条件不变 |
| 20 | 电源键长按（旧 L492） | 系统助手 | POWER_KEY_ASSISTANT_TARGET；同步提交与历史；连接框架或曾连接时显示；断开后禁用 |
| 21 | 自动设置默认助理（旧 L527） | 系统助手 | ASSISTANT_AUTO_CONFIG；连接框架或曾连接时显示；断开后禁用 |
| 22 | 启用厂商助手自定义模型（旧 L544） | 系统助手 → 更多系统助手设置 | AGENT_CUSTOM_MODEL；连接框架或曾连接时显示；断开后禁用 |
| 23 | 仅以 /agent 前缀接管（旧 L554） | 系统助手 → 更多系统助手设置 | AGENT_REQUIRE_PREFIX；连接框架或曾连接时显示；断开后禁用 |
| 24 | 息屏后维持 Hey Google 检测（旧 L572） | 系统助手 → 更多系统助手设置 | HOTWORD_SELF_HEAL；连接框架或曾连接时显示；断开后禁用 |
| 25 | 锁屏唤起自动语音输入（旧 L582） | 系统助手 → 更多系统助手设置 | LOCKSCREEN_VOICE_COMMAND；连接框架或曾连接时显示；断开后禁用 |
| 26 | 亮屏唤起自动语音输入（旧 L592） | 系统助手 → 更多系统助手设置 | SCREEN_ON_VOICE_COMMAND；连接框架或曾连接时显示；断开后禁用 |
| 27 | 将 Google App 转为系统应用（旧 L606） | 系统助手 → 更多系统助手设置 | 原 Root 条件、确认框及安装器；已授权 Root 或曾使用；无 Root 点击转系统增强 |
| 28 | 手势条长按触发一圈即搜（旧 L636） | 系统助手 → 更多系统助手设置 | GESTURE_BAR_CIRCLE_TO_SEARCH；连接框架或曾连接时显示；断开后禁用 |
| 29 | 双指长按触发一圈即搜（旧 L646） | 系统助手 → 更多系统助手设置 | DOUBLE_FINGER_CIRCLE_TO_SEARCH；连接框架或曾连接时显示；断开后禁用 |
| 30 | 外观与主题（旧 L662） | 个性化 | AppRoute.AppearanceSettings；原条件不变 |
| 31 | 语音（旧 L674） | 个性化 | AppRoute.SpeechSettings；原条件不变 |
| 32 | 语言（旧 L680） | 个性化 | 原 LocaleManager / LanguagePreference；原条件不变 |
| 33 | 数据备份（旧 L683） | 数据 | AppRoute.DataBackup；原条件不变 |
| 34 | 悬浮窗权限（旧 L700） | 权限与增强 | 原 OverlayPermissionSettings.open；原条件不变 |
| 35 | 无障碍增强工具（旧 L727） | 权限与增强 | 原 ACTION_ACCESSIBILITY_SETTINGS；原条件不变 |
| 36 | 强制保持无障碍（旧 L759） | 权限与增强 | 原异步 setEnabled；pending 禁用与失败回滚；Android 14+ 且连接或曾连接；断开/请求中禁用 |
| 37 | background_notifications_title（旧 L807） | 后台执行 | 原 APP_NOTIFICATION_SETTINGS + 包名；原条件不变 |
| 38 | background_diagnostics_title（旧 L816） | 后台执行 | 原 CreateDocument JSON 诊断导出；busy 禁用；原条件不变 |
| 39 | 版本（旧 L824） | 关于 | 原 PackageInfo 版本；原条件不变 |
| 40 | 检查更新（旧 L836） | 关于 | 原 AppUpdateChecker 与结果对话框；原条件不变 |
| 41 | 问题反馈（旧 L855） | 关于 | 原上游 issues 链接；原条件不变 |
| 42 | 源代码（旧 L867） | 关于 | 原上游源码链接；原条件不变 |

新增入口不计入旧 42 项：顶部就绪卡 → 权限；权限与增强 → 权限健康；系统助手 → 更多系统助手设置（`AppRoute.AssistantSettings`，可序列化/返回栈恢复）；后台执行 → 完成提示。完成提示复用现有 `AppearanceSettings.taskCompletionAlertsEnabled`，外观页旧入口保留，两个入口修改同一个值，不新增通知行为。

## S4 / S9 核对

| 设计 | 实现与保留行为 |
| --- | --- |
| S4.1 顶部后台操作就绪 | Root、无障碍、悬浮窗、通知、引擎共 5 项；StatusRing 16dp，标题/总结；整卡打开权限页。未知值显示检查中，不能误报全部正常 |
| 引擎现状 | 仅已授权 Root 且页面 RESUMED 时读现有 diagnosticSnapshot，检查 reachable / healthy / BUILD 与内嵌版本一致；IO 执行，进入/回前台或明确刷新时检测，不轮询，不启动/部署/请求 Root |
| 检测取消 | 离页或退后台取消协程，旧结果不能回写。已发出的阻塞 socket 受现有 2 秒连接、2.5 秒读取超时约束，关闭由原 try-with-resources 保证；没有修改传输层来配合 UI |
| S4.2 八组 | 模型与对话、能力、后台执行、系统助手、权限与增强、个性化、数据、关于；组标题在 EtaListGroup 外；危险开关补后果说明；图标 Rounded / 颜色 token |
| 系统助手二级页 | 首页保留 4 个常用项；8 个次要项迁入新页。未连接/无历史时展示说明和系统增强入口；原断连禁用、历史展示、Root 二次确认不变 |
| S4.3 通用页面 | 沿用 Miuix 大标题/折叠滚动和已有列表；底部 8dp + 通用 24dp = 32dp；不向首页自绘顶栏重新接入折叠处理 |
| S9 权限卡 | 缺项优先，保留所有旧 permission ID 与 OpenItemAction 路由；每项图标块、名称、状态、说明、Tonal 操作。已授权不重复显示开启按钮，仍可点击卡片进入原系统设置 |
| S9 首次引导 | 沿用已验收的 S1.2 第 5 点：无内容会话且核心权限缺项时，首页提示 → 设置就绪卡 → 权限页；不增加轮播或引擎后台轮询 |
| 权限返回 | 原 Root 变化、Activity ON_RESUME 刷新保持；进入设置另调用同一权限 store 刷新。悬浮窗旧入口始终可点击，未改 fallback |
| 文案 | 新增文案三语同步；日历/勿扰原 store 文字在 UI 使用本地化资源，不改检测器 |

设置、更多助手与权限页显式使用 canvas；公共 MiuixScaffoldPage 新增可选 containerColor，默认仍与 [Miuix 0.9.4 Scaffold](https://raw.githubusercontent.com/miuix-kotlin-multiplatform/miuix/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Scaffold.kt) 相同的 surface，其余页面不变。

## 页面前后对照

| 页面 | 改版前 | 改版后 |
| --- | --- | --- |
| 设置顶部 | [旧长列表顶部](stage-5/settings-before-top.png) | [五项就绪卡 / 模型与对话 / 能力](stage-5/settings-stage5-after-top.png) |
| 设置底部 | [旧分组底部](stage-5/settings-before-bottom.png) | [个性化 / 数据 / 关于](stage-5/settings-stage5-after-bottom.png) |
| 系统助手次要项 | 混在设置首页，按上表 22–29 保留原条件 | [更多助手独立页](stage-5/settings-stage5-assistant.png)，展示历史已连接但当前断开的禁用状态 |
| 权限健康 | 原权限列表，同一检测 store 与 action ID | [缺项优先的权限卡](stage-5/permissions-stage5-light.png)，额外只读引擎状态 |
| 深色 / 200% | 同一主题与字号环境 | [设置底部](stage-5/settings-stage5-dark-200.png)、[权限顶部](stage-5/permissions-stage5-dark-200-top.png)、[权限底部](stage-5/stage5_permission_dark_200.png)，真实拖动可达 |

## 验证与限制

| 检查 | 实际结果 |
| --- | --- |
| 原项源码对照 | 42 / 42 原叶子调用保留，仅标题本地化、后果摘要、图标/色彩变化；所有原回调/偏好键、SystemizerConfirmDialog 及后续 helper 与基线一致 |
| 自动化定向回归 | 28 tests，0 failures / errors / skipped。新增 5 个设置测试 + 4 个权限/就绪测试；包括 42 项在真实渲染页面上逐一滚动可达、8 组、首页/更多/权限跳转、冷启动与断框禁用、完成提示共用原偏好、引擎取消/刷新旧结果不覆盖、权限 ID 保留与已授权卡片点击 |
| 手势回归 | 使用 MotionEvent DOWN / MOVE / UP；设置长页到底/回顶、更多页、权限页、深色 200%；连同 AgentAppShellScrollTest 和阶段 4 侧栏/缩略图/新建按钮回归通过，无 dispatchRawDelta 代替手指 |
| 接线检查 | 12 项通过；引擎源和内嵌 BUILD 未改 |
| 文案 | 新增 35 键，英文/简体/繁体齐全，无重复键；角色复用既有三语标题，日历/勿扰按钮复用原三语动作文字 |
| Lint / Debug 构建 | 通过；0 error/fatal，448 warning、1 hint；未新增 lint 抑制或放宽检查 |
| 范围与格式 | git diff --check 通过；生产改动限于 UI / 路由 / 三语资源与版本说明 |

初验问题均在提交前处理：Robolectric LocaleConfig fixture；权限优先级断言误把顶部卡的“引擎”文字当作列表行；真实拖动结束后等待惯性稳定，并等待原 DataStore 流确实提交再断言开关。没有放宽断言或改生产存储来掩盖问题。截图发现白色页面底与卡片重合，已仅为本阶段三个页面显式指定 canvas；引擎状态与无障碍读屏说明使用“未就绪”，避免误称未授权。

截图来自实际 Compose native graphics 绘制及合成状态，不是手机截图。before 使用基线同一渲染器/密度；测试只在固定环境复现触摸和显示，不替代 ColorOS 真机验收。Robolectric 未加载 AGP 生成的 LocaleConfig，在测试里提供三语 LocaleConfig shadow；生产语言逻辑没有为此改动。

本记录随本阶段单次提交生成，不预填未运行的 CI。最终交付必须等本提交测试与 Lint、签名 APK、Release 三项全部成功，并核对版本、源码和 APK 摘要。Windows 平台差异见阶段 3，完整 Linux 回归由 CI 执行。

子任务仅使用 external_model 的 tencent / `cn:deepseek-v4.1-flash`：迁移核对、生命周期复核、设置 UI 测试和权限测试草稿。主会话核对代码、实际执行测试、检查截图及发布证据；外部回复不视为测试通过。

## 手机验收清单

1. 覆盖安装本阶段签名版，在关于核对 `2026110606`；原服务商、模型、语言、开关和数据保持。首页上下真实拖动和长模型名胶囊仍正常。
2. 设置从顶部拖到底再返回，确认 8 组与上表 42 个旧项（条件项按设备状态检查），依次进入服务商、工具、Skills、MCP、角色、Linux、外观、语音、语言、备份，返回位置与手势正常。
3. 顶部卡五项与实际状态一致，缺权限/引擎未运行时不误报全部正常。点击整卡进入权限页；未授权优先、已授权仍可点，授权后返回刷新。确认设置页不会自行拉起引擎或弹 Root 授权。
4. 系统助手首页 4 项与更多页 8 项可达；检查框架连接/断开后的禁用和历史展示，Root 未授权的系统化入口提示及跳转，原生助手与系统化确认/取消不变。无需为了验 UI 实际安装模块。
5. 在后台执行关闭/打开完成提示，再进外观确认同一值；原后台通知设置、诊断导出可用。检查敏感操作摘要、强制无障碍请求期间禁用与失败反馈。
6. 浅色、深色、200% 字号各过设置/更多助手/权限，真实上下拖动能到底，按钮可点击；从系统授权页返回、锁屏再回前台状态刷新。
7. 顺带验阶段 4 收尾：侧栏拖开/纵滚/拖回、需要你状态环；双任务三处状态一致；实时条主体查看与暂停不串；缩略图离屏停止/回屏恢复；常用新建按钮圆形收起后仍可点、桌面快捷方式照常。

阶段 4/5 本次新增细节的真机验收：待用户执行。阶段 3 已验收不替代当前版本。
