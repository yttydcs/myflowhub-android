# 2026-02-25 - Android：GitHub Actions 自动构建与发布 APK（CI + Release）

## 背景 / 目标

当前 `MyFlowHub-Android` 已具备最小可用的 Android Hub + UI（M0），但 GitHub 上尚未建立“自动构建/自动发版”的交付链路：
- push/PR 无法自动产出可下载的 debug APK（影响快速验证）
- tag 无法自动发布“已签名的 release APK”（影响自用分发与覆盖升级）

本次 workflow 的目标是：在不扩大功能范围的前提下，补齐 GitHub Actions 的 CI + Release，使 APK 可全自动构建并发布到 GitHub Releases。

## 具体变更内容

### 新增

- GitHub Actions
  - `.github/workflows/ci.yml`：push/PR 自动构建（先生成 `myflowhub.aar`，再构建 `app-debug.apk`，并上传为 Actions Artifacts）
  - `.github/workflows/release.yml`：push tag `vMAJOR.MINOR.PATCH` 后自动构建并发布（含签名）
- 脚本
  - `scripts/build_aar.sh`：用于 GitHub Actions（Linux runner）构建 `app/libs/myflowhub.aar`（对齐现有 `scripts/build_aar.ps1`）
- 文档
  - `docs/release.md`：Secrets 配置、keystore 生成、tag 规则与发版步骤说明
- 计划归档
  - `docs/plan_archive/plan_archive_2026-02-25_android-hub-m0-smoke.md`：归档历史 workflow 计划，避免与新 workflow 的 `plan.md` 混用

### 修改

- `app/build.gradle.kts`
  - 支持通过 `-PversionName/-PversionCode` 注入版本号（用于 tag 发版）
  - 支持通过环境变量注入 release 签名（用于稳定签名、可覆盖升级）
  - 对 release 构建增加前置校验：未注入版本/签名时直接失败，避免误产出未签名或版本错误的 release
- `README.md`：补充“自动构建 / 发版”入口与文档指引

### 删除

- 无

## plan.md 任务映射

- ANDA1 - 归档旧计划（文档）
  - `docs/plan_archive/plan_archive_2026-02-25_android-hub-m0-smoke.md`
  - `plan.md`
- ANDA2 - Gradle：支持版本号注入（tag -> versionName/versionCode）
  - `app/build.gradle.kts`
- ANDA3 - Gradle：Release 签名支持（GitHub Secrets）
  - `app/build.gradle.kts`
- ANDA4 - 脚本：提供 Linux/macOS 可用的 AAR 构建脚本
  - `scripts/build_aar.sh`
- ANDA5 - GitHub Actions：CI（push/PR）构建 debug APK + Artifact
  - `.github/workflows/ci.yml`
- ANDA6 - GitHub Actions：Release（tag）构建 release APK + 发布到 Releases
  - `.github/workflows/release.yml`
- ANDA7 - 文档：补充发版与 Secrets 配置说明
  - `docs/release.md`
  - `README.md`

## 关键设计决策与权衡

1) **对齐现状的多仓 checkout**
- `hubmobile/go.mod` 目前使用 `replace ../../MyFlowHub-Server`。
- 为避免立即引入 Server 版本化依赖改造，本次选择在 GitHub Actions 中额外 checkout `yttydcs/myflowhub-server` 到 `repo/MyFlowHub-Server`，与本地 meta-workspace 目录结构对齐。
- 权衡：Release 的 Server 依赖默认取 `main` 最新，但会记录在 `build-info.txt` 里，保证可审计与可回放。

2) **release 强制版本注入 + 强制签名**
- release workflow 由 tag 驱动：从 tag 解析 `versionName/versionCode` 注入 Gradle，避免“tag 与 APK 版本不一致”。
- release 构建要求签名 env 完整，否则直接失败，避免误发布未签名 release APK（会导致覆盖升级失败）。

3) **产物策略**
- CI（push/PR）：提供 debug APK（便于快速验证）
- Release（tag）：提供已签名 release APK，并额外上传 `myflowhub.aar` 与 `build-info.txt`（便于复用/审计）

## 测试与验证方式 / 结果

### 已完成（静态审查）

- Gradle 逻辑：版本注入、release 签名注入与前置校验逻辑已补齐。
- Actions 逻辑：已包含环境安装、AAR 构建、APK 构建、产物上传、release 发布与签名校验步骤。

### 待执行（需要在 GitHub 上触发 Actions）

1) 配置 Secrets：按 `docs/release.md` 设置 keystore 与密码相关 Secrets
2) 验证 CI：push 任意提交或发起 PR，Actions 产出 `myflowhub-android-debug` Artifact（含 `app-debug.apk` 与 `myflowhub.aar`）
3) 验证 Release：推送 tag（例如 `v0.1.0`），检查：
   - GitHub Release 自动生成
   - Release Assets 包含 `app-release.apk`、`myflowhub.aar`、`build-info.txt`
   - `app-release.apk` 签名校验通过（workflow 内已执行 `apksigner verify`）

说明：当前容器环境缺少 Android SDK/NDK/JDK，无法本地执行完整构建；已通过 GitHub Actions 方式提供可复现验证路径。

## 潜在影响与回滚方案

- 影响：
  - 新增 Actions 会带来 GitHub runner 构建时间消耗（已启用 Gradle/Go cache，仍需首次下载 SDK/NDK）。
  - release 发布依赖 Secrets；Secrets 缺失将导致 tag 发版失败（符合预期，避免误发未签名包）。
- 回滚：
  - 若需关闭自动构建/发版：删除 `.github/workflows/*.yml` 并回滚 `app/build.gradle.kts` 的签名/版本注入逻辑。
  - 若误发 Release：在 GitHub Release 页面手动删除对应 Release/tag（并重新打正确 tag）。

