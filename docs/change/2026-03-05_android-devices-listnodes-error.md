# 2026-03-05 - Android 设备树跨级请求异常：反射错误解包与可观测性修复

## 变更背景 / 目标
- 背景：在拓扑 `1 -> 9 -> 10` 场景下，Android 设备树请求失败时 UI 常只显示 `java.lang.reflect.InvocationTargetException`，导致无法直接识别真实失败原因（如 `not found`、`timeout`、`not connected`）。
- 目标：
  - 去除反射包装噪声，优先暴露 root cause；
  - 在 UI 错误文案中补充 `lastError` 回退信息，降低跨节点排障成本。

## 具体变更内容

### 新增
- `app/src/main/java/com/myflowhub/android/GoReflect.kt`
  - 新增 `invokeStatic(method, args...)`：统一静态方法反射调用与 `InvocationTargetException` 解包。
  - 新增 `renderError(err, fallback)`：统一错误文案格式化（root cause + 可选 lastError）。
  - 新增 `rootCause` 工具函数：提取异常链最深 cause，避免仅展示包装异常。

### 修改
- `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
  - 所有 Go 方法调用改为统一走 `invoke/invokeString/requireString`，避免散落的 `Method.invoke`。
  - `lastError()` 增加容错（`runCatching`），避免错误回退链本身抛异常。
- `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
  - 统一使用 `toUiErrorMessage(err, go)` 生成错误文案。
  - 错误文案策略改为：root cause 优先，必要时附带 `(lastError: xxx)`。

### 删除
- 无功能删除；仅替换原有分散反射调用方式。

## plan.md/todo.md 任务映射
- DEVTREE-1（梳理异常链路与输出规范） -> 完成
- DEVTREE-2（实现反射调用统一解包） -> 完成
- DEVTREE-3（设备树 UI 错误展示增强） -> 完成
- DEVTREE-4（验证、Code Review、归档） -> 完成

## 关键设计决策与权衡（性能 / 扩展性）
- 决策：将异常解包逻辑集中在 `GoReflect`，而非在每个 UI 页面重复 `try/catch`。
  - 理由：降低重复代码与遗漏风险，未来新增 Go API 调用也自动受益。
- 性能权衡：
  - 正常成功路径仅多一次轻量 Kotlin 函数调用（常数级开销）；
  - `rootCause` 遍历与 `lastError` 回退只在异常路径触发，不影响主路径吞吐。
- 扩展点：
  - 后续若要统一上报 telemetry（错误码分类、链路标签），可直接在 `GoReflect.renderError` 或 `GoClientBridge` 辅助函数中扩展。

## 测试与验证方式 / 结果
- 构建验证：
  - 命令：`ANDROID_HOME=D:\project\MyFlowHub3\_android-sdk ANDROID_SDK_ROOT=D:\project\MyFlowHub3\_android-sdk .\gradlew.bat :app:assembleDebug`
  - 结果：`BUILD SUCCESSFUL`（两次执行均通过）。
- 手工验证建议（用于现场拓扑复测）：
  - 在 Node10 端复现 `target=1` 的失败请求；
  - 确认 UI 不再只显示 `InvocationTargetException`，而是显示具体原因（例如 `not found` / `timeout`）。

## Code Review（3.3）结论
- 需求覆盖：通过
- 架构合理性：通过（异常处理集中，调用边界清晰）
- 性能风险：通过（异常路径增强，不引入多余 I/O/N+1）
- 可读性与一致性：通过（统一调用与错误格式函数）
- 可扩展性与配置化：通过（集中入口便于后续扩展）
- 稳定性与安全：通过（反射错误可解释性提升，未扩大权限面）
- 测试覆盖情况：通过（编译验证通过；现场拓扑行为需手工回归）

## 潜在影响与回滚方案
- 潜在影响：
  - 错误文案将从“通用反射异常”变为“具体 cause + 可选 lastError”，UI 文案表现会有变化（预期）。
- 回滚方案：
  - 回滚提交中以下文件：
    - `app/src/main/java/com/myflowhub/android/GoReflect.kt`
    - `app/src/main/java/com/myflowhub/android/GoClientBridge.kt`
    - `app/src/main/java/com/myflowhub/android/ui/DevicesScreen.kt`
  - 或按任务粒度回退 DEVTREE-2/DEVTREE-3。

## 备注（基于代码路径的推断）
- 结合当前实现，`10 -> 9` 成功但 `10 -> 1` 异常，常见触发条件是“回程路由索引缺失”（如 Node10 未完成有效登录/路由注册）。
- 该结论是基于 `management` 转发与 `auth` 路由索引建立逻辑的推断，需结合现场新错误文案与日志最终确认。
