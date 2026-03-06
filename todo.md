# TODO - Android VarStore：Node Vars `list` 走 Hub 缓存

## Workflow 信息
- Repo：`MyFlowHub-Android`
- 分支：`fix/android-nodevars-list-hub-target`
- Worktree：`d:\project\MyFlowHub3\worktrees\fix-android-nodevars-list-hub-target`
- Base：`main`

## 项目目标与当前状态

### 目标
1) Android Node Vars 查询与规范一致：`list` 请求发送到“直接父/Hub”，payload 通过 `owner` 指定目标节点。
2) 避免 `target=owner` 触发 leaf 节点不回 `list_resp` 的超时。

### 当前状态（事实）
- 当前 `listOwnerNames(ownerId)` 调用：
  - `g.varStoreList(sourceId, targetId=ownerId, owner=ownerId)`。
- 该行为会在 `varstore v0.1.1` 的 header target 转发逻辑下把 `list` 下发到 owner 节点；若 owner 为 metrics leaf（不处理 list/get），会超时。

## 可执行任务清单（Checklist）

- [x] `ANDROID-NODEVARS-1`：修正 Node Vars list target
  - 目标：将 `listOwnerNames(ownerId)` 的 `target` 改为 `parseDefaultTargetId()`（Hub），保持 `owner=ownerId` 不变。
  - 涉及文件：
    - `app/src/main/java/com/myflowhub/android/ui/VarStoreScreen.kt`
  - 验收条件：
    - Node Vars 加载不再因直发 owner 导致 `request timed out`；
    - owner 无缓存/无变量时返回空列表，不抛异常。
  - 测试点：
    - 业务链路验证：Node Vars 查询 owner=metrics 节点时应返回成功或空列表；
    - 静态验证：Kotlin 编译通过。
  - 回滚点：
    - revert 本任务提交。

- [x] `ANDROID-NODEVARS-2`：最小回归验证
  - 目标：确保改动不破坏 Android 构建。
  - 验收条件：
    - 执行 `./gradlew :app:compileDebugKotlin`（Windows 使用 `gradlew.bat`）通过；若受环境限制失败，记录失败原因。
  - 回滚点：
    - revert 本任务提交。

- [x] `ANDROID-NODEVARS-3`：Code Review + 归档
  - 目标：完成审查闭环与归档。
  - 涉及文件：
    - `docs/change/2026-03-06_android-nodevars-list-hub-target.md`
  - 验收条件：
    - Review 结论完整（覆盖需求/架构/性能/可扩展性/稳定性/测试）；
    - 归档文档包含任务映射、验证结果和回滚方案。

## 依赖关系
- `ANDROID-NODEVARS-1 -> ANDROID-NODEVARS-2 -> ANDROID-NODEVARS-3`

## 风险与注意事项
- Node Vars 的查询结果依赖链路缓存；Hub 重启后若 owner 尚未重新 publish，可能出现“空列表但不超时”，属于缓存刷新窗口，不属于本次修复范围。
