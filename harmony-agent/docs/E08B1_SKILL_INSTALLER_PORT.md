# E08b1：Skills 安装与恢复核心（0.9.28）

基于 `harmony/e08a-memory`，提交在 `harmony/e08b-skills`。本次是 Skills 页的第一部分，下一 PR 接鸿蒙文件端口、文件选择器和页面。当前正式入口没有初始化本安装器，也没有注册 Skill 模型工具。

## 逐项对照

| Eta 来源 @ 3b1794d | 鸿蒙核心 | 状态 |
| --- | --- | --- |
| SkillParser / SkillModels | 同名字段、YAML 简单标量/折叠块/字面块、metadata、ID 与查找规范化 | A（纯逻辑） |
| SkillPackageInstaller | 本地单 Skill ZIP；仓库检查/显式多选；配额、路径、冲突、重新读取摘要绑定、取消边界 | A（纯逻辑；平台接入留 E08b2） |
| SkillRecoveryJournal | 写盘日志、目录备份、提交、保留备份的可重入恢复、登记状态恢复 | A（端口与真实临时文件测试） |
| SkillIndexService | 三个内置 Skill 种子、缓存强刷、开关、按名称排序、删除、重新安装 | A（纯逻辑） |
| SkillMutationLock / Room | 串行执行 + 文件锁端口；恢复完成后才调用受保护操作；登记状态原子写端口 | A（协议）；鸿蒙平台待接 |
| builtin_skills | 固定提交的 3 份原始资源生成；脚本校验 Git blob SHA，品牌替换 | A（数据） |
| AgentSkillsScreen / AgentSkillsStore | 下一 PR | B（当前仍禁用入口） |
| SkillLoader / ResourceReader / GitHub 下载 / 模型工具 | 本地管理之外，后续接入；未注册 | B |

## 与 Eta 的差异

- Java `ZipInputStream` 改为中央目录/本地头一致性校验的 ZIP32 读取器，原始 Deflate 为显式平台端口。接受存储/Deflate、UTF-8 路径和数据描述符；拒绝 ZIP64、加密、多卷、其他压缩算法和非 UTF-8 名称。默认配额仍为 32 MiB ZIP、128 MiB 解压总量、32 MiB 单文件、512 KiB SKILL.md、2,048 条、16 层。额外拒绝 Unix 符号链接条目，不执行包内脚本。
- 归档读为有界单份字节，按条目解压并逐次写入暂存目录，不将整个解压结果驻留内存。SHA-256 来自核心实际校验的同一份字节。鸿蒙 inflater 的输出上限与文件锁接口须在下一 PR 落地。
- 索引现阶段只接单个私有根目录，已安装项删除只允许规范的 `skills/<id>` 根，不开放任意路径；旧鸿蒙 extensions 数据未自动导入。
- 原子文件写/rename/文件锁/登记存储经端口注入。Node 测试使用真实临时目录、符号链接、ZIP 和 rename；不能据此宣称鸿蒙平台文件语义已验证。
- 内置 Skill 文档保留原文（品牌替换），描述的工具能力尚未接入；页面将按 E08 要求显示“已保存；鸿蒙版对话暂未使用此功能”。

## 测试盘点

| Eta 原测试类 | 完整移植 | 跳过 | 说明 |
| --- | ---: | ---: | --- |
| SkillPackageInstallerTest | 21 | 0 | 包含批量提交第 4 次移动失败、恢复第 6 次移动失败的原故障位置 |
| SkillRecoveryJournalTest | 7 | 1 | malformedJournal 原例还验证 SkillLoader；Loader 不在页面管理范围，未将仅索引断言算作完整移植 |
| SkillRuntimeTest | 5 | 0 | 临时文件/符号链接断言实际执行，无 assume 跳过 |
| 合计 | 33 | 1 | 额外 8 个适配回归，其中 1 个保留上述 malformedJournal 索引半部的验证 |

额外测试覆盖 ZIP 头/CRC/长度/Unicode 冲突、解压上限、并行冲突、登记提交失败后恢复禁用状态与无效恢复日志。SkillResourceReader、公共 GitHub 下载及安装工具门控未移植，属于未来模型工具任务，不计入本次通过数。

## 验证与待测

四项命令通过：`npm run check`、`npm test`（887 / 0 失败）、`npm run check:ets`（0 error）、`npm run check:res`（1,943 资源）。ArkTS 检查通过 / 未生成 HAP。本次无新增鸿蒙接口或权限；签名保持空配置。

真机检查待 E08b2 页面接入：导入一个带 references/scripts 的 ZIP，关开 App 后内容与开关仍在；同名取消保留旧版、确认替换更新；更换已选择 ZIP 的字节后拒绝确认；无效路径、多 Skill、超限和内置同名均拒绝；删除用户 Skill 后重启不恢复。所有步骤均标为待测。

## 待用户确认

ZIP 兼容范围先采用 SDK 能明确实现的 ZIP32 + UTF-8 + 存储/Deflate；原始 Java 可接受而本端口拒绝的其他 ZIP 变体保留上述差异。安装/恢复层与页面拆两个 PR，便于独立检查。
