package com.myflowhub.android
// Context: This file supports the Android app or gomobile host flow around HubBridge.

/**
 * Bridge interface between Android and Go hub runtime.
 *
 * AND2 will replace [StubHubBridge] with a gomobile-backed implementation.
 */
interface HubBridge {
    fun start(config: HubConfig): HubState
    fun stop(): HubState
    fun status(): HubState
}

internal class HubStartBinding private constructor(
    private val method: java.lang.reflect.Method,
    internal val supportsRfcommListener: Boolean,
) {
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

    override fun start(config: HubConfig): HubState {
        state = state.copy(running = true, nodeId = "(stub)", parentConnected = config.parentAddr.isNotBlank())
        return state
    }

    override fun stop(): HubState {
        state = state.copy(running = false)
        return state
    }

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

    override fun start(config: HubConfig): HubState =
        call { startBinding.invoke(config) }

    override fun stop(): HubState =
        call { GoReflect.invokeStatic(stopMethod) as String }

    override fun status(): HubState =
        call { GoReflect.invokeStatic(statusMethod) as String }

    private fun call(fn: () -> String): HubState {
        return try {
            HubStateJson.parse(fn())
        } catch (t: Throwable) {
            HubState(running = false, lastError = t.message ?: t.toString())
        }
    }
}

