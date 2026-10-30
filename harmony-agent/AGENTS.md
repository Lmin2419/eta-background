# AGENTS.md：随身助手（Harmony Agent）开发规则

本文件给在此目录工作的编码代理（Codex / A 总、Claude 等）阅读。任务清单见 [docs/ROADMAP.md](docs/ROADMAP.md)，当前状态见 [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md)。

## 项目是什么

HarmonyOS 原生 AI 助手，ArkUI + ArkTS。借鉴 Eta（Android）的页面组织与交互逻辑，但不移植 Kotlin / Android 代码。用户自带模型 API。目标设备：Pura X Max / HarmonyOS 7 / API 26。

仓库根目录是 Android Eta（Gradle）。**鸿蒙工程只在 `harmony-agent/`。** 不要修改根目录的 Android 代码、Gradle 配置或现有 `.github/workflows/pr-check.yml`、`etax-build.yml`。

## 目录

| 路径 | 内容 |
| --- | --- |
| `entry/src/main/ets/core/` | 纯 TypeScript 逻辑（可单测）。`core/foundation/` 是 0.9 新框架 |
| `entry/src/main/ets/ui/foundation/` | 新框架 ArkUI 页面 |
| `entry/src/main/ets/platform/` | 鸿蒙平台适配（RDB、ASSET、HTTP、通知、后台） |
| `entry/src/main/ets/pages/Index.ets` | 正式入口；`FoundationPreview.ets` 为预览入口 |
| `entry/src/main/cpp/` | 旧 HDC 原生桥（0.9 入口未加载） |
| `tests/` | `tsx --test` 单测 |
| `archives/` | 0.8 源码归档，只读 |

## 命令

```bash
cd harmony-agent
npm ci
npm run check   # tsc，只覆盖 core/**/*.ts 和 tests/**/*.ts
npm test        # 全部单测，必须 0 失败

# .ets 检查：首次下载公开的 OpenHarmony 7.0 SDK（API 26，免登录，约 3.4 GB，只保留 ArkTS 部分）
bash scripts/fetch-ohos-sdk.sh                      # 默认放到 ~/.cache/ohos-sdk-26
OHOS_SDK_ETS=~/.cache/ohos-sdk-26/ets npm run check:ets   # 必须 0 error
```

- `npm run check` 只覆盖 `core/**/*.ts` 和 `tests/**/*.ts`。`.ets` 用 `npm run check:ets` 检查：它调用 SDK 自带的官方检查器，做类型检查和 ArkTS 规则检查（例如 `arkts-no-any-unknown`），**但不生成 HAP**。
- 如果你的环境访问不了 `mirrors.huaweicloud.com`，PR 上的 CI 任务 `Harmony / ArkTS (.ets) check` 也会执行同样的检查，以它的结果为准。
- 已知误报只有 `Cannot find name '$$this'`（双向绑定语法，正式构建会预处理），脚本已经自动忽略。不要往误报列表里加别的东西来掩盖真实错误。
- 生成 HAP 需要 HarmonyOS 命令行工具（`HARMONY_CLT_HOME`，`npm run build:harmony`），通常没有。所以：逻辑仍然写在 `core/**/*.ts` 并写单测，`.ets` 保持薄；PR 中写明“ArkTS 检查通过 / 未生成 HAP”，最终以用户的 DevEco 编译为准。

## 架构规则（违反即视为缺陷）

- 页面只渲染 `FoundationController.snapshot()` 并发送意图；状态归属在 controller / runtime / editor，页面不维护第二份选择状态。
- 会话 ID、模型、正文、草稿来自同一会话投影。
- 模型配置页使用独立草稿；测试连接和获取目录不保存、不改变聊天模型。
- 离页、改地址、切换服务要作废旧请求；迟到的异步结果不能覆盖新状态。
- 发送前先持久化用户轮次，再访问网络；停止要保留已收到内容；不自动重发请求，不自动重放手机动作。
- 新入口不初始化旧 `AppRuntime`、HDC、后台服务或扩展。接回旧能力时一次只接一个，按 ROADMAP 任务卡执行。
- 旧代码只作为参考，从 `core/`、`platform/` 或 `archives/` 迁移需要的部分；不要整体恢复旧入口。

## 安全和权限

- 不提交 API Key、证书、`.p12`、`.p7b`、`.cer`、个人签名配置。`build-profile.json5` 中的 `signingConfigs` 保持为空，不要提交用户本地的签名改动。
- Key 只存 ASSET；日志、诊断报告、错误提示、通知中不得出现 Key、Authorization 头、完整消息正文。
- 新增 `module.json5` 权限必须在 PR 描述中单独列出并说明原因。不申请 `system_basic` / `system_core` 级权限，不使用 Root、Hook、系统签名或伪造定位。
- 没有经过真机验证的能力不注册为模型工具。

## 验证与文档的诚实规则

- 区分三种状态：**代码完成**、**自动测试通过**、**真机验证通过**。只有用户在真机上确认过的才能写“真机通过”。
- 每个任务完成时更新 `docs/PROJECT_STATUS.md` 中对应行；新能力在 `docs/device-runs/TEMPLATE.md` 中补充真机验收步骤。
- 不要删除或改写历史文档（`*_0.x.md`）的结论，只在新文档中引用。

## 版本

每次可交付的改动都升补丁号：同时修改 `AppScope/app.json5` 的 `versionName` / `versionCode`（`0.9.1` → `90001`），以及 `package.json` 的 `version`。界面上的版本文字不要硬编码。

## 分支与 PR

- 每个 ROADMAP 任务一个分支 `harmony/<任务编号>-<短名>`，一个 PR 到 `main`。不要直接推送 `main`。
- 界面文字、文档和 PR 描述使用简体中文。
- PR 描述包含：
  1. 改了什么（对应任务卡）；
  2. 自动验证：`npm run check`、`npm test` 的结果（测试数量 / 失败数）；是否执行了 HAP 编译；
  3. 新增权限（没有就写“无”）；
  4. **真机验证步骤**：用户在手机上逐步操作的清单和预期结果；
  5. 已知限制和未完成项。
