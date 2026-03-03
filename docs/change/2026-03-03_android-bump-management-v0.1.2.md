# 2026-03-03 Android：升级 hubmobile 的 management 至 v0.1.2（触发 debug-latest）

## 背景 / 目标
- 背景：`myflowhub-subproto/management v0.1.1` 的 `list_nodes` 会把 upstream(parent) 链接也枚举出来，可能导致设备树回指/重复节点。
- 目标：
  - Android 侧 `hubmobile` 依赖升级到 `github.com/yttydcs/myflowhub-subproto/management v0.1.2`（children-only 修复）；
  - 合并并 push `main`，触发 GitHub Actions 更新 `debug-latest`（直接下载 APK/AAR）。

## 变更内容
- 依赖升级：
  - `hubmobile/go.mod`：`github.com/yttydcs/myflowhub-subproto/management v0.1.1` → `v0.1.2`（indirect）
  - `hubmobile/go.sum`：同步更新 checksum
- 审计与交接：
  - 归档旧 `plan.md`：`docs/plan_archive/plan_archive_2026-03-03_android-bump-management-v0.1.2-prev.md`
  - 新增本次 workflow 计划：`plan.md`

## Plan 任务映射
- ANDMG0：归档旧 plan.md
- ANDMG1：升级 hubmobile 的 management 依赖到 v0.1.2
- ANDMG2：本地验证（Go/Gradle）

## 关键设计决策与权衡
- 仅做依赖升级：不改任何 UI/协议 wire schema；用最小变更确保 Android 构建确定性地使用 children-only 修复版本。
- 本地验证尽量贴近 CI：在具备本地 Android SDK 的情况下验证 `gomobile bind` 与 `assembleDebug`，降低 main push 后 CI 失败概率。

## 测试与验证
- Go（hubmobile）：
  - `cd hubmobile; GOWORK=off go test ./... -count=1 -p 1`
- AAR（gomobile bind，需 Android SDK/NDK）：
  - 设置：
    - `ANDROID_HOME=D:\project\MyFlowHub3\_android-sdk`
    - `ANDROID_NDK_HOME=D:\project\MyFlowHub3\_android-sdk\ndk\26.1.10909125`
  - 执行：
    - `gomobile init`
    - `cd hubmobile; gomobile bind -target android/arm64 -androidapi 26 -javapkg com.myflowhub.gomobile -o ..\app\libs\myflowhub.aar .`
- Android Debug APK：
  - 设置：
    - `ANDROID_HOME=D:\project\MyFlowHub3\_android-sdk`
  - 执行：
    - `.\gradlew.bat :app:assembleDebug --stacktrace --no-daemon --console=plain`

## 潜在影响
- 运行时行为变化来自 `management v0.1.2` 的 children-only 语义收敛（不再返回 parent/upstream）。
- 若未来需要调试 upstream 拓扑，应新增独立的 upstream 查询 action，而不是复用 `list_nodes`。

## 回滚方案
- 回滚提交，或执行：
  - `cd hubmobile`
  - `GOWORK=off go get github.com/yttydcs/myflowhub-subproto/management@v0.1.1`
  - `GOWORK=off go mod tidy`

