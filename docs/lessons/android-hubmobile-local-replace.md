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

## Symptoms
- 在 `hubmobile/` 下执行 `go test ./...` 时提示 `go: updates to go.mod needed`。
- 执行 `go mod tidy` 时出现：
  - `module github.com/yttydcs/myflowhub-proto@latest found ... but does not contain package github.com/yttydcs/myflowhub-proto/protocol/stream`
- 尝试用显式 `go.work` 跑 `gomobile bind` 时，又出现：
  - `directory gobind is contained in a module that is not one of the workspace modules listed in go.work`

## Impact
- Android 本地 AAR 无法重建。
- 依赖 `hubmobile` 新导出 API 的功能验证会被构建链路阻塞。

## Trigger Conditions
- `hubmobile/go.mod` 使用本地 `replace github.com/yttydcs/myflowhub-server => ../../MyFlowHub-Server`
- 本地 `MyFlowHub-Server` 已依赖未发布的 Proto 包
- 构建脚本或流程使用 `GOWORK=off`

## Root Cause
- Android 仓的本地开发态 replace 链只覆盖了 Server / SDK，没有覆盖 Server 新引入的 Proto 本地源码需求。
- `gomobile bind` 依赖的 `gobind` 生成模块对显式 `go.work` 比较敏感，因此“临时 workspace”并不能稳定替代本地 replace。

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

## Prevention / Guardrails
- 只要 `hubmobile` 本地 replace 到 `MyFlowHub-Server`，就要同步检查 Server 是否已经消费未发布的 Core / Proto / SubProto 代码。
- 优先保证 `GOWORK=off` 的本地构建路径可用，因为 Android 现有 `build_aar.ps1` 就是按这个假设设计的。
- 若未来 Server 再引入新的本地源码依赖，应在 Android 仓的 `hubmobile/go.mod` 一并补齐对应 replace，而不是等到 AAR 构建时再排障。

## Related Docs
- [2026-03-31_android-rfcomm-listener-config.md](../change/2026-03-31_android-rfcomm-listener-config.md)
- [2026-03-31_android-rfcomm-basic-usability.md](../change/2026-03-31_android-rfcomm-basic-usability.md)
