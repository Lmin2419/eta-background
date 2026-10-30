# 开发接手说明

先读 [当前进度](PROJECT_STATUS.md)，再读 [框架结构](FOUNDATION_0.9.md)；后续任务见 [ROADMAP](ROADMAP.md)，编码代理规则见 [AGENTS.md](../AGENTS.md)，Windows 真机测试流程见 [LOCAL_DEVELOPMENT](LOCAL_DEVELOPMENT.md)。项目处于 0.9 交互框架预览阶段；不要从旧功能表推断当前可用入口。

## 仓库目录与入口

本工程位于 Eta 仓库中的 `harmony-agent/`，执行 npm、Hvigor 和打包命令时先进入该目录。仓库根目录的 Gradle 工程是 Android Eta，不是鸿蒙工程。

| 路径（相对此工程） | 用途 |
| --- | --- |
| `entry/src/main/ets/pages/Index.ets` | 正式页面入口 |
| `entry/src/main/ets/platform/FoundationHost.ets` | 正式 RDB / ASSET / HTTP 依赖装配 |
| `entry/src/main/ets/core/foundation/` | 页面栈、控制器、服务草稿、会话运行、预览数据 |
| `entry/src/main/ets/ui/foundation/` | AssistantShell、聊天、模型服务和弹层 ArkUI 组件 |
| `entry/src/main/ets/pages/FoundationPreview.ets` | 同一套组件的交互预览；内存数据，不在生产页面路由内 |
| `entry/src/main/ets/platform/ModelHttp.ets` | 新框架模型网络适配，避免加载 HDC |
| `tests/foundation.test.ts` | 新框架 15 项集成测试，包括本地 HTTP / SSE |
| `docs/verification/2026-10-30/` | 15 张原生截图、点击轨迹、构建及测试日志 |
| `archives/` | 0.8 重构前源码归档与 SHA-256 |
| `scripts/` | 依赖准备、原生构建、预览构建、导出和产物校验 |

## 本地检查和构建

需要 Node.js、npm、Python 3，以及带 API 26 的 HarmonyOS Command Line Tools。旧原生库构建还使用 CMake / SDK C++ 工具链及 `native-sources.json` 中固定版本的开源依赖。没有把 SDK 或下载缓存提交到仓库。

```bash
cd harmony-agent
npm ci
npm run check
npm test
export HARMONY_CLT_HOME=/absolute/path/to/command-line-tools
npm run build:harmony
npm run build:preview
npm run package:framework
npm run verify:package
```

0.9.1 起 `entry/build-profile.json5` 不再编译 C++，`build:harmony` 只构建 ArkTS；原生 HDC 库在 ROADMAP T10 以预编译文件方式接回。导出脚本要求先有当前版本 HAP。不要拿旧的同版本 HAP 配新源码来导出。`build-profile.json5` 在仓库中不含个人签名，构建产物默认未签名。

生成的 `*-source.zip` 是源码工程；`*-deveco.zip` 含预编译 ARM64 库，适合 Windows DevEco 使用；`*-upgrade.zip` 只覆盖应用源码和资源，保留用户现有构建和签名配置。生成物保存在被忽略的 `dist/`。当前仓库代码比此前发送的 0.9.0 ZIP 更新；后续发布应使用新补丁版本，避免同名产物混淆。

安装与签名细节见 [UPGRADE_0.9](UPGRADE_0.9.md)。已经有签名的旧工程优先使用升级包；同包名和同签名覆盖安装。仓库源码本身的签名数组为空，不能直接作为已签名安装包。

## 状态规则

- 会话 ID、模型、正文和草稿必须来自同一会话投影，不在页面里再维护第二套选择状态。
- 模型配置页使用独立草稿。测试连接和获取目录不等于保存，也不改变聊天模型。
- 搜索 / 勾选只改目录弹层暂存，确认导入改编辑草稿，保存服务才持久化。
- 离页、改地址或服务切换必须作废旧请求；旧返回不能覆盖新表单或打开不相关弹层。
- 发送先持久化用户轮次，再访问网络；取消要保留已收到内容，迟到结果不复活任务。
- 按会话和 run ID 合并流式内容；用户上滑时不强行追到底部。
- 新入口不初始化旧 AppRuntime / HDC / 后台服务。接回旧能力时单独验收，不能只恢复开关。

## 原生预览的再现范围

DevEco 打开 `FoundationPreview.ets` 并开启交互预览即可使用同一套组件和示例服务。这个入口不访问真实模型，也不读取个人密钥。

本轮云端 Linux Previewer 的兼容诊断源码另存 [scripts/preview-diagnostics](../scripts/preview-diagnostics/README.md)，包括真实鼠标点击和截图采集。它依赖本轮 SDK 的 ABI 和环境路径，并非跨 SDK 的通用启动器。正式应用不依赖这些兼容文件。

截图证明相应状态可渲染、相应原生点击可触发。它们不能证明真机输入法、系统栏、后台保活、ASSET 和真实 HTTP 行为；这些项目仍按 [验收清单](ACCEPTANCE_0.9.md) 执行。

## 归档和下一次交付

重构前源码 ZIP 已保留，后续需要旧实现时从归档提取相关文件，先比较状态和依赖，再迁移到新框架。不要整体覆盖新入口。

下一次交付前更新进度、验收结果、版本号和打包校验。记录失败和未测项，不把编译通过、模拟服务通过写成真实手机通过。截图和构建证据要标清对应源码及服务类型。
