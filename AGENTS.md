# 开发约定

- `main` 即最新版本。新工作直接基于 `main`，不要再从 `etax-340-step1-foundation` 等旧集成分支开始。
- 底座是上游 v3.3.5，**按功能选择性合入**上游 3.4.x（三方合并：上游改动前后 + 我们的文件；冲突时保留我们的界面与后台引擎，只取上游逻辑）。不整体跟进上游的 COUI 主题与自带虚拟屏。只做必要修改，不重构上游代码。
- 例外：**UI 改版**已确定全 App 换装，按 `etax/design/`（先读 `README.md`，再按 `04-implementation-plan.md` 分阶段执行）改界面层，可以改上游的界面代码；业务逻辑、运行时、引擎仍按上一条只做必要修改。一个阶段一次提交、一次发版。
- **下一版（阶段 13–16）按 `etax/design/05-liquid-glass-v2.md` 执行**：先读它的第 0 节。玻璃只用于浮在内容上面的控件，内容层保持实色。
- **2026111407 起（用户要求）**：界面组件借鉴上游，用 COUI 组件库。**14.4（2026111633–38）起是 COUI 2.1.0（`io.github.suqi8.coui.kmp`，上游同款）**：菜单、对话框、半窗、输入对话框在 `ui/components/coui/EtaCouiOverlays.kt`（弹层宿主 `EtaCouiOverlayRoot` 放在 `AgentAppTheme`，菜单在调用处不放布局节点）；设置类页面骨架在 `ui/components/coui/EtaCouiPage.kt`（`MiuixScaffoldPage` / `MiuixScaffold` 调用它）；开关是 `EtaCouiSwitch`。新库主题（`COUITheme`）会改全局按压效果、滚动回弹和选字颜色，只包住新组件，页面内容用 `ProvideEtaContentLocals` 换回胖小胖自己的。对话页顶部按钮、输入框、侧栏底部栏是液态玻璃（旧库 `io.github.itoseo:coui` 的流体材质，**依赖已去掉，用到的代码搬在 `thirdparty/coui`，MPL-2.0，见 `etax/third_party/coui/NOTICE.md`，改这部分要保留文件头**）；COUI 17 磨砂试用后于 2026111642 按用户要求去掉，不要再加回。采样源用 `rememberEtaBackdrop` / `Modifier.etaBackdrop`。`EtaSheet` 是居中小窗（`EtaCouiDialog`，底部「关闭」），不是贴底半窗——用户明确要小窗。弹层背后的整页记录按需开（`demandPageRecording`，没有弹层时不录）；新的整页模糊消费者要挂上它。本机测试（Robolectric）编译不了这些着色器：`EtaCouiSupport.shaders` 为 false 时关掉圆角遮罩、材质光效和模糊，组件照常画；涉及 COUI 的改动要按真机模式（`shaders = true`）加硬件渲染截图验证，参考 `HomeChatRenderTest`、`DeviceModeScreens`、`CouiOverlaysDeviceModeTest`（CI 产物 `device-mode-screens`）；新库圆角遮罩本机也编译不了，弹层截图用 `deviceMode = false`。出包前先看截图。按下标取 `measurables` 的自定义布局，每个子项各包一层容器。新增背景采样源（`rememberEtaBackdrop`、`rememberLayerBackdrop` 一类）要用 `trackRenderSource` 登记，设置里的「渲染诊断」才看得到。
- **后台执行提速**：`etax/design/11-background-speed-plan.md`（接手 10 号第四、五期：后台动作后自动回看、只核对目标元素、少走弯路、系统提示瘦身、文件工具提速；先读第 0、2 节和执行守则，每期一个版本）。
- **底层 Agent 执行的计划**：`etax/design/10-agent-execution-plan.md`（交卷前自检、工具报错讲人话、只读查询并行、界面操作少几轮、系统提示瘦身；先读第 0–2 节和执行守则，每期一个版本）。
- **界面优化计划**：`etax/design/09-optimization-plan.md`（2026111603 之后的优化方案：不要做的事、分期、每项的做法与验收；最后一节是执行守则，动手前先读；界面示意见文件开头的方案页面链接）。
- 发版：提高 `app/build.gradle.kts` 的 `versionCode`（`yyyyMMdd` + 两位序号）；改动引擎时同步 `etax/engine/src/VDrive.java` 的 `BUILD`；在 `etax/README.md` 顶部「版本更新记录」和根目录 `README.md`「最新测试版本」写更新说明。推送到 `main` 后 CI 自动签名并发布 GitHub Release。
- 构建与测试流程见 `.github/RELEASING.md`。
