# 虚拟屏调试 ACL 实验

当前结论：HarmonyOS SDK 有创建虚拟屏 API，但你的截图显示本 App 没有 ACCESS_VIRTUAL_SCREEN。截图中的 HDC 命令也全部中断，因此不能据此判断 shell 是否能完成后台屏操作。

权限属于 system_grant / system_basic，SDK 标注 availableType NORMAL、provisionEnable true；普通应用存在通过 Profile ACL 获得受限权限的机制，但是否向你的账号、应用和场景开放，需要华为签发结果确认。系统权限设置页没有“一键打开”此权限。

## 先尝试正规的调试签名

1. 先用普通 0.5 升级包安装，确认聊天与模型流程。保存一份当前已能安装的工程。
2. 在工程 `entry/src/main/module.json5` 的 `module.requestPermissions` 数组中增加：

   `{ "name": "ohos.permission.ACCESS_VIRTUAL_SCREEN" }`

   也可使用独立的 `harmony-agent-0.5.0-virtual-screen-acl.zip` 合并这份清单。它只用于本实验；如果你自己改过清单，优先手动加入上面一项，保留自己的其他设置。
3. 回 DevEco 的签名配置页，让自动签名按新清单重新申请/生成调试 Profile，点击应用、确定，再同步、清理、Run。**沿用旧 Profile、仅在清单写权限，不会得到授权。** 华为文档允许调试阶段由 DevEco 自动申请受限权限，但具体权限及账号是否可获批以实际结果为准。
4. 若自动签名不支持、提示 ACL 受限或安装报授予权限失败，在华为开发者中心 → ID、证书和 Profile → Profile 新建调试 Profile，查看“受限 ACL 权限”是否提供该权限；未批准时通过“去申请”提交真实的设备自动化实验场景。审批后新建含此 ACL 的 Profile，在 DevEco 配置对应证书和 Profile。不要篡改证书、apl 或伪造签名等级。
5. 如果账号无法申请，恢复原清单重新签名，继续使用主屏助手。不能承诺普通个人账号一定取得权限。

## 手机上的实测

设备 → 虚拟屏能力检测：

- 先点检测，确认“应用虚拟屏权限：已取得”。
- 点“实测创建与释放（需 ACL）”。程序创建 320 × 180 的无焦点虚拟屏，绑定本 App 的 XComponent surface，记录返回 ID 后销毁自己创建的屏幕。
- 报告显示创建与释放是否成功。遇到释放错误可点“释放实验屏幕资源”重试；不会操作主屏 ID，不会移动第三方应用。
- 这一步不调用模型，不读取第三方屏幕，不执行点击。普通 0.5 清单未声明此权限时，按钮会明确提示未获 ACL，不冒充权限申请成功。

创建成功后还需验证：独立显示模式、第三方 Ability 启动/迁移、对应屏幕的截图/控件树，以及带 displayId 的输入。现有主屏 UITest 工具不能直接假定适用于虚拟屏；验证完成前不注册后台屏工具。

参考官方资料：

- [申请受限权限](https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/declare-permissions-in-acl)
- [申请调试 Profile](https://developer.huawei.com/consumer/cn/doc/doccenter-getting-started/agc-help-debug-profile-0000002248181278)
- SDK 26.0.0.105：@ohos.display.d.ts（createVirtualScreen、destroyVirtualScreen、VirtualScreenConfig）；PermissionDefinitions.json（ACCESS_VIRTUAL_SCREEN）。

云端没有你的开发者登录、设备签名或手机连接，未替你申请权限，也未把编译成功当作实机创建成功。
