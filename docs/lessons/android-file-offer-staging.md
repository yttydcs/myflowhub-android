# Android File Offer Staging

## Summary
- Android 侧如果要复用 `myflowhub-subproto/file` 的发送端，就不能把 document picker 返回的 `content://` URI 直接交给 Go；必须先把文件复制到 `file.base_dir + dir/name` 对应的本地路径。
- 当前 `subproto/file` 在收到 `write_resp` 后，会按 `cfg.BaseDir + resp.Dir + resp.Name` 解析本地源文件并启动 `sendFileData`。如果这条镜像路径不存在，控制面虽然成功，但数据面不会真正开始。
- 现有 `hubmobile` runtime 仍是单个 `file.base_dir` 配置；在没有任务系统前，应保持 UI 单操作串行，避免并发上传/下载切换 base dir。

## Lookup Hints
- `FileOffer`
- `write_resp`
- `sendFileData`
- `handleWriteRespLocal`
- `upload-staging`
- `expectedUploadStagePath`
- `content://`
- `file.base_dir`

## Symptoms
- Android 端点击 `Upload` 后看到成功响应，但远端迟迟收不到文件。
- 抓日志时能看到 `offer` / `write_resp`，却没有后续 DATA。
- 控制面报错 `file not found`、`offer source must be a file`，或 staging 路径为空。

## Impact
- 用户以为文件已经开始上传，但实际上只完成了控制面握手。
- 本地和远端会出现“响应成功但文件没动”的误导性状态。

## Trigger Conditions
- 直接把 Android `content://` URI 当作 Go 侧源文件。
- 本地 staging 路径没有和远端 `dir/name` 保持一致。
- 同时发起多次 upload/download，导致 runtime 的 `file.base_dir` 在会话建立前被另一操作改掉。

## Root Cause
- `myflowhub-subproto/file` 的 `handleWriteRespLocal` 不接收任意 `filePath`，而是固定调用 `resolvePaths(cfg.BaseDir, dir, name)` 解析本地源文件。
- Android document picker 返回的是 `ContentResolver` 可读的逻辑资源，不是 Go sender 直接可读的稳定文件路径。

## Investigation Trail
- 先确认 `offer` 控制请求和 `write_resp` 确实收到了。
- 再看 sender 起点，定位到 `subproto/file@v0.1.4/handler.go` 中的 `handleWriteRespLocal` 和 `sendFileData`。
- 对照当前 Android UI 流程，确认是否在调用 `go.fileOffer(...)` 前完成了本地 staging。

## Resolution
- 在 Kotlin 层先把所选文件复制到应用私有目录。
- staging 目标路径必须使用与远端一致的 `dir/name`。
- 调用 `go.fileOffer(...)` 时，把同一个 `dir/name` 和 staging 根目录作为 `localBaseDir` 传给 Go。
- UI 侧保持单操作串行，避免在会话建立期间切换 runtime base dir。

## Prevention / Guardrails
- 只要继续复用现有 `subproto/file` sender，就不要跳过 staging。
- 若未来要支持“任意本地绝对路径直接发”，应先扩展 sender 支持显式 `filePath`，而不是在 Android UI 层强拼。
- 在没有独立任务系统前，不要把并发多传输作为默认行为。
- 对上传入口，优先在 UI 层拦截空文件和非法文件名，避免进入“控制面成功但数据面不发”的灰区。

## Related Docs
- [2026-04-03_android-file-offer-upload-v1.md](../change/2026-04-03_android-file-offer-upload-v1.md)
- [2026-04-03_android-file-pull-download-v1.md](../change/2026-04-03_android-file-pull-download-v1.md)
- [file.md](../../../../repo/MyFlowHub-Server/docs/specs/file.md)
