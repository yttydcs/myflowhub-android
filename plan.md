# Plan - Android：修复 Android 15 前台服务崩溃 + gomobile 反射兼容（v0.1.3）

> 本 workflow 目标：让 `v0.1.2` 在 Android 15（targetSdk=34）上不再因前台服务类型缺失而崩溃，并修复 “Go AAR unavailable（EnsureInit 找不到）”。

## 0. Workflow 信息

- Workflow 名称：`android-fgs-type-gomobile-reflect`
- 分支（本仓）：`fix/android-fgs-type-gomobile-reflect`
- Base：`main`
- Worktree：`worktrees/fix-android-fgs-type-gomobile-reflect`
- 目标 tag：`v0.1.3`

## 1. 背景（问题复现与根因）

### 1.1 Hub Start 闪退（已定位）

- 复现：Android 15 真机，点击 `Hub -> Start`。
- 现象：App 进程崩溃退出。
- logcat（关键）：`android.app.MissingForegroundServiceTypeException: Starting FGS without a type targetSDK=34`
- 根因：`HubService.startForeground(...)` 使用了 2 参数版本，未指定 foreground service type，且 service 未声明 `android:foregroundServiceType`。

### 1.2 Login 提示 Go AAR unavailable（已定位）

- 现象：Login 页提示 `Go AAR unavailable：com.myflowhub.gomobile.hubmobile.Hubmobile.EnsureInit[class java.lang.String]`。
- 根因：Kotlin 侧使用反射 `getMethod("EnsureInit", String::class.java)`，但 gomobile 生成的 Java 方法名可能为 `ensureInit`（或其他大小写规则），导致 `NoSuchMethodException`。

## 2. 目标与非目标

### 2.1 必须达成（验收口径）

1) Android 15 上点击 `Hub -> Start` 不崩溃：
   - Service 能成功 `startForeground`；若后续 Go Hub 启动失败，UI 能看到 `lastError`，但进程不应崩。
2) `GoClientBridge` / `GoHubBridge` 不再因方法名大小写差异导致初始化失败：
   - Login 页不再出现 `Go AAR unavailable`（除非 AAR/so 真缺失）。
3) 发布 `v0.1.3`：推送 tag 后 GitHub Actions 自动产出签名 APK。

### 2.2 不做

- 不新增功能（协议 UI、自动发现、broker 等不在本 workflow 范围）。
- 不做安全加固/权限策略调整（仅满足 Android 平台运行要求）。

## 3. 总体方案（选型理由 / 备选对比）

### 3.1 Foreground Service Type 方案

- 采用：`dataSync`（用户已确认）。
- Manifest：
  - 为 `HubService` 声明 `android:foregroundServiceType="dataSync"`。
  - 增加权限 `android.permission.FOREGROUND_SERVICE_DATA_SYNC`（Android 14+ 要求）。
- 代码：使用 `ServiceCompat.startForeground(..., ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)`，保证在 API>=29 传入 type，低版本安全回退。

备选（不采用）：
- `specialUse`：需要额外配置与更强约束，不适合当前“网络转发/同步”语义。
- 继续 2 参数 `startForeground`：Android 15 + targetSdk 34 会直接抛异常，无法接受。

### 3.2 gomobile 反射兼容方案

- 采用：在 Kotlin 封装一个 `GoReflect`：同一方法名同时尝试 `UpperCamel` 与 `lowerCamel`（仅首字母大小写变体）。
- 覆盖：`EnsureInit/Start/Stop/Status/...` 以及所有 `GoClientBridge` 需要的方法。
- 错误展示：将 `InvocationTargetException` 的 root-cause 提取并显示（避免只看到外层异常）。

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

