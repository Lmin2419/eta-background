# E03 草稿：路由逻辑已提取，布局规范冲突待决定

## 阻塞原因

任务卡与固定来源对宽屏侧栏的要求相反：

- `docs/ROADMAP.md` E03 明确要求：**“窄屏是抽屉，宽屏常驻，动效照抄。”**
- 固定基准 `3b1794d` 的 `app/src/main/kotlin/io/github/mangi/eta/ui/components/ConversationSidePaneScaffold.kt` 没有宽屏分支。所有宽度都采用 `minOf(maxWidth * 0.84f, 340.dp)`，侧栏和全宽聊天舞台并排测量、统一推移，关闭时侧栏移出屏幕。
- `AgentAppRoot.kt` → `AgentAppShell.kt` 的调用链也没有在宽屏替换为常驻侧栏；`AgentAppShell` 在 Home 路由一致调用该 scaffold。
- `ETA_PORT.md` §1、§9 要求结构、尺寸和交互按该源码 1:1 迁移。直接做“宽屏常驻”会成为主动改变 Eta 布局；直接做“全部宽度推移”又不满足 E03 任务卡。

这是主聊天布局与返回/拖动策略的选择，会影响 E04a 输入区、E04b 消息列表宽度和 E07 侧栏行为。不是一个字号或平台 API 替换。按 ROADMAP 第二节无人值守规则第 3 条：**“遇到必须由你决定、而且会影响后续的问题时，PR 保持草稿”**。因此本 PR 不切换 Index，不伪装 E03 已完成。

待用户选择：

1. **严格跟随固定 Eta 源码**：窄屏和宽屏都采用 0.84 / 340 vp 推移侧栏，修正任务卡“宽屏常驻”文字。建议此项，符合“与 Eta 一样”的总目标。
2. **保留任务卡的宽屏特例**：宽屏常驻侧栏，明确列为经用户接受的 Eta 差异；需决定宽屏断点及聊天舞台是否扣除侧栏宽度。

本夜默认处理：保持现有正式 UI，保存下述独立逻辑和测试，**不替用户决定上述二选一**。未等待用户回复；当前队列的依赖任务按规则记为未开始。

## 已完成的独立部分（0.9.10 草稿）

| Eta 元素 | 鸿蒙实现 | 状态 |
| --- | --- | --- |
| `AppRoute` / `NewProviderType` | `core/eta/routes.ts`；33 个 route identity、带参数路由、旧 Browser 默认值、与 Kotlin wire 字段同名的 JSON 编解码 | A 逻辑完成；未接 UI |
| C 类路由 | 身份可解码以兼容来源记录，但 `routeHidden` 标记隐藏，导航入口拒绝；不生成占位页 | C |
| Browser / Workspace / 语音路由 | 标为 B，当前导航意图拒绝 | B；没有假可用入口 |
| `AgentNavigator` | `core/eta/agentNavigator.ts`；push 全栈去重、replace 回收后续栈、pop、popToHome | A 逻辑完成 |
| `AgentAppRoot` 路由/侧栏意图 | `core/eta/etaNavigation.ts`；保留子页返回前的侧栏意图、首页返回先关侧栏；验证过的通知会话成功打开才回 Home | 逻辑草稿；未替换 ShellNavigation |
| `StartupSplash` | `core/eta/startupSplash.ts`；必要配置未就绪时阻止、就绪后移除、销毁时移除的单次门控 | 只移植纯逻辑；鸿蒙首帧适配未接 |
| `Navigation` / `NavDestination` | 未实施 | 依赖布局决策 |
| 顶栏 / 溢出菜单 / 侧栏 / Index 切换 | 未实施 | 依赖布局决策；现有 UI 保留 |

通知方法仅接受平台已经验证的导航请求；没有模型调用或动作执行。它尚未接入 `FoundationHost.route`，原有任务 token 校验和旧通知不恢复执行的规则没有改变。JSON 路由参数不携带 Key。

## 单测与自动检查

完整移植 Eta 原测试 **2 项**：

- `AppRouteSerializationTest.legacyBrowserRouteRestoresTheManualPage`
- `AppRouteSerializationTest.allRoutesRoundTripWithMiuixNavJsonConfiguration`

仅移植逻辑、跳过原平台验证 **3 项**：

- `StartupSplashTest.仅在首帧必要配置未就绪时阻止绘制`：保留门控调用次数与一次性放行断言；未执行 ViewTreeObserver / 鸿蒙启动窗口。
- `StartupSplashTest.准备期间销毁页面会移除绘制拦截`：保留移除后不再调用 readiness 断言；未执行 Activity 生命周期。
- `AgentNavigationRestorationTest.Activity 重建后保留完整页面栈`：验证序列化后重建逻辑对象；没有模拟 Android Activity / Bundle 或鸿蒙进程恢复。

另新增 **6 项**边界回归：push/pop/replace、返回顺序、C/B 路由阻断、恢复与无效输入原子性、通知跳转、异步结果迟到。全量 **439 项测试通过，0 失败**。

`npm run check`、`npm test`、`npm run check:ets`、`npm run check:res` 均通过。新核心文件显式加入 SDK 检查入口，避免因为暂未被 Index 导入而漏检；ArkTS 0 error，29 条既有 warning，1,843 项资源通过、0 warning。**ArkTS 检查通过 / 未生成 HAP**。这不代表页面、启动窗口或系统侧滑返回已实现。

## 与 Eta 的差异

- 暂无新界面；生产导航仍是 Foundation 旧骨架。33 个 route 只是纯逻辑身份，不能算 33 个页面完成。
- 可恢复的 C/B 路由会在鸿蒙导航边界被过滤；Kotlin 编解码保留这些身份本身。
- 新增迟到导航结果代际保护，无自动重放；原通知 token 校验仍归平台层。
- 2 项原始完整单测 + 3 项仅逻辑移植；平台验证均待测，不能作为“真机通过”。

新增权限：无。新增鸿蒙接口：无。本草稿没有调用新平台 API；未改签名、Android 源码或 workflow。

## 真机检查点 ①（未实施、待测）

在本冲突解决并完成 E03 UI 之后执行，当前草稿不能验收：

1. 首页点左上历史，拖动开/关侧栏，检查推移/常驻策略与最终选定方案一致。
2. 侧栏打开后进入设置，再返回：先返回首页、再关闭侧栏，最后一次返回才退出应用。
3. 竖屏、横屏、折叠/展开切换，不丢选择、草稿、搜索和列表位置；聊天舞台宽度符合选定方案。
4. 系统侧滑逐级返回，子页面退场不提前消费首页侧栏返回。
5. 已验证 token 的通知只定位原会话；无效/过期 token、不存在会话不跳到无关会话，不启动旧动作。
6. 冷启动与外观配置加载不会先显示空白或错误主题；退出启动流程后不留下绘制拦截。
