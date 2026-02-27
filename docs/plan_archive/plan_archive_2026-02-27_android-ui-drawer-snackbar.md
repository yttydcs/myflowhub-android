# Android UI：抽屉侧边栏 + Hub/Login 强反馈（Snackbar）+ Hub 启动状态修复

## 目标

- 将 Android App 导航改为更现代的「左侧抽屉（Drawer）」风格（类似 Windows 左侧栏，但在手机上采用可展开抽屉）。
- 为 `Hub` 与 `Login` 页面关键操作增加强反馈：每次点击后必须弹出 `Snackbar`，成功或失败都要明确结果（失败包含原因）。
- 修复 `Hub` 首次点击 `Start` 后 UI 仍显示 `running=false` 的问题：点击一次即可在 **5s** 内自动刷新到 `running=true`（或提示失败/超时）。

## 当前状态

- 现有 UI：
  - 底部 `NavigationBar`（`AppRoot.kt`）。
  - `HubScreen`：点击 `Start` 后立即读取一次 `svc?.getState()`，由于服务执行 `ACTION_START` 是异步的，第一次刷新常读到旧状态，需要第二次点击才看到 `running=true`。
  - `LoginScreen`：结果主要以页面 `Text("Message: ...")` 展示，弹窗反馈不足，且 Go 调用在主线程执行的风险较高。

## 约束与约定

- **必须在本 worktree 内开发**（禁止在 `repo/MyFlowHub-Android` 主 worktree 直接改实现代码）。
- 提示组件统一采用 **Material3 Snackbar**。
- Hub 启动成功判定：以 `HubState.running == true` 为准。
- Hub 启动/停止等待超时：**5 秒**。

## 任务清单（Checklist）

### ANDUI-1：全局 Snackbar 宿主与统一提示入口

- **目标**：在 App 根部提供统一的 `SnackbarHostState` 与 `notify()`/`showSnackbar()` 入口，供各页面调用。
- **涉及文件/模块**
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - （必要时新增）`app/src/main/java/com/myflowhub/android/ui/UiFeedback.kt`
- **验收条件**
  - 任一页面调用 `notify("...")` 能在界面底部弹出 Snackbar。
  - 结果文案清晰：区分成功/失败/进行中（例如 “正在启动…/启动成功/启动失败：xxx”）。
- **测试点**
  - 手动：随便点一个按钮（例如 Login Refresh）可看到 Snackbar。
- **回滚点**
  - 回滚 `AppRoot` 相关改动即可恢复到无 Snackbar 的旧行为。

### ANDUI-2：导航改为左侧抽屉（Drawer）

- **目标**：用 `ModalNavigationDrawer` 替换底部导航栏；提供顶部 AppBar 的菜单按钮打开/关闭抽屉；抽屉中展示各页面入口（Login/Hub/Devices/Logs/Protocols）。
- **涉及文件/模块**
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
- **验收条件**
  - 手机竖屏：默认不常驻侧边栏；点击左上角菜单按钮可打开抽屉。
  - 点击抽屉项后切换到对应页面，且高亮当前项。
- **测试点**
  - 手动：切换 5 个 Tab 正常；旋转屏幕后状态不崩溃（tab 仍可恢复到可用状态）。
- **回滚点**
  - 恢复到原底部 `NavigationBar` 的实现。

### ANDUI-3：Hub Start/Stop 强反馈 + 5s 状态追踪修复

- **目标**
  - 点击一次 `Start` 即可得到即时反馈（Snackbar：正在启动…）。
  - 启动后 5s 内自动刷新并提示最终结果：
    - 成功：`running=true` → Snackbar：启动成功
    - 失败：`lastError` 非空且 `running=false` → Snackbar：启动失败：lastError
    - 超时：5s 仍未 `running=true` 且无明确失败 → Snackbar：启动超时（可提示用户点 Refresh/查看通知/日志）
  - 同理 `Stop`：提示停止中/停止成功/停止失败（如果有错误或超时）
- **实现策略**
  - 使用协程（`rememberCoroutineScope`）+ `Dispatchers.Main` 进行轮询 UI 状态更新；
  - 轮询间隔建议 150–250ms，最多 5s；
  - 轮询读取 `svc?.getState()` 并更新本地 `state`。
  - 操作期间禁用 Start/Stop 防止乱序（或至少对并发点击做取消/覆盖策略）。
- **涉及文件/模块**
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
- **验收条件**
  - 只点一次 Start，5s 内 UI 显示 `Running: true`，并弹出 Snackbar “启动成功”。
  - 启动失败时 Snackbar 必须展示原因。
- **测试点**
  - 手动：无 parent 时、带 parent 时均可启动；连续快速点击 Start 不导致状态错乱或崩溃。
- **回滚点**
  - 去掉轮询与 Snackbar，仅保留原本一次性刷新行为。

### ANDUI-4：Login 全按钮强反馈 + 后台线程执行（避免卡 UI）

- **目标**
  - 对 `Connect/Disconnect/EnsureKeys/Register/Login/ClearAuth/Refresh` 全部增加 Snackbar：
    - 成功：显示成功提示（可带目标 addr、返回 msg 摘要）
    - 失败：显示失败原因（异常 message 或 `go.lastError()`）
  - 将 Go 反射调用放到 `Dispatchers.IO`，避免主线程阻塞导致“按钮点击没反馈/界面卡顿”。
  - 操作期间适度禁用按钮（避免重复点击导致并发/乱序）。
- **涉及文件/模块**
  - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
- **验收条件**
  - 任意按钮点击后必弹 Snackbar；成功/失败信息明确。
  - 操作过程中 UI 不明显卡顿（按钮触摸反馈、输入框滚动正常）。
- **测试点**
  - 手动：未连接时点 Login/Register 给出清晰错误（例如提示先 Connect）。
  - 连接成功后 Register/Login 可更新 nodeId/hubId/role 并提示成功。
- **回滚点**
  - 恢复原同步调用与仅页面文本输出的方式。

### ANDUI-5：验证与回归

- **目标**：确保改动不破坏其他页面的基本可用性。
- **验证步骤**
  - `./gradlew :app:assembleDebug`
  - 安装到真机：
    - 抽屉导航可用
    - Hub Start/Stop 与 Snackbar 逻辑符合验收
    - Login 操作有 Snackbar 且 UI 不卡
- **回滚点**
  - 回滚本分支全部提交即可。

### ANDUI-6：Code Review + 归档

- **目标**
  - 3.3：逐项 Code Review（需求覆盖/架构/性能/可读性/扩展/安全/测试）
  - 4：在 worktree 内新增 `docs/change/YYYY-MM-DD_android-ui-drawer-snackbar.md`
- **涉及文件/模块**
  - `docs/change/`
- **验收条件**
  - Review 结论为“通过”
  - 变更文档包含：背景、内容、任务映射、关键决策、测试结果、影响与回滚

## 依赖关系

- ANDUI-1（全局 Snackbar）应先于 ANDUI-3/4（页面提示）完成。
- ANDUI-2（Drawer）与 ANDUI-1 可并行实现，但建议先搭好 Scaffold/snackbarHost 再替换导航，减少一次性大改风险。

## 风险与注意事项

- **性能/交互风险**：Go 调用若仍在主线程，Snackbar/动画也可能“发闷”；必须保证在 `Dispatchers.IO` 执行。
- **并发与乱序**：同一页面多次点击触发多协程并发时，要明确取消/禁用策略，避免提示顺序错乱。
- **轮询策略**：轮询间隔过小会浪费资源；过大则反馈迟钝。建议 200ms 左右，5s 内最多 25 次。

