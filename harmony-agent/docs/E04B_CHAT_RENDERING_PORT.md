# E04b · 消息列表与 Markdown（0.9.14）

基于 PR #18 / `harmony/e04a-home-input`，分支 `harmony/e04b-chat-rendering`。来源 `app/ @ 3b1794d`；Required Notice 保留，仅修改 harmony-agent/。

## 逐项对照

| Eta 元素 | 鸿蒙实现 | 状态 |
| --- | --- | --- |
| AgentChatBody / toTimelineEntries | List + LazyForEach + 受观察的稳定消息对象；用户、工作过程、助手、系统提示分开，不再绘制 0.9 的助手卡片 | A |
| ChatHistoryPaging | 接入 L3 的分页需求、读位置锚点和底部状态；每页 128 条，回到底部可释放超过三页的历史窗口；新回复不挤掉正在阅读的窗口 | A，平台滚动待测 |
| BottomFollow | 使用 L3 决策/平滑步进与 ArkUI 帧时钟；用户上滑暂停跟随，底部浮动按钮恢复；不在每段回复后强拉到底 | A，帧率/手势待测 |
| UserMessageBubble | 320vp 上限，20/20/20/6vp 气泡、原边距与色；原生长按菜单、复制触感 | A；编辑/删除留 E04c，当前 B |
| AgentMessageBlock | 原 20vp 外边距、无助手卡片；正文流式渲染、完成且显现结束后显示操作区 | A；重新生成/删除留 E04c |
| ThinkingRow | 自动跟随思考生命周期，四行尾窗，手动打开全文后不再自动折叠；折叠态按消息身份保留 | A；顶部淡出绘制差异见下 |
| AgentWorkProcess / AgentWorkSteps | 接入 L3 分组、结论和摘要；工作过程头、22vp 轴、20vp 节点、32vp 行；同工具连续调用合组，可展开单次命令与结果 | A 显示；当前文本运行时只产生思考，工具执行未接入 |
| AgentToolActivity / ToolIcons | 原工具名称/图标由脚本提取；通用命令与结果展示、单行结论无多余展开、工具组次数与失败摘要 | 通用显示 A；浏览器实时预览/Subagent 专属卡片依赖后续能力，未提供入口 |
| AgentStatusCard | 原状态图标、标题、步骤、耗时、停止布局，EtaGallery 可预览成功态 | A 组件；未伪造活动任务填入首页 |
| AgentConversationLoading | 原加载/失败/重试结构；加载前不展示空聊天和输入框 | A |
| MarkdownContent / MarkdownStyle | L2 文档树渲染段落、标题、粗体/斜体/删除线、内联代码/链接、代码、引用、提示、嵌套列表/任务框、表格和分隔线；源码 offset 作 key | A，字体/布局待测 |
| 代码与表格 | JetBrains Mono；横向滚动、代码复制；表格按实际文本测量，72–260vp 自然列宽，剩余宽度按比例分配 | A，原生测量差异待测 |
| StreamingMarkdown / SmoothTextReveal | L2 最新目标队列、字素边界、显现协调器；ArkUI FrameCallback 驱动，完整文本保留排版、透明未显现文字；列表符号随首段显现 | A 平台适配，非像素级等价证明 |
| 解析线程 / 失败 | TaskPool 解析，取消后拒收迟到结果；任务拒绝时显示已收到原文，下一次目标可恢复解析 | A 适配；TaskPool 实机调用待测 |
| 正式入口 | AgentHomeScreen 已替换旧 ChatPage 消息区域；旧 core/markdown.ts 只为旧预览/归档调用保留，E09 清理 | A 接入 |

## 检查与测试

复用已移植的 L2（74 项完整原测试 + 5 项平台逻辑替代）和 L3（46 项完整原测试 + 3 项平台逻辑替代），**不重复计入新增移植数**。本 PR 新增 **9 项集成测试**，新增完整原测试 **0 项**、新增跳过 **0 项**。已有 8 项平台绘制/滚动测试仍待真机，不宣称已转为通过。

新增覆盖：中英文/emoji 字素显现且全量文本不变；重叠行内样式；表格测量分配；静态历史不重播；流式纠正/取消；思考与过程头的不同手动规则；3000 字词正文/代码/表格；停止投影保留内容；平台解析拒绝时保留正文并恢复。

四项检查通过：`npm run check`、`npm test` **473 项 / 0 失败**、`npm run check:ets` **0 error / 39 warning**、`npm run check:res` **1,861 资源 / 0 warning**。检查器补齐正常 Hvigor 会提供的 SDK `globalModulePaths`，修复 SinceWarningSuppressor 崩溃；没有新增错误忽略或修改 SDK。

**ArkTS 检查通过 / 未生成 HAP**。TaskPool、文本绘制、滚动和设备帧率尚未在设备执行；Node 长文本测试不代表设备性能测试。

## 与 Eta 的差异、待用户确认

1. 按已批准方案用 ArkUI List、系统菜单和原生绘制；连续圆角以 borderRadius 表达。逐字显现以完整 Text/Span 的颜色变化实现，按整字素出现，没有 Compose 的字素内部局部裁切；不截断字符串造成重排。思考尾窗有四行裁切与自动滚底，未复制 Compose DstIn 的顶部淡出、工作文字 shader shimmer。检查点 ② 对照截图/录屏后再决定自绘，当前不冒充视觉完全一致。
2. TaskPool 每次解析建立独立解析器；跨线程不传可变解析缓存，返回块继续以 offset 保持 UI 身份。L2 的队列、取消及显现逻辑不变。保存完整静态文档/会话内显现状态；历史窗口释放时回收会话。长回复设备性能待测。
3. 0.9 持久化仍是轮次，适配为稳定的 user/thinking/assistant/notice ID；未把旧设备步骤复活为工具。E04c 继续接入 L4 的运行投影与修订。任务状态提示不再把“停止”误标为运行失败。
4. Browser、Subagent 及终端等特定执行环境没有开启；只保留当前可承载的通用展示组件，不会注册未验证工具。语音、角色候选与附件入口继续受 E08/能力分级控制。
5. 列表窗口分页复用现有已载入的 0.9 数据；没有新建第二份历史数据库。ArkUI getItemRect/scrollToIndex 与 Compose 的测量时序不同，恢复位置安排在下一帧，检查点必须覆盖分页跳动和折叠屏。
6. 代码/表格可复制；Markdown 链接仅在用户点击时交给系统打开，不自动访问链接。失败的解析仅作纯文本显示，不改变保存的正文，不重发模型请求。

## 接口与权限

新增公开接口：`UIContext.postFrameCallback` / `FrameCallback`、`getMeasureUtils().measureTextSize`、`taskpool.execute` / `@Concurrent`、List.getItemRect / scrollToIndex / onWillScroll、LengthMetrics.vp、Span.textBackgroundStyle、剪贴板 setData、用户点击链接的 UIAbilityContext.startAbility。**新增权限无**；触感沿用已批准 VIBRATE；signingConfigs 为空。

## 真机检查点 ② · 本任务部分（全部待测）

1. 打开已有多轮会话：用户右侧气泡，助手正文不再套卡片；普通历史不重播显现。长按用户消息复制，复制后有触感；编辑等仍待 E04c。
2. 让模型回复约 3000 字，包含标题、粗斜体、嵌套列表、任务框、引用、提示、代码与表格。截图并录屏：稳定段落不闪，emoji 不出现半个代理字符，代码/表格横向滚动且复制完整。
3. 流式上滑，后续增量不强拉；点底部箭头回最新。改变输入框高度、开关键盘、折叠/展开后继续验证跟随与阅读位置。
4. 超过 128 条消息，向上加载或点更早记录，原来读的消息保持位置；回底后再次向上仍能加载，不重复不丢失。
5. 思考进行时只露四行尾部；点开全文；思考结束后自动模式收起，手动展开保持；过程头首次点击可直接收起。滚出/滚回保留手动选择。
6. 停止保留正文；后台停止与杀进程重开没有自动重发。模拟 TaskPool 失败时应显示原文，不能清空回复。
7. EtaGallery 查看状态卡；通用工具展示是显示组件，不作为设备操作验收。对照文字尾窗、工作过程动画和逐字显现，记录是否需要自绘。
