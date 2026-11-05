# 阶段 1 验证记录

## 版本与范围

- 基线：`main` 的 `a8037a2`（Claude 的设计文档提交），应用代码与 `7db9f5a` 相同。
- 阶段版本：`2026110503` / `3.3.5`。
- 引擎：`20261105-e49-parallel-observe`，源码与 BUILD 未改。
- 只完成 [实施计划阶段 1](../04-implementation-plan.md)：token、主题桥接、语义状态色、只读状态映射和可复用状态环。
- 现有页面的布局、字号应用、导航入口和执行逻辑保持原样。状态环在工作过程、任务中心及系统浮层里的全面替换随各自后续阶段进行。

## 设计落地

颜色和字号以 `01-design-language.md` 为准，画布里的外部字体不会被打包。沿用系统默认字体及仓库已有 JetBrains Mono。无新增依赖或 UI 框架。

- 默认浅色强调色 `#4B5BFF`，深色 `#8C97FF`；浅深色中性色和状态色均来自 Eta token。
- Monet / 自选强调色仅替换 accent 配对色和派生 running 色；onAccent 同步使用生成的 onPrimary，避免生成背景与固定前景不配对。
- 纯黑开关保留原有「Monet + 深色」适用条件，仅覆盖 canvas / surface，不覆盖 raised / sunken。
- 设置页不再通过嵌套主题覆盖背景和主色。
- 深色分类图标提亮用原色向白色混合 25%；规范未指定该提亮值。分类底色仍为浅色 12%、深色 20% 叠加在 surface。
- 阴影按文档允许的 Compose elevation 近似：e1/e2/e3 为 2/8/24dp，深色改为 1dp 描边。调用方决定表面填色。
- 状态环的循环只在运行、可见、处于 STARTED 生命周期且未减少动画时存在；旋转值与流光值只在绘制阶段读取。

## 状态映射取舍

| 已有状态 | 视觉状态 | 原因 |
| --- | --- | --- |
| RunStatusUi.Cancelled / Stopped / RunFailed | Failed | 设计明确把已停止归入失败视觉；具体停止文案仍可由调用方提供 |
| Queued / WaitingForGui | Queued | 等待并发名额或前台控制资源，避免表现成已在操作 |
| Paused / PreviewPaused / ScreenOffPaused / AppTakeoverPaused | Paused | 保留现有暂停事实 |
| Stopping / Finishing | Running | 仍在结束或提交过程中，尚未收到终态 |
| ToolActivityStatusUi.Unknown | Attention | 无法确定结果，不虚构成功、失败或持续运行 |
| completed=true 但无明确终态 | Attention | 不凭 completed 布尔值断言成功，也不继续转圈 |
| ContinuationUnavailable | Attention | 后续指令无法继续，不等同于原任务执行失败 |
| replyFailed=true | 不改变原结果 | 回复投递与任务结果独立；成功卡片仍为 Success |

明确的终态优先于旧 paused 标志；其后才处理 completed、paused 和具体过程状态。这些函数只供显示层消费，不回写运行时。

## 自动化与渲染

新增 13 项 Robolectric 测试：

- `EtaThemeTest`：规范配色、Monet/全部自选强调色的选择规则、纯黑范围、实时浅深色切换、Miuix/设置/状态色一致性、界面缩放、跟随系统夜间模式、运行中切换减少动画。
- `TaskVisualStatusTest`：四类输入状态映射、暂停与排队、停止/失败、完成与旧标志冲突、回复失败保留任务结果、未知结果保守回退。
- `StatusRingTest`：六态无障碍文案、浅深色实际绘制、四种标准尺寸、运行帧不触发重组、减少动画与隐藏组件不再请求动画帧。

定向验证命令（Windows 另外传 `-Pandroid.overridePathCheck=true`）：

```sh
./gradlew :app:testDebugUnitTest \
  --tests '*EtaThemeTest' --tests '*TaskVisualStatusTest' --tests '*StatusRingTest'
```

13 项全部通过。测试使用已有 Robolectric native graphics 生成 `app/build/reports/eta-theme/light.png` 和 `dark.png`，已人工查看；这是主题与组件样本，不是全 App 页面截图，不引入截图依赖。

Windows 全量首次回归：2,472 项，76 项失败，2 项跳过。与同机 `7db9f5a` 的 2,460 项 / 75 失败 / 2 跳过对照，75 项为既有失败；多出一项 `BackgroundTerminalJobsTest.failureTimeoutAndStopRemainDistinguishable`，在更早未修改基线 `87652ed` 也失败过，且本次在未修改 `7db9f5a` 和阶段 1 代码上单独重跑均通过。该测试涉及 Windows Shell 停止时序，与主题无关。补充系统夜间模式用例后的最终定向回归为新增 13 项加上述终端复核 1 项，共 14 项全通过。

本地接线检查、引擎重新编译内嵌、`:app:lintDebug` 与 `:app:assembleDebug` 通过。Debug APK 的包名为 `io.github.mangi.etax`、versionCode 为 `2026110503`。Linux 全量单测、Lint、签名及自动预发布结果以 [Etax Build](https://github.com/Lmin2419/eta-background/actions/workflows/etax-build.yml) 对应阶段提交为准；CI 全绿后再进入下一阶段。

## 真机待验

本机 ADB 未连接设备，未宣称完成 OPPO Find X9 Ultra / ColorOS 17 验收。需在原签名发布包上检查：

1. 首页、侧栏、设置及二级页的浅色、深色、跟随系统模式，确认布局和入口保持原样。
2. Monet 开关、系统/自选强调色，以及 Monet 深色下纯黑选项；切换后返回页面确认一致。
3. 字体缩放、界面缩放、系统减少动画，以及前后台切换后的状态环。
4. 系统级浮层的全套换装仍属于阶段 6，不以本阶段样本代替实机验收。
