# 原报告中会影响实现的修正

1. **HDC 不等于外接电脑主控。** HDC 主机端可以编译进鸿蒙应用，通过系统授权连接本机无线调试服务。能否在特定手机系统成功连接仍须真机确认。轻启说明了这条产品路线，但不是本助手已完成实测的证据。
2. **Eta 的非 Root GUI 路径不能直接概括成“Shizuku + 无障碍”。** 本次阅读的 Eta 版本使用 Android AccessibilityService 实现重要的非 Root 观察/输入能力。页面事件主要来自无障碍回调，不是 Shizuku 本身凭空提供完整的跨应用事件订阅。
3. `hdc uia dump`、`hdc input tap`、`hdc snapshot`、`dumpsys window` 不是可以直接拿来开发的统一鸿蒙命令集合。本版采用 `hdc shell uitest dumpLayout -p ...`、`uitest uiInput ...`、`uitest screenCap -p ...`、`aa start -b ... -a ...`。具体可用性按系统版本探测。
4. UITest 页面树是 JSON，不是报告所说的通用 XML；可访问性、WebView/自绘内容和受保护页面的完整度不能保证。
5. Shizuku 非 Root 不等于能读取任意应用私有数据；Android 普通应用沙箱也不是报告暗示的全面开放。事件、剪贴板、后台限制要按系统和实际 API 分别分析。
6. “锁屏必断”“重启后怎样”“星盾不定期断开”不能在无设备日志时当作所有版本的既定规律。应用进程、本机 HDC、无线调试开关和已激活设备端服务是不同生命周期。
7. 双 2080 Ti 是否适合具体模型，取决于模型版本、权重大小、量化和显存，不能由“本地模型”四个字保证低延迟。本版支持用户配置 API，不绑定报告中的模型名称。

参考：

- [Eta](https://github.com/Mangi-11/Eta)，审阅提交 `3019c43ce60cf53ebe1c2ba5ec86ec630830f39d`。
- [OpenHarmony HDC](https://github.com/openharmony/developtools_hdc/tree/10e867eb30f43b605089458be20fd85e943a327d)。
- [hap-installer](https://github.com/LMV475/hap-installer)，提供应用内 HDC 实现参考。
- [quietstart](https://github.com/thx853068675-dev/quietstart)，公开文档和发行说明；完整执行实现未公开在本次取得的材料中。
- 另有 [gujiwuqing/quietstart](https://github.com/gujiwuqing/quietstart) 的 MIT 源码工程，包含内置 UiTest 工作模块路线；可供后续设备端 worker 研究，本版未复用其代码。

这些修正决定了本版采用“手机上的 Agent + 手机内嵌 HDC”的结构，同时把常驻运行与关调试持续运行作为需要另行验证的工作。
