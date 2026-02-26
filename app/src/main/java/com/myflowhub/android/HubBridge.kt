package com.myflowhub.android

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
    private val startMethod: java.lang.reflect.Method
    private val stopMethod: java.lang.reflect.Method
    private val statusMethod: java.lang.reflect.Method

    init {
        cls = loadHubClass()
        startMethod = cls.getMethod("Start", String::class.java, String::class.java, String::class.java, String::class.java)
        stopMethod = cls.getMethod("Stop")
        statusMethod = cls.getMethod("Status")
        // Optional probe to help diagnose missing AAR in runtime.
        runCatching { cls.getMethod("EnsureLinked").invoke(null) }
    }

    override fun start(config: HubConfig): HubState =
        call { startMethod.invoke(null, config.addr, config.parentAddr, config.selfId, config.workDir) as String }

    override fun stop(): HubState =
        call { stopMethod.invoke(null) as String }

    override fun status(): HubState =
        call { statusMethod.invoke(null) as String }

    private fun call(fn: () -> String): HubState {
        return try {
            HubStateJson.parse(fn())
        } catch (t: Throwable) {
            HubState(running = false, lastError = t.message ?: t.toString())
        }
    }

    private fun loadHubClass(): Class<*> {
        val candidates = listOf(
            // Current default (scripts/build_aar.*): -javapkg com.myflowhub.gomobile + Go pkg "hubmobile"
            "com.myflowhub.gomobile.hubmobile.Hubmobile",
            "com.myflowhub.gomobile.Hubmobile",
            // Backward-compatible fallbacks for older AARs.
            "com.myflowhub.native.hubmobile.Hubmobile",
            "com.myflowhub.native.Hubmobile",
        )

        var lastError: Throwable? = null
        for (fqcn in candidates) {
            try {
                return Class.forName(fqcn)
            } catch (t: Throwable) {
                lastError = t
            }
        }

        throw IllegalStateException(
            "未找到 gomobile 生成类；请确认 app/libs/myflowhub.aar 已打包进 APK。已尝试：${candidates.joinToString()}",
            lastError,
        )
    }
}

