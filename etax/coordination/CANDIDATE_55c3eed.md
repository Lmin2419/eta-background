# 三轮同步候选 55c3eed：验证与交付记录

更新时间：2026-11-02T14:40:42Z。本记录依据用户批准第一轮、第二轮、第三轮同步实施（C011）；只对应本次固定源码与实际下载核验的候选。不是正式发布或真机通过声明。

## 源码身份与范围

- PR：[#45](https://github.com/Lmin2419/eta-background/pull/45)，Draft/open、未合并。
- 分支：`etax-340-step1-foundation`。
- head：`55c3eed287bbe5b5c7fed60e2c46ace1947d7f9c`。
- 本轮基线：`99f2d87843a0d07ba4a6ca6998019e7ceb1a8d8e`；功能提交 `64c268a31fc943683deda07a5db0ee08edfa52c0`，最终提交只修正复制名称的 Compose 配置感知资源读取。
- tree：`c9e19250d63e948cdd27b0770816f5e081d9f069`。
- 相对本轮基线 55 文件，+2,110/−95；包含实现、测试、三种语言资源、README/更新说明。未改业务包名、versionCode、签名、系统 Hook 或引擎代码。
- 原 e45 稳定性修复随候选交付：运行/暂停通知可直接打开 ETA 会话；真实终态后撤彩边；保留主屏就绪确认后直接释放虚拟显示的收尾顺序。
- 新增普通读/计数任务的有限取数指导、固定区间通知分页与留存匹配总数、有界自动执行统计，以及轻量常用任务/桌面入口。没有额外模型分析请求、常驻服务、任务数据库或定时工作流。
- 集成回归另修正只读恢复查询的协程取消与快捷任务/通知导航竞争；不取消正在执行的后台任务。
- [功能与统计口径说明](https://github.com/Lmin2419/eta-background/blob/55c3eed287bbe5b5c7fed60e2c46ace1947d7f9c/etax/EFFICIENCY_AND_COMMON_TASKS.md)
- [开发更新日志](https://github.com/Lmin2419/eta-background/blob/55c3eed287bbe5b5c7fed60e2c46ace1947d7f9c/etax/README.md)

## 自动化证据

| 检查 | 实际结果 |
| --- | --- |
| 功能提交 64c268a 本地全量单测 | 2,240 项；0 失败、0 错误、1 跳过 |
| 最终 55c3eed 本地 Lint / Debug | 通过；0 错误、391 Warning、1 Hint |
| 接线检查 | 12/12 通过 |
| 引擎部署回归 | 真实本地兼容 shell 与 Linux CI 均 8/8 通过 |
| [最终源码 Etax Build](https://github.com/Lmin2419/eta-background/actions/runs/41460652172) | 成功；全量 2,240 项、0 失败/错误、1 跳过；Lint 0 错误；Release 与签名/引擎核对通过 |
| [最终源码 PR Check](https://github.com/Lmin2419/eta-background/actions/runs/41460660662) | 成功；单元测试、Lint 与 Debug 构建通过 |

本地全量先在功能提交 64c268a 执行，最终 55c3eed 仅修复配置感知资源读取；最终 SHA 的全量结论来自实际 Etax Build 日志，不把旧 SHA 的结果冒充新 SHA。唯一已知本地跳过是既有 EtaWaveShaderTest 的 native 子 shader 支持假设不满足；没有新增测试跳过。两项英文数量资源产生非阻断 PluralsCandidate 提示，固定上限并非 1；未通过关闭 lint 隐藏错误。

针对本次真实问题的覆盖：

1. 通知 61 条分页、同毫秒排序、冻结时间边界、更新/覆盖/过期、无效游标和不可读取错误；保留通知数不冒充完整聊天消息数。
2. 常用任务原子存储、损坏保护、编辑/删除后快捷入口、双击与 Intent 恢复去重、最新导航优先、新会话无旧历史/草稿/附件/角色。
3. 互斥阶段计时、并行工具、暂停/恢复、重试、缺少首增量时不编造、统计归档与恢复、执行统计不覆盖会话预览或进入模型上下文。
4. 既有终态/彩边、通知入口、息屏保护与 VDrive 显示释放回归随全量执行。

开发过程中确实出现过测试失败，未绕过后直接宣称通过：
- 新统计持久化测试初始使用 API 33 时，Robolectric 字体文件系统在 SDK 切换中初始化失败；统一该纯事件持久化测试到 API 36 后通过。
- 常用任务夹具退出超时定位为只读运行恢复查询未随协程取消中断；生产查询使用既有 runInterruptible，保留原 10 秒完成断言，组合回归与全量回归通过。
- 首次 Lint 实际报出 CommonTasksScreen 的 LocalContextGetResourceValueCall 错误；改用 LocalResources 感知系统配置，保留完整 lint 检查，最终候选重新验证。
- 部署测试初始环境 PATH 缺少 javac/mksh；补齐本地已安装工具后实际执行 8 项通过。

未在代码或 CI 中跳过失败、关闭安全校验、放宽任务控制权保护或增加第三方强制重启。

## 实际 APK 核验

| 项目 | 核验值 |
| --- | --- |
| 候选 APK | [Etax-v3.3.5+etax.55c3eed.apk](https://github.com/Lmin2419/eta-background/actions/runs/41460652172/artifacts/13149228404) |
| artifact / run | 13149228404 / 41460652172；归属 head 55c3eed287bbe5b5c7fed60e2c46ace1947d7f9c |
| 大小 | 6,767,283 bytes |
| SHA-256 | `ef1eb66e99ebf80cfab18ea14fe1f2bd93ec58e4996c1007649064ea660a5979` |
| 包名 | `io.github.mangi.etax` |
| versionName / versionCode | `3.3.5+etax.55c3eed` / `2026103003` |
| 原证书 SHA-256 | `dc309f48b192f58aef3c750841728e284ad05db2a07a607b2e1fafbcc4c1a2a5` |
| 引擎 | `20261102-e45-finalize` |
| GitHub 工件到期 | 2026-12-02T14:36:13Z |

APK 真实字节的 SHA-256 与 GitHub 工件 digest、CI 日志三方相同。使用本地 aapt2 / apksigner 独立读取实物，证书与前一已核验后台版一致，可覆盖安装。未将临时签名包冒充原签名；没有把下载链接当成已经验包。

实际下载后独立核对 APK 的包名、版本、原证书 SHA-256，以及以下五项与固定源码/本地重编译引擎逐字节一致：

- assets/vdrive.dex
- assets/vdrive.version
- assets/eta-engine/vd.sh
- assets/eta-engine/install_vdrive.sh
- assets/eta-engine/check_pair.sh

没有打包 engine.token。引擎 `20261102-e45-finalize`，本地 dex SHA-256 `40c45f260e324ff8945455372346373f7f8e6e6f6cdcd263d8ad6517f2e86331`。此次不重写引擎协议；本地重新编译用于核对实际候选内容。

## 未验证与手机重点

没有远程安装/操作手机；当前 head 没有用户设备实测。旧版本报告和先前接管成功不能移记为本候选通过。未声称具体提速比例，也未将旧报告的通知 61 行当成完整消息证据。

建议用户只需关注：
1. 首页右上角 → 常用任务，新建并运行两次是否各开新会话；编辑后桌面入口读取新指令、删除后不再执行。
2. 运行通知“打开 ETA 会话”和主体预览入口分别正确；两个任务先后结束，最后一个结束后彩边消失。
3. 暂停 → 接管 → AI 继续后正常完成，展开执行统计；最终答复与通知内继续仍保留。
4. 同模型/网络下做小范围计数，先看统计正确性与覆盖边界，再根据新增实测耗时比较。

ColorOS 过渡动画、通知布局、桌面固定入口与实际耗时尚待验证。长按电源键/LSP/Vector 软重启、长任务后全局冷启动白屏的唯一根因仍未确证；不主动触发这些风险路径。常用任务未纳入现有手动备份格式。

## 边界

保留单 VDrive、双后台任务上限、同 App 串行、旧 screen_*、流体云/每任务通知/三个完成入口/内联回复、已授权的息屏后台与锁屏保护、Harmony 内容。无新教学、数据项目、调度或跨会话执行器。

只提交开发分支源码和协作证据；未合并 main、发布、打标签或替换正式下载。每小时自动化保持关闭，不唤醒 dsh。候选 APK 仅使用既有 CI 工件，不把二进制或任何密钥写入仓库。
