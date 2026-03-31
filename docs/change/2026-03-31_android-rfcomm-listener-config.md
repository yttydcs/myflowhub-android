# 2026-03-31_android-rfcomm-listener-config

## 变更背景 / 目标
- 背景：
  - Android 端之前已经具备 RFCOMM provider 的 listen/dial 底层能力，Server runtime 也支持 `RFCOMMEnable/RFCOMMUUID/RFCOMMInsecure`；
  - 但 Android Hub 侧仍未把 listener 配置从 UI/Prefs/Service/Bridge/hubmobile 传到 runtime，导致 RFCOMM listener 只能“代码里存在”，无法产品化使用；
  - 同时，本地 `hubmobile` 构建链在替换 Server worktree 后，还会受未发布 Proto 包影响，AAR 验证存在额外依赖坑。
- 目标：
  - 将 Android Hub 的 RFCOMM listener 做成最小可用配置链路；
  - 保持 TCP listener 默认行为不变；
  - 在旧 AAR / 本地依赖不齐时给出明确、可诊断的失败方式。

## 具体变更内容
- 新增：
  - `app/src/test/java/com/myflowhub/android/HubStartBindingTest.kt`
    - 覆盖新旧 `Hubmobile.Start(...)` 签名解析与旧 AAR 显式失败逻辑
  - `docs/lessons/android-hubmobile-local-replace.md`
    - 记录 Android 本地 gomobile/AAR 构建对 Proto 本地 replace 的依赖规则
- 修改：
  - `app/src/main/java/com/myflowhub/android/HubConfig.kt`
    - 新增 `rfcommListenEnabled` / `rfcommServiceUuid` / `rfcommInsecure`
  - `app/src/main/java/com/myflowhub/android/BluetoothRfcommSupport.kt`
    - 增加 Hub 是否需要蓝牙权限的统一判断
    - 增加 RFCOMM 默认 UUID、规范化与合法性校验
  - `app/src/main/java/com/myflowhub/android/Prefs.kt`
    - 持久化 RFCOMM listener 配置
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
    - 暴露 RFCOMM listener 开关、UUID、insecure 选项
    - 启动前校验 UUID，并将 listener + parent RFCOMM 权限检查合并到统一入口
  - `app/src/main/java/com/myflowhub/android/HubService.kt`
    - 增加 RFCOMM listener extras，并恢复完整 `HubConfig`
  - `app/src/main/java/com/myflowhub/android/HubBridge.kt`
    - 反射优先加载新 `Start(...)` 签名
    - 旧 AAR 仅在 TCP-only 场景兼容；一旦请求 RFCOMM listener，显式报错要求重建 AAR
  - `hubmobile/hubmobile.go`
    - 扩展导出 `Start(...)` 参数，并写入 `hubruntime.Options.RFCOMMEnable/RFCOMMUUID/RFCOMMInsecure`
  - `hubmobile/go.mod`
    - 新增 `github.com/yttydcs/myflowhub-proto => ../../MyFlowHub-Proto` 本地 replace
    - 同步间接依赖图（`auth v0.1.5`、`stream v0.1.0`）
  - `hubmobile/go.sum`
    - 同步上述依赖校验
  - `app/src/test/java/com/myflowhub/android/BluetoothRfcommSupportTest.kt`
    - 补充 UUID 规范化、Hub 权限判定测试

## Requirements impact
- none

## Specs impact
- none

## Lessons impact
- updated

## Related requirements
- none

## Related specs
- `docs/change/2026-03-12_bluetooth-rfcomm-transport-android.md`
- `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\change\2026-03-12_bluetooth-rfcomm-transport-server.md`

## Related lessons
- `docs/lessons/android-rfcomm-permission.md`
- `docs/lessons/android-hubmobile-local-replace.md`

## 对应 plan.md 任务映射
- `ANDRFL-1`：完成 workflow 控制文档与 dependency-only worktree 边界记录
- `ANDRFL-2`：完成 Hub 配置模型、Prefs、Helper、Hub UI 扩展
- `ANDRFL-3`：完成 Service / Bridge / hubmobile 启动链路扩展与旧 AAR 兼容
- `ANDRFL-4`：完成 Go / AAR / Android 验证
- `ANDRFL-5`：完成 3.3 代码审查
- `ANDRFL-6`：完成 change / lessons 归档

## 经验 / 教训摘要
- Android RFCOMM listener 的缺口不在 provider，而在宿主配置链路没有产品化暴露。
- gomobile API 升级不能假设本地 AAR 永远同步；旧签名必须显式处理，否则会出现“看起来启动成功、实际配置被忽略”的隐患。
- 当前 Android 本地 AAR 构建不仅依赖 Server / SDK worktree，还依赖 Proto 本地源码，因为 Server main 已消费未发布的 `protocol/stream`。

## 可复用排查线索
- 症状：
  - Hub 页面看得到 RFCOMM provider 能力，但无法配置 listener
  - 启用 RFCOMM listener 后启动失败，或提示 AAR 过旧
  - `go test ./...` / `gomobile bind` 在 `hubmobile` 下因 `protocol/stream`、`go.mod tidy`、`gobind` 失败
- 触发条件：
  - Android Hub 需要产品化 RFCOMM listener
  - 使用本地 Server worktree 构建 `hubmobile`
  - 本地 AAR 未重建或 Proto 本地 replace 缺失
- 关键词：
  - `RFCOMMEnable`
  - `Hubmobile.Start`
  - `app/libs/myflowhub.aar`
  - `protocol/stream`
  - `go mod tidy`
  - `gobind`
- 快速检查：
  - `HubConfig / HubService / HubBridge / hubmobile.Start` 是否都传递了 RFCOMM listener 字段
  - 本地 AAR 是否已重建
  - `hubmobile/go.mod` 是否同时 replace 到 `MyFlowHub-Server`、`MyFlowHub-SDK`、`MyFlowHub-Proto`

## 关键设计决策与权衡
- 决策：Android 端只暴露 `enable/uuid/insecure`
  - 原因：满足最小可用即可，避免过早把 `adapter/channel` 等平台细节带入 UI
- 决策：对旧 AAR 做双签名兼容而不是直接硬切
  - 原因：能保留 TCP-only 兼容，同时阻止 RFCOMM listener 配置被静默忽略
- 决策：在 `hubmobile/go.mod` 增加 Proto 本地 replace，而不是长期依赖显式 `go.work`
  - 原因：现有 `build_aar.ps1` 强制 `GOWORK=off`，而 `gomobile bind` 对显式 workspace 还会触发 `gobind` 模块约束；本地 replace 更稳

## 测试与验证方式 / 结果
- 已执行：
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
  - `ANDROID_HOME=D:\project\MyFlowHub3\_android-sdk`
  - `ANDROID_SDK_ROOT=D:\project\MyFlowHub3\_android-sdk`
  - `.\scripts\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar`
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat :app:assembleDebug`
- 结果：
  - 全部通过
- 备注：
  - Kotlin 编译仍有既有 `BluetoothAdapter.getDefaultAdapter()` deprecation warning，不影响本次功能

## 潜在影响与回滚方案
- 潜在影响：
  - Hub 页面新增 RFCOMM listener 配置项
  - 本地开发态 `hubmobile/go.mod` 现在额外依赖同层 `MyFlowHub-Proto` worktree
  - 旧 AAR 在启用 RFCOMM listener 时会返回明确错误，而不是继续隐式兼容
- 回滚方案：
  - 回退以下文件：
    - `app/src/main/java/com/myflowhub/android/HubConfig.kt`
    - `app/src/main/java/com/myflowhub/android/BluetoothRfcommSupport.kt`
    - `app/src/main/java/com/myflowhub/android/Prefs.kt`
    - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
    - `app/src/main/java/com/myflowhub/android/HubService.kt`
    - `app/src/main/java/com/myflowhub/android/HubBridge.kt`
    - `app/src/test/java/com/myflowhub/android/BluetoothRfcommSupportTest.kt`
    - `app/src/test/java/com/myflowhub/android/HubStartBindingTest.kt`
    - `hubmobile/hubmobile.go`
    - `hubmobile/go.mod`
    - `hubmobile/go.sum`
    - `docs/change/2026-03-31_android-rfcomm-listener-config.md`
    - `docs/lessons/android-hubmobile-local-replace.md`
    - `docs/lessons/README.md`

## 子Agent执行轨迹
- 未使用子 Agent
