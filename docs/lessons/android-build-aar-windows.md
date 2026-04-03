# Android Build AAR on Windows

## Summary
- Windows 下的 `scripts/build_aar.ps1` 如果不显式处理 native command 退出码，`gomobile init` / `gomobile bind` 失败时可能仍然打印 `Done`，造成“看起来成功但 `app/libs/myflowhub.aar` 不存在”的假象。
- 当前脚本已补齐：自动探测 `ANDROID_HOME` / `ANDROID_SDK_ROOT` / `%LOCALAPPDATA%\\Android\\Sdk`、探测已安装 NDK、按 `hubmobile/go.mod` 版本安装 `gomobile/gobind`，并在任一关键命令失败时快速退出。
- 若 NDK 已补齐，但 AAR 构建继续报 `MyFlowHub-Server` 编译错误，这属于第二层阻塞，应转到 `android-hubmobile-local-replace.md` 继续排查。

## Lookup Hints
- `build_aar.ps1`
- `app/libs/myflowhub.aar`
- `no usable NDK`
- `Android NDK`
- `sdkmanager`
- `gomobile bind failed`
- `gomobile init`
- `RunArchiveStore`
- `ArchivedRunRecord`

## Symptoms
- 执行 `.\\scripts\\build_aar.ps1 ...` 后看到 `Done`，但 `app/libs/myflowhub.aar` 实际不存在。
- 执行 AAR 构建时出现 `no usable NDK`、`未找到 Android NDK`、`ANDROID_NDK_HOME` 相关错误。
- NDK 装好后，`gomobile bind` 又报 `MyFlowHub-Server/modules/defaultset/... undefined: flowhandler.RunArchiveStore` 一类错误。

## Impact
- Android APK 可能继续沿用 stub 或旧 AAR，设备运行时拿不到最新的 Go 导出方法。
- 用户容易误判为“脚本成功，只是 APK 没刷新”，实际问题在 AAR 根本没产出。

## Trigger Conditions
- PowerShell 脚本未显式检查原生命令非零退出码。
- 本机只有 Android SDK，没有 `ndk/<version>`。
- `hubmobile/go.mod` 继续通过本地 `replace` 命中一个与当前 subproto 版本不兼容的 `MyFlowHub-Server` 源码树。

## Root Cause
- PowerShell 默认不会像 Bash `set -e` 一样自动把 native command 非零退出码变成脚本失败。
- `gomobile bind` 的 Android 产物依赖可用 NDK；只有 SDK 不够。
- Android 仓的 `hubmobile` 默认仍按本地 `replace` 依赖 `MyFlowHub-Server`，因此第二层失败可能来自外部仓源码漂移，而不是当前 Android 代码。

## Investigation Trail
- 先看脚本是否真的报错退出，而不是只看 `Done`。
- 再确认 `C:\\Users\\HelloWorld\\AppData\\Local\\Android\\Sdk\\ndk\\<version>` 是否存在。
- 若 NDK 已存在，再看 `gomobile bind` 的报错是否落在 `MyFlowHub-Server` 源码路径。
- 如果错误来自 `modules/defaultset`、`RunArchiveStore`、`ArchivedRunRecord` 等 server 代码，转查 `android-hubmobile-local-replace.md`。

## Resolution
- 若缺少 command-line tools：
  - 下载 Android command-line tools，并安装到 `SDK\\cmdline-tools\\latest`
- 若缺少 NDK：
  - 使用 `sdkmanager --install "ndk;26.1.10909125"` 安装
- 重新执行：
  - `.\\scripts\\build_aar.ps1 -Target android/arm64 -JavaPkg com.myflowhub.gomobile -OutFile app/libs/myflowhub.aar`
- 构建后务必确认：
  - `Get-Item app/libs/myflowhub.aar`
- 若 NDK 已装但错误转为 `MyFlowHub-Server` 编译失败：
  - 按 `docs/lessons/android-hubmobile-local-replace.md` 继续排查本地 replace / 外部依赖漂移

## Prevention / Guardrails
- 判断 AAR 构建是否成功时，始终同时看“命令退出码 + AAR 文件是否真实存在”。
- 只要在 Windows 上改过 `hubmobile` 导出，就先跑一次 `build_aar.ps1`，不要只看 `assembleDebug`。
- 遇到“装好 NDK 后又出现 server 编译错误”时，不要继续怀疑 Android UI 代码，优先检查本地 `replace` 链。

## Related Docs
- [2026-04-03_android-file-offer-upload-v1.md](../change/2026-04-03_android-file-offer-upload-v1.md)
- [android-hubmobile-local-replace.md](android-hubmobile-local-replace.md)
- [m0_smoke.md](../m0_smoke.md)
