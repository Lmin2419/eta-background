# 阶段 9 · 子代理选模型、会话置顶分组、Linux 时区

基线 `46b67ff`（包含用户指定 `259c934` 和后续设计补充）。三次提交完成后一起推送、一起发布；目标 versionCode `2026110617`、versionName `3.3.5`，引擎不变。当前本记录随每个子阶段追加，未把未执行的 CI、发布或真机结果记作通过。

## 9a · Linux 时区、逻辑修复和 APK 文件名

| 项目 | 改动与验证 |
| --- | --- |
| chroot | 四处 `env -i` 均带 `TZ`；zoneinfo 存在性由 root 脚本检查，命令/交互、GNU env/BusyBox 均覆盖 |
| proot | Kotlin 检查 rootfs 的 zoneinfo；存在则使用系统 ID，否则 POSIX；测试比较 rootfs 文件清单与字节，未写入文件 |
| POSIX | `java.time` 规则转换；上海、印度、纽约、伦敦、悉尼及 Lord Howe、Casablanca；用 GNU date 对照 Java 冬夏及 Ramadan 偏移；另核对 Havana/Santiago 午夜与半小时 DST 切换前后各一秒。不能表示的规则回退当前偏移 |
| 会话重命名 | 只改标题，不更新消息时间；旧会话的 UI 和数据库 updated_at 均保持原值。置顶/移动分组同一约束在 9c 验证 |
| 暂停胶囊 | 手动暂停→息屏→亮屏，SDK 33/36/37 均恢复胶囊，仍为暂停，不显示运行流光、不添加唤醒锁 |
| 无效默认子模型 | 简中、繁中、英文提示，使用 attentionText；不静默显示跟随主模型。角色级提示在 9b |
| APK | `Pangxiaopang-3.3.5-2026110617.apk`；debug / release 使用同一命名，manifest versionName 后缀不变。CI 签名保留 basename，上传及发布继续使用实际产物路径，并在签名后断言完整文件名 |

### 先失败，再通过

新增复现测试在原生产代码上得到 **12 个语义断言失败**（不是编译失败）：TZ 7、暂停胶囊 3 个 SDK、重命名 1、无效模型提示 1。[基线 XML](stage-9/9a-before/)。APK 另先构建旧包，名字断言失败：[修改前](stage-9/9a-after/apk-name-before.txt)。

修改后相关回归 **70 项，0 失败、0 错误、0 跳过**；包含原有会话增量持久化、加载、Shell 以及运行时测试。[测试 XML](stage-9/9a-after/)、[字面构建输出](stage-9/9a-after/local-regression.txt)。实际 Debug 构建产物的名字及文件存在性通过：[修改后](stage-9/9a-after/apk-name-after.txt)。Release 的签名、上传与资源名将在三次提交完成后的 CI 核对。

### 界面截图

Robolectric 原生 Compose 渲染，不是手机照片；仅此项有界面改动。

| 提示 | 浅色 | 深色 |
| --- | --- | --- |
| 修改后无效默认模型 | [查看](stage-9/9a-after/stage9a-subagent-unavailable-light.png) | [查看](stage-9/9a-after/stage9a-subagent-unavailable-dark.png) |

9a 未修改 Android 普通 Shell、rootfs 内容、请求协议、工具调度、引擎、猫矢量、包名、签名和 manifest versionName。腾讯 quick 时区建议未直接采用；coding 的 GLM/DeepSeek/Kimi 超时后主会话实现；独立 quick 复核最终由 MiniMax M3.1 Flash 返回，指出的边界用可执行测试验证。

## 9b · 按角色模型

- DataStore 仍使用 `subagent_settings_v1`；新增 `profiles` 字典，旧 JSON 读为空表。设置修改使用 `dataStore.edit` 内的 transform，避免覆盖其他角色刚保存的配置。
- 解析顺序：角色的启用模型 → 默认子模型 → 主模型；思考强度独立按角色 → 默认 → 实际模型默认，再按能力归一化。未保存临时角色不用同名覆盖。
- `configProvider(profile)` 在派发、续接的角色确定后调用；活动快照/结果中的 model 仍来自实际配置。请求构造、工具调度、引擎未改；工具继承和禁止递归派发保持。
- 全局标题改为默认模型/默认思考强度；角色摘要增加模型行，详情顶部加模型和思考强度；复用模型面板，首项为跟随默认模型。失效的默认/角色模型均有 attentionText 提示。
- 角色文件的保存/删除入口同步迁移/移除设置，UI 不再先保存新名字再另删旧名字。拒绝重名覆盖；临时文件替换；DataStore 更新异常时恢复原文件字节。同步工具入口保留，UI 从 IO 调用；未宣称跨文件与 DataStore 的断电级事务原子性。

### 复现和回归

初始复现 **10 项、7 个语义失败**；另外单独复现改名（增加可选参数但未实现迁移时）和旧无参配置 provider 的派发/续接断言各 1 项失败。[失败证据](stage-9/9b-before/)。相同派发测试兼容旧 Function0 和新 Function1，不以编译错误代替复现。

最终相关回归 **29 项全通过，0 失败、0 错误、0 跳过**：[XML 和字面输出](stage-9/9b-after/)。覆盖四级组合、无效角色回退默认、思考能力归一化、临时/已保存角色、DataStore 往返、文件改名/删除、重名拒绝、写入失败回滚、三个实际模型标签与续接、真实设置选择后仅更新目标角色、原 SettingsReorganizationTest。测试使用独立真实 DataStore/Room，避免 Robolectric 不同图形沙箱共享文件锁；等待实际写入值并推进 UI 帧，没有跳过测试。

| 截图 | 浅色 | 深色 |
| --- | --- | --- |
| 角色列表 | [查看](stage-9/9b-after/stage9b-roles-light.png) | [查看](stage-9/9b-after/stage9b-roles-dark.png) |
| 角色详情 | [查看](stage-9/9b-after/stage9b-role-detail-light.png) | [查看](stage-9/9b-after/stage9b-role-detail-dark.png) |
| 角色模型选择 | [查看](stage-9/9b-after/stage9b-role-picker-light.png) | [查看](stage-9/9b-after/stage9b-role-picker-dark.png) |

另有[角色模型失效提示](stage-9/9b-after/stage9b-role-unavailable-light.png)。均为原生 Robolectric 渲染；真机的三个模型并发调用留给最终 APK 验收。

委派：腾讯 coding（GLM-5.3）给解析草案，主会话纠正其中失效模型漏回退默认的问题；review 的 DeepSeek V4 Pro 超时后 Kimi K3-1 完成目录保存边界复核，主会话补了异常恢复测试。

## 9c · 置顶、分组、迁移和备份 v6

待执行，不能视为已验收。

## 真机验收（最终发布后执行）

- chroot 和 proot 分别运行 `date`、`date +%z`、`python3 -c 'import datetime;print(datetime.datetime.now())'`，与手机时间/时区一致。
- 旧会话改名后不跳到「今天」。
- 手动暂停任务，息屏再亮屏，胶囊恢复且仍需手动点继续。
- 三个子代理分别使用三个模型；活动面板显示实际模型。
- 置顶、分组、折叠后退出 App 再进入仍保持；删除分组不删会话。
- 浅色、深色各走一遍设置和侧栏；导出/导入 v6，另导入 v5 确认保留手机子代理设置。
