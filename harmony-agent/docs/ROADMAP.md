# 随身助手推进方案（0.9.1 → 0.14）

**2026-10-31 起执行者换为 dsh。** 流程见 [EXECUTOR_GUIDE.md](EXECUTOR_GUIDE.md)：
- Claude 写任务卡并验收；
- dsh 实现，并在预览器里自测；
- 用户合并 PR，只在真机检查点测试。

本轮任务见第二节“第六轮”；第四、五轮的验收结果在它前面。

当前分支 `harmony/ui-preview-tests`（0.9.33，PR #37），之前的 PR 链都叠在它下面。下一步：用户真机测 0.9.33，然后把整条链一次合并进 `main`。

历史报告：[第二轮](reports/2026-10-31-report.md)、[第三轮](reports/2026-10-31-round3.md)。

制定：2026-10-30。基于 `main@aadbf93`。执行规则见 [../AGENTS.md](../AGENTS.md)。

## 当前进度

| 任务 | 状态 | 说明 |
| --- | --- | --- |
| ArkUI 启动白屏修复（0.9.32） | 代码与四项检查通过，真机启动待测 | 用户已安装 0.9.31，但 BuilderParam 使首页 this 指向侧栏。显式闭包修复 99 处同类传递；10 项新增接收者回归，从 #16 同步下游；[修复记录](ARKUI_BUILDER_RECEIVER_FIX.md) |
| T01–T03（M0 / 0.9.1） | **代码完成，待真机** | Claude 完成，PR #8（分支 `harmony/m0-0.9.1`），三个任务合在一个 PR 里，方便一次真机测完。200 项测试通过；ArkTS 检查 0 错误（OpenHarmony SDK 26.0.0.38）；未生成 HAP |
| 无头界面测试 | 完成（Claude） | `npm run test:ui`：不依赖 hvigor 的预览编译 + OpenHarmony Linux 预览器 + `tests/ui/` 场景；CI 任务 `Harmony / UI preview tests`，上传截图。首次运行即发现 0.9.32 顶栏和输入栏因 `bindMenu/bindPopup($$this)` 运行时报错而不显示，已在同一分支修复（0.9.33）。说明见 `scripts/ui-preview/README.md` |
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
| E04b 修复（0.9.31） | DevEco 10905201 反馈修复，重新编译待用户验证 | ListItem 底部 1vp 锚点改为合法 Row；SDK 父容器校验 + 7 回归；从 #19 同步下游，[修复记录](DEV_ECO_10905201_FIX.md) |
| E04c（0.9.15） | 排队与消息操作接入、四项检查通过 | `harmony/e04c-chat-actions` 基于 E04b；491 测试、5 原测试 / 0 跳过、13 集成测试、0 ArkTS error、1,863 资源；[交付记录](E04C_CHAT_ACTIONS_PORT.md) |
| E07a（0.9.16） | 侧栏/导出完成，存储对照留 E07b | 503 测试，0 ArkTS error，1,864 资源；9 项原 Store 测试的逻辑适配 + 3 新验证；完整 Room 对照未完成，[交付记录](E07A_CONVERSATION_PANE_PORT.md) |
| E07b（PR #22 原分析） | 用户已确认方案 A；E07b3 待真机检查点 ①–④ | [原分析与决定](E07B_STORAGE_DECISION.md)；实施按下面 E07b1 / E07b2 分开交付 |
| E07b1（0.9.19） | 逻辑与端口完成、四项通过；未切换 | 588 测试、ArkTS 0 error、1,935 资源；44 项原测试逻辑移植 / 0 跳过，另 4 边界测试；[交付记录](E07B1_STORE_PORT.md) |
| E07b2（0.9.20） | RDB / 迁移代码完成、四项通过；未切换 | 602 测试、ArkTS 0 error、1,935 资源；新增 14 项 SQL / 迁移测试，原测试新增 0 / 跳过 0；[交付记录](E07B2_RDB_MIGRATION.md) |
| P1a（0.9.21） | Responses 接入、四项通过 | 663 测试、ArkTS 0 error、1,935 资源；51 原测试 / 0 跳过，另 10 适配验证；订阅登录仍 C；[交付记录](P1A_RESPONSES_PORT.md) |
| P1b（0.9.22） | Chat Completions 接入、四项通过 | 698 测试、ArkTS 0 error、1,935 资源；30 原测试 / 0 跳过 + 5 适配验证；[交付记录](P1B_CHAT_COMPLETIONS_PORT.md) |
| E05（0.9.17） | 设置与外观页面接入、四项通过 | 512 测试、0 ArkTS error、1,879 资源；默认思考/自动压缩等目标仍禁用，[交付记录](E05_SETTINGS_APPEARANCE_PORT.md) |
| E05b（0.9.23，P1c 补齐） | 开关/运行时接入、四项通过；原测试已补齐 | 741 测试、ArkTS 0 error、1,935 资源；35 原测试 + P1c 补 3 原测试、8 集成；独立旧库检查点保留原文；[交付记录](E05B_THINKING_COMPACTION_PORT.md) |
| E06（0.9.18） | 页面与主要路径接入、四项通过；完整对齐未验收 | 540 测试、0 ArkTS error、1,935 资源；12 原测试完整 / 4 平台部分 / 11 C 类排除；内置种子等差异见 [交付记录](E06_PROVIDER_PORT.md) |
| E06b（0.9.24） | 内置服务/目录/能力编辑完成、四项通过 | 757 测试；9 原测试 / 0 跳过 + 7 适配验证；ArkTS 0 error、资源通过；[交付记录](E06B_CATALOG_CAPABILITIES_PORT.md) |
| P1c（0.9.25） | Anthropic 接入、四项通过 | 781 测试；15 原测试 + E05b 补 3 / 0 跳过 + 6 适配；ArkTS 0 error、1,940 资源；[交付记录](P1C_ANTHROPIC_PORT.md) |
| P1d（0.9.26） | Gemini 接入、四项通过 | 831 测试；42 原测试 / 协议跳过 0 + 8 适配；另 10 完整平台/工具集成待后续；ArkTS 0 error、1,941 资源；[交付记录](P1D_GEMINI_PORT.md) |
| E08a（0.9.27） | 记忆页面/本地管理完成、四项通过 | 846 测试；9 原测试 / 0 跳过 + 6 适配；ArkTS 0 error、1,943 资源；对话未使用，[交付记录](E08A_MEMORY_PORT.md) |
| E08b1（0.9.28） | Skills 安装/恢复核心完成、四项通过 | 887 测试；33 原测试 / 1 Loader 相关跳过 + 8 适配；ArkTS 0 error、1,943 资源；平台/页面留 E08b2，[交付记录](E08B1_SKILL_INSTALLER_PORT.md) |
| E08b2（0.9.29 / #35） | 草稿-阻塞，第三次检查仍失败 | 903 测试通过；`EtaSkills.ets:41` 的构造器参数属性不被 ArkTS 接受（1 error）；资源未运行。后续从 #34 分支跳过它 |
| E08c（0.9.30 / #36） | MCP 代码完成、四项通过 | 919 测试；ArkTS 0 error、1,946 资源；14 原测试 / 2 完整模型 executor 延期 + 18 适配；无模型调用注册；[交付记录](E08C_MCP_PORT.md) |
| E08 后续 / E09 | 本轮未开始 | 角色、Subagent 等留后续；M1 仍未验收 |
| M1：E01–E09（Eta 界面与交互 1:1 移植） | **进行中** | 规范见 [ETA_PORT.md](ETA_PORT.md)。由执行者按顺序执行，开工条件见 M1 |
| T04 起（M2–M5） | 未开始 | 在 M1 完成后执行 |

每完成一个任务，执行者更新本表。

## 一、现状判断

1. **代码领先于真机验证。** 0.9 的框架代码和 193 项测试都过了，但新框架从未在手机上运行过；最近一次真机反馈还是 0.3。最大的风险不是功能少，而是没有稳定的“改代码 → 上真机 → 回传问题”循环。
2. **仓库源码目前无法在 DevEco 直接编译。** `entry/build-profile.json5` 启用了原生构建，`entry/src/main/cpp/CMakeLists.txt` 在 `vendor/` 缺少预编译 OpenSSL 时会 `FATAL_ERROR`，而 `vendor/` 没有入库，只能在装有 HarmonyOS 命令行工具的 Linux 上生成。0.9 入口其实不加载这个原生库。
3. **聊天还不能日常使用。** 切到后台会立即停止回复（`EntryAbility.onBackground`）；流式渲染时每帧重建整条消息；没有复制、重新生成、错误重试、深色模式；只支持 OpenAI Chat Completions 协议。
4. **界面与交互和 Eta 差距大。** 0.9 只借鉴了 Eta 的一部分页面，质量也不够。用户决定改为**照着 Eta 源码 1:1 移植**（M1）。原来计划的消息渲染、消息操作、深色模式三项（T05、T06、T08）并入移植任务。
5. **手机自动化的路线已有原型。** 方案是应用内 HDC 客户端连接本机无线调试，0.3 已在真机读到 103 个控件。这是本项目区别于普通聊天应用的核心，但在 0.9 中暂时撤下了，需要按步骤接回。
6. **（2026-10-31）17 个 PR 还没上过真机。** 0.9.1 → 0.9.18 都只有自动检查。当前最大的风险是第一次 DevEco 编译或运行时集中暴露问题，所以真机测试优先于继续叠加界面。

## 二、分工与循环

| 谁 | 做什么 |
| --- | --- |
| Claude | 写任务卡；验收 PR（重跑界面测试、看截图、审代码）；处理工具链和难点 |
| 执行者（dsh，之前是 Codex） | 按任务卡写代码、单测、界面场景；`npm run verify` 全过才开 PR；按验收意见修改 |
| 你 | 合并验收通过的 PR；到真机检查点时用 DevEco 编译安装，按清单测试，把结果和诊断报告贴回 |

```
任务卡 → dsh 实现并在预览器自测 → 开 PR → Claude 验收 → 你合并
真机：只在检查点集中测一次，问题先由 dsh 在预览器复现再修
```

规则：

- **一次只做一个任务。** 上一个 PR 合并后再开始下一个，避免多个未验证的改动叠在一起。M1 的 PR 在 CI 全绿后即可合并，只在标注的真机检查点停下来测试；其他里程碑的 PR 要真机通过后才合并。
- **里程碑闸门。** 每个里程碑末尾有一轮完整真机验收；不通过就不进入下一个里程碑。
- 每个合并的 PR 升一次补丁版本号，方便你在手机上确认装的是哪一版。

### 无人值守连续执行（你不在时）

你不在的时候没人合并 PR、也没人测真机。执行者按“本轮队列”一个接一个做，规则如下（第四轮起）：

1. **分支**：
   - 互不依赖的任务，各自从 base 拉分支（见本轮队列），PR 的 base 也设为它；
   - 依赖一个还没合并的任务时，从那个任务的分支拉，PR 的 base 设为那个分支；
   - 不合并任何 PR，不推送 `main` 和 base 分支，只推送自己任务的分支。
2. **通过才前进**：`npm run verify` 全部通过（或者本地跑不了预览器时 `--no-ui` 通过，并且 CI 的三个 Harmony 任务全绿），才能把 PR 从草稿改为可审查，再开始下一个任务。
3. **卡住就跳过**：
   - 一个任务认真尝试 3 次仍无法通过检查，或者遇到必须由你决定、而且会影响后续的问题时，PR 保持草稿，写明“阻塞原因”；
   - 然后跳到队列中**不依赖它**的下一个任务。
4. **小决定自己做**：不影响后续的小问题，选最接近 Eta 源码的做法，写进 PR 的“待用户确认”一节，继续往下做。
5. **不冒充真机结果**：真机检查点只列出步骤，标为“待测”。
6. **PR 不要太大**：生成的资源文件不计，手写代码超过约 1500 行时拆成多个 PR。
7. **结束时的报告**：队列做完或全部阻塞时停下，输出一份报告，格式见下面“2026-10-30 夜间”队列末尾的“早上的报告”。

### 第五轮验收结果（Claude，2026-11-01）

| PR | 任务 | 结论 | 说明 |
| --- | --- | --- | --- |
| #40 | R5-1 | **通过** | `14-message-actions` 转正。`09`、`13` 受列表 key 影响，留到 R4-4 |
| #43 | R5-2 | **通过**（Claude 补了 3 处小修） | 编译错误全部修好，扩展文件已在检查范围内，计数逻辑正确。Claude 补的三处：读窗口失败单独记为 `readError`，不再覆盖注入结果；处理完注入请求后立即写文件（原来的 1 秒节流可能丢掉结果）；避免读取重叠 |
| #44 | R5-3 | **通过** | |

**全部 7 个 PR 已集成到 `harmony/round4-integration`**：解决了版本号和状态文档的冲突，版本 0.9.36。`npm run verify` 全部通过：986 项单测，17 个界面场景。用户只需合并这一个 PR，7 个 PR 会自动标记为已合并。

**重要：D0a 的预期要降低。** 公开 SDK（API 26）中，第三方无障碍扩展的全部接口自 API 12 起都标为弃用，而且没有公开的替代接口，包括：
- 生命周期回调 `onConnect`、`onAccessibilityEvent`；
- 读屏 `getWindowRootElement`、`attributeValue`；
- 查找 `findElement`；
- 手势注入 `injectGestureSync`。

新接口很可能只对系统应用开放。真机上服务可能不出现在设置里，或者开启后收不到事件。实验成本很低，仍然做，结论以检查点 ⑥ 为准；但 **HDC 通道（D0b）很可能是主通道**，所以 Claude 的 D0b2 提前。

### 第六轮队列（dsh）

**base**：用户合并集成 PR 后，从 `harmony/ui-preview-tests` 拉分支；还没合并时，从 `harmony/round4-integration` 拉。

| 顺序 | 任务 | 说明 |
| --- | --- | --- |
| 1 | R4-4 列表 key 审计（0.9.38） | 任务卡见下面第五轮的 R4-4。`09-provider-edit`、`13-sidebar` 通过后转正 |
| 2 | R5-4 `Rect` 去重 | D1a、D1b 已经集成，可以做了 |
| 停 | 等 Claude 的 D0b2，然后做 D0b3；全部完成后用户测检查点 B | |

### 第四轮验收结果（Claude，2026-10-31）

| PR | 任务 | 结论 | 说明 |
| --- | --- | --- | --- |
| #38 | R4-1 | **通过**（Claude 接手完成） | dsh 的帧时钟兜底保留。根因是 `ForEach` 复用：`Span` 和 Markdown 块按偏移作 key，内容变了子项也不更新，所以回复看不见、段落停在第一段。另外预览器没有 `taskpool`。修复见 PROJECT_STATUS 0.9.34 |
| #39 | R4-2 | **通过** | 作为场景基线，和 #40 一起合并 |
| #40 | R4-3 | **小改后通过** | 两处修复正确（模型页 `@Builder logo`、预览外观端口）。三个 pending 场景失败的原因不是应用问题，见下面 R5-1 |
| #41 | D1a | **通过** | 20 项原测试逐条对应 |
| #42 | D1b | **通过** | 18 项原测试逐条对应；`Rect` 重复定义在两者合并后清理（R5-4） |
| #43 | D0a | **退回** | 扩展文件有 9 处编译错误。旧的 `check:ets` 没有检查 `srcEntry`，现已补上。另有一处会造成假阳性的实验逻辑错误。见 R5-2 |
| #44 | D0b1 | **小改后通过** | 文件头不应写 Eta 的版权（这是原创代码，不是移植）。见 R5-3 |

**本轮发现的工具问题（Claude 已在 `harmony/ui-preview-tests` 修复）**：
- `check:ets` 现在也检查 `module.json5` 中所有 `srcEntry`；
- `npm test` 改为 `scripts/run-tests.cjs`，自动收集 `tests/` 下所有 `*.test.ts`，以后不用再改测试脚本；
- 选择器只在最上层页面里找：被压住的页面（例如侧栏、上一级列表）不再参与匹配；
- 新增 `tapAt`，用于点系统对话框（它们不在控件树里）；
- 预览器原生崩溃（无应用错误）时自动重跑一次；
- `run.sh` 按场景的 `name` 找报告目录。

### 本轮队列（第五轮，dsh）

**所有分支先合并 `harmony/ui-preview-tests` 的最新提交。** 冲突处理：
- `package.json` 的 `test` 脚本取 base 的 `node scripts/run-tests.cjs`；
- 版本号取较大的那个。

| 顺序 | 任务 | PR | 版本 |
| --- | --- | --- | --- |
| 1 | R5-1 R4-3 补改 | #40 | 不变（0.9.35） |
| 2 | R5-2 D0a 返工 | #43 | 不变（0.9.36） |
| 3 | R5-3 D0b1 小改 | #44 | 不升 |
| 4 | R4-4 列表 key 审计 | 新 PR，从 R4-1 分支拉（需要 `renderKey.ts`） | 0.9.38（0.9.37 留给 D0b3） |
| 5 | R5-4 `Rect` 去重 | D1a、D1b 合并之后，新 PR | 不升 |
| 停 | 等 Claude 的 D0b2，然后做 D0b3；全部完成后用户测检查点 B | | |

#### R5-1 R4-3 补改（#40）

先合并 base，然后按下面修改三个 pending 场景。全部通过后移到 `tests/ui/`。

1. **`13-sidebar`**：
   - 最后一步错了：已经有对话时，首页不会再显示“有什么可以帮你”。改为断言 `hello` 可见。
   - 补上任务卡要求的两步：清空搜索后列表恢复；长按会话出现菜单，`back` 关闭。
2. **`14-message-actions`**：
   - “删除”按钮要等回复完成才出现，先 `{"waitFor": {"a11y": "删除"}, "timeout": 15}`；
   - 确认对话框是系统对话框，不在控件树里：去掉 `waitFor 删除消息？`，改为 `screenshot` 后 `{"tapAt": [0.65, 0.54]}` 点“删除”（坐标已在预览器中验证），再 `expectMissing`；
   - 补上长按用户气泡边缘（`"offset": [-52, 0]`）→ 点“编辑” → 输入框进入编辑状态。
3. **`09-provider-edit`**：
   - 详情页默认就在“配置”页，点“配置”当然没有变化。去掉这一步，直接改“名称”输入框（输入英文）→ 返回 → 列表显示新名称。
   - 如果列表不显示新名称，这是 R4-4 要修的列表 key 问题。场景留在 `pending/`，在 R4-4 里修。
4. `08-providers` 偶发失败是预览器原生崩溃（`TextFieldPattern::GetInspectorId`），合并 base 后会自动重跑，不用改。

#### R5-2 D0a 返工（#43）

先合并 base，此时 `npm run check:ets` 会报出扩展文件的 9 处错误。逐条按 SDK 的接口修：

1. **前台包名**：`AccessibilityEvent` 没有 `bundleName`。改为 `await root.attributeValue('bundleName')`，`root` 来自 `getWindowRootElement()`。
2. **子节点**：`AccessibilityElement` 没有 `children` 属性。改为 `await node.attributeValue('children')`。计数改成 `async`，仍然最多数 500 个。
3. **本应用包名**：`AccessibilityExtensionContext` 没有 `abilityInfo`。改为 `this.context.applicationInfo.name`。
4. **找按钮**：`findElement('content', …)` 返回的是**数组**。取第一个，数组为空时结果写 `not-found`。
5. **位置**：`target.rect` 不存在。改为 `await target.attributeValue('rect')`。返回的 `Rect` 是 `{left, top, width, height}`，中心点是 `left + width / 2`、`top + height / 2`。
6. **手势**：`GesturePath` 没有 `addPoint`。改为 `path.points.push(point)`。
7. **类型**：去掉所有 `any` / `unknown`，给每个 `then` / `catch` 的参数写明类型。
8. **实验逻辑错误（重要）**：
   - `DiagnosticsPage.refreshProbe` 在扩展报告 `ok` 时也给点击次数加一。这样即使注入没有真正点到按钮，计数也会增加，实验结论会是假阳性。
   - 点击次数**只能**由测试按钮的 `onClick` 增加。“注入结果”单独显示。
9. **文案**：诊断区的文字全部改为 `$r('app.string.*')`。测试按钮的文字和扩展里查找用的文字放在 `core/eta/device/accessibilityProbe.ts` 的同一个常量里，避免两边不一致。
10. **检查点文件**：`docs/CHECKPOINT-B.md` 移到 `docs/device-runs/CHECKPOINT-B.md`。
11. **文件头**：`AgentAccessibility.ets` 是原创代码，不要写 Eta 的版权行。

**验收**：
- `npm run check:ets` 为 0 error（现在包含扩展文件）；
- `19-a11y-probe` 场景断言：点测试按钮后次数加一；点“请求注入点击”后次数**不变**（预览器里没有扩展）。

#### R5-3 D0b1 小改（#44）

- `hdcConnection.ts` 和测试文件是原创代码，去掉 `Copyright © 2026 蛮吉 (Mangi-11)` 那一行，改为一句说明“随身助手原创代码”。
- 合并 base。

#### R4-4 列表 key 审计（0.9.38）

**背景**：
- ArkUI 的 `ForEach` / `LazyForEach` 遇到 key 不变的子项会直接复用，不再执行子项构建函数；
- 所以只用 id 作 key、而行内容来自 item 的列表，内容变了界面不会变；
- 规则见 AGENTS.md，工具是 `core/eta/renderKey.ts` 的 `renderKey` 和 `syncSlots`。

**要逐个处理的位置**（行号以当前代码为准）：

| 文件 | 现在的 key | 处理 |
| --- | --- | --- |
| `ConversationPanePanel.ets` | `group.id`、`item.id` | `renderKey(group.id, group)`、`renderKey(item.id, item)` |
| `AgentWorkProcess.ets` | `tool.id`、`step.key` | 行有展开状态：`@Observed` 槽位 + `@ObjectLink` + `syncSlots`，参照 `MarkdownContent.ets` 的 `MarkdownBlockSlot` |
| `AgentChatQueueStrip.ets` | `item.id` | `renderKey` |
| `ModelProviderListScreen.ets` | `p.id` | `renderKey` |
| `ProviderModelsTab.ets` | `model.id` | `renderKey` |
| `ProviderHeadersEditor.ets` | `h.id` | 行里有输入框，**不能**用 `renderKey`（每输入一个字就重建，会丢焦点）。用槽位 + `syncSlots` |
| `McpServersScreen.ets` | `server.id`、下标 | `renderKey` |
| `AgentModelPicker.ets` | `group.providerId`、`entry.key` | `renderKey` |
| `CommunityCatalogScreens.ets` | `p.id`、`model.modelId` | `renderKey` |
| `DiagnosticsPage.ets` | 下标 | `renderKey(index, line)` |

另外用 `grep -n "ForEach(" -r entry/src/main/ets/ui` 把清单补全。key 只是一个基本类型值（字符串、数字本身就是行内容）的，不用改。

**验收场景**（每个都断言“改了之后界面显示的是新内容”）：
- 切换模型后再打开模型弹层，选中标记在新模型上；
- 侧栏长按会话 → 重命名（英文）→ 列表显示新名称；
- `09-provider-edit` 通过：改名后列表显示新名称；
- 请求头编辑：连续输入一段英文，输入框内容完整，焦点没丢；
- 已有场景全部通过。

#### R5-4 `Rect` 去重（D1a、D1b 都合并之后）

`core/eta/device/observation.ts` 删除自己的 `Rect`，改为从 `ScrollGestureContract` 导入。测试数量不变。

### 本轮队列（第四轮，执行者 dsh，2026-10-31 Claude 修订）

**先读** [EXECUTOR_GUIDE.md](EXECUTOR_GUIDE.md)。每个任务按它的流程做：
- 先在预览器里自测，`npm run verify` 全过才开 PR；
- 由 Claude 验收，用户合并；
- 真机只在下表“真机”一栏写了检查点的地方测。

**base**：整条 PR 链合并进 `main` 之后，所有任务从 `main` 拉分支；合并之前，从 `harmony/ui-preview-tests` 拉。互不依赖的任务**各自从 base 拉，不要叠加**。

**版本号**：只有改变手机上安装内容的任务才升版本，按下表的编号，不要自己挑。合并时与 `main` 冲突（通常就是版本号），由 dsh 合并 `main` 解决，取较大的版本号。

| 顺序 | 任务 | 谁 | 依赖 | 版本 | 真机 |
| --- | --- | --- | --- | --- | --- |
| 0 | 真机反馈修复 | dsh | 插队 | 下一个空号 | 先按手册 4.1 在预览器复现 |
| 1 | R4-1 回复文字渐显兜底 | dsh | 无 | 0.9.34 | 检查点 B |
| 2 | R4-2 界面场景补齐（只写场景，不修问题） | dsh | 无 | 不升 | 无 |
| 3 | R4-3 修复 R4-2 发现的问题 | dsh | R4-2 | 0.9.35 | 检查点 B |
| 4 | D1a 设备契约移植（`agent/device`） | dsh | 无 | 不升 | 无 |
| 5 | D1b 工具与无障碍逻辑移植 | dsh | 无 | 不升 | 无 |
| 6 | D0a 无障碍扩展实验 | dsh | 无 | 0.9.36 | 检查点 B（⑥） |
| 7 | D0b1 HDC 连接状态机（纯逻辑） | dsh | 无 | 不升 | 无 |
| — | D0b2 HDC 原生库：arm64 交叉编译并入库 | **Claude** | 无 | — | — |
| 8 | D0b3 手机操作连接页 | dsh | D0b1、D0b2 | 0.9.37 | 检查点 B（⑦） |
| 停 | 用户一次测完检查点 B | 用户 | 以上全部 | — | 然后按闸门 G-D0 决定 D2 的主通道 |

**检查点 B**（一次测完，dsh 汇总成 `docs/device-runs/CHECKPOINT-B.md`）：
- 回复文字逐渐出现；
- 模型弹层和菜单有背景、阴影；
- R4-3 修过的问题；
- 无障碍实验 ⑥；
- HDC 连接 ⑦。

在这之前，用户先单独测一次 0.9.33（PR #37），确认“整个页面不能动”已经解决。

#### R4-1 回复文字渐显兜底（0.9.34）

- **现象**：预览器里回复完成后，正文一直是透明的（`Span` 的 `fontColor` 为 `#00000000`），只看得到复制、重新生成、删除三个图标。验收场景 `tests/ui/pending/05-reply-visible.json` 现在失败。
- **怀疑的原因**：逐帧推进靠 `platform/EtaFrameLoop.ets` 的 `UIContext.postFrameCallback`，预览器里可能不回调。真机上是否正常还不知道。
- **步骤**：
  1. 运行验收场景，确认它失败。
  2. 在 `EtaFrameLoop.onFrame` 里临时加 `console.info('EtaFrameLoop frame')`，重新运行场景，在 `previewer.log` 里查有没有这行。把结论写进 PR，然后删掉这行。
  3. **如果帧回调没来**：把“兜底”逻辑写成纯 TS 的 `core/eta/frameFallback.ts`，计时函数通过参数注入，方便单测：
     - `start()` 之后，如果 100 ms 内没有收到帧回调，就改用 `setTimeout` 每 16 ms 驱动一次 `tick`；传给 `tick` 的秒数用两次调用之间的实际时间差计算；
     - 收到真正的帧回调后，立即停用兜底；
     - `tick` 返回 `false` 或调用 `stop()` 时，清掉定时器。

     `EtaFrameLoop.ets` 只负责把 `postFrameCallback` 和 `setTimeout` 接到这个类上。
  4. **如果帧回调来了、文字仍然透明**：问题在渐显协调器（`revealCoordinator`，`core/eta/` 中）。先写单测复现，再修。
- **单测**（`tests/eta/frameFallback.test.ts`，至少 5 项）：
  - 帧回调正常时不启用兜底；
  - 100 ms 没有帧时启用兜底，`tick` 收到的秒数大于 0；
  - `tick` 返回 `false` 后不再调度；
  - `stop()` 清掉定时器；
  - `stop()` 之后迟到的帧回调或定时器都不再调用 `tick`。
- **只改**：`platform/EtaFrameLoop.ets`、新增的 `core/eta/frameFallback.ts` 和测试；走第 4 步时可以改渐显协调器及其测试。不改 Markdown 的渲染结构。
- **验收**：
  - 场景移到 `tests/ui/05-reply-visible.json` 并通过；
  - 其他场景不受影响；
  - `npm run verify` 全过。

#### R4-2 界面场景补齐（不升版本）

- **目标**：每个已经移植的页面至少一个场景，覆盖主要交互。**这个任务只写场景，不修问题。**
- **只改**：
  - `tests/ui/*.json`；
  - `.ets` 中补 `.id('eta-…')` 或 `.accessibilityText(...)`；
  - `core/foundation/previewFixture.ts` 中补演示数据。
- **场景**（每个一个文件，编号接着往后排；不知道页面上有什么时，先用 `dump` 步骤看）：

  | 场景 | 要断言的内容 |
  | --- | --- |
  | 设置首页 | 侧栏 → 设置 → 看到设置页 → `back` 回到首页 |
  | 外观 | 设置 → 外观 → 切换一项，断言选中状态变了 → 返回 |
  | 模型服务 | 侧栏 → 模型 → 列表有“示例模型服务” → 进详情 → 模型页显示“示例模型” |
  | 编辑服务 | 详情中改名称（英文）→ 保存或返回 → 列表显示新名称（Eta 有这个交互才写） |
  | 记忆 | 设置 → 记忆 → 页面打开；“新增”打开对话框，能取消（入口为灰色时见下文） |
  | MCP | 设置 → MCP → 添加服务器的对话框能打开、能取消（入口为灰色时见下文） |
  | 诊断 | 设置 → 诊断 → 点“复制报告”有反应 |
  | 会话侧栏 | 发送一条消息 → 打开侧栏，列表出现这个会话 → 搜索框输入 `zzz` 后列表为空 → 清空后恢复 → 长按会话出现菜单 → `back` 关闭 |
  | 消息操作 | 回复完成后点“删除”，确认后回复消失；长按用户气泡边缘（`offset`）出现“复制 / 编辑 / 删除”，点“编辑”后输入框进入编辑状态 |
  | 停止 | 发送后立即点停止 → 出现“已停止”提示，已收到的文字保留 |
  | 新对话 | 有消息时新建对话 → 回到空的首页 |
  | 深色模式 | `"screen": {"color": "dark"}`：首页、侧栏、设置各截一张图 |
  | 窄屏 | `"screen": {"width": 1080, "height": 2340}`：首页、侧栏、模型选择各截一张图 |

- **入口是灰色时**：设置里显示“鸿蒙版尚未接入”的行（目前记忆、Skills、MCP 等都是灰色），场景只断言两件事：
  - 它是灰色的；
  - 点了不跳转：用 `"expectChange": false`，之后 `expect` 仍在设置页。

  不要去接入。在 PR 中列一张“已移植但入口仍为灰色”的清单（例如 E08a 记忆、E08c MCP 已有页面），由 Claude 判断是漏接还是有意为之。
- **发现问题时**（例如点了没反应、应用错误、断言失败）：
  - 把这个场景放进 `tests/ui/pending/`，`tests/ui/` 里只留通过的场景；
  - 在 PR 的“发现的问题”中逐条写：场景名、失败的步骤、错误原文、截图文件名。
- **验收**：
  - `tests/ui/` 全部通过；
  - 上表每一行都有场景，或在 `pending/` 里有对应的失败场景；
  - Claude 看过全部截图。

#### R4-3 修复 R4-2 发现的问题（0.9.35）

- `tests/ui/pending/` 中每个失败场景对应一个问题，**一个问题一个提交**。修好后把场景移到 `tests/ui/`。
- 修复必须对照 Eta 源码：先在 PR 里写“Eta 是怎么做的”，再改。
- 某个问题尝试 3 次仍未修好：场景留在 `pending/`，写进阻塞说明，继续修下一个。
- R4-1 已经修好的渐显问题不在这里重复修。

#### D1a 设备契约移植（`agent/device`，不升版本）

- **来源**：`app/src/main/kotlin/io/github/mangi/eta/agent/device/` 下 7 个文件，对应单测在 `app/src/test/…/agent/device/`：

  | Eta 文件 | Eta 单测项数 |
  | --- | --- |
  | `ScrollEvidenceContract` | 2 |
  | `ScrollGestureContract` | 9 |
  | `ScrollAxisContract` | 4 |
  | `GestureFallbackPolicy` | 1 |
  | `ScreenshotOutcomePolicy` | 2 |
  | `ShellActionOutcomePolicy` | 2 |
  | `DeviceToolContract` | 无单测 |

- **目标位置**：`entry/src/main/ets/core/eta/device/<同名>.ts`、`tests/eta/device/<同名>.test.ts`。在 `package.json` 的 `test` 脚本里加上 `tests/eta/device/*.test.ts`。
- **翻译规则**：
  - 名字不改；Kotlin `data class` → TS `interface` 或 `class`；`sealed class` → 带 `kind` 字段的联合类型；
  - `android.graphics.Rect` 换成 `{left, top, right, bottom}`；
  - `DeviceToolContract` 中依赖 Android `Context` 的部分改为参数传入；
  - 每个测试用例的名字和 Kotlin 测试方法名一致，方便对照；
  - 文件头写来源注释。
- **不接入**任何页面或运行时。
- **验收**：`npm test` 的数量正好多 20 项、0 失败；`npm run verify` 全过；PR 中附对照表（Eta 文件 → TS 文件 → 移植的测试数）。

#### D1b 工具与无障碍逻辑移植（不升版本）

- **来源与单测**：

  | Eta 文件 | Eta 单测项数 |
  | --- | --- |
  | `agent/tool/ToolArguments` | 无 |
  | `agent/tool/ToolExecutionDecision` | 无 |
  | `agent/tool/ObservationReferencePolicy` | 2 |
  | `agent/tool/AgentAfterActionSummary` | 3 |
  | `agent/accessibility/TextEditPlanner` | 7 |
  | `agent/accessibility/AccessibilityNodeIdentity` | 6 |

- **目标位置**：`core/eta/tool/`、`core/eta/device/`，测试在 `tests/eta/`，规则同 D1a。
- **依赖**：`AgentAfterActionSummary` 用到 `RootShellDeviceController.ElementObservation` 和 `UiNode`，**只移植这两个数据类型**，放到 `core/eta/device/observation.ts`。如果还要拉进超过约 300 行别的代码，停下来报告。
- **验收**：测试正好多 18 项、0 失败；`npm run verify` 全过。

#### D0a 无障碍扩展实验（0.9.36）

目的是在真机上回答三个问题：
1. 普通应用的无障碍扩展能不能在系统设置里开启；
2. 能不能读到其他应用的控件；
3. 能不能注入手势。

背景见 [PHONE_OPS_PLAN.md](PHONE_OPS_PLAN.md) 的 D0a。

- **新增文件**：
  - `entry/src/main/ets/accessibility/AgentAccessibility.ets`：继承 `@kit.AccessibilityKit` 的 `AccessibilityExtensionAbility`，实现 `onConnect`、`onDisconnect`、`onAccessibilityEvent`；
  - `entry/src/main/resources/base/profile/accessibility_config.json`：

    ```json
    {"accessibilityCapabilities": ["retrieve", "gesture"]}
    ```

    再补一个说明字段 `accessibilityCapabilityRationale`，写清用途；
  - `core/eta/device/accessibilityProbe.ts`：探针记录的类型、序列化、校验，带单测。
- **`module.json5` 的 `extensionAbilities`** 加一项：

  ```json5
  {
    "name": "AgentAccessibility",
    "srcEntry": "./ets/accessibility/AgentAccessibility.ets",
    "type": "accessibility",
    "exported": true,
    "metadata": [{ "name": "ohos.accessibleability", "resource": "$profile:accessibility_config" }]
  }
  ```

  `metadata` 的 `name` 按 OpenHarmony 文档写 `ohos.accessibleability`。云开写的是 `accessibility_config`，如果真机设置里找不到服务，再试它，并记录结果。
- **不新增权限**。DevEco 编译或安装时如果提示需要权限或签名能力，停下来，把报错原文贴回。
- **扩展做的事**：

  扩展和界面不在同一个进程，通过 `context.filesDir` 下的两个小 JSON 文件通信。

  1. `onConnect` / `onDisconnect` 时写入 `a11y-probe.json`，内容：

     ```
     connected, connectedAt, lastEventAt, foregroundBundle, nodeCount, gesture: {requestedAt, result, at}
     ```

  2. 收到事件时，最多每秒一次：
     - 用 `this.context.getWindowRootElement()` 取前台窗口的根节点，读 `bundleName`；
     - 递归读 `children`，统计节点数，最多数到 500。

     **只记录包名和数量，不读取、不记录任何文字。**
  3. 收到事件时，如果存在界面写入的 `a11y-gesture-request.json`，并且前台是本应用：
     - 用 `findElement('content', '<测试按钮文字>')` 找到测试按钮，读它的 `rect`；
     - 用 `GesturePath(100)` 加一个 `GesturePoint(中心点)`，调用 `this.context.injectGestureSync(...)`；
     - 把结果写回探针文件，删除请求文件；
     - 结果取值：`ok`、`not-found` 或 `error:<错误码>`。
- **界面**：“设置 → 诊断”新增“无障碍实验”区，内容：
  - 显示连接状态、前台包名、控件数、最近事件时间、注入结果；
  - “刷新”按钮；
  - “请求注入点击”按钮；
  - 测试按钮：显示点击次数。点它次数加一；注入成功时次数也会加一。
- **预览器**：显示“未连接”状态；在诊断场景里断言这一区可见、“刷新”有反应。
- **真机检查点 ⑥**：写进 `CHECKPOINT-B.md`，步骤见 PHONE_OPS_PLAN 的 D0a。需要记录：
  - 设置里叫什么名字、能不能打开；
  - 切到“设置”应用再回来，看到的包名和控件数；
  - 注入后计数有没有加一；
  - 任何系统弹窗的原文。

#### D0b1 HDC 连接状态机（纯逻辑，不升版本）

- `core/eta/device/hdcConnection.ts`：
  - 状态：未配置、连接中、待授权、已连接、已断开（带原因）；
  - 输入：用户的配置和操作，以及传输层事件；
  - 每次连接带递增的尝试编号，**旧尝试迟到的事件一律忽略**。
- 传输层只通过注入的接口访问（`connect`、`close`、事件回调），不直接 import 原生模块。接口形状参考现有的 `core/device.ts` 和 `entry/src/main/cpp/bridge.cpp` 的导出函数。
- **单测**：
  - 每个合法转换；
  - 非法事件被忽略；
  - 迟到事件被忽略；
  - 每种断开原因映射到对应的文案键。
- **验收**：`npm run verify` 全过。

#### D0b2 HDC 原生库（Claude 负责）

用公开 SDK 的 native 组件，交叉编译 arm64 的 `libagent_hdc.so`，连同 SHA-256 和构建脚本一起入库到 `entry/libs/arm64-v8a/`。完成后在这里更新状态，D0b3 才能开始。

#### D0b3 手机操作连接页（0.9.37）

**前提**：D0b2 已入库 `entry/libs/arm64-v8a/libagent_hdc.so`（见 `BUILD-INFO.md`）。不要改这个库；需要改原生代码时停下来，交给 Claude。

**原理**：
- 应用内置一个 HDC 客户端，连到**本机**的“无线调试”端口。
- 连上后，以 shell 身份执行 `uitest` 读屏和操作。
- 0.3 版本在真机上读到过 103 个控件，所以这条路是验证过的。

**要做的**：

1. **原生库接线**：
   - `platform/Services.ets` 已有 `NativeHdcTransport`，它包装了 `start` / `command` / `cancel`；
   - 新增 `platform/HdcConnectionTransport.ets`，在它之上实现 D0b1 的 `HdcTransport` 接口；
   - `connect(target, attempt)` 依次执行：
     1. `start(filesDir + '/hdc')`；
     2. `tconn <target>`；
     3. `shell id`：输出含 `uid=` 才算已授权，否则返回 `authorizing`；
     4. `shell uitest help`：确认 uitest 可用。

     流程参照 `core/device.ts` 的 `HarmonyDevice.connect`。
   - 超时、拒绝连接、协议错误映射到 D0b1 的 `HdcConnectOutcome`。**原生库的报错原文只写进诊断日志，不显示给用户。**
2. **控制器**：
   - 连接状态放在 `core`：`HdcConnection` 加一个“页面快照”，包含状态、原因的文案键、目标地址、shell 身份（只显示 `uid=` 那一段）、uitest 是否可用、最近一次测试读屏的控件数；
   - 页面只渲染快照、发送意图；
   - 单测覆盖快照的每种状态。
3. **页面**：“设置 → 手机操作”。Eta 没有这个页面，按 Eta 设置页的样式做（`EtaPreferenceRow`、`EtaCard`），文案走资源。内容依次是：
   - 说明卡：打开“设置 → 系统 → 开发者选项 → 无线调试”，记下页面上的 IP 地址和端口；
   - 地址输入框：`IP:端口`，用 D0b1 的 `validateHdcTarget` 校验；
   - “连接”按钮；
   - 状态行：未配置、连接中、待授权（提示“请在手机弹窗中允许调试”）、已连接、已断开（原因）；
   - 已连接后显示 shell 身份、uitest 是否可用；
   - “测试读屏”按钮：执行 `uitest dumpLayout`，用 `core/device.ts` 的 `parseLayout` 统计控件数，**只显示数量，不显示任何文字**；
   - “断开”按钮。
4. **入口**：设置页“启用设备直达工具”所在的分组里加一行“手机操作”，点击进入这个页面。其他手机工具开关仍是灰色，**不注册任何模型工具**。
5. **预览器**：
   - `FoundationPreview` 注入假传输层，可以按场景切换：成功、待授权、拒绝；
   - 场景 `20-phone-ops.json`：进入页面 → 点地址输入框 → `{"type": "127.0.0.1:5555"}`（`type` 已支持 `.` 和 `:`）→ 连接 → 断言状态文字 → 测试读屏 → 断言控件数 → 断开；
   - 另写一个“非法地址”的场景，断言出现提示、不会去连接。
6. **真机检查点 ⑦**：步骤写进 `docs/device-runs/CHECKPOINT-B.md`：
   1. 断开电脑；
   2. 开启无线调试；
   3. 在应用里填写地址并连接；
   4. 允许授权弹窗；
   5. 看 shell 身份和 uitest 是否可用；
   6. 测试读屏，控件数应大于 0；
   7. 关闭无线调试，页面应显示断开原因；
   8. 记录任何报错原文。

**不做**：
- 不接入聊天、不注册模型工具（那是 D2、D3）；
- 不做后台保活；
- 不保存截图。

**验收**：`npm run verify` 全过；新场景通过；PR 中列出新增的文案和页面截图。

### 本轮队列（2026-10-31 第三轮，Claude 审阅后安排）

**背景**：0.9.1 → 0.9.18 共 17 个 PR 还没有在手机上编译运行过一次。检查器能拦住类型、ArkTS 规则和资源问题，但拦不住界面编译阶段的语法校验、运行时崩溃和布局问题。所以本轮**优先做纯逻辑、能用 Eta 单测证明正确的任务**，少叠加未经真机验证的界面。用户测完真机后，界面问题优先修。

**base**：本轮第一个任务从 `harmony/round3-plan` 拉分支（它基于 `harmony/e06-providers`，只多了这份规划）。其余规则同“无人值守连续执行”。

| 顺序 | 任务 | 说明 |
| --- | --- | --- |
| 0 | 真机反馈修复（插队） | 用户贴来 DevEco 编译报错或真机问题时，**立即中断当前任务**，在出问题的最早那个分支上修复，再依次合并到下游分支 |
| 1 | E07b1 会话存储逻辑与端口 | 纯 TS，可用内存 DAO 测试 |
| 2 | E07b2 RDB 适配与旧数据迁移代码 | **只写代码和测试，不切换正式入口** |
| 3 | P1a OpenAI Responses 协议及公共部件 | 用户使用 OpenAI 模型，优先 |
| 4 | P1b OpenAI Chat Completions 对齐 Eta | 替换 0.9 的 `core/model.ts` 请求路径 |
| 5 | E05b 默认启用思考与自动压缩上下文 | 依赖 P1a 的推理参数 |
| 6 | E06b 内置服务预置与模型能力 | |
| 7 | P1c Anthropic → P1d Gemini | |
| 后备 | E08：记忆 → Skills → MCP | 前面全部完成后才做 |

**E07b3（切换到新存储）不在本轮**：必须等用户的真机检查点 ①–④ 通过后再做。

#### 第三轮任务卡

**E07b1 会话存储逻辑与端口**
- 按 [E07B_STORAGE_DECISION.md](E07B_STORAGE_DECISION.md) 的方案 A（Claude 建议，用户转发本轮指令即视为确认），在 `core/eta/` 定义纯 TS 的 ConversationDao 和事务端口，划分会话元数据、消息行、history/journal 检查点、选中项、分块文本。
- 移植 `AgentConversationStore`、`AgentConversationPersistence`，以及 `AgentConversationStoreTest`（17 项）、`AgentConversationPersistenceTest`、`AgentAppStateConversationLoadingTest`。用内存 DAO 跑通；不缩短 fixture，不截断数据。

**E07b2 RDB 适配与旧数据迁移（不切换）**
- 用鸿蒙 RDB 实现 E07b1 的事务端口：事务成功后才推进保存基线；失败时不推进基线、不清空内存。
- 从 0.9 快照迁移：
  - 旧快照原样保留，迁移用独立的版本标记；
  - 校验会话数、消息数、正文哈希和选中 ID；
  - 迁移可重复执行；
  - 中途失败、旧数据损坏时阻止切换并写诊断记录，**绝不删除旧数据**。
- 测试：迁移幂等、中途失败、重启恢复、旧数据损坏、长文本完整性。正式入口仍使用 0.9 存储。

**E07b3 切换（本轮不做）**
- 等真机检查点 ①–④ 通过后，控制器切换到新存储。真机验收步骤：
  1. 在旧版本写入若干会话；
  2. 升级；
  3. 核对历史、模型和导出；
  4. 注入一次迁移失败，确认旧数据完整。

**P1 模型协议：照抄 Eta 的 `agent/model/`（替代原 T09 的“自己实现”）**
- 来源：`app/src/main/kotlin/io/github/mangi/eta/agent/model/` 中的 `OpenAiResponsesProvider`、`OpenAiChatCompletionsProvider`、`AnthropicMessagesProvider`、`GeminiInteractions*`，以及公共部件 `ProviderSseReader`、`ProviderReasoning`、`ProviderRequestHeaders`、`ProviderUrls`、`ProviderMessageContent`、`ProviderParameterDiagnostics`、`OpenAiRequestMessages`。
- 单测：

  | Eta 测试 | 项数 |
  | --- | --- |
  | `OpenAiResponsesProviderTest` | 11 |
  | `CodexResponsesRequestTest` | 5 |
  | `OpenAiChatCompletionsProviderTest` | 14 |
  | `OpenAiChatToolCallIdsTest` | 7 |
  | `AnthropicMessagesProviderTest` | 15 |
  | `GeminiInteractionsTest` | 21 |
  | `ProviderReasoningTest` | 23 |
  | `ProviderSseReaderTest` | 4 |
  | `ProviderRequestHeadersTest` | 7 |
  | `ProviderUrlsTest` | 1 |

- 拆分：
  - **P1a**：Responses 和公共部件；
  - **P1b**：Chat Completions 对齐，替换 0.9 的 `core/model.ts` 请求路径，保留“先持久化再发送、停止保留内容、不自动重发”的规则和 M0 的诊断记录；
  - **P1c**：Anthropic；
  - **P1d**：Gemini。
- 每个协议完成后，E06“新建服务”中对应的类型从 B 类改为可用。
- 订阅账号登录（Codex PKCE 等）仍为 C 类，不做。
- **真机**：用户用自己的 OpenAI API Key，分别以 Responses 和 Chat Completions 各完成一轮测试连接 → 获取目录 → 聊天 → 停止。

**E05b 默认启用思考与自动压缩上下文**
- 来源：`data/provider/ReasoningCapabilityResolver`（12 项测试）、`agent/runtime/AgentRuntimeRequestConfigResolver`（7 项）、`agent/model/AgentContextCompactor`、`AgentContextCompactionTest`（16 项）、`AgentCompactionProviderTest`（3 项）。
- 完成后，设置页这两行从禁用改为可用。

**E06b 内置服务预置与模型能力**
- 来源：`data/provider/BuiltinProviders`（2 项）、`OfficialModelCatalog`（4 项）、`GeminiModelCapabilities`（3 项）、`ProviderConnectionProfile`，以及 E06 交付记录中列出的模型能力编辑缺口。

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

### 给执行者的通用提示词

```
你在仓库 Lmin2419/eta-background 的 harmony-agent/ 目录工作。
先读 harmony-agent/AGENTS.md、harmony-agent/docs/EXECUTOR_GUIDE.md，以及 harmony-agent/docs/ROADMAP.md 中任务 <编号> 的任务卡。
E、D 开头的任务还要读 docs/ETA_PORT.md 或 docs/PHONE_OPS_PLAN.md。
严格按任务卡的范围，不要顺手做其他事。
按 EXECUTOR_GUIDE 拉分支；npm run verify 全部通过后开 PR，PR 描述按 EXECUTOR_GUIDE 第 7 节写。
```

修复真机问题时：

```
任务 <编号> 的真机测试失败。以下是我的操作、看到的现象和诊断报告：
<粘贴>
请按 EXECUTOR_GUIDE 4.1：先在预览器里写复现场景并确认失败，再在同一分支修复，场景通过后移到 tests/ui/。
预览器复现不了时，说明原因，再给我最少的真机步骤。
```

转交 Claude 的验收意见时：

```
这是 Claude 对 PR #<编号> 的验收意见，请按编号逐条修改，并在 PR 评论中逐条回复：
<粘贴>
```

## 三、里程碑总览

| 里程碑 | 版本 | 目标 | 任务 |
| --- | --- | --- | --- |
| M0 | 0.9.1 | 能直接编译上真机，能回传问题 | T01–T03 |
| M1 | 0.10 | **Eta 界面与交互 1:1 移植** | E01–E09 |
| M2 | 0.11 | 聊天能力补齐：后台不中断、错误与重试、多协议 | T04、T07、T09（T05、T06、T08 已并入 M1） |
| M3–M5 | 0.12–0.14 | **手机操作（2026-10-31 修订）**：一套 Eta 工具，两个后端（无障碍扩展 / 本机 HDC），先做真机可行性实验再定主通道 | D0–D6，见 [PHONE_OPS_PLAN.md](PHONE_OPS_PLAN.md)；原 T10–T17 作废，T04 并入 D4 |
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

#### T09 其他模型协议（已改为 P1：照抄 Eta 的 `agent/model/`，见第二节第三轮队列）

- 配置中已经有 `protocol` 字段，目前只实现了 `chat_completions`。用户使用 OpenAI 的模型（ChatGPT 6.0 Astra），顺序为：**OpenAI Responses → Anthropic Messages → Gemini**。
- 先用现有 Chat Completions 在真机测试用户的模型（G0 闸门中完成）。如果它可用，Responses 主要是为了推理内容和新特性；如果不可用，T09 提前到 M2 的第一项。E06 已经移植了选择协议类型的界面，T09 只需实现协议本身，并把对应类型从 B 类改为可用。
- 注意：App 需要 OpenAI 平台的 **API Key**（或兼容服务的 Key）。ChatGPT 会员订阅本身不提供 API 额度；用订阅账号登录属于第五节的“验证后决定”项，不在 T09 范围内。
- 每个协议都包括：请求构建、SSE 解析（含 reasoning / thinking 内容）、模型目录、连接测试和错误信息解析。服务配置页可以选择协议。
- **自动验收**：每个协议用固定的 SSE 样本做解析测试，并用本地 HTTP 服务做端到端测试。
- **真机验收**：用你真实的账号对每个协议完成一轮测试 → 获取目录 → 聊天 → 停止。

**闸门 G2（发布 0.11）**：完整跑一遍真机模板；连续日常使用 3 天，期间没有回复丢失、表单被清空或会话串内容的问题。

---

### M3 · 0.12「手机操作：连接与读屏」

> **已被 [PHONE_OPS_PLAN.md](PHONE_OPS_PLAN.md) 取代（2026-10-31）**，以下 T10–T17 只作历史参考，不再执行。

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
