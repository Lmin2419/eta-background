# E04a · 首页、输入栏与模型选择器（0.9.13）

基于 `harmony/e03-navigation` / PR #17，叠加分支 `harmony/e04a-home-input`。来源统一为 `app/ @ 3b1794d`，Required Notice 保留；仅修改 harmony-agent/，不合并 PR。

## 逐项对照

| Eta 元素 | 鸿蒙实现 | 状态 |
| --- | --- | --- |
| AgentHomeScreen / EmptyChatState | 新首页，原问候语、两行四卡、20vp 外边距、12vp 间距、56vp 底部留白；键盘显示时隐藏卡片 | A 布局；四个设备/Web/终端建议 B，显示“鸿蒙版尚未接入” |
| AgentChatInputBar | 表面色、20vp 圆角、0.5vp 描边、40vp 点击区；输入 1–6 行，发送/停止与现有持久化运行时连接 | A；执行中追加发送留 E04c，暂禁用 |
| 附件菜单 | 按原顺序：图片、文件、文件夹、输入路径 | B，均禁用且带指定摘要 |
| ThinkingEffortChip / Atom | 保留原图标、位置和尺寸；源码实际为推理强度，任务卡的“手机操作”描述有误 | B，等待配置/协议连接；不误接成手机工具开关 |
| 模型按钮 / ProviderBranding | 脚本提取 18 个模型品牌匹配规则、12 个提供商映射；22 个原品牌资源；未知用 Dns | A |
| AgentModelPicker | 总模型数 ≤8 原生向上弹窗，>8 底部面板；搜索、服务商筛选、空结果、稳定列表 key、初始定位当前项上两项 | A；原生定位待真机 |
| 选择状态 | controller 唯一持有；140ms 高亮后确认；重复点击不改待选项，关闭/切页/后台作废回调，失败可重选 | A |
| AgentContextUsageButton | 环形进度、0.8/0.95 警戒色、原格式及 locale 数字，缺少窗口/用量的提示 | A；手动压缩尚无实现，不显示执行按钮 |
| 旧会话/服务迁入 | 已保存模型和旧选中模型合并；元信息名称/上下文保留；DTO 不携带 Key/地址/secretRef | A |
| 有消息首页 | 暂复用旧 ChatPage 消息区域，已移除旧输入栏和旧模型选择器叠层 | E04b 继续替换 |

## 自动验证与移植单测

完整移植 **12 项**：AgentModelPickerProjectorTest **9 项**、ProviderBrandingTest **3 项**。另 **4 项**适配回归测试覆盖延迟确认、取消、重开、旧服务投影。InputPopupPositionProviderTest **2 项平台坐标测试暂不移植**：用户批准优先采用原生菜单/弹窗，本版未执行 Compose 坐标算法；留检查点 ② 对照实际位置，不以纯逻辑测试冒充渲染测试。

四项检查全部通过：`npm run check`；`npm test` **464 项 / 0 失败**；`npm run check:ets` **0 error / 30 warning**（仅沿用已知 $$this 过滤）；`npm run check:res` **1,855 资源 / 0 warning**。**ArkTS 检查通过 / 未生成 HAP**，没有真机结果或运行截图。

## 与 Eta 的差异、待用户确认

1. 原生 Popup / Sheet 的系统定位、圆角、动画和字体按已确认决定先使用鸿蒙机制，检查点 ② 截图后判断是否自绘。弹层不自造 Compose 绝对坐标算法。
2. 旧版允许免 Key 的兼容服务，本版保留已保存服务的可选性；原 Eta 纯逻辑投影仍按 API Key/OAuth 筛选并通过原测试。平台适配将保存的服务视为已配置，不把 Key 读入 UI。OAuth 登录入口不开放。
3. Atom 实际是推理强度，不是手机操作开关：以固定源码为准保留原含义，本轮 B 类。四个建议均依赖未接工具，按 B 类处理，不发送看似可执行的任务。
4. 消息正文/思考/队列/重新生成分别由 E04b、E04c 完成；本 PR 不声称整个聊天页已完成。无语音配置时按原条件不显示麦克风。
5. 键盘与尺寸变化保留 controller 草稿；输入框只有会话身份/显式输入修订改变时才重置，普通流式刷新不重写输入。

## 鸿蒙接口与权限

新增公开接口使用：`window.on/off('keyboardHeightChange')`、`i18n.System.getAppPreferredLanguage`、`intl.NumberFormat`、TextArea.minLines/maxLines、Progress.Ring、List/LazyForEach/IDataSource、Scroller.scrollToIndex/scrollEdge、bindPopup/bindSheet/bindMenu。**新增权限无**；继承已批准 VIBRATE，签名为空。

## 真机检查点 ② · 本任务部分（待测）

1. 新建对话，与 Eta chat_home.jpg 并排截图；四卡原顺序、原问候语，B 类卡片不可点；折叠/展开、浅深色与最大字体各看一次。
2. 输入多行，键盘显示后卡片隐藏；持续输入时收到回复不清空输入；会话切换恢复各自草稿。
3. 配置 ≤8 个模型，点品牌按钮弹出短列表；配置 >8 个模型，显示可搜索面板。服务商筛选、模糊搜索、无结果、选中标记、当前项附近定位正确。
4. 点模型后立即关闭/返回/切后台，旧延迟不能改新页面选择；正常选择只保存一次。关闭再开搜索与服务商筛选复位。
5. 普通发送逐段收到内容，点停止保留已收内容；上下文环缺元数据有明确提示，有元数据的数字格式正确。
6. 原有免 Key 服务仍可选择；目录中当前模型被服务端移除后本地已选模型仍保留。返回、取消弹层不丢输入，也不出现旧模型面板覆盖新面板。
