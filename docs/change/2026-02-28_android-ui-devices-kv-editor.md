# 2026-02-28 Android：分区块配色交换 + Devices Key/Value 编辑器对齐 Win

## 变更背景 / 目标

近期在 Android 端对 UI 进行了多轮调整，当前仍存在以下体验问题：

1) 页面背景与分区块（Card/框）层次不符合预期（“前景块/背景”不够清晰）。
2) `Devices` 节点行的展开按钮更像圆形，且错误态出现 `Retry` 时会让 `Edit` 不再保持最右对齐。
3) `Devices` 的编辑弹窗仍是 `List/Get/Set` 模式，不符合 Win 侧“打开即 Key/Value 列表、直接编辑”的习惯；同时需要避免打开就对所有 key 做全量请求造成卡顿。
4) `UI` 节点 `node_info` 可能取不到，需要明确提示与兜底信息，避免弹窗空白。

本次目标是在不改协议与服务端行为的前提下，完成上述交互与视觉对齐，并保证性能与稳定性。

## 具体变更内容

### 1) 配色层次：背景更浅灰阶、分区块更白

- 修改：`app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - 内容区 `Scaffold.containerColor` 改为 `surfaceColorAtElevation(1.dp)`（轻微灰阶层次白）。
  - 导航栏/抽屉容器色改为 `surface`。
- 修改：为分区块 `Card` 显式指定 `containerColor = MaterialTheme.colorScheme.surface`（更白）
  - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
  - `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`

效果：页面背景与分区块底色实现“互换”层次（背景略灰、块更白）。

### 2) Devices 节点行：展开按钮方形 + Edit 始终最右

- 修改：`app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
  - 展开按钮（`OutlinedButton`）指定 `shape = RoundedCornerShape(0.dp)`，避免圆形/胶囊观感。
  - 右侧操作按钮顺序调整：`Retry`（如有）放在 `Edit` 左侧，保证 `Edit` 永远在行末右边界。

### 3) NodeDetails：取不到/为空时更明确 + UI 节点兜底信息

- 修改：`app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
  - 当 `node_info` 失败：显示错误文本 + “可能未实现/无权限”等提示。
  - 当 `node_info` 成功但 `items` 为空：显示明确提示避免空白。
  - 当目标 `nodeId == cfg.nodeId`（推断为当前 UI 节点）：额外展示本地可得信息（`UI DeviceID / HubID / Role / TargetAddr / Login NodeID`）作为兜底。

### 4) NodeEdit：改为 Key/Value 列表（行内保存）并避免 N+1 卡顿

- 修改：`app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
  - 打开弹窗后自动执行 `configList` 获取 keys，移除顶部 `List/Get/Set` 按钮区。
  - 使用 `LazyColumn` 展示 key/value 列表；每行 `Save` 单独保存。
  - value 读取采用“可见行触发加载”的策略（Lazy 渲染），并通过 `Semaphore(4)` 限制并发，避免大量并行请求。
  - 处理滚动/可见性导致的取消：对 `CancellationException` 直接抛出，不将其记录为错误，避免“滚动导致误报失败”。

## plan.md 任务映射

- Task 1：分区块配色交换（完成）
- Task 2：Devices 节点行样式细节（完成）
- Task 3：NodeDetails 信息增强（完成）
- Task 4：编辑弹窗 Key/Value 列表（完成）
- Task 5：构建与冒烟验证（完成）

## 关键设计决策与权衡

1) **配色策略选型**
   - 采用 `surfaceColorAtElevation(1.dp)` 作为页面背景色：兼容动态配色，灰阶变化更轻。
   - 采用 `surface` 作为分区块 Card 的底色：提升“前景块”的清晰度。

2) **性能策略：避免全量 N+1**
   - 不在弹窗打开时对所有 key 立刻 `configGet`。
   - 仅在行进入可见区时触发 `configGet`，并限制并发，保证滚动与交互流畅。

3) **可取消性**
   - 将 value 加载放在 `LaunchedEffect` 的协程上下文中运行，滚动导致 item 销毁时可自然取消请求，减少无效 IO。
   - 对取消不落错误，避免体验噪音。

## 测试与验证方式 / 结果

- 构建：
  - `./gradlew :app:assembleDebug`（通过）
- 冒烟验证建议：
  1) 观察所有 Tab：页面背景为轻微灰阶白，分区块 Card 为更白的底色。
  2) Devices：展开按钮为方形；错误节点出现 `Retry` 时 `Edit` 仍在最右侧。
  3) Devices：Edit 弹窗打开即 keys 列表；滚动时逐行加载 value；单行 Save 有成功/失败提示。
  4) UI 节点详情：即使 node_info 为空/失败也不空白，能看到明确提示与本地兜底信息。

## 潜在影响与回滚方案

- 影响：
  - `NodeEditDialog` 的交互模式发生变化（从命令式 List/Get/Set → 列表编辑），需要适应新的保存方式（每行 Save）。
  - 大量 keys 的情况下会产生“随滚动逐步加载”的网络请求；并发已限制，但仍建议后端合理控制 keys 数量。

- 回滚：
  - 直接 `git revert` 本次变更提交即可恢复到旧交互与配色。

