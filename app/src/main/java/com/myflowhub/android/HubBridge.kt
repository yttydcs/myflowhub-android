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

