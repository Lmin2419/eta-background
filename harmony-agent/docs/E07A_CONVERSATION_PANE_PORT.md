# E07a · 会话侧栏与导出（0.9.16）

基于 PR #20 / `harmony/e04c-chat-actions`；分支 `harmony/e07-conversations`。固定 Eta `3b1794d`；只改 harmony-agent/，保留 Required Notice。E07 分为页面/导出与存储对照两笔；**本 PR 不宣称 E07 全部完成**。

## 逐项对照

| Eta 元素 | 鸿蒙实现 | 状态 |
| --- | --- | --- |
| ConversationPanePanel / Metrics | 固定顶部搜索、中部滚动列表、底部六图标；原 16vp 外边距、48vp 行、12vp 圆角、6vp 活动点；所有尺寸脚本生成 | A |
| 分组标题 | 连续按 Pinned / Today / 日期分组，显示时钟和数量；运行会话按 Today；不合并非连续组 | A；当前没有置顶入口 |
| 行与长按菜单 | 标题/活动阶段/活动圆点；选中色，重命名、导出、删除原顺序；长按触感 | A |
| 侧栏颜色 | 浅色 surface，深色 surfaceContainer；搜索/选中行 surfaceContainerHighest | A |
| 底部 Dock | 设置、模型可打开；工具、Skills、权限、角色按源顺序保留禁用图标并提示尚未接入，等 E08 | A / B |
| 搜索 | 标题与全部历史正文，字面 `%_`，Java 风格 Unicode 大小写折叠；本地化系统提示 code 可匹配 | A，完整分块数据库验证留 E07b |
| 时间标签 | 接入 L1 ConversationTimeLabels；鸿蒙 i18n Calendar/Intl 提供系统时区、语言、12/24 小时制 | A；设备区域设置待测 |
| 重命名 | 原标题输入和取消/保存；空白禁用，保存失败保留输入与旧标题；生成中可改标题 | A |
| 删除 | 活动任务显示“停止并删除”；先停止并等结果结算再删除；排队消息不再被启动 | A；设备场景待测 |
| 空会话 | 新入口的空会话不在历史列表和持久化会话集；0.9 库接口保留默认策略兼容旧入口 | A；首次真正发送才持久化轮次 |
| 导出 Markdown | 接入 L1 Exporter，全历史导出而非仅 128 条可见窗口；系统文件保存选择器，取消不写；分段写完再关闭文件 | A |
| 会话分页 | E04b 页面窗口接入共同 messagePage；平台数据库加载仍使用现有 0.9 快照 | 页面 A；底层存储对照留 E07b |
| 归档 | 固定 Eta 源码无归档菜单，不新增入口 | 不适用 |

## 检查与单测

四项通过：`npm run check`、`npm test` **503 / 0 失败**、`npm run check:ets` **0 error / 58 warning**、`npm run check:res` **1,864 资源 / 0 warning**。**ArkTS 检查通过 / 未生成 HAP**。

新增 12 项：3 项集成/分组验证；9 项 `AgentConversationStoreTest` 逻辑边界的适配验证（保留名称并加 logic 前缀）。**完整原测试新增 0；原 Room 测试 17 项未完成，由 E07b 逐项对照，不把这 9 项算成完整 Android 数据库测试通过。** L1 时间/Markdown 导出已有原测试继续通过，不重复计数。

## 与 Eta 的差异、待用户确认

- 原生 Search、List、上下文菜单、确认弹窗及 CustomDialog；圆角代替 squircle，系统字体与手势待 E09 截图；不保证像素等价。
- 角色头像和角色第二行由 E08 接入；未制造角色数据。Dock 的未接入页面为 B 类。
- 为兼容 0.9，仍使用原轮次快照存储（50 会话、200 轮、单回复 32000 字、文档 2MB）。底层窗口读取、上下文 checkpoint 和角色绑定的 Eta 原测试还需 E07b 对照；本 PR 只交付页面与可用的文本历史操作。
- 导出固定打开文件选择器前的快照；选择窗口切后台时仍遵守 M0 停止运行规则，避免切换期间导出另一会话。取消保存不报成功。
- Native 列表端点手感待真机，不宣称与 Compose overscroll 完全一致。首个空会话仍有内存身份供草稿/模型选择使用，但不进入持久化历史。

## 接口和权限

新增公开接口：`i18n.getCalendar`、`i18n.System.getSystemLocale/is24HourClock`、`i18n.getTimeZone`、`intl.DateTimeFormat`；`picker.DocumentViewPicker.save`、`fileIo.open/write/close`、`util.TextEncoder`；`CustomDialogController` 和 MenuItem.contentFontColor。**新增权限无**，使用文件选择器返回的 URI；签名配置为空。

## E09 真机步骤 · 全部待测

1. 启动和连续新建空会话，侧栏不出现空历史；发送后出现该会话。核对顶部搜索/底部六图标的顺序。
2. 昨天、本周、更早会话按 Eta 时间分组；切系统 12/24 小时、语言和时区后核对日期；深色下侧栏与聊天区可区分。
3. 搜索历史正文、大小写、`%_` 和“已中断”；清搜索恢复全部列表。当前活动会话有状态圆点。
4. 长按 → 重命名，空白不能保存；取消无变化，保存后重开仍保留。运行中改名不改模型/正文。
5. 导出 70 轮以上会话，首尾都在 Markdown；取消保存无文件；选择期间切会话不改变导出对象。
6. 活动会话排队后选择删除，提示停止并删除；确认后不再续发队列，重开历史中无已删除会话；取消无变化。
7. 折叠/展开手机、最大字体，搜索和底部 Dock 固定，中部列表独立滚动；附与 Eta 的截图对照。
