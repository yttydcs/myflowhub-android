# 2026-03-05 - Android(hubmobile)：升级 subproto/auth 到 v0.1.1

## 变更背景 / 目标
- 背景：上游 `myflowhub-subproto/auth v0.1.1` 修复跨级路由传播问题；Android 的 `hubmobile` 仍锁定 `auth v0.1.0`（indirect）。
- 目标：对齐 `hubmobile` 依赖版本，避免下游模块滞后。

## 具体变更内容
- 修改：
  - `hubmobile/go.mod`
    - `github.com/yttydcs/myflowhub-subproto/auth`：`v0.1.0` -> `v0.1.1`（indirect）
    - 依赖求解联动：`github.com/yttydcs/myflowhub-subproto/file`：`v0.1.1` -> `v0.1.2`（indirect）
  - `hubmobile/go.sum`
    - 同步新的模块校验和。
- 无 Android UI / 业务代码改动。

## todo 任务映射
- ANDAUTH-1：升级 hubmobile 依赖版本 -> 完成
- ANDAUTH-2：最小验证 -> 完成
- ANDAUTH-3：Code Review + 归档 -> 完成

## 关键设计决策与权衡
- 使用 `GOWORK=off` 执行依赖更新与测试，避免本地 workspace 干扰审计结果。
- `file v0.1.2` 的联动升级来自 `replace github.com/yttydcs/myflowhub-server => ../../MyFlowHub-Server` 的依赖求解结果，属于必要的版本对齐。

## 测试与验证方式 / 结果
- `cd hubmobile && GOWORK=off go list -m github.com/yttydcs/myflowhub-subproto/auth`
  - 结果：`v0.1.1`
- `cd hubmobile && GOWORK=off go test ./... -count=1 -p 1`
  - 结果：通过。

## Code Review（3.3）结论
- 需求覆盖：通过
- 架构合理性：通过
- 性能风险：通过
- 可读性与一致性：通过
- 可扩展性与配置化：通过
- 稳定性与安全：通过
- 测试覆盖情况：通过（hubmobile 模块测试通过）

## 潜在影响与回滚方案
- 潜在影响：hubmobile 的 indirect 子模块版本向上对齐；运行逻辑不变。
- 回滚：回退 `hubmobile/go.mod/go.sum` 到变更前版本并重新测试。
