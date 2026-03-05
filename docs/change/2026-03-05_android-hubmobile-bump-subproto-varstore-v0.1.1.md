# 2026-03-05 - Android(hubmobile)：升级 subproto/varstore 到 v0.1.1

## 变更背景 / 目标
- 背景：上游 `myflowhub-subproto/varstore` 发布 `v0.1.1`，修复跨层拓扑下 VarStore 的 target 转发与 owner 路由自愈。
- 目标：对齐 Android `hubmobile` 间接依赖版本，确保运行链路与 Server 一致。

## 具体变更内容
- `hubmobile/go.mod`
  - `github.com/yttydcs/myflowhub-subproto/varstore v0.1.0 -> v0.1.1`（indirect）
- `hubmobile/go.sum`
  - 同步校验和。
- `todo.md`
  - 记录本次 workflow 任务与验收。

## plan 任务映射
- ANDVAR-1：依赖升级 -> 完成
- ANDVAR-2：最小验证 -> 完成
- ANDVAR-3：归档 -> 完成

## 关键设计决策与权衡
- 仅进行 patch 版本依赖对齐，不改 Android UI/桥接逻辑，降低回归风险。

## 测试与验证方式 / 结果
- `cd hubmobile && GOWORK=off go list -m github.com/yttydcs/myflowhub-subproto/varstore`
  - 输出：`v0.1.1`
- `cd hubmobile && GOWORK=off go test ./... -count=1 -p 1`
  - 结果：通过。

## 潜在影响与回滚方案
- 潜在影响：Android 节点在跨层 VarStore 场景中将使用修复后的依赖行为。
- 回滚：将 `hubmobile/go.mod/go.sum` 回退到 `varstore v0.1.0` 并重新发布。
