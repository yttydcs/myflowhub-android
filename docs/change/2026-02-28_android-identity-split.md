# Android：拆分 UI/HUB 身份（-ui/-hub）并自动迁移

日期：2026-02-28  
分支：`fix/android-identity-split`  

## 背景 / 目标

现状（升级前）：

- Android 端 Hub（嵌入 hub runtime）与 UI（Client）共用同一个 `device_id`（同一 SharedPreferences key）。
- 当手机 Hub 先连接父 HubServer 完成 `auth register` 拿到 `node_id=Nh` 后，手机 UI 再通过本机 Hub 走 Auth 子协议 `register/login` 时，父侧会按同一 `device_id` 映射到同一 `node_id=Nh`。
- 结果：UI 无法作为“独立 Node”参与整体 node 设计，不符合「UI 通过网络协议/子协议控制 Hub」的拓扑要求。

目标（升级后）：

- Hub 与 UI 拥有 **两套独立身份**：
  - Hub：`SelfID` 以 `-hub` 结尾
  - UI：`DeviceID` 以 `-ui` 结尾
- 自动迁移存量（旧版本只有一个 ID）：拆分为 `base-hub/base-ui` 并清空旧登录状态，要求重新注册/登录。

## 具体变更内容

### 1) Prefs：身份键拆分 + 自动迁移

文件：`app/src/main/java/com/myflowhub/android/Prefs.kt`

- 新增独立存储键：
  - `hub_self_id`：Hub SelfID（-hub）
  - `ui_device_id`：UI DeviceID（-ui）
- 兼容旧键 `self_id`（legacy）：仅用于迁移输入来源，不再作为新版本的读写目标。
- 新增 `ensureIdentity()`：
  - 当新键缺失或检测到冲突（Hub/UI 相同）时，按 base 规则生成：
    - `base-hub`
    - `base-ui`
  - 发生迁移时清空 `auth_node_id/auth_hub_id/auth_role`，避免旧身份残留造成误判。

### 2) AppRoot：移除 Hub/UI 身份互相同步

文件：`app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`

- 移除：
  - UI 修改 DeviceID 时覆盖 Hub SelfID
  - Hub 修改 SelfID 时覆盖 UI DeviceID
- 启动时调用 `Prefs.ensureIdentity()`；若发生迁移，使用 snackbar 提示：
  - 新的 Hub/UI 身份
  - 登录信息已清空，需要重新注册/登录

### 3) UI：文案明确 + 自动修正（-ui/-hub）

文件：

- `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
  - 明确展示：`Hub SelfID (-hub)`
  - UI 字段改名：`UI DeviceID (-ui)`
  - Register/Login 点击时自动规范化 UI DeviceID：
    - `xxx` → `xxx-ui`
    - `xxx-hub` → `xxx-ui`
    - 与 Hub SelfID 相同 → 派生为 `base-ui`
  - 若 Login 触发了 DeviceID 规范化：清空登录信息并提示先 Register（避免 node_id 不匹配）
- `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
  - 字段改名：`Hub SelfID (-hub)`
  - Start 时自动规范化 Hub SelfID：
    - `xxx` → `xxx-hub`
    - `xxx-ui` → `xxx-hub`

### 4) 登录/注册：严格成功判定 + Bridge 返回值健壮性

文件：

- `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - `register/login` 反射返回 `null` 时不再回退 `"{}"`，改为抛错，避免 UI “假成功”。
- `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
  - Register/Login 返回解析后要求：
    - `node_id > 0`
    - `hub_id > 0`
  - 不满足则视为失败并提示原因，避免写入不完整身份导致后续页面（Devices/Protocols）不可用。

## 与 plan.md 任务映射

- Task 1：Prefs 身份键拆分 + 自动迁移 ✅
- Task 2：移除 AppRoot 身份同步 ✅
- Task 3：UI 文案/修正（-ui/-hub）✅
- Task 4：登录/注册成功判定与 Bridge 健壮性 ✅
- Task 5：构建与冒烟验证 ✅（构建通过；真机冒烟需人工执行）

## 关键设计决策与权衡

- 采用“拆分 SharedPreferences key + 自动迁移”的方式修复根因，避免继续让 Hub/UI 共用同一 `device_id`。
- 迁移策略选择“同时改 Hub 与 UI”（`base-hub/base-ui`）：
  - 优点：默认即满足规范，不需要用户手动改两处。
  - 代价：Hub 的 SelfID 改变后，父侧可能重新分配 Hub 的 `node_id`（预期行为，需提示）。
- 本次不拆分 UI/HUB 的 Go workDir/密钥文件：
  - 当前 `workDir` 仍为同一目录；后续若需要“每个 node 独立密钥”，需另开 workflow 评估 Go 全局状态与存储隔离方案。

## 测试与验证方式 / 结果

- 构建：
  - `./gradlew :app:assembleDebug` ✅
  - 注：本机需配置 Android SDK（通过 `local.properties` 的 `sdk.dir`，该文件已在 `.gitignore`）。
- 真机冒烟（建议你执行）：
  1. 升级安装后首次启动：观察提示已迁移身份（Hub=*-hub，UI=*-ui），并确认登录状态已清空。
  2. Hub 页：配置 `parentAddr` 后 Start，记录 Hub 的 `node_id`（Nh）。
  3. Login 页：Connect 到本机 Hub，Register，记录 UI `node_id`（Nu），确认 `Nu != Nh`。
  4. Login 页：Login 后进入 Devices/Protocols，发送一次请求，确认可用。

## 潜在影响与回滚方案

- 影响：
  - 迁移后 Hub SelfID 变化会导致父侧重新分配 Hub node_id（可能影响你已有的固定引用）。
  - 登录状态会被清空，需要重新 Register/Login。
- 回滚：
  - 代码回滚：`git revert` 本分支相关提交。
  - 运行态回滚：清除 App 数据（会重新生成默认 `*-hub/*-ui`），或手工在 Hub/Login 页面改回期望的 ID。
