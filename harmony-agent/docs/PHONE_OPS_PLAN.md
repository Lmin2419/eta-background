# 手机操作方案（2026-10-31 修订）

替代 ROADMAP 原 M3–M5 中的 T10–T17。执行规则仍按 [AGENTS.md](../AGENTS.md)。

## 1. 目标

像 Eta 一样，助手直接在手机上运行：读取当前屏幕，按用户的话去操作其他 App（点击、滑动、输入、打开应用、返回）。全程用户能看到在做什么，可以随时接管、随时停止。

## 2. 参考来源与取舍

**主参考是 Eta 自己的实现**（仓库根目录 `app/`，基准 `3b1794d`）。其他开源项目只补 Eta 没有的鸿蒙细节。

| 来源 | 许可证 | 借鉴 | 不借鉴 |
| --- | --- | --- | --- |
| **Eta**：`agent/device`、`agent/tool`、`agent/accessibility`、`agent/runtime`、`agent/overlay` | PolyForm Noncommercial（同 ETA_PORT） | 手机工具的定义与提示词；设备契约（滚动证据、手势降级、截图结果、观察引用、动作后摘要、文本编辑规划）；执行服务、检查点、接管、悬浮状态的交互逻辑；以及这些模块的单测 | Root、Shizuku、厂商 Hook、Android 专属接口 |
| [智谱 Open-AutoGLM](https://github.com/zai-org/Open-AutoGLM) | 代码 Apache-2.0；模型 AutoGLM-Phone-9B 为 MIT，页面注明“仅供研究和教育用途” | `phone_agent/config/apps_harmonyos.py`：176 个常见应用的名称 → 包名 / Ability；鸿蒙 uitest 细节（`uiInput text` 无坐标输入、敏感页截图失败兜底、`aa dump -l` 获取当前应用）；提示词中 18 条操作经验规则；`Take_over` / `Interact` / 敏感操作确认；可选视觉模型 `autoglm-phone` | 电脑端 Python 架构 |
| [云开 solplum/yunkai](https://github.com/solplum/yunkai) | MIT | 写操作“计划卡逐步确认”、敏感页拦截、银行和支付应用默认不读、外发文字二次确认、提示注入防护；鸿蒙真机经验：锁屏会静默掐断流式连接，挂起期间计时器也停 | 它的鸿蒙无障碍只有骨架（只打印连接日志，读屏和手势未实现），**不能当作可行性证据** |
| [HDC-Claw](https://github.com/encauporce/HDC-Claw) | MIT | HDC 无线协议细节（RSA-3072 握手、单包执行 `1001`、会话保活）、`/bin/uinput` 触控、动作前后的布局对比 | Python 实现 |
| [hon-cli](https://github.com/mao026/hon-cli)、[agent-device](https://github.com/callstack/agent-device) | MIT | uitest 控件树规范化、系统弹窗处理、给模型用的命令设计 | 依赖电脑 |

借鉴规则：
- 复制代码时，文件头写明来源和提交号，并在 `THIRD_PARTY_NOTICES.md` 中保留原版权声明。
- 只参考思路时，不需要声明。

## 3. 架构：一套工具，两个后端

```
Eta 的手机工具定义（模型可见的名称、参数、提示词）
        │
设备契约层 core/eta/device/（纯 TS，移植 Eta 单测）
  观察引用与过期判断 · 动作后重新观察并对比 · 滚动是否生效 · 手势降级 · 敏感页 · 结果未知不重放
        │
DeviceBackend 接口：observe / tap / longPress / swipe / scroll / input / back / home / launch / wait
   ├─ AccessibilityBackend：鸿蒙无障碍扩展。对应 Eta 的无障碍服务；真机可用时作为主通道，不需要开发者模式
   └─ HdcBackend：本机无线调试 + uitest（已有 core/device.ts）。对应 Eta 的 Root 通道；作为增强通道或备用通道
```

| 能力 | 无障碍后端 | HDC 后端 |
| --- | --- | --- |
| 读控件树 | `getWindowRootElement` / `getWindows` | `uitest dumpLayout` |
| 截图 | 普通应用没有截屏权限（`CAPTURE_SCREEN` 是 system_core 级），**只有控件树** | `uitest screenCap` |
| 点击、滑动 | `injectGestureSync` | `uitest uiInput click / swipe / longClick / drag` |
| 输入 | `performAction`（设置文本） | `uitest uiInput inputText` 或 `text` |
| 返回、桌面 | 全局动作 | `uitest uiInput keyEvent Back / Home` |
| 打开应用 | `startAbility` 显式启动目标应用的入口 Ability（需要真机确认） | `aa start -b -a` |
| 前提 | 用户在系统设置中开启无障碍服务（需要真机确认是否允许） | 开发者模式 + 无线调试 + 授权，重启后可能要重新授权 |

两个后端可以同时存在，和 Eta 的“无障碍 + Root”一样。控件树是主要依据，截图只在 HDC 后端可用，并且默认关闭。

## 4. 任务卡

### D0 可行性实验（两个都很小，可以并行；结论必须来自真机）

**D0a 无障碍扩展实验**
- 在应用里声明一个 `type: "accessibility"` 的扩展，能力为 `retrieve` 和 `gesture`。参照 Eta 的 `AgentAccessibilityService` 和云开的 `accessibility_config.json`。
- “设置 → 诊断”中新增“无障碍实验”区，显示：
  - 服务是否已连接；
  - 前台窗口的包名；
  - 前台窗口的控件数量；
  - 一个按钮“在本页注入一次点击”：只点击本应用测试页上的按钮，验证手势注入。
- **不读取、不记录**其他应用的文字内容，只记录数量和包名。
- **真机检查点 ⑥（用户操作）**：
  1. 打开“设置 → 辅助功能 → 无障碍”（不同系统版本名称可能不同），看有没有“随身助手”，能否开启；
  2. 开启后切到“设置”应用，再回来，看诊断页是否读到“设置”的包名和控件数；
  3. 点“注入一次点击”，看是否生效。
- 把结果记入 `docs/device-runs/`，包括失败时的现象。

实现细节（扩展的写法、和界面的通信方式、`metadata` 名称）见 ROADMAP 第四轮的 D0a 任务卡。

**D0b HDC 通道复活**（原 T10）。拆成三步：
- D0b1 连接状态机（执行者）；
- D0b2 原生库交叉编译（Claude）；
- D0b3 连接页（执行者）。

各部分要求：
- 原生库：用公开 OpenHarmony SDK 的 native 组件（和我们已在用的 SDK 是同一个下载包，`native-linux-x64-26.0.0.38`）在 Linux 上交叉编译 arm64 的 `libagent_hdc.so`。编好的库入库到 `entry/libs/arm64-v8a/`，记录 SHA-256 和构建脚本，用户的 DevEco 不需要编 C++。
- 连接页：“设置 → 手机操作”。引导开启无线调试 → 填写地址端口 → 授权 → 显示 shell 身份和 uitest 可用性 → “测试读屏”显示控件数。
- 连接状态机放在 `core`，带单测：未配置、连接中、待授权、已连接、断开原因。
- **真机检查点 ⑦**：断开电脑，只用手机完成连接；测试读屏的控件数大于 0；关闭无线调试后页面显示断开原因。

**闸门 G-D0**：根据 ⑥、⑦ 的结果决定主通道：

| 结果 | 决定 |
| --- | --- |
| 无障碍能开启，能读到其他应用的控件，能注入手势 | 无障碍为主通道，HDC 为增强通道 |
| 无障碍不可用 | HDC 为唯一通道 |
| 两个都不可用 | 停下来，由用户决定方向 |

### D1 设备契约与工具层移植（纯逻辑，可以和 D0 同时开始）
- 移植 Eta 中与平台无关的设备逻辑及单测，放到 `core/eta/device/` 和 `tests/eta/device/`：
  - `agent/device`：`ScrollEvidenceContract`、`ScrollGestureContract`、`ScrollAxisContract`、`GestureFallbackPolicy`、`ScreenshotOutcomePolicy`、`ShellActionOutcomePolicy`、`DeviceToolContract`；
  - `agent/tool`：`ToolArguments`、`ToolExecutionDecision`、`ObservationReferencePolicy`、`AgentAfterActionSummary`；
  - `agent/accessibility`：`TextEditPlanner`、`AccessibilityNodeIdentity`。
- 手机工具的 schema 和提示词按 `docs/ETA_TOOL_INVENTORY.csv` 的分级移植；没有后端的工具不注册给模型。
- 和现有 `core/device.ts`、`core/loop.ts` 的观察指纹机制合并，**只保留一套**。

### D2 后端实现与应用目录
- 按 G-D0 的结论先实现主通道，再实现另一个，统一实现 `DeviceBackend` 接口。
- 应用目录：引入 Open-AutoGLM 的 `apps_harmonyos.py`，转换成 JSON，保留版权声明。HDC 后端可以再用 `bm dump -a` 补全。普通应用拿不到全部应用列表（`GET_INSTALLED_BUNDLE_LIST` 是 system_basic 级），无障碍后端只用静态目录。
- uitest 细节：参考 Open-AutoGLM 的输入和截图兜底、HDC-Claw 的会话保活。以 `uitest help` 的实际输出为准，不要猜测命令参数。

### D3 手机模式接入聊天
- 按 Eta 的交互：输入栏的 Atom 按钮从 B 类改为可用 → 开启手机模式 → 只读工具 → 写操作工具。
- 时间线：复用 E04 的 `AgentWorkSteps`，显示观察、动作、结果。
- 安全与接管（Eta、云开、Open-AutoGLM 的做法合并）：
  - 写操作“计划卡确认”默认开启，设置中可以改为“仅敏感操作确认”；
  - 敏感页（支付、密码、验证码）立即停止，并提示用户接管（`Take_over`）；
  - 银行和支付类应用默认不读取；
  - 外发文字（发消息、发帖）二次确认；
  - 每步动作后重新观察并对比；结果未知时报告“未确认”，不重放；
  - 任何时候都能停止。
- 系统提示词：Eta 原文，再加上 Open-AutoGLM 18 条规则中适用的部分。
- 界面测试：连接页、计划卡、时间线用**假后端**写 `tests/ui` 场景（预览器里不能操作真实应用）。

### D4 后台、锁屏与状态通知（合并原 T04、T15–T17）
- 操作其他应用时助手在后台，用持续任务保持运行。参考旧的 `platform/Background.ets`，并以真机实测的保活时长为准。
- 任务通知：显示当前步骤，带“停止”按钮。不能用悬浮窗（`SYSTEM_FLOAT_WINDOW` 是 system_basic 级），所以不做 Eta 的悬浮胶囊；实况窗需要华为审核，暂不做。
- 锁屏：按云开的经验，锁屏会掐断连接、挂起计时器。锁屏后要立即暂停并说明原因，解锁后由用户决定是否继续。

### D5 可选视觉操作模型（只用于 HDC 后端，因为需要截图）
- 设置中增加“手机操作模型”，默认“与聊天模型相同”，可选智谱 `autoglm-phone`（`https://open.bigmodel.cn/api/paas/v4`）。
- 选了它，就按它的训练格式：截图 + 任务 → `<think>…</think><answer>do(...)</answer>`，坐标为 0–999 的相对坐标。解析器放在 `core`，带单测；动作照样经过 D1 的契约层和 D3 的确认。

### D6 真机回归自动化（可选，需要用户决定）
- 手机操作只能在真机上验证。如果用户愿意，在用户电脑上用 hdc + uitest 跑场景脚本（风格参考 hon-cli），由本地 Codex CLI 或用户一键执行，自动截图对比。
- 不做也可以，那就由用户按检查点手动测试。

## 5. 顺序

```
真机验证 0.9.33 → 整条 PR 链合并进 main
      │
      ├─ D0a 无障碍实验 ─┐
      ├─ D0b HDC 复活 ───┼─→ 真机检查点 ⑥⑦ → 闸门 G-D0 → D2 → D3 → D4 → D5
      └─ D1 契约移植（纯逻辑，不依赖真机）─┘
```

ROADMAP 原 M2 中的 T07（错误分类与重试）不受影响，可以穿插进行；T04（后台不中断回复）并入 D4。
