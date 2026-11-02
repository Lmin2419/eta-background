# 6883e75：预览与接管返修候选

记录时间：2026-11-02T07:02:59Z。状态：**源码、本地回归与两项远端 CI 通过；实际原签名 APK 已独立核验，真机未执行。**

| 项目 | 核验值 |
| --- | --- |
| PR | [45](https://github.com/Lmin2419/eta-background/pull/45)，Draft/open，未合并 |
| head | 6883e75487177f40ea403536cb9f9fec261ce4e9 |
| parent | 3c74066e58ccff2409b305ff3527ab454a3c238b |
| 引擎 | 20261102-e42-manual |
| APK | [Etax-v3.3.5+etax.6883e75.apk](https://github.com/Lmin2419/eta-background/actions/runs/41400925568/artifacts/13122322383) |
| 工件 ID / 大小 | 13122322383 / 6,736,363 bytes |
| SHA-256 | 2b26a631ba05951f5e97c91d2ffbc87c7de310f2ee194b2eb6dba5719892a63f |
| 包名 / 版本 | io.github.mangi.etax / 3.3.5+etax.6883e75 |
| versionCode | 2026103003 |
| 签名证书 | CN=ETAX Self Signed, O=Personal, C=CN |
| 证书 SHA-256 | dc309f48b192f58aef3c750841728e284ad05db2a07a607b2e1fafbcc4c1a2a5 |
| 工件到期 | 2026-12-02T07:00:40Z；GitHub 工件下载需登录 |

## 已执行的验证

[PR Check](https://github.com/Lmin2419/eta-background/actions/runs/41400930694) 与 [Etax Build](https://github.com/Lmin2419/eta-background/actions/runs/41400925568) 均成功；签名构建日志记录全量 2,183 项（0 失败、0 错误、1 跳过），Lint 0 错误、388 Warning、1 Hint；12 项接线、8 项部署回归、Debug 与 Release 构建通过。已下载实际 APK，独立核验包名、版本、原证书、SHA-256 与五项内嵌引擎/脚本完全相符。真机仍待用户验证。

签名构建日志的 Regression results、Lint results、证书和 Signed APK engine pair 均已实际读取。工件 API 的 head、大小、digest 与实际下载 APK 核对；aapt2 / apksigner 独立核对包身份和原证书。vdrive.dex、vdrive.version、vd.sh、install_vdrive.sh、check_pair.sh 与源码/生成产物逐字节一致；没有 engine.token。

本地完整回归与静态检查结论和 CI 相同。修复前新增两项回归确实失败，修复后完整通过；不将最初误读的旧 Lint 文件作为本候选证据。

## 修复及复现

先在原实现上复现完成页面 needs_observation 无法接管、只读窗口错误释放其他拥有者采集两项失败。根引擎改为分别判断操作是否排空和 AI 是否须观察；人工每次动作还须新预览，AI 旧观察仍无效。连续三次目标屏触摸、旋转前拒绝输入、节点误接受点击不替代真实像素、实际 ImageView 矩阵/留白均有回归。

通知使用清晰的手机图标；小窗首帧前和纯黑白无内容帧使用不可操作的手机占位。接管自动放大并保留独立缩放按钮；本地点击波纹/滑动轨迹参考上游 VirtualTouchFeedbackOverlay，有限缓存及淡出，不写入模型图像。完成页的接管、结束接管、打开 App 和错误原因区分明确。

最近任务已将目标 App 移回主屏时，旧 Surface 保留至目标窗口的两次就绪确认后再释放，不移动第二次或重启 App。ColorOS 动画仍须手机确认。

## 实测边界与手机步骤

用户报告来自上一候选交付后的体验，但没有独立读取安装包身份，不把这些体验或旧回归写为本候选通过。当前修复没有远程安装/操作手机。

覆盖安装核对版本后，从 ETA 图标启动：检查通知图标与首帧占位；后台计算器接管后连续点三个以上数字/运算符；测试接管时放大/缩小、点击波纹及可滑动页轨迹；完成页面继续操作与打开 App；另一个后台任务应继续。不要求 USB，不通过长按电源键复现已知软重启。

保留双任务、单 VDrive、同 App 串行、旧 screen_*、每任务通知/流体云/三个完成按钮/内联回复、已授权息屏后台及主屏锁定保护、Harmony。LSP/Vector 软重启及全局冷启动白屏未定因；本候选不宣称修复。未合并、发布、打标签或替换正式下载。
