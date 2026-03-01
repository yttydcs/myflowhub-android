# MyFlowHub Android：新增 VarStore（对齐 Win：订阅 + 自动更新 + value 直显）

分支：`feat/android-varstore`

## 目标

1) **新增侧导航 Tab：VarStore**

- 宽屏（`NavigationRail`）与窄屏（`Drawer`）均新增入口。
- 页面结构与 Win 端 VarPool（VarStore）尽量一致（My Variables / Watched Variables + 弹窗编辑）。

2) **列表必须直接看到 value**

- My Variables / Watched Variables 的卡片内直接展示 value，不依赖点进详情。
- 采用 `list -> get` 批量拉取，并做并发限流避免卡顿。

3) **订阅 + 自动更新**

- 支持 `subscribe/unsubscribe`，并展示订阅状态（subKnown/subscribed）。
- 自动消费服务端通知（`var_changed/var_deleted/notify_set/notify_revoke` 等）更新 UI。

4) **提前限制变量名**

- 在 Android 端提前校验：`^[A-Za-z0-9_]+$`，不合法直接提示，不发请求。

## 当前状态

- Android 端已有 `Protocols` 通用控制台，可手工 `SendAndAwait(subProto=3)`，但缺少：
  - “对齐 Win 的 VarStore 专用 UI”
  - “列表直显 value”
  - “订阅 + 自动更新”
  - “watch list 持久化”

## 约束与约定

- 仅在本分支实现：`feat/android-varstore`。
- 提交信息使用中文（允许 `feat:` 前缀为英文）。
- Kotlin 与 Go 之间继续使用“JSON 字符串 API”，保持 gomobile 兼容稳定。
- 性能约束：
  - 禁止全量 N+1 造成 UI 卡顿：`list -> get` 必须并发限流。
  - 自动更新不得高频自旋：空拉取需退避（delay/backoff）。
- 交互约束：
  - 每个动作必须有明确提示：成功/失败都要 snackbar（复用 `UiNotifier`）。
  - 新操作应可打断旧操作（避免“提示延迟且无法中断”）。

## 任务清单（Checklist）

### Task ANDVS-1：Go（hubmobile）封装 VarStore 请求 API（list/get/set/revoke/subscribe/unsubscribe）

- **目标**
  - 提供 VarStore 的专用导出函数，统一超时/错误处理，便于 Kotlin 直接调用。
  - `unsubscribe` 的 await 目标 action 按服务端行为使用 `subscribe_resp`（与 Win 端一致）。
- **涉及文件**
  - `hubmobile/varstore.go`（新增）
  - `hubmobile/client.go`（复用/补齐公共 helper，如有必要）
- **验收条件**
  - Kotlin 可调用：`VarStoreList/Get/Set/Revoke/Subscribe/Unsubscribe`，失败时抛出明确错误信息。
  - 输入校验：source/target/owner/subscriber/visibility 基本合法性；服务端错误能透传到 UI。
- **测试点**
  - `cd hubmobile; $env:GOWORK='off'; go test ./...`
- **回滚点**
  - revert 本任务提交。

### Task ANDVS-2：Go（hubmobile）新增 VarStore 事件缓冲 + Pull API（自动更新基座）

- **目标**
  - 在 `onUnmatchedFrame` 中识别 `SubProtoVarStore=3` 的通知帧，解析并写入 ring-buffer。
  - 提供 `VarStoreEventsPull(cursor,limit)` 类似 `LogsPull` 的分页拉取接口。
- **涉及文件**
  - `hubmobile/client.go`
  - `hubmobile/varstore_events.go`（新增：ring-buffer + pull）
- **验收条件**
  - 触发 varstore 变更后，`VarStoreEventsPull` 能拉到事件（至少包含 action + data 原文）。
- **测试点**
  - 单测：ring-buffer pull 的 cursor/limit 边界、覆盖写入、空结果处理。
- **回滚点**
  - revert 本任务提交。

### Task ANDVS-3：Kotlin：补齐 Bridge + Prefs（watch list 持久化）

- **目标**
  - `GoClientBridge` 增加反射方法：VarStore* 与 VarStoreEventsPull。
  - `Prefs` 增加 watch list 的 load/save（仅存 `{name, owner}` 列表）。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - `app/src/main/java/com/myflowhub/android/Prefs.kt`
- **验收条件**
  - watch list 重启后仍可恢复；Bridge 调用方法名稳定。
- **测试点**
  - 手动：添加 watch → 重启 app → watch 仍在。
- **回滚点**
  - revert 本任务提交。

### Task ANDVS-4：Compose：新增 VarStore 页面（对齐 Win）+ 订阅 + 自动更新 + 取消机制

- **目标**
  - `AppRoot` 新增 `VarStore` Tab，并实现 `VarStoreScreen`：
    - Status（TargetID、LastFrame、watch 数量等）
    - My Variables / Watched Variables 卡片：直显 value + 操作按钮（Refresh/Edit/Revoke/Remove/Subscribe）
    - Add/Edit 弹窗：按 Win 结构（可直接编辑 value/visibility/type）
  - 提前变量名校验：`^[A-Za-z0-9_]+$`
  - 自动更新：后台协程循环 `VarStoreEventsPull` → 更新 store → 刷新 UI
  - 交互打断：新动作发起时取消旧动作（避免 snackbar 与 busy 状态滞留）
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - `app/src/main/java/com/myflowhub/android/ui/VarStoreScreen.kt`（新增）
- **验收条件**
  - Connect+Login 后进入 VarStore：
    - Refresh 可看到列表，且卡片内直接看到 value。
    - subscribe/unsubscribe 可用，状态可见（Subscribed badge / 按钮文案变化）。
    - 另一端 set/revoke 后，本端无需手动刷新即可看到变更/删除。
  - 输入非法 name 会被前置拦截并提示。
- **测试点**
  - 多变量时：滚动流畅；并发 get 受控；不会一次性卡住 UI。
  - 连续快速点击不同操作：旧操作会被取消，提示不会长时间延迟。
- **回滚点**
  - revert 本任务提交。

### Task ANDVS-5：构建与冒烟验证

- **构建**
  - 生成 AAR（如需更新）：`./scripts/build_aar.ps1`
  - 构建 APK：`./gradlew :app:assembleDebug`
- **冒烟步骤**
  1. Connect + Register/Login（已有流程）拿到 nodeId/hubId。
  2. VarStore：Refresh → My Variables 出现并直显 value。
  3. Add/Edit/Revoke：成功/失败均有 snackbar。
  4. Subscribe：在另一端触发 set/revoke，本端自动更新。
- **通过标准**
  - 无崩溃；提示及时；自动更新生效。

### Task ANDVS-6：Code Review + 归档

- 进行 3.3 Code Review（逐项结论：通过/不通过）。
- 创建 `docs/change/YYYY-MM-DD_android-varstore.md`，映射 ANDVS-1~ANDVS-5，包含验证与回滚方案。

## 风险与注意事项

- `list` 仅返回 names，为满足“直显 value”必然产生 `list -> get*N`：必须并发限流，并在 UI 侧可取消。
- 变量名规则与用户期望（如 `status.flag`）可能不一致：已按你确认的“提前限制”策略处理。
- 自动更新依赖 unmatched frame 采集：需保证不对所有协议做重度解析；仅 varstore 且输出受 ring-buffer 上限约束。

