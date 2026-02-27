# 2026-02-27 - Android：修复 Android 15 前台服务崩溃 + gomobile 反射兼容（v0.1.3）

## 变更背景 / 目标

`v0.1.2` 在 Android 15（targetSdk=34）上存在两个关键问题：

1) 点击 `Hub -> Start` 会导致进程崩溃退出；
2) `Login` 页面提示 `Go AAR unavailable`，导致 gomobile 侧能力不可用。

本次变更目标：

- 修复前台服务启动崩溃（Android 15 合规）；
- 修复 gomobile Java 方法名差异导致的反射失败；
- 后续通过 `v0.1.3` tag 自动构建 release APK 供下载验证。

## 具体变更内容（新增 / 修改 / 删除）

对应分支 / 提交：

- Branch：`fix/android-fgs-type-gomobile-reflect`
- Commit：`f695e55`

### ANDFIX-1：Manifest 声明 foreground service type（dataSync）

文件：

- `app/src/main/AndroidManifest.xml`

变更：

- 增加权限 `android.permission.FOREGROUND_SERVICE_DATA_SYNC`；
- 为 `HubService` 声明 `android:foregroundServiceType="dataSync"`。

### ANDFIX-2：HubService 使用带 type 的 startForeground

文件：

- `app/src/main/java/com/myflowhub/android/HubService.kt`

变更：

- 使用 `ServiceCompat.startForeground(..., ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)`；
- 兼容 `minSdk=26`（低版本自动回退到 2 参数 `startForeground`）。

### ANDFIX-3：gomobile 反射方法名兼容（首字母大小写）

文件：

- `app/src/main/java/com/myflowhub/android/GoReflect.kt`（新增）
- `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`（改用 `GoReflect.method`）
- `app/src/main/java/com/myflowhub/android/HubBridge.kt`（改用 `GoReflect.method`）

变更：

- 对每个 gomobile 方法名同时尝试：原名、首字母小写、首字母大写；
- 解决 `EnsureInit/ensureInit` 等命名差异导致的 `NoSuchMethodException`。

## plan.md 任务映射

- ANDFIX-1：Manifest 前台服务类型声明
- ANDFIX-2：HubService 传递 foreground service type
- ANDFIX-3：gomobile 反射兼容
- ANDFIX-4：本地构建验证（见下文）

## 关键设计决策与权衡（性能 / 扩展性）

1) Foreground service type 选型：`dataSync`
   - 理由：Hub 的常驻任务主要是网络收发/转发/同步，语义贴近；
   - 影响：未来若引入定位/媒体播放等行为，需要调整 type 与权限。
2) 使用 `ServiceCompat.startForeground`
   - 理由：统一处理 API 版本差异，避免多分支代码；
   - 性能：无额外开销（启动路径一次性调用）。
3) 反射兼容策略范围
   - 本次仅兼容“首字母大小写”变体，属于最小修复；
   - 若未来 gomobile 生成规则变化更大，再扩展候选策略。

## 测试与验证方式 / 结果

本地（开发机）：

- `.\gradlew.bat :app:assembleDebug`：通过

真机（Android 15）验收建议：

1) 安装 `v0.1.3` 后进入 `Hub -> Start`：
   - 不应再出现 `MissingForegroundServiceTypeException`；
   - 应出现常驻通知。
2) 进入 `Login`：
   - 不应再提示 `Go AAR unavailable`；
   - `EnsureKeys/Connect` 等按钮可正常调用（目标 Hub 可达时）。

## 潜在影响与回滚方案

影响：

- Manifest 新增 `FOREGROUND_SERVICE_DATA_SYNC` 权限与 `dataSync` 类型声明；
- 反射方法查找逻辑变为“多候选”，对旧 AAR 命名更兼容。

回滚：

- 回退提交 `f695e55`；
- 或撤销 FGS type 相关变更（不建议，会在 Android 15 上复现崩溃）。

