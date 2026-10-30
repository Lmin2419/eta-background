# E01 · 0.9.2 资源移植交付记录

日期：2026-10-30。对应 [ROADMAP E01](ROADMAP.md) 与 [ETA_PORT §3、§8](ETA_PORT.md)。

状态：**代码完成、自动测试通过；未生成 HAP、未进行真机验证**。E01 不改变现有页面或交互，E02 尚未开始。

## 基准、范围与复现

- Eta：`app/`，固定提交 `3b1794d70881c3c849b53d7778dd2bfdf0a46cc5`。此次从 `harmony/m0-0.9.1@abae672` 派生，已核对两者的 `app/` 树一致。
- 分支：`harmony/e01-resources`；PR #8 未合并时 base 为 `harmony/m0-0.9.1`。#8 合并后改为 `main`。不修改 Android 源码或 workflow。
- Miuix：0.9.4，固定 `39c40f99844227b853f0049a0933b1f3ae6c00ba`，所需 Kotlin 输入与许可证随本工程保存。
- Material Icons：`@material-design-icons/svg@0.14.15`，精确锁定；品牌图标与字体直接复制 Eta 文件。
- 本次版本：`0.9.2 / 90002`；不修改包名、签名或权限。

从完整仓库进入 `harmony-agent/` 后执行：

```bash
npm ci
npm run port:eta          # 生成资源、来源清单、tokens 和字体注册文件
npm run port:eta:check    # 只读比对；缺文件、图标缺失或生成结果漂移时失败
npm run check
npm test
bash scripts/fetch-ohos-sdk.sh
OHOS_SDK_ETS=~/.cache/ohos-sdk-26/ets npm run check:ets
```

需要 Node.js、Python 3.9+ 与根目录的 `app/`、`LICENSE`。脚本不联网，不修改输入文件；第一次 `npm ci`、SDK 下载需要网络。生成结果已提交，DevEco 用户无需运行脚本。脚本支持 `--eta-root` 和 `--output-root`，供转换测试使用。基准变化必须另开任务，不能只改输出文件或沿用旧来源注释。

## 逐项对照表

以下 A 表示 **E01 资源层完整移植**，不表示消费这些资源的页面或设备能力已实现。

| Eta 元素 | 鸿蒙实现 | 状态 / 数量 |
| --- | --- | --- |
| 根目录 LICENSE、Required Notice | `harmony-agent/LICENSE`；`rawfile/licenses/Eta-PolyForm-Noncommercial-1.0.0.txt` | A，原文完整复制 |
| `values-b+zh+Hans/*.xml` | `resources/base/element/string.json`、`plural.json` | A，中文默认；原文 1,536 条 string、22 组 plural，补齐 2 项不翻译术语 |
| `values/*.xml` | `resources/en_US/element/string.json`、`plural.json` | A，1,538 条 Eta string、22 组 plural |
| `values-b+zh+Hant/*.xml` | `resources/zh_Hant/element/string.json`、`plural.json` | A，保留上游繁中测试覆盖；原文 1,536 条 string、22 组 plural，补齐同样 2 项术语 |
| `Icons.*` 引用 | `base/media/ic_<style>_<name>.svg`，来源与每个引用文件见 `eta-icons.json` | A，148 个 Material，缺失 0 |
| `MiuixIcons.Basic.ArrowRight` | `ic_miuix_basic_arrow_right.svg`，按原始 Bezier 路径生成 | A，1 个 |
| `res/drawable/*.xml` | 同名 SVG，保留路径、填充、描边、透明度、坐标变换 | A，11 个；源文件实际为 10 个 vector 和 1 个实心矩形 shape |
| `ProviderBranding.kt` 的服务 / 模型标识 | `base/media/*_logo_*.webp` | A，22 个原文件，逐字节相同 |
| Miuix `Colors.kt`、Eta `EtaPreferenceColors` / `StatusColors` | `base` / `dark` 的 `color.json`，统一资源键 | A，各 53 个 Miuix 角色 + 12 个 Eta 颜色 + 1 个旧启动背景，共 66 色 |
| `EtaPreferenceDefaults`、`EtaCardDefaults`、`EtaControlDefaults` 等 | `core/eta/tokens.ts`，保留名称、数值和 `contentStart` 行为 | A，41 项尺寸、字号常量；包含 padding / size 对象 |
| `EtaFonts.kt`、两个 JetBrains Mono 字体 | `rawfile/fonts/` 与 `platform/EtaFonts.ets`，正式和预览入口注册 | A，regular / bold 原文件；正文不换字体 |
| 关于页来源说明 | 5 条 `eta_port_*` 资源（含未接入摘要） | 资源 A；关于页 E05 才接入 |

每个 locale 最终有 **1,546 条 string**：1,538 条 Eta + 3 条旧鸿蒙资源 + 5 条鸿蒙说明；另有 **22 组 plural**，不计入 string 数。中文缺少的两项是上游 `translatable="false"` 的 `route_skills`（Skills）和 `speech_api_key`（API Key），保留英文。

来源和散列清单：

- [文案](eta-strings.json)：语言、原始 / 输出条数、回退 key、源 XML 散列。
- [图标](eta-icons.json)：源码引用 → SVG；AutoMirrored 标记；drawable / 品牌标识映射与输出散列。
- [主题](eta-theme.json)：源文件散列、53 个颜色角色和全部提取的尺寸值。
- [字体](eta-fonts.json)：原始字体散列、注册方式与许可证。
- [外部输入](../scripts/eta-upstream/README.md)：Miuix、OFL 的固定来源。

`scripts/eta-port/` 是有意保留的鸿蒙输入：旧资源防止既有引用断开，新增关于页文案满足许可要求。Eta 文案、图标几何、颜色和尺寸均由脚本读取上游生成。JSON / TTF / WebP 不能安全嵌入源码注释，来源由上述清单和 Required Notice 文件记录；生成的 TS / ETS / SVG 文件带来源注释。

## 与 Eta 的差异

1. **默认语言**：按 ETA_PORT 改为简中，未覆盖的语言回退简中；上游默认英文。繁中资源保留，未增加语言设置页面。两项资源单测中的法语回退预期因此改为简中；不把测试中的资源选择辅助函数当作操作系统行为证明。
2. **名称**：产品自称“Eta”改为“随身助手”，资源 key、URL、包标识与归属声明保留。关于页的“基于 Eta”保留；资源转换不改作者 Required Notice。
3. **格式**：使用鸿蒙 JSON 资源，保留 SDK 26 ResourceManager 支持的 `%1$s` / `%2$d` / `%d` 等；Android 转义解析为实际字符。`formatted="false"` 的百分号原样保留，不重复转义成 `%%`。复数保留 quantity 与参数，不在生成时预先选择单复数。
4. **尺寸与颜色**：dp → vp、sp → fp，数值相同；ARGB 及 alpha 保留。设置页浅色背景 `#F0F1F2` / 强调色 `#0080FF` 与 Miuix 默认主题分别命名，不误把设置页颜色应用到全局。颜色资源已备齐，E05 才接入主题切换。
5. **图标方向**：14 个 AutoMirrored 引用的方向标记保存在映射表，SVG 是原始朝向；消费它们的 E02 组件须绑定 ArkUI 文本方向，避免把所有图标永久翻转。当前页面尚未使用新 SVG。
6. **字体**：正文按规范用鸿蒙系统字体；JetBrains Mono regular / bold 在鸿蒙注册为两个 family 别名，后续代码块按字重选择，两个字体文件均未修改。
7. **应用资源边界**：旧启动背景资源保留，避免 E01 改变现有界面；上游 Android 启动图标只作为转换资源保留，不替换“随身助手”的 AppScope 图标。C 类功能的文案可保留在完整资源集中，不据此创建页面或入口。

没有另行设计页面；连续圆角、布局、动效、后台与交互不在 E01 实施范围。

## 单测与自动验证

从 `app/src/test/kotlin/io/github/mangi/eta/ui/app/LocaleResourcesTest.kt` 翻译 **2 项**至 `tests/eta/localeResources.test.ts`，保留原用例名便于检索：

1. `supportedLocalesSelectExpectedResourcesAndOthersFallBackToEnglish`：测试名加 Harmony 默认简中适配说明；覆盖英文、简中、繁中与法语回退、标题和单复数。
2. `supplementalUiResourcesFollowLocaleWhileTechnicalTermsStayEnglish`：补充界面文案、技术术语、产品改名和运行摘要复数。

这些单测检查生成资源，不运行 Android Resources 或鸿蒙 ResourceManager。Store / Reducer 不属于 E01，不能把新增转换器测试计作 Eta 业务逻辑移植数量。

另有 11 项 Node 资源测试，包括原文件一致性、缺失图标失败、重复生成比对、颜色与尺寸、来源及许可证；其中一项执行 12 项 Python 测试，覆盖 Android 引号/转义/Unicode/空白、XML 实体、格式占位符、产品改名边界、复数、非法输入、SVG 描边/alpha/组变换和只读漂移检查。

| 检查 | 结果 |
| --- | --- |
| `npm run check` | 通过 |
| `npm test` | 213 项通过，0 失败、0 跳过；原有 200 项未减少 |
| 内嵌 Python 转换测试 | 12 项通过（由上述 Node 测试运行，不重复加入 213 总数） |
| `npm run port:eta:check` | 通过；图标缺失列表为空 |
| `OHOS_SDK_ETS=~/.cache/ohos-sdk-26/ets npm run check:ets` | SDK 26.0.0.38；0 错误、29 警告；沿用既有 1 项 `$$this` 误报忽略，未扩大忽略范围 |
| HAP | **未生成**；本次没有执行完整 Harmony 构建或签名 |
| 真机 | **未测试**；E01 任务卡没有真机检查点 |

新用到的公开鸿蒙接口：`UIContext.getFont()`、`Font.registerFont()`；使用 `$rawfile()` 指向两个字体。无新增权限，无 system_basic / system_core 接口。`build-profile.json5` 的 `signingConfigs` 保持为空。

## CI、真机与下一步

PR #8 待 A1、A3、D1、G1 用户确认。现有 Harmony workflow 只对 base 为 `main` 的 PR 触发；E01 base 暂为 M0 时不会运行，不标记为 CI 通过。#8 合并后改 base 到 `main`，确认 `Harmony / TypeScript / Tests` 和 `Harmony / ArkTS (.ets) check` 全绿，由用户合并 E01，再开始 E02。

E01 无需单独等待真机，可在后续任务的检查点合并核验。若用户选择提前检查：

1. DevEco 打开本分支 `harmony-agent/`，沿用本地原签名，Run；预期能覆盖安装，资源打包和字体注册没有报错。
2. 打开侧栏 → 设置；预期版本为 `0.9.2`，现有布局不变。
3. 回到聊天页，打开模型服务再返回；预期与 M0 行为一致，没有新增灰色/空白页面。

这三步目前均**未测**。字体实际字重、系统语言回退、SVG 与 Eta 画面对照在相应页面接入后按任务卡验证。普通 ArkTS 检查不覆盖资源打包、字体加载、实际渲染或签名，最终能否编译以用户 DevEco 为准。
