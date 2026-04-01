package com.myflowhub.android

internal object HubServiceSupport {
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

    fun restoreConfig(snapshot: HubConfig?, desiredRunning: Boolean, workDir: String): HubConfig? {
        if (!desiredRunning || snapshot == null) {
            return null
        }
        return runtimeConfig(snapshot, workDir)
    }

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

    private fun summarizeError(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return ""
        }
        return if (trimmed.length <= 72) trimmed else trimmed.take(69) + "..."
    }
}
