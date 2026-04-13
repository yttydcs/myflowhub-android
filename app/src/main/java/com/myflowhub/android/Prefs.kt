package com.myflowhub.android
// Context: This file supports the Android app or gomobile host flow around Prefs.

import android.content.Context
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val PREFS = "hub_prefs"
    private const val KEY_ADDR = "addr"
    private const val KEY_PARENT = "parent"
    private const val KEY_RFCOMM_ENABLE = "rfcomm_enable"
    private const val KEY_RFCOMM_UUID = "rfcomm_uuid"
    private const val KEY_RFCOMM_INSECURE = "rfcomm_insecure"
    // Keep the running snapshot separate from the editable Hub form state.
    private const val KEY_HUB_RUN_DESIRED = "hub_run_desired"
    private const val KEY_HUB_RUN_SNAPSHOT = "hub_run_snapshot"

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
    private const val KEY_VARSTORE_SUB_PREFS = "varstore_sub_prefs"

    private const val KEY_TOPICBUS_SUBS = "topicbus.subs"
    private const val KEY_TOPICBUS_MAX_EVENTS = "topicbus.max_events"
    private const val DEFAULT_TOPICBUS_MAX_EVENTS = 500

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
        val rfcommUuid = (sp.getString(KEY_RFCOMM_UUID, BluetoothRfcommSupport.defaultServiceUuid()) ?: "")
            .trim()
            .ifBlank { BluetoothRfcommSupport.defaultServiceUuid() }
        val identity = ensureIdentity(context)
        return HubConfig(
            addr = addr,
            parentAddr = parent,
            selfId = identity.hubSelfId,
            rfcommListenEnabled = sp.getBoolean(KEY_RFCOMM_ENABLE, false),
            rfcommServiceUuid = rfcommUuid,
            rfcommInsecure = sp.getBoolean(KEY_RFCOMM_INSECURE, false),
        )
    }

    fun save(context: Context, cfg: HubConfig) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_ADDR, cfg.addr)
            .putString(KEY_PARENT, cfg.parentAddr)
            .putString(KEY_HUB_SELF_ID, cfg.selfId)
            .putBoolean(KEY_RFCOMM_ENABLE, cfg.rfcommListenEnabled)
            .putString(KEY_RFCOMM_UUID, cfg.rfcommServiceUuid.trim())
            .putBoolean(KEY_RFCOMM_INSECURE, cfg.rfcommInsecure)
            .apply()
    }

    fun saveHubRunSnapshot(context: Context, cfg: HubConfig) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val snapshot = JSONObject()
            .put("addr", cfg.addr)
            .put("parent_addr", cfg.parentAddr)
            .put("self_id", cfg.selfId)
            .put("rfcomm_listen_enabled", cfg.rfcommListenEnabled)
            .put("rfcomm_service_uuid", cfg.rfcommServiceUuid.trim())
            .put("rfcomm_insecure", cfg.rfcommInsecure)
            .toString()
        sp.edit()
            .putString(KEY_HUB_RUN_SNAPSHOT, snapshot)
            .apply()
    }

    fun loadHubRunSnapshot(context: Context): HubConfig? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_HUB_RUN_SNAPSHOT, null) ?: return null
        val obj = JSONObject(raw)
        val rfcommUuid = obj.optString("rfcomm_service_uuid", "")
            .trim()
            .ifBlank { BluetoothRfcommSupport.defaultServiceUuid() }
        return HubConfig(
            addr = obj.optString("addr", ":9000"),
            parentAddr = obj.optString("parent_addr", ""),
            selfId = obj.optString("self_id", ""),
            rfcommListenEnabled = obj.optBoolean("rfcomm_listen_enabled", false),
            rfcommServiceUuid = rfcommUuid,
            rfcommInsecure = obj.optBoolean("rfcomm_insecure", false),
        )
    }

    fun setHubDesiredRunning(context: Context, desired: Boolean) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean(KEY_HUB_RUN_DESIRED, desired)
            .apply()
    }

    fun isHubDesiredRunning(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_HUB_RUN_DESIRED, false)
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

    data class VarStoreSubPref(
        val name: String,
        val owner: Long,
        val subscribed: Boolean,
    )

    data class TopicBusPrefs(
        val topics: List<String>,
        val maxEvents: Int,
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

    fun loadVarStoreSubPrefs(context: Context): List<VarStoreSubPref> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = (sp.getString(KEY_VARSTORE_SUB_PREFS, "") ?: "").trim()
        if (raw.isBlank()) return emptyList()

        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val seen = hashSetOf<String>()
        val out = mutableListOf<VarStoreSubPref>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name", "").trim()
            val owner = obj.optLong("owner", 0)
            if (name.isBlank() || owner <= 0) continue
            val id = "${name}#${owner}"
            if (!seen.add(id)) continue
            val subscribed = obj.optBoolean("subscribed", false)
            out.add(VarStoreSubPref(name = name, owner = owner, subscribed = subscribed))
        }
        out.sortWith(compareBy({ it.owner }, { it.name }))
        return out
    }

    fun saveVarStoreSubPrefs(context: Context, prefs: List<VarStoreSubPref>) {
        val seen = hashSetOf<String>()
        val arr = JSONArray()
        for (pref in prefs) {
            val name = pref.name.trim()
            val owner = pref.owner
            if (name.isBlank() || owner <= 0) continue
            val id = "${name}#${owner}"
            if (!seen.add(id)) continue
            val obj = JSONObject()
            obj.put("name", name)
            obj.put("owner", owner)
            obj.put("subscribed", pref.subscribed)
            arr.put(obj)
        }
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_VARSTORE_SUB_PREFS, arr.toString()).apply()
    }

    fun loadTopicBusPrefs(context: Context): TopicBusPrefs {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = (sp.getString(KEY_TOPICBUS_SUBS, "") ?: "").trim()
        val maxEvents = runCatching { sp.getInt(KEY_TOPICBUS_MAX_EVENTS, DEFAULT_TOPICBUS_MAX_EVENTS) }
            .getOrDefault(DEFAULT_TOPICBUS_MAX_EVENTS)
            .coerceAtLeast(1)

        if (raw.isBlank()) {
            return TopicBusPrefs(topics = emptyList(), maxEvents = maxEvents)
        }

        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return TopicBusPrefs(topics = emptyList(), maxEvents = maxEvents)
        val seen = hashSetOf<String>()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val topic = arr.optString(i, "").trim()
            if (topic.isBlank() || !seen.add(topic)) continue
            out.add(topic)
        }
        return TopicBusPrefs(topics = out, maxEvents = maxEvents)
    }

    fun saveTopicBusPrefs(context: Context, topics: List<String>, maxEvents: Int) {
        val seen = hashSetOf<String>()
        val arr = JSONArray()
        for (topic in topics) {
            val trimmed = topic.trim()
            if (trimmed.isBlank() || !seen.add(trimmed)) continue
            arr.put(trimmed)
        }
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_TOPICBUS_SUBS, arr.toString())
            .putInt(KEY_TOPICBUS_MAX_EVENTS, maxEvents.coerceAtLeast(1))
            .apply()
    }
}

