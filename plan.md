# Android：TopicBus（完全对齐 Win）

## 目标
- 在 Android 侧边栏新增一个独立 Tab：`TopicBus`（整页），功能与 Win 的 `TopicBus` 页面一致：订阅/退订 topic、发布事件、查看事件流与详情。
- 关键行为对齐 Win：
  - `maxEvents` 默认 500，且可持久化。
  - 订阅列表本地持久化（Win 也是本地为准，不依赖 `list_subs`）。
  - Event Stream UI 更新节流（Win 为 200ms flush）。
  - payload：若不是合法 JSON，则包装成 JSON string（Win 的 normalizePayload）。

## 当前状态
- Android 端目前只有 `Protocols` 通用控制台（可手工拼 TopicBus 协议），但缺少 Win 风格的 TopicBus 页面与事件流。
- Android 端已有 VarStore 的“事件捕获 + ring buffer + Kotlin 轮询 pull”模式，可复用。

## 约束 / 原则
- 本 workflow 在独占分支 `feat/android-topicbus` + 独占 worktree 完成，不在主 worktree 直接写实现代码。
- 仅实现 TopicBus 相关：避免顺手改动其它模块。
- 所有关键操作必须有成功/失败提示（SnackBar 语义与现有页面一致）。

## 方案概述（对齐 Win 的实现方式）
- Go（`hubmobile`）：
  - TopicBus 请求封装：subscribe/unsubscribe/batch/resubscribe（send+await，默认 8s），publish（fire-and-forget send）。
  - TopicBus 事件捕获：在 `onUnmatchedFrame` 里捕获 `SubProtoTopicBus + action=publish`，写入 ring buffer；Kotlin 轮询 `TopicBusEventsPull()` 拉取。
- Kotlin（Compose）：
  - 新增 `TopicBusScreen.kt`：布局与交互对齐 Win（Control/Publish/Subscription List/Snapshot/Event Stream/Event Detail）。
  - 偏好持久化：topics + maxEvents（SharedPreferences，JSON array + int）。
  - 事件列表更新节流：200ms flush，避免高频重组导致卡顿。

## 任务清单（Checklist）

### TB-1：Go - 捕获 TopicBus publish 事件（ring buffer + pull）
- 目标：Android 能稳定、低开销地看到 TopicBus publish 的事件流。
- 涉及文件：
  - `hubmobile/client.go`（在 `onUnmatchedFrame` 追加捕获）
  - `hubmobile/topicbus_events.go`（新增）
  - `hubmobile/topicbus_events_test.go`（新增）
- 设计要点：
  - 仅处理：`hdr.Major()==MajorMsg` 且 `hdr.SubProto()==topicbus.SubProtoTopicBus` 且 `action==publish`。
  - 单条 payload 上限（建议 32KB）+ dropped 标记，避免 OOM。
  - pull 协议对齐 VarStore：`cursor/next_cursor/has_more/events[]`。
- 验收：
  - `TopicBusEventsPull(\"0\",\"200\")` 返回结构稳定；cursor 递增；limit 生效；超限事件被 dropped。
- 测试点：
  - ring buffer 覆盖写 + pull 游标边界；hasMore 逻辑。
- 回滚点：
  - revert `topicbus_events.go` 与 `client.go` 相关变更。

### TB-2：Go - TopicBus 请求封装（subscribe/unsubscribe/publish）
- 目标：Android TopicBus 页面无需手工拼协议，即可完成所有操作。
- 涉及文件：
  - `hubmobile/topicbus.go`（新增）
  - `hubmobile/topicbus_test.go`（可选：若可稳定 mock，优先补最小单测；否则手工验收）
- 设计要点：
  - subscribe/unsubscribe：`sendAndAwait()`，expect `*_resp`，默认 timeout=8s。
  - publish：`Client.Send()` fire-and-forget（对齐 Win）。
  - payloadText：合法 JSON → raw；否则 `json.Marshal(payloadText)` 包装为 JSON string。
- 验收：
  - 未连接时：返回明确错误 `not connected`。
  - code!=1：返回可读错误（带 msg/code）。
- 回滚点：
  - revert `topicbus.go`。

### TB-3：Android - Bridge 与偏好存储
- 目标：Kotlin 可调用 Go TopicBus API；topics/maxEvents 可持久化。
- 涉及文件：
  - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - `app/src/main/java/com/myflowhub/android/Prefs.kt`
- 验收：
  - 重启 App 后 topics 与 maxEvents 仍保留。
  - Go 方法缺失时能给出明确错误（不 silent fail）。
- 回滚点：
  - revert `GoClientBridge.kt` / `Prefs.kt` 的新增 TopicBus 部分。

### TB-4：Android - 新增 TopicBusScreen（整页复刻 Win）
- 目标：UI/交互与 Win 对齐，可用性与反馈到位。
- 涉及文件：
  - `app/src/main/java/com/myflowhub/android/ui/TopicBusScreen.kt`（新增）
- 设计要点：
  - 操作互斥：点击新操作会取消旧操作（避免“提示延迟/不被新操作中断”）。
  - Event Stream：轮询 pull + 200ms flush；maxEvents 裁剪；支持按 topic 过滤；可查看详情。
  - Subscribe/Unsubscribe：先保存本地列表；离线/未登录仅提示“已保存列表”。
- 验收：
  - 全部按钮均有成功/失败提示；未连接/未登录提示明确。
  - 事件列表随 publish 变化刷新；过滤/选中/详情正常。
- 回滚点：
  - 删除 `TopicBusScreen.kt` 并移除入口。

### TB-5：Android - 侧边栏入口接入
- 目标：侧边栏出现 `TopicBus` Tab，可进入页面。
- 涉及文件：
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
- 验收：
  - Rail/Drawer 均出现 TopicBus；点击可进入。
- 回滚点：
  - revert `AppRoot.kt` tab 变更。

### TB-6：本地验证（手工）
- 步骤：
  1. `./gradlew :app:assembleDebug`（或对应 Windows 命令）确保编译通过。
  2. 运行 App：连接 Hub → 登录 → TopicBus：Subscribe → Publish → 观察 Event Stream。
  3. 设置 `maxEvents=5` 并持续 publish，确认事件裁剪。
  4. 重启 App，确认 topics/maxEvents 持久化。
- 验收：所有步骤通过；无明显卡顿；无崩溃。
- 回滚点：revert 本分支所有提交。

## 风险与注意事项
- TopicBus 可能高频：必须节流 UI 更新；ring buffer 需限制容量与 payload 大小。
- publish 无响应：必须是 fire-and-forget，否则会因为 await 超时导致“点击后很久才结束”的体验问题。

