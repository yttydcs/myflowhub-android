package com.myflowhub.android
// Context: This file supports the Android app or gomobile host flow around HubConfig.

data class HubConfig(
    val addr: String,
    val parentAddr: String,
    val selfId: String,
    val rfcommListenEnabled: Boolean = false,
    val rfcommServiceUuid: String = BluetoothRfcommSupport.defaultServiceUuid(),
    val rfcommInsecure: Boolean = false,
    val workDir: String = "",
)
