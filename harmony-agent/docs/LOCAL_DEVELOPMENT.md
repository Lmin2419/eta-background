# 本地开发与真机测试（Windows + DevEco Studio）

从 0.9.1 起，仓库源码可以直接用 DevEco 打开并编译，不再需要升级 ZIP，也不需要编译 C++ 或 OpenSSL。以后每次 Codex（A 总）或 Claude 提交了新代码，你只需要拉取更新、点 Run、在手机上测试。

## 一次性准备

1. 安装 [Git for Windows](https://git-scm.com/download/win) 和 DevEco Studio（带 API 26 SDK，和之前编译 0.9.0 用的一样）。
2. 在想存放代码的文件夹空白处右键 → “Open Git Bash here”，执行：

   ```bash
   git clone https://github.com/Lmin2419/eta-background.git
   ```

   仓库是私有的，第一次会弹出 GitHub 登录窗口。
3. DevEco → 文件 → 打开 → 选择 `eta-background\harmony-agent` 文件夹。**不要选 `eta-background` 根目录**，根目录是 Android 工程。
4. 等待右下角同步完成（自动执行 ohpm install）。

## 签名：沿用旧签名才能保留数据

手机上已经装着旧版随身助手时，新版本必须使用**同一个签名**才能覆盖安装、保留对话和模型配置。

1. 用记事本打开**旧工程**根目录的 `build-profile.json5`，找到 `"signingConfigs": [ ... ]`，把方括号里的整段内容复制下来（包含 `name`、证书路径、密码等字段）。
2. 打开**仓库** `harmony-agent\build-profile.json5`：
   - 把复制的内容粘贴到 `"signingConfigs": []` 的方括号里；
   - 在 `products` 的那一项里加上 `"signingConfig": "default"`（引号里的名字要和你签名配置里的 `name` 一致）。
3. 证书文件通常在 `C:\Users\<你的用户名>\.ohos\config\` 下，路径是绝对路径，不需要移动。
4. **防止把签名提交到仓库**：在 Git Bash 中进入仓库目录，执行一次：

   ```bash
   cd eta-background
   git update-index --skip-worktree harmony-agent/build-profile.json5
   ```

   以后 git 会忽略你对这个文件的本地修改。

没有旧工程时：DevEco → 文件 → 项目结构 → Signing Configs → 勾选自动签名。
如果安装时提示“签名不一致”：**不要卸载手机上的应用**（卸载会清空数据），先检查是否复制了旧工程的签名配置。

## 编译和安装

1. 手机用数据线连接电脑（或已配对无线调试），DevEco 右上角选择模块 `entry` 和你的手机。
2. 点 Run（绿色三角）。
3. 打开应用 → 右上角菜单 → 设置，确认显示的版本号（例如 `0.9.1 · 交互框架预览`）。

编译报错时，把 DevEco 底部 Build 窗口的**完整错误文字**复制下来发给 A 总或 Claude。ArkTS 页面只有在 DevEco 中才能编译，开发环境里看不到这类错误。

## 更新代码

```bash
cd eta-background
git checkout main
git pull
```

然后在 DevEco 中点“同步”（文件 → Sync and Refresh Project），再点 Run。

### 测试某个 PR 的分支

PR 页面上能看到分支名，例如 `harmony/t04-background-reply`：

```bash
git fetch origin harmony/t04-background-reply
git checkout harmony/t04-background-reply
```

测完回到主线：`git checkout main`。

### 极少数情况：拉取时提示 build-profile.json5 冲突

说明上游改了这个文件。先备份你的签名段落，然后：

```bash
git update-index --no-skip-worktree harmony-agent/build-profile.json5
git stash
git pull
git stash pop
git update-index --skip-worktree harmony-agent/build-profile.json5
```

如果 `stash pop` 报冲突，手动把签名段落粘回去即可。

## 测试后怎样反馈

1. 按 PR 中的“真机验证步骤”或 [docs/device-runs/TEMPLATE.md](device-runs/TEMPLATE.md) 逐项操作。
2. 出现问题时：设置 → 诊断 → **复制报告**。报告已经自动去掉了 Key、网址路径和消息内容。
3. 把下面三样一起发给 A 总（或贴到 PR 评论里）：
   - 你做了什么（第几步、点了哪里）；
   - 看到了什么（可以附截图）；
   - 诊断报告。
