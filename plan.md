# Android：Debug Action 发布 debug-latest（APK + AAR）

## 目标
- 在 **GitHub Actions 页面**中，针对 **main 分支 push** 触发的 debug 构建，提供一个“直接下载 `.apk`”的入口（不再需要先下载 artifact 的 `.zip`）。
- 采用固定 Release/Tag：`debug-latest`，每次 main push 覆盖更新同一个 Release 的 APK 资产。
- 同步发布 `myflowhub.aar`（便于调试/集成验证）。

## 当前状态
- 现有 `.github/workflows/ci.yml` 会在 debug 构建后使用 `actions/upload-artifact` 上传 `app-debug.apk`。
- GitHub Artifact 在网页端下载会被打包为 `.zip`（不可避免），导致无法“直接下载 apk”。
- 当前 `publish-debug-latest` job 因 **artifact 解压目录结构与脚本硬编码路径不一致** 导致失败（找不到 APK）。
- `debug-latest` Release 目前仅计划上传 APK，未覆盖 `myflowhub.aar` 的发布需求。

## 方案概述
- 在 `ci.yml` 中仅对 `refs/heads/main` 的 push：
  - 构建完成后创建/更新 GitHub Release：`debug-latest`（pre-release）。
  - 上传/覆盖 Release 资产（使用 `gh release upload --clobber`）：
    - `myflowhub-debug.apk`
    - `myflowhub.aar`
  - 在本次 run 的 Summary 输出下载直链：
    - `.../releases/download/debug-latest/myflowhub-debug.apk`
    - `.../releases/download/debug-latest/myflowhub.aar`
- 为避免循环触发：`ci.yml` 需要忽略所有 tag push（或至少忽略 `debug-latest`）。

## 依赖与前置
- 仓库 Settings → Actions → Workflow permissions：`Read and write permissions`（已确认）。
- `publish-debug-latest` job 需具备 `contents: write`（job-level permissions）。
- Runner：`ubuntu-latest`（需可用 `gh` CLI；GitHub Hosted Runner 默认内置）。

## 任务清单（Checklist）

### T1. 调整 workflow 触发条件（避免 tag 循环）
- 目标：确保 workflow 不会因为创建/更新 `debug-latest` tag/release 而被再次触发。
- 涉及文件：
  - `.github/workflows/ci.yml`
- 验收：
  - `push tag debug-latest` 不会触发 `ci.yml`（或 workflow 内部明确跳过）。

### T2. 增加发布 debug release 的权限与并发控制
- 目标：在 main push 场景下允许写入 Release，并避免并发覆盖冲突。
- 涉及文件：
  - `.github/workflows/ci.yml`
- 验收：
  - workflow 拥有 `contents: write`（仅用于发布步骤）。
  - 同一时间只保留最新一次 main push 进行发布（旧的被取消）。

### T3. 发布/覆盖 `debug-latest` Release 的 APK 资产
- 目标：Release 中始终存在最新的 Debug 构建产物，可直接下载（APK + AAR）。
- 涉及文件：
  - `.github/workflows/ci.yml`
- 设计要点：
  - 使用 `gh` CLI：`gh release view/create/upload --clobber`
  - 资产命名固定：
    - `myflowhub-debug.apk`
    - `myflowhub.aar`
  - **不要硬编码依赖 upload-artifact 的目录根**：基于下载后的 `artifacts/` 目录结构定位 APK/AAR（或将 download 目标目录对齐到预期根目录）。
- 验收：
  - main push 后，仓库 Releases 中 `debug-latest` 可见且包含：
    - `myflowhub-debug.apk`
    - `myflowhub.aar`

### T4. Actions Summary 输出“直接下载链接”
- 目标：在 Actions run 页面内可一键点击下载。
- 涉及文件：
  - `.github/workflows/ci.yml`
- 验收：
  - run 的 Summary 包含直链，点击直接下载：
    - `.apk`（非 zip）
    - `.aar`

### T5. 验证与回滚预案
- 验证点：
  - main push 触发后：Release 更新成功、Summary 链接可用、原有 artifact 仍可用（可选保留）。
  - 非 main/PR：不发布 debug-latest release。
- 回滚点：
  - revert 该次 workflow 修改；
  - 必要时删除 `debug-latest` Release（以及同名 tag，如果由 workflow 创建）。

## 风险与注意事项
- GitHub Artifact 网页下载固定 zip：只能通过 Release/外链解决“直接下载 apk”诉求。
- workflow 创建 tag/release 可能导致 push(tag) 再触发：必须增加 tags-ignore 或条件跳过。
- 并发 push main 可能导致 release 资产互相覆盖：需加 `concurrency`。
- upload-artifact 会对多个 path 计算公共根目录：download 后的解压结构可能与“原始路径”不同，发布脚本必须按实际结构处理。

