# 第三方来源与许可证

本工程包含或链接以下组件。完整许可证源文件保存于 `entry/src/main/resources/rawfile/licenses/`，供后续 HAP 打包使用；0.9.2 本次未生成 HAP。原始文件的版权声明保留在下载的源文件中。

## 0.9.2 起：Eta 直接移植（M1 / E01）

按 [ETA_PORT](docs/ETA_PORT.md) 直接移植本仓库 `app/` 的固定基准 `3b1794d70881c3c849b53d7778dd2bfdf0a46cc5`。本目录的 [LICENSE](LICENSE) 复制根目录许可证全文；移植部分属于修改后的作品，遵守 **PolyForm Noncommercial 1.0.0，仅限非商业用途**。应用名为“随身助手”。

**Required Notice: Copyright © 2026 蛮吉 (Mangi-11).**

| 来源 | 本次移植内容 | 许可证与归属 |
| --- | --- | --- |
| [Eta 固定基准](https://github.com/Lmin2419/eta-background/tree/3b1794d70881c3c849b53d7778dd2bfdf0a46cc5/app) | 文案及复数、11 个 drawable 的 SVG 转换、颜色与尺寸常量、字体配置、`LocaleResourcesTest.kt` 的 2 项单测；生成脚本注明来源 | PolyForm Noncommercial 1.0.0；保留上面的 Required Notice；全文另存 `rawfile/licenses/Eta-PolyForm-Noncommercial-1.0.0.txt` |
| [Material Icons SVG 0.14.15](https://www.npmjs.com/package/@material-design-icons/svg/v/0.14.15) | 按 Eta 源码引用提取 148 个对应样式图标，不重绘 | Apache-2.0；`rawfile/licenses/Material-Icons-APACHE-2.0.txt`；npm 精确版本及完整性散列保存在 lockfile |
| [Miuix 0.9.4](https://github.com/compose-miuix-ui/miuix/tree/39c40f99844227b853f0049a0933b1f3ae6c00ba) | 默认深浅色、字体大小、按钮和对话框尺寸、ArrowRight 图标；保留原始 Kotlin 输入供离线生成，不作为 Android 运行依赖 | Apache-2.0；保留原文件声明；`rawfile/licenses/Miuix-APACHE-2.0.txt` |
| [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) | Eta 中的 regular / bold 两个字体文件原样复制；仅代码字体注册，正文仍用系统字体 | OFL-1.1；`rawfile/licenses/JetBrainsMono-OFL.txt` 保留 JetBrains 的版权声明；许可证文本来源 v2.304，不据此推定 Eta 内字体版本 |
| Eta `ProviderBranding.kt` 引用的品牌图标 | 22 个 WebP 文件原样复制，未来仅用于标识对应模型服务 | 保留上游来源；品牌与商标权仍属各自权利人，项目许可证不额外授予商标权 |

具体来源、逐项资源映射、散列和适配差异见 [E01 交付记录](docs/E01_RESOURCE_PORT.md)、[资源输入来源](scripts/eta-upstream/README.md) 以及 `docs/eta-{strings,icons,theme,fonts}.json`。关于页所需的作者、许可和 Required Notice 已加入资源，按计划在 E05 显示。

## 原生桥及其他依赖（旧能力）

| 组件 | 来源/版本 | 许可证与改动 |
| --- | --- | --- |
| OpenHarmony HDC | [openharmony/developtools_hdc](https://github.com/openharmony/developtools_hdc/tree/10e867eb30f43b605089458be20fd85e943a327d)，OpenHarmony-6.0-Release | Apache-2.0；通过 `scripts/patch-hdc.py` 修改私有目录、socket、日志、定时常量、启用加密协商；TCP 路径保留官方认证，未加入绕过授权逻辑 |
| HDC 去 USB 补丁 | [LMV475/hap-installer](https://github.com/LMV475/hap-installer/blob/ef72d65354a899ddca0e806f7b752b5f0cb621a3/entry/src/main/cpp/hdctools/fix_no_libusb.patch) | Apache-2.0；补丁保存为 `scripts/no-usb.patch`；未复制该项目的 NAPI 桥接或界面 |
| Bounds checking / securec | [OpenHarmony](https://github.com/openharmony/third_party_bounds_checking_function/tree/61baa7a3eb9347efca58397d1d0504faa96d79f0) | MulanPSL-2.0，未修改 |
| OpenSSL | [openssl/openssl](https://github.com/openssl/openssl/tree/openssl-3.0.16)，3.0.16 | Apache-2.0，未修改源码；静态编译到原生库 |
| LZ4、xxHash | [lz4/lz4](https://github.com/lz4/lz4/tree/v1.10.0)，1.10.0 | 库代码为 BSD-2-Clause，未编入 GPL 命令行程序；xxHash 版权声明单独保留 |
| libc++ runtime | HarmonyOS SDK 携带的 `libc++_shared.so` | Apache-2.0 with LLVM exceptions 及 SDK LLVM NOTICE 中列出的条款；运行库随 HAP 打包 |
| libuv、NAPI、HiLog | HarmonyOS 系统 SDK 提供 | 动态链接系统库；不打包第三方 libuv 构建产物 |

依赖下载地址和 SHA-256 记录在 `scripts/native-sources.json`。没有链接 libusb。

HDC 原始源文件声明 Copyright (C) 2021 Huawei Device Co., Ltd.；securec 原始源文件的 Huawei 版权声明、LZ4/xxHash 的 Yann Collet 版权声明以及其他上游声明均保留。修改清单在补丁脚本中，本工程不声称这些第三方实现为原创。

## 0.3–0.9.1 的历史使用范围

以下段落记录此前独立实现阶段的范围，不描述 0.9.2 起的直接移植；当前范围以上面的 M1 / E01 清单为准。

[Mangi-11/Eta](https://github.com/Mangi-11/Eta) 在 `3019c43ce60cf53ebe1c2ba5ec86ec630830f39d` 的实现用于架构研究。其许可证为 PolyForm Noncommercial，不能视作 Apache/MIT 工程任意复制。本工程未复制它的源代码、提示词或素材；AgentLoop、模型适配、鸿蒙执行器、界面和 NAPI 桥接均独立实现。

参考 [轻启 / quietstart](https://github.com/thx853068675-dev/quietstart) 的公开说明确认手机端无线调试路线；没有取得或复制其专有执行实现。后续向 Eta 贡献或合并代码时，应按上游当时的贡献与授权规则处理。

0.4 进一步参考 Eta 的 TopBarBackdrop、MiuixScaffoldPage、AgentExecutionService 的交互与生命周期组织方式，使用 ArkUI 背景模糊、窗口安全区、BackgroundTasksKit 与 NotificationKit 独立实现，未移植 Compose/Miuix 或 Android 服务代码。经用户授权读取 Lmin2419/eta-background 的 VDriveWorker.java（8ae286a8993efaa4650426ae450ab4634f2b2354）用于能力核查，未复制其引擎或私有素材。

0.5 交互对照上游同一 commit 的 AgentChatBody.kt 和 RemoteModelImportSheet.kt：正向消息流、跟随底部/用户滚动仲裁、紧凑输入区、远端目录搜索/多选确认、取消不改草稿。使用原生 ArkUI 与独立 TypeScript 协议/状态实现，没有把 Kotlin 源文件、Compose/Miuix 库或上游素材打包进工程。

0.9 经用户要求，重新对照自有 `Lmin2419/eta-background` 的 `8ae286a8993efaa4650426ae450ab4634f2b2354`。重点阅读 AgentAppRoot、AgentHomeScreen、ConversationSidePaneScaffold、AgentChatBody、AgentChatInputBar、ModelProviderListScreen、ModelProviderDetailScreen、ProviderModelsTab、RemoteModelImportSheet，并查看仓库自带聊天首页截图。新建页面栈、会话/输入投影、独立模型草稿和原生 ArkUI 页面；没有把截图、Kotlin 文件或 Android 库打入 HAP，新增 SVG 图标自行绘制。对应清单见 `docs/FOUNDATION_0.9.md`。旧 0.8 源码单独归档，旧扩展能力不作为本版功能声明。

## unicode-segmenter 0.14.0（L2b）

仅 vendoring 扩展字素分割模块、依赖的 Unicode 数据与类型声明。来源 https://github.com/cometkim/unicode-segmenter ，MIT；Copyright (c) 2024 Hyeseong Kim。Rust 上游片段同为 MIT，两个许可证及逐文件 SHA-256 放在 `entry/src/main/ets/core/eta/markdown/vendor/unicode-segmenter/`。纯 JS，无原生/网络依赖、不安装全局 polyfill；用于替代 Eta 的 JVM BreakIterator，Markdown 块解析仍为本仓库 TS 实现。

## models.dev 离线目录（E06 / 0.9.18）

来源为 Eta 固定基准 `3b1794d70881c3c849b53d7778dd2bfdf0a46cc5` 的 `app/src/main/assets/catalog/models-dev-api.json.gzip`，Git blob SHA-1 `9d9c322cf19c1c129e70da1947586521e8aa2160`。数据版权 Copyright (c) 2025 models.dev，MIT；原文保存在 [licenses/models-dev-MIT.txt](licenses/models-dev-MIT.txt)，并随资源打包至 `rawfile/licenses/models-dev-MIT.txt`。

`scripts/port-eta-catalog.ts` 验证原 gzip 散列，按移植解析器过滤不支持的候选并删除未使用字段；生成 `rawfile/catalog/models-dev-api.json`，173 个服务、4,932 个模型。生成前后候选集必须完全等值。此快照不包含 API Key，不代表全部模型在本平台或用户账户可用；运行时可从 models.dev 更新。
