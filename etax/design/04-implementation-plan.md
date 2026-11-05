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
