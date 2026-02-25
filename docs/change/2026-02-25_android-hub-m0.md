# 2026-02-25 Android Hub + UI（M0）

## 背景 / 目标

在 Android 手机上提供最小可用的 “Hub + UI”：
- 手机作为 Hub：可上联父节点、可对局域网暴露监听端口
- Android 侧提供最小 UI 与启停控制
- 使用 Foreground Service 常驻（用户已接受常驻通知）
- M0 只做可行性验证与最小冒烟文档

## 变更内容

### 新增
- Gradle Android 工程骨架（Compose UI + Foreground Service）
  - `app/src/main/java/com/myflowhub/android/MainActivity.kt`：最小配置页 + 状态展示 + Start/Stop
  - `app/src/main/java/com/myflowhub/android/HubService.kt`：前台服务宿主（Start/Stop）
  - `app/src/main/java/com/myflowhub/android/Prefs.kt`：配置持久化（SharedPreferences）
- Go 侧 gomobile 绑定入口（用于产出 AAR）
  - `hubmobile/`：Go module（提供 `Start/Stop/Status`，返回 JSON 状态）
  - `scripts/build_aar.ps1`：本地构建 AAR 的脚本（`gomobile init` + `gomobile bind`）
- M0 冒烟文档
  - `docs/m0_smoke.md`：构建 AAR、构建 APK、启动与 LAN 验证步骤

### 修改
- `app/build.gradle.kts`：当 `app/libs/myflowhub.aar` 存在时才加入依赖（未生成 AAR 时仍可编译；运行时回退 stub）
- `HubBridge`：增加 `GoHubBridge`（反射调用 gomobile 生成类），无 AAR 时自动回退 `StubHubBridge`

## 任务映射（plan.md）

- AND0：新建 Android 仓库 + worktree ✅
- AND1：Android App 骨架（Compose + Foreground Service）✅
- AND2：gomobile 绑定（AAR）+ 集成 ✅（提供入口与脚本；AAR 需在本机具备 SDK/NDK/JDK 后生成）
- AND3：M0 冒烟脚本/文档 ✅

## 关键设计决策与权衡

1) **不重写 Hub**：Android 仅作为宿主壳，Hub 逻辑由 Go 侧复用（AAR 通过 gomobile 绑定）。
2) **依赖策略**：
   - `app` 对 AAR 依赖为“存在即引入”，避免开发态必须先生成 AAR 才能打开工程/编译。
   - Go module `hubmobile` 使用 `replace` 指向同 workflow 的 Server worktree，保证使用到本分支的 `hubruntime`。
3) **桥接方式**：Kotlin 侧通过反射调用 `com.myflowhub.native.Hubmobile`，降低对生成代码的强耦合与编译期依赖。

## 测试与验证

### 已在当前环境执行
- Go 侧（hubmobile）：

```powershell
cd d:\project\MyFlowHub3\worktrees\android-hub-m0\MyFlowHub-Android\hubmobile
$env:GOWORK='off'
go test ./... -count=1
```

### 需在具备 Android SDK/NDK/JDK 的机器执行
- 见 `docs/m0_smoke.md`。

## 例外说明（控制面 repo 变更）

本 workflow 在控制面目录下新增了仓库：`d:\project\MyFlowHub3\repo\MyFlowHub-Android`。
- 原因：新仓库需要先在 `repo/` 初始化 git（产生一次初始化提交），才能创建独占 worktree 进行实现开发。
- 实现改动均在 worktree：`d:\project\MyFlowHub3\worktrees\android-hub-m0\MyFlowHub-Android` 中完成。

## 潜在影响与回滚方案

### 潜在影响
- M0 为自用分发：默认权限策略与开放注册的安全风险未在 Android 侧做收敛，仅按现状对齐。
- Foreground Service 会在通知栏常驻（符合已确认的预期）。

### 回滚
- Android 仓库侧：revert 本 worktree 的提交即可撤销功能实现。
- 若要撤销控制面例外：删除 `repo/MyFlowHub-Android` 并移除对应 worktree（在未推送远端时）。

