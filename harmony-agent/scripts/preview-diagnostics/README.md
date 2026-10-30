# 本轮 Linux 原生预览诊断源码

这些文件用于保存 2026-10-30 的原生截图采集过程，不是应用的运行依赖，也不是跨版本通用预览器。

- `interactive.py`：启动 Previewer、接收原始 JPEG 帧和 inspector 树，通过本地 HTTP 发送实际鼠标按下 / 松开；没有直接修改应用状态。
- `capture_flow.py`：依控件树坐标点击首页建议、发送、侧栏、模型配置和目录，断言导入前后四个输入相等。
- `audit.c`：按调用库隔离 SDK 的 EventRunner 同名符号；含该 SDK 特有的符号偏移。
- `preview_paths.cpp`：仅补全 Previewer 调用 Ark VM 时缺失的模块路径前缀。

当时路径：工程 `/workspace/harmony-agent`；SDK `/workspace/toolchain/command-line-tools/sdk/default/openharmony`；兼容目录 `/workspace/preview-runtime`；Xvfb `:97`。辅助工具包括 Xvfb、bwrap、Python websockets、gcc / g++。原始脚本保留当时的路径，迁移环境时须重新确认 SDK ABI、路径和库别名；不应直接套用符号偏移。

外部兼容层还补齐了 SDK `.z.so` / hilog / zlib 库别名，并用 bwrap 临时映射合法资产路径。SDK 和正式 HAP 没有被修改或加入这些库。竖屏尺寸 1264×1848，横屏 2564×1828，320 dpi。

常规开发优先用 DevEco 的 `FoundationPreview.ets` 原生交互预览。精确验证范围、已知限制和截图见 [视觉记录](../../docs/VISUAL_VALIDATION_0.9.md) 与 [证据目录](../../docs/verification/2026-10-30/README.md)。
