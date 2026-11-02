# Eta 后台版与上游 Eta 3.4.0 分阶段整合计划

> 状态：开发中，未发布。本文记录整合目标和进度，不把后续计划写成已经完成的功能。
> 第 1–2 轮已有实现；用户 2026-11-01 明确授权继续第 3–4 轮及息屏实现，候选代码已接线，真机验收独立保留，3.4.0 整体升级尚未完成。

本次候选具体行为与平台限制见 [TASK_PREVIEW.md](TASK_PREVIEW.md)，下方基线及前两轮记录保留其当时含义。

e43 后续：按 6883e75 的用户反馈修复预览暂停与 GUI 暂停来源不同造成的 AI 恢复遗漏；明确只读/暂停提示，提高预览至 640/960、质量 78，并改用耐单色染色的透明手机位图。引擎 `20261102-e43-resume`，仍使用通知打开实时小窗；保留流体云，不宣称通知中已嵌入实时画布。

2026-11-02：按首个预览候选 `3c74066` 的用户反馈返修连续接管、完成页面控制、缩放与首帧占位，并加入上游风格的本地触点/滑动跟随动画。引擎更新为 `20261102-e42-manual`；不把旧候选自动化或用户反馈移记为本次返修真机通过。

## 基线

| 项目 | 基线 |
| --- | --- |
| 本仓库 `main` | `37c866725346b186b69230f92bb67cd2a934bbc9` |
| 当前安装包对应源码 | `4637961bc55fe546da17fda33f2dc274fdc65e20` |
| 当前安装包版本 | `versionCode 2026103003` / `versionName 3.3.5` / `io.github.mangi.etax` |
| 当前引擎 | `20261030-e40-takeover` |
| 上游仓库 | <https://github.com/Mangi-11/Eta> |
| 上游参考版本 | `v3.4.0` / `e90922a4490389f524a10c6acebbcca336883935` |
| 本轮分支 | `etax-340-step1-foundation` |

开始本轮时，远端 `main` 与给定基线 `37c866725346b186b69230f92bb67cd2a934bbc9` 一致，没有发现需要额外保留的新提交。若之后远端 `main` 继续前进，以新提交为准另行核对，不对远端做强制重置。

## 五轮目标与状态

| 轮次 | 目标 | 状态 |
| --- | --- | --- |
| 1 | 建立集成分支与基线，合入独立的 Android/Linux 双环境说明及共享目录映射提示 | 已完成，待 PR 验收 |
| 2 | 统一前后台原生操作入口：按任务当前执行位置路由到现有前台 GUI 控制器或现有 `BackgroundScreenTools`，保持旧 `screen_*` 兼容 | 已实施，待完整验收 |
| 3 | 统一任务状态：会话、通知和后续预览读取同一份运行状态，不各自推断完成 | 已实施，待候选真机验收 |
| 4 | 保留流体云、每任务通知和通知内继续指令，完善三个完成入口、按需画面预览、确认暂停后的手动接管、恢复前重新观察、“等待你”和“部分完成”状态 | 预览及接管已实施；等待/部分完成语义未扩展 |
| 5 | 完成系统 Hook、应用交还和通知兼容收尾；息屏执行另行实验；执行完整 3.4.0 回归与发布验收 | 息屏显示与路由策略已适配；Hook 未改、真机与发布未验收 |

第 1 轮改变执行上下文说明、系统提示、工具说明、coder 角色说明和对应测试。第 2 轮只增加每个任务独立的原生操作路由和现有后台屏幕适配，不增加第二个虚拟显示引擎，也不把默认的 `screen_*` 兼容入口删除。

## 第 1 轮实际合入的上游提交

| 上游提交 | 主题 | 本轮处理 |
| --- | --- | --- |
| `2bc0f2103ac2ea8a5106626d1e41f54111fe7f96` | Android/Linux 双环境分工及共享目录映射提示 | 按语义合入，保留后台版提示与权限边界；补充“配置映射不等于已验证挂载成功”的说明 |

该提交涉及：

- `app/src/main/kotlin/io/github/mangi/eta/agent/terminal/TerminalExecutionContext.kt`
- `app/src/main/kotlin/io/github/mangi/eta/agent/tool/AgentLocalTools.kt`
- `app/src/main/kotlin/io/github/mangi/eta/agent/model/AgentPromptBuilder.kt`
- `app/src/main/kotlin/io/github/mangi/eta/agent/model/AgentTerminalToolCatalog.kt`
- `app/src/main/kotlin/io/github/mangi/eta/agent/model/AgentFileToolCatalog.kt`
- `app/src/main/kotlin/io/github/mangi/eta/agent/subagent/SubagentProfile.kt`
- `app/src/test/kotlin/io/github/mangi/eta/agent/model/AgentPromptBuilderTest.kt`
- `app/src/test/kotlin/io/github/mangi/eta/agent/terminal/TerminalExecutionContextTest.kt`

本轮没有整文件覆盖后台版文件。实现语义为：

- `TerminalExecutionContext` 展示 Linux 工作区、默认同路径目录和额外共享目录的两端路径映射。
- 系统提示、终端工具说明、文件工具说明和 coder 角色统一区分 Android 系统/设备操作与 Linux 开发环境。
- 保留现有 Root 检查、权限检查、失败处理和前后台选择规则。
- 保留后台能力介绍、应用状态保护、内置搜索修复及其他后台版定制提示。
- 只增加上下文说明，不增加复制、移动或挂载文件的实际操作。
- 明确区分“已配置映射”和“已验证挂载成功”：配置存在不证明本次挂载成功，实际可见性、读写能力以终端或文件工具的执行结果为准。

## 第 2 轮实际实现：每个任务独立的原生操作入口

- 新增 `TaskScreenSession`，每个 run 保存自己的执行位置、观察帧、观察代次和后台屏幕绑定；不存在全局“当前屏幕”。
- 原生工具新增可选 `execution_location=foreground|background`。新任务初始默认前台；进入后台后省略 location 会沿用本任务当前后台位置，不回到主屏。
- 前台请求继续交给现有 `AgentLocalTools` 和 `GuiControlCoordinator`，不改变 GUI 操作权、权限和生成号检查。
- 后台请求复用现有 `BackgroundScreenTools`、每任务 `Binding`、`ScreenLeaseRegistry` 和旧 `screen_*` 执行路径；没有第二个虚拟显示引擎。
- 后台观察生成任务自己的 `observation_id`，同时保留 `frame_id`、`display`、`screenshot` 坐标和几何信息；节点与截图来自同一任务屏幕。
- 后台输入必须带当前 `observation_id`；暂停恢复、用户接管、丢屏、屏幕树变化或几何变化后旧观察不能输入，也不会自动改走主屏。
- 后台截图复用现有 `AgentImageTools` 图片投递路径；仅返回文件路径不算模型已看到图片。
- 旧 `screen_*` 工具和 `screen_*` 默认兼容路径保留；新入口通过显式参数进入。
- `PASTE`、`NOTIFICATIONS`、`QUICK_SETTINGS`、URI 打开和系统面板在后台没有可靠实现，返回明确错误；`BACK`、`HOME`、`ENTER`、`RECENTS` 复用现有引擎按键注入。

### 第 2 轮第二次返修（自动化通过，真机未验证）

- 节点解析按 `Boot.dump()` 的真实字段 `cls/pkg/vid/edit/click/scrollable/actions/enabled/bounds` 转换到原生观察字段，保留输入框 `view_id`。
- 观察和输入前校验共用同一套包名解析：优先顶层 `package`，真实 dump 没有顶层字段时取节点 `pkg`，避免观察生成与校验规则不一致。
- 观察绑定 `displayId`、显示宽高、`rotation`、任务恢复代次和 frame；截图尺寸单独取 `VDriveWorker.shot()` 的实际 `width/height`，不再用显示几何冒充截图尺寸。
- 节点观察使用 `Boot.handle()` 最终响应中的 `geometry`（`displayId/width/height/rotation`）作为节点侧权威几何，默认不截图；只有需要向模型提供截图时才走 `screen_shot`，并额外检查节点与截图的几何一致性。仅截图观察不再被当成空节点树；节点树读取与截图读取分别标记来源。
- 输入前重新取得权威几何并比较 `displayId/width/height/rotation`；不一致立即失效，不把旧观察重新认证。
- 已发布观察的 `frame_id`、截图尺寸、缩放比例保持不可变；校验失败只失效旧观察，校验成功也不在旧 `observation_id` 下替换图片或帧号。
- 节点路径使用 `Boot.handle()` 最终响应中的 `geometry`，默认不截图、不压缩、不写文件；仅当模型需要截图时才读取 `VDriveWorker.shot()`，并检查节点与截图几何一致。

## 保留的后台版能力

本轮不改变以下现有设计和行为：

- 单个 VDrive 引擎及现有部署、认证、版本配对协议。
- 最多两个后台执行任务；同一 App 串行，额外任务排队。
- 每任务独立屏幕上下文、节点观察、截图和输入校验。
- 流体云/系统实时通知、每任务通知和通知内继续指令。
- 完成卡片的“打开 ETA 会话”“打开 App”“继续指令”三个入口。
- 前后台选择规则、应用状态保护、内置搜索修复和现有定制提示。
- 息屏暂停策略；息屏执行仍未启用。
- 包名、签名、`versionCode`、`versionName`、VDrive 引擎和系统 Hook。
- `HARMONY.md`、`harmony-agent` 及仓库现有其他内容。

## 后续风险与未决事项

- 坐标与观察协议转换：前台 `observation_id/normalized` 与后台 `frame_id/display/screenshot` 的语义不同，本轮由每任务 `TaskScreenSession` 分别维护；仍需用真实设备验证旋转、视角切换和第三方应用画面。
- 双任务隔离：本轮已用现有租约和每任务会话分开节点、截图、输入与清理；仍需真机复测两个应用同时操作、同一应用串行和取消隔离。
- 后台按键：`BACK`、`HOME`、`ENTER`、`RECENTS` 复用现有引擎；`PASTE`、通知栏和快捷设置没有可靠后台实现，必须返回错误，不能伪造成功。
- 旧 `screen_*` 兼容入口仍保留；默认原生工具目录新增可选 `execution_location`，但旧会话和旧工具名不受影响。
- 按需预览不能干扰观察：预览不得改变当前观察帧、坐标基准或任务输入状态。
- 接管确认：用户确认暂停后，手动接管必须有明确的窗口就绪和旧屏释放确认。
- 恢复前重新观察：暂停/接管/息屏后恢复时必须重新取得屏幕并观察，不能重放旧输入。
- 应用交还：窗口就绪检查只能证明窗口状态，不能证明第三方应用内容已完成加载。
- 系统 Hook 兼容：PowerHooks、AssistantManager 等系统 Hook 的兼容性需要单独验证，不能以本轮提示词改动推断已兼容 3.4.0。
- 息屏执行仍未启用：本轮继续执行息屏暂停，后续才进行独立实验。
- 长按电源键触发软重启：目前只有用户报告，完整 `system_server` 崩溃栈尚未提供；本轮未定位、未修复，不能将本轮合并宣传为该问题的修复。

## 验收边界

本轮的自动化验证覆盖上下文描述、提示词一致性、工具说明、每任务前后台路由、观察失效、取消隔离、旧 `screen_*` 兼容路径和坐标转换；不能替代真实设备上的 Android/Linux 挂载成功验证、ColorOS 通知行为、双任务屏幕隔离、旋转/接管恢复或系统 Hook 兼容性验证。所有测试结论以交付分支的实际构建版本为准。

2026-11-02 e44 返修：只读预览从主屏接管恢复与通知入口统一；手动租约释放保留拥有者/排空校验；finish/release 收尾不再绕过迁移窗口就绪确认；输入失败提示不被帧刷新覆盖。详见 TASK_PREVIEW.md 与对应候选 CI。验证码响应原因未定，系统动画效果需真机验证；不新增 Hook 或绕过校验。

2026-11-02 e45 返修：运行卡片新增打开 ETA 会话入口；终态清理彩边不依赖前台历史标记，已关闭完成卡片拒绝旧运行快照；虚拟屏最终回收避免先关输出再移除显示的双重状态变化。用户已确认上一候选接管/继续流程跑通，但本次动画效果与结束状态须匹配新包验证；不变更正式下载或发布状态。
