package com.myflowhub.android

data class HubConfig(
    val addr: String,
    val parentAddr: String,
    val selfId: String,
    val workDir: String = "",
)
