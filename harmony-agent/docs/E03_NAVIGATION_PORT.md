# E03 · 应用壳层与导航（0.9.12）

基于 PR #16 `harmony/e02-components`，PR #17 从原 0.9.10 草稿继续。用户于 2026-10-31 确认所有宽度用 Eta 推移侧栏，旧冲突已解除。此提交以原 #17 与追加后的 #16 为双父提交合入，未合并任何 PR、未推送 main。所有改动限于 harmony-agent/。

## 逐项对照

| Eta 元素 | 鸿蒙实现 | 状态 |
| --- | --- | --- |
| AppRoute / NewProviderType | `core/eta/routes.ts`，完整 33 个原路由身份与 Kotlin JSON 字段，B/C gate | A 逻辑；C 不提供入口 |
| AgentNavigator | `agentNavigator.ts`，全栈去重 push、replace 回收后续、popToHome | A |
| AgentAppRoot | `etaNavigation.ts` + `etaShellNavigation.ts`，唯一核心栈；旧 Screen 意图映射进该栈，ShellNavigation 仅兼容导出 | A，已接控制器 |
| Navigation / NavDestination | `ui/eta/navigation/AgentAppShell.ets`，平台栈仅投影核心，返回意图发给控制器 | A，系统侧滑待测 |
| ConversationSidePaneScaffold | 同名组件，所有宽度 min(0.84×屏宽,340vp)；侧栏与全宽聊天同步推移、遮罩、拖动、焦点/无障碍隔离 | A，布局待测 |
| ConversationPaneMotion | 使用 L3 已移植纯逻辑，尺寸变化重新定位当前目标锚点 | A |
| AgentTopBar / TopBarOverflowMenu | 历史入口、空聊天标题、更多菜单；新建、Browser 灰色“鸿蒙版尚未接入” | A / Browser B；Terminal 和依赖 Linux 的 Kimi Web C 隐藏 |
| titleForRoute | 生成器直接提取原源码的 33 项标题映射；8 个源码内字面量也生成资源 | A；源码中文常量在其他语言继续回退原文 |
| StartupSplash | `StartupContentHost` 等外观准备完成再 `WindowStage.loadContent`，销毁后拒绝载入；不额外设计启动 UI | A 平台适配，首帧待测 |
| 通知会话定位 | FoundationHost 先验证 TaskLedger token，再调用 controller → EtaNavigation；不存在会话/部分 token/无效 token 不跳转，不启动运行 | A；不接后台通知服务 |
| Index / 旧页面 | Index 已切 AgentAppShell；旧聊天、模型、侧栏内容、设置、诊断暂时复用 | E03 范围内完成；E04/E07/E05/E06 继续逐页替换 |
| 角色身份、播报按钮 | 尚无配置过的角色/语音，不伪造可用入口 | 后续 E08，按条件不显示 |

## 单测与检查

- 完整移植 Eta 原测试 **2 项**：AppRouteSerializationTest 的 legacyBrowserRouteRestoresTheManualPage、allRoutesRoundTripWithMiuixNavJsonConfiguration。
- 原平台部分跳过 **3 项**：StartupSplashTest 的准备前阻止与销毁移除（只迁移门控逻辑），AgentNavigationRestorationTest 的 Activity 重建（只测试编解码重建；无 Android Activity / 鸿蒙进程模拟）。保留原名，不计为完整平台测试。
- 本次另加 **7 项**集成边界测试：旧意图共用 Eta 栈、删除服务返回列表、恢复过滤、首帧一次性、销毁后不载入、两项通知定位；原草稿已有 6 项导航边界测试。
- `npm run check`、`npm test`（**448 项，0 失败**）、`npm run check:ets`（**0 error，28 warning**）、`npm run check:res`（**1,852 项资源，0 warning**）全部通过。检查中修复类型重导出、ArkUI 公共方法重名、生成常量名与系统栏颜色参数类型；未增加误报过滤。
- **ArkTS 检查通过 / 未生成 HAP**。未做真机渲染或手势验证。

## 与 Eta 的差异、待用户确认

1. 已按用户决定保留所有宽度推移式侧栏，任务卡已纠正；原生菜单/弹窗/动画决定记录在 ETA_PORT 末尾。弹簧沿用 Eta 刚度 146、阻尼比 1，转换为 ArkUI springCurve；ArkUI PanGesture 释放按 0.5 位置锚定，未复用 Compose 的 fling 预测器，快速短划的最终锚点可能不同，检查点 ① 对照。
2. 旧页面暂复用，旧内容自身的颜色、尺寸和交互将由后续任务替换。本 PR 不宣称已完成整个 Eta UI；设置旧内容本身没有顶栏，因此临时由新壳层提供一次顶栏，E05 由设置页骨架接管。旧页面还没向新顶栏报告滚动折叠量，二级页大标题暂不随旧页面滚动折叠。
3. 鸿蒙首帧通过延后 loadContent，而非 Android OnPreDrawListener；E05 前保持既有浅色启动配置。系统启动窗的真实切换时序仍待 DevEco/真机确认。
4. 鸿蒙诊断是独立平台 destination，不编造 Eta 第 34 个路由。进程保存仅写路由身份和 ID，不写 Key；恢复时保留首页/设置/模型列表，未移植页面和编辑详情退回最近可恢复页，避免凭据编辑器以空快照启动。完整编辑器恢复留 E06 对接。
5. 0.9 的通知入口曾无条件回首页，本次改为有效 token 且会话加载成功才回首页。旧通知只导航，不恢复旧动作。
6. 暂不接 HDC、后台任务、手机动作、角色或语音。Browser B 灰色且带指定摘要，C 不留占位页。没有提出新的权限决定。

## 新用到的鸿蒙接口与权限

公开接口：Navigation / NavDestination、NavPathStack.getPathStack/setPathStack、NavPathInfo.onPop、UIAbility.onSaveState、ResourceManager.getColorSync、UIContext.getFocusController().clearFocus、inputMethod.getController().hideTextInput、PanGesture、curves.springCurve。延用系统管理安全区与 KeyboardAvoidMode.RESIZE，不叠加第二份安全区留白。

E03 **新增权限无**；从 #16 合入用户批准的 **ohos.permission.VIBRATE**（normal/system_grant、不弹窗），侧栏拖动结束/点遮罩关闭调用 GestureEnd。签名为空。

## 真机检查点 ①（全部待测）

1. 冷启动，等待对话载入，首页没有旧三栏导航；左上历史、右上更多。点历史：窄屏与展开屏都推移，全宽聊天没有被压窄，侧栏不超过 340vp。
2. 慢拖、快速短划、拖动后取消、点遮罩关闭；进入/关闭有源调用点触感。对照 Eta 截图记录锚点与动效差异。侧栏隐藏时 TalkBack 不读其中的按钮，展开时聊天内容不可点击。
3. 侧栏展开 → 设置 → 模型服务 → 详情 → 系统返回，依次回列表/设置/首页；首页仍展开侧栏，再返回关侧栏，再返回才退出。模型目录弹层优先关闭。
4. 横竖屏/折叠展开和输入法显隐，确认当前会话、草稿、搜索不丢，拖动不留下半开位置，页面仅使用一次安全区。
5. 新建、选择会话之后侧栏关闭；模型删除后回列表，不能留在被删除详情。
6. 有效历史任务 token 只定位原会话；不存在会话/错误 token/部分参数不能跳转，无网络请求或手机动作。
7. 冷启动无错误主题中间帧，准备期间关闭窗口不会继续载入；重建后可恢复设置/模型列表，编辑详情按上述限制回上一级。

完成本任务后按已批准队列继续 E04a；检查点仅记录待测，不等待回复。
