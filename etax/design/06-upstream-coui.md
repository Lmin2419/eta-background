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

## 测试环境

Robolectric 画不了 COUI 的 Mesh 阴影和 AGSL 着色器。`EtaCouiSupport.shaders`（Robolectric 下为 false）时：菜单退回旧小窗、页面退回旧骨架、卡片不用平滑圆角、玻璃控件走磨砂。只用于本机测试，真机始终是 COUI。

## 待真机验收

- 设置页大标题收缩、返回圆钮、开关拖动与触感。
- 侧栏推开手势、底部玻璃栏折射列表、搜索输入与键盘。
- 顶部「标题＋模型」胶囊在长标题、大字体下的截断。
- Agent：让它写一段带转义的脚本，故意制造小错，看是否先修再报告。
