# M0 冒烟验证（Android Hub + UI）

> 目标：验证 Android 端作为 Hub（前台服务常驻 + 局域网可见）能跑通最小链路，并可被局域网设备访问。

## 0. 前置条件

1) 安装：
   - Go（与本 workspace 一致的版本/工具链）
   - JDK（建议 17）
   - Android SDK（建议安装 `platforms;android-34`）
   - Android NDK（gomobile 需要，版本按你本机环境为准）
2) 环境变量（示例）：
   - `JAVA_HOME`
   - `ANDROID_HOME` 或 `ANDROID_SDK_ROOT`
   - `ANDROID_NDK_HOME`（或确保 SDK/NDK 可被 gomobile 发现）

## 1. 构建 AAR（gomobile bind）

```powershell
cd d:\project\MyFlowHub3\worktrees\android-hub-m0\MyFlowHub-Android

# 产物输出到 app/libs/myflowhub.aar
pwsh .\scripts\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.native -OutFile app/libs/myflowhub.aar
```

成功后应存在：`app/libs/myflowhub.aar`。

## 2. 构建 APK

```powershell
cd d:\project\MyFlowHub3\worktrees\android-hub-m0\MyFlowHub-Android
.\gradlew :app:assembleDebug
```

产物默认路径（可能随 Gradle 版本变化）：`app/build/outputs/apk/debug/app-debug.apk`

## 3. 安装与启动

1) 安装 APK（示例）：
   - 使用 Android Studio 安装
   - 或用 `adb install -r` 安装
2) 打开 App：
   - `Listen addr`：建议 `:9000`（绑定所有网卡）
   - `Parent addr`（可选）：例如 `192.168.1.10:9000`
   - `Self ID`：保持稳定（用于自注册与父链 bootstrap）
3) 点击 `Start`：
   - 通知栏出现常驻通知（Foreground Service）

## 4. 局域网验证（management node_echo）

从同一局域网的另一台设备（PC/另一手机）对 Android Hub 发起 `management node_echo`：

- 目标：对 Hub 执行 `node_echo`，期望响应 `node_echo_resp`，且 `code=1`、`echo=ping`。

你可以用现有的 MyFlowHub 客户端（例如 Win 端）直接连到手机的 `IP:9000` 执行该动作。

## 5. 常见问题

- 若 `Start` 后无法从局域网访问：
  - 确认手机与客户端在同一 Wi-Fi/LAN
  - 确认监听地址为 `:9000` 或 `0.0.0.0:9000`（不要只监听 `127.0.0.1`）
  - 检查手机防火墙/路由器隔离（AP isolation）

