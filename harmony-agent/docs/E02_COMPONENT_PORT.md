# E02 · Eta 通用组件（0.9.9）

来源为 `app/src/main/kotlin/io/github/mangi/eta/ui/components/`，基准 `3b1794d`。Miuix 固定为 0.9.4 / `39c40f9`，补充源码逐字保存于 `scripts/eta-upstream/`，散列见 `sources.json`。保留 Required Notice 和 Apache-2.0 归属。

本 PR 提供组件和开发预览，**正式入口仍是 0.9 框架**；E03 才切换骨架。页面业务状态不在组件中复制，选中值由调用方传入，组件只发事件；按压、菜单展开和测量值是局部显示状态。

## 逐项对照

| Eta 元素 | 鸿蒙文件 / 行为 | 分级与验证 |
| --- | --- | --- |
| EtaCard | `EtaCard.ets`；24 vp 圆角、内边距、点击/长按、保持按下、Sink 反馈 | A，类型检查通过，画面待测 |
| EtaPreferenceTheme / Group / GroupTitle / Icon / Divider / Colors | `EtaPreferenceStyle.ets`；设置页主色覆盖、16 vp 分组边距、22/24 vp 图标、0.33 vp 分割线 | A，资源生成与检查通过 |
| EtaPreferenceRow / Preference / ArrowPreference | `EtaPreferenceRow.ets`；52 vp 最小高、12 vp 纵向内边距、可选头尾/底部/自定义标题槽；尾槽最多占剩余空间 45% | A，测量逻辑单测通过；真实布局待测 |
| Switch / Radio / CheckboxPreference | `EtaSelectionPreferences.ets`；整行触发、禁用、前/后置复选框、单一无障碍选择语义 | A；Miuix 勾号与圆形复选状态，状态来自调用方 |
| EtaPreferenceGroupItem | 同名文件，首尾圆角、中间分割线 | A，预览待测 |
| Dropdown / WindowSpinner / OverlayDropdown | `EtaDropdownPreference.ets`；上下箭头、已选值、选择回调后调用 item 回调，空列表/禁用时关闭菜单 | A；原生菜单适配差异见下 |
| FeatureCard / ChoiceChip / ToolChip / SectionHeader / ListEmptyState | `EtaSmallComponents.ets`；保留尺寸、摘要行数、选择与禁用、可选图标/动作 | A，预览待测 |
| StatusColors / label | `StatusColors.ets`，运行和权限状态逐项映射资源 | A |
| EtaTextButton / EtaSwitch | `EtaControls.ets`；22 vp 按钮圆角、44 vp 最小高、44×24 vp 开关 | A；开关使用规范允许的原生 Toggle |
| WindowDialog / OverlayDialog / DialogActions / ItemDescriptionDialog | `EtaDialogs.ets`；同一面板、取消左/确认右、等宽、危险操作色、说明滚动；通过 CustomDialogController 控制生命周期 | A；宽屏/窄屏对话框宿主见预览实现 |
| MiuixBackButton | 同名文件；40×40 vp、镜像返回 SVG、返回无障碍标签 | A |
| AdaptiveTopAppBar | 同名文件；窄屏大标题折叠、宽屏小标题；尺寸、淡出阈值、弹簧参数从来源生成 | A，滚动和宽窄切换需渲染验证 |
| MiuixScaffoldPage / PageBottomSpacer | 同名文件；600 vp 断点、800 vp 内容上限、24 vp 底部留白；安全区由宿主单次传入 | A，预览待测 |
| TopBarBackdrop 均匀模糊 / 关闭 | `TopBarBackdrop.ets`；25 vp backdropBlur、0.8 surface 叠色 / 实色 | A，采样效果待测 |
| TopBarBackdrop 渐进模糊 | 参数保留；当前走 Eta 的实色降级分支 | B；公开 ArkUI 渐变模糊作用于内容而非同一离屏背景采样，未冒充已实现 |
| EtaGallery | `pages/EtaGallery.ets`，未加入 main_pages；全组件状态、深色预览、150% 字体预览 | 开发入口；已纳入 check:ets，不是截图证据 |

## 单测与检查

- `EtaPreferenceRenderingTest` 原有 2 项依赖 Robolectric/ComposeView。完整原平台测试移植 **0 项，跳过平台部分 2 项**，原因：本环境没有 Compose 或 ArkUI 渲染器。
- 对应 2 个原用例名称保留于 `tests/eta/etaPreferenceRendering.logic.test.ts`，只验证共用测量逻辑；不是可见文字/像素边界的原生断言。
- 新增 3 项组件契约检查：UI 无硬编码色值与来源声明、预览不进入正式路由但纳入 ArkTS、Miuix 选择勾号的生成几何。
- 全量 **428 项测试通过，0 失败**；ArkTS **0 error，29 条原有 warning**，只沿用既有 `$$this` 双向绑定误报过滤（目前 2 条）。
- restool **1,843 项资源通过，0 warning**。颜色各 73 个、生成尺寸/字号/动效常量 106 项，另补 3 个 Miuix 组件 SVG；生成器支持重复校验。
- **ArkTS 检查通过 / 未生成 HAP**。未执行真实窗口渲染、手势、SVG/字体和 DevEco 完整编译，不写“真机通过”。

## 与 Eta 的差异、待用户确认

1. 连续圆角改为 ArkUI 圆角；正文使用鸿蒙系统字体。这两项按 ETA_PORT 允许的适配执行。
2. 可选项：现在使用鸿蒙原生 `bindMenu`，或后续连 Miuix 弹出层定位/动画一起自绘。默认前者，保留选择顺序、禁用/取消语义与上下箭头；菜单窗口/浮层两个 API 统一交给系统窗口管理，菜单尺寸、勾选动画不保证逐像素相同。没有支持 Miuix 级联子菜单 API，现有 Eta 设置选择项是平面列表。
3. 可选项：加入 VIBRATE 权限移植边界/选择触感，或保持权限集合。默认不新增权限，暂不触发选择和滚动边界触感。
4. 可选项：后续增加专用背景纹理渲染器实现渐进模糊，或暂按源码实色降级。默认实色；E05 的渐进选项须禁用并标记“鸿蒙版尚未接入”，不能显示为可用。
5. 卡片 Sink 用同一刚度/阻尼转换为 ArkUI 弹簧；单选与复选勾号采用原 SVG 静态状态，未移植 Canvas 路径裁剪动画。对话框开闭/返回、菜单和滚动回弹由 ArkUI 驱动，物理动画与 Android 不保证完全相同。
6. 列表页骨架接受内容槽，使用 Scroll；大数据页面需要 E07 自己的 List/LazyForEach，不把长列表塞进该槽。顶栏不接 Android nestedScroll，使用 ArkUI 滚动偏移；宽窄屏中途切换、过滚动、标题渐隐实际画面待测。
7. ArkUI 的公共属性占用 `enabled`、`onClick` 等名称，端口对应名为 `isEnabled`、`onAction`，行为不变。弹窗宿主负责 `cancel` / `onDidDisappear` 和大小屏对齐，组件不持有业务选择副本。

## 预览 / 真机检查步骤（全部待测）

本任务不设强制真机检查点，不阻塞夜间队列。可以在 DevEco 打开 `pages/EtaGallery.ets`，运行 Preview：

1. 普通预览与“Eta · 深色 · 大字体”预览切换；确认颜色、150% 字体、长摘要换行；没有导入生产路由。
2. 比较无图标与有图标行：标题起点分别为 16 / 56 vp；尾部开关、勾号、下拉文字不覆盖标题，右侧距容器 16 vp。
3. 点整行开关、单选与复选，各触发一次；禁用项不触发。打开下拉、选项、点击外部取消，再打开，选中值一致。
4. 点“外观与主题”卡片和长摘要行打开窗口/说明弹窗；取消在左、确认在右，外部点按/系统返回关闭；窄屏靠底，宽 ≥840 vp 且高 ≥480 vp 时居中。
5. 滚动大标题折叠；切换 600 vp 以上宽度时小标题固定，内容最大 800 vp；深色下分割线和禁用色可辨认。
6. 对照 `EtaPreferenceRenderingTest` 两项原生断言，记录文字可见性与实际边界。此步骤通过前，两项平台测试一直标为待测。

新增权限：**无**。新增公开鸿蒙接口：`CustomDialogController`、`bindMenu`、`Toggle`、`Menu/MenuItem`、`backdropBlur`、`Scroller`、`curves.springCurve/springMotion`、`accessibilityRole/Checked/Selected`；预览页使用 `ApplicationContext.setFontSizeScale`。未接 HDC、后台服务、模型工具、system_basic 权限或签名。

提交前补查：分组项目的 16 vp 间距计入父容器宽度，避免 `100% + margin` 横向溢出；长下拉标签优先收缩、箭头保持尺寸；指针移动不提前结束卡片/行按压态，抬起或取消才复位。四项检查重新通过，测试数量不变。
