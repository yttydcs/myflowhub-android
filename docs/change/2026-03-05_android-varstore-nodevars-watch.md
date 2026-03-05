# 2026-03-05 Android：VarStore 对齐 Win（Node Vars 查询 + 快捷 Add Watch + 订阅偏好恢复）

## 变更背景 / 目标
- 背景：
  - Android VarStore 已支持 Watch 列表与基础订阅操作，但与 Win VarPool 仍有差异：
    - 缺少“按节点查询变量名并快捷加入 Watch”的入口；
    - 缺少订阅偏好（name+owner+subscribed）持久化与自动恢复。
- 目标：
  - 补齐上述差异，完成第 9 项对齐能力闭环。

## 具体变更内容

### 新增
1. VarStore 订阅偏好存储模型（Prefs）
   - 新增 key：`varstore_sub_prefs`
   - 新增数据结构：`VarStoreSubPref(name, owner, subscribed)`
   - 新增读写函数：
     - `loadVarStoreSubPrefs(context)`
     - `saveVarStoreSubPrefs(context, prefs)`

2. Node Vars 查询能力（VarStoreScreen）
   - 新增 `Node Vars` 入口按钮（位于 Watched Variables 区域）。
   - 新增 Node Variables 对话框：
     - 输入 `Owner NodeID`
     - 点击 `Load` 调用 VarStore list（owner 定向查询）
     - 支持 `Search` 过滤
     - 每条变量支持 `Add Watch`，已存在项显示 `Watched` 并禁用重复添加

3. 订阅偏好自动恢复
   - 启动后加载 watch list + sub prefs；
   - 连接且登录后，按 `desiredSubs=true` 执行并发限流恢复订阅（默认并发 4）；
   - 自动恢复结果反馈（全部成功/部分失败）。

### 修改
1. Watch 操作联动 sub prefs
   - Add/Remove/Revoke Watch 时同步维护 `desiredSubs` 和 sub prefs 持久化。

2. Subscribe/Unsubscribe 行为持久化
   - Subscribe 前记录 `desired=true` 并持久化；
   - Unsubscribe 前记录 `desired=false` 并持久化；
   - 与 Win 对齐为“目标态优先”（即便瞬时失败，目标态仍可在后续恢复中生效）。

## 对应 todo.md 任务映射
- VARALIGN-1：
  - `Prefs.kt` 增加 `VarStoreSubPref` 与读写函数。
- VARALIGN-2：
  - `VarStoreScreen.kt` 增加 Node Vars 对话框、按节点查询、Add Watch 快捷入口。
- VARALIGN-3：
  - `VarStoreScreen.kt` 增加 `desiredSubs` 持久化、自动恢复订阅与操作联动。
- VARALIGN-4：
  - 完成 `assembleDebug` 构建验证与本归档文档。

## 关键设计决策与权衡
- 复用现有协议能力，不新增后端接口：
  - 直接使用既有 `VarStoreList/VarStoreGet/VarStoreSubscribe/VarStoreUnsubscribe`，改动面最小。
- 目标态持久化优先：
  - 订阅偏好保存“期望状态”，以支持重启/重连后自动恢复。
- 性能控制：
  - 自动恢复采用并发限流（4），避免一次性并发过大导致 I/O 抖动。
  - Node Vars 列表在 UI 侧支持搜索过滤，降低定位成本。

## 测试与验证方式 / 结果
- 构建验证：
  - 命令：
    - `ANDROID_HOME=D:\\project\\MyFlowHub3\\_android-sdk`
    - `ANDROID_SDK_ROOT=D:\\project\\MyFlowHub3\\_android-sdk`
    - `./gradlew.bat :app:assembleDebug`
  - 结果：通过（`BUILD SUCCESSFUL`）。

- 功能路径（手工建议）：
  1) 连接并登录后进入 `VarStore`，点击 `Node Vars`；
  2) 输入某节点 ID，`Load` 变量列表；
  3) 对任一变量点击 `Add Watch`，确认 Watched 列表出现；
  4) 对 watch 执行 Subscribe，重启 App 后确认自动恢复订阅生效。

## 潜在影响与回滚方案
- 潜在影响：
  - 新增本地偏好项 `varstore_sub_prefs`；旧版本不会读取该 key，不影响运行。
  - 自动恢复在网络不稳定场景可能出现部分失败，会给出提示，不影响手动操作。

- 回滚方案：
  1) 回退 `VarStoreScreen.kt` 本次新增 Node Vars 与自动恢复逻辑；
  2) 回退 `Prefs.kt` 中 `VarStoreSubPref` 相关读写方法；
  3) 如需清理存量配置，可删除本地 `SharedPreferences` 中 `varstore_sub_prefs` 键。
