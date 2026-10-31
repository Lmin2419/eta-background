# DevEco 10905201：聊天列表底部锚点修复

## 用户反馈与复现

用户在 2026-10-31 用 DevEco 编译提交 `689db323`（0.9.30）时失败，完整诊断指向 `entry/src/main/ets/ui/eta/components/AgentChatBody.ets:120:18`：

```text
10905201: The 'Blank' component can only be nested in the 'Row,Column,Flex' parent component.
```

这是 **CompileArkTS 的 UI 结构错误**，尚未进入签名/安装，不要求用户重新签名、卸载或修改证书。日志总计 ERROR:2，但用户提供的具体诊断只有上述一处，不据汇总数量臆测另一项根因。

已用公开 SDK 26 的真实 ArkTS 语法树和组件父容器规则复现同一文件、行、列；修复前扫描 91 个 `.ets` 文件只发现这一处不合法嵌套。

## 修复

Eta 的 `AgentChatBody.kt` 列表底部空白锚点在 E04b 翻译时误用了鸿蒙 `Blank`。将 ListItem 中的 `Blank().width('100%').height(S.Dp1)` 改为 **`Row(){}.width('100%').height(S.Dp1)`**。

- 保留底部独立列表项、100% 宽度、1vp 高度、滚动锚点索引与行为。
- 不改消息排序、布局尺寸 token、颜色、文案、权限、会话存储或签名。
- 来源仍是 `app/.../ui/components/AgentChatBody.kt @ 3b1794d`；这是 ArkUI 容器约束适配，不是重新设计 Eta 页面。
- 在最早引入错误的 `harmony/e04b-chat-rendering` / PR #19 追加修复，再沿现有 base 关系同步到下游分支。#22/#35 原有草稿状态保持；不合并 PR、不推送 main。

## 为什么旧检查没有报错

原 `check:ets` 只调用 SDK 的 `etsStandaloneChecker`（类型和 ArkTS 规则）；本错误属于完整 UI 转换阶段的父子组件约束，所以旧检查漏检。

现在 `check:ets` 增加 `scripts/arkui-parent-check.cjs`：使用同一个 SDK 的 ArkTS parser 和 `SPECIFIC_PARENT_COMPONENT` 规则表，扫描 `entry/src/main/ets` 下的 `.ets` 文件。穿透条件/循环，未知自定义容器不猜测成系统容器；语法解析失败也报错，不悄悄跳过。

同时运行 **7 项 SDK 回归测试**：原错误和准确行列、合法 Row 锚点、Row/Column/Flex、条件/循环下的错误、未知自定义容器/独立根节点不误报、ListItem/ListItemGroup、语法不完整。它们在 `npm run check:ets` 执行，不计入 `npm test` 的原 Eta 测试数；没有添加错误忽略规则，没有修改 Android workflow。

这仅补齐已知父子组件规则，**不等于运行完整 ArkUI 编译、生成 HAP 或完成真机验证**。用户 DevEco 的重新编译和安装仍为待测。

## 验收（待测）

1. 在本机已签名工程替换上述一行，或使用包含此修复的新源码并沿用自己的签名。
2. DevEco → 文件 → 同步和刷新项目；构建 → 清理项目；点击 Run。
3. 确认 CompileArkTS 不再报此 10905201，正常生成并安装签名 HAP。
4. 打开长会话，流式发送/停止，检查到底部、手动上滚、回到底部和加载更早消息；1vp 锚点不能带来额外大块留白。
5. 若仍报错，提供新 Build 输出的具体文件/行号；不把这次云端复现写成用户真机通过。

## 0.9.31 交付检查

- `npm run check`：通过；`npm test`：919 通过、0 失败。
- `npm run check:ets`：0 error、99 warning；91 个 ETS 文件父容器检查 0 error；7 项 SDK 回归全部通过。原有 8 条 `$$this` 误报规则未增加。
- `npm run check:res`：restool 通过，1,946 资源。
- 起点 PR #19 的修复提交 `428d42b8` 已通过云端 Harmony CI。下游沿现有 base 同步：#20 → #21；#21 分别进入 #22 草稿与 #23 → #24 → round3-plan → #25 → #26 → #27 → #28 → #29 → #30 → #31 → #32 → #33 → #34；#34 分别进入 #35 草稿与 #36。
- 下游使用带上游第二父节点的合并提交，保留各任务自己的代码。#35 的原有构造器错误没有借本次修复绕过，其草稿状态保持。最终用户交付在 `harmony/e08c-mcp` / PR #36，版本 0.9.31。
- 没有合并任何 PR、没有推送 main；没有 HAP 构建或用户真机通过结论。

Required Notice: Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0。
