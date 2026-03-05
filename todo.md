# TODO - Android：设备树跨级请求异常（10 -> 1）定位与修复

## Workflow 信息
- Repo：`MyFlowHub-Android`
- 分支：`fix/android-devices-listnodes-error`
- Worktree：`d:\project\MyFlowHub3\repo\MyFlowHub-Android\worktrees\fix-android-devices-listnodes-error\MyFlowHub-Android`
- 参考规范：`d:\project\MyFlowHub3\guide.md`

## 项目目标与当前状态
- 目标：
  - 修复 Android 端设备树请求失败时仅显示 `java.lang.reflect.InvocationTargetException` 的可观测性问题。
  - 给出 10 -> 1 跨级请求异常的可验证定位路径，避免“看不见根因”。
- 当前状态：
  - `DevicesScreen` 的错误展示直接使用 `Throwable.message`，反射调用场景会丢失真实 cause。
  - `GoClientBridge` 直接 `Method.invoke`，未统一解包 `InvocationTargetException`。

## 可执行任务清单（Checklist）

- [x] DEVTREE-1：梳理异常链路与输出规范
  - 目标：明确 `listNodes` 从 UI -> 反射 -> Go 的异常传播路径与期望错误文案。
  - 涉及模块/文件：
    - `app/src/main/java/com/myflowhub/android/GoReflect.kt`
    - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
    - `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
  - 验收条件：
    - 明确“优先展示 root cause；必要时补充 lastError”策略。
  - 测试点：
    - 人工审查异常分支逻辑覆盖（反射异常、空 message、普通异常）。
  - 回滚点：
    - 回退上述文件的异常处理改动。

- [x] DEVTREE-2：实现反射调用统一解包（核心修复）
  - 目标：在 Android 端统一处理 `InvocationTargetException`，保留真实错误上下文。
  - 涉及模块/文件：
    - `app/src/main/java/com/myflowhub/android/GoReflect.kt`
    - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - 验收条件：
    - Go 方法抛错时，UI 可拿到具体 cause 信息（如 `not found` / `timeout` / `not connected`），不再只看到 `InvocationTargetException`。
    - 不改变正常成功路径性能（仅异常路径增加处理）。
  - 测试点：
    - 人工路径：构造失败请求，确认文案包含根因。
  - 回滚点：
    - 回退 `GoReflect` 的 invoke 辅助函数与 `GoClientBridge` 调用点。

- [x] DEVTREE-3：设备树 UI 错误展示增强
  - 目标：统一 `DevicesScreen` 错误文案格式，追加可诊断信息（必要时拼接 `lastError`）。
  - 涉及模块/文件：
    - `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
  - 验收条件：
    - 设备树加载失败时可直接看到可操作错误（而非反射包装类名）。
  - 测试点：
    - 人工路径：Root Load 失败、子节点展开失败、NodeInfo/Config 失败时文案一致。
  - 回滚点：
    - 回退 `DevicesScreen.kt` 文案处理改动。

- [x] DEVTREE-4：验证、Code Review、归档
  - 目标：完成质量闭环与审计文档。
  - 涉及模块/文件：
    - `docs/change/2026-03-05_android-devices-listnodes-error.md`
  - 验收条件：
    - 关键编译/静态检查通过。
    - 完成 3.3 Code Review（需求覆盖、架构、性能、可读性、扩展性、稳定性、安全、测试）。
    - 完成 docs/change 归档。
  - 测试点：
    - `./gradlew.bat :app:assembleDebug`
  - 回滚点：
    - 回退本分支提交，或按任务粒度回滚。

## 依赖关系
- DEVTREE-1 -> DEVTREE-2 -> DEVTREE-3 -> DEVTREE-4

## 风险与注意事项
- 仅修复“错误可观测性”不会自动修复网络拓扑/认证问题；但可显著降低排障成本。
- 不能在主 repo 工作区改实现；仅在当前 worktree 变更。
- 需避免在成功路径引入额外 I/O 或重计算。

## 当前执行状态
- 已完成：DEVTREE-1、DEVTREE-2、DEVTREE-3、DEVTREE-4
- 进行中：无
- 待完成：无
