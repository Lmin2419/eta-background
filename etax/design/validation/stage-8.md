# 阶段 8 · 细节修正与息屏不暂停

基线 `ab061cf`（2026110614）。按 S10 / S11 拆成 8a、8b 两次提交，最后一并推送、发布 2026110615。猫 SVG 和生成矢量、包名、备份/导出、User-Agent、日志、上游致谢保持不变。没有改任务输入、预览触摸坐标、窗口几何、任务调度或动画。

## 8a · 每条先红后绿

修改生产代码前先运行新测试。失败摘要保存在 [before/](stage-8/before/) 中的 XML；只保留用例名和首行错误，不包含长堆栈。布局测试先运行 8 项，其中 6 项按预期失败；随后文案、提示身份和常用卡测试 5 项全部按预期失败。

| 规格 | 修改前自动化失败 | 修正及修改后断言 |
| --- | --- | --- |
| S10.1 | 223dp 名称可见字符为 0；提示只有 1 行 | 标题 Column 两行，状态 10dp 圈无 Chip 背景；223/244dp × 100/200% 名称至少 4/6/2 字、按钮 48dp；244dp 的 Google Chrome 至多省略 2 字母；五状态完整；短长提示切换后画面 bounds 不变 |
| S10.2 | 圆图标内部被状态圈遮盖 | 28dp 图标，16dp 实心圆垫和 12dp 圈，中心 (32,32)，28dp 高图标槽不撑高行；方形覆盖 ≤16dp²、圆形内部零覆盖；像素断言排除圆形抗锯齿边缘 2dp；多任务叠图加 2dp 圆垫 |
| S10.3 | 四条建议副标题有 visual overflow | 副标题两行、同排等高；393dp 四条完整；点击回调不变 |
| S10.4 | 运行按钮与菜单中心差 8dp | 垂直居中后差 ≤1dp；真实 MotionEvent 手指拖到底部，最后菜单仍在创建按钮上方；96dp 底部留白不变 |
| S10.5 | 英文长标签无法单行完整显示 | Root / A11y / Overlay / Alerts / Engine 单行；读屏保留 Accessibility / Notifications 全称 |
| S10.6 | 胶囊任务行只有 32dp | 任务行、打开会话、补充指令的语义点击区均 ≥48dp；文字字号不变 |
| S10.7 | 权限引导中的品牌空格未清理 | 三语去掉旧版本后缀；中文相邻汉字的品牌空格归零；保留项目与历史上游名称 |
| S10.8 | 五个资源仍为旧长文案 | 简/繁/英 15 个值逐项匹配 S10.8；操作数量、顺序、PendingIntent 不变 |
| S10.9 | 提示换成非中文前缀后，暂停仍停留在旧提示 | 先引入 PreviewHintType，保留旧文案运行 TaskPreviewActivityTest 全绿，再替换资源；24 类提示三语资源、自动刷新/保留、所有错误分支和接管上下文均覆盖；新帧不抹掉输入失败；未知后端诊断仍保留 |
| S10.10 | 排队返回「等待执行」，不是专用短标签 | 实际 Notification extras 遍历所有状态及三种完成态、三语短标签；不再 take(N)；完成态独立于是否弹出横幅 |

8a 本地针对性回归 **42 项通过，0 失败、0 跳过**，覆盖 Stage8Layout/Copy/SavedTask/Hint、TaskPreviewActivity、Stage6Surfaces、AgentRuntimeScreenOn。提示类型独立迁移、尚未改文案时的通过记录见 [hint-types-before-copy.xml](stage-8/before/hint-types-before-copy.xml)。新增文案引用实际按钮名；历史文档中的废弃按钮名以泛称标注，避免误导现版本操作。

提示区按本地化字体实际两行高度固定容器，避免 CJK fallback 字体与 Latin minLines 高度不同而跳动。文本内部测量高度可以不同，断言实际画面区域的位置和高度始终一致。预览窗按设计固定深色，浅/深主题截图的宿主底色不同。

## 修改前后截图

均为 Robolectric API 36 原生图形渲染（393dp 宿主），不是手绘稿，也不是 ColorOS 真机截图。测试夹具使用猫标记及手机占位图；任务窗口几何、手势逻辑没有为截图调整。每格点击可查看原始 PNG。

| 预览头部 | 浅色前 → 后 | 深色前 → 后 |
| --- | --- | --- |
| 223dp / 100% | [前](stage-8/before/stage8-preview-light-223-100.png) → [后](stage-8/after/stage8-preview-light-223-100.png) | [前](stage-8/before/stage8-preview-dark-223-100.png) → [后](stage-8/after/stage8-preview-dark-223-100.png) |
| 223dp / 200% | [前](stage-8/before/stage8-preview-light-223-200.png) → [后](stage-8/after/stage8-preview-light-223-200.png) | [前](stage-8/before/stage8-preview-dark-223-200.png) → [后](stage-8/after/stage8-preview-dark-223-200.png) |
| 244dp / 100% | [前](stage-8/before/stage8-preview-light-244-100.png) → [后](stage-8/after/stage8-preview-light-244-100.png) | [前](stage-8/before/stage8-preview-dark-244-100.png) → [后](stage-8/after/stage8-preview-dark-244-100.png) |
| 244dp / 200% | [前](stage-8/before/stage8-preview-light-244-200.png) → [后](stage-8/after/stage8-preview-light-244-200.png) | [前](stage-8/before/stage8-preview-dark-244-200.png) → [后](stage-8/after/stage8-preview-dark-244-200.png) |

| 界面 | 浅色前 → 后 | 深色前 → 后 |
| --- | --- | --- |
| 建议卡 | [前](stage-8/before/stage8-home-light.png) → [后](stage-8/after/stage8-home-light.png) | [前](stage-8/before/stage8-home-dark.png) → [后](stage-8/after/stage8-home-dark.png) |
| 任务角标 | [前](stage-8/before/stage8-badge-square-light.png) → [后](stage-8/after/stage8-badge-square-light.png) | [前](stage-8/before/stage8-badge-square-dark.png) → [后](stage-8/after/stage8-badge-square-dark.png) |
| 常用任务按钮 | [前](stage-8/before/stage8-saved-light.png) → [后](stage-8/after/stage8-saved-light.png) | [前](stage-8/before/stage8-saved-dark.png) → [后](stage-8/after/stage8-saved-dark.png) |
| 常用任务底部 | [前](stage-8/before/stage8-saved-bottom-light.png) → [后](stage-8/after/stage8-saved-bottom-light.png) | [前](stage-8/before/stage8-saved-bottom-dark.png) → [后](stage-8/after/stage8-saved-bottom-dark.png) |
| 英文权限卡 | [前](stage-8/before/stage8-readiness-en-light.png) → [后](stage-8/after/stage8-readiness-en-light.png) | [前](stage-8/before/stage8-readiness-en-dark.png) → [后](stage-8/after/stage8-readiness-en-dark.png) |
| 展开胶囊 | [前](stage-8/before/stage8-capsule-light.png) → [后](stage-8/after/stage8-capsule-light.png) | [前](stage-8/before/stage8-capsule-dark.png) → [后](stage-8/after/stage8-capsule-dark.png) |

英文权限卡基线另一次直接恢复 `ab061cf` 的 PermissionHealthCard 和英文 strings 渲染；渲染完成后恢复修改，未用新标签冒充旧界面。

## 8b · 息屏行为

下一独立提交完成 S11，并在本节补充失败基线、锁屏保护、手动暂停、后台任务与发布验证结果。

## 委派与范围

四个腾讯入口子任务：布局和提示类型用 coding（配置主用 GLM-5.3），短标签复核用 quick（配置主用 DeepSeek-v4.1-flash）；这三个返回未附实际模型元数据，不能推断是否内部回退。息屏边界 review 返回明确记录：DeepSeek-v4-pro 75 秒超时、Kimi-k3-1 HTTP 429，最终 **GLM-5.3** 完成。主会话逐项复核并执行补丁、测试、截图；未照搬子任务中不符合规格的完成态优先级建议。

## 最终手机验收清单

安装最终 2026110615 签名 APK 后，浅色和深色分别检查；自动化不替代真机结果。

1. 预览：普通与 200% 字号，223/244dp 级窄窗中应用名有实际字符；放大、关闭易点；接管状态「你在操作」；短提示和长提示切换，画面不跳高。
2. 任务条：方形、圆形图标角标位于右下外侧，无镂空划痕；多任务叠图边缘清楚。
3. 首页四条建议完整；常用任务「运行 / ⋯」对齐，滚到最后仍能点菜单；英文权限卡不折词；展开胶囊任务、会话、补充入口易点。
4. 通知：393dp 下运行中三个按钮完整；暂停、接管、完成、停止、失败标签为完整短词；点按动作与原来一致。切换简体、繁体、英文核对。
5. 预览接管、交还、页面回主屏、输入失败后画面刷新，提示与按钮对应；授权引导只显示「胖小胖」。
6. **S11 纯思考 / 搜索：** 发出后立即按电源键，稍后亮屏，任务已完成或仍继续，不能变成「已暂停」。
7. **S11 主屏操作：** 执行中按电源键，任务不暂停，锁屏期间没有误触；解锁后能看到转用后台屏或哪一步无法在锁屏下完成的说明。
8. **S11 后台虚拟屏：** 息屏后继续执行，行为不变。
9. 先手动暂停或接管，再息屏、亮屏，必须仍暂停，只有主动继续/交还才恢复；运行中任务亮屏后状态、流光和通知一致。

通知实际排版、ColorOS 流体云、系统省电下的连接与完成时间仍需以上真机验收。
