package com.myflowhub.android
// 本文件实现 Android 宿主中与 `HubState` 相关的逻辑。

data class HubState(
    val running: Boolean = false,
    val nodeId: String = "",
    val parentConnected: Boolean = false,
    val lastError: String = "",
)

