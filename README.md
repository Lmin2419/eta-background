# Eta 后台版

[简体中文](README.md) · [English](README_EN.md) · [版本记录与源码导航](etax/README.md) · [构建说明](.github/RELEASING.md)

**Eta 后台版是 [Eta](https://github.com/Mangi-11/Eta) 的衍生分支，基于上游 v3.3.5。原作者为 [蛮吉（Mangi-11）](https://github.com/Mangi-11)。** 本仓库维护后台虚拟屏、多任务与通知交互等扩展，非上游官方发行版。内部工程名沿用 `Etax`，安装包名为 `io.github.mangi.etax`，可与官方 Eta 共存。

## 最新测试版本

当前源码新增 `2026103003` / `20261030-e40-takeover` 的执行中前台接管暂停保护，包含虚拟屏回收、交还确认及超时修复，正在验证，见[本次更新](etax/README.md#release-2026103003)。以下为上一份已验签测试包。

**2026-10-30 · `3.3.5+etax.3b1794d` · versionCode `2026103001`**，配套引擎为 `20261029-e38-input`。

本次修复联网搜索与悬浮窗设置入口：内置搜索优先使用必应，失败时尝试 DuckDuckGo，每个来源的网络超时预算为 10 秒；返回具体失败原因。悬浮窗权限已授权后仍可点击设置，系统缺少专用授权页时提供备用入口和操作指引。

任务完成提示、三个独立操作入口及提示开关继续保留，详见[完成提示使用说明](etax/README.md#completion-notice-guide)。

**[下载签名测试 APK](https://github.com/Lmin2419/eta-background/actions/runs/41024159901/artifacts/12963208802)** · [对应源码 3b1794d](https://github.com/Lmin2419/eta-background/tree/3b1794d70881c3c849b53d7778dd2bfdf0a46cc5) · [详细更新与校验值](etax/README.md#release-2026103001)

该构建的 2,086 项回归测试中，2,085 项通过、1 项跳过，失败及错误均为 0；另有 8 项引擎部署测试通过，lint 无错误。APK 签名与此前后台版一致，可覆盖安装；包内引擎已与源码核验。开发环境已验证实际必应搜索结果解析，手机网络和 ColorOS 17 授权页仍待真机复测。下载需登录 GitHub，该附件将于 **2026-11-29（UTC）** 到期。

## 我们新增与改进的功能

| 功能 | Eta 后台版的实现 |
| --- | --- |
| 联网搜索与权限入口 | 内置必应搜索及 DuckDuckGo 备用来源、有界等待与错误分类；悬浮窗授权入口始终可点击，并兼容系统备用页面。 |
| 前后台选择 | 当前页面与当面操作使用前台工具；明确要求后台执行或续接后台任务时使用虚拟屏。 |
| 后台操作 App | Root 虚拟屏承载目标 App；每个任务独立管理屏幕、观察结果与输入状态。 |
| 操作核验 | 前后台能力统一登记；后台检查窗口就绪，滚动明确选择目标，区分动作提交与界面变化，并拦截重复无进展操作。 |
| 双任务与队列 | 最多两个任务执行、两块执行或保留中的虚拟屏；其他请求排队，同一 App 串行使用。 |
| 实时会话 | 长按电源键提交后即可在 ETA 会话看到运行记录，退出助手面板不取消任务。 |
| 原生任务通知 | 每任务独立状态卡、流式进度、暂停与停止；完成状态在原卡片更新。支持的系统可将其显示为流体云/Live Updates。 |
| 任务完成提示 | 任务结束时在原卡片上请求一次系统顶部横幅；提供“打开 ETA 会话”“打开 App”“继续指令”。可在外观设置关闭弹出提示，任务通知仍保留。 |
| 通知栏继续指令 | 原生内联输入直接提交到原会话，无需打开聊天；另有“打开 ETA 会话”入口。 |
| 完成后接管 App | 停止截图采集并保留页面，点击结果卡请求将原有任务移回前台；确认旧屏为空后释放。 |
| 横屏、截图与节点 | 统一屏幕身份、逻辑尺寸和方向；截图坐标带帧引用，错误原因可诊断；无节点页面可使用截图坐标工具。 |
| 助手界面 | 可在外观设置开关的边缘流光、助手面板退出与拖动过渡、会话阅读位置恢复。 |
| 稳定性策略 | 息屏暂停、解锁后手动继续；不使用后台唤醒锁维持息屏执行，不为铺满屏幕强制重启目标 App。 |

上游 v3.3.5 的子智能体、独立子模型、过程详情、工具摘要和 Debian 环境恢复已合入。后台版仍限制两块虚拟屏；子智能体不能借用父任务的后台屏幕工具。详见[版本对比与更新记录](etax/README.md)。

## 使用与兼容性

- Android 13+、ARM64。后台虚拟屏操作需要 Root，并开启 Eta 的无障碍服务；模型服务由使用者配置。
- 设置系统默认数字助理后，可通过长按电源键唤起；具体入口取决于手机系统。
- 当前真机反馈主要来自 OPPO Find X9 Ultra / ColorOS 17。流体云布局由系统决定，其他 ROM 的通知与跨屏行为需要实测。
- 已完成页面与执行页面合计最多两块，容量不足时可能回收已完成页面。Android 内存回收、游戏自身行为或跨屏配置变化仍可能导致第三方 App 重建；不承诺所有游戏都能无缝接管。
- 息屏持续运行已撤回。早期“三屏、第四项替换最早任务”和“一分钟销毁已完成屏”的策略也已替换，当前规则以上表为准。

## 获取源码与安装包

`main` 保存当前完整源码。旧版分支保留用于查看历史；不在仓库重复存放 APK、编译缓存或源码副本。

| 版本 | 源码 | 更新内容 |
| --- | --- | --- |
| 3.3.5 后台版 · 2026103001 | [3b1794d](https://github.com/Lmin2419/eta-background/tree/3b1794d70881c3c849b53d7778dd2bfdf0a46cc5) | 最新签名测试包：内置搜索容错、网络错误分类及悬浮窗设置入口修复。 |
| 3.3.5 后台版 · 2026102902 | [70844e7](https://github.com/Lmin2419/eta-background/tree/70844e71e25c9234b5075db1ca43c7e113dd0162) | 任务完成顶部提示、三个独立入口、提示开关及结束通知去重。 |
| 3.3.5 后台版 · 2026102901 | [f8bd297](https://github.com/Lmin2419/eta-background/tree/f8bd2975ce9d98970aac2e8ad04511a467e0a59b) | 后台能力统一、窗口就绪与操作效果核验；包含此前的部署恢复、前台操作及流光开关改进。 |
| 3.3.4 后台扩展历程 | [历史记录](https://github.com/Lmin2419/eta-background/commits/etax-3.3.4-background-multitask) | 多任务、原生通知、双屏稳定性、接管与几何修复。该开发分支之后已继续合入 3.3.5。 |
| 3.3.4 早期版 | [etax-3.3.4](https://github.com/Lmin2419/eta-background/tree/etax-3.3.4) | 后台引擎源码化、通知与电源键入口接线。 |
| 3.3.3 早期版 | [etax-3.3.3](https://github.com/Lmin2419/eta-background/tree/etax-3.3.3) | 初版后台扩展及 Root 引擎内嵌、自启。 |

在 [Actions → Etax Build](https://github.com/Lmin2419/eta-background/actions/workflows/etax-build.yml) 的成功构建中下载 APK。Actions 附件需要登录且会过期；长期分发可由维护者上传到本仓库 Releases。自行构建与签名见[构建说明](.github/RELEASING.md)。官方 Eta 的安装包不包含本分支的后台扩展。

## 源码与测试

Android 应用位于 `app/`；后台桥接在 `app/src/main/java/com/eta/shadow/`；Root 引擎在 `etax/engine/`，共用几何与操作证据协议在 `etax/shared/`。`app/src/test/` 保留上游和后台版回归测试，`etax/engine/tests/` 覆盖引擎部署脚本，防止并发、生命周期、通知回复、输入核验与部署问题回归。

问题反馈请说明版本、手机/系统、复现步骤和实际结果。分享诊断前请移除个人聊天、账号与密钥。

## 上游与许可

感谢 **蛮吉（Mangi-11）** 及 [Eta 上游贡献者](https://github.com/Mangi-11/Eta/graphs/contributors)。原始项目：[Mangi-11/Eta](https://github.com/Mangi-11/Eta)；本分支维护：[Lmin2419/eta-background](https://github.com/Lmin2419/eta-background)。

本项目沿用 **[PolyForm Noncommercial 1.0.0](LICENSE)**，保留上游版权与 Required Notice。源码可按该许可证使用、修改和分发，商业用途不在其一般授权范围内。这是带非商业限制的源码公开项目，不是 MIT/Apache 或 OSI 定义的开源许可。第三方组件继续遵循各自许可证。

Required Notice: Copyright © 2026 蛮吉 (Mangi-11).

## 鸿蒙助手开发进度

鸿蒙原生工程、当前交互框架、重构前源码归档、验收说明和 UI 截图已保存到 [harmony-agent/](harmony-agent/README.md)。从 [鸿蒙项目总览](HARMONY.md) 查看当前范围与开发接手说明；目前为 0.9 交互框架预览。
