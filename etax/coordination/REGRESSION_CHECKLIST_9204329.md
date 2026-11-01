# Etax 第 2 轮真机回归清单（候选 9204329，C002 修订）

> 当前状态：**BLOCKED_DEVICE**。`adb devices -l` 无设备，20 项全部未执行；以下“预期”不是实际结果。  
> 代码/自动化已通过，真机待验证。第 20 项只整理既有现象/日志，不主动触发软重启，也不宣称已修复。

## 设备就绪说明

### 候选身份

| 项目 | 值 |
| --- | --- |
| 固定代码 head | `9204329cffd78e392bf45d73104866701f131a1c` |
| APK 文件 | `Etax-v3.3.5+etax.9204329.apk` |
| SHA-256 | `2b3dbc1000d7291c2a76da110a6265c5301f2a997b67c5fe3615b61575b1556c` |
| 大小 | 6,718,399 bytes |
| 包名 / 版本 | `io.github.mangi.etax` / `3.3.5+etax.9204329`（versionCode `2026103003`） |
| 签名 | `CN=ETAX Self Signed, O=Personal, C=CN`；证书 SHA-256 `dc309f48b192f58aef3c750841728e284ad05db2a07a607b2e1fafbcc4c1a2a5` |
| 引擎 | `20261030-e40-takeover` |
| 证据 | [候选构建](https://github.com/Lmin2419/eta-background/actions/runs/41277041172) · [工件 13072382013](https://github.com/Lmin2419/eta-background/actions/runs/41277041172/artifacts/13072382013) · [PR Check](https://github.com/Lmin2419/eta-background/actions/runs/41277043314) |

### 用户最少动作

1. 用 USB 连接测试手机并开启“USB 调试”；在电脑执行 `adb devices -l`，状态必须是 `device`，不能是 `unauthorized`/`offline`。如果手机未连接，保持 BLOCKED_DEVICE，不继续下面的步骤。
2. 由用户安装固定候选（dsh 不自行安装）：
   `adb install -r Etax-v3.3.5+etax.9204329.apk`
   预期包名、versionName/versionCode 与上表一致。
3. 在 App 内完成 Root 授权、无障碍观察者授权，以及任务通知/悬浮窗所需权限；健康页应显示 Root、observer 和 `20261030-e40-takeover` 引擎可用。
4. 保持手机解锁、连接、App 进程和 Root/observer 可用；测试期间不要让电脑休眠或断开 USB。
5. 从 **App 内部** 创建任务开始测试，不用外部脚本、模拟测试或测试替身代替真机结论。

### 如何核对设备与已装版本

```powershell
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.display.id
adb shell dumpsys package io.github.mangi.etax | Select-String 'versionName|versionCode'
adb shell pm path io.github.mangi.etax
```

记录设备型号、Android/ColorOS 版本、安装时间和命令输出摘要；设备序列号脱敏，不写入本文档。历史用户报告为 OPPO Find X9 Ultra / Android 17 / ColorOS 17，但本轮未读取核验，不能写成已验证环境。

### 需要用户在场的步骤

全部 20 项都需要测试设备及用户在旁。特别是：前台当前页面（1）、截图坐标（4）、静态等待和新 frame（5）、双应用并行与取消（6、8）、同应用串行（7）、目标显示旋转（9）、暂停恢复（10）、用户接管（11）、通知/继续指令/打开会话/应用交还（14–17）和既有软重启现象整理（20）必须由用户现场操作或确认。第 20 项不由 dsh 主动触发。

**不要**为了检测连接而自行安装 APK、改设备设置、触发软重启或绕过 Root/observer 权限保护。缺少设备只记一次阻塞，不循环重试。

## 顺序用例

> 第 2–4 项使用**原生后台 GUI 工具**；第 13 项才单独使用旧 `screen_*` 兼容入口，两套参数不能混写。原生节点字段是 `package`，不是底层 dump 的 `pkg`。

| # | 用例 | 操作步骤 | 预期结果 | 实际结果/错误码 | 证据引用 |
| --- | --- | --- | --- | --- | --- |
| 1 | 前台当前页面操作 | 在 App 内创建任务，显式 `execution_location=foreground`；用 `observe_screen`（默认不附截图）观察当前页，再用 `tap_element` 或 `type_text` 操作。 | 前台 GUI 操作成功；不发生后台屏绑定；任务通知和完成卡片正常。 | 未执行（无设备） | 待填任务 ID/截图 |
| 2 | 后台节点观察 | 调用 `launch_app`，参数含精确 `package_name` 和 `execution_location=background`；随后调用 `observe_screen`，显式 `execution_location=background`、`include_screenshot=false`、`include_ui_tree=true`。 | 返回 `ui_tree_read=true`；节点含 `index/text/desc/class/package/view_id/bounds/clickable/editable`；不调用截图；有 `observation_id` 和 display 几何。 | 未执行（无设备） | 待填 `observation_id`/返回 JSON |
| 3 | 后台节点输入 | 接用例 2，选择同次观察中的 `editable` 节点；调用 `type_text`，参数为 `index`、同次 `observation_id`、`text`，不要传 `view_id`；输入成功后重新 `observe_screen`，用新的 `observation_id` 继续。 | `type_text` 成功；输入后重新观察得到新 `observation_id`/节点；不复用旧观察；不切换到主屏。 | 未执行（无设备） | 待填新旧 `observation_id`/错误码 |
| 4 | 截图坐标操作 | 调用 `observe_screen`，显式 `include_screenshot=true`、`include_ui_tree=true`；记录同一观察的 `frame_id` 和图片宽高；用 `tap` 或 `tap_area`，显式 `coordinate_space=screenshot` 且携带同次 `frame_id`；操作后重新观察。 | 截图尺寸等于实际图片尺寸；坐标按 scale 转换后在显示坐标内；输入成功；`frame_id` 与观察一致；不复用旧截图。 | 未执行（无设备） | 待填 `frame_id`/截图 |
| 5 | 静态等待与新 frame | 观察静态页面，等待至少 70 秒；再触发一次不改变节点/包名/显示几何的轻量操作（例如 `press_key BACK` 后返回，或用户确认页面产生新 frame）；检查原截图坐标是否仍可用于同一屏。 | 若节点/包名/显示几何和 frame 均未变，原观察仍可用；若产生新 frame 但节点/几何不变，原已发布截图坐标按实现规则仍可用；没有实际产生新 frame 时该子项标“未覆盖”，不能把“frame 必须不变”作为唯一通过条件。 | 未执行（无设备） | 待填两个时间点/`observation_id`/`frame_id` |
| 6 | 两个不同应用并行 | 任务 A 后台打开应用 A，任务 B 后台打开应用 B；分别 `observe_screen` 并点击。 | 两任务各自拥有独立 `observation_id`、显示屏和通知；互不覆盖、互不串屏。 | 未执行（无设备） | 待填两个任务 ID |
| 7 | 同一应用串行 | 任务 A 已占用应用 A 后台屏；任务 B 再请求打开同一应用 A。 | 同一应用不并行操作；按实现串行/明确拒绝，不能把 B 的输入送到 A 的屏幕。 | 未执行（无设备） | 待填排队/拒绝返回 |
| 8 | 取消隔离 | 任务 A 执行长等待，任务 B 执行点击；取消任务 A。 | A 停止且清理自己的屏；B 继续执行并保留自己的观察和通知。 | 未执行（无设备） | 待填取消返回和 B 结果 |
| 9 | 目标显示旋转 | 先记录后台目标屏的 `displayId/width/height/rotation`；在设备允许的正常操作下改变目标显示方向（不修改全局显示配置）；再观察旧 `observation_id`。 | 只有目标 `displayId/width/height/rotation` 实际变化才验证旋转失效；旧观察返回 `OBSERVATION_STALE`/等价 stale，重新观察后输入成功。目标显示未变化时该项“未覆盖”，不判失败或通过。 | 未执行（无设备） | 待填旋转前后几何字段 |
| 10 | 暂停恢复 | 任务执行中暂停，再恢复；用暂停前 `observation_id` 输入。 | 旧观察返回 `OBSERVE_AFTER_RESUME`（或明确等价错误）；不自动重放；重新观察后可输入。 | 未执行（无设备） | 待填暂停/恢复时间和错误码 |
| 11 | 用户接管 | 后台任务运行中，用户从最近任务进入目标应用；观察旧任务。 | 任务暂停并提示“应用已移出后台”；旧观察失效；确认接管后重新观察再恢复；另一任务不受影响。 | 未执行（无设备） | 待填通知文案和暂停状态 |
| 12 | 安全的不确定输入 | 仅在当前 App 已有能力可安全执行时构造单次工具结果不确定场景；记录工具名、参数、`TIMEOUT`/`operationMayHaveRun` 返回；若无法安全稳定构造，标记“未覆盖”及原因，不用普通 wait 超时或自动化替身代替。 | 单次工具结果不确定不等于整个 run 终态；不自动重放；旧 `observation_id` 再输入被拒绝；重新观察后才允许输入。不能通过卡死系统/阻塞 ActivityManager 制造超时。 | 未执行（无设备）；若无法安全构造则“未覆盖” | 待填安全步骤/返回 |
| 13 | 旧 screen_* 路径 | 仅使用旧兼容入口：`screen_open`、`screen_dump`、`screen_tap`/`screen_text` 操作后台应用；不复用原生 `observation_id`，不混写原生参数。 | 兼容路径按旧规则工作；不污染原生任务的观察；后台错误不静默切主屏。 | 未执行（无设备） | 待填每个 screen_* 返回 |
| 14 | 完成通知 | 让任务正常完成；检查顶部提示、状态栏和下拉通知。 | 完成状态、任务标题、结果正文正确；通知不重复、不误报失败。 | 未执行（无设备） | 待填截图/通知 ID |
| 15 | 继续指令 | 在完成通知内联输入继续指令并提交。 | 指令进入原会话，不创建空白会话；原任务上下文保留；执行上限和暂停规则生效。 | 未执行（无设备） | 待填原会话 ID |
| 16 | 打开会话 | 点击完成通知的“打开 ETA 会话”。 | 打开对应任务会话，不串到其他任务；返回路径正常。 | 未执行（无设备） | 待填页面截图 |
| 17 | 应用交还 | 点击“打开 App”或按接管路径将目标应用交还前台。 | 目标应用可见并可操作；旧后台屏按窗口就绪/释放规则处理；不把迟到回调误报为成功。 | 未执行（无设备） | 待填应用包名/日志 |
| 18 | 失败提示 | 让后台启动失败（不存在的包/不可用显示）。 | 明确返回错误码和原因；不自动改走主屏；任务通知显示失败状态。 | 未执行（无设备） | 待填错误码 |
| 19 | 后台不可靠能力 | 请求 `PASTE`、通知栏、快捷设置、URI 或系统面板。 | 明确返回 `BACKGROUND_UNSUPPORTED_OPERATION` 或对应错误；不伪造成功。 | 未执行（无设备） | 待填请求与返回 |
| 20 | 既有软重启现象（不主动触发） | 仅整理用户已有现象、时间和已存在的脱敏日志；**不要求再次长按电源键、不远程操作设备**。若没有既有现象/日志，标记“未覆盖”。 | 记录已有现象即可；不把未主动复现写成通过，不宣称已修复；作为独立未解决观察项，不作为常规回归必做动作。 | 未执行（无设备）；无既有材料则“未覆盖” | 待填用户提供的现象/日志 |

## 结果记录要求

- 每项记录任务 ID、`observation_id`/`frame_id`、实际工具返回 JSON、错误码、截图或日志路径；设备序列号脱敏。
- 只有真实设备实际执行并观察到结果才填“通过”；无设备必须保持“未执行”。
- 第 12 项若无法安全构造，保持“未覆盖”，不得用普通等待超时或自动化替身代替。
- 第 20 项只整理已有现象；不因未主动复现而填通过。
- 本清单不能替代 Logcat、VDrive 引擎日志、ColorOS 通知/跨屏实测；不得把预期写成实际结果。
