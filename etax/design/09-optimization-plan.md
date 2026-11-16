# 09 · 优化方案（2026111603 之后）

这份方案有两个读者：你（拍板要做哪些）和接手执行的模型（照着做）。每一项都写了现状、做法、验收和容易踩的坑，
文件名和函数名都是 2026111603 这一版核实过的。标了「先核实」的地方，动手前必须先用 grep 确认。

## 0. 判断：现在该优化什么

界面骨架已经统一了：对话、电脑会话、终端是「浮在内容上的玻璃控件」，其余页面都是 COUI 大标题页，没有第三种。
性能也有余量：OPPO PMA110（120Hz）开模糊每帧 5.8ms、GPU 4.0ms，帧预算是 8.3ms。所以下一步不该再加视觉效果。

剩下的问题都在「闭环」上：

| 方面 | 问题 | 对应条目 |
| --- | --- | --- |
| 使用逻辑 | 别的对话、电脑会话完成时，提示条只停 6 秒，错过就没有任何痕迹（停在首页时连系统通知也不发） | 1.1 |
| 页面 | 任务胶囊里有电脑会话，点「查看全部」进任务页却没有 | 1.2 |
| 页面 | 终端只能从「侧栏 → 设置」里进，三步 | 1.5 |
| 功能 | 选择文字要拖很长的选择柄；一次只能操作一条消息 | 1.3、2.1 |
| 使用逻辑 | 「复核报错再试」靠关键词判断，普通聊天里说到「失败」也会出现 | 1.4 |
| 使用逻辑 | 侧栏搜到对话内容，点开却停在底部，要自己翻 | 2.2 |
| 工程 | 本机有 5 个固定失败的测试、一批已经走不到的旧代码，接手的模型容易被带偏 | 第零期 |

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

## 3. 第一期 ·「不丢」（一个版本）

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

## 4. 第二期 ·「顺手」（每项一个版本，可以只做 2.1）

### 2.1 消息多选：批量复制、分享、删除

- **现状**：长按一条消息出菜单，一次只能操作一条；整段对话只能整体导出。
- **做法**：
  1. 状态放在 `AgentChatBody`：`Set<String>?`，为空表示不在多选。用一个 `CompositionLocal`（仿照 `LocalRecheckAction`）传给 `ChatMessageItem`。
  2. 入口：用户消息和回答的长按菜单各加一项「多选」，点了就进入多选并选中这一条。回复进行中不能进入。
  3. 多选中：点用户消息或回答切换选中；思考、工具步骤不能单独选。**不改消息布局**：选中的消息铺一层强调色 10% 的圆角底，右上角一个 18dp 对勾。长按菜单和回答下面的按钮此时不响应。
  4. 底部：输入框的位置换成一条玻璃栏（`EtaFloatingSurface`，圆角与输入框相同）：「已选 N 条」、复制、分享、删除、取消。
     复制的格式由纯函数 `selectionPlainText(messages, ids, 用户称呼, 助手称呼)` 生成：按对话顺序，每条前面是「我：」或「胖小胖：」（角色对话用角色名），条与条之间空一行。
     分享走系统分享（`ACTION_SEND`、`text/plain`）。删除先用 `EtaCouiDialog` 确认，再逐条调用已有的 `onDeleteMessage`。电脑会话的历史不能改，不显示删除。
  5. 返回键退出多选（`NavigationBackHandler`，参考侧栏搜索栏 `PaneNativeSearchDock` 的写法）。
- **验收**：`selectionPlainText` 单测；用 `Stage3TestPanel` 测进入、切换、返回退出；真机模式截图 `home-multiselect-light/dark`。
- **注意**：底部玻璃栏要拿到和输入框同一个采样源（`LocalEtaCouiBackdrop`）；多选状态在切换对话时清掉。

### 2.2 搜索结果直达命中的那条消息

- **现状**：侧栏搜索能搜到对话内容（`AgentAppState.updateSearchQuery`），点结果只是打开对话，停在最底部。
- **做法**：从搜索结果打开对话时把搜索词带进去。`AgentChatBody` 收到后在已加载的消息里从新到旧找第一条包含这个词的，滚动到它，并让它的底色高亮 1.5 秒。
  没找到而且还有更早的消息时，最多再加载 3 页；还找不到就停在底部并提示「匹配的内容在更早的消息里」。
- **注意**：这一项风险最高。列表里的下标不等于消息的下标（「已完成 · N 步」把多条工具步骤合成了一项），要按条目的 key 找；
  定位时要先把「保持贴底」关掉（`onBottomAnchorChanged(false)`），否则会被拉回底部。做之前先读 `ChatHistoryPaging.kt`。
- **验收**：单测覆盖「在已加载的消息里找到」「要翻页才找到」「找不到」三种；手机上实际搜一个很早的词试。

## 5. 以后再说（没排期，按价值排序）

1. 输入框里打「/」调出常用任务和技能。
2. 新对话页最前面加「接着做」胶囊：最近一次对话、电脑上正在跑的会话。
3. 设置页搜索。
4. 「记住这次的解决办法」：复核成功后，一键把教训写进记忆。
5. 上游的全局助手唤出光效（`4101bc7`）：先录两边的对比再定。
6. 省额度：简单问题自动用便宜模型，需要你先定规则。

## 6. 需要你拍板（括号里是不回复时的默认做法）

1. 侧栏要不要加「终端」格子？（加）
2. 多选时选中的样式：浅色底加右上角对勾，不在行首加勾选圈。（这样做，因为不改消息布局、不会让正文重新排版）
3. 2.2「搜索直达」风险偏高，要不要做？（做，但放在最后单独一个版本，出问题不影响别的）

## 7. 进度

| 期 | 版本 | 结果 |
| --- | --- | --- |
| 第零期 地基 | | |
| 第一期 不丢 | | |
| 2.1 消息多选 | | |
| 2.2 搜索直达 | | |

## 8. 给执行模型的守则

这些是前面二十多个版本踩出来的，和 `AGENTS.md` 一起读：

1. **一期一个版本、一次提交**。提高 `app/build.gradle.kts` 的 `versionCode`（`yyyyMMdd` 加两位序号），在 `etax/README.md` 最上面写更新说明，
   同步 `README.md`、`README_EN.md` 的版本号，并填上面第 7 节的进度表。
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
