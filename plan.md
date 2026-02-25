# Plan - Android：Hub（M0）冒烟补齐（LAN 直连 + Parent 链路）

> 说明：本 workflow 的目标是补齐 “M0 可行性验证” 的**可复现冒烟**与文档/工具；不扩大到 M1（全量 UI/体验/安全加固）。  
> 上一个 M0 workflow 的计划与归档已存在于 `docs/plan_archive/` 与 `docs/change/`；本计划是新的补齐 workflow（不会假设旧 worktree 仍存在）。

## 0. Workflow 信息

- Workflow 名称：`android-hub-m0-smoke`
- 分支（本仓）：`fix/android-hub-m0-smoke`
- Base：`main`
- 涉及仓库：
  - `MyFlowHub-Android`：文档/脚本/（可选）PC 侧 smoke 工具
  - `MyFlowHub-Server`：仅用于本地运行 parent hub 与满足 `hubmobile/go.mod` 的 replace（本 workflow **不做功能改动**）

## 1. 目标（验收口径）

### 1.1 必须达成
1) 能从 `MyFlowHub-Android` 构建：
   - `app/libs/myflowhub.aar`（`gomobile bind`，至少 `android/arm64`）
   - debug APK（`assembleDebug`）
2) 真机启动后：Android Hub 以 Foreground Service 常驻运行；监听 `:9000`（或 `0.0.0.0:9000`）对 LAN 可达。
3) 冒烟验证覆盖两条链路：
   - **LAN 直连手机 Hub**：PC/另一设备手动填写 `手机IP:port` 访问，`management node_echo` 成功。
   - **手机 Hub 连接 parent Hub**：手机填写 parent 后成功上联；从 parent 侧对手机节点转发 `management node_echo(target=手机node_id)` 成功。

### 1.2 不做（明确排除）
- 自动发现（mDNS/广播）。
- 子协议全量 UI（本次仅要求“Hub 运行时具备子协议能力”，UI 不对齐 Win）。
- 安全加固（开放注册/默认高权限仅做风险提示，不在 M0 补齐 workflow 改造）。

## 2. 当前状态（已知问题）

- M0 代码已合并到 `main`（Android 壳 + `hubruntime` + parent bootstrap watcher）。
- `docs/m0_smoke.md` 仍引用已删除的旧 worktree 路径，且验证步骤不完整（缺少 parent 链路）。
- 缺少一个“不依赖 Win UI”的 PC 侧最小 smoke 工具（可选但推荐），会降低交接与排障效率。

## 3. 计划拆分（Checklist）

> 约定：每个任务必须有回滚点；不得引入计划外改动；新增任务需先更新本 plan 并重新确认。

### ANDS1 - 修正/补全 M0 冒烟文档（两条链路）
- 目标：让 `docs/m0_smoke.md` 能在**没有旧 worktree 的前提下**复现，并补齐 parent 链路验证步骤。
- 涉及文件：
  - `docs/m0_smoke.md`
- 验收条件：
  - 文档包含：构建 AAR、构建 APK、安装启动、LAN 直连验证、parent 链路验证、常见问题排障。
  - 所有路径/命令均可在 “仅 clone 本仓 + 相邻仓库” 的情况下执行（不引用已删除的历史 worktree 路径）。
- 测试点：按文档在真机完成两条链路冒烟。
- 回滚点：revert 本任务提交。

### ANDS2 - （推荐）提供 PC 侧最小 smoke 工具（register + node_echo + list_nodes）
- 目标：提供一个最小可执行/可 `go run` 的工具，避免完全依赖 Win UI 做冒烟。
- 设计约束：
  - 只用标准库实现 HeaderTcp(v2) 编解码与 JSON message（避免引入额外 Go 依赖）。
  - 必须包含输入校验与清晰错误输出（便于排障：网络不可达/未注册/target 不存在等）。
- 预期位置（可调整，但需在本 plan 中固定）：
  - `tools/hubsmoke/`（`go.mod` + `main.go`）
- 验收条件：
  - 直连手机：`register -> node_echo` 成功。
  - 连接 parent：对 `target=手机node_id` 的 `node_echo` 成功。
- 回滚点：删除 `tools/hubsmoke/` 并 revert 文档引用。

### ANDS3 - Code Review（强制）
- 按全局 3.3 清单逐项输出结论（通过/不通过）。
- 不通过：回到对应任务修正，再次 Review。

### ANDS4 - 归档变更（强制）
- 在本 worktree 根目录创建 `docs/change/` 并新增归档文档：
  - `docs/change/YYYY-MM-DD_android-hub-m0-smoke.md`
- 必须包含：任务映射、关键决策与权衡、验证方式与结果（含两条链路）、回滚方案。

## 4. 依赖与注意事项

- Android 构建依赖：Go、JDK（建议 17）、Android SDK（建议 android-34）、NDK（gomobile 需要）、ADB。
- 网络注意：手机与 PC 必须同一 LAN；路由器需关闭 AP isolation；PC 防火墙需放行出站/入站（视测试方向）。
- 监听注意：务必使用 `:9000` 或 `0.0.0.0:9000`，不要只监听 `127.0.0.1`。

