# 鸿蒙助手：源码、进度和验证资料

当前源码版本为 **0.9.1 交互框架预览**。先建立清晰的聊天、会话、模型服务和页面导航，再逐项接回设备执行等能力；不是已经完成 Eta 全功能对齐的上线版。

- [推进方案与任务卡（ROADMAP）](harmony-agent/docs/ROADMAP.md)，编码代理规则见 [AGENTS.md](harmony-agent/AGENTS.md)
- [鸿蒙工程源码与构建说明](harmony-agent/README.md)
- [本地开发与真机测试（Windows + DevEco）](harmony-agent/docs/LOCAL_DEVELOPMENT.md)
- [当前进度：已实现、已验证和待真机验证](harmony-agent/docs/PROJECT_STATUS.md)
- [开发接手说明](harmony-agent/docs/DEVELOPMENT_HANDOFF.md)
- [15 张原生截图、模拟交互与 Eta 首页对照](harmony-agent/docs/verification/2026-10-30/README.md)
- [当前验收清单](harmony-agent/docs/ACCEPTANCE_0.9.md)
- [重构前 0.8 源码备份](harmony-agent/archives/README.md)
- [历史详细实施方案](harmony-agent/docs/ETA_PARITY_IMPLEMENTATION_PLAN.md)

原生页面和点击验证使用相同的 ArkUI 组件及模拟模型服务。TypeScript 检查、193 项核心测试、API 26 HAP 和 PreviewBuild 已通过。真机系统栏、输入法、真实模型 API、后台能力与签名安装仍须分别验收。

鸿蒙工程在独立的 `harmony-agent/` 目录；根目录 Gradle 工程继续属于 Android Eta。DevEco 应打开 `harmony-agent/`，不要打开 Android 工程作为鸿蒙工程。

更新：2026-10-30。当前源码含最新截图检查后的布局修正；之前交付的 0.9.0 ZIP 没有被覆盖。
