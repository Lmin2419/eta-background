# e47：系统连接失效后的引擎回收

配套引擎：`20261103-e47-recovery`。本轮为未发布候选，不改变正式下载、包名、签名和 versionCode。

## 问题与证据边界

用户提交的荣耀 / KernelSU 测试报告记录：e46 返回 `SYSTEM_RESTARTED`，恢复多次遇到 `PORT_BUSY`；手动结束旧引擎后，设置 App 的后台启动、节点和点击恢复。Root 已授权，但 `screen_state.fix` 仍笼统建议检查 Root。未取得该设备原始日志，不能确定最初哪个系统服务失联，也不能将其等同于 OPPO 的电源键故障。

源码核对发现：

1. 系统服务 Binder 失效仅设置 `systemInvalid`，没有关闭监听或退出常驻引擎。这样的进程仍活着，并非已经退出的僵尸进程。
2. 停止脚本先按 `/proc/PID/comm` 筛选，可能漏掉主线程改名后的引擎；旧版 argv 被重写时只检查 IPv4 的一个 socket inode，遗漏 IPv6 映射和多监听情形。
3. `screen_state` 没有使用已有部署错误分类，连接失败统一显示 Root 排查提示。

## 本轮修改

- 引擎失去系统服务连接时立即拒绝新工作、关闭监听；给已有连接短暂时间返回错误，1.5 秒后只终止本引擎进程。退出不等待可能卡住的 Binder 清理或 JVM shutdown hook。没有任务时同样生效。
- 常规认证 `quit` 复用有界退出；退出期间已有连接也不能提交新任务。此路径不重启 Android、不操作 zygote/system_server、不复活旧会话。
- 停止脚本以 Root UID、真实 argv 和必要的 socket 所有权识别 ETA，引擎线程名不再是门槛。兼容 tcp/tcp6 和多个 inode；带有引擎字样的普通 shell 命令不算引擎。
- 发信号前复查 PID 的启动时间及身份，防止 PID 复用或进程已执行其他程序时误杀。无法归属的端口保持 `PORT_BUSY`；保留事务锁、实际端口释放检查、认证、配对验证、回滚和原有重试退避。
- `screen_state` 保留原 `engine.code`，补充 `recovery.code/inProgress/retryAfterMs/lastDeploymentCode`；服务失联、端口占用、认证、版本配对和 Root 获取失败各有提示。仅断连不再断言 Root 未授予；su 启动后通道中断单独报 `SU_IO_FAILED`。

`recovery` 是当前 App 进程的部署诊断快照；不是完整历史或系统故障归因。已有 `engine-journal.jsonl` 继续记录跨进程历史，本轮不增加日志数据库、定时服务或持续采样。

## 验证与边界

- 脚本回归使用真实 mksh、BusyBox ash 和 Toybox，并以真实 socket 验证端口释放与未知占用保护；procfs fixture 覆盖改名、tcp6、PID 复用及身份变化。
- 独立 JVM 子进程验证阻塞的退出钩子不能阻止有界退出；App 测试覆盖空会话失联、已有连接拒绝新工作、诊断错误分类。
- 具体测试数量、构建与签名包以对应源码 head 的构建证据为准。自动化不等于荣耀/ColorOS 真机回归通过。

手机验收无需重启、重新注入模块或主动制造系统服务崩溃：覆盖安装候选后，从 ETA 内请求后台打开设置，确认返回的引擎 build 为 e47、`healthy=true`，再读取页面并点击 WLAN。已有异常若自然出现，保留 `engine/recovery/fix` 的原文和设置导出的脱敏诊断；不要自行结束不明端口占用进程，也不要重复提交结果不确定的点击。

## 独立未解决事项

用户在 OPPO 上反馈 d870d11 的 Root 原生助手配置完成后，长按电源键仍打开小布。这已是失败反馈，不再记为尚无实测。设置默认助手无法证明厂商按键分发会采用它；本轮 e47 不宣称修复此问题，也不重新启用 Vector/LSPosed、改写系统组件或要求重启。详见 [原生助手说明](NATIVE_ASSISTANT.md)。
