# Plan - Android：File 模块 v1

## Workflow Information
- Repo: `MyFlowHub-Android`
- Branch: `feat/android-file-module`
- Base: `origin/main@44b0683`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-module`
- Current Stage: `4`

## Stage Records

### Initialization
- guide.md: `not found`
- 控制面仓：`D:\project\MyFlowHub3\repo\MyFlowHub-Android`
- 主执行仓：`D:\project\MyFlowHub3\worktrees\feat-android-file-module`
- 当前 worktree 从 `origin/main@44b0683` 建立

### Stage 1 - Requirements Analysis
#### 目标
- 为 Android 端补齐一个可正式使用的 File 模块入口，至少让用户不再依赖通用协议控制台，就能完成文件目录浏览、文本预览和目录创建。

#### 范围
- 必须
  - 新增独立 `File` 页面并接入主导航。
  - 基于已存在的 `sendAndAwait` 能力，跑通 `file` 子协议的 `read(op=list)`、`read(op=read_text)`、`write(op=mkdir)`。
  - 支持目标节点输入、目录进入/返回、刷新列表、文本预览、新建目录。
  - 对登录缺失、目标节点非法、响应失败、返回格式异常给出明确错误。
  - 补充纯逻辑单元测试，覆盖路径规范化、目录名校验、响应解析。
- 可选
  - 适度保留高级调试入口，继续保留 `Protocols` 通用控制台。
- 不做
  - 不做 Win 端已有的上传、下载、offer、task 列表、自动接收、任务重试/取消。
  - 不做浏览节点持久化、树形节点选择器、后台事件推送。
  - 不做 File 任务事件流和后台传输状态管理。

#### 使用场景
- 用户完成登录后，想直接浏览某个节点的 `file` 目录，而不是手工拼 JSON 发 `SubProto=5`。
- 用户需要在 Android 端快速预览远端文本文件，例如配置片段、日志片段、说明文件。
- 用户需要在目标节点当前目录下创建一个子目录，为后续文件管理做准备。

#### 功能需求
- `File` 页面必须要求已登录的 `source node id`，未登录时不允许发起 file 请求。
- 控制请求必须以当前登录态的 `hubId` 作为 header `TargetID`，实际浏览目标节点放在 file request 的 `data.target` 中。
- 页面上的目标节点默认取当前登录态中的 `hubId`，同时允许用户手工修改。
- 目录列表请求必须走 `action=read`、`op=list`，默认非递归。
- 文本预览请求必须走 `action=read`、`op=read_text`，默认最大预览 64KB。
- 新建目录请求必须走 `action=write`、`op=mkdir`，并对目录名做本地前置校验。
- 点击目录项后必须进入子目录并自动刷新；点击文件项后必须打开预览。
- 返回上级目录、手动刷新、预览关闭后重新打开等基本交互必须正常工作。

#### 非功能需求
- 改动面保持最小，但必须保证 file 控制帧协议语义正确。
- 允许新增 `hubmobile` file 导出 API；但不修改 Server / Proto 规格本身。
- 解析与校验逻辑应提取为纯 Kotlin helper，便于本地 JUnit 验证。

#### 输入输出
- 输入
  - 用户需求：先做 Android `File` 模块。
  - Android 现状：
    - 仅在 `ProtocolsScreen.kt` 中通过通用控制台可手动调用 `SubProto=5`
    - 没有正式的 File 页面和 file 专用桥接方法
  - 相关规格：
    - `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\specs\file.md`
  - 相关基础代码：
    - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
    - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
    - `app/src/main/java/com/myflowhub/android/ui/ProtocolsScreen.kt`
    - `hubmobile/client.go`
- 输出
  - 新的 Android `File` 页面
  - `hubmobile/file.go` 与 `GoClientBridge` 中的 file 专用封装
  - file 响应解析 / 校验 helper 与单元测试
  - `docs/change/2026-04-02_android-file-module.md`

#### 边界异常
- 未登录或 `source node id` 为空时，必须阻止请求并提示需要先登录。
- `target node id` 非正整数时，必须阻止请求。
- 目录名为空、为 `.` / `..`、包含 `/` 或 `\` 时，必须阻止 `mkdir`。
- 若服务端返回 `code != 1`，必须展示服务端消息或包含 code 的默认错误。
- 若响应 JSON 缺失 `data` 或结构异常，必须显式报错，不得静默吞掉。
- 本轮不承诺二进制文件预览，不承诺超大文本流式加载。

#### 验收标准
- 主导航出现 `File` 入口，进入后可操作。
- 在已登录状态下，输入目标节点后可以列出根目录和子目录内容。
- 点击文本文件可看到预览内容，并能显示大小 / 截断状态。
- 输入合法目录名后可以成功创建目录，并在刷新后看到结果。
- `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1` 通过。
- `.\gradlew.bat testDebugUnitTest` 通过。
- `.\gradlew.bat :app:assembleDebug` 通过。

#### 风险
- Android 端仍缺少 Win 的任务系统，因此本轮只覆盖浏览类操作，不覆盖传输闭环。
- 目标节点选择暂时是手工输入，远端节点发现体验仍弱于 Win。
- 由于 Android worktree 下 `hubmobile/go.mod` 的本地 `replace` 默认指向控制面 `repo/` 目录，本地 Go/AAR 验证可能需要额外准备依赖目录镜像。

#### Issue List
- 无

### Stage 2 - Architecture Design
#### 总体方案
- 采用“在 `hubmobile` 新增 file 专用导出 API，再由 Android UI 产品化”的方案：
  - `hubmobile/file.go` 负责按 file 子协议要求补 `KindCtrl` 前缀、写入 `data.target`，并等待 `read_resp/write_resp`。
  - `GoClientBridge` 反射新增 `FileList` / `FileReadText` / `FileCreateDir` 方法。
  - 新建 `FileProtocolSupport.kt`，负责目录规范化、目录名校验和 list/read_text/mkdir 结果解析。
  - 新建 `FileScreen.kt`，承载浏览、预览和建目录交互。
  - `AppRoot.kt` 增加 `File` 导航入口。
- 选型理由：
  - file 子协议控制帧要求 `payload[0]=KindCtrl`，现有通用 `SendAndAwait` 只编码 JSON message，不满足 file await 语义。
  - Win 端现有可用实现也是 file 专用 await 路径：header `TargetID=hubId`，请求体 `data.target=目标节点`。
  - 因此要保证 Android File v1 真正可用，必须在 `hubmobile` 提供 file 专用方法，而不是只在 Kotlin 侧拼 JSON。

#### 备选对比
- 方案 A：只在 Kotlin 侧包装 `sendAndAwait`
  - 优点：Android 代码改动更小
  - 缺点：无法补齐 file 控制帧 `KindCtrl` 前缀，也无法正确复用 Win 现有 file 路由语义
- 方案 B：继续只保留 `Protocols` 通用控制台
  - 优点：零新增接口
  - 缺点：不可用性问题依旧，不能视为正式 File 模块
- 采用方案：`hubmobile` file 导出 API + `GoClientBridge` + 独立 UI

#### 模块职责
- `hubmobile/file.go`
  - 负责 file `read/write` 的请求封装
  - 负责 `KindCtrl` 前缀、`data.target`、`hubID` 路由和同步等待响应
- `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - 反射 `hubmobile` 新导出的 file 方法
- `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
  - 规范化目录路径
  - 校验目录名
  - 解析 `list` / `read_text` / `mkdir` data 响应
  - 提供可单测的纯逻辑
- `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
  - 管理页面状态
  - 发起 list/read_text/mkdir 请求
  - 处理目录导航、预览弹窗、新建目录弹窗和错误提示
- `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - 将 `File` 页面接入现有导航体系
- `app/src/test/java/com/myflowhub/android/FileProtocolSupportTest.kt`
  - 覆盖 helper 纯逻辑和关键错误路径

#### 数据 / 调用流
1. 用户进入 `File` 页面。
2. 页面读取登录态中的 `cfg.nodeId` 作为 `source id`，读取 `cfg.hubId` 作为 header 路由用 `hubId`，并将浏览目标节点默认设为 `cfg.hubId`。
3. 用户点击 `Load` / `Refresh` 时，`FileScreen` 调用 `go.fileList(sourceId, hubId, targetId, dir)`。
4. `GoClientBridge.fileList(...)` 反射调用 gomobile 导出的 `FileList(...)`。
5. `hubmobile/file.go` 构造 `ReadReq{Op=list, Target=targetId, Dir=...}`，编码 `KindCtrl + JSON(message)`，并通过 `hubID` 路由等待 `read_resp`。
6. `FileProtocolSupport.parseList(...)` 解析 `dirs/files` 为 UI entry 列表。
7. 用户点击目录项时更新 `currentDir` 并重复 list；点击文件项时调用 `go.fileReadText(...)`。
8. 用户创建目录时，`FileScreen` 先用 `FileProtocolSupport.requireFolderName(...)` 校验，再调用 `go.fileCreateDir(sourceId, hubId, targetId, ...)`，`hubmobile/file.go` 发送 `write(op=mkdir)` 并等待 `write_resp`，成功后刷新当前目录。

#### 接口草案
- `hubmobile.FileList(sourceID, hubID, targetID, dir string) (string, error)`
- `hubmobile.FileReadText(sourceID, hubID, targetID, dir, name, maxBytes string) (string, error)`
- `hubmobile.FileCreateDir(sourceID, hubID, targetID, dir, name string) (string, error)`
- `GoClientBridge.fileList(sourceId: String, hubId: String, targetId: String, dir: String): String`
- `GoClientBridge.fileReadText(sourceId: String, hubId: String, targetId: String, dir: String, name: String, maxBytes: String = "65536"): String`
- `GoClientBridge.fileCreateDir(sourceId: String, hubId: String, targetId: String, dir: String, name: String): String`
- `FileProtocolSupport.normalizeDir(dir: String): String`
- `FileProtocolSupport.requirePositiveNodeId(raw: String): Long`
- `FileProtocolSupport.requireFolderName(raw: String): String`
- `FileProtocolSupport.parseList(raw: String): FileListResult`
- `FileProtocolSupport.parseReadText(raw: String): FileTextResult`

#### 错误与安全
- 所有请求前必须校验 `source id`、`hub id`、`target id` 和目录名，避免无效请求直接打到网络层。
- `hubmobile` 必须显式补 `KindCtrl` 前缀并等待正确的 `read_resp/write_resp`。
- `hubmobile` 或 Kotlin 侧在响应 JSON 结构异常时，立即抛出明确错误。
- 目录名禁止 `.`、`..` 和路径分隔符，减少路径逃逸类误用。
- 文本预览严格限制最大字节数，避免一次性把大文件内容拉到 UI。

#### 性能与测试策略
- 列表与预览均按用户手势触发，不增加后台轮询。
- 目录列表使用普通 `LazyColumn` 渲染即可，当前范围内无需引入复杂缓存。
- 通过纯 Kotlin helper 提取解析 / 校验逻辑，使用本地 JUnit 覆盖核心规则。
- `hubmobile` 通过本地 `go test` 覆盖编译与依赖回归。
- 验证方式：
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat :app:assembleDebug`

#### 可扩展性设计点
- `hubmobile/file.go` 后续可继续扩展 `pull/offer/tasks`，不影响当前 UI 层接口。
- `FileProtocolSupport` 后续可继续增加 `offer/pull` DTO 解析，而不必把 JSON 解析散落在各页面。
- 当前 `FileScreen` 状态模型可后续替换为 saved browser nodes 或节点树选择器，而不影响 file 协议入口。

#### Issue List
- 无

### Stage 3.1 - Planning
#### Project Goal and Current State
- 当前 Android 端虽然已具备 `file` 协议的底层发送能力，但只有 `ProtocolsScreen.kt` 中的通用控制台可手工调用 `SubProto=5`。
- 与 Win 相比，Android 缺少正式的 File 页面、file 专用桥接封装和最小可用的浏览交互。
- 本轮目标是补齐 File v1，而不是一次性追平 Win 的传输任务体系。

#### Docs Governance Routing Decision
- 使用 `$m-docs` 校验计划文档路由、requirements/specs 影响和 lessons 查询入口。
- Requirements impact: `none`
- Specs impact: `none`
- Related requirements: `none`
- Related specs:
  - `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\specs\file.md`
- Related lessons: `none`
- Related changes:
  - `docs/change/2026-02-27_android-hub-ui-v1.md`
  - `docs/change/2026-03-31_android-rfcomm-basic-usability.md`
  - `docs/change/2026-04-01_android-hub-resilience.md`
- 文档路由：
  - 当前 workflow 控制文档位于 worktree 根 `plan.md`
  - 上一轮控制文档已归档到 `docs/plan_archive/plan_archive_2026-04-02_android-hub-resilience-prev.md`
  - 完成结果归档到 `docs/change/2026-04-02_android-file-module.md`
  - 若本轮形成稳定排障规则，再更新 `docs/lessons`

#### Executable Task List
- [x] `ANDFILE-1`：归档旧控制文档并建立本轮 `plan.md`
- [x] `ANDFILE-2`：新增 `hubmobile` file 导出 API、Android helper 与 `GoClientBridge` 封装
- [x] `ANDFILE-3`：新增 `FileScreen` 并接入 `AppRoot` 导航
- [x] `ANDFILE-4`：补充 `FileProtocolSupportTest` 并完成本地验证
- [x] `ANDFILE-5`：完成 3.3 自审与 4 阶段归档

#### Task Details
##### ANDFILE-1 - 控制文档切换
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-module`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-module\plan.md`
- Goal: 归档上一轮 Hub 健壮性 workflow 的控制文档，并建立本轮 Android File 模块计划。
- Files / Modules:
  - `plan.md`
  - `docs/plan_archive/plan_archive_2026-04-02_android-hub-resilience-prev.md`
- Acceptance:
  - 旧计划已归档
  - 新 `plan.md` 完整记录 stage 1 / 2 / 3.1
- Test Points:
  - 归档文件存在且可读
- Rollback:
  - 还原旧 `plan.md` 并删除本轮 archive

##### ANDFILE-2 - File 协议桥接与解析
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-module`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-module\plan.md`
- Goal: 为 Android File 页面提供明确的 file 专用桥接和纯逻辑解析层。
- Files / Modules:
  - `hubmobile/file.go`
  - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
- Acceptance:
  - `hubmobile` 可直接发起并等待 file list/read_text/mkdir
  - `GoClientBridge` 暴露 file 专用方法
  - code 判定、路径校验和 data 解析逻辑集中在 helper 中
- Test Points:
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
  - `FileProtocolSupportTest.kt`
  - 代码审阅 file 控制帧封装与校验路径
- Rollback:
  - 回退上述文件到当前主线版本

##### ANDFILE-3 - File 页面与导航
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-module`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-module\plan.md`
- Goal: 提供 Android File v1 的正式 UI 入口。
- Files / Modules:
  - `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
- Acceptance:
  - 主导航新增 `File`
  - 支持目标节点输入、目录刷新/进入/返回、文件预览、新建目录
  - 基本错误提示和空状态可用
- Test Points:
  - 代码审阅 Compose 状态流转
  - `.\gradlew.bat :app:assembleDebug`
- Rollback:
  - 回退 `FileScreen.kt` 与 `AppRoot.kt`

##### ANDFILE-4 - 测试与验证
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-module`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-module\plan.md`
- Goal: 为本轮 File v1 的关键解析和校验逻辑提供可重复验证。
- Files / Modules:
  - `app/src/test/java/com/myflowhub/android/FileProtocolSupportTest.kt`
  - `plan.md`
- Acceptance:
  - 单测覆盖目录规范化、目录名校验、list/read_text 解析
  - `hubmobile go test`、`testDebugUnitTest` 与 `assembleDebug` 通过
- Test Points:
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat :app:assembleDebug`
  - `git diff --check`
- Rollback:
  - 回退测试文件与相关实现

##### ANDFILE-5 - 自审与归档
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\feat-android-file-module`
- Plan Path: `D:\project\MyFlowHub3\worktrees\feat-android-file-module\plan.md`
- Goal: 确保本轮 File v1 的实现、验证和归档可审计。
- Files / Modules:
  - `plan.md`
  - `docs/change/2026-04-02_android-file-module.md`
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
- `hubmobile/client.go` 中现有 await client 能力
- Android 登录态 `Prefs.ClientConfig`
- `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\specs\file.md`
- 现有 Compose / Material3 页面结构

#### Risks and Notes
- 当前 File 页面仍依赖手工输入目标节点，体验上还不等于 Win 的节点树浏览。
- 本轮需要新增 `hubmobile/file.go`，因此本地 Go/AAR 验证依赖当前 workspace 中 `MyFlowHub-Server` / `MyFlowHub-SDK` / `MyFlowHub-Proto` 的目录镜像。
- 若后续范围扩展到 `pull/offer/tasks`，需要回到 3.1 重新扩充计划。

#### Parallelism Assessment
- 本轮写集集中在同一 Android app 模块，`GoClientBridge`、helper、UI 和测试耦合较高，不适合并行派发。
- 子Agent：不使用。

#### Issue List
- 无

### Stage 3.2 - Implementation
- `ANDFILE-2`
  - `hubmobile/file.go` 新增 `FileList` / `FileReadText` / `FileCreateDir`，补齐 file 控制帧 `KindCtrl` 前缀和 `hubId -> data.target` 路由语义。
  - `GoClientBridge.kt` 反射新增 file 专用方法。
  - `FileProtocolSupport.kt` 统一承载路径规范化、目录名校验和 list/read_text/mkdir 解析。
- `ANDFILE-3`
  - `FileScreen.kt` 新增 Android File v1 页面，支持目标节点输入、目录进入/返回、文本预览和新建目录。
  - `AppRoot.kt` 新增 `File` 导航入口。
- `ANDFILE-4`
  - `FileProtocolSupportTest.kt` 覆盖路径和解析纯逻辑。
  - `app/build.gradle.kts` 为本地 JVM 单测增加 `org.json:json`。
  - 本地执行 `hubmobile go test`、AAR 构建、`testDebugUnitTest`、`assembleDebug` 全部通过。

### Stage 3.3 - Code Review
- 需求覆盖：通过
  - 已覆盖 File v1 的目录浏览、文本预览和 mkdir，未越界引入传输任务系统。
- 架构合理性：通过
  - 识别并修正了通用 `SendAndAwait` 不适用于 file 控制帧的问题，最终方案与 Win 现有 file await 语义保持一致。
- 性能风险（N+1 / 重复计算 / 多余 I/O / 锁竞争）：通过
  - 请求均按用户手势触发，无新增后台轮询；解析逻辑为常量级 JSON 处理。
- 可读性与一致性：通过
  - file 协议 helper、bridge 和 UI 职责分层清晰，命名与现有 Android 模块一致。
- 可扩展性与配置化：通过
  - `hubmobile/file.go` 与 `FileProtocolSupport.kt` 为后续 `pull/offer/tasks` 扩展保留了入口。
- 稳定性与安全：通过
  - 本地前置校验 `hubId` / `targetId` / 目录名；file 响应 code 和 op 均有显式判定。
- 测试覆盖情况：通过
  - `hubmobile go test`、`testDebugUnitTest`、AAR 构建和 `assembleDebug` 均通过。
  - 残余风险：当前环境未做真机联调验证，目录浏览与文本预览仍缺少设备侧人工冒烟。
- 子Agent治理与审计（任务映射、上下文完整性、文件所有权、结果复核、冲突处理、记录完整性）：通过
  - 未使用子 Agent。

### Stage 4 - Change Archive
- 使用 `$m-docs` 完成变更归档和 lessons 影响校验。
- `docs/change/2026-04-02_android-file-module.md`：已创建
- `docs/lessons/android-hubmobile-local-replace.md`：已更新
- Requirements impact: `none`
- Specs impact: `none`
- Lessons impact: `updated`

阻塞：否
等待用户确认是否结束本轮 workflow
