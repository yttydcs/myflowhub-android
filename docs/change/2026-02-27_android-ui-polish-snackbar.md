# 2026-02-27 Android：Snackbar 及时替换 + Hub/Login/Devices UI 精细化

## 背景 / 目标

- 解决 Snackbar “排队导致延迟 / 无法被新提示打断”的体验问题（尤其 Register/Login）。
- Hub / Login / Devices 页面交互反馈弱、信息层级不清晰：补齐更明确的加载态、成功/失败提示，以及更现代的布局分区。
- 在宽屏设备上提供更接近 Windows 的左侧导航体验。

## 变更内容

### 新增

- `app/src/main/java/com/myflowhub/android/ui/UiNotifier.kt`
  - 统一 UI 通知入口（info/success/error/progress）。

### 修改

- `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
  - 引入 `UiNotifier + SharedFlow`，使用 `collectLatest` + `dismiss()` 实现 Snackbar **最新优先**（Replace 而非 Queue）。
  - 宽屏使用 `NavigationRail`（左侧栏），窄屏保留 Drawer。

- `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
  - 卡片化分区（连接 / 身份与认证）。
  - 增强加载态：顶部 `LinearProgressIndicator` + 当前操作文本 + `Cancel`（UI 层可取消并避免旧结果回写）。
  - Register/Login 返回为空时也给出失败提示，避免 progress 常驻不结束。

- `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
  - 使用 Material3 `Card` 分区与 `AssistChip` 状态展示。
  - Start/Stop 增加 5s 轮询等待与超时提示，加载态更明确。

- `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
  - 卡片化 + 宽屏双栏（Tree / Details）。
  - Config 操作使用 Snackbar 输出进度与结果；Key 列表用 Chip 可点选。

## 任务映射（plan.md）

- ANDUI2-1：UiNotifier + Snackbar Replace
- ANDUI2-2：Hub UI 优化
- ANDUI2-3：Login UI 优化 + 结果无延迟
- ANDUI2-4：Devices UI 优化
- ANDUI2-5：构建与回归验证

## 关键设计决策与权衡

- Snackbar 采用 **最新优先**：新提示到来会 `dismiss()` 当前提示并立即显示；避免业务关键结果被排队遮蔽。
- Login 的 `Cancel` 属于 UI 层“打断”：底层 Go/网络调用可能仍在执行，因此通过 `opSeq` token 防止旧操作完成后回写 UI/提示。
- Devices 树渲染沿用递归 + scroll 的实现（变更最小化）；若后续树规模显著增大，可考虑改为 `LazyColumn` + 扁平化节点列表来优化性能。

## 测试与验证

- 构建：`./gradlew.bat :app:assembleDebug`（需要设置 Android SDK：`ANDROID_HOME` / `ANDROID_SDK_ROOT` 或 `local.properties`）。
- 手测建议：
  - Login：快速点击 Connect/Disconnect/Refresh；Register/Login 过程中提示可被新提示替换且能正常结束；Cancel 后不会出现旧结果“回跳”。
  - Hub：Start/Stop 成功/失败/超时提示明确；状态信息清晰分组。
  - Devices：Load → 展开子节点 → 选中节点 → Config list/get/set，提示与界面反馈清晰。

## 潜在影响与回滚

- 影响：提示不再排队（历史 Snackbar 可能被替换掉），但符合“结果及时可见”的目标。
- 回滚：revert 本次改动涉及的 UI 文件与 `UiNotifier.kt`；导航栏可回退为 Drawer-only。

