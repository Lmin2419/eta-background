# L2b：流式状态、缓存与渐显策略（0.9.6）

来源：Eta `3b1794d` 的 `ui/markdown/`。Copyright © 2026 蛮吉 (Mangi-11).，PolyForm Noncommercial 1.0.0。基于 L2a（PR #12），仅逻辑，无页面改动。

| Eta 元素 | 鸿蒙实现 | 状态 |
| --- | --- | --- |
| StreamingMarkdownState / consumeStreamingMarkdownTargets | 单消费者最新值队列，连续追加不饿死显示，纠正/恢复流作废旧结果，取消后不发布 | A，逻辑 |
| StreamingMarkdownRestoreState | 布局代际、稳定恢复基线、只允许当前终态完成 | A，逻辑 |
| StreamingMarkdownStateCache | 多挂载者引用计数，活跃消息保留，完成离屏消息交给静态缓存 | A，逻辑 |
| StaticMarkdownDocumentCache | 512 Ki UTF-16 字符预算，最多约 96 项，单文档准入，LRU | A，逻辑 |
| SmoothTextReveal 策略/协调器 | 36 字素/秒基速、0.20 秒追赶目标、帧增量最大 0.05 秒，按源码顺序跨块推进 | A，非绘制逻辑 |
| MarkdownStyle / listMarkerVisible | 段落层级间距、思考语气比例、marker 随正文出现 | A，策略 |
| Compose DrawModifierNode / TextMeasurer / 帧时钟 | E04b 对接 ArkUI | 本 PR 未包含 |

## 测试及未移植范围

39 项完整原测试：StreamingMarkdownParsing 6、RestoreState 5、StateCache 6、StaticCache 4、SmoothTextRevealPolicy 18。另有 5 项 Coordinator 测试的纯逻辑替代；原 Android/Compose 平台部分跳过原因逐项写在 `eta-l2b-tests.json`：

1. detachedEarlierBlockCompletesAndLaterBlockKeepsRevealing：替换 TextMeasurer、帧时钟，验证离屏块完成且后续块仍推进。
2. oneFrameCarriesAdvanceAcrossBlocksInSourceOrder：显式帧增量验证 10/10/5 的跨块推进，不验证实际帧调度。
3. reattachedBlockKeepsCompletedPrefixAndAnimatesOnlyNewText：仅验证重挂载进度及字素数量，不验证布局。
4. layoutWithoutMountedNodeIsImmediatelyReadableOnLaterAttach：布局身份端口替代 Android 排版对象。
5. staleDetachDoesNotCompleteTheReplacementNode：仅验证节点身份及过期 detach，不验证 Modifier 生命周期。

L2 总计 79 项原测试名称均有对照：**74 项完整移植、5 项原平台测试跳过并有纯逻辑替代**。不是“79 项原生测试通过”。L2a 另有 6 项适配测试。

四项检查：`npm run check` 通过；`npm test` 383 通过、0 失败/Node skip；`npm run check:ets` 0 error；`npm run check:res` 1,833 资源通过。**ArkTS 检查通过 / 未生成 HAP**。纯逻辑尚未导入生产页面；最终 JS/ArkUI 打包与显示须以 DevEco 为准，无新增鸿蒙接口或权限。

## 与 Eta 的差异和待用户确认

- JVM BreakIterator 改用 vendored `unicode-segmenter@0.14.0` 的 UAX #29 实现，纯 JS/MIT，无原生依赖、无全局 polyfill。Unicode 数据版本可能和 Android ICU 不同；原 emoji/组合字符测试通过。源码、两个 MIT 许可证、版本及 SHA-256 在 `core/eta/markdown/vendor/unicode-segmenter/`，应用 rawfile 也带许可证。
- 选项：依赖未在 SDK 类型中找到的 Intl.Segmenter / 固定纯 JS 分割器。默认采用后者，不用 UTF-16 单字符假装字素；未运行完整 HAP 构建，不将此选择写为已验证可打包。
- Kotlin Channel/协程改为 Promise 消费器及取消令牌。解析函数可以注入后台执行端口；目前默认在调用线程解析，E04b 必须对接调度再评估长回复性能。
- 帧时钟、布局与 draw invalidation 隔离为端口。当前不是逐字显示已接入，更不代表已验证绘制不闪烁。

## 真机检查步骤（待测）

L2 无独立真机检查点；纳入 E04b 检查点②：长回复含中英文、家庭 emoji、旗帜和组合音标；生成中滚出/滚回消息、切后台再回来；确认已显示文字不倒退重播、列表 marker 与正文同步、停止不再出现迟到输出。连续切换至少 100 个历史消息检查缓存回收。所有项目待测。
