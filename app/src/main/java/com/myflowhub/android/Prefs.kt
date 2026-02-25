package com.myflowhub.android

import android.content.Context
import java.util.UUID

object Prefs {
    private const val PREFS = "hub_prefs"
    private const val KEY_ADDR = "addr"
    private const val KEY_PARENT = "parent"
    private const val KEY_SELF_ID = "self_id"

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
}

