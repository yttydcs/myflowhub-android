# Plan - Android：Windows AAR 构建闭环 v1

## Workflow Information
- Repo: `MyFlowHub-Android`
- Branch: `feat/android-file-pull-v1`
- Base: `main@77c6d77`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1`
- Current Stage: `4`

## Stage Records

### Initialization
- guide.md: `not found`
- 控制面仓：`D:\project\MyFlowHub3\repo\MyFlowHub-Android`
- 主执行仓：`D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1`
- 当前 worktree 从 `main@77c6d77` 建立
- 已归档上一轮计划：
  - `docs/plan_archive/plan_archive_2026-04-03_android-file-offer-upload-v1-prev.md`

### Stage 1 - Requirements Analysis
#### 目标
- 让 Android File 现有源码能力更接近“可真正装包验证”的状态，优先补齐 Windows 本地 `build_aar.ps1` 的可用性：正确解析 SDK/NDK 与 gomobile 工具链，失败时显式退出，不再出现 AAR 未生成却打印 `Done` 的假成功。

#### 范围
- 必须
  - 对齐 Windows `scripts/build_aar.ps1` 与现有 `scripts/build_aar.sh` / CI 的关键行为：解析 `ANDROID_HOME` / `ANDROID_SDK_ROOT`，自动探测已安装 NDK，按 `hubmobile/go.mod` 版本安装 `gomobile/gobind`。
  - 修复 PowerShell 下 native command 失败未中断的问题，确保 `gomobile init` / `gomobile bind` 非零退出码会使脚本失败。
  - 在脚本成功路径上校验 `app/libs/myflowhub.aar` 真实产出，避免“命令跑完但产物不存在”。
  - 在当前机器上重新验证 AAR 构建链路；若环境仍缺 NDK 或 cmdline-tools，必须明确记录阻塞点和下一步。
- 可选
  - 若本机能在不扩大仓库改动面的前提下补齐 NDK，再次执行构建并验证 `assembleDebug` 能把新 AAR 打进 APK。
- 不做
  - 不改 Android File 协议语义、UI 流程或 Go `offer/pull` 逻辑。
  - 不在仓库脚本里自动下载超大 NDK 包，也不把机器私有路径硬编码进脚本。
  - 不修改 CI Bash 脚本，除非确认 Windows 脚本修复后仍存在必须同步的行为差异。

#### 使用场景
- 开发者在 Windows 上修改 `hubmobile` 导出后，需要本地重建 `app/libs/myflowhub.aar`，让 APK 真正带上新的 Go 能力。
- 当前 Android File 已补齐 `pull/offer` 代码，但由于 AAR 没有产出，设备上仍无法验证新导出方法。
- 当本机 SDK/NDK 不完整时，开发者需要第一时间知道缺的是什么，而不是误以为构建已经成功。

#### 功能需求
- `build_aar.ps1` 必须优先复用现有环境变量；若仅设置了 `ANDROID_SDK_ROOT`，脚本应自动补齐 `ANDROID_HOME`。
- 当 `ANDROID_NDK_HOME` 未设置时，脚本必须尝试从 `ANDROID_SDK_ROOT/ndk/*` 中探测最新已安装 NDK；若仍找不到，显式失败并给出清晰提示。
- 当 `gomobile` 或 `gobind` 缺失时，脚本必须优先按 `hubmobile/go.mod` 中的 `golang.org/x/mobile` 版本安装，不直接默认 `@latest`。
- `gomobile init`、`gomobile bind`、`go install` 等 native command 失败时，脚本必须立即失败，不能继续打印成功信息。
- 只有当目标 AAR 文件真实存在时，脚本才能打印成功。

#### 非功能需求
- 保持现有脚本参数接口不变：`-Target`、`-AndroidApi`、`-JavaPkg`、`-OutFile`。
- 改动尽量收敛在 Windows 脚本与必要文档，不扩散到 app 运行时代码。
- 错误信息应足够明确，能直接指导本地排障。
- 不引入环境相关硬编码，不依赖特定用户目录。

#### 输入输出
- 输入
  - 用户需求：继续推进 Android File 的基本可用性
  - 当前现状：
    - Android File 的 `list/read_text/mkdir/pull/offer` 已完成源码级接线
    - `app/build.gradle.kts` 只有在 `app/libs/myflowhub.aar` 存在时才会把 Go 运行时打进 APK
    - 当前机器 `C:\Users\HelloWorld\AppData\Local\Android\Sdk` 下没有 `ndk/` 目录
    - 当前 `scripts/build_aar.ps1` 在 `gomobile` 失败时仍可能打印 `Done`
- 输出
  - 更健壮的 Windows `build_aar.ps1`
  - 明确的本地验证结果：AAR 已产出，或被环境阻塞且阻塞点明确
  - 对应计划、自审与归档文档

#### 边界异常
- `ANDROID_API` 非法或过低时，必须在真正调用 `gomobile` 前失败。
- `ANDROID_SDK_ROOT` / `ANDROID_HOME` 缺失或无效时，必须显式失败。
- `gomobile` / `gobind` 安装失败时，必须显式失败。
- `gomobile bind` 返回非零退出码或未生成目标 AAR 时，必须显式失败。
- 本机若缺少 NDK 或 `sdkmanager`，本轮可以记录为环境阻塞，但不能把阻塞伪装成成功。

#### 验收标准
- `.\\scripts\\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar` 在当前机器上不会再出现“无 AAR 产物但打印 Done”的情况。
- 若本机存在可用 NDK，脚本执行后 `app/libs/myflowhub.aar` 存在。
- 若本机仍缺 NDK，脚本输出会明确指出缺失项并以失败结束。
- 在 AAR 成功产出的情况下，`.\\gradlew.bat :app:assembleDebug` 通过。

#### 风险
- 本机当前缺少 NDK，可能导致本轮只能做到“失败显式化”，不能直接产出 AAR。
- PowerShell 对 native command 的错误传播与 Bash 不同，若处理不完整仍可能留下灰区。
- `hubmobile/go.mod` 的本地 replace 继续依赖工作区目录拓扑，若外部依赖仓状态漂移，AAR 构建仍可能被其它问题阻塞。

#### Issue List
- 无

### Stage 2 - Architecture Design
#### 总体方案
- 采用“让 `scripts/build_aar.ps1` 与已验证过的 `scripts/build_aar.sh` 行为对齐”的方案，不改 app runtime 与 `hubmobile` 业务逻辑。
- 重点补齐三类缺口：环境变量与 NDK 探测、`gomobile/gobind` 版本固定安装、PowerShell native command 失败传播。

#### 选型理由 / 备选对比
- 方案 A：只在文档里补充“需要先装 NDK”
  - 优点：改动最小
  - 缺点：不能解决当前脚本的假成功，仍然不利于本地排障
- 方案 B：在 PowerShell 脚本中自动下载并安装 NDK
  - 优点：理论上最省手工操作
  - 缺点：副作用大、下载体积大、环境耦合重，不适合默认执行
- 采用方案：脚本显式校验 + 自动探测已安装环境
  - 原因：最小改动即可显著提升 Windows 本地可用性，并保持与 CI/Bash 一致

#### 模块职责
- `scripts/build_aar.ps1`
  - 负责解析本地 Android SDK/NDK 环境
  - 负责按模块版本安装 `gomobile/gobind`
  - 负责调用 `gomobile init/bind` 并严格传播失败
- `docs/m0_smoke.md`（如需）
  - 若脚本行为或排障入口发生变化，补充本地验证说明

#### 数据 / 调用流
1. 脚本读取参数并校验 `AndroidApi`。
2. 脚本归一化 `ANDROID_HOME` / `ANDROID_SDK_ROOT`，自动探测可用 NDK。
3. 脚本解析 `hubmobile/go.mod` 中的 `golang.org/x/mobile` 版本，并在缺工具时安装 `gomobile/gobind`。
4. 脚本执行 `gomobile init`。
5. 脚本进入 `hubmobile` 执行 `gomobile bind`。
6. 脚本确认目标 AAR 真实存在后才报告成功。

#### 接口草案
- `Ensure-GoBinInPath`
- `Resolve-XMobileVersion`
- `Install-GomobileTools`
- `Resolve-AndroidSdkRoot`
- `Resolve-AndroidNdkHome`
- `Invoke-NativeChecked`

#### 错误与安全
- 对环境变量、目录存在性和工具缺失都做显式检查。
- 不在脚本里写入用户专属固定路径，只消费调用方已有环境或标准 SDK 目录结构。
- 对 native command 的返回码做统一检查，避免 PowerShell 静默继续。

#### 性能与测试策略
- 仅当 `gomobile` / `gobind` 缺失时才安装，避免重复下载。
- 优先复用已安装的最新 NDK 目录，不做额外扫描以外的重操作。
- 验证重点：
  - 无 NDK 环境下的失败路径是否清晰
  - 有效环境下是否能真实生成 AAR
  - `assembleDebug` 是否仍正常

#### 可扩展性设计点
- 未来若要支持显式 `-AndroidSdkRoot` / `-AndroidNdkHome` 参数，可在现有 helper 基础上继续扩展。
- Windows 脚本与 Bash 脚本对齐后，后续若再调整 gomobile 工具链策略，可双端同步演进。

#### Issue List
- 无

### Stage 3.1 - Planning
#### Project Goal and Current State
- 当前 Android File 业务能力已经在源码层具备基础可用性，但设备运行时仍未闭环，因为 `app/libs/myflowhub.aar` 尚未重建。
- `scripts/build_aar.sh` 已具备较完整的 SDK/NDK 与 `gomobile/gobind` 处理逻辑；`scripts/build_aar.ps1` 仍停留在较早版本，且未严格检查 native command 失败。
- 当前机器 Android SDK 根目录存在，但没有安装 `ndk/` 子目录，因此本轮需要同时修脚本和重新验证环境结果。

#### Docs Governance Routing Decision
- 使用 `$m-docs` 校验计划文档路由、requirements/specs 影响和 lessons 查询入口。
- Requirements impact: `none`
- Specs impact: `none`
- Related requirements: `none`
- Related specs: `none`
- Related lessons:
  - `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\docs\lessons\android-hubmobile-local-replace.md`
- Related changes:
  - `docs/change/2026-03-04_android-release-gomobile-pin.md`
  - `docs/change/2026-04-03_android-file-offer-upload-v1.md`

#### Executable Task List
- [x] `ANDAAR-1`：归档上一轮 `plan.md` 并建立本轮控制文档
- [x] `ANDAAR-2`：补齐 Windows `build_aar.ps1` 的环境解析、工具安装与失败传播
- [x] `ANDAAR-3`：执行 AAR / APK 验证，确认当前机器的真实构建结果
- [x] `ANDAAR-4`：完成 3.3 自审与 4 阶段归档

#### Task Details
##### ANDAAR-1 - 控制文档切换
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\plan.md`
- Goal: 归档上一轮 `offer/upload v1` 计划，并建立本轮 AAR 构建闭环控制文档。
- Files / Modules:
  - `plan.md`
  - `docs/plan_archive/plan_archive_2026-04-03_android-file-offer-upload-v1-prev.md`
- Write Set:
  - `plan.md`
  - `docs/plan_archive/plan_archive_2026-04-03_android-file-offer-upload-v1-prev.md`
- Acceptance:
  - 上一轮计划已归档
  - 新计划完整记录 stage 1 / 2 / 3.1
- Test Points:
  - 归档文件存在且可读
- Rollback:
  - 删除新 archive，恢复旧 `plan.md`

##### ANDAAR-2 - Windows AAR 构建脚本
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\plan.md`
- Goal: 让 Windows `build_aar.ps1` 与当前 Bash/CI 行为对齐，并显式处理失败。
- Files / Modules:
  - `scripts/build_aar.ps1`
  - `docs/m0_smoke.md`（如需）
- Write Set:
  - `scripts/build_aar.ps1`
  - `docs/m0_smoke.md`（如需）
- Acceptance:
  - 能自动解析 `ANDROID_SDK_ROOT` / `ANDROID_HOME`
  - 能探测已安装 NDK 并安装缺失的 `gomobile/gobind`
  - 任一 native command 失败都会中断脚本
  - 只有真实生成 AAR 才会打印成功
- Test Points:
  - `.\\scripts\\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar`
- Rollback:
  - 回退 PowerShell 脚本及相关文档改动

##### ANDAAR-3 - 本地验证
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\plan.md`
- Goal: 锁定当前 Windows 机器上的 AAR 构建真实状态，并在环境允许时继续验证 APK。
- Files / Modules:
  - `scripts/build_aar.ps1`
  - `app/libs/myflowhub.aar`
- Write Set:
  - `app/libs/myflowhub.aar`（若成功生成）
- Acceptance:
  - 当前机器上的失败或成功状态可复现、可解释
  - 若 AAR 成功生成，则 `assembleDebug` 通过
- Test Points:
  - `.\\scripts\\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar`
  - `.\\gradlew.bat :app:assembleDebug`
- Rollback:
  - 删除新生成的 `app/libs/myflowhub.aar` 并回退脚本改动

#### Dependencies, Risks, and Notes
- Dependencies:
  - Android SDK / NDK
  - `golang.org/x/mobile`
  - `hubmobile/go.mod`
- Risks:
  - 当前机器缺少 NDK，可能导致本轮验证停留在失败路径
  - 若 `hubmobile` 外部 replace 依赖漂移，AAR 构建可能暴露新的非本轮问题
- Notes:
  - 不使用子 Agent
  - 若本轮需要新增复用性较强的排障经验，将在 Stage 4 同步写入 `docs/lessons`

#### Parallelism Assessment
- 不使用子 Agent。
- 原因：
  - 当前改动面很小，核心风险集中在同一条 Windows 构建链路上
  - 计划、脚本、环境验证和归档需要同一上下文收敛

阻塞：否
进入 3.2

### Stage 3.2 - Implementation Result
#### ANDAAR-2
- `scripts/build_aar.ps1`
  - 新增 SDK/NDK 自动探测与标准 SDK 目录 fallback
  - 新增按 `hubmobile/go.mod` 版本安装 `gomobile/gobind`
  - 新增 native command 退出码检查，修复 PowerShell 下 `gomobile` 失败仍继续的问题
  - 成功路径改为必须检查目标 AAR 文件真实存在
- `docs/m0_smoke.md`
  - 补充 Windows 本地 AAR 构建前置说明

#### ANDAAR-3
- 本机环境验证：
  - 初次执行 `.\\scripts\\build_aar.ps1 ...`
    - 明确失败为缺少 `ndk;26.1.10909125`
  - 已在本机安装 Android command-line tools 与 `ndk;26.1.10909125`
  - 再次执行 `.\\scripts\\build_aar.ps1 ...`
    - 明确失败在 `gomobile bind`
    - 暴露 `worktrees\\MyFlowHub-Server\\modules\\defaultset` 的 API 漂移，而不是伪装成功
  - `ANDROID_HOME=C:\\Users\\HelloWorld\\AppData\\Local\\Android\\Sdk`
  - `ANDROID_SDK_ROOT=C:\\Users\\HelloWorld\\AppData\\Local\\Android\\Sdk`
  - `.\\gradlew.bat :app:assembleDebug`
    - 通过
- 探索性尝试：
  - 试过临时去掉本地 replace 再执行 `gomobile bind`
  - 当前会触发额外的 `go mod tidy failed: missing module declaration`
  - 因行为尚不稳定，本轮未把该 fallback 固化进仓库脚本

### Stage 3.3 - Code Review
- 需求覆盖：`通过`
  - 已修复 Windows AAR 构建的假成功问题，并明确暴露当前真实阻塞
- 架构合理性：`通过`
  - 方案收敛在 `build_aar.ps1` 与文档，没有扩散到 app / hubmobile 运行时代码
- 性能风险（N+1 / 重复计算 / 多余 I/O / 锁竞争）：`通过`
  - 仅在缺工具时安装；目录探测和路径校验开销很小
- 可读性与一致性：`通过`
  - PowerShell helper 命名与现有 Bash 脚本职责对应，错误路径清晰
- 可扩展性与配置化：`通过`
  - 后续可继续扩显式 SDK/NDK 参数，不影响当前接口
- 稳定性与安全：`通过`
  - 关键 native command 现在都会显式失败，不再静默继续
- 测试覆盖情况：`通过（带外部阻塞注记）`
  - 已验证缺 NDK 和外部依赖漂移两段失败路径
  - `assembleDebug` 通过
  - AAR 最终成功产出仍受外部 `MyFlowHub-Server` 漂移阻塞
- 子Agent治理与审计（任务映射、上下文完整性、文件所有权、结果复核、冲突处理、记录完整性）：`通过`
  - 本轮未使用子 Agent

### Stage 4 - Change Archive
- 使用 `$m-docs` 完成变更归档与 lessons 检查
- Change archive:
  - `docs/change/2026-04-03_android-windows-aar-build-closure-v1.md`
- Lessons:
  - `docs/lessons/android-build-aar-windows.md`
  - `docs/lessons/README.md`
- Requirements impact: `none`
- Specs impact: `none`
- Lessons impact: `updated`

阻塞：否
等待用户确认是否结束本轮 workflow
