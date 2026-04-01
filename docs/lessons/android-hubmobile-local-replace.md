# Android Hubmobile Local Replace

## Summary
- 当 Android 仓的 `hubmobile` 使用本地 `MyFlowHub-Server` worktree 构建 AAR 时，如果 Server main 已依赖未发布的 Proto 包（例如 `protocol/stream`），仅 replace `Server/SDK` 还不够；本地构建还需要把 `MyFlowHub-Proto` 纳入依赖解析。

## Lookup Hints
- `gomobile bind`
- `gobind`
- `protocol/stream`
- `go mod tidy`
- `go: updates to go.mod needed`
- `module ... does not contain package`
- `hubmobile/go.mod`
- `MyFlowHub-Proto`
- `Build AAR (gomobile)`
- `actions/checkout`

## Symptoms
- 在 `hubmobile/` 下执行 `go test ./...` 时提示 `go: updates to go.mod needed`。
- 执行 `go mod tidy` 时出现：
  - `module github.com/yttydcs/myflowhub-proto@latest found ... but does not contain package github.com/yttydcs/myflowhub-proto/protocol/stream`
- 尝试用显式 `go.work` 跑 `gomobile bind` 时，又出现：
  - `directory gobind is contained in a module that is not one of the workspace modules listed in go.work`
- GitHub Actions 的 `Build AAR (gomobile)` 失败，但前置 `Setup Go` / `Install gomobile` / `Decode keystore` 已通过。

## Impact
- Android 本地 AAR 无法重建。
- 依赖 `hubmobile` 新导出 API 的功能验证会被构建链路阻塞。
- CI / Release 会在进入 Gradle 打包前提前失败，导致 APK 与 GitHub Release 无法产出。

## Trigger Conditions
- `hubmobile/go.mod` 使用本地 `replace github.com/yttydcs/myflowhub-server => ../../MyFlowHub-Server`
- `hubmobile/go.mod` 继续通过本地 `replace` 依赖 `MyFlowHub-SDK` / `MyFlowHub-Proto`
- 本地 `MyFlowHub-Server` 已依赖未发布的 Proto 包
- 构建脚本或流程使用 `GOWORK=off`
- GitHub Actions 没有把所有 `replace` 目标 checkout 到 runner workspace

## Root Cause
- Android 仓的本地开发态 replace 链只覆盖了 Server / SDK，没有覆盖 Server 新引入的 Proto 本地源码需求。
- `gomobile bind` 依赖的 `gobind` 生成模块对显式 `go.work` 比较敏感，因此“临时 workspace”并不能稳定替代本地 replace。
- GitHub Actions 如果只 checkout 其中一部分依赖仓，runner 上的目录结构就会和 `hubmobile/go.mod` 脱节，`gomobile bind` 会在 module 解析阶段失败。

## Investigation Trail
- 先在 `hubmobile/` 下跑 `go test ./...`，确认不是 Kotlin / Android 构建问题，而是 Go module 图未闭合。
- 再执行 `go mod tidy`，观察缺失包是否来自本地 Server 的新增依赖。
- 确认 `MyFlowHub-Proto` 本地源码中确实存在 `protocol/stream`。
- 最后验证 `gomobile bind` 在显式 `go.work` 下会被 `gobind` 额外限制，说明应优先恢复 `GOWORK=off` + 本地 replace 路径。

## Resolution
- 在 `hubmobile/go.mod` 中新增：
  - `replace github.com/yttydcs/myflowhub-proto => ../../MyFlowHub-Proto`
- 重新执行：
  - `cd hubmobile; $env:GOWORK='off'; go mod tidy`
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
  - `.\scripts\build_aar.ps1 ...`
- 若问题出现在 GitHub Actions：
  - 检查 `ci.yml` / `release.yml` 是否把 `MyFlowHub-Server`、`MyFlowHub-SDK`、`MyFlowHub-Proto` 全部 checkout 到与 `replace` 对应的 `repo/` 目录。

## Prevention / Guardrails
- 只要 `hubmobile` 本地 replace 到 `MyFlowHub-Server`，就要同步检查 Server 是否已经消费未发布的 Core / Proto / SubProto 代码。
- 优先保证 `GOWORK=off` 的本地构建路径可用，因为 Android 现有 `build_aar.ps1` 就是按这个假设设计的。
- 若未来 Server 再引入新的本地源码依赖，应在 Android 仓的 `hubmobile/go.mod` 一并补齐对应 replace，而不是等到 AAR 构建时再排障。
- 只要 `hubmobile/go.mod` 中仍保留本地 `replace`，GitHub Actions 就必须镜像同样的目录拓扑；不要只更新 `go.mod` 而漏改 workflow checkout。

## Related Docs
- [2026-03-31_android-rfcomm-listener-config.md](../change/2026-03-31_android-rfcomm-listener-config.md)
- [2026-03-31_android-rfcomm-basic-usability.md](../change/2026-03-31_android-rfcomm-basic-usability.md)
- [2026-04-01_android-release-checkout-deps.md](../change/2026-04-01_android-release-checkout-deps.md)
