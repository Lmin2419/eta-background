# 09 · 优化方案（2026111603 之后）

这份方案有两个读者：你（拍板要做哪些）和接手执行的模型（照着做）。每一项都写了现状、做法、验收和容易踩的坑，
文件名和函数名都是 2026111603 这一版核实过的。标了「先核实」的地方，动手前必须先用 grep 确认。

## 0. 方向和总表

界面示意和给用户看的说明在方案页面：https://claude.ai/artifact/QMhpkDbLgpfQWXYRbKpSnx 。这份文件是执行细则。

三个方向：
1. **首页是工作台**：打开应用先看到正在跑的、刚完成的、可以接着做的。
2. **读结果，不读过程**：回复完成后，思考和步骤收成一行。
3. **省着用**：模型和思考强度一键切；答得不好只把这一条换个模型重答；用了多少一眼看到。

性能有余量（OPPO PMA110、120Hz：开模糊每帧 5.8ms、GPU 4.0ms，帧预算 8.3ms），所以不需要为性能做改动。

| 编号 | 内容 | 期 | 条目 |
| --- | --- | --- | --- |
| U2 | 一套状态点：运行中、等你处理、完成未读、出错未读 | 一 | 1.1、1.6 |
| P3 | 任务页并入电脑会话 | 一 | 1.2 |
| L2 | 选择文字一键复制 | 一 | 1.3 |
| L3 | 「复核报错再试」只在用过工具的回合出现 | 一 | 1.4 |
| P2 | 侧栏一行小格子，加终端 | 一 | 1.5、1.6 |
| U1 | 整轮过程收成一行 | 二 | 2.1 |
| L1 | 标题胶囊是「这个对话」的总入口 | 二 | 2.2 |
| F1 | 模型预设 | 三 | 3.1 |
| F2 | 换模型重答 | 三 | 3.2 |
| P1 | 新对话页变成工作台 | 四 | 4.1 |
| F4 | 消息多选 | 五 | 5.1 |
| L4 | 搜索结果直达 | 五 | 5.2 |
| F3 | 用量页 | 六 | 6.1 |

## 1. 不要做的事

这些看起来像改进，实际风险大于收益，执行时不要顺手做：

- **不合并两套模糊**（Miuix `LayerBackdrop` 与 COUI backdrop）。实测性能够用，合并要动首页全部玻璃控件。
- **不重构上游的大文件**：`AgentAppState.kt`（3010 行）、`AgentChatBody.kt`、`ChatMessageItem.kt` 只做局部改动。它们是上游文件，结构变了以后合上游会很痛苦。
- **不碰 Root 引擎**（`etax/engine`），不合上游自带的虚拟屏（原因见 `08-upstream-344.md`）。
- **不做自动复审**。已定：「复核报错再试」只在用户点的时候发。
- **不加新的玻璃或动效种类**。新界面一律复用 `EtaFloatingSurface`、`CircleIconButton`、`EtaContextMenu`、`EtaSheet`、`EtaButton`。

## 2. 第零期 · 地基（一个版本，界面不变）

目的是让后面每一期都能放心验证。全部做完后截图应与 2026111603 一致。

### 0.1 本机测试全绿

- **现状**：本机全量测试固定有 5 个失败，CI 上都通过：`AgentFileOperationsTest.deepDirectoryResumesAfterOutputBudgetWithoutLosingOrRepeatingNames`、
  `BackgroundTerminalJobsTest.commandContinues…`、`TerminalPrivateStorageTest.ordinaryDirectories…`、`DexKitNativeLibraryFileTest`（2 个）。
- **做法**：先找根因，再决定怎么处理。线索：前两个的失败信息里中文变成了 `??`，像是测试进程的文件名编码不是 UTF-8，
  先试在 `app/build.gradle.kts` 的 `unitTests.all { }` 里给测试进程加 `-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8` 或环境变量 `LC_ALL=C.UTF-8`。
  后三个像是因为本机用 root 跑测试，「不能访问」类的断言不成立。确实是环境造成的，用 `org.junit.Assume` 在这种环境下跳过（例如 `assumeFalse(System.getProperty("user.name") == "root")`）。
- **验收**：本机 `:app:testDebugUnitTest` 失败数为 0；CI 上的跳过数仍是 3（说明 CI 照常跑了这些测试，没有被一起跳过）。
- **注意**：只允许「这个环境跑不了」时跳过，不许为了变绿而删断言或无条件跳过。

### 0.2 删掉已经走不到的旧代码

- **现状**（已核实）：`RoutedShell` 只剩首页和终端两个页面在用，两者都有自己的玻璃顶部，所以 `AgentAppShell.kt` 里的 `AgentTopBar` 和 `TopBarOverflowMenu` 已经走不到；
  `AgentModelPicker.kt` 里的 `AgentModelPickerButton` 没有任何调用方；`HomeConversationTopBar` 的 `runningTaskCount` 参数已不使用。
- **做法**：删除这三处及只被它们用到的私有函数、导入。`ModelPickerRow`、`ModelBrandMark` 还有别处在用，留着。`rememberTopBarBackdrop()` 留着（首页顶部渐隐模糊在用）。
  删之前对每个符号再 grep 一次调用方。只测被删代码的测试一起删，其余测试不动。
- **验收**：编译、全量测试、Lint 通过；`device-mode-screens` 里首页、侧栏、终端的截图与上一版一致。

### 0.3 把任务胶囊和完成提示条拆成单独文件

- **做法**：把 `HomeConversationTopBar.kt` 里的 `HomeBackgroundItem`、`HomeTaskCapsule`、`DoneNoticeItem`、`HomeDoneNotice`、`DoneNoticeCapsule`、`DoneNoticeTracker`、
  `desktopRunningSessions`、`desktopDoneNotice`、`opensPreview` 原样移到新文件 `ui/components/HomeBackgroundTasks.kt`。只移动，不改逻辑。第一期要在这些代码上加东西。

### 0.4 截图补三张

- **做法**：在 `DeviceModeScreens.kt` / `DeviceModeScreensTest.kt` 里加：任务页（`TasksScreen`）、电脑会话列表（`DesktopSessionsScreen`，COUI 顶栏本机画不了，用 `deviceMode = false`）、
  选择文字面板的内容（直接渲染 `TurnSelectionContent`，不经过半窗）。各出浅色、深色。
- **验收**：CI 产物 `device-mode-screens` 里多出这几张，肉眼看过没有空白和裁切。

## 3. 第一期 ·「闭环」（一个版本）

### 1.1 完成了一定看得到：未读圆点

- **现状**：`HomeDoneNotice` 的提示条 6 秒后收起。停在首页时，本机任务不弹横幅、电脑会话不发通知（`DesktopReminders.onTurn` 里 `homeVisible` 时直接返回），错过就没有痕迹。
  电脑端其实已经有未读集合 `DesktopBridge.unread`，但只在 `DesktopSessionsScreen` 里显示；本机对话没有未读概念。
- **做法**：
  1. 新建 `agent/runtime/FinishedUnseen.kt`：进程内的 `MutableStateFlow<Set<String>>`（本机对话 id），`mark(id)`、`clear(id)`。不落盘。
  2. 记：`AgentExecutionService` 里 `if (firstCompletion) scheduleCompletionAlert(...)` 这一行旁边，任务有对话 id、不是用户自己停的（`AgentOverlayStatus.Stopped`）、
     且 `ForegroundConversation.isShowing(id)` 为假时 `mark`。不要放进 `scheduleCompletionAlert` 里面，那里受「完成提示」开关影响。
  3. 清：`AgentAppRoot` 里设置 `ForegroundConversation.id` 的那个 `SideEffect`，当前对话不为空时 `clear`。
  4. 显示：首页左上角的历史按钮右上角加一个 8dp 强调色圆点，条件是「本机未读集合或 `DesktopBridge.unread` 里有不是当前对话的项」；
     侧栏会话行（本机行和混排的电脑行）标题后面加同样的圆点。圆点的画法参考 `DesktopChatScreen` 里子代理按钮上的数字角标（外面包一层 `Box`，角标 `align(TopEnd)`）。
- **验收**：`FinishedUnseen` 的单元测试（记、清、正在看的不记、用户停的不记）；真机模式截图 `home-unseen-light`（测试里先 `mark` 一个）；
  手机上：A 对话发长任务，切到 B，等提示条消失，历史按钮有圆点，侧栏里 A 有圆点，点开 A 后都消失。
- **注意**：先核实 `DesktopBridge.unread` 什么时候加、什么时候清，只读它，不改桥的逻辑。

### 1.2 任务页并入电脑会话

- **现状**：`TasksScreen` 有「运行中 / 已完成 / 常用任务」三段，数据只来自 `AgentExecutionService.taskSnapshots`。
- **做法**：「运行中」一段在本机任务后面列出 `desktopRunningSessions(DesktopBridge.sessions.value, 已连接)` 的结果：电脑图标、标题、一个「查看」按钮。
  `TasksScreen` 加参数 `onOpenDesktopSession: (String) -> Unit`，`AgentAppRoot` 里调用处传 `{ id -> pushRoute(AppRoute.DesktopChat(id)) }`。
  「已完成」不加电脑会话（电脑的历史在电脑会话页里）。两边都没有任务时才显示空状态。
- **验收**：0.4 的任务页截图里注入一条电脑会话能看到；单测覆盖「只有电脑会话在跑时不显示空状态」。

### 1.3 选择文字面板加「复制全部」「只复制回答」

- **现状**：`TurnSelectionContent`（`ui/components/TurnTextSelection.kt`）把整轮的思考和回答放在一个可选区域里，但长内容仍要拖选择柄。
- **做法**：同文件加纯函数 `turnPlainText(segments, includeThinking): String`（段落之间空一行）。面板文字上方加一行两个小号 `EtaButton`：
  「复制全部」（含思考）、「只复制回答」（这一轮没有思考时只显示一个「复制全部」）。按钮放在 `DisableSelection` 里，点完用 `EtaToastCenter` 提示「已复制」。字符串三种语言都要加。
- **验收**：`TurnTextSelectionTest` 加 `turnPlainText` 的用例；0.4 的面板截图里能看到按钮。

### 1.4 「复核报错再试」只在用过工具的回合出现

- **现状**：条件是 `message.content.looksLikeGaveUp()`（`RecheckAction.kt`，关键词）。纯聊天里谈到「失败」也会出现。
- **做法**：`RecheckAction` 加两个字段 `turnUsedTools`、`lastToolFailed`，在 `AgentChatBody` 构造它的地方从 `messages` 算：
  取最新回答所在的那一轮（复用 `turnSegments` 里找轮次边界的逻辑，抽成 `turnRange(messages, answerId)`），看里面的 `ToolActivityMessageUi`。
  显示条件改为：`turnUsedTools && (lastToolFailed || looksLikeGaveUp())`。电脑会话同样适用（那边的工具步骤也是 `ToolActivityMessageUi`）。
- **验收**：纯函数单测：纯聊天不出现；最后一步工具失败时即使没有关键词也出现；工具都成功且没有关键词时不出现。

### 1.5 终端入口放进侧栏

- **现状**（已核实）：能打开终端的只有 `SettingsScreen.kt` 里的一行，和已经走不到的旧溢出菜单。
- **做法**：`ConversationPanePanel.kt` 里构造 `PaneTile` 列表的地方（「任务、角色、浏览器、电脑」那一组），在浏览器后面加「终端」（`Icons.Outlined.Terminal`、`R.string.route_terminal`）。
  回调沿着 `workspace` 那个参数对象加一个字段，由 `AgentAppShell` 已有的 `onOpenTerminal` 传进来。
- **验收**：侧栏截图（浅色、深色、200% 字体）格子排列整齐；有电脑、Kimi 格子时最多 6 个，先核实格子是怎么换行的。

### 1.6 第一期的两处补充

- **状态点分四种**（接 1.1）：运行中用已有的 `StatusRing(TaskVisualStatus.Running)`；电脑会话有待审批或待回答（`DesktopBridge.approvals`、`DesktopBridge.questions` 里有这个会话）时是橙点；
  完成未读是强调色点；出错未读是红点（`FinishedUnseen` 里要同时记下是否出错）。左上角历史按钮只显示一个汇总的点，优先级是橙、红、强调色。颜色用 `EtaTheme.colors` 里已有的注意色、危险色、强调色。
- **格子超过 4 个改横滑**（接 1.5）：`PaneTileRow` 现在是等宽的方块排成一行。格子数不超过 4 时不变；超过 4 时改成 `Row(Modifier.horizontalScroll(...))`，每格固定 72dp 宽。
  顺序改为：任务、电脑、终端、浏览器、角色、Kimi。整行包一层 `CappedFontScale`，200% 字体时标签不再被截断。
  注意：侧栏自己能横向拖动收起，格子行可横滑后，在格子上左滑会先滚格子，这和代码块的行为一致，可以接受；参考 `SidePaneGestureTest` 加一条测试确认侧栏别处仍能拖动收起。

## 4. 第二期 ·「读起来轻」（两项各一个版本）

### 2.1 整轮过程收成一行（U1）

- **现状**：`List<AgentChatMessageUi>.toTimelineEntries()`（`AgentChatBody.kt`）把连续的思考、工具步骤合成 `AgentTimelineEntry.WorkProcess`，中间的说明文字和最终回答各是一条 `AgentTimelineEntry.Message`。
  所以一轮回答里「思考 / 已完成 · N 步 / 说明」交替出现，最终回答排在最后。
- **做法**：
  1. `AgentTimelineEntry` 加一种 `TurnProcess(key, summary, expanded)`，只代表那一行摘要。
  2. 新文件 `ui/components/TurnProcessFold.kt`，纯函数 `foldFinishedTurns(entries, latestTurnRunning, expandedKeys): List<AgentTimelineEntry>`：
     按用户消息把条目分成轮；每一轮找最后一条 `AgentMessageUi`（最终回答）；它前面至少有一个 `WorkProcess` 时，在这一轮最前面放一条 `TurnProcess`，
     并把「用户消息之后、最终回答之前」的条目去掉（key 在 `expandedKeys` 里时保留这些条目）。key 用 `"turn-" + 这一轮第一条过程条目的 key`。
     正在进行的最后一轮不折叠；没有最终回答的轮（被停止、出错）不折叠。
  3. 摘要：思考次数（`ThinkingMessageUi` 个数）、步骤数（`ToolActivityMessageUi` 个数）、失败步数、用时（各 `ThinkingMessageUi.elapsedSeconds` 之和，取不到就不显示）。
     文字形如「过程 · 思考 2 次 · 12 步 · 2 分 14 秒」，有失败时加「· 1 步失败」并用危险色。
  4. 在调用 `toTimelineEntries` 之后接上这个函数；展开的 key 集合用 `rememberSaveable` 存在 `AgentConversationMessages` 里。摘要行的样式照 `AgentWorkHeader`（30dp 高、小号灰字），点一下切换展开。
  5. `AppearanceSettings` 加 `foldFinishedProcess: Boolean = true`，写法照 `showTurnStats`（`SettingsDataStore` 的键、读、写三处，`AppearanceSettingsScreen` 一行开关）。关掉时不调用折叠函数。
- **注意**：这是全方案里最容易出问题的一项。历史翻页（`ChatHistoryPaging.kt`）按条目的 key 记阅读位置，传给它的必须是折叠之后的同一个列表。
  动手前把 `AgentConversationMessages` 里所有用到 `timelineEntries` 的地方读一遍。`finalResultMessageIds`、`latestAnswerId`、`turnSegments` 都按消息算，不受影响。
  `toTimelineEntries` 靠复用条目实例来跳过重组，折叠函数对没变的轮也要返回同一个 `TurnProcess` 实例（按 key 和摘要比较后复用）。
- **验收**：纯函数单测：一轮里多组过程收成一条；进行中的不折；没有最终回答的不折；展开集合生效；历史前插后 key 不变。
  `ChatHistoryPagingTest` 仍通过。真机模式截图 `home-fold-light`（收起）和展开各一张。

### 2.2 标题胶囊是「这个对话」的总入口（L1）

- **现状**：`HomeConversationTopBar.kt` 里的 `ModelPopover` 菜单只有模型、自动朗读、管理模型。`HomeConversationTopBar` 的 `onExport` 参数传进来了但没用。重命名、导出要去侧栏长按。
- **做法**：对话有标题时，菜单在模型之后加一条分隔线和「重命名」「导出」两项（第五期再加「多选消息」）。导出直接用已有的 `onExport`。
  重命名加一个参数 `onRename`，`AgentAppRoot` 里把 `conversationRenameTarget` 设成当前对话的那条 `ConversationSummaryUi`（已有的改名对话框会弹出）。
  模型超过 8 个时走的是半窗，同样的两项加在半窗底部。新对话（没有标题）不显示这一段。
- **验收**：用 `Stage3TestPanel` 打开菜单，点「重命名」「导出」各触发一次回调。

## 5. 第三期 ·「省着用」上（两项各一个版本）

### 3.1 模型预设（F1）

- **做法**：
  1. 数据：`data class ModelPreset(id, name, modelId, effort: ReasoningEffort)`，整个列表存成一条 JSON 字符串放进 `SettingsDataStore`；新建 `ModelPresetRepository`（读成 Flow、保存整个列表）。JSON 解析失败时当作空列表。
  2. 应用：`AgentAppState.applyPreset(preset)`：先 `selectModel(preset.modelId)`，再 `updateReasoningEffort(preset.effort)`。顺序不能反，思考强度要按新模型的能力规整。
     回复进行中时两样都排到下一轮：现有的 `queuedModels` 只记了模型，要同时记下思考强度，在 `applyQueuedModel` 里一起生效。
  3. 界面：标题胶囊菜单最上面一段列出预设，每项标题是名字，说明是「模型名 · 思考强度」（`EtaMenuItem` 有 `summary` 参数），当前模型和强度都对得上时打勾。
     这一段最后一项「存为预设…」用 `EtaCouiInputDialog` 问名字，把当前模型和思考强度存进去。预设里的模型已被停用时，这一项置灰并注明「模型不可用」。
  4. 管理：设置的「模型与对话」一组里加一行「模型预设」，进去是列表，可以改名、删除。页面用 `MiuixScaffoldPage` 和现有的设置行组件。
- **验收**：仓库单测（存取、坏 JSON）；`presetMatches(当前模型, 当前强度)` 纯函数单测；菜单里点预设触发 `applyPreset`。

### 3.2 换模型重答（F2）

- **现状**：`AgentAppState.regenerateMessage(messageId)` 已有；重新生成会产生多个候选，回答下面能左右切换（`candidateCount`、`selectReplyCandidate`）。
- **做法**：
  1. `AgentAppState` 加 `regenerateWithModel(messageId, modelId)`：前置条件和 `regenerateMessage` 相同；先把这个对话绑定的模型改掉
     （`updateCurrentConversation(homeState.copy(modelId = modelId))`、`refreshConversationModel()`、`persistConversations()`），再调 `regenerateMessage(messageId)`。
     **不要**调 `selectModel`，它会连全局默认模型一起改。
  2. 新增 `AgentHomeAction.RegenerateWithModel(messageId, modelId)`，沿着现有 `onRegenerateMessage` 的路径接到 `AgentChatBody` 的新参数 `onRegenerateWithModel`（默认值为空表示不支持）。
  3. 回答的长按菜单（`ChatMessageItem.kt` 的 `EtaContextMenu`）在「重新生成」下面加「换模型重答」，点了在同一个位置弹第二个菜单列出模型；超过 8 个时用半窗，写法照 `ModelPopover`。
     模型列表由 `AgentChatBody` 用一个 `CompositionLocal` 提供。电脑会话不支持重新生成，那边不提供，菜单项不出现。
- **验收**：先看有没有 `AgentAppState` 的现成测试可以照着写；没有就测到回调这一层。手机上：小模型答一次，换大模型重答，回答下面出现「2 / 2」可以切换，设置里的默认模型没变。

## 6. 第四期 ·「工作台」（一个版本）

### 4.1 新对话页变成工作台（P1）

- **现状**：`HomeEmptyState.kt`：爪印、问候语、副标题，输入框上方一排建议胶囊。
- **做法**：
  1. `ChatHomeShortcuts` 加字段：`running: List<HomeBackgroundItem>`、`recent: List<ConversationSummaryUi>`、`onOpenConversation`、`onOpenDesktopSession`、`onOpenTasks`。
     在构造 `ChatHomeShortcuts` 的地方填：`running` 用 0.3 拆出来的那套逻辑（本机未完成的任务加 `desktopRunningSessions`）；`recent` 取未归档、有标题的对话按更新时间取前 3 个。
     数据从外面传进来而不是在 `HomeEmptyState` 里直接读全局状态，这样测试能注入。
  2. 布局：`running` 和 `recent` 都为空时保持现在的样子。否则问候语缩成一行（去掉副标题和大爪印），下面依次是「正在进行」（最多 3 条，多了显示「全部任务」）、「接着做」（标题、时间、未读点）。
     两段都用实色卡片（`EtaTheme.colors.surface`），行高和侧栏会话行一致。建议胶囊仍在输入框上方。角色对话的新对话页不变。
  3. 这一页不显示顶部的任务胶囊：`HomeConversationTopBar` 加参数 `showBackgroundCapsule`，对话里没有消息时传假。完成提示条照常显示。
- **验收**：真机模式截图：`home-new`（没有内容，与现在一致）、`home-workbench-light/dark/font200`（注入两条进行中、两条最近）。点各行触发对应回调的测试。

## 7. 第五期 ·「批量与直达」（每项一个版本，可以只做 5.1）

### 5.1 消息多选（F4）：批量复制、分享、删除

- **现状**：长按一条消息出菜单，一次只能操作一条；整段对话只能整体导出。
- **做法**：
  1. 状态放在 `AgentChatBody`：`Set<String>?`，为空表示不在多选。用一个 `CompositionLocal`（仿照 `LocalRecheckAction`）传给 `ChatMessageItem`。
  2. 入口：标题胶囊菜单的「多选消息」，以及用户消息和回答的长按菜单各加一项「多选」，点了就进入多选并选中这一条。回复进行中不能进入。
  3. 多选中：点用户消息或回答切换选中；思考、工具步骤不能单独选。**不改消息布局**：选中的消息铺一层强调色 10% 的圆角底，右上角一个 18dp 对勾。长按菜单和回答下面的按钮此时不响应。
  4. 底部：输入框的位置换成一条玻璃栏（`EtaFloatingSurface`，圆角与输入框相同）：「已选 N 条」、复制、分享、删除、取消。
     复制的格式由纯函数 `selectionPlainText(messages, ids, 用户称呼, 助手称呼)` 生成：按对话顺序，每条前面是「我：」或「胖小胖：」（角色对话用角色名），条与条之间空一行。
     分享走系统分享（`ACTION_SEND`、`text/plain`）。删除先用 `EtaCouiDialog` 确认，再逐条调用已有的 `onDeleteMessage`。电脑会话的历史不能改，不显示删除。
  5. 返回键退出多选（`NavigationBackHandler`，参考侧栏搜索栏 `PaneNativeSearchDock` 的写法）。
- **验收**：`selectionPlainText` 单测；用 `Stage3TestPanel` 测进入、切换、返回退出；真机模式截图 `home-multiselect-light/dark`。
- **注意**：底部玻璃栏要拿到和输入框同一个采样源（`LocalEtaCouiBackdrop`）；多选状态在切换对话时清掉。

### 5.2 搜索结果直达命中的那条消息（L4）

- **现状**：侧栏搜索能搜到对话内容（`AgentAppState.updateSearchQuery`），点结果只是打开对话，停在最底部。
- **做法**：从搜索结果打开对话时把搜索词带进去。`AgentChatBody` 收到后在已加载的消息里从新到旧找第一条包含这个词的，滚动到它，并让它的底色高亮 1.5 秒。
  没找到而且还有更早的消息时，最多再加载 3 页；还找不到就停在底部并提示「匹配的内容在更早的消息里」。
- **注意**：这一项风险最高。列表里的下标不等于消息的下标（「已完成 · N 步」把多条工具步骤合成了一项），要按条目的 key 找；
  定位时要先把「保持贴底」关掉（`onBottomAnchorChanged(false)`），否则会被拉回底部。做之前先读 `ChatHistoryPaging.kt`。
- **验收**：单测覆盖「在已加载的消息里找到」「要翻页才找到」「找不到」三种；手机上实际搜一个很早的词试。

## 8. 第六期 ·「省着用」下（先调研，再做）

### 6.1 用量页（F3）

- **目标**：设置里加「用量」：今天用了多少（输入、输出、缓存命中率）、最近 7 天柱状图、按模型分；可以设一条每日提醒线，超过时用提示条提醒一次。只算手机上的对话。
- **先调研，写一段结论再动手**：
  1. 每次模型请求的用量是在哪里落到消息上的（从 `TokenUsageUi` 的赋值处往回找，上游 3.4.3 的用量统计代码）。
  2. 消息有没有时间和模型 id；能不能直接从已存的对话里按天、按模型汇总。
  3. 应用重启后恢复运行（`recoverRuntimeRuns`）会不会把同一次请求的用量再应用一遍。
- **两种做法，按调研结果选**：能从已存的对话汇总，就按需汇总，不新增存储。不能，就从这一版开始记账：每次请求结束记一笔（日期、模型、各项 token）到应用私有目录下按月分的 JSON 文件，
  用「运行 id + 第几次请求」去重，之前的不补。不要为这个加 Room 表和数据库迁移。
- **验收**：汇总或记账逻辑的单测（跨天、去重、坏文件）；页面截图；提醒线一天只提醒一次。

## 9. 以后再说

1. **定时运行常用任务**（比如每天 8 点汇总未读通知）。应用里现在没有任何定时机制，要设计后台启动、锁屏时怎么办、失败怎么通知。值得做，但要单独出方案。
2. 输入框里打「/」调出常用任务和技能。
3. 设置页搜索。
4. 「记住这次的解决办法」：复核成功后，一键把教训写进记忆。
5. 上游的全局助手唤出光效（`4101bc7`）：先录两边的对比再定。

核实后去掉的：状态栏实时进度（`AgentExecutionService` 的任务通知已经用了 `Notification.ProgressStyle` 和 `setShortCriticalText`，不用做）；
「这次对话改了哪些文件」面板（按上游约定工具记录不保存文件路径，没有数据）。

## 10. 需要你拍板（括号里是不回复时的默认做法）

1. 过程收成一行：回复完成后默认收起还是展开？（收起，外观设置里可以关）
2. 换模型重答之后，这个对话接着用新模型还是换回原来的？（接着用新模型，全局默认模型不变）
3. 用量页要不要「每日提醒线」？（要，出厂不设值）
4. 顺序：先「读起来轻」还是先「省着用」？（按期号；两期互不依赖，可以对调）
5. 侧栏要不要加「终端」格子？（加）
6. 多选时选中的样式：浅色底加右上角对勾，不在行首加勾选圈。（这样做，不会让正文重新排版）

## 11. 进度

| 期 | 版本 | 结果 |
| --- | --- | --- |
| 零 地基 | 2026111604 | 本机测试全绿（UTF-8 语言环境、权限位断言）；删外壳旧顶栏与旧模型选择钮；胶囊与提示条移到 `HomeBackgroundTasks.kt`；选择文字面板截图。任务页截图推到第一期 1.2 一起做。同版修了真机反馈：COUI 骨架滑不动、电脑会话页看不到手机任务 |
| 一 闭环 | 2026111605 | `FinishedUnseen` 未读圆点（侧栏行、历史按钮汇总，电脑用 `DesktopBridge.unread`）；任务页「进行中」加电脑会话；选择文字一键复制；复核按钮看工具（`turnToolSignal`）；侧栏终端格子、超过 4 个横滑。任务页截图仍未加（要 `CommonTasksStore`） |
| 二 2.1 过程收成一行 | | |
| 二 2.2 标题胶囊总入口 | | |
| 三 3.1 模型预设 | | |
| 三 3.2 换模型重答 | | |
| 四 工作台 | | |
| 五 5.1 消息多选 | | |
| 五 5.2 搜索直达 | | |
| 六 用量页 | | |

## 12. 给执行模型的守则

这些是前面二十多个版本踩出来的，和 `AGENTS.md` 一起读：

1. **一期一个版本、一次提交**。提高 `app/build.gradle.kts` 的 `versionCode`（`yyyyMMdd` 加两位序号），在 `etax/README.md` 最上面写更新说明，
   同步 `README.md`、`README_EN.md` 的版本号，并填上面第 11 节的进度表。
2. **修问题先复现**。在本机复现出来（截图或失败的测试），改完用同一个测试证明，再出包。没复现就改，前面白白多出过三个版本。
3. **界面改动先看真机模式截图再出包**（`DeviceModeScreens`，`EtaCouiSupport.shaders = true` 加硬件渲染）。截图里关了模糊，只看布局、深色、大字体。
4. **同一时间只跑一个 Gradle**，否则内存不够会被杀。**Lint 运行期间不要改 Kotlin 源文件**，会让 Lint 分析崩掉。
5. **推送会取消工作分支上正在跑的构建**。要等上一版的 APK 上传完再推下一版。工作分支上只有手动触发（`workflow_dispatch`）才出签名 APK。
6. **不建 PR、不合并到 `main`、不发 Release**，除非用户明确说。
7. 自定义布局里按下标取子项的，每个子项各包一层容器；组件不要往父布局里发看不见的节点（首页整页空白就是这么来的）。
   新增背景采样源要用 `trackRenderSource` 登记。
8. 用到 Compose 的测试，结束前把主线程排着的任务跑完（`ShadowLooper.idleMainLooper()`），否则会让同一进程里后面的测试偶发失败。
9. 字符串三种语言都要加（`values`、`values-b+zh+Hans`、`values-b+zh+Hant`）。配对码、令牌不进仓库。
10. 方案里写的文件名、函数名以 2026111603 为准，动手前 grep 一次；对不上就以代码为准，并在更新说明里写一句。
