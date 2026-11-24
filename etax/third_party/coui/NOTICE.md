# thirdparty/coui：来自 io.github.itoseo:coui 的代码

`app/src/main/kotlin/io/github/mangi/eta/thirdparty/coui/` 下的文件取自 **io.github.itoseo:coui 0.0.1-alpha11**（Maven Central，源码包 `coui-0.0.1-alpha11-sources.jar`，原包名 `com.itos.coui`）。

- 许可证：**Mozilla Public License 2.0**（见原 POM 的 `<licenses>`；全文 https://mozilla.org/MPL/2.0/ ）。MPL-2.0 按文件生效：这些文件（含改动）仍按 MPL-2.0 提供，每个文件开头都有 MPL 的 Exhibit A 声明，修改时请保留。
- 胖小胖做的改动：只把包名 `com.itos.coui` 改成 `io.github.mangi.eta.thirdparty.coui`；`topappbar/CouiToolbarBackdrop.kt` 只保留了原 `topappbar/TopAppBarSurface.kt` 里的一行声明；没搬的部分（菜单、对话框、顶栏、选择器、开关等）已由 COUI 2.1.0 或胖小胖自己的组件替代。
- 原库附带的第三方声明（部分着色器与几何代码源自 AndroidX、Skia）一并保留在本目录：`coui-androidx-LICENSE`（Apache License 2.0）、`coui-skia-LICENSE`（BSD 3-Clause）。

为什么搬进来：COUI 2.1.0 没有液态玻璃（边缘折射、内发光）。胖小胖保留「液态玻璃 / COUI 17 磨砂」两种控件材质（外观设置里切换），液态玻璃只能靠原库的渲染器，所以把它用到的这部分（采样源、浮动材质与着色器、阴影、聚光、按压动效、原生搜索栏、主题与文字）搬进仓库，去掉了对整个旧库的依赖（14 号方案 14.4d，2026111638）。
