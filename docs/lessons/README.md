# Lessons

## Purpose
- 记录可复用的排障线索、复发性问题和需要优先检查的规则。

## How To Enter This Section
- 当你遇到已知症状、权限问题、平台兼容性问题或需要快速定位历史坑点时，先看这里。

## What Belongs Here
- 易复发问题
- 需要固定排查顺序的问题
- 不应只留在 `docs/change` 的经验规则

## Naming / Maintenance Rules
- 使用稳定名字，不带日期。
- 新增或更新 lesson 后，同步更新本索引。

## Current Lessons
- [android-hub-service-restart.md](android-hub-service-restart.md)
  - 症状：Android Hub 启动后切到后台，服务被系统重建后看起来“不再工作”或无法恢复
- [android-hubmobile-local-replace.md](android-hubmobile-local-replace.md)
  - 症状：本地 `hubmobile` 执行 `go test` / `gomobile bind` 时出现 `protocol/stream`、`go mod tidy` 或 `gobind` 相关错误
- [android-rfcomm-permission.md](android-rfcomm-permission.md)
  - 症状：Android 12+ 下 `bt+rfcomm://...` 连接/父链无法使用，或出现蓝牙权限 / `SecurityException` 类错误
