# TODO - Android：VarStore 对齐 Win（Node Vars + 快捷 Watch + 订阅偏好恢复）

## 项目目标与当前状态
- 目标：
  - Android VarStore 对齐 Win VarPool 的关键能力差异（第 9 项）。
  - 支持“查询某节点变量名列表”与“从查询结果快捷加入 Watch”。
  - 补齐订阅偏好持久化与自动恢复（连接+登录后）。
- 当前状态：
  - Android 已有 `watch list` 持久化与 TopicBus 偏好持久化。
  - Android 还没有 Node Vars 对话查询入口，也没有 VarStore 订阅偏好持久化/自动恢复。

## 可执行任务清单（Checklist）

- [x] VARALIGN-1：新增 VarStore 订阅偏好持久化模型（Prefs）
  - 目标：保存/读取 `name+owner+subscribed` 目标态，作为自动恢复输入。
  - 涉及模块/文件：
    - `app/src/main/java/com/myflowhub/android/Prefs.kt`
  - 验收条件：
    - 能读写去重后的订阅偏好列表（owner>0、name 非空）。
    - 兼容旧数据（不存在时返回空）。
  - 测试点：
    - 编译通过。
    - 手工路径：订阅状态变化后重启 App，偏好仍保留。
  - 回滚点：
    - 回退 `Prefs.kt` 本任务相关新增方法和数据类。

- [x] VARALIGN-2：在 VarStoreScreen 增加 Node Vars 查询与快捷 Add Watch
  - 目标：输入 owner 节点，查询变量名列表，支持快速加入 Watch。
  - 涉及模块/文件：
    - `app/src/main/java/com/myflowhub/android/ui/VarStoreScreen.kt`
  - 验收条件：
    - 提供 `Node Vars` 入口与对话框；
    - 输入合法 owner 后可加载变量名列表；
    - 每个变量支持 “Add Watch”，重复项不可重复添加；
    - 添加后可触发一次 `getVar` 刷新当前值。
  - 测试点：
    - 未连接/未登录时错误提示正确；
    - owner 非法输入时校验生效；
    - Add Watch 后在 Watched 列表可见。
  - 回滚点：
    - 回退 `VarStoreScreen.kt` 的 Node Vars UI 与查询逻辑。

- [x] VARALIGN-3：补齐订阅偏好保存 + 自动恢复订阅（对齐 Win）
  - 目标：订阅/取消订阅时更新目标态并持久化；重启后自动恢复目标订阅。
  - 涉及模块/文件：
    - `app/src/main/java/com/myflowhub/android/ui/VarStoreScreen.kt`
    - `app/src/main/java/com/myflowhub/android/Prefs.kt`
  - 验收条件：
    - Subscribe/Unsubscribe 操作同步更新并保存订阅偏好；
    - 启动后（连接+登录）自动恢复目标订阅；
    - 自动恢复支持并发限流与失败统计提示，不阻塞 UI。
  - 测试点：
    - 手工：订阅若干变量 -> 重启 -> 自动恢复生效；
    - 手工：断网/未登录时不会崩溃，恢复逻辑可在状态恢复后重试。
  - 回滚点：
    - 回退自动恢复相关 `LaunchedEffect` 与偏好写入调用。

- [x] VARALIGN-4：构建验证 + 代码审查 + 归档
  - 目标：完成本轮质量闭环与变更归档。
  - 涉及模块/文件：
    - `docs/change/YYYY-MM-DD_android-varstore-nodevars-watch.md`
  - 验收条件：
    - `:app:assembleDebug` 通过；
    - 完成 3.3 Code Review（需求覆盖/架构/性能/可读性/扩展性/稳定性/测试）；
    - 完成 docs/change 归档。
  - 测试点：
    - `./gradlew :app:assembleDebug`
  - 回滚点：
    - 回退本分支提交或按任务粒度回滚。

## 依赖关系
- `VARALIGN-1` -> `VARALIGN-3`
- `VARALIGN-2` 与 `VARALIGN-1` 可并行，但 `VARALIGN-3` 依赖二者完成
- `VARALIGN-4` 最后执行

## 风险与注意事项
- 自动恢复订阅若并发过高可能触发瞬时失败，需并发限流（建议 4）。
- 对话框变量列表可能较大，UI 需分页/过滤避免一次性渲染过重。
- 保持改动最小化：仅 Android 本仓，不改 Server/Proto/Win。

## 当前执行状态
- 已完成：
  - VARALIGN-1
  - VARALIGN-2
  - VARALIGN-3
- 待完成：
  - 无
- 本地验证备注：
  - 使用 `ANDROID_HOME/ANDROID_SDK_ROOT=D:\\project\\MyFlowHub3\\_android-sdk` 执行 `./gradlew.bat :app:assembleDebug`，构建通过。
