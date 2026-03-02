# Android：修复 debug-latest 发布找不到 APK，并同步发布 AAR

## 变更背景 / 目标

`ci` workflow 已引入 `debug-latest` 预发布 Release 用于提供 Debug APK 的稳定直链下载，但在 `publish-debug-latest` job 中出现失败：

- 下载后的 artifact 目录结构为：
  - `artifacts/build/outputs/apk/debug/app-debug.apk`
  - `artifacts/libs/myflowhub.aar`
- workflow 脚本却硬编码查找：
  - `artifacts/repo/MyFlowHub-Android/app/build/outputs/apk/debug/app-debug.apk`

导致发布阶段报错 “找不到 APK”，无法更新 `debug-latest` Release。

本次目标：

- 修复发布脚本的产物定位方式，使其与 artifact 解压结构一致且更稳健。
- 在 `debug-latest` Release 中同时发布：
  - `myflowhub-debug.apk`
  - `myflowhub.aar`
- Actions Summary 同时输出 APK/AAR 的直链下载地址。

## 具体变更内容

### 修改

- `.github/workflows/ci.yml`
  - `publish-debug-latest`：
    - 通过 `find artifacts` 定位 `app-debug.apk` 与 `myflowhub.aar`，并使用“路径后缀匹配 + 必须且仅能命中 1 个”进行校验。
    - 发布/覆盖 Release assets（`--clobber`）：
      - `myflowhub-debug.apk`
      - `myflowhub.aar`
    - Summary 输出两条直链（APK/AAR）。

## plan.md 任务映射

- T3：发布/覆盖 `debug-latest` Release 资产（APK + AAR）
- T4：Actions Summary 输出直链（APK + AAR）

## 关键设计决策与权衡

1) **基于下载后的 `artifacts/` 实际结构定位产物（不再硬编码原始路径）**

`actions/upload-artifact` 会对多个 path 计算公共根目录，导致 `download-artifact` 解压后的目录结构与“仓库内原始路径”不一致。

采用 `find` + 校验：

- 兼容未来 artifact 根目录变化（例如公共根目录从 `app/` 变为仓库根目录）。
- 若出现多份同名产物，直接失败并输出候选路径与 `ls -R artifacts`，便于排查与审计。

2) **固定 Release 资产命名**

保持稳定命名以保证直链稳定：

- APK：`myflowhub-debug.apk`
- AAR：`myflowhub.aar`

## 测试与验证方式 / 结果

说明：此变更主要影响 GitHub Actions 行为，本地无法完全等价模拟。

验证步骤（合并到 `main` 后）：

1. push 任意提交到 `main`，触发 `ci` workflow。
2. `publish-debug-latest` job 成功。
3. 打开 `debug-latest` Release，确认 assets 同时存在并可下载：
   - `myflowhub-debug.apk`
   - `myflowhub.aar`
4. 在 Actions run Summary 中确认存在两条直链：
   - APK 直链
   - AAR 直链

## 潜在影响与回滚方案

潜在影响：

- 若未来开启 ABI splits / 产出多份 APK，当前策略会因“命中不唯一”而失败，需要再调整匹配规则。

回滚方案：

- revert 本次对 `.github/workflows/ci.yml` 的修改。
