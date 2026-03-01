package com.myflowhub.android

import android.content.Context
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val PREFS = "hub_prefs"
    private const val KEY_ADDR = "addr"
    private const val KEY_PARENT = "parent"

    // Legacy key: before identity split, Hub SelfID and UI DeviceID shared the same storage.
    private const val KEY_SELF_ID_LEGACY = "self_id"

    // New keys: keep Hub identity and UI identity independent.
    private const val KEY_HUB_SELF_ID = "hub_self_id"
    private const val KEY_UI_DEVICE_ID = "ui_device_id"

    private const val KEY_TARGET_ADDR = "target_addr"
    private const val KEY_AUTH_NODE_ID = "auth_node_id"
    private const val KEY_AUTH_HUB_ID = "auth_hub_id"
    private const val KEY_AUTH_ROLE = "auth_role"

    private const val KEY_VARSTORE_WATCH_LIST = "varstore_watch_list"

    data class IdentityMigration(
        val legacyId: String,
        val hubSelfId: String,
        val uiDeviceId: String,
    )

    data class IdentityEnsureResult(
        val hubSelfId: String,
        val uiDeviceId: String,
        val migration: IdentityMigration? = null,
    )

    fun ensureIdentity(context: Context): IdentityEnsureResult {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val existingHub = (sp.getString(KEY_HUB_SELF_ID, "") ?: "").trim()
        val existingUi = (sp.getString(KEY_UI_DEVICE_ID, "") ?: "").trim()
        val legacy = (sp.getString(KEY_SELF_ID_LEGACY, "") ?: "").trim()

        val hasHub = existingHub.isNotBlank()
        val hasUi = existingUi.isNotBlank()

        // Fast path: both identities present and already distinct.
        if (hasHub && hasUi && existingHub != existingUi) {
            return IdentityEnsureResult(hubSelfId = existingHub, uiDeviceId = existingUi)
        }

        val seed = when {
            hasHub -> existingHub
            hasUi -> existingUi
            legacy.isNotBlank() -> legacy
            else -> UUID.randomUUID().toString()
        }
        val base = deriveBaseId(seed)
        val newHub = "${base}-hub"
        val newUi = "${base}-ui"

        val isNewInstall = !hasHub && !hasUi && legacy.isBlank()
        val legacyShown = when {
            legacy.isNotBlank() -> legacy
            seed.isNotBlank() -> seed
            else -> ""
        }
        val migration = if (!isNewInstall) {
            IdentityMigration(
                legacyId = legacyShown,
                hubSelfId = newHub,
                uiDeviceId = newUi,
            )
        } else {
            null
        }

        sp.edit()
            .putString(KEY_HUB_SELF_ID, newHub)
            .putString(KEY_UI_DEVICE_ID, newUi)
            .apply {
                if (migration != null) {
                    remove(KEY_AUTH_NODE_ID)
                    remove(KEY_AUTH_HUB_ID)
                    remove(KEY_AUTH_ROLE)
                }
            }
            .apply()

        return IdentityEnsureResult(hubSelfId = newHub, uiDeviceId = newUi, migration = migration)
    }

    private fun deriveBaseId(seed: String): String {
        var base = seed.trim()
        base = when {
            base.endsWith("-hub") -> base.removeSuffix("-hub")
            base.endsWith("-ui") -> base.removeSuffix("-ui")
            else -> base
        }
        base = base.trim().trimEnd('-')
        if (base.isBlank()) {
            base = UUID.randomUUID().toString()
        }
        return base
    }

    fun load(context: Context): HubConfig {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val addr = sp.getString(KEY_ADDR, ":9000") ?: ":9000"
        val parent = sp.getString(KEY_PARENT, "") ?: ""
        val identity = ensureIdentity(context)
        return HubConfig(addr = addr, parentAddr = parent, selfId = identity.hubSelfId)
    }

    fun save(context: Context, cfg: HubConfig) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_ADDR, cfg.addr)
            .putString(KEY_PARENT, cfg.parentAddr)
            .putString(KEY_HUB_SELF_ID, cfg.selfId)
            .apply()
    }

    data class ClientConfig(
        val targetAddr: String,
        val deviceId: String,
        val nodeId: String,
        val hubId: String,
        val role: String,
    )

    data class VarStoreWatchKey(
        val name: String,
        val owner: Long,
    )

    fun loadClient(context: Context): ClientConfig {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val identity = ensureIdentity(context)
        val deviceId = identity.uiDeviceId
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
            .putString(KEY_UI_DEVICE_ID, cfg.deviceId)
            .putString(KEY_AUTH_NODE_ID, cfg.nodeId)
            .putString(KEY_AUTH_HUB_ID, cfg.hubId)
            .putString(KEY_AUTH_ROLE, cfg.role)
            .apply()
    }

    fun loadVarStoreWatchList(context: Context): List<VarStoreWatchKey> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = (sp.getString(KEY_VARSTORE_WATCH_LIST, "") ?: "").trim()
        if (raw.isBlank()) return emptyList()

        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val seen = hashSetOf<String>()
        val out = mutableListOf<VarStoreWatchKey>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name", "").trim()
            val owner = obj.optLong("owner", 0)
            if (name.isBlank() || owner <= 0) continue
            val id = "${name}#${owner}"
            if (!seen.add(id)) continue
            out.add(VarStoreWatchKey(name = name, owner = owner))
        }
        out.sortWith(compareBy({ it.owner }, { it.name }))
        return out
    }

    fun saveVarStoreWatchList(context: Context, keys: List<VarStoreWatchKey>) {
        val seen = hashSetOf<String>()
        val arr = JSONArray()
        for (key in keys) {
            val name = key.name.trim()
            val owner = key.owner
            if (name.isBlank() || owner <= 0) continue
            val id = "${name}#${owner}"
            if (!seen.add(id)) continue
            val obj = JSONObject()
            obj.put("name", name)
            obj.put("owner", owner)
            arr.put(obj)
        }
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_VARSTORE_WATCH_LIST, arr.toString()).apply()
    }
}

