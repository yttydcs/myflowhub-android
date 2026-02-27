# Plan - Android：浅色层次白 + 顶部品牌栏（MyFlowHub）+ 全页面可滚动

> 本 workflow 目标：将应用外框（顶栏/侧栏/抽屉）的观感调整为“Material 浅色层次白（轻微灰阶）”，顶部仅显示 `MyFlowHub + 菜单按钮（窄屏）`，并为所有页面补齐可滚动能力，避免内容超出底部不可见。

## 0. Workflow 信息

- Workflow 名称：`android-ui-chrome-scroll`
- 分支（本仓）：`fix/android-ui-chrome`
- Base：`main`
- Worktree：`worktrees/fix-android-ui-chrome`
- 当前状态：已完成需求/架构确认，未开始实现

## 1. 背景与问题

1) 视觉：左侧/外框观感偏灰，期望更接近 Material 的浅色层次白（轻微灰阶）。
2) 顶部：当前顶部显示各 Tab 标题（如 Login/Hub/Devices），期望移除，仅保留品牌栏（MyFlowHub）。
3) 布局：部分页面内容可能超出底部，缺少滚动，导致信息/按钮不可达。
4) 交互：窄屏的“打开菜单”按钮希望放在顶部 `MyFlowHub` 位置（品牌栏）。

## 2. 目标与范围

### 2.1 必须达成（验收口径）

- 背景：外框与内容区使用浅色层次白（不明显发灰）。
- 顶部：仅展示 `MyFlowHub`；不再展示各 Tab 的标题；菜单按钮仅窄屏显示并可打开 Drawer。
- 滚动：所有页面在内容超出时可纵向滚动到底部（至少 Login/Hub/Protocols；Devices/Logs 需复核并补齐）。
- 既有约束：Devices **仅宽屏双栏**策略保持不变（`maxWidth >= 900.dp`）。

### 2.2 不做

- 不改业务逻辑/协议/Go bridge。
- 不引入复杂主题系统（不新增自定义 `ColorScheme`），优先通过 Material3 组件参数完成。

## 3. 方案设计（执行策略）

### 3.1 外框（App Chrome）

- TopAppBar 固定为品牌栏：title=`MyFlowHub`。
- 窄屏：TopAppBar 显示菜单按钮（打开 Drawer）。
- 宽屏：不显示菜单按钮（按用户选择 A）；左侧 `NavigationRail` 常驻。
- 颜色：优先使用 `MaterialTheme.colorScheme.surface` 作为内容区基底；外框（TopAppBar / Rail / DrawerSheet）使用轻微层次（例如 `surfaceColorAtElevation(...)`）实现“浅色层次白”。

### 3.2 全页面可滚动策略

- Login/Hub/Protocols：页面根容器增加 `verticalScroll(rememberScrollState())`，确保内容溢出可达；必要时增加 `imePadding()`（键盘遮挡时仍可滚动查看按钮）。
- Devices/Logs：保持现有内部滚动（避免破坏 `weight`/双栏），仅在发现溢出或不可达时做最小补丁（例如为非滚动容器补齐 scroll 或 insets）。

## 4. 任务清单（Checklist）

### ANDUI3-1：AppRoot 顶部品牌栏 + 外框浅色层次白

- **目标**：顶部仅显示 `MyFlowHub`；窄屏带菜单按钮打开 Drawer；外框与侧栏/抽屉不再明显偏灰。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
- **验收条件**
  - 宽/窄屏顶部均不出现 `Login/Hub/Devices/...` 标题；窄屏菜单按钮位于品牌栏并可打开 Drawer。
  - 左侧 Rail / DrawerSheet 观感为浅色层次白（轻微灰阶）。
- **测试点**
  - 手动：窄屏打开/关闭 Drawer；切换 Tab 高亮正确；宽屏 Rail 常驻。
- **回滚点**
  - revert 本任务提交（仅影响 UI 外框）。

### ANDUI3-2：Login 全页面可滚动（含键盘遮挡处理）

- **目标**：Login 页面内容超出时可滚动到底部；键盘弹出不遮挡关键按钮。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
- **验收条件**
  - 小高度/横屏下，可滚动看到所有卡片与按钮；输入时键盘弹出仍可滚动操作。
- **测试点**
  - 手动：横屏/小窗口（分屏）打开 Login；聚焦输入框弹出键盘；滚动到最底部。
- **回滚点**
  - revert 本任务提交（仅 UI 布局）。

### ANDUI3-3：Hub 全页面可滚动

- **目标**：Hub 页面内容超出时可滚动到底部，Start/Stop 与状态区可达。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
- **验收条件**
  - 小高度/横屏下可滚动看到“配置/操作/状态”全部内容。
- **测试点**
  - 手动：横屏进入 Hub；滚动到底部查看状态卡；Start/Stop 操作不受影响。
- **回滚点**
  - revert 本任务提交。

### ANDUI3-4：Protocols 全页面可滚动（包含 ProtocolConsole）

- **目标**：Protocols 页面在选择协议后，控制台内容超出时可滚动到底部并可点击 Send。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/ProtocolsScreen.kt`
- **验收条件**
  - ProtocolConsole 在小高度/横屏下可滚动看到所有输入与 SendAndAwait 按钮。
- **测试点**
  - 手动：进入 Protocols → 选择任一协议 → 滚动到底部；尝试发送（不要求业务成功）。
- **回滚点**
  - revert 本任务提交。

### ANDUI3-5：Devices/Logs “全页面可滚动”复核与最小补丁

- **目标**：确保 Devices/Logs 在小高度/窄屏下无“内容不可达/被截断”。（不做大改动，优先最小修复）
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
  - `app/src/main/java/com/myflowhub/android/ui/LogsScreen.kt`
- **验收条件**
  - Devices：树/详情任一 pane 内容过长时可滚动；窄屏下详情区可滚动；关键按钮可达。
  - Logs：日志区可滚动；页面按钮/输入区不会被遮挡导致不可操作。
- **测试点**
  - 手动：窄屏进入 Devices/Logs，尝试滚动长内容；横屏/分屏复核。
- **回滚点**
  - 如有改动：revert 本任务提交。

### ANDUI3-6：本地构建与冒烟（debug）

- **目标**：可构建 APK 并在真机验证外框/滚动。
- **命令**
  - `./gradlew :app:assembleDebug`
- **验收条件**
  - 安装后：外框/滚动符合验收口径；无明显布局错乱。
- **回滚点**
  - 无（仅验证）。

### ANDUI3-7：Code Review（强制）

- 按全局 3.3 清单逐项审查并输出结论（通过/不通过）。

### ANDUI3-8：归档变更（强制）

- 新增 `docs/change/YYYY-MM-DD_android-ui-chrome-scroll.md`：背景/目标、变更内容、任务映射、关键决策与权衡、测试结果、影响与回滚方案。

## 5. 依赖关系、风险与注意事项

- 风险：Devices/Logs 已存在内部滚动与 `weight`，避免外层再套 scroll 导致嵌套滚动与测量异常；优先做“最小补丁”。必要时仅对关键容器补齐滚动或 insets。
- 注意：保持 Devices 宽屏双栏阈值与行为不变（用户已确认“仅宽屏双栏”）。
