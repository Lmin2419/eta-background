# E06b：内置服务与模型能力（0.9.24）

基于 `harmony/e05b-thinking-compaction`，分支 `harmony/e06b-catalog-capabilities`。固定来源 `app/` @ `3b1794d`；Required Notice: Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0。

## 逐项对照

| Eta 来源 / 元素 | 鸿蒙实现 | 分类 |
| --- | --- | --- |
| BuiltinProviders：12 个稳定 ID、顺序、地址、默认协议、提示词 | `core/eta/builtinProviders.ts`，正式 Host 首次加载与升级调用合并；品牌/系统替换为随身助手/HarmonyOS | A |
| OfficialModelCatalog：31 个模型、原稳定 ID、窗口、模态、工具/思考/结构化输出元信息、修订增量 | 源码生成 `officialModels.generated.ts`，`officialModelCatalog.ts` 补缺；原 ID 存 catalogId，API 模型 ID 沿用旧存储 id | A |
| ProviderRepository.ensureBuiltInsMerged / resetBuiltIn | 保留原服务、顺序、选中项与 Key 引用；空库种子、缺失内置服务补入、版本增量、内置重置/禁止删除 | A |
| GeminiModelCapabilities | 7 个确证模型的思考档位与托管搜索能力；未知模型不推断支持；与 E05b 同一解析器 | A（纯能力逻辑） |
| ProviderConnectionProfile | 订阅目标精确绑定的纯函数；无认证网络请求 | A（纯逻辑） / C（订阅登录入口） |
| ProviderModelsTab.ModelEditDialog | 原顺序：名称 → Model ID → 窗口/预设 → 思考开关/档位 → 提示 → 恢复自动/删除 → 保存/取消 | A |
| capabilityTags | 有效窗口/未知窗口、支持思考标签；草稿提交后进入实际运行时能力解析 | A |
| Anthropic / Gemini 连接 | 本 PR 只预置与展示；连接按钮/模型选择保持协议门控，待 P1c/P1d | B |

## 升级与存储边界

- 原 0.9 模型配置文档增加独立目录修订号；不切换 E07b3 会话存储。已有自定义服务在前、缺失内置服务按原顺序追加；容量支持原有最多 50 个自定义服务加 12 个预置。
- 更新只追加对应修订的新模型。用户删除的旧预设不在每次启动时重建；OpenAI 已有远端目录或清空的连接不被重新填满。旧 Kotlin catalog reasoning JSON 的条件修补不执行，因为鸿蒙旧版本从未存过该格式。
- 原始 catalog 窗口与有效运行窗口分别保存；与 Eta 一致仅 OpenAI/Gemini 官方预设直接配置有效窗口。旧鸿蒙远端元信息中的 contextWindow 含义保留，避免升级改变已设窗口。
- 合并先写文档，成功后发布状态；失败保留旧文档。重置保留 Key 引用，不读取/重写明文；移除自定义服务仍沿用提交后清理 ASSET 的顺序。未配置密钥的内置模型不加入可发送选择列表。
- API Key/自定义敏感请求头仍走 ASSET；无签名、证书或密钥入库。

## 与 Eta 的差异 / 待用户确认

- 内置服务状态、官方模型元信息适配现有 0.9 文档；没有提前切换 E07b3。官方稳定 ID 另存 catalogId；API 模型 ID 延用现有选择/会话引用，避免破坏升级。
- 模型编辑保存到服务草稿，再由“保存配置”提交，沿用已交付 E06 的一致性规则。取消弹窗不会修改草稿。
- 使用已批准的鸿蒙原生弹窗/Toggle；动态思考档位来自原枚举显示名，静态文案通过资源脚本。31 项模型元数据由生成脚本锁定，不读取实时线上产品目录。
- 这份固定版本的 ProviderModelsTab 只提供窗口与思考能力编辑，未提供独立的结构化输出开关；本次保留其元信息，不另造 UI。社区完整目录能力开关未在原页面出现的也不另加。
- 原服务长按复制菜单、订阅登录、托管搜索 UI、自定义请求头 ASSET 单条长度上限等 E06 已记录差异仍存在；本 PR 不把这些列为完成。内置恢复入口位于配置页，与源码对应。
- 提示词仅做品牌和平台名替换；没有注册未真机验证的工具，也没有启用后台执行。

## 自动验证与真机待测

`npm run check`、`npm test`（757 通过 / 0 失败）、`npm run check:ets`（0 error、92 warning；8 已知误报忽略）、`npm run check:res`（资源编译通过）。**ArkTS 检查通过 / 未生成 HAP**。新增鸿蒙接口：无；新增权限：无。模拟测试不等于截图/真机完成。

原测试 9 项 / 跳过 0：BuiltinProvidersTest 2、OfficialModelCatalogTest 4、GeminiModelCapabilitiesTest 3；全部原断言逻辑与模型名单保留。额外 7 项验证升级幂等、删除保持、失败回滚、密钥/选择保留、容量与能力覆盖、编辑保存、订阅目标绑定。生成器可重复校验纳入资源测试。

设备步骤在 `docs/device-runs/TEMPLATE.md` 的 E06b 段；全为待测。需用个人 OpenAI API Key 做保存 → 测试连接 → 获取目录 → 选择模型 → 聊天，以及取消/重开/删除/重置/升级。不得把自动测试写为真机通过。
