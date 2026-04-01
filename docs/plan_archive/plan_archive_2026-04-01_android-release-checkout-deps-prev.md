# Plan - Android：修复 GitHub Actions checkout 依赖链

## Workflow Information
- Repo: `MyFlowHub-Android`
- Branch: `fix/android-release-workflow-deps`
- Base: `origin/main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps`
- Current Stage: `3.1 (follow-up)`

## Stage Records

### Initialization
- guide.md: `none`（仓内不存在 `guide.md`）
- base/worktree confirmation:
  - 主执行仓：`D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps`
  - 控制面仓：`D:\project\MyFlowHub3\repo\MyFlowHub-Android`
  - 实现只允许在当前 Android worktree 内进行
- repo state:
  - `repo/MyFlowHub-Android/main` 干净，仅用于 worktree / tag / 集成观察
  - 当前 worktree 从 `origin/main@199f7c8` 建立

### Stage 1 - Requirements Analysis
#### Goal
- 修复 Android 仓 GitHub Actions 在 `Build AAR (gomobile)` 阶段因本地 `replace` 目录缺失导致的失败，恢复 CI / Release 的基本可用性。

#### Scope
- 必须
  - 让 `ci.yml` 与 `release.yml` 在 runner 上补齐 `hubmobile/go.mod` 当前依赖的本地 checkout 目录。
  - 更新仓内 release 文档，使“GitHub Actions checkout 哪些依赖仓”与当前 `replace` 链一致。
  - 补齐本次 workflow 的计划与变更归档。
- 可选
  - 若验证中暴露可复用、稳定的排障规则，可补充 lesson。
- 不做
  - 不修改 `hubmobile/go.mod`、Go 业务逻辑、Android UI 或 RFCOMM 功能。
  - 不改 GitHub Secrets、签名逻辑、发布版本号规则。
  - 不在本轮直接重打 release tag；是否重新发版由用户后续决定。

#### Use Cases
- 开发者 push 普通分支时，CI 能在 runner 上构建 `myflowhub.aar` 与 debug APK。
- 开发者 push `vMAJOR.MINOR.PATCH` tag 时，Release workflow 能在进入签名与发布前先通过 `Build AAR (gomobile)`。
- 后续 `hubmobile/go.mod` 再引入本地 `replace` 依赖时，仓内文档可以作为排查入口。

#### Functional Requirements
- workflow 必须 checkout `MyFlowHub-Server`、`MyFlowHub-SDK`、`MyFlowHub-Proto` 到与 `hubmobile/go.mod` 相对路径兼容的位置。
- `ci.yml` 与 `release.yml` 的 checkout 拓扑必须保持一致，避免 debug / release 行为漂移。
- `docs/release.md` 必须明确当前 Actions 不只 checkout Server，还会补齐 SDK / Proto 依赖。
- 失败原因和修复点必须可从 `docs/change` 追溯。

#### Non-functional Requirements
- 改动面最小，只修补 checkout 依赖链与相应文档。
- 不新增额外 secrets、缓存层或复杂脚本。
- 保持现有 `gomobile` / Gradle / release 发布逻辑不变。

#### Inputs / Outputs
- 输入
  - `hubmobile/go.mod` 中的相对 `replace`
  - `.github/workflows/ci.yml`
  - `.github/workflows/release.yml`
  - `docs/release.md`
  - 失败 run: `release.yml#28` / run id `23779735932`
- 输出
  - 更新后的 workflow 文件
  - 更新后的 release 文档
  - 本轮 `plan.md`
  - `docs/change/2026-04-01_android-release-checkout-deps.md`

#### Edge Cases
- 未来 `hubmobile/go.mod` 再新增新的本地 `replace`，但 workflow 没同步更新。
- Release 成功通过 AAR 构建后，仍可能在签名或发布步骤因 secrets / release 配置失败；本轮不把这类问题误判为 checkout 依赖问题。
- 若远端仓库名或默认分支变更，workflow 仍需显式报错，不允许静默回退到错误目录。

#### Acceptance Criteria
- `ci.yml` 与 `release.yml` 都包含 SDK / Proto 的 checkout 步骤，并与 `hubmobile/go.mod` 当前相对路径匹配。
- `docs/release.md` 对 Actions checkout 依赖的说明与代码一致。
- 本地审阅可确认 `hubmobile/go.mod` 的三个 `replace` 均能在 runner 目录结构中命中。
- `docs/change` 记录 run 失败背景、修复方案、验证方式和回滚路径。

#### Risks
- 只修 workflow 而不修文档，会让后续排障重新回到“Server-only checkout”的旧认知。
- 若 `hubmobile/go.mod` 未来继续扩展 `replace`，workflow 仍有再次漂移风险；需要在归档中明确 guardrail。

#### Issue List
- 无

### Stage 2 - Architecture Design
#### Overall Solution
- 采用“让 GitHub runner 目录结构显式对齐 `hubmobile/go.mod` 相对 `replace` 链”的方案：在 `ci.yml` / `release.yml` 中新增 SDK / Proto checkout，并同步更新 release 文档。
- 选型理由：
  - 这是与现有 `GOWORK=off` + 本地 `replace` 构建方式最一致、最小的修复。
  - 不引入 `go.work`、`go mod edit` 或临时复制目录，减少 runner 与本地环境的行为差异。

#### Alternatives Considered
- 方案 A：在 workflow 中动态 `go mod edit -replace`
  - 优点：可少 checkout 几个仓
  - 缺点：会让 CI 行为与仓内源码声明脱节，审计成本高
- 方案 B：移除 `hubmobile/go.mod` 的本地 `replace`，完全改用 semver 依赖
  - 优点：长远上更干净
  - 缺点：超出本轮最小修复范围，且当前 Android 本地联调链仍依赖这些目录
- 方案 C：在 runner 上引入 `go.work`
  - 优点：理论上可统一多仓工作区
  - 缺点：已有 lesson 明确指出 `gomobile/gobind` 对显式 workspace 敏感，不稳定
- 采用方案：补 checkout，使 workflow 工作区与 `replace` 声明一致

#### Module Responsibilities
- `.github/workflows/ci.yml`
  - 补齐 debug 构建路径所需的 SDK / Proto checkout。
- `.github/workflows/release.yml`
  - 补齐 release 构建路径所需的 SDK / Proto checkout。
- `docs/release.md`
  - 说明 GitHub Actions 为满足 `hubmobile/go.mod` 的本地 `replace` 会 checkout 哪些依赖仓。
- `docs/change/2026-04-01_android-release-checkout-deps.md`
  - 记录失败背景、修复、验证与 guardrail。

#### Data / Call Flow
1. GitHub Actions checkout Android 仓到 `repo/MyFlowHub-Android`。
2. workflow 额外 checkout `MyFlowHub-Server`、`MyFlowHub-SDK`、`MyFlowHub-Proto`。
3. `scripts/build_aar.sh` 在 `GOWORK=off` 下进入 `hubmobile/`。
4. Go module 解析命中 `../../MyFlowHub-Server`、`../../MyFlowHub-SDK`、`../../MyFlowHub-Proto`。
5. `gomobile bind` 生成 `app/libs/myflowhub.aar`，后续 Gradle / Release 步骤继续执行。

#### Interface Drafts
- `actions/checkout@v4`
  - `repository: yttydcs/myflowhub-sdk`
  - `path: repo/MyFlowHub-SDK`
- `actions/checkout@v4`
  - `repository: yttydcs/myflowhub-proto`
  - `path: repo/MyFlowHub-Proto`

#### Error Handling and Safety
- 依赖仓 checkout 失败应直接让 workflow 失败，避免 `gomobile` 进入更晚、更难诊断的 module 错误。
- 不改动签名与发布逻辑，避免把修复面扩大到 secrets / release 资产流程。

#### Performance and Testing Strategy
- 额外 checkout 两个仓会增加少量网络时间，但比在 `Build AAR` 阶段失败后反复排障更可控。
- 验证策略：
  - 静态校验 `hubmobile/go.mod` 的三个 `replace` 与 workflow checkout 路径一致。
  - `git diff --check`
  - 审阅 workflow 结构，确认 CI / Release 对齐
  - 记录 GitHub 历史 run 作为回归前背景

#### Extensibility Design Points
- 后续若 `hubmobile/go.mod` 再新增本地 `replace`，只需按同样模式补 checkout，并同步文档。
- 若未来切换为纯 semver 依赖，可直接删除这些 checkout，并在 `docs/release.md` 与归档中收口。

#### Issue List
- 无

### Stage 3.1 - Planning
#### Project Goal and Current State
- 当前 `v0.1.27` release run 在 `Build AAR (gomobile)` 失败；同类失败至少从 `v0.1.23` 开始持续出现。
- 当前 `hubmobile/go.mod` 已声明：
  - `replace github.com/yttydcs/myflowhub-server => ../../MyFlowHub-Server`
  - `replace github.com/yttydcs/myflowhub-sdk => ../../MyFlowHub-SDK`
  - `replace github.com/yttydcs/myflowhub-proto => ../../MyFlowHub-Proto`
- 当前 workflow 只 checkout 了 Android + Server，未补 SDK / Proto。
- Follow-up：`v0.1.28` 已验证 checkout 链补齐，但 release run #29 仍失败；日志确认 `actions/checkout` 为 `MyFlowHub-Proto` 拉取了 default branch `refactor/proto-extract`，而不是 workflow 期望的 `main`。

#### Docs Governance Routing Decision
- 使用 `$m-docs` 校验计划文档路由、requirements/specs 影响和 lessons 查询入口。
- Requirements impact: `none`
- Specs impact: `none`
- Related requirements: `none`
- Related specs: `none`
- Related lessons:
  - `docs/lessons/android-hubmobile-local-replace.md`
- 文档路由：
  - 当前 workflow 控制文档位于 worktree 根 `plan.md`
  - 完成结果归档到 `docs/change/2026-04-01_android-release-checkout-deps.md`
  - 仅当本轮沉淀出比现有 lesson 更稳定的新排障规则时才新增 / 更新 `docs/lessons`

#### Related Requirements / Specs / Lessons
- `docs/release.md`
- `docs/lessons/android-hubmobile-local-replace.md`
- `docs/change/2026-02-25_android-apk-release-ci.md`
- `docs/change/2026-03-04_android-release-gomobile-pin.md`

#### Executable Task List
- [x] `ANDRELCHK-1`：归档旧 `plan.md` 并建立本轮控制文档
- [x] `ANDRELCHK-2`：修复 `release.yml` 的 checkout 依赖链
- [x] `ANDRELCHK-3`：修复 `ci.yml` 的 checkout 依赖链
- [x] `ANDRELCHK-4`：更新 `docs/release.md` 说明
- [x] `ANDRELCHK-5`：完成验证、自审与变更归档
- [ ] `ANDRELCHK-6`：将依赖仓 checkout 显式 pin 到 `main`
- [ ] `ANDRELCHK-7`：重发 tag 并复核 release 结果

#### Task Details
##### ANDRELCHK-1 - 控制文档切换
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps\plan.md`
- Goal: 归档旧 workflow 计划，确保当前 `plan.md` 仅服务于本次 release 修复。
- Files / Modules:
  - `plan.md`
  - `docs/plan_archive/plan_archive_2026-04-01_android-rfcomm-listener-config-prev.md`
- Write Set:
  - `plan.md`
  - `docs/plan_archive/plan_archive_2026-04-01_android-rfcomm-listener-config-prev.md`
- Acceptance:
  - 旧计划已归档
  - 新计划完整记录 stage 1 / 2 / 3.1
- Test Points:
  - 归档文件存在且可读
- Rollback:
  - 还原 `plan.md` 与归档文件路径

##### ANDRELCHK-2 - Release workflow checkout 依赖补齐
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps\plan.md`
- Goal: 让 Release 的 runner 工作区满足 `hubmobile/go.mod` 的当前本地 `replace`。
- Files / Modules:
  - `.github/workflows/release.yml`
- Write Set:
  - `.github/workflows/release.yml`
- Acceptance:
  - 新增 SDK / Proto checkout
  - 路径与 `../../MyFlowHub-SDK`、`../../MyFlowHub-Proto` 一致
- Test Points:
  - 审阅 workflow 路径
  - `git diff --check`
- Rollback:
  - 回退 `release.yml` 到当前主线版本

##### ANDRELCHK-3 - CI workflow checkout 依赖补齐
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps\plan.md`
- Goal: 让 debug CI 与 release 使用同一套依赖 checkout 拓扑。
- Files / Modules:
  - `.github/workflows/ci.yml`
- Write Set:
  - `.github/workflows/ci.yml`
- Acceptance:
  - 新增 SDK / Proto checkout
  - 与 release 保持一致
- Test Points:
  - 审阅 workflow 路径
  - `git diff --check`
- Rollback:
  - 回退 `ci.yml` 到当前主线版本

##### ANDRELCHK-4 - Release 文档对齐
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps\plan.md`
- Goal: 修正文档中“只 checkout Server”的过时描述。
- Files / Modules:
  - `docs/release.md`
- Write Set:
  - `docs/release.md`
- Acceptance:
  - 文档明确说明 Actions 会 checkout Server / SDK / Proto
  - 原因与 `hubmobile/go.mod` 的 `replace` 链对应
- Test Points:
  - 文档审阅
- Rollback:
  - 回退 `docs/release.md`

##### ANDRELCHK-5 - 验证、审查与归档
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps\plan.md`
- Goal: 让本轮修复、验证与经验可审计。
- Files / Modules:
  - `plan.md`
  - `docs/change/2026-04-01_android-release-checkout-deps.md`
  - `docs/lessons/README.md`（仅当 lesson 需要更新）
- Write Set:
  - `plan.md`
  - `docs/change/2026-04-01_android-release-checkout-deps.md`
  - `docs/lessons/README.md`（conditional）
- Acceptance:
  - 自审 checklist 完整
  - 变更归档包含背景、验证、回滚、lesson impact
- Test Points:
  - `git diff --check`
  - `git status --short`
- Rollback:
  - 删除本轮归档并回退相关文件

##### ANDRELCHK-6 - 依赖仓 checkout 显式 pin 到 main
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps\plan.md`
- Goal: 避免 `actions/checkout` 落到依赖仓的 default branch，而不是 Android release 期望的 `main`。
- Files / Modules:
  - `.github/workflows/release.yml`
  - `.github/workflows/ci.yml`
  - `docs/release.md`
  - `docs/change/2026-04-01_android-release-checkout-deps.md`
  - `docs/lessons/android-hubmobile-local-replace.md`
- Write Set:
  - `.github/workflows/release.yml`
  - `.github/workflows/ci.yml`
  - `docs/release.md`
  - `docs/change/2026-04-01_android-release-checkout-deps.md`
  - `docs/lessons/android-hubmobile-local-replace.md`
  - `plan.md`
- Acceptance:
  - `Checkout Server / SDK / Proto` 都显式指定 `ref: main`
  - 文档记录 default branch 漂移风险
- Test Points:
  - 审阅 workflow checkout 参数
  - 对照 `v0.1.28` 日志确认之前的 failure 根因已覆盖
- Rollback:
  - 回退 workflow 与文档的 `ref: main` 变更

##### ANDRELCHK-7 - 重发 tag 验证 release
- Owner: `main`
- Worktree: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps`
- Plan Path: `D:\project\MyFlowHub3\worktrees\fix-android-release-workflow-deps\plan.md`
- Goal: 用新的 release run 验证 workflow 是否越过 `Build AAR (gomobile)`。
- Files / Modules:
  - `repo/MyFlowHub-Android/main`（控制面 merge / tag）
- Write Set:
  - `repo/MyFlowHub-Android/main`
  - remote `main`
  - remote tag
- Acceptance:
  - 新 tag 触发新的 release run
  - 至少确认 checkout 已拉到 `main`
  - 优先确认是否越过 `Build AAR (gomobile)`
- Test Points:
  - GitHub Actions run 状态与日志
- Rollback:
  - 如误发 tag，删除远端 tag；如主线提交有误，另起 revert

#### Dependencies
- `hubmobile/go.mod` 中的本地 `replace` 目录结构
- GitHub 仓库：
  - `yttydcs/myflowhub-server`
  - `yttydcs/myflowhub-sdk`
  - `yttydcs/myflowhub-proto`
- Actions 基础设施：
  - `actions/checkout@v4`
  - `ubuntu-latest`

#### Risks and Notes
- 本轮无法在本地直接模拟 GitHub runner 的远端 checkout 权限与网络稳定性，只能做静态与结构验证。
- GitHub 日志下载接口对匿名访问受限，因此归档中的失败原因基于 run 元数据、workflow 结构与仓内依赖链综合判断。
- `v0.1.28` follow-up 日志已确认 `MyFlowHub-Proto` 的 default branch 为 `refactor/proto-extract`，因此 workflow 若不显式写 `ref: main`，checkout 结果会与当前 release 期望不一致。

#### Parallelism Assessment
- 本轮变更集中在两个 workflow 和一份文档，写集高度重叠，不适合并行派发。
- 子Agent：不使用。

#### Issue List
- 无

### Stage 3.2 - Implementation
- `ANDRELCHK-1`
  - 已将旧 `plan.md` 归档到 `docs/plan_archive/plan_archive_2026-04-01_android-rfcomm-listener-config-prev.md`，并重建当前 workflow 的控制文档。
- `ANDRELCHK-2`
  - `release.yml` 已补齐 `MyFlowHub-SDK` 与 `MyFlowHub-Proto` checkout。
- `ANDRELCHK-3`
  - `ci.yml` 已补齐 `MyFlowHub-SDK` 与 `MyFlowHub-Proto` checkout。
- `ANDRELCHK-4`
  - `docs/release.md` 已改为说明完整的 Hubmobile 本地依赖拓扑。
- `ANDRELCHK-5`
  - 已更新 lesson，并新增 `docs/change/2026-04-01_android-release-checkout-deps.md` 归档。
- `ANDRELCHK-6`
  - 待执行：基于 `v0.1.28` 失败日志，为依赖仓 checkout 增加显式 `ref: main`。
- `ANDRELCHK-7`
  - 待执行：workflow 修复后重发 tag 并复核远端 release。

### Stage 3.3 - Code Review
- 需求覆盖：通过
- 架构合理性：通过
- 性能风险（N+1 / 重复计算 / 多余 I/O / 锁竞争）：通过
- 可读性与一致性：通过
- 可扩展性与配置化：通过
- 稳定性与安全：通过
- 测试覆盖情况：部分通过
  - 已做静态结构校验与文档一致性校验；尚未执行远端 Actions rerun
- 子Agent治理与审计（任务映射、上下文完整性、文件所有权、结果复核、冲突处理、记录完整性）：通过
- Follow-up 结论：
  - `v0.1.28` 远端实跑暴露新的确定性问题，需回到 `3.1 / 3.2` 增补 `ref: main` 修复后再重新审查。

### Stage 4 - Change Archive
- 使用 `$m-docs` 完成变更归档与 lesson 路由校验。
- `docs/change/2026-04-01_android-release-checkout-deps.md`：已创建
- `docs/lessons/android-hubmobile-local-replace.md`：已更新
- Requirements impact: `none`
- Specs impact: `none`
- Lessons impact: `updated`

阻塞：否
返回 3.2 执行 follow-up 修复
