# 05 · 液态玻璃 2.0 执行方案

> **2026111407 起**：按用户真机反馈，小窗、半窗、对话框与按钮改用上游 3.4.x 的 COUI 组件（`ui/components/coui/EtaCoui.kt`），设置类页面回到整页打开（§3.3 的玻璃窗口不再使用），新对话空白页输入框始终在底部；液态玻璃改用 COUI 流体材质（顶部三控件与输入胶囊）。本文其余规范（配色、字号、后台压缩等）仍有效。

> 状态：方向已与用户确认（2026-11-14）。按阶段 13 → 16 顺序执行，**一个阶段 = 一次提交 = 一个安装包**。
> 本文与 01–03 冲突时以本文为准。参考图：`mockups/liquid-glass-v2.png`（四屏：对话页、小窗、设置窗口、悬浮侧栏）。
>
> 路径缩写：`UI/` = `app/src/main/kotlin/io/github/mangi/eta/ui/`，`AG/` = `app/src/main/kotlin/io/github/mangi/eta/agent/`，`DATA/` = `…/eta/data/`。

## 0. 给执行者

**用户要的是「高级、美观」，不是「到处是玻璃」。** 上一轮（2026111403–05）把卡片、气泡、图标、标签全做成了玻璃并铺了彩色光晕，被用户否定为「粗暴」。本方案的第一件事是把这些收回去。

每个阶段的固定流程：

1. 改代码 → `./gradlew :app:compileDebugKotlin`
2. `./gradlew :app:testDebugUnitTest`。**已知 5 个与构建机环境有关的失败可忽略**：`AgentFileOperationsTest.deepDirectory…`、`BackgroundTerminalJobsTest.commandContinues…`、`TerminalPrivateStorageTest.ordinaryDirectories…`、`DexKitNativeLibraryFileTest`（2 个）。其余必须全过。
3. `./gradlew :app:lintDebug`，必须 0 Error。
4. 提高 `app/build.gradle.kts` 的 `versionCode`（`yyyyMMdd` + 两位序号）；在 `etax/README.md` 顶部「版本更新记录」加一节；更新根目录 `README.md`、`README_EN.md` 的「最新测试版本」。
5. 提交并推送到 `claude/deepseek-mobile-sync-j54ub0`。**不合并 main、不发 Release**，除非用户明确要求。
6. 在 GitHub Actions 手动运行 **Etax Build**（workflow_dispatch，同一分支）。push 自动触发的那次不产出安装包，可以取消。成功后下载产物 `Pangxiaopang-3.3.5-<versionCode>.apk` 发给用户。
7. 用中文、短句向用户说明改了什么、哪里还没在真机上看过。等用户真机反馈后再做下一阶段。

踩过的坑（必须避开）：

- **新建文件前先查重名**（`ls` / grep）。曾经新建 `EtaSkeleton.kt` 覆盖了已有文件。
- **字符串三种语言都要加**：`res/values/`（英文）、`values-b+zh+Hans/`、`values-b+zh+Hant/`，缩进 2 个空格，同名只能出现一次，否则 lint 报 `MissingTranslation` 或重复资源。
- `remember { }` 不能返回 `Unit`（lint `RememberReturnType`）。
- **`Popup` / `Dialog` 是独立窗口，拿不到背后的内容，做不了真模糊。** 需要玻璃的浮层一律画在同一棵 Compose 树里（见 §3.1）。
- 改公共组件签名时，新参数放末尾并给默认值（测试里用位置参数调用 `AgentChatBody`）。
- 不引入上游的 COUI；不改引擎（`etax/engine`）；除 §6 外不改业务逻辑。
- 手写示意图里出现过「发送键被挤扁」。App 里所有固定尺寸的控件用 `requiredSize`，并加渲染测试（§8）。

## 1. 设计原则

1. **内容实色，浮层玻璃。** 消息、卡片、列表是内容，永远实色；只有浮在内容上面的控件用玻璃。
2. **一屏最多 4 块玻璃，玻璃上不叠玻璃。**
3. **珊瑚橘一屏只出现一处**（发送键，或正在进行的状态）。其余黑、白、灰。
4. **能原地解决的不跳页。** 小选择用小窗，设置类用玻璃窗口，沉浸内容才整页。
5. **动效从触发点出发、原路返回、中途可反向。**

## 2. 规范（Tokens）

### 2.1 颜色（改 `UI/theme/EtaColors.kt`）

| Token | 浅色 | 深色 | 说明 |
| --- | --- | --- | --- |
| `canvas` | `#FBFAF8` | `#161616` | 页面底。浅色从纯白改为极浅的纸白，白色卡片和玻璃才有层次 |
| `surface` / `surfaceRaised` | `#FFFFFF` | `#1F1F1F` / `#262626` | 卡片、面板 |
| `surfaceSunken` | `#F1F0ED` | `#2A2A2A` | 输入底、用户气泡、选中底 |
| `outline` / `outlineStrong` | `#E9E7E3` / `#D8D5D0` | `#323232` / `#444444` | 细线 |
| `textPrimary` | `#1A1A1A` | `#F0EFED` | |
| `textSecondary` | `#55534F` | `#BDBBB7` | |
| `textTertiary` | `#6B6966` | `#A09E9A` | 分组标题、次要说明（仍满足 4.5:1） |
| `textQuiet`（新增） | `#9E9B96` | `#6F6D6A` | **只用于**占位文字、统计小字、时间、右箭头。不参与 4.5:1 断言 |
| `accent` | `#B4532F`（不变） | `#E38D6F` | 只用于**文字**：链接、文字按钮 |
| `brand`（新增） | `#D97757` | `#E38D6F` | 只用于**图形填充**：发送键、运行圈、上下文圆环、爪印 |
| `onBrand`（新增） | `#FFFFFF` | `#2A1208` | brand 上的图标 |
| `accentSoft` / `onAccentSoft` | `#ECEAE6` / `#1A1A1A` | `#303030` / `#F0EFED` | 中性，不泛橘 |
| `toastBackground` / `toastForeground` / `toastAction` | `#1F1F1F` / `#F5F5F3` / `#F2B8A2` | `#F2F1EF` / `#1A1A1A` / `#9A4426` | 去掉残留的暖棕 |
| `shadow` | `#1A1612` | 同左 | 阴影不用偏蓝的黑 |

- 状态色（success / danger / attention）不动。
- 以上文字色在四种底色上的对比度已验算：浅色最低 4.80，深色最低 5.37，`EtaContrastTest` 能过。白色在 `brand` 上是 3.12（图形够用，文字不够），所以 **`brand` 上不放文字**。
- 测试：`EtaContrastTest` 增加 `contrast(onBrand, brand) >= 3f`；`app/src/test/.../ui/theme/` 里断言具体色值的测试同步改值。
- 同步改 `res/values/colors.xml` 的 `splash_background`（`#FFFBFAF8`）和 `values-night`（`#FF161616`）。
- 发送键（`UI/components/ChatComposerAction.kt` 的 `ComposerAction.Send`）底色从 `accent` 改为 `brand`，图标 `onBrand`。运行圈、上下文圆环、爪印统一用 `brand`。

### 2.2 字（改 `UI/theme/EtaTypography.kt`）

- 现有样式保留，改两处：`topBarTitle` 改为无衬线 `style(17, 24, SemiBold)`（小字号衬线发虚）；新增 `micro = style(11, 14)`，用于统计小字和时间。
- **衬线只用于** `display`、`title1`、`title2` 和 Markdown 的 H1/H2。
- 会变化或要对齐的数字（时间、token、百分比、计数）一律 `EtaTypography.tabular(...)`。
- `UI/` 下不允许再写散落的 `fontSize = xx.sp`（除 Markdown 渲染内部）；发现就换成 token。

### 2.3 间距、圆角、尺寸

- 4dp 网格。对话页左右边距 20dp，列表页 16dp。
- 圆角 token 不变（6 / 10 / 14 / 20 / 28 / Full），新增 `radiusWindow = 36.dp`（窗口、悬浮侧栏）。
- **同心圆角**：内圆角 = 外圆角 − 内边距（最小 6）。固定搭配：

| 外层 | 内边距 | 内层 |
| --- | --- | --- |
| 窗口 36 | 16 | 卡片 20 |
| 输入胶囊 28 | 12 | 内部元素 16 |
| 小窗 24 | 8 | 条目 16 |
| 卡片 20 | 12 | 缩略图 8 |

- 可点区域 ≥ 48dp（视觉可以更小，用外层补足点击区）。
- **固定尺寸控件用 `Modifier.requiredSize`**（发送键、圆形按钮、圆环），空间不够时让文字省略，控件不变形。

### 2.4 线与阴影

- 分隔线 0.5dp、`outline`；列表里**从文字起点开始**（不贯穿图标），最后一行后面没有线。
- 阴影只用三档：卡片 `E1`（2dp / 0.05）、浮层玻璃 `E2`（10dp / 0.10）、窗口 `E3`（24dp / 0.16）。改 `UI/theme/EtaElevation.kt` 的数值，颜色用 `shadow`。

### 2.5 动效（`UI/theme/EtaMotion.kt`）

- 全 App 只用两套弹簧：**跟手**（`dampingRatio 0.86, stiffness 420`，已有默认值，用于位置和尺寸）和**弹出**（`0.62 / 520`，用于小东西冒出来：按钮、对勾、爪印）。把这两套加成具名常量，替换散落的 spring 参数。
- 出现时以触发点为 `transformOrigin`，消失时原路返回；用 `Animatable`，动画进行中可以反向。
- 除页面切换（480ms）外，没有超过 400ms 的动画。`reduceMotion` 时全部退化为 ≤120ms 淡入淡出。

### 2.6 玻璃材质（`UI/components/EtaGlass.kt` 的 `etaGlass`）

**允许使用玻璃的地方（穷举，其余一律实色）：**

1. 对话页顶部三个控件（历史、模型胶囊、新建）
2. 输入胶囊
3. 小窗 `EtaPopover`（含长按菜单）
4. 玻璃窗口的外框（一层）
5. 悬浮侧栏的外框（一层）
6. 后台任务胶囊（深色玻璃）
7. 提示条
8. 回到底部按钮、下拉刷新指示器
9. 二级整页的顶栏（`TopBarBackdrop`，现状保留）

参数：

- 模糊半径 28；底色用 `surface`；有模糊时不透明度 0.64（深色 0.55），无模糊时 0.92。**小窗和窗口因为上面有大量文字，底色不透明度提到 0.90–0.95**（参考图里就是这样，背后的字不能透上来干扰阅读）。
- 边缘：上沿高光 + 外圈一条 `rgba(0,0,0,0.07)` 的 0.5dp 描边（在浅色底上把玻璃和背景分开）。顶部反光 `etaGlassSheen` 的强度减半（浅色 0.20，深色 0.05）。
- 设置 → 外观加「玻璃」选项：通透（0.50）/ 标准（0.64，默认）/ 磨砂（0.80）/ 关闭（实色，等价于现有的 `blurEnabled = false`）。存进 `AppearanceSettings`（新增枚举字段 `glassStrength`），用 CompositionLocal 传给 `etaGlass`。
- **滚动边缘**：对话页顶部控件下方加一条渐隐（`canvas` → 透明，高度 = 顶栏高度 + 24dp）叠加渐进模糊（`progressiveTextureBlur`，半径 12）。消息滑到控件下面时渐渐变淡，而不是从按钮之间硬穿过去。

**回收清单（阶段 13 必做，把 2026111403–05 加过头的改回实色）：**

| 文件 | 改回 |
| --- | --- |
| `UI/components/EtaCard.kt` | 删除 `etaGlassCard`，两个 `EtaCard` 恢复 `modifier.etaElevation(E1, radiusLg)` + 原 `colors` |
| `UI/components/ChatMessageItem.kt`（用户气泡） | `.background(colors.surfaceSunken, shape)` |
| `UI/components/EtaIconTile.kt` | `.background(colors.tileBackground(tint), radiusSm)`；**设置行里的 `EtaPreferenceIcon` 不再画底块，只画 20dp 线条图标** |
| `UI/components/EtaChoiceChip.kt` | `.clip(radiusFull).background(container)` |
| `UI/components/EtaSegmented.kt` | 轨道 `.background(surfaceSunken)`；滑块 `.etaElevation(E1, shape).background(surfaceRaised, shape)` |
| `UI/components/EtaSearchField.kt` | `.background(colors.surfaceSunken, shape)` |
| `UI/components/ListEmptyState.kt` | 实色圆（`surfaceSunken`） |
| `UI/components/ConversationPanePanel.kt` | 当前对话行 `.background(textPrimary.copy(alpha = 0.06f))`；去掉 `etaAmbientBackground` |
| `UI/components/MiuixScaffoldPage.kt`、`AgentChatBody.kt` | 去掉 `etaAmbientBackground` |
| `UI/components/EtaGlass.kt` | 删除 `etaAmbientBackground` |
| `UI/components/AgentChatQueueStrip.kt` | `.background(surfaceSunken, shape)` |
| `UI/screens/desktop/DesktopInlineCards.kt` | 运行条 `.background(runningSoft, radiusFull)`；审批卡 `.etaElevation(E1).background(surfaceRaised, radiusLg)` |
| `UI/components/EtaScrollEffects.kt` | 保留，但幅度减半：卡片缩放 0.08 → 0.04，淡出 0.55 → 0.35 |

保留不动：顶部控件、输入胶囊、长按菜单、提示条、回到底部、下拉刷新的玻璃；大标题随滚动收缩；长按浮起；发送光带；爪印动效；触感。

## 3. 组件

### 3.1 浮层宿主 `EtaOverlayLayer`（新，阶段 14）

目的：小窗、窗口、对话框都画在 App 同一棵 Compose 树的最上层，这样能模糊背后的内容。

- 放在 `UI/components/EtaAppOverlayHost.kt`：现有结构是 `Box { Box(layerBackdrop(appBackdrop)) { content } ; 提示条 }`。在内容和提示条之间加一层浮层。
- 控制器（参考骨架，以能编译为准）：

```kotlin
@Stable internal class EtaOverlayController {
    internal val entries = mutableStateListOf<Entry>()
    internal class Entry(val id: Long, val content: @Composable () -> Unit)
    fun add(content: @Composable () -> Unit): Long { /* 追加并返回 id */ }
    fun remove(id: Long) { /* 移除 */ }
}
internal val LocalEtaOverlay = staticCompositionLocalOf<EtaOverlayController?> { null }
```

- 调用方写法与现在的 `EtaContextMenu(show, anchor, onDismiss) { … }` 一样。组件内部用 `DisposableEffect(show)` 向控制器登记、离开时移除；**登记前用 `currentCompositionLocalContext` 抓住调用处的 CompositionLocal，在宿主里用 `CompositionLocalProvider(context) { … }` 包住内容**，否则调用处提供的 Local（如 `LocalRoleplayChat`）在浮层里读不到。
- 退出动画：组件自己保留「可见 / 正在退出」状态，动画播完再从控制器移除（照 `EtaContextMenu` 里 `visible` + `Animatable` 的写法）。
- 浮层内 `LocalEtaGlassBackdrop provides appBackdrop`（宿主里已有 `appBackdrop`）。
- 返回键：沿用 `UI/components/ConversationSidePaneScaffold.kt` 第 217 行附近用的返回处理（搜 `onBackCompleted`）。最上面的浮层先消费返回。
- `LocalEtaOverlay` 为 null（如单元测试里没有宿主）时，退回现在的 `Popup` 实现。

### 3.2 小窗 `EtaPopover`（新，阶段 14）

- 从锚点展开：`scale 0.9 → 1` + 淡入，`transformOrigin` 在锚点一侧；关闭原路返回。
- 宽 232–300dp，圆角 24，内边距 8；条目高 46dp、圆角 16、选中底 `textPrimary 6%`；最高占屏幕 60%，超出可滚动。
- 位置：优先锚点下方，放不下翻到上方；离屏幕边缘至少 8dp。
- 背后不加遮罩（不变暗），点外面或返回键关闭。
- 玻璃：`etaGlass(radius 24, blurAlpha = 0.92, elevation = E2)`。
- 用在哪：

| 触发 | 内容 | 现状 |
| --- | --- | --- |
| 长按消息 | 菜单（已有 `EtaMenuItem`，带图标） | `EtaContextMenu`（Popup）→ 改用 EtaPopover |
| 点模型胶囊 | 模型列表（多于 6 个时带搜索）+ 末行「管理模型…」（打开模型服务窗口） | `ConversationSettingsSheet`（底部面板）|
| 点输入框里的「思考 · 中」 | 思考等级选项 | 现在在模型面板里 |
| 点「＋」 | 四格：照片 / 拍照 / 文件 / 文件夹（沿用现有附件动作） | 现有附件入口 |
| 点上下文圆环 | 用量卡片 + 「压缩」按钮 | `ContextUsageStatsPopoverPopup`（Popup）|
| 点回复下的统计小字 | 本轮统计卡片 | `ChatStatsPopoverPopup`（Popup）|
| 长按侧栏里的对话 | 置顶 / 移到分组 / 改名 / 导出 / 删除 | 现在是底部面板 |

- 原 `ConversationSettingsSheet` 里的「导出」移到侧栏对话的长按菜单（已有）；「压缩」移到上下文圆环的小窗。

### 3.3 玻璃窗口 `EtaGlassWindow`（新，阶段 15）

- 从底部升起，盖在当前页面上。顶部距状态栏下沿 24dp；左右贴边；上圆角 36。
- 外框：`etaGlass`，底色 `surfaceSunken`、不透明度 0.90、`E3`。**窗口里面的卡片是实色白卡，不再叠玻璃。**
- 背后的页面：缩到 0.94、圆角 28、盖一层 18% 的黑。在 `EtaAppOverlayHost` 里对内容层做 `graphicsLayer`，由窗口的展开进度驱动。
- 顶部：40×5 的抓手；左侧大标题（`title1`，随窗口内滚动收缩成居中小标题，复用 `AdaptiveTopAppBar`）；右侧 30dp 圆形关闭键。窗口内进入下一级后，左侧出现返回箭头。
- 手势：下拉超过 30% 或速度超过 1200dp/s 关闭，否则回弹；支持系统返回手势。
- 窗口内导航：自己的返回栈 `windowStack`，左右滑入（复用现有页面切换曲线）。
- 宽屏（≥ 600dp）：居中 560dp 宽，最高 86%，四角都是 36。
- 键盘：窗口内容 `imePadding`。
- **页面内容不重写**：窗口里渲染的就是现有的各个 Screen。让 `UI/components/MiuixScaffoldPage.kt` 的 `MiuixScaffoldPage` / `MiuixScaffold` 和 `AdaptiveTopAppBar` 感知「在窗口里」（新增 `LocalEtaWindow`）：
  - 不加状态栏内边距（`AdaptiveTopAppBar` 里的 `windowInsetsPadding(WindowInsets.systemBars.only(Top))` 在窗口内跳过）；
  - 页面底色透明（由窗口外框提供）；
  - 返回按钮：窗口栈深度为 1 时不显示（用右上角关闭键），深度 > 1 时显示返回箭头。

导航改造（`UI/app/AgentAppRoot.kt`）：

1. 给 `AppRoute` 加扩展属性 `presentation: Page | Window`（按 §4 的表）。
2. 新增 `windowStack: SnapshotStateList<AppRoute>`（`rememberSaveable`，路由已可序列化，见 `AppRouteSerializationTest`）。
3. `pushRoute(route)`：`Window` 类路由压进 `windowStack`；`Page` 类路由先清空 `windowStack`（播放关闭动画）再走原来的 `backStack`。
4. `popRoute()`：`windowStack` 非空时先弹它。
5. 把 `NavDisplay { entry<…> { … } }` 里属于 Window 的条目内容抽成本地函数 `@Composable fun WindowRouteContent(route: AppRoute)`（`when (route)`），窗口宿主和原 `entry` 都调用它。原 `entry` 保留，保证从通知等入口直达时仍可用。

### 3.4 其他

- **对话框**：现有 `EtaOverlayDialog` 只改样式——居中、宽 300dp、圆角 28、玻璃外框（0.94），两个按钮并排各 48dp 高，危险操作红字。
- **后台任务胶囊**（阶段 14）：对话页有后台任务运行时，顶部控件下方居中出现一枚深色玻璃胶囊（`#161616` 82%，高 32dp）：运行圈 + 当前任务标题与步骤。点一下原地展开成卡片（任务列表、停止、「查看全部」→ 打开任务窗口）。数据用现有的运行中任务状态（`UI/components/ConversationLiveTasks.kt`、`runningTaskCount`）。**有了它之后，右上角恢复成单个 44dp 圆形「＋」**，不再放运行圈。
- **悬浮侧栏**（阶段 16）：离左、上、下各 8dp，宽 `min(320dp, 屏宽 84%)`，圆角 36，玻璃外框（0.86，模糊背后的对话）；右侧露出的对话盖 14% 的黑，点一下关闭。内容：标题 + 搜索图标；五格功能入口（任务 / 角色 / 浏览器 / 电脑 / Kimi，白色实色方块）；对话列表；底部设置圆钮 + 黑色「新建会话」。
- **新对话空白页**（阶段 16）：输入胶囊放在屏幕垂直方向约 58% 处，上方是爪印、问候和建议；发出第一条消息后，胶囊用「跟手」弹簧滑到底部。

## 4. 页面呈现方式

| 方式 | 路由 |
| --- | --- |
| **整页 Page** | `Home`、`Browser`、`Terminal`、`Workspace`、`LinuxFiles`、`CharacterDetail`、`CharacterEditor`、`DesktopChat` |
| **窗口 Window** | `Settings`、`ModelProviders`、`ModelProviderDetail`、`ModelProviderNew`、`CommunityCatalog`、`CommunityCatalogProvider`、`Tools`、`WebToolsSettings`、`Skills`、`McpServers`、`McpServerDetail`、`Subagents`、`ConversationTitleSettings`、`AssistantOverlaySettings`、`AssistantSettings`、`AppearanceSettings`、`SpeechSettings`、`SpeechRecognition`、`SpeechSynthesis`、`SpeechOss`、`DataBackup`、`Permissions`、`SystemEnhance`、`Memory`、`CommonTasks`、`Tasks`、`Characters`、`CharacterPersona`、`CharacterMemory`、`DesktopSessions`、`DesktopBridgeSettings`、`SharedFolders`、`LinuxEnvironment`，以及 §6 新增的 `ContextCompactionSettings` |

规则：从窗口里打开整页（如电脑会话列表 → 电脑对话）时，先关掉所有窗口再进整页；从该整页返回时回到对话页，不重新打开窗口。

## 5. 每条回复下的统计小字（阶段 13）

- 现状：统计数据已随 2026111402 合入，但 `TurnStatsPill` 放在操作行里，而操作行只在最新一条回复下显示（`UI/components/AgentChatBody.kt` 里 `showCopyAction = … message.id == latestAnswerId`），所以旧回复看不到。
- 改法：在 `UI/components/ChatMessageItem.kt` 的回复正文下方、操作行上方，新增 `TurnStatsLine(usage)`，**不受 `showCopyAction` 限制**。显示条件：`message.usage?.hasPerformanceStats == true && !message.isStreaming && revealComplete && !assistantOverlay`，且设置开关打开。
- 文案：`52 tok/s · 首字 0.6s · 4.8s`。三段分别是生成速度（`decodeTokens / decodeMs`）、平均首字时间（`ttftMs / ttftSteps`）、总用时（`llmMs + toolMs`）；哪一段没有数据就省略哪一段。格式化复用 `UI/components/ChatStatsPopover.kt` 里的 `formatTokensPerSecond`、`formatStatDuration`。
- 样式：`micro`（11sp）+ tabular + `textQuiet`，上边距 8dp。点一下打开本轮统计卡片（现有内容）。从操作行里删掉 `TurnStatsPill`。
- 设置：外观里加「回复下方显示速度与用量」，默认开（`AppearanceSettings.showTurnStats`）。
- 只有 2026111402 之后产生的回复有数据，更早的回复不显示。

## 6. 后台异步压缩上下文（阶段 13）

### 6.1 现状

`AG/model/AgentContextSession.kt` 的 `compact()` 在每一步开始前被 `AgentLoop` 调用。输入 token ≥ 窗口 × `TRIGGER_RATIO`（0.85）时，**当场同步**调用 `AgentContextCompactor`，用当前对话模型把除最新一条用户消息外的全部历史压成摘要，压完才继续。问题：卡住前台；用贵模型；一次压光，近期细节损失大。

### 6.2 目标

- 用量到 60% 时在后台启动压缩，前台照常运行。
- 只压较早的部分，最近 4 轮用户对话原样保留。
- 压完后在下一个安全点（下一步开始前，或下一轮开头）把旧历史换成摘要；压缩期间新产生的消息原样接在后面。
- 可指定专用的压缩模型；不指定则跟随对话模型（此时仍能复用提示缓存）。
- 到 85% 后台还没好就等它；后台失败就退回现有同步压缩。任何情况下都不会超出窗口。

### 6.3 设置

- 新增 `DATA/model/ContextCompactionSettings.kt`（`@Serializable`）：`background: Boolean = true`、`modelId: String? = null`、`reasoningEffort: String? = null`、`startPercent: Int = 60`、`keepRecentTurns: Int = 4`。
- 存取照 `ConversationTitleSettings` 的写法：`DATA/datastore/SettingsDataStore.kt` 加 `contextCompactionSettingsFlow()` / `contextCompactionSettings()` / `setContextCompactionSettings()`（key `context_compaction_settings_v1`）；`DATA/repository/RuntimeConfigRepository.kt` 加同名转发，以及 `suspend fun compactionModelConfig(): AgentModelClient.ModelConfig?`——`modelId` 为空或该模型已停用时返回 null（表示跟随对话模型）；否则照 `applyTitleGenerationSettings` 关掉所有工具、关掉自动压缩、思考等级默认取最低。
- 设置页：新增路由 `AppRoute.ContextCompactionSettings` 和 `UI/screens/settings/ContextCompactionSettingsScreen.kt`，照 `ConversationTitleSettingsScreen.kt` 写。条目：自动压缩（开关，沿用 `Prefs.Keys.AGENT_AUTO_COMPACTION_ENABLED`）、后台压缩（开关）、压缩模型（复用 `SubagentModelPickerContent`，继承项文字「跟随对话模型」）、思考等级、开始压缩（50% / 60% / 70%）、保留最近（2 / 4 / 6 轮）、一段说明文字（含「对话历史会发送给所选模型的服务商」）。
- `UI/SettingsScreen.kt` 第 333 行附近的「自动压缩」开关行改成进入该页的箭头行「上下文压缩」。

### 6.4 实现（`AG/model/`）

**a. `AgentContextCompactor.compactWithOutcome` 加参数 `protectLatestUser: Boolean = true`。** 为 false 时不保留「最新用户消息」（`protectedUser = null`），整段都进摘要。后台压缩传 false，因为它压的是旧前缀，最近几轮本来就原样留在后面。

**b. 新文件 `AgentBackgroundCompaction.kt`：**

```kotlin
internal object BackgroundCompactionRegistry {          // 进程内，按会话保存一个任务
    class Result(val cut: Int, val fingerprint: String, val summary: List<JSONObject>,
                 val usage: AgentTokenUsage?, val archiveId: String)
    enum class State { RUNNING, READY, FAILED }
    fun state(key: String): State?
    fun start(key: String, work: () -> Result)           // 起线程 "agent-context-precompact"
    fun takeReady(key: String): Result?                  // 取走并清除
    fun failures(key: String): Int
    fun clear(key: String)
}
```

**c. `AgentContextSession` 新增构造参数 `backgroundKey: String? = null`**（放末尾）。只有主对话传值：`AgentLoop` 新增同名参数并转给 Session；`AG/model/AgentModelClient.kt` 第 207 行创建 `AgentLoop` 时传 `sessionId`（来自 `AgentRuntimeRunExecutor.kt:376` 的 `request.effectiveModelSessionId`）。它的定义在 `AG/runtime/AgentRuntimeWire.kt:179`：优先 `modelSessionId`，其次交接信息里的会话 ID，都没有时退回 `runId`。前两种在同一对话的多次运行间不变，后台任务可以跨轮接上；退回 `runId` 的入口每轮不同，后台结果只在本轮内有效，这是可接受的。 子代理、标题生成、重写回复都不传（保持现有同步行为）。

**d. `compact(force, final)` 的新流程**（`force = true` 的手动压缩完全不变）：

```
0. SignedToolContext.hasPendingResponse(messages) → 同现状
1. applyReady()                                    // 见下
2. 窗口未知 / 未开自动压缩 / backgroundKey == null / settings.background == false → 走原逻辑，结束
3. before >= window * 0.85（硬线）：
     registry 里有 RUNNING 且 !final → 每 100ms 检查一次并 runController.throwIfCancelled()，最多等 90s；
                                        变成 READY 就 applyReady() 并结束
     其余情况 → 走原来的同步压缩，结束
4. before >= window * startPercent 且 registry 里没有本会话任务 且 失败次数 < 2 → startBackground()
5. publishSnapshot()（原逻辑）
final = true 时不等待：任务留在 registry 里，下一轮开头的第 1 步会接上。
```

```
startBackground():
  history = messages[systemCount..] 的深拷贝（JSONObject(it.toString())）
  k   = 从后往前数第 keepRecentTurns 个用户消息（role == "user" 且没有 _eta_observation）的下标
  cut = 满足 e <= k 且 AgentContextCompactor.canSplit(history, e) 的最大 e；找不到或 cut < 2 → 不启动
  fingerprint = sha256(history.take(cut) 每条 toString()，用 '\n' 连接)
  cfg = compactionModelConfig() ?: config            // null = 跟随对话模型
  registry.start(key) {
      AgentContextCompactor(cfg, ProviderClientFactory.getClient(cfg), AgentRunController() /* 独立，不随本轮取消 */,
          roleplay, mainTools = …, mainSessionId = "$mainSessionId-compact", messageProjector,
          enableCacheReuse = enableCacheReuse && cfg === config)
        .compactWithOutcome(system 消息 + history.take(cut), systemCount, sensitiveIds 的快照, protectLatestUser = false)
      → Result(cut, fingerprint, 结果里去掉 system 的部分, usage, archiveId)
  }
  onEvent(ContextCompaction(op, PHASE_BACKGROUND_STARTED, before))

applyReady():
  r = registry.takeReady(key) ?: return
  当前 history.take(r.cut) 的指纹 != r.fingerprint → 丢弃并 return      // 用户编辑、删除或重新生成过被压范围
  candidate = system 消息 + r.summary + history.drop(r.cut)
  publishSnapshot(candidate)        // 失败时照现有 try/catch 恢复 compacted 标志并丢弃结果
  原地替换 messages；inputTokens = null；compacted = true
  onEvent(ContextCompaction(op, PHASE_COMPLETED, before, usage = r.usage, archiveId = r.archiveId))
```

**e. 事件与界面：**

- `AG/runtime/AgentEvent.kt` 的 `ContextCompaction` 加常量 `PHASE_BACKGROUND_STARTED = "background_started"`。
- **检查所有消费这个事件的地方**（搜 `ContextCompaction`：`AgentRuntimeWire`、`AgentRunMessageProjector`、`AgentAppState`）。后台开始**不能**把 `isCompacting` 置为 true（那会锁住输入框并显示「正在压缩」）。在 `AgentChatUiState` 新增 `backgroundCompacting: Boolean`：收到后台开始置 true，收到完成或失败置 false。
- 输入胶囊里的上下文圆环（16dp，`brand` 弧 + `outline` 轨道，用量 ≥ 30% 才显示）在 `backgroundCompacting` 时旁边加一个呼吸的小点；点开的用量小窗里显示「正在后台整理较早的对话」。完成后沿用现有的那行小提示。

**f. 测试**（照 `app/src/test/.../agent/model/` 里现有的压缩测试写，用假的 provider）：

1. 到软线启动后台任务，前台 `messages` 在任务完成前不变；
2. 任务完成后下一次 `compact()` 应用：前缀被摘要替换，尾部消息原样、顺序不变；
3. 指纹不匹配时结果被丢弃；
4. 到硬线且任务在跑：等待后应用，不再同步压一次；
5. 任务失败：到硬线走同步兜底；失败 2 次后不再后台重试；
6. `final = true` 不阻塞；
7. `force = true` 行为不变；
8. 切点不拆开工具调用批次（`canSplit`），并保留最近 N 轮。

### 6.5 已知取舍（写进设置页说明和更新记录）

- 指定了专用模型就吃不到对话模型的提示缓存；便宜模型的输入单价通常低得多，多数情况仍更省。
- 压缩模型的窗口应不小于对话模型；更小时摘要请求可能失败，会自动退回同步压缩（用对话模型）。
- 验证码、密码等敏感工具结果在送去摘要前已被剔除（`AgentConversationCodec.transcript(…, sensitiveIds)`），后台路径沿用。

## 7. 分期

### 阶段 13 · 收干净、规范落地、统计小字、后台压缩

1. §2.1–2.5 的 token 改动（含测试、启动页底色）。
2. §2.6 回收清单全部执行。
3. 对话页按参考图①：模型胶囊只留名称和下箭头（去掉品牌图标）；三个控件等高 44dp；滚动边缘渐隐；用户气泡实色；输入胶囊一行为「＋ · 思考等级文字 · 留白 · 上下文圆环 · 话筒 · 发送键（36dp 圆，`brand`，`requiredSize`）」。右上角本阶段保留「运行圈 + 新建」胶囊，阶段 14 有了任务胶囊再换成单个圆钮。
4. §5 统计小字。
5. §6 后台压缩与设置页。
6. §8 的渲染测试。

验收：全 App 只有 §2.6 列出的位置是玻璃；彩色光晕消失；每条新回复下有统计小字；用量到 60% 时输入框不锁、圆环旁出现小点，稍后出现「上下文已压缩」提示且最近几轮对话仍在上下文里。

### 阶段 14 · 浮层与小窗

1. `EtaOverlayLayer`（§3.1）、`EtaPopover`（§3.2）。
2. 迁移：长按菜单、模型选择、思考等级、附件、上下文用量、本轮统计、侧栏对话长按菜单。
3. 后台任务胶囊；右上角恢复为单个「＋」。
4. 对话框样式；外观里的「玻璃」四档。

验收：以上入口都从点击处展开、原路收回，背后内容真模糊；没有一处因此跳页或弹出底部面板。

### 阶段 15 · 玻璃窗口与设置迁移

1. `EtaGlassWindow`、窗口栈与导航改造（§3.3）。
2. `MiuixScaffoldPage` / `MiuixScaffold` / `AdaptiveTopAppBar` 的窗口模式。
3. §4 表里的 Window 路由全部改为窗口呈现。

验收：从对话页打开设置及任何子页都不离开对话；下滑或返回逐级退出；键盘、深色、宽屏下布局正确。

### 阶段 16 · 侧栏、空白页与收尾

1. 悬浮侧栏、新对话空白页（§3.4）。
2. 宽屏窗口形态；系统悬浮胶囊与预览窗的外观向本规范靠拢（做不到真模糊就用磨砂色）。
3. 性能：同屏真模糊层不超过 4 个；滚动中不新增模糊层；系统省电模式下自动降为磨砂。
4. 深色模式逐页过一遍；§8 清单全量自查。
5. 把 01–03 里与本文不一致的描述改掉。
6. 可选，最后做：输入胶囊和顶部控件的边缘折射（AGSL `RuntimeShader`，只在「通透」档启用）。**做不到稳定满帧就不做。**

## 8. 细节验收清单（每个阶段交付前自查）

- [ ] 固定尺寸控件不变形：新增渲染测试，在 320dp 宽 + 字体 200% 下渲染对话页顶栏和输入胶囊，断言发送键、圆形按钮的宽等于高。
- [ ] 文字过长时省略，不把控件顶破、不意外换行。
- [ ] 可点区域 ≥ 48dp。
- [ ] 圆角符合 §2.3 的同心表。
- [ ] 分隔线 0.5dp、从文字起点开始、末行无线。
- [ ] 图标只用 `Icons.Outlined` / `Icons.AutoMirrored.Outlined`；尺寸只有 22（顶部控件）、20（列表行）、18（行内操作）、16（标签内）。
- [ ] 一屏只有一处珊瑚橘（搜 `brand` / `accent` 的使用点核对）。
- [ ] 变化的数字用 tabular。
- [ ] 每个控件都有按下、禁用、加载、空、出错的样子。
- [ ] 动效可中途反向；`reduceMotion` 下只有淡入淡出。
- [ ] 键盘弹出时，输入胶囊、小窗、窗口位置正确。
- [ ] 深色模式单独看过，不是简单反色。
- [ ] 无障碍：图标按钮有 `contentDescription`，标题有 heading 语义，浮层有 `paneTitle`。
- [ ] 玻璃上的文字始终清楚（文字下方的有效底色不透明度 ≥ 0.6；小窗和窗口 ≥ 0.9）。
