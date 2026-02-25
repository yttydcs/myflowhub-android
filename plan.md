# Plan - Android：Hub + UI（M0 可行性验证）- Android 仓库

> 位置：`d:\project\MyFlowHub3\worktrees\android-hub-m0\MyFlowHub-Android\plan.md`  
> 说明：本计划只覆盖 **Android 仓库侧**（App + gomobile 绑定）。Server 侧（hubruntime + parent bootstrap + 集成测试）见：  
> `d:\project\MyFlowHub3\worktrees\android-hub-m0\MyFlowHub-Server\plan.md`

## 0. Workflow 信息

- Workflow 名称：`android-hub-m0`
- 分支（本仓）：`feat/android-hub-m0`
- Worktree（本仓）：`d:\project\MyFlowHub3\worktrees\android-hub-m0\MyFlowHub-Android`
- 控制面仓库（仅管理用，禁止实现改动）：`d:\project\MyFlowHub3\repo\MyFlowHub-Android`
- Base：`main`

## 1. 目标（M0 验收口径）

### 1.1 必须达成
1) Android 端具备最小 UI：
   - 配置：监听地址（addr）、父节点地址（parent）、self_id（用于自注册/父链 bootstrap）
   - 状态：NodeID、监听地址、父链连接状态、最近错误
   - 控制：Start/Stop
2) Android 端以 Foreground Service 常驻运行 Hub（用户已接受通知常驻）。
3) Hub 监听非回环地址，局域网内可见（用户已确认）。

### 1.2 暂不要求（M1）
- UI/交互体验对齐 Win。
- 安全加固（开放注册 + 高权限默认）仅提示风险，不在 M0 改造。
- 自动更新/Release 流水线。

## 2. 约束与关键决策（对齐已确认结论）

- **不重写 Hub**：Android 只是宿主壳，复用 Go Hub 栈（Server 侧已提供 `hubruntime`）。
- **绑定方式**：优先 `gomobile bind` 产出 AAR（至少 `android/arm64`）。
- **工作目录**：Go 侧需把 `workdir` 指向 App 私有目录（用于 `config/*` 相对路径写入）。
- **后台策略**：Foreground Service + 常驻通知（M0 即采用）。

## 3. 计划拆分（Checklist）

> 约定：每个任务必须有回滚点；不得引入计划外改动；新增任务需先更新本 plan 并确认。

### AND1 - Android App 骨架（Compose + Foreground Service）
- 目标：能安装 APK，并通过 UI 启停 Hub 前台服务；常驻通知可见；显示最小状态。
- 涉及模块/文件（预期）：
  - `app/`（Kotlin + Compose）
  - `HubService`（Foreground Service）
  - `MainActivity`（配置页 + 状态页）
  - 持久化：`SharedPreferences` 或 `DataStore`（二选一，按实现便利）
- 验收条件：
  - `./gradlew :app:assembleDebug` 成功
  - 真机安装后可 Start/Stop，且通知常驻可见
- 回滚点：revert 提交。

### AND2 - gomobile 绑定（AAR）+ 集成到 App
- 目标：封装一个 gomobile 友好的 Go 包，产出 AAR 并在 App 内调用 `Start/Stop/Status`。
- 设计要点：
  - Go 包对外只暴露：`Start(opts)` / `Stop()` / `Status()`（返回 JSON 字符串也可）
  - `workdir` 使用 `context.getFilesDir()` 或 `context.getNoBackupFilesDir()` 下子目录
  - `self_id` 由 UI 配置（默认可用随机 UUID；M0 允许手工输入）
- 验收条件：
  - AAR 构建成功（至少 arm64）
  - App 启动后 Hub 真正开始监听（局域网可连）
- 回滚点：revert 提交。

### AND3 - M0 冒烟脚本/文档
- 目标：把验证步骤写成可复现文档（含依赖安装、命令与期望结果）。
- 验收条件：
  - 文档包含：构建 AAR、构建 APK、启动 Hub、LAN 设备验证（至少 management `node_echo`）
- 回滚点：revert 提交。

### 3.3 - Code Review（强制）
- 按全局 3.3 清单逐项输出结论（通过/不通过）。
- 不通过：返回对应任务修正，重新 review。

### 4 - 归档变更（强制）
- 在本 worktree 根目录创建 `docs/change/` 并新增归档文档：
  - `docs/change/YYYY-MM-DD_android-hub-m0.md`
- 必须包含：任务映射、关键决策与权衡、验证方式与结果、回滚方案。

## 4. 验证命令（Android）

```powershell
cd d:\project\MyFlowHub3\worktrees\android-hub-m0\MyFlowHub-Android
./gradlew :app:assembleDebug
```

