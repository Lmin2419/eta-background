# 01 · 设计语言「静流」· 胖小胖配色

> 一句话：**界面安静，AI 在动。** 平时只有暖白中性色和一个焦糖主色；只有 AI 正在工作的地方才出现暖色流光和动效。
>
> 2026-11-06 起 App 更名 **胖小胖**，采用配色 B「蜂蜜焦糖」（与金渐层猫图标统一）。下表为现行数值；原冷色「静流」数值已废弃。用户扫一眼就知道哪里在跑、哪里要他处理。

## 1. 设计原则

1. **内容是主角。** 对话正文、任务画面占最大面积；操作入口退到边缘，需要时才出现。
2. **状态一眼可读。** 运行中、需要你、已暂停、已完成、失败五种状态，在 App、通知、浮窗、预览窗里**颜色、图形、文案完全一致**（见 §8）。
3. **一套语言覆盖所有表面。** App 页面、电源键助手面板、顶部胶囊、任务预览窗、通知共用同一组 token。
4. **每屏一个主操作。** 实心品牌色按钮每屏最多一个；其余用浅色、描边或文字按钮。
5. **动效只表达变化。** 只给状态切换、空间关系（展开/收起/进出）做动画；静止内容不动。系统开启「减少动画」时全部降级为 ≤120ms 淡入淡出。
6. **轻。** 不新增字体文件、不引入新 UI 框架；继续用 Compose + Miuix + material-icons-extended（Rounded）。

## 2. 颜色

所有颜色以 token 使用，**页面代码里不再出现裸 `Color(0x…)`**（除 token 定义文件）。

### 2.1 中性色

| Token | 用途 | 浅色 | 深色 |
| --- | --- | --- | --- |
| `canvas` | 页面底色 | `#FBF6EE` | `#12100D` |
| `surface` | 卡片、分组列表、侧栏 | `#FFFFFF` | `#1B1814` |
| `surfaceRaised` | 输入栏、弹层、底部面板、浮窗 | `#FFFFFF` | `#24201B` |
| `surfaceSunken` | 用户气泡、输入框、代码块、搜索框 | `#F4EBDD` | `#2B261F` |
| `outline` | 1px 分隔线、卡片描边 | `#EADFCD` | `#362F27` |
| `outlineStrong` | 输入框聚焦前描边、次要按钮描边 | `#D9CBB5` | `#4A4136` |
| `textPrimary` | 正文、标题 | `#201A12` | `#F3EDE4` |
| `textSecondary` | 摘要、说明 | `#5E5347` | `#BDB2A4` |
| `textTertiary` | 时间、占位、页脚 | `#6E6254` | `#9C9082` |
| `scrim` | 遮罩 | `#1A120A` 40% | `#000000` 56% |
| `pressed` | 按压叠加层 | `#2A1A08` 8% | `#FFFFFF` 8% |

对比度已核对：`textTertiary` 在 `surface` / `canvas` 上 ≥ 4.5:1；`textSecondary` ≥ 6:1。

### 2.2 品牌色

| Token | 浅色 | 深色 | 说明 |
| --- | --- | --- | --- |
| `accent` | `#B25A0E` | `#F0A65A` | 焦糖色（深色模式为蜂蜜色），主按钮、链接、选中、运行中 |
| `onAccent` | `#FFFFFF` | `#2A1600` | 主按钮上的文字和图标 |
| `accentSoft` | `#FCEAD5` | `#3D2B17` | 浅色按钮、选中行、品牌徽章底 |
| `onAccentSoft` | `#8A4507` | `#F8C894` | `accentSoft` 上的文字 |

**Monet / 自选强调色：** 外观设置里开启动态取色或选了其他强调色时，`accent` / `accentSoft` / `onAccentSoft` 用 Miuix `ThemeController` 生成的 primary 系列替换；其余 token 不变。默认值（未开 Monet、强调色为「系统」）为上表的焦糖色。

### 2.3 流光渐变（Aurora）

只用于「AI 正在工作」：运行中的状态环、电源键唤醒的边缘光与声波、输入栏发送瞬间、AI 运行中的进度条。**不得用作卡片或页面背景。**

```
aurora = linear-gradient(100°, #FFC861 0%, #FF9F7A 35%, #F27BA8 70%, #B9A3FF 100%)   // 蜂蜜 → 蜜桃 → 玫瑰 → 淡紫
```

代码中为 `EtaAurora.honey / peach / rose / lavender`，边缘光与声波都引用它。动画：渐变沿方向平移，一圈 2.4s，线性循环；「减少动画」时静止。

### 2.4 状态色

| Token | 前景（浅/深） | 底（浅/深） | 用于 |
| --- | --- | --- | --- |
| `running` | 同 `accent` | 同 `accentSoft` | 运行中 |
| `attention` | `#6D46DB` / `#B8A2FF` | `#EFEAFF` / `#2A2342` | 需要你处理（接管、确认、验证码、权限缺失）。用紫色，避免与焦糖主色混淆 |
| `paused` | 同 `textSecondary` | 同 `surfaceSunken` | 已暂停 |
| `success` | `#1E8E4E` / `#5CD08A` | `#E3F5EA` / `#13301F` | 完成 |
| `danger` | `#D33636` / `#FF7A7A` | `#FDE8E8` / `#3A1717` | 失败、删除、停止 |

替换现有 `StatusColors.kt` 的 `StatusSuccess` / `StatusWarning`。

### 2.5 分类色（图标底块）

设置、能力等列表的前导图标放在 32dp 圆角底块里，按类别着色。浅色模式：底 = 该色 12% 不透明度叠在 `surface` 上，图标 = 该色；深色模式：底 = 该色 20%，图标 = 该色提亮版。

| Token | 色值 | 类别 |
| --- | --- | --- |
| `tileBlue` | `#3B82F6` | 模型、对话 |
| `tileCaramel` | `#D27A2C` | 后台执行、任务 |
| `tileViolet` | `#8B5CF6` | Skills、Subagent、记忆 |
| `tilePink` | `#E0569B` | 角色、外观 |
| `tileOrange` | `#F08C00` | 系统助手、电源键 |
| `tileGreen` | `#16A34A` | 权限、无障碍 |
| `tileTeal` | `#0EA5A4` | 网页、浏览器、MCP |
| `tileSlate` | `#64748B` | 终端、Linux、数据、关于 |

## 3. 字体

- 中文与拉丁文都用**系统默认字体**（ColorOS 上即 OPPO Sans），不打包新字体。
- 等宽：沿用已打包的 JetBrains Mono（`EtaMonospace`）。
- 数字（计时、计数、token 数）开启等宽数字：`fontFeatureSettings = "tnum"`。

| Token | 字号 / 行高 (sp) | 字重 | 用途 |
| --- | --- | --- | --- |
| `display` | 30 / 38 | SemiBold 600 | 空状态问候 |
| `title1` | 26 / 34 | SemiBold | 二级页大标题（折叠前） |
| `title2` | 20 / 28 | SemiBold | 底部面板、弹窗标题；顶栏标题 18/24 |
| `headline` | 17 / 24 | SemiBold | 卡片标题、分组标题 |
| `body` | 16 / 26 | Regular 400 | 对话正文（Markdown 段落） |
| `rowTitle` | 16 / 22 | Medium 500 | 列表行标题、按钮文字 |
| `subhead` | 14 / 20 | Regular | 行摘要、说明文字 |
| `footnote` | 13 / 18 | Regular | 工作过程、工具行、次要信息 |
| `caption` | 12 / 16 | Medium，字距 0.2 | 徽章、时间、分组小标题 |
| `mono` | 13 / 20 | JetBrains Mono | 代码、命令、终端 |

Markdown：h1 22/30、h2 19/28、h3 17/26 SemiBold；段落间距 12；列表项间距 6；行内代码 `mono` 14sp，底 `surfaceSunken`，圆角 6，左右内边距 4。

## 4. 间距与布局

- 4dp 网格。可用值：`2 · 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 56`。
- 页面左右边距：手机 16，≥600dp 宽度 24。
- 对话正文最大宽度 720dp，居中。
- 列表行最小高度 56（有摘要时 64–72），图标块与文字间距 12。
- 分组列表之间间距 24；分组小标题与列表间距 8。
- 触控目标 ≥ 48dp（图标按钮视觉 40dp，触控区 48dp）。

## 5. 圆角

| Token | 值 | 用途 |
| --- | --- | --- |
| `radiusXs` | 6 | 行内代码、小徽章 |
| `radiusSm` | 10 | 图标底块、缩略图、代码块 |
| `radiusMd` | 14 | 列表选中背景、建议卡、菜单项 |
| `radiusLg` | 20 | 卡片、分组列表、用户气泡 |
| `radiusXl` | 28 | 底部面板顶部、弹窗、浮窗、输入栏 |
| `radiusFull` | 999 | 按钮、Chip、搜索框、胶囊 |

用户气泡：`radiusLg`，但右下角 8（指向自己）。

## 6. 层级与阴影

默认扁平：靠 `canvas` / `surface` / `surfaceRaised` 三层色差区分。只有浮在内容之上的东西才有阴影。

| Token | 浅色 | 深色 | 用于 |
| --- | --- | --- | --- |
| `e0` | 无 | 无 | 页面内容 |
| `e1` | `0 1 2 rgba(16,18,32,.06)` | 无阴影，改 1px `outline` 描边 | 卡片（浅色模式下可选） |
| `e2` | `0 6 24 rgba(16,18,32,.10)` + `0 1 3 rgba(16,18,32,.06)` | 1px `outline` + `surfaceRaised` | 输入栏、下拉菜单、悬浮回到底部按钮 |
| `e3` | `0 16 48 rgba(16,18,32,.18)` | 1px `outlineStrong` + `surfaceRaised` | 底部面板、弹窗、任务预览浮窗、助手面板 |

Compose 实现：`Modifier.shadow(elevation, shape, ambientColor, spotColor)` 近似；深色模式不画阴影，只画描边。

## 7. 动效

| Token | 时长 | 曲线 | 用于 |
| --- | --- | --- | --- |
| `micro` | 100ms | standard | 按压、开关、图标切换 |
| `short` | 180ms | standard | Chip、按钮状态、淡入淡出 |
| `medium` | 260ms | emphasized-decelerate | 展开/收起、消息出现、面板内容切换 |
| `long` | 380ms | emphasized-decelerate | 页面切换、浮窗出现 |

- `standard` = cubic-bezier(0.2, 0, 0, 1)；`emphasized-decelerate` = (0.05, 0.7, 0.1, 1)；退出用 `accelerate` = (0.3, 0, 1, 1)，时长取进入的 70%。
- 可拖拽的面板（侧栏、底部面板、助手面板）用弹簧：`dampingRatio = 0.86`，`stiffness = 420`。
- 消息出现：上移 8dp + 淡入，`medium`。流式文字沿用现有平滑揭示（`SmoothTextReveal`），不改。
- 运行中的状态环：弧线旋转一圈 1.1s 线性 + 流光渐变；只在可见时运行（沿用现有 `rememberDrawPhasePulse` 的做法，动画只使绘制失效，不触发重组）。
- 触感：发送、开始接管、交还 AI、任务完成、长按菜单出现时给一次轻触感（沿用 `AgentHapticFeedback`）。App 界面内统一走 `EtaHaptics`（发送 / 开关 = tick，长按菜单 = long press，回复结束 / 复制 = confirm），设置 → 个性化 →「触感反馈」可关。
- 按压回弹：可点的按钮、卡片、列表行按下时 `micro` 缩到 0.97（行 0.98），松手用弹簧（`dampingRatio = 0.55`，`stiffness = 600`）回到原大小（`etaPressScale` / `etaTouchScale`）。
- 爪印动效（`EtaPaw.kt`）：「思考中」四颗脚趾依次按下，1.4s 一轮；首页掌心先落、脚趾间隔 70ms 弹出；下拉刷新脚趾随拉开进度逐颗出现，拉满轻振。启动画面同一动作（`avd_splash_paw`）。
- 加载占位：浅灰圆角条加一道 1.3s 的柔光扫过（`etaShimmer`），用于首次加载列表、打开会话；减少动画时为静态灰条。
- 提示条：底部黑色胶囊，带小爪印，弹簧浮现（`dampingRatio = 0.72`），短提示 2.2s、长提示 3.5s。
- 「减少动画」（`Settings.Global.ANIMATOR_DURATION_SCALE == 0` 或系统无障碍设置）时：所有位移动画改为淡入淡出 ≤120ms；流光静止；状态环改为静态图形。

## 8. 状态语言（全局统一）

所有出现任务状态的地方（会话列表、对话内工作过程、实时任务条、任务中心、预览窗、顶部胶囊、通知）都用这一张表。

| 状态 | 图形（StatusRing 16/20dp） | 颜色 | 中文文案 | 何时 |
| --- | --- | --- | --- | --- |
| 运行中 | 旋转的弧线，弧线带流光 | `running` | 正在执行 / 正在{动作} | AI 在操作 |
| 需要你 | 实心圆 + 白色「!」 | `attention` | 需要你处理 | 验证码、登录、确认、权限缺失、接管请求 |
| 已暂停 | 空心圆环 + 两竖线 | `paused` | 已暂停 | 用户暂停、接管中、应用被移回主屏 |
| 已完成 | 实心圆 + 白色对勾 | `success` | 已完成 | 正常结束 |
| 失败 | 实心圆 + 白色叉 | `danger` | 未完成 | 出错、被停止（停止用「已停止」） |
| 排队中 | 虚线圆环 | `textTertiary` | 排队中 | 超过两个任务并发上限 |

这与现有顶部胶囊的 `PhaseIndicator`（运行旋转弧、暂停空心环、结束实心点）一脉相承，只是补齐了「需要你」「失败」「排队」并统一颜色。

## 9. 图标

- 只用 material-icons-extended 的 **Rounded** 家族（现有代码混用 Outlined/Rounded，统一为 Rounded；`ShortText`、`MoreHoriz` 等也换成 Rounded 版本）。
- 尺寸：顶栏 24，列表/菜单 20，行内/徽章 16。
- 图标颜色默认 `textSecondary`；选中或主操作用 `accent`；危险操作用 `danger`。
- 品牌形象「胖小胖」（阶段 12）：一枚猫爪印——四颗脚趾肉垫 + 下沿三瓣的掌心肉垫。桌面图标为白底（向边缘极轻地过渡到 `#F2EFEB`）上的珊瑚橘肉垫（`#EB9A7B → #C95F3D` 径向渐变、柔和投影和高光），爪印约占可见区 2/3；源文件 `etax/design/brand/pangxiaopang-icon.svg`，导出为 `drawable-nodpi/ic_launcher_art.webp`（648px，含可见区外的出血边）。单色场景统一用同一枚爪印的剪影 `etax/design/brand/pangxiaopang-glyph.svg`；改形状时只改这两个源文件的路径，再同步到下列矢量。资源：
  - `ic_launcher_art`（背景层，生成）+ 空的 `ic_launcher_foreground`：桌面图标；
  - `ic_eta_mark`（矢量）：App 内品牌标志（空状态、助手头像、「思考中」动画），珊瑚橘爪印带高光，无底色；
  - `ic_launcher_monochrome` / `ic_notification`：单色爪印剪影，主题图标与状态栏用，系统按 alpha 着色；
  - `ic_task_phone`：后台任务通知大图标与预览窗占位，手机轮廓 + 珊瑚橘爪印，屏幕区域透明。
  minSdk 33 只用自适应图标，无 `mipmap-*dpi` 位图。`BrandIconRenderingTest` 会把它们按桌面遮罩、主题色、状态栏着色画成预览图（`build/reports/eta-icons/`）。
- 名称：用户可见处一律「胖小胖」（三种语言相同）；包名 `io.github.mangi.etax`、内部代码名、备份文件格式不变；关于页保留对上游 Eta 的致谢。

## 10. 文案语气

- 简短、直接、说人话。按钮用动词：「接管」「交还 AI」「停止」「打开 App」。
- 状态文案说清楚**正在做什么**和**要你做什么**：「正在点击“提交订单”」「需要你输入验证码」。
- 不用感叹号，不说「亲」「哦」。错误先说结果再说原因：「没能打开美团：应用未安装」。
- 中英繁三份字符串同步（`values`、`values-b+zh+Hans`、`values-b+zh+Hant`）。
