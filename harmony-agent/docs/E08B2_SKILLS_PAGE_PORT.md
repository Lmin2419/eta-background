# E08b2：Skills 页与鸿蒙文件端口（0.9.29）

基于 E08b1 / PR #34；本 PR 在 `harmony/e08b2-skills-page`。**草稿：三次完整检查后仍有 1 个 ArkTS 错误，不可合并。** 本分支设置中的 Skills 入口已接新页面，顶部按任务卡提示“已保存；鸿蒙版对话暂未使用此功能”。正式会话存储仍未切换。

## 逐项对照

| Eta 元素 @ 3b1794d | 鸿蒙实现 | 状态 |
| --- | --- | --- |
| AgentSkillsScreen | 安装 → 内置 → 用户 → 已移除；原始标题/文案/间距/颜色；加载和空状态 | A（代码，设备待测） |
| SkillSwitchRow | 同图标映射、两行描述、省略、描述菜单、开关、用户删除 | A（代码） |
| AgentSkillsStore | 唯一状态、刷新、忙碌门控、删除/重装、替换二次确认、有限错误提示 | A（逻辑） |
| SkillZipImportGateway | 成功只展示元数据；冲突策略/摘要；错误分类与恢复失败覆盖 | A |
| OpenDocument / ContentResolver | DocumentViewPicker 返回的文件 URI 封装为读取能力；每次确认重新打开所选文件 | A（平台代码） |
| Java ZIP / IO / FileLock / Room | zlib 原始 Deflate、私有 FileIO、独占文件锁、现有 RDB 的独立 registry 文档 | A（平台代码） |
| 模型使用 Skill / GitHub 下载工具 | 未注册；页面保存不影响聊天 | B（明确提示） |

## 与 Eta 的差异

- 已确认使用鸿蒙原生菜单和弹窗。尺寸、长文本滚动、深浅色、折叠/展开宽度仍需截图对照；没有宣称像素级真机一致。
- 删除/描述弹窗的目标也归 Store，页面仅渲染并发意图。多一次 `isPicking` 门控，防止系统选择器尚未返回时重复启动。
- Kotlin Coroutine IO 改为异步 FileIO / zlib，列表/选择器/安装都有离页代数检查。UI 无法提交任意文件路径，所选 URI 不写入数据库。后续通知跳离 Skills 页同样作废其待处理结果。
- ZIP 范围沿用 E08b1：UTF-8、ZIP32、存储/Deflate，不支持 ZIP64/加密/多卷。没有注册包内脚本执行或虚拟屏能力。
- 文件存于应用私有 `eta-skills-v1`，登记状态为现有 RDB 的 `eta_skills_registry_v1`；不启用 E07b3，不改旧会话/旧扩展数据。平台使用 `fsync + 同目录 rename`，设备断电/进程终止恢复仍待实测。
- 核对 OpenHarmony `bundlemanager_bundle_framework/interfaces/kits/js/zip/napi/class_zip/zip_n_exporter.cpp` 后确认 `getZStream()` 仅返回计数，不能取其 `nextOut`。端口保留最初输出缓冲区，检查 STREAM_END/实际输入输出数量；额外契约测试覆盖该区别。此源码核对不替代目标机验证。
- 5 项 UI/gateway 原测试完整移植、0 跳过；E08b1 的 1 项完整 Loader 测试仍延期，不改记为通过。

## 验证

第三次检查：`npm run check` 通过、`npm test`（903 测试、0 失败）通过；`npm run check:ets` **1 error / 119 warnings**；`npm run check:res` 未执行（检查流水线在 ArkTS 失败处停止）。新增 5 原测试 + 11 适配测试（9 Store/登记管理 + 2 有界 inflater 端口）。ArkTS 检查未通过 / 未生成 HAP。真机全部待测。

阻塞位置：`entry/src/main/ets/platform/EtaSkills.ets:41`，`constructor(private zip:zlib.Zip){}` 违反 `arkts-no-ctor-prop-decls`；应改为显式字段和构造函数内赋值。首次检查发现动态资源引用、第二次发现来源注释格式；已修正。第三次全量逻辑测试通过后，新增 inflater 端口的上述 ArkTS 写法仍未通过。按用户三次规则停止本任务，保持草稿。下一独立 MCP 从 E08b1 / PR #34 分支开始，不带入本草稿。

新增权限：无。仍仅有既存 INTERNET 与用户批准的 VIBRATE，signingConfigs 为空。新用到的普通鸿蒙接口：DocumentViewPicker.select；fileIo lstat/listFile/mkdir/open/stat/read/write/fsync/rename/unlink/rmdir/close、File.lock/unlock；zlib createZip/inflateInit2/inflate/getZStream/inflateEnd；util TextDecoder；复用 cryptoFramework SHA256、RDB 文档写入。无 system_basic/system_core API。

## 真机步骤（待测）

1. 设置 → Skills，内置 3 项出现；顺序、图标、开关、摘要与 Eta 截图比对。折叠/横屏、深色、大字体各看一次。
2. “导入 ZIP”选择器取消：列表不变；选择单 Skill 包（包含 UTF-8 中文、references 和 scripts）：出现成功通知与用户项，脚本不执行。
3. 禁用后退出重开，状态保持；描述菜单正常；内置无删除菜单；用户删除要确认，重启后仍删除。
4. 同名包导入：取消保留旧版；确认更新；确认前外部修改 ZIP 内容：拒绝替换。
5. 多 Skill、非法路径、内置同名、超额/损坏包失败后旧列表仍在；通知不包含 Key 或包正文。
6. 选择器/导入时返回或由通知跳离，迟到结果不得再次弹出；重开刷新实际磁盘状态。安装过程中强制结束应用，重开只显示完整旧版或完整新版，失败保留恢复目录。

## 待用户确认

沿用已批准的原生弹窗/菜单和 ZIP32 兼容范围。用户反馈有效 ZIP 在本机无法解压时，优先修复本平台端口，不扩大读取范围或跳过 CRC/大小/摘要校验。
