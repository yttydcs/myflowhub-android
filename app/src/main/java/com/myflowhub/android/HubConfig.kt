package com.myflowhub.android
// 本文件实现 Android 宿主中与 `HubConfig` 相关的逻辑。

data class HubConfig(
    val addr: String,
    val parentAddr: String,
    val selfId: String,
    val rfcommListenEnabled: Boolean = false,
    val rfcommServiceUuid: String = BluetoothRfcommSupport.defaultServiceUuid(),
    val rfcommInsecure: Boolean = false,
    val workDir: String = "",
)
