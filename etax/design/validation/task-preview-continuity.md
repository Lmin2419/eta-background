# 后台任务条预览入口连续性 · 2026110701

## 反馈与范围

用户确认：消失的是聊天输入框上方、带应用图标的任务条；从任务条/预览窗点暂停，停止也可能发生；来自上一版本。基于远端 main `2c43814`（2026110617）排查，GitHub API 已核对 main，原生 fetch 本次遇到连接重置。

只改 `AgentExecutionService.acquire` 的完成卡片回收条件、`onDestroy` 的活跃卡片清理条件，及 `ExecutionLeaseRegistry.contains` 查询。没有改模型请求、运行调度、预览窗取帧、接管/交还、CompletedAppActivity 或引擎。手动暂停不自动恢复。

## 先复现，再修改

| 路径 | 修改前观察 | 修复/保留行为 |
| --- | --- | --- |
| 停止后台任务 → 释放租约 → 发下一指令 | `acquire` 的新批次清掉全部完成卡片，旧后台预览入口丢失 | 只清理非后台完成卡片；后台卡片由原来的上限/显式关闭策略回收 |
| 旧 FGS 已 stopSelf → 新任务取得租约并发布卡片 → 旧 FGS onDestroy | 租约正确存活，但清卡逻辑误删新任务 | 清卡与租约一致：仅删除没有存活 `run:<runId>` 租约的活跃卡片 |
| 暂停 → 再发布进度/另一任务 | 原测试通过，没有复现自动丢失 | 继续保持暂停、应用标识及预览入口；不冒称已完整复现用户手机上的纯暂停路径 |

初始 `TaskPreviewEntryContinuityTest`：Android API 33、36 合计六项，修改前四项断言失败、两项通过；只改上述生产代码后六项通过。不是编译错误充当红灯。

随后补齐：旧 owner 自身卡片仍清理、替换 owner 先创建、非后台完成卡仍回收、后台完成历史最多三条、真实 runtime 暂停 action 后新任务发布，以及浅深色手指点击打开预览和显式关闭。

## 自动验证

本地相关回归共 **130 tests / 0 failures / 0 errors / 0 skipped**。`lintDebug` exit 0（无 Error/Fatal）；补齐 Scaffold 容器后的浅深色渲染两项再次通过。覆盖任务入口、租约、通知、完成提醒、预览窗、Mapper、后台恢复、屏幕开关、消息排队及侧栏。

```sh
./gradlew :app:testDebugUnitTest \
  --tests '*TaskPreviewEntry*Test' --tests '*ExecutionLeaseRegistryTest' \
  --tests '*AgentExecutionServiceTest' --tests '*TaskPreviewControlTest' \
  --tests '*TaskPreviewActivityTest' --tests '*TaskAndSidebarPresentationTest' \
  --tests '*AgentRuntimeScreenOnTest' --tests '*AgentMessageQueueReducerTest' \
  --tests '*TaskSnapshotsTest' --tests '*AgentSystemNotificationTest' \
  --tests '*TaskCompletionNotificationTest' --tests '*Stage81*Test' \
  --tests '*BackgroundScreenRecoveryTest'
./gradlew :app:lintDebug
```

Windows 加 `-Pandroid.overridePathCheck=true`。保留失败日志：首次渲染测试资源限定符顺序错误，改正后再跑；扩大回归暴露旧测试仅 `cancelAll()`、未发送删除通知事件造成的跨用例残留。新增 **仅测试使用**的清理函数，通过原有 `onNotificationDismissed` 清理完成卡；不删、不跳过、不削弱原有通知断言。全量 Linux CI、签名 APK 与 Release 结果以该提交工作流为准，未完成前不得宣称发版成功。

## 原生组件截图

这四张是 Robolectric 原生组件渲染，不是真机虚拟屏截图。指针测试发送 MotionEvent DOWN/UP，断言启动 TaskPreviewActivity 且 run_id 正确；真实第三方应用图标及页面内容仍待手机验证。

| 状态 | 浅色 | 深色 |
| --- | --- | --- |
| 暂停后仍可打开 | [浅色](task-preview-continuity/preview-entry-paused-light.png) | [深色](task-preview-continuity/preview-entry-paused-dark.png) |
| 停止并发新指令后仍可打开 | [浅色](task-preview-continuity/preview-entry-stopped-light.png) | [深色](task-preview-continuity/preview-entry-stopped-dark.png) |

## 真机验收

安装 `Pangxiaopang-3.3.5-2026110701.apk`，不清除 App 数据。

1. 对话中明确让助手在后台打开某 App，确认输入框上方出现带应用图标的任务条，点开能看到虚拟屏。
2. **任务条点暂停**，再发普通指令：任务仍暂停，新消息按原规则排队，任务条不消失，仍能点开预览；未点继续前不能自行操作。
3. **预览窗内点暂停/接管再关闭**，回聊天发送指令：入口仍在；手动交还后才能继续。不要把输入栏长按“打断并发送”当普通排队。
4. **停止旧任务 → 立即发新的后台指令**，重复几次：旧任务入口不提前消失；新任务实际打开后台 App 后应显示自己的入口，并能打开预览。多任务时先展开汇总条。
5. 若旧页面已被接管、显式释放或容量回收，预览应如实显示“页面已释放”，不能把入口保留理解成页面永久保留。
6. 显式关闭完成任务条仍生效；成功任务条仍按原规则三秒隐藏。通知里的查看、继续、打开会话/应用正常。
7. 浅色、深色各过一遍以上流程；暂停后息屏再亮屏仍保持暂停且可继续。

## 委派与边界

腾讯 quick 使用 DeepSeek-v4.1-flash 辅助排查，主会话核对源码和执行测试。独立 review 尝试 DeepSeek-v4-pro（75 秒）、Kimi-k3-1（55 秒）、GLM-5.3（45 秒）均超时，未取得复核结论，不把它记为复核通过。凭据未发送给子代理。
