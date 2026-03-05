# TODO - Android(hubmobile)：升级 subproto/auth 到 v0.1.1（下游依赖对齐）

## Workflow 信息
- Repo：`MyFlowHub-Android`
- 分支：`chore/android-bump-auth-v0.1.1`
- Worktree：`d:\project\MyFlowHub3\repo\MyFlowHub-Android\worktrees\chore-android-bump-auth-v0.1.1\MyFlowHub-Android`
- 升级目标：`hubmobile/go.mod` 中 `github.com/yttydcs/myflowhub-subproto/auth`（indirect）

## 项目目标与当前状态
- 目标：
  - 对齐 Android hubmobile 的 indirect 依赖到 `auth v0.1.1`，避免下游模块版本滞后。
- 当前状态：
  - `hubmobile/go.mod` 中 `auth` 仍为 `v0.1.0 // indirect`。

## 可执行任务清单（Checklist）

- [x] ANDAUTH-1：升级 hubmobile 依赖版本
  - 目标：`hubmobile/go.mod/go.sum` 对齐 `auth v0.1.1`（并按依赖求解结果同步 `file v0.1.2`）。
  - 涉及文件：
    - `hubmobile/go.mod`
    - `hubmobile/go.sum`
  - 验收条件：
    - `cd hubmobile && GOWORK=off go list -m github.com/yttydcs/myflowhub-subproto/auth` 输出 `v0.1.1`。
  - 回滚点：
    - 回退 `hubmobile/go.mod/go.sum`。

- [x] ANDAUTH-2：最小验证
  - 目标：确认 hubmobile 模块在新依赖下可通过测试。
  - 验收条件：
    - `cd hubmobile && GOWORK=off go test ./... -count=1 -p 1` 通过。
  - 回滚点：
    - 回退依赖升级提交。

- [x] ANDAUTH-3：Code Review + 归档
  - 目标：完成审查闭环与变更归档。
  - 涉及文件：
    - `docs/change/2026-03-05_android-hubmobile-bump-subproto-auth-v0.1.1.md`
  - 验收条件：
    - 文档包含任务映射、验证结果、影响与回滚。
  - 回滚点：
    - 回滚文档提交。

## 依赖关系
- `ANDAUTH-1 -> ANDAUTH-2 -> ANDAUTH-3`

## 风险与注意事项
- `hubmobile` 使用 `replace` 指向本地 Server，依赖解析时需显式 `GOWORK=off` 保持可审计结果。
- 仅升级依赖，不改 Android UI/业务逻辑。

## 当前执行状态
- 已完成：ANDAUTH-1、ANDAUTH-2、ANDAUTH-3
- 进行中：无
- 待完成：无
