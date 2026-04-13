package com.myflowhub.android
// Context: This file supports the Android app or gomobile host flow around HubState.

data class HubState(
    val running: Boolean = false,
    val nodeId: String = "",
    val parentConnected: Boolean = false,
    val lastError: String = "",
)

