# E07b2：RDB 适配与旧快照迁移（0.9.20）

基于 `harmony/e07b1-store-ports`，分支 `harmony/e07b2-rdb-migration`。按第三轮决定仅交付代码和测试，**FoundationHost 未切换、未初始化新库、未执行迁移**。E07b3 必须等待用户真机检查点 ①–④ 通过。

## 逐项对照

| 来源 / 任务要求 | 鸿蒙实现 | 状态 |
| --- | --- | --- |
| Eta `ConversationDao`、entities、chunk DAO @ `3b1794d` | 同一纯 TS DAO 的参数化 SQL 实现；消息分页、元数据投影、256 字符预览、检查点和分块查询 | A 代码；RDB 待测 |
| Room 事务、Upsert、外键 | 独占连接 + 串行事务、`ON CONFLICT DO UPDATE`、外键与分块清理触发器；禁止 REPLACE 误删父行 | A 代码；RDB 待测 |
| E07b1 提交成功后推进基线 | SQL 提交失败回滚，Persistence 不推进基线；失败后重试不丢消息 | A 自动测试 |
| 原 0.9 `assistant.db` / JSON 快照 | 显式只读源；RDB 保留 meta 原文和每行 payload 原文，文件源保留完整原文 | A 代码；不接正式入口 |
| 无损候选迁移 | 独立 `eta_conversations_v1.db`，同一事务写备份、消息、检查点、选中项及最后的版本标记 | A 自动测试 |
| 会话数 / 消息数 / 哈希 / 选择校验 | SHA-256 校验正文和附件 JSON；同时核对原文、全部行字段、时间、标题、模型绑定、检查点 | A 自动测试 |
| 幂等 / 损坏 / 失败 | 原快照变动、目标非空、正文被篡改、分块缺失均阻止成功；回滚后独立记录固定诊断码 | A 自动测试 |
| 生产切换 | E07b3；这一轮不做 | B，现有页面与存储继续使用 |

## 数据保留与迁移边界

- 源接口只有 `read()`。新适配不调用旧 Repository.load、旧 `DatabaseConversationStorage.read` 或 write，因此不会触发旧版本的隐式迁移或运行状态改写。旧表、旧 JSON 不执行删除或覆盖。
- 新库 `migration_records` 的 `harmony-0.9-to-eta-v1` 标记独立于旧库；`:source` 保存快照原文，`:evidence` 保存 RDB 原始 meta / payload，`:conversation:<id>` 保留每个旧会话的全部字段。大字符串使用已有 16,384 UTF-16 单元分块，不采用旧解析器的截断或选择回退。
- 会话 ID、时间、标题保持原值。新消息 ID 是 `[旧会话ID,旧轮次ID,消息角色或步骤ID]` 的 JSON 字符串，稳定且消除不同会话局部轮次 ID 冲突；原轮次 ID 在保留记录中不变。
- 模型 ID 由显式解析端口提供；原 `profileId` / `preferredModel` 原样保留。不加载 ASSET，不修改模型配置，不自行猜测缺失模型。已有标记的模型映射改变时阻止再次认定成功，留给切换阶段明确处理。
- 旧 running 轮次在新消息投影中是 Interrupted，running 步骤是 Unknown；原始状态在备份中不变。没有创建可执行队列、运行任务、工具或通知，迁移不会自动续跑。
- 校验成功仅说明这一份候选快照完整，**不表示可以切换**。旧正式存储仍可能继续变化；E07b3 必须先取得旧存储静默期，在该边界重新核对源哈希、模型映射和候选库，决定是否重新生成候选。当前实现遇到不同源哈希直接拒绝，不覆盖已有候选。
- 诊断表只记录固定错误码和时间，不记录 SQL、API Key、消息、异常原文。目标库不可写时无法持久化诊断，但仍向调用者返回固定错误码，旧数据不受影响。

## 与 Eta 的差异 / 待用户确认

1. Room 换为鸿蒙 RDB；SQLite 在桌面验证 SQL 和事务，不代表手机 RDB 已验证。新库 camelCase 列名匹配 TS DTO，语义对齐源 DAO，未复制 Android Room 的 schema version 历史。
2. 0.9 → Eta 是本项目专用迁移，没有对应 Android 原测试，不将本次 14 个适配测试算作新增移植数。E07b1 的 **44 项原逻辑测试仍全部运行；新增原测试 0、跳过 0**。
3. 存储侧备份保留所有旧字段；当前 Eta UI DTO 不表达旧轮次的每个字段。完整展示和回退处理留 E07b3，不以保存 sidecar 宣称界面已消费这些字段。
4. 消息 ID 使用稳定的会话限定组合；这是防止不同会话相同旧 turn ID 碰撞的小决定。模型解析通过端口注入，现有 0.9 选择器格式是 `profileId:modelId`，不改旧绑定。
5. 使用 Node >=22.13 的 `node:sqlite` 跑桌面实 SQL 测试，不添加 npm 运行依赖。鸿蒙事务回滚失败时将连接标为不可继续使用，要求重开。

## 自动验证

- `npm run check`：通过。
- `npm test`：**602 通过 / 0 失败 / 0 跳过**。本次 14 项：SQL 分页/外键/更新/级联、串行与 DAO 生命周期、基线提交失败、迁移幂等、写入失败、重启、提交失败、缺失备份分块、损坏源、长 Unicode、变化源/目标占用、正文篡改、历史任务不重放、只读旧 RDB。
- `npm run check:ets`：**0 error / 92 warning**，8 项既有 `$$this` 检查器误报；未增加忽略项。独立适配器明确加入检查入口，未因生产未引用而漏检。
- `npm run check:res`：1,935 项资源通过，0 warning。
- **ArkTS 检查通过 / 未生成 HAP**。没有设备连接、RDB 真机、截图或迁移切换通过结论。
- 新用到的鸿蒙接口：`relationalStore.getRdbStore` 独立库及 `StoreConfig.isReadOnly`（API 12）、`RdbStore.querySql/executeSql/beginTransaction/commit/rollBack/close`、`ResultSet.columnNames/getValue/goToNextRow/close`、`cryptoFramework.createMd('SHA256').update/digest`、`util.TextEncoder`、`fileIo.readTextSync`。新增权限：**无**，签名配置保持为空。

## 真机步骤（待测）

1. 继续检查点 ①–④：DevEco 编译并安装，确认当前会话、服务及选中项恢复；此版本不应创建或切换新会话库。
2. E07b3 启动前，在开发测试入口对**复制的**旧 RDB 和 JSON 执行迁移；核对数量、选择、全部正文哈希、模型、时间和导出，再执行一次，结果应为 already_verified。
3. 在每个写入阶段及提交阶段注入失败，重开新库；应没有成功标记/部分会话；旧源哈希不变，修复后可以重试。
4. 修改源、破坏目标分块、提供坏 JSON，均应阻止切换并返回固定诊断码；确认诊断中不包含正文或密钥。
5. 用 600 消息、超旧上限的正文和跨分块 emoji，验证 RDB CursorWindow、分页/搜索/导出和 SHA-256。删除新库中的测试会话应清理该新库分块，旧库不变。
