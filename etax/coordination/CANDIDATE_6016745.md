# 常用任务原生接入候选 6016745

更新：2026-11-02T19:57:20Z。**本 head 的源码、云端全量检查、Debug/Release 和原签名 APK 独立核验已完成；手机实测未执行。此包是 Draft PR 测试候选，尚未发布。**

## 源码

- PR [#45](https://github.com/Lmin2419/eta-background/pull/45)，业务分支 etax-340-step1-foundation，Draft/open、未合并。
- 最终 head：`6016745315267d1bb9525039757353b605527ab3`；tree：`1a13cfc0cb7b2269644aa377b06db7f9e155e052`。
- 功能提交 a2a5c476893c302d9accb7748302f144d5652fcb，最后补 SDK 拒绝快捷更新的真实返回值；相对 e2acd6f，29 文件 +607/−90。
- 沿用 `20261102-e46-transition`，包含 e46 前台截图失效/布局预检、后台窗口暂缺错误区分、完成页面交还保护；本轮未改 Root 引擎、部署和系统 Hook。
- [原生接入与输入量测量](https://github.com/Lmin2419/eta-background/blob/6016745315267d1bb9525039757353b605527ab3/etax/COMMON_TASK_AGENT.md)、[源码更新日志](https://github.com/Lmin2419/eta-background/blob/6016745315267d1bb9525039757353b605527ab3/etax/README.md)、[坐标和交还](https://github.com/Lmin2419/eta-background/blob/6016745315267d1bb9525039757353b605527ab3/etax/COORDINATES_AND_HANDOFF.md)。

## 实际验证

| 项目 | 当前证据 |
| --- | --- |
| 本地功能提交 a2a5c4 全量 | 2278 项、0 失败/错误、1 跳过 |
| 本地 a2a5c4 Lint / Debug | 成功；0 错误、393 Warning、1 Hint |
| 最终 6016745 定向 / Debug | 22 项、0 失败/错误/跳过，Debug 成功；包含快捷方式拒绝更新的补充断言 |
| 接线 / 部署 | 本 head 两项 CI 日志：12/12 接线、8/8 部署回归通过 |
| 最终 PR Check | [41510965270](https://github.com/Lmin2419/eta-background/actions/runs/41510965270)，成功；实际读取 job 125502868641 日志 |
| 最终 Etax Build | [41510955867](https://github.com/Lmin2419/eta-background/actions/runs/41510955867)，成功；实际读取 job 125502876103 日志 |
| 最终 head 全量 / Lint / Debug / Release | 2278 项、0 失败/错误、1 跳过；Lint 0 错误、393 Warning、1 Hint；Debug 和 Release 成功 |
| 最终 APK 独立核验 | 已实际下载；digest、包身份、原证书及 5 项内嵌资产一致，无 engine.token |
| 手机 | 未执行；没有远程安装或操作手机 |

a2a5c4 的 CI 41510091145/41510078760 被同分支并发规则取消，已实际读取取消状态；不算最终 head 检查失败，也不冒充通过。最终候选无重复触发。

固定输入样本已经计入新 common_task 工具：全功能组内置系统文字 8710→6707 字符；系统消息和基础工具 JSON 83128→77169 UTF-8 字节（减少 7.2%）。无历史、记忆、图片、自定义提示和运行时追加工具；不是 token、首字时间或缓存命中率测量。64 个长流程不改变前缀，已有预算回归。

## 原签名候选包

- [Etax-v3.3.5+etax.6016745.apk](https://github.com/Lmin2419/eta-background/actions/runs/41510955867/artifacts/13174936352)；artifact 13174936352，所属 workflow head 与本候选完全相同。
- 大小：6782783 bytes；SHA-256：`0b3c243a5122893e29f237e3132af62d02962bd0584d83dde4e773da1ffb8a15`，实际文件与 Actions artifact digest 一致。
- 包名：`io.github.mangi.etax`；版本：`3.3.5+etax.6016745`；versionCode：`2026103003`。
- apksigner 验证通过，证书 SHA-256：`dc309f48b192f58aef3c750841728e284ad05db2a07a607b2e1fafbcc4c1a2a5`，与既有测试包原签名一致。
- ZIP 内 vdrive.dex、vdrive.version 及 vd.sh/install_vdrive.sh/check_pair.sh 与当前源码/生成资产逐字节相符；引擎 `20261102-e46-transition`；未包含 engine.token。
- Actions 工件到期：2026-12-02T19:55:00Z。不创建 Release、不替换正式下载，也不把 APK 二进制放入仓库。

## 手机验证

1. 新会话询问常用任务能力，应知道是内置功能；无需插件、Root 或终端来管理流程。
2. 让它保存“计算测试：后台计算 123×45”，明确现在只保存；列表应出现，计算器不启动。
3. 在对话里改成 200＋300，列表同步；要求运行时才执行并得出 500。桌面快捷入口读取最新版。
4. 删除条目后旧快捷方式不能再执行；同名不同流程不得自动覆盖。
5. 原 e46：操作后旧图不能继续输入，后台窗口暂不可读不误报显示丢失；任务完成后从最近任务拉回 App 观察闪烁。APP_TAKEN_OVER 保护保留，不要求人为破坏控制权或触发软重启。

单 VDrive、两任务/同 App 串行、旧 screen_*、流体云/每任务通知/三个入口/通知内继续、预览和接管、既有息屏后台/锁屏保护、Harmony 保留。没有合并、发布、标签、正式下载更换、包名/签名更换或 APK 二进制入库。每小时自动化继续停用，不唤醒 dsh。ColorOS 闪烁、实际模型等待与历史 LSP 软重启仍不在自动化已修复结论中。
