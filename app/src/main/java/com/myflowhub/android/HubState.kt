package com.myflowhub.android

data class HubState(
    val running: Boolean = false,
    val nodeId: String = "",
    val parentConnected: Boolean = false,
    val lastError: String = "",
)

