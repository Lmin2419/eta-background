# 阶段 2 验证记录

## 版本与边界

- 基线：阶段 1 提交 `65daec0828af5253fe859069dda0844dd398d868`，versionCode `2026110503`；[阶段 1 CI 已全绿](https://github.com/Lmin2419/eta-background/actions/runs/42035691985)。
- 本阶段：`2026110601` / `3.3.5`，按 [04 · 阶段 2](../04-implementation-plan.md) 换装公共组件。
- 引擎仍为 `20261105-e49-parallel-observe`。运行时、模型请求、任务调度、通知协议、存储模型、路由定义及导航分发未改。
- 这份逐页对照是对基线源码与当前组件调用的核对。截图是 Robolectric 实际绘制的公共组件样例，**不是每个业务页面的截图，也不代替真机验收**。
- 用户要求只换外观，所以保留现有窗口/浮层宿主、关闭生命周期、搜索回调、选择回调、嵌套滚动和 Sheet 拖拽判定；未迁移首页模型入口、任务中心、设置分组或系统浮层。

## 公共组件清单

| 组件 | 换装前 | 换装后 |
| --- | --- | --- |
| EtaCard / EtaPreferenceGroupItem | 24dp 圆角，默认卡片内边距 0；分组首尾圆角 | 20dp 圆角、默认 16dp 内边距；浅色 e1、深色 outline。懒列表继续逐项组合，首尾圆角和连续侧边线。移除既有卡片重复的内部 16dp |
| EtaListGroup / EtaPreferenceGroup | 原公共分组包装器，组间 16dp | 新 EtaListGroup 承接旧包装器，组间 24dp，组外 caption 标题，标题到卡片 8dp |
| EtaPreferenceRow / EtaPreference / EtaArrowPreference | 前导图标 24dp、间距 16dp，正文起点 56dp；行高至少 52dp | 32dp 图标底块 + 12dp 间距，正文/分隔线起点 60dp；56dp / 有摘要 64dp 起，自适应换行；尾部继续保留最大宽度和优先布局 |
| EtaIconTile / EtaPreferenceIcon | 裸色图标 | 32dp 分类色底块、10dp 圆角、20dp Rounded 图标；浅/深色底分别 12% / 20%，分类色引用 token |
| EtaSwitch / EtaSelectionPreferences | 44×24 缩放开关，默认单选/复选配色 | 52×32 开关、白色滑块、accent/outlineStrong 轨道；单选/复选引用 token，原控件手势及行级语义保留 |
| EtaButton（替代 EtaTextButton） | Miuix TextButton 包装，44dp 高、22dp 圆角 | 48dp/full，Primary/Tonal/Secondary/Ghost/Destructive/Small；按压 0.97、8% 叠色、默认禁用 38%；加载时保留文字测量并用 16dp 单色环；旧显式 colors、尺寸、interactionSource、indication 继续传递 |
| EtaIconButton | Miuix 默认 40dp 点击区域 | 40dp 视觉、至少 48dp 目标，默认 textSecondary；保留图标描述与原点击闭包。返回图标 Rounded ArrowBackIosNew 20dp，保留 RTL |
| EtaChoiceChip / ToolChip | 描边选择胶囊 / 小矩形工具标签 | 32dp 选择胶囊、选中勾选图标；ToolChip 为 24dp 胶囊，可显式传状态显示 12dp StatusRing，既有调用不推断或改变状态 |
| EtaSearchField | 默认 InputField/SearchBar，45dp、17sp、Miuix 图标 | 44dp sunken 胶囊、14/20 字体、Rounded 搜索/清空、聚焦 accent 描边；沿用字符串状态、Search IME 与原 onSearch |
| EtaSegmented | 默认 TabRow | 36dp sunken 轨道、3dp 内边距、raised 滑块与弹簧；减少动画时短过渡；选中下标仍由页面持有 |
| EtaWindowDialog / EtaOverlayDialog / MiuixDialogActions | 默认标题排版和两侧等宽按钮 | 宽至多 360dp、屏边距 24dp、raised/28dp、内距 24dp；20/28 左标题、14/20 正文，右对齐 Ghost + Primary/Destructive 44dp；大字时动作换行 |
| EtaSheet | 默认 OverlayBottomSheet | 同一宿主换 raised/28dp/e3、36×4 手柄和左对齐标题；原手柄 24dp 手势区与拖拽/返回/嵌套滚动保留 |
| EtaToast | 无此公共 Compose 提示条 | 44dp 反色胶囊、3s、可选操作、短进退场；尊重系统无障碍推荐时长。只提供组件，已有 Android Toast/通知不替换 |
| ListEmptyState / SectionHeader | 默认 Miuix 字体、48dp 空态图标 | 56dp 分类底块、headline/subhead、规范间距；分组标题使用 caption/textTertiary |
| AdaptiveTopAppBar / TopBarBackdrop / MiuixScaffoldPage | 手机大标题/宽屏小标题，可选模糊采样 | canvas 纯色 + 1dp 底线；56dp 小栏、26/34 大标题、18/24 小标题。原 scrollBehavior、列表状态、安全区、宽屏居中、回弹/触感继续使用；不再分配或采样顶栏 blur layer |
| AgentConversationLoading / EtaSkeleton | 居中转圈与失败文字 | 首次加载骨架、失败空态与同一重试按钮；骨架仅在可见、STARTED、未减少动画时循环，帧值只在绘制阶段读取 |

设计取舍：

- C4 把 Destructive 的背景和前景都写为 danger；这里使用设计语言中的 `dangerSoft/danger` 配对，保证文字可读。
- C11 的 25% 拖拽关闭阈值没有覆盖 Miuix 的现有判定。此阶段优先遵守“只换外观不改交互”；未复制或重写 Sheet 状态机。
- 主按钮显式颜色继续尊重调用方；默认按钮改为 Tonal。没有借此次换装重排页面主次操作或删除现有按钮。
- 外观页的模糊设置入口/存储仍保留；公共顶栏固定为纯色，其余已有模糊使用点不在本阶段移除。
- 只同步新增“清除搜索”三语文案；保留原页面已有文案。

## 二级页面逐个对照

下表逐个核对 `AppRoute` 中除 Home、Browser、Terminal 外的 31 个路由。所有“保留”项指源码入口、状态归属及回调闭包核对；不表示已用真实账号/Root 逐页执行这些业务。

| 页面 / 路由（实现文件） | 换装前 | 换装后 | 原有交互核对 |
| --- | --- | --- | --- |
| 设置 Settings（[SettingsScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/SettingsScreen.kt)） | 裸分类图标、旧分组和开关、可模糊顶栏 | 统一分组、32dp 分类底块、56/64dp 行、纯色折叠顶栏 | 原分组顺序、全部开关、系统设置/权限/诊断/更新入口保留；尚未做阶段 5 重组 |
| 外观 AppearanceSettings（[AppearanceSettingsScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/AppearanceSettingsScreen.kt)） | 旧单选、下拉、开关及列表排版 | 新列表字号、选择色、52×32 开关和分组边线 | 主题、Monet、自选色、纯黑、缩放、效果及完成提示设置读写保留 |
| 工具 Tools（[AgentToolsScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/tools/AgentToolsScreen.kt)） | TabRow + 功能卡片 | EtaSegmented + 20dp 卡片、新图标底块与说明按钮 | 当前设备/全部的两个列表状态、能力过滤、卡片动作及说明弹窗保留 |
| 网页工具 WebToolsSettings（[WebToolsSettingsScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/tools/WebToolsSettingsScreen.kt)） | 旧表单分组、按钮、密码图标 | 新分组/按钮/图标按钮、纯色顶栏 | 搜索提供商配置、密钥显隐、测试与保存回调保留 |
| Skills（[AgentSkillsScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/skills/AgentSkillsScreen.kt)） | 旧技能行、安装和删除弹窗、空态 | Violet 底块、新开关/空态/按钮/弹窗 | ZIP 选择、安装、替换、启停、删除和恢复入口保留 |
| 常用任务 CommonTasks（[CommonTasksScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/tasks/CommonTasksScreen.kt)） | 旧卡片与运行/管理按钮 | 20dp 卡片和新胶囊按钮，移除重复内距，编辑/删除弹窗换装 | 任务顺序、运行、创建/编辑/删除、数量上限保留；未迁入任务中心 |
| Subagent / Subagents（[SubagentSettingsScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/subagent/SubagentSettingsScreen.kt)） | 旧配置行、模型 InputField、默认两个底部面板 | 新行/搜索框/模型过滤 Chip；模型与资料面板接 EtaSheet | 子模型选择、自定义资料、详情、编辑/删除闭包与关闭状态保留 |
| 角色库 Characters（[CharacterLibraryScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/characters/CharacterLibraryScreen.kt)） | SearchBar + 旧分组/按钮/空态 | EtaSearchField、Pink 底块、新导入/创建按钮和空态 | query 过滤、导入类型、创建、恢复默认、角色导航保留 |
| 角色详情 CharacterDetail（[CharacterDetailScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/characters/CharacterDetailScreen.kt)） | 紧凑默认顶栏、详情分组和操作 | 统一 56dp 紧凑顶栏、新行及动作按钮 | 头像/收藏/开聊/描述/编辑入口保留；原头像主视觉不重构 |
| 角色编辑 CharacterEditor（[CharacterEditorScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/characters/CharacterEditorScreen.kt)） | 旧顶栏操作、编辑分组与对话框 | Rounded 操作按钮、新分组/弹窗 | 保存、忙碌禁用、字段修改、开场白与世界书编辑保留 |
| 我的人设 CharacterPersona（[CharacterContextScreens](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/characters/CharacterContextScreens.kt)） | 旧表单骨架和整行保存按钮 | 新顶栏/表单字号、居中的 48dp 保存按钮 | personaDraft 更新、保存返回、IME padding 保留 |
| 剧情记忆 CharacterMemory（[CharacterContextScreens](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/characters/CharacterContextScreens.kt)） | 旧重新载入/保存按钮 | 新 Tonal/Primary 胶囊和顶栏 | 原角色 id、重新载入、保存及忙碌状态保留 |
| 权限健康 Permissions（[PermissionHealthScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/permissions/PermissionHealthScreen.kt)） | 默认分组/状态行/跳转箭头 | 新行字号、Rounded 箭头、深色边线与纯色顶栏 | 各权限状态展示和原系统设置跳转保留 |
| 系统增强 SystemEnhance（[SystemEnhanceScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/enhance/SystemEnhanceScreen.kt)） | 旧能力行、图标、说明按钮 | 新分类底块、列表/按钮/空态 | Root/系统能力条件、说明和开启入口保留 |
| 语音设置 SpeechSettings（[SpeechSettingsScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/voice/SpeechSettingsScreen.kt)） | 默认语音分组与选择行 | 新组标题、行/开关和顶栏 | 识别、合成及配置子页导航保留 |
| 语音识别 SpeechRecognition（[SpeechRecognitionScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/voice/SpeechRecognitionScreen.kt)） | 旧提供商选择、表单和测试按钮 | 新下拉、图标按钮、按钮与弹窗 | 识别配置、测试、结果、停止、OSS 入口保留 |
| 语音合成 SpeechSynthesis（[SpeechSynthesisScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/voice/SpeechSynthesisScreen.kt)） | 默认声音选择和试听控件 | 新选择行/分组、共享图标按钮和顶栏 | 音色/参数、试听/取消及保存保留 |
| OSS SpeechOss（[SpeechOssScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/voice/SpeechOssScreen.kt)） | 旧凭据表单与密钥图标 | 新表单字号、显隐图标按钮、顶栏 | 字段更新、密钥显隐、配置状态保留 |
| 数据备份 DataBackup（[DataBackupScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/backup/DataBackupScreen.kt)） | 默认备份分组与确认弹窗 | 新行/卡片、统一确认动作、纯色顶栏 | 文件选择、备份/恢复、开关与忙碌条件保留 |
| 全局记忆 Memory（[AgentMemoryScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/memory/AgentMemoryScreen.kt)） | 自定义 MiuixScaffold、旧记忆卡片及操作 | 新顶栏、卡片/按钮/图标、继承字号 | 记忆开关、编辑、重载/保存/删除和滚动连接保留 |
| Linux 环境 LinuxEnvironment（[LinuxEnvironmentScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/terminal/LinuxEnvironmentScreen.kt)） | 默认状态卡/发行版分组/动作 | 新卡片、列表、按钮、弹窗和顶栏 | 安装/启动/停止/修复/卸载、发行版选择与忙碌判断保留 |
| 共享目录 SharedFolders（[SharedFoldersScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/terminal/SharedFoldersScreen.kt)） | 旧映射行、移除/提示按钮 | 新列表、48dp 图标目标、确认弹窗 | 原目录选择、映射、移除和提示关闭回调保留 |
| 工作区 Workspace（[WorkspaceScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/terminal/WorkspaceScreen.kt)） | 旧工作区设置/提示行 | 新分组、开关、图标按钮、顶栏 | 初始化、工作区状态及提示关闭保留 |
| Linux 文件 LinuxFiles（[LinuxFilesScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/terminal/LinuxFilesScreen.kt)） | 默认文件行和查看卡片 | 新文件分组、20dp 查看卡片、空态、顶栏 | 路径导航、文本选择、二进制/错误分支保留 |
| 服务商列表 ModelProviders（[ModelProviderListScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ModelProviderListScreen.kt)） | InputField、旧新增入口/服务商行 | EtaSearchField、新组/箭头/选择按钮 | 搜索字符串、已有服务商选择、新建各类型与目录入口保留 |
| 社区目录 CommunityCatalog（[CommunityCatalogScreens](../../../app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/CommunityCatalogScreens.kt)） | 旧搜索、目录状态与候选卡 | 新搜索/卡片/刷新按钮/顶栏 | 目录读取、刷新、过滤与服务商导航保留 |
| 目录服务商 CommunityCatalogProvider（[CommunityCatalogScreens](../../../app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/CommunityCatalogScreens.kt)） | 旧模型搜索、选择和导入按钮 | 新搜索、选择行、批量动作按钮 | 模型筛选、全选当前结果、导入和导入禁用状态保留 |
| MCP 列表 McpServers（[McpServersScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/mcp/McpServersScreen.kt)） | 默认服务器分组、添加按钮和空态 | 新分组/顶栏图标/空态/添加弹窗 | 新建、启停、服务器详情入口保留 |
| MCP 详情 McpServerDetail（[McpServersScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/screens/mcp/McpServersScreen.kt)） | 旧参数/连接/工具行及删除弹窗 | 新列表字号、开关、按钮和弹窗 | 参数修改、连接/刷新工具、删除、返回保留 |
| 服务商详情 ModelProviderDetail（[ModelProviderDetailScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ModelProviderDetailScreen.kt)） | TabRow、默认搜索、连接表单和导入 Sheet | EtaSegmented、EtaSearchField、新按钮/弹窗，远程模型导入 EtaSheet | currentTab、草稿、登录/连接、模型编辑、设为当前、导入选择和重置/删除保留 |
| 新建服务商 ModelProviderNew（[ModelProviderDetailScreen](../../../app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ModelProviderDetailScreen.kt)） | 同一详情实现的新建分支、旧表单动作 | 新表单字号、按钮和顶栏；仍不显示已有服务商页签 | 原 NewProviderType、草稿、验证、保存/取消闭包保留 |

相关子界面也随公共组件换装：

- 角色世界书编辑：原字段、增删条目与保存回调，新的按钮与弹窗。
- 服务商远程模型导入、Subagent 模型选择/资料详情：新搜索/Chip/Sheet，原选择集合和关闭流程。
- 技能替换/删除、MCP 增删、常用任务编辑/删除、终端会话弹窗：新 Dialog 与动作外观，原确认/取消条件。
- Home、Browser、Terminal 仍保持原页面结构。首页公共顶栏去模糊、工具标签/加载态/共享卡片自然更新；终端共享动作按钮换装，PTY 输入、工具栏键序和浏览器 WebView 行为不改。这三页的专门重排不在阶段 2。
- `AppRoute.kt` 与 `AgentNavigator.kt` 对比阶段 1 无改动；现有导航目标没有被删除或重定向。

## 渲染证据

同一 fixture，前后均使用阶段 1 的默认 Eta 蓝、相同窗口和数据：

| 场景 | 阶段 1 组件外观 | 阶段 2 组件外观 |
| --- | --- | --- |
| 浅色 | [before-light](stage-2/before-light.png) | [after-light](stage-2/after-light.png) |
| 深色 | [before-dark](stage-2/before-dark.png) | [after-dark](stage-2/after-dark.png) |
| 200% 字体 | [before-font-200](stage-2/before-font-200.png) | [after-font-200](stage-2/after-font-200.png) |

新增组件：[浅色](stage-2/after-new-light.png) / [深色](stage-2/after-new-dark.png)；对话框：[浅色](stage-2/after-dialog-light.png) / [深色](stage-2/after-dialog-dark.png)；Sheet：[浅色](stage-2/after-sheet-light.png) / [深色](stage-2/after-sheet-dark.png)；840dp 宽屏组件：[浅色](stage-2/after-wide-light.png) / [深色](stage-2/after-wide-dark.png)。

使用已有 Robolectric native graphics，无新依赖。生成目录为 `app/build/reports/eta-stage2/`，测试由 `EtaComponentsRenderingTest` 负责；CI 以 `ui-component-previews` 上传该目录与阶段 1 主题样本。提交在本目录下的是本机已查看的快照，CI 附件对应实际 CI 提交。

## 自动化结果

本地结果（2026-11-06）：

| 检查 | 结果 |
| --- | --- |
| etax 接线检查 | 12 项通过 |
| 引擎内嵌 | 编译成功，BUILD 仍为 `20261105-e49-parallel-observe` |
| 最终定向回归 | 27 项全部通过，0 失败、0 错误、0 跳过。包含本阶段 12 项行为测试、3 项渲染测试、2 项既有列表测试、6 项主题测试、3 项状态环测试，以及下述终端时序用例复测 |
| Lint | 通过，0 error/fatal；412 warning、1 hint，未新增抑制或放宽门槛 |
| Debug APK | 构建成功；包名 `io.github.mangi.etax`，versionCode `2026110601` |
| 图片与链接 | 已查看 14 张公共组件前后/深浅色/大字体/宽屏/弹层快照；逐页实现链接随源码一起提交 |

Windows 全量回归共 2,487 项、76 失败、0 错误、2 跳过（当时本阶段有 14 项新增测试，随后补充 1 项窄屏无障碍测试并定向通过）。与同机保存的 `7db9f5a` 基线 2,460 项 / 75 失败 / 2 跳过比较，75 个基线失败仍在；额外的 `BackgroundTerminalJobsTest.failureTimeoutAndStopRemainDistinguishable` 在全量运行中出现 stopped/interrupted 的时序差异，最新定向复测通过，该用例在阶段 1 也曾出现。未为本次 UI 换装修改相关终端或部署逻辑。**Windows 全量结果不标为通过**，Linux CI 仍须执行全部 2,488 项测试并通过。

本文件与代码在同一个阶段提交中发布；提交时尚未产生远端 CI 结果。验收以 [main 的 Etax Build 运行记录](https://github.com/Lmin2419/eta-background/actions/workflows/etax-build.yml?query=branch%3Amain) 中对应 `2026110601` 的提交为准，并核对三个 job 与同提交签名 Release；最终交付另附直接运行链接。

主要新增回归：

- 实际触摸开关滑块和整行，各触发一次；禁用时不触发。
- 单选/复选仅保留一个行级动作与原状态语义。
- 按钮加载不改变自然宽度，加载环固定 16dp；加载/禁用时不执行点击；整行按钮文字居中。
- 搜索编辑、Search IME、清空均回到页面持有的字符串状态。
- 分段切换只回传原下标；禁用时不变。
- 折叠顶栏高度、紧凑模式、恢复手机模式与返回动作；重用同一个滚动状态。
- 200% 字体下对话框动作换行且不相交；取消/确认各触发一次。
- 320dp 窄屏与 200% 字体组合下，对话框保持屏边距、动作不相交，标题保留 heading 与 paneTitle 无障碍语义。
- OverlayDialog 的请求关闭与完成关闭回调分开且各一次。
- Sheet 的 allowDismiss 与两种关闭回调仍由原宿主处理。
- Toast 操作不抢占 3s 关闭计时，关闭回调仅一次。

```sh
bash etax/check_hooks.sh
bash etax/embed_engine.sh
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug :app:assembleDebug
```

Windows 使用本机 JDK 25 / Android SDK 37，并额外传 `-Pandroid.overridePathCheck=true`。Linux 引擎部署回归和签名发布由同阶段 [Etax Build](https://github.com/Lmin2419/eta-background/actions/workflows/etax-build.yml) 完成；自动标签为 `etax-2026110601-<提交短号>`。验收门槛是该提交的测试/Lint、签名构建、发布三个 job 全绿，不能以本地组件定向测试代替。

## 真机与委派记录

本轮未连接 OPPO Find X9 Ultra / ColorOS 17，不声称完成真机验收。安装本阶段同签名 APK 后仍需覆盖：深/浅色逐页观感、系统 200% 字体、Monet/自选强调色、键盘弹出/返回、长列表滚动、RTL、横竖屏、TalkBack，以及 Sheet 的手指拖拽与系统返回手势。已有业务入口与真实服务连接仍需设备环境验证。

委派仅发送当前子任务的脱敏摘要，无凭据或会话内容。阶段前部曾尝试腾讯 `cn:glm-5.3`、`cn:deepseek-v4-pro`，返回 `Could not complete the tencent request to /chat/completions`。用户固定模型后，所有委派只使用腾讯入口 `cn:deepseek-v4.1-flash`；该入口早期请求也失败过，配置修复后的审查请求成功返回。

Flash 提醒核对卡片重复内距、旧组件/二级入口遗漏、窄屏大字、浮层关闭手势和自定义标题无障碍语义。主会话逐项检查调用点，补齐剩余按钮迁移、paneTitle/heading 和 320dp + 200% 字体回归；手指拖拽与真实键盘场景保留为真机待验。所有文件修改、测试、截图检查与最终 CI 跟进由主会话完成，外部结论不直接作为通过依据。
