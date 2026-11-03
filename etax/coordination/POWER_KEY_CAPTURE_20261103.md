# 长按电源键：单次被动采集

这是诊断附件，不是修复包，不修改业务源码，不恢复已取消的定时协作。

## 当前结论

旧记录没有证实 Vector 守护进程先于 system_server 失联；也没有确认对应长按电源键。不能把该记录作为长按复现成功的样本。

Vector 3116 对应提交 `569048119f0c1071b3d5069b645cfd4526b7ee8e` 的 [BridgeService.kt](https://github.com/JingMatrix/Vector/blob/569048119f0c1071b3d5069b645cfd4526b7ee8e/zygisk/src/main/kotlin/org/matrix/vector/service/BridgeService.kt#L93) 在调用 `service?.attachProcess` **之前**输出 `GET_BINDER`。这只能证明进入桥接入口，不能证明 daemon 成功响应。

跨进程接收器的归属/失效清理仍是源码上的可疑机制，不能宣称为本机根因。不要为此替换 `mOnewayThread`、吞掉所有广播异常，或修改系统 Hook 范围。

## 给手机端 Agent 的操作

1. 读取同目录 [capture-power-key.sh](capture-power-key.sh)，保存到 `/sdcard/Download/capture-power-key.sh`。只准备文件不会恢复 Root 或激活 Vector。
2. 脚本 SHA-256 应为 `f3e58dd9a50877b84fb23cfb81dd3b03365026f9afa3b44561fbc27e316227c1`。先执行 `/system/bin/sh -n /sdcard/Download/capture-power-key.sh`。若语法、哈希或读取失败，停止，不自行替换成未经评审的脚本。
3. **只有执行终端本来就具有 Root 时才采集**。无权限就报告 `BLOCKED_ROOT`，只完成准备，不自动提权、重新注入或激活。不要为这次诊断重启手机。
4. 用能保持运行的有限时终端任务启动：

   ```sh
   /system/bin/sh /sdcard/Download/capture-power-key.sh 180
   ```

5. 只有真的看到 `CAPTURE_READY` 才告知用户采集已就绪。由用户决定是否在这一次窗口内手动激活、启用模块并长按；Agent 不模拟这些操作，也不循环重试。记录用户操作的时间和结果，分清“只激活”“已启用模块”“实际长按”。未进行这些操作，就记为没有复现。
6. 若出现自然崩溃，不重新激活或再次测试。脚本尝试在发现系统进程更换时停止；正常情况下窗口结束也会停止。**采集不会防止自然崩溃或临时 Root 丢失，也不能保证系统故障时日志完整。**
7. 仅分析本次新目录，回答：是否命中 ETA 长按路径、最后完成到哪个步骤、首个异常在什么进程、与 Vector 失联的先后关系。`GET_BINDER` 入口、日志停止和进程消失均不能单独认定根因。
8. 原始系统/框架日志可能含隐私，只留在手机；只分享脱敏的关键异常、堆栈、时间线和明确缺失项，不上传完整日志到 GitHub。不要重复检查与本次问题无关的任务、虚拟屏、网络等模块。

## 脚本边界及验证

- 默认采集窗口 180 秒，允许 5–300 秒；不清除或扩大系统日志缓冲，不调用 `dumpsys` 或应用 Provider，不修改系统配置。
- 连续采集 main/system/crash/events；系统日志最多约 4 MiB 轮转。错误文件另有限额，已写入的旧采集目录不会被覆盖。
- 读取已知 Vector 日志目录，保留最多两个文件各 128 KiB 的开始、周期、结束快照；周期约 10 秒。无法读取会明确记录，不把无日志当作无故障。
- 记录 system_server/vectord 的 PID 与启动时间变化；轮询及快照存在采样间隔，同一间隔内变化不能确定谁先发生。
- 只结束本脚本创建、且 PID/启动时间仍匹配的 logcat 采集子进程。不会结束 system_server、zygote、vectord、第三方 App 或其他采集任务。
- 本地 bash/dash 语法检查通过；两种 shell 下共 14 个模拟生命周期检查通过，覆盖无 Root、超大时长、已有证据目录、采集启动失败、中途退出、系统进程变化及正常结束。**未在 Android mksh/Toybox 或 ColorOS 真机验证。**
- 当前业务 head 保持 `6016745315267d1bb9525039757353b605527ab3`；未构建或发布新的 APK，未宣称软重启已修复。
