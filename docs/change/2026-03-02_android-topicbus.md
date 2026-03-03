# Android：TopicBus（对齐 Win）

## 背景 / 目标

Android 端已有 `Protocols` 通用控制台，但缺少 Win 端同款的 TopicBus 页面（订阅/退订、发布、事件流与详情）。本次变更在 Android 侧边栏新增 `TopicBus` 整页，并实现与 Win 的核心交互一致：

- 订阅列表本地持久化（topics）
- `maxEvents`（默认 500）持久化与裁剪
- publish 支持 JSON / 纯文本（纯文本按 Win 逻辑包装成 JSON string）
- 事件流实时展示，并对高频更新做节流（200ms flush）

## 变更内容

### 新增
- Go（hubmobile）
  - `hubmobile/topicbus.go`：TopicBus subscribe / unsubscribe / publish 的封装（subscribe/unsubscribe 使用 send+await，publish 为 fire-and-forget）。
  - `hubmobile/topicbus_events.go`：TopicBus publish 事件捕获（ring buffer + cursor pull）。
  - `hubmobile/topicbus_events_test.go`：ring buffer 覆盖写与 pull 游标行为测试。
- Android（Compose）
  - `app/src/main/java/com/myflowhub/android/ui/TopicBusScreen.kt`：TopicBus 整页（含自动 resubscribe、事件流 pull、200ms flush）。
  - `app/src/main/java/com/myflowhub/android/ui/TopicBusCards.kt`：TopicBus 页面组件（Control / Publish / Subs / Snapshot / Event Stream / Detail）。

### 修改
- `hubmobile/client.go`
  - 在 `onUnmatchedFrame` 增加 `captureTopicBusUnmatchedFrame()`：仅捕获 `MajorMsg + SubProtoTopicBus + action=publish`。
- `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - 侧边栏新增 Tab：`TopicBus`。
- `app/src/main/java/com/myflowhub/android/Prefs.kt`
  - 新增 TopicBus 偏好：`topicbus.subs`、`topicbus.max_events`（SharedPreferences）。
- `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - 新增反射桥接：TopicBus subscribe/unsubscribe/batch/publish + `TopicBusEventsPull`。
- `plan.md`
  - 更新为本次 TopicBus workflow 的计划与验收口径。

## 计划任务映射（plan.md）
- TB-1：Go - 捕获 TopicBus publish 事件（ring buffer + pull）
- TB-2：Go - TopicBus 请求封装（subscribe/unsubscribe/publish）
- TB-3：Android - Bridge 与偏好存储
- TB-4：Android - 新增 TopicBusScreen（整页复刻 Win）
- TB-5：Android - 侧边栏入口接入
- TB-6：本地验证（手工）

## 关键设计决策与权衡
- **事件流采用 pull**：沿用 Android 端 VarStore 的模式（Go 侧 ring buffer + Kotlin 定时拉取），避免 Go→Kotlin push 的跨语言回调复杂度与生命周期风险。
- **性能**：
  - ring buffer 有容量上限（默认 2000）；
  - 单条 payload 限制（32KB），超限写入 dropped 占位，避免 OOM；
  - Kotlin 端对事件列表采用 200ms flush 批量更新，降低重组频率。
- **publish 为 fire-and-forget**：与 Win 对齐，避免等待不存在的 `publish_resp` 导致 UI “点击后很久才结束”。

## 测试与验证

### Go（hubmobile）
- `GOWORK=off go test ./...`（在 `hubmobile/` 下执行）通过。

### Android（本地编译）
- `./gradlew.bat :app:assembleDebug` 通过。

## 潜在影响
- TopicBus 高频 publish 时 UI 仍会有持续刷新（已做节流），但若事件量极大仍可能带来耗电与滚动性能压力；可通过调小 `maxEvents` 缓解。

## 回滚方案
- revert 本分支涉及 TopicBus 的提交（Go：`topicbus*.go` + `client.go`；Android：`TopicBusScreen.kt/TopicBusCards.kt` + `Prefs/Bridge/AppRoot`）。

