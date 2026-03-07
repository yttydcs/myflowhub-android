# 2026-03-07 bump SDK/VarStore v0.1.2（Android hubmobile 依赖升级）

## 背景 / 目标

- 背景：
  - `myflowhub-subproto/varstore v0.1.2`：VarStore 逐跳回程对齐，`*_resp` 统一 `MajorCmd`。
  - `myflowhub-sdk v0.1.2`：`await` 对 VarStore 的 `MajorCmd` 响应增加严格白名单兼容。
- 目标：Android `hubmobile` 升级依赖版本，确保在新 VarStore 协议下不会出现 await 超时，便于端到端联调测试。

## 具体变更

- `hubmobile/go.mod`
  - `github.com/yttydcs/myflowhub-sdk v0.1.0 -> v0.1.2`
  - `github.com/yttydcs/myflowhub-subproto/varstore v0.1.1 -> v0.1.2`（indirect）
- `hubmobile/go.sum`：随依赖求解更新

## 测试与验证

- `cd hubmobile && GOWORK=off go mod tidy`（已执行）
- `cd hubmobile && GOWORK=off go test ./...`（已执行，通过）

## 回滚方案

- 将 `hubmobile/go.mod` 回退到旧版本并重新 `GOWORK=off go mod tidy`。
