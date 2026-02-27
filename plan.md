# Plan - Android：Snackbar 及时替换 + Hub/Login/Devices 视觉与交互优化（v0.1.5）

> 本 workflow 目标：解决 Snackbar 提示“排队导致延迟/无法被新提示打断”的问题，并对 `Hub / Login / Devices` 三个页面做更现代、更清晰的 UI 设计与交互细化（含加载态、状态展示、信息层级）。

## 0. Workflow 信息

- Workflow 名称：`android-ui-polish-snackbar`
- 分支（本仓）：`feat/android-ui-polish-snackbar`
- Base：`main`
- Worktree：`worktrees/feat-android-ui-polish-snackbar`
- 目标版本：`v0.1.5`（待用户确认发布节奏；本计划先完成功能与体验）

## 1. 背景与问题

### 1.1 Snackbar 提示延迟/无法打断

- 现象：例如 `Register / Login`，点击后提示与结果提示存在明显延迟，且看起来不会被新的提示中断。
- 初步根因：`SnackbarHostState.showSnackbar()` 会挂起直到当前 Snackbar 消失；连续调用会“排队等待”，导致结果提示晚于预期出现。

### 1.2 Hub/Login/Devices 页面观感与信息层级偏弱

- 现状：字段与按钮堆叠，缺少分组、状态可视化（badge/chip）、加载态与错误态的视觉区分；信息密度与可读性不佳。

## 2. 目标与范围

### 2.1 必须达成（验收口径）

1) Snackbar 行为改为“最新优先”：
   - 新提示到来时，**立即替换**当前 Snackbar（不排队等待）。
   - 对“进行中”提示支持常驻（Indefinite），在结束时由结果提示替换。
2) Hub 页面：
   - `Start/Stop` 期间有明确加载态（按钮禁用 + 进度提示/指示器）。
   - 5s 状态轮询逻辑保留，但呈现更清晰（Running/NodeID/Parent/LastError 分组展示）。
3) Login 页面：
   - `Connect/Disconnect/Register/Login/EnsureKeys/ClearAuth` 交互更清晰：输入校验、加载态、成功/失败提示层级明确。
4) Devices 页面：
   - 结构更清晰（树/详情/配置分区），并对 loading/error 提供更明显的视觉反馈。

### 2.2 可选（若不引入额外不确定性）

- 在宽屏/横屏下采用“双栏”布局（左树右详情）提升类似 Windows 的体验。
- 抽屉菜单项补充图标与副标题（提升可扫读性）。

### 2.3 不做

- 不更改业务协议与 Go 侧接口。
- 不新增复杂主题系统（仅使用 Material3 组件与现有 MaterialTheme）。

## 3. 方案设计（执行策略）

### 3.1 Snackbar 统一管理：Replace 而非 Queue

- 在 `AppRoot` 提供统一的 `UiNotifier`：
  - `show(message, kind)`：对任意新消息先 `dismiss()` 当前 Snackbar，再显示新消息；
  - `kind=Progress` 使用 `SnackbarDuration.Indefinite`；
  - `kind=Result` 使用 `SnackbarDuration.Short`。
- 页面侧仅调用 `ui.progress(...) / ui.success(...) / ui.error(...)`，避免散落的 showSnackbar。

### 3.2 页面 UI 统一风格（Hub/Login/Devices）

- 采用 Material3 的 `Card` 分区 + 小标题 + 辅助说明文本；
- 状态用 `AssistChip`/`FilterChip`（例如 Connected/Running）；
- 操作区：主操作用 `FilledButton`，次操作用 `OutlinedButton`/`FilledTonalButton`；
- 加载态：`LinearProgressIndicator` 或 `CircularProgressIndicator`（尽量不遮挡输入）。

## 4. 任务清单（Checklist）

### ANDUI2-1：实现可替换 Snackbar 的 UiNotifier

- **目标**：新提示立即替换旧提示；支持 progress 常驻与结果替换。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - 新增：`app/src/main/java/com/myflowhub/android/ui/UiNotifier.kt`
- **验收条件**
  - `progress("正在登录…")` 后，`success("登录成功")` 立即替换显示，不等待前一个消失。
- **测试点**
  - 手动：快速连续触发多条提示（Connect → Disconnect → Connect），提示不排队。
- **回滚点**
  - 回滚 `UiNotifier` 引入与 `AppRoot` 注入即可。

### ANDUI2-2：Hub UI 视觉与交互优化

- **目标**：字段/按钮/状态分区；更明显的 loading/error；Snackbar 文案更清晰。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
- **验收条件**
  - Start/Stop 有进度指示；状态信息清晰分组；错误展示醒目且不刷屏。
- **测试点**
  - 手动：Start 成功/失败/超时三种路径均有明确提示。
- **回滚点**
  - 回滚 `HubScreen` UI 结构调整。

### ANDUI2-3：Login UI 视觉与交互优化 + 结果提示无延迟

- **目标**：连接区/身份区/操作区/结果区分组；progress→result Snackbar 及时替换；避免“结果提示晚出现”。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
- **验收条件**
  - Register/Login 的“进行中”提示在操作期间常驻，结束后立刻被结果提示替换。
- **测试点**
  - 手动：参数为空/未 Connect/未填 NodeID 等边界场景提示清晰。
- **回滚点**
  - 回滚 `LoginScreen` 的 UI 与调用包装层。

### ANDUI2-4：Devices UI 视觉与结构优化

- **目标**：树/详情/配置分区清晰；loading/error 更明显；可选宽屏双栏布局。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
- **验收条件**
  - 树节点加载/错误有明显反馈；选中节点信息更易读；配置操作结果明确。
- **测试点**
  - 手动：Load/展开子节点/查看 NodeInfo/Config get-set 流程顺畅。
- **回滚点**
  - 回滚 `DevicesScreen` UI 改动。

### ANDUI2-5：验证与回归

- **构建**
  - `./gradlew.bat :app:assembleDebug`
- **手测**
  - Snackbar：progress 与结果替换无延迟；
  - Hub/Login/Devices：布局更清晰、交互更顺滑、错误更易定位。

### ANDUI2-6：Code Review + 归档

- **Code Review**：按清单逐项给出“通过/不通过”结论。
- **归档**
  - `docs/change/2026-02-27_android-ui-polish-snackbar.md`

## 5. 风险与注意事项

- Go 调用本质是阻塞式：协程取消不一定能中断底层执行；避免并发调用导致状态错乱，保持串行执行与清晰 loading。
- Snackbar 的“替换策略”可能导致用户看不到历史提示；但符合“结果必须第一时间可见”的需求。

## 4. 任务拆分（Checklist）

> 进入 3.2（写代码）后，每次提交必须标注对应 Task ID。

### ANDFIX-1：补齐前台服务类型声明

- 目标：消除 `MissingForegroundServiceTypeException`。
- 变更范围：
  - `app/src/main/AndroidManifest.xml`
- 验收：Android 15 点击 Start 不再触发该异常。
- 测试点：`adb logcat` 无该异常；通知常驻显示。
- 回滚点：回退 manifest 变更（不建议；会复现崩溃）。

### ANDFIX-2：HubService 使用带 type 的 startForeground

- 目标：在 API>=29 明确传入 `FOREGROUND_SERVICE_TYPE_DATA_SYNC`；兼容 minSdk=26。
- 变更范围：
  - `app/src/main/java/com/myflowhub/android/HubService.kt`
- 验收：Android 15 启动 service 成功，App 不崩；通知常驻。
- 测试点：点击 Start/Stop 多次；后台驻留 30s 以上不退出。
- 回滚点：回退到旧实现（会复现崩溃）。

### ANDFIX-3：gomobile 反射方法名兼容 + 更清晰错误

- 目标：解决 `EnsureInit` 等方法找不到导致的 `Go AAR unavailable`。
- 变更范围：
  - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - `app/src/main/java/com/myflowhub/android/HubBridge.kt`（`GoHubBridge`）
  - （可选新增）`app/src/main/java/com/myflowhub/android/GoReflect.kt`
- 验收：Login 页不再出现 `Go AAR unavailable`；可正常执行 `EnsureKeys/Connect` 等。
- 测试点：冷启动 App；切到 Login 页观察；执行 `EnsureKeys`。
- 回滚点：回退到旧反射实现（会复现 NoSuchMethod）。

### ANDFIX-4：本地构建与冒烟验证（开发机）

- 目标：保证能构建出可安装 APK，并在真机复现通过。
- 变更范围：无（仅命令执行）。
- 验收：
  - `./gradlew :app:assembleDebug`
  - 真机 Android 15：Start 不崩；Login 无 Go AAR unavailable。
- 测试点：见 ANDFIX-2/3。
- 回滚点：无需（验证失败则回到 ANDFIX-1~3 修正）。

### ANDFIX-5：Code Review（按清单输出通过/不通过）

- 目标：确保修复不引入回归，且符合架构/性能/可维护性要求。

### ANDFIX-6：归档变更（docs/change）

- 目标：生成可审计归档文档。
- 变更范围：`docs/change/YYYY-MM-DD_android-fgs-type-gomobile-reflect.md`
- 验收：文档包含背景/变更/任务映射/验证/回滚。

### ANDFIX-7：发布 v0.1.3（tag 触发 release action）

- 目标：GitHub Release 产出 signed APK。
- 验收：Release 资产中包含 `app-release.apk`、`myflowhub.aar`、`build-info.txt`。

## 5. 依赖关系

- ANDFIX-1/2 必须先完成（否则 Android 15 仍会崩）。
- ANDFIX-3 可并行，但建议与 ANDFIX-1/2 一起完成后做真机冒烟。
- ANDFIX-6 在 Code Review 通过后进行。
- ANDFIX-7 在合并 main 并 push 后进行。

## 6. 风险与注意事项

- FGS type 选择需与实际行为匹配：本次选择 `dataSync`，后续若扩展为媒体播放/定位等需调整。
- gomobile 生成规则可能随版本变化：本次只做“首字母大小写”兼容，若未来规则更复杂需再扩展。

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

