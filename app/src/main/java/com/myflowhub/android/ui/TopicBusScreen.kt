package com.myflowhub.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myflowhub.android.GoClientBridge
import com.myflowhub.android.Prefs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class TopicBusEvent(
    val topic: String,
    val name: String,
    val ts: Long,
    val dataRaw: String,
)

@Stable
internal class TopicBusState {
    var targetId by mutableStateOf("")
    var selfNodeId by mutableStateOf(0L)
    var defaultTargetId by mutableStateOf(0L)

    val topics = mutableStateListOf<String>()
    var selectedTopic by mutableStateOf("")

    var maxEvents by mutableStateOf(500)
    val events = mutableStateListOf<TopicBusEvent>()
    var lastFrameAt by mutableStateOf("")
}

private fun nowIso(): String = Instant.now().toString()

private val tsFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

internal fun formatTimestamp(ts: Long): String {
    if (ts <= 0) return ""
    return runCatching { tsFormatter.format(Instant.ofEpochMilli(ts)) }.getOrDefault("")
}

private fun normalizeTopics(topics: List<String>): List<String> {
    if (topics.isEmpty()) return emptyList()
    val out = ArrayList<String>(topics.size)
    val seen = HashSet<String>(topics.size)
    for (t in topics) {
        val trimmed = t.trim()
        if (trimmed.isBlank() || !seen.add(trimmed)) continue
        out.add(trimmed)
    }
    return out
}

private fun mergeTopics(existing: List<String>, add: List<String>): List<String> =
    normalizeTopics(existing + add)

private fun removeTopics(existing: List<String>, remove: List<String>): List<String> {
    val removeSet = normalizeTopics(remove).toHashSet()
    if (removeSet.isEmpty()) return normalizeTopics(existing)
    return normalizeTopics(existing.filter { it !in removeSet })
}

private fun parseTopics(raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return emptyList()
    return normalizeTopics(trimmed.split(Regex("[\\n,，;；]+")))
}

private fun formatDetailJson(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.startsWith("{")) {
        return runCatching { JSONObject(trimmed).toString(2) }.getOrDefault(raw)
    }
    if (trimmed.startsWith("[")) {
        return runCatching { JSONArray(trimmed).toString(2) }.getOrDefault(raw)
    }
    return raw
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicBusScreen(
    modifier: Modifier = Modifier,
    go: GoClientBridge?,
    goError: String,
    cfg: Prefs.ClientConfig,
    ui: UiNotifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val state = remember { TopicBusState() }
    var connected by remember { mutableStateOf(false) }

    var subText by remember { mutableStateOf("") }
    var publishTopic by remember { mutableStateOf("") }
    var publishName by remember { mutableStateOf("") }
    var publishPayload by remember { mutableStateOf("") }

    var maxEventsInput by remember { mutableStateOf("500") }

    var selectedEventIndex by remember { mutableStateOf(-1) }

    var opJob by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("") }
    var opSeq by remember { mutableStateOf(0) }
    var lastAutoResubKey by remember { mutableStateOf("") }

    val pendingEvents = remember { ArrayList<TopicBusEvent>(128) }
    var flushJob by remember { mutableStateOf<Job?>(null) }
    var lastFlushAt by remember { mutableStateOf(0L) }

    fun beginOp(label: String): Int {
        opJob?.cancel()
        val token = opSeq + 1
        opSeq = token
        busy = true
        busyLabel = label
        ui.progress(label)
        return token
}

@Composable
fun TopicBusWideLayout(
    cardColors: CardColors,
    cardElevation: CardElevation,
    connected: Boolean,
    state: TopicBusState,
    busy: Boolean,
    subText: String,
    onSubTextChange: (String) -> Unit,
    maxEventsInput: String,
    onMaxEventsInputChange: (String) -> Unit,
    publishTopic: String,
    onPublishTopicChange: (String) -> Unit,
    publishName: String,
    onPublishNameChange: (String) -> Unit,
    publishPayload: String,
    onPublishPayloadChange: (String) -> Unit,
    filteredEvents: List<TopicBusEvent>,
    selectedEventIndex: Int,
    onSelectEventIndex: (Int) -> Unit,
    selectedEvent: TopicBusEvent?,
    onResubscribe: () -> Unit,
    onUnsubscribeSelected: () -> Unit,
    onSubscribeFromInput: () -> Unit,
    onUnsubscribeFromInput: () -> Unit,
    onApplyMaxEvents: () -> Unit,
    onClearEvents: () -> Unit,
    onUseSelected: () -> Unit,
    onClearPublish: () -> Unit,
    onPublish: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TopicBusControlCard(
                cardColors = cardColors,
                cardElevation = cardElevation,
                connected = connected,
                state = state,
                subText = subText,
                onSubTextChange = onSubTextChange,
                maxEventsInput = maxEventsInput,
                onMaxEventsInputChange = onMaxEventsInputChange,
                busy = busy,
                onResubscribe = onResubscribe,
                onUnsubscribeSelected = onUnsubscribeSelected,
                onSubscribeFromInput = onSubscribeFromInput,
                onUnsubscribeFromInput = onUnsubscribeFromInput,
                onApplyMaxEvents = onApplyMaxEvents,
                onClearEvents = onClearEvents,
            )
            TopicBusPublishCard(
                cardColors = cardColors,
                cardElevation = cardElevation,
                publishTopic = publishTopic,
                onPublishTopicChange = onPublishTopicChange,
                publishName = publishName,
                onPublishNameChange = onPublishNameChange,
                publishPayload = publishPayload,
                onPublishPayloadChange = onPublishPayloadChange,
                busy = busy,
                onUseSelected = onUseSelected,
                onPublish = onPublish,
                onClear = onClearPublish,
            )
        }

        Column(modifier = Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TopicBusSubscriptionListCard(
                cardColors = cardColors,
                cardElevation = cardElevation,
                topics = state.topics,
                selectedTopic = state.selectedTopic,
                onSelect = { state.selectedTopic = it },
            )
            TopicBusSnapshotCard(
                cardColors = cardColors,
                cardElevation = cardElevation,
                selectedTopic = state.selectedTopic,
                lastFrameAt = state.lastFrameAt,
                cached = state.events.size,
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TopicBusEventStreamCard(
            modifier = Modifier.weight(1.1f),
            cardColors = cardColors,
            cardElevation = cardElevation,
            selectedTopic = state.selectedTopic,
            events = filteredEvents,
            selectedIndex = selectedEventIndex,
            onSelectIndex = onSelectEventIndex,
        )
        TopicBusEventDetailCard(
            modifier = Modifier.weight(0.9f),
            cardColors = cardColors,
            cardElevation = cardElevation,
            event = selectedEvent,
        )
    }
}

@Composable
fun TopicBusNarrowLayout(
    cardColors: CardColors,
    cardElevation: CardElevation,
    connected: Boolean,
    state: TopicBusState,
    busy: Boolean,
    subText: String,
    onSubTextChange: (String) -> Unit,
    maxEventsInput: String,
    onMaxEventsInputChange: (String) -> Unit,
    publishTopic: String,
    onPublishTopicChange: (String) -> Unit,
    publishName: String,
    onPublishNameChange: (String) -> Unit,
    publishPayload: String,
    onPublishPayloadChange: (String) -> Unit,
    filteredEvents: List<TopicBusEvent>,
    selectedEventIndex: Int,
    onSelectEventIndex: (Int) -> Unit,
    selectedEvent: TopicBusEvent?,
    onResubscribe: () -> Unit,
    onUnsubscribeSelected: () -> Unit,
    onSubscribeFromInput: () -> Unit,
    onUnsubscribeFromInput: () -> Unit,
    onApplyMaxEvents: () -> Unit,
    onClearEvents: () -> Unit,
    onUseSelected: () -> Unit,
    onClearPublish: () -> Unit,
    onPublish: () -> Unit,
) {
    TopicBusControlCard(
        cardColors = cardColors,
        cardElevation = cardElevation,
        connected = connected,
        state = state,
        subText = subText,
        onSubTextChange = onSubTextChange,
        maxEventsInput = maxEventsInput,
        onMaxEventsInputChange = onMaxEventsInputChange,
        busy = busy,
        onResubscribe = onResubscribe,
        onUnsubscribeSelected = onUnsubscribeSelected,
        onSubscribeFromInput = onSubscribeFromInput,
        onUnsubscribeFromInput = onUnsubscribeFromInput,
        onApplyMaxEvents = onApplyMaxEvents,
        onClearEvents = onClearEvents,
    )
    TopicBusPublishCard(
        cardColors = cardColors,
        cardElevation = cardElevation,
        publishTopic = publishTopic,
        onPublishTopicChange = onPublishTopicChange,
        publishName = publishName,
        onPublishNameChange = onPublishNameChange,
        publishPayload = publishPayload,
        onPublishPayloadChange = onPublishPayloadChange,
        busy = busy,
        onUseSelected = onUseSelected,
        onPublish = onPublish,
        onClear = onClearPublish,
    )
    TopicBusSubscriptionListCard(
        cardColors = cardColors,
        cardElevation = cardElevation,
        topics = state.topics,
        selectedTopic = state.selectedTopic,
        onSelect = { state.selectedTopic = it },
    )
    TopicBusSnapshotCard(
        cardColors = cardColors,
        cardElevation = cardElevation,
        selectedTopic = state.selectedTopic,
        lastFrameAt = state.lastFrameAt,
        cached = state.events.size,
    )
    TopicBusEventStreamCard(
        modifier = Modifier.fillMaxWidth(),
        cardColors = cardColors,
        cardElevation = cardElevation,
        selectedTopic = state.selectedTopic,
        events = filteredEvents,
        selectedIndex = selectedEventIndex,
        onSelectIndex = onSelectEventIndex,
    )
    TopicBusEventDetailCard(
        modifier = Modifier.fillMaxWidth(),
        cardColors = cardColors,
        cardElevation = cardElevation,
        event = selectedEvent,
    )
}

    fun endOp(token: Int) {
        if (opSeq != token) return
        busy = false
        busyLabel = ""
    }

    fun ensureGo(): GoClientBridge {
        return go ?: throw IllegalStateException("Go 不可用：${goError.ifBlank { "unknown error" }}")
    }

    suspend fun ensureConnected(g: GoClientBridge) {
        val ok = withContext(Dispatchers.IO) { runCatching { g.isConnected() }.getOrDefault(false) }
        if (!ok) throw IllegalStateException("Connect before using TopicBus.")
    }

    fun parseSelfNodeId(): Long {
        val id = cfg.nodeId.trim().toLongOrNull() ?: 0L
        if (id <= 0) throw IllegalStateException("Login required to send TopicBus requests.")
        return id
    }

    fun parseDefaultTargetId(): Long {
        val id = cfg.hubId.trim().toLongOrNull() ?: 0L
        if (id <= 0) throw IllegalStateException("Hub ID missing.")
        return id
    }

    fun resolveTargetId(): Long {
        val raw = state.targetId.trim().ifBlank { parseDefaultTargetId().toString() }
        val parsed = raw.toLongOrNull() ?: throw IllegalStateException("Target ID 格式非法：'$raw'（期望为正整数）")
        if (parsed <= 0) throw IllegalStateException("Target ID 必须为正整数")
        return parsed
    }

    fun syncMaxEventsInput() {
        maxEventsInput = state.maxEvents.toString()
    }

    fun trimEvents() {
        val max = state.maxEvents.coerceAtLeast(1)
        val excess = state.events.size - max
        if (excess > 0) {
            state.events.subList(0, excess).clear()
        }
    }

    fun flushPending() {
        if (pendingEvents.isEmpty()) return
        state.events.addAll(pendingEvents)
        pendingEvents.clear()
        trimEvents()
        lastFlushAt = System.currentTimeMillis()
    }

    fun scheduleFlush() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastFlushAt
        if (elapsed >= 200) {
            flushPending()
            return
        }
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            delay((200 - elapsed).coerceAtLeast(0))
            flushPending()
        }
    }

    fun pushEvent(ev: TopicBusEvent) {
        pendingEvents.add(ev)
        scheduleFlush()
    }

    fun savePrefs() {
        Prefs.saveTopicBusPrefs(context, state.topics, state.maxEvents)
    }

    fun clearEvents() {
        pendingEvents.clear()
        state.events.clear()
        selectedEventIndex = -1
        ui.info("已清空事件")
    }

    suspend fun subscribeTopics(topics: List<String>) {
        val g = ensureGo()
        ensureConnected(g)
        val sourceId = parseSelfNodeId()
        val targetId = resolveTargetId()
        val normalized = normalizeTopics(topics)
        if (normalized.isEmpty()) throw IllegalStateException("Topic is required.")

        withContext(Dispatchers.IO) {
            if (normalized.size == 1) {
                g.topicBusSubscribe(sourceId.toString(), targetId.toString(), normalized[0])
            } else {
                val arr = JSONArray()
                for (t in normalized) arr.put(t)
                g.topicBusSubscribeBatch(sourceId.toString(), targetId.toString(), arr.toString())
            }
        }
    }

    suspend fun unsubscribeTopics(topics: List<String>) {
        val g = ensureGo()
        ensureConnected(g)
        val sourceId = parseSelfNodeId()
        val targetId = resolveTargetId()
        val normalized = normalizeTopics(topics)
        if (normalized.isEmpty()) throw IllegalStateException("Topic is required.")

        withContext(Dispatchers.IO) {
            if (normalized.size == 1) {
                g.topicBusUnsubscribe(sourceId.toString(), targetId.toString(), normalized[0])
            } else {
                val arr = JSONArray()
                for (t in normalized) arr.put(t)
                g.topicBusUnsubscribeBatch(sourceId.toString(), targetId.toString(), arr.toString())
            }
        }
    }

    suspend fun publishEvent() {
        val g = ensureGo()
        ensureConnected(g)
        val sourceId = parseSelfNodeId()
        val targetId = resolveTargetId()
        val topic = publishTopic.trim()
        val name = publishName.trim()
        if (topic.isBlank()) throw IllegalStateException("Topic is required.")
        if (name.isBlank()) throw IllegalStateException("Name is required.")
        withContext(Dispatchers.IO) {
            g.topicBusPublish(sourceId.toString(), targetId.toString(), topic, name, publishPayload)
        }
    }

    val filteredEvents by remember {
        derivedStateOf {
            val selected = state.selectedTopic
            if (selected.isBlank()) state.events.toList()
            else state.events.filter { it.topic == selected }
        }
    }

    val selectedEvent by remember {
        derivedStateOf { filteredEvents.getOrNull(selectedEventIndex) }
    }

    LaunchedEffect(Unit) {
        val prefs = Prefs.loadTopicBusPrefs(context)
        state.topics.clear()
        state.topics.addAll(normalizeTopics(prefs.topics))
        state.maxEvents = prefs.maxEvents.coerceAtLeast(1)
        syncMaxEventsInput()
        trimEvents()
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
            val ok = withContext(Dispatchers.IO) { runCatching { g.isConnected() }.getOrDefault(false) }
            connected = ok
            delay(800)
        }
    }

    LaunchedEffect(go) {
        var cursor = "0"
        while (isActive) {
            val g = go
            if (g == null) {
                delay(800)
                continue
            }
            val ok = withContext(Dispatchers.IO) { runCatching { g.isConnected() }.getOrDefault(false) }
            if (!ok) {
                delay(800)
                continue
            }

            try {
                val respRaw = withContext(Dispatchers.IO) { g.topicBusEventsPull(cursor, "200") }
                val obj = JSONObject(respRaw)
                val next = obj.optLong("next_cursor", obj.optLong("nextCursor", 0)).toString()
                val arr = obj.optJSONArray("events") ?: JSONArray()
                val hasMore = obj.optBoolean("has_more", false)
                cursor = next

                if (arr.length() == 0) {
                    delay(300)
                    continue
                }

                for (i in 0 until arr.length()) {
                    val evt = arr.optJSONObject(i) ?: continue
                    val dropped = evt.optBoolean("dropped", false)
                    if (dropped) {
                        pushEvent(
                            TopicBusEvent(
                                topic = "(dropped)",
                                name = "payload too large",
                                ts = 0L,
                                dataRaw = "TopicBus event dropped (payload too large).",
                            ),
                        )
                        continue
                    }
                    val topic = evt.optString("topic", "").trim()
                    val name = evt.optString("name", "").trim()
                    val ts = evt.optLong("ts", 0L)
                    val data = evt.opt("data")
                    if (topic.isBlank() || name.isBlank()) continue

                    val dataRaw = when (data) {
                        is JSONObject -> data.toString(2)
                        is JSONArray -> data.toString(2)
                        null -> ""
                        else -> formatDetailJson(data.toString())
                    }
                    pushEvent(TopicBusEvent(topic = topic, name = name, ts = ts, dataRaw = dataRaw))
                }
                state.lastFrameAt = nowIso()

                if (!hasMore) {
                    delay(150)
                }
            } catch (_: CancellationException) {
                return@LaunchedEffect
            } catch (_: Throwable) {
                delay(800)
            }
        }
    }

    LaunchedEffect(connected, cfg.nodeId, cfg.hubId, state.topics.size) {
        if (busy) return@LaunchedEffect
        val nodeId = cfg.nodeId.trim()
        val hubId = cfg.hubId.trim()
        if (!connected || nodeId.isBlank()) return@LaunchedEffect
        if (state.topics.isEmpty()) return@LaunchedEffect

        val key = buildString {
            append(nodeId)
            append("#")
            append(hubId)
            append("#")
            append(state.topics.joinToString("\n"))
        }
        if (key == lastAutoResubKey) return@LaunchedEffect
        lastAutoResubKey = key

        val token = beginOp("正在 Resubscribe…")
        try {
            subscribeTopics(state.topics)
            ui.success("Resubscribed.")
        } catch (_: CancellationException) {
            // ignore
        } catch (t: Throwable) {
            if (opSeq != token) return@LaunchedEffect
            ui.error("Resubscribe 失败：${t.message ?: t}")
        } finally {
            endOp(token)
        }
    }

    LaunchedEffect(state.selectedTopic) {
        selectedEventIndex = -1
    }

    LaunchedEffect(filteredEvents.size) {
        if (selectedEventIndex >= filteredEvents.size) {
            selectedEventIndex = -1
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().imePadding().padding(16.dp)) {
        val isWide = maxWidth >= 900.dp
        val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        val cardElevation = CardDefaults.cardElevation(defaultElevation = 1.dp)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (busyLabel.isNotBlank()) {
                    Text(busyLabel, style = MaterialTheme.typography.bodySmall)
                }
            }

            fun canSend(): Boolean = connected && cfg.nodeId.trim().isNotBlank()

            val onResubscribe = {
                val token = beginOp("正在 Resubscribe…")
                val job = scope.launch {
                    try {
                        if (state.topics.isEmpty()) {
                            ui.info("No topics to resubscribe.")
                            return@launch
                        }
                        subscribeTopics(state.topics)
                        ui.success("Resubscribed.")
                    } catch (_: CancellationException) {
                        // ignore
                    } catch (t: Throwable) {
                        if (opSeq != token) return@launch
                        ui.error("Resubscribe 失败：${t.message ?: t}")
                    } finally {
                        endOp(token)
                    }
                }
                opJob = job
            }

            val onUnsubscribeSelected = {
                val token = beginOp("正在 Unsubscribe…")
                val job = scope.launch {
                    try {
                        val topic = state.selectedTopic.trim()
                        if (topic.isBlank()) throw IllegalStateException("Select a topic to unsubscribe.")

                        val updated = removeTopics(state.topics, listOf(topic))
                        state.topics.clear()
                        state.topics.addAll(updated)
                        if (state.selectedTopic == topic) state.selectedTopic = ""
                        savePrefs()

                        if (!canSend()) {
                            ui.info("已更新订阅列表；登录后可发送 Unsubscribe。")
                            return@launch
                        }

                        unsubscribeTopics(listOf(topic))
                        ui.success("Unsubscribed.")
                    } catch (_: CancellationException) {
                        // ignore
                    } catch (t: Throwable) {
                        if (opSeq != token) return@launch
                        ui.error("Unsubscribe 失败：${t.message ?: t}")
                    } finally {
                        endOp(token)
                    }
                }
                opJob = job
            }

            val onSubscribeFromInput = {
                val token = beginOp("正在 Subscribe…")
                val job = scope.launch {
                    try {
                        val topics = parseTopics(subText)
                        if (topics.isEmpty()) throw IllegalStateException("Topic is required.")

                        val updated = mergeTopics(state.topics, topics)
                        state.topics.clear()
                        state.topics.addAll(updated)
                        savePrefs()

                        if (!canSend()) {
                            ui.info("已保存订阅列表；登录后可发送 Subscribe。")
                            return@launch
                        }

                        subscribeTopics(topics)
                        ui.success("Subscribed.")
                    } catch (_: CancellationException) {
                        // ignore
                    } catch (t: Throwable) {
                        if (opSeq != token) return@launch
                        ui.error("Subscribe 失败：${t.message ?: t}")
                    } finally {
                        endOp(token)
                    }
                }
                opJob = job
            }

            val onUnsubscribeFromInput = {
                val token = beginOp("正在 Unsubscribe…")
                val job = scope.launch {
                    try {
                        val topics = parseTopics(subText)
                        if (topics.isEmpty()) throw IllegalStateException("Topic is required.")

                        val updated = removeTopics(state.topics, topics)
                        state.topics.clear()
                        state.topics.addAll(updated)
                        if (state.selectedTopic.isNotBlank() && state.selectedTopic !in state.topics) {
                            state.selectedTopic = ""
                        }
                        savePrefs()

                        if (!canSend()) {
                            ui.info("已更新订阅列表；登录后可发送 Unsubscribe。")
                            return@launch
                        }

                        unsubscribeTopics(topics)
                        ui.success("Unsubscribed.")
                    } catch (_: CancellationException) {
                        // ignore
                    } catch (t: Throwable) {
                        if (opSeq != token) return@launch
                        ui.error("Unsubscribe 失败：${t.message ?: t}")
                    } finally {
                        endOp(token)
                    }
                }
                opJob = job
            }

            val onApplyMaxEvents = {
                val token = beginOp("正在更新 Max Events…")
                val job = scope.launch {
                    try {
                        val parsed = maxEventsInput.trim().toIntOrNull()
                            ?: throw IllegalStateException("Max events must be a positive number.")
                        if (parsed <= 0) throw IllegalStateException("Max events must be a positive number.")
                        state.maxEvents = parsed
                        trimEvents()
                        savePrefs()
                        syncMaxEventsInput()
                        ui.success("Max events updated.")
                    } catch (_: CancellationException) {
                        // ignore
                    } catch (t: Throwable) {
                        if (opSeq != token) return@launch
                        ui.error("更新失败：${t.message ?: t}")
                    } finally {
                        endOp(token)
                    }
                }
                opJob = job
            }

            val onUseSelected = {
                val selected = state.selectedTopic.trim()
                if (selected.isBlank()) {
                    ui.info("Select a topic to populate the publish form.")
                } else {
                    publishTopic = selected
                }
            }

            val onClearPublish = {
                publishTopic = ""
                publishName = ""
                publishPayload = ""
            }

            val onPublish = {
                val token = beginOp("正在 Publish…")
                val job = scope.launch {
                    try {
                        publishEvent()
                        ui.success("Event published.")
                    } catch (_: CancellationException) {
                        // ignore
                    } catch (t: Throwable) {
                        if (opSeq != token) return@launch
                        ui.error("Publish 失败：${t.message ?: t}")
                    } finally {
                        endOp(token)
                    }
                }
                opJob = job
            }

            if (isWide) {
                TopicBusWideLayout(
                    cardColors = cardColors,
                    cardElevation = cardElevation,
                    connected = connected,
                    state = state,
                    busy = busy,
                    subText = subText,
                    onSubTextChange = { subText = it },
                    maxEventsInput = maxEventsInput,
                    onMaxEventsInputChange = { maxEventsInput = it },
                    publishTopic = publishTopic,
                    onPublishTopicChange = { publishTopic = it },
                    publishName = publishName,
                    onPublishNameChange = { publishName = it },
                    publishPayload = publishPayload,
                    onPublishPayloadChange = { publishPayload = it },
                    filteredEvents = filteredEvents,
                    selectedEventIndex = selectedEventIndex,
                    onSelectEventIndex = { selectedEventIndex = it },
                    selectedEvent = selectedEvent,
                    onResubscribe = onResubscribe,
                    onUnsubscribeSelected = onUnsubscribeSelected,
                    onSubscribeFromInput = onSubscribeFromInput,
                    onUnsubscribeFromInput = onUnsubscribeFromInput,
                    onApplyMaxEvents = onApplyMaxEvents,
                    onClearEvents = ::clearEvents,
                    onUseSelected = onUseSelected,
                    onClearPublish = onClearPublish,
                    onPublish = onPublish,
                )
            } else {
                TopicBusNarrowLayout(
                    cardColors = cardColors,
                    cardElevation = cardElevation,
                    connected = connected,
                    state = state,
                    busy = busy,
                    subText = subText,
                    onSubTextChange = { subText = it },
                    maxEventsInput = maxEventsInput,
                    onMaxEventsInputChange = { maxEventsInput = it },
                    publishTopic = publishTopic,
                    onPublishTopicChange = { publishTopic = it },
                    publishName = publishName,
                    onPublishNameChange = { publishName = it },
                    publishPayload = publishPayload,
                    onPublishPayloadChange = { publishPayload = it },
                    filteredEvents = filteredEvents,
                    selectedEventIndex = selectedEventIndex,
                    onSelectEventIndex = { selectedEventIndex = it },
                    selectedEvent = selectedEvent,
                    onResubscribe = onResubscribe,
                    onUnsubscribeSelected = onUnsubscribeSelected,
                    onSubscribeFromInput = onSubscribeFromInput,
                    onUnsubscribeFromInput = onUnsubscribeFromInput,
                    onApplyMaxEvents = onApplyMaxEvents,
                    onClearEvents = ::clearEvents,
                    onUseSelected = onUseSelected,
                    onClearPublish = onClearPublish,
                    onPublish = onPublish,
                )
            }
        }
    }
}
