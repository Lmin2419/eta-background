# 候选 5afaf87：主屏处理后的恢复与采集交还

状态：未发布。源码、自动化及实际原签名 APK 核验完成，真机待用户验证。更新：2026-11-02T10:04:57Z。

## 源码与用户反馈

- PR [#45](https://github.com/Lmin2419/eta-background/pull/45)，Draft/open，head `5afaf87b3f8cd36aecd10f75e57c139b3bc64467`，parent `7c846a3d19d1dbbfee03a5781e9666283e704750`。
- 引擎 `20261102-e44-handoff`，DEX SHA-256 `e34c346ed1358d2f712a4d9635f53e5330a5b829678cbe5f76b1af18b3b6a6ae`。
- 美团验证页手动预览无响应、无提示；移到主屏验证后，小窗继续失败而通知继续成功，再次移到主屏有闪烁。没有手机日志或已装包的独立核验，不定性风控。
- 主要源码：[TaskPreviewActivity](https://github.com/Lmin2419/eta-background/blob/5afaf87b3f8cd36aecd10f75e57c139b3bc64467/app/src/main/kotlin/io/github/mangi/eta/agent/runtime/TaskPreviewActivity.kt)、[VDrive](https://github.com/Lmin2419/eta-background/blob/5afaf87b3f8cd36aecd10f75e57c139b3bc64467/etax/engine/src/VDrive.java)、[VDriveWorker](https://github.com/Lmin2419/eta-background/blob/5afaf87b3f8cd36aecd10f75e57c139b3bc64467/etax/engine/src/VDriveWorker.java)。

## 实际改动

1. 只读窗口直接派发 Runtime 的通知恢复动作，旧屏失效不再成为前置条件；不同窗口人工拥有者仍阻止恢复。
2. 旧页面消失时 release 幂等成功，已迁移目标仍验证拥有者与在途排空，不因关闭预览碰触迁移中的采集、不重建屏。手动窗口仍等释放 ACK，失败或超时不恢复。
3. Runtime 的 finish(notify) 原本直接 stopCapture，release_session 原本直接销毁；任务实际移到主屏后，两者现在保留旧采集，交由原目标窗口就绪检查收尾。未用固定动画延迟、全局显示参数或新 Hook。
4. 同 App 交还尚未结束时返回 CLEANUP_PENDING，使用现有有界等待；没有新增并行容量或放松串行约束。
5. 输入失败提示持续显示，下一帧不是上一输入成功的证据；目标窗口未就绪明确告知未输入。没有自动重复点击或绕过真人验证。

## 验证

- 五项新增回归：只读恢复跨主屏、释放幂等及拥有者/排空、Runtime 清理等待窗口、finish/预览关闭不提前断开采集、输入失败提示保留。其中前四项先在旧源码失败。
- 既有迁移测试的 APP_BUSY 预期改为 CLEANUP_PENDING；仍保留不能抢占/回收旧屏的断言。
- 本地全量：2,191 项，0 失败、0 错误、1 跳过。
- 接线 12/12；部署 8/8，实际 mksh/BusyBox/Toybox 路径。
- 本地 Lint/Debug：通过；Lint 0 错误、389 Warning、1 Hint。实际 Debug 包内 e44 DEX 与生成产物相同。
- [PR Check](https://github.com/Lmin2419/eta-background/actions/runs/41422705824)：成功；读取实际 job 125201828538 日志确认两次 BUILD SUCCESSFUL。
- [Etax Build](https://github.com/Lmin2419/eta-background/actions/runs/41422696799)：成功；实际 job 125201798335 日志确认 2,191 项（0 失败、0 错误、1 跳过），Lint 389 Warning、1 Hint、无错误，Release 构建及签名配对通过。
- 实际原签名 APK：已下载，独立执行 aapt2/apksigner、SHA-256 与五项内嵌资产逐字节检查；不使用旧工件结论。
- 真机未执行；没有远程安装/操作手机。

## 已核验安装包

| 项目 | 实际结果 |
| --- | --- |
| 文件 | [Etax-v3.3.5+etax.5afaf87.apk](https://github.com/Lmin2419/eta-background/actions/runs/41422696799/artifacts/13130804607) |
| Artifact | 13130804607；到期 2026-12-02T09:58:01Z |
| 大小 | 6,737,651 bytes |
| APK SHA-256 | `269e7686a9b1795f05bb14cd462968d816d20fc587880161d54a191457c820cf` |
| 包名 | `io.github.mangi.etax` |
| 版本 | `3.3.5+etax.5afaf87`；versionCode `2026103003` |
| 原证书 SHA-256 | `dc309f48b192f58aef3c750841728e284ad05db2a07a607b2e1fafbcc4c1a2a5` |
| 引擎 | `20261102-e44-handoff` |
| 资产匹配 | vdrive.dex、vdrive.version、vd.sh、install_vdrive.sh、check_pair.sh 五项与本候选源码/生成产物相同；没有 engine.token |

APK 哈希与 CI 日志及 GitHub artifact digest 一致；上表来自实际下载文件。源码更新说明已在业务提交中，候选证据写在协作分支，不因此改变已构建 head。

## 手机重点与限制

自然遇到验证时，在主屏手动完成，再从通知预览点“让 AI 继续”，确认实际任务继续；再次从最近任务打开原 App，观察过渡及不重启，另一后台任务不受影响。不主动诱发验证码或重复有副作用的动作。

验证码点击无响应的原因仍未确定；本次只修复已确认的恢复与提前回收路径，ColorOS 实际动画仍待测。LSP/电源键软重启与全局冷启动白屏未定因，不主动复现。保留两任务、单引擎、同 App 串行、旧 screen_*、通知/流体云/三个完成入口/内联回复、Harmony 和已授权息屏行为。

未改包名、签名或 versionCode；不合并 main、发布/打标签/替换正式下载，不上传 APK/令牌/私钥到仓库，不恢复定时或唤醒 dsh。
