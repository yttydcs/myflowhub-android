# 2026-03-04 - Android：修复 Release Build AAR 的 gomobile 版本漂移

## 变更背景 / 目标
- 背景：`MyFlowHub-Android` 在相同 commit（`6708fa2`）下，历史 tag `v0.1.12` 的 release 构建成功，但新 tag `v0.1.13` 的 release 在 `Build AAR (gomobile)` 失败。
- 根因判断：AAR 构建依赖 `gomobile` 工具链，原流程在工具缺失时使用 `@latest` 安装，导致“同一代码、不同时间”可能拉到不同版本，产生非确定性失败。
- 目标：将 gomobile/gobind 工具链改为“由模块版本驱动”，恢复 release 的可复现性与可审计性。

## 具体变更内容

### 修改
1) `scripts/build_aar.sh`
- 新增 `ensure_go_bin_in_path`，确保安装后 `$(go env GOPATH)/bin` 可执行。
- 新增 `resolve_x_mobile_version`，从 `hubmobile` 模块解析 `golang.org/x/mobile` 实际版本。
- 新增 `install_gomobile_tools`：
  - 优先安装 `gomobile/gobind@<hubmobile/go.mod 对齐版本>`；
  - 仅在版本解析失败时 fallback 到 `@latest`。
- 启动前从“只检查 gomobile”改为“同时检查 gomobile + gobind”。

2) `.github/workflows/release.yml`
- 在 Build AAR 前新增“固定版本安装 gomobile/gobind”步骤（版本来源：`hubmobile/go.mod`）。
- Build AAR 步骤改为 `tee gomobile-build.log`。
- 新增失败时上传 `gomobile-build.log` artifact，提升故障可观测性。

3) `.github/workflows/ci.yml`
- 与 release 同步新增“固定版本安装 gomobile/gobind”步骤。
- Build AAR 步骤改为 `tee gomobile-build.log`。
- 新增失败时上传 `gomobile-build.log` artifact。

### 新增
- `docs/plan_archive/plan_archive_2026-03-04_android-release-gomobile-pin-prev.md`（归档旧计划）
- 新 `plan.md`（本 workflow 全流程计划）

### 删除
- 无。

## 对应 plan.md 任务映射
- ANDREL0：归档旧计划并建立新计划（已完成）
- ANDREL1：加固 `scripts/build_aar.sh`（已完成）
- ANDREL2：release workflow 固定版本安装（已完成）
- ANDREL3：ci workflow 固定版本安装（已完成）
- ANDREL4：验证与记录（已完成）

## 关键设计决策与权衡（性能 / 扩展性）
1) 版本来源选 `go list -m` 而非硬编码
- 优点：与 `hubmobile/go.mod` 同源，减少双维护与版本漂移。
- 代价：依赖模块元信息可解析；若被移除需显式失败并修复。

2) workflow 显式安装 + 脚本兜底双层策略
- 优点：release/ci 路径确定化，同时保留本地执行时的自愈能力。
- 代价：逻辑存在一定重复，但换来更高鲁棒性。

3) 可观测性增强
- 失败时自动保存 gomobile 日志，降低故障定位时间。

## 测试与验证方式 / 结果
- 已执行：
  - `go list -m -f '{{.Version}}' golang.org/x/mobile`（在 `hubmobile`，`GOWORK=off`）
    - 结果：`v0.0.0-20260217195705-b56b3793a9c4`
  - `bash -n scripts/build_aar.sh`（Git Bash）
    - 结果：通过（无语法错误）
- 未在本地执行：完整 `gomobile bind` 与 Android release 打包（受本机 Android 构建环境/凭据限制，按 CI 验证）。

## 3.3 Code Review 结论（强制项）
- 需求覆盖：通过（定位并消除 `@latest` 漂移，release 构建链路可观测）
- 架构合理性：通过（workflow 显式安装 + 脚本兜底，职责清晰）
- 性能风险：通过（无新增 N+1/重复 I/O；仅构建阶段多一步版本解析）
- 可读性与一致性：通过（命名明确，CI/release 策略一致）
- 可扩展性与配置化：通过（版本自动跟随 `go.mod`，后续升级无需改硬编码）
- 稳定性与安全：通过（失败快速退出，日志可追溯，不新增 secret 面）
- 测试覆盖情况：部分通过（静态检查与版本解析验证已做；最终发布链路需实际 tag run 验证）

## 潜在影响与回滚方案
- 潜在影响：
  - workflow 新增安装步骤，构建时间可能小幅增加。
  - 若 `hubmobile/go.mod` 不再声明 `x/mobile`，安装步骤会明确失败（可快速暴露配置问题）。
- 回滚方案：
  1. `git revert <本次提交>` 回滚脚本与 workflow 变更；
  2. 若只需局部回滚，可分别 revert `scripts/build_aar.sh` 或 workflow 文件。
