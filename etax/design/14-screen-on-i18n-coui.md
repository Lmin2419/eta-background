# 14 · 常亮三档、界面文字三语、COUI 2.1.0 整体迁移

13 号方案收尾时留下三件和上游不一样的事（见 [2026111628 更新记录](../README.md#release-2026111628) 末尾）。这里一项项做，**慢慢做**：每版只做一两段，每段都要编译、单测、lint，加上浅色、深色和简体中文截图对照。

## 先回答：DSH 插件要不要更新

不用。手机端会发的全部消息类型都已经在插件 0.5.0 里处理了：

- 会话：`sessions.list / search / older / create / follow / unfollow / prompt / queue / cancel / rename / pin / unpin / archive / unarchive / subagents`
- 子代理：`subagents.prompt / interrupt`
- 交互与连接：`approval.answer`、`question.answer / reply`、`attachments.image`、`models.list`、`hello`、`ping`、`client.heartbeat`

合并上游没有改桌面协议，插件里也几乎没有给人看的文字，三语化也不涉及它。以后手机端加新的消息类型时再一起升级。

## 14.1 运行期间保持屏幕常亮：三档（2026111629）

上游 95b4060a 把常亮改成「任何任务（含后台任务）执行时都常亮」。但后台虚拟屏本来就能在锁屏时干活，定时任务也多半在你不看手机时跑，一律常亮只会白白耗电。

| 档位 | 什么时候常亮 |
| --- | --- |
| 关闭 | 从不因为任务常亮，按系统设置息屏 |
| **前台操作和对话时**（默认） | 在主屏上操作手机、普通对话，或你正看着胖小胖界面时常亮；后台虚拟屏和定时任务不常亮 |
| 所有任务 | 任何任务执行时都常亮（上游语义），更耗电 |

- 规则放在 `KeepScreenOnPolicy`（纯函数，单测覆盖），服务里只收集事实：有没有持有主屏操作权、是不是定时任务、有没有用过后台虚拟屏、胖小胖界面在不在前台。
- 「前台」档里，定时任务只有在它真的操作主屏（持有操作权）时才常亮；用后台屏的对话在你看着胖小胖时常亮，离开界面就恢复按系统息屏。
- 旧开关迁移：还没选过三档时，旧开关关 → 关闭，开或没设过 → 前台操作和对话时。写入新档位时旧开关同步成「会不会常亮」，备份和旧版本读到的意思不变。
- 设置 › 运行期间保持屏幕常亮：开关换成下拉三选一，说明文字跟着档位变。

## 14.2 界面层文字三语

先数了一下：界面层（`ui/`，不算预览）还有约 600 处写死的中文。按区域分几段做：

| 段 | 范围 | 约多少处 | 版本 |
| --- | --- | --- | --- |
| a | 定时任务（列表、详情、编辑、格式化）、AI 生图（设置、画廊） | 206 | 2026111629 |
| b | 角色（资料库、编辑、详情、世界书、导入提示等） | 135 | 2026111630 |
| c | 服务商（社区目录、账号、请求头、模型、登录、连接字段）、子代理设置 | 130 | 2026111630 |
| d | 对话统计浮层、电脑会话投影、渲染诊断、工具图标与步骤、其余零散页面 | 130 | 2026111631 |
| e | 工具步骤短标签 `AgentTraceFormatter`（含胖小胖独有的后台屏工具） | 约 50 | 2026111631 |

做法：

- 上游已经有三语资源的（定时任务、生图大部分都有），直接复用上游的 key，不重复造。
- 我们自己更口语的说法（比如「结果怎么给你」「能用哪些工具」）保留，单独加 key，三语都写。中文界面和改之前保持一致，英文、繁体跟着系统语言。
- Compose 页面用 `stringResource` / `pluralStringResource`；不在组合里的格式化函数用 `AppStrings`（带中文回退，纯 JVM 单测不受影响）。
- 每段加简体中文截图测试（`b+zh+Hans`），和英文、深色一起看。英文更长，重点看标签会不会换行压住内容。
- 收尾时界面层剩 91 处中文，都不是给人看的，刻意保留：工具名匹配关键字（如 `"网页搜索"` 用来认出旧记录里的工具）、语音音色 ID、渲染诊断的采样源名、正则、繁简两种写法的匹配词、单测用的默认值；渲染诊断复制出来的报告也保持中文，是给排查用的。

## 14.3 Agent 层用户看得到的文字三语

浮层、通知、任务胶囊、后台屏提示、电脑会话页的状态文字。**发给模型的提示词和工具说明不翻译**（和上游一样，避免影响模型行为和缓存）。

做下来（2026111632）：Agent 层还有约 2400 处中文，绝大多数是工具说明、提示词和返回给模型的结果，按上面的原则保留。真正给人看的约 60 处：电脑连接的各种错误和状态、对话里的压缩与重试提示、语音进度、浮窗助手建议发出的问题、后台屏通知的默认原因。程序内部用来比对状态的标记（「已停止」「已完成」等）不改。

## 14.4 COUI 2.1.0 整体迁移

现在旧库 `io.github.itoseo:coui` 管整个界面外壳，新库 `io.github.suqi8.coui.kmp` 2.1.0 只用在定时任务的时间滚轮。约 20 个文件直接用旧库，入口集中在 `ui/components/coui/EtaCoui.kt`。

| 段 | 内容 |
| --- | --- |
| a | 适配层收拢（页面只通过 `coui` 包用旧库）；开关换新库；页面边距改成自有常量 |
| b | 菜单（锚在按钮上的弹出菜单）、对话框 |
| c | 液态玻璃（顶栏、输入框）、搜索栏、页面骨架 |
| d | 去掉旧库依赖 |

每段前后都用同一组截图（设置、侧栏、对话、菜单、深色、大字体）对比，加上 CI 的真机模式截图；液态玻璃这段视觉风险最大，放最后，需要你在真机上看一眼再继续。

14.4a 记录（2026111633）：
- 新增 `ui/components/coui/EtaCouiAdapters.kt`：`EtaMenuAnchor` / `rememberEtaMenuAnchor` / `Modifier.etaMenuAnchor`、`EtaBackdrop` / `rememberEtaBackdrop` / `Modifier.etaBackdrop`、`etaFloatingGlassAvailable`、`EtaCouiSwitch`、`EtaCouiMenu`、`etaCouiPageColors`。深浅色改读 `EtaTheme.colors.isDark`（和旧库主题同源）。
- 还直接用旧库的：`AgentAppTheme`（主题入口）、`ConversationPanePanel`（侧栏搜索栏）、`MiuixScaffoldPage`（设置页骨架），以及 `coui` 包本身。
- 新库的坑：圆角（squircle）遮罩用 RuntimeShader，软件绘制会抛异常，没有着色器时用 `LocalSquircleEnabled` 关掉；新库开关本身只有 44×24，要自己补 48×48 触控区，否则行高变矮。
- 新库的 `COUITheme` 会改全局的按压效果、滚动回弹和选字颜色，所以现在只包住新组件；等页面骨架迁移时再整体换。
- 基线截图 143 张，对比脚本逐像素比较；只有时间文字、数据状态和动画时机造成的无关差异。

14.4b 准备（菜单、对话框）：
- 旧库菜单是独立窗口，背后整窗模糊（`ProvideCouiMenuBackdrop`）。新库 `OverlayListPopup` 画在组合树里，默认挂在 COUI 根 `Scaffold` 上（`renderInRootScaffold = true`），我们还没有用它的根骨架，迁移时要么先放一个根宿主，要么传 `false`。
- 按住滑动选中仍然有：新库 `ListPopup` 自带拖动选择和按压高光滑动；从按住的位置弹出用 `PreciseClickState` + `Modifier.preciseClickAnchor`，对应我们现在的 `EtaMenuAnchor`。
- 模糊改用新库的材质模糊（`materialBlur`），和现在整窗模糊的观感会有差别，这一段要真机对比。

14.4b 记录（2026111635）：
- 新增 `ui/components/coui/EtaCouiOverlays.kt`：`EtaCouiOverlayRoot`（弹层宿主 + 整窗记录 + 菜单层）、`EtaMenuAnchor`（`PreciseClickState` + 元素位置）、`EtaCouiMenu`（`OverlayListPopup` + `ListPopupColumn` + `DropdownImpl`）、`EtaCouiDialog`（`OverlayDialog` + `DialogButtonBar`）、`EtaCouiSheet`（`OverlayBottomSheet`）、`EtaCouiInputDialog`（`OverlayInputDialog`）。页面调用的名字不变，调用处一行没改。
- 宿主放在 `AgentAppTheme` 里（参数 `couiOverlayHost`，默认开；悬浮窗、任务预览关）。一开始放在 `EtaAppOverlayHost` 里，发现 `AgentAppRoot` 的重命名、删除对话框写在它外面，新库没有宿主时弹层会静默不显示，所以挪到最外层；没有宿主时记一条日志。
- 新库菜单会在调用处放一个占位节点拿父布局位置，这会重现 2026111407 的整页空白（侧栏按子节点顺序摆放），所以菜单改为调用处只登记、由宿主上的菜单层在锚点元素的位置画出来。
- 弹层内容在宿主里组合，看到的是宿主处的环境；对话框和半窗的内容用 `currentCompositionLocalContext` 换回调用处的环境，再把模糊源清空（嵌套弹层用实色）。
- 半窗去掉了底部「关闭」，改为拖动条 + 下拖 / 点外面 / 返回关闭（上游做法）；只有「关闭」的提示框按钮用灰色次要样式（和换库前一致）。
- 新库 Scaffold 用子组合摆放内容，页面第一次读系统栏边距发生在子组合里，那时才注册边距监听要等窗口再派发一次（本机测试里不会再派发，页面上移一个状态栏高度）。宿主外层先读一次 `WindowInsets.systemBars`，监听在第一次组合就注册好，真机第一帧也就有边距。
- 新库圆角遮罩的着色器本机渲染器编译不了（`layout(color)`），截图测试关掉着色器跑；测试面板的字体缩放提到最外层，弹层宿主也跟着放大（真机上字体缩放来自系统，本来就是这样）。
- 剩下直接用旧库的：`AgentAppTheme`（旧主题，供按钮、顶栏、液态玻璃）、`MiuixScaffoldPage`、侧栏搜索栏、`EtaCoui.kt` 的浮动玻璃和胶囊按钮——都在 14.4c。

14.4c-1 记录（2026111636）：
- 14.4c 拆成两段。侧栏搜索栏挂在底部液态玻璃栏上、采样源是旧库的玻璃背景，和液态玻璃一起换（14.4c-2）。
- 新增 `ui/components/coui/EtaCouiPage.kt`：`EtaCouiScaffold`、`EtaCouiLazyPage`，结构照上游 `CouiScaffold`：新库 `Scaffold` + `TopAppBar`（大标题收缩）/ `SmallTopAppBar`（宽屏、`compactTitle`）+ `COUIScrollBehavior` + `TopAppBarGlow`（内容记录在 `layerBackdrop` 里，顶栏透明）。
- 返回键用新库 `IconButton` + 原来的箭头图标（新库的图标在单独的 `coui-icons` 包里，没为一个箭头引入）。
- 新库主题包住骨架，内容用 `ProvideEtaContentLocals` 换回胖小胖的按压效果、滚动回弹、选字颜色。
- `MiuixScaffold`（自带内容的页面）：内容仍挂一个不折叠的 Miuix 滚动行为（2026111603 的滑不动问题），外面再包一层新库滚动行为的嵌套滚动，只用来让顶栏凝光跟着滚动量出现。
- 列表用新库的 `overScrollVertical` + `rememberCOUIFlingBehavior` + `scrollEndHaptic`。新库惯性更长，两处「拖动后立刻点」的测试要等列表停稳。
- 去掉了 `!EtaCouiSupport.shaders` 时的旧骨架分支（新库关掉圆角遮罩后单测里能画），删掉旧库的 `EtaCouiTopBarIconButton`、`EtaCouiBackButton`、`rememberEtaToolbarStyle`。

14.4c-2 记录（2026111637，对比版）：
- 新库没有液态玻璃（只有 COUI 17 的视图模糊材质和按钮的边缘光、触点聚光），上游最新版对话页改成固定顶栏 + 凝光模糊 + 普通图标按钮、输入框底栏磨砂。旧库是 MPL-2.0，可以把液态玻璃的渲染器（约 5000 行）搬进仓库保留。问了你，选的是先做一版两种都能切的，真机对比后再定。
- 外观设置加「控件材质」（`AppearanceControlMaterial`：`LIQUID_GLASS` 默认 / `COUI_FROST`），`LocalControlMaterial` 在 `AgentAppTheme` 里提供。
- `EtaBackdrop` 从旧库的类型别名改成胖小胖自己的包装（`legacy` / `coui` 只有一个非空），`rememberEtaBackdrop` 按设置记录一种，`Modifier.etaBackdrop` 挂对应的记录。页面代码只改了侧栏搜索栏一处。
- `EtaFloatingSurface` 看到 `coui` 采样源就走 `EtaFrostSurface`：`couiViewBlur`（圆钮、胶囊 `contentSecondaryButton`，圆角矩形输入框 `frameworkBottomBar`）+ 细亮边 + 浅阴影 + 按下缩小；输入框按上游不再铺 78% 底色。
- 磨砂模式下侧栏底部走「胶囊输入框 + 圆钮」那套（控件自动是磨砂），旧库原生搜索栏只在液态玻璃模式下用；定下磨砂的话再换新库的 `SearchBar` + `InputField`（上游同款，点开右边圆钮滑走）。
- 本机渲染器编译不了两种模糊的着色器（`cannot swizzle value of type 'shader'`），截图测试做不了，改成单测检查采样源切换。

## 进度

- [x] 14.1 常亮三档（2026111629）
- [x] 14.2a 定时任务、AI 生图页面三语（2026111629）
- [x] 14.2b 角色（2026111630）
- [x] 14.2c 服务商、子代理（2026111630）
- [x] 14.2d 其余界面（2026111631）
- [x] 14.2e 工具步骤短标签（2026111631）
- [x] 14.3 Agent 层用户可见文字（2026111632）
- [x] 14.4a 适配层收拢、开关换新库（2026111633）
- [x] 14.4b 菜单、对话框、半窗、输入框（2026111635）
- [x] 14.4c-1 设置类页面骨架（2026111636）
- [x] 14.4c-2 对比版：液态玻璃 / COUI 17 磨砂可切换（2026111637）
- [ ] 14.4c-3 按真机对比结果定下一种，去掉另一种
- [ ] 14.4d 去掉旧库
