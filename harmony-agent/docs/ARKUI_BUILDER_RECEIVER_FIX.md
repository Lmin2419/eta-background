# 启动白屏：ArkUI Builder 接收者修复（2026-10-31）

## 用户反馈与原因

用户用 DevEco 安装 0.9.31 后，手机打开白屏。运行日志为 `TypeError: Cannot read property busy of undefined`，调用栈包含 `AgentAppShell.homeContent` 与 `ConversationSidePaneScaffold.initialRender`。安装已成功，这次错误不属于签名问题；重复安装无法修复代码。

`AgentAppShell` 将 `content: this.homeContent` 作为 `@BuilderParam` 传给侧栏。子组件执行 `this.content()` 时，裸 `@Builder` 的 `this` 变成子组件；子组件没有 `ShellState`，因此读取 `this.state.busy` 崩溃。原类型检查接受这种 ArkUI 写法，不能证明运行时接收者正确。**不是通过可选链吞掉错误或新建第二份 state 来处理。**

## 代码修复与对照

将自定义组件的 Builder 插槽显式保留在提供它的组件上：

```ts
// before
content: this.homeContent
// after
content: () => { this.homeContent(); }
```

同步检查转发的 BuilderParam、导航图标、设置分组、模型页、记忆/MCP 页及 Gallery 弹窗。最新分支共有 113 处插槽赋值，修正其中 99 处裸成员引用，涉及 24 个 ETS 文件。这个数是统一的接收者规则修正数，**不代表发现了 99 个独立崩溃**；全局无状态 Builder、已有箭头闭包不改。

| Eta 元素 | 鸿蒙实现 | 状态与差异 |
| --- | --- | --- |
| AgentAppShell / ConversationSidePaneScaffold 的内容与侧栏 | 闭包引用同一个 ShellState/controller，保持状态更新与按钮意图归属 | A；只修正 ArkUI 接收者语义 |
| TopBar、MiuixScaffoldPage、Preference 分组及嵌套插槽 | 每层显式转发到原组件，不把父页面方法移到装饰组件执行 | A；顺序、尺寸、颜色、文案不变 |
| 模型服务、设置、记忆、MCP、Gallery 弹窗 | 沿用同一 owner 的构建函数和事件回调 | 原 A/B/C 分级不变；未新增页面或业务能力 |

与 Eta 的新增视觉/交互差异：无。Android/Compose 没有此次 ArkUI 接收者问题；新增原 Eta 单测 **0**、跳过 **0**；本次新增 **10 项 SDK/JavaScript 接收者回归**，不冒充移植测试或真机测试。

## 防回归

`scripts/arkui-builder-check.cjs` 使用实际 SDK 的 ArkTS AST，读取 `@BuilderParam` 声明、组件调用和命名导入，检查自定义插槽的裸 `this.method`。普通数据属性、普通回调、框架自身的 bindMenu/bindSheet/navDestination 不套用该规则；解析失败报错。规则是本项目要求显式保留 owner，不是声称 SDK 禁止裸 Builder。

`check:ets` 同时运行 10 项回归：原 busy 错误、闭包/后续状态替换、逐层转发、导入别名/弹窗初始化、普通属性不误报、全局 Builder/语法错误，以及从真实源文件提取的首页/侧栏、顶栏返回、分组转发、Gallery 弹窗表达式。修复前 3 项真实表达式用例失败，修复后全部通过。E02 尚未引入生产壳层时，仅那 1 项明确标为跳过；E03 起运行全部 10 项。

这些测试执行 JavaScript 的接收者与回调语义，没有运行 ArkUI 原生渲染器、模拟器或手机；完整启动结果仍须真机验证。未增加错误忽略规则。

## 自动检查

- `npm run check`：通过。
- `npm test`：919 项通过，0 失败（最新分支）。
- `npm run check:ets`：0 error、99 warning；113 处插槽检查无错误，10 项接收者回归通过；既有 7 项父容器回归通过。
- `npm run check:res`：1,946 项资源通过。
- **ArkTS 检查通过 / 未生成 HAP**。新增鸿蒙接口、权限：无；签名配置不改。用户已反馈上一包能安装，但本修复的启动/页面交互仍为待测。

## 分支与交付

最早的同类写法在 E02 组件库 / PR #16；先修复它，再按现有 base 图合入 E03 / #17（生产首页）及后续各分支，并修正各任务新增的同类插槽。原草稿 #22、#35 保持草稿；#35 独立的构造器 ArkTS 错误不在本修复范围。最终用户交付版本为 **0.9.32 / PR #36**；历史任务分支保留自己的版本号。

不合并任何 PR，不推送 main，不修改 Android 源码/workflow，不改变会话存储，不删除用户数据。

## 真机验收（全部待测）

1. 新源码沿用本机同一个签名配置，DevEco 同步、清理、Run 覆盖安装，无须重复卸载。根目录 `build-profile.json5` 中的个人签名不要上传仓库。
2. 强制关闭后启动 3 次，确认正常进入首页，FaultLog 不再出现此 `busy of undefined` 调用栈。
3. 打开/关闭侧栏，点击新会话和顶栏菜单，检查会话切换、按钮状态与主页面一致。
4. 进入设置、外观、模型服务列表/详情/新建/目录，分别返回；打开并取消菜单/弹窗，确认没有白屏、错误标题或失效的返回按钮。
5. 打开记忆、MCP 列表/详情并返回；存在有效模型时发送一轮、停止、切会话，确认流式状态更新仍有效。
6. 使用 EtaGallery 预览分组与对话框：确认/取消应作用于该对话框，主题/选择状态更新不丢失；宽屏/窄屏各检查一次。

Required Notice: Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0。
