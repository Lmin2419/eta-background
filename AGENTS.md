# 开发约定

- `main` 即最新版本。新工作直接基于 `main`，不要再从 `etax-340-step1-foundation` 等旧集成分支开始。
- 底座是上游 v3.3.5，**按功能选择性合入**上游 3.4.x（三方合并：上游改动前后 + 我们的文件；冲突时保留我们的界面与后台引擎，只取上游逻辑）。不整体跟进上游的 COUI 主题与自带虚拟屏。只做必要修改，不重构上游代码。
- 例外：**UI 改版**已确定全 App 换装，按 `etax/design/`（先读 `README.md`，再按 `04-implementation-plan.md` 分阶段执行）改界面层，可以改上游的界面代码；业务逻辑、运行时、引擎仍按上一条只做必要修改。一个阶段一次提交、一次发版。
- **下一版（阶段 13–16）按 `etax/design/05-liquid-glass-v2.md` 执行**：先读它的第 0 节。玻璃只用于浮在内容上面的控件，内容层保持实色。
- **2026111407 起（用户要求）**：小窗菜单、底部半窗、对话框与按钮借鉴上游 3.4.x，直接使用 COUI 组件库（`io.github.itoseo:coui`，经 `ui/components/coui/EtaCoui.kt` 与 `EtaContextMenu` / `EtaSheet` / `EtaWindowDialog` / `EtaButton` 包装）；液态玻璃用 COUI 流体材质。设置类页面整页打开。2026111408 起设置及子页面、对话框、开关、下拉菜单也用 COUI（`MiuixScaffoldPage` / `MiuixScaffold` 内部是 COUI 骨架，`EtaSwitch`、`EtaDropdownPreference` 包装 COUI 组件）；侧栏是上游推开式加底部液态玻璃栏。本机测试（Robolectric）画不了 COUI 着色器，`EtaCouiSupport.shaders` 为 false 时退回旧骨架与旧小窗，只用于测试。
- 发版：提高 `app/build.gradle.kts` 的 `versionCode`（`yyyyMMdd` + 两位序号）；改动引擎时同步 `etax/engine/src/VDrive.java` 的 `BUILD`；在 `etax/README.md` 顶部「版本更新记录」和根目录 `README.md`「最新测试版本」写更新说明。推送到 `main` 后 CI 自动签名并发布 GitHub Release。
- 构建与测试流程见 `.github/RELEASING.md`。
