# 阶段 8 · 细节修正与息屏不暂停

基线 `ab061cf`（2026110614）。按 S10 / S11 拆成 8a、8b 两次提交，最后一并推送、发布 2026110615。猫 SVG 和生成矢量、包名、备份/导出、User-Agent、日志、上游致谢保持不变。没有改任务输入、预览触摸坐标、窗口几何、任务调度或动画。

## 8a · 每条先红后绿

修改生产代码前先运行新测试。失败摘要保存在 [before/](stage-8/before/) 中的 XML；只保留用例名和首行错误，不包含长堆栈。布局测试先运行 8 项，其中 6 项按预期失败；随后文案、提示身份和常用卡测试 5 项全部按预期失败。

| 规格 | 修改前自动化失败 | 修正及修改后断言 |
| --- | --- | --- |
| S10.1 | 223dp 名称可见字符为 0；提示只有 1 行 | 标题 Column 两行，状态 10dp 圈无 Chip 背景；223/244dp × 100/200% 名称至少 4/6/2 字、按钮 48dp；244dp 的 Google Chrome 至多省略 2 字母；五状态完整；短长提示切换后画面 bounds 不变 |
| S10.2 | 圆图标内部被状态圈遮盖 | 28dp 图标，16dp 实心圆垫和 12dp 圈，中心 (32,32)，28dp 高图标槽不撑高行；方形覆盖 ≤16dp²、圆形内部零覆盖；像素断言排除圆形抗锯齿边缘 2dp；多任务叠图加 2dp 圆垫 |
| S10.3 | 四条建议副标题有 visual overflow | 副标题两行、同排等高；393dp 四条完整；点击回调不变 |
| S10.4 | 运行按钮与菜单中心差 8dp | 垂直居中后差 ≤1dp；真实 MotionEvent 手指拖到底部，最后菜单仍在创建按钮上方；96dp 底部留白不变 |
| S10.5 | 英文长标签无法单行完整显示 | Root / A11y / Overlay / Alerts / Engine 单行；读屏保留 Accessibility / Notifications 全称 |
| S10.6 | 胶囊任务行只有 32dp | 任务行、打开会话、补充指令的语义点击区均 ≥48dp；文字字号不变 |
| S10.7 | 权限引导中的品牌空格未清理 | 三语去掉旧版本后缀；中文相邻汉字的品牌空格归零；保留项目与历史上游名称 |
| S10.8 | 五个资源仍为旧长文案 | 简/繁/英 15 个值逐项匹配 S10.8；操作数量、顺序、PendingIntent 不变 |
| S10.9 | 提示换成非中文前缀后，暂停仍停留在旧提示 | 先引入 PreviewHintType，保留旧文案运行 TaskPreviewActivityTest 全绿，再替换资源；24 类提示三语资源、自动刷新/保留、所有错误分支和接管上下文均覆盖；新帧不抹掉输入失败；未知后端诊断仍保留 |
| S10.10 | 排队返回「等待执行」，不是专用短标签 | 实际 Notification extras 遍历所有状态及三种完成态、三语短标签；不再 take(N)；完成态独立于是否弹出横幅 |

8a 本地针对性回归 **42 项通过，0 失败、0 跳过**，覆盖 Stage8Layout/Copy/SavedTask/Hint、TaskPreviewActivity、Stage6Surfaces、AgentRuntimeScreenOn。提示类型独立迁移、尚未改文案时的通过记录见 [hint-types-before-copy.xml](stage-8/before/hint-types-before-copy.xml)。新增文案引用实际按钮名；历史文档中的废弃按钮名以泛称标注，避免误导现版本操作。

提示区按本地化字体实际两行高度固定容器，避免 CJK fallback 字体与 Latin minLines 高度不同而跳动。文本内部测量高度可以不同，断言实际画面区域的位置和高度始终一致。预览窗按设计固定深色，浅/深主题截图的宿主底色不同。

## 修改前后截图

均为 Robolectric API 36 原生图形渲染（393dp 宿主），不是手绘稿，也不是 ColorOS 真机截图。测试夹具使用猫标记及手机占位图；任务窗口几何、手势逻辑没有为截图调整。每格点击可查看原始 PNG。

| 预览头部 | 浅色前 → 后 | 深色前 → 后 |
| --- | --- | --- |
| 223dp / 100% | [前](stage-8/before/stage8-preview-light-223-100.png) → [后](stage-8/after/stage8-preview-light-223-100.png) | [前](stage-8/before/stage8-preview-dark-223-100.png) → [后](stage-8/after/stage8-preview-dark-223-100.png) |
| 223dp / 200% | [前](stage-8/before/stage8-preview-light-223-200.png) → [后](stage-8/after/stage8-preview-light-223-200.png) | [前](stage-8/before/stage8-preview-dark-223-200.png) → [后](stage-8/after/stage8-preview-dark-223-200.png) |
| 244dp / 100% | [前](stage-8/before/stage8-preview-light-244-100.png) → [后](stage-8/after/stage8-preview-light-244-100.png) | [前](stage-8/before/stage8-preview-dark-244-100.png) → [后](stage-8/after/stage8-preview-dark-244-100.png) |
| 244dp / 200% | [前](stage-8/before/stage8-preview-light-244-200.png) → [后](stage-8/after/stage8-preview-light-244-200.png) | [前](stage-8/before/stage8-preview-dark-244-200.png) → [后](stage-8/after/stage8-preview-dark-244-200.png) |

| 界面 | 浅色前 → 后 | 深色前 → 后 |
| --- | --- | --- |
| 建议卡 | [前](stage-8/before/stage8-home-light.png) → [后](stage-8/after/stage8-home-light.png) | [前](stage-8/before/stage8-home-dark.png) → [后](stage-8/after/stage8-home-dark.png) |
| 任务角标 | [前](stage-8/before/stage8-badge-square-light.png) → [后](stage-8/after/stage8-badge-square-light.png) | [前](stage-8/before/stage8-badge-square-dark.png) → [后](stage-8/after/stage8-badge-square-dark.png) |
| 常用任务按钮 | [前](stage-8/before/stage8-saved-light.png) → [后](stage-8/after/stage8-saved-light.png) | [前](stage-8/before/stage8-saved-dark.png) → [后](stage-8/after/stage8-saved-dark.png) |
| 常用任务底部 | [前](stage-8/before/stage8-saved-bottom-light.png) → [后](stage-8/after/stage8-saved-bottom-light.png) | [前](stage-8/before/stage8-saved-bottom-dark.png) → [后](stage-8/after/stage8-saved-bottom-dark.png) |
| 英文权限卡 | [前](stage-8/before/stage8-readiness-en-light.png) → [后](stage-8/after/stage8-readiness-en-light.png) | [前](stage-8/before/stage8-readiness-en-dark.png) → [后](stage-8/after/stage8-readiness-en-dark.png) |
| 展开胶囊 | [前](stage-8/before/stage8-capsule-light.png) → [后](stage-8/after/stage8-capsule-light.png) | [前](stage-8/before/stage8-capsule-dark.png) → [后](stage-8/after/stage8-capsule-dark.png) |

英文权限卡基线另一次直接恢复 `ab061cf` 的 PermissionHealthCard 和英文 strings 渲染；渲染完成后恢复修改，未用新标签冒充旧界面。

## 8b · 息屏行为

8a 提交 `4d0bd9e`。8b 只移除 ACTION_SCREEN_OFF 中的自动暂停循环，以及 pausedForScreenOff / ScreenOffPaused 的属性、投影、三语文案和测试引用；保留隐藏 ambient 窗口、刷新状态、主屏锁定拒绝和显式继续的全部原有守卫。

先改写原 physicalScreenOff 测试，新预期在旧实现失败（[记录](stage-8/before/screen-off-test.xml)：「Power key must not pause the task」）；删除暂停分支后，息屏、亮屏任务均保持运行。对应回归 **36 项通过，0 失败、0 跳过**：

- 主屏曾执行前台操作的任务，息屏也不暂停、不取消；息屏移除流光/胶囊，亮屏 ambient 状态回到 Running。
- 手动暂停和持有接管 lease 的任务经过息屏/亮屏仍暂停。原队列测试改名为 manuallyPausedQueueWaitsForManualResume，仍测试手动暂停语义。
- 后台虚拟屏息屏期间真实 TaskScreenSession 路由仍调用 screen_dump 后端；正在打开后台应用的任务也不暂停。
- Stage8ScreenGuardTest 反射调用现有执行器编译出的 beforeToolExecution 回调（按类型签名查找，不依赖 lambda 序号），在「屏幕关闭 / 锁屏 / 两者同时」三种组合下，对 tap / type_text / swipe / launch_app / open_uri 均取得 SCREEN_LOCKED；web_search / fetch_url 均 Allow，无实际输入、Root RPC 或网络请求。
- 上述回调运行后任务控制器不暂停、不取消，也没有取得 GUI 所有权；执行器源码与 ab061cf **逐字节一致**。
- 息屏广播前后最新 WakeLock 实例相同；未新增锁、电池保活、权限或 keepScreenOn 策略。

## 构建与发布门禁

versionCode 仅递增一次至 **2026110615**，versionName 仍为 3.3.5，Root 引擎仍为 20261105-e49-parallel-observe。两次本地提交完成后一次推送 main，触发同一条 [Etax Build](https://github.com/Lmin2419/eta-background/actions/workflows/etax-build.yml) 流水线；交付必须同时满足「测试与 Lint / 签名构建 / 发布」三项成功，且 [Release](https://github.com/Lmin2419/eta-background/releases) 存在本版本签名 APK。CI 的 test-results / lint / workflow 日志是最终平台独立证据，不把本地截图或上一版通过视作本版 CI。

最终本地相关回归 **118 项通过、0 失败、0 跳过**，包含全部阶段 8 用例、通知三类测试、预览状态机、窗口、TaskScreenSession、任务投影和运行时息屏/恢复。额外的「界面可见接管/暂停提示」测试先失败后通过，失败记录见 [visible-hint-test.xml](stage-8/before/visible-hint-test.xml)；两个界面提示资源现在引用同一份新提示目录。旧通知测试同步断言 task_short_thinking，仍保留原测试的推广通知和终端会话释放检查。

Linux 首次完整 CI 跑完 **2598 项**，唯一失败为旧 LocaleResourcesTest 仍期待品牌与中文之间的空格；按 S10.7 同步简繁两条预期后，LocaleResourcesTest + Stage8CopyTest **5 项通过**，修正折回 8a，最终仍为两次提交。首次 CI 记录见 [42231569916](https://github.com/Lmin2419/eta-background/actions/runs/42231569916)；最终以修正后的提交对应流水线及 Release 为准。

第二轮 Linux 检查 [42233949156](https://github.com/Lmin2419/eta-background/actions/runs/42233949156) 已通过 **2598 项 / 0 失败 / 0 错误 / 1 项既有环境跳过**；Lint 0 Error、479 Warning、1 Hint，引擎 13 项通过。发布前边界复核确认 WaitingForGui 在既有任务投影中属于 Queued，短标签同步为「排队中 / 排隊中 / Queued」，避免误称需要用户操作；先失败后修复记录见 [gui-queued-test.xml](stage-8/before/gui-queued-test.xml)，通知回归 **17 项通过**。该候选发布已主动取消，最终仍以此修正后的两次提交统一构建发布。

本机 Windows 全套测试尝试遇到文件路径/描述符/POSIX 权限失败，并在 BackgroundTerminalJobsTest 的 FileDescriptor.close0 原生管道关闭处卡住（线程栈确认后终止该次执行）。没有删除、跳过或放宽这些测试，也没有为 Windows 改业务代码；完整套件由 Linux CI 验证。上述 118 项是实际完成的相关回归，不冒充全仓通过。

接线检查 **12/12**；Root 引擎从源码重新编译并内嵌成功，内容与基线一致。本地 `:app:lintDebug` **通过**，0 Error / 0 Fatal；466 条 Warning、1 条 Hint，均不阻断构建。

## 委派与范围

四个腾讯入口子任务：布局和提示类型用 coding（配置主用 GLM-5.3），短标签复核用 quick（配置主用 DeepSeek-v4.1-flash）；这三个返回未附实际模型元数据，不能推断是否内部回退。息屏边界 review 返回明确记录：DeepSeek-v4-pro 75 秒超时、Kimi-k3-1 HTTP 429，最终 **GLM-5.3** 完成。主会话逐项复核并执行补丁、测试、截图；未照搬子任务中不符合规格的完成态优先级建议。

## 最终手机验收清单

安装最终 2026110615 签名 APK 后，浅色和深色分别检查；自动化不替代真机结果。

1. 预览：普通与 200% 字号，223/244dp 级窄窗中应用名有实际字符；放大、关闭易点；接管状态「你在操作」；短提示和长提示切换，画面不跳高。
2. 任务条：方形、圆形图标角标位于右下外侧，无镂空划痕；多任务叠图边缘清楚。
3. 首页四条建议完整；常用任务「运行 / ⋯」对齐，滚到最后仍能点菜单；英文权限卡不折词；展开胶囊任务、会话、补充入口易点。
4. 通知：393dp 下运行中三个按钮完整；暂停、接管、完成、停止、失败标签为完整短词；点按动作与原来一致。切换简体、繁体、英文核对。
5. 预览接管、交还、页面回主屏、输入失败后画面刷新，提示与按钮对应；授权引导只显示「胖小胖」。
6. **S11 纯思考 / 搜索：** 发出后立即按电源键，稍后亮屏，任务已完成或仍继续，不能变成「已暂停」。
7. **S11 主屏操作：** 执行中按电源键，任务不暂停，锁屏期间没有误触；解锁后能看到转用后台屏或哪一步无法在锁屏下完成的说明。
8. **S11 后台虚拟屏：** 息屏后继续执行，行为不变。
9. 先手动暂停或接管，再息屏、亮屏，必须仍暂停，只有主动继续/交还才恢复；运行中任务亮屏后状态、流光和通知一致。

通知实际排版、ColorOS 流体云、系统省电下的连接与完成时间仍需以上真机验收。
