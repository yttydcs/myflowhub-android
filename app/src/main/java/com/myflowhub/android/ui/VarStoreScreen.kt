package com.myflowhub.android.ui
// Context: This file supports the Android app or gomobile host flow around VarStoreScreen.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myflowhub.android.GoClientBridge
import com.myflowhub.android.Prefs
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class VarKey(
    val name: String,
    val owner: Long,
)

private data class VarResp(
    val code: Int,
    val msg: String,
    val name: String,
    val value: String,
    val owner: Long,
    val visibility: String,
    val type: String,
    val names: List<String>,
)

@Stable
private class VarValueState(
    owner: Long,
) {
    var value by mutableStateOf("")
    var owner by mutableStateOf(owner)
    var visibility by mutableStateOf("")
    var kind by mutableStateOf("")

    var subscribed by mutableStateOf(false)
    var subKnown by mutableStateOf(false)

    var loading by mutableStateOf(false)
    var loadError by mutableStateOf("")

    var lastUpdated by mutableStateOf("")
}

@Stable
private class VarStoreState {
    var targetId by mutableStateOf("")
    var selfNodeId by mutableStateOf(0L)
    var defaultTargetId by mutableStateOf(0L)

    val keys = mutableStateListOf<VarKey>()
    val data = mutableStateMapOf<String, VarValueState>()

    var lastFrameAt by mutableStateOf("")
}

private val varNameRegex = Regex("^[A-Za-z0-9_]+$")

private fun nowIso(): String = Instant.now().toString()

private fun parseVarResp(raw: String): VarResp = parseVarResp(JSONObject(raw))

private fun parseVarResp(obj: JSONObject): VarResp {
    val namesArr = obj.optJSONArray("names") ?: JSONArray()
    val names = buildList {
        for (i in 0 until namesArr.length()) {
            val name = namesArr.optString(i, "").trim()
            if (name.isNotBlank()) add(name)
        }
    }
    return VarResp(
        code = obj.optInt("code", 0),
        msg = obj.optString("msg", "").trim(),
        name = obj.optString("name", "").trim(),
        value = obj.optString("value", ""),
        owner = obj.optLong("owner", 0),
        visibility = obj.optString("visibility", "").trim(),
        type = obj.optString("type", "").trim(),
        names = names,
    )
}

@Composable
fun VarStoreScreen(
    modifier: Modifier = Modifier,
    go: GoClientBridge?,
    goError: String,
    cfg: Prefs.ClientConfig,
    ui: UiNotifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val state = remember { VarStoreState() }
    val desiredSubs = remember { mutableMapOf<String, Boolean>() }

    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("") }
    var opJob by remember { mutableStateOf<Job?>(null) }
    var opSeq by remember { mutableStateOf(0) }

    var addMineOpen by remember { mutableStateOf(false) }
    var addMineName by remember { mutableStateOf("") }
    var addMineValue by remember { mutableStateOf("") }
    var addMineVisibility by remember { mutableStateOf("public") }
    var addMineKind by remember { mutableStateOf("string") }

    var addWatchOpen by remember { mutableStateOf(false) }
    var addWatchName by remember { mutableStateOf("") }
    var addWatchOwner by remember { mutableStateOf("") }

    var editOpen by remember { mutableStateOf(false) }
    var editKey by remember { mutableStateOf<VarKey?>(null) }
    var editValue by remember { mutableStateOf("") }
    var editVisibility by remember { mutableStateOf("public") }
    var editKind by remember { mutableStateOf("string") }

    var nodeVarsOpen by remember { mutableStateOf(false) }
    var nodeVarsOwnerInput by remember { mutableStateOf("") }
    var nodeVarsQuery by remember { mutableStateOf("") }
    var nodeVarsLoading by remember { mutableStateOf(false) }
    var nodeVarsError by remember { mutableStateOf("") }
    val nodeVarsNames = remember { mutableStateListOf<String>() }

    var restoreEpoch by remember { mutableStateOf(0) }
    var lastAutoRestoreKey by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }

    fun keyId(key: VarKey): String = "${key.name}#${key.owner}"

    fun normalizeKey(input: VarKey): VarKey {
        val name = input.name.trim()
        val owner = input.owner
        return VarKey(name = name, owner = owner)
    }

    fun upsertKey(input: VarKey): VarKey {
        val key = normalizeKey(input)
        if (key.name.isBlank() || key.owner <= 0) return key
        for (existing in state.keys) {
            if (existing.name == key.name && existing.owner == key.owner) {
                return existing
            }
        }
        state.keys.add(key)
        return key
    }

    fun removeLocalKey(input: VarKey) {
        val key = normalizeKey(input)
        if (key.name.isBlank() || key.owner <= 0) return
        state.keys.removeAll { it.name == key.name && it.owner == key.owner }
        state.data.remove(keyId(key))
        desiredSubs.remove(keyId(key))
    }

    fun touchRestoreEpoch() {
        restoreEpoch += 1
    }

    fun ensureSubPrefDefaults() {
        for (key in state.keys) {
            val normalized = normalizeKey(key)
            if (normalized.name.isBlank() || normalized.owner <= 0) continue
            if (state.selfNodeId > 0 && normalized.owner == state.selfNodeId) continue
            desiredSubs.putIfAbsent(keyId(normalized), false)
        }
    }

    fun loadSubPrefs() {
        desiredSubs.clear()
        val prefs = Prefs.loadVarStoreSubPrefs(context)
        for (pref in prefs) {
            val name = pref.name.trim()
            val owner = pref.owner
            if (name.isBlank() || owner <= 0 || !varNameRegex.matches(name)) continue
            desiredSubs["${name}#${owner}"] = pref.subscribed
        }
        ensureSubPrefDefaults()
    }

    fun saveSubPrefs() {
        ensureSubPrefDefaults()
        val prefs = state.keys
            .filter { !(state.selfNodeId > 0 && it.owner == state.selfNodeId) }
            .mapNotNull {
                val normalized = normalizeKey(it)
                if (normalized.name.isBlank() || normalized.owner <= 0) return@mapNotNull null
                Prefs.VarStoreSubPref(
                    name = normalized.name,
                    owner = normalized.owner,
                    subscribed = desiredSubs[keyId(normalized)] == true,
                )
            }
        Prefs.saveVarStoreSubPrefs(context, prefs)
    }

    fun saveSubPrefsBestEffort() {
        runCatching { saveSubPrefs() }
    }

    fun isSelfKey(key: VarKey): Boolean = state.selfNodeId > 0 && key.owner == state.selfNodeId

    fun valueForKey(input: VarKey): VarValueState {
        val key = normalizeKey(input)
        val id = keyId(key)
        val existing = state.data[id]
        if (existing != null) return existing
        val created = VarValueState(owner = key.owner)
        state.data[id] = created
        return created
    }

    fun updateValue(input: VarKey, patch: (VarValueState) -> Unit) {
        val key = upsertKey(input)
        if (key.name.isBlank() || key.owner <= 0) return
        val st = valueForKey(key)
        patch(st)
        st.lastUpdated = nowIso()
    }

    fun beginOp(label: String): Int {
        opJob?.cancel()
        val token = opSeq + 1
        opSeq = token
        busy = true
        busyLabel = label
        ui.progress(label)
        return token
    }

    fun endOp(token: Int) {
        if (opSeq != token) return
        busy = false
        busyLabel = ""
    }

    fun cancelOp() {
        opSeq += 1
        opJob?.cancel()
        opJob = null
        busy = false
        busyLabel = ""
        ui.info("已取消")
    }

    fun parseSelfNodeId(): Long {
        val raw = cfg.nodeId.trim()
        val id = raw.toLongOrNull() ?: 0L
        if (id <= 0) throw IllegalStateException("Login required to use VarStore.")
        return id
    }

    fun parseDefaultTargetId(): Long {
        val raw = cfg.hubId.trim()
        val id = raw.toLongOrNull() ?: 0L
        if (id <= 0) throw IllegalStateException("Hub ID missing.")
        return id
    }

    fun resolveTargetId(): Long {
        val raw = state.targetId.trim().ifBlank { parseDefaultTargetId().toString() }
        val parsed = raw.toLongOrNull() ?: throw IllegalStateException("Target ID 格式非法：'$raw'（期望为正整数）")
        if (parsed <= 0) throw IllegalStateException("Target ID 必须为正整数")
        return parsed
    }

    suspend fun ensureConnected(g: GoClientBridge) {
        val isConnectedNow = withContext(Dispatchers.IO) { runCatching { g.isConnected() }.getOrDefault(false) }
        if (!isConnectedNow) throw IllegalStateException("Connect before sending VarStore requests.")
    }

    fun ensureVarName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) throw IllegalStateException("Variable name is required.")
        if (!varNameRegex.matches(trimmed)) {
            throw IllegalStateException("Variable name 仅允许字母/数字/下划线（A-Z a-z 0-9 _）")
        }
    }

    suspend fun getVar(token: Int, key: VarKey): Boolean {
        val g = go ?: throw IllegalStateException("Go AAR unavailable")
        ensureConnected(g)
        val sourceId = parseSelfNodeId()
        val targetId = resolveTargetId()
        val normalized = normalizeKey(key)
        if (normalized.name.isBlank() || normalized.owner <= 0) return false

        val vs = valueForKey(normalized)
        vs.loading = true
        vs.loadError = ""

        return try {
            val raw = withContext(Dispatchers.IO) {
                g.varStoreGet(sourceId.toString(), targetId.toString(), normalized.name, normalized.owner.toString())
            }
            if (opSeq != token) return false
            val resp = parseVarResp(raw)
            updateValue(normalized) {
                it.value = resp.value
                it.owner = resp.owner
                it.visibility = resp.visibility
                it.kind = resp.type
                it.loading = false
                it.loadError = ""
            }
            true
        } catch (t: Throwable) {
            if (opSeq != token) return false
            updateValue(normalized) {
                it.loading = false
                it.loadError = t.message ?: t.toString()
            }
            false
        }
    }

    suspend fun listMine(token: Int): List<VarKey> {
        val g = go ?: throw IllegalStateException("Go AAR unavailable")
        ensureConnected(g)
        val sourceId = parseSelfNodeId()
        val targetId = resolveTargetId()

        val raw = withContext(Dispatchers.IO) {
            g.varStoreList(sourceId.toString(), targetId.toString(), sourceId.toString())
        }
        if (opSeq != token) return emptyList()

        val resp = parseVarResp(raw)
        val owner = resp.owner.takeIf { it > 0 } ?: sourceId
        val names = resp.names.map { it.trim() }.filter { it.isNotBlank() && varNameRegex.matches(it) }

        val keep = state.keys.filter { it.owner != owner }
        val nextKeys = mutableListOf<VarKey>()
        nextKeys.addAll(keep)
        for (name in names) {
            nextKeys.add(VarKey(name = name, owner = owner))
        }

        state.keys.clear()
        state.keys.addAll(nextKeys.distinctBy { keyId(it) })

        val keepIds = state.keys.mapTo(hashSetOf()) { keyId(it) }
        val stale = state.data.keys.filter { it !in keepIds }
        for (id in stale) {
            state.data.remove(id)
        }

        return state.keys.toList()
    }

    suspend fun getMany(token: Int, keys: List<VarKey>, parallelism: Int = 4): Pair<Int, Int> {
        if (keys.isEmpty()) return 0 to 0
        val queue = Channel<VarKey>(capacity = parallelism * 2)
        val ok = AtomicInteger(0)
        val fail = AtomicInteger(0)

        return try {
            kotlinx.coroutines.coroutineScope {
                val workers = List(parallelism) {
                    launch {
                        for (key in queue) {
                            if (opSeq != token) break
                            val success = getVar(token, key)
                            if (success) ok.incrementAndGet() else fail.incrementAndGet()
                        }
                    }
                }
                for (key in keys) {
                    if (opSeq != token) break
                    queue.send(key)
                }
                queue.close()
                workers.joinAll()
            }
            ok.get() to fail.get()
        } finally {
            queue.close()
        }
    }

    fun loadWatchList() {
        val saved = Prefs.loadVarStoreWatchList(context)
        val filtered = saved
            .map { VarKey(name = it.name.trim(), owner = it.owner) }
            .filter { it.name.isNotBlank() && it.owner > 0 && varNameRegex.matches(it.name) }
            .distinctBy { keyId(it) }
        val keepMine = state.keys.filter { isSelfKey(it) }
        state.keys.clear()
        state.keys.addAll((keepMine + filtered).distinctBy { keyId(it) })
        ensureSubPrefDefaults()
        touchRestoreEpoch()
        ui.success("已加载 Saved Watch List（${filtered.size}）")
    }

    fun saveWatchList() {
        val watch = state.keys
            .filter { !isSelfKey(it) }
            .map { Prefs.VarStoreWatchKey(name = it.name.trim(), owner = it.owner) }
        Prefs.saveVarStoreWatchList(context, watch)
    }

    fun addWatchKey(input: VarKey): Boolean {
        val key = normalizeKey(input)
        if (key.name.isBlank() || key.owner <= 0) return false
        val existed = state.keys.any { it.name == key.name && it.owner == key.owner }
        upsertKey(key)
        if (!isSelfKey(key)) {
            desiredSubs.putIfAbsent(keyId(key), false)
            saveWatchList()
            saveSubPrefsBestEffort()
            touchRestoreEpoch()
        }
        return !existed
    }

    fun parseNodeVarsOwnerInput(): Long {
        val owner = nodeVarsOwnerInput.trim().toLongOrNull() ?: 0L
        if (owner <= 0) throw IllegalStateException("Owner NodeID must be a positive number.")
        return owner
    }

    suspend fun listOwnerNames(ownerId: Long): List<String> {
        val g = go ?: throw IllegalStateException("Go AAR unavailable")
        ensureConnected(g)
        val sourceId = parseSelfNodeId()
        val hubTargetId = parseDefaultTargetId()
        if (ownerId <= 0) throw IllegalStateException("Owner NodeID must be a positive number.")
        val raw = withContext(Dispatchers.IO) {
            g.varStoreList(sourceId.toString(), hubTargetId.toString(), ownerId.toString())
        }
        val resp = parseVarResp(raw)
        if (resp.code == 4) return emptyList()
        if (resp.code != 1) {
            val msg = resp.msg.ifBlank { "VarStore list failed (code=${resp.code})" }
            throw IllegalStateException(msg)
        }
        return resp.names
            .map { it.trim() }
            .filter { it.isNotBlank() && varNameRegex.matches(it) }
            .distinct()
            .sorted()
    }

    fun openNodeVarsDialog() {
        val defaultOwner = state.targetId.trim().toLongOrNull()?.takeIf { it > 0 }
            ?: state.keys.firstOrNull { !isSelfKey(it) }?.owner
            ?: state.defaultTargetId.takeIf { it > 0 }
        nodeVarsOwnerInput = defaultOwner?.toString() ?: ""
        nodeVarsQuery = ""
        nodeVarsError = ""
        nodeVarsNames.clear()
        nodeVarsLoading = false
        nodeVarsOpen = true
    }

    fun openAddMine() {
        addMineName = ""
        addMineValue = ""
        addMineVisibility = "public"
        addMineKind = "string"
        addMineOpen = true
    }

    fun openAddWatch() {
        addWatchName = ""
        addWatchOwner = ""
        addWatchOpen = true
    }

    fun openEditDialog(key: VarKey) {
        val normalized = normalizeKey(key)
        val vs = valueForKey(normalized)
        editKey = normalized
        editValue = vs.value
        editVisibility = vs.visibility.ifBlank { "public" }
        editKind = vs.kind.ifBlank { "string" }
        editOpen = true
    }

    suspend fun submitAddMine() {
        val g = go ?: throw IllegalStateException("Go AAR unavailable")
        ensureConnected(g)
        val sourceId = parseSelfNodeId()
        val targetId = resolveTargetId()

        val name = addMineName.trim()
        ensureVarName(name)
        val value = addMineValue
        if (value.trim().isBlank()) throw IllegalStateException("Variable value is required.")
        val visibility = addMineVisibility.trim().ifBlank { "public" }
        val kind = addMineKind.trim().ifBlank { "string" }

        val key = VarKey(name = name, owner = sourceId)
        withContext(Dispatchers.IO) {
            g.varStoreSet(
                sourceId.toString(),
                targetId.toString(),
                name,
                value,
                visibility,
                kind,
                sourceId.toString(),
            )
        }
        // set_resp doesn't include value; mirror Win behavior: use user input for immediate UI feedback.
        updateValue(key) {
            it.value = value
            it.owner = sourceId
            it.visibility = visibility
            it.kind = kind
        }
        addMineOpen = false
        val token = opSeq
        getVar(token, key)
        ui.success("Variable added.")
    }

    suspend fun submitAddWatch() {
        val owner = addWatchOwner.trim().toLongOrNull() ?: 0L
        if (owner <= 0) throw IllegalStateException("Owner NodeID must be a positive number.")
        val name = addWatchName.trim()
        ensureVarName(name)
        val key = VarKey(name = name, owner = owner)
        val added = addWatchKey(key)
        addWatchOpen = false

        val token = opSeq
        if (added) {
            getVar(token, key)
            ui.success("Watch added.")
        } else {
            ui.info("Already watched.")
        }
    }

    suspend fun submitEdit() {
        val key = editKey ?: throw IllegalStateException("No key selected.")
        val g = go ?: throw IllegalStateException("Go AAR unavailable")
        ensureConnected(g)
        val sourceId = parseSelfNodeId()
        val targetId = resolveTargetId()

        val value = editValue
        if (value.trim().isBlank()) throw IllegalStateException("Variable value is required.")
        val visibility = editVisibility.trim().ifBlank { "public" }
        val kind = editKind.trim().ifBlank { "string" }

        withContext(Dispatchers.IO) {
            g.varStoreSet(
                sourceId.toString(),
                targetId.toString(),
                key.name,
                value,
                visibility,
                kind,
                key.owner.toString(),
            )
        }
        updateValue(key) {
            it.value = value
            it.owner = key.owner
            it.visibility = visibility
            it.kind = kind
        }
        editOpen = false
        val token = opSeq
        getVar(token, key)
        ui.success("Variable saved.")
    }

    suspend fun revokeKey(key: VarKey) {
        val g = go ?: throw IllegalStateException("Go AAR unavailable")
        ensureConnected(g)
        val sourceId = parseSelfNodeId()
        val targetId = resolveTargetId()

        withContext(Dispatchers.IO) {
            g.varStoreRevoke(sourceId.toString(), targetId.toString(), key.name, key.owner.toString())
        }
        removeLocalKey(key)
        if (!isSelfKey(key)) {
            saveWatchList()
            saveSubPrefsBestEffort()
            touchRestoreEpoch()
        }
        ui.success("Revoked.")
    }

    suspend fun toggleSubscribe(key: VarKey) {
        val normalized = normalizeKey(key)
        val vs = valueForKey(normalized)
        val id = keyId(normalized)
        val subscribe = !(vs.subKnown && vs.subscribed)

        val g = go ?: throw IllegalStateException("Go AAR unavailable")
        ensureConnected(g)
        val sourceId = parseSelfNodeId()
        val targetId = resolveTargetId()

        if (subscribe) {
            desiredSubs[id] = true
            saveSubPrefsBestEffort()
            touchRestoreEpoch()
            val token = opSeq
            val respRaw = withContext(Dispatchers.IO) {
                g.varStoreSubscribe(sourceId.toString(), targetId.toString(), normalized.name, normalized.owner.toString(), sourceId.toString())
            }
            if (opSeq != token) return
            val resp = parseVarResp(respRaw)
            if (desiredSubs[id] != true) return
            updateValue(normalized) {
                it.subKnown = true
                it.subscribed = true
                if (resp.visibility.isNotBlank()) it.visibility = resp.visibility
                if (resp.type.isNotBlank()) it.kind = resp.type
            }
            ui.success("Subscribed.")
            return
        }

        // unsubscribe (optimistic, but rollback on failure)
        desiredSubs[id] = false
        saveSubPrefsBestEffort()
        touchRestoreEpoch()
        val prevKnown = vs.subKnown
        val prevSub = vs.subscribed
        updateValue(normalized) {
            it.subKnown = true
            it.subscribed = false
        }
        try {
            withContext(Dispatchers.IO) {
                g.varStoreUnsubscribe(sourceId.toString(), targetId.toString(), normalized.name, normalized.owner.toString(), sourceId.toString())
            }
            ui.success("Unsubscribed.")
        } catch (t: Throwable) {
            updateValue(normalized) {
                it.subKnown = prevKnown
                it.subscribed = prevSub
            }
            throw t
        }
    }

    fun applyVarEvent(action: String, resp: VarResp) {
        if (resp.owner <= 0 || resp.name.isBlank()) return
        val key = VarKey(name = resp.name, owner = resp.owner)
        when (action) {
            "var_changed", "notify_set", "up_set" -> {
                updateValue(key) {
                    it.value = resp.value
                    it.owner = resp.owner
                    it.visibility = resp.visibility
                    it.kind = resp.type
                    it.loadError = ""
                    it.loading = false
                }
            }

            "var_deleted", "notify_revoke", "up_revoke" -> {
                removeLocalKey(key)
            }
        }
    }

    suspend fun restoreDesiredSubscriptions(parallelism: Int = 4): Pair<Int, Int> {
        val g = go ?: return 0 to 0
        ensureConnected(g)
        val sourceId = parseSelfNodeId()
        val targetId = resolveTargetId()
        ensureSubPrefDefaults()
        val desired = state.keys
            .map { normalizeKey(it) }
            .filter {
                it.name.isNotBlank() &&
                    it.owner > 0 &&
                    !isSelfKey(it) &&
                    desiredSubs[keyId(it)] == true
            }
        if (desired.isEmpty()) return 0 to 0

        val queue = Channel<VarKey>(capacity = parallelism.coerceAtLeast(1) * 2)
        val fail = AtomicInteger(0)
        return try {
            kotlinx.coroutines.coroutineScope {
                val workers = List(parallelism.coerceAtLeast(1).coerceAtMost(desired.size)) {
                    launch {
                        for (key in queue) {
                            try {
                                val raw = withContext(Dispatchers.IO) {
                                    g.varStoreSubscribe(
                                        sourceId.toString(),
                                        targetId.toString(),
                                        key.name,
                                        key.owner.toString(),
                                        sourceId.toString(),
                                    )
                                }
                                val resp = parseVarResp(raw)
                                updateValue(key) {
                                    it.subKnown = true
                                    it.subscribed = true
                                    if (resp.visibility.isNotBlank()) it.visibility = resp.visibility
                                    if (resp.type.isNotBlank()) it.kind = resp.type
                                }
                            } catch (t: Throwable) {
                                if (t is CancellationException) throw t
                                fail.incrementAndGet()
                            }
                        }
                    }
                }
                for (key in desired) {
                    queue.send(key)
                }
                queue.close()
                workers.joinAll()
            }
            desired.size to fail.get()
        } finally {
            queue.close()
        }
    }

    LaunchedEffect(Unit) {
        state.selfNodeId = cfg.nodeId.trim().toLongOrNull() ?: 0L
        state.defaultTargetId = cfg.hubId.trim().toLongOrNull() ?: 0L
        if (state.targetId.isBlank() && state.defaultTargetId > 0) {
            state.targetId = state.defaultTargetId.toString()
        }

        val saved = Prefs.loadVarStoreWatchList(context)
        state.keys.clear()
        state.keys.addAll(
            saved
                .map { VarKey(name = it.name.trim(), owner = it.owner) }
                .filter { it.name.isNotBlank() && it.owner > 0 && varNameRegex.matches(it.name) }
                .distinctBy { keyId(it) },
        )
        loadSubPrefs()
        touchRestoreEpoch()
    }

    LaunchedEffect(cfg.nodeId, cfg.hubId) {
        state.selfNodeId = cfg.nodeId.trim().toLongOrNull() ?: 0L
        state.defaultTargetId = cfg.hubId.trim().toLongOrNull() ?: 0L
        if (state.targetId.isBlank() && state.defaultTargetId > 0) {
            state.targetId = state.defaultTargetId.toString()
        }
    }

    LaunchedEffect(go) {
        while (isActive) {
            val g = go
            if (g == null) {
                connected = false
                delay(800)
                continue
            }
            connected = withContext(Dispatchers.IO) { runCatching { g.isConnected() }.getOrDefault(false) }
            delay(800)
        }
    }

    LaunchedEffect(connected) {
        if (!connected) {
            lastAutoRestoreKey = ""
        }
    }

    LaunchedEffect(connected, cfg.nodeId, cfg.hubId, restoreEpoch) {
        val nodeId = cfg.nodeId.trim()
        if (!connected || nodeId.isBlank()) return@LaunchedEffect

        val targets = state.keys
            .map { normalizeKey(it) }
            .filter {
                it.name.isNotBlank() &&
                    it.owner > 0 &&
                    !isSelfKey(it) &&
                    desiredSubs[keyId(it)] == true
            }
            .sortedWith(compareBy({ it.owner }, { it.name }))

        if (targets.isEmpty()) return@LaunchedEffect

        val restoreKey = buildString {
            append(nodeId)
            append("#")
            append(cfg.hubId.trim())
            append("#")
            for (key in targets) {
                append(keyId(key))
                append(";")
            }
        }
        if (restoreKey == lastAutoRestoreKey) return@LaunchedEffect
        lastAutoRestoreKey = restoreKey

        val result = runCatching { restoreDesiredSubscriptions(parallelism = 4) }.getOrElse {
            ui.error("自动恢复订阅失败：${it.message ?: it}")
            return@LaunchedEffect
        }
        val attempted = result.first
        val failed = result.second
        if (attempted <= 0) return@LaunchedEffect
        if (failed > 0) {
            ui.error("自动恢复订阅：成功 ${attempted - failed}，失败 ${failed}")
        } else {
            ui.success("已自动恢复订阅（${attempted}）")
        }
    }

    LaunchedEffect(go, cfg.nodeId, cfg.hubId) {
        var cursor = "0"
        while (isActive) {
            val g = go
            if (g == null) {
                delay(800)
                continue
            }
            if (cfg.nodeId.trim().isBlank()) {
                delay(800)
                continue
            }
            val isConnectedNow = withContext(Dispatchers.IO) { runCatching { g.isConnected() }.getOrDefault(false) }
            if (!isConnectedNow) {
                delay(800)
                continue
            }
            try {
                val respRaw = withContext(Dispatchers.IO) { g.varStoreEventsPull(cursor, "200") }
                val obj = JSONObject(respRaw)
                val next = obj.optLong("next_cursor", obj.optLong("nextCursor", 0)).toString()
                val arr = obj.optJSONArray("events") ?: JSONArray()
                val hasMore = obj.optBoolean("has_more", false)
                cursor = next

                if (arr.length() == 0) {
                    delay(500)
                    continue
                }

                for (i in 0 until arr.length()) {
                    val evt = arr.optJSONObject(i) ?: continue
                    val action = evt.optString("action", "").trim()
                    if (action.isBlank()) continue
                    val dropped = evt.optBoolean("dropped", false)
                    if (dropped) continue
                    val data = evt.optJSONObject("data") ?: continue
                    val vr = parseVarResp(data)
                    applyVarEvent(action, vr)
                }
                state.lastFrameAt = nowIso()

                if (!hasMore) {
                    delay(200)
                }
            } catch (_: CancellationException) {
                return@LaunchedEffect
            } catch (_: Throwable) {
                delay(800)
            }
        }
    }

    val nodeVarsFiltered by remember {
        derivedStateOf {
            val q = nodeVarsQuery.trim().lowercase()
            if (q.isBlank()) {
                nodeVarsNames.toList()
            } else {
                nodeVarsNames.filter { it.lowercase().contains(q) }
            }
        }
    }

    val grouped by remember {
        derivedStateOf {
            val mine = mutableListOf<VarKey>()
            val others = mutableListOf<VarKey>()
            for (key in state.keys) {
                if (isSelfKey(key)) mine.add(key) else others.add(key)
            }
            mine.sortBy { it.name }
            others.sortWith(compareBy({ it.owner }, { it.name }))
            mine to others
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (busyLabel.isNotBlank()) {
                    Text(busyLabel, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            if (go == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Go AAR 不可用", fontWeight = FontWeight.SemiBold)
                        Text(goError.ifBlank { "unknown error" })
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("VarStore", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.targetId,
                            onValueChange = { state.targetId = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Target node id") },
                            placeholder = { Text(cfg.hubId.ifBlank { "默认使用 Hub ID" }) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        FilledTonalButton(
                            enabled = go != null && !busy,
                            onClick = {
                                val token = beginOp("正在刷新 VarStore…")
                                val job = scope.launch {
                                    try {
                                        val keys = listMine(token)
                                        if (opSeq != token) return@launch
                                        val (ok, fail) = getMany(token, keys, parallelism = 4)
                                        if (opSeq != token) return@launch
                                        if (fail > 0) {
                                            ui.error("刷新完成：成功 ${ok}，失败 ${fail}")
                                        } else {
                                            ui.success("VarStore refreshed.")
                                        }
                                    } catch (_: CancellationException) {
                                        // Cancelled/replaced by a newer operation.
                                    } catch (t: Throwable) {
                                        if (opSeq != token) return@launch
                                        ui.error("刷新失败：${t.message ?: t}")
                                    } finally {
                                        endOp(token)
                                    }
                                }
                                opJob = job
                            },
                        ) { Text("Refresh") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (cfg.nodeId.isNotBlank()) AssistChip(onClick = {}, label = { Text("Node ${cfg.nodeId}") })
                        if (cfg.hubId.isNotBlank()) AssistChip(onClick = {}, label = { Text("Hub ${cfg.hubId}") })
                        if (state.lastFrameAt.isNotBlank()) AssistChip(onClick = {}, label = { Text("Updated") })
                    }

                    if (state.lastFrameAt.isNotBlank()) {
                        Text("Last Frame: ${state.lastFrameAt}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("My Variables", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        FilledTonalButton(enabled = !busy, onClick = { openAddMine() }) { Text("Add") }
                    }
                    Text(
                        "Owner = ${state.selfNodeId.takeIf { it > 0 } ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        items(items = grouped.first, key = { keyId(it) }) { key ->
            val vs = valueForKey(key)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(key.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Owner ${key.owner} · ${vs.visibility.ifBlank { "unknown" }} · ${vs.kind.ifBlank { "unknown" }}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        AssistChip(onClick = {}, label = { Text("Mine") })
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (vs.loading) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Text(vs.value.ifBlank { "-" })
                            if (vs.loadError.isNotBlank()) {
                                Text("Error: ${vs.loadError}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                val token = beginOp("正在刷新 ${key.name}…")
                                val job = scope.launch {
                                    try {
                                        val ok = getVar(token, key)
                                        if (opSeq != token) return@launch
                                        if (ok) ui.success("Variable refreshed.") else ui.error("刷新失败：${valueForKey(key).loadError}")
                                    } catch (_: CancellationException) {
                                        // Cancelled/replaced by a newer operation.
                                    } catch (t: Throwable) {
                                        if (opSeq != token) return@launch
                                        ui.error("刷新失败：${t.message ?: t}")
                                    } finally {
                                        endOp(token)
                                    }
                                }
                                opJob = job
                            },
                        ) { Text("Refresh") }

                        OutlinedButton(enabled = !busy, onClick = { openEditDialog(key) }) { Text("Edit") }

                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                val token = beginOp("正在撤销 ${key.name}…")
                                val job = scope.launch {
                                    try {
                                        revokeKey(key)
                                    } catch (_: CancellationException) {
                                        // Cancelled/replaced by a newer operation.
                                    } catch (t: Throwable) {
                                        if (opSeq != token) return@launch
                                        ui.error("撤销失败：${t.message ?: t}")
                                    } finally {
                                        endOp(token)
                                    }
                                }
                                opJob = job
                            },
                        ) { Text("Revoke") }

                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                removeLocalKey(key)
                                ui.success("Removed locally.")
                            },
                        ) { Text("Remove") }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Watched Variables", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        OutlinedButton(enabled = !busy, onClick = { openNodeVarsDialog() }) { Text("Node Vars") }
                        FilledTonalButton(enabled = !busy, onClick = { openAddWatch() }) { Text("Add Watch") }
                        OutlinedButton(enabled = !busy, onClick = { loadWatchList() }) { Text("Reload Saved") }
                    }
                    Text("Watch Count: ${grouped.second.size}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        items(items = grouped.second, key = { keyId(it) }) { key ->
            val vs = valueForKey(key)
            val subscribed = vs.subKnown && vs.subscribed
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(key.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Owner ${key.owner} · ${vs.visibility.ifBlank { "unknown" }} · ${vs.kind.ifBlank { "unknown" }}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (subscribed) {
                            AssistChip(onClick = {}, label = { Text("Subscribed") })
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (vs.loading) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Text(vs.value.ifBlank { "-" })
                            if (vs.loadError.isNotBlank()) {
                                Text("Error: ${vs.loadError}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                val token = beginOp("正在刷新 ${key.name}…")
                                val job = scope.launch {
                                    try {
                                        val ok = getVar(token, key)
                                        if (opSeq != token) return@launch
                                        if (ok) ui.success("Variable refreshed.") else ui.error("刷新失败：${valueForKey(key).loadError}")
                                    } catch (_: CancellationException) {
                                        // Cancelled/replaced by a newer operation.
                                    } catch (t: Throwable) {
                                        if (opSeq != token) return@launch
                                        ui.error("刷新失败：${t.message ?: t}")
                                    } finally {
                                        endOp(token)
                                    }
                                }
                                opJob = job
                            },
                        ) { Text("Refresh") }

                        OutlinedButton(enabled = !busy, onClick = { openEditDialog(key) }) { Text("Edit") }

                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                val token = beginOp("正在撤销 ${key.name}…")
                                val job = scope.launch {
                                    try {
                                        revokeKey(key)
                                    } catch (_: CancellationException) {
                                        // Cancelled/replaced by a newer operation.
                                    } catch (t: Throwable) {
                                        if (opSeq != token) return@launch
                                        ui.error("撤销失败：${t.message ?: t}")
                                    } finally {
                                        endOp(token)
                                    }
                                }
                                opJob = job
                            },
                        ) { Text("Revoke") }

                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                removeLocalKey(key)
                                saveWatchList()
                                saveSubPrefsBestEffort()
                                touchRestoreEpoch()
                                ui.success("Watch removed.")
                            },
                        ) { Text("Remove") }

                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                val token = beginOp(if (subscribed) "正在取消订阅…" else "正在订阅…")
                                val job = scope.launch {
                                    try {
                                        toggleSubscribe(key)
                                    } catch (_: CancellationException) {
                                        // Cancelled/replaced by a newer operation.
                                    } catch (t: Throwable) {
                                        if (opSeq != token) return@launch
                                        ui.error("订阅操作失败：${t.message ?: t}")
                                    } finally {
                                        endOp(token)
                                    }
                                }
                                opJob = job
                            },
                        ) { Text(if (subscribed) "Unsubscribe" else "Subscribe") }
                    }
                }
            }
        }

        item {
            if (busy) {
                OutlinedButton(onClick = { cancelOp() }) { Text("Cancel") }
            }
        }
    }

    if (nodeVarsOpen) {
        AlertDialog(
            onDismissRequest = { nodeVarsOpen = false },
            title = { Text("Node Variables") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Query variables under a node and quickly add them to Watch.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = nodeVarsOwnerInput,
                        onValueChange = { nodeVarsOwnerInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Owner NodeID") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            enabled = !nodeVarsLoading,
                            onClick = {
                                nodeVarsLoading = true
                                nodeVarsError = ""
                                val job = scope.launch {
                                    try {
                                        val owner = parseNodeVarsOwnerInput()
                                        val names = listOwnerNames(owner)
                                        nodeVarsNames.clear()
                                        nodeVarsNames.addAll(names)
                                        if (names.isEmpty()) {
                                            ui.info("该节点当前无可见变量。")
                                        } else {
                                            ui.success("已加载 ${names.size} 个变量。")
                                        }
                                    } catch (_: CancellationException) {
                                        // Cancelled by another operation.
                                    } catch (t: Throwable) {
                                        nodeVarsError = t.message ?: t.toString()
                                        ui.error("加载节点变量失败：${nodeVarsError}")
                                    } finally {
                                        nodeVarsLoading = false
                                    }
                                }
                                opJob = job
                            },
                        ) { Text(if (nodeVarsLoading) "Loading…" else "Load") }

                        OutlinedTextField(
                            value = nodeVarsQuery,
                            onValueChange = { nodeVarsQuery = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Search") },
                            singleLine = true,
                        )
                    }

                    if (nodeVarsLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (nodeVarsError.isNotBlank()) {
                        Text("Error: $nodeVarsError", style = MaterialTheme.typography.bodySmall)
                    }

                    val owner = nodeVarsOwnerInput.trim().toLongOrNull() ?: 0L
                    Text(
                        "Results: ${nodeVarsFiltered.size}",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = nodeVarsFiltered, key = { it }) { name ->
                            val watched = owner > 0 && state.keys.any { it.name == name && it.owner == owner }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Owner $owner", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (watched) {
                                        AssistChip(onClick = {}, label = { Text("Watched") })
                                    }
                                    OutlinedButton(
                                        enabled = !nodeVarsLoading && owner > 0 && !watched,
                                        onClick = {
                                            val key = VarKey(name = name, owner = owner)
                                            val added = addWatchKey(key)
                                            if (!added) {
                                                ui.info("已在 Watch 中。")
                                                return@OutlinedButton
                                            }
                                            val token = opSeq
                                            val job = scope.launch {
                                                runCatching { getVar(token, key) }
                                            }
                                            opJob = job
                                            ui.success("Watch added.")
                                        },
                                    ) { Text("Add Watch") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { nodeVarsOpen = false }) { Text("Close") }
            },
        )
    }

    if (addMineOpen) {
        AlertDialog(
            onDismissRequest = { addMineOpen = false },
            title = { Text("Create Variable") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Owner: ${state.selfNodeId.takeIf { it > 0 } ?: "-"}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = addMineName,
                        onValueChange = { addMineName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = addMineValue,
                        onValueChange = { addMineValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Value") },
                        singleLine = false,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = addMineVisibility == "public",
                            onClick = { addMineVisibility = "public" },
                            label = { Text("public") },
                        )
                        FilterChip(
                            selected = addMineVisibility == "private",
                            onClick = { addMineVisibility = "private" },
                            label = { Text("private") },
                        )
                    }
                    OutlinedTextField(
                        value = addMineKind,
                        onValueChange = { addMineKind = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Type") },
                        singleLine = true,
                    )
                    Text("Name 仅允许字母/数字/下划线（A-Z a-z 0-9 _）", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = go != null && !busy,
                    onClick = {
                        val token = beginOp("正在保存…")
                        val job = scope.launch {
                            try {
                                submitAddMine()
                            } catch (_: CancellationException) {
                                // Cancelled/replaced by a newer operation.
                            } catch (t: Throwable) {
                                if (opSeq != token) return@launch
                                ui.error("新增失败：${t.message ?: t}")
                            } finally {
                                endOp(token)
                            }
                        }
                        opJob = job
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { addMineOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (addWatchOpen) {
        AlertDialog(
            onDismissRequest = { addWatchOpen = false },
            title = { Text("Add Watch") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = addWatchName,
                        onValueChange = { addWatchName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = addWatchOwner,
                        onValueChange = { addWatchOwner = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Owner NodeID") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text("Name 仅允许字母/数字/下划线（A-Z a-z 0-9 _）", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val token = beginOp("正在保存 Watch…")
                        val job = scope.launch {
                            try {
                                submitAddWatch()
                            } catch (_: CancellationException) {
                                // Cancelled/replaced by a newer operation.
                            } catch (t: Throwable) {
                                if (opSeq != token) return@launch
                                ui.error("添加 Watch 失败：${t.message ?: t}")
                            } finally {
                                endOp(token)
                            }
                        }
                        opJob = job
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { addWatchOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (editOpen) {
        val key = editKey
        AlertDialog(
            onDismissRequest = { editOpen = false },
            title = { Text("Edit Variable") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Name: ${key?.name ?: "-"}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Owner: ${key?.owner ?: "-"}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Value") },
                        singleLine = false,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = editVisibility == "public",
                            onClick = { editVisibility = "public" },
                            label = { Text("public") },
                        )
                        FilterChip(
                            selected = editVisibility == "private",
                            onClick = { editVisibility = "private" },
                            label = { Text("private") },
                        )
                    }
                    OutlinedTextField(
                        value = editKind,
                        onValueChange = { editKind = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Type") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val token = beginOp("正在保存…")
                        val job = scope.launch {
                            try {
                                submitEdit()
                            } catch (_: CancellationException) {
                                // Cancelled/replaced by a newer operation.
                            } catch (t: Throwable) {
                                if (opSeq != token) return@launch
                                ui.error("保存失败：${t.message ?: t}")
                            } finally {
                                endOp(token)
                            }
                        }
                        opJob = job
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { editOpen = false }) { Text("Cancel") }
            },
        )
    }
}
