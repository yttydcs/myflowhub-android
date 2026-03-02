# MyFlowHub Android：侧边栏/抽屉增加图标 + 抽屉圆角矩形化

分支：`feat/android-nav-icons`

## 目标

1) **侧边菜单增加图标（Outlined）**
- 宽屏 `NavigationRail`：每个 Tab 显示 Outlined 图标 + 文本。
- 窄屏 `Drawer`：每个 Tab 显示 Outlined 图标 + 文本。

2) **抽屉外观从“偏圆润”改为“圆角矩形”**
- 点击顶部菜单按钮弹出的 `ModalDrawerSheet`：圆角半径降低（更接近圆角矩形）。
- 抽屉内 `NavigationDrawerItem`：同步降低圆角，保持一致观感。

## 当前状态

- 宽屏 `NavigationRailItem` 目前用首字母占位（`Text(entry.label.take(1))`），没有图标。
- 窄屏抽屉 `NavigationDrawerItem` 目前没有图标，且整体圆角偏大（默认 shape）。

## 已确认的交互/视觉决策

- 图标风格：`Outlined`
- Tab → 图标映射（按 Win 语义取近似）：
  - Login：`AccountCircle`
  - Hub：`Hub`
  - Devices：`Devices`
  - VarStore：`Storage`
  - Logs：`Article`（或 `ListAlt`，以可用性优先）
  - Protocols：`Code`
- 抽屉圆角调整：**面板 + 条目**都一起改小圆角（更一致）。

## 约束与约定

- 仅在本分支/独占 worktree 实现；主 worktree（`repo/MyFlowHub-Android`）仅用于最终合并与推送。
- 提交信息使用中文（允许 `feat:`/`fix:` 前缀为英文）。
- 尽量不引入额外依赖；若所需 Outlined 图标不在当前依赖中，则补充 `material-icons-extended`（代价：APK 体积略增）。

## 任务清单（Checklist）

### Task NAVI-1：为 Tab 增加 Outlined 图标（Rail + Drawer）

- **目标**
  - `AppTab` 承载 `label + icon`，成为单一事实来源。
  - 宽屏 `NavigationRailItem` 显示 `Icon(imageVector = icon)`。
  - 窄屏 `NavigationDrawerItem` 显示同一套图标。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - （可选）`app/build.gradle.kts`（如需补齐 icons 依赖）
- **验收条件**
  - 所有 Tab 均显示对应 Outlined 图标；无占位首字母。
  - 编译通过。
- **测试点**
  - `./gradlew :app:compileDebugKotlin`
- **回滚点**
  - revert 本任务提交。

### Task NAVI-2：抽屉与条目圆角矩形化（降低圆角半径）

- **目标**
  - `ModalDrawerSheet` 设置低圆角 `drawerShape`。
  - `NavigationDrawerItem` 设置同一套低圆角 `shape`。
- **涉及文件**
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
- **验收条件**
  - 抽屉面板与条目圆角明显小于默认值，整体更“方”，但仍保留圆角。
- **测试点**
  - 手动：窄屏打开抽屉观察圆角变化与选中态背景是否正常。
- **回滚点**
  - revert 本任务提交。

### Task NAVI-3：构建与冒烟验证

- **构建**
  - `./gradlew :app:assembleDebug`
- **冒烟**
  - 宽屏（模拟/真机横屏或大屏）：`NavigationRail` 图标 + 文本正常。
  - 窄屏：打开 Drawer，图标正常，圆角矩形化生效。

### Task NAVI-4：Code Review + 归档

- 进行 3.3 Code Review（逐项结论：通过/不通过）。
- 创建 `docs/change/YYYY-MM-DD_android-nav-icons.md`（映射 NAVI-1~NAVI-3，包含验证与回滚方案）。

## 风险与注意事项

- 若 `Hub/Devices/Storage/Article/Code` 等 Outlined 图标不在当前 icons 依赖中，需要补充 `material-icons-extended`；会增加 APK 体积，但实现最稳定。
- 若某个图标在当前版本不可用，按“可编译优先”替换为语义接近的 Outlined 图标，并在 `docs/change` 中说明。

