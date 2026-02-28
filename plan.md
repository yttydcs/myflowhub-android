# MyFlowHub Android：移除系统标题栏 + Devices 弹窗详情/编辑（对齐 Win）

分支：`fix/android-devices-dialog`

## 目标

- 移除系统自带 ActionBar（黑底白字 `MyFlowHub`），避免与应用内 Compose TopBar 重复。
- Devices 页面 UI/交互对齐 Win：
  - 顶部仅保留：`Root NodeId 输入框 + Load 按钮`（同一行）。
  - 节点行：左侧展开/收起；点击标题弹出详情；右侧 `Edit` 弹出编辑窗口。
  - 不再使用双栏/宽屏详情面板，统一使用弹窗（Dialog）展示详情与编辑。
- Login 页面补充显示 `node_id`（避免登录后只看到 `Hub X` 不可诊断）。

## 当前状态

- `AndroidManifest.xml` 当前使用 `@android:style/Theme.DeviceDefault`，会显示系统 ActionBar（黑底白字标题）。
- `DevicesScreen` 当前存在：`Direct/Subtree` 切换 + 双栏详情面板（宽屏）/上下堆叠（窄屏）。
- 真实数据可能存在环：例如 Root=1 时，子树中再次出现 Node 1（应被标记为 Duplicate 并禁止展开，避免死循环）。

## 约束与约定

- 仅在本 worktree 分支实现：`fix/android-devices-dialog`。
- 提交信息使用中文（允许 `fix:` 前缀为英文）。
- Devices 查询模式固定为 `listNodes`（已确认），移除 `Direct/Subtree` UI。
- Dialog 形态：优先使用 Compose Material3 `AlertDialog` / `Dialog`，内容支持滚动；不引入新导航框架。

## 任务清单（Checklist）

### Task 1：移除系统 ActionBar（黑底白字标题）

- **目标**
  - App 顶部仅保留应用内 Compose TopBar。
- **涉及文件**
  - `app/src/main/AndroidManifest.xml`
- **方案**
  - 将 `android:theme` 切换为 `@android:style/Theme.DeviceDefault.NoActionBar`（或 Light 变体，视效果验证）。
- **验收条件**
  - 真机启动后不再出现系统黑色标题栏 `MyFlowHub`。
- **测试点**
  - 多机型/旋转/深浅色模式下观察状态栏与内容布局无异常。
- **回滚点**
  - revert 本任务提交。

### Task 2：Devices 顶部改为 Root + Load 单行，移除 Direct/Subtree

- **目标**
  - 顶部只保留 `Root NodeId` 输入与 `Load`（同一行）。
  - 固定使用 `listNodes`。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
- **验收条件**
  - 页面不再出现 `Direct/Subtree`；Root 输入框与 Load 在同一行。
- **测试点**
  - Root 为空时默认使用 `hub_id`；Root 非法输入提示明确。
- **回滚点**
  - revert 本任务提交。

### Task 3：Devices 节点行交互对齐 Win（展开 / 详情 / Edit）

- **目标**
  - 左侧点击展开/收起（duplicate/无子节点时禁用）。
  - 点击标题打开“详情弹窗”。
  - 右侧 `Edit` 打开“编辑弹窗”。
  - 状态展示：Has children / Not loaded / Duplicate / Error。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
- **验收条件**
  - 可复现 Root=1 场景时：再次出现 Node 1 必须显示为 Duplicate，并禁止展开（不死循环）。
- **测试点**
  - 展开时懒加载子节点；错误节点可提示并支持 Retry（如适用）。
- **回滚点**
  - revert 本任务提交。

### Task 4：详情弹窗（NodeInfo）

- **目标**
  - 点击标题弹出详情：展示 `node_id` + `nodeInfo(items)`；支持滚动与重新加载。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
- **验收条件**
  - 详情弹窗打开/关闭不影响树状态；加载中/错误态提示明确。
- **测试点**
  - 快速重复打开不同节点时，不应出现“旧请求覆盖新节点”的串数据。
- **回滚点**
  - revert 本任务提交。

### Task 5：编辑弹窗（Config List/Get/Set）

- **目标**
  - `Edit` 弹窗用于配置编辑：List/Get/Set；Key/Value 输入；结果强反馈（snackbar + 弹窗内状态）。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
- **验收条件**
  - 选择节点后可 List keys；Get/Set 成功失败均提示明确。
- **测试点**
  - 非法 key / 未选中节点 / 断网等错误提示清晰。
- **回滚点**
  - revert 本任务提交。

### Task 6：Login 页展示 NodeId（增强诊断）

- **目标**
  - 登录后在页面显著展示 `node_id`（并保留 hub_id/role）。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
- **验收条件**
  - 登录成功后能看到 `Node <id>`；清除登录态后消失。
- **测试点**
  - Register/Login/ ClearAuth 后显示符合预期。
- **回滚点**
  - revert 本任务提交。

### Task 7：构建与冒烟验证

- **构建**
  - `./gradlew :app:assembleDebug`
- **冒烟步骤**
  1. 启动：确认系统 ActionBar 不再出现。
  2. Login：确认显示 node_id/hub_id。
  3. Devices：Root 默认=hub_id，Load 后可展开；点击标题弹详情；Edit 弹编辑窗口并可 List/Get/Set。
- **通过标准**
  - 操作反馈及时；无崩溃；树可展开；duplicate 节点不死循环。

### Task 8：Code Review + 归档

- 进行 3.3 Code Review（逐项结论：通过/不通过）。
- 创建 `docs/change/YYYY-MM-DD_android-devices-dialog.md`，映射 Task 1-7，包含验证与回滚方案。

## 依赖 / 风险 / 注意事项

- 真实拓扑可能存在环（如 Node 1 出现在子树中）：UI 侧必须以 Duplicate 方式处理，不能当成“异常数据”强行剔除。
- 弹窗内容可能较长：必须提供滚动，避免小屏溢出。
