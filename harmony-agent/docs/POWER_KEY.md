# 电源键长按唤起助手：可行性与验证入口

2026-10-29 核查。用户希望关闭小艺的长按电源键唤醒，再把长按动作交给本助手。

**当前结论：没有找到普通应用可直接完成这种替换的公开接口，因此 0.3 不宣称支持电源键绑定。关闭小艺开关不等于系统把这个按键开放给第三方。** 已实现独立的助手唤起入口，后续若找到经过设备验证的合法触发方式，可以连接到这个入口。

## 已核对的路线

| 路线 | 证据和边界 | 决定 |
| --- | --- | --- |
| 关闭小艺后选择其他助手 | 华为帮助文档说明开关控制小艺唤醒；没有给出绑定第三方助手的操作 | 不把开关关闭视为授权或默认助手替换 |
| 默认应用 API | 当前 SDK 的 `defaultAppManager.ApplicationType` 为浏览器、图片、音视频、文档、邮件等类型，没有数字助理类型 | 没有采用 Android `VoiceInteractionService` 的等价公开入口；不能虚构一个“设置为默认助手”按钮 |
| ArkTS 按键订阅 | `inputConsumer.keyPressed` 要求前台焦点窗口；支持音量/媒体以及特定指纹滑动键，列表中没有电源键。`hotkeyChange` 是带 Ctrl/Shift/Alt 修饰键的组合快捷键 | 不能据此承诺后台手机电源键长按唤醒；外接键盘快捷键是另一种功能 |
| 原生按键状态轮询 | OpenHarmony 当前 `OH_Input_GetKeyState` 有键值白名单，不含 POWER | 不能把通用键值常量存在当成可轮询电源键；开源实现也不等同目标华为 ROM 的验收结果 |
| 原生全局监听/拦截 | `INPUT_MONITORING`、`INTERCEPT_INPUT_EVENT` 在 SDK 权限表中为 system_basic，需对应签名/资格。监听成功也不代表系统保留键会下发，更不等于替换系统处理 | 普通个人调试签名能否合法取得资格须确认；本版未申请这两项权限、未监听全局按键 |
| 本机 HDC | 授权调试会话与 App UID 权限不同，可能存在只读输入诊断能力；当前没有目标手机 | 保留为实测候选，不声称已经可用，也不把 shell 能启动应用推断为能获得电源键事件 |
| 小艺/系统进程 Hook、修改按键策略 | 依赖系统签名、Root 或厂商内部机制 | 按普通权限边界不做 |

目标机路线至少需要同时确认：能够合法获取**物理电源键**的按下/抬起及长按时长；后台能够收到；能在系统允许的状态启动助手；不会把系统关机/重启等动作误触发为助手任务。应用被结束后的行为也必须明确。某个调试命令存在或能读目录，都不代表这些条件已满足。

## 0.3 已准备好的唤起入口

- `EntryAbility` 使用 singleton，入口 URI 为 `harmonyagent://assistant`。
- 已运行时收到入口，切回聊天页面；正在操作手机时请求暂停。冷启动先载入会话，再打开聊天页。
- 外部入口只能打开 UI，不接收任务目标、模型配置、工具参数，不自动提交消息、不开始录音、不自动连接调试。
- 这个入口**本身没有绑定电源键**，也不授予后台启动或锁屏解锁能力。只验证了编译，系统 URI 路由和热/冷启动效果待真机测试。

其他有合法前台启动权限的应用，可以用显式 Want 测试：

```typescript
await context.startAbility({
  bundleName: 'com.example.harmonyagent',
  abilityName: 'EntryAbility',
  uri: 'harmonyagent://assistant'
});
```

用户主动点击桌面图标仍能正常打开助手。上述调用成功只证明能打开界面，不能用作电源键绑定成功的证据。

## 首次连接时的只读诊断

工程附带 `scripts/probe-power-key.py`，用已经授权的电脑 HDC 收集系统版本、shell 身份、输入节点访问元数据和命令是否存在。它不读取按键内容、不写设置、不关闭小艺，也不启动长期监听。

```bash
hdc list targets
python3 scripts/probe-power-key.py --target <已授权设备标识> --output power-key-probe.json
```

此诊断只用于后续决定有没有值得继续验证的 HDC 路线，不构成日常依赖电脑的设计。当前没有手机连接，尚未执行真机诊断。

## 来源

- [华为：小艺“唤醒”功能说明](https://consumer.huawei.com/cn/support/content/zh-cn16013467/)，HarmonyOS 5.0 及以上；说明小艺的长按电源键开关。
- [OpenHarmony 官方按键订阅文档](https://github.com/openharmony/docs/blob/master/zh-cn/application-dev/reference/apis-input-kit/js-apis-inputconsumer.md)，与本地 SDK 26 的公开声明交叉核对。
- [OpenHarmony 原生按键状态查询实现](https://github.com/openharmony/multimodalinput_input/blob/7f674c11dee34d4c158827ca8f0e8db3b12a1d4b/frameworks/native/input/oh_input_manager.cpp)，`g_keyCodeValueSet` 与 `OH_Input_GetKeyState`；作为实现证据，不替代华为机型验证。
- 本地 HarmonyOS SDK 26.0.0.105：`@ohos.multimodalInput.inputConsumer.d.ts`、`@ohos.bundle.defaultAppManager.d.ts`、`multimodalinput/oh_input_manager.h`、`toolchains/lib/PermissionDefinitions.json`。

官方开发者网页的部分内容未能直接抓取，因此相关技术结论使用已安装 SDK 声明和官方开源资料交叉确认，没有把搜索不到当作绝对不存在的证明。
