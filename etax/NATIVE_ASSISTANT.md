# 内置 Root 系统助手与 PMA110 长按适配候选

入口：设置 → 系统助手接管 → 长按电源键唤起（Root）。入口不依赖 XposedService 或远程偏好。

## PMA110_17.0.0.106：新增 ColorOS 原生事件适配（未发布）

用户实测 d870d11 的默认助手配置成功后仍唤起小布。此次核对同版本官方 OTA 中的厂商框架，确认该分支在 `startSpeech` 中直接指定小布组件，绕过默认数字助理设置。仅反复设置 `assistant` 或确认窗口记录无法修复这条路径。

新的适配复用该固件已有能力：

1. 保留下面的系统助手角色配置；核对机型 PMA110、SDK 37、Android 用户 0 和实际 `oplus-services.jar` SHA-256 后才允许应用厂商适配。其他固件不猜测兼容；升级固件后旧适配不会自动取得唤起资格。
2. 保存两个原值后，通过短生命周期 Root 命令开启 `persist.sys.custom.enable.powerkeybroadcast`，并将厂商策略 `breeno_disable_key`（type 0）设为 `1`，阻止原 `sendSpeechMessage` 入口继续启动小布。小布应用保留；不冻结、卸载、强停或修改其数据。
3. 系统自己识别长按，发送 `oppo.intent.action.power.longpress`。ETA 在现有 `:voice` 进程接收并请求自己的 `VoiceInteractionSession`，沿用现有助手面板；不增加常驻 Root 进程、原始按键监听、按键注入或 system_server Hook。
4. Manifest 接收器要求发送者拥有系统签名权限 `DEVICE_POWER`。接收后还检查已应用的本地事务、固件指纹、当前默认助手、用户解锁及系统初始化/设备限制/水下拍摄状态。服务尚在绑定时最多等待 1.5 秒，不循环重绑或重配系统助手。这里只记录请求，仍由用户确认物理长按是否成功。
5. 厂商策略服务 `oplusdevicepolicy` 的写权限只接受 system UID 或平台签名。仅这一项固定策略操作由 Root 启动 UID 1000 的短命令进程，再调用厂商 Binder API；其他助手操作仍要求 UID 0。没有将代码注入 system_server，也不直接覆盖厂商策略文件。

独立 AtomicFile 日志保存原值和每个已尝试步骤，先备份再写。原策略已被其他管理方设为 `1` 或未知值时拒绝覆盖。超时、Binder 写入结果不确定或 Root 失效时保留日志，不自动重放。恢复先持久化停用 ETA 接收资格，再恢复小布入口，最后恢复广播属性；中断后可继续恢复。

旧版默认助手配置的升级会保留最早的原助手备份：设置显示“默认助手已配置，尚缺 ColorOS 按键适配”，再次开启即补齐厂商部分，不要求卸载 App 或先重配助手。**卸载 ETA、更换默认助手或主动移除 Root 前，应在此恢复原配置**；这两个厂商值可能持续保留，App 被删除后无法替用户恢复。

### 固件证据与适用范围

通过 [公开固件索引](https://fwatlas.com/oppo/oppo-find-x9-ultra/pma110-17-0-0-106-cn01-pma110-11-c-20-1200-202610051630) 定位官方 `gauss-compota-c-cn.allawnfs.com` OTA，读取其内嵌元数据和 system 分区。分区操作摘要及最终分区摘要校验一致；仓库不包含厂商二进制、完整反编译源码或临时下载凭证。

| 证据 | 值 |
| --- | --- |
| 版本 / OTA ID | `PMA110_17.0.0.106(CN01)` / `PMA110_11.C.20_1200_202610051630` |
| Android / 安全补丁 | 17、SDK 37 / 2026-09-01 |
| 系统指纹 | `OPPO/PMA110/OP61BDL1:17/CP2A.260605.016/B.30d6865-a9327b-aae421:user/release-keys` |
| system 分区 SHA-256 | `09cab6f8321edd0215a6cddd02a944d96221d050536da9c4f252fe3e0e563db8` |
| `oplus-services.jar` SHA-256（启用时校验） | `56a8ae52c2562d5da80c9737b1b04801cce4e6744d9efefc4c67b2270f9817a5` |
| `oplus-framework.jar` SHA-256 | `d6da8dd5cb56b780a98e1ab26c7284a974295eee0b8ad0107765c4949a62a36f` |

关键方法为 `SinglePowerKeyMonitor.OplusPowerKeyRule.onLongPress`、`PhoneWindowManagerExtImpl.sendBroadcastByCustomizeKey/sendSpeechMessage/startSpeech`，以及 `OplusDevicePolicyManagerService.getData/setData/removeData/checkPermission`。长按广播在厂商后续检查前发送，因此 ETA 也检查上述限制；不声称覆盖所有 OEM 专用按键模式。静态分析确认该路线存在，不能代替手机实际事件投递、Root 实现和 UI 唤起验证。

## 既有通用配置

- 用户显式开启后申请 Root，先保存原助手角色、`assistant`、`voice_interaction_service`、`voice_recognition_service` 和 `power_button_long_press` 的原值，再配置 ETA 为系统数字助理、原生长按行为为 `5`（默认数字助理）。
- 原生助手、助手面板及锁屏入口复用现有实现。通过短生命周期 `app_process` 查询角色和执行固定设置动作，不注入 system_server，不创建常驻监听服务，不依赖 Vector/LSPosed，不更改设备全局 hidden-api 策略。
- 助手角色通过 Root 身份调用系统角色接口，并等待其完成回调，避免 `cmd role` 五秒等待上限造成假失败。空回调也按结果不确定处理；辅助进程有自身 25 秒退出上限，App 关闭后不会无限等待 Binder。
- RoleController 可能连带改变语音识别服务，配置时将该项恢复为原值。未设置（null）与空字符串分别保存；恢复时分别删除键或写回空值。
- 本地 AtomicFile 记录原配置及已尝试的步骤。保存失败不开始系统写入；再次开启不会覆盖备份。已知失败尝试恢复，命令超时/结果不确定时不重放、不与可能尚在执行的系统角色变更竞争。
- “恢复原配置”先核对 Android 用户和配置归属。检测到其他助手或外部改动时停止覆盖，保留备份；中途 Root 失效、进程退出或恢复失败也保留备份，供用户恢复后重试。
- 设置变更不主动重启任何进程或设备，不停用/卸载小布，不修改短按锁屏、SOS 或电源组合键。永久设置可能跨重启保留；临时 Root 丢失后，需要重新取得 Root 才能恢复设置，不能保证自动恢复。

## 验证边界

2026-11-03 用户反馈：安装 d870d11 后配置完成，退出设置正常长按电源键仍打开小布；设置显示 `WAKE_NOT_SEEN`。因此该设备的原生替换未成功，不能再只写“等待验证”。现有 Hook 源码也明确使用厂商 `OplusSpeechHandler` 分支，原生默认助手设置不覆盖该 Hook 的职责。

上方新增适配针对这个已确认的分发差异。未收到新版真机结果前，不把旧失败改写为成功。e47 虚拟屏引擎恢复与此问题独立，本次不改引擎版本。

“配置已完成”只表示角色、设置及适配值已回读一致，**不代表手机已经成功唤起 ETA**。本轮没有修改厂商代码或添加底层按键拦截。

用户长按一次后，若确实出现 ETA 面板，可回到设置点击“确认已通过长按唤起 ETA”。确认同时要求配置仍匹配，以及配置后/本次开机内最近五分钟出现过新的 ETA 窗口；窗口记录本身不辨认硬件按键，因此界面明确标记“已由你确认”。普通唤起、打开对话页、仅配置成功不能自动通过验证。

框架仍连接时不允许开启或确认独立原生通道。框架连接状态不是 system_server 中旧 Hook 是否卸载的证明；本轮不负责卸载已注入的旧代码，也不要求通过重启清理它。既有长按软重启原因尚未唯一确定，本轮不宣称修复 Vector 或 ROM 崩溃。

## 手机验收

1. 覆盖安装新候选，使用当前未激活 Vector 的状态，从 ETA 设置打开本入口。旧配置可显示“尚缺 ColorOS 按键适配”；授权 Root 和悬浮窗后开启。成功应显示“配置已完成 · 长按唤起尚未验证”。若报检查失败/固件不匹配/策略不可用，保留错误，不反复开启或重新注入。
2. 退出设置，正常长按电源键一次。若打开 ETA 面板，返回设置确认；若仍唤起小布或没有反应，选择“恢复原配置”。这属于该 ROM 的原生按键通道尚未适配，不能把它当成已完成替换。
3. 检查关闭/恢复后原助手和原长按行为恢复。切换过其他默认助手的场景应停止覆盖，保留备份。
4. 锁屏唤起和息屏起手只在亮屏唤起正常之后按个人正常使用验证，不绕过锁屏；不测试关机、SOS 或超长按，不安排软/正式重启，不远程操作用户设备。

旧原生通道在上述设备已收到失败反馈；新增 ColorOS 适配真机未验证，其他 ROM 未验证。自动化覆盖备份、空值、角色副作用、固件匹配、策略权限失败、超时、Root 丢失、用户切换、外部配置变化、恢复顺序、接收器权限及唤起确认；自动化不能证明厂商物理按键兼容。

## 源码

- `NativeAssistantSetup.kt`：可逆事务与配置归属检查。
- `NativeAssistantRootMain.kt`：固定短命令协议，通用角色/设置操作和下述两个厂商值；使用当前 APK，不增加单独引擎 payload。
- `NativeAssistantSettings.kt`：备份、短期窗口证据、App 生命周期内串行设置操作。
- `NativeAssistantPreference.kt`：内置设置入口、状态、确认与恢复。
- `OplusPowerRoute.kt`：厂商适配的独立可逆事务、固件及配置归属检查。
- `OplusPowerRoot.kt`：固定属性、厂商策略读写、当前框架摘要核对。
- `OplusPowerRouteAndroid.kt`：原值日志、系统广播接收与本地事件诊断；沿用 `EtaVoiceInteractionService` 展示助手。
