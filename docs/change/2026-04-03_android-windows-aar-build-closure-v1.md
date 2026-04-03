# 2026-04-03_android-windows-aar-build-closure-v1

## 变更背景 / 目标
- 背景：
  - Android File 的 `list/read_text/mkdir/pull/offer` 已完成源码级接线，但设备运行时仍依赖 `app/libs/myflowhub.aar` 才能真正生效；
  - 本地 Windows `scripts/build_aar.ps1` 在 `gomobile` 失败时仍可能继续打印 `Done`，导致“没有 AAR 产物却像成功”的假象；
  - 上一轮已确认本机缺少 Android NDK，但脚本本身没有把这个问题暴露得足够明确。
- 目标：
  - 让 Windows AAR 构建脚本具备与 Bash/CI 接近的健壮性；
  - 明确区分“环境缺少 NDK”和“本地 replace 命中的 Server 代码漂移”这两层阻塞；
  - 为后续 Android File 真机闭环提供可信的本地构建诊断基础。

## 具体变更内容
- 修改：
  - `scripts/build_aar.ps1`
    - 新增 `Invoke-NativeChecked`，显式检查 `go install`、`gomobile init`、`gomobile bind` 等原生命令退出码
    - 新增 `Ensure-GoBinInPath`，确保安装后的 `gomobile/gobind` 可执行
    - 新增 `Resolve-XMobileVersion` / `Install-GomobileTools`，优先按 `hubmobile/go.mod` 中的 `golang.org/x/mobile` 版本安装工具链
    - 新增 `Resolve-AndroidSdkRoot` / `Resolve-AndroidNdkHome`，自动探测 `ANDROID_HOME` / `ANDROID_SDK_ROOT` / `%LOCALAPPDATA%\\Android\\Sdk` 与已安装 NDK
    - 仅在目标 AAR 文件真实存在时才打印成功
  - `docs/m0_smoke.md`
    - 补充 Windows 本地 AAR 构建的前置说明，强调脚本会显式检查 NDK 并自动尝试标准 SDK 目录
  - `docs/lessons/README.md`
    - 增加新的 Windows AAR 排障入口
- 新增：
  - `docs/lessons/android-build-aar-windows.md`
    - 记录 Windows 下 `build_aar.ps1` 假成功、缺 NDK 以及本地 replace 二段式阻塞的排查顺序
  - `docs/plan_archive/plan_archive_2026-04-03_android-file-offer-upload-v1-prev.md`
    - 归档上一轮 `offer/upload v1` 计划

## Requirements impact
- none

## Specs impact
- none

## Lessons impact
- updated

## Related requirements
- none

## Related specs
- none

## Related lessons
- `docs/lessons/android-build-aar-windows.md`
- `docs/lessons/android-hubmobile-local-replace.md`

## 对应 plan.md 任务映射
- `ANDAAR-1`：归档上一轮 `plan.md` 并建立本轮控制文档
- `ANDAAR-2`：补齐 Windows `build_aar.ps1` 的环境解析、工具安装与失败传播
- `ANDAAR-3`：执行 AAR / APK 验证，确认当前机器的真实构建结果
- `ANDAAR-4`：完成 3.3 自审与 4 阶段归档

## 经验 / 教训摘要
- 在 Windows 下判断 AAR 构建是否成功，不能只看脚本最后一行日志，必须同时确认 `app/libs/myflowhub.aar` 真实存在。
- Android 构建链路的第一层阻塞是 NDK 缺失；补齐 NDK 之后，第二层阻塞才会显露为本地 `MyFlowHub-Server` replace 漂移。
- 这类二段式构建问题适合拆成“脚本 fail-fast”与“依赖仓漂移”两个独立 lesson 处理，避免把多个问题揉成一个模糊症状。

## 可复用排查线索
- 症状：
  - `.\\scripts\\build_aar.ps1 ...` 打印成功，但 `app/libs/myflowhub.aar` 不存在
  - 构建直接报 `未找到 Android NDK` 或 `no usable NDK`
  - NDK 安装后又报 `RunArchiveStore` / `ArchivedRunRecord` 未定义
- 触发条件：
  - PowerShell 脚本未显式检查 native command 非零退出码
  - 本机 SDK 存在但没有 `ndk/<version>`
  - `hubmobile/go.mod` 的本地 replace 命中了不兼容的 `MyFlowHub-Server`
- 关键词：
  - `build_aar.ps1`
  - `app/libs/myflowhub.aar`
  - `no usable NDK`
  - `ANDROID_NDK_HOME`
  - `gomobile bind failed`
  - `RunArchiveStore`
- 快速检查：
  - 查看 `C:\\Users\\HelloWorld\\AppData\\Local\\Android\\Sdk\\ndk\\26.1.10909125` 是否存在
  - 执行 `Get-Item app/libs/myflowhub.aar`
  - 如果错误栈落在 `worktrees\\MyFlowHub-Server\\modules\\defaultset`，转查 `android-hubmobile-local-replace.md`

## 关键设计决策与权衡
- 决策：先修 `build_aar.ps1` 的失败传播，不直接把“自动下载 NDK”写进仓库脚本
  - 原因：自动下载副作用大、耗时长，且不适合放进默认构建脚本；先把错误暴露准确，收益更直接
- 决策：本轮没有把“临时去掉本地 Server replace 再构建 AAR”固化进脚本
  - 原因：虽然这是一个可能方向，但 `gomobile bind` 在该路径下出现了额外的 `go mod tidy` 临时模块问题，行为仍不够稳定；在未验证清楚前不应默认落地
- 决策：将“缺 NDK”和“本地 Server replace 漂移”拆成两个 lessons
  - 原因：两者的排查顺序和修复动作不同，拆开更利于后续检索

## 测试与验证方式 / 结果
- 已执行：
  - `.\\scripts\\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar`
    - 结果：在 NDK 缺失时，脚本明确失败并提示安装 `ndk;26.1.10909125`
  - 安装本机 Android command-line tools 与 `sdkmanager --install "platforms;android-26" "platforms;android-34" "build-tools;34.0.0" "ndk;26.1.10909125"`
    - 结果：本机 SDK 已具备可用 NDK
  - 再次执行 `.\\scripts\\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar`
    - 结果：脚本明确失败在 `gomobile bind`，并暴露 `worktrees\\MyFlowHub-Server\\modules\\defaultset` 的 API 漂移错误，不再伪装成功
  - `ANDROID_HOME=C:\\Users\\HelloWorld\\AppData\\Local\\Android\\Sdk`
  - `ANDROID_SDK_ROOT=C:\\Users\\HelloWorld\\AppData\\Local\\Android\\Sdk`
  - `.\\gradlew.bat :app:assembleDebug`
    - 结果：通过
- 探索性验证：
  - 试过临时去掉 `hubmobile/go.mod` 中的本地 replace，再跑 `gomobile bind`
  - 结果：当前 `gomobile` 路径会额外触发 `go mod tidy failed: missing module declaration`，因此未将该 fallback 固化到仓库脚本

## 3.3 Code Review 结论（强制项）
- 需求覆盖：通过（已把 Windows 本地 AAR 构建的假成功修正为显式失败）
- 架构合理性：通过（与现有 Bash/CI 行为对齐，没有扩大到 app 运行时代码）
- 性能风险：通过（仅在缺工具时才安装；目录探测开销很小）
- 可读性与一致性：通过（helper 命名与 Bash 脚本职责对应，错误路径明确）
- 可扩展性与配置化：通过（后续可继续扩显式 SDK/NDK 参数，而不影响当前接口）
- 稳定性与安全：通过（失败快速退出，不再静默继续）
- 测试覆盖情况：部分通过（脚本已在真实环境下验证两段失败路径；AAR 最终成功产出仍被外部依赖仓漂移阻塞）
- 子Agent治理与审计：通过（未使用子 Agent）

## 潜在影响与回滚方案
- 潜在影响：
  - Windows 本地执行 `build_aar.ps1` 时，错误会比以前更早暴露
  - 脚本会优先尝试标准 SDK 目录 `%LOCALAPPDATA%\\Android\\Sdk`
  - 本机已安装 command-line tools 与 `ndk;26.1.10909125`，后续 Android 构建可直接复用
  - AAR 仍未最终产出，当前 File 真机闭环继续受外部 `MyFlowHub-Server` 依赖漂移阻塞
- 回滚方案：
  - 回退以下文件：
    - `scripts/build_aar.ps1`
    - `docs/m0_smoke.md`
    - `docs/lessons/android-build-aar-windows.md`
    - `docs/lessons/README.md`
    - `docs/change/2026-04-03_android-windows-aar-build-closure-v1.md`
  - 如需回退本机环境，可使用 `sdkmanager --uninstall "ndk;26.1.10909125"` 删除 NDK（非仓库内改动）

## 子Agent执行轨迹
- 未使用子 Agent
