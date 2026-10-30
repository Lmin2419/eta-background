# 后台虚拟屏：仓库核查与鸿蒙检测

2026-10-29。已通过用户连接的 GitHub 读取其私有仓库 `Lmin2419/eta-background`，未写入或发布到该仓库。

## 现有 Android 实现

参考固定提交 `8ae286a8993efaa4650426ae450ab4634f2b2354` 的 [VDriveWorker.java](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/etax/engine/src/VDriveWorker.java)，以及仓库 README。后台版明确要求 Android Root 与无障碍。

它使用 Android DisplayManager 创建虚拟显示，ImageReader 接收画面，通过隐藏 API 的 `setDisplayId` 给输入事件指定显示，并调用任务管理器或 `am start --display` 启动/迁移目标 App。主屏幕与后台屏分别管理几何、观察、输入和生命周期。代码不能直接在鸿蒙的 ArkTS/系统服务上运行。

可沿用的是屏幕身份隔离、观察引用、取消与资源释放、任务排队和同一 App 的串行使用规则。Android Java/隐藏 API、Root 引擎、输入注入实现和通知 PendingIntent 必须替换。此次没有复制其源代码或私有素材到鸿蒙工程。

## 鸿蒙证据与当前结论

| 环节 | 证据 | 当前判断 |
| --- | --- | --- |
| 创建虚拟屏 | SDK `display.createVirtualScreen(config)`，API 16 起，需要 `ohos.permission.ACCESS_VIRTUAL_SCREEN` | API 存在，但不是普通运行时弹窗权限 |
| 资格 | SDK 权限表为 `system_grant`、`system_basic`、`availableType:NORMAL`、`provisionEnable:true` | 需要对应签名资格；普通个人账号能否合法申请该资格需确认，不通过修改签名等级绕过 |
| 显示结果 | 虚拟屏配置需要 surfaceId；可指定 supportsFocus | 生成屏幕 ID 不证明第三方 App 会出现在该屏或能读取其画面 |
| 启动/迁移 App | Android `am --display` 不能直接用于鸿蒙 | 需验证目标 ROM 的 Ability/窗口服务，不虚构 `aa` 参数 |
| 截图与控件树 | 现有 UITest 执行器面向当前实际显示 | 需确认指定显示的观察接口，并使观察 ID、坐标、输入都绑定同一屏幕 |
| HDC 路线 | shell 与普通 App UID 权限不同 | 有调查价值；shell 能运行 `aa` 或枚举显示不证明完整后台屏闭环可用 |

因此 **0.4 没有发布“后台虚拟屏执行”功能**。在普通调试签名和现有主屏 UITest 工具下，不能把 Android Root 版本宣称为已移植。定位持续任务也不增加虚拟屏权限。

## 手机上怎样尝试

打开“设备 → 虚拟屏能力检测 → 检测本机虚拟屏能力”。无需模型 API。

1. 回读本 App 的 ACCESS_VIRTUAL_SCREEN 授权状态、系统 API 和当前可见显示列表。
2. 已连接本机 HDC 时，读取 `id`、`aa help`、`uitest help`、`hidumper -ls` 的结果；显示目录只保留 display/screen/window 条目，每项有超时和输出上限。
3. 报告是可选择复制的本机文本，不发给模型、不自动上传。

这次检测**不创建/迁移屏幕，不启动 App，不注入输入，也不读取屏幕内容**。没有获得应用虚拟屏权限时，不申请假的普通弹窗授权。收到实际系统报告后才能决定下一项可合法执行的实验；当前云端无法接触用户的手机，不能替代这一步。

可交付虚拟屏执行前，必须逐一证明：合法创建与回收、目标 App 启动/迁移、对应屏幕截图和节点、显示定向输入、锁屏/折叠/销毁时停止、主屏不受误操作影响。任一环节未验证，不向模型注册后台屏工具。

本地证据：HarmonyOS SDK 26.0.0.105 的 `@ohos.display.d.ts`（createVirtualScreen / VirtualScreenConfig）与 `toolchains/lib/PermissionDefinitions.json`（ACCESS_VIRTUAL_SCREEN）。商用 ROM 的实际授权仍以手机诊断为准。

## 0.5 可执行实验

新增独立的 [ACL 调试签名步骤](VIRTUAL_SCREEN_ACL.md) 与 XComponent surface 上的 createVirtualScreen/destroyVirtualScreen 实验。普通升级清单仍不声明受限权限；可选实验清单必须配合实际获批的 Profile。HDC 命令缺少退出标记或传输异常时清除连接状态并停止后续检查，不再输出四份同样的断线错误。尚未完成第三方应用迁移、目标屏采集或输入。
