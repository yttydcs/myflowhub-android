package com.myflowhub.android
// Context: This file supports the Android app or gomobile host flow around HubStateJson.

import org.json.JSONObject

object HubStateJson {
    fun parse(raw: String): HubState {
        return try {
            val obj = JSONObject(raw)
            HubState(
                running = obj.optBoolean("running", false),
                nodeId = obj.optLong("node_id", 0).let { if (it == 0L) "" else it.toString() },
                parentConnected = obj.optBoolean("parent_connected", false),
                lastError = obj.optString("last_error", ""),
            )
        } catch (t: Throwable) {
            HubState(running = false, lastError = t.message ?: t.toString())
        }
    }
}

