# 实施方案证据索引

日期：2026-10-29。源码固定在 `8ae286a8993efaa4650426ae450ab4634f2b2354`；私有仓库链接需要具有访问权的 GitHub 账号。下列是重点审阅入口，不表示每个大文件都已逐行审计。

## S：自有 Eta 源码

| 编号 | 固定版本文件 | 用于本方案的事实 |
| --- | --- | --- |
| S01 | [AgentExecutionService.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/agent/runtime/AgentExecutionService.kt) | 每任务通知、终态更新、最多三张完成卡、完成提醒、回复接纳、进度合并与 Android 三入口。 |
| S02 | [NotificationReplyRequest.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/agent/runtime/NotificationReplyRequest.kt) | 通知继续时重建上下文、校验源运行与配置、去重、创建新运行；不重放旧工具。 |
| S03 | [CompletedAppActivity.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/agent/runtime/CompletedAppActivity.kt) | 明确用户点击后的保留页面接管；忙碌/未知结果不重新启动 App。 |
| S04 | [RuntimeRunRegistry.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/agent/runtime/RuntimeRunRegistry.kt) | 执行上限 2、未结束上限 8、同会话写入互斥、替换与终态收尾。 |
| S05 | [AgentRuntimeService.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/agent/runtime/AgentRuntimeService.kt) | 息屏暂停、亮屏不自动恢复、运行调度与归档。 |
| S06 | [BackgroundScreenTools.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/agent/runtime/BackgroundScreenTools.kt) | 屏幕租约、容量、同 App 互斥、代次与清理确认。 |
| S07 | [AgentBackgroundToolCatalog.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/agent/model/AgentBackgroundToolCatalog.kt) | 能力真实可用后暴露工具；输入就绪、坐标系、截图依赖、完成后不自动接管。 |
| S08 | [ShadowSchema.java](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/java/com/eta/shadow/ShadowSchema.java) | 15 个 screen_* 工具契约和完成模式。 |
| S09 | [ActionEvidence.java](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/etax/shared/src/com/eta/shadow/ActionEvidence.java) | 输入接纳和页面变化分离；UNKNOWN/NO_OBSERVED_CHANGE；3 次无变化后拒绝；120 秒 TTL、64 项容量。 |
| S10 | [BackgroundScroll.java](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/java/com/eta/shadow/BackgroundScroll.java) | 目标滚动容器选择、歧义拒绝、相对内容证据、无进展记录。 |
| S11 | [VDriveWorker.java](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/etax/engine/src/VDriveWorker.java) | Android 虚拟显示、ImageReader、定向输入、任务迁移、保留与接管；并非跨平台实现。 |
| S12 | [AgentChatBody.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatBody.kt) | 底部锚定、键盘空间、消息列表、模型入口、输入材质和流式 UI。 |
| S13 | [AgentMessageQueueReducer.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentMessageQueueReducer.kt) | 排队/立即插话/撤回编辑；主动停止后保留队列且不自动发送。 |
| S14 | [ProviderConfigDraft.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ProviderConfigDraft.kt) | 模型配置编辑草稿与保存配置的类型与更新流程。 |
| S15 | [RemoteModelImportSheet.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/RemoteModelImportSheet.kt) | 只列未添加候选、默认不选、筛选全选、确认导入、关闭不修改；目录绑定连接。 |
| S16 | [ChatMessageItem.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/ui/components/ChatMessageItem.kt) | 流式 Markdown、思考折叠、消息展示。 |
| S17 | [TaskCompletionNotificationTest.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/test/kotlin/io/github/mangi/eta/agent/runtime/TaskCompletionNotificationTest.kt) | 完成三入口、提醒设置、锁屏不补提醒、删除后不复活等行为测试；本次未执行。 |
| S18 | [README.md](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/README.md) | 后台版当前概况、构建/测试说明；属于仓库记录，不代替本次实测。 |
| S19 | [README.md](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/etax/README.md) | 引擎说明和历史记录；必须区分现行策略与撤回的旧方案。 |
| S20 | [LICENSE](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/LICENSE) | PolyForm Noncommercial License 1.0.0。 |
| S21 | [AgentRunMessageProjector.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentRunMessageProjector.kt) | 运行事件到消息展示的投影与状态关联。 |
| S22 | [ConversationSidePaneScaffold.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/ui/components/ConversationSidePaneScaffold.kt) | 侧栏/会话内容的布局组织参考。 |
| S23 | [TaskNotificationDismissReceiver.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/main/kotlin/io/github/mangi/eta/agent/runtime/TaskNotificationDismissReceiver.kt) | 通知删除的 Android 接收入口；鸿蒙需按自己的事件机制适配。 |
| S24 | [NotificationReplyRequestTest.kt](https://github.com/Lmin2419/eta-background/blob/8ae286a8993efaa4650426ae450ab4634f2b2354/app/src/test/kotlin/io/github/mangi/eta/agent/runtime/NotificationReplyRequestTest.kt) | 通知回复上下文与拒绝条件测试来源；未在本次执行。 |

源码全文仅在本工作区参考目录提取，没有打入本次计划 ZIP；完整提取文件名和 Git blob SHA 见 [审阅清单](ETA_FORK_AUDIT.json)。

## H：本地 SDK 26.0.0.105

基目录：`/workspace/toolchain/command-line-tools/sdk/default/`。以下直接来自已安装 SDK 声明与权限定义，接口存在不等于你的签名资格已取得，也不等于目标设备已经验证。

### H01：普通通知与删除

文件：`openharmony/ets/api/notification/notificationRequest.d.ts`。

| 字段 | 证据位置 | 声明含义 |
| --- | --- | --- |
| isOngoing / isUnremovable | 131–146 行附近 | 两项注释均为 “Not supported currently.”；不能用于保证不可清除或持续运行 |
| updateOnly | 147–158 行附近 | API 18 起，已有通知才更新；不存在时失败，不新建。可与删除标记配合避免通知复活 |
| tapDismissed | 168–179 行附近 | false 保留点击后的卡片；true 点击通知或按钮后自动清除 |
| actionButtons | 292–298 行附近 | 手机最多两按钮；API 16 起穿戴设备最多三个，不适用于手机 |
| removalWantAgent | 423–430 行附近 | 删除触发；不支持跳转 UIAbility，只能发布公共事件 |
| isAlertOnce | 244 行附近 | 控制重复更新提醒；具体提示仍受系统设置约束 |

`getActiveNotifications` 是查询本应用活动通知的公开路线；不能据此宣称有全局通知读取权限。

### H02：通知用户输入

文件：`openharmony/ets/api/notification/notificationActionButton.d.ts`，65 行：`userInput?: NotificationUserInput`。
另见同目录 `notificationUserInput.d.ts` 的 `inputKey`。这提供可尝试的公开 API 路线，但不能仅由类型声明确认当前系统 UI、输入投递与冷启动行为。

官方参考：[NotificationUserInput](https://developer.huawei.com/consumer/cn/doc/harmonyos-references-V13/js-apis-inner-notification-notificationuserinput-V13)。旧文档用于解释字段，实际兼容性以 SDK 和目标手机为准。

### H03：HMS LiveViewKit

文件：`hms/ets/api/@hms.core.liveview.liveViewManager.d.ts`。

- `startLiveView`、`updateLiveView`、`stopLiveView` 是独立服务 API。
- 184 行附近错误码 `1003500005`：`The right of liveView is not enabled.`
- 394–432 行附近的 event 场景列表包含 `PROGRESS`，并明确具体场景需要申请对应权限/权益。
- 不应从 NotificationKit 某系统级 Live View 类型的限制，直接断言所有第三方应用均不能使用 HMS LiveViewKit。
- 不应从存在 PROGRESS 推断通用 AI 任务已经获准。需查询账号可申请场景、签名/服务配置并真机调用。

官方入口：[Live View Kit](https://developer.huawei.com/consumer/cn/sdk/live-view-kit)、[构建本地实况窗](https://developer.huawei.com/consumer/cn/doc/harmonyos-guides-v5/liveview-create-locally-V5)。后者还说明本地更新依赖应用进程；旧版本示例的场景枚举少于本次 SDK，不能用旧枚举覆盖 API 26 的声明。

### H04：悬浮球与悬浮视图

文件：`openharmony/ets/api/@ohos.window.floatingBall.d.ts`、`@ohos.window.floatView.d.ts`；权限证据在 `openharmony/toolchains/lib/PermissionDefinitions.json`。

| 权限 | grantMode | availableLevel | availableType | since | provisionEnable |
| --- | --- | --- | --- | --- | --- |
| USE_FLOAT_BALL | system_grant | system_basic | NORMAL | 20 | true |
| FLOAT_VIEW | user_grant | system_basic | NORMAL | 26.0.0 | true |

FLOAT_VIEW 的 user_grant 不代表普通运行时弹窗足够；system_basic 和 provision 条件仍需核验。应用内高斯模糊不依赖这两项跨应用窗口权限。

### H05：特殊后台任务

文件：`openharmony/ets/api/@ohos.resourceschedule.backgroundTaskManager.d.ts`，1346–1373 行及 BackgroundTaskSubmode。

SDK 对 `MODE_SPECIAL_SCENARIO_PROCESSING` 的场景说明为媒体文件导出、第三方组件投播等，要求主类型和子类型匹配；现有对应子类型含媒体处理、视频投播。API 24 起要求 `KEEP_BACKGROUND_RUNNING_SPECIAL_SCENARIO` ACL；更早使用的相应系统权限另有兼容规定。此类任务必须独立使用，通知不能组合。

`requestAuthFromUser` / `checkSpecialScenarioAuth` 提供申请与结果检查，但不替代 ACL 和场景资格。不能把普通模型网络请求直接包装成媒体处理任务。

PermissionDefinitions 中该权限为 system_grant / system_basic / NORMAL / provisionEnable=true / since=24。普通 KEEP_BACKGROUND_RUNNING 声明不自动包含这项资格。

### H06：虚拟屏

权限文件中的 `ACCESS_VIRTUAL_SCREEN`：system_grant / system_basic / NORMAL / since=16 / provisionEnable=true。显示相关声明在 `openharmony/ets/api/@ohos.display.d.ts`。

权限只覆盖其定义的显示 API 范围；第三方应用迁移、指定屏输入、定向读取、保留接管须分别验证。现有工程的可选 ACL 补丁只是权限请求，不是已经授予资格。

## L：本地鸿蒙 0.5 事实

| 编号 | 文件 | 本次核对的局限/已有能力 |
| --- | --- | --- |
| L01 | [Index.ets](../entry/src/main/ets/pages/Index.ets) | 页面持有 liveReply 和运行状态；aboutToDisappear 停止任务/后台、清理草稿；需要解耦 |
| L02 | [TaskNotification.ets](../entry/src/main/ets/platform/TaskNotification.ets) | 固定 ID 4001、通用 URI、当前按钮；使用 isOngoing 但 SDK 不支持其预期含义 |
| L03 | [taskNotice.ts](../entry/src/main/ets/core/taskNotice.ts) | 已有进度合并与终态后迟到更新抑制；没有每任务持久删除标记等完整语义 |
| L04 | [Services.ets](../entry/src/main/ets/platform/Services.ets) / [stream.ts](../entry/src/main/ets/core/stream.ts) | 0.5 已有真正 requestInStream 与 SSE 解析，不应再标“未实现流式” |
| L05 | [conversations.ts](../entry/src/main/ets/core/conversations.ts) | JSON 快照、会话/步骤、容量与恢复边界；需增量检查点及长期存储设计 |
| L06 | [VALIDATION.md](VALIDATION.md) | 112 项核心测试与构建记录；没有目标手机 0.5 复测证据 |
| L07 | [ETA_TOOL_INVENTORY.csv](ETA_TOOL_INVENTORY.csv) | 历史 129 项工具功能目录，非本次全部完成证明 |
| L08 | [Background.ets](../entry/src/main/ets/platform/Background.ets) | 短时/定位持续任务路线，不能直接等价为通用长期后台资格 |
| L09 | [VirtualScreenProbe.ets](../entry/src/main/ets/platform/VirtualScreenProbe.ets) | 能力检查及创建释放实验；未实现第三方 App 虚拟屏闭环 |

SDK 文件与权限片段的 SHA-256 另存于 [SDK 审阅记录](ETA_PARITY_SDK_EVIDENCE.json)。后续 SDK/ROM 升级应重新核验限制，避免旧判断被永久写死。

