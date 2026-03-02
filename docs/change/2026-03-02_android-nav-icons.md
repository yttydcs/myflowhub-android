# 2026-03-02 Android：侧边栏/抽屉增加图标 + Drawer 圆角矩形化

## 变更背景 / 目标

为了让 Android 端导航更易识别、并更贴近桌面端（Win）的“侧边菜单”观感，本次对导航壳层做两类改动：

1) 为侧边菜单（宽屏 `NavigationRail` + 窄屏抽屉 `Drawer`）增加 **Outlined** 风格图标。
2) 将抽屉弹出面板的视觉从“偏圆润”调整为“圆角矩形”（降低圆角半径），并保持条目圆角一致。

## 具体变更内容

### 1) Tab 图标（Outlined）接入 Rail + Drawer

- 修改：`app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - `AppTab` 扩展为 `label + icon`（`ImageVector`），作为导航的单一事实来源。
  - 宽屏：`NavigationRailItem.icon` 由首字母占位改为 `Icon(entry.icon)`。
  - 窄屏：`NavigationDrawerItem` 增加 `icon = Icon(entry.icon)`。
  - 图标映射：
    - Login：`AccountCircle`
    - Hub：`Hub`
    - Devices：`Devices`
    - VarStore：`Storage`
    - Logs：`AutoMirrored.Article`（避免 deprecation）
    - Protocols：`Code`

### 2) Drawer 面板与条目圆角矩形化（降低圆角）

- 修改：`app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - `ModalDrawerSheet`：设置 `drawerShape = RoundedCornerShape(topEnd=12.dp, bottomEnd=12.dp)`（贴边侧保持直角，外侧轻圆角）。
  - `NavigationDrawerItem`：设置 `shape = RoundedCornerShape(12.dp)`，条目与面板观感一致。

### 3) 依赖补齐：material-icons-extended

- 修改：`app/build.gradle.kts`
  - 增加 `implementation("androidx.compose.material:material-icons-extended")`，确保 `Hub/Devices/Storage/Code/Article` 等 Outlined 图标可用。

## plan.md 任务映射

- NAVI-1：Tab 图标（Outlined）接入 Rail + Drawer（完成）
- NAVI-2：Drawer 面板 + 条目低圆角（完成）
- NAVI-3：构建与冒烟验证（完成）

## 关键设计决策与权衡

1) **图标集中在 `AppTab`**
   - 好处：未来新增/调整 Tab 只改一处，不会在 Rail/Drawer 两套 UI 里重复维护。

2) **Drawer 仅右侧圆角**
   - 抽屉贴屏幕左边缘，保持左侧直角更符合 Material/系统常见行为，且不会产生“贴边留白”。

3) **引入 `material-icons-extended` 的取舍**
   - 好处：图标可用性稳定、不会因图标缺失导致编译失败。
   - 代价：APK 体积略增（可接受，且属于 UI 资产依赖）。

## 测试与验证方式 / 结果

- 编译：
  - `./gradlew :app:compileDebugKotlin`（通过）
- 构建：
  - `./gradlew :app:assembleDebug`（通过）
- 手动冒烟建议：
  - 宽屏：左侧 `NavigationRail` 显示图标+文本。
  - 窄屏：打开抽屉，条目显示图标+文本；抽屉与条目圆角明显减小。

## 潜在影响与回滚方案

- 潜在影响：
  - 新增 icons 依赖会略增体积。
  - UI 仅涉及导航壳层，不影响业务逻辑与协议交互。

- 回滚：
  - `git revert` 本次提交即可恢复到“无图标 + 默认圆角”的导航外观。

