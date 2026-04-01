# 2026-04-01_android-hub-resilience

## 变更背景 / 目标
- 背景：
  - Android Hub 之前虽然把 `HubService` 声明为 `START_STICKY`，但服务被系统重建时若 `intent == null`，实现会直接 `no-op`；
  - Go runtime 已具备父链自动重连，因此“自动重连观感差、后台似乎不工作”的主要缺口其实在 Android 宿主没有恢复最近一次运行态；
  - 同时，前台通知和 Hub 页面状态都只展示一次性快照，用户很难判断后台是否仍在运行、父链是否恢复。
- 目标：
  - 让 Android Hub 在服务 / 进程被系统重建后具备基本可恢复性；
  - 保持父链自动重连继续由 Go runtime 负责；
  - 补齐后台通知和前台页面的状态连续性。

## 具体变更内容
- 新增：
  - `app/src/main/java/com/myflowhub/android/HubServiceSupport.kt`
    - 抽出运行配置归一化、恢复决策和通知文案逻辑
  - `app/src/test/java/com/myflowhub/android/HubServiceSupportTest.kt`
    - 覆盖恢复条件、配置归一化和通知文本
  - `docs/lessons/android-hub-service-restart.md`
    - 记录 `START_STICKY` 下空 intent 恢复缺口与运行快照规则
- 修改：
  - `app/src/main/java/com/myflowhub/android/Prefs.kt`
    - 新增 Hub 运行快照持久化
    - 新增 `desiredRunning` 持久化
  - `app/src/main/java/com/myflowhub/android/HubService.kt`
    - `ACTION_START` 时持久化最近一次启动快照
    - `ACTION_STOP` 时清除 `desiredRunning`
    - `intent == null` 时根据运行快照执行恢复
    - 增加低频状态轮询和动态前台通知更新
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
    - 增加前台页面状态轮询，持续显示 `running` / `parentConnected` / `lastError`
  - `docs/lessons/README.md`
    - 增加新 lesson 索引

## Requirements impact
- none

## Specs impact
- none

## Lessons impact
- updated

## Related requirements
- none

## Related specs
- `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\specs\core.md`

## Related lessons
- `docs/lessons/android-hub-service-restart.md`

## 对应 plan.md 任务映射
- `ANDHUBRES-1`：归档旧控制文档并切换到本轮 `plan.md`
- `ANDHUBRES-2`：持久化运行快照与 `desiredRunning`，补齐空 intent 恢复
- `ANDHUBRES-3`：补齐服务侧状态轮询与通知刷新
- `ANDHUBRES-4`：补齐 Hub 页面状态轮询与展示连续性
- `ANDHUBRES-5`：补充单元测试并完成本地验证
- `ANDHUBRES-6`：完成 3.3 自审与 4 阶段归档

## 经验 / 教训摘要
- `START_STICKY` 只能让 Android 重新创建服务，不会自动恢复依赖 intent extras 的运行配置。
- Hub 表单配置和“最近一次真实启动配置”不是一回事；若把两者混用，后台恢复会漂移到用户尚未重新启动的新草稿。
- 父链自动重连已经在 Go runtime 内部实现，Android 宿主更应该修生命周期恢复和状态可见性，而不是再造一层重连状态机。

## 可复用排查线索
- 症状：
  - Android Hub 启动后切到后台，过一段时间看起来不再工作
  - 父链断开后即使 Go runtime 仍会重连，Android 通知 / 页面也看不出状态变化
  - 服务重建后 `Hub` 页面显示 `Stopped`，但用户认为之前已经启动过
- 触发条件：
  - `HubService` 返回 `START_STICKY`
  - 服务启动强依赖 `ACTION_START` extras
  - 服务重建时 `intent == null`
- 关键词：
  - `START_STICKY`
  - `intent == null`
  - `desiredRunning`
  - `HubService`
  - `后台不工作`
  - `自动恢复`
- 快速检查：
  - `HubService.onStartCommand()` 在 `else` / `null intent` 分支是否仍是 `no-op`
  - 是否单独持久化了“最近一次启动快照”，而不是只保存 UI 表单
  - `bridge.status()` 的结果是否会刷新到通知和页面

## 关键设计决策与权衡
- 决策：运行快照与表单配置分离
  - 原因：恢复语义必须对齐“上一次真实启动”，而不是对齐用户尚未重新生效的表单草稿
- 决策：不在 Android 端重复实现父链重连
  - 原因：Server `hubruntime` 已具备 `parent.reconnect_sec` 语义，再在宿主层重复实现只会引入双状态机
- 决策：采用秒级低频轮询而不是引入更重的后台框架
  - 原因：本轮目标是最小修复基本可用性，不扩大到 WorkManager / boot restore

## 测试与验证方式 / 结果
- 已执行：
  - `git diff --check`
  - 第一次 `.\gradlew.bat testDebugUnitTest`
    - 失败原因：当前 shell 未设置 Android SDK 路径
  - 重新执行：
    - `ANDROID_HOME=D:\project\MyFlowHub3\_android-sdk`
    - `ANDROID_SDK_ROOT=D:\project\MyFlowHub3\_android-sdk`
    - `.\gradlew.bat testDebugUnitTest`
- 结果：
  - Kotlin 编译与 JUnit 单测通过
- 备注：
  - 仍存在既有 `BluetoothAdapter.getDefaultAdapter()` deprecation warning，不属于本轮引入
  - 当前环境未做真机进程回收 / 后台恢复实机验证

## 潜在影响与回滚方案
- 潜在影响：
  - Hub 服务现在会持久化最近一次启动快照和 `desiredRunning`
  - 前台通知会在运行中动态更新
  - Hub 页面会持续轮询服务状态
- 回滚方案：
  - 回退以下文件：
    - `app/src/main/java/com/myflowhub/android/Prefs.kt`
    - `app/src/main/java/com/myflowhub/android/HubService.kt`
    - `app/src/main/java/com/myflowhub/android/HubServiceSupport.kt`
    - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
    - `app/src/test/java/com/myflowhub/android/HubServiceSupportTest.kt`
    - `docs/change/2026-04-01_android-hub-resilience.md`
    - `docs/lessons/android-hub-service-restart.md`
    - `docs/lessons/README.md`

## 子Agent执行轨迹
- 未使用子 Agent
