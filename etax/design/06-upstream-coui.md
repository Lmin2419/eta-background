# 06 · 新版：界面骨架向上游 COUI 看齐（2026111408）

用户要求：弹出逻辑、按钮、页面骨架直接借鉴上游 3.4.x（COUI 组件库 `io.github.itoseo:coui` 0.0.1-alpha11），在其上保留液态玻璃；同时修 Agent 写代码时因小错放弃的问题。与 05 号方案冲突时以本文为准。

## 已定的选择

| 项 | 选择 |
| --- | --- |
| 侧栏 | 上游推开式（宽 340dp，遮罩 0.16），底部液态玻璃栏：搜索胶囊 + 权限 + 设置；「新建会话」在右上角 |
| 主按钮 | COUI 胶囊按钮，主按钮为珊瑚橘强调色 |
| 对话页顶部 | 对话有标题时，中间玻璃胶囊上行标题、下行模型名；新对话只显示模型名 |
| 设置 | 全部子页用 COUI 骨架（`MiuixScaffoldPage` / `MiuixScaffold` 内部实现），COUI 开关、COUI 下拉菜单，卡片与行按 `CouiPageDefaults` 尺寸 |
| 开关颜色 | 打开为正文色轨道（浅色黑、深色浅色），圆点用页面底色 |
| Agent | 只改提示词和工具反馈，不做自动复审 |
| 低版本 | Android 14 以下、关闭模糊或省电时，玻璃控件退回磨砂 |

## Agent 排错

- 命令输出超长：保留开头和结尾（结尾 48KB），中间写明省略了多少（`truncateKeepingTail`、`ByteArrayOutputCollector.readFrom(keepTailBytes)`）。
- `edit_file` 找不到原文：返回最相近的一段、行号和可能原因（只差空格或缩进、多一层转义、CRLF）（`editMismatchHint`）。
- 提示词 `AgentPromptBuilder.CODING_GUIDANCE`：用 `write_file` / `edit_file` 写文件；针对具体报错至少修三次；先查引号转义、空格缩进、换行符；说做不到之前先复核最后一条真实报错。编程子代理同样带上。界面操作的「无进展停止」规则不适用于代码和命令报错。

## 液态玻璃背景源（2026111409 起）

对话页只有一个 COUI 背景源（`LocalChatCouiSource`，外壳创建，挂在 `AgentChatBody` 的消息区上，源里先画不透明底色），顶部控件与输入框是它的兄弟节点、从它采样。不要在整页外面再套源，也不要让某个源的消费者位于另一个源的子树里再被采样——2026111408 这样做导致真机上消息区空白。侧栏同理：列表区一个源（带底色），底部玻璃栏是兄弟节点。圆角矩形的玻璃控件必须给 `EtaFloatingSurface` 传 `cornerRadius`，否则 COUI 按胶囊处理。

2026111410：真机上手机首页仍空白（电脑会话页正常）。首页顶部控件与侧栏底部玻栏暂改回磨砂玻璃，首页只有输入框用 COUI 液态玻璃，与电脑会话页一致；确切原因（COUI 流体材质放在外壳顶栏 / 屏幕外侧栏里）待定位后再恢复。

2026111411：10 仍整页空白（顶部按钮与输入框都没画出来），排除顶部控件与侧栏玻璃栏。只有首页与终端页经过外壳（`RoutedShell`），外壳在内容上挂 Miuix 整页采样（`captureForTopBar`）并在顶栏消费它；它套在 COUI 整窗源（`ProvideCouiMenuBackdrop`）里。有 COUI 整窗源时外壳不再做 Miuix 采样。

## 首页空白的真正原因（2026111412）

09–11 的「玻璃背景源」猜测都不对。真正原因：COUI 模式下 `EtaContextMenu` 用一个 0 尺寸 `Column` 收集菜单项，这个节点会进入调用处的父布局。`ConversationPanePanel` 在顶层调用了长按菜单，于是 `ConversationSidePaneScaffold` 的自定义 `Layout`（按 `measurables[0]` 侧栏、`measurables[1]` 对话页取）拿到的第 2 个子节点是这个空 Column，真正的对话页没有被测量和摆放。

- 修复：`EtaContextMenu` 用不发出节点的作用域收集菜单项；侧栏在 `Layout` 里包一层 `Box`，保证只有两个子节点。
- 规则：**按下标取 `measurables` 的自定义布局，子项必须各包一层容器**；组件不要在调用处父布局里发出看不见的节点。
- 本机复现方法：`HomeChatRenderTest`——`EtaCouiSupport.shaders = true`（真机模式）、`robolectric.pixelCopyRenderMode=hardware` 用 PixelCopy 截窗口。开模糊时本机编译不了 Miuix 模糊着色器，测试里关掉模糊。
- 10、11 的误判改动已撤回：顶部按钮、侧栏底栏恢复 COUI 液态玻璃，顶栏恢复背后模糊。

## 测试环境

Robolectric 画不了 COUI 的 Mesh 阴影和 AGSL 着色器。`EtaCouiSupport.shaders`（Robolectric 下为 false）时：菜单退回旧小窗、页面退回旧骨架、卡片不用平滑圆角、玻璃控件走磨砂。只用于本机测试，真机始终是 COUI。

## 待真机验收

- 设置页大标题收缩、返回圆钮、开关拖动与触感。
- 侧栏推开手势、底部玻璃栏折射列表、搜索输入与键盘。
- 顶部「标题＋模型」胶囊在长标题、大字体下的截断。
- Agent：让它写一段带转义的脚本，故意制造小错，看是否先修再报告。
