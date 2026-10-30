# L2a：Markdown 文档与流式解析（0.9.5）

来源：Eta `3b1794d` 的 `ui/markdown/`。Required Notice：Copyright © 2026 蛮吉 (Mangi-11).，PolyForm Noncommercial 1.0.0。L2 拆成解析与流式状态两个 PR，以便逐项审查；本 PR 依赖 L3，未接入页面。

| Eta 元素 | 鸿蒙逻辑 | 状态 |
| --- | --- | --- |
| MarkdownModel | UTF-16 富文本范围、块偏移、revealKeys | A，纯逻辑 |
| MarkdownInline | 中文宽松强调、行内代码、kbd、换行、实体、引用链接、安全协议、图片替代链接 | A，原测试覆盖 |
| MarkdownDocumentBuilder | 标题、段落、围栏/缩进代码、列表/任务列表、引用/提示、表格、公式原文；稳定块身份 | A，TS 块解析适配 |
| StreamingGfmParser / Projection | 候选表格缓冲、未闭合链接缓冲、虚拟 EOF 补齐、终态引用链接、完成文档交接 | A，原测试覆盖 |
| TexMathDelimiters | 等长公式定界符替换；跳过代码、转义、空行；保留 UTF-16 偏移 | A，原测试覆盖 |
| 状态、缓存、恢复、SmoothTextReveal | 下一部分 L2b | 本 PR 未完成；不计为跳过 |

## 验证

- 35 项原测试：MarkdownInline 11、StreamingGfmParser 12、TexMathDelimiters 12；名称、输入与断言对应，0 跳过。逐项清单见 `eta-l2a-tests.json`。
- 另 6 项 TS 适配测试：缓存身份/主题切换、表格补齐、嵌套/任务列表、围栏尾部空行、安全链接、实体/换行。
- `npm run check`、`npm test`（339 通过、0 失败/跳过）、`npm run check:ets`（0 error）、`npm run check:res`（1,833 资源、0 警告）通过。
- **ArkTS 检查通过 / 未生成 HAP**。新增 TS 文件由 tsc/单测覆盖，尚未由生产入口引用；不得把现有入口的 ArkTS 检查理解为已验证未来 UI 适配。无新增鸿蒙接口、权限或签名。

## 与 Eta 的差异

- JVM 的 intellij-markdown 换为仓库内纯 TS 解析器，未引入第三方解析库。保留原 35 项测试及补充块级验证；这不等于通过整个 CommonMark/GFM 规范集，也不声称任何输入都与 JVM AST 相同。
- AnnotatedString / SpanStyle 改为纯数据的文本、样式类型、UTF-16 范围；ArkUI 渲染留到 E04b。链接和代码背景使用资源角色，在渲染阶段解析主题值。
- 当前构建先解析块再复用稳定对象，保持偏移与对象身份；尚未做 JVM AST 层级的性能对比。
- 不联网加载 Markdown 远程图片，沿用 Eta 的替代文字链接；只接受 http/https/mailto 链接。

## 待用户确认

选项：直接引入其他 GFM 库 / 按任务卡优先自己实现。默认使用纯 TS 实现，与夜间队列要求一致；未知语法按可读原文保留。之后若发现解析差异，以新增回归用例修复，不自行改变 Eta 展示规则。

## 真机检查（待测）

L2a 无独立真机检查点。E04b 接入后，发送含中文加粗、反引号、表格、引用链接、LaTeX 与代码空行的回复；逐字观察生成中和完成后格式稳定，复制内容无虚拟闭合符；深浅色切换后链接及代码背景同步。当前不得记录为真机通过。
