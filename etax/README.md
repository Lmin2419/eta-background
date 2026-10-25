# Etax（Eta 助手·后台版）· 源码版

基于上游 [Mangi-11/Eta](https://github.com/Mangi-11/Eta) `v3.3.3`。
以前的做法是把官方 APK 的 dex 反编译、手工插入调用再打包；现在所有接线都写在源码里，
直接从源码编译出 APK，不再依赖 `dexopt2/classes.dex`。

## 改了什么

| 位置 | 作用 |
|---|---|
| `app/src/main/java/com/eta/shadow/` | 原来的影子代码（通知流式输出、后台虚拟屏工具、观察者） |
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

**root 引擎（可选）**：把现在的 `vdrive/dexout/classes.dex` 复制成 `etax/engine/vdrive.dex`，
再把引擎的 BUILD 字符串写进 `etax/engine/vdrive.version`，APK 就会内嵌它，`check_pair.sh` 照常能配对。
不放也不影响功能，引擎本来就是用 `install_vdrive.sh` 单独部署的。

## 跟上游升级

```bash
git fetch origin tag v3.3.5
git checkout -b etax-3.3.5 v3.3.5
git cherry-pick <本分支的两个 etax 提交>
```

有冲突时 git 会停下来，等你处理完再继续。处理完后跑一遍 `bash etax/check_hooks.sh`，
确认 12 处接线都还在；CI 也会在编译前自动跑这一项检查。

已经试过 `v3.3.4`：`agent/runtime/` 下 3 个文件有冲突，共 5 处，需要手工合并，检查脚本也正确报了错。
