# 2026-02-25 - Android Hub（M0）冒烟补齐（LAN 直连 + Parent 链路）

## 背景 / 目标

此前 Android Hub（M0）已完成代码落地并合并到 `main`，但实际冒烟复现存在两点不足：

1) `docs/m0_smoke.md` 仍引用历史 worktree 路径（`worktrees/android-hub-m0/...`），在新环境下不可直接复现。
2) 冒烟步骤缺少“手机 Hub 上联 Parent，并由 Parent 侧转发 `management` 命令到手机”的验证链路。

本次 workflow 的目标是把 M0 冒烟补齐到“可交接、可复现、可排障”的状态（不扩大到 M1：全量 UI/体验/安全加固）。

## 具体变更内容

### 新增

- `tools/hubsmoke/`：PC 侧最小冒烟工具（标准库实现 HeaderTcp(v2) 编解码 + JSON message），支持：
  - `register`（auth/register 获取 node_id/hub_id）
  - `list-nodes`（management/list_nodes）
  - `echo`（management/node_echo，支持 `-target <node_id>`）
- `tools/hubsmoke/run.ps1`：为避免 meta-workspace 顶层 `go.work` 干扰，强制 `GOWORK=off` 后执行 `go run`。
- `tools/hubsmoke/main_test.go`：覆盖关键编解码路径的最小单测（round-trip + 扩展头 + 超大 payload 拒绝）。

### 修改

- `docs/m0_smoke.md`：
  - 修正构建路径：以 `d:\\project\\MyFlowHub3\\repo\\MyFlowHub-Android` 为基准，不再引用已删除的历史 worktree 路径。
  - 补齐两条冒烟链路：
    1) LAN 直连手机 Hub（手填 `IP:port`）
    2) 手机上联 Parent Hub，并由 Parent 侧转发 `node_echo(target=手机node_id)` 到手机
  - 增补排障：AAR 未打包导致 `(stub)`、LAN 不可达、Parent 连接失败、Parent 侧看不到手机节点等。

### 删除

- 无

## plan.md 任务映射

- ANDS1 - 修正/补全 M0 冒烟文档（两条链路）
  - `docs/m0_smoke.md`
- ANDS2 - 提供 PC 侧最小 smoke 工具（register + node_echo + list_nodes）
  - `tools/hubsmoke/*`

## 关键设计决策与权衡

- **工具依赖最小化**：`hubsmoke` 仅用标准库实现 wire 编解码与 JSON message，避免引入 `myflowhub-core` 等依赖，降低在新环境交接/运行成本。
- **兼容 meta workspace 的 go.work**：由于 `d:\\project\\MyFlowHub3\\go.work` 未包含 `MyFlowHub-Android` 内部 Go module（例如 `hubmobile`），直接 `go run/go test` 会报错；`run.ps1` 通过 `GOWORK=off` 强制 module mode，避免用户踩坑。
- **安全与权限**：本次不改变现状“开放注册”策略，仅在文档中提示风险；安全加固（配对/审批/密钥）留给后续 M1 workflow。

## 测试与验证方式 / 结果

### 已执行（本环境）

- `hubsmoke` 编译与单测：
  - 在 `tools/hubsmoke/` 下执行（需 `GOWORK=off`）：`go test ./...`
  - 结果：通过

### 待执行（需要 Android SDK + 真机）

按 `docs/m0_smoke.md` 执行：

1) 构建 AAR：`.\scripts\build_aar.ps1 -Target android/arm64 ...`
2) 构建 APK：`.\gradlew :app:assembleDebug`
3) 真机安装启动（Foreground Service 常驻）
4) 冒烟 A：PC 直连手机 `IP:port`，执行 `hubsmoke echo`
5) 冒烟 B：启动 Parent Hub（PC），手机配置 `Parent addr` 上联，然后 PC 侧对 `target=手机node_id` 执行 `hubsmoke echo`

结果：本容器环境缺少 Android SDK/真机，未执行；已提供可复现步骤与工具。

## 潜在影响与回滚方案

- 影响：
  - 仅新增工具与文档补齐，不影响 Android App/HUB 运行逻辑与协议实现。
  - `tools/hubsmoke` 作为辅助工具，不参与 Android 构建产物。
- 回滚：
  - 回滚 `docs/m0_smoke.md` 与 `tools/hubsmoke/` 相关提交即可（不涉及其它仓库）。

