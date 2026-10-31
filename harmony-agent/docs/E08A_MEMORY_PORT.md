# E08a：记忆页面与本地管理（0.9.27）

分支 `harmony/e08a-memory`，base 为 P1d `harmony/p1d-gemini`（#32）。来源 `app/` @ `3b1794d`。Required Notice: Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0。

## 逐项对照

| Eta 元素 / 顺序 | 鸿蒙实现 | 分级 |
| --- | --- | --- |
| Settings → 记忆 | 原位置开放，Memory route 接入统一 Navigation | A（页面） |
| MiuixScaffold 顶栏 / 返回 / 清空 | 共用顶栏、原图标和资源文案，清空需要确认 | A；系统弹窗外观待测 |
| 开启记忆 / 核心记忆预算 | 原顺序、摘要、开关持久化；预算随选中模型窗口变化 | A（本地设置） |
| MEMORY.md / 字节容量 / 预览编辑分段 | 同原顺序、字号、16/10/12 内边距、圆角；空文件进编辑，有内容进预览 | A |
| Markdown 预览 / 无边框整页编辑 | 复用已移植 GFM 渲染；ArkUI TextArea；键盘出现收起设置区 | A；输入法/布局待测 |
| 未保存状态点 / 放弃 / 保存 | 只在 dirty 或 saving 显示；超限禁用；放弃回到保存基线 | A |
| 清空与通知弹窗 | 原文案、确认后清空，成功/失败通知可关闭 | A |
| AgentMemoryStore / UiState | 独立状态；保存成功才更新基线；后台写入失败保留旧内容 | A（逻辑） |
| 分页/搜索/修订 CAS/追加/替换 | 1 MiB UTF-8 上限、SHA-256、原逐行语义、串行提交 | A（逻辑） |
| ContextBuilder / ConversationMemoryPins | 原预算、核心章节、标题索引、32 项 LRU；尚未接聊天 | A（逻辑）、B（运行时接入） |
| 对话使用记忆 / memory_get / memory_write | 本轮未接入，顶部按任务卡显示“已保存；鸿蒙版对话暂未使用此功能” | B |

## 单测与四项检查

- Eta 原测试共 **9 / 跳过 0**：AgentMemoryStoreTest 3（原完整 fixture，Robolectric/AtomicFile 用真实临时文件与原子 rename 端口替代）、AgentMemoryContextBuilderTest 4、ConversationMemoryPinsTest 2。完整 1 MiB 和 10,000 字符 fixture，未缩短。
- 另 **6** 鸿蒙边界测试：编辑/预览/保存/放弃/重开；超限和提交失败；保存时输入变更；并发修订冲突；旧读取迟到；LRU/容量格式。
- `npm run check` 通过；`npm test` **846 通过 / 0 失败**；`npm run check:ets` **0 error / 96 warning**（8 已知误报忽略）；`npm run check:res` **1,943 资源通过**。
- **ArkTS 检查通过 / 未生成 HAP**，最终以 DevEco 为准。所有真机项待测。
- 新增权限：无。新使用的鸿蒙接口：`CryptoArchitectureKit.cryptoFramework.createMd('SHA256')` / `Md.update` / `Md.digest`、`util.TextEncoder.encodeInto` 用于记忆摘要；页面使用既有 ArkUI TextArea、Navigation、showAlertDialog 与键盘高度传递。均为普通 API，无 system_basic 权限。

## 与 Eta 的差异 / 待用户确认

1. 平台持久端口使用现有 RuntimeDatabase 的两个独立文档键 `eta_memory_md` / `eta_memory_enabled`，事务提交后返回。Android 使用 AtomicFile；这里保留内容/原子性/修订协议，没有引入新会话库，没有触发 E07b3，也不读取或覆盖旧 0.8 扩展数据。
2. 页面草稿、模式、dirty 与保存状态归 UiStore，ArkUI 仅持有渲染缓存和尺寸；离页作废旧异步状态回调。持久保存已经开始时允许完成，重新进入会读到最终已提交内容。
3. 按 E08 范围先完成页面与本地数据管理，记忆注入和模型读写工具尚未开放；ContextBuilder/Pins 的测试通过不表示对话已使用。显式提示不隐藏。未增加原页不存在的导入/导出入口。
4. 清空/通知用已批准的鸿蒙系统弹窗；文本编辑用原生 TextArea，选区/键盘、滚动联动和转场曲线需真机截图对照。连续圆角使用平台圆角。未声称与 Compose 绘制像素一致。
5. 尺寸新增 9vp 和编辑行高由 `port-eta-theme.py` 读取原记忆页生成；文案/图标/颜色均走既有资源脚本。沿用系统字体与已记录的 Markdown 平台差异。
6. 摘要和临时文件端口单测是宿主验证；Harmony RDB 的提交、崩溃/掉电语义和大文本输入仍待真机，未冒充验证结果。

## 真机步骤 — 待测

- 设置 → 记忆：对照 Eta 截图核对顶栏、开关、预算、文档标题、模式切换、容量和空状态顺序。
- 首次为空应进入编辑；输入多段中文/Emoji/Markdown，预览、编辑往返；保存后退出重进应默认预览。
- 改动出现保存栏；放弃恢复原内容；保存时按钮状态正确。弹出键盘时设置区收起，正文和保存栏不被遮挡；折叠/展开、横竖屏、深浅色逐项对照。
- 关闭记忆开关后重启应保持关闭，内容仍在。清空先取消确认内容不变，再确认应空；关闭通知后可继续编辑。
- 超过 1 MiB UTF-8 时不可保存；写入失败保留旧内容和草稿，不显示私有正文。
- 确认顶部“对话暂未使用”提示仍在，不把记忆注入勾为通过。
