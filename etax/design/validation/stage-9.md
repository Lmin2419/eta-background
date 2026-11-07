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

### 实现与边界

| 项目 | 实现和自动检查 |
| --- | --- |
| Room 24→25 | 新增 pinned_at/group_id、conversation_groups；生产 builder 注册 MIGRATION_24_25，原 destructive fallback 保持。真实 v24 数据经生产入口升级后逐表比较，会话、消息、归档、模型选择均保留 |
| 防漏注册 | 故意仅去掉生产迁移注册，测试捕获数据被清空并失败；恢复后通过。[失败证据](stage-9/9c-before/missing-migration-guard.xml) |
| 完整状态链 | Entity、索引、惰性加载、UI、增量保存和重启；已加载/未加载会话均保存置顶/分组，重命名、置顶、移动不改 updated_at |
| 侧栏 | 置顶按 pinned_at 倒序，分组按 sort_order，组内按 updated_at；每条只出现一次；空组显示、折叠持久化。搜索/归档平铺，搜索有图钉和分组标签 |
| 分组操作 | 真实 DOWN/UP 长按进入多选，批量置顶/取消、移动、新建后直接移动；长按组头改名、上下移、确认删除。名称 trim、1–20 Unicode 码点、忽略大小写去重；输入错误原位显示 |
| 归档/删除 | 归档清置顶但保留分组；删除组在同一 DB 事务清引用，不删会话/消息。侧栏组头数量为非置顶未归档成员，移动和删除确认数量包括该组全部引用 |
| 备份 v6 | 导出分组/折叠/置顶和子代理默认及角色设置；v6 往返一致；v5 无置顶分组且保留手机原子代理设置；悬空 group_id 清空；不存在的模型 ID 可导入、运行时按 9b 回退 |
| 失败恢复 | 注入数据库写失败，验证旧分组/会话/设置未变；保留原备份提交后设置恢复和错误报告路径，不扩展为跨 DB/DataStore 的断电级原子事务 |

### 先失败，再通过

[修改前 XML](stage-9/9c-before/)分别记录真实 v24 迁移、状态保存、备份和侧栏手势的语义失败。迁移测试使用标准 Application，避免 EtaApp 启动后台任务抢先打开待升级夹具。原有迁移链测试一并跑到 v25。

- 数据专项 **47 项全部通过**：[XML](stage-9/9c-data-after/)。
- 最终 Windows 综合回归 **159 项，155 通过、4 失败、0 跳过**：[XML](stage-9/9c-final-local/)、[字面输出](stage-9/9c-after/stage9-final-regression-2.txt)。阶段 9 新增测试及侧栏真实触摸、首页手指滚动、长会话、设置回归全部通过。角色选择夹具直接向真实 Room 写输入模型，避免把不属于此 UI 测试的 provider 修复链路一起启动；仍通过实际 UI 选择并等待 DataStore 目标值。
- 剩余 4 项是既有记忆文件覆盖断言。以相同四测试分别运行 **9c 前的备份代码** 和当前代码，结果完全相同：首项写入新文件通过，后续三项覆盖后仍读到先前内容。未据此更改记忆业务、跳过或删除测试：[旧代码](stage-9/memory-pre9c-replay/)、[当前代码](stage-9/memory-current-replay/)。全量 Linux CI 必须另外通过，不能用这份 Windows 局部结果替代。
- 本地 lint、Debug APK 结果见本节末尾；签名 APK、全量测试、发布以最终 main 提交的 [Etax Build](https://github.com/Lmin2419/eta-background/actions/workflows/etax-build.yml) 为最终门禁。只有三个 job 全绿且 Release 资源存在才交付。

本地 `lintDebug :app:assembleDebug` **通过**，lint 0 Error / 0 Fatal；[字面输出](stage-9/9c-after/lint-apk.txt)、[lint XML](stage-9/9c-after/lint-results-debug.xml)。接线检查通过；本地引擎脚本回归因 Windows 缺 `mksh` 报错，未改引擎代码或禁用测试，交由配置完整的 Linux CI 全量执行。

### 渲染截图

均为原生 Robolectric Compose 渲染，不是实机截图。

| 场景 | 浅色 | 深色 |
| --- | --- | --- |
| 置顶和两个分组（学习折叠） | [查看](stage-9/9c-after/stage9c-sidebar-light.png) | [查看](stage-9/9c-after/stage9c-sidebar-dark.png) |
| 移到分组面板 | [查看](stage-9/9c-after/stage9c-move-light.png) | [查看](stage-9/9c-after/stage9c-move-dark.png) |
| 分组操作面板 | [查看](stage-9/9c-after/stage9c-group-actions-light.png) | [查看](stage-9/9c-after/stage9c-group-actions-dark.png) |

委派：腾讯 quick 提供投影测试矩阵，coding GLM-5.3 提供备份草案；主会话落实实际 Room 24→25，未采用草案混淆备份版本和数据库版本的额外建议。持久化 review 的 DeepSeek V4 Pro 超时后 Kimi K3-1 返回；其关于异步空组快照和归档仍可置顶的担心，经实际同步初始快照及 DAO 条件核对不成立。最后一次分组 UI review 的 DeepSeek V4 Pro / Kimi K3-1 / GLM-5.3 均超时（75/55/45 秒），由主会话完成本地复核。

未改猫图标、包名、Eta* 内部名、备份/导出文件名、User-Agent、日志和关于页上游致谢链接；仅安装包文件名按本阶段要求改为 Pangxiaopang。未改引擎、模型请求构造和工具调度。


## 真机验收（最终发布后执行）

- chroot 和 proot 分别运行 `date`、`date +%z`、`python3 -c 'import datetime;print(datetime.datetime.now())'`，与手机时间/时区一致。
- 旧会话改名后不跳到「今天」。
- 手动暂停任务，息屏再亮屏，胶囊恢复且仍需手动点继续。
- 三个子代理分别使用三个模型；活动面板显示实际模型。
- 置顶两个会话，建立「工作」「学习」分组并批量移入；折叠学习，退出 App 再进入后置顶/顺序/分组/折叠仍保持。取消置顶回原分组；改名、排序、删除学习后会话仍在。
- 归档已置顶分组会话后取消置顶，取消归档回原组；搜索结果平铺并显示图钉、分组、归档标签。
- 自定义子代理角色改名/删除同步设置；停用角色所选模型有明确提示并按默认→主模型回退。
- 浅色、深色各走一遍设置和侧栏；导出/导入 v6，另导入 v5 确认保留手机子代理设置。
