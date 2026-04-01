# Plan - Android：Hub 后台恢复与状态连续性

## Workflow Information
- Repo: `MyFlowHub-Android`
- Branch: `fix/android-hub-resilience`
- Base: `origin/main@f974fdc`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-hub-resilience`
- Current Stage: `4`

## Stage Records

### Initialization
- guide.md: `D:\project\MyFlowHub3\guide.md`
- 控制面仓：`D:\project\MyFlowHub3\repo\MyFlowHub-Android`
- 主执行仓：`D:\project\MyFlowHub3\worktrees\fix-android-hub-resilience`
- 当前 worktree 从 `origin/main@f974fdc` 建立

### Stage 1 - Requirements Analysis
#### 目标
- 提升 Android Hub 的基本健壮性，解决服务 / 进程在后台被系统重建后无法自动恢复的问题，并补齐状态连续性，让正在运行的父链自动重连结果在 Android 宿主侧可持续感知。

#### 范围
- 必须
  - 持久化 Android Hub 的 `desiredRunning` 与最近一次启动配置快照。
  - `HubService` 在 `intent == null` 重建时能自动恢复最近一次运行中的 Hub。
  - 后台周期性刷新服务状态与前台通知。
  - Hub 页面周期性刷新状态，而不是只在 bind / start / stop 时取一次。
  - 补充单元测试覆盖恢复决策与状态文案逻辑。
- 可选
  - 若本轮形成稳定排障规则，补充 lesson。
- 不做
  - 不改 Go `hubruntime` 的父链自动重连算法与 `parent.reconnect_sec`。
  - 不引入 `WorkManager`、Boot Receiver、force-stop 恢复或 OEM 保活适配。
  - 不新增 RFCOMM 配置字段。

#### 使用场景
- 用户点击 `Start` 后退到后台，系统回收 app 进程并重建前台服务，Hub 应按最近一次启动配置恢复。
- Hub 运行中父链短暂断开，由 Go runtime 自动重连；Android 通知和页面应在合理延迟内反映连接状态变化。
- 用户显式 `Stop` 后，不应因为后续服务重建而再次自动恢复。

#### 功能需求
- `ACTION_START` 必须记录最近一次启动配置快照，并持久化 `desiredRunning=true`。
- `ACTION_STOP` 必须清除 `desiredRunning`。
- `HubService` 在 `intent == null` 场景下，只有在 `desiredRunning=true` 且存在配置快照时才允许恢复。
- 恢复逻辑必须基于“最近一次启动快照”，而不是 UI 当前表单草稿。
- 周期性状态刷新必须基于现有 `bridge.status()`，不在 Android 端重复实现父链重连。
- 启动失败时不得留下会持续自动恢复的错误状态。

#### 非功能需求
- 改动面保持最小，优先修复 Android 宿主生命周期问题。
- 轮询频率保持低频，避免明显额外耗电或多余 I/O。
- 变更要可追溯、可回滚。

#### 输入输出
- 输入
  - 用户需求：Android Hub 缺少自动重连观感，后台似乎不会自动工作
  - 现状：`HubService` 返回 `START_STICKY`，但 `intent == null` 时没有恢复逻辑
  - 相关文件：
    - `app/src/main/java/com/myflowhub/android/HubService.kt`
    - `app/src/main/java/com/myflowhub/android/Prefs.kt`
    - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
    - `hubmobile/hubmobile.go`
    - `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\specs\core.md`
- 输出
  - 更新后的 Android 服务恢复 / 状态刷新实现
  - 更新后的单元测试
  - `docs/change/2026-04-01_android-hub-resilience.md`
  - `docs/lessons/*.md`（conditional）

#### 边界异常
- 服务重建但没有运行快照或 `desiredRunning=false` 时，不允许误恢复。
- 用户启动后又编辑表单但未重新点击 `Start`，恢复时必须以最近一次真实启动配置为准。
- 启动失败或旧 AAR / 权限问题导致运行态未建立时，不应留下错误恢复循环。
- 本轮不承诺覆盖设备重启、应用 force-stop、厂商后台清理等场景。

#### 验收标准
- `HubService` 在空 intent 重建时能按持久化快照自动恢复最近一次运行中的 Hub。
- 用户显式 `Stop` 后，服务不会在后续重建中自动恢复。
- 通知和 Hub 页面能在轮询周期内反映 `running` / `parentConnected` / `lastError` 的变化。
- 本地单元测试通过，且具备明确回滚路径。

#### 风险
- Android 平台上的 force-stop、设备重启、OEM 杀后台仍可能导致服务无法恢复，本轮不处理。
- 若把“当前表单”误当作“最近一次运行配置”，会造成恢复语义漂移；因此必须单独持久化启动快照。

#### Issue List
- 无

### Stage 2 - Architecture Design
#### 总体方案
- 采用“修复 Android 宿主生命周期，而不重复实现 Go 重连”的方案：
  - `Prefs` 额外持久化 `desiredRunning` 与最近一次启动配置快照。
  - `HubService` 统一负责启动、停止、空 intent 恢复、状态轮询与通知更新。
  - `HubScreen` 负责发起 Start / Stop，并在前台低频轮询 bound service 的状态用于展示。
- 选型理由：
  - Go runtime 已具备父链自动重连，本轮问题主要在 Android 宿主没有把运行态跨服务重建延续下来。
  - 将“最近一次运行配置”与“表单草稿配置”分离，是最小且语义正确的恢复方式。

#### 备选对比
- 方案 A：只改成 `START_REDELIVER_INTENT`
  - 优点：实现简单
  - 缺点：仍不显式表达“最近一次运行配置”和“显式 Stop 后不恢复”的边界
- 方案 B：在 Android 侧自行实现父链重连
  - 优点：可更主动控制重连表现
  - 缺点：与 Go runtime 重复，容易形成双状态机
- 方案 C：引入 `WorkManager` / Boot Receiver / Alarm 自愈
  - 优点：覆盖更多后台场景
  - 缺点：超出本轮最小修复范围，复杂度显著上升
- 采用方案：持久化运行快照 + 服务恢复 + 状态轮询

#### 模块职责
- `Prefs.kt`
  - 持久化最近一次启动快照与 `desiredRunning`
- `HubService.kt`
  - 处理 `ACTION_START` / `ACTION_STOP`
  - 处理 `intent == null` 的恢复
  - 维护后台状态轮询与通知更新
- `HubScreen.kt`
  - 保持现有启动 / 停止入口
  - 增加前台状态轮询
- `HubServiceSupport.kt`（如需新增）
  - 承载可单测的恢复与通知纯逻辑

#### 数据 / 调用流
1. 用户在 Hub 页面点击 `Start`。
2. `HubScreen` 发送 `ACTION_START` 给 `HubService`。
3. `HubService` 归一化配置并持久化“运行快照 + desiredRunning=true”。
4. `HubService` 调用 `bridge.start(cfg)` 启动 Go runtime。
5. `HubService` 启动周期性状态轮询，用 `bridge.status()` 刷新 `state` 与前台通知。
6. 若服务被系统以 `intent == null` 重建，`HubService` 从 `Prefs` 读取运行快照；只有在 `desiredRunning=true` 时才自动恢复。
7. 用户点击 `Stop` 后，`HubService` 清除 `desiredRunning`、停止 runtime、移除通知并停止自身。
8. `HubScreen` 在 bound service 存在时低频轮询 `getState()`，同步展示最新状态。

#### 接口草案
- `Prefs.saveHubRunSnapshot(context, cfg)`
- `Prefs.loadHubRunSnapshot(context): HubConfig?`
- `Prefs.setHubDesiredRunning(context, desired: Boolean)`
- `Prefs.isHubDesiredRunning(context): Boolean`
- 纯逻辑 helper：
  - 恢复配置决策
  - 通知文本渲染

#### 错误与安全
- 恢复前必须检查运行快照是否存在，缺失时直接跳过恢复。
- 启动失败时清除 `desiredRunning`，避免服务重建后反复进入无意义恢复。
- 状态轮询失败时要保留可诊断错误，不得静默吞掉。

#### 性能与测试策略
- 服务与 UI 都采用秒级低频轮询，避免过高后台开销。
- 通过提取纯逻辑 helper，让关键恢复 / 展示规则可用本地 JUnit 覆盖。
- 验证方式：
  - `.\gradlew.bat testDebugUnitTest`
  - 审阅 `HubService` 在空 intent、显式 Stop、启动失败场景下的状态机

#### 可扩展性设计点
- 后续若需要开机恢复或更强保活，可复用同一套运行快照与恢复 helper。
- 若未来需要暴露 `parent.reconnect_sec`，可在不改恢复骨架的情况下扩展到 `HubConfig` / `Prefs`。

#### Issue List
- 无

### Stage 3.1 - Planning
#### Project Goal and Current State
- 当前 Android Hub 的后台问题主要不在 Go runtime，而在 Android 服务生命周期：
  - `HubService.onStartCommand()` 返回 `START_STICKY`
  - `intent == null` 时直接 `no-op`
  - 结果是系统重建服务后不会恢复最近一次正在运行的 Hub
- 同时：
  - UI 只在 bind / start / stop 时短暂轮询状态
  - 前台通知只显示一次性静态文本，无法持续反映连接状态
- Server 侧 `hubruntime` 已有父链自动重连：
  - `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\specs\core.md`
  - 本轮不在 Android 端重复实现

#### Docs Governance Routing Decision
- 使用 `$m-docs` 校验计划文档路由、requirements/specs 影响和 lessons 查询入口。
- Requirements impact: `none`
- Specs impact: `none`
- Related requirements: `none`
- Related specs:
  - `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\specs\core.md`
- Related lessons: `none`（结束时再决定是否新增）
- Related changes:
  - `docs/change/2026-02-25_android-hub-m0.md`
  - `docs/change/2026-02-27_android-fgs-type-gomobile-reflect.md`
  - `docs/change/2026-03-31_android-rfcomm-basic-usability.md`
  - `docs/change/2026-03-31_android-rfcomm-listener-config.md`
- 文档路由：
  - 当前 workflow 控制文档位于 worktree 根 `plan.md`
  - 旧 `plan.md` 已归档到 `docs/plan_archive/plan_archive_2026-04-01_android-release-checkout-deps-prev.md`
  - 完成结果归档到 `docs/change/2026-04-01_android-hub-resilience.md`
  - 若沉淀出稳定排障规则，再更新 `docs/lessons`

#### Executable Task List
- [x] `ANDHUBRES-1`：归档旧控制文档并建立本轮 `plan.md`
- [x] `ANDHUBRES-2`：持久化运行快照与 `desiredRunning`，补齐空 intent 恢复
- [x] `ANDHUBRES-3`：补齐服务侧状态轮询与通知刷新
- [x] `ANDHUBRES-4`：补齐 Hub 页面状态轮询与展示连续性
- [x] `ANDHUBRES-5`：补充单元测试并完成本地验证
- [x] `ANDHUBRES-6`：完成 3.3 自审与 4 阶段归档

#### Task Details
##### ANDHUBRES-1 - 控制文档切换
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-hub-resilience`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-hub-resilience\plan.md`
- Goal: 归档遗留的 release workflow 计划，并建立本轮 Android 健壮性修复的控制文档。
- Files / Modules:
  - `plan.md`
  - `docs/plan_archive/plan_archive_2026-04-01_android-release-checkout-deps-prev.md`
- Acceptance:
  - 旧计划已归档
  - 新 `plan.md` 完整记录 stage 1 / 2 / 3.1
- Test Points:
  - 归档文件存在且可读
- Rollback:
  - 还原 `plan.md` 并删除本轮新增 archive

##### ANDHUBRES-2 - 运行快照与恢复
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-hub-resilience`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-hub-resilience\plan.md`
- Goal: 为服务恢复提供明确的持久化运行语义，而不是依赖 UI 当前表单状态。
- Files / Modules:
  - `app/src/main/java/com/myflowhub/android/Prefs.kt`
  - `app/src/main/java/com/myflowhub/android/HubService.kt`
  - `app/src/main/java/com/myflowhub/android/HubServiceSupport.kt`（conditional）
- Acceptance:
  - `ACTION_START` 持久化最近一次启动快照与 `desiredRunning=true`
  - `ACTION_STOP` 清除 `desiredRunning`
  - `intent == null` 时按快照恢复，且只在 `desiredRunning=true` 时恢复
  - 启动失败不会留下错误的自动恢复状态
- Test Points:
  - 纯逻辑单测覆盖恢复决策
  - 代码审阅恢复路径
- Rollback:
  - 回退上述文件到当前主线版本

##### ANDHUBRES-3 - 服务侧状态连续性
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-hub-resilience`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-hub-resilience\plan.md`
- Goal: 让前台通知和服务内部状态随 runtime 变化持续刷新，而不是只在启动瞬间固定文本。
- Files / Modules:
  - `app/src/main/java/com/myflowhub/android/HubService.kt`
  - `app/src/main/java/com/myflowhub/android/HubServiceSupport.kt`（conditional）
- Acceptance:
  - 服务在运行时低频轮询 `bridge.status()`
  - 前台通知能显示当前运行 / 父链 / 错误概况
  - 服务销毁时能正确停止轮询
- Test Points:
  - 纯逻辑单测覆盖通知文本
  - 代码审阅轮询生命周期
- Rollback:
  - 回退服务与 helper 相关改动

##### ANDHUBRES-4 - Hub UI 状态刷新
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-hub-resilience`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-hub-resilience\plan.md`
- Goal: 让用户在前台页面也能看到持续变化的 Hub 状态，而不是一次性快照。
- Files / Modules:
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
- Acceptance:
  - bound service 存在时，Hub 页面会低频刷新状态
  - 不破坏现有 Start / Stop 交互与权限校验
- Test Points:
  - 代码审阅 Compose 生命周期与轮询退出条件
- Rollback:
  - 回退 `HubScreen.kt`

##### ANDHUBRES-5 - 测试与验证
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-hub-resilience`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-hub-resilience\plan.md`
- Goal: 为本轮恢复与状态连续性逻辑提供可重复的本地验证。
- Files / Modules:
  - `app/src/test/java/com/myflowhub/android/HubServiceSupportTest.kt`（conditional）
  - `plan.md`
- Acceptance:
  - 单测覆盖恢复与通知核心规则
  - `.\gradlew.bat testDebugUnitTest` 通过
- Test Points:
  - `.\gradlew.bat testDebugUnitTest`
  - `git diff --check`
- Rollback:
  - 回退测试文件与相关实现

##### ANDHUBRES-6 - 自审与归档
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-hub-resilience`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-hub-resilience\plan.md`
- Goal: 确保本轮实现、验证和可复用经验可审计。
- Files / Modules:
  - `plan.md`
  - `docs/change/2026-04-01_android-hub-resilience.md`
  - `docs/lessons/*.md`（conditional）
  - `docs/lessons/README.md`（conditional）
- Acceptance:
  - 3.3 checklist 完整
  - `docs/change` 记录背景、实现、验证、回滚和 lesson impact
  - 若新增 lesson，同步更新 `docs/lessons/README.md`
- Test Points:
  - `git diff --check`
  - `git status --short`
- Rollback:
  - 删除本轮 archive / lesson 并回退实现文件

#### Dependencies
- Android foreground service 生命周期与通知能力
- `HubBridge.start/stop/status`
- `Prefs.load/save`
- `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\specs\core.md` 中已有的父链自动重连语义

#### Risks and Notes
- 本轮聚焦“服务 / 进程重建后的恢复”和“状态连续性”，不承诺安卓平台意义上的强保活。
- `hubmobile.Start()` 在 runtime 已存在时会返回当前状态，因此恢复路径即使遇到重复 start，也不会创建双实例。
- UI 当前会把 Hub 表单持续保存到 `Prefs`；因此本轮必须额外引入“运行快照”，避免恢复语义与表单草稿混淆。

#### Parallelism Assessment
- 本轮写集集中在 `HubService`、`Prefs`、`HubScreen` 和测试，状态逻辑高度耦合，不适合并行派发。
- 子Agent：不使用。

#### Issue List
- 无

### Stage 3.2 - Implementation
- `ANDHUBRES-2`
  - `Prefs.kt` 新增运行快照和 `desiredRunning` 持久化接口。
  - `HubService.kt` 改为在 `ACTION_START` 时写入最近一次启动快照，在 `ACTION_STOP` 时清理 `desiredRunning`。
  - `HubService.kt` 在 `intent == null` 场景下按持久化快照恢复，并在快照损坏或缺失时显式清理恢复状态。
- `ANDHUBRES-3`
  - 新增 `HubServiceSupport.kt`，抽出运行配置归一化、恢复决策和通知文本逻辑。
  - `HubService.kt` 增加后台状态轮询，按 `bridge.status()` 更新 `state` 和前台通知。
- `ANDHUBRES-4`
  - `HubScreen.kt` 增加 bound service 存在时的前台状态轮询，使页面状态能持续刷新。
- `ANDHUBRES-5`
  - 新增 `HubServiceSupportTest.kt`，覆盖恢复和通知文本核心规则。
  - 使用 `ANDROID_HOME=D:\project\MyFlowHub3\_android-sdk`、`ANDROID_SDK_ROOT=D:\project\MyFlowHub3\_android-sdk` 执行 `.\gradlew.bat testDebugUnitTest` 通过。

### Stage 3.3 - Code Review
- 需求覆盖：通过
  - 已覆盖服务恢复、显式 Stop 不自动恢复、状态连续性和单测验证。
- 架构合理性：通过
  - Android 侧只修宿主生命周期，不重复实现 Go runtime 的父链重连。
- 性能风险（N+1 / 重复计算 / 多余 I/O / 锁竞争）：通过
  - 状态轮询保持秒级低频，通知只在状态变化时更新。
- 可读性与一致性：通过
  - 运行快照与表单配置分离，职责边界清晰。
- 可扩展性与配置化：通过
  - 后续若需要 boot restore 或更多后台策略，可复用同一套快照 / 恢复 helper。
- 稳定性与安全：通过
  - 恢复前检查快照；启动失败和恢复失败都会清理 `desiredRunning`，避免错误循环。
- 测试覆盖情况：通过
  - 本地 JUnit 已覆盖恢复与通知逻辑；`testDebugUnitTest` 通过。
  - 残余风险：当前环境未做真机进程回收场景验证。
- 子Agent治理与审计（任务映射、上下文完整性、文件所有权、结果复核、冲突处理、记录完整性）：通过
  - 未使用子 Agent。

### Stage 4 - Change Archive
- 使用 `$m-docs` 完成变更归档与 lesson 路由校验。
- `docs/change/2026-04-01_android-hub-resilience.md`：已创建
- `docs/lessons/android-hub-service-restart.md`：已创建
- `docs/lessons/README.md`：已更新
- Requirements impact: `none`
- Specs impact: `none`
- Lessons impact: `updated`

阻塞：否
等待用户确认是否结束本轮 workflow
