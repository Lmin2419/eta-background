# 0.9：先搭框架，再逐项接回能力

## 已冻结旧实现

改动前曾在当时的云端工作区 `/workspace/snapshots/harmony-agent-before-ui-rebuild-20261030` 保存完整快照。该容器已回收，快照不再可用；长期备份以仓库中的 `archives/harmony-agent-0.8.0-archived-source.zip` 为准。

供下载的精简源码归档是 `harmony-agent-0.8.0-archived-source.zip`，含 163 个源码、配置、测试和文档文件，另附清单。它排除构建缓存、下载依赖、证书和本地配置。SHA-256：`367fec9bc460be82540975498de97a9d8d60d1568263af61dea63703467ef1c7`。旧完整 DevEco 包与其他 0.8 交付也保留。

## 页面与 Eta 的对应

参考自有仓库 `Lmin2419/eta-background`，commit `8ae286a8993efaa4650426ae450ab4634f2b2354`。以下是交互与状态结构的移植，平台实现为新的 ArkUI / TypeScript；不把 Kotlin、Compose 或 Android 服务塞进鸿蒙应用。

| Eta 来源 | 采用的交互 | 本版实现 |
| --- | --- | --- |
| AgentAppRoot.kt | 独立页面栈，入口与运行逻辑分离 | foundation/navigation.ts、controller.ts、AssistantShell.ets |
| AgentHomeScreen.kt | 页面发送意图，运行状态由外部持有 | Index.ets + FoundationHost.ets |
| ConversationSidePaneScaffold.kt | 窄屏抽屉、遮罩关闭、会话和设置入口 | AssistantShell.ets；宽屏采用常驻侧栏 |
| AgentChatBody.kt | 固定消息视口，用户上滑暂停跟随，切换恢复位置 | ChatPage.ets |
| AgentChatInputBar.kt | 输入草稿与会话身份分离，发送/停止互斥 | ChatComposer + controller.draft/send/stop |
| ModelProviderListScreen.kt | 服务列表、搜索、独立详情页 | ProviderListPage |
| ModelProviderDetailScreen.kt、ProviderModelsTab.kt | 配置与模型两页，不同于聊天模型选择 | ProviderDetailPage |
| RemoteModelImportSheet.kt | 搜索、多选、全选当前结果、确认导入、取消不改列表 | ModelSelectionSheet + ProviderEditor |
| docs/Screenshots/chat_home.jpg | 顶部菜单、中心标题和四张建议卡片、底部输入区 | ChatPage 空态与 AssistantShell 顶栏 |

首页卡片仅填入草稿，不自动发送。示例内容改为写作、梳理、翻译、解释，符合本版文本范围。附件、手机执行、Atom/工具模式等入口未提供。

## 边界和状态归属

```
EntryAbility / Index
    ↓
FoundationHost：RDB / ASSET / HarmonyHttpTransport
    ↓
FoundationController：页面意图 → 已提交的会话、模型、编辑状态
    ├─ ShellNavigation：页面栈、抽屉、弹层
    ├─ ProviderEditor：独立草稿、异步请求代次、目录暂存
    └─ FoundationChatRuntime：每会话运行、取消、流式状态
    ↓
AssistantShell → ChatPage / ProviderPages
```

页面不初始化旧 AppRuntime、HDC、后台服务或扩展管理器。保留的旧源码暂不接入新的入口。`ModelHttp.ets` 从混合 HDC 的 Services 中拆出，文本网络路径不加载原生 HDC 模块。

会话的已提交 ID 是唯一选择来源；页面不保存另一个稍后同步的 ID。每个会话有自己的输入、模型、消息列表和滚动位置。输入按版本回填，不被每次流式刷新覆盖；异步发送只清除它真正提交的那一版草稿。

模型编辑页有自己的草稿和请求代次。离开或修改地址会作废旧请求，迟到的结果不能弹出其他服务的模型列表。测试连接与获取目录不执行保存，也不改变聊天选择。真正改变地址才清除旧 Key；相同值的 ArkUI 回传不会清除。

模型目录暂存于选择弹层，取消不改原列表，确认后写入编辑草稿，点“保存服务”才持久化。文本测试与目录错误分开显示：文本可用而 `/models` 不可用时，仍可手动输入模型 ID。

运行先持久化用户轮次，再读取密钥、访问网络。最多两个会话同时回复，一个会话只有一个运行；离开应用主动停止，返回不自动重发。停止或失败保留已收到文字。当前运行按 run ID 合并到对应消息，切换会话不会串流。

## 验证范围

新框架 15 项集成测试覆盖页面返回、会话身份、草稿、模型编辑隔离、目录确认/取消、迟到请求、保存失败、流式会话切换和取消。加上旧核心回归共 193 项。真实本地 HTTP 服务验证分片 SSE 在完整响应结束前进入页面状态，并在最终回复后持久化；UI 像素、Harmony HTTP 回调和 RDB 真机行为不由这个测试证明。

已经编译原生 HAP 和原生预览入口。原生预览的环境阻塞已排除，取得 15 张竖屏 / 横屏截图及实际点击流程记录，见视觉记录。模拟 API 的原生界面验证不替代手机系统、真实网络或存储验收。本版只标为“交互框架预览”，不沿用历史 83 项任务表来计算上线完成率。

## 接回旧能力的条件

先通过本版核心真机清单，再一次只接一个能力。每个能力需要明确入口、加载/进行中/成功/失败/取消状态、离页语义、权限失败回退、验证记录；不能只增加开关。通知与后台等能力要单独验证系统回调和进程生命周期，不能用纯状态单测作为完成证据。
