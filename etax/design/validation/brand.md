# 胖小胖品牌收尾验证

基线：`main` 的 `33b6d1c` / `2026110613`。本版：**`2026110614`**，基础版本 `3.3.5`，引擎仍为 `20261105-e49-parallel-observe`。依据 `01-design-language.md` 的 B「蜂蜜焦糖」与 §9 品牌规则；仅修正品牌展示、界面颜色与原有测试等待方式，不重构业务。

## 修改清单

| 位置 | 修改 | 保持的行为 |
| --- | --- | --- |
| `AgentOverlayContent.kt` | 旧 `7AB8FF/B59CFF/FF9EC7/FFC98A` 流光改为 `EtaAurora.honey/peach/rose/lavender`，尾部重复 honey 保持环形接缝连续；运行/暂停/完成/失败统一为 `running/paused/success/danger` | 触摸、窗口几何、动画时序、状态机和操作回调不变 |
| `EtaSpeechFeedback.kt` | 冷灰 `34363B/F2F3F5` 改为 `surfaceSunken`；错误 `B42318/FF9E99` 改为 `dangerText`，普通文字为 `textPrimary` | 继续使用现有状态和重试/设置入口；dangerText 是已有的可读文字 token，浅色 `C22E2E`、深色 `FF7A7A` |
| 收藏按钮 | 原固定金色改为 `accent`，圆底改为不透明 `surfaceRaised`，未收藏图标为 `textSecondary`；角色详情复用的 `FavoriteColor` 同步 | 保留按钮大小、收藏动作、角色身份与照片，不让焦糖色落在任意深浅照片上失去对比 |
| Markdown | 无 Composition 的默认链接/行内代码底改为浅色主题的 `accent/surfaceSunken`；Important 提示色改为 `attentionText` | 实际页面仍通过 `rememberMarkdownStyle` 传入当前主题，切换深色时走原有样式缓存键；没有更改解析、导出或内容 |
| 授权网页 | HTTP 回调 HTML 的 `<title>` 改为「胖小胖」 | UTF-8、按字节计算的 Content-Length、状态校验、回调代码和一次性监听生命周期不变 |
| 系统应用标签 | 构建配置的 `etaxLabel` 改为「胖小胖」 | applicationId、组件类名、权限和系统助手绑定不变；覆盖桌面及系统助手选择页的显式 label |
| 用户可见文案 | 三种语言的 `background_runtime.xml`、`execution_service.xml`、`native_assistant_strings.xml`、`root_capabilities.xml`，共 12 个文件、52 处文本替换；小爱与小布的英文 UI 回退文案同步 | 资源名、格式占位符、通知 ID、渠道 ID、业务判断不变 |
| 设计与发布 | S1.2 改为猫标记；5 个画板中的 η/可见旧名称改为猫源图/胖小胖，设计语言板的品牌色样改为焦糖色；设计 README 更新品牌入口；Release 展示标题与正文同步名称 | 画板仍为既有布局，APK、导出和备份文件名不改 |

## 硬编码颜色逐项判断

以下颜色已逐个检查，内容表达与界面选择状态分开处理。

| 文件 / 原值 | 分类与结论 | 理由 |
| --- | --- | --- |
| CharacterAvatar：`F58FA8 → C56CD6` | 内容色，保留 | 名字散列选择的角色身份色之一 |
| CharacterAvatar：`7FA7F5 → 6A6FE0` | 内容色，保留 | 同上，不能把所有角色都染成品牌色 |
| CharacterAvatar：`6CC9B4 → 3E9FB8` | 内容色，保留 | 同上 |
| CharacterAvatar：`F5B36C → E07A5F` | 内容色，保留 | 同上 |
| CharacterAvatar：`A48BEB → 6C63C9` | 内容色，保留 | 同上 |
| CharacterAvatar：`8CC97A → 4FA37C` | 内容色，保留 | 同上 |
| CharacterAvatar：`Color.White` 字标 | 内容色，保留 | 与角色身份渐变配套的字标，不是页面正文色 |
| CharacterPortraitCard：`Transparent → Black 72%` | 照片遮罩，保留 | 承载角色名/标签，随照片内容保证叠字边界 |
| CharacterPortraitCard：白色角色名、白色 92% 标签字、白色 18% 标签底 | 内容遮罩配套，保留 | 不改用户角色图像上的既有信息表达 |
| CharacterPortraitCard：`FFC94D` 收藏星 | 界面选择色，改为 `accent` | 收藏状态必须跟随主题；详情页复用同一值 |
| CharacterPortraitCard：黑色 28% 收藏圆底、白色未选星 | 界面控件，改为 `surfaceRaised/textSecondary` | 配合焦糖选中色，避免任意照片底下的对比不确定性 |
| MarkdownInline：`3482FF` 链接、`1F808080` 代码底 | 界面色，改为 `accent/surfaceSunken` | 不是代码语言高亮或用户指定内容色 |
| MarkdownContent：`8B5CF6` Important 提示 | 界面语义色，改为 `attentionText` | 自绘提示标题/图标应使用随主题变化的可读语义色 |
| Markdown 其他项 | 原已使用主题/状态色，保留 | Note/Tip/Warning/Caution、引用线、表格、复制按钮和代码块由现有主题提供；该渲染目录没有另一套硬编码语法高亮色表 |

## 剩余名称与保留理由

对 `app/src/main` 的 Kotlin、Java、XML、HTML、JSON 可见字符串，以及构建标签、画板做了全局搜索；资源不仅检查 `strings.xml`，也检查拆分的 XML。英文、简体、繁体均已核对。

| 保留项 | 理由 / 核查结论 |
| --- | --- |
| 包名、内部类名、`Eta*` 标识符、Theme.Eta 资源名 | 用户明确要求保持；名称修正只发生在展示文本 |
| 所有 User-Agent、日志模板、线程名、内部 TAG | 用户明确要求保持，避免协议/诊断改变 |
| 备份结构、备份/Markdown/诊断导出文件名 | 用户明确要求保持兼容；没有修改序列化或导出代码 |
| 关于页的 Eta 上游致谢和 GitHub 链接 | 三语致谢原文与链接保持原样，不能抹去上游归属 |
| `AssistantBinding.displayName = "Eta"` | 现有用途为日志/诊断，不是系统应用 label，保持日志不变 |
| `PhoneProviderAccess` 的 `"Eta"`、MCP `clientInfo.name` | 分别是 provider attribution tag 与协议客户端标识；不是界面标题 |
| 内置角色 `creator = "Eta"` | 原作者/内容元数据，保持归属与导出内容 |
| `AgentAppRoot` 的 `--es title Eta` | 本地只把值传给厂商 `com.oplus.battery/...PowerControlActivity`；接收端是厂商私有实现，仓库无法确认是否仅展示或参与内部逻辑。本次保留完整命令；没有把“本应用未读取它”当作厂商端无功能影响的证明 |
| 通知渠道 | 运行、完成、朗读、助手语音、后台命令等渠道名称已是通用描述，无需改名；渠道 ID 不变 |
| TalkBack | 自有品牌读屏入口使用 `app_name`，三语值均为胖小胖；任务状态/操作标签不改，仍可独立读出和操作 |
| 旧画板其他历史颜色、早期阶段验证记录 | 本次同步 η/可见名称及明确的品牌色样；未重做整套画板。颜色冲突以最新 `01` 的 B token 为准；本记录覆盖阶段 7 D8 中“收藏固定金色”的旧结论 |

## 流光与 24dp 猫标记

`EtaAssistantEdgeGlow`、当前调用的环形 `EtaAssistantWave` 原本已经引用 honey/peach/rose/lavender，本次保留。新修正的任务边缘光也使用同源颜色。浅色与深色原生渲染均以暖蜂蜜/蜜桃为主，玫瑰/淡紫作过渡；未发现冷蓝残留、灰脏的叠色或白色过曝块。保留原透明度、流动时序和减少动画静止逻辑，不用改变几何或动画来掩盖颜色问题。

24dp 猫标记在 mdpi（24 物理像素）及实际助手 xhdpi fixture 中都能辨认耳朵、脸型与双眼。保留原彩色猫，不切换剪影；与已有通知剪影并排核对，未新增或手改任何矢量。`pangxiaopang.svg`、`svg2vector.py`、桌面/主题/通知/任务品牌资源均与基线逐字节相同，不需要重新生成。

- [浅色：胶囊、反馈、波形、两种边缘光和 24dp 对照](brand/brand-light.png)
- [深色：同一组合](brand/brand-dark.png)
- [助手面板浅色](brand/assistant-light.png) / [助手面板深色](brand/assistant-dark.png)

以上为 Robolectric Native Graphics 截图，不是真机截图。小尺寸辨识与 OLED 低亮度下的流光感受仍列入手机验收。

## 测试与发布

`SettingsReorganizationTest.completionAlertsAndLocalThinkingKeepTheirOriginalPreferences` 保留原测试名、真实手指滚动/点按与本地思考开关断言；不跳过、不直接调用保存代替 UI。监听目标完整设置值的协程在 IO 调度器运行，等待期间推进 Compose 帧和主线程回调，避免阻塞 UI 写入；完成后再从 repository 回读核对。10 秒真实时钟上限只负责报错，时间到不能使测试通过。

本地 Windows / JDK 25 / Android SDK 37：

- 相关回归 **65 项全部通过、0 跳过**，包含品牌渲染、阶段 6/7、设置、授权、Markdown 解析/缓存/导出及对比度。
- 授权测试额外验证中文 HTML title、UTF-8 字节长度，保留状态校验、错误回调、一次性关闭与绑定测试。
- 设置持久化原用例另行连续执行 **3 次全部通过**；最终 Markdown 提示色补齐后再跑上述相关范围。
- 接线 **12/12** 通过；引擎重新编译内嵌成功，BUILD 保持 `20261105-e49-parallel-observe`。Linux 引擎部署与全量单测交由 CI，不能用 Windows 定向测试冒充全量通过。

本地 `lintDebug` / `assembleDebug` 成功：**0 error/fatal、463 warning、1 hint**，未增加抑制。实际 Debug APK 读取到包名 `io.github.mangi.etax`、versionCode `2026110614`，默认/简繁体系统应用 label 和启动 Activity label 均为「胖小胖」。10 个品牌源文件/生成资源与基线逐字节相同；修改资源的 key、属性与格式占位符不变，User-Agent 和日志无差异。

远端 `check`、`build`、`release` 必须全部成功，Release 的 versionCode、源码提交、签名和 APK SHA-256 对应后才交付。提交时远端运行尚未生成，最终交付附本版的直接 [Etax Build](https://github.com/Lmin2419/eta-background/actions/workflows/etax-build.yml?query=branch%3Amain) 与 [Release](https://github.com/Lmin2419/eta-background/releases) 链接。

委派通过 Tencent 的 coding / quick / review，分别用于等待策略、角色颜色分类和厂商命令边界复核；主会话核对实际文件、修正建议并运行测试。配置主模型分别为 `cn:glm-5.3`、`cn:deepseek-v4.1-flash`、`cn:deepseek-v4-pro`。三次均返回有效答案，工具没有返回成功响应的实际路由模型/备用切换元数据，因此不虚构备用调用记录。

## 手机验收清单

安装 **versionCode 2026110614** 对应的同提交签名 Release APK，覆盖安装，记录手机型号、系统版本和结果。以下主要项目在浅色、深色下各走一遍，保留已有数据。

1. **应用名**：桌面、系统应用信息、默认数字助手/语音识别选择页显示「胖小胖」；切换英文、简体、繁体时通知和权限说明无自有品牌旧称。关于页仍保留 Eta 上游致谢及原 GitHub 链接。
2. **悬浮胶囊**：启动后台任务，观察暖色边缘流光，展开后查看/暂停/停止可用；暂停为中性暖灰、完成为绿色、失败为红色，运行品牌色与蜂蜜焦糖一致。需要你仍为紫色，不误作运行中。
3. **语音反馈**：电源键或输入区启动语音，检查正常提示为暖色凹面底，网络/权限错误文字清楚；聆听波形和唤醒边缘光为暖色，深色低亮度下不发脏、不刺眼。减少动画时仍静止。
4. **授权标题**：完成一次支持回调的模型账号授权，浏览器成功页标题是「胖小胖」，提示和回到应用的流程正常。
5. **小猫**：首页空态、助手 24dp 头像可辨认猫形；桌面、主题和通知图标与 2026110613 一致，无 η 字形。
6. **角色和 Markdown**：收藏/取消收藏的星标清楚，角色照片与身份色不变；长回复的链接、行内代码、代码块、Important 提示和表格在浅深色下可读，复制/导出仍可用。
7. **设置保存**：切换「任务完成提示」与本地思考开关，退出再进入仍保持目标值；上下真实手指滚动可用。旧备份可导入，新导出仍使用原文件名与格式。

本版 CI 和 Release 全绿后停下，以上真机结果由用户验收，不自动进入其他改动。
