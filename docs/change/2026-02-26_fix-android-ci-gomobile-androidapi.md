# 2026-02-26 - Android：修复 CI/Release 的 gomobile Android API 不兼容

## 背景 / 目标

GitHub Actions 的 `Build AAR (gomobile)` 与 `Assemble * APK` 在 Ubuntu runner 上出现多处失败，导致：
- CI（push/PR）无法稳定产出 debug APK 与 AAR
- Release（tag）无法稳定构建并发布 release APK

本次变更目标是：在不扩展业务功能范围的前提下，修复 CI/Release 的构建链路，使两条流水线可稳定通过。

## 具体变更内容

### 修改

1) `gomobile bind`：显式指定 `-androidapi 26`（对齐 App `minSdk=26`）
- `scripts/build_aar.sh` / `scripts/build_aar.ps1`：
  - 增加 `AndroidApi` 参数（默认 26），并做基本校验（正整数且 >= 21）
  - 修复默认 `JavaPkg`：避免 Java 关键字 `native`，改为 `com.myflowhub.gomobile`
  - 日志输出增加 `AndroidApi/JavaPkg`，便于审计与定位
- `hubmobile/go.mod`：
  - 显式依赖 `golang.org/x/mobile`，避免 `gobind` 在 module 依赖中找不到 `golang.org/x/mobile/bind`
- `app/src/main/java/com/myflowhub/android/HubBridge.kt`：
  - 反射加载 gomobile 生成类时优先尝试 `com.myflowhub.gomobile.*`，并保留对旧包名的兼容回退
- `docs/m0_smoke.md`：示例 `JavaPkg` 与脚本默认值对齐

2) GitHub Actions：固定 Go 版本与 Android packages，降低不确定性
- `.github/workflows/ci.yml` / `.github/workflows/release.yml`：
  - `actions/setup-go` 固定为 `1.25.7`
  - 安装 Android packages 时包含 `platforms;android-26`（与 `-androidapi 26` 对齐）

3) Gradle Wrapper / Gradle 脚本：修复 Linux 上构建失败
- `gradlew`：修复可执行位（git mode 100755），避免 Ubuntu 上 `exit 126`
- `gradle/wrapper/gradle-wrapper.properties`：修复 `distributionUrl` 转义错误（`https\\\\://` -> `https\\://` 的正确形式）
- `gradle/wrapper/gradle-wrapper.jar`：修复缺失类导致的 `NoClassDefFoundError`
- `app/build.gradle.kts`：
  - 修复 Kotlin DSL 脚本编译错误（移除 taskGraph Closure 相关写法，改为基于 taskNames 判断）
  - 对齐 Java/Kotlin 的 target：统一为 JVM 17，避免 `Inconsistent JVM-target compatibility`

4) CI 可观测性增强
- `.github/workflows/ci.yml`：仅在失败时上传 `gradle-debug.log`，降低无效 artifact 噪音

### 新增

- `todo.md`：完整记录根因、候选方案、任务拆分、验收/回滚点，便于交接与审计

## 任务映射（todo.md）

- AND-CI-01：脚本 + go.mod + Android 反射加载对齐（androidapi/javapkg/bind）
- AND-CI-02：Actions 固定 Go 版本 + 安装 android-26 platform
- AND-CI-03：修复 gradlew 可执行与 wrapper 启动问题
- AND-CI-04：修复 `app/build.gradle.kts` Kotlin DSL 与 JVM target 不一致
- AND-CI-05：本归档文档

## 关键设计决策与权衡

1) 选择 `androidapi=26`
- 原因：当前 App `minSdk=26`，且 CI 使用 NDK r26（支持 API 21..34）。统一到 26 可减少兼容分支与验证成本。
- 影响：若未来需要覆盖更低系统版本，需同时下调 `minSdk` 并重新评估 gomobile/NDK/API 兼容策略。

2) `JavaPkg` 使用 `com.myflowhub.gomobile`
- 原因：避免 `native` 等 Java 关键字造成生成代码编译失败。
- 兼容：Android 侧保留对旧包名反射加载的 fallback，降低历史产物切换风险。

3) Gradle Wrapper 修复策略以“最小可用”优先
- 通过修复 wrapper jar / properties 与 gradlew mode，使 CI 在 Ubuntu runner 上可稳定启动与执行。

## 测试与验证方式 / 结果

- GitHub Actions（fix 分支）：
  - `ci`：已从失败恢复为成功（验证 AAR 构建 + debug APK 构建链路）。
- 本地（Windows）：
  - `gradlew.bat -v` 可正常运行，确认 wrapper 可启动。

> 说明：Release（tag）需依赖仓库 Secrets（keystore）配置；建议在合并到 `main` 后，通过推送新 tag（例如 `v0.1.1`）验证 release 流水线全链路。

## 潜在影响与回滚方案

- 影响：
  - 固定 Go 版本与 AndroidApi 后，构建更稳定，但升级依赖需显式调整版本（更可控）。
  - `gradle-wrapper.jar` 变更会影响所有开发者的 wrapper 行为（属于必要修复）。
- 回滚：
  - 逐项 revert：脚本/Go 依赖、workflow、wrapper、Gradle 脚本；若回滚 wrapper 修复，Ubuntu CI 将大概率重新失败。

