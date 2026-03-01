# TODO - Android：修复 Android 15 前台服务崩溃 + gomobile 反射兼容（v0.1.3）

本 workflow 以 `plan.md` 为唯一计划文档；此文件仅保留快速 Checklist。

- [ ] ANDFIX-1 Manifest：声明 `foregroundServiceType=dataSync` + 权限
- [ ] ANDFIX-2 HubService：带 type 的 `startForeground`
- [ ] ANDFIX-3 Kotlin：gomobile 反射方法名大小写兼容
- [ ] ANDFIX-4 本地构建 + 真机冒烟（Android 15）
- [ ] ANDFIX-5 Code Review
- [ ] ANDFIX-6 `docs/change` 归档
- [ ] ANDFIX-7 打 tag `v0.1.3` 触发 release

