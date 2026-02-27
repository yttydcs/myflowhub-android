# 2026-02-27 - Android：手机作为 Hub v1（Login + Devices + Logs + 协议入口基座）

## 背景 / 目标

为 Android 端补齐“手机作为 Hub + 手机作为 Client 操作 Hub”的最小闭环能力，参考 Win 端交互方式，满足：
- 手动填写 `ip:port`（局域网可见），暂不做自动发现
- 提供登录页（连接/注册/登录）
- 将 Management 更名为 Devices，并实现树/详情/配置编辑
- 提供日志查看（10k 行 ring buffer）
- 为后续逐步跑通各子协议提供可用的 UI 入口（B：每个协议独立入口，先复用通用控制台）

## 具体变更内容

### Go（hubmobile / AAR）

1) 设备身份与鉴权基座
- 新增 `EnsureInit/EnsureKeys/Register/Login/AuthState/GetSelfNodeID/GetLastError`
- Node Keys 持久化到 app workdir：`hub/config/node_keys.json`（与 Win/Server 结构对齐）
- Login 签名算法：ES256（与 Win 的签名规则对齐）

2) Client 会话与通用请求
- 新增 `Connect/Close/IsConnected/LastAddr`
- 基于 `myflowhub-sdk/await.Client` 实现 `SendAndAwait`（按 `MsgID + SubProto + Action` 匹配响应）

3) Devices（management 协议）封装
- 新增 `ListNodes/ListSubtree/NodeInfo/ConfigList/ConfigGet/ConfigSet`（JSON 入/出，便于 Kotlin 直接渲染）

4) 日志（10k ring buffer）
- 新增 `LogsPull(cursor, limit)`：cursor/limit 分页拉取
- 通过 `hubruntime.Options.Logger` 注入自定义 `slog.Handler`，统一收集 hub runtime + client 操作日志
- ring buffer 使用循环队列，避免追加时 O(n) 拷贝

5) gomobile 依赖稳定性
- 增加 `hubmobile/gomobile_deps.go`（`//go:build !android`）：
  - 通过空导入 `_ "golang.org/x/mobile/bind"` 固定 module graph，避免 `gomobile bind` 报 “no Go package in golang.org/x/mobile/bind”

### Android（Compose UI）

1) 导航与页面
- 新增 `Login/Hub/Devices/Logs/Protocols` 五个入口（底部导航）
- 保留 Hub 前台服务能力（Start/Stop/Status）

2) Login 页（参照 Win）
- 连接/断开、EnsureKeys、注册、登录、清空本地 auth 信息
- 展示：Connected/LastAddr/LastError、HubID、Role

3) Devices 页（原 Management -> Devices）
- 树：按需展开加载（`list_nodes`），root 可切换 `direct/subtree`
- 详情：`node_info`
- 配置：`config list/get/set`

4) Logs 页
- 分页拉取与展示（默认拉取 200 行，UI 仅显示最近 400 行）

5) Protocols 页（B：独立入口）
- `auth/varstore/topicbus/file/flow/exec` 提供独立入口
- v1 先复用通用控制台（`SendAndAwait`），便于逐步跑通；file 子协议后续可能需要补齐 ctrl kind 前缀适配

## 任务映射（plan.md）

- ANDH1：归档旧 plan/todo（`docs/plan_archive/*`）
- ANDH2：Go 身份/会话/登录（`hubmobile/keys.go`、`hubmobile/client.go`、`hubmobile/workdir.go`）
- ANDH3：Go Devices（management）封装（`hubmobile/management.go`）
- ANDH4：Go 日志 ring buffer（`hubmobile/logs.go`、`hubmobile/logs_test.go`）
- ANDH5：Compose 导航 + Login（`app/src/main/java/.../ui/AppRoot.kt`、`LoginScreen.kt`、`GoClientBridge.kt`）
- ANDH6：Devices 页面（`DevicesScreen.kt`）
- ANDH7：Logs + 子协议入口（`LogsScreen.kt`、`ProtocolsScreen.kt`）
- ANDH8：验证（见下）

## 关键设计决策与权衡

1) Go 侧以 “JSON 字符串 API” 对外暴露
- 原因：gomobile + Kotlin 反射集成最稳定；避免跨语言复杂类型映射与签名不一致问题
- 影响：Kotlin 侧需做少量 JSON 解析；后续可逐步替换为更强类型的封装

2) 日志 ring buffer 选循环队列
- 原因：10k 行上限下保持 O(1) 追加，避免频繁移动切片导致性能劣化

3) Devices 树按需加载
- 原因：避免一次性拉取全量导致卡顿/流量放大；并保留 root 的 `subtree` 模式便于快速看全局

4) CI/本地构建一致性
- `hubmobile` 保持 `replace myflowhub-server => ../../MyFlowHub-Server`（用于 CI 与 meta-workspace 本地开发）
- `gomobile_deps.go` 用于确保 `gomobile bind` 稳定

## 测试与验证方式 / 结果

1) Go 单测
- `cd hubmobile; $env:GOWORK='off'; go test ./...`（通过）

2) 生成 AAR（Windows 本地）
- 设置环境变量：
  - `ANDROID_HOME=D:/project/MyFlowHub3/_android-sdk`
  - `ANDROID_NDK_HOME=D:/project/MyFlowHub3/_android-sdk/ndk/26.1.10909125`
- 执行：`.\scripts\build_aar.ps1`（通过，产出 `app/libs/myflowhub.aar`）

3) 构建 debug APK（Windows 本地）
- 为本机配置 `local.properties` 指向 SDK（仅本地，不入库）
- 执行：`./gradlew :app:assembleDebug`（通过）

## 潜在影响与回滚方案

- 影响：
  - 新增 Go client 会话与更多 API，会增加 AAR 的体积与依赖；但为后续子协议 UI 提供稳定基座
  - Protocols 页中的通用控制台对 file 等子协议可能需要后续适配（属于“可用先行”策略）
- 回滚：
  - revert 本分支相关提交即可回退到 M0（仅 Hub Start/Stop/Status）
  - AAR/SDK 配置均为可选：未生成 AAR 时运行会回退到 stub（仅可启动 UI，不具备协议能力）

