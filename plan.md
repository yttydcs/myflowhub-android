# Plan - Android：升级 hubmobile 的 management 至 v0.1.2 并触发 debug-latest

## Workflow 信息
- Repo：`MyFlowHub-Android`
- 分支：`chore/android-bump-management-v0.1.2`
- Worktree：`d:\project\MyFlowHub3\worktrees\chore-android-bump-management-v0.1.2`
- Base：`origin/main`
- 关联发布/依赖：
  - `myflowhub-subproto`：`management/v0.1.2`（children-only 修复）
  - Android CI：`push main` 会更新 `debug-latest`（APK + AAR）
- 参考：`d:\project\MyFlowHub3\guide.md`（commit 信息中文）

## 背景 / 问题陈述（事实，可审计）
- `myflowhub-subproto/management v0.1.1` 的 `list_nodes` 会把 upstream(parent) 链接也枚举出来，导致设备树可能出现回指/重复节点。
- `myflowhub-subproto/management` 已发布 `v0.1.2`（children-only），`myflowhub-server/main` 也已升级至该版本。
- Android 侧 `hubmobile/go.mod` 仍显示 `github.com/yttydcs/myflowhub-subproto/management v0.1.1`（indirect）。
- Android 的 `ci.yml` 仅在 `main` 分支 push 时发布/更新 `debug-latest`；因此需要一次 `main` push 来触发。

## 目标
1) 将 `hubmobile` 模块的 `github.com/yttydcs/myflowhub-subproto/management` 升级到 `v0.1.2`（更新 `go.mod/go.sum`）。
2) 本地验证通过（Go + Android build），降低 CI 失败概率。
3) 合并并 push 到 `main`，触发 GitHub Actions 更新 `debug-latest`（直接可下载 APK）。

## 非目标
- 不改 Android UI/交互。
- 不改协议 wire schema / action 语义（仅升级依赖版本）。
- 不新增 Android 业务发布 tag（debug-latest 由 CI 维护）。

## 约束（边界）
- Go 验证必须使用 `GOWORK=off`，避免被 meta-workspace `go.work` 干扰。
- 变更最小化：只改 `hubmobile/go.mod`、`hubmobile/go.sum` 与文档。

## 验收标准
- `hubmobile/go.mod` 中 `github.com/yttydcs/myflowhub-subproto/management` 版本为 `v0.1.2`。
- `hubmobile`：`GOWORK=off go test ./... -count=1 -p 1` 通过。
- Android：`scripts/build_aar.sh` 与 `./gradlew :app:assembleDebug` 通过（若本机缺依赖，则至少保证 Go 侧验证通过并说明原因）。
- 推送 `main` 后触发 Actions（产生 `debug-latest` 的 APK + AAR）。

---

## 3.1) 计划拆分（Checklist）

### ANDMG0 - 归档旧 plan.md
- 目标：避免历史计划覆盖本次任务。
- 操作：`plan.md` → `docs/plan_archive/plan_archive_2026-03-03_android-bump-management-v0.1.2-prev.md`
- 验收条件：归档文件存在且可阅读。
- 回滚点：撤销 `git mv`。

### ANDMG1 - 升级 hubmobile 的 management 依赖到 v0.1.2
- 目标：让 Android 构建确定性使用 children-only 修复版本。
- 涉及文件：
  - `hubmobile/go.mod`
  - `hubmobile/go.sum`
- 操作：
  - `cd hubmobile`
  - `GOWORK=off go get github.com/yttydcs/myflowhub-subproto/management@v0.1.2`
  - `GOWORK=off go mod tidy`
- 验收条件：`go.mod/go.sum` 仅发生版本与 checksum 的最小变更。
- 回滚点：revert 提交或 `go get ...@v0.1.1 && go mod tidy`。

### ANDMG2 - 本地验证（尽量覆盖 CI 关键路径）
- 目标：降低 push main 后 CI 失败概率。
- 验收命令：
  - `cd hubmobile; GOWORK=off go test ./... -count=1 -p 1`
  - `GOWORK=off bash scripts/build_aar.sh`
  - `./gradlew :app:assembleDebug`
- 回滚点：revert 提交。

### ANDMG3 - Code Review（阶段 3.3）
- 目标：确认变更最小化、只涉及依赖升级与可审计文档。

### ANDMG4 - 归档变更（阶段 4）
- 目标：记录依赖升级与验证步骤/结果。
- 文件：
  - `docs/change/2026-03-03_android-bump-management-v0.1.2.md`

