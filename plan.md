# Plan - Android：RFCOMM（Bluetooth Classic）Provider（供 Core/Server 通过 Pipe 复用）

## Workflow 信息
- Repo：`MyFlowHub-Android`
- 分支：`feat/bluetooth-rfcomm-transport`
- Worktree：`d:\project\MyFlowHub3\worktrees\feat-bluetooth-rfcomm-transport\repo\MyFlowHub-Android`
- Base：`main`
- 依赖仓：
  - `MyFlowHub-Core`：定义 Android RFCOMM Provider 接口（Go），并在 Android build tag 下通过 Provider 实现 dial/listen
  - `MyFlowHub-Server`：hubruntime 装配 RFCOMM listener（Android 侧运行时依赖本 Provider）

## 背景 / 问题陈述（事实，可审计）
- Android 平台的 Bluetooth Classic RFCOMM 需要使用 Java/Kotlin API（`BluetoothSocket/BluetoothServerSocket`）。
- 由于 Go 侧不直接调用 Android 蓝牙栈，本仓需要提供一个“Java 实现 Go interface”的 Provider，并在启动 hub runtime 前注入到 Go（供 Core RFCOMM transport 使用）。
- 用户确认：Android 默认 `secure=true`（优先安全连接），但允许参数切换。

## 目标
1) 实现 Android RFCOMM Provider（Java/Kotlin）：
  - 支持 listen（服务端 accept）与 dial（客户端 connect）；
  - UUID-first：固定默认 MyFlowHub UUID；同时支持从参数传入覆盖；
  - 输出为 Go 可用的“字节流 Pipe”能力（Read/Write/Close）。
2) 在 app 启动或调用 `Hubmobile.Start()` 前完成 Provider 注入，否则 Go 侧应给出明确错误提示。
3) 权限/错误信息可诊断：对 Android 12+ 的权限缺失给出可读错误。

## 非目标
- 本轮不做设备扫描 UI（按设备名解析/MAC 扫描作为未来扩展点）。
- 不改业务协议/子协议。

## 验收标准
- 代码侧：
  - `cd hubmobile; GOWORK=off go test ./...` 通过（主机平台编译通过即可）。
  - `scripts/build_aar.sh` 可构建（如环境具备 Android SDK/NDK）。
- 手工冒烟（需要真机）：
  - 两台 Android/或 Android↔PC 之间，通过同一 UUID 建立 RFCOMM 连接；
  - 连接建立后可完成至少一条 MyFlowHub 帧的收发（配合 Server/SDK 冒烟）。

## 3.1) 计划拆分（Checklist）

### AND-BT0 - 归档旧 plan（已执行）
- 已执行：`git mv plan.md docs/plan_archive/plan_archive_2026-03-12_bluetooth-rfcomm-transport-android-prev.md`

### AND-BT1 - Kotlin/Java 实现 RFCOMM Provider（dial + listen）
- 目标：实现 Provider，满足 Core 定义的 Go interface（gomobile 可绑定的签名），并封装 BluetoothSocket 读写关闭。
- 涉及模块/文件（预期）：
  - `app/src/main/java/**`（新增 provider 实现）
  - `hubmobile/*`（暴露注入入口：如 `SetRFCOMMProvider(...)`）
- 验收条件：
  - Provider 可被注入且不崩溃；
  - 错误路径可读（权限/蓝牙关闭/UUID 无服务等）。
- 回滚点：revert。

### AND-BT2 - app 集成：启动前注入 Provider
- 目标：在调用 `Hubmobile.Start()` 前注入 Provider，并按配置决定是否启用 RFCOMM listener/dial。
- 涉及文件（预期）：
  - `app/src/main/java/**`（启动逻辑）
- 验收条件：未注入时能提示；注入后可进入 RFCOMM 逻辑路径。
- 回滚点：revert。

### AND-BT3 - Code Review（强制）
- 审查项：需求覆盖/架构/性能/可读性/扩展性/稳定性与安全/测试覆盖。

### AND-BT4 - 归档变更（强制）
- 输出：`docs/change/2026-03-12_bluetooth-rfcomm-transport-android.md`
- 标注：重大变更（新增 Android 蓝牙 transport 接入点）。

### AND-BT5 - 合并 / push（需 workflow 结束后执行）
- 在 `repo/MyFlowHub-Android` 合并到 `main` 并 push。

