# 02 · 组件规范

> **2026111406 起以 [05-liquid-glass-v2.md](05-liquid-glass-v2.md) 为准**：配色（纸白底、brand / onBrand / textQuiet）、玻璃使用范围（只用于浮着的控件）、小窗与玻璃窗口、悬浮侧栏、对话页顶部三控件与输入胶囊。本文与之冲突的描述均以 05 为准。

数值全部引用 [01-design-language.md](01-design-language.md) 的 token。每个组件写明：外观、状态、交互、对应的现有代码（改哪里）。

**原则：先改现有的 `Eta*` 包装组件，再改页面。** 现有 `EtaCard`、`EtaPreferenceRow`、`EtaArrowPreference`、`EtaSwitch`、`EtaChoiceChip`、`EtaTextButton`、`EtaWindowDialog`、`ListEmptyState`、`SectionHeader`、`MiuixScaffoldPage`、`AdaptiveTopAppBar` 已被 15+ 个页面使用，改它们就能让大部分二级页面自动换装。

---

## C1 · 主题入口 `EtaTheme`

新建 `ui/theme/` 包：

| 文件 | 内容 |
| --- | --- |
| `EtaColors.kt` | `data class EtaColors(...)`，§2 全部 token；`lightEtaColors()`、`darkEtaColors()`；`withAccent(primary, soft, onSoft)` 用于 Monet |
| `EtaTypography.kt` | §3 全部字号 token（`TextStyle`） |
| `EtaShapes.kt` | §5 圆角 token（`RoundedCornerShape`） |
| `EtaSpacing.kt` | §4 间距常量 |
| `EtaMotion.kt` | §7 时长、曲线、弹簧；`reduceMotion: Boolean` |
| `EtaElevation.kt` | §6 阴影 Modifier 扩展：`Modifier.etaElevation(level, shape)`，深色模式自动换描边 |
| `EtaTheme.kt` | `object EtaTheme { val colors; val type; val shapes; val motion }`，通过 `CompositionLocal` 提供 |

`AgentAppTheme` 在现有 `MiuixTheme` 外层（或内层）提供 `EtaTheme`。同时把 Miuix 的 `ColorScheme` 关键槽位映射到 Eta token（background→`canvas`、surface→`surface`、primary→`accent`、onSurface→`textPrimary` 等），让未改造的 Miuix 组件也贴近新外观。

---

## C2 · 顶栏 `EtaTopBar`

- **首页紧凑顶栏**：高 56；左 `≡`（会话）；中 `ModelPill`；右「新对话」图标 + 「任务」图标（带运行数角标）。底色 `canvas`，滚动后出现 `outline` 底线（不再用毛玻璃模糊背景，省性能）。
- **二级页大标题顶栏**：展开时标题 `title1` 左对齐，位于返回按钮下方；向上滚动折叠为 56 高、标题 `title2`（18/24）居中。沿用 `AdaptiveTopAppBar` 的折叠行为，只换样式。
- 返回按钮：Rounded `ArrowBackIosNew` 20dp，触控 48。
- 改：`AgentAppShell.AgentTopBar`、`AdaptiveTopAppBar`、`TopBarBackdrop`（去掉模糊，改纯色 + 底线）。

## C3 · 模型胶囊 `ModelPill`

- 高 36，圆角 full，底 `surfaceSunken`，左右内边距 12；内容：服务商小图标 16 + 模型名（`rowTitle` 15sp）+ `ExpandMore` 16。
- 模型名：用户自己起的名字原样显示；否则对模型 ID 去掉 `组织/` 路径前缀和日期快照后缀（`-20250929`、`-2024-08-06`、`-latest`），四位版本号如 `-0905` 保留。胶囊占满顶栏左右按钮之间的空间，不设字数上限；仍放不下时**中间省略**（保留开头的系列名和结尾的版本/规格），完整名称在对话设置面板里可换行显示。实现：`compactModelName()` / `pillName`。
- 点击 → 打开「对话设置」底部面板（见 03 · S1.4）。
- 运行中不可切换模型时：文字 `textTertiary`，点击给出提示「当前回复结束后可切换模型」。
- 改：模型选择从输入栏（`AgentChatModelControls`）移到顶栏；`AgentModelPicker` 的列表内容复用到面板里。

## C4 · 按钮

| 种类 | 外观 | 用于 |
| --- | --- | --- |
| Primary | 底 `accent`，字 `onAccent`，高 48，圆角 full，`rowTitle` | 每屏唯一主操作 |
| Tonal | 底 `accentSoft`，字 `onAccentSoft` | 次主操作（运行常用任务、接管） |
| Secondary | 透明底，1px `outlineStrong`，字 `textPrimary` | 并列的普通操作 |
| Ghost | 透明，字 `accent` | 对话框取消、链接式操作 |
| Destructive | 底 `danger` 底色，字 `danger` 前景色 | 删除、停止 |
| Icon | 40 视觉 / 48 触控，图标 24 `textSecondary`，按压出现 `pressed` 圆形底 | 顶栏、工具栏 |
| Small | 高 32，圆角 full，`footnote` 加粗 | 卡片内操作 |

状态：按压 `pressed` 叠加 + 缩放 0.97（`micro`）；禁用透明度 38%；加载中文字换成 16dp 转圈，宽度不变。
改：`EtaTextButton` 扩展为 `EtaButton(kind, size, …)`；新增 `EtaIconButton`。

## C5 · Chip

- **选择 Chip**（筛选、思考强度）：高 32，圆角 full。未选：底 `surfaceSunken`、字 `textSecondary`；选中：底 `accentSoft`、字 `onAccentSoft`、前置 16dp 图标。
- **状态 Chip**：高 24，圆角 full，左 `StatusRing` 12 + `caption` 文案，底为对应状态底色（§2.4），字为前景色。
- 改：`EtaChoiceChip`、`ToolChip`。

## C6 · 状态环 `StatusRing`

新组件，参数 `status: TaskVisualStatus`（Running / Attention / Paused / Success / Failed / Queued）、`size: 12|16|20|28`。图形见 01 §8。
- Running：2dp 弧线（270°）绕圈旋转，弧线描边用 aurora 渐变；动画只在 `drawWithCache` 里读取，不触发重组。
- 其他状态静态绘制，无动画。
- 状态切换时交叉淡入 `short`。
- 统一替换：`AgentOverlayCapsule.PhaseIndicator`、`AgentStatusCard.StatusIndicator`、`AgentWorkProcess` 头部图标、`ToolActivityStatusUi.dotColor()`、会话列表的运行标记。

## C7 · 卡片与分组列表

- **EtaCard**：底 `surface`，圆角 `radiusLg`，内边距 16；浅色 `e1`、深色 1px `outline`。
- **EtaListGroup**（设置类分组）：底 `surface`，圆角 `radiusLg`，左右与页面边距对齐（inset grouped）；组内行之间 1px `outline` 分隔线，左缩进到文字起点（16 + 32 + 12 = 60）。组标题在组外上方：`caption`、`textTertiary`、左缩进 16、距组 8。
- 改：`EtaCard`、`EtaPreferenceGroupItem`、`SectionHeader`。

## C8 · 列表行 `EtaPreferenceRow`

```
[16][图标块 32][12][标题 rowTitle            ][值/开关/›][16]
                   [摘要 subhead textSecondary]
```
- 最小高 56（有摘要 64+）。
- 图标块：32×32，圆角 `radiusSm`，分类色底（§2.5），图标 20。
- 尾部：开关 / 当前值（`subhead textTertiary`）+ `ChevronRight` 20 `textTertiary` / 无。
- 状态：按压整行 `pressed`；禁用整行 38%；带「需要你」时标题右侧加 8dp `attention` 圆点，摘要写明要做什么。
- 开关：轨道 52×32，选中轨道 `accent`、圆点白；未选轨道 `outlineStrong`。
- 改：`EtaArrowPreference`、`EtaPreference`、`EtaPreferenceRow`、`EtaSwitch`、`EtaDropdownPreference`、`EtaSelectionPreferences`。

## C9 · 搜索框

高 44，圆角 full，底 `surfaceSunken`，左 `Search` 20 `textTertiary`，占位 `subhead textTertiary`，有内容时右侧清除按钮。聚焦时 1.5dp `accent` 描边（`short`）。

## C10 · 分段控件 `EtaSegmented`

高 36，轨道 `surfaceSunken` 圆角 full，内边距 3；选中滑块 `surfaceRaised` + `e1`，字 `textPrimary` `rowTitle` 14sp；未选字 `textSecondary`。滑块移动 `medium` 弹簧。用于任务中心三个页签。

## C11 · 底部面板 `EtaSheet`

- 底 `surfaceRaised`，顶部圆角 `radiusXl`，`e3`；拖拽把手 36×4 `outlineStrong` 居中、距顶 8。
- 标题 `title2` 左对齐，距把手 12；右上可放一个文字按钮。
- 进入：从底部弹簧滑入 + 遮罩 `scrim` 淡入；下拉超过 25% 高度或快速下滑关闭。
- 菜单型面板：每项 52 高，图标 20 + `rowTitle`；危险项 `danger` 色，放最后并以分隔线隔开。
- 用于：对话设置、附件、会话长按菜单、消息长按菜单、确认停止任务。

## C12 · 对话框 `EtaDialog`

宽 min(屏宽−48, 360)，底 `surfaceRaised`，圆角 `radiusXl`，内边距 24；标题 `title2`，正文 `subhead textSecondary`；按钮横排右对齐（Ghost 取消 + Primary/Destructive 确认），高 44。改 `EtaWindowDialog`、`EtaOverlayDialog`、`MiuixDialogActions`。

## C13 · 提示条 Toast

深底胶囊（浅色模式 `#1E2027`，深色模式 `#ECEDF2` 反色），高 44，圆角 full，`subhead`；可带一个文字操作（「撤销」用 `accent` 提亮色）。位置：输入栏上方 12dp。显示 3s，滑出 `short`。

## C14 · 空状态 `EtaEmptyState`

居中：56dp 图标块（分类色）→ 16 → `headline` → 6 → `subhead textSecondary`（最多两行）→ 20 → 可选 Tonal 按钮。改 `ListEmptyState`。

## C15 · 加载

- 列表/页面首次加载：骨架块（`surfaceSunken` 圆角条），1.2s 明暗呼吸；不要转圈占满屏。
- AI 运行中的进度：高 3 的条，aurora 渐变平移（不确定进度）。
- 对话加载失败：`EtaEmptyState` + 「重试」。改 `AgentConversationLoading`。

---

## 对话专用组件

### C20 · 用户消息 `UserBubble`

- 右对齐，最大宽度 85%；底 `surfaceSunken`，字 `textPrimary` `body`；内边距 12×16；圆角 `radiusLg`，右下 8。
- 附图在气泡上方：每张 120×120（多张横排，超出横向滚动），圆角 `radiusMd`。文件引用显示为 Chip（文件图标 + 名称）。
- 长按：触感 + 底部菜单「复制 / 编辑 / 删除」。编辑态：气泡变为可编辑输入（沿用现有 `messageEdit` 流程），下方「取消」「保存并重新发送」。
- 改：`ChatMessageItem.UserMessageBubble`。

### C21 · 助手消息 `AgentMessageBlock`

- 无气泡、无头像，全宽 `body`。角色对话保留现有署名行（小头像 + 角色名）。
- 回答结束后显示操作行（左对齐，距正文 8）：复制、重新生成、朗读、更多（编辑/删除/导出），图标 18、触控 40、颜色 `textTertiary`。多候选时最左侧显示「‹ 2/3 ›」。
- 流式中不显示操作行（沿用 `resolveFinalResultMessageIds` 的规则）。
- 代码块：底 `surfaceSunken`，圆角 `radiusSm`，顶部 32 高标题条（语言名 `caption` + 复制按钮），代码 `mono`，横向滚动。
- 表格：外框 1px `outline` 圆角 `radiusSm`，表头底 `surfaceSunken`、`footnote` 加粗，单元格 `footnote`，超宽横向滚动。
- 改：`AgentMessageBlock`、`MarkdownContent` 系列的样式参数（不改解析）。

### C22 · 工作过程 `AgentWorkProcess`

- **折叠行**（默认）：`StatusRing 16` + 「工作过程 · 8 步 · 1分12秒」(`footnote textSecondary`) + `ExpandMore`；运行中文字换成当前步骤「正在点击“提交订单”」并带现有的扫光效果（`ShimmerText`）。
- **展开**：左侧 24dp 槽位放节点徽章（20dp 圆，图标 12，颜色按状态），节点间 1px `outline` 竖线；每个节点：步骤名 `footnote textPrimary` + 一行结论 `footnote textTertiary` + 右侧耗时 `caption textTertiary`（等宽数字）。
- 后台屏幕相关步骤（观察/点击/输入）右侧可附 32×56 的画面缩略图（若该步结果里有截图；没有则不显示，不新增截图请求）。
- 失败节点徽章 `danger`，可单独展开详情（沿用 `ToolActivityRow` 展开逻辑）。
- 改：`AgentWorkProcess`、`AgentWorkSteps`、`AgentToolActivity`（只改视觉；分组、懒组合、计时逻辑不动）。

### C23 · 实时任务条 `LiveTaskStrip`（新增）

对话里有后台任务在跑时，浮在输入栏正上方（排队条之上）：
```
[应用图标 28 + 右下角状态徽标] [标题: 美团 · 后台执行     ] [⏸] [查看 ›]
                                [副行: 正在点击“提交订单”  ]
```
- **状态徽标（S10.2 真机反馈修正）**：`StatusRing 12` 放在一个 16dp 实心圆垫上，圆垫颜色 = 任务条当前底色（`surfaceRaised`，需要你时 `attentionSoft`），起「抠图」作用，流光不再直接画在图标像素上。徽标圆心在图标右下角**外侧**：以图标左上为原点，圆心约在 (32, 32)。效果：圆形图标完全不被遮挡，方形图标最多压住右下角 4×4dp（正好是圆角外的透明区）。图标在行内垂直居中，徽标超出部分不撑高行。
- 多任务叠放头像：每个图标外加 2dp 同色圆垫描边，叠放处能分清前后。
- 高 56，底 `surfaceRaised`，圆角 `radiusLg`，`e2`；左右与输入栏对齐。
- 副行跟随最新步骤，单行省略，切换时上滑淡入（`short`）。
- 状态为「需要你」时：整条底色换 `attention` 底色，副行写明「需要你输入验证码」，右侧按钮变为 Tonal「去处理」。
- 点击条主体或「查看」→ 打开任务预览窗（`TaskPreviewActivity`）；`⏸` → 暂停/继续（发送与通知按钮相同的 action）。
- 多个后台任务：显示「2 个后台任务」+ 头像叠放，点击展开为列表（每行同上），再点空白收起。
- 数据：`AgentExecutionService` 的任务卡片状态（同进程），需新增一个可观察的快照 `StateFlow<List<TaskNotificationState>>`，按 `conversationId` 过滤。

### C24 · 输入栏 `AgentChatInputBar`

```
┌──────────────────────────────────────────────┐
│ [附件缩略图 64 ×]  [文件 Chip ×]               │  ← 有附件才出现
│ 给 Eta 发消息…                                 │  ← 1–8 行
│ [+] [◐ 深度]                       [🎤] [↑]  │  ← 44 高
└──────────────────────────────────────────────┘
```
- 外框：底 `surfaceRaised`，圆角 `radiusXl`，`e2`；距屏幕左右 12、距底 8（+ 输入法/导航栏 inset）。
- 输入：`body`，占位「给 Eta 发消息…」`textTertiary`；最多 8 行后内部滚动。
- `+`：打开附件面板（图片、文件、文件夹、设备路径），沿用现有四种附件入口。
- 思考强度 Chip（C5 选择 Chip，图标 + 「快速/标准/深度」）：点按弹出小菜单选择；当前模型不支持时隐藏。
- 右侧按钮状态机：

| 状态 | 右侧 | 说明 |
| --- | --- | --- |
| 空闲 · 无文字 | 麦克风（40 圆，`surfaceSunken`） | 点按语音输入；长按按住说话（与助手面板一致） |
| 空闲 · 有文字/附件 | 发送（40 圆，`accent`，`ArrowUpward` 白） | 点按发送，发送瞬间按钮外圈闪一次 aurora（`short`） |
| 运行中 · 无文字 | 停止（40 圆，`danger` 底色，`Stop` 前景色） | 点按停止当前回复 |
| 运行中 · 有文字 | 排队（40 圆，`accentSoft`，`Schedule` 图标） | 点按加入排队；**长按 = 打断并立即发送**（触感 + 气泡提示「松手打断并发送」） |

- 聚焦时外框不变（不再改变布局），仅占位文字淡出。
- 改：`AgentChatInputBar`、`ThinkingEffortChip`、`PendingImageStrip`；模型控件移出（见 C3）。

### C25 · 排队条

沿用 `AgentChatQueueStrip` 的交互（单条极简、多条可展开、点正文撤回编辑），视觉改为：`surfaceSunken` 胶囊、`footnote`、左侧 `Schedule` 16 图标、右侧计数徽章（`accentSoft` 圆）。

### C26 · 回到底部按钮

36 圆，`surfaceRaised` + `e2`，`ArrowDownward` 20；离底部超过一屏时淡入，位于输入栏上方 12、右侧 16。流式中有新内容时按钮上加 6dp `accent` 圆点。

### C27 · 上下文压缩 / 性能标记

保持居中胶囊形态：`surfaceSunken`、`caption textTertiary`、左侧 16 图标；进行中图标用 `StatusRing Running 12`。
