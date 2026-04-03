# 2026-04-03_android-file-pull-download-v1

## 变更背景 / 目标
- 背景：
  - Android File v1 已能浏览目录、预览文本和创建目录，但还不能真正把远端文件下载到本地；
  - 现有 `hubmobile` 只有 file 控制面 await，缺少 `read_resp` / DATA / ACK 接收运行时；
  - 用户希望 Android 侧先具备基本可用的 `pull/download`，不要求一次性对齐 Win 的完整任务系统。
- 目标：
  - 为 Android 补齐第一个真正可用的远端文件下载链路；
  - 让 `hubmobile` 能处理 file 子协议的数据面并实际落盘；
  - 在 File 页面提供远端文件下载入口、固定下载根目录和明确的保存路径反馈。

## 具体变更内容
- 新增：
  - `hubmobile/file_runtime.go`
    - 增加 Android 侧 file runtime 适配层
    - 复用 `myflowhub-subproto/file` handler 处理 `read_resp` / DATA / ACK
    - 提供最小 fake `IServer` / parent `IConnection`
    - 通过有界 worker 队列避免把文件 I/O 直接放在 SDK read loop 中
  - `hubmobile/file_pull_test.go`
    - 覆盖 `FilePull` 接线、本地路径校验和 runtime 落盘/ACK 语义
  - `docs/change/2026-04-03_android-file-pull-download-v1.md`
    - 记录本轮 pull/download v1 变更与验证
- 修改：
  - `hubmobile/client.go`
    - 为 `await.Client` 安装 `SetOnFrame` 回调，观察 file 子协议帧
  - `hubmobile/file.go`
    - 新增 `FilePull`
    - 补 `source/hub/target`、`want_hash`、远端文件名、本地下载根目录校验
    - pull 启动结果回传 `local_base_dir` / `local_path`
  - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
    - 反射新增 `FilePull`
  - `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
    - 新增下载根目录 helper、本地保存路径推导、pull 启动结果解析
  - `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
    - 展示固定下载根目录
    - 为远端文件增加 `Download` 操作、确认弹窗和启动反馈
    - 显式限制本地节点不允许误触发下载
  - `app/src/test/java/com/myflowhub/android/FileProtocolSupportTest.kt`
    - 增加 pull/download 相关解析和路径测试
  - `docs/lessons/android-hubmobile-local-replace.md`
    - 补充“本地 Server worktree 漂移时，使用临时 modfile 隔离验证 Android hubmobile”的排查与验证方法

## Requirements impact
- none

## Specs impact
- none

## Lessons impact
- updated

## Related requirements
- none

## Related specs
- `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\specs\file.md`

## Related lessons
- `docs/lessons/android-hubmobile-local-replace.md`

## 对应 plan.md 任务映射
- `ANDFILEPULL-1`：归档旧控制文档并建立本轮 `plan.md`
- `ANDFILEPULL-2`：实现 `hubmobile` pull 接收运行时与导出 API
- `ANDFILEPULL-3`：补齐 Android File 下载入口和路径反馈
- `ANDFILEPULL-4`：补充 Go / Kotlin 单测并完成验证
- `ANDFILEPULL-5`：完成 3.3 自审与 4 阶段归档

## 经验 / 教训摘要
- Android 侧要补 `pull/download`，不能只加 `read(op=pull)` 控制请求；必须把 `subproto/file` 的接收运行时接进来，否则不会真正落盘。
- `await.Client.SetOnFrame` 适合做 file 帧观察和转发，但不适合直接做重 I/O；需要最小队列 + worker 隔离。
- 当本地 `MyFlowHub-Server` worktree 发生 API 漂移时，可以用临时 `modfile` 指向发布版 Server，只验证 Android 仓当前改动，避免被外部仓状态阻塞。

## 可复用排查线索
- 症状：
  - Android File 页面能看到 `Download`，但点了只返回启动响应，不落盘
  - `hubmobile` 能收到 `read_resp`，但后续 DATA / ACK 没有处理
  - `go test ./...` 在 Android worktree 下被 `MyFlowHub-Server` 本地漂移卡住
- 触发条件：
  - 只做控制面 await，没有 file runtime
  - 直接在 `onFrame` 内做同步重 I/O
  - Android 仓本地 replace 指向了漂移中的 Server worktree
- 关键词：
  - `FilePull`
  - `SetOnFrame`
  - `read_resp`
  - `DATA`
  - `ACK`
  - `file_runtime.go`
  - `go.verify.mod`
- 快速检查：
  - `hubmobile/client.go` 是否安装了 `SetOnFrame(onObservedFrame)`
  - `hubmobile/file_runtime.go` 是否只接 file 子协议帧并把处理交给 worker
  - pull 启动返回 JSON 是否包含 `local_base_dir` / `local_path`
  - 若 `go test ./...` 因外部 Server 漂移失败，先用临时 `modfile` 验证 Android 仓本轮代码

## 关键设计决策与权衡
- 决策：复用 `myflowhub-subproto/file` handler，而不是移植 Win 任务系统
  - 原因：当前目标是 Android 先具备最小可用下载链路，复用现有协议 runtime 变更面最小
- 决策：下载落盘路径采用固定 Android 下载根目录 + 远端 `dir/name` 镜像
  - 原因：与现有 file handler 语义一致，不额外引入 Win 风格 `saveDir/saveName`
- 决策：只对远端文件开放下载入口
  - 原因：避免本地节点和目录项误触发，同时保持产品语义清晰

## 测试与验证方式 / 结果
- 已执行：
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
  - `cd hubmobile; $env:GOWORK='off'; go test "-modfile=go.verify.mod" -mod=mod ./... -count=1 -p 1`
  - `ANDROID_HOME=C:\Users\HelloWorld\AppData\Local\Android\Sdk`
  - `ANDROID_SDK_ROOT=C:\Users\HelloWorld\AppData\Local\Android\Sdk`
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat :app:assembleDebug`
  - `.\scripts\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar`
- 结果：
  - 标准 `go test ./...` 仍受 `worktrees/MyFlowHub-Server` 本地依赖漂移阻塞
  - 使用临时 `modfile` 隔离 Android 仓依赖后，Go 单测通过
  - Android JVM unit test 通过
  - `assembleDebug` 通过
  - AAR 重建受本机 Android SDK 缺少 NDK 阻塞，`app/libs/myflowhub.aar` 未产出
- 备注：
  - 本轮没有修改 SDK / Server 仓；Go 标准命令失败属于外部 worktree 状态，不是本轮 Android 代码编译错误
  - 设备侧若要实际调用新的 `FilePull` 导出，仍需在补齐 NDK 后重新执行 `build_aar.ps1`

## 潜在影响与回滚方案
- 潜在影响：
  - Android File 页面新增下载动作和固定下载根目录提示
  - `hubmobile` 新增 file runtime 层，后续 file 数据面会经过该适配层
  - 新 AAR 需要包含 `FilePull` 才能在运行时生效
  - 若本机缺少 Android NDK，本地 AAR 无法重建，运行时仍会落到旧 AAR / 无 AAR 状态
- 回滚方案：
  - 回退以下文件：
    - `hubmobile/client.go`
    - `hubmobile/file.go`
    - `hubmobile/file_runtime.go`
    - `hubmobile/file_pull_test.go`
    - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
    - `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
    - `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
    - `app/src/test/java/com/myflowhub/android/FileProtocolSupportTest.kt`
    - `docs/change/2026-04-03_android-file-pull-download-v1.md`
    - `docs/lessons/android-hubmobile-local-replace.md`

## 子Agent执行轨迹
- 未使用子 Agent
