# Plan - Android：手机作为 Hub（v1）——登录 + Devices + 日志 + 子协议能力基座

> 说明：本 workflow 目标是让 Android 端具备“作为 Hub + 作为 Client 控制 Hub”的最小闭环能力，并为后续逐个跑通子协议 UI 打好基座。
> - 自动发现：不做（仅手动填写 `ip:port`）
> - broker：不纳入本版本
> - 子协议：目标对齐现状（`auth/exec/file/flow/management/topicbus/varstore`），允许逐步跑通

## 0. Workflow 信息

- Workflow 名称：`android-hub-ui-v1`
- 分支（本仓）：`feat/android-login-devices`
- Base：`main`
- Worktree：`worktrees/feat-android-login-devices`
- 涉及仓库：
  - `MyFlowHub-Android`：Kotlin UI、前台服务、hubmobile（gomobile AAR）
  - 依赖：`myflowhub-server` / `myflowhub-sdk` / `myflowhub-proto`（Go module 依赖；不在本 workflow 直接修改）

## 1. 背景与澄清

- “为什么看起来没有引用 server？”
  - `hubmobile` 的 Go 代码通过 `github.com/yttydcs/myflowhub-server/hubruntime` 引入了 Server 侧的 Hub 与默认子协议实现；但当前只向 Android 暴露了极少数 API（`Start/Stop/Status/EnsureLinked`），所以 UI 侧还无法使用完整能力。
  - 本 workflow 的工作重点不是重写 server，而是：
    1) 把“客户端会话/签名/Send&Await/日志/错误”能力下沉到 Go（AAR）；
    2) Kotlin Compose 做页面与交互，参照 Win 的 `Home/Devices`。

## 2. 目标（验收口径）

### 2.1 必须达成（v1）

1) Hub（本机）：
   - 手机可启动/停止前台 Hub 服务；LAN 可见（沿用现状）。
   - 支持手动填写 `parent ip:port` 建立父子链路（复用/补齐 `EnsureLinked` 相关能力与错误提示）。
2) Client（操作 Hub）：
   - 支持操作 “本机 Hub” 与 “远端 Hub” 两种目标（手动填写 `ip:port`；不做自动发现）。
   - 统一设备身份：同一套 Node Key 用于（本机 Hub）与（Client 登录/签名）。（已确认）
3) 登录页（参照 Win）：
   - 提供：连接、注册、登录；展示本机 `NodeID`、当前连接目标、最近一次错误信息。
4) Devices 页面（原 Management 改名为 Devices，参照 Win Devices）：
   - 显示树（直接子节点）；
   - 显示子树（按需展开/加载）；
   - 选中节点后展示详细信息；
   - 支持查看/编辑节点配置（`config list/get/set`）。
5) 日志：
   - 展示运行日志 + 关键操作日志；
   - Go 侧维护日志 ring buffer，保留 10k 行；Android 可分页读取（cursor/limit）。
6) 子协议能力基座：
   - Go 侧提供通用 `Send&Await`（`subproto+action+payloadJSON`）能力；
   - UI 侧按 “协议独立入口（B）” 提供入口页（可先复用通用控制台组件），允许协议逐步跑通。

### 2.2 不做（明确排除）

- 自动发现（mDNS/广播）。
- broker 子协议及其 UI。
- 日志导出（仅保留查看与 10k ring buffer）。
- 额外安全加固/密钥轮换（后续另开 workflow）。

## 3. 当前状态

- Android 端已具备：前台 Hub Service + 最小启动/停止/状态页面。
- `hubmobile` 目前仅暴露：`Start/Stop/Status/EnsureLinked` 等少量能力；缺少登录、管理协议封装、日志拉取等接口。

## 4. 开发环境注意事项（Worktree）

- `hubmobile/go.mod` 使用 `replace github.com/yttydcs/myflowhub-server => ../../MyFlowHub-Server`
  - CI 环境下（Android repo 与 Server repo 同级）可用；
  - 在本 worktree 目录结构下，为了本地构建 AAR，需要提供 `worktrees/MyFlowHub-Server` 路径。
- 推荐做法（不改代码、不影响 CI）：在 `d:/project/MyFlowHub3/worktrees` 下创建一个目录 junction：
  - `worktrees/MyFlowHub-Server` -> `repo/MyFlowHub-Server`
  - （仅本机开发便利，不提交仓库）

## 5. 计划拆分（Checklist）

> 约定：不得引入计划外改动；若需新增任务，先更新本 plan 并重新确认。

### ANDH1 - 归档旧计划（文档）

- 目标：将上一个 workflow 的 `plan.md`/`todo.md` 归档，避免与本 workflow 混淆。
- 涉及文件：
  - `docs/plan_archive/plan_archive_2026-02-26_android-apk-release-ci.md`（新增/已生成）
  - `docs/plan_archive/todo_archive_2026-02-26_fix-android-ci-gomobile-androidapi.md`（新增/已生成）
  - `plan.md`（本文件）
- 验收条件：归档文件可追溯；本计划可独立执行。
- 回滚点：revert 本任务提交。

### ANDH2 - Go(AAR)：设备身份/会话/鉴权基座

- 目标：
  - 初始化/加载 Node Keys（持久化到 app workdir 下的 `hub/config/`，沿用 server/auth 的文件结构）；
  - 连接/关闭会话（目标 hub：本机/远端）；
  - 注册/登录（参照 Win 签名与 payload 结构）。
- 涉及模块 / 文件（初稿，按实现调整）：
  - `hubmobile/*.go`（新增/修改）
  - （依赖）`myflowhub-sdk/session`、`myflowhub-sdk/await`、`myflowhub-proto/protocol/auth`
  - Android bridge：`app/src/main/java/com/myflowhub/android/HubBridge.kt`
- 验收条件：
  - Go 侧暴露稳定的 Java API：`EnsureKeys`、`Connect`、`Close`、`Register`、`Login`、`GetSelfNodeId`、`GetLastError`
  - 失败时返回可展示的错误信息（不 silent fail）。
- 测试点：
  - Go 单元测试（如可行）：签名/序列化结果一致性、错误分支。
  - Android 手工：输入 `ip:port` -> login 成功/失败提示正确。
- 回滚点：revert 本任务提交。

### ANDH3 - Go(AAR)：Devices（management 协议）封装

- 目标：提供 Devices 页面所需的 management action 封装，并返回 JSON 给 Kotlin 直接渲染。
- 涉及模块 / 文件：
  - `hubmobile/*.go`
  - （依赖）`myflowhub-proto/protocol/management`
- 验收条件：
  - 暴露 API：`ListNodes`、`ListSubtree`、`NodeInfo`、`ConfigList`、`ConfigGet`、`ConfigSet`
  - 性能：避免 N+1（树加载按需；细节仅在选中节点时请求）
- 测试点：
  - Android 手工：能拉到树/子树；详情展示正确；配置改动可生效且有错误提示。
- 回滚点：revert 本任务提交。

### ANDH4 - Go(AAR)：日志 ring buffer（10k 行）

- 目标：将 hubruntime 的 logger 接入可读的 ring buffer，提供分页读取 API（cursor/limit）。
- 涉及模块 / 文件：
  - `hubmobile/*.go`
- 验收条件：
  - 默认保留 10k 行（可配置但不在 UI 暴露）；
  - 读取 API 可增量拉取，不重复/不丢失（在单进程内）。
- 测试点：
  - Go 单元测试：ring buffer 行数上限、cursor 边界。
  - Android 手工：Logs 页面可看到启动/连接/管理操作日志。
- 回滚点：revert 本任务提交。

### ANDH5 - Android(Compose)：导航 + Login 页面

- 目标：实现登录页（参照 Win），并把核心状态（连接目标/登录状态/NodeID/错误）可视化。
- 涉及文件：
  - `app/src/main/java/com/myflowhub/android/MainActivity.kt`（导航入口）
  - `app/src/main/java/com/myflowhub/android/ui/*`（新增：页面/组件）
  - `app/src/main/java/com/myflowhub/android/Prefs.kt`（持久化）
  - `app/src/main/java/com/myflowhub/android/HubBridge.kt`（调用 Go API）
- 验收条件：注册/登录流程可用；能切换操作目标（本机/远端）。
- 测试点：手工按流程验证（见 ANDH8）。
- 回滚点：revert 本任务提交。

### ANDH6 - Android(Compose)：Devices 页面

- 目标：实现 Devices（树 + 详情 + 配置编辑），并将原“Management”更名。
- 涉及文件：
  - `app/src/main/java/com/myflowhub/android/ui/devices/*`
  - （可能）资源/strings：`app/src/main/res/*`
- 验收条件：对齐 Win Devices 的核心体验（树/详情/编辑）。
- 测试点：手工验证（见 ANDH8）。
- 回滚点：revert 本任务提交。

### ANDH7 - Android(Compose)：Logs 页面 + 子协议入口页（B）

- 目标：
  - Logs 页面：分页读取 + 简单刷新；
  - 协议入口：为 `auth/exec/file/flow/topicbus/varstore` 提供独立入口（可先复用通用控制台组件），满足“具备子协议能力”的验证诉求。
- 涉及文件：
  - `app/src/main/java/com/myflowhub/android/ui/logs/*`
  - `app/src/main/java/com/myflowhub/android/ui/protocols/*`
- 验收条件：能看到 10k ring buffer 日志；协议入口可触发至少一条 `Send&Await` 验证。
- 回滚点：revert 本任务提交。

### ANDH8 - 验证步骤（可执行）

- 本地构建：
  - `powershell scripts/build_aar.ps1` 生成 `app/libs/myflowhub.aar`
  - `./gradlew :app:assembleDebug`
- 安装与冒烟：
  - 安装 debug APK 到真机（arm64）；
  - 启动 Hub Service；
  - Login 页连接远端 hub（`ip:port`），执行 register/login；
  - Devices 页：加载树/查看详情/修改一项配置；
  - Logs 页：查看是否记录上述操作日志。

### ANDH9 - Code Review（强制）

- 按全局 3.3 清单逐项审查并输出结论（通过/不通过）。

### ANDH10 - 归档变更（强制）

- 新增 `docs/change/YYYY-MM-DD_android-hub-ui-v1.md`，包含：背景/目标、变更内容、任务映射、关键设计决策（性能/扩展性）、测试与结果、影响与回滚方案。

## 6. 风险与注意事项

- gomobile 限制：部分 Go 包可能在移动端不可用；新增依赖需保持可 bind/可交叉编译。
- 性能：Devices 树加载需避免频繁拉全量子树；日志 ring buffer 需控制内存。
- 可扩展性：协议入口先走通用控制台，后续可替换为更友好的专用 UI，不影响底层 API。

