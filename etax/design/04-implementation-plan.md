# 04 · 实施计划（给 Codex）

## 总规则

1. **只改界面层。** 业务逻辑、数据流、运行时、引擎、通知调度、工具协议都不动。唯一允许的非 UI 新增：
   - `AgentExecutionService` 的任务卡片快照 `StateFlow`（给实时任务条和任务中心读，只读）；
   - 新路由 `AppRoute.Tasks`、`AppRoute.AssistantSettings`。
2. **所有现有功能入口必须仍可到达**，按 [03 · S0 入口迁移表](03-screens.md#入口迁移表所有现有功能都必须仍可到达) 核对。
3. **先 token 和公共组件，再页面。** 页面里不再写裸颜色、裸字号、裸圆角。
4. **不引入新的 UI 框架或字体。** 继续 Compose + Miuix + material-icons-extended Rounded。如需截图测试库，见「验证」一节，可选。
5. **性能不倒退。** 现有代码对重组和动画很讲究（绘制阶段动画、懒组合、长会话复用条目）；改样式时保持这些做法：无限动画只在可见且运行中时存在；不在 `LazyColumn` 条目里新建 `Animatable` 常驻。
6. **文案三语同步**：`values`（英文）、`values-b+zh+Hans`、`values-b+zh+Hant`。新文案先写简体中文，再补繁体与英文。
7. **每个阶段单独提交、单独发版**（versionCode 递增，按 `AGENTS.md` 写版本说明），阶段内 CI 全绿后再进入下一阶段。

## 分阶段

### 阶段 1 · 设计 token 与主题（地基）
- 新建 `ui/theme/`（见 [02 · C1](02-components.md#c1--主题入口-etatheme)）。
- `AgentAppTheme` 提供 `EtaTheme`；把 Miuix `ColorScheme` 主要槽位映射到 Eta token。
- 默认强调色改为 Eta 蓝（外观设置「强调色：系统」且未开 Monet 时）；开 Monet 或选了其他强调色时 `accent` 系列取 Miuix 生成色。
- `StatusColors.kt` 改为引用状态 token；新增 `StatusRing`（C6）与 `TaskVisualStatus` 枚举，以及从现有状态到它的映射函数（`AgentOverlayPhase`、`RunStatusUi`、`ToolActivityStatusUi`、`TaskNotificationState` → `TaskVisualStatus`）。
- 验收：App 整体换上新底色与品牌色，无布局变化；深浅色、Monet 开关三种组合都正常；单测通过。

### 阶段 2 · 公共组件换装
- 改：`EtaCard`、`EtaPreferenceGroupItem`、`EtaArrowPreference`/`EtaPreference`/`EtaPreferenceRow`、`EtaSwitch`、`EtaChoiceChip`、`EtaTextButton`→`EtaButton`、`EtaWindowDialog`/`EtaOverlayDialog`/`MiuixDialogActions`、`ListEmptyState`、`SectionHeader`、`AdaptiveTopAppBar`、`TopBarBackdrop`、`MiuixScaffoldPage`、`EtaDropdownPreference`、`EtaSelectionPreferences`、`ToolChip`、`AgentConversationLoading`。
- 新增：`EtaIconButton`、`EtaIconTile`、`EtaSearchField`、`EtaSegmented`、`EtaSheet`、`EtaToast`、`EtaListGroup`。
- 统一图标为 Rounded。
- 验收：所有二级页面（设置及其子页、工具、Skills、MCP、记忆、角色、服务商、语音、外观、备份、Linux、权限、系统增强）外观符合 C7/C8/C2，交互无变化。

### 阶段 3 · 首页对话
- 顶栏（C2/C3）+ 对话设置面板（S1.4）；模型控件从输入栏移出。
- 空状态（S1.2）。
- 用户消息、助手消息、操作行、代码块、表格（C20/C21）；工作过程（C22）；排队条（C25）；回到底部（C26）；压缩标记（C27）。
- 输入栏（C24）含右侧按钮状态机与长按「打断并发送」。
- 附件面板（S1.5）、消息长按菜单（S1.6）。
- 验收：03 · S1 全部交互；长会话（≥500 条）滚动帧率与改版前持平（用现有性能统计或 Android Studio Profiler 粗测，结论写进版本说明）。

### 阶段 4 · 侧栏与任务中心
- 侧栏（S2）：搜索、工作区快捷入口、会话列表样式、长按面板、底部固定栏。
- `AgentExecutionService` 增加 `val taskSnapshots: StateFlow<List<TaskNotificationState>>`（在现有 `cards` 变更处同步发布，主线程）。
- 实时任务条（C23）接入首页。
- 任务中心（S3）：新路由 `AppRoute.Tasks`；进行中卡片的缩略图轮询只在页面可见时运行（2s 一帧，edge 320），离开立即停。
- 常用任务页内容并入任务中心「常用」页签；`AppRoute.CommonTasks` 保留并重定向到 `Tasks(tab = Common)`，桌面快捷方式与 `CommonTaskLaunchRequest` 不受影响。
- 验收：03 · S2、S3、C23；两个后台任务同时运行时任务中心、实时任务条、通知三处状态一致。

### 阶段 5 · 设置重组
- 设置首页：系统就绪卡（S4.1）+ 8 个分组（S4.2）。
- 新路由 `AppRoute.AssistantSettings`，迁入系统助手次要项。
- 权限页（S9）。
- 验收：逐项对照改版前 `SettingsScreen` 的全部设置项（附在 PR/提交说明里），一项不少；搜索不到的项为 0。

### 阶段 5.5 · 真机反馈修正（插入，分两次提交）
- **5.5a 输入区衔接**：按 [03 · S1.9](03-screens.md#s19-底部输入区与消息区的衔接阶段-5-真机反馈后补充) 统一首页底色为 `canvas`，输入区外层改为 24dp 渐变 + 实心 `canvas`，去掉色带与硬边。验收：浅/深/200% 字号截图各一张。
- **5.5b 会话批量管理**：按 [03 · S2.5](03-screens.md#s25-会话批量管理多选批量删除归档阶段-5-真机反馈后补充) 实现长按多选、批量删除、归档 / 已归档子页 / 撤销、搜索含归档、发新消息自动取消归档。含 Room 23→24 迁移与备份兼容。
- 验收：迁移测试（旧库升级数据完整）；备份往返（含/不含 `archived_at`）；批量删除只持久化一次；运行中会话删除先停止、归档被跳过；多选进入/退出/全选受搜索过滤；真实手指长按进入多选的手势测试。

### 阶段 6 · 系统级浮层
- 任务预览窗（S5）：Compose 化，状态机表格逐项实现；更新 `TaskPreviewActivityTest`。
- 顶部状态胶囊（S7）、电源键助手面板（S6）、边缘流光引用 aurora token。
- 通知文案模板（S8）：只改文案拼装与按钮顺序，不改通知调度。
- 验收：03 · S5–S8；预览窗在接管、交还、完成、失败、画面不可用各状态下按钮与提示正确；`:voice` 进程内的面板深浅色正确。

### 阶段 7 · 打磨
- 动效（01 §7）逐项核对；「减少动画」降级。
- 无障碍：所有图标按钮有 `contentDescription`；TalkBack 读出状态（如「美团，后台执行中，正在点击提交订单」）；字体放大到 200% 不截断关键按钮；对比度复查。
- 横屏 / 平板（S1.8）。
- 深色模式逐页过一遍。

### 阶段 8 · 细节修正与息屏不暂停（2026110614 真机反馈）
- 两次提交，最后一起推送、一起发版：
  - **8a**：界面与文案，03 §S10 十条。
  - **8b**：运行时行为，03 §S11「息屏不暂停」。8b 单独提交，方便回退。
- 每条都要满足它的验收标准，并配一个能自动跑的断言（宽度、省略、像素覆盖、对齐、触控高度、文案、状态）。
- 先补测试复现问题（修改前测试失败），再改代码让测试通过。
- 结果写进 `validation/stage-8.md`：每条附修改前后截图（浅色、深色），以及 223dp / 244dp 两个窗宽、100% / 200% 两个字号的预览窗头部截图。

### 阶段 8.1 · 预览窗完成态与弹出逻辑（2026110615 真机反馈，一次提交）
- 按 03 §S12 做：
  - 状态只看任务，不看画面；
  - 完成、失败态改两个 Tonal 按钮，去掉孤立按钮行和重复的关闭；
  - 完成通知正文点开直接进会话。
- 先写复现测试（修改前失败），再改代码。结果写进 `validation/stage-8.1.md`，附上述 5 种情况的修改前后截图（浅色、深色）。
- 只改预览窗界面、`PreviewWindowMapper` 和完成通知的点击目标；不改取帧、接管、交还、`CompletedAppActivity` 的逻辑。

### 阶段 9 · 子代理选模型、会话置顶分组、Linux 时区（2026110616 用户需求）
- 三次提交，最后一起推送、一起发版。每次提交单独跑一遍相关测试，确保都能通过：
  - **9a**：03 §S15，包括 Linux 时区、三个逻辑问题和安装包文件名（S15.3）。
  - **9b**：03 §S13，子代理按角色选模型。
  - **9c**：03 §S14，会话置顶与分组，含 Room 24→25 迁移和备份 v6（同时带上子代理模型设置）。单独提交，出问题方便回退。
- 先写复现或验收测试，再改代码。9c 必须先有 v24→v25 带数据的迁移测试。
- 结果写进 `validation/stage-9.md`：每一节一段，附截图（浅色、深色）和测试结果。
- 不改模型请求、工具调度、引擎；数据库和备份只做 S14 要求的增量。

### 阶段 10 · 全 App 打磨与电脑同步换装（2026111101，用户需求）
- 动效：`StatusRing` 状态切换改为放大淡入（0.6→1，`medium`）；新出现的末尾消息 `etaRiseIn`（16dp，只对首次出现的条目分配动画）；空状态退出淡出上移；侧栏 / 电脑列表条目 `etaAnimateItem`；可点 `EtaCard` 默认 `Sink` 按压。减少动画时只淡入淡出。
- 对话：操作行只常显在最新回复；工作过程一行「已完成 · N 步」/「N 步 · M 个失败」。
- 首页：建议改为单行列表；顶栏任务按钮仅在有任务运行时出现。
- 设置：`tileBackground` 统一中性底、`tileForeground` 收拢为焦糖 / 绿 / 灰三色（取代 01 §2.5 的多分类色）；就绪卡全部就绪时一行；开关轨道柔化。
- 电脑：会话列表沿用 S2 行样式（单行、时间在右、状态圈只用于运行中 / 需要你，长按操作）；「最近」+ 默认收起的工作区；审批 / 提问为输入框上方内嵌卡片（不弹窗）；运行状态条带「停止」；通知三档预设；配对与诊断收进「高级」。

## 验证

- 每阶段：`./gradlew :app:testDebugUnitTest :app:lintDebug` 全绿；CI 签名构建成功。
- 为新组件补 Robolectric 测试：状态到 `TaskVisualStatus` 的映射、输入栏右侧按钮状态机、预览窗操作栏状态机、入口迁移（每个旧入口有新入口可达）。
- **可选（推荐）**：引入 Roborazzi（Robolectric 截图，开发期依赖，不进 APK），为关键组件与页面生成浅/深色截图，CI 上传为产物，便于不装机先看效果。若引入，只放在 `testImplementation`，版本用当时最新稳定版。
- 真机：OPPO Find X9 Ultra / ColorOS 17 上过一遍 03 的每个页面，浅色深色各一次。

## 不要做的事

- 不要改 `AgentAppState`、运行时、模型请求、工具、引擎的逻辑来「配合」界面；界面需要的数据只读获取。
- 不要删除任何设置项或功能入口。
- 不要用渐变做背景或卡片底色；aurora 只用于 AI 运行中（01 §2.3）。
- 不要加 emoji 图标、不要加左侧彩色竖条卡片。
- 不要一次提交全部阶段。
