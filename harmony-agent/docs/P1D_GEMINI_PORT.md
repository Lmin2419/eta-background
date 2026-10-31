# P1d：Gemini Interactions（0.9.26）

基于 `harmony/p1c-anthropic`（#31），分支 `harmony/p1d-gemini`。来源 `app/` @ `3b1794d`。Required Notice: Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0。

## 逐项对照

| Eta 元素 | 鸿蒙实现 | 分类 |
| --- | --- | --- |
| GeminiInteractionsRequest | 原生 `/interactions`、x-goog-api-key、store=false、protected fields、思考档位、generation_config | A（协议） |
| GeminiInteractionHistory / EphemeralState | 当轮 raw steps / 签名 / 图片与工具结果；旧无签名历史明确转文本；临时字段不持久化 | A（逻辑） |
| GeminiToolSchema | 组合子 required 补本地声明，保留属性约束/资源作用域/引用，深复制不改原目录 | A |
| GeminiInteractionsStream | 分步文本/思考/参数事件、签名覆盖、累计用量合并、终态检查、取消、异常脱敏 | A（协议） |
| GeminiCitationFormatter | UTF-8 偏移映射 Unicode 边界；无效偏移降级来源列表 | A |
| SearchSuggestions | 原事件/容量/跨工具轮延后逻辑与测试；正式页面没有建议 HTML 展示端，未开启托管搜索 | B（展示） |
| GeminiModelCatalog | 原生分页 1000、跨页 4 MiB、重复令牌/格式错误整次失败、去重、已知能力补全 | A |
| E06 新建 Gemini / 选择 / 测试 / 目录 | 开放原生类型，固定 SSE；显示 Eta 原说明，独立草稿不重置 | A |
| 手机/浏览器/后台工具、OAuth | 未接入、未注册；测试工具不能作为真机能力依据 | 保持既有 B/C |

## 测试与验证

- 任务卡 **GeminiInteractionsTest 21/21 完整移植，跳过 0**。
- 直接依赖额外 **21**：GeminiToolSchemaTest 9、GeminiModelFailureTest 7、GeminiModelCatalogHttpTest 5。原长 HTML / 两页各 2,200,000 字符目录 fixture 保持完整。目录取消用真实 HTTP + AbortController 替代 JVM 协程；无 OAuth 路径可被调用。
- 新增鸿蒙边界测试 **8**：Unicode/CRLF 各切分点、完整地址/预算、保存后发送/重开不重发、停止/EOF、草稿/导入失败、临时签名与建议不持久化、强制思考规范化、无流式/非法 Key。
- 合计本 PR 新增 **50**，其中原测试 **42**。全部 `npm test` **831 通过 / 0 失败 / 0 skipped**。
- 额外盘点但未移植的整组集成测试 **10**：GeminiRuntimeIntegrationTest 5（依赖尚未接回的完整 AgentModelClient 工具调度、消息投影、搜索展示），GeminiProviderConfigurationTest 5（Android Room/SettingsDataStore、托管搜索配置、schema v4 全量备份）。不以近似的宿主测试冒充这 10 项通过；已有配置和聊天边界由上述 8 项测试验证。完整工具运行与备份留对应任务。
- `npm run check` 通过；`npm run check:ets` **0 error / 92 warning**（忽略 8 已知 $$ 误报）；`npm run check:res` **1,941 资源通过**。
- **ArkTS 检查通过 / 未生成 HAP**。新增权限：无；新增鸿蒙接口：无（沿用 NetworkKit，GET 容量上限调整到 4 MiB）。真机、真实 API 服务均待测。

## 与 Eta 的差异 / 待用户确认

1. 旧配置允许原生根地址或完整 `/interactions`；边界层规范化地址，纯 ProviderUrls 保持 Eta 追加路径。不会切回旧 generateContent，也不自动重发。
2. 正式界面尚无搜索建议 HTML 展示，故 supportsSearchSuggestions 不开启；纯协议测试的托管搜索不代表功能已开放。显式当前轮标记和 signed steps 仅在请求内存流转。
3. 正式入口仍是 0.9 的文本聊天/存储；全 AgentModelClient 工具调度不在 P1d。协议具备工具序列能力不等于设备执行能力。所有原数据保留，E07b3 未做。
4. 沿用既有 NetworkKit 流式 **2,000,000 字节**上限；纯 Gemini parser 按原码 16 Mi 字符流/2 Mi 字符事件限额，平台限制更严格。HTTP 平台错误当前仍显示固定状态说明，纯协议错误分类测试不等于真机能显示全部服务商细分字段。
5. 原生 Gemini 对强制思考模型和未知手动模型把全局关闭偏好规范化为 Default（照 Eta 能力 normalize），不会发送不受支持的 off；显式协议层 off 仍拒绝。其余协议的现有偏好逻辑未变。
6. 页面继续用 ArkUI/系统弹窗，截图与 DevEco 编译结论待用户；不新增 UI 设计。

## 真机步骤 — 待测

新建 Gemini → 填 API Key/原生地址 → 测试连接 → 获取目录/翻页 → 选择并添加/保存 → 选择模型聊天，观察首段文字在回复完成前出现 → 停止，重开仍有已收文字且不再发包。重复目录失败/切换服务/修改地址，核对未保存草稿和旧模型不被清空。用错误 Key、弱网、取消验证错误提示不含 Key/正文且无自动重试。切换全局思考开关和模型档位，核对强制模型不会请求 off。不得把测试工具调用结果写为手机操作通过。
