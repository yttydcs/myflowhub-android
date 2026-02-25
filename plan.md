# Plan - Android：GitHub Actions 自动构建与发布 APK（CI + Release）

> 说明：本 workflow 只解决“在 GitHub 上全自动产出 APK 并发布”的交付链路问题；不扩大到 UI/Hub 功能演进。
> - CI：push/PR 构建 debug APK（并内置 Hub AAR），作为 Actions Artifact 供随时下载验证。
> - Release：push tag `vMAJOR.MINOR.PATCH` 后，自动构建并发布 **已签名** release APK 到 GitHub Releases，并同时上传 `myflowhub.aar` 与 `build-info.txt`。

## 0. Workflow 信息

- Workflow 名称：`android-apk-release`
- 分支（本仓）：`feat/android-apk-release`
- Base：`main`
- 涉及仓库：
  - `MyFlowHub-Android`：新增 GitHub Actions、Gradle 签名/版本注入、文档补充
  - `MyFlowHub-Server`：CI 中仅 checkout（用于满足 `hubmobile/go.mod` 的 `replace ../../MyFlowHub-Server`），**不做功能改动**

## 1. 目标（验收口径）

### 1.1 必须达成
1) CI（push/PR）：
   - 自动构建 `app-debug.apk`（debug），并上传为 Actions Artifact。
   - 构建时先生成 `app/libs/myflowhub.aar`（gomobile bind，至少 `android/arm64`），确保 APK 内置 Hub 能力。
2) Release（tag）：
   - 推送 tag `vMAJOR.MINOR.PATCH` 后自动创建/更新 GitHub Release。
   - 自动构建并上传 **已签名** `app-release.apk`。
   - 同时上传 `app/libs/myflowhub.aar` 与 `build-info.txt`（包含 Android/Server commit、构建环境信息）。
3) 版本规则：
   - `versionName = MAJOR.MINOR.PATCH`（去掉 `v` 前缀）
   - `versionCode = MAJOR*1_000_000 + MINOR*1_000 + PATCH`
   - 若 tag 不符合格式，Release workflow 直接失败（避免产出错误版本）。

### 1.2 不做（明确排除）
- 自动发现（mDNS/广播）。
- UI/Hub 功能演进与安全加固（仅交付链路）。

## 2. 当前状态（已知问题）

- 当前仓库无 `.github/workflows`，GitHub 不会自动构建 APK。
- `app` 缺少“从 CI 注入版本号/签名配置”的通用入口（需补齐以支持全自动发布）。

## 3. 计划拆分（Checklist）

> 约定：每个任务必须有回滚点；不得引入计划外改动；新增任务需先更新本 plan 并重新确认。

### ANDA1 - 归档旧计划（文档）
- 目标：将已完成的历史 workflow 计划归档，避免后续混淆。
- 涉及文件：
  - `docs/plan_archive/plan_archive_2026-02-25_android-hub-m0-smoke.md`（新增）
  - `plan.md`（重写为本 workflow 的计划）
- 验收条件：旧计划内容可追溯，本计划内容可独立执行。
- 回滚点：revert 本任务提交。

### ANDA2 - Gradle：支持版本号注入（tag -> versionName/versionCode）
- 目标：支持通过 `-PversionName/-PversionCode` 覆盖 `defaultConfig`，并包含输入校验。
- 涉及文件：
  - `app/build.gradle.kts`
- 验收条件：
  - 本地执行 `./gradlew :app:assembleDebug -PversionName=1.2.3 -PversionCode=1002003` 可通过。
  - 未提供参数时保持当前默认版本行为不变。
- 测试点：Gradle 构建成功；参数非法时有清晰错误信息。
- 回滚点：revert 本任务提交。

### ANDA3 - Gradle：Release 签名支持（GitHub Secrets）
- 目标：在 CI/release 场景通过环境变量注入 keystore 与密码，实现稳定签名（便于覆盖安装升级）。
- 涉及文件：
  - `app/build.gradle.kts`
- 验收条件：
  - 当签名 env 完整时：`assembleRelease` 产出已签名 APK。
  - 当签名 env 缺失时：不影响 debug 构建；release workflow 会在前置校验阶段失败（避免产出未签名 release）。
- 回滚点：revert 本任务提交。

### ANDA4 - 脚本：提供 Linux/macOS 可用的 AAR 构建脚本
- 目标：让 GitHub Actions 可复用脚本生成 `app/libs/myflowhub.aar`（与现有 `scripts/build_aar.ps1` 对齐）。
- 涉及文件（建议）：
  - `scripts/build_aar.sh`（新增）
- 验收条件：在 CI 中可执行并成功生成 AAR。
- 回滚点：删除脚本并调整 workflow 调用方式。

### ANDA5 - GitHub Actions：CI（push/PR）构建 debug APK + Artifact
- 目标：push/PR 自动构建（含 AAR）并上传 artifact。
- 涉及文件：
  - `.github/workflows/ci.yml`（新增）
- 验收条件：
  - Actions 运行成功，artifact 包含 `app-debug.apk`（与可选 `myflowhub.aar`）。
- 测试点：在 GitHub 上触发一次 CI（可通过 PR/手动 commit 验证）。
- 回滚点：删除 workflow 文件。

### ANDA6 - GitHub Actions：Release（tag）构建 release APK + 发布到 Releases
- 目标：tag `v*.*.*` 触发，全自动发布。
- 涉及文件：
  - `.github/workflows/release.yml`（新增）
  - （可选）`docs/release.md` 或 README 补充（见 ANDA7）
- 验收条件：
  - 推送 tag 后自动创建 GitHub Release。
  - Release Assets 包含：`app-release.apk`、`myflowhub.aar`、`build-info.txt`。
  - Release APK 已签名、版本号与 tag 规则一致。
- 回滚点：删除 workflow 文件。

### ANDA7 - 文档：补充发版与 Secrets 配置说明
- 目标：让他人可按文档独立配置 Secrets 并发版。
- 涉及文件（其一即可）：
  - `README.md` 或 `docs/release.md`（新增/修改）
- 必须写清：
  - tag 格式与版本规则
  - GitHub Secrets 列表与含义
  - keystore 生成与 base64 写入方式（避免泄漏）
- 回滚点：revert 文档提交。

### ANDA8 - Code Review（强制）
- 按全局 3.3 清单逐项输出结论（通过/不通过）。
- 不通过：回到对应任务修正，再次 Review。

### ANDA9 - 归档变更（强制）
- 在本 worktree 根目录创建 `docs/change/` 并新增归档文档：
  - `docs/change/YYYY-MM-DD_android-apk-release-ci.md`
- 必须包含：任务映射、关键决策与权衡（尤其签名/版本/可复现性）、验证方式与结果、回滚方案。

## 4. 依赖与注意事项

### 4.1 GitHub Secrets（Release 必需）
- `ANDROID_KEYSTORE_BASE64`：keystore 文件 base64
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

### 4.2 CI 构建依赖
- JDK：17
- Android SDK：compileSdk 34（platforms/build-tools）
- Android NDK：gomobile 需要（版本在 workflow 中固定，并可调整）
- Go：`hubmobile/go.mod` 依赖 toolchain（CI 允许自动下载对应 Go toolchain）

### 4.3 风险与回滚
- 可复现性：Release 默认使用 `myflowhub-server` 的 `main` 最新；通过 `build-info.txt` 记录 Server commit 以便审计与回放。
- 回滚：revert workflow/Gradle 改动；GitHub Release 可手动删除（如误发版）。


