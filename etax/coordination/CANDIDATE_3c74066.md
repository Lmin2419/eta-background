# 3c74066 候选包与验证证据

记录时间：2026-11-01T18:34:07Z。本页属于协作分支，只记录对应 SHA 的事实，不作为发布公告或真机验收通过证明。

## 代码与范围

- 仓库：Lmin2419/eta-background
- PR：[45](https://github.com/Lmin2419/eta-background/pull/45)，Draft/open、未合并
- 业务分支：etax-340-step1-foundation
- head：3c74066e58ccff2409b305ff3527ab454a3c238b
- parent：2ada1360dd56a72de46cd57dc627e149c37efd9a
- 变更：39 文件，+1064/−69
- 新引擎：20261101-e41-preview
- 内容：统一 Runtime 状态；通知入口小预览/放大；每任务人工控制排空及恢复；息屏后台继续、锁屏前台保护
- 源码说明：[TASK_PREVIEW.md](https://github.com/Lmin2419/eta-background/blob/3c74066e58ccff2409b305ff3527ab454a3c238b/etax/TASK_PREVIEW.md)

## CI 与本地检查

本地全量单测 2,175 项（0 失败、0 错误、1 跳过）、Lint（0 错误、389 Warning、1 Hint）、Debug 构建、12 项接线和 8 项部署测试通过；最终旋转补充另作定向验证通过。远端 PR Check 41335810776 与 Etax Build 41335805841 正在执行，尚未将云端结果填为成功，签名候选尚待生成。

| 检查 | 证据 |
| --- | --- |
| PR Check | [41335810776](https://github.com/Lmin2419/eta-background/actions/runs/41335810776)，对应上述 head |
| Etax Build | [41335805841](https://github.com/Lmin2419/eta-background/actions/runs/41335805841)，对应上述 head |
| 本地全量单测 | 2,175 项，0 失败、0 错误、1 跳过；6 行旋转重排补充后另跑 TaskPreviewActivityTest 通过，最终完整 SHA 由 CI 再验证 |
| 本地 Lint | 0 错误，389 Warning、1 Hint；包含现有告警及新预览触摸/文字告警，不宣称零告警 |
| 本地 Debug 构建 | 成功；未将本地调试签名包作为用户候选 |
| 接线 | 12/12 通过 |
| 部署回归 | 8/8，真实 mksh/BusyBox/Toybox 环境通过 |
| 引擎编译 | e41 编译成功，内嵌版本与源码对应 |
| 代码审查 | 模型与预览帧分离、按 run 绑定、操作排空、旧几何失效、关闭资源释放、终态竞争、权限/通知兼容均已检查 |

## 安装包状态

签名工件尚未生成。请勿把此前 2ada136 或本地 Debug 包当作本轮四项功能候选；完成后在本页补充实际工件、大小、SHA-256、包名/版本和签名证书。当前构建：[Etax Build](https://github.com/Lmin2419/eta-background/actions/runs/41335805841)。

## 证据限制与设备测试

没有连接用户手机，没有远程安装或操作设备。本候选真机状态为未执行。用户已报告安装的 4637961 及其 U001–U003 正常体验不能计入 3c74066。历史测试设备为 OPPO Find X9 Ultra、Android 17 / ColorOS 17，未在本轮独立读取设备信息。

小窗通过通知打开临时 App 面板；不能据单元测试断言 ColorOS 把实时画面嵌入流体云或按指定位置展示手机图标。特别需要手机验证：主屏键盘焦点、只看不暂停、双任务中只接管一个、横竖屏坐标、结束页面不重启、息屏联网与实际工具执行。新包从 ETA 图标进入普通流程，不主动触发已知电源键/LSP 软重启路径。

本轮没有修复已知软重启的根因，也未唯一确认长任务后全局冷启动白屏原因。部分完成/等待用户的结构化结果分类仍待后续。没有启用新系统 Hook、强停第三方 App、网络白名单或常亮主屏保活。

未合并 main、未发布、未打标签、未替换正式下载链接；包名、签名流程和 versionCode 保持原规则。工件过期后需按对应 head 重新构建并重核实际哈希，不能继续把旧记录冒充新产物。
