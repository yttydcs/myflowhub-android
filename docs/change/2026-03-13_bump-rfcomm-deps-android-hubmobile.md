# Android（hubmobile）：对齐 RFCOMM 发布依赖版本

## 背景 / 目标
- Core 已发布 `v0.3.1`，Server 已发布 `v0.0.6`，SDK 已发布 `v0.1.3`。
- Android `hubmobile` 需要把依赖版本对齐到上述可拉取版本，保证 RFCOMM 相关能力在 `GOWORK=off` 下也可构建。

## 具体变更
- `hubmobile/go.mod`
  - `github.com/yttydcs/myflowhub-core -> v0.3.1`
  - `github.com/yttydcs/myflowhub-sdk -> v0.1.3`
  - `github.com/yttydcs/myflowhub-server -> v0.0.6`
- `hubmobile/go.sum`：更新校验和
- 保留：`replace github.com/yttydcs/myflowhub-server => ../../MyFlowHub-Server`

## 设计与权衡
- 保持现有 Android CI / meta-workspace 的本地 `replace` 策略不变，只对齐 require 版本。
- 发布 patch 版本 `v0.1.22`，不改 Android UI 和业务逻辑。

## 测试与验证
- `cd repo/MyFlowHub-Android/hubmobile; $env:GOWORK='off'; go test ./... -count=1`

## 回滚
- revert 本次依赖升级提交；如已发版，则使用更高 patch 版本修正。
