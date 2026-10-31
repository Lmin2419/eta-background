# P1c：Anthropic Messages（0.9.25）

基于 `harmony/e06b-catalog-capabilities`（#30），分支 `harmony/p1c-anthropic`；来源 `app/` @ `3b1794d`。Required Notice: Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0。

## 逐项对照

| Eta 元素 | 鸿蒙实现 | 分类 |
| --- | --- | --- |
| AnthropicMessagesProvider.buildRequestJson | 原消息角色投影、同角色合并、图像 base64、工具 schema 顶层组合子处理、显式输出上限优先 | A（协议） |
| system/tools/conversation cache breakpoints | 原临时缓存标记；观察截图前的工具结果承接缓存；临时标记不外发 | A（协议） |
| Anthropic SSE | 原块顺序、思考/文本/工具事件、签名拼接、重复 stop 去重、message_stop 立即结束、EOF/未闭块/工具签名检查 | A |
| Usage / stop details | 输入+缓存读写合成上下文占用，增量合并保留输入；停止原因按原枚举映射给旧运行时 | A |
| AnthropicEphemeralState / assistantHistoryMessage | 当前轮签名块只在内存请求链保留；更早轮去掉；持久 DTO 不含 opaque 块 | A（逻辑） |
| E06 新建 Anthropic / 连接字段 | 开放类型、目录和测试按钮；显示 anthropic-version；隐藏不适用的 OpenAI 端点切换；强制 SSE | A |
| E05b 跨协议摘要隔离 | 补全 3 项原测试，Chat/Responses/Anthropic 全部移除工具与自定义输入覆盖 | A（验证） |
| 手机工具 / 后台 / 角色 UI / OAuth | 未注册、未启用；纯协议测试不代表设备执行能力 | 维持既有 B/C |

## 原测试与适配验证

- **15 项 AnthropicMessagesProviderTest 全部完整移植 / 跳过 0**：自适应输出预算、签名/加密块原顺序、顶层 schema、并行工具结果、用量、拒绝原因、跨轮/同轮缓存、连接关闭前完成、不可见签名、未闭块/签名缺失/EOF/error、文本与工具事件、思考请求。
- **补全 E05b 的 AgentCompactionProviderTest 3 项 / 跳过 0**：两种目的 × 三种协议，以及 user/assistant/system 三种深度提示位置。角色数据库/世界书仍为外部端口；这里仅移植请求投影所需最小 DTO，未开放角色功能。
- 这 3 项使用原 `AgentToolCatalog.build(false, false)` 完整 **57 工具** JSON。用原 Kotlin/Java 源码在独立 JVM 生成；保留全部 schema/描述，包含原虚拟屏测试数据。源文件哈希、输出 SHA-256 和数量随 fixture 提交。Android capture/未调用的执行分支只做宿主适配，排除分支若被调用会直接抛错，不能悄悄缩短目录。该 fixture 仅由测试读取，不进入应用工具注册。
- 6 项新适配测试：所有 Unicode/CRLF 切分、地址/头/预算、正式运行时保存顺序与重开、停止/EOF、编辑器测试/目录/保存不重置、无流式或无效请求头在发包前失败。

四项检查通过：`npm run check`；`npm test` **781 通过 / 0 失败**；`npm run check:ets` **0 error / 92 warning**（8 已知误报忽略）；`npm run check:res` **1,940 资源通过**。**ArkTS 检查通过 / 未生成 HAP**，DevEco 为最终编译检查。新增权限：无；新增鸿蒙接口：无。

## 与 Eta 的差异 / 待用户确认

- 协议直接复用已校验的 ModelHttp 平台流式端口；保留既有先保存后联网、停止留内容、禁止自动重发规则。停止枚举翻译为现有 ModelReply 的 stop/tool_calls/length/content_filter 拼写。
- 适配旧配置允许填写根地址、`/v1` 或完整 `/v1/messages`；仅在适配层规范化，纯 ProviderUrls 仍照源码追加路径。无非流式/JSON 自动重试回退。
- 新建服务仍使用现有服务草稿：同类型未保存草稿可恢复，不同类型的新建使用对应默认地址。API Key/请求头仍由 ASSET 保存；anthropic-version 为普通非敏感配置。
- 错误分类沿用 P1a：不回显服务端完整正文，避免错误中包含用户消息/密钥；这是已有诊断约束。
- RoleplayRequestProjection 的世界书与存储解析是显式输入，原卡片固定样例的数据完整保留；不声称全角色系统已移植。测试中的工具只验证传输投影，不运行任何 Android 或鸿蒙动作。
- 全部视觉/真实服务效果待用户检查，未生成 HAP。E07b3 仍未切换，旧数据不删除。

## 真机步骤 — 待测

新建 Anthropic → 填地址/个人 Key/版本头 → 测试连接/获取全部分页模型 → 添加/保存 → 选择模型发起聊天 → 检查文本和思考流式变化 → 停止 → 重开查看保存内容。切换已配置服务验证 Key/版本头不串用；错误 Key、目录失败、取消和弱网不清空输入、不自动重发。检查状态栏/折叠横竖屏布局仍依原检查点。
