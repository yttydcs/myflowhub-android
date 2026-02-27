package com.myflowhub.android

import android.content.Context
import java.util.UUID

object Prefs {
    private const val PREFS = "hub_prefs"
    private const val KEY_ADDR = "addr"
    private const val KEY_PARENT = "parent"
    private const val KEY_SELF_ID = "self_id"

    private const val KEY_TARGET_ADDR = "target_addr"
    private const val KEY_AUTH_NODE_ID = "auth_node_id"
    private const val KEY_AUTH_HUB_ID = "auth_hub_id"
    private const val KEY_AUTH_ROLE = "auth_role"

    fun load(context: Context): HubConfig {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val addr = sp.getString(KEY_ADDR, ":9000") ?: ":9000"
        val parent = sp.getString(KEY_PARENT, "") ?: ""
        var selfId = sp.getString(KEY_SELF_ID, "") ?: ""
        if (selfId.isBlank()) {
            selfId = UUID.randomUUID().toString()
            sp.edit().putString(KEY_SELF_ID, selfId).apply()
        }
        return HubConfig(addr = addr, parentAddr = parent, selfId = selfId)
    }

    fun save(context: Context, cfg: HubConfig) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_ADDR, cfg.addr)
            .putString(KEY_PARENT, cfg.parentAddr)
            .putString(KEY_SELF_ID, cfg.selfId)
            .apply()
    }

    data class ClientConfig(
        val targetAddr: String,
        val deviceId: String,
        val nodeId: String,
        val hubId: String,
        val role: String,
    )

    fun loadClient(context: Context): ClientConfig {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val deviceId = (sp.getString(KEY_SELF_ID, "") ?: "").ifBlank {
            // Ensure HubConfig initialization also seeds deviceId.
            load(context).selfId
        }
        return ClientConfig(
            targetAddr = sp.getString(KEY_TARGET_ADDR, "127.0.0.1:9000") ?: "127.0.0.1:9000",
            deviceId = deviceId,
            nodeId = sp.getString(KEY_AUTH_NODE_ID, "") ?: "",
            hubId = sp.getString(KEY_AUTH_HUB_ID, "") ?: "",
            role = sp.getString(KEY_AUTH_ROLE, "") ?: "",
        )
    }

    fun saveClient(context: Context, cfg: ClientConfig) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_TARGET_ADDR, cfg.targetAddr)
            .putString(KEY_SELF_ID, cfg.deviceId)
            .putString(KEY_AUTH_NODE_ID, cfg.nodeId)
            .putString(KEY_AUTH_HUB_ID, cfg.hubId)
            .putString(KEY_AUTH_ROLE, cfg.role)
            .apply()
    }
}

