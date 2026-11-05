# Eta 后台版构建与分发

## 从源码构建

使用 JDK 25、仓库自带的 Gradle Wrapper、Android SDK `platforms;android-37.0` 和 `build-tools;35.0.0`，并设置 `ANDROID_HOME`。CI 工具版本见 [setup-build](actions/setup-build/action.yml)。

```bash
bash etax/check_hooks.sh
python3 -m unittest discover -s etax/engine/tests -v
bash etax/embed_engine.sh
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug :app:assembleDebug
```

引擎部署测试需在 Linux 构建主机运行，并安装 mksh、BusyBox（ash）和 Toybox；也可通过 `ETA_TEST_MKSH`、`ETA_TEST_BUSYBOX`、`ETA_TEST_TOYBOX` 指定各自的可执行文件路径。它们用于复现 Android Shell 的锁、端口交接与回滚行为，CI 会安装对应工具。

`embed_engine.sh` 从 `etax/engine/src` 和 `etax/shared/src` 编译 Root 引擎，读取源码 BUILD 常量，并将 dex、版本与启动脚本放入 APK assets。这些是生成文件，不提交到 Git。直接运行 Gradle 前必须先执行该步骤；签名构建和 PR 检查共用同一个脚本。

本地自定义工具链可设置 `ANDROID_JAR`、`R8_JAR`、`JAVA`、`JAVAC`。引擎默认编译目录为 `etax/engine/build/`，已忽略。升级引擎源代码时同步更新 `VDrive.java` 中的 BUILD 标识。

## GitHub Actions

唯一的工作流是 **Etax Build**（`.github/workflows/etax-build.yml`），推送到除 `harmony/**` 以外的任意分支或手动运行时触发。只改 `docs/`、`harmony-agent/`、`HARMONY.md` 或各 README 的推送不触发构建。三个 job 依次执行：

| Job | 何时运行 | 内容 |
| --- | --- | --- |
| `check`（测试与 Lint） | 每次触发 | 接线检查、引擎部署测试、引擎编译内嵌、全量单元测试、lint；失败报告保留 7 天 |
| `build`（签名构建 APK） | `check` 通过后，仅 `main`、`etax-*` 或手动运行 | Release 构建、签名、包名与引擎配对检查，上传 APK，保留 30 天 |
| `release`（发布 GitHub Release） | `build` 成功后，仅 `main` 且使用发布证书 | 核对 APK 摘要后发布为预发布，标签为 `etax-<versionCode>-<短提交号>` |

测试只在 `check` 里跑一次；PR 页面显示其源分支提交的检查结果，因此不再单独设置 PR 工作流。单元测试先于 lint 执行，减少 CPU 竞争对 UI 时序测试的影响。如需在分支规则中设置必需检查，选择 `测试与 Lint`。

自动发布的 Release 默认标为预发布；真机验证通过后在 Release 页面取消预发布即可。临时证书构建不会发布。

## 签名

在 `Settings → Secrets and variables → Actions` 配置以下四项；不要提交证书或密码：

| Secret | 内容 |
| --- | --- |
| `ETAX_KEYSTORE_BASE64` | 发布 keystore 的 Base64 文本 |
| `ETAX_STORE_PASSWORD` | keystore 密码 |
| `ETAX_KEY_ALIAS` | 签名别名 |
| `ETAX_KEY_PASSWORD` | 对应私钥密码 |

配置了 keystore 但缺少其他项会直接报错。全部未配置时会生成临时证书，仅供自行测试；临时证书每次不同，不能用来覆盖既有安装。维护者分发必须沿用原发布证书，避免用户需要卸载及丢失数据。

CI 将上述 Secrets 映射为 Gradle 读取的 `ETA_RELEASE_STORE_FILE`、`ETA_RELEASE_STORE_PASSWORD`、`ETA_RELEASE_KEY_ALIAS`、`ETA_RELEASE_KEY_PASSWORD`。本地 Release 构建也可提供这四个环境变量后运行 `./gradlew :app:assembleRelease`。

## 版本、历史与下载

`app/build.gradle.kts` 的 `versionCode` 使用 `yyyyMMdd` 加两位序号，只能递增。`versionName` 表示当前上游基础版本；后台版变更由 versionCode、源码提交与 [更新日志](../etax/README.md) 区分。CI 文件名如 `Etax-v3.3.5+etax.<短提交号>.apk`。

历史源码通过分支和提交保留，无需复制整套源码目录。正式分发前更新版本日志，在真机检查通知内回复、双任务、微信截图坐标、横屏游戏及前台接管，再从成功的 Etax Build 取得签名 APK。

对于 `2026102902` 的完成提示，还需复测单任务与双任务结束、完成/停止/失败状态、三个按钮、提示开关及划掉通知后的行为。操作路径见[完成提示使用说明](../etax/README.md#completion-notice-guide)。记录实际手机、系统版本和结果；CI 通过与 ColorOS 的动画和横幅展示分别记录。

从 `2026103001` 起，另外检查内置搜索在手机网络上的正常结果和失败提示，确认 DeepSeek／自定义配置不会被自动替换；从设置页及权限健康页分别打开悬浮窗设置，覆盖已授权、未授权以及返回后状态刷新的情况。搜索故障记录 `code`、`attempts` 和网络类型，避免将其他网站可访问当作目标搜索服务正常或故障的证据。

更新中英文首页及版本记录时，下载入口、源码提交、versionCode、引擎标识和 SHA-256 应对应同一份已验签 APK。仅补充文档时继续引用该安装包的源码提交，避免将文档提交号误写成安装包版本。

从 `2026103002` 起，增加虚拟屏生命周期验收：20–30 分钟后台读取和截图后，分别打开已缓存与未运行的应用；完成通知接管后再重复点击，确认不重复启动；交还尚未就绪时开始另一任务，确认不会回收过渡中的屏。代码回归覆盖创建失败、Surface 断开失败、操作超时与真实清理完成。超时仅能请求协作取消，不能保证终止挂起的 Binder；窗口就绪也不代表应用内容已经加载。实机白屏问题单独记录，不以 CI 通过代替结论。

从 `2026103003` 起，再区分“最近任务里看到缩略图”和“任务已实际移到主屏”：前者不应误暂停；后者应暂停该任务并停止旧屏输入，另一后台任务不受影响。手动恢复后重新取得屏幕和观察；旧回调不影响新绑定，旧会话不得自动把应用拉回后台。保持完成页面的正常打开与通知内续接回归。

Actions 附件需要登录且到期失效。长期下载由维护者在本仓库 Releases 创建版本、选择经过验证的提交并上传 APK，附上版本说明与 SHA-256。当前流程不自动创建或发布 Release；推送标签也不会自动触发构建。发布源码及 APK 时保留 [LICENSE](../LICENSE) 和上游 Required Notice。
