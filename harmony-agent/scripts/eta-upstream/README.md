# E01 固定资源输入

这些文件仅用于离线提取资源，不编入 Android / Compose 运行依赖。保留源文件内容和版权头；完整清单与 SHA-256 见 [sources.json](sources.json)。更新来源必须有新的任务卡，不自动跟进上游。

## Miuix

来源：[compose-miuix-ui/miuix 0.9.4](https://github.com/compose-miuix-ui/miuix/tree/39c40f99844227b853f0049a0933b1f3ae6c00ba)，固定提交 `39c40f99844227b853f0049a0933b1f3ae6c00ba`，Apache-2.0。

| 本地文件（`miuix-0.9.4/`） | 上游位置 |
| --- | --- |
| `LICENSE` | `LICENSE` |
| `Colors.kt` | `miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/Colors.kt` |
| `TextStyles.kt` | `miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/TextStyles.kt` |
| `ArrowRight.kt` | `miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/icon/basic/ArrowRight.kt` |
| `Button.kt` | `miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Button.kt` |
| `Card.kt` | `miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Card.kt` |
| `DialogContentLayout.kt` | `miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/layout/DialogContentLayout.kt` |

## JetBrains Mono 许可证

`JetBrainsMono-OFL.txt` 原文来自 [JetBrains/JetBrainsMono / OFL.txt](https://github.com/JetBrains/JetBrainsMono/blob/cd5227bd1f61dff3bbd6c814ceaf7ffd95e947d9/OFL.txt)，tag `v2.304`，提交 `cd5227bd1f61dff3bbd6c814ceaf7ffd95e947d9`。

**字体文件本身来自 Eta 的 `app/src/main/res/font/`，不是从该 JetBrains tag 重新下载。** 此处的版本号只表示许可证文本来源。字体 SHA-256 见 [eta-fonts.json](../../docs/eta-fonts.json)。

## 其他输入

- Eta 的 XML / Kotlin / 字体 / 品牌标识从仓库根目录 `app/` 只读提取，基准 `3b1794d70881c3c849b53d7778dd2bfdf0a46cc5`。Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
- Material Icons 由 `npm ci` 按 lockfile 安装 `@material-design-icons/svg@0.14.15`，不提交 node_modules。
- `../eta-port/legacy-strings.json`、`legacy-colors.json` 保存 M0 仍在使用的资源；`harmony-strings.json` 为鸿蒙专属的关于页与未接入说明。生成器明确分开统计这些补充项与 Eta 条数。
