# Etax（Eta 助手·后台版）· 源码版

基于上游 [Mangi-11/Eta](https://github.com/Mangi-11/Eta) `v3.3.3`。
以前的做法是把官方 APK 的 dex 反编译、手工插入调用再打包；现在所有接线都写在源码里，
直接从源码编译出 APK，不再依赖 `dexopt2/classes.dex`。

## 改了什么

| 位置 | 作用 |
|---|---|
| `app/src/main/java/com/eta/shadow/` | 影子代码（通知流式输出、后台虚拟屏工具、观察者、引擎自启 `Engine.java`） |
| `etax/engine/` | 内嵌的 root 引擎（dex + 启动脚本），编译时打进 APK assets，装完授 root 自动拉起 |
| 10 个上游 Kotlin 文件，每处带 `// etax` 注释 | 接线点，对应原来 dex 里的 10 处注入，外加任务开始和结束两个钩子 |
| `app/build.gradle.kts`、`AndroidManifest.xml` | 包名 `io.github.mangi.etax`，显示名「Eta 助手·后台版」 |
| `etax/check_hooks.sh` | 构建前检查 12 处接线都在，少一处就让构建失败 |
| `.github/workflows/etax-build.yml` | 在 GitHub Actions 上编译 APK |

## 编译（GitHub Actions）

1. 在 GitHub 上新建一个**私有**仓库，把整个目录推上去，分支名用 `etax-3.3.3`。
2. 仓库 Settings → Secrets and variables → Actions，添加这 4 个 Secret（用你现在的 `etax.jks`，
   这样新包才能直接覆盖安装、不丢数据）：
   - `ETAX_KEYSTORE_BASE64`：`base64 -w0 etax.jks` 的输出
   - `ETAX_STORE_PASSWORD`：keystore 密码
   - `ETAX_KEY_ALIAS`：别名，用 `keytool -list -keystore etax.jks` 查看
   - `ETAX_KEY_PASSWORD`：key 密码
3. 推送后到 Actions 页面打开 **Etax Build**，跑完后在页面底部下载 APK，大约 15–25 分钟。

不配置 Secret 也能编译，只是会用临时证书签名，装之前要先卸载旧的 etax。

**root 引擎（内嵌 + 自启）**：引擎文件放在 `etax/engine/`（`vdrive.dex`、`vdrive.version`、
`vd.sh`、`install_vdrive.sh`、`check_pair.sh`），编译时打进 APK 的 assets。装完 APK 后，
无障碍服务一连上、或第一条 `screen_*` 工具调用时，`com.eta.shadow.Engine` 会自动用 `su` 把这些文件
释放到 `/data/local/tmp/eta/` 并跑 `vd.sh` 把引擎拉起来 —— **装完授一次 root 就能用，不用再手动部署**。

- 引擎需要 **root**：未 root 的手机 `su` 会失败，`screen_*` 会提示去授权（通知栏流式输出那条链路不吃 root，照常可用）。
- 自启是幂等的：引擎在跑就不动它（`vd.sh status`）；换了新版 APK（dex 变了）会自动重铺并重启。
- 仍可手动管理：`sh /data/local/tmp/eta/vd.sh`（重启）、`vd.sh stop`、`vd.sh status`、`check_pair.sh`（配对校验）照常能用。
- 若 `etax/engine/vdrive.dex` 为空，APK 不内嵌引擎，`screen_*` 会提示“引擎没打进包”。

## 跟上游升级

```bash
git fetch origin tag v3.3.5
git checkout -b etax-3.3.5 v3.3.5
git cherry-pick <本分支的两个 etax 提交>
```

有冲突时 git 会停下来，等你处理完再继续。处理完后跑一遍 `bash etax/check_hooks.sh`，
确认 12 处接线都还在；CI 也会在编译前自动跑这一项检查。

已经试过 `v3.3.4`：`agent/runtime/` 下 3 个文件有冲突，共 5 处，需要手工合并，检查脚本也正确报了错。
