# Android：抽屉导航 + Hub/Login 强反馈（Snackbar）+ Hub 启动状态刷新

## 背景 / 目标

- 现状问题：
  - `Hub` 页面点击第一次 `Start` 时，UI 常仍显示 `running=false`，需要第二次点击才会变 `true`（状态读取时机早于 Service 完成启动）。
  - `Login` / `Hub` 页面按钮点击反馈弱，成功或失败都不够明确。
  - 导航样式偏旧（底部 Tab），希望更现代化并更接近 Windows 的左侧栏体验。
- 目标：
  - 导航改为左侧抽屉（Drawer）。
  - `Hub` / `Login` 关键操作统一弹出 `Snackbar`，成功/失败/超时均有明确提示。
  - `Hub`：点击一次 `Start` 后 **5s** 内自动刷新到 `running=true`（或失败/超时提示）。

## 具体变更内容

### 新增 / 修改

- 修改导航为 `ModalNavigationDrawer`（左侧抽屉）+ `TopAppBar` 菜单按钮：
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
- 新增全局 `SnackbarHostState`，并下发 `notify(message)` 给页面：
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
- `Hub` 页面：
  - `Start/Stop` 增强为：点击后立即提示“正在启动/停止…”，并在 5s 内轮询 `HubService.getState()` 更新 UI，最终提示成功/失败/超时。
  - `getState()` 调用放到 `Dispatchers.IO`，避免阻塞主线程导致“点击没反馈/发闷”。
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
- `Login` 页面：
  - `Connect/Disconnect/Refresh/EnsureKeys/Register/Login/ClearAuth` 全部增加 `Snackbar` 结果提示。
  - Go 调用放到 `Dispatchers.IO`，并加入基础输入校验（例如：未 Connect 时禁止 Register/Login）。
  - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`

## 对应 plan.md 任务映射

- ANDUI-1：全局 Snackbar 宿主与统一提示入口
- ANDUI-2：导航改为左侧抽屉（Drawer）
- ANDUI-3：Hub Start/Stop 强反馈 + 5s 状态追踪修复
- ANDUI-4：Login 全按钮强反馈 + 后台线程执行（避免卡 UI）
- ANDUI-5：验证与回归（本地构建）

## 关键设计决策与权衡

- **状态刷新策略（Hub Start/Stop）**
  - 采用“短期轮询（≤5s）”而非长期后台轮询或事件总线：
    - 优点：改动小、实现简单、对现有 Service/Bridge 侵入低；
    - 成本：启动/停止窗口内有少量轮询调用，但上限明确（约 5s / 200ms）。
- **性能**
  - 所有 Go/反射调用（以及 Hub 状态读取）切到 `Dispatchers.IO`，避免主线程阻塞导致 UI 触摸反馈弱。
- **交互一致性**
  - 通过 `AppRoot` 统一 `notify()`，避免各页面各自实现 Toast/文本提示，降低维护成本。

## 测试与验证方式 / 结果

- 构建：
  - `./gradlew.bat :app:assembleDebug`（通过）
- 手动建议验证：
  - 导航：点击左上角菜单打开抽屉，切换 Login/Hub/Devices/Logs/Protocols。
  - Hub：只点一次 `Start`，观察 5s 内 `Running: true`，且 Snackbar 提示“启动成功”；失败/超时则提示原因。
  - Login：各按钮点击均出现 Snackbar；异常场景（未 Connect、参数为空）有明确提示。

## 潜在影响与回滚方案

- 影响：
  - UI 导航从底部 Tab 改为抽屉；用户需要适应从左上角菜单进入切换。
  - Hub 在启动/停止窗口内会进行短期轮询查询状态（上限 5s）。
- 回滚：
  - 回滚本分支提交即可恢复旧导航与旧提示逻辑。

