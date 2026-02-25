# MyFlowHub-Android

Android 端 App（UI + Hub 宿主）与 gomobile 绑定产物。

> 说明：本仓库在 `d:\project\MyFlowHub3\repo\MyFlowHub-Android` 仅作为控制面；实现改动请在对应 worktree 进行。

## 自动构建 / 发版

- CI（push/PR）：自动构建 debug APK，并作为 Actions Artifact 提供下载。
- Release（tag）：推送 `vMAJOR.MINOR.PATCH` tag 后自动构建 **已签名** release APK，并发布到 GitHub Releases（同时上传 `myflowhub.aar` 与 `build-info.txt`）。

详见：`docs/release.md`
