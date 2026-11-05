# 开发约定

- `main` 即最新版本。新工作直接基于 `main`，不要再从 `etax-340-step1-foundation` 等旧集成分支开始。
- 底座保持上游 v3.3.5，不跟进上游 3.4；只做必要修改，不重构上游代码。
- 发版：提高 `app/build.gradle.kts` 的 `versionCode`（`yyyyMMdd` + 两位序号）；改动引擎时同步 `etax/engine/src/VDrive.java` 的 `BUILD`；在 `etax/README.md` 顶部「版本更新记录」和根目录 `README.md`「最新测试版本」写更新说明。推送到 `main` 后 CI 自动签名并发布 GitHub Release。
- 构建与测试流程见 `.github/RELEASING.md`。
