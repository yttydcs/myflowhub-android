# Plan - Android：hubmobile 升级依赖到 Core v0.3.0（对齐 Pipe 抽象重大变更）

## Workflow 信息
- Repo：`MyFlowHub-Android`
- 分支：`chore/bump-core-v0.3.0-android-hubmobile`
- Worktree：`d:\project\MyFlowHub3\worktrees\chore-bump-core-v0.3.0-android-hubmobile\MyFlowHub-Android`
- Base：`main`
- 关联仓库：
  - `MyFlowHub-Core`：已发布 `v0.3.0`（重大变更：`IConnection.RawConn()` → `IConnection.Pipe()`）

## 背景 / 问题陈述（事实，可审计）
- Android 仓库内仅 `hubmobile` 模块依赖 `myflowhub-core`，当前仍固定 `v0.2.1`。
- Core 已发布 `v0.3.0`（Pipe 抽象重大变更），且其它仓库已逐步完成依赖升级。
- 若 `hubmobile` 长期锁定旧 Core，将导致跨仓协作时版本不一致，并在 `GOWORK=off` 下潜在出现编译口径差异。

## 目标
1) 将 `hubmobile/go.mod` 中 `github.com/yttydcs/myflowhub-core` 升级到 `v0.3.0`。
2) 执行 `go mod tidy` 并确保 `GOWORK=off go test ./...` 通过（至少主机平台可编译）。

## 非目标
- 不改 Android UI/业务逻辑/协议语义。
- 不调整 CI/release workflow（仅做依赖升级；如需额外验证链路另开 workflow）。

## 验收标准
- `cd hubmobile; GOWORK=off go test ./... -count=1 -p 1` 通过。
- `hubmobile/go.mod` 不再引用 `myflowhub-core v0.2.1`。
- 合并到 `main` 并 push。

## 3.1) 计划拆分（Checklist）

### ANDDEP0 - 归档旧 plan（已执行）
- 已执行：`git mv plan.md docs/plan_archive/plan_archive_2026-03-12_bump-core-v0.3.0-android-hubmobile-prev.md`

### ANDDEP1 - 升级 hubmobile 的 Core 依赖到 v0.3.0
- 目标：`hubmobile/go.mod` 中 `github.com/yttydcs/myflowhub-core` 从 `v0.2.1` 升级到 `v0.3.0`，并 tidy。
- 说明：若升级后出现编译失败（例如 `IConnection` 接口迁移到 `Pipe()`），允许做最小必要适配，但不改变对外行为与协议语义。
- 涉及文件：`hubmobile/go.mod`、`hubmobile/go.sum`
- 回滚点：revert 本任务提交。

### ANDDEP2 - 回归测试（GOWORK=off）
- 测试点：
  - `cd hubmobile; GOWORK=off go test ./... -count=1 -p 1`

### ANDDEP3 - Code Review（强制）
- 逐项审查：需求覆盖/架构/性能/可读性/扩展性/稳定性与安全/测试覆盖。

### ANDDEP4 - 归档变更（强制）
- 输出：`docs/change/2026-03-12_bump-core-v0.3.0-android-hubmobile.md`

### ANDDEP5 - 合并 / push（需 workflow 结束后执行）
- 在 `repo/MyFlowHub-Android` 合并到 `main` 并 push。
