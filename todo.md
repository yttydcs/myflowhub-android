# TODO - Android(hubmobile)：升级 subproto/varstore 到 v0.1.1（下游依赖对齐）

## Workflow 信息
- Repo：`MyFlowHub-Android`
- 分支：`chore/android-bump-varstore-v0.1.1`
- Worktree：`d:\project\MyFlowHub3\worktrees\chore-android-bump-varstore-v0.1.1`
- 升级目标：`hubmobile/go.mod` 中 `github.com/yttydcs/myflowhub-subproto/varstore`（indirect）

## 项目目标与当前状态
- 目标：
  - 对齐 Android hubmobile 的 indirect 依赖到 `varstore v0.1.1`，纳入跨层转发修复。
- 当前状态：
  - `hubmobile/go.mod` 中 `varstore` 仍为 `v0.1.0 // indirect`。

## 可执行任务清单（Checklist）

- [x] ANDVAR-1：升级 hubmobile 依赖版本
  - 目标：`hubmobile/go.mod/go.sum` 对齐 `varstore v0.1.1`。
  - 涉及文件：
    - `hubmobile/go.mod`
    - `hubmobile/go.sum`
  - 验收条件：
    - `cd hubmobile && GOWORK=off go list -m github.com/yttydcs/myflowhub-subproto/varstore` 输出 `v0.1.1`。

- [x] ANDVAR-2：最小验证
  - 目标：确认 hubmobile 模块在新依赖下可通过测试。
  - 验收条件：
    - `cd hubmobile && GOWORK=off go test ./... -count=1 -p 1` 通过。

- [x] ANDVAR-3：Code Review + 归档
  - 目标：完成审查闭环与变更归档。
  - 涉及文件：
    - `docs/change/2026-03-05_android-hubmobile-bump-subproto-varstore-v0.1.1.md`
  - 验收条件：
    - 文档包含任务映射、验证结果、影响与回滚。

## 依赖关系
- `ANDVAR-1 -> ANDVAR-2 -> ANDVAR-3`

## 风险与注意事项
- `hubmobile` 使用 `replace` 指向本地 Server，依赖解析时需显式 `GOWORK=off`。
- 仅升级依赖，不改 Android UI/业务逻辑。
