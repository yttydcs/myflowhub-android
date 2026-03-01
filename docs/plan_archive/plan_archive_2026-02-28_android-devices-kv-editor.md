# MyFlowHub Android：分区块配色交换 + Devices 编辑器对齐 Win（Key/Value 列表）

分支：`fix/android-devices-editor`

## 目标

1) **分区块配色交换**

- 将“页面背景色”与“分区块 Card（前景块）背景色”对调：
  - 页面背景：使用更浅的灰阶层次（当前分区块/容器的浅灰感）
  - 分区块 Card：使用更白的底色（当前页面背景的白）

2) **Devices 交互细节对齐 Win**

- 左侧展开按钮使用**正方形**（非圆形），视觉更接近 Win 的树控件。
- 节点行右侧 `Edit` 按钮始终**右对齐**（错误态出现 `Retry` 时也不挤占 Edit 的最右位置）。
- `NodeDetails`：当 `node_info` 取不到或为空时给出更明确的原因/提示（尤其 UI node）。

3) **Devices 编辑界面对齐 Win（Key/Value 列表）**

- 打开编辑弹窗后直接展示 `key/value` 列表，value 可直接编辑并保存。
- 移除顶部 `List/Get/Set` 形式按钮（改为自动加载 + 行内保存）。

## 当前状态

- 页面中使用 `Card` 做分区块，但“分区块色阶”和“页面背景”层次不符合预期（需要对调）。
- Devices：
  - 展开按钮使用默认 Button 形状（在方形尺寸下会呈现圆形/胶囊感）。
  - 错误节点出现 `Retry` 时会把 `Edit` 按钮挤出最右对齐位置。
  - 编辑弹窗仍是 `List/Get/Set` 模式，不符合 Win 期望的 key/value 列表编辑。

## 约束与约定

- 仅在本 worktree 分支实现：`fix/android-devices-editor`。
- 提交信息使用中文（允许 `fix:` 前缀为英文）。
- 不修改协议与服务端行为；仅做 Android UI 与客户端交互优化。
- 性能约束：Config 编辑不允许“打开就对所有 key 做全量 N+1 请求”导致卡顿；采用 Lazy/按需加载并限制并发。

## 任务清单（Checklist）

### Task 1：分区块配色交换（Card vs 页面背景）

- **目标**
  - 页面背景与 Card 背景对调，让分区块更“白”，背景更“浅灰”。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
  - `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
- **验收条件**
  - 所有页面分区块 Card 的底色明显更白；页面背景为轻微灰阶层次白。
- **测试点**
  - 深浅色模式下均保持可读性与对比度。
- **回滚点**
  - revert 本任务提交。

### Task 2：Devices 节点行样式细节（方形展开 + Edit 右对齐）

- **目标**
  - 展开按钮为正方形（非圆形）。
  - `Edit` 始终最右对齐；`Retry` 出现时排在 `Edit` 左侧。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
- **验收条件**
  - 任意节点行：`Edit` 的右边界对齐 Card 内容区右边界。
  - 展开按钮视觉为方形按钮。
- **回滚点**
  - revert 本任务提交。

### Task 3：NodeDetails 信息增强（UI node 取不到时更明确）

- **目标**
  - `node_info` 失败/为空时，弹窗内显示更明确的原因（例如“节点不支持 node_info”/“not found”等）。
  - 对于 UI node（`nodeId == cfg.nodeId`）提供最小 fallback 信息展示（deviceId/hubId/role 等），避免“空白无从判断”。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
- **验收条件**
  - UI node 点击详情时，即使取不到 node_info，也能看到明确提示与基础身份信息。
- **回滚点**
  - revert 本任务提交。

### Task 4：Devices 编辑弹窗改为 Key/Value 列表（行内保存）

- **目标**
  - 打开编辑弹窗后自动加载 config keys，并以列表形式展示 key/value。
  - value 可直接编辑；每行提供保存入口（行内 Save）。
  - values 按需加载（Lazy）并限制并发，避免打开就全量请求。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
- **验收条件**
  - 弹窗打开后无需点击 List/Get/Set；直接可编辑并保存。
  - 对 key 数量较多的节点，仍保持滚动流畅；不会一次性触发大量请求卡 UI。
- **测试点**
  - Key 数量多时：滚动时逐行加载 value；错误时行内提示可重试；保存成功/失败 snackbar 明确。
- **回滚点**
  - revert 本任务提交。

### Task 5：构建与冒烟验证

- **构建**
  - `./gradlew :app:assembleDebug`
- **冒烟步骤**
  1. 对比配色：页面背景与 Card 色阶符合“交换”预期。
  2. Devices：展开按钮方形；`Edit` 始终右对齐；错误节点仍可 Retry。
  3. Devices：Edit 弹窗展示 key/value 列表，可编辑并保存。
  4. Devices：点击 UI node 详情时，有明确提示或 fallback 信息。
- **通过标准**
  - 以上步骤无崩溃，操作反馈及时。

### Task 6：Code Review + 归档

- 进行 3.3 Code Review（逐项结论：通过/不通过）。
- 创建 `docs/change/YYYY-MM-DD_android-devices-editor.md`，映射 Task 1-5，包含验证与回滚方案。

## 风险与注意事项

- Config values 若数量很大：需要 Lazy 加载 + 并发限制，否则会产生 N+1 性能风险。
- 部分节点（例如 UI node）可能不实现 management/node_info：需要明确提示而不是“空白”。

