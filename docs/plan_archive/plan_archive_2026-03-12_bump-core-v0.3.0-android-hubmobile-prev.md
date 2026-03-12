# Plan - Android：修复 Release 的 gomobile 漂移导致 Build AAR 失败

## Workflow 信息
- Repo：`MyFlowHub-Android`
- 分支：`fix/android-release-gomobile-pin`
- Worktree：`d:\project\MyFlowHub3\worktrees\fix-android-release-gomobile-pin\MyFlowHub-Android`
- Base：`main@6708fa2`
- 参考：`d:\project\MyFlowHub3\guide.md`（commit 信息中文）

## 1) 需求分析
### 目标
- 修复 Android `release` workflow 在 `Build AAR (gomobile)` 失败的问题，恢复“打 tag 即可发布”的稳定性。

### 范围
- 必须：
  - 消除 `gomobile`/`gobind` 的非确定性版本漂移。
  - 保证 release 构建链路在工具安装阶段具备可审计日志。
- 可选：
  - 同步加固 CI workflow，避免同类问题延后爆发。
- 不做：
  - 不改业务功能/UI。
  - 不改协议语义与 Hub 逻辑。

### 使用场景
- 维护者在仓库推送 `vMAJOR.MINOR.PATCH` tag 后，GitHub Actions 自动构建并发布签名 APK + AAR。

### 功能需求
- Build AAR 前必须安装并使用与 `hubmobile/go.mod` 对齐的 `gomobile/gobind` 版本。
- `build_aar.sh` 在缺少工具时，安装策略应优先使用模块内已声明版本，而非 `@latest`。

### 非功能需求
- 可复现：相同 commit 在不同时间打 tag，产物链路行为一致。
- 可观测：失败时可定位出是“安装阶段”还是“bind 阶段”。
- 变更最小：仅改脚本/工作流与归档文档。

### 输入输出
- 输入：`v*.*.*` tag 触发 `release.yml`。
- 输出：`app-release.apk`、`myflowhub.aar`、`build-info.txt`（保持现有发布语义不变）。

### 边界异常
- Runner 预装旧版本 gomobile/gobind。
- `GOPATH/bin` 未在 PATH，安装后命令不可见。
- `go list -m golang.org/x/mobile` 解析失败。

### 验收标准
- 新 tag 触发的 Android release run 中，`Build AAR (gomobile)` 成功。
- workflow 日志中可见固定版本安装信息。
- 本地脚本语法检查通过（`bash -n scripts/build_aar.sh`）。

### 风险
- 若未来 `hubmobile/go.mod` 删除 `golang.org/x/mobile` 声明，版本解析会失败。
- 改动 workflow 可能影响现有缓存命中率（可接受，优先稳定性）。

### 问题清单
- 无。
- 阻塞：否

## 2) 架构设计（分析）
### 总体方案（含选型理由 / 备选对比）
- 方案A（采用）：
  - 在 workflow 中显式安装与模块对齐的 `gomobile/gobind`。
  - 在 `build_aar.sh` 中将自动安装从 `@latest` 改为“优先模块版本，失败再 fallback”。
  - 理由：release 路径直接确定化，脚本也具备兜底，覆盖 CI/本地两类入口。
- 方案B（不选）：仅改 workflow，不改脚本。
  - 问题：本地开发和其它入口仍可能拉到 `@latest`，风险外溢。

### 模块职责
- `scripts/build_aar.sh`：AAR 构建入口与工具自愈。
- `.github/workflows/release.yml`：发布场景下的确定性工具准备。
- `.github/workflows/ci.yml`：日常验证场景下的一致性工具准备。

### 数据 / 调用流
- workflow 触发 -> setup-go -> 解析 `golang.org/x/mobile` 版本 -> install gomobile/gobind -> `scripts/build_aar.sh` -> `gomobile bind`。

### 接口草案
- 无对外 API 变更。
- 脚本内新增环境/命令约束：确保 `$(go env GOPATH)/bin` 在 PATH。

### 错误与安全
- 工具安装失败时即时退出并打印明确错误。
- 保持现有 keystore secret 读取策略，不新增 secret 面。

### 性能与测试策略
- 性能关键点：避免重复安装；仅在 workflow 中一次安装，脚本侧仅在缺失时安装。
- 测试：
  - 静态：`bash -n scripts/build_aar.sh`
  - 行为：推送修复分支触发 CI；再打新 tag 触发 release。

### 可扩展性设计点
- 版本来源使用 `go list -m`，后续更新 `hubmobile/go.mod` 时无需改硬编码版本。

- 阻塞：否

## 3.1) 计划拆分（Checklist）

### ANDREL0 - 归档旧计划并建立本计划
- 目标：保证本 workflow 文档可独立接手。
- 涉及文件：`plan.md`、`docs/plan_archive/plan_archive_2026-03-04_android-release-gomobile-pin-prev.md`
- 验收条件：旧计划归档完成，新计划包含完整任务与验收。
- 测试点：人工核对文件存在与内容完整。
- 回滚点：撤销归档与新 plan 提交。

### ANDREL1 - 加固 `scripts/build_aar.sh` 的工具安装逻辑
- 目标：避免 `@latest` 漂移导致的非确定性失败。
- 涉及文件：`scripts/build_aar.sh`
- 验收条件：缺失工具时优先安装模块版本；安装后保证 PATH 可执行。
- 测试点：`bash -n scripts/build_aar.sh`。
- 回滚点：revert 本任务提交。

### ANDREL2 - Release workflow 固定 gomobile/gobind 版本安装
- 目标：保证 tag 发布链路完全确定。
- 涉及文件：`.github/workflows/release.yml`
- 验收条件：Build AAR 前显式安装并打印版本来源。
- 测试点：workflow YAML 语法与步骤顺序检查。
- 回滚点：revert 本任务提交。

### ANDREL3 - CI workflow 同步固定版本（防止回归）
- 目标：release/ci 使用同一工具链策略，减少环境漂移。
- 涉及文件：`.github/workflows/ci.yml`
- 验收条件：CI 的 Build AAR 前存在相同安装逻辑。
- 测试点：workflow YAML 语法与步骤顺序检查。
- 回滚点：revert 本任务提交。

### ANDREL4 - 验证与记录
- 目标：形成可审计证据并可回放。
- 涉及文件：`docs/change/2026-03-04_android-release-gomobile-pin.md`
- 验收条件：包含任务映射、验证步骤、风险与回滚。
- 测试点：文档字段完整性检查。
- 回滚点：撤销文档提交。

### ANDREL5 - 对齐 hubmobile module 依赖（修复 go mod tidy 漂移）
- 目标：修复 `Build AAR` 过程中因依赖漂移导致的 `go.mod` 校验失败。
- 涉及文件：`hubmobile/go.mod`、`hubmobile/go.sum`
- 验收条件：`GOWORK=off go test ./...` 通过，且仅产生必要版本对齐变更。
- 测试点：`cd hubmobile; GOWORK=off go mod tidy && GOWORK=off go test ./... -count=1 -p 1`。
- 回滚点：revert 本任务提交，或恢复到变更前版本。

## 依赖关系
- ANDREL1/2/3 完成后，执行 ANDREL5，再执行 ANDREL4。

## 风险与注意事项
- GitHub API 匿名限流会影响即时日志拉取；以 workflow 页面结果为准。
- 不在 `repo/` 主 worktree做实现改动，全部修改仅在本 worktree 完成。

## 执行记录
- 2026-03-04：完成 ANDREL0（旧 `plan.md` 归档到 `docs/plan_archive/plan_archive_2026-03-04_android-release-gomobile-pin-prev.md`）。
- 2026-03-04：完成 ANDREL1（`scripts/build_aar.sh` 引入模块版本驱动的 gomobile/gobind 安装策略）。
- 2026-03-04：完成 ANDREL2（`release.yml` Build AAR 前固定安装 gomobile/gobind，并新增 gomobile 日志上传）。
- 2026-03-04：完成 ANDREL3（`ci.yml` 同步固定安装策略与 gomobile 日志上传）。
- 2026-03-04：完成 ANDREL5（`hubmobile/go.mod/go.sum` 对齐 `myflowhub-subproto/file v0.1.1`，消除 tidy 漂移）。
- 2026-03-04：完成 ANDREL4（新增 `docs/change/2026-03-04_android-release-gomobile-pin.md` 归档）。
