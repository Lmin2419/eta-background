# 无头界面测试（OpenHarmony Linux 预览器）

在没有手机、没有 DevEco 的 Linux 环境里，把应用真正渲染出来，按场景点击、输入、断言、截图。能在交给用户真机测试之前，拦住"一打开就报错""点了没反应""整页卡住"这类问题。

```bash
cd harmony-agent
npm ci
pip install -r scripts/ui-preview/requirements.txt
bash scripts/fetch-ohos-sdk.sh --with-previewer      # 公开 OpenHarmony 7.0 SDK，API 26，免登录
export OHOS_SDK_ETS=~/.cache/ohos-sdk-26/ets
npm run test:ui                                       # tests/ui/ 下全部场景
npm run test:ui -- tests/ui/02-drawer.json            # 单个场景
```

报告在 `dist/ui-report/`：每个场景的截图（`*.jpg`）、控件树（`*-tree.json`）、预览器日志、`result.json`，以及汇总 `summary.md`。失败步骤会自动截图为 `failed-step-NN.jpg`。

需要：Linux x86-64、Node.js、Python 3（`websockets`）、gcc / g++、readelf、Xvfb、系统 libcurl（`libcurl4`），以及写入 `/data/storage/el1/bundle/` 的权限（root 或 sudo，见下文）。

## 原理

1. **预览编译**（`build-preview.cjs`）：不依赖 hvigor，直接调用 SDK 自带 ets-loader 的插件（界面转换、ArkTS 检查、es2abc）编出 `modules.abc`，用 restool 打包资源。预览入口 `pages/FoundationPreview` 只在构建输出里临时加入页面列表，不改仓库文件。
2. **预览器运行环境**（`setup.sh`，一次性）：在 `<sdk>/previewer-runtime/` 准备兼容层，SDK 原文件不改：
   - 补库别名：`libhilog.so`、`libshared_libz.so`、各组件的 `lib*.z.so`；
   - 网络库换成系统 libcurl：在覆盖目录中完成，保留 SDK 原有目录结构（`bin/` 旁边需要 `resources/`）；
   - 审计库 `audit.so`：让三个各自带有 `EventRunner` 的库绑定自己的实现，地址从 ELF 符号表自动生成；
   - 路径补丁 `preview_paths.so`：给页面路径补上模块前缀。
3. **场景驱动**（`uitest.py`）：通过预览器的命令通道发送点击（`MousePress/MouseRelease`）、按键（`KeyPress`）、读取控件树（`inspector`），通过 WebSocket 接收画面帧。

## 场景文件（`tests/ui/*.json`）

```json
{"name": "02-drawer", "page": "pages/FoundationPreview",
 "steps": [
  {"waitFor": {"a11y": "会话历史"}, "timeout": 20},
  {"tap": {"a11y": "会话历史"}},
  {"waitFor": {"a11y": "设置"}},
  {"screenshot": "drawer-open"}
 ]}
```

| 步骤 | 作用 |
| --- | --- |
| `waitFor` / `expect` | 在超时内等到匹配的可见元素 |
| `expectMissing` | 元素不应可见 |
| `tap` | 点击元素中心；默认要求点击后界面发生变化，否则判定"无响应"（`"expectChange": false` 关闭）。`"offset": [dx, dy]` 从中心偏移若干像素 |
| `longPress` | 长按，`"hold": 1.0` 秒；也支持 `offset`（例如按在气泡边缘而不是文字上，文字上长按会触发系统的文字选择） |
| `swipe` | `{"swipe": {"direction": "right"}}`，方向 `up` / `down` / `left` / `right`；`"on": {选择器}` 在某个元素上滑，`"distance": 0.6` 为距离占比 |
| `back` | `{"back": true}`，系统返回键 |
| `tapAt` | `{"tapAt": [0.65, 0.54]}`，按屏幕比例点击。**只用于系统对话框**（`showAlertDialog`）：它们在单独的子窗口里，控件树看不到。点完要在后面的步骤断言效果（例如消息消失） |
| `dump` | 保存截图并打印当前所有可见元素（类型、id、文字、无障碍文字、占位文字、是否可用、位置），写场景时用来找选择器 |
| `type` | 输入文字，**只支持 a–z、0–9、空格和 `. , - / ; = : _`**（预览器的输入法通道无法把文字送进输入框，改用实体按键事件；`:`、`_` 用 Shift 组合键） |
| `wait` | 等待秒数 |
| `screenshot` | 保存截图和控件树 |

选择器（可以组合，全部满足才算匹配；零尺寸的不可见节点会跳过）：`text`、`contains`、`id`（组件 `.id()`）、`a11y` / `a11yContains`（`accessibilityText`）、`placeholder`、`type`、`enabled`，以及 `attrs`（按控件树原始属性全等比较，例如 `{"type": "Span", "attrs": {"fontColor": "#00000000"}}` 匹配透明文字）。

场景顶层可以写 `"screen": {"color": "dark", "width": 1080, "height": 2340}` 切换深色模式和屏幕尺寸。尚未通过的验收场景放在 `tests/ui/pending/`，不在默认套件里，用 `npm run test:ui -- tests/ui/pending/<文件>` 单独运行。

**每一步还会自动检查**：预览器日志中出现新的 `FIX THIS APPLICATION ERROR`、`TypeError`、`ReferenceError` 等应用错误，或预览器崩溃，该步骤就判为失败。

**失败时**：终端打印失败原因、前 60 个可见元素和新出现的应用错误；`summary.md` 写明失败的步骤和错误；`failed-step-NN.jpg` / `-elements.txt` / `-tree.json` 保存当时的画面。CI 会把 `summary.md` 写进运行页面的 Summary。

## 局限

- 预览器中的系统接口（数据库、ASSET、网络、窗口等）大多是模拟实现，所以场景使用 `FoundationPreview`（内存数据 + 假模型流式回复），**不测试**真实存储、网络、签名、安装和性能。
- 不能输入中文。
- 依赖 `postFrameCallback` 的逐帧动画（例如回复文字的渐显）在预览器里不一定推进；涉及这类效果的场景要以真机为准。
- `bindPopup` 的气泡背景和阴影不绘制，只显示内容（加 `backgroundBlurStyle: BlurStyle.NONE` 也一样）；弹层外观只能在真机上看。
- 菜单（`bindMenu` / `bindContextMenu`）的内容会出现在控件树里，可以断言和点击；系统对话框（`showAlertDialog`）不会，只能截图查看并用 `tapAt` 点按钮。
- 预览器没有 `taskpool` 模块（返回空对象），依赖它的代码要有同线程兜底，例如 Markdown 解析。
- 预览器本身偶尔会原生崩溃（例如 `TextFieldPattern::GetInspectorId` 段错误，约五分之一的概率出现在打开带输入框的页面时）。`run.sh` 遇到“原生信号崩溃且日志中没有应用错误”时会把这个场景重跑一次，摘要中注明；连续两次崩溃，或者有任何 JS 错误，仍然判为失败。
- 运行时要求 abc 所在目录以 `/data/storage/el1/bundle/` 开头、目录名以 `.abc` 结尾（与真机沙箱路径一致），所以需要写入 `/data` 的权限。
- 兼容层针对 SDK 26.0.0.38 验证；升级 SDK 后先跑一遍 `npm run test:ui` 确认。
