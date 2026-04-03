# Android Hubmobile Local Replace

## Summary
- 当 Android 仓的 `hubmobile` 使用本地 `MyFlowHub-Server` worktree 构建 AAR 时，如果 Server main 已依赖未发布的 Proto 包（例如 `protocol/stream`），仅 replace `Server/SDK` 还不够；本地构建还需要把 `MyFlowHub-Proto` 纳入依赖解析。
- 当 Android 仓不是从控制面 `repo/MyFlowHub-Android` 路径执行，而是从 `worktrees/<branch>` 执行时，`hubmobile/go.mod` 中的相对 `replace ../../MyFlowHub-*` 还会指向 `worktrees/` 下的同名目录；若这些目录不存在，`go test` / `gomobile bind` 会直接失败。
- 当本地 `MyFlowHub-Server` worktree 已经发生 API 漂移，而当前任务只需要验证 Android 仓本轮改动时，可以临时生成一个不引用本地 Server replace 的 `modfile`，用发布版 Server 先隔离验证 `hubmobile` 自身代码。

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
- `default branch`
- `worktrees/MyFlowHub-Server`
- `junction`
- `go.verify.mod`
- `-modfile`

## Symptoms
- 在 `hubmobile/` 下执行 `go test ./...` 时提示 `go: updates to go.mod needed`。
- 执行 `go mod tidy` 时出现：
  - `module github.com/yttydcs/myflowhub-proto@latest found ... but does not contain package github.com/yttydcs/myflowhub-proto/protocol/stream`
- 尝试用显式 `go.work` 跑 `gomobile bind` 时，又出现：
  - `directory gobind is contained in a module that is not one of the workspace modules listed in go.work`
- GitHub Actions 的 `Build AAR (gomobile)` 失败，但前置 `Setup Go` / `Install gomobile` / `Decode keystore` 已通过。
- GitHub Actions 的依赖仓 checkout 成功，但实际拉到的是依赖仓的 default branch，而不是 Android release 期望的 `main`。
- 在 Android worktree 下执行 `cd hubmobile; go test ./...` 或 `.\scripts\build_aar.ps1` 时，直接提示找不到 `../../MyFlowHub-Server` / `../../MyFlowHub-SDK` / `../../MyFlowHub-Proto`。
- 在 Android worktree 下执行 `cd hubmobile; go test ./...` 时，报错来自 `MyFlowHub-Server` 本地 API 漂移，而不是当前 Android 仓代码本身。

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
- GitHub Actions 虽然 checkout 了依赖仓，但没有显式指定 `ref: main`
- Android 仓当前是从 `D:\project\MyFlowHub3\worktrees\<branch>` 执行，而不是从 `D:\project\MyFlowHub3\repo\MyFlowHub-Android` 执行

## Root Cause
- Android 仓的本地开发态 replace 链只覆盖了 Server / SDK，没有覆盖 Server 新引入的 Proto 本地源码需求。
- `gomobile bind` 依赖的 `gobind` 生成模块对显式 `go.work` 比较敏感，因此“临时 workspace”并不能稳定替代本地 replace。
- GitHub Actions 如果只 checkout 其中一部分依赖仓，runner 上的目录结构就会和 `hubmobile/go.mod` 脱节，`gomobile bind` 会在 module 解析阶段失败。
- 如果依赖仓的 default branch 不是 `main`，而 workflow 又没有显式写 `ref: main`，runner 会拉到错误分支，表现为“看似目录齐全，但协议类型仍旧 undefined”。
- `hubmobile/go.mod` 的相对 `replace ../../MyFlowHub-*` 默认假设当前 Android 仓位于控制面 `repo/` 目录；一旦换成 `worktrees/<branch>` 拓扑，解析结果就变成 `D:\project\MyFlowHub3\worktrees\MyFlowHub-*`，必须显式准备这些目录。
- 当任务范围只允许修改 Android 仓时，直接去修本地 `MyFlowHub-Server` worktree 会扩大变更面；这时需要先把“验证 Android 仓自身代码”与“修外部依赖仓状态”分离。

## Investigation Trail
- 先在 `hubmobile/` 下跑 `go test ./...`，确认不是 Kotlin / Android 构建问题，而是 Go module 图未闭合。
- 再执行 `go mod tidy`，观察缺失包是否来自本地 Server 的新增依赖。
- 确认 `MyFlowHub-Proto` 本地源码中确实存在 `protocol/stream`。
- 最后验证 `gomobile bind` 在显式 `go.work` 下会被 `gobind` 额外限制，说明应优先恢复 `GOWORK=off` + 本地 replace 路径。
- 如果当前 Android 仓是 worktree 路径，再额外检查 `Resolve-Path hubmobile/../../MyFlowHub-Server` 实际落点，确认它是否真的存在。

## Resolution
- 在 `hubmobile/go.mod` 中新增：
  - `replace github.com/yttydcs/myflowhub-proto => ../../MyFlowHub-Proto`
- 重新执行：
  - `cd hubmobile; $env:GOWORK='off'; go mod tidy`
  - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
  - `.\scripts\build_aar.ps1 ...`
- 若问题出现在 GitHub Actions：
  - 检查 `ci.yml` / `release.yml` 是否把 `MyFlowHub-Server`、`MyFlowHub-SDK`、`MyFlowHub-Proto` 全部 checkout 到与 `replace` 对应的 `repo/` 目录。
  - 同时检查这些 checkout 是否显式写了 `ref: main`，不要依赖仓库 default branch。
- 若问题出现在 Android worktree：
  - 在 `D:\project\MyFlowHub3\worktrees\` 下补齐与 `replace` 对应的目录镜像，或创建 junction：
    - `MyFlowHub-Server -> D:\project\MyFlowHub3\repo\MyFlowHub-Server`
    - `MyFlowHub-SDK -> D:\project\MyFlowHub3\repo\MyFlowHub-SDK`
    - `MyFlowHub-Proto -> D:\project\MyFlowHub3\repo\MyFlowHub-Proto`
  - 之后重新执行：
    - `cd hubmobile; $env:GOWORK='off'; go test ./... -count=1 -p 1`
    - `.\scripts\build_aar.ps1 ...`
- 若当前阻塞来自本地 `MyFlowHub-Server` worktree API 漂移，但本轮只需要验证 Android 仓代码：
  - 临时复制 `hubmobile/go.mod` 为 `go.verify.mod`
  - 去掉其中 `replace github.com/yttydcs/myflowhub-server => ../../MyFlowHub-Server`
  - 执行：
    - `cd hubmobile; $env:GOWORK='off'; go test "-modfile=go.verify.mod" -mod=mod ./... -count=1 -p 1`
  - 验证完成后删除 `go.verify.mod` / `go.verify.sum`

## Prevention / Guardrails
- 只要 `hubmobile` 本地 replace 到 `MyFlowHub-Server`，就要同步检查 Server 是否已经消费未发布的 Core / Proto / SubProto 代码。
- 优先保证 `GOWORK=off` 的本地构建路径可用，因为 Android 现有 `build_aar.ps1` 就是按这个假设设计的。
- 若未来 Server 再引入新的本地源码依赖，应在 Android 仓的 `hubmobile/go.mod` 一并补齐对应 replace，而不是等到 AAR 构建时再排障。
- 只要 `hubmobile/go.mod` 中仍保留本地 `replace`，GitHub Actions 就必须镜像同样的目录拓扑；不要只更新 `go.mod` 而漏改 workflow checkout。
- 对跨仓依赖，除非明确需要跟随 default branch，否则在 workflow 中固定 `ref: main`（或固定 tag / SHA），避免隐式漂移。
- 只要 Android 任务在 worktree 中需要执行 `hubmobile` 本地验证，就要先确认 `worktrees/` 下是否已有 `MyFlowHub-Server` / `MyFlowHub-SDK` / `MyFlowHub-Proto` 的镜像或同名 worktree。
- 若任务明确限制“只改 Android 仓”，遇到外部依赖 worktree 漂移时，应先使用临时 `modfile` 完成 Android 仓隔离验证，再决定是否要另起 workflow 修外部依赖。

## Related Docs
- [2026-03-31_android-rfcomm-listener-config.md](../change/2026-03-31_android-rfcomm-listener-config.md)
- [2026-03-31_android-rfcomm-basic-usability.md](../change/2026-03-31_android-rfcomm-basic-usability.md)
- [2026-04-01_android-release-checkout-deps.md](../change/2026-04-01_android-release-checkout-deps.md)
- [2026-04-02_android-file-module.md](../change/2026-04-02_android-file-module.md)
- [2026-04-03_android-file-pull-download-v1.md](../change/2026-04-03_android-file-pull-download-v1.md)
