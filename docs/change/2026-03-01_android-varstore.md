# 2026-03-01 Android：新增 VarStore（对齐 Win：订阅 + 自动更新 + value 直显）

## 变更背景 / 目标

Android 端此前仅有 `Protocols` 通用控制台可手工发送 VarStore 子协议请求，但缺少：

1) 专用 VarStore UI（对齐 Win 端 VarPool/VarStore 的“我的变量 / 关注变量”结构）
2) 列表中直接看到 value（避免必须进入详情才能确认结果）
3) subscribe/unsubscribe + 自动更新（接收通知帧实时刷新 UI）
4) watch list 持久化（重启后仍保留关注项）
5) 变量名提前限制（减少无效请求与错误噪音）

本次目标是在不修改服务端协议行为的前提下，补齐 Android 端 VarStore 能力，并保证性能（并发限流）与交互可取消（新操作可打断旧操作）。

## 具体变更内容

### 1) Go（hubmobile）：VarStore 请求封装（list/get/set/revoke/subscribe/unsubscribe）

- 新增：`hubmobile/varstore.go`
  - 导出：`VarStoreList/Get/Set/Revoke/Subscribe/Unsubscribe`
  - 统一：
    - 输入校验（source/target/name/owner/subscriber/visibility 等）
    - `IsConnected()` 检查
    - `sendAndAwait` 超时（默认 8s）
    - `VarResp.code != 1` 统一转为 error（msg 为空时给出兜底错误）
  - 兼容服务端行为：`unsubscribe` 的 await 目标 action 使用 `subscribe_resp`（服务端对 unsubscribe 复用 subscribe_resp）

### 2) Go（hubmobile）：VarStore 通知事件缓冲 + Pull API（自动更新基座）

- 新增：`hubmobile/varstore_events.go`
  - 通过 `captureVarStoreUnmatchedFrame` 从 `onUnmatchedFrame` 捕获 VarStore 通知类 action：
    - `var_changed/var_deleted/notify_set/notify_revoke/up_set/up_revoke`
  - 写入 ring-buffer（默认 2000 条）；单条 payload 限制 32KB，超限仅写入 dropped 标记，避免内存风险
  - 导出：`VarStoreEventsPull(cursor,limit) string`（类似 LogsPull 的分页消费模式）
- 修改：`hubmobile/client.go`
  - 在 `onUnmatchedFrame` 末尾调用 `captureVarStoreUnmatchedFrame`
- 新增单测：`hubmobile/varstore_events_test.go`

### 3) Kotlin：Bridge + Prefs（watch list 持久化）

- 修改：`app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - 补齐反射方法：`VarStore*` 与 `VarStoreEventsPull`
- 修改：`app/src/main/java/com/myflowhub/android/Prefs.kt`
  - 新增 `VarStoreWatchKey`
  - 新增 `loadVarStoreWatchList / saveVarStoreWatchList`（SharedPreferences 内存 JSON 数组）

### 4) Compose：新增 VarStore 页面（对齐 Win）+ 订阅 + 自动更新 + 取消机制

- 修改：`app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - 侧导航新增 Tab：`VarStore` → 路由到 `VarStoreScreen`
- 新增：`app/src/main/java/com/myflowhub/android/ui/VarStoreScreen.kt`
  - 页面结构（对齐 Win）：
    - 控制区：Target NodeID + Refresh
    - My Variables / Watched Variables 列表：卡片内直接展示 value
    - Add/Edit 弹窗：直接编辑 value/visibility/type
  - 并发限流：`getMany(..., parallelism=4)`（避免 list->get N+1 卡顿）
  - 取消机制：新操作会 `cancel` 旧 job，并用 `opSeq` token 防止旧结果回写；对 `CancellationException` 不提示错误
  - 自动更新：后台轮询 `VarStoreEventsPull` 消费事件，实时更新本地 state
  - 变量名提前限制：`^[A-Za-z0-9_]+$`（不合法直接提示，不发请求）

## plan.md 任务映射

- ANDVS-1 Go：VarStore 请求封装（完成）
- ANDVS-2 Go：VarStore 事件缓冲 + Pull API（完成）
- ANDVS-3 Kotlin：Bridge + Prefs watch list（完成）
- ANDVS-4 Compose：VarStore Tab + 页面/弹窗 + 并发限流 + 可取消（完成）
- ANDVS-5 构建 + 真机冒烟（本地构建完成；真机待你确认测试）

## 关键设计决策与权衡

1) **自动更新：选择 unmatched-frame 捕获 + ring-buffer + pull**
   - 优点：不需要在 Kotlin 层做底层会话监听；与现有 LogsPull 模式一致；可控内存上限。
   - 权衡：仅能捕获“未被 await 匹配的帧”；因此仅用于“通知类 action”最合适。

2) **性能：列表直显 value 必然 list->get*N**
   - 采用协程 worker + Channel，限制并发（默认 4），避免一次性并发把 UI/网络打满。

3) **交互：可取消与提示一致性**
   - 通过 `opJob.cancel()` + `opSeq token` 实现“新操作打断旧操作”。
   - 对取消不落错误提示，避免“连续点击导致误报失败”的体验噪音。

4) **安全默认**
   - Go 侧：输入校验 + not connected 保护；服务端 code!=1 统一转换为 error。
   - 事件缓冲：payload 上限 32KB，避免意外大包撑爆内存。

## 测试与验证方式 / 结果

- Go 单测：
  - `cd hubmobile; $env:GOWORK='off'; go test ./...`（通过）
- 本地 AAR（gomobile）：
  - `./scripts/build_aar.ps1`（通过；需设置 ANDROID_HOME/ANDROID_SDK_ROOT 指向 SDK）
- APK 构建：
  - `./gradlew :app:assembleDebug`（通过）
- 真机冒烟建议：
  1) Connect + Register/Login（已有流程）
  2) VarStore：Refresh → My Variables 出现并直显 value
  3) Add/Edit/Revoke：成功/失败均有 snackbar；Cancel 能打断当前操作
  4) Subscribe：另一端 set/revoke 后，本端无需手动刷新即可看到变更/删除

## 潜在影响与回滚方案

- 潜在影响：
  - `onUnmatchedFrame` 额外做了 VarStore 通知帧的轻量解析（已限制 action 白名单与 payload 上限）。
  - VarStore UI 会产生 list->get*N 请求；已并发限流但仍需注意变量数量规模。

- 回滚：
  - 直接 `git revert` 本次变更提交即可回退 VarStore UI 与 hubmobile VarStore 能力。

