# 随身助手 · 0.9 交互框架预览

这次按用户要求先保存旧源码，再重建页面与交互框架。参考自有 `Lmin2419/eta-background` 的页面组织，使用原生 ArkUI 实现。**当前是框架预览版，不是上线版，也不代表已经完成 Eta 的功能对齐。**

## 仓库进度与接手入口

- [当前进度、实现范围、已验证与未验证事项](docs/PROJECT_STATUS.md)
- [开发接手说明与构建入口](docs/DEVELOPMENT_HANDOFF.md)
- [15 张原生 UI 截图及 Eta 首页对照](docs/verification/2026-10-30/README.md)
- [重构前 0.8 源码归档](archives/README.md)

仓库中的源码包含截图检查后修正的布局；此前发出的 0.9.0 安装 ZIP 保持原样。若从仓库继续开发，以当前源码和上述进度说明为准。

## 现在可以走通的流程

- 首页 → 侧栏 → 新建、搜索、切换、重命名、删除对话。
- 侧栏或右上角菜单 → 模型服务列表 → 服务详情的“配置 / 模型”两页。
- 填写 API → 测试文本连接并获取目录 → 搜索、多选添加 → 保存服务。
- 聊天输入区单独选择已保存的模型，发送多轮文本，显示流式内容，停止并保留部分回复。
- 返回上一层、关闭弹层、切换会话都由明确的页面状态控制；编辑服务不改变当前聊天的模型。

手机操作、HDC 连接、后台定位、通知控制、虚拟屏、MCP、Skill、浏览器与电源键等旧入口已退出本版界面。旧实现保存在源码和独立归档中，后续逐项验证后再接回。普通聊天只申请网络权限；离开应用会停止当前回复。

## 安装与升级

已安装旧版：使用 `harmony-agent-0.9.0-upgrade.zip`，合并覆盖原工程的 `AppScope` 和 `entry` 后，同步项目、清理构建，再点 Run。升级包**不含两个 build-profile.json5**，保留你已经配置好的签名和原生构建方式。保持原包名和同一签名，不要先卸载应用。

完整工程：使用 `harmony-agent-0.9.0-deveco.zip`。DevEco 安装 API 26 SDK，打开工程，给 `com.example.harmonyagent` 自动生成名称为 `default` 的调试签名，然后 Run。此包自带旧原生代码的 ARM64 库，无需在 Windows 编译 OpenSSL。若签名名称不同，需要同步修改 `app.products[].signingConfig`。运行日志应安装 `signed.hap`，出现 `unsigned.hap` / `no signature file` 时先检查这个关联。

应用版本代码为 90000。编译 SDK 26，工程最低 API 20；模型网络适配要求实际运行系统 API ≥ 23。本包不包含个人证书、真实 API Key 或已签名 HAP。

详细步骤：[0.9 升级与核验](docs/UPGRADE_0.9.md)。首次启动仍读取同一应用沙箱的旧对话和模型配置，旧手机执行任务不会自动恢复执行。

## 开发与验证

```bash
npm ci
npm run check
npm test
export HARMONY_CLT_HOME=/path/to/command-line-tools
npm run build:harmony
npm run build:preview
npm run package:framework
npm run verify:package
```

`pages/Index.ets` 是生产入口，`pages/FoundationPreview.ets` 是 DevEco 预览入口。后者使用相同的 `AssistantShell`、聊天和模型组件，配合纯内存存储与模拟 API，不会连接真实服务或读取个人数据。可以在预览中添加示例模型、发送消息和点击停止。它不在生产 `main_pages.json` 中。

本轮通过 TypeScript 检查、193 项核心测试（其中 15 项专门覆盖新框架交互）与 API 26 HAP 编译。包含真实本地 HTTP/SSE 分片测试，但不能替代鸿蒙 HTTP 适配的真机测试。

**已取得原生画面：**在外部预览环境解决 Linux SDK 的加载兼容问题后，已用实际点击走通首页、流式回复、停止、侧栏、模型服务和多选导入流程，并保存 15 张竖屏 / 横屏截图。测试和导入后表单值保持不变。预览使用同一套 ArkUI 组件和模拟 API；真机键盘、系统栏、真实网络及持久化仍需单独验收。详见 [视觉验证记录](docs/VISUAL_VALIDATION_0.9.md)。这轮截图还修正了卡片圆角、图标裁切、发送按钮位置和短列表居中问题；此前已交付的 0.9.0 ZIP 未被覆盖，截图反映的是当前修正后的源码。

## 资料

- [新框架结构、Eta 来源和归档](docs/FOUNDATION_0.9.md)
- [本版验收清单](docs/ACCEPTANCE_0.9.md)
- [视觉验证与 Eta 对照](docs/VISUAL_VALIDATION_0.9.md)
- [第三方来源](THIRD_PARTY_NOTICES.md)

`docs` 中 0.5–0.8 的功能计划、83 项任务表和验收记录属于历史资料，不作为本版可用功能声明。
