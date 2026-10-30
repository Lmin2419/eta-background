# 随身助手推进方案（0.9.1 → 0.14）

制定：2026-10-30。基于 `main@aadbf93`。执行规则见 [../AGENTS.md](../AGENTS.md)。

## 当前进度

| 任务 | 状态 | 说明 |
| --- | --- | --- |
| T01–T03（M0 / 0.9.1） | **代码完成，待真机** | Claude 完成，PR #8（分支 `harmony/m0-0.9.1`），三个任务合在一个 PR 里，方便一次真机测完。200 项测试通过；ArkTS 检查 0 错误（OpenHarmony SDK 26.0.0.38）；未生成 HAP |
| `.ets` 与资源检查工具 | 完成 | `scripts/fetch-ohos-sdk.sh` + `npm run check:ets` + `npm run check:res`（restool 资源打包），CI 任务 `Harmony / ArkTS (.ets) and resources check`，也覆盖 base 为 `harmony/**` 的叠加 PR |
| E01（0.9.2） | PR #9，修复完成、四项检查通过 | 已合入 M0 `2d44742`；app_name 仅由 AppScope 定义。213 项测试通过，ArkTS 0 错误，restool 1,833 项资源通过；按夜间规则进入 L1 |
| L1（0.9.3） | 代码完成、四项检查通过 | `harmony/l1-conversation` 基于 E01；36 项 Eta 单测，0 跳过；总计 249 项通过，ArkTS 0 错误、restool 通过；未接入页面，真机待测 |
| L3（0.9.4） | 代码完成、四项检查通过 | `harmony/l3-chat-behavior` 基于 L1；46 项 Eta 原测试完整移植，3 项平台测试仅迁移逻辑；共 298 项通过，ArkTS 0 错误、资源通过。真机待测 |
| L2a（0.9.5） | 解析部分完成、四项检查通过 | `harmony/l2-markdown` 基于 L3；35 项 Eta 原测试，0 跳过，另 6 项 TS 适配测试；总计 339 项通过。L2b 另迁移剩余 44 项；未接入页面 |
| L2b（0.9.6） | 状态/缓存/渐显策略完成、四项检查通过 | `harmony/l2b-markdown-state` 基于 L2a；39 项完整原测试，5 项平台测试只迁移逻辑；累计 L2 74 项完整 + 5 项逻辑替代，总测试 383 项通过。绘制/帧时钟留 E04b |
| L4a（0.9.7） | 消息投影完成、四项检查通过 | `harmony/l4-runtime-projection` 基于 L2b；18 项 Eta 原测试、0 跳过，总测试 401 项。L4b 继续结果恢复及角色修订依赖，未接入运行时 |
| L4b（0.9.8） | 结果恢复完成、四项检查通过 | `harmony/l4b-result-recovery` 基于 L4a；8 项任务原测试 + 14 项依赖原测试、0 跳过，总测试 423 项。L4 合计 26 项任务测试全部移植；未接运行时 |
| E02（0.9.11） | 组件代码完成、四项检查通过 | `harmony/e02-components` 基于 L4b；430 项测试；追加已批准的 VIBRATE 与内容层渐进模糊尝试，含 2 项原渲染测试的测量逻辑；原平台验证 2 项待测。Gallery 已纳入 ArkTS；正式入口未切换，未生成 HAP |
| E03（0.9.12 / PR #17） | 新壳层完成、四项检查通过 | 合入 #16 追加；Index 已切 Navigation/NavDestination，所有宽度推移；448 测试、ArkTS 0 error、1,852 资源；原测试 2 完整 / 3 平台待测；[交付记录](E03_NAVIGATION_PORT.md) |
| E04a（0.9.13） | 首页与输入栏完成、四项检查通过 | `harmony/e04a-home-input` 基于 E03；464 测试、12 原测试完整 / 2 平台定位待测、0 ArkTS error、1,855 资源；[交付记录](E04A_HOME_INPUT_PORT.md) |
| E04b（0.9.14） | 消息与 Markdown 接入、四项检查通过 | `harmony/e04b-chat-rendering` 基于 E04a；473 测试、0 ArkTS error、1,861 资源；复用 L2/L3 原测试，新增 9 集成测试；[交付记录](E04B_CHAT_RENDERING_PORT.md) |
| E04c（0.9.15） | 排队与消息操作接入、四项检查通过 | `harmony/e04c-chat-actions` 基于 E04b；491 测试、5 原测试 / 0 跳过、13 集成测试、0 ArkTS error、1,863 资源；[交付记录](E04C_CHAT_ACTIONS_PORT.md) |
| E07a（0.9.16） | 侧栏/导出完成，存储对照留 E07b | 503 测试，0 ArkTS error，1,864 资源；9 项原 Store 测试的逻辑适配 + 3 新验证；完整 Room 对照未完成，[交付记录](E07A_CONVERSATION_PANE_PORT.md) |
| E07b | 草稿-阻塞：存储模型迁移待决定 | 17 项原 Store 测试尚未完整迁移；[具体差异与 A/B 方案](E07B_STORAGE_DECISION.md)。E05/E06 从 E07a 继续，E08 需要新模型的部分受此阻塞 |
| M1 其余（E05、E06、E08–E09） | 夜间队列已排好 | 见第二节“本轮队列” |
| M1：E01–E09（Eta 界面与交互 1:1 移植） | **进行中** | 规范见 [ETA_PORT.md](ETA_PORT.md)。由 Codex（A 总）按顺序执行，开工条件见 M1 |
| T04 起（M2–M5） | 未开始 | 在 M1 完成后执行 |

每完成一个任务，执行者更新本表。

## 一、现状判断

1. **代码领先于真机验证。** 0.9 的框架代码和 193 项测试都过了，但新框架从未在手机上运行过；最近一次真机反馈还是 0.3。最大的风险不是功能少，而是没有稳定的“改代码 → 上真机 → 回传问题”循环。
2. **仓库源码目前无法在 DevEco 直接编译。** `entry/build-profile.json5` 启用了原生构建，`entry/src/main/cpp/CMakeLists.txt` 在 `vendor/` 缺少预编译 OpenSSL 时会 `FATAL_ERROR`，而 `vendor/` 没有入库，只能在装有 HarmonyOS 命令行工具的 Linux 上生成。0.9 入口其实不加载这个原生库。
3. **聊天还不能日常使用。** 切到后台会立即停止回复（`EntryAbility.onBackground`）；流式渲染时每帧重建整条消息；没有复制、重新生成、错误重试、深色模式；只支持 OpenAI Chat Completions 协议。
4. **界面与交互和 Eta 差距大。** 0.9 只借鉴了 Eta 的一部分页面，质量也不够。用户决定改为**照着 Eta 源码 1:1 移植**（M1）。原来计划的消息渲染、消息操作、深色模式三项（T05、T06、T08）并入移植任务。
5. **手机自动化的路线已有原型。** 方案是应用内 HDC 客户端连接本机无线调试，0.3 已在真机读到 103 个控件。这是本项目区别于普通聊天应用的核心，但在 0.9 中暂时撤下了，需要按步骤接回。

## 二、分工与循环

| 谁 | 做什么 |
| --- | --- |
| Codex（A 总） | 按任务卡写代码、单测、文档，每个任务一个 PR；跑 `npm run check`、`npm test` |
| 你 | 拉取 PR 分支，用 DevEco 编译并安装到 Pura X Max，按 PR 里的“真机验证步骤”操作，把结果和诊断报告贴回 PR 或给 Codex |
| Claude（可选） | 审 PR、根据真机结果调整下一步 |

```
任务卡 → Codex 开 PR → CI 通过 → 你真机测试 → 通过就合并 / 失败就把诊断报告交给 Codex 修
```

规则：

- **一次只做一个任务。** 上一个 PR 合并后再开始下一个，避免多个未验证的改动叠在一起。M1 的 PR 在 CI 全绿后即可合并，只在标注的真机检查点停下来测试；其他里程碑的 PR 要真机通过后才合并。
- **里程碑闸门。** 每个里程碑末尾有一轮完整真机验收；不通过就不进入下一个里程碑。
- 每个合并的 PR 升一次补丁版本号，方便你在手机上确认装的是哪一版。

### 无人值守连续执行（你不在时）

你不在的时候没人合并 PR、也没人测真机。Codex 按“本轮队列”一个接一个做，规则如下：

1. **叠加分支**：每个任务从**上一个通过检查的任务分支**拉新分支，PR 的 base 设为那个分支，形成一条链。不合并任何 PR，不推送 `main`，只推送自己任务的分支。前面的分支修改后，把它合并（或 rebase）进后面的分支。
2. **通过才前进**：当前 PR 的 `npm run check`、`npm test`、`npm run check:ets`、`npm run check:res` 全部通过（或 CI 的两个 Harmony 任务全绿），才能把 PR 从草稿改为可审查，再开始下一个任务。
3. **卡住就跳过**：一个任务认真尝试 3 次仍无法通过检查，或者遇到必须由你决定、而且会影响后续的问题时，PR 保持草稿，写明“阻塞原因”，然后跳到队列中**不依赖它**的下一个任务，从最后一个通过检查的分支继续拉分支。
4. **小决定自己做**：不影响后续的小问题，选最接近 Eta 源码的做法，写进 PR 的“待用户确认”一节，继续往下做。
5. **不冒充真机结果**：真机检查点只列出步骤，标为“待测”。
6. **PR 不要太大**：生成的资源文件不计，手写代码超过约 2500 行时拆成多个 PR。
7. **早上的报告**：队列做完或全部阻塞时停下，最后输出一份报告，格式见“本轮队列”末尾。

### 本轮队列（2026-10-31 继续，用户已确认）

E02 追加（#16，完成） → E03（#17，完成） → E04a → E04b → E04c → E07 → E05 → E06；全部完成后可继续 E08 记忆 → Skills → MCP。继续遵守无人值守叠加 PR 规则；结束报告写 `docs/reports/2026-10-31-report.md`。决定见 ETA_PORT 末尾。

### 本轮队列（2026-10-30 夜间）

按顺序执行，做到哪算哪：

| 顺序 | 任务 | base 分支 | 说明 |
| --- | --- | --- | --- |
| 1 | 修复 PR #9（E01） | `harmony/m0-0.9.1` | 合并 `harmony/m0-0.9.1` 的最新提交；去掉 entry 中重复的 `app_name`，AppScope 改为“随身助手”；四项检查全部通过 |
| 2 | L1 对话逻辑 | `harmony/e01-resources` | 见 M1 的 L1 任务卡 |
| 3 | L3 聊天列表与侧栏行为 | L1 的分支 | 见 L3 |
| 4 | L2 Markdown 解析 | L3 的分支 | 见 L2；最可能卡住，放在后面 |
| 5 | L4 运行消息投影 | 上一个通过检查的分支 | 见 L4；依赖过多时按规则 3 跳过 |
| 6 | E02 组件库 | 上一个通过检查的分支 | |
| 7 | E03 应用骨架与导航 | E02 的分支 | 真机检查点 ① 留给你早上测 |
| 8 | E04a 首页与输入栏 | E03 的分支 | |
| 9 | E04b 消息列表与 Markdown 渲染 | E04a 的分支 | 使用 L2、L3 的逻辑；L2 被跳过时，暂用现有 `core/markdown.ts` 并注明 |
| 10 | E04c 排队、编辑、重新生成 | E04b 的分支 | 使用 L1 的逻辑接入界面 |
| 11 | E07 会话列表与历史 | E04c 的分支 | 使用 L1 的时间分组和导出 |
| 后备 | E05 → E06 | 依次叠加 | 前面全部完成后才做 |

**早上的报告**（Codex 最后输出，同时写入最后一个分支的 `harmony-agent/docs/reports/2026-10-31-overnight.md`）：

```
## 夜间执行报告
| 顺序 | 任务 | PR | 分支 | 状态（可审查 / 草稿-阻塞 / 未开始） | 检查结果 | 移植的 Eta 单测数 |
## 阻塞与原因
## 待用户确认的决定（每条写清选项和默认做法）
## 需要真机测试的项目（按检查点列出步骤）
## 建议的合并顺序
```

### 给 Codex 的通用提示词

```
你在仓库 Lmin2419/eta-background 的 harmony-agent/ 目录工作。
先读 harmony-agent/AGENTS.md 和 harmony-agent/docs/ROADMAP.md；E 开头的任务还要读 harmony-agent/docs/ETA_PORT.md。
执行 ROADMAP 中的任务 <编号>，严格按任务卡的范围，不要顺手做其他任务。
新建分支 harmony/<编号>-<短名>，完成后开 PR 到 main，PR 描述按 AGENTS.md 的格式写，
其中“真机验证步骤”要具体到每一步点哪里、预期看到什么。
```

修复真机问题时：

```
任务 <编号> 的真机测试失败。以下是我的操作、看到的现象和诊断报告：
<粘贴>
请在同一分支修复，并更新 PR 的真机验证步骤。
```

## 三、里程碑总览

| 里程碑 | 版本 | 目标 | 任务 |
| --- | --- | --- | --- |
| M0 | 0.9.1 | 能直接编译上真机，能回传问题 | T01–T03 |
| M1 | 0.10 | **Eta 界面与交互 1:1 移植** | E01–E09 |
| M2 | 0.11 | 聊天能力补齐：后台不中断、错误与重试、多协议 | T04、T07、T09（T05、T06、T08 已并入 M1） |
| M3 | 0.12 | 手机操作：连接与读屏（只读） | T10–T12 |
| M4 | 0.13 | 手机操作：点击、输入、打开应用 | T13–T15 |
| M5 | 0.14 | 后台长任务与通知 | T16–T17 |
| 之后 | — | 扩展能力，按顺序逐个做 | 见第五节 |

---

## 四、任务卡

### M0 · 0.9.1「能上真机，能回传问题」

#### T01 让仓库源码可直接在 DevEco 编译 + CI + 版本

**目标**：你在 Windows 上 `git clone` 后用 DevEco 打开 `harmony-agent/`，不需要 `vendor/` 就能编译安装 0.9.x。

**做法**

- 原生 HDC 库改为可选：`CMakeLists.txt` 在 `vendor/` 不存在时不构建 `agent_hdc`，只输出提示，不再 `FATAL_ERROR`（或从 `entry/build-profile.json5` 去掉 `externalNativeOptions`，到 M2 再加回）。选择改动更小、对 DevEco 更稳的方案，并在 PR 中说明。
- 确认新入口（`Index.ets` → `FoundationHost`）的引用链不导入 `platform/Services.ets` 或任何 `libagent_hdc.so`。
- 新增 `.github/workflows/harmony-check.yml`：只在 `harmony-agent/**` 变化时触发，Node 22，执行 `npm ci`、`npm run check`、`npm test`。不要修改现有的 `pr-check.yml`、`etax-build.yml`。
- 版本号改为 `0.9.1` / `90001`（`AppScope/app.json5`、`package.json`、`oh-package.json5`）；`AssistantShell.ets` 中硬编码的“0.9.0 · 交互框架预览”改为运行时读取应用版本（`bundleManager.getBundleInfoForSelf`）。
- 修正 `archives/README.md` 和 `docs/FOUNDATION_0.9.md` 中对 `/workspace/snapshots/...` 的描述：那是已回收的临时云端容器路径，仓库中的 ZIP 才是长期备份。

**自动验收**：新 workflow 在 PR 上运行并通过；193 项测试不减少。

**真机验收（你）**：在 DevEco 中打开仓库的 `harmony-agent/`，不执行任何脚本直接 Run；设置页显示 `0.9.1`。

#### T02 应用内诊断页与脱敏报告

**目标**：每次真机出问题时，你可以一键复制一份不含隐私的报告交给 Codex，不用描述半天。

**做法**

- `core/diagnostics.ts`：有界事件日志（例如最多 300 条），每条包含时间、模块（http / storage / lifecycle / ui / device）、阶段、错误码和简短说明。持久化到现有 RDB 文档存储，崩溃重启后仍可查看。
- `redact()`：去除 `sk-…` 类 Key、`Authorization` / `Bearer`、URL 查询串（服务地址只保留域名），长文本截断。**消息正文、模型回复和 Key 一律不进日志。**
- 埋点：`HarmonyHttpTransport` 的状态码、首包耗时、总耗时、分片数和错误码；RDB / ASSET 失败；前后台切换；未捕获异常（`errorManager`）。
- 设置 → “诊断”：显示设备型号、系统版本、API 版本、应用版本、窗口尺寸、折叠状态、字体缩放和最近事件；提供“复制报告”和“清空记录”按钮。

**自动验收**：脱敏单测覆盖 Key、Bearer、查询串和超长文本；环形上限；确认消息正文不会写入日志。

**真机验收**：正常发送一条消息，再断网发送一条；打开诊断页复制报告，报告中有两次请求的记录，且不含 Key 和消息内容。

#### T03 真机验收模板与本地开发说明（纯文档）

- `docs/device-runs/TEMPLATE.md`：把 `ACCEPTANCE_0.9.md` 改写成大约 20 分钟可做完的勾选表，每行写编号、操作、预期、结果（通过 / 失败 / 未测）和备注。按“安装升级、首页布局、模型服务、聊天、会话、前后台、折叠 / 键盘 / 字体”分组。每次真机测试复制一份为 `docs/device-runs/<日期>-<版本>.md`。
- `docs/LOCAL_DEVELOPMENT.md`，面向 Windows 和 DevEco：
  - 克隆仓库，打开 `harmony-agent/`；
  - 把旧工程 `build-profile.json5` 中的 `signingConfigs` 和证书路径复制过来，**沿用原签名**才能覆盖安装、保留数据；
  - 用 `git update-index --skip-worktree build-profile.json5` 防止把签名提交出去；
  - 如何拉取 PR 分支测试（`git fetch origin <分支>` → `git checkout <分支>`）；
  - 遇到“签名不一致”时不要卸载，先检查证书。

**闸门 G0**：PR #8 的 A1、A3、D1、G1 通过即可合并并开始 M1；完整模板在 M1 期间补测。发现的问题由 Codex 逐条修复。

---

### M1 · 0.10「Eta 界面与交互 1:1 移植」

规范见 **[ETA_PORT.md](ETA_PORT.md)**，每张卡都必须按它执行：照抄 Eta 源码的结构、文案、尺寸和交互，连同 Eta 的单测一起移植；鸿蒙做不到的功能按 A / B / C 分级处理。

**开工条件**：PR #8 合并。为了尽快开工，PR #8 的合并条件放宽为真机模板中的 A1（DevEco 编译）、A3（版本号）、D1（聊天）、G1（诊断页）四项通过，其余项在 M1 期间补测。PR #8 合并前，E01 可以从 `harmony/m0-0.9.1` 拉分支，PR 的 base 设为该分支，#8 合并后再改为 `main`。

**节奏**：M1 的任务按顺序进行，每个 PR 的 CI（单测和 ArkTS 检查）全绿后，由你合并，再开始下一个。只在标有“真机检查点”的任务上停下来等你测试，其他任务不必逐个上真机。

**版本**：E01–E08 每合并一个 PR 升一次补丁号（0.9.2、0.9.3……），E09 发布 0.10.0。

#### E01 移植基础：许可证、文案、图标、颜色与尺寸

- 按 ETA_PORT §3 增加 `harmony-agent/LICENSE`，更新 `THIRD_PARTY_NOTICES.md`；“关于”页的说明文字先放进文案资源，E05 再显示。
- 按 ETA_PORT §8 写脚本，**由脚本生成资源，不手工抄写**：
  - `scripts/port-eta-strings.py`（配单测）→ `string.json`（中文为默认，另有 `en_US`）；
  - `scripts/port-eta-icons.mjs` → 148 个 Material 图标、1 个 Miuix 图标、11 个矢量图，全部转为 SVG，并生成 `docs/eta-icons.json`。`@material-design-icons/svg` 固定为 `0.14.15`，加入 devDependencies；
  - 颜色 `color.json`（`base` 和 `dark` 两套）；
  - 尺寸 `core/eta/tokens.ts`；
  - JetBrains Mono 字体。
- 本任务不改界面。
- **自动验收**：脚本单测通过；PR 中报告生成的文案条数，与 Eta 的条数对比；图标缺失列表为空；`check:ets` 0 错误。
- **真机**：不需要。

#### L1–L4：逻辑先行（纯 TypeScript，不改界面）

把 Eta 中不依赖界面的逻辑，连同它的单测，先移植到 `core/eta/` 和 `tests/eta/`。**这一步不接入界面**，后面的 E04、E07 直接使用。规则见 ETA_PORT §5：

- 测试用例名保持可对照；
- 依赖 Android 框架的测试（Robolectric、Compose 测试）只移植逻辑部分，跳过的测试逐条写明原因；
- 需要的 Eta 数据类型只移植最小子集，放在 `core/eta/model/`。

| 任务 | Eta 源文件（`app/src/main/kotlin/io/github/mangi/eta/ui/`） | Eta 单测（`app/src/test/kotlin/...`） |
| --- | --- | --- |
| **L1 对话逻辑** | `app/AgentMessageQueueReducer.kt`、`AgentConversationRevisionReducer.kt`、`AgentRunEventCoalescer.kt`、`AgentRuntimeHistoryReducer.kt`、`AssistantConversationSeed.kt`、`ConversationTimeLabels.kt`、`ConversationMarkdownExporter.kt` | 同名 `*Test.kt`，共 36 项 |
| **L2 Markdown 解析** | `markdown/` 中的非绘制部分：`StreamingGfmParser`、`MarkdownInline`、`MarkdownDocumentBuilder`、`MarkdownModel`、`TexMathDelimiters`、`StreamingMarkdownState`、`StreamingMarkdownRestoreState`、`StreamingMarkdownStateCache`、`StaticMarkdownDocumentCache`、`SmoothTextReveal` 中的策略和协调部分 | `ui/markdown/*Test.kt`，共 79 项 |
| **L3 聊天列表与侧栏行为** | `components/` 中的逻辑：`AgentChatScrollPolicy`、`ChatHistoryPaging` / `HistoryScrollDemand`、`ConversationPaneState`、`ConversationPaneMotion`、`AgentChatTimelineEntries`、`AgentWorkSteps`、`AgentChatFinalResult`、`ChatConversationKey`（用 `grep` 找到它们实际所在的文件） | `ui/components/` 中的同名 `*Test.kt`，共约 49 项 |
| **L4 运行消息投影** | `app/AgentRunMessageProjector.kt`（750 行）、`AgentPendingResultRecovery.kt` | 同名 `*Test.kt`，共 26 项 |

- **L2 的特殊情况**：Eta 的块级解析依赖 JetBrains `intellij-markdown`（GFM）。鸿蒙这边优先用 TypeScript 自己实现所需的部分，以通过 Eta 的 79 项测试为准。确实需要第三方库时，只能用纯 TS / JS、MIT 或 Apache 许可证、没有原生依赖的库，以源码形式放进仓库（附许可证），并在 PR 中说明能否被 ArkTS 构建打包；做不到就按规则 3 跳过。
- **L4 的特殊情况**：依赖的 Eta 运行时类型如果太多，导致需要移植的代码超过约 2500 行，就按规则 3 跳过，在报告中说明。
- **自动验收**：移植的单测全部通过；PR 中报告移植数量和跳过数量（附原因）；`check:ets` 和 `check:res` 不受影响。
- **真机**：不需要。

#### E02 Eta 组件库

- 把 `ui/components/` 中的通用组件移植到 `entry/src/main/ets/ui/eta/components/`：
  - 卡片：`EtaCard`；
  - 设置行：`EtaPreferenceStyle` 一族（分组、分组标题、图标、分割线、分类颜色）、`EtaPreferenceRow` / `EtaPreference` / `EtaArrowPreference` / `EtaSwitchPreference` / `EtaRadioButtonPreference` / `EtaCheckboxPreference`、`EtaPreferenceGroupItem`；
  - 选择菜单：`EtaDropdownPreference` 与 `EtaSelectionPreferences`；
  - 入口和小控件：`EtaFeatureCard`、`EtaChoiceChip`、`ToolChip`、`SectionHeader`、`ListEmptyState`、`StatusColors`；
  - 按钮、开关和对话框：`EtaControls`（`EtaTextButton`、`EtaSwitch`、对话框）、`MiuixDialogActions`、`ItemDescriptionDialog`；
  - 页面骨架：`MiuixScaffoldPage`、`AdaptiveTopAppBar`、`MiuixBackButton`、`TopBarBackdrop`。
- 新增预览页 `pages/EtaGallery.ets`（不加入 `main_pages.json`），列出每个组件的各种状态：禁用、长摘要、无图标、深色、大字体。
- **自动验收**：`check:ets` 0 错误；增加一个测试，检查 `ui/eta/` 中没有写死的颜色值（`#RRGGBB`）。
- **真机**：不需要。如果方便，可以在 DevEco 预览中截图组件页，附在 PR 里。

#### E03 应用骨架与导航

- `core/eta/routes.ts`：完整对应 Eta 的 `AppRoute`，C 类路由标记为隐藏；页面栈由 core 持有（替换 `ShellNavigation`），配单测（push、pop、回到首页、通知深链）。
- ArkUI 用 `Navigation` + `NavDestination` 渲染；系统返回和侧滑返回转发给 core。
- 移植 `AgentAppShell.kt` 的顶栏（`AgentTopBar`、`TopBarOverflowMenu`、`titleForRoute`）、`ConversationSidePaneScaffold.kt` 和 `ConversationPaneMotion.kt`：所有宽度均为推移式侧栏，照抄 ConversationSidePaneScaffold.kt。启动页移植 `StartupSplash.kt`，连同它的单测。
- `Index.ets` 改用新骨架。还没移植的页面暂时显示 0.9 的旧页面，保证每个 PR 都能正常使用。
- **真机检查点 ①**：侧栏开合、横竖屏和展开屏下的布局、返回手势逐级返回。

#### E04 首页与聊天（较大，可拆成 E04a / E04b / E04c 三个 PR）

- **E04a 首页与输入栏**：
  - `AgentHomeScreen.kt`：问候语和四张建议卡，文案照搬，“Eta”按规范替换；
  - `AgentChatInputBar.kt`：“+”附件菜单；Atom 手机操作模式按钮为 B 类；模型按钮带品牌图标；发送 / 停止；
  - `AgentChatModelControls.kt`、`AgentModelPicker.kt`。
- **E04b 消息列表与 Markdown**（使用 L2、L3 已移植的逻辑，这里只做渲染和对接）：
  - 消息列表 `AgentChatBody.kt`：跟随到底部、上滑后暂停跟随、回到底部按钮、`ChatHistoryPaging.kt` 分页加载；
  - 单条消息 `ChatMessageItem.kt`：用户消息、助手消息、思考过程折叠、`AgentWorkSteps.kt`、`AgentWorkProcess.kt`、`AgentToolActivity.kt`、`AgentStatusCard.kt`、复制和长按菜单；
  - 加载状态 `AgentConversationLoading.kt`；
  - Markdown：把 `ui/markdown/`（13 个文件）的解析部分移植到 `core/eta/markdown/`，替换 `core/markdown.ts`，渲染部分移植到 `ui/eta/markdown/`。流式输出时用稳定的 key（原 T05 并入这里）。
- **E04c 排队、编辑与重新生成**：移植 `AgentChatQueueStrip.kt`，并把 L1、L4 已经移植的 `AgentMessageQueueReducer`、`AgentConversationRevisionReducer`、`AgentRunEventCoalescer`、`AgentRunMessageProjector` 接入聊天界面（原 T06 并入这里）。
- 与现有 `chatRuntime` 对接时，保持“先持久化再发送、停止保留内容、不自动重发”的规则。
- **真机检查点 ②**：
  - 首页与 `docs/Screenshots/chat_home.jpg` 并排截图对照；
  - 约 3000 字、包含表格和代码的回复，流式过程不闪烁；
  - 生成中继续发消息会排队；编辑重发、重新生成正确；
  - 杀掉进程重开后数据一致。

#### E05 设置页与外观

- 设置页按 ETA_PORT §7 逐行移植 `SettingsScreen.kt`：A 类可用，B 类灰色并显示“鸿蒙版尚未接入”，C 类隐藏。
- `AppearanceSettingsScreen.kt`：跟随系统 / 浅色 / 深色等鸿蒙能实现的选项；Monet 取色隐藏（原 T08 并入这里，包括大字体检查）。
- “关于”：版本号、基于 Eta 的说明、许可证和 Required Notice、诊断入口（M0 的诊断页改用 Eta 组件重新绘制）、反馈、GitHub。
- **真机检查点 ③**：与 `settings.jpg` 对照截图；系统深色模式和最大字体下截图设置页。

#### E06 模型提供商

- 移植 `pages/providers/` 的 11 个文件：
  - 列表；
  - 详情（配置、模型两个标签页）；
  - 新建（OpenAI 兼容、Anthropic、Gemini 三种类型；在 T09 实现对应协议之前，后两种为 B 类）；
  - 连接字段、自定义请求头编辑器、远程模型导入弹层；
  - 社区目录（models.dev）。
- 账号登录控件（`ProviderAccountControls.kt`）暂时为 C 类，放到第五节“验证后决定”。
- 继续遵守 0.9 的编辑规则：独立草稿；测试连接和获取目录不会保存；旧请求作废。
- **数据迁移**：0.9 已经保存的服务、Key 和模型列表必须能在新界面看到，迁移逻辑要配单测。
- **真机检查点 ④**：旧服务仍然在；新建服务 → 测试 → 导入模型 → 保存，整个过程表单不被清空；与 Eta 的提供商页面结构对照。

#### E07 会话列表与历史

- 侧栏内容照抄 `ConversationPanePanel.kt`：按时间分组（`ConversationTimeLabels`）、搜索、重命名、删除，Eta 有归档的话也照做。
- 导出 Markdown：使用 L1 已经移植的 `ConversationMarkdownExporter`。
- 时间分组使用 L1 的 `ConversationTimeLabels`；`AgentConversationStoreTest` 等其余相关单测在这里移植。
- **真机**：在 E09 一起测。

#### E08 二级页面（一个页面一个 PR）

按顺序移植：记忆 → Skills → MCP 服务器 → 角色（5 个页面）→ Subagent → 工具能力与网页工具设置 → 数据备份 → 语音（只做界面，B 类）→ 权限（改为鸿蒙权限项）。

- 每个页面的界面、本地数据管理（新增、编辑、开关、导入、导出）都要按 Eta 实现。旧鸿蒙代码中已有的 `core/mcp.ts`、`backup.ts`、`extensions.ts` 可以参考。
- **让功能真正影响对话**（例如记忆注入提示词、MCP 和 Skills 作为工具被模型调用）属于另外的任务。在那之前，相应页面顶部显示一条提示：“已保存；鸿蒙版对话暂未使用此功能”。其中**记忆注入**比较简单，可以在本任务直接做。
- 工具能力页的卡片按鸿蒙实际能力显示：手机操作类工具在 M4、M5 之前为 B 类。
- **真机**：在 E09 一起测。

#### E09 M1 验收与清理（发布 0.10.0）

- 删除 `ui/foundation/` 旧页面，以及不再使用的旧 `components/`。
- 汇总所有页面的“与 Eta 的差异”，写入 `docs/ETA_PORT_DIFF.md`。
- 在 `docs/device-runs/TEMPLATE.md` 中补充每个 Eta 页面的检查项。
- **真机检查点 ⑤（闸门 G1）**：
  - 四张官方截图逐张对照；
  - 每个 A 类页面走一遍；
  - 确认 B 类入口都是灰色、C 类入口都看不到；
  - 深浅色和大字体检查；
  - 覆盖安装后旧数据仍然在。

---

### M2 · 0.11「聊天能力补齐」

开始前 M1 已完成，以下任务在 Eta 移植后的界面上实现。

#### T04 切到后台不中断回复

- 现状：`EntryAbility.onBackground` 调用 `runtime.setForeground(false)`，立即停止所有回复。
- 改为：进入后台时如有回复正在进行，用 `backgroundTaskManager.requestSuspendDelay` 申请短时任务；全部回复结束后立即 `cancelSuspendDelay`；收到即将超时的回调时停止回复并保留已收到内容，提示“后台时间已用完”。回到前台时释放短时任务。
- 在 `core` 中抽出 `SuspendPort` 接口，状态逻辑可以单测；平台实现参考旧的 `platform/Background.ets`。不新增权限。
- **自动验收**：用假端口测试五种情况：后台继续回复、全部完成后释放、超时停止并保留文本、回到前台释放、申请失败时按原逻辑停止并提示。
- **真机验收**：问一个需要长回答的问题，生成过程中回到桌面 30 秒再返回，回复完整、状态为已完成；诊断页能看到申请和释放记录。

#### T05 消息渲染与流式性能（已并入 E04b，不单独执行）

- `components/MessageBody.ets` 的 `ForEach` 键值包含整段文本，流式时每帧都重建全部块。改为稳定键值（序号 + 类型），只让最后一块随流式变化。把该组件迁到 `ui/foundation/`，不再依赖旧的 `StorageLink('darkTheme')`。
- `core/markdown.ts` 补充：有序 / 无序列表、链接（点击前确认再用系统浏览器打开）、分隔线、代码块语言标签；流式中未闭合的代码块按代码块显示。
- 超长回复（2 万字以上）不卡顿：对消息列表使用 `LazyForEach` 或分段渲染。
- **自动验收**：markdown 单测覆盖列表、链接、未闭合代码块、表格和中文标点。
- **真机验收**：让模型输出一篇约 3000 字、包含标题、列表、表格和代码的回复，流式过程不闪烁、滚动顺畅，代码块可以复制。

#### T06 消息操作（已并入 E04c，不单独执行）

- 消息长按菜单或下方操作行：复制全文、重新生成（仅最后一轮）、编辑后重发（仅最后一轮，文字回填到输入框）、删除本轮。
- 在 controller 中增加对应意图；`ConversationRepository` 支持替换或删除最后一轮。生成中禁止这些操作。重新生成要覆盖最后一轮的回复，不能重复追加用户消息。
- **自动验收**：逐项单测，包括生成中被拒绝、操作后重启数据一致。
- **真机验收**：逐项操作一遍，然后杀掉进程重开，确认结果仍然正确。

#### T07 错误分类、超时与手动重试

- 在模型层把失败归类：无网络、连接或 DNS 失败、TLS 错误、超时（首字节 60 秒、流中断 90 秒）、401 / 403（Key 无效）、404（地址或模型不存在）、429（被限流）、5xx，以及服务端返回的错误信息（解析 `error.message`）。
- 用中文说明原因和建议的操作；失败消息下方提供“重试”，等同于对该轮重新生成（复用 E04c 移植的重新生成逻辑）。**不要自动重试。**
- **自动验收**：用本地 HTTP 服务逐类测试；超时使用假时钟。
- **真机验收**：分别填错 Key、断网、填错模型名称，确认各自显示对应的原因。

#### T08 深色模式与大字体（已并入 E01、E05，不单独执行）

- 把硬编码颜色改为颜色资源（`resources/base` 与 `resources/dark` 下的 `color.json`），跟随系统深浅色；系统栏颜色也随之切换。
- 在字体缩放 1.75 和 2.0 下检查首页卡片、输入区、服务表单，不出现截断或重叠。
- **真机验收**：系统切换深色模式并把字体调到最大，截图首页、聊天页、服务页各一张。

#### T09 其他模型协议（一个协议一个 PR）

- 配置中已经有 `protocol` 字段，目前只实现了 `chat_completions`。用户使用 OpenAI 的模型（ChatGPT 6.0 Astra），顺序为：**OpenAI Responses → Anthropic Messages → Gemini**。
- 先用现有 Chat Completions 在真机测试用户的模型（G0 闸门中完成）。如果它可用，Responses 主要是为了推理内容和新特性；如果不可用，T09 提前到 M2 的第一项。E06 已经移植了选择协议类型的界面，T09 只需实现协议本身，并把对应类型从 B 类改为可用。
- 注意：App 需要 OpenAI 平台的 **API Key**（或兼容服务的 Key）。ChatGPT 会员订阅本身不提供 API 额度；用订阅账号登录属于第五节的“验证后决定”项，不在 T09 范围内。
- 每个协议都包括：请求构建、SSE 解析（含 reasoning / thinking 内容）、模型目录、连接测试和错误信息解析。服务配置页可以选择协议。
- **自动验收**：每个协议用固定的 SSE 样本做解析测试，并用本地 HTTP 服务做端到端测试。
- **真机验收**：用你真实的账号对每个协议完成一轮测试 → 获取目录 → 聊天 → 停止。

**闸门 G2（发布 0.11）**：完整跑一遍真机模板；连续日常使用 3 天，期间没有回复丢失、表单被清空或会话串内容的问题。

---

### M3 · 0.12「手机操作：连接与读屏」

前提：G2 已通过。从这一阶段起，原生 HDC 库重新进入构建。

#### T10 原生库交付方式 + 本机无线调试连接

- **原生库**：在装有 HarmonyOS 命令行工具的 Linux 环境用 `scripts/prepare-native.sh` 构建 arm64 `libagent_hdc.so`，放入 `entry/libs/arm64-v8a/` 并入库，同时记录 SHA-256 和构建来源，提供重新构建的脚本说明。这样你在 Windows 的 DevEco 中无需交叉编译。公开的 OpenHarmony 7.0 SDK 也有 native 组件（含 aarch64 交叉编译器），可能可以替代 HarmonyOS 命令行工具来构建这个库，到 T10 时先验证。如果 Codex 环境做不到，这一步交给 Claude，Codex 只负责 ArkTS 部分。
- **连接页**：设置 → “手机操作”。引导开启开发者选项和无线调试 → 填写地址和端口 → 连接 → 手机弹出授权 → 显示已连接，以及 shell 身份和 UITest 是否可用。
- **状态机** `core/foundation/deviceLink.ts`：未配置 / 连接中 / 等待授权 / 已连接 / 已断开（附原因）。错误按阶段区分：原生库加载、TCP 连接、授权、协议握手、UITest 不可用。原生模块只在进入此页时懒加载。
- 只做连接和“测试读屏”按钮（显示控件数量和分辨率），**不接入聊天**。迁移参考 `core/device.ts`、`platform/AppRuntime.ets` 和 `cpp/`。
- **自动验收**：状态机、超时、迟到的连接结果被作废（使用假 bridge）；`tests/wire_test.cpp` 仍然通过。
- **真机验收**：断开电脑，只在手机上完成连接和授权；测试读屏得到的控件数大于 0；关闭无线调试后页面显示断开原因；诊断报告中有各阶段的记录。

#### T11 聊天中的“手机模式”与读屏工具

- 输入区增加“手机模式”开关，默认关闭，只有设备已连接时才可开启。开启后只向模型注册**只读**工具 `observe_screen`：输出精简后的控件树（最多 120 个节点），密码类控件的文字隐去，截图默认关闭。
- 普通聊天仍然不携带任何工具（现有测试必须继续通过）。
- 依赖 T04：观察其他应用时，助手处于后台，需要短时任务保持运行。
- **真机验收**：开启手机模式后说“回到桌面后告诉我第一屏有哪些应用”，然后自己切到桌面；回答与实际屏幕一致，助手没有执行任何点击。

#### T12 执行时间线

- 在回复上方显示可折叠的步骤卡：思考、工具、结果、失败。复用已有的步骤持久化（只保存工具名、时间和状态，不保存原始参数和截图）。
- **真机验收**：读屏任务完成后可以展开步骤；重启后步骤记录仍然存在。

**闸门 G3**：按 `DEVICE_VALIDATION.md` 中的“手机独立连接、页面读取、内外屏切换、断连”四项完成真机测试。

---

### M4 · 0.13「手机操作：执行」

#### T13 点击、滑动、输入、返回、回到桌面

- 接入旧 `loop.ts` 中的观察 ID 和页面指纹校验：每个动作必须基于最新一次观察，动作后自动重新观察；观察过期则拒绝执行。
- 文字输入要正确处理中文、引号和 shell 特殊字符（已有测试，迁移后保持通过）。
- 动作结果未知时（连接断开、退出码缺失）报告“结果未确认”，**不要重放**。
- **真机验收**：“打开设置，找到显示设置，不修改任何选项”；在无账号的搜索框中输入带引号的中文。

#### T14 打开应用与应用目录

- 通过已授权的 HDC 查询已安装应用列表并缓存（应用名 → bundle / ability）；新增 `open_app` 工具，打开后确认目标应用已在前台。
- **真机验收**：“打开计算器”“打开设置”，都能正确打开并报告真实结果。

#### T15 用户接管与停止

- 任务进行中切回助手时自动暂停，显示“继续 / 停止”；停止后迟到的模型响应不再触发任何动作。
- 申请通知权限（用户拒绝时降级为仅在应用内显示），任务进行中显示一条进度通知，带“停止”按钮。参考 `BACKGROUND.md` 中的 nonce 校验设计。
- **真机验收**：按 `DEVICE_VALIDATION.md` 中的“用户接管、停止、断连”逐项测试。

**闸门 G4**：跨应用低风险任务连续 10 次，记录成功率、平均耗时和失败原因，写入 `docs/device-runs/`。

---

### M5 · 0.14「后台长任务」

#### T16 定位类持续任务（实验开关，默认关闭）

- 迁回旧的定位持续任务实现：手动开启，删除通知即停止，撤销权限即停止。坐标不进入模型，也不保存。
- 设置页醒目说明：这是实验功能，可能耗电，系统可能随时终止。

#### T17 留存和耗电实测

- 不是编码任务。由你按 `DEVICE_VALIDATION.md` 中的“持续运行与功耗”测试 30 分钟和 2 小时两种场景，记录实际存活时长和耗电，再决定这个模式是否保留。

---

## 五、之后的扩展（按顺序，每项一张新任务卡）

这些功能的**界面**已经在 M1 中按 Eta 移植（B 类入口显示为灰色）。下面是让它们**真正生效**的顺序：

1. 网页搜索与抓取（用户自己配置搜索 API），以及内置浏览器（ArkWeb）
2. 图片和文件附件（系统选择器，不需要相册权限）
3. MCP 与 Skills 作为模型工具（需要对话支持工具调用）
4. Subagent 子任务
5. 语音输入与播报
6. 工作区文件（应用沙箱）
7. 订阅账号登录（例如 ChatGPT 账号）：先确认服务方是否允许第三方客户端，再决定是否做

每项都需要明确入口，以及加载中 / 成功 / 失败 / 取消 / 权限被拒 / 离页六种状态，并在真机通过后再勾选。

## 六、明确不做

| 不做 | 原因 |
| --- | --- |
| 替换小艺的电源键长按 | 没有普通应用可用的公开接口，见 `POWER_KEY.md` |
| 后台虚拟屏执行 | `ACCESS_VIRTUAL_SCREEN` 需要特殊签名资格；拿到合法 ACL 前不做，见 `VIRTUAL_SCREEN.md` |
| 读取全局通知、使用统计、全量通讯录或相册 | 需要 `system_basic` 及以上权限 |
| Root、Hook、系统签名、伪造定位 | 超出普通用户权限范围 |

## 七、风险

| 风险 | 应对 |
| --- | --- |
| `.ets` 检查器不等于完整构建（不生成 HAP，不打包资源） | 每个 PR 都有 `npm run check:ets` 和 CI 拦截类型与 ArkTS 规则错误；资源和打包问题仍以你的 DevEco 编译为准，报错原样贴回 |
| HarmonyOS 7 对无线调试或 HDC 加密协议有变化 | M3 先只做连接和读屏，问题在 T10 中暴露，不影响聊天功能 |
| Eta 移植工作量大（界面代码约 3.7 万行 Kotlin） | 按页面拆成小 PR；资源用脚本生成；逻辑连同 Eta 单测一起移植，用单测证明行为一致；只在 5 个真机检查点停下来测试 |
| ArkUI 做不到与 Miuix 完全一样（连续圆角、系统字体） | 记入差异清单，由你决定是否接受 |
| 短时任务每日额度不足以完成长任务 | T04 提示真实原因；M5 再评估持续任务方案 |
| 签名不一致导致无法覆盖安装、丢失数据 | T03 说明沿用旧签名的方法，禁止用卸载来解决 |
