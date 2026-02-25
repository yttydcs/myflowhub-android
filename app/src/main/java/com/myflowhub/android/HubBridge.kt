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
        cls = Class.forName("com.myflowhub.native.Hubmobile")
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
}

