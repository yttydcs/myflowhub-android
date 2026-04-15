package com.myflowhub.android
// 本文件实现 Android 宿主中与 `HubBridge` 相关的逻辑。

/**
 * Bridge interface between Android and Go hub runtime.
 *
 * AND2 will replace [StubHubBridge] with a gomobile-backed implementation.
 */
interface HubBridge {
    // 用给定配置启动 Go 侧 Hub runtime，并返回最新状态。
    fun start(config: HubConfig): HubState

    // 停止 Go 侧 Hub runtime，并返回停止后的状态快照。
    fun stop(): HubState

    // 查询当前 Hub runtime 状态，不改变运行状态。
    fun status(): HubState
}

internal class HubStartBinding private constructor(
    private val method: java.lang.reflect.Method,
    internal val supportsRfcommListener: Boolean,
) {
    // 兼容新旧 gomobile Start 签名，把 Kotlin 配置拼成正确的反射调用参数。
    fun invoke(config: HubConfig): String {
        return if (supportsRfcommListener) {
            GoReflect.invokeStatic(
                method,
                config.addr,
                config.parentAddr,
                config.selfId,
                config.workDir,
                config.rfcommListenEnabled,
                config.rfcommServiceUuid,
                config.rfcommInsecure,
            ) as String
        } else {
            if (config.rfcommListenEnabled) {
                throw IllegalStateException("当前 app/libs/myflowhub.aar 过旧，不支持 RFCOMM listener 配置；请先重建 AAR。")
            }
            GoReflect.invokeStatic(method, config.addr, config.parentAddr, config.selfId, config.workDir) as String
        }
    }

    companion object {
        // 优先解析支持 RFCOMM 的新签名，找不到时退回旧版 AAR 的 Start 签名。
        fun resolve(cls: Class<*>): HubStartBinding {
            val modern = runCatching {
                GoReflect.method(
                    cls,
                    "Start",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    java.lang.Boolean.TYPE,
                    String::class.java,
                    java.lang.Boolean.TYPE,
                )
            }.getOrNull()
            if (modern != null) {
                return HubStartBinding(modern, supportsRfcommListener = true)
            }

            val legacy = GoReflect.method(
                cls,
                "Start",
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
            )
            return HubStartBinding(legacy, supportsRfcommListener = false)
        }
    }
}

class StubHubBridge : HubBridge {
    private var state = HubState(running = false)

    // 在没有 AAR 时用假状态维持 UI 可运行，便于先调宿主壳。
    override fun start(config: HubConfig): HubState {
        state = state.copy(running = true, nodeId = "(stub)", parentConnected = config.parentAddr.isNotBlank())
        return state
    }

    // 假实现只更新本地状态，不触发真实网络/进程行为。
    override fun stop(): HubState {
        state = state.copy(running = false)
        return state
    }

    // 直接返回本地 stub 状态，供界面继续轮询。
    override fun status(): HubState = state
}

class GoHubBridge : HubBridge {
    private val cls: Class<*>
    private val startBinding: HubStartBinding
    private val stopMethod: java.lang.reflect.Method
    private val statusMethod: java.lang.reflect.Method

    init {
        cls = GomobileLoader.loadHubClass()
        // Best-effort install Bluetooth RFCOMM provider (no-op if AAR is old or method missing).
        runCatching { BluetoothRfcommProvider.installIfAvailable(cls) }
        startBinding = HubStartBinding.resolve(cls)
        stopMethod = GoReflect.method(cls, "Stop")
        statusMethod = GoReflect.method(cls, "Status")
        // Optional probe to help diagnose missing AAR in runtime.
        runCatching { GoReflect.method(cls, "EnsureLinked").invoke(null) }
    }

    // 通过反射调用 gomobile 导出的 Start，并把 JSON 状态解码为 Kotlin DTO。
    override fun start(config: HubConfig): HubState =
        call { startBinding.invoke(config) }

    // 反射调用 Go Stop，统一沿用同一套错误包装。
    override fun stop(): HubState =
        call { GoReflect.invokeStatic(stopMethod) as String }

    // 反射调用 Go Status，供前台页面和 service 轮询。
    override fun status(): HubState =
        call { GoReflect.invokeStatic(statusMethod) as String }

    // 把 gomobile 返回的 JSON 和异常统一收敛为 HubState，避免 UI 层到处 try/catch。
    private fun call(fn: () -> String): HubState {
        return try {
            HubStateJson.parse(fn())
        } catch (t: Throwable) {
            HubState(running = false, lastError = t.message ?: t.toString())
        }
    }
}

