# 第三方来源与许可证

本工程包含或链接以下组件。完整许可证随工程及 HAP 保存于 `entry/src/main/resources/rawfile/licenses/`。原始文件的版权声明保留在下载的源文件中。

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

## Eta 的使用范围

[Mangi-11/Eta](https://github.com/Mangi-11/Eta) 在 `3019c43ce60cf53ebe1c2ba5ec86ec630830f39d` 的实现用于架构研究。其许可证为 PolyForm Noncommercial，不能视作 Apache/MIT 工程任意复制。本工程未复制它的源代码、提示词或素材；AgentLoop、模型适配、鸿蒙执行器、界面和 NAPI 桥接均独立实现。

参考 [轻启 / quietstart](https://github.com/thx853068675-dev/quietstart) 的公开说明确认手机端无线调试路线；没有取得或复制其专有执行实现。后续向 Eta 贡献或合并代码时，应按上游当时的贡献与授权规则处理。

0.4 进一步参考 Eta 的 TopBarBackdrop、MiuixScaffoldPage、AgentExecutionService 的交互与生命周期组织方式，使用 ArkUI 背景模糊、窗口安全区、BackgroundTasksKit 与 NotificationKit 独立实现，未移植 Compose/Miuix 或 Android 服务代码。经用户授权读取 Lmin2419/eta-background 的 VDriveWorker.java（8ae286a8993efaa4650426ae450ab4634f2b2354）用于能力核查，未复制其引擎或私有素材。

0.5 交互对照上游同一 commit 的 AgentChatBody.kt 和 RemoteModelImportSheet.kt：正向消息流、跟随底部/用户滚动仲裁、紧凑输入区、远端目录搜索/多选确认、取消不改草稿。使用原生 ArkUI 与独立 TypeScript 协议/状态实现，没有把 Kotlin 源文件、Compose/Miuix 库或上游素材打包进工程。

0.9 经用户要求，重新对照自有 `Lmin2419/eta-background` 的 `8ae286a8993efaa4650426ae450ab4634f2b2354`。重点阅读 AgentAppRoot、AgentHomeScreen、ConversationSidePaneScaffold、AgentChatBody、AgentChatInputBar、ModelProviderListScreen、ModelProviderDetailScreen、ProviderModelsTab、RemoteModelImportSheet，并查看仓库自带聊天首页截图。新建页面栈、会话/输入投影、独立模型草稿和原生 ArkUI 页面；没有把截图、Kotlin 文件或 Android 库打入 HAP，新增 SVG 图标自行绘制。对应清单见 `docs/FOUNDATION_0.9.md`。旧 0.8 源码单独归档，旧扩展能力不作为本版功能声明。
