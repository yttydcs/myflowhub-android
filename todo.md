# TODO - MyFlowHub-Android：修复 CI/Release 的 gomobile Android API 不兼容

## 目标

- 修复 GitHub Actions 中 `Build AAR (gomobile)` 失败问题，使 **CI（debug）** 与 **Release（tag）** 两条流水线稳定通过。
- 方案选择：`gomobile bind` 显式使用 `-androidapi 26`（对齐当前 App `minSdk=26`）。
- 同步将 GitHub Actions 的 Go 版本升级到 `1.25.x`（建议精确到 `1.25.7`），避免 `gomobile@latest` 触发隐式 toolchain 切换/下载的不确定性。

## 当前状态（问题复现）

- 失败日志（GitHub Actions）：
  - `gomobile: ... ndk/26.x ... unsupported API version 16 (not in 21..34)`
  - `javac ... Hubmobile.java ... package com.myflowhub.native.hubmobile; ... <identifier> expected`
  - `Assemble debug APK`：`Process completed with exit code 126.`
  - `./gradlew -v` / Actions `./gradlew :app:assembleDebug`：
    - `java.lang.NoClassDefFoundError: org/gradle/cli/CommandLineParser`
- 根因：
  - `gomobile bind` 默认 `androidapi=16`
  - 但 CI 安装的 **NDK r26** 仅支持 **API 21..34**
  - `javapkg` 默认值含 Java 关键字：`native`，导致生成的 `package com.myflowhub.native...` 非法
  - `gradlew` 在 Linux 上执行失败（exit 126）：仓库内 `gradlew` 需要可执行位（git mode 100755）
  - `gradle/wrapper/gradle-wrapper.jar` 内容异常：缺少 `gradle-cli` 等必需类（例如 `org.gradle.cli.CommandLineParser`）
    - 现象：运行 `gradlew` 只加载 `gradle-wrapper.jar`，但该 jar 内没有所需类，导致 `NoClassDefFoundError`
    - 备注：当前 jar 内含一个嵌套的 `gradle-wrapper.jar`（内层 jar 含缺失类），但脚本未将其加入 classpath
- 现状配置：
  - `app/build.gradle.kts`：`minSdk=26`
  - Workflows：安装 `ndk;26.1.10909125`，Go 为 `1.24.5`
  - `scripts/build_aar.sh/.ps1`：未显式指定 `-androidapi`

## 任务清单（Checklist）

> 约定：每个任务都要在 PR/commit 中可追溯；不引入计划外改动；如需新增任务，先更新本 TODO 并重新确认。

### AND-CI-01：修复 AAR 构建脚本的 androidapi

- 目标：为 `gomobile bind` 增加 `-androidapi 26`（默认值），并允许必要时可配置覆盖。
- 目标（补充）：修复 `javapkg` 默认值，避免使用 Java 关键字；并同步 Android 侧反射加载的类名，确保运行时能正确加载 AAR。
- 约定（已确认）：JavaPkg 默认值使用 `com.myflowhub.gomobile`（A）。
- 备注：`gomobile bind` 内部会调用 `gobind`，而 `gobind` 会通过 `go/packages` 在“当前 module 依赖”中定位 `golang.org/x/mobile/bind`。因此需要在 `hubmobile/go.mod` 中显式依赖 `golang.org/x/mobile`，否则会报 `no Go package in golang.org/x/mobile/bind`。
- 涉及文件：
  - `scripts/build_aar.sh`
  - `scripts/build_aar.ps1`
  - `hubmobile/go.mod`
  - `hubmobile/go.sum`
  - `app/src/main/java/com/myflowhub/android/HubBridge.kt`
  - `docs/m0_smoke.md`
- 验收条件：
  - 两个脚本都将 `gomobile bind` 调用改为包含 `-androidapi 26`
  - 输出日志能明确打印使用的 `androidapi`
  - 对 `androidapi` 做基本校验（必须为正整数且 >= 21）
  - `javapkg` 默认值不包含 Java 关键字；生成 Java `package` 合法
  - `hubmobile/go.mod` 显式依赖 `golang.org/x/mobile`（与 CI 中安装的 gomobile 版本一致）
  - Android 侧能正确 `Class.forName(...)` 找到 gomobile 生成类（至少在集成测试/冒烟测试中可验证）
  - `docs/m0_smoke.md` 的示例 `JavaPkg` 与代码默认值一致
- 测试点：
  - `bash -n scripts/build_aar.sh`（若本机无 bash，可跳过）
  - PowerShell 脚本语法解析（不实际执行 Android 构建）
  - Actions：`Build AAR (gomobile)` 通过；debug APK 能安装启动并进入后台服务（最小冒烟）
- 回滚点：
  - 还原对应脚本提交

### AND-CI-02：稳定 GitHub Actions 的 Go 版本

- 目标：将 `.github/workflows/ci.yml` 与 `.github/workflows/release.yml` 的 `actions/setup-go` 调整为 `1.25.x`（建议精确到 `1.25.7`）。
- 备注：由于本次将 `gomobile bind` 固定为 `-androidapi 26`，Actions 需要额外安装 `platforms;android-26`（提供 `android.jar`，否则 AAR 构建会失败）。
- 涉及文件：
  - `.github/workflows/ci.yml`
  - `.github/workflows/release.yml`
- 验收条件：
  - Workflows 中 Go 版本不再为 `1.24.5`
  - 安装 Android packages 时包含 `platforms;android-26`
  - 不再出现因 `gomobile@latest requires go >= 1.25` 而触发的隐式 toolchain 切换日志（或至少显著减少下载/切换的不确定性）
- 测试点：
  - 推送分支触发 CI（debug）成功
- 回滚点：
  - 还原 workflow 的 Go 版本调整提交

### AND-CI-03：修复 Linux 下 gradlew 不可执行（exit 126）

- 目标：确保 GitHub Actions（Ubuntu）可执行 `./gradlew`，且 Gradle Wrapper 可正常启动，避免 `Assemble debug APK` 失败。
- 方案候选（优先级从高到低）：
  1) **推荐**：将仓库中的 `gradlew` 设置为可执行（git mode 100755），让任何环境 checkout 后都能直接运行。
  2) **必须**：修复 `gradle/wrapper/gradle-wrapper.jar`（否则 `gradlew` 启动即 `NoClassDefFoundError`）
     - 候选 A（推荐/最小变更）：用当前 jar 内嵌套的“内层 wrapper jar”替换外层 `gradle-wrapper.jar`（保证类齐全）。
     - 候选 B：从官方 Gradle 发行包（与 `gradle-wrapper.properties` 的 `gradle-8.7` 对齐）提取正确的 wrapper jar 并替换。
     - 候选 C：在本地/CI 运行 `gradle wrapper` 重新生成 wrapper（会同时改动脚本与 jar）。
  3) 备选：在 workflow 中执行 `chmod +x repo/MyFlowHub-Android/gradlew`（对当前 CI 有效，但依赖 workflow）。
- 涉及文件：
  - `gradlew`（仅文件 mode 变更，内容不变）
  - `gradle/wrapper/gradle-wrapper.jar`
  - （可选）`.gitattributes`（强制 `gradlew` 使用 LF，防止未来被改回 CRLF）
  - （可选）`.github/workflows/ci.yml`、`.github/workflows/release.yml`
- 验收条件：
  - `git ls-files -s gradlew` 显示为 `100755 ... gradlew`
  - `./gradlew -v` 在 Actions（Ubuntu）可正常输出版本信息
  - Actions：`Assemble debug APK` 步骤可执行并通过
- 测试点：
  - 推送分支触发 CI（debug）成功
- 回滚点：
  - 还原 `gradlew` mode 或移除 `chmod` 步骤

### AND-CI-04：归档变更

- 目标：按规范在当前 worktree 的 `docs/change/` 归档本次修复。
- 涉及文件：
  - `docs/change/YYYY-MM-DD_fix-android-ci-gomobile-androidapi.md`
- 验收条件：
  - 归档文档包含：背景/目标、具体变更、任务映射、关键决策（为何选 androidapi=26、为何升 Go）、测试方式/结果、影响与回滚方案

## 依赖关系

- AND-CI-01 与 AND-CI-02 可并行实现，但建议先改脚本再改 workflow，便于定位问题。

## 风险与注意事项

- **签名密钥安全**：本次不轮换 keystore；但密钥/密码不应进入仓库与日志，且建议后续轮换。
- **平台差异**：CI 在 Ubuntu；本地脚本（尤其 `.ps1`）仅做语法校验，不保证可在缺少 Android SDK/NDK 的环境直接跑通。
