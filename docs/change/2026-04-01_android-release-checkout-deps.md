# 2026-04-01 - Android：修复 GitHub Actions checkout 依赖链

## 变更背景 / 目标
- 背景：tag `v0.1.27` 触发的 release workflow `run #28`（run id `23779735932`）在 `Build AAR (gomobile)` 失败，导致 GitHub Release 未创建。
- 已知现象：
  - `Decode keystore` 已通过，因此失败点不在签名 secrets。
  - 历史上从 `v0.1.23` 开始，release workflow 持续在同一 `Build AAR (gomobile)` 步骤失败。
  - 当前 `hubmobile/go.mod` 已声明本地 `replace` 到 `MyFlowHub-Server`、`MyFlowHub-SDK`、`MyFlowHub-Proto`，但 workflow 只 checkout 了 Android + Server。
- 目标：让 GitHub runner 的目录结构与 `hubmobile/go.mod` 的相对 `replace` 链保持一致，恢复 CI / Release 的基本可用性。

## 具体变更内容
### 修改
1) `.github/workflows/release.yml`
- 在 `Checkout Server` 后新增：
  - `Checkout SDK (for hubmobile replace)` -> `repo/MyFlowHub-SDK`
  - `Checkout Proto (for hubmobile replace)` -> `repo/MyFlowHub-Proto`
- 保持其余 `gomobile` / Gradle / release 发布逻辑不变。

2) `.github/workflows/ci.yml`
- 同步新增 SDK / Proto checkout。
- 保证 debug CI 与 release 使用同一套依赖目录拓扑。

3) `docs/release.md`
- 将“Server 依赖”说明更新为“Hubmobile 本地依赖”。
- 明确记录当前 Actions 会额外 checkout `Server / SDK / Proto`，以及这样做是为了满足 `hubmobile/go.mod` 的本地 `replace`。

4) `docs/lessons/android-hubmobile-local-replace.md`
- 补充 GitHub Actions 场景的症状、根因、resolution 与 guardrail。
- 明确要求：只要 `hubmobile/go.mod` 保留本地 `replace`，workflow checkout 就必须同步镜像这些目录。

### 新增
- `docs/change/2026-04-01_android-release-checkout-deps.md`
- `docs/plan_archive/plan_archive_2026-04-01_android-rfcomm-listener-config-prev.md`
- 新 `plan.md`（本 workflow 控制文档）

### 删除
- 无。

## Requirements impact
- `none`

## Specs impact
- `none`

## Lessons impact
- `updated`

## Related requirements
- `none`

## Related specs
- `none`

## Related lessons
- `docs/lessons/android-hubmobile-local-replace.md`

## 对应 plan.md 任务映射
- `ANDRELCHK-1`：归档旧 `plan.md` 并建立本轮控制文档
- `ANDRELCHK-2`：修复 `release.yml` 的 checkout 依赖链
- `ANDRELCHK-3`：修复 `ci.yml` 的 checkout 依赖链
- `ANDRELCHK-4`：更新 `docs/release.md` 说明
- `ANDRELCHK-5`：完成验证、自审与变更归档

## 经验 / 教训摘要
- `gomobile bind` 的稳定性不只取决于工具链版本，也取决于 runner 是否具备与 `hubmobile/go.mod` 一致的本地目录拓扑。
- Android 仓继续使用 `GOWORK=off` + 本地 `replace` 时，CI / Release 必须显式 checkout 对应依赖仓，不能只假设 semver 依赖可覆盖所有路径。

## 可复用排查线索
- 症状：
  - release / ci 在 `Build AAR (gomobile)` 失败
  - `Decode keystore` 已通过，但 APK / Release 没有产出
- 触发条件：
  - `hubmobile/go.mod` 新增或保留本地 `replace`
  - workflow checkout 没有同步镜像这些目录
- 关键词：
  - `Build AAR (gomobile)`
  - `hubmobile/go.mod`
  - `replace`
  - `actions/checkout`
  - `MyFlowHub-SDK`
  - `MyFlowHub-Proto`
- 快速检查：
  - 对照 `hubmobile/go.mod` 的 `replace` 列表，检查 `ci.yml` / `release.yml` 是否为每个本地目录都提供了对应 checkout

## 关键设计决策与权衡
1) 选择补 checkout，而不是在 workflow 中动态改 `go.mod`
- 优点：CI 行为与仓内源码声明一致，排障路径最短。
- 代价：runner 会多 checkout 两个仓，构建时间略增。

2) 选择同时修复 CI 与 Release
- 优点：避免 debug / release 构建策略漂移，同类问题不会只在 tag 时暴露。
- 代价：需要同时修改两个 workflow，但改动对称、风险低。

3) 选择更新已有 lesson，而不是新建相近故障文档
- 优点：把“本地 replace / CI checkout 漂移”收敛到一个稳定入口，方便后续查询。
- 代价：lesson 覆盖范围更广，需要在标题保持抽象。

## 测试与验证方式 / 结果
- 已执行：
  - `git diff --check`
    - 结果：通过
  - 静态比对 `hubmobile/go.mod` 的本地 `replace`
    - 结果：当前三条 `replace`（Server / SDK / Proto）都已在 `ci.yml` 与 `release.yml` 中找到对应 checkout
  - 文档审阅
    - 结果：`docs/release.md` 已不再保留“只 checkout Server”的过时描述
- 未执行：
  - GitHub Actions 实际 rerun / 新 tag 验证
  - 本地 `gomobile bind` / Gradle 全链路构建
- 说明：
  - 本轮修改只涉及 workflow / 文档，未改业务代码；最终有效性仍需实际 push / tag 触发远端 workflow 确认。

## 潜在影响
- workflow 会多 checkout 两个仓，网络耗时略增。
- 如果未来 `hubmobile/go.mod` 再新增新的本地 `replace`，仍需要同步补 checkout；否则同类故障会复发。

## 回滚方案
1. 回退 `.github/workflows/ci.yml` 与 `.github/workflows/release.yml` 的新增 checkout 步骤。
2. 回退 `docs/release.md` 与 lesson 更新。
3. 若只需撤销文档，不影响 workflow，可单独回退 `docs/` 变更。

## 子Agent执行轨迹
- 未使用子Agent。
