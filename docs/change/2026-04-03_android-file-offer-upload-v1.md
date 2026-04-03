# 2026-04-03_android-file-offer-upload-v1

## 变更背景 / 目标
- 背景：
  - Android File 已具备 `list/read_text/mkdir/pull`，但还不能从 Android 本地选择文件并发送到远端节点；
  - 现有 `hubmobile` 已接入 `subproto/file` runtime，可观察 `write_resp` / ACK，但没有对外的 `offer` 导出入口；
  - 用户希望 Android 先具备基本可用的 `offer/upload`，不要求一次性补齐 Win 的任务系统。
- 目标：
  - 为 Android 补齐第一个真正可用的本地文件上传链路；
  - 让 `hubmobile` 能发送 `file.write(op=offer)` 并在匹配的 `write_resp` 后真正发 DATA；
  - 在 File 页面提供 document picker、上传 staging 根目录和明确的启动反馈。

## 具体变更内容
- 修改：
  - `hubmobile/file.go`
    - 新增 `FileOffer`
    - 补 `source/hub/target`、`want_hash`、本地 staging 路径和空文件校验
    - 生成 `session_id`、按需计算 `sha256`，并把 `offer` 启动结果回传为 JSON
  - `hubmobile/file_pull_test.go`
    - 增加 `FileOffer` 接线测试
    - 增加 runtime 收到 `write_resp` 后发送 DATA 的测试
    - 增加空文件拒绝测试
  - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
    - 反射新增 `FileOffer`
  - `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
    - 新增 upload staging 根目录 helper
    - 新增 staging 目标路径推导、`offer` 启动结果解析和通用文件名校验
  - `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
    - 新增 `Upload` 入口
    - 接入 Android document picker
    - 在后台线程把选中文件复制到应用 staging 根目录
    - 展示远端目标路径、本地 staging 路径和启动反馈
  - `app/src/test/java/com/myflowhub/android/FileProtocolSupportTest.kt`
    - 增加 upload root、staging 路径、`offer` 启动结果和文件名校验测试
- 新增：
  - `docs/change/2026-04-03_android-file-offer-upload-v1.md`
    - 记录本轮 offer/upload v1 变更与验证
  - `docs/lessons/android-file-offer-staging.md`
    - 记录 Android `offer` 必须先做本地 staging 的原因和排查方法

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
- `docs/lessons/android-file-offer-staging.md`
- `docs/lessons/android-hubmobile-local-replace.md`

## 对应 plan.md 任务映射
- `ANDFILEOFFER-1`：归档上一轮 `plan.md` 并建立本轮控制文档
- `ANDFILEOFFER-2`：为 `hubmobile` 补齐 `offer/upload` 导出与运行时接线
- `ANDFILEOFFER-3`：在 Android File 页面补齐 document picker、staging 和上传 UI
- `ANDFILEOFFER-4`：补充 Go / Kotlin 测试并完成本地验证
- `ANDFILEOFFER-5`：完成 3.3 自审与 4 阶段归档

## 经验 / 教训摘要
- Android `offer/upload` 不能只发 `write(op=offer)` 控制帧；真正的数据发送依赖 `write_resp` 后由 `subproto/file` sender 继续发 DATA。
- 对 Android document picker 而言，`content://` URI 不能直接拿给当前 Go sender 使用；必须先把文件复制到 `file.base_dir + dir/name`。
- 当前 runtime 的 `file.base_dir` 是按操作配置的；在没有独立任务系统之前，UI 应保持单操作串行，避免并发切换 base dir。
- 当本地 `MyFlowHub-Server` worktree 漂移阻塞 Android 仓 Go 测试时，可以沿用临时 `modfile` 做隔离验证。

## 可复用排查线索
- 症状：
  - Android 端点击 `Upload` 后只看到启动响应，但远端没有收到文件
  - `offer` 控制请求返回成功，但没有后续 DATA
  - 选择了本地文件后仍提示 `file not found` 或 `offer source must be a file`
  - `build_aar.ps1` 日志里出现 `Done`，但实际 AAR 文件不存在
- 触发条件：
  - 选中的文件没有先复制到 `file.base_dir + dir/name`
  - `content://` URI 只保存在 UI 层，没有转换成 app 可控的本地路径
  - 本机 Android SDK 没有可用 NDK
- 关键词：
  - `FileOffer`
  - `write_resp`
  - `sendFileData`
  - `upload-staging`
  - `expectedUploadStagePath`
  - `no usable NDK`
  - `go.verify.mod`
- 快速检查：
  - `hubmobile/file.go` 是否新增了 `FileOffer`
  - `FileScreen.kt` 是否先做 staging，再调用 `go.fileOffer(...)`
  - `subproto/file` 的 sender 是否会从 `file.base_dir + dir/name` 解析本地文件
  - 执行 `Get-Item app/libs/myflowhub.aar` 确认 AAR 是否真的产出，而不是只看脚本日志

## 关键设计决策与权衡
- 决策：复用现有 `subproto/file` sender，而不是在 Android 里另写一套 DATA 发送器
  - 原因：协议语义已经成熟，最小改动即可拿到正确的数据面行为
- 决策：上传 staging 根目录放到应用私有目录，而不是复用用户可见下载目录
  - 原因：上传源文件只用于内部 sender，不需要暴露给用户，也避免污染下载区
- 决策：本轮只做“当前远端目录 + 本地原始文件名”语义
  - 原因：先交付最小可用上传，避免把远端重命名、多文件选择和任务系统一起带进来
- 决策：空文件在发起前直接失败
  - 原因：当前 `subproto/file` sender 对 `resp.Size == 0` 不会真正起发送，显式失败比静默无效更安全

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
  - 使用临时 `modfile` 移除本地 Server replace 后，Go 单测通过
  - Android JVM unit test 通过
  - `assembleDebug` 通过
  - `build_aar.ps1` 底层 `gomobile` 仍报 `no usable NDK`，且 `app/libs/myflowhub.aar` 不存在；本地运行时仍需补齐 NDK 后重建

## 潜在影响与回滚方案
- 潜在影响：
  - Android File 页面新增 `Upload` 动作和 upload staging 根目录提示
  - 应用会把用户选择的文件复制到私有 staging 目录，再由 Go sender 使用
  - 新 AAR 需要包含 `FileOffer` 才能在设备运行时生效
  - 当前本机环境仍无法本地重建 AAR，运行时验证受 NDK 缺失阻塞
- 回滚方案：
  - 回退以下文件：
    - `hubmobile/file.go`
    - `hubmobile/file_pull_test.go`
    - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
    - `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
    - `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
    - `app/src/test/java/com/myflowhub/android/FileProtocolSupportTest.kt`
    - `docs/change/2026-04-03_android-file-offer-upload-v1.md`
    - `docs/lessons/android-file-offer-staging.md`
    - `docs/lessons/README.md`

## 子Agent执行轨迹
- 未使用子 Agent
