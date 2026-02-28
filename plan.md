# MyFlowHub Android：拆分 UI/HUB 身份（-ui/-hub）并自动迁移

## 目标

- 让 Android 端在「UI 通过网络协议/子协议控制 Hub」模式下，**UI 与 Hub 为两个独立 Node**：
  - Hub 使用 `SelfID`（后缀 `-hub`）
  - UI 使用 `DeviceID`（后缀 `-ui`）
  - 二者不得再共用同一个 `device_id`，避免在父 HubServer 上映射到同一个 `node_id`。
- 自动迁移存量配置：旧版本只有一个 ID（同时被 Hub 与 UI 使用）时，升级后自动拆分为：
  - `base-hub`（Hub SelfID）
  - `base-ui`（UI DeviceID）
  - 并清理旧的登录状态，要求重新 Register/Login。

## 当前状态

- `main` 已包含 Android UI 外框与提示优化（浅色层次白、顶部 MyFlowHub、全页滚动、操作提示）。
- 发现问题：Android 端 Hub 与 UI 共用同一 `device_id`（同一 SharedPreferences key），导致：
  - Hub 先向父 HubServer 注册得到 `node_id=Nh`
  - UI 再注册/登录仍被父侧映射为同一 `node_id=Nh`，不符合「UI 作为独立 Node」的设计。

## 约束与约定

- 仅在本 worktree 分支实现：`fix/android-identity-split`。
- 提交信息使用中文（允许 `fix:` 前缀为英文）。
- 默认后缀：`-ui` / `-hub`（已确认）。
- 迁移策略：允许同时改动 Hub SelfID 与 UI DeviceID（已确认，可能导致 Hub 的 node_id 变化）。

## 任务清单（Checklist）

### Task 1：Prefs 身份键拆分 + 自动迁移

- **目标**
  - 将 SharedPreferences 的 Hub SelfID 与 UI DeviceID 分离存储，不再互相覆盖。
  - 首次升级时自动从旧单一 ID 迁移为 `base-hub/base-ui`，并清理旧 auth 字段。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/Prefs.kt`
  - （如需要）新增 `app/src/main/java/com/myflowhub/android/Identity.kt`
- **验收条件**
  - `HubConfig.selfId` 永远读取/写入 Hub 专用 key（值带 `-hub`）。
  - `ClientConfig.deviceId` 永远读取/写入 UI 专用 key（值带 `-ui`）。
  - 若检测到旧格式（无 UI key），自动迁移并清空 `auth_node_id/auth_hub_id/auth_role`。
- **测试点**
  - 安装旧版后升级：启动后自动生成 `*-hub/*-ui`，且登录信息被清空。
  - 新装：直接生成 `*-hub/*-ui`（不出现无后缀的 UUID）。
- **回滚点**
  - `git revert` 本任务提交；或清除 App 数据重新初始化。

### Task 2：移除 AppRoot 中 Hub/UI 身份互相同步

- **目标**
  - 防止 UI 修改 DeviceID 时覆盖 Hub SelfID，或 Hub 修改 SelfID 时覆盖 UI DeviceID。
  - 启动时在迁移完成后加载配置，并在 UI 给出一次性提示（snackbar）。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
- **验收条件**
  - `LoginScreen.onCfgChange` 不再写 Hub 配置的 `selfId`。
  - `HubScreen.onCfgChange` 不再写 Client 配置的 `deviceId`。
  - 迁移发生时提示包含新旧关键值（至少提示“已迁移，需要重新注册/登录”）。
- **测试点**
  - 修改 Hub SelfID 不会改变 UI DeviceID；反之亦然。
- **回滚点**
  - revert 本任务提交。

### Task 3：UI 文案与诊断信息（减少混淆）

- **目标**
  - Hub 页面清晰标注 `Hub Self ID (-hub)`。
  - Login 页面清晰标注 `UI Device ID (-ui)`，并展示当前 Hub SelfID 供对照。
  - 若检测到二者相同或后缀不符合规范，给出明确提示并提供一键修正（按策略自动拆分）。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
- **验收条件**
  - 用户能在界面上直观看到两套 ID，不再误以为“应该相同”。
- **测试点**
  - 手动把两者改成相同值后，能被提示并自动修正。
- **回滚点**
  - revert 本任务提交。

### Task 4：登录/注册成功判定与桥接返回值健壮性

- **目标**
  - 避免出现“返回 `{}` 仍提示成功”的假象。
  - 当返回缺少 `node_id` / `hub_id` 等关键字段时，明确提示失败原因。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
- **验收条件**
  - `register/login` 若反射调用失败或返回 `null`，UI 必须走失败提示路径。
  - `register/login` 解析成功但 `node_id<=0` 时，不写入配置并提示异常。
- **测试点**
  - 正常链路：Register -> node_id 写入；Login -> Logged in 提示。
  - 异常链路：断网/未 connect/非法 node_id 时提示准确。
- **回滚点**
  - revert 本任务提交。

### Task 5：构建与冒烟验证

- **构建**
  - `./gradlew :app:assembleDebug`
- **冒烟步骤**
  1. 首次启动（迁移/新装）：确认生成 `*-hub/*-ui`
  2. Hub：配置 parentAddr，Start，确认 Hub node_id
  3. UI：Connect 到本机 Hub，Register，确认 UI node_id 与 Hub node_id 不同
  4. UI：Login，进入 Devices/Protocols 等页面进行一次请求
- **通过标准**
  - UI node_id ≠ Hub node_id，且 UI 可稳定发送子协议请求

### Task 6：Code Review + 归档

- 进行 3.3 Code Review（逐项结论：通过/不通过）。
- 创建 `docs/change/YYYY-MM-DD_android-identity-split.md`，映射 Task 1-5，包含验证与回滚方案。

## 依赖 / 风险 / 注意事项

- 迁移策略会改变 Hub SelfID，导致父侧重新分配 Hub node_id：这是预期行为（已确认）。
- 本次仅拆分 `device_id` 维度；是否需要进一步拆分 UI/HUB 的密钥/工作目录属于后续可选增强（如有需要需另开 workflow）。
