# 阶段 6 · 系统级浮层

## 基线与发版

- 按 `04-implementation-plan.md` 阶段 6、`03-screens.md` S5–S8 实施。阶段 5.5b 的 [CI 42167005946](https://github.com/Lmin2419/eta-background/actions/runs/42167005946) 三项全部成功后，拉取 `main` 的 `6bb534c` 开始。
- 前一阶段签名版本为 [2026110609 / 6bb534c](https://github.com/Lmin2419/eta-background/releases/tag/etax-2026110609-6bb534c)，2550 个测试、0 失败/错误、1 个原有跳过；13 个引擎部署测试通过。APK SHA-256 为 `bbafc94957ba3bcdc764084112e200ec0593c68409834ce9024e9c8a84455bec`，与 CI 签名日志一致。
- 本阶段独立提交、版本 `2026110610` / `3.3.5`；引擎仍为 `20261105-e49-parallel-observe`。提交的 Etax Build 必须在 checks、engine-tests、build 全部成功且发布签名 APK 后交付；结果见该提交的 [Actions](https://github.com/Lmin2419/eta-background/actions/workflows/etax-build.yml) 与 [Release](https://github.com/Lmin2419/eta-background/releases)。不进入阶段 7。

## S5 任务预览窗

`TaskPreviewActivity.setContent` 挂载 `EtaPreviewWindow`，`AndroidView` 使用原来的同一个 `TaskPreviewImageView`。固定深色 `#16171C`、文字 `#ECEDF2`、描边 `#2A2C35`；28dp 外圆角、16dp 画面圆角；头部 App 图标/名称、状态、Rounded 放大/缩小及关闭图标。小窗宽为可用屏宽 62%，大窗宽为可用屏宽减 32dp。沿用原 resize 流程，仅更换尺寸展示参数和 380ms 阻尼弹簧插值；进出动效为 380/180ms。

| 状态 | 改版前 | 改版后及验证 |
| --- | --- | --- |
| AI 执行中 | 原生按钮同时显示，接管/返回等以可用性区分 | 运行中；主「接管」、次「暂停」；显示当前步骤。没有首帧时禁用接管但允许放大 |
| 已暂停 | 「旧版继续按钮」原生按钮 | 已暂停；主「继续」、次「接管」；暂停提示。控制器已暂停但卡片仍为 Attention 时，立即呈现继续 |
| 你在操作 | 接管/返回/继续并列 | 已暂停·你在操作；主「交还 AI」、返回图标。画面 2dp accent 描边；原触点和轨迹保留 |
| 需要你 | 通用状态文字 | Attention 状态；主「接管处理」、次「暂停」；显示可用原因 |
| 正在交接 | 通用禁用按钮 | 等待当前操作结束、运行指示；操作禁用，无次按钮 |
| 完成 | 底部另起原生「打开 App」 | 主「打开 App」、次「关闭」、结果首句；保留额外「操作页面」入口，仍可接管已完成页面 |
| 失败/画面不可用 | 占位图与错误提示混在通用操作中 | 未完成；主「关闭」、次「打开会话」（有会话才显示）；错误原因。已完成但无画面仍保留额外打开 App；暂停任务旧屏释放后仍保留继续 |

输入失败提示使用 danger，沿用原 `inputFeedback` 生命周期：新画面不是输入成功的确认，不能自动抹掉错误。手动输入刷新期间 geometry 暂空仍保留交还 AI；返回键继续要求有效 observed geometry。引擎 `VDriveWorker` 的 preview key 输入只接受 `KEYCODE_BACK`，因此不显示主页键、不新增引擎能力。

源码保护校验：`TaskPreviewImageView.kt` 整文件未改；SHA-256 `870a45d5df86c3513e92ba69162b6b6d855c09d4c8501de0552bded5f71f7418`。以下方法与阶段前文本完全一致：`touch`、`sendInput`、`clearGesture`、`rpc`、`invalidateFrame`、`framePollIntervalMs`、`onStart`、`onStop`、`onConfigurationChanged`、`finishRelease`、`previewFailure`。`displayFrame`、`clearFrame`、`acquire`、`release` 仅增加展示状态赋值；原判定、调用、顺序不变。窗口 flags、gravity、y、dimAmount 保留。

`PreviewWindowMapperTest` 覆盖七行、初始无帧、确认不可用、手动优先、终态、暂停优先及 busy/resizing/closing。原 `TaskPreviewActivityTest` 和 `AgentRuntimeScreenOnTest` 的 View 反射断言迁移至新的状态/动作，继续检验旧屏释放后恢复、其他窗口手动租约、旧帧拒收、未知输入结果不重放。

## S6 电源键助手

| 项目 | 改版前 | 改版后 |
| --- | --- | --- |
| 面板 | 自定义黑白色、顶部大圆角、空闲时透明底 | 系统深浅色 `surfaceRaised`，28dp 全圆角/e3，左右及导航栏上方 8dp，把手 36×4 |
| 对话 | 传入所有消息，沿用旧字号 | 仅展示最近 3 个用户轮次及其过程/回复，完整记录仍在会话中；C20/C21、15sp/24sp，工作过程默认折叠 |
| 输入 | 行末 40dp 语音/发送按钮 | 原输入、发送、停止、识别回调保留；居中 64dp accent 按钮，左键盘、右打开会话；录音外圈 aurora 声波 |
| 建议 | 纵向三条 | 横向可滚动的三个 C5 样式 Chip，仍执行原三个完整任务文本 |
| 动效/流光 | 独立色值、底部横向声波 | 共享 EtaAurora 四色；底部弹簧进入；既有交接退出流程向顶部缩小淡出 |
| 长按说话 | `detectTapGestures` 的长按消费循环可能把取消判为松手 | 只替换助手按钮的本地手势收尾为 Up-or-cancellation；保持短按/长按动作与录音接口。原生取消、移出、禁用/卸载不发送；正常松手只完成一次 |

`EtaVoicePanelTouchTest` 使用真实 MotionEvent DOWN/MOVE/UP/CANCEL，不用 dispatchRawDelta 或语义长按。覆盖拖动停靠、长回复后把手、上拉打开会话、取消拖动、IME 跟随、退出与减少动画、语音结束/取消和任务停止分离，并新增长按期间重组、正常松手、移出及窗口取消。

**保留边界**：前台自动操作前的同步撤窗/等待 detach 屏障仍按原流程执行，不延迟撤窗来播放动画，也不改变 Runtime 调度。通常的助手退出/会话交接使用新视觉动效。应用内打开会话的完整记录、语音识别配置、模型下载入口均保留。

## S7 顶部胶囊

| 项目 | 改版前 | 改版后 |
| --- | --- | --- |
| 收起 | 固定 248dp、随主题、两行内容、自绘 PhaseIndicator | 固定深色 92%，40dp 头部、160–300dp 随文字宽度、StatusRing 16、单行步骤与 ExpandMore |
| 展开 | 补充/暂停/停止三个图标，通用表面 | 宽 min(屏宽−32dp, 360dp)、20dp 圆角；思考尾句只在展开区；查看/暂停或继续/停止，停止用 danger |
| 其他入口 | 多任务选择、补充输入、会话 | 全部保留；查看有后台 App 时打开任务预览，否则回到对应会话 |
| Attention | 通用阶段色 | `#3A2A12` 背景、「需要你处理·点此查看」，进入该状态给一次触感 |

保持现有 yielded 触摸让路、补充输入焦点交接及任务选择回调。尺寸只在展示档位/语义文字需要时更新，不逐动画帧重排系统窗口。API 36+ 原有系统任务表面优先策略不变，系统支持时仍显示 Live Updates/流体云。

## S8 通知

| 项目 | 改版前 | 改版后及保留项 |
| --- | --- | --- |
| 标题/颜色 | 仅任务标题 | 应用名·任务名，未知应用名时仅任务名，总长≤60；不截断 UTF-16 代理对；accent，小图标不变 |
| 运行 | 状态加流式尾段 | 现有本地化动作及已知第 n 步；无步数不编造，模型思考正文不拼入通知 |
| 暂停/需要你 | 通用状态 | 状态和可用原因，避免重复暂停标签 |
| 完成/失败 | 状态加结果尾段 | 已完成·用时 m:ss·结果首句；未完成·原因。终态展示源改取首句，无首发时间时省略耗时 |
| 回复失败 | 状态后的附加提示 | 保持紧接状态词「已完成·回复未发送」，保留原完成结果与重试动作 |
| 运行按钮 | 暂停/停止/会话 | 查看、暂停/继续、停止；查看按有无 App 进入预览/会话 |
| 完成按钮 | 会话/App/继续指令 | 顺序与 PendingIntent、RemoteInput、认证要求全部保留 |
| 系统实时表面 | 原 ProgressStyle | 保留既有 eligible/indeterminate/promoted 规则，已知步数写入进度值；频道、调度、通知身份、完成弹出及去重不变 |

`TaskNotificationCopyTest` 覆盖标题/emoji、步数缺失、首句与小数/网址、三种非终态、终态优先、回复顺序、耗时缺失；`AgentSystemNotificationTest` 在 API 36/37 校验新按钮顺序和任务身份，原完成通知/重试/去重测试继续执行。新增文案均有英文、简中、繁中。

## 本地验证与截图

本地共验证 **303 项（279 + 24），0 失败/错误**。常规 279 项通过后，混合执行中的 Windows Robolectric Native 字体 ZIP 挂载冲突导致 24 项无法初始化；未屏蔽或删除测试，改在新的独立测试进程重跑这 24 项，全部通过。助手长按取消还额外验证了取消后立即短按不会残留状态。运行范围包含 Preview、系统通知、任务快照、AgentRuntime、AgentOverlay、Voice、Assistant、Markdown 及阶段 6 原生渲染测试；另运行 `lintDebug`、`assembleDebug`、`etax/check_hooks.sh` 和 `embed_engine.sh`。最终独立执行 `testDebugUnitTest`（24 项原生界面）、`lintDebug`、`assembleDebug` 得到 BUILD SUCCESSFUL；Lint **0 error / 462 warning / 1 hint**，包含项目既有告警。关键接线 12/12 通过，引擎内嵌成功；Debug APK 版本码 2026110610。预览源码保护校验全部通过。全量 Linux 回归及 13 个真实 shell 引擎部署测试由该提交 CI 运行。

以下为 Robolectric Native 实际 Compose 渲染，不是真机/真实第三方 App 运行截图。预览采用蓝色测试帧来验证 FIT_CENTER 坐标透传；没有用画图工具替换界面。手机验收仍待用户执行。

- [接管预览窗](stage-6/preview_manual.png)
- [助手浅色](stage-6/voice_light.png)、[深色](stage-6/voice_dark.png)、[200% 字号](stage-6/voice_font200.png)
- [展开状态胶囊](stage-6/capsule_expanded.png)

## 手机验收清单

安装本阶段 `2026110610` 的签名 APK，覆盖已有版本。它包含 5.5a 和 5.5b；升级时保留已有会话，先按 [5.5a 清单](stage-5.5a.md) / [5.5b 清单](stage-5.5b.md) 验证输入区、批量管理、归档、备份和迁移。

1. 从后台任务通知点查看：固定深色预览；只读点击放大不接管；放大/缩小、旋转、关闭正常，接管前不会输入。
2. 逐项跑七态：执行→暂停→继续；接管等待→手动点击/拖动/长按/返回→交还；需要你；完成；失败/画面不可用。完成可打开 App 或操作原页面；不可用可打开会话；旧屏移到主屏后仍可继续任务。
3. 手动操作时制造输入未就绪：错误保持到下一操作/成功输入，新画面不能抹掉它。关窗后 AI 保持暂停，其他预览窗口的手动租约不能被绕过；锁屏不得暴露预览。
4. 长按电源键，分别在系统浅/深色及 200% 字号检查助手；左键盘、中央录音/发送/停止、右会话均可点。长按说话松手只发送一次，移出/系统取消不发送；语音识别取消不误停后台任务。
5. 助手累积至少 4 轮：只显示最近 3 轮，打开会话可找到完整历史；过程可展开；长回复能真实上划，拖把手停靠/取消/上拉会话正常；键盘动画期间输入框跟随。
6. 检查顶部状态胶囊或系统任务表面：展开查看/暂停/继续/停止，切换任务、补充输入和打开会话均正常；需要你只在进入时触感一次。Agent 点击让路不被胶囊截走。
7. 同时运行两个任务：通知标题、已知步数、暂停原因正确；按钮控制对应任务。完成显示耗时/首句，三个完成按钮、直接回复、回复失败后的结果保留和重试正常；不会多弹或复活已划掉通知。

## 子任务委派

本阶段实际调用 `external_model` 的 `tencent / cn:deepseek-v4.1-flash` 五次：预览状态映射草稿、通知文案草稿、助手输入区样式草稿、预览边界复核、长按取消保护复核。单次指令与代码均小于 12,000 字符；最长一批 8,968 字符。不发送密钥、令牌或登录资料。主会话核对并修正了模型草稿的资源/API、终态/无帧边界和测试假设，实际文件修改、编译、手势回归、截图、CI 和发版均由主会话完成。本阶段无委派接口故障。
