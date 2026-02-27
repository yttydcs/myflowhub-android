# 2026-02-27 - Android：浅色层次白外框 + 顶部品牌栏 + 全页面可滚动

## 背景 / 目标

用户反馈：

- 左侧（侧边栏/抽屉）观感偏灰，希望更接近 Material 的浅色层次白（轻微灰阶）。
- 顶部存在各 Tab 的标题（如 Login/Hub/Devices），希望移除，仅保留 `MyFlowHub + 菜单按钮（窄屏）`。
- 页面内容可能超出底部且无法滚动，导致信息/按钮不可达。
- 菜单按钮希望放在顶部 `MyFlowHub` 的位置。

本次变更目标：

1) 调整 App 外框（TopAppBar / NavigationRail / DrawerSheet / Scaffold）容器色为浅色层次白；
2) 顶部固定为品牌栏：只显示 `MyFlowHub`，窄屏显示菜单按钮；
3) 为所有页面补齐“内容溢出可滚动”的能力（至少 Login/Hub/Protocols；Logs/Devices 保持内部滚动策略并复核）。

## 具体变更内容

### 1) App 外框（Chrome）

- `AppRoot`：
  - 新增 `AppTopBar(showMenu, onMenuClick)`：标题固定为 `MyFlowHub`，不再显示当前 Tab 名称。
  - 窄屏：TopBar 提供菜单按钮打开 `ModalNavigationDrawer`。
  - 宽屏：不显示菜单按钮（按用户选择 A），左侧 `NavigationRail` 常驻。
  - 统一容器色：
    - 侧栏/抽屉：`surfaceColorAtElevation(1.dp)`（浅色层次白，避免偏灰）
    - TopBar：`surfaceColorAtElevation(2.dp)`
    - 内容区：`Scaffold(containerColor = MaterialTheme.colorScheme.surface)`
  - 全局内容区增加 `imePadding()`，避免键盘遮挡输入页面底部控件。

### 2) 页面滚动与标题精简

- `LoginScreen` / `HubScreen` / `ProtocolsScreen`：
  - 页面根 `Column` 增加 `verticalScroll(rememberScrollState())`，内容超出可滚动到底部。
- `LogsScreen` / `ProtocolsScreen`：
  - 移除 Tab 内的重复标题文本（`Text("Logs")` / `Text("Protocols")`），避免与顶部品牌栏重复。

## 对应计划任务映射（plan.md）

- ANDUI3-1：`AppRoot` 顶部品牌栏 + 外框浅色层次白 ✅
- ANDUI3-2：`LoginScreen` 全页面可滚动 ✅
- ANDUI3-3：`HubScreen` 全页面可滚动 ✅
- ANDUI3-4：`ProtocolsScreen`（含 ProtocolConsole）可滚动 ✅
- ANDUI3-5：`Devices/Logs` 复核：保持内部滚动策略；Logs 移除重复标题 ✅

## 关键设计决策与权衡

1) 不引入自定义 `ColorScheme`：
   - 仅通过 Material3 组件参数（`containerColor` / `surfaceColorAtElevation`）实现“更白”的外框，改动小、可回滚、与系统主题保持语义一致。
2) 全局 `imePadding()` 下沉到 `AppRoot` 的内容容器：
   - 优点：避免每个 Screen 重复处理键盘遮挡；新页面默认安全。
   - 风险：极少数页面可能不需要 IME inset，但本项目页面均以表单/控制台为主，收益更大。
3) 滚动策略：
   - Login/Hub/Protocols：整体内容量有限，使用 `verticalScroll` 直接解决“内容不可达”问题。
   - Logs/Devices：已有内部滚动与 `weight`/双栏布局，避免对根容器再套滚动引发嵌套滚动与测量风险，保持最小改动。

## 测试与验证方式 / 结果

- 本地构建（Debug）：
  - 通过在本机生成 `local.properties` 指向 `D:/project/MyFlowHub3/_android-sdk`（该文件已被 `.gitignore` 忽略）后执行：
    - `./gradlew :app:assembleDebug`
  - 结果：构建成功。

> UI 观感与滚动体验需在真机/模拟器上手工验证（窄屏 Drawer 打开、横屏/小高度滚动到底部等）。

## 潜在影响与回滚方案

- 影响：
  - 顶部不再展示当前 Tab 名称（通过侧栏/抽屉高亮辨识当前页面）。
- 回滚：
  - 回滚本分支 3 个提交即可恢复到旧外框与旧滚动行为；
  - 或仅回滚 `AppRoot` 提交恢复顶部显示 Tab 标题。

