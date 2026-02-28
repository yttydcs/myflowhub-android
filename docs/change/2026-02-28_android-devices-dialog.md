# Android：移除系统标题栏 + Devices 弹窗详情/编辑（对齐 Win）

日期：2026-02-28  
分支：`fix/android-devices-dialog`  

## 背景 / 目标

### 背景

- Android 顶部存在系统 ActionBar（黑底白字 `MyFlowHub`），与应用内 Compose TopBar（同样显示 `MyFlowHub`）重复。
- `Devices` 页面顶部存在 `Direct/Subtree` 两套入口且交互复杂；同时页面在宽屏/窄屏下使用“双栏/上下堆叠”展示详情，不符合当前期望的 Win 交互。

### 目标

- 移除系统 ActionBar，仅保留应用内 Compose TopBar。
- `Devices` 页面交互对齐 Win：
  - 顶部只保留 `Root node id` 输入框 + `Load` 按钮（同一行）。
  - 左侧展开/收起；点击标题弹出详情；右侧 `Edit` 弹出编辑窗口。
  - 取消双栏详情面板，统一使用弹窗（Dialog）。
- `Login` 页面增强诊断：登录后显示 `node_id`（避免只看到 `Hub X`）。

## 具体变更内容

### 1) 移除系统 ActionBar（黑底白字标题）

文件：`app/src/main/AndroidManifest.xml`

- 将 `application` 主题从 `@android:style/Theme.DeviceDefault` 切换为 `@android:style/Theme.DeviceDefault.NoActionBar`。
- 结果：系统 ActionBar 不再渲染，避免与 Compose TopBar 重复。

### 2) Devices：顶部简化（Root + Load 单行）并移除 Direct/Subtree

文件：`app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`

- 移除 `Direct/Subtree` 模式切换，固定使用 `listNodes` 分层展开。
- 顶部 Card 仅保留：
  - `Root node id` 输入框（为空时默认使用 `hub_id`）
  - `Load` 按钮

### 3) Devices：节点行交互对齐 Win（展开 / 详情 / Edit）

文件：`app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`

- 节点行（Tree row）行为：
  - 左侧按钮：展开/收起（Duplicate 节点禁用展开，避免环导致死循环）。
  - 点击标题：弹出 `NodeDetailsDialog`，展示 nodeInfo（items）。
  - 右侧 `Edit`：弹出 `NodeEditDialog`，提供 Config 的 `List/Get/Set`。
  - 错误节点：提供 `Retry` 重试加载 children。
- 对“异常 Node 1”类现象的处理：
  - 真实拓扑可能存在环/回边（例如 Root=1 的子树中再次出现 Node 1）。
  - UI 以 `Duplicate` 标记并禁用展开，避免无限递归；该情况不再视为异常数据。

### 4) Login：展示 node_id（增强诊断）

文件：`app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`

- 登录后在页面底部 chips 增加 `Node {node_id}` 展示，并保留 `Hub {hub_id}` 与 `role`。

## 与 plan.md 任务映射

- Task 1：移除系统 ActionBar ✅
- Task 2：Devices 顶部 Root+Load 单行，移除 Direct/Subtree ✅
- Task 3：Devices 节点行交互对齐 Win ✅
- Task 4：详情弹窗（NodeInfo）✅
- Task 5：编辑弹窗（Config List/Get/Set）✅
- Task 6：Login 展示 NodeId ✅
- Task 7：构建与冒烟验证 ✅（构建通过；真机冒烟需人工执行）

## 关键设计决策与权衡

- 顶部标题栏移除采用系统 `NoActionBar` 主题：
  - 优点：改动最小，不引入额外主题/依赖。
  - 注意：系统状态栏样式仍由系统主题控制；如后续需要更精细的 system bar 控制，可再单独开 workflow。
- Devices 详情/编辑采用 Dialog 而非双栏：
  - 优点：窄屏体验一致；与 Win “点击弹窗”交互更一致。
  - 代价：需要在 Dialog 内提供滚动以避免内容溢出（本次已实现）。

## 测试与验证方式 / 结果

- 构建：
  - `./gradlew :app:assembleDebug` ✅
- 真机冒烟（建议你执行）：
  1. 启动 App：确认系统黑色 `MyFlowHub` 标题栏消失（只剩应用内 TopBar）。
  2. Login：登录后确认页面显示 `Node <id>` 与 `Hub <id>`。
  3. Devices：不输入 Root 直接 Load（默认 hub_id）；展开节点；点击标题弹出详情；点击 Edit 弹出编辑窗口并验证 `List/Get/Set`。

## 潜在影响与回滚方案

- 潜在影响：
  - Dialog 交互替代双栏后，详情/编辑改为弹窗；若用户习惯旧双栏，需要适应。
  - 大量展开节点时仍可能带来 UI 压力（与之前类似），建议按需展开。
- 回滚：
  - `git revert` 本次变更提交即可回到旧的 ActionBar/Devices 双栏实现。

