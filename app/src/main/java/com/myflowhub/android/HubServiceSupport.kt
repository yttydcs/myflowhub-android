package com.myflowhub.android
// 本文件实现 Android 宿主中与 `HubServiceSupport` 相关的逻辑。

internal object HubServiceSupport {
    // 把 UI 输入归一化成真正运行时配置，补齐默认监听地址、RFCOMM UUID 和 workDir。
    fun runtimeConfig(cfg: HubConfig, workDir: String): HubConfig {
        return cfg.copy(
            addr = cfg.addr.ifBlank { ":9000" },
            parentAddr = cfg.parentAddr.trim(),
            selfId = cfg.selfId.trim(),
            rfcommServiceUuid = cfg.rfcommServiceUuid
                .trim()
                .ifBlank { BluetoothRfcommSupport.defaultServiceUuid() },
            workDir = workDir,
        )
    }

    // 只有用户仍然期望保持运行时，才尝试从快照恢复启动配置。
    fun restoreConfig(snapshot: HubConfig?, desiredRunning: Boolean, workDir: String): HubConfig? {
        if (!desiredRunning || snapshot == null) {
            return null
        }
        return runtimeConfig(snapshot, workDir)
    }

    // 生成前台通知里的短文本，把运行状态和关键错误压缩成一行。
    fun notificationText(state: HubState): String {
        val error = summarizeError(state.lastError)
        if (!state.running) {
            return if (error.isBlank()) "Stopped" else "Stopped: $error"
        }

        val parts = mutableListOf("Running")
        if (state.nodeId.isNotBlank()) {
            parts += "node ${state.nodeId}"
        }
        if (state.parentConnected) {
            parts += "parent connected"
        } else if (error.isNotBlank()) {
            parts += error
        }
        return parts.joinToString(" | ")
    }

    // 通知区域只保留短错误摘要，避免长堆栈把状态文本挤爆。
    private fun summarizeError(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return ""
        }
        return if (trimmed.length <= 72) trimmed else trimmed.take(69) + "..."
    }
}
