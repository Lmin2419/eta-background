# E06 · 模型提供商（0.9.18）

分支 `harmony/e06-providers`，base `harmony/e05-settings` / PR #23。固定移植源 `3b1794d70881c3c849b53d7778dd2bfdf0a46cc5`。只改 harmony-agent/；不合并 PR，不推送 main。正式壳层已替换模型提供商旧页面；这是代码交付，不代表视觉 1:1 或真机验收完成。

## 逐文件对照表

源目录为 `app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/`，对应文件头保留来源及 Required Notice。

| Eta 源文件 / 元素 | 鸿蒙实现 | 状态 |
| --- | --- | --- |
| ModelProviderListScreen.kt：标题、新建分组、服务列表 | 同名 ArkUI 页；名称/地址搜索、品牌、类型、模型数、默认选择、长按删除确认 | A，截图待测；内置服务预置差异见后文 |
| ModelProviderDetailScreen.kt：新建、配置/模型标签 | 同名页；新建只显示配置，保存后进入同一 ID 的详情；已保存服务显示两页 | A；使用原生按钮承载固定标签 |
| ProviderConnectionFields.kt：名称、URL、API Key、类型 | 同名组件；Chat Completions、Key 显隐、HTTP 明示选择、ASSET 保存选择 | A；Anthropic/Gemini 创建及 Responses 为 B，显示“鸿蒙版尚未接入” |
| ProviderConfigDraft.kt：独立配置草稿 | core/eta/providerConfigDraft.ts 与 ProviderEditor；保存/恢复 DTO、深复制请求头；实际编辑使用兼容旧草稿的控制器 | A；没有把完整 Android ProviderConfig 数据模型宣称为已迁移 |
| ProviderHeadersEditor.kt：展开、自定义头增删改 | 稳定行 ID、密文值输入；按源 CustomHeaderFilter 检查托管头、重复名称和 ASCII | A；持久化存 ASSET，容量差异见后文 |
| ProviderModelsTab.kt：管理分组、搜索、多选、编辑 | 同名页；获取目录、自定义模型、模糊搜索、长按多选、全选可见、批量删除、名称/ID/context 编辑及四个预设 | A；修改先暂存草稿，保存才生效 |
| ProviderModelsTab.kt：推理能力覆盖等元数据 | 当前上下文窗口/视觉标记保留；思考开关显示禁用，完整推理能力覆盖与标签未接 | 临时 B / 未完成，不改变最终对齐要求 |
| RemoteModelImportSheet.kt：远程目录 | 原生 72% 弹层；搜索、全选可见、勾选、确认/取消；排除已添加模型，获取不清空配置 | A；无隐式保存、无隐式切换会话模型 |
| CommunityCatalogScreens.kt：目录/详情/导入 | 同名 ArkUI 页；本地快照、缓存、在线刷新、搜索、多选、导入后进入详情 | A；新服务禁用、空凭据，不抢当前选择 |
| ProviderComponents.kt：页面、区块、图标、输入 | 同名共享组件与 E02 组件；生成资源、同序分组、固定顶栏 | A，原生布局视觉差异待测 |
| ProviderAccountControls.kt | 账号登录入口隐藏 | C，任务卡明确排除 |
| ProviderLoginStore.kt | 不接入 OAuth 登录执行/设备码循环 | C，与账号控件一同排除 |

其他来源：`CommunityCatalogParser.kt`、`CommunityCatalogRepository.kt`、`ProviderSourceRegistry.kt`、`CustomHeaderFilter.kt`；逻辑在 core/eta/，网络/RDB/资源读取通过显式平台端口。

## 数据和交互落实

- **0.9 原有配置**：保持服务 ID、名称、URL、已安全保存的 Key、模型顺序、默认模型。缺失的新字段按兼容默认值读取；没有清空旧库重新创建服务。未勾选安全保存的旧 Key 不会凭空恢复。
- **独立草稿**：测试连接与“自动拉取”使用当前草稿执行 `/models` 查询并打开导入弹层，不保存、不发聊天请求。取消不改变模型列表；确认只添加到草稿。切页、换服务、编辑 URL/凭据/请求头使旧请求失效。
- **新建**：允许先保存名称和地址、暂不添加模型，转入详情后再导入；反复保存更新同一个 ID，不重复创建。
- **请求头**：托管认证头不能覆盖；自定义头与 Key 不进入磁盘草稿、日志、备份。已保存的头使用独立 ASSET 引用；元数据写入失败会删除新建安全记录、保留旧引用。未保存头的值仅保留内存；重启恢复已保存值。
- **模型**：编辑和删除保留其他元数据；保存后，被删除或禁用的模型即使仍被旧会话记住，也不能继续发请求。设置默认提供商不改写已存在会话的绑定。
- **服务开关与提示词**：禁用服务不能发送；服务系统提示词替换默认 system 文本，发送测试验证不会重复附加。历史请求与其他服务不受编辑过程影响。
- **社区目录**：沿用源筛选（安全 HTTPS 基地址、兼容提供商、有效 context、支持工具与文本输出等）；固定快照 173 个服务 / 4,932 个候选模型。缓存有效则优先；坏缓存回退快照，刷新失败保留旧目录；自动更新 24 小时过期、失败尝试间隔 1 小时。导入按用户选择时捕获的快照，不被稍后的刷新覆盖。离开页面后迟到导入不会把用户导航拉回。

## 检查、原测试账本

四项检查通过：`npm run check`；`npm test` **540 通过 / 0 失败 / 0 runner skip**；`npm run check:ets` **0 error / 77 warning**（按既有规则仅忽略 8 个 $$ 已知误报）；`npm run check:res` **1,935 资源 / 0 warning**。**ArkTS 检查通过 / 未生成 HAP**，最终编译以 DevEco 为准。

| 原测试来源 | 完整移植 | 仅平台端口逻辑 | 未移植 / 原因 |
| --- | --- | --- | --- |
| ProviderComponentsTest | 7：草稿、context 校验、2 搜索、3 品牌映射 | 0 | 2 OAuth 能力判断，C 类账号功能排除 |
| CommunityCatalogParserTest | 3：安全 URL、原生厂商端点、候选筛选 | 0 | 0 |
| CommunityCatalogRepositoryTest | 2：选择模型导入、拒绝空/过期选择 | 4：快照、坏缓存、缓存优先、导入不抢当前选择 | 4 项的 Android assets/Room 实测不计完整移植；鸿蒙实际 RDB/资源待测 |
| ProviderLoginStoreTest | 0 | 0 | 9 登录执行测试，随 C 类账号功能排除 |
| 合计 | **12** | **4** | **11 C 类排除；另 4 项平台部分不能计完整** |

另新增 **12 项集成/边界测试**，本次增加 28 个可运行用例。E04a 的三项品牌测试来自 ProviderBrandingTest，与本次 ProviderComponentsTest 的三项不同；不重复计数。不把平台逻辑测试写成真机或 Room/RDB 测试通过。

## 与 Eta 的差异、待用户确认

1. 菜单、弹窗、长按、多选、标签页使用已批准的原生机制；模型和目录长列表用 ArkUI List/LazyForEach，原生弹层键盘避让、宽窄屏尺寸必须截图核对，没有声称像素一致。
2. **模型修改延后保存**：遵守 E06 明确要求继续 0.9 独立草稿规则，底部显示“保存后生效”。“当前模型”标记指草稿内默认模型，不在编辑时替换聊天会话选择；这是相对 Eta 即时模型操作的明确差异。
3. **内置服务预置未补齐**：保留 0.9 用户库，支持手动新建与社区导入；未自动创建 Eta 全部内置提供商，也未实现“恢复内置服务”。品牌映射已按源移植，不能据此宣称内置库迁移已完成。后续需要以稳定 ID、保留自定义覆盖的种子迁移补齐。
4. **模型能力编辑未齐**：context/name/ID 已接；完整推理、reasoning effort、结构化输出等覆盖、能力标签和 source-specific 登录状态仍有差异。思考开关临时 B；不把 A 目标直接降级为最终 B。
5. 原生 ASSET 单条 VALUE 上限 1,024 字节；本版整个自定义请求头 JSON 存为一条，超限明确失败并保留草稿。未静默截断；如需更长配置，应拆分并验证事务恢复。
6. 社区离线资源由固定源 gzip 生成等值候选 JSON（去掉解析器忽略字段），不包含账号/Key。更新为独立公开 GET，不携带服务凭据。本环境没有使用用户 API Key 做真实网络调用，在线接口兼容性待真机验证。
7. 新增的少量源硬编码中文提示已进入生成资源；新增 Harmony 补充提示的 en_US/zh_Hant 仍有中文回退，未声称翻译全部完成。
8. OpenAI Responses **未提前实现**，T09 不变；当前只可执行 Chat Completions。用户的 ChatGPT 6.0 Astra 是否接受这一协议仍待真实端点验证，不能根据名称推断可用。

交付的是提供商页面与主要编辑/导入路径，**E06 完整 1:1 验收仍有上述缺口**。新决定默认保留旧数据和草稿规则；不更改最终验收目标。

## 接口、权限与许可证

公开接口：`resourceManager.getRawFileContent`、`util.TextDecoder`、`http.createHttp/request/destroy`；复用现有 RDB 事务、ASSET、Navigation、CustomDialogController、原生 AlertDialog、List/LazyForEach、窗口尺寸读取。**本 PR 新增权限：无**；继承 E02 已批准的 INTERNET + VIBRATE，不使用 system_basic。

signingConfigs 保持空。Required Notice: Copyright © 2026 蛮吉 (Mangi-11). Eta 代码为 PolyForm Noncommercial 1.0.0。models.dev 快照为 MIT，来源、固定 blob 散列和许可证见 THIRD_PARTY_NOTICES；可用 `npx tsx scripts/port-eta-catalog.ts <固定源 gzip 路径>` 复现并验证候选集完全一致。

## 真机检查点 ④ · 全部待测

1. 从 0.9.1/现有版本覆盖安装此分支：设置 → 模型提供商。核对原名称、URL、模型列表和安全保存的 Key；记录版本 0.9.18。
2. 新建 OpenAI 兼容 → 输入名称/URL/Key → 测试连接。应显示目录弹层，原表单完整；此步骤只验证 `/models`，不证明推理接口可用。
3. 搜索模型 → 勾选两项 → 取消；列表不变。重新获取 → 全选当前筛选/清空/勾选 → 确认；仅草稿出现模型，保存后再进详情仍在。
4. 在弱网中获取目录，立刻返回、换服务或修改地址/请求头；迟到结果不得覆盖新表单。修改地址清空旧凭据，不能把旧 Key 发到新地址。
5. 空模型先保存，进入配置/模型两标签；添加、编辑名称/ID/context、长按多选删除，保存后重启验证。删除当前会话记住的模型后发送，应该要求重新选择且无网络请求。
6. 添加 X-Custom 请求头并保存，重启恢复；非法名/重复名/认证头/超 1KB JSON 应报错且保留草稿。诊断、导出中不得出现头值与 Key；勿把真实秘密贴回报告。
7. 进入社区目录先断网查看快照，联网刷新；搜索服务 → 选择模型 → 导入。新服务禁用且无 Key，当前聊天模型不被抢走；离开页面后迟到导入不会跳回来。
8. 给一个服务填写系统提示词、启用、保存并真实聊一轮；确认 Chat Completions 协议。若用户端点只支持 Responses，记录事实交用户决定是否提前 T09。
9. 与 Eta 提供商列表、配置/模型标签、导入弹层、社区页并排截图；折叠/展开、深浅色、最大字体、键盘弹出各检查一遍。重点核验滚动、选择状态、返回、多选取消和固定顶栏。
