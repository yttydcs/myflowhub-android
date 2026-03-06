# Android Node Vars `list` 对齐 Hub 缓存规范

## 变更背景 / 目标
- 背景：Node Vars 查询使用 `target=owner`，在 varstore `v0.1.1` 的 target 转发逻辑下会把 `list` 下发到 leaf owner；当 owner 不处理 `list/get` 时触发等待超时。
- 目标：`list` 请求严格按规范走直接父/Hub，由 Hub 缓存命中或 assist 路径返回。

## 具体变更内容
- 修改：
  - `app/src/main/java/com/myflowhub/android/ui/VarStoreScreen.kt`
    - `listOwnerNames(ownerId)` 新增 `hubTargetId = parseDefaultTargetId()`。
    - `g.varStoreList(source, target, owner)` 中 `target` 从 `ownerId` 改为 `hubTargetId`。
    - `owner` 参数保持 `ownerId`，不改变查询语义。
- 新增：
  - 本变更归档文档。

## Plan 任务映射
- `ANDROID-NODEVARS-1`：完成。Node Vars `list` 改为 Hub target。
- `ANDROID-NODEVARS-2`：完成。执行 Kotlin 编译验证并记录环境限制。
- `ANDROID-NODEVARS-3`：完成。完成 Code Review 与归档。

## 关键设计决策与权衡
- 决策：Node Vars 的 `list` 固定走 `hubId`，不走 owner。
  - 架构一致性：与 varstore 查询语义（请求方 -> 父/Hub）一致。
  - 性能：优先利用 Hub 缓存，避免下发到 leaf 的无效 hop 与超时等待。
  - 可扩展性：owner 仍由 payload 指定，后续 owner->Hub 路由演进不影响 Android UI 入口。

## 测试与验证方式 / 结果
- 执行：`.\gradlew.bat :app:compileDebugKotlin`
  - 结果：失败（环境缺少 Android SDK 配置，不是代码语义错误）。
  - 失败信息：`SDK location not found. Define ANDROID_HOME or local.properties sdk.dir`。

## Code Review 结论（3.3）
- 需求覆盖：通过。Node Vars `list` 已统一走 Hub。
- 架构合理性：通过。与协议规范和 Win 端修复方向一致。
- 性能风险：通过。减少错误 target 导致的超时链路。
- 可读性与一致性：通过。变量命名明确，变更点单一。
- 可扩展性与配置化：通过。复用现有 `parseDefaultTargetId()`，不引入硬编码。
- 稳定性与安全：通过。沿用既有输入校验和连接校验。
- 测试覆盖情况：受限。受本机 Android SDK 环境缺失影响，未完成完整编译验证。

## 潜在影响与回滚方案
- 潜在影响：
  - 当配置中缺失 hubId 时，Node Vars 会在本地抛出缺失提示（早失败）。
- 回滚方案：
  - 回滚 `VarStoreScreen.kt` 中本次改动（恢复 `target=owner`）。
