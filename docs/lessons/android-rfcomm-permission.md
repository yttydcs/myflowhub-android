# Android RFCOMM Permission

## Summary
- Android 端的 Bluetooth Classic RFCOMM 不是“只写 provider 代码就能用”。从 Android 12 开始，若未声明并授予 `BLUETOOTH_CONNECT`，`bt+rfcomm://...` 的 connect / parent dial 会在权限层直接失败。

## Lookup Hints
- `bt+rfcomm://`
- `BLUETOOTH_CONNECT`
- `SecurityException`
- `蓝牙权限`
- `BluetoothAdapter`
- `createRfcommSocketToServiceRecord`
- `listenUsingRfcommWithServiceRecord`

## Symptoms
- Android Login 页填写 `bt+rfcomm://...` 后，Connect 立即失败。
- Android Hub 使用 RFCOMM 父链时，Start 失败或无法上联。
- UI 只看到模糊异常，或只知道“连接失败”，但不知道是权限问题。

## Impact
- Android 上 RFCOMM 路径不可用。
- 用户会误以为 RFCOMM 本身不支持，实际是权限和入口文案未收口。

## Trigger Conditions
- Android 12+ 设备。
- 输入使用 `bt+rfcomm://...` endpoint。
- Manifest 未声明蓝牙权限，或运行时未授予 `BLUETOOTH_CONNECT`。

## Root Cause
- RFCOMM provider 已接入，但 Android 宿主未补齐蓝牙权限声明和运行时授权。
- UI 入口仍以 `ip:port` 文案为主，导致用户不知道 endpoint 语义和 RFCOMM 前置条件。

## Investigation Trail
- 先确认仓内是否存在 RFCOMM provider：`BluetoothRfcommProvider.kt`
- 再确认 Manifest 是否声明蓝牙权限
- 再确认是否有 Android 12+ 的运行时授权流程
- 最后看 Login / Hub 入口是否把 RFCOMM endpoint 当成普通 TCP 输入处理

## Resolution
- 在 Manifest 中声明 RFCOMM 相关权限。
- 对 Android 12+ 按需申请 `BLUETOOTH_CONNECT`。
- 在 RFCOMM 入口前做权限检查，并把失败提示改为可诊断文案。
- 更新 Login / Hub 输入文案，明确支持 `bt+rfcomm://...`

## Prevention / Guardrails
- 任何新增的 Android RFCOMM 入口，都必须复用统一 helper 判断 endpoint 与权限状态。
- 不要只依赖 UI 权限检查；provider 侧也要把 `SecurityException` 转成清晰错误。
- 不要用仅 `ip:port` 的文案描述实际支持 endpoint 的输入框。

## Related Docs
- [2026-03-31_android-rfcomm-basic-usability.md](../change/2026-03-31_android-rfcomm-basic-usability.md)
- [2026-03-12_bluetooth-rfcomm-transport-android.md](../change/2026-03-12_bluetooth-rfcomm-transport-android.md)
