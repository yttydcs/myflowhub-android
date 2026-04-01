# Android Hub Service Restart

## Summary
- Android 前台服务即使返回了 `START_STICKY`，也不会自动恢复依赖 `ACTION_START` extras 的运行配置。对于 MyFlowHub 这类“表单可持续编辑、服务在后台运行”的场景，必须单独保存 `desiredRunning` 和最近一次启动快照。

## Lookup Hints
- `START_STICKY`
- `intent == null`
- `HubService`
- `desiredRunning`
- `后台不工作`
- `自动恢复`

## Symptoms
- Hub 已经启动，切到后台后过一段时间又像没启动一样。
- 服务被系统重建后，Hub 页面显示 `Stopped`，但用户明明之前点过 `Start`。
- 父链重连本身存在，但 Android 通知和页面看起来没有任何恢复迹象。

## Impact
- 用户会误判为 Android Hub 没有自动重连或没有后台能力。
- 运行中的 Hub 会在宿主服务重建后丢失，削弱实际可用性。

## Trigger Conditions
- 服务返回 `START_STICKY`。
- 运行配置来源于 `Intent` extras。
- `onStartCommand()` 的空 intent 分支没有恢复逻辑。
- UI 表单会在运行期间继续保存新草稿。

## Root Cause
- 服务运行态没有独立于 UI 表单配置保存。
- Android 在 sticky restart 时可能以 `intent == null` 重建服务；若代码只处理 `ACTION_START` / `ACTION_STOP`，最近一次运行配置就会丢失。

## Investigation Trail
- 先检查 `HubService.onStartCommand()` 是否在 `intent == null` 时直接 `no-op`。
- 再检查 `Prefs` 是否只保存了表单配置，而没有保存最近一次运行快照。
- 最后确认 Go runtime 是否已经具备父链自动重连，避免把宿主恢复问题误判为协议层能力缺失。

## Resolution
- 将 `desiredRunning` 和最近一次启动快照持久化。
- 在 `intent == null` 场景下按快照恢复服务。
- 用低频 `bridge.status()` 轮询刷新通知和页面状态。

## Prevention / Guardrails
- 任何依赖 `Intent` extras 启动的 Android 常驻服务，都要明确 sticky restart 的配置来源。
- 不要把“可编辑表单状态”直接当作“运行中服务配置”。
- 如果底层 runtime 已有重连能力，宿主层优先修生命周期恢复和状态可见性，不要再叠一层重连状态机。

## Related Docs
- [2026-04-01_android-hub-resilience.md](../change/2026-04-01_android-hub-resilience.md)
- `D:\project\MyFlowHub3\repo\MyFlowHub-Server\docs\specs\core.md`
