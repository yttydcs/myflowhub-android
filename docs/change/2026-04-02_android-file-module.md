# 2026-04-02_android-file-module

## 变更背景 / 目标
- 背景：
  - Android 端虽然已经有 `file` 子协议编号和通用协议控制台，但正式产品入口仍缺失；
  - 用户若想浏览目录或预览文本，只能手工填写 `SubProto=5`、action 和 JSON；
  - 进一步核对后确认，现有 `hubmobile.SendAndAwait` 不会为 file 子协议补 `KindCtrl` 前缀，因此不能直接拿来做正式 File await 能力。
- 目标：
  - 为 Android 端补齐 File v1 正式入口；
  - 至少跑通目录浏览、文本预览和新建目录；
  - 让 `hubmobile` 具备 file 专用 await 封装，确保协议语义正确。

## 具体变更内容
- 新增：
  - `hubmobile/file.go`
    - 新增 `FileList` / `FileReadText` / `FileCreateDir`
    - 统一补 `KindCtrl` 前缀，并按 Win 现有语义走 `header TargetID=hubId`、`data.target=目标节点`
  - `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
    - 统一处理目录规范化、目录名校验、list/read_text/mkdir 响应解析
  - `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
    - 提供 Android File v1 页面，支持目标节点输入、目录进入/返回、文本预览、新建目录
  - `app/src/test/java/com/myflowhub/android/FileProtocolSupportTest.kt`
    - 覆盖 helper 的路径规范化、目录名校验和响应解析
- 修改：
  - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
    - 反射新增 `FileList` / `FileReadText` / `FileCreateDir`
  - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
    - 主导航新增 `File` 入口
  - `app/build.gradle.kts`
    - 单测增加 `org.json:json`，避免本地 JVM unit test 落到 Android stub JSON 实现
  - `docs/lessons/android-hubmobile-local-replace.md`
    - 补充 Android worktree 下相对 `replace` 需要目录镜像 / junction 的排查与修复规则

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
- `ANDFILE-1`：归档旧控制文档并建立本轮 `plan.md`
- `ANDFILE-2`：新增 `hubmobile` file 导出 API、Android helper 与 `GoClientBridge` 封装
- `ANDFILE-3`：新增 `FileScreen` 并接入 `AppRoot` 导航
- `ANDFILE-4`：补充 `FileProtocolSupportTest` 并完成本地验证
- `ANDFILE-5`：完成 3.3 自审与 4 阶段归档

## 经验 / 教训摘要
- Android File 模块不能直接复用通用 `SendAndAwait`，因为 file 协议控制帧要求 `KindCtrl + JSON(message)`。
- Win 现有 file 路由语义值得直接复用：控制帧 header 打到 `hubId`，实际目标节点写到 `data.target`。
- Android 仓一旦在 worktree 下做 `hubmobile` 本地验证，就要考虑 `go.mod` 相对 `replace` 的路径拓扑，而不是只看主仓 `repo/` 目录是否齐全。

## 可复用排查线索
- 症状：
  - Android `File` 页面能编译，但 file 请求实际不返回或 await 超时
  - `hubmobile` 在 Android worktree 下执行 `go test` / `gomobile bind` 时找不到 `../../MyFlowHub-Server`
- 触发条件：
  - 试图把 file 请求简单包在通用 `SendAndAwait` 上
  - Android 仓从 `worktrees/<branch>` 路径执行 `hubmobile` 构建
- 关键词：
  - `KindCtrl`
  - `read_resp`
  - `write_resp`
  - `hubmobile/file.go`
  - `../../MyFlowHub-Server`
  - `junction`
- 快速检查：
  - file 控制帧是否显式补了 `payload[0]=0x01`
  - 请求 header 是否打到 `hubId`，而不是直接打到浏览目标节点
  - `D:\project\MyFlowHub3\worktrees\MyFlowHub-Server` / `MyFlowHub-SDK` / `MyFlowHub-Proto` 是否存在

## 关键设计决策与权衡
- 决策：新增 `hubmobile/file.go`，而不是继续包通用 `SendAndAwait`
  - 原因：只有这样才能正确表达 file 控制帧 wire 语义
- 决策：File v1 只做浏览、预览、mkdir
  - 原因：先补齐 Android 正式入口，不把 Win 的传输任务体系一次性拖进来
- 决策：保留目标节点手工输入
  - 原因：这是当前 Android 端最小可用路径，节点树选择器可后续再补

## 测试与验证方式 / 结果
- 已执行：
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
  - `ANDROID_HOME=D:\project\MyFlowHub3\_android-sdk`
  - `ANDROID_SDK_ROOT=D:\project\MyFlowHub3\_android-sdk`
  - `.\gradlew.bat testDebugUnitTest`
  - `.\scripts\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar`
  - `.\gradlew.bat :app:assembleDebug`
- 结果：
  - 全部通过
- 备注：
  - 为了让 Android worktree 下的 `hubmobile/go.mod` 相对 `replace` 可解析，本地验证前补了以下 dependency mirror / junction：
    - `D:\project\MyFlowHub3\worktrees\MyFlowHub-Server`
    - `D:\project\MyFlowHub3\worktrees\MyFlowHub-SDK`
    - `D:\project\MyFlowHub3\worktrees\MyFlowHub-Proto`

## 潜在影响与回滚方案
- 潜在影响：
  - Android 主导航新增 `File` 页面
  - `hubmobile` 导出方法新增 file 专用 API，需要新 AAR 才能在运行时生效
  - 本地 Android worktree 的 Go/AAR 验证现在默认依赖 `worktrees/` 下的依赖目录镜像
- 回滚方案：
  - 回退以下文件：
    - `hubmobile/file.go`
    - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
    - `app/src/main/java/com/myflowhub/android/FileProtocolSupport.kt`
    - `app/src/main/java/com/myflowhub/android/ui/FileScreen.kt`
    - `app/src/main/java/com/myflowhub/android/ui/AppRoot.kt`
    - `app/src/test/java/com/myflowhub/android/FileProtocolSupportTest.kt`
    - `app/build.gradle.kts`
    - `docs/change/2026-04-02_android-file-module.md`
    - `docs/lessons/android-hubmobile-local-replace.md`

## 子Agent执行轨迹
- 未使用子 Agent
