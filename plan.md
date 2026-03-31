# Plan - Android：RFCOMM 基本可用性补齐

## Workflow Information
- Repo: `MyFlowHub-Android`
- Branch: `fix/android-rfcomm-basic-usability`
- Base: `origin/main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-rfcomm-basic-usability\MyFlowHub-Android`
- Current Stage: `4`

## Stage Records

### Initialization
- guide.md: `D:\project\MyFlowHub3\guide.md`，已读取；commit 信息需使用中文。
- base/worktree confirmation:
  - 主仓 `repo/MyFlowHub-Android` 当前分支为 `main`
  - 已创建专用 worktree：`D:\project\MyFlowHub3\worktrees\fix-android-rfcomm-basic-usability\MyFlowHub-Android`
  - 本 workflow 仅在该 worktree 内实现与验证

### Stage 1 - Requirements Analysis
#### Goal
- 提升 Android 端 Bluetooth Classic RFCOMM 的基本可用性，确保用户在使用 `bt+rfcomm://...` 端点时不会因为缺少权限或 UI 误导而直接失败。

#### Scope
- 必须
  - 补齐 AndroidManifest 中 RFCOMM 所需的蓝牙权限声明。
  - 为 Android 12+ 的 RFCOMM 使用路径补齐运行时权限申请。
  - 在 Login / Hub 入口对 RFCOMM 端点做最小可用的权限前置检查，避免直接触发底层失败。
  - 将 UI 文案从“仅 `ip:port`”修正为明确支持 endpoint，包括 `bt+rfcomm://...`。
  - 当权限缺失时返回可诊断提示，而不是仅暴露底层反射或系统异常。
- 可选
  - 在 UI 中展示当前 RFCOMM 权限状态的轻量提示。
  - 为纯逻辑 helper 增加本地单元测试。
- 不做
  - 不实现 BLE/GATT。
  - 不实现蓝牙扫描、配对引导、按设备名发现。
  - 不改 Core / SDK / Server 的 RFCOMM 语义。
  - 不扩展 Android Hub 的 RFCOMM listen 产品化配置。

#### Use Cases
- 用户在 Login 页填写 `bt+rfcomm://AA:BB:CC:DD:EE:FF?...` 并发起 Connect。
- 用户在 Hub 页填写 RFCOMM 父链 endpoint 并启动 Hub。
- Android 12+ 首次使用 RFCOMM 时，App 能请求蓝牙权限并给出下一步提示。
- 用户继续使用 TCP 时，不应被蓝牙权限流程打扰。

#### Functional Requirements
- Manifest 必须声明 RFCOMM 使用所需蓝牙权限，并兼容 Android 12+ 与旧版本。
- 只有在当前输入 endpoint 走 RFCOMM 时，才触发运行时蓝牙权限申请。
- 若权限未授予，Connect / Start 不应继续调用底层 RFCOMM 逻辑。
- Login / Hub 页输入框与提示文案必须明确 endpoint 语义，而不是只写 `ip:port`。
- RFCOMM Provider 在缺少蓝牙权限时，应返回清晰错误信息。

#### Non-functional Requirements
- 保持 TCP 路径行为不变。
- 采用最小改动面，不引入新的第三方 UI/权限库。
- 失败路径必须显式、可观测，不静默吞错。
- 权限申请默认采用按需触发，避免对 TCP-only 用户造成无谓打扰。

#### Inputs / Outputs
- 输入
  - Login 页 `targetAddr`
  - Hub 页 `parentAddr`
  - Android 运行时权限授予结果
- 输出
  - 成功时：继续执行既有 Connect / Start 流程
  - 失败时：UI Snackbar / 状态文本显示明确错误

#### Edge Cases
- Android 12+ 用户拒绝蓝牙权限。
- 用户输入仍为 TCP 地址。
- 蓝牙权限已授予，但系统蓝牙未开启。
- 输入为非法 RFCOMM endpoint。
- Go AAR 不可用时，不应把问题误报为蓝牙权限问题。

#### Acceptance Criteria
- `app/src/main/AndroidManifest.xml` 包含 RFCOMM 相关权限声明。
- Android 12+ 上，当 `targetAddr` 或 `parentAddr` 使用 `bt+rfcomm://` 且权限缺失时，UI 会先请求权限并阻止继续执行。
- Login / Hub 的输入提示明确支持 `bt+rfcomm://...` endpoint。
- 缺少权限导致的 RFCOMM 失败信息可被用户直接理解。
- 至少完成一条自动验证链路与一条构建验证链路。

#### Risks
- Android 权限策略按 API 版本分叉，处理不当可能误伤旧版本行为。
- 如果权限检查只放在 UI，未来新增 RFCOMM 入口可能遗漏；需在 provider 侧保留清晰兜底错误。

#### Issue List
- 无

### Stage 2 - Architecture Design
#### Overall Solution
- 采用“UI 入口按需申请 + Provider 错误兜底 + 文案修正”的最小闭环方案。
- 选型理由：
  - 按需申请只在 RFCOMM 端点触发，避免对 TCP-only 用户造成打扰。
  - Provider 保留权限异常兜底，避免未来新增入口绕过 UI 时出现难排查错误。
  - 文案修正能把现有隐式支持变成可发现的能力。

#### Alternatives Considered
- 方案 A：App 启动即统一申请蓝牙权限
  - 优点：实现简单
  - 缺点：即便用户只用 TCP 也会被打扰，不符合最小侵入原则
- 方案 B：只补 Manifest，不做 UI 申请
  - 优点：改动最小
  - 缺点：Android 12+ 仍然不可用，无法满足“基本可用性”
- 采用方案：按 RFCOMM 端点按需申请 + provider 兜底

#### Module Responsibilities
- `app/src/main/java/com/myflowhub/android/BluetoothRfcommSupport.kt`
  - 统一 RFCOMM endpoint 判断、权限集合和权限状态检查。
- `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - 持有蓝牙权限状态与请求 launcher，并下发给页面。
- `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
  - Connect 前识别 RFCOMM endpoint，必要时先请求权限并提示。
- `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
  - Start 前识别 RFCOMM 父链 endpoint，必要时先请求权限并提示。
- `app/src/main/java/com/myflowhub/android/BluetoothRfcommProvider.kt`
  - 将底层 `SecurityException` 归一化为明确错误。
- `app/src/main/AndroidManifest.xml`
  - 声明蓝牙权限。

#### Data / Call Flow
1. 用户在 Login / Hub 页面输入 endpoint。
2. 页面调用 RFCOMM helper 判断是否为 `bt+rfcomm://...`。
3. 若当前路径需要 RFCOMM 且权限未授予：
   - 触发权限请求
   - UI 给出提示并中止当前操作
4. 若权限已就绪：
   - 继续调用既有 `GoClientBridge.connect()` 或 `HubService` 启动流程
5. 若底层仍因权限问题抛出异常：
   - `BluetoothRfcommProvider` 转换为清晰错误并返回 UI

#### Interface Drafts
- `BluetoothRfcommSupport.usesRfcommEndpoint(raw: String): Boolean`
- `BluetoothRfcommSupport.requiredRuntimePermissions(sdkInt: Int): List<String>`
- `BluetoothRfcommSupport.hasRuntimePermissions(context: Context): Boolean`
- `LoginScreen(..., hasBluetoothPermission: Boolean, requestBluetoothPermission: () -> Unit)`
- `HubScreen(..., hasBluetoothPermission: Boolean, requestBluetoothPermission: () -> Unit)`

#### Error Handling and Safety
- 仅对 RFCOMM endpoint 触发蓝牙权限逻辑。
- 权限缺失时优先给出“需要蓝牙权限”的明确提示，不直接落到底层异常。
- provider 侧对 `SecurityException` 做消息归一化，防止反射包装异常污染用户提示。

#### Performance and Testing Strategy
- 权限检查仅在用户点击 Connect / Start 时执行，不引入后台轮询。
- 自动验证：
  - 新增 helper 纯逻辑单元测试
  - 运行 `gradlew.bat testDebugUnitTest`
- 构建验证：
  - 运行 `gradlew.bat :app:assembleDebug`

#### Extensibility Design Points
- endpoint 判断与权限集合收敛到单一 helper，后续若加入 `BLUETOOTH_SCAN` 或更多 RFCOMM UI 入口，可复用同一逻辑。
- UI 通过参数接收权限状态与请求函数，避免页面内部直接依赖 Activity。

#### Issue List
- 无

### Stage 3.1 - Planning
#### Project Goal and Current State
- 当前 Android 已具备 RFCOMM provider 与 Go bridge，但缺少蓝牙权限声明和运行时授权流程，且 Login / Hub UI 文案仍偏向 TCP。
- 本次目标是把 Android RFCOMM 从“代码层隐式存在”提升到“用户可实际走通的最小可用状态”。

#### Docs Governance Routing Decision
- Requirements impact: `none`
- Specs impact: `none`
- Related requirements: `none`
- Related specs:
  - `docs/change/2026-03-12_bluetooth-rfcomm-transport-android.md`
- Related lessons: `none`
- 文档路由：
  - 执行计划保留在 worktree 根 `plan.md`
  - 完成结果归档到 `docs/change/YYYY-MM-DD_android-rfcomm-basic-usability.md`

#### Related Requirements / Specs / Lessons
- 参考：
  - `docs/change/2026-03-12_bluetooth-rfcomm-transport-android.md`
  - `docs/change/2026-02-27_android-hub-ui-v1.md`
  - `docs/change/2026-02-27_android-fgs-type-gomobile-reflect.md`

#### Executable Task List
- [x] `ANDBT-1`：新增 RFCOMM 权限 helper 与 Manifest 权限声明
- [x] `ANDBT-2`：在 AppRoot / Login / Hub 接入按需蓝牙权限申请
- [x] `ANDBT-3`：修正 RFCOMM 输入文案与 provider 错误提示
- [x] `ANDBT-4`：补充自动测试并完成构建验证
- [x] `ANDBT-5`：Code Review
- [x] `ANDBT-6`：归档 `docs/change`

#### Task Details
##### ANDBT-1 - 权限与 helper 基座
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-rfcomm-basic-usability\MyFlowHub-Android`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-rfcomm-basic-usability\MyFlowHub-Android\plan.md`
- Goal: 补齐 RFCOMM 运行所需权限声明，并集中 RFCOMM 判断与权限逻辑。
- Files / Modules:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/myflowhub/android/BluetoothRfcommSupport.kt`
- Write Set:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/myflowhub/android/BluetoothRfcommSupport.kt`
- Acceptance:
  - RFCOMM 所需 Manifest 权限已声明
  - helper 能识别 RFCOMM endpoint，并判断当前 API 是否需要运行时权限
- Test Points:
  - 本地单元测试覆盖 endpoint 识别与权限决策
- Rollback:
  - 回退上述文件

##### ANDBT-2 - UI 权限申请接入
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-rfcomm-basic-usability\MyFlowHub-Android`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-rfcomm-basic-usability\MyFlowHub-Android\plan.md`
- Goal: 让 Login / Hub 在 RFCOMM 路径下按需请求蓝牙权限。
- Files / Modules:
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
- Write Set:
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
- Acceptance:
  - RFCOMM Connect / Start 在权限缺失时会先请求权限并中止当前操作
  - TCP 路径不受影响
- Test Points:
  - 单元测试 + `:app:assembleDebug`
- Rollback:
  - 回退上述文件

##### ANDBT-3 - 文案与错误可诊断性
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-rfcomm-basic-usability\MyFlowHub-Android`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-rfcomm-basic-usability\MyFlowHub-Android\plan.md`
- Goal: 让用户知道可以输入 RFCOMM endpoint，并在权限异常时看到清晰报错。
- Files / Modules:
  - `app/src/main/java/com/myflowhub/android/BluetoothRfcommProvider.kt`
  - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
- Write Set:
  - `app/src/main/java/com/myflowhub/android/BluetoothRfcommProvider.kt`
  - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
- Acceptance:
  - 输入提示明确支持 `bt+rfcomm://...`
  - 权限异常报错不再只是底层异常
- Test Points:
  - 构建通过
- Rollback:
  - 回退上述文件

##### ANDBT-4 - 测试与验证
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-rfcomm-basic-usability\MyFlowHub-Android`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-rfcomm-basic-usability\MyFlowHub-Android\plan.md`
- Goal: 为新增 helper 提供自动验证，并跑通最小构建回归。
- Files / Modules:
  - `app/build.gradle.kts`
  - `app/src/test/**`（如需新增）
- Write Set:
  - `app/build.gradle.kts`
  - `app/src/test/**`
- Acceptance:
  - `gradlew.bat testDebugUnitTest` 通过
  - `gradlew.bat :app:assembleDebug` 通过
- Test Points:
  - 本地单元测试
  - Debug 构建
- Rollback:
  - 回退测试与依赖变更

#### Dependencies
- 依赖 Android SDK / Gradle 本地环境可用。
- 若 `app/libs/myflowhub.aar` 缺失，仍应保持 App 可编译。

#### Risks and Notes
- 当前 RFCOMM 主要覆盖 client connect 与 hub parent dial，不扩展到 listen 配置产品化。
- 若测试环境缺少 Android SDK 或 Gradle 依赖，需至少保留单元测试结果与受限说明。

#### Parallelism Assessment
- 本次改动集中在同一 Android App 模块，写集高度重叠，不适合派发子 Agent。
- 采用主 agent 串行实现与统一回归。

#### Issue List
- 无

阻塞：否
进入 3.2
