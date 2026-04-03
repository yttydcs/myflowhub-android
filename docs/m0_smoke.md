# M0 冒烟验证（Android Hub + UI）

> 目标：验证 Android 端作为 Hub（前台服务常驻 + 局域网可见）跑通两条最小链路：  
> 1) **LAN 直连手机 Hub**（手动填写 `IP:port` 访问）  
> 2) **手机 Hub 上联 Parent Hub**，并由 Parent 侧转发 `management` 命令到手机

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
cd d:\project\MyFlowHub3\repo\MyFlowHub-Android

# 产物输出到 app/libs/myflowhub.aar
.\scripts\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar
```

成功后应存在：`app/libs/myflowhub.aar`。

> 说明：`build_aar.ps1` 会优先读取 `ANDROID_HOME` / `ANDROID_SDK_ROOT`，未设置时会尝试 `%LOCALAPPDATA%\Android\Sdk`。若缺少可用 NDK，脚本会直接失败并提示安装 `ndk;26.1.10909125`，不再把失败伪装成成功。

> 注意：必须先构建 AAR，再构建 APK。若你先构建了 APK，需要重新 `assembleDebug` 才能把 AAR 打进 APK。

## 2. 构建 APK

```powershell
cd d:\project\MyFlowHub3\repo\MyFlowHub-Android
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

> 若 App 状态里 `NodeID` 显示为 `(stub)`：说明 AAR 未被打包进 APK（会回退到 StubHubBridge），请回到第 1/2 步检查构建顺序并重装 APK。

## 4. 冒烟 A：LAN 直连手机 Hub（management node_echo）

从同一局域网的另一台设备（PC/另一手机）对 Android Hub 发起 `management node_echo`：

- 目标：对 Hub 执行 `node_echo`，期望响应 `node_echo_resp`，且 `code=1`、`echo=ping`。

### 4.1 推荐：使用本仓 `tools/hubsmoke`（不依赖 Win UI）

1) 获取手机 IP：在手机 Wi‑Fi 详情页查看（示例：`192.168.1.50`）。
2) 在 PC 上执行：

```powershell
cd d:\project\MyFlowHub3\repo\MyFlowHub-Android\tools\hubsmoke
.\run.ps1 echo -addr 192.168.1.50:9000 -message ping
```

期望输出包含 `echo ok`（或等价成功信息）。

### 4.2 备选：使用现有客户端（例如 Win 端）

用 Win 客户端直连手机 `IP:9000`，执行 `management node_echo`，检查响应。

## 5. 冒烟 B：手机 Hub 上联 Parent，并由 Parent 转发到手机（management node_echo）

### 5.1 启动 Parent Hub（PC）

用 `hub_server` 作为最小 parent hub（示例监听 `:9000`）：

```powershell
cd d:\project\MyFlowHub3\repo\MyFlowHub-Server
go run .\\cmd\\hub_server -addr :9000 -node-id 1
```

> 注意：需要确保 Windows 防火墙允许局域网设备访问该端口。

### 5.2 手机填写 Parent addr 并启动

1) `Parent addr` 填 PC 的局域网地址（示例：`192.168.1.10:9000`）
2) `Self ID` 保持稳定（建议固定一个字符串，例如 `android-hub-01`）
3) 点击 `Start`，观察状态中 `Parent connected: true`（或保持 running 且无错误）

### 5.3 从 Parent 侧转发 `node_echo` 到手机节点

1) 先在 Parent 侧查询手机的 `node_id`（list_nodes）：

```powershell
cd d:\project\MyFlowHub3\repo\MyFlowHub-Android\tools\hubsmoke
.\run.ps1 list-nodes -addr 192.168.1.10:9000
```

2) 记下手机对应的 `node_id`（示例：`2`），然后转发 `echo`：

```powershell
cd d:\project\MyFlowHub3\repo\MyFlowHub-Android\tools\hubsmoke
.\run.ps1 echo -addr 192.168.1.10:9000 -target 2 -message ping
```

期望输出包含 `echo ok`，且 `echo=ping`。

## 6. 常见问题

- 若 `Start` 后无法从局域网访问：
  - 确认手机与客户端在同一 Wi-Fi/LAN
  - 确认监听地址为 `:9000` 或 `0.0.0.0:9000`（不要只监听 `127.0.0.1`）
  - 检查手机防火墙/路由器隔离（AP isolation）

- 若 `NodeID` 显示为 `(stub)`：
  - 确认 `app/libs/myflowhub.aar` 已存在
  - 重新执行 `.\gradlew :app:assembleDebug` 并重装 APK

- 若手机无法连上 Parent：
  - 确认 `Parent addr` 填的是 PC 局域网 IP（不是 `127.0.0.1`）
  - 确认 Parent 侧端口已监听（PC 上 `go run` 窗口无报错）
  - 检查 PC 防火墙入站规则/路由器隔离

- 若 Parent 侧 `list-nodes` 看不到手机 node：
  - 等待 1–3 秒（parent bootstrap watcher 需要一点时间）
  - 检查手机 UI `Parent connected` 是否为 true
  - 确认手机 `Self ID` 非空且稳定（用于 parent bootstrap）

