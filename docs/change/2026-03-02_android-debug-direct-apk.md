# Android：Debug 工作流提供 APK 直链下载（debug-latest）

## 背景 / 目标

此前 Debug 构建通过 `actions/upload-artifact` 上传 `app-debug.apk`，但在 GitHub Actions 页面下载 Artifact 时会被打包为 `.zip`，无法满足“点击后直接下载 `.apk`”的测试体验诉求。

目标：
- 仅在 `main` 分支 push 的 Debug 构建中，提供可直接下载的 `.apk` 链接。
- 使用固定的 `debug-latest` 作为 Release/Tag，保证链接稳定、永远指向最新 main commit 的 Debug APK。

## 具体变更

### 修改
- `.github/workflows/ci.yml`
  - 忽略所有 tag push，避免 `debug-latest` tag 更新造成 workflow 循环触发。
  - 新增 `publish-debug-latest` job（仅 `push` 到 `refs/heads/main` 执行）：
    - 从 `build-debug` 下载 debug 产物 artifact。
    - 使用 `gh api` 强制更新 `debug-latest` tag 指向 `GITHUB_SHA`。
    - 创建或更新 `debug-latest` Release（pre-release），并上传/覆盖 `myflowhub-debug.apk`。
    - 写入 Actions run Summary：包含 Release 页面与 `.apk` 直链下载地址。

### 新增
- `docs/change/2026-03-02_android-debug-direct-apk.md`（本文）

## plan.md 任务映射
- T1：调整 workflow 触发条件（忽略 tag push）
- T2：发布权限与并发控制（job-level `contents: write` + `concurrency`）
- T3：发布/覆盖 Release 资产（`myflowhub-debug.apk`）
- T4：Summary 输出直链
- T5：验证与回滚预案（见下）

## 关键设计决策与权衡

1) **为什么用 Release 而不是 Artifact**
- GitHub Artifact 在网页端下载固定为 `.zip`，无法实现“直接下载 `.apk`”。
- Release asset 支持浏览器直接下载单文件，且可用固定 tag 形成稳定直链。

2) **为什么固定 `debug-latest`**
- 测试入口稳定：链接不随 run 变化。
- 通过强制更新 tag，使 Release 对应的代码也与最新 commit 一致（避免“下载到最新 APK，但 tag 指向旧代码”的困惑）。

3) **权限与安全**
- workflow 默认 `contents: read`。
- 仅 `publish-debug-latest` job 需要 `contents: write`，并且仅在 `main` push 场景执行。

4) **并发控制**
- 使用 `concurrency`（`myflowhub-android-debug-latest`）确保同一时间只有一个发布任务在更新 `debug-latest`，降低资源覆盖冲突。

## 测试与验证方式 / 结果

说明：此变更主要影响 GitHub Actions 行为，无法在本地完全模拟验证。

验证步骤（合并到 `main` 后）：
1. push 任意提交到 `main`，触发 `ci` workflow。
2. 在该 run 页面查看 Summary：
   - 存在 `Direct download` 链接，点击后直接下载 `myflowhub-debug.apk`（非 zip）。
3. 打开 Release：`debug-latest`：
   - 能看到并下载最新的 `myflowhub-debug.apk`。
   - Release notes 中的 `commit=` 与本次构建 commit 一致。

## 潜在影响与回滚方案

潜在影响：
- 每次 `main` push 会更新 `debug-latest` tag 与 Release（属于预期行为）。

回滚方案：
- revert 本次对 `.github/workflows/ci.yml` 的修改。
- （可选）在 GitHub Releases 页面删除 `debug-latest` Release，并删除同名 tag。

