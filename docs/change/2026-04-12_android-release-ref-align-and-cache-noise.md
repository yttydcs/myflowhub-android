# 2026-04-12 - Android：对齐 release dependency ref 并收窄 failure noise

## 变更背景 / 目标
- `v0.1.30` 触发的远端 `release #31` 与 `ci #64` 均失败。
- 已确认：
  - `Build AAR (gomobile)` 是主失败阶段。
  - workflow 仍把 sibling repo 固定 checkout 到 `main`，与 `hubmobile/go.mod` 当前 semver 基线不一致。
  - `ci.yml` 在 `Build AAR` 失败后，还会继续上传不存在的 `gradle-debug.log`，制造次级错误，干扰定位。
- 目标：
  - 让 CI / Release 的 sibling repo checkout 与 `hubmobile/go.mod` 一致。
  - 减少 failure path 噪音，让下次远端 run 更容易定位真正的主失败点。

## 具体变更内容
### 修改
1) `.github/workflows/ci.yml`
- 新增 `Resolve hubmobile dependency refs` 步骤：
  - 从 `hubmobile/go.mod` 解析 `myflowhub-server`、`myflowhub-sdk`、`myflowhub-proto` 当前声明版本。
- `Checkout Server / SDK / Proto` 改为使用上一步解析出的 ref，不再硬编码 `main`。
- 关闭 `actions/setup-go` cache。
- `gradle/actions/setup-gradle` 改为 `cache-disabled: true`，先绕开远端 cache `400` 噪音。
- `Upload Gradle log (on failure)` 改为 `if-no-files-found: ignore`，避免 `Build AAR` 失败后再被缺失文件二次报错污染。

2) `.github/workflows/release.yml`
- 同步新增 `Resolve hubmobile dependency refs`，并用解析出的 ref checkout `Server / SDK / Proto`。
- 关闭 `actions/setup-go` cache。
- `gradle/actions/setup-gradle` 改为 `cache-disabled: true`。
- `build-info.txt` 额外记录：
  - `serverRef`
  - `sdkRef`
  - `protoRef`
  - `sdkCommit`
  - `protoCommit`

3) `docs/release.md`
- 更新 GitHub Actions dependency checkout 说明：
  - 从“固定拉 `main`”改为“按 `hubmobile/go.mod` 当前 semver 版本解析 ref”。
- 明确记录：
  - 若依赖版本未真正发布，workflow 会显式失败，而不会悄悄漂到 `main`
  - release 的 `build-info.txt` 会记录实际使用的 ref 与 commit

## Requirements impact
`none`

## Specs impact
`none`

## Lessons impact
`none`

## Related requirements
- `none`

## Related specs
- `none`

## Related lessons
- `D:\project\MyFlowHub3\docs\lessons\cross-repo-semver-release.md`

## 对应 todo.md 任务映射
- `AND-R2-1` - Confirm remote failure mode
- `AND-R2-2` - Align workflow dependency refs
- `AND-R2-3` - Reduce secondary failure noise
- `AND-R2-4` - Validate locally
- `AND-R2-5` - Prepare next patch release

## 关键设计决策与权衡
1. 不再让 release workflow 无条件依赖 `main`
- 好处：与 `hubmobile/go.mod` 当前 semver 基线一致，可审计、可回放。
- 代价：如果 `hubmobile/go.mod` 填了一个尚未真正发布的版本，workflow 会更早失败。

2. 对 cache `400` 先采取规避，而不是继续赌远端缓存服务稳定
- 好处：先排除环境型噪音，缩小下次远端 run 的变量面。
- 代价：CI / Release 会稍慢。

3. 对缺失 `gradle-debug.log` 采用 `ignore`
- 好处：`Build AAR` 失败时，run 页面不再被次级错误淹没。
- 代价：如果 Gradle 步骤本身真的执行过但没产生日志，需要从主失败点继续看。

## 测试与验证方式 / 结果
- 远端公开 run 页面核验：
  - `release #31`：失败
  - `ci #64`：失败
- 本地验证：
  - `hubmobile` `GOWORK=off go test ./... -count=1 -p 1`
    - 结果：通过
  - `./scripts/build_aar.ps1`
    - 结果：通过，成功产出 `app/libs/myflowhub.aar`
- 静态审查：
  - workflow 现在会先解析 `hubmobile/go.mod` 中的依赖版本，再 checkout `Server / SDK / Proto`

## 潜在影响
- 下一次 Android CI / Release 将不再隐式跟随 sibling repo `main` 漂移。
- 若 `hubmobile/go.mod` 中声明的版本与远端 tag 不一致，workflow 会更早、更明确地失败。

## 回滚方案
- 回退 `.github/workflows/ci.yml`、`.github/workflows/release.yml`、`docs/release.md`。
- 不改写 `v0.1.30`；如需重新发布，只发更高 patch 版本。

## Notes
- 当前还没直接拿到公开 artifact 内的 `gomobile-build.log` 正文；本轮修复优先处理高置信度问题，而不是猜测性扩大改动。
