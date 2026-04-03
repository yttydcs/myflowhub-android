# Plan - Android：File pull/download v1

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

### Stage 1 - Requirements Analysis
#### 目标
- 为 Android File 模块补齐第一个真正可用的下载能力，让用户可以从远端节点发起 `pull`，并把文件落到 Android 本地目录，而不是只有浏览和文本预览。

#### 范围
- 必须
  - 在 `hubmobile` 中接入 `file` 子协议的接收运行时，确保 `pull` 不只是发控制帧，而是能处理 `read_resp`、DATA、ACK 并实际落盘。
  - 新增 Android 侧的 `pull/download` 入口，只允许对远端文件发起下载。
  - 为 Android 侧确定一个稳定的本地下载根目录，并在 UI 中明确展示。
  - 下载发起后给出可见反馈，至少让用户知道目标保存路径与“已开始接收”状态。
  - 补充 Go / Kotlin 单元测试，覆盖关键协议接线和响应解析。
- 可选
  - 在 File 页面展示轻量级“最近下载”或本地落盘提示，帮助用户确认结果。
- 不做
  - 不做 Win 端完整的传输任务系统、任务窗口、重试/取消、打开目录。
  - 不做 `offer/upload`、被动接收 offer、自动接收策略。
  - 不做 Win 那种自定义 `saveDir/saveName` 语义；本轮本地落盘路径采用 Android 固定下载根目录 + 远端 `dir/name` 镜像。
  - 不改 Server / Proto 规格，不新增协议字段。

#### 使用场景
- 用户在 Android File 页面浏览远端节点时，希望把某个文件下载到本机，而不需要切到 Win。
- 用户需要在 Android 设备上拉取日志、配置或文本文件，之后再用系统文件管理器或后续功能查看。
- 用户接受“先下载到应用确定的本地目录”，但需要清楚知道文件会存到哪里。

#### 功能需求
- 仅当 `source node id`、`hub id`、远端目标节点都合法时，才允许发起下载。
- 下载必须使用 `file.read(op=pull)` 语义，请求体 `data.target=远端提供方`。
- `hubmobile` 必须在接收到匹配的 `read_resp` 后创建本地接收会话，并处理后续 DATA / ACK。
- 本地下载根目录必须可创建、稳定，并与 Android 当前运行环境解耦，不依赖用户手工准备目录。
- UI 必须明确展示下载根目录与本次文件预计保存路径。
- 下载只允许针对远端文件项，不允许对目录或本地节点误触发。

#### 非功能需求
- 改动优先保持在 Android 仓和 `hubmobile` 内部，不扩散到 SDK / Server 仓。
- 不得破坏已经可用的 `list/read_text/mkdir` 行为。
- `onFrame` 接入不能把文件大块写盘逻辑直接卡在 read loop 上；需要有可控的转发或异步处理。
- 所有路径和节点参数都要本地校验，错误必须显式暴露。

#### 输入输出
- 输入
  - 用户需求：继续 Android File 对齐，优先补 `pull/download`
  - 现状：
    - Android 现有 File 只支持 `list/read_text/mkdir`
    - `hubmobile` 只有 file 控制面 await，没有 file DATA / ACK 接收运行时
  - 相关规格：
    - `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\specs\file.md`
  - 相关参考实现：
    - `D:\project\MyFlowHub3\repo\MyFlowHub-Win\internal\services\file\transfer.go`
    - `github.com/yttydcs/myflowhub-subproto/file@v0.1.4`
- 输出
  - `hubmobile` 中可工作的 Android File pull 接收链路
  - Android File 页面中的下载入口与本地保存路径反馈
  - 对应测试与变更归档

#### 边界异常
- 未登录、节点 ID 非法、目标为本地节点、文件名为空时，必须阻止下载。
- 本地下载根目录无法创建或不可写时，必须在发起前或发起后给出明确错误。
- `read_resp.code != 1` 时，必须把服务端错误显示给用户。
- 连接断开、中途中止、长时间未完成时，本轮不承诺完整任务恢复；至少不能静默成功。
- 本轮不承诺下载完成后的系统分享、自动打开和 MediaStore 集成。

#### 验收标准
- 远端文件在 Android File 页面上可发起下载。
- `hubmobile` 能处理匹配的 `read_resp` 和后续 DATA / ACK，最终在本地目录看到文件。
- UI 能显示本地下载根目录，并在下载开始时给出明确保存路径或状态反馈。
- `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1` 通过。
- `.\gradlew.bat testDebugUnitTest` 通过。
- `.\gradlew.bat :app:assembleDebug` 通过。

#### 风险
- `subproto/file` handler 依赖 `core.ServerFromContext` 与连接管理语义，移动端需要补一层轻量 runtime 适配。
- 若直接在 SDK `onFrame` 回调里做重 I/O，可能影响读循环时序，需要控制处理方式。
- Android 本地落盘路径若选得过于封闭，用户会觉得“下载成功但找不到文件”。

#### Issue List
- 无

### Stage 2 - Architecture Design
#### 总体方案
- 采用“继续使用现有 `sendAndAwait` 发起 `pull` 控制请求，同时把 `myflowhub-subproto/file` handler 接入 `await.Client.SetOnFrame`，让它处理 `read_resp` / DATA / ACK”的方案。
- 本地落盘路径不复刻 Win 的 `saveDir/saveName` 任务体系，而是采用 Android 固定下载根目录加远端 `dir/name` 镜像，保证协议不变、接线最小。

#### 选型理由 / 备选对比
- 方案 A：只新增 `FileStartPull` await 方法，不接 file handler
  - 优点：改动最小
  - 缺点：只能收到 `read_resp`，不能真正接收文件，无法交付
- 方案 B：把 Win `internal/services/file` 任务系统整套移植到 Android
  - 优点：更接近 Win 完整能力
  - 缺点：范围过大，涉及事件总线、任务窗口、路径语义和 Wails 绑定差异
- 采用方案：复用 `subproto/file` handler 做真正的协议接收，UI 只产品化最小下载体验
  - 原因：协议语义正确，范围可控，且后续仍可扩展任务视图

#### 模块职责
- `hubmobile/file.go`
  - 新增 `pull` 导出接口与响应校验
  - 负责发起 `read(op=pull)` 控制请求
- `hubmobile` 新增 file runtime 适配层
  - 初始化 `subproto/file` handler
  - 维护最小 fake server / parent connection / onFrame 转发
  - 配置 Android 本地下载根目录
- `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - 反射新增下载相关导出方法
- `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
  - 解析 `pull` 响应与本地保存路径展示逻辑
- `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
  - 增加下载按钮、下载确认交互与本地路径反馈

#### 数据 / 调用流
1. 用户在 File 页面选择远端文件并点击 `Download`。
2. Kotlin 侧计算 Android 本地下载根目录，调用 `go.filePull(...)`。
3. `hubmobile` 在发起请求前配置 file runtime 的本地 node id、parent hub id、base dir。
4. `hubmobile` 通过现有 `sendAndAwait` 发送 `read(op=pull)`。
5. SDK `onFrame` 收到 `read_resp` 时，file runtime 将该帧投递给 `subproto/file` handler；handler 在本地创建接收会话。
6. 后续 DATA / ACK 继续通过 `onFrame` 进入 handler，handler 负责写 `.part`、ACK 和最终落盘。
7. Kotlin 收到 `pull` 响应后，向用户显示“已开始下载”和预计保存路径。

#### 接口草案
- `hubmobile.FilePull(sourceID, hubID, targetID, dir, name, wantHash, localBaseDir string) (string, error)`
- `hubmobile.FileLocalBaseDir(baseDir string) (string, error)` 或内部 helper，不一定导出
- `GoClientBridge.filePull(sourceId: String, hubId: String, targetId: String, dir: String, name: String, wantHash: String, localBaseDir: String): String`
- `FileProtocolSupport.parsePullStart(raw: String, localBaseDir: String): FilePullStartResult`

#### 错误与安全
- 严格校验 `source/hub/target`、文件名和本地下载根目录。
- `onFrame` 仅转发 File 子协议帧，避免误处理其它子协议。
- fake server 只暴露 file handler 所需的最小 `Send/ConnManager/NodeID` 能力。
- 本地下载根目录不允许为空；路径创建失败时立即报错。

#### 性能与测试策略
- `onFrame` 只做轻量分发，file handler 的实际处理放到独立 worker，避免阻塞 read loop。
- Go 测试重点覆盖：
  - file runtime 对 `read_resp` / DATA 的接线
  - pull 请求导出与本地 base dir 校验
- Kotlin 单测重点覆盖：
  - pull 启动结果解析
  - 本地保存路径展示
- 集成验证：
  - `hubmobile go test`
  - `testDebugUnitTest`
  - `assembleDebug`

#### 可扩展性设计点
- 这层 file runtime 适配后，后续可继续接 `offer`、轻量任务状态、甚至 Win 风格 task 视图。
- 本地下载根目录策略后续可再扩展为用户可配置，而不影响本轮 wire 语义。

#### Issue List
- 无

### Stage 3.1 - Planning
#### Project Goal and Current State
- 当前 Android File 已支持浏览类能力，但没有真正的下载接收链路。
- `await.Client.SetOnFrame` 已经具备可观察 matched frame 的条件，适合接 `subproto/file` handler。
- `subproto/file` handler 默认使用 `file.base_dir + dir/name` 镜像落盘，本轮按这个协议约束设计 Android 下载路径。

#### Docs Governance Routing Decision
- 使用 `$m-docs` 校验计划文档路由、requirements/specs 影响和 lessons 查询入口。
- Requirements impact: `none`
- Specs impact: `none`
- Related requirements: `none`
- Related specs:
  - `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\specs\file.md`
- Related lessons:
  - `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\docs\lessons\android-hubmobile-local-replace.md`
- Related changes:
  - `docs/change/2026-04-02_android-file-module.md`

#### Executable Task List
- [x] `ANDFILEPULL-1`：归档上一轮 `plan.md` 并建立本轮控制文档
- [x] `ANDFILEPULL-2`：为 `hubmobile` 接入 file pull 接收运行时和导出 API
- [x] `ANDFILEPULL-3`：在 Android File 页面补齐下载入口和本地路径反馈
- [x] `ANDFILEPULL-4`：补充 Go / Kotlin 单测并完成本地验证
- [x] `ANDFILEPULL-5`：完成 3.3 自审与 4 阶段归档

#### Task Details
##### ANDFILEPULL-1 - 控制文档切换
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\plan.md`
- Goal: 归档上一轮 File v1 控制文档，并建立本轮 pull/download v1 计划。
- Files / Modules:
  - `plan.md`
  - `docs/plan_archive/plan_archive_2026-04-03_android-file-module-prev.md`
- Acceptance:
  - 旧计划已归档
  - 新计划完整记录 stage 1 / 2 / 3.1
- Test Points:
  - 归档文件存在且可读
- Rollback:
  - 删除新 archive，恢复旧 plan

##### ANDFILEPULL-2 - hubmobile pull 运行时
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\plan.md`
- Goal: 让 Android 侧真正具备 `pull` 控制请求 + `read_resp` / DATA / ACK 接收落盘能力。
- Files / Modules:
  - `hubmobile/file.go`
  - `hubmobile/client.go`
  - `hubmobile/*` 新增 file runtime 适配文件
- Acceptance:
  - 可发起 `pull`
  - 匹配的 `read_resp` 会创建本地接收会话
  - 后续 DATA / ACK 能写盘完成
- Test Points:
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
- Rollback:
  - 回退新增的 file runtime 与 pull 导出改动

##### ANDFILEPULL-3 - Android File 下载 UI
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\plan.md`
- Goal: 在现有 File 页面上增加远端文件下载入口和本地保存路径反馈。
- Files / Modules:
  - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
  - `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
- Acceptance:
  - 远端文件可以点击下载
  - UI 能展示本地下载根目录和预计保存路径
  - 错误信息可见
- Test Points:
  - `testDebugUnitTest`
  - 手工触发下载流程
- Rollback:
  - 回退上述 Android UI / bridge 改动

##### ANDFILEPULL-4 - 测试与验证
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-pull-v1\plan.md`
- Goal: 锁定 pull 关键语义并完成构建回归。
- Files / Modules:
  - `hubmobile/*_test.go`
  - `app/src/test/java/com/myflowhub/android/*`
  - 可能需要 `app/build.gradle.kts`
- Acceptance:
  - Go / Kotlin 单测补齐
  - `assembleDebug` 通过
- Test Points:
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat :app:assembleDebug`
- Rollback:
  - 删除新增测试并回退构建配置

#### Dependencies, Risks, and Notes
- 依赖：
  - `myflowhub-subproto/file@v0.1.4`
  - `await.Client.SetOnFrame`
- 风险：
  - 若本地外部依赖仓版本漂移，AAR 重建可能再次被外部 Server 变更卡住
  - File handler 的假 server 适配若实现不完整，会表现为 ACK 不回或 read_resp 不落盘
- Notes:
  - 继续沿用 `worktrees/MyFlowHub-Server` / `MyFlowHub-SDK` / `MyFlowHub-Proto` junction 方案做本地 Go/AAR 验证

### Stage 3.2 - Implementation Result
#### ANDFILEPULL-2
- `hubmobile/client.go`
  - 为 `await.Client` 安装 `SetOnFrame` 全帧观察回调。
- `hubmobile/file_runtime.go`
  - 新增 Android 侧 file runtime 适配层：
    - 使用 `myflowhub-subproto/file` handler
    - 提供最小 fake `IServer` / parent `IConnection`
    - 通过有界 worker 队列处理 `read_resp` / DATA / ACK`
    - 复用现有 session 发送 ACK，不改 SDK / Server 仓
- `hubmobile/file.go`
  - 新增 `FilePull`
  - 补 `local_base_dir`、目标节点、文件名和 `want_hash` 校验
  - 返回包含 `local_base_dir` / `local_path` 的启动结果 JSON

#### ANDFILEPULL-3
- `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - 反射新增 `FilePull`
- `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
  - 新增下载根目录 helper、本地目标路径推导和 `parsePullStart`
- `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
  - 增加固定下载根目录展示
  - 增加远端文件 `Download` 按钮、确认弹窗和启动反馈
  - 显式限制仅远端文件允许下载

#### ANDFILEPULL-4
- `hubmobile/file_pull_test.go`
  - 覆盖 `FilePull` 参数接线
  - 覆盖 runtime 对 `read_resp` / DATA / ACK 的落盘链路
- `app/src/test/java/com/myflowhub/android/FileProtocolSupportTest.kt`
  - 覆盖下载根目录解析、pull 启动结果解析和本地路径校验
- 验证结果：
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
    - 受 `worktrees/MyFlowHub-Server` 依赖漂移阻塞，未直接通过
  - `cd hubmobile; $env:GOWORK='off'; go test "-modfile=go.verify.mod" -mod=mod ./... -count=1 -p 1`
    - 通过
  - `.\scripts\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar`
    - 受本机 Android SDK 缺少 NDK 阻塞，AAR 未产出
  - `ANDROID_HOME=C:\Users\HelloWorld\AppData\Local\Android\Sdk`
  - `ANDROID_SDK_ROOT=C:\Users\HelloWorld\AppData\Local\Android\Sdk`
  - `.\gradlew.bat testDebugUnitTest`
    - 通过
  - `.\gradlew.bat :app:assembleDebug`
    - 通过

### Stage 3.3 - Code Review
- 需求覆盖：`通过`
  - 已补齐 Android 远端文件下载入口、`pull` 控制请求和本地落盘接收链路
- 架构合理性：`通过`
  - 复用 `subproto/file` handler，未把 Win 任务系统整套移植进 Android
- 性能风险（N+1 / 重复计算 / 多余 I/O / 锁竞争）：`通过`
  - `onFrame` 仅做 file 子协议筛选、复制和有界队列投递
- 可读性与一致性：`通过`
  - Go/Kotlin 新接口命名沿用现有 File 模块风格
- 可扩展性与配置化：`通过`
  - 下载根目录与 UI helper 已独立，后续可扩展为用户可配置
- 稳定性与安全：`通过`
  - 已补 source/hub/target、本地 base dir、远端 name/dir 校验
- 测试覆盖情况：`通过（带环境注记）`
  - Android 单测与 assemble 已通过
  - Go 标准本地命令受外部依赖 worktree 漂移阻塞，已用临时 `modfile` 完成当前仓代码隔离验证
  - 运行时 AAR 重建仍受本机 NDK 缺失阻塞，设备侧需在补齐 NDK 后重新执行 `build_aar.ps1`
- 子Agent治理与审计（任务映射、上下文完整性、文件所有权、结果复核、冲突处理、记录完整性）：`通过`
  - 本轮未使用子 Agent

### Stage 4 - Change Archive
- 使用 `$m-docs` 完成变更归档与 lessons 检查
- Change archive:
  - `docs/change/2026-04-03_android-file-pull-download-v1.md`
- Requirements impact: `none`
- Specs impact: `none`
- Lessons impact: `updated`
- Related lessons:
  - `docs/lessons/android-hubmobile-local-replace.md`

阻塞：否
进入 3.2
