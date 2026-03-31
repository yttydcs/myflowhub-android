# 2026-03-31_android-rfcomm-basic-usability

## 变更背景 / 目标
- 背景：
  - Android 仓已经接入 RFCOMM provider 和 Go bridge，但 Android 12+ 缺少蓝牙权限声明与运行时授权流程；
  - Login / Hub 页面文案仍偏向 TCP，用户看不出 `bt+rfcomm://...` 已被支持；
  - 结果是 RFCOMM 在代码层“存在”，但实际使用层面并不具备基本可用性。
- 目标：
  - 补齐 Android 端 RFCOMM 所需权限和入口闭环；
  - 保持 TCP 路径不受影响；
  - 让 `bt+rfcomm://...` 的最小使用路径具备可发现性和可诊断性。

## 具体变更内容
- 新增：
  - `app/src/main/java/com/myflowhub/android/BluetoothRfcommSupport.kt`
    - 统一 RFCOMM endpoint 识别、按 API 版本计算运行时权限、权限缺失文案
  - `app/src/test/java/com/myflowhub/android/BluetoothRfcommSupportTest.kt`
    - 覆盖 RFCOMM endpoint 识别与权限决策
  - `docs/README.md`
    - 补充当前 docs 入口说明
  - `docs/lessons/README.md`
    - 新增 lessons 索引
  - `docs/lessons/android-rfcomm-permission.md`
    - 记录 Android 12+ RFCOMM 权限坑位和排查顺序
- 修改：
  - `app/src/main/AndroidManifest.xml`
    - 增加 `BLUETOOTH` / `BLUETOOTH_ADMIN`（`maxSdkVersion=30`）
    - 增加 `BLUETOOTH_CONNECT`
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
    - 增加按需蓝牙权限 launcher 与状态下发
  - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
    - Connect 前在 RFCOMM 路径下按需申请权限
    - 输入文案改为 endpoint 语义，并提示支持 `bt+rfcomm://...`
  - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
    - Parent endpoint 改为 endpoint 语义
    - RFCOMM 父链启动前按需申请权限
  - `app/src/main/java/com/myflowhub/android/BluetoothRfcommProvider.kt`
    - 将权限类 `SecurityException` 归一化为明确错误
  - `app/build.gradle.kts`
    - 增加 JUnit 依赖用于本地单元测试

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

## Related lessons
- `docs/lessons/android-rfcomm-permission.md`

## 对应 plan.md 任务映射
- `ANDBT-1`：完成 RFCOMM 权限 helper 与 Manifest 权限声明
- `ANDBT-2`：完成 AppRoot / Login / Hub 按需权限申请接入
- `ANDBT-3`：完成 endpoint 文案与 provider 错误提示修正
- `ANDBT-4`：完成单元测试与构建验证
- `ANDBT-5`：完成代码审查
- `ANDBT-6`：完成归档与 lessons 更新

## 经验 / 教训摘要
- Android RFCOMM 的问题不只是 provider 代码，还包含宿主权限和 UI 入口。
- 如果 UI 仍写成 `ip:port`，用户即使有底层能力，也很难真正走通 RFCOMM。
- 权限检查应当按需触发，不要无差别打扰 TCP-only 用户。

## 可复用排查线索
- 症状：
  - `bt+rfcomm://...` 连接失败
  - Android Hub RFCOMM 父链无法启动
  - 蓝牙权限相关错误或 `SecurityException`
- 触发条件：
  - Android 12+
  - 使用 RFCOMM endpoint
- 关键词：
  - `BLUETOOTH_CONNECT`
  - `bt+rfcomm://`
  - `BluetoothAdapter`
  - `SecurityException`
- 快速检查：
  - Manifest 是否声明蓝牙权限
  - 是否存在 Android 12+ 的运行时授权
  - Connect / Start 入口是否仍只按 TCP 设计文案

## 关键设计决策与权衡
- 决策：使用按 RFCOMM endpoint 触发的权限申请
  - 原因：避免 TCP-only 用户在启动时被无谓打扰
- 决策：保留 provider 侧权限错误兜底
  - 原因：防止未来新增入口绕过 UI 时再次暴露难诊断异常
- 决策：仅产品化 Login target / Hub parent 这两条 RFCOMM 路径
  - 原因：这是当前 Android 端真实可用的最小闭环，不扩大到 listen 配置产品化

## 测试与验证方式 / 结果
- 已执行：
  - `ANDROID_HOME=D:\project\MyFlowHub3\_android-sdk`
  - `ANDROID_SDK_ROOT=D:\project\MyFlowHub3\_android-sdk`
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat :app:assembleDebug`
- 结果：
  - 均通过
- 备注：
  - Kotlin 编译有既有 `BluetoothAdapter.getDefaultAdapter()` deprecation warning，不影响本次功能

## 潜在影响与回滚方案
- 潜在影响：
  - Android 12+ 在首次使用 RFCOMM 时会弹出蓝牙权限授权框
  - Login / Hub 文案从 TCP 偏向改为 endpoint 语义
- 回滚方案：
  - 回退以下文件：
    - `app/src/main/AndroidManifest.xml`
    - `app/src/main/java/com/myflowhub/android/BluetoothRfcommSupport.kt`
    - `app/src/main/java/com/myflowhub/android/BluetoothRfcommProvider.kt`
    - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
    - `app/src/main/java/com/myflowhub/android/ui/LoginScreen.kt`
    - `app/src/main/java/com/myflowhub/android/ui/HubScreen.kt`
    - `app/build.gradle.kts`
    - `app/src/test/java/com/myflowhub/android/BluetoothRfcommSupportTest.kt`
    - `docs/README.md`
    - `docs/lessons/README.md`
    - `docs/lessons/android-rfcomm-permission.md`

## 子Agent执行轨迹
- 未使用子 Agent
