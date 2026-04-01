# Plan - Android：RFCOMM listener 配置化

## Workflow Information
- Repo: `MyFlowHub-Android`
- Branch: `feat/android-rfcomm-listen`
- Base: `main`（本地 `main` 已包含 `1df0828 fix: 补齐 Android RFCOMM 基本可用性`）
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Android`
- Current Stage: `4`

## Stage Records

### Initialization
- guide.md: `D:\project\MyFlowHub3\guide.md`，已读取；commit 信息需使用中文；所有 worktree 必须位于 `D:\project\MyFlowHub3\worktrees\`。
- base/worktree confirmation:
  - Android 主执行仓：`D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Android`
  - 依赖仓（仅用于 `hubmobile/go.mod` 的相对 `replace`，本轮不计划改代码）：
    - `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Server`
    - `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-SDK`
    - `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Proto`
  - 本 workflow 的实现代码仅允许写入 Android worktree；Server / SDK 仅作为构建依赖，除非主计划扩展，否则禁止顺手修改。
- cross-repo dependency records:
  - `MyFlowHub-Server`
    - Branch: `chore/android-rfcomm-listen-deps`
    - Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Server`
    - Control doc: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Server\todo.md`
    - Ownership boundary: 仅为 `hubmobile` 编译提供本地 `replace` 目标，不做实现变更
  - `MyFlowHub-SDK`
    - Branch: `chore/android-rfcomm-listen-deps`
    - Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-SDK`
    - Control doc: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-SDK\todo.md`
    - Ownership boundary: 仅为 `hubmobile` 编译提供本地 `replace` 目标，不做实现变更
  - `MyFlowHub-Proto`
    - Branch: `chore/android-rfcomm-listen-deps`
    - Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Proto`
    - Control doc: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Proto\todo.md`
    - Ownership boundary: 仅为本地验证提供未发布的 `protocol/stream` 包，不做实现变更

### Stage 1 - Requirements Analysis
#### Goal
- 让 Android Hub 将已有的 RFCOMM listener 底层能力产品化为“可配置、可启动、可诊断”的最小可用路径，使用户可以在保留 TCP listener 的同时，可选启用 RFCOMM listener。

#### Scope
- 必须
  - 在 Android Hub 配置模型中新增 RFCOMM listener 配置位，并持久化到 `Prefs`。
  - 在 Hub UI 暴露最小必要配置：`RFCOMMEnable`、`RFCOMMUUID`、`RFCOMMInsecure`。
  - 在启动 Hub 前，对“RFCOMM parent”或“启用 RFCOMM listener”的场景统一做蓝牙权限前置检查。
  - 将新配置从 `HubScreen -> HubService -> HubBridge -> hubmobile.Start -> hubruntime.Options` 全链路传递。
  - 对旧 AAR / 旧 `Start(...)` 签名给出显式兼容或错误提示，避免无声失效。
  - 重建 `app/libs/myflowhub.aar` 并完成最小测试 / 构建回归。
- 可选
  - 在 UI 上增加轻量配置说明或默认值提示。
  - 在状态区展示配置层面的 RFCOMM listener 信息（仅在低成本时考虑）。
- 不做
  - 不扩展 `RFCOMMChannel`、`RFCOMMAdapter` 等高级配置。
  - 不改 Server / SDK / Core 的 RFCOMM 行为。
  - 不新增 BLE/GATT、扫描、配对引导。
  - 不改变当前 parent self-register 仅支持 TCP endpoint 的既有限制。
  - 不把运行态 listener 观测面扩展成完整状态面板，除非实现最小可用所必需。

#### Use Cases
- 用户继续使用默认 TCP listener，仅填写 `:9000`，行为保持不变。
- 用户在 Hub 页面开启 RFCOMM listener，使用默认或自定义 UUID，并仍保留 TCP listener。
- 用户同时配置 RFCOMM parent endpoint 与 RFCOMM listener，启动前只触发一次蓝牙权限检查。
- 用户在旧 AAR 仍未更新时尝试启用 RFCOMM listener，App 会明确提示需要更新本地 AAR，而不是悄悄退化。

#### Functional Requirements
- `HubConfig` 必须包含 RFCOMM listener 开关、UUID、insecure 选项，并提供合理默认值。
- `Prefs.load/save` 必须持久化上述字段，且对未升级用户保持兼容默认值。
- `HubScreen` 必须允许用户启停 RFCOMM listener，并编辑 UUID。
- 若启用 RFCOMM listener 且蓝牙权限缺失，Start 不得继续执行底层启动逻辑。
- RFCOMM UUID 输入非法时，UI 必须在启动前显式报错。
- `HubService` 必须通过 extras 传递新增字段。
- `HubBridge` 必须优先调用新签名的 `Start(...)`；若仅存在旧签名，则在未启用 RFCOMM listener 时保持兼容，在启用时返回明确错误。
- `hubmobile.Start(...)` 必须把 RFCOMM listener 配置写入 `hubruntime.Options`。

#### Non-functional Requirements
- 保持 TCP-only 路径不受影响。
- 默认安全值应与 Server 保持一致：RFCOMM listener 默认关闭，启用时 `insecure=false`。
- 改动面保持最小，不引入新的三方库或额外后台流程。
- 失败路径必须显式、可诊断，不允许静默回退。

#### Inputs / Outputs
- 输入
  - `HubConfig.addr`
  - `HubConfig.parentAddr`
  - `HubConfig.rfcommListenEnabled`
  - `HubConfig.rfcommServiceUuid`
  - `HubConfig.rfcommInsecure`
  - Android 蓝牙权限授予结果
  - 本地 AAR 中 `Hubmobile.Start` 的实际签名
- 输出
  - 成功时：Hub 以 TCP-only 或 TCP+RFCOMM listener 启动
  - 失败时：UI / HubState 返回明确错误（权限缺失、UUID 非法、AAR 过旧等）

#### Edge Cases
- 用户启用 RFCOMM listener，但 UUID 为空或格式非法。
- 用户只启用 TCP listener，不应被 RFCOMM 权限逻辑打扰。
- 用户同时配置 RFCOMM parent 与 listener，权限缺失时只报一次明确前置错误。
- 本地 `app/libs/myflowhub.aar` 仍为旧版本，不包含新 `Start(...)` 签名。
- 蓝牙权限已授予但系统蓝牙未开启，此类错误应继续由底层 provider / runtime 返回明确诊断。

#### Acceptance Criteria
- Hub 页面可以开启 / 关闭 RFCOMM listener，并编辑 UUID / insecure 选项。
- `Prefs` 能在页面重开后恢复上述配置。
- 启动时可将 RFCOMM listener 配置正确传到 `hubruntime.Options.RFCOMMEnable/RFCOMMUUID/RFCOMMInsecure`。
- RFCOMM listener 启用且权限缺失时，Start 会被阻止并提示授权。
- 旧 AAR 场景下不会悄悄忽略 listener 配置，而是给出明确错误。
- 自动验证至少覆盖新增 helper / bridge 逻辑；并完成 AAR 重建与 `:app:assembleDebug`。

#### Risks
- `hubmobile` 改签名后，如果本地 AAR 未重建，Android 反射调用会出现方法不匹配。
- RFCOMM UUID 默认值如果在多处硬编码，后续与 Server 漂移会产生隐性兼容风险；应集中在 helper/配置层管理。
- `hubmobile/go.mod` 依赖相对 `replace`，构建验证依赖同层 Server / SDK worktree 保持可用。

#### Issue List
- 无

### Stage 2 - Architecture Design
#### Overall Solution
- 采用“Android 配置模型扩展 + 启动前输入/权限校验 + Bridge 双签名兼容 + hubmobile 最小参数扩展”的方案。
- 选型理由：
  - 直接对齐现有 `hubruntime.Options` 字段，避免发明新的中间编码协议。
  - 在 Android 侧先做权限和 UUID 校验，能把错误前移到用户动作点。
  - 通过 `HubBridge` 同时兼容新旧 `Start(...)` 签名，可避免 stale AAR 下直接崩溃，同时保持 TCP-only 场景可继续运行。

#### Alternatives Considered
- 方案 A：把 RFCOMM listener 配置编码进 `addr` 字符串
  - 优点：少改参数
  - 缺点：混淆 TCP addr 与 listener 语义，难以校验与持久化，不可维护
- 方案 B：一次性暴露完整 Server RFCOMM 配置面（adapter/channel/insecure/uuid）
  - 优点：能力最完整
  - 缺点：超出 Android 当前“基本可用”目标，UI 复杂度和误用风险偏高
- 方案 C：仅改 `hubmobile` / runtime，不改 Android UI
  - 优点：代码变更看起来更少
  - 缺点：用户无法发现和配置 listener，不满足产品化目标
- 采用方案：最小 UI 配置面 + 双签名兼容 + 全链路显式传参

#### Module Responsibilities
- `app/src/main/java/com/myflowhub/android/HubConfig.kt`
  - 新增 RFCOMM listener 配置字段与默认值承载。
- `app/src/main/java/com/myflowhub/android/Prefs.kt`
  - 负责 RFCOMM listener 配置持久化与默认值兼容。
- `app/src/main/java/com/myflowhub/android/BluetoothRfcommSupport.kt`
  - 统一 RFCOMM scheme 判断、默认 UUID、权限判断、UUID 校验文案。
- `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
  - 暴露 RFCOMM listener 配置 UI，并在 Start 前做输入与权限校验。
- `app/src/main/java/com/myflowhub/android/HubService.kt`
  - 接收新增 extras，构造完整 `HubConfig`。
- `app/src/main/java/com/myflowhub/android/HubBridge.kt`
  - 反射加载新旧 `Start(...)` 签名，并在旧 AAR + listener 配置场景下显式失败。
- `hubmobile/hubmobile.go`
  - 扩展 `Start(...)` 参数并映射到 `hubruntime.Options`。
- `app/src/test/**`
  - 覆盖新增 helper / bridge 兼容逻辑。

#### Data / Call Flow
1. `Prefs.load()` 读取 Hub 基础配置与 RFCOMM listener 配置，填充 `HubConfig`。
2. 用户在 `HubScreen` 修改配置并即时保存。
3. 点击 Start 时：
   - 校验 `addr` / `selfId`
   - 若启用了 RFCOMM listener，校验 UUID
   - 若 `parentAddr` 是 RFCOMM 或启用了 RFCOMM listener，检查蓝牙权限
4. `startHubService()` 通过 intent extras 传递完整 `HubConfig`。
5. `HubService` 构造 `HubConfig` 并调用 `HubBridge.start(config)`。
6. `GoHubBridge`
   - 优先走新 `Start(addr, parentAddr, selfId, workDir, rfcommEnable, rfcommUuid, rfcommInsecure)`
   - 若只存在旧签名且当前未启用 RFCOMM listener，则回退旧调用
   - 若只存在旧签名且启用了 RFCOMM listener，则返回明确错误
7. `hubmobile.Start(...)` 构造 `hubruntime.Options`，写入 RFCOMM listener 相关字段并启动 runtime。

#### Interface Drafts
- `data class HubConfig(..., rfcommListenEnabled: Boolean = false, rfcommServiceUuid: String = BluetoothRfcommSupport.defaultServiceUuid(), rfcommInsecure: Boolean = false)`
- `BluetoothRfcommSupport.defaultServiceUuid(): String`
- `BluetoothRfcommSupport.normalizeServiceUuid(raw: String): String`
- `BluetoothRfcommSupport.isValidServiceUuid(raw: String): Boolean`
- `HubService.EXTRA_RFCOMM_ENABLE / EXTRA_RFCOMM_UUID / EXTRA_RFCOMM_INSECURE`
- `hubmobile.Start(addr string, parentAddr string, selfID string, workDir string, rfcommEnable bool, rfcommUUID string, rfcommInsecure bool) (string, error)`

#### Error Handling and Safety
- RFCOMM listener 仅在用户显式开启时生效。
- UUID 非法时在 UI 入口前置失败，避免把错误延后到底层 listener 创建。
- 旧 AAR 只允许在 TCP-only 场景继续兼容；涉及 RFCOMM listener 配置时必须显式报错。
- `rfcommInsecure` 默认 `false`，避免默认放宽安全性。

#### Performance and Testing Strategy
- 配置读取 / 保存沿用 SharedPreferences，不增加额外 I/O 通道。
- 权限检查仅在用户点击 Start 时执行，不增加后台轮询。
- 自动验证计划：
  - `GOWORK=off go test ./... -count=1 -p 1`（`hubmobile/`）
  - `.\scripts\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar`
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat :app:assembleDebug`

#### Extensibility Design Points
- 先只暴露 `enable/uuid/insecure`，后续若确实需要 `adapter/channel`，可以在 `HubConfig` 和 `HubService` 继续向后兼容扩展。
- `GoHubBridge` 的双签名反射层可复用到后续 gomobile API 渐进升级场景。
- RFCOMM 默认 UUID 和校验逻辑集中在 helper，减少与 Server 默认值漂移的风险。

#### Issue List
- 无

### Stage 3.1 - Planning
#### Project Goal and Current State
- 当前 Android 已具备：
  - RFCOMM provider 的 dial + listen 底层能力
  - parent RFCOMM 的基础可用性与权限闭环
- 当前仍缺：
  - Hub listener 配置模型、UI、持久化、Service extras、Bridge 参数与 `hubmobile` 显式传参
- 本次目标是把“Server 已支持但 Android 未产品化暴露”的 RFCOMM listener 收口为最小可用链路。

#### Docs Governance Routing Decision
- 使用 `$m-docs` 校验计划文档路由、requirements/specs 影响和 lessons 查询入口。
- Requirements impact: `none`
- Specs impact: `none`
- Related requirements: `none`
- Related specs:
  - `docs/change/2026-03-12_bluetooth-rfcomm-transport-android.md`
  - `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\change\2026-03-12_bluetooth-rfcomm-transport-server.md`
- Related lessons:
  - `docs/lessons/android-rfcomm-permission.md`
- 文档路由：
  - 当前 workflow 控制文档保留在 worktree 根 `plan.md`
  - 完成结果归档到 `docs/change/2026-03-31_android-rfcomm-listener-config.md`
  - 若本轮沉淀出新的“旧 AAR / Start 签名漂移”排查规则，再决定是否补充 `docs/lessons`

#### Related Requirements / Specs / Lessons
- Android 参考：
  - `docs/change/2026-03-12_bluetooth-rfcomm-transport-android.md`
  - `docs/change/2026-03-31_android-rfcomm-basic-usability.md`
  - `docs/lessons/android-rfcomm-permission.md`
- Server 参考：
  - `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\change\2026-03-12_bluetooth-rfcomm-transport-server.md`

#### Executable Task List
- [x] `ANDRFL-1`：更新控制文档与依赖 worktree 边界
- [x] `ANDRFL-2`：扩展 Hub 配置模型、Prefs、Helper 与 Hub UI
- [x] `ANDRFL-3`：扩展 Service / Bridge / hubmobile 启动链路并处理旧 AAR 兼容
- [x] `ANDRFL-4`：补测试并完成 Go / AAR / Android 构建验证
- [x] `ANDRFL-5`：Code Review
- [x] `ANDRFL-6`：归档 `docs/change`，必要时更新 `docs/lessons`

#### Task Details
##### ANDRFL-1 - 控制文档与依赖边界
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Android`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Android\plan.md`
- Goal: 让当前 workflow、依赖 worktree、docs 路由和回滚边界可审计。
- Files / Modules:
  - `plan.md`
  - `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Server\todo.md`
  - `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-SDK\todo.md`
  - `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Proto\todo.md`
- Write Set:
  - `plan.md`
  - `..\MyFlowHub-Server\todo.md`
  - `..\MyFlowHub-SDK\todo.md`
  - `..\MyFlowHub-Proto\todo.md`
- Acceptance:
  - 当前执行边界、依赖边界、docs impact 记录完整
  - 依赖仓 control doc 明确为 dependency-only
- Test Points:
  - `git status --short --branch`
- Rollback:
  - 回退上述文档文件

##### ANDRFL-2 - Hub 配置面与 UI
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Android`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Android\plan.md`
- Goal: 在不扩展高级参数的前提下，让 Android 用户可以配置 RFCOMM listener。
- Files / Modules:
  - `app/src/main/java/com/myflowhub/android/HubConfig.kt`
  - `app/src/main/java/com/myflowhub/android/Prefs.kt`
  - `app/src/main/java/com/myflowhub/android/BluetoothRfcommSupport.kt`
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
- Write Set:
  - `app/src/main/java/com/myflowhub/android/HubConfig.kt`
  - `app/src/main/java/com/myflowhub/android/Prefs.kt`
  - `app/src/main/java/com/myflowhub/android/BluetoothRfcommSupport.kt`
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
- Acceptance:
  - UI 可启用 RFCOMM listener 并编辑 UUID / insecure
  - Prefs 可持久化配置
  - Start 前会做 UUID 与权限校验
- Test Points:
  - `.\gradlew.bat testDebugUnitTest`
- Rollback:
  - 回退上述 Android 文件

##### ANDRFL-3 - 启动链路与旧 AAR 兼容
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Android`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Android\plan.md`
- Goal: 把 RFCOMM listener 配置可靠地传到 `hubruntime`，并显式处理新旧 `Start(...)` 签名差异。
- Files / Modules:
  - `app/src/main/java/com/myflowhub/android/HubService.kt`
  - `app/src/main/java/com/myflowhub/android/HubBridge.kt`
  - `hubmobile/hubmobile.go`
- Write Set:
  - `app/src/main/java/com/myflowhub/android/HubService.kt`
  - `app/src/main/java/com/myflowhub/android/HubBridge.kt`
  - `hubmobile/hubmobile.go`
- Acceptance:
  - 新 AAR 场景下 RFCOMM listener 配置进入 `hubruntime.Options`
  - 旧 AAR 场景下 TCP-only 可兼容，listener 场景显式失败
- Test Points:
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
  - `.\scripts\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar`
- Rollback:
  - 回退上述文件与重建后的 `app/libs/myflowhub.aar`

##### ANDRFL-4 - 验证与回归
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Android`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Android\plan.md`
- Goal: 证明 helper、hubmobile 和 Android APK 均可通过最小回归。
- Files / Modules:
  - `hubmobile/go.mod`
  - `hubmobile/go.sum`
  - `app/src/test/**`
  - `app/libs/myflowhub.aar`
- Write Set:
  - `hubmobile/go.mod`
  - `hubmobile/go.sum`
  - `app/src/test/**`
  - `app/libs/myflowhub.aar`
- Acceptance:
  - Go / AAR / Android 验证链路可执行并结果明确
  - 若本地 replace 依赖要求同步 module graph，`hubmobile/go.mod/go.sum` 变更应最小且可审计
  - `:app:assembleDebug` 通过
- Test Points:
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
  - `.\scripts\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar`
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat :app:assembleDebug`
- Rollback:
  - 回退测试文件与生成产物

#### Dependencies
- Android SDK:
  - `ANDROID_HOME=D:\project\MyFlowHub3\_android-sdk`
  - `ANDROID_SDK_ROOT=D:\project\MyFlowHub3\_android-sdk`
- `hubmobile/go.mod` 依赖同层相对 `replace`：
  - `..\MyFlowHub-Server`
  - `..\MyFlowHub-SDK`
- 由于 `MyFlowHub-Server(main)` 当前依赖未发布的 `protocol/stream` 包，本地 Go / AAR 验证需额外通过临时 `go.work` 引入：
  - `D:\project\MyFlowHub3\worktrees\feat-android-rfcomm-listen\MyFlowHub-Proto`
- `golang.org/x/mobile` / `gomobile` 工具链需可用，才能重建 AAR。

#### Risks and Notes
- 本轮不改 Server / SDK 代码；如验证过程中发现需要修改依赖仓逻辑，必须先回到 `3.1` 更新主计划。
- `app/libs/myflowhub.aar` 为构建产物，但本轮改动涉及 `hubmobile` 导出 API，必须重建后再做 Android 验证。
- 如果 `HubState` 不扩展 listener 字段，本轮只保证“配置可用 + 启动可用 + 报错可诊断”，不额外承诺运行态可视化。

#### Parallelism Assessment
- 主改动集中在同一 Android 模块和同一 `hubmobile` API 面，写集高度重叠。
- Server / SDK worktree 本轮仅为 dependency-only，不适合派发子 Agent。
- 采用主 agent 串行实现与统一验证。

#### Issue List
- 无

### Stage 3.3 - Code Review
- 需求覆盖：通过
  - RFCOMM listener 配置已从 UI/Prefs/Service/Bridge 传至 `hubruntime.Options`
  - 旧 AAR / listener 请求场景已显式失败
- 架构合理性：通过
  - 沿用现有 `HubConfig` 与 `HubBridge` 链路，没有引入新的中间配置层
- 性能风险（N+1 / 重复计算 / 多余 I/O / 锁竞争）：通过
  - 新增逻辑仅发生在配置保存和 Start 点击前校验，无持续后台开销
- 可读性与一致性：通过
  - RFCOMM 默认 UUID、权限判断、UUID 校验集中在 helper；Bridge 兼容逻辑独立成 `HubStartBinding`
- 可扩展性与配置化：通过
  - 当前只暴露最小配置面，未来可继续向 `HubConfig` / `HubService` 扩字段
- 稳定性与安全：通过
  - `rfcommInsecure` 默认 `false`
  - UUID 非法、权限缺失、旧 AAR 不支持 listener 时均为显式失败
- 测试覆盖情况：通过
  - Go 测试、AAR 构建、JVM 单测、`assembleDebug` 均已通过
- 子Agent治理与审计（任务映射、上下文完整性、文件所有权、结果复核、冲突处理、记录完整性）：通过
  - 未使用子 Agent

### Stage 4 - Change Archive
- `$m-docs` 路由结论：
  - Requirements impact: `none`
  - Specs impact: `none`
  - Lessons impact: `updated`
- 已归档：
  - `docs/change/2026-03-31_android-rfcomm-listener-config.md`
  - `docs/lessons/android-hubmobile-local-replace.md`
  - `docs/lessons/README.md`

阻塞：否
进入 3.2
