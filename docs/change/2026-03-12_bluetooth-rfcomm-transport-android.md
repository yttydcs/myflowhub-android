# Android：RFCOMM（Bluetooth Classic）Provider（重大变更）

## 背景 / 目标
- 背景：Android 平台的 Bluetooth Classic RFCOMM 需要通过 Java/Kotlin API（`BluetoothSocket/BluetoothServerSocket`）实现。Core 侧不直接依赖 Android 蓝牙栈，因此需提供 Provider 注入。
- 目标：
  - 实现 Android RFCOMM Provider（dial + listen），并将其以“字节流 Pipe”形式提供给 Go；
  - 在调用 `Hubmobile.Start()` 前完成 Provider 注入；
  - Android 默认 `secure=true`，但允许通过参数切换 `secure=false`（不推荐）。

## 变更内容
- `hubmobile/rfcomm_android.go`
  - 新增 `SetRFCOMMProvider(...)`：将 Kotlin Provider 注入到 Core 的 `rfcomm_listener`（Android build tag）
- `app/src/main/java/com/myflowhub/android/BluetoothRfcommProvider.kt`
  - Kotlin 实现 RFCOMM Provider：
    - listen：`BluetoothAdapter.listenUsingRfcommWithServiceRecord(uuid)`
    - dial：`BluetoothDevice.createRfcommSocketToServiceRecord(uuid)`
    - 封装为可被 gomobile 绑定的接口（Read/Write/Close + RemoteBDAddr）
- `app/src/main/java/com/myflowhub/android/HubBridge.kt`
  - 启动时 best-effort 安装 Provider（兼容旧 AAR / 方法缺失时不崩溃）

## 关键设计决策与权衡
- **Provider 注入**：Go 侧只定义接口与调用点，平台差异集中在 Kotlin 实现，避免 Core 引入 Android 特有依赖。
- **安全默认**：默认 secure；insecure 仅作为兼容兜底。

## 测试与验证
- `cd hubmobile; go test ./... -count=1`（主机平台编译通过）
- 真机冒烟（需要蓝牙权限/设备）：
  - 两台 Android（或 Android↔PC）使用同一 UUID 完成 RFCOMM 建链；
  - 建链后完成至少一条 MyFlowHub 帧收发（配合 Server/SDK）。

## 潜在影响
- Android 12+ 需要正确申请并授予蓝牙权限；蓝牙关闭/权限缺失会导致 dial/listen 失败（应提示可诊断错误）。

## 回滚方案
- revert 本次提交；Android 侧继续使用 TCP 作为唯一承载。

