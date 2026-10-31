# Eta → 鸿蒙：界面与交互 1:1 移植规范

制定：2026-10-30。对应 [ROADMAP](ROADMAP.md) 的 M1（任务 E01–E09）。

## 1. 目标

鸿蒙版的界面结构、视觉、文案和交互逻辑**照着 Eta 源码逐页移植**，不再“借鉴后重新设计”。0.9 的 `ui/foundation/` 只参考了 Eta 的一部分，M1 完成后整体替换。

能力范围仍受鸿蒙普通应用权限限制：界面照搬，功能按第 6 节分级处理。

## 2. 来源与基准

| 项目 | 位置 |
| --- | --- |
| Eta 源码 | 本仓库根目录 `app/`，基准提交 **`3b1794d70881c3c849b53d7778dd2bfdf0a46cc5`**（最后一次修改 `app/` 的提交） |
| 界面代码 | `app/src/main/kotlin/io/github/mangi/eta/ui/`（约 3.7 万行 Kotlin / Compose） |
| 界面逻辑单测 | `app/src/test/kotlin/io/github/mangi/eta/ui/`（71 个文件） |
| 中文文案 | `app/src/main/res/values-b+zh+Hans/*.xml`（约 1500 条） |
| 英文文案 | `app/src/main/res/values/*.xml` |
| 组件规范 | 仓库根目录 `docs/UI_COMPONENTS.md` |
| 官方截图 | 仓库根目录 `docs/Screenshots/`：`chat_home.jpg`、`settings.jpg`、`tools.jpg`、`skills.jpg` |

Eta 之后若有更新，不自动跟进；需要同步时，在新任务卡中写明新的基准提交。

## 3. 许可证（必须遵守）

Eta 使用 **PolyForm Noncommercial 1.0.0**，Required Notice 为 `Copyright © 2026 蛮吉 (Mangi-11).`。移植代码属于修改后的作品，因此：

1. `harmony-agent/` 增加 `LICENSE`（复制根目录 LICENSE 全文，含 Required Notice）。`THIRD_PARTY_NOTICES.md` 写明哪些部分移植自 Eta。
2. 应用内“设置 → 关于”显示：基于 Eta 移植，原作者蛮吉 (Mangi-11)，许可证 PolyForm Noncommercial 1.0.0，以及 Required Notice 原文。
3. 仅限非商业用途，不上架收费，不加广告。
4. 每个移植文件的开头注释写明来源，例如 `// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaCard.kt @ 3b1794d`。
5. 许可证不授予商标权。**应用名保持“随身助手”**；Eta 文案中自称“Eta”的地方（例如“交给 Eta 去完成”）替换为“随身助手”。“关于”页可以写“基于 Eta”来说明来源。
6. 第三方资源：Material Icons 为 Apache-2.0，JetBrains Mono 为 OFL-1.1，Miuix 为 Apache-2.0。使用时一并写入 `THIRD_PARTY_NOTICES.md` 和 `rawfile/licenses/`。

## 4. 技术对照表（Compose / Miuix → ArkUI）

| Eta（Compose / Miuix） | 鸿蒙（ArkUI / ArkTS） | 注意 |
| --- | --- | --- |
| `Column` / `Row` / `Box` | `Column` / `Row` / `Stack` | `Arrangement`、`Alignment` 改为 `justifyContent`、`alignItems` |
| `LazyColumn` + `items(key)` | `List` + `LazyForEach`（数据源 + 稳定 key） | 短列表可以用 `ForEach`。key 不要包含会变化的长文本 |
| `Modifier.padding / size / clip(RoundedCornerShape(n.dp))` | `.padding / .width / .height / .borderRadius(n)` | `dp` 换成 `vp`，`sp` 换成 `fp`，数值保持一致 |
| Miuix 连续圆角（squircle） | `borderRadius` | ArkUI 没有完全等价的实现，属于可接受差异，记入差异清单 |
| `MiuixTheme.colorScheme.*` / Eta 颜色常量 | `$r('app.color.*')`，分 `base` 和 `dark` 两套 `color.json` | 颜色不能硬编码，必须支持深浅色 |
| `EtaPreferenceDefaults` 等尺寸常量 | `core/eta/tokens.ts` 中的同名常量 | 页面中不出现魔法数字 |
| `stringResource(R.string.x)` | `$r('app.string.x')` | key 与 Android 保持一致，由 E01 脚本生成 |
| `Icons.Rounded.X` 等 | `$r('app.media.ic_rounded_x')`（SVG）+ `.fillColor()` | 由 E01 脚本生成 |
| `remember { mutableStateOf() }` | `@State` / `@Local` | 只放纯界面状态，例如展开、按下 |
| ViewModel / Store 的 `StateFlow` | `core/eta/*.ts` 中的控制器 + `snapshot()` | 沿用 0.9 的规则：页面只渲染快照、只发意图 |
| Miuix Navigation（`AppRoute` / `NavKey`） | `Navigation` + `NavDestination` | 路由名与 `AppRoute` 一一对应；页面栈由 core 持有，可以单测 |
| `ModalBottomSheet` / Miuix 底部弹层 | `bindSheet` | |
| `EtaWindowDialog` / `EtaOverlayDialog` | `CustomDialogController` 或 `promptAction.openCustomDialog` | 取消在左、确认在右，与 `MiuixDialogActions` 一致 |
| `EtaSwitch`（44 × 24） | `Toggle({ type: ToggleType.Switch })`，颜色和尺寸按 Eta 设置 | |
| `AnimatedVisibility` / `animate*AsState` | `transition` / `animateTo` / 属性动画 | 时长和曲线尽量照抄 |
| 触感反馈 `HapticFeedback` | `@kit.SensorServiceKit` 的 `vibrator` | 需要 `ohos.permission.VIBRATE`（normal 级），新增时在 PR 中说明 |
| `miuix-blur` / 背景模糊 | `backgroundBlurStyle` / `backdropBlur` | |
| 预测性返回 `PredictiveBackController` | `Navigation` 自带的侧滑返回 | |
| `WindowInsets` / 输入法 | `expandSafeArea`、`KeyboardAvoidMode` | |
| `Toast` | `promptAction.showToast` | |

## 5. 逻辑层：连同单测一起移植

Eta 的界面状态逻辑大量写在 `ui/app/` 的 Store、Reducer 中，而且有单测。做法：

1. 移植为 `entry/src/main/ets/core/eta/*.ts`（纯 TypeScript，不依赖 ArkUI），文件名用 camelCase，例如 `messageQueueReducer.ts`。
2. 同名单测 `app/src/test/kotlin/io/github/mangi/eta/ui/app/*Test.kt` 逐条翻译为 `tests/eta/*.test.ts`，用例名保持可对照。**单测全部移植并通过，是行为与 Eta 一致的主要证据。**
3. Android 专属的部分（Context、Intent、Root、无障碍）用接口隔离，平台实现放在 `platform/`。

优先移植：`AgentMessageQueueReducer`（排队与插话）、`AgentConversationRevisionReducer`（编辑、重新生成）、`AgentRunEventCoalescer`、`AgentRunMessageProjector`、`AgentRuntimeHistoryReducer`、`ConversationTimeLabels`、`ConversationMarkdownExporter`、`AssistantConversationSeed`、`AgentAppState` 的会话加载，以及 `ui/markdown/` 的解析部分。

0.9 中已经验证过的状态规则（见 AGENTS.md 的“架构规则”）继续有效。Eta 的行为与这些规则冲突时，以 Eta 为准，但要在 PR 的“与 Eta 的差异”中说明。

## 6. 页面清单与能力分级

- **A：完整移植**。界面和功能都在鸿蒙实现。
- **B：先移植界面**。功能要到后续任务才接入，在此之前入口行显示为禁用（灰色），摘要写“鸿蒙版尚未接入”，不能假装可用。
- **C：隐藏**。依赖 Android、Root、Hook 或鸿蒙不开放的能力，不显示入口，也不移植代码。

| Eta 路由 | Eta 源文件 | 分级 | 任务 |
| --- | --- | --- | --- |
| Home（首页、聊天、侧栏、顶栏） | `screens/home/AgentHomeScreen.kt`、`components/AgentChatBody.kt`、`ChatMessageItem.kt`、`AgentChatInputBar.kt`、`AgentChatModelControls.kt`、`AgentModelPicker.kt`、`AgentChatQueueStrip.kt`、`AgentWorkSteps.kt`、`AgentWorkProcess.kt`、`AgentToolActivity.kt`、`AgentStatusCard.kt`、`ConversationSidePaneScaffold.kt`、`ConversationPanePanel.kt`、`app/AgentAppShell.kt` | A；输入栏的 Atom（手机操作）模式为 B | E03、E04 |
| Settings | `ui/SettingsScreen.kt` | 按第 7 节逐行处理 | E05 |
| AppearanceSettings | `ui/AppearanceSettingsScreen.kt` | A（Monet 取色不适用，隐藏该项） | E05 |
| ModelProviders / ModelProviderDetail / ModelProviderNew / CommunityCatalog / CommunityCatalogProvider | `pages/providers/*`（11 个文件） | A；未实现的协议类型为 B（T09） | E06 |
| Memory | `screens/memory/AgentMemoryScreen.kt` | A | E08 |
| Skills | `screens/skills/*` | A（导入、开关、查看）；依赖执行环境的部分为 B | E08 |
| McpServers / McpServerDetail | `screens/mcp/McpServersScreen.kt` | A | E08 |
| Characters / CharacterDetail / CharacterEditor / CharacterPersona / CharacterMemory | `screens/characters/*` | A | E08 |
| Subagents | `screens/subagent/SubagentSettingsScreen.kt` | A | E08 |
| Tools / WebToolsSettings | `screens/tools/*` | 页面 A；工具卡片按鸿蒙实际能力显示（手机操作类工具在 M3、M4 之前为 B） | E08 |
| DataBackup | `screens/backup/DataBackupScreen.kt` | A（默认不导出 Key） | E08 |
| SpeechSettings / SpeechRecognition / SpeechSynthesis / SpeechOss | `ui/voice/*` | B | E08（只做界面） |
| Permissions | `screens/permissions/PermissionHealthScreen.kt` | 改为鸿蒙权限：网络、通知、无线调试连接；Android 专属项为 C | E08 |
| Browser | `screens/browser/*` | B（以后用 ArkWeb 实现） | 之后 |
| Workspace | `screens/terminal/WorkspaceScreen.kt` | B（以后用应用沙箱文件实现） | 之后 |
| Terminal / LinuxEnvironment / SharedFolders / LinuxFiles | `screens/terminal/*` | C | — |
| SystemEnhance | `screens/enhance/SystemEnhanceScreen.kt` | C | — |

## 7. 设置页逐行分级（对照 `SettingsScreen.kt`）

| 分组 | 行 | 分级 |
| --- | --- | --- |
| LLM 提供商 | 模型提供商、默认启用思考 | A |
| 上下文与扩展 | 自动压缩上下文、记忆、Subagent、Skills、MCP 服务器、角色 | A |
| 工具 | 工具列表、网页搜索与读取（设置页） | A |
| 工具 | 启用网页搜索、读取与浏览器 | B |
| 工具 | 启用设备直达工具、允许读取敏感设备信息、允许敏感设备操作 | B（M3、M4 接入） |
| 工具 | 启用终端文件工具 | B |
| 工具 | Linux 工具环境、系统增强 | C |
| 系统助手接管 | 整组（系统助手、长按电源键、自动设为默认助手） | C（见 `POWER_KEY.md`） |
| 小布 / 小爱兼容入口、Gemini、一圈即搜 | 整组 | C |
| 通用 | 外观、数据备份 | A |
| 通用 | 语音 | B |
| 权限 | 悬浮窗权限、无障碍增强、强制无障碍 | C；在同一位置新增鸿蒙的“无线调试连接”行（B，T10 接入） |
| 关于 | 后台诊断（指向 M0 的诊断页）、版本、反馈、GitHub | A |
| 关于 | 后台通知 | B（M5） |
| 关于 | 检查更新 | C（鸿蒙版不提供应用内更新） |

C 类分组整组隐藏时，分组标题也一起隐藏，不留空分组。

## 8. 资源移植（E01，用脚本生成，不手工抄写）

| 资源 | 做法 |
| --- | --- |
| 文案 | `scripts/port-eta-strings.py`：读取 `values-b+zh+Hans/*.xml` 和 `values/*.xml`，生成 `resources/base/element/string.json`（中文，默认）和 `resources/en_US/element/string.json`。处理转义（`\'`、`\"`、`\n`、`&amp;`）、占位符（`%1$s` 等，以 SDK 文档支持的格式为准）和 plurals；“Eta”按第 3 节替换。脚本要配单测 |
| 颜色 | Eta 的 `EtaPreferenceColors`、`StatusColors`、设置页背景 `#F0F1F2`、强调色 `#0080FF`，以及 Miuix 0.9.4 的浅色和深色默认配色（从 Miuix 源码 `theme/Colors.kt` 提取，Apache-2.0），写入 `resources/base/element/color.json` 和 `resources/dark/element/color.json` |
| 尺寸 | `EtaPreferenceDefaults`（边距 16、组间距 16、图标 24、图标与文字间距 16、行最小高度 52、分割线 0.33）、`EtaCardDefaults`（圆角 24）、`EtaControlDefaults`（按钮圆角 22、最小高度 44、开关 44×24、对话框边距 24 / 16）等，写入 `core/eta/tokens.ts` |
| 图标 | `scripts/port-eta-icons.mjs`：扫描 `app/src/main/kotlin` 中用到的 `Icons.*`（共 148 个）和 `MiuixIcons.*`（1 个），从 npm 包 `@material-design-icons/svg`（Apache-2.0）复制对应样式的 SVG，Rounded 对应 round 目录；`res/drawable` 中的 11 个矢量图（`ic_atom`、`ic_assistant_send` 等）转换为 SVG；全部输出到 `resources/base/media/`。生成对照表 `docs/eta-icons.json` |
| 字体 | JetBrains Mono（代码块使用）放到 `resources/rawfile/fonts/` 并注册；正文使用鸿蒙系统字体 |
| 品牌图标 | `ProviderBranding.kt` 中各模型服务的品牌标识按原样移植，只用于标识对应服务 |

## 9. 每个页面的验收标准

1. **结构**：Eta 源文件中的每个分组、行、按钮、菜单项和弹窗在鸿蒙页面中都存在，顺序一致；分级为 B 或 C 的按第 6、7 节处理。PR 附“逐项对照表”（Eta 元素 → 鸿蒙实现 → 状态）。
2. **尺寸和颜色**：只用 `tokens.ts` 常量和 `$r('app.color.*')`，深浅色都正确。
3. **交互**：点击、长按、菜单、弹窗、返回、滚动、空状态、加载、错误状态的行为与 Eta 源码一致。
4. **逻辑**：对应的 Eta 单测已经移植并全部通过。
5. **截图**：首页、设置、工具能力、Skills 与 `docs/Screenshots/` 中的四张官方截图并排对照，由用户在真机上截图。其他页面对照 Eta 源码。
6. **差异清单**：所有无法一致的地方，例如连续圆角、系统字体、隐藏的 C 类入口，都写进 PR 的“与 Eta 的差异”。没有写进清单的差异视为缺陷。

## 10. 不要做的事

- 不改根目录的 Android 代码。
- 不自行“优化”或重新设计 Eta 的界面；觉得 Eta 有问题时，先在 PR 中提出，由用户决定。
- 不为 C 类功能写占位页面。
- 不把 B 类入口做成看起来可用、点了没反应的样子。

## 11. 已确认的决定（2026-10-31）

1. **E03 宽屏选 A**：所有宽度均为推移式侧栏，照抄 `ConversationSidePaneScaffold.kt`；宽度为 min(屏宽 × 0.84, 340vp)，全宽聊天舞台同步推移，不做宽屏常驻。ROADMAP 旧任务卡的常驻要求已纠正。
2. **L1–L4**：同意显式平台端口、自研 GFM 解析、固定 unicode-segmenter@0.14.0、最小 DTO 与单调时钟。
3. **菜单、弹窗、动画**：先用鸿蒙原生机制；真机检查点 ①、② 截图与 Eta 对照后，再决定是否自绘个别组件。
4. **触感**：允许新增 `ohos.permission.VIBRATE`，normal / system_grant、安装时自动授予、不弹窗；按 Eta 调用点接入，PR 单列权限，并更新 verify-release 权限白名单。E02 追加提交，再合入下游。
5. **渐进模糊**：先用 API 12 `linearGradientBlur` 作用于顶栏下方的内容层，模拟 ProgressiveBlur.Top、曲线 2.2 / 半径 10 / 表面 alpha 0.3；EtaGallery 提供预览。无法实现则实色降级，外观设置对应项按 B 类“鸿蒙版尚未接入”。E02 追加后合入下游。实现尝试与实测结论分开记录。

继续队列：E02 追加 → E03 → E04a → E04b → E04c → E07 → E05 → E06；完成后可继续 E08 记忆 → Skills → MCP。仍按无人值守叠加 PR，四项检查通过才前进，不合并 PR、不推送 main；真机项全部待测。

### 已确认的决定（2026-10-31 第三轮）

9. **会话存储选方案 A**（Claude 建议，用户转发第三轮指令即视为确认）：完整迁移到 Eta 的存储模型，按 E07B_STORAGE_DECISION.md 执行，拆成 E07b1（逻辑与端口）、E07b2（RDB 与迁移代码，不切换）、E07b3（切换）。E07b3 必须在真机检查点 ①–④ 通过后进行；迁移不删除旧数据。
10. **模型协议照抄 Eta**：原 T09 改为 P1，移植 `agent/model/` 中的四种协议及公共部件，连同单测。用户使用 OpenAI，所以先做 Responses。
