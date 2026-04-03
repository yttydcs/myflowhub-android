# Plan - Android：File offer/upload v1

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
  - `docs/plan_archive/plan_archive_2026-04-03_android-file-pull-download-v1-prev.md`

### Stage 1 - Requirements Analysis
#### 目标
- 为 Android File 模块补齐第一个真正可用的上传能力：用户能在 Android 上选择本地文件，向远端节点发起 `offer`，并在远端接受后真正发送 DATA，而不是只有浏览和下载。

#### 范围
- 必须
  - 在 `hubmobile` 中新增 `offer/upload` 导出入口，正确发送 `file.write(op=offer)`，并复用现有 file runtime 对匹配的 `write_resp` / ACK 处理来启动 DATA 发送。
  - Android 侧增加最小单文件选择能力，允许用户从系统 document picker 选择一个本地文件。
  - 在发起 `offer` 前，把选中的本地文件复制到应用可控目录，满足 `subproto/file` 发送端对 `file.base_dir + dir/name` 镜像路径的要求。
  - File 页面增加远端目录上的 `Upload` 入口和明确反馈，至少让用户知道来源文件、目标远端路径和“已开始发送”状态。
  - 补充 Go / Kotlin 单元测试，覆盖关键请求接线、路径/命名校验和本地 staging 纯逻辑。
- 可选
  - 在页面上展示最近一次上传的目标路径和本地 staging 路径，便于人工确认。
- 不做
  - 不做 Win 端完整传输任务系统、任务窗口、进度条、重试/取消、打开目录。
  - 不做被动接收 `offer`、远端确认弹窗、自动接收策略。
  - 不做多文件批量上传、不做目录上传。
  - 不做远端自定义文件名；本轮远端文件名沿用用户所选本地文件名。
  - 不改 Server / Proto 规格，不新增协议字段。

#### 使用场景
- 用户在 Android File 页面浏览远端节点目录时，希望把手机上的一个本地文件发送到当前远端目录，而不需要切到 Win。
- 用户需要从 Android 端把日志、截图、配置文件发送到远端节点，接受“先复制到应用 staging 目录再发”的实现方式。
- 用户接受本轮只做单文件上传，但需要清楚知道发送的是哪个本地文件、要发往哪个远端路径。

#### 功能需求
- 仅当 `source node id`、`hub id`、远端目标节点都合法，且浏览目标不是本地节点时，才允许发起 `offer`。
- 上传必须使用 `file.write(op=offer)` 语义，请求体 `data.target=远端接收方`。
- `hubmobile` 必须在收到匹配的 `write_resp(code=1, accept=true)` 后触发 DATA 发送，并继续消费 ACK。
- Android 侧必须在发起前把所选文件复制到应用本地 staging 根目录下的 `dir/name` 路径，避免直接依赖 `content://` URI 或临时权限。
- UI 必须明确展示 staging 根目录、本次来源文件名和远端目标路径。
- 上传只允许在远端浏览目标下触发；本地节点浏览时不允许误触发。

#### 非功能需求
- 改动优先保持在 Android 仓和 `hubmobile` 内部，不扩散到 SDK / Server 仓。
- 不得破坏已经可用的 `list/read_text/mkdir/pull` 行为。
- 不得在 UI 线程直接执行大文件复制或 hash；文件 I/O 必须放到后台线程。
- 所有路径、节点参数、文件名和 picker 返回值都要本地校验，错误必须显式暴露。
- 本轮默认单操作串行，不承诺并发 upload/download 的独立任务隔离。

#### 输入输出
- 输入
  - 用户需求：继续 Android File 对齐，优先补 `offer/upload`
  - 现状：
    - Android 现有 File 已支持 `list/read_text/mkdir/pull`
    - `hubmobile` 已有 file runtime，可观察 `read_resp/write_resp` 和 DATA/ACK
    - Android 侧还没有系统文件选择与本地 staging 逻辑
  - 相关规格：
    - `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\specs\file.md`
  - 相关参考实现：
    - `D:\project\MyFlowHub3\repo\MyFlowHub-Win\internal\services\file\transfer.go`
    - `github.com/yttydcs/myflowhub-subproto/file@v0.1.4`
- 输出
  - `hubmobile` 中可工作的 Android File `offer/upload` 启动链路
  - Android File 页面中的本地文件选择、staging、上传入口和启动反馈
  - 对应测试与变更归档

#### 边界异常
- 未登录、节点 ID 非法、目标为本地节点、选择结果为空时，必须阻止上传。
- picker 返回的 URI 不可读、没有可用显示名、复制 staging 失败时，必须给出明确错误。
- `write_resp.code != 1` 或 `accept=false` 时，必须把远端错误显示给用户。
- 本地 staging 目录不可创建或目标文件名非法时，必须在发起前失败。
- 本轮不承诺上传完成后的进度跟踪、失败重试和后台恢复。

#### 验收标准
- 用户在 Android File 页面浏览远端目录时，可以选择一个本地文件并发起上传。
- `hubmobile` 能发送 `offer` 控制帧，并在匹配的 `write_resp` 后触发 DATA 发送。
- UI 能显示 staging 根目录、来源文件名和远端目标路径，并在上传开始时给出明确反馈。
- `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1` 通过，或明确记录外部依赖阻塞并给出隔离验证结果。
- `.\gradlew.bat testDebugUnitTest` 通过。
- `.\gradlew.bat :app:assembleDebug` 通过。

#### 风险
- `subproto/file` 发送端要求本地源文件位于 `file.base_dir + dir/name`，Android 侧必须先做 staging，否则 `write_resp` 后无法真正发 DATA。
- 当前 runtime 的 `file.base_dir` 按操作配置；本轮默认单操作串行，若后续需要并发多任务，需要引入更明确的会话隔离。
- 设备上的 document provider 差异可能导致文件名、大小或读取流行为不一致，需要保守处理 fallback。

#### Issue List
- 无

### Stage 2 - Architecture Design
#### 总体方案
- 采用“Android 先把所选本地文件复制到应用 staging 根目录，再由 `hubmobile.FileOffer(...)` 发送 `write(op=offer)`，并复用现有 `file runtime + subproto/file` 在 `write_resp` 后自动起 DATA 发送”的方案。
- 不移植 Win 的任务系统，也不直接读取 `content://` URI 给 Go；文件选择与 staging 保持在 Kotlin，协议发送与 DATA/ACK 仍放在 Go runtime。

#### 选型理由 / 备选对比
- 方案 A：Kotlin 直接把 `content://` URI 传给 Go，让 Go 读取 Android content resolver
  - 优点：省一次 staging copy
  - 缺点：gomobile 暴露 Android `ContentResolver` 成本高，且 `subproto/file` 发送端只认本地路径
- 方案 B：在 `hubmobile` 里自建一套独立 upload sender，绕过 `subproto/file` 的 `write_resp -> send DATA` 逻辑
  - 优点：可直接支持任意本地绝对路径
  - 缺点：重复实现 file sender，风险大且偏离最小改动
- 采用方案：Kotlin staging + Go `offer`
  - 原因：最小改动即可复用现有 runtime，协议语义正确，后续仍可扩到任务视图

#### 模块职责
- `hubmobile/file.go`
  - 新增 `offer` 导出接口与响应校验
  - 负责生成 `session_id`、校验本地 staging 文件并发起 `write(op=offer)`
- `hubmobile/file_runtime.go`
  - 继续维护 file runtime
  - 复用现有 `write_resp` / ACK 观察链路，让 `subproto/file` 在写响应后自动起 DATA sender
- `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - 反射新增上传相关导出方法
- `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
  - 增加 upload staging 根目录、staging 路径推导、picker 结果校验和 `offer` 启动结果解析
- `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
  - 增加 `Upload` 入口、系统 document picker、上传确认交互和启动反馈

#### 数据 / 调用流
1. 用户在 File 页面浏览远端目录并点击 `Upload`。
2. Android 通过 document picker 选择一个本地文件。
3. Kotlin 校验文件名并把内容复制到 staging 根目录下的 `currentDir/name`。
4. Kotlin 调用 `go.fileOffer(...)`，把远端目录、文件名和 staging 根目录传给 Go。
5. `hubmobile` 配置 file runtime 的本地 node id、parent hub id、base dir，并发送 `write(op=offer)`。
6. SDK `onFrame` 收到匹配的 `write_resp` 后，runtime 将帧投递给 `subproto/file` handler；handler 在本地创建发送会话并从 staging 文件发送 DATA。
7. 后续 ACK 继续通过 `onFrame` 进入 handler；Kotlin 收到 `offer` 启动结果后向用户显示“已开始发送”和远端目标路径。

#### 接口草案
- `hubmobile.FileOffer(sourceID, hubID, targetID, dir, name, wantHash, localBaseDir string) (string, error)`
- `GoClientBridge.fileOffer(sourceId: String, hubId: String, targetId: String, dir: String, name: String, wantHash: String, localBaseDir: String): String`
- `FileProtocolSupport.resolveUploadRoot(filesDir: File, externalFilesDir: File?): String`
- `FileProtocolSupport.expectedUploadStagePath(localBaseDir: String, dir: String, name: String): String`
- `FileProtocolSupport.parseOfferStart(raw: String): FileOfferStartResult`

#### 错误与安全
- 严格校验 `source/hub/target`、文件名、`want_hash`、staging 根目录。
- 本地 staging 只允许落在应用私有目录内，且通过规范化路径阻止目录穿越。
- document picker 返回值必须检查 `uri != null`、显示名非空、输入流可打开。
- `onFrame` 继续只转发 File 子协议帧，避免误处理其它子协议。

#### 性能与测试策略
- picker 文件复制、hash 和 Go 调用都放在后台线程。
- staging 采用单次流复制到目标文件，不做重复读写。
- Go 测试重点覆盖：
  - `FileOffer` 参数接线、session id 和本地 staging 路径校验
  - runtime 收到 `write_resp` 后触发 DATA 发送
- Kotlin 单测重点覆盖：
  - upload 根目录解析
  - staging 路径推导
  - `offer` 启动结果解析和文件名校验
- 集成验证：
  - `hubmobile go test`
  - `testDebugUnitTest`
  - `assembleDebug`

#### 可扩展性设计点
- staging helper 后续可扩展为“多文件导入后再逐个 offer”而不改当前 Go wire 语义。
- 当前 `offer` 启动结果和 UI 状态结构可后续接到轻量任务列表，而不需要推翻本轮接口。
- 若后续需要并发 upload/download，可在 runtime 之上补多 base dir / 多会话调度层。

#### Issue List
- 无

### Stage 3.1 - Planning
#### Project Goal and Current State
- 当前 Android File 已支持 `list/read_text/mkdir/pull`，但还没有 Android 本地文件选择与 `offer/upload` 起传链路。
- 现有 `await.Client.SetOnFrame` 是全帧 tap，匹配成功的 `write_resp` 也会经过该回调，可直接复用到 `offer`。
- `subproto/file` 的发送端会在 `write_resp` 后从 `file.base_dir + dir/name` 解析本地源文件，因此 Kotlin staging 是本轮的关键产品化动作。

#### Docs Governance Routing Decision
- 使用 `$m-docs` 校验计划文档路由、requirements/specs 影响和 lessons 查询入口。
- Requirements impact: `none`
- Specs impact: `none`
- Related requirements: `none`
- Related specs:
  - `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\specs\file.md`
- Related lessons:
  - `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\docs\lessons\android-file-offer-staging.md`
  - `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\docs\lessons\android-hubmobile-local-replace.md`
- Related changes:
  - `docs/change/2026-04-02_android-file-module.md`
  - `docs/change/2026-04-03_android-file-pull-download-v1.md`

#### Executable Task List
- [x] `ANDFILEOFFER-1`：归档上一轮 `plan.md` 并建立本轮控制文档
- [x] `ANDFILEOFFER-2`：为 `hubmobile` 补齐 `offer/upload` 导出与运行时接线
- [x] `ANDFILEOFFER-3`：在 Android File 页面补齐 document picker、staging 和上传 UI
- [x] `ANDFILEOFFER-4`：补充 Go / Kotlin 测试并完成本地验证
- [x] `ANDFILEOFFER-5`：完成 3.3 自审与 4 阶段归档

#### Task Details
##### ANDFILEOFFER-1 - 控制文档切换
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\plan.md`
- Goal: 归档上一轮 pull/download v1 计划，并建立本轮 offer/upload v1 控制文档。
- Files / Modules:
  - `plan.md`
  - `docs/plan_archive/plan_archive_2026-04-03_android-file-pull-download-v1-prev.md`
- Write Set:
  - `plan.md`
  - `docs/plan_archive/plan_archive_2026-04-03_android-file-pull-download-v1-prev.md`
- Acceptance:
  - 上一轮计划已归档
  - 新计划完整记录 stage 1 / 2 / 3.1
- Test Points:
  - 归档文件存在且可读
- Rollback:
  - 删除新 archive，恢复旧 `plan.md`

##### ANDFILEOFFER-2 - hubmobile offer 运行时
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\plan.md`
- Goal: 让 Android 侧真正具备 `offer` 控制请求 + `write_resp` 后 DATA 发送能力。
- Files / Modules:
  - `hubmobile/file.go`
  - `hubmobile/file_runtime.go`
  - `hubmobile/*_test.go`
- Write Set:
  - `hubmobile/file.go`
  - `hubmobile/file_runtime.go`
  - `hubmobile/file_pull_test.go` 或新增对应测试文件
- Acceptance:
  - 可发起 `offer`
  - 匹配的 `write_resp` 会创建本地发送会话
  - 后续 DATA / ACK 能完成发送链路
- Test Points:
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
- Rollback:
  - 回退 `offer` 导出和运行时相关改动

##### ANDFILEOFFER-3 - Android File 上传 UI
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\plan.md`
- Goal: 在现有 File 页面上增加本地文件选择、staging 和远端上传入口。
- Files / Modules:
  - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
  - `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
- Write Set:
  - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
  - `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
- Acceptance:
  - 远端目录可以触发 `Upload`
  - picker 选择结果会被 staging 到应用私有目录
  - UI 能展示 staging 根目录、源文件名和远端目标路径
- Test Points:
  - `testDebugUnitTest`
  - 手工触发本地选择和上传启动流程
- Rollback:
  - 回退上述 Android bridge / helper / UI 改动

##### ANDFILEOFFER-4 - 测试与验证
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\plan.md`
- Goal: 锁定 `offer/upload` 关键语义并完成构建回归。
- Files / Modules:
  - `hubmobile/*_test.go`
  - `app/src/test/java/com/myflowhub/android/*`
- Write Set:
  - `hubmobile/*_test.go`
  - `app/src/test/java/com/myflowhub/android/*`
- Acceptance:
  - Go / Kotlin 单测补齐
  - `assembleDebug` 通过
- Test Points:
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat :app:assembleDebug`
- Rollback:
  - 删除新增测试并回退相关代码

#### Dependencies, Risks, and Notes
- Dependencies:
  - `myflowhub-subproto/file@v0.1.4`
  - `await.Client.SetOnFrame`
  - Android `ActivityResultContracts.OpenDocument`
- Risks:
  - 若本地 `hubmobile` 依赖 worktree 继续漂移，Go 标准命令仍可能被外部 replace 阻塞
  - 若本机 Android SDK 仍缺 NDK，则新的 Go 导出方法无法重建 AAR，只能完成源码级验证
  - staging 路径若和当前目录/文件名冲突，可能覆盖旧的临时上传源文件；本轮按最小策略接受此限制
- Notes:
  - 单操作串行由页面 `busy` 状态约束，本轮不承诺多任务并发
  - 继续沿用已有 `worktrees/MyFlowHub-Server` / `MyFlowHub-SDK` / `MyFlowHub-Proto` 本地依赖方案

#### Parallelism Assessment
- 不使用子 Agent。
- 原因：
  - `hubmobile/file.go`、`file_runtime.go`、`FileProtocolSupport.kt` 和 `FileScreen.kt` 的语义强耦合，接口边界要一起收敛
  - 当前 write set 小，串行实现更容易保证计划、代码和验证一致

阻塞：否
进入 3.2

### Stage 3.2 - Implementation Result
#### ANDFILEOFFER-2
- `hubmobile/file.go`
  - 新增 `FileOffer`
  - 复用现有 file runtime 配置、按需计算 `sha256`、生成 `session_id`
  - 显式校验 staging 文件存在、非目录、非空
- `hubmobile/file_pull_test.go`
  - 新增 `FileOffer` 接线测试
  - 新增 `write_resp -> DATA` 发送链路测试
  - 新增空文件拒绝测试

#### ANDFILEOFFER-3
- `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - 反射新增 `FileOffer`
- `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
  - 新增 upload staging 根目录 helper
  - 新增 staging 路径推导、`offer` 启动结果解析和通用文件名校验
- `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
  - 增加 `Upload` 按钮
  - 接入 document picker
  - 增加 staging 和上传确认弹窗
  - 显示 staging 根目录、最近一次上传和远端目标路径

#### ANDFILEOFFER-4
- `app/src/test/java/com/myflowhub/android/FileProtocolSupportTest.kt`
  - 增加 upload root、staging 路径、`offer` 启动结果和文件名校验测试
- 验证结果：
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
    - 受 `worktrees/MyFlowHub-Server` 本地依赖漂移阻塞，未直接通过
  - `cd hubmobile; $env:GOWORK='off'; go test "-modfile=go.verify.mod" -mod=mod ./... -count=1 -p 1`
    - 通过
  - `ANDROID_HOME=C:\Users\HelloWorld\AppData\Local\Android\Sdk`
  - `ANDROID_SDK_ROOT=C:\Users\HelloWorld\AppData\Local\Android\Sdk`
  - `.\gradlew.bat testDebugUnitTest`
    - 通过
  - `.\gradlew.bat :app:assembleDebug`
    - 通过
  - `.\scripts\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar`
    - 底层 `gomobile` 仍报 `no usable NDK`
    - `app/libs/myflowhub.aar` 未产出

### Stage 3.3 - Code Review
- 需求覆盖：`通过`
  - 已补齐 Android 本地文件选择、staging 和 `offer/upload` 启动链路
- 架构合理性：`通过`
  - 复用 `subproto/file` sender，没有重复实现 DATA 发送器
- 性能风险（N+1 / 重复计算 / 多余 I/O / 锁竞争）：`通过`
  - 文件复制、hash 和 Go 调用都放在后台线程；无新增轮询
- 可读性与一致性：`通过`
  - Go / Kotlin 新接口命名沿用现有 File 模块风格
- 可扩展性与配置化：`通过`
  - upload staging helper 和 `FileOffer` 结果结构后续可继续接轻量任务视图
- 稳定性与安全：`通过`
  - 已补节点、路径、文件名和空文件校验；对 `accept=false` 显式失败
- 测试覆盖情况：`通过（带环境注记）`
  - Android 单测与 `assembleDebug` 通过
  - Go 标准命令受外部依赖 worktree 漂移阻塞，已用临时 `modfile` 完成隔离验证
  - AAR 本地重建仍受 NDK 缺失阻塞，设备运行时需补齐环境后重建
- 子Agent治理与审计（任务映射、上下文完整性、文件所有权、结果复核、冲突处理、记录完整性）：`通过`
  - 本轮未使用子 Agent

### Stage 4 - Change Archive
- 使用 `$m-docs` 完成变更归档与 lessons 检查
- Change archive:
  - `docs/change/2026-04-03_android-file-offer-upload-v1.md`
- Requirements impact: `none`
- Specs impact: `none`
- Lessons impact: `updated`
- Related lessons:
  - `docs/lessons/android-file-offer-staging.md`
  - `docs/lessons/android-hubmobile-local-replace.md`
