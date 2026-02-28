package com.myflowhub.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.myflowhub.android.GoClientBridge
import com.myflowhub.android.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private typealias NodeInfoPair = Pair<Long, Boolean>

@Stable
private class DeviceTreeNode(
    val key: String,
    val nodeId: Long,
    hasChildrenHint: Boolean,
    val duplicate: Boolean,
) {
    var hasChildrenHint by mutableStateOf(hasChildrenHint)
    var expanded by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var error by mutableStateOf("")
    var children by mutableStateOf<List<DeviceTreeNode>?>(null)
}

@Composable
fun DevicesScreen(
    modifier: Modifier = Modifier,
    go: GoClientBridge?,
    goError: String,
    cfg: Prefs.ClientConfig,
    ui: UiNotifier,
) {
    val scope = rememberCoroutineScope()

    var rootTargetId by remember { mutableStateOf("") }
    var root by remember { mutableStateOf<DeviceTreeNode?>(null) }

    var detailsNodeId by remember { mutableStateOf<Long?>(null) }
    var editNodeId by remember { mutableStateOf<Long?>(null) }

    val seenNodeIds = remember { mutableSetOf<Long>() }
    val nodeIndex = remember { mutableMapOf<String, DeviceTreeNode>() }

    fun toErrorMessage(err: Throwable): String = err.message ?: err.toString()

    fun ensureIdentity(): Pair<String, String> {
        if (go == null) throw IllegalStateException("Go AAR unavailable")
        if (!runCatching { go.isConnected() }.getOrDefault(false)) throw IllegalStateException("Connect before querying devices.")
        val sourceId = cfg.nodeId.trim()
        val hubId = cfg.hubId.trim()
        if (sourceId.isBlank()) throw IllegalStateException("Login required to query devices.")
        if (hubId.isBlank()) throw IllegalStateException("Hub ID missing.")
        return sourceId to hubId
    }

    fun parseRootId(hubId: String): Long {
        val raw = rootTargetId.trim().ifBlank { hubId }
        val id = raw.toLongOrNull() ?: throw IllegalStateException("Root node id 格式非法：'$raw'（期望为正整数）")
        if (id <= 0) throw IllegalStateException("Root node id 必须为正整数")
        return id
    }

    fun parseNodes(resp: String): List<NodeInfoPair> {
        val obj = JSONObject(resp)
        val code = obj.optInt("code", 0)
        if (code != 1) {
            val msg = obj.optString("msg", "").ifBlank { "request failed (code=$code)" }
            throw IllegalStateException(msg)
        }
        val nodes = obj.optJSONArray("nodes") ?: JSONArray()
        val out = mutableListOf<NodeInfoPair>()
        for (i in 0 until nodes.length()) {
            val n = nodes.optJSONObject(i) ?: continue
            val id = n.optLong("node_id", n.optLong("nodeId", 0))
            if (id <= 0) continue
            val hasChildren = n.optBoolean("has_children", n.optBoolean("hasChildren", false))
            out.add(id to hasChildren)
        }
        return out.sortedBy { it.first }
    }

    fun makeChildKey(parentKey: String, nodeId: Long): String {
        val base = "$parentKey/$nodeId"
        var key = base
        var suffix = 1
        while (nodeIndex.containsKey(key)) {
            suffix++
            key = "$base@$suffix"
        }
        return key
    }

    fun registerNode(node: DeviceTreeNode) {
        nodeIndex[node.key] = node
    }

    fun buildChildren(parentKey: String, children: List<NodeInfoPair>, selfNodeId: Long): List<DeviceTreeNode> {
        val list = mutableListOf<DeviceTreeNode>()
        for ((id, hasChildren) in children) {
            if (id == selfNodeId) continue
            val duplicate = seenNodeIds.contains(id)
            if (!duplicate) {
                seenNodeIds.add(id)
            }
            val node = DeviceTreeNode(
                key = makeChildKey(parentKey, id),
                nodeId = id,
                hasChildrenHint = hasChildren,
                duplicate = duplicate,
            )
            registerNode(node)
            list.add(node)
        }
        return list
    }

    fun clearTree() {
        root = null
        nodeIndex.clear()
        seenNodeIds.clear()
        detailsNodeId = null
        editNodeId = null
    }

    fun loadRoot() {
        scope.launch {
            ui.progress("正在加载设备树…")
            try {
                val (sourceId, hubId) = ensureIdentity()
                val rootId = parseRootId(hubId)
                val rootNode = DeviceTreeNode(
                    key = "root:$rootId",
                    nodeId = rootId,
                    hasChildrenHint = true,
                    duplicate = false,
                ).also { it.expanded = true; it.loading = true }

                clearTree()
                root = rootNode
                registerNode(rootNode)
                seenNodeIds.add(rootId)

                val resp = withContext(Dispatchers.IO) {
                    go!!.listNodes(sourceId, rootId.toString())
                }
                val children = parseNodes(resp).filter { it.first != rootId }
                rootNode.children = buildChildren(rootNode.key, children, rootId)
                rootNode.hasChildrenHint = rootNode.children?.isNotEmpty() == true
                rootNode.loading = false
                ui.success("已加载 Root $rootId")
            } catch (t: Throwable) {
                val msg = toErrorMessage(t)
                root?.loading = false
                root?.error = msg
                ui.error("加载失败：$msg")
            }
        }
    }

    fun loadChildren(node: DeviceTreeNode, sourceId: String) {
        if (node.duplicate) return
        if (node.loading) return
        if (node.children != null) return
        scope.launch {
            node.loading = true
            node.error = ""
            try {
                val resp = withContext(Dispatchers.IO) {
                    go!!.listNodes(sourceId, node.nodeId.toString())
                }
                val children = parseNodes(resp).filter { it.first != node.nodeId }
                node.children = buildChildren(node.key, children, node.nodeId)
                node.hasChildrenHint = node.children?.isNotEmpty() == true
                node.loading = false
            } catch (t: Throwable) {
                node.loading = false
                node.error = toErrorMessage(t)
                node.children = null
            }
        }
    }

    fun toggle(node: DeviceTreeNode) {
        if (node.duplicate) return
        if (node.expanded) {
            node.expanded = false
            return
        }
        node.expanded = true
        if (node.children != null) return
        val sourceId = cfg.nodeId.trim()
        if (go == null || sourceId.isBlank()) {
            node.error = "Login required."
            node.expanded = false
            return
        }
        loadChildren(node, sourceId)
    }

    fun retry(node: DeviceTreeNode) {
        if (node.duplicate) return
        if (node.loading) return
        val sourceId = cfg.nodeId.trim()
        if (go == null || sourceId.isBlank()) {
            node.error = "Login required."
            return
        }
        node.children = null
        node.expanded = true
        loadChildren(node, sourceId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (go == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Go AAR 不可用", fontWeight = FontWeight.SemiBold)
                    Text(goError.ifBlank { "unknown error" })
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = rootTargetId,
                        onValueChange = { rootTargetId = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Root node id") },
                        placeholder = { Text(cfg.hubId.ifBlank { "默认使用 Hub ID" }) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    FilledTonalButton(
                        enabled = go != null,
                        onClick = { loadRoot() },
                    ) { Text("Load") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Nodes", fontWeight = FontWeight.SemiBold)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val rootNode = root
                    if (rootNode == null) {
                        Text("No data. Load to start.")
                    } else {
                        TreeNodeView(
                            node = rootNode,
                            depth = 0,
                            onToggle = { toggle(it) },
                            onRetry = { retry(it) },
                            onShowDetails = { detailsNodeId = it.nodeId },
                            onEdit = { editNodeId = it.nodeId },
                        )
                    }
                }
            }
        }
    }

    val detailsId = detailsNodeId
    if (detailsId != null) {
        NodeDetailsDialog(
            nodeId = detailsId,
            go = go,
            cfg = cfg,
            ui = ui,
            onDismiss = { detailsNodeId = null },
        )
    }

    val editId = editNodeId
    if (editId != null) {
        NodeEditDialog(
            nodeId = editId,
            go = go,
            cfg = cfg,
            ui = ui,
            onDismiss = { editNodeId = null },
        )
    }
}

@Composable
private fun TreeNodeView(
    node: DeviceTreeNode,
    depth: Int,
    onToggle: (DeviceTreeNode) -> Unit,
    onRetry: (DeviceTreeNode) -> Unit,
    onShowDetails: (DeviceTreeNode) -> Unit,
    onEdit: (DeviceTreeNode) -> Unit,
) {
    val indent = (depth * 14).dp
    val isRoot = depth == 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val canExpand = node.hasChildrenHint && !node.duplicate
                val glyph = when {
                    !canExpand -> "•"
                    node.expanded -> "-"
                    else -> "+"
                }
                OutlinedButton(
                    enabled = canExpand,
                    onClick = { onToggle(node) },
                    modifier = Modifier.width(42.dp).height(42.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(glyph)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onShowDetails(node) }
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = buildString {
                            append("Node ")
                            append(node.nodeId)
                            if (isRoot) append(" (root)")
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    val subtitle = when {
                        node.duplicate -> "Duplicate: expansion disabled."
                        node.loading -> "Loading…"
                        node.error.isNotBlank() -> "Error: ${node.error}"
                        node.expanded && node.children != null -> "Children: ${node.children?.size ?: 0}"
                        node.hasChildrenHint -> "Not loaded."
                        else -> "No children."
                    }
                    Text(subtitle)
                }

                val statusLabel = when {
                    node.duplicate -> "Duplicate"
                    node.error.isNotBlank() -> "Unknown"
                    node.hasChildrenHint -> "Has children"
                    else -> "Unknown"
                }
                AssistChip(
                    onClick = {},
                    label = { Text(statusLabel) },
                )

                OutlinedButton(onClick = { onEdit(node) }) { Text("Edit") }

                if (node.error.isNotBlank() && !node.duplicate) {
                    OutlinedButton(onClick = { onRetry(node) }) { Text("Retry") }
                }
            }

            val children = node.children
            if (node.expanded && children != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    children.forEach { child ->
                        TreeNodeView(
                            node = child,
                            depth = depth + 1,
                            onToggle = onToggle,
                            onRetry = onRetry,
                            onShowDetails = onShowDetails,
                            onEdit = onEdit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeDetailsDialog(
    nodeId: Long,
    go: GoClientBridge?,
    cfg: Prefs.ClientConfig,
    ui: UiNotifier,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun toErrorMessage(err: Throwable): String = err.message ?: err.toString()

    suspend fun load() {
        loading = true
        error = ""
        items = emptyMap()
        try {
            val g = go ?: throw IllegalStateException("Go AAR unavailable")
            val sourceId = cfg.nodeId.trim()
            if (sourceId.isBlank()) throw IllegalStateException("Login required.")
            val connected = withContext(Dispatchers.IO) { runCatching { g.isConnected() }.getOrDefault(false) }
            if (!connected) throw IllegalStateException("Connect before querying devices.")

            val resp = withContext(Dispatchers.IO) {
                g.nodeInfo(sourceId, nodeId.toString())
            }
            val obj = JSONObject(resp)
            val code = obj.optInt("code", 0)
            if (code != 1) {
                val msg = obj.optString("msg", "").ifBlank { "request failed (code=$code)" }
                throw IllegalStateException(msg)
            }
            val itemsObj = obj.optJSONObject("items") ?: JSONObject()
            val keys = itemsObj.keys()
            val map = mutableMapOf<String, String>()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = itemsObj.optString(k, "")
            }
            items = map.toSortedMap()
        } catch (t: Throwable) {
            error = toErrorMessage(t)
            ui.error("加载详情失败：$error")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(nodeId, go, cfg.nodeId) {
        load()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Node $nodeId") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (error.isNotBlank()) {
                    Text("Error: $error")
                }
                if (items.isNotEmpty()) {
                    items.entries.take(30).forEach { (k, v) ->
                        Text("$k: $v")
                    }
                    if (items.size > 30) {
                        Text("… (${items.size} items)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            if (!loading) {
                TextButton(onClick = { scope.launch { load() } }) { Text("Reload") }
            }
        },
    )
}

@Composable
private fun NodeEditDialog(
    nodeId: Long,
    go: GoClientBridge?,
    cfg: Prefs.ClientConfig,
    ui: UiNotifier,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("") }

    var configKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var configKey by remember { mutableStateOf("") }
    var configValue by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    fun toErrorMessage(err: Throwable): String = err.message ?: err.toString()

    fun begin(label: String) {
        busy = true
        busyLabel = label
        message = ""
        ui.progress(label)
    }

    fun end() {
        busy = false
        busyLabel = ""
    }

    fun requireSourceId(): String {
        val g = go ?: throw IllegalStateException("Go AAR unavailable")
        val sourceId = cfg.nodeId.trim()
        if (sourceId.isBlank()) throw IllegalStateException("Login required.")
        val connected = runCatching { g.isConnected() }.getOrDefault(false)
        if (!connected) throw IllegalStateException("Connect before editing.")
        return sourceId
    }

    fun configList() {
        scope.launch {
            begin("正在加载配置 Key 列表…")
            try {
                val sourceId = requireSourceId()
                val resp = withContext(Dispatchers.IO) {
                    go!!.configList(sourceId, nodeId.toString())
                }
                val obj = JSONObject(resp)
                val code = obj.optInt("code", 0)
                if (code != 1) {
                    val msg = obj.optString("msg", "").ifBlank { "request failed (code=$code)" }
                    throw IllegalStateException(msg)
                }
                val keys = obj.optJSONArray("keys") ?: JSONArray()
                val list = mutableListOf<String>()
                for (i in 0 until keys.length()) {
                    val k = keys.optString(i, "")
                    if (k.isNotBlank()) list.add(k)
                }
                configKeys = list.sorted()
                message = "Loaded ${configKeys.size} keys."
                ui.success("已加载 ${configKeys.size} 个 Key")
            } catch (t: Throwable) {
                message = toErrorMessage(t)
                ui.error("加载失败：$message")
            } finally {
                end()
            }
        }
    }

    fun configGet() {
        scope.launch {
            begin("正在读取配置…")
            try {
                val sourceId = requireSourceId()
                val key = configKey.trim()
                if (key.isBlank()) throw IllegalStateException("Key 不能为空")
                val resp = withContext(Dispatchers.IO) {
                    go!!.configGet(sourceId, nodeId.toString(), key)
                }
                val obj = JSONObject(resp)
                val code = obj.optInt("code", 0)
                if (code != 1) {
                    val msg = obj.optString("msg", "").ifBlank { "request failed (code=$code)" }
                    throw IllegalStateException(msg)
                }
                configValue = obj.optString("value", "")
                message = "OK"
                ui.success("已读取")
            } catch (t: Throwable) {
                message = toErrorMessage(t)
                ui.error("读取失败：$message")
            } finally {
                end()
            }
        }
    }

    fun configSet() {
        scope.launch {
            begin("正在保存配置…")
            try {
                val sourceId = requireSourceId()
                val key = configKey.trim()
                if (key.isBlank()) throw IllegalStateException("Key 不能为空")
                val resp = withContext(Dispatchers.IO) {
                    go!!.configSet(sourceId, nodeId.toString(), key, configValue)
                }
                val obj = JSONObject(resp)
                val code = obj.optInt("code", 0)
                if (code != 1) {
                    val msg = obj.optString("msg", "").ifBlank { "request failed (code=$code)" }
                    throw IllegalStateException(msg)
                }
                message = "Saved."
                ui.success("已保存")
            } catch (t: Throwable) {
                message = toErrorMessage(t)
                ui.error("保存失败：$message")
            } finally {
                end()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Node $nodeId") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (busyLabel.isNotBlank()) {
                        Text(busyLabel)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(enabled = !busy, onClick = { configList() }, modifier = Modifier.weight(1f)) {
                        Text("List")
                    }
                    FilledTonalButton(enabled = !busy, onClick = { configGet() }, modifier = Modifier.weight(1f)) {
                        Text("Get")
                    }
                    FilledTonalButton(enabled = !busy, onClick = { configSet() }, modifier = Modifier.weight(1f)) {
                        Text("Set")
                    }
                }

                if (configKeys.isNotEmpty()) {
                    Text("Keys: ${configKeys.size}")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        configKeys.take(8).forEach { k ->
                            AssistChip(
                                onClick = {
                                    configKey = k
                                    message = ""
                                },
                                label = { Text(k) },
                            )
                        }
                        if (configKeys.size > 8) {
                            Text("…")
                        }
                    }
                }

                OutlinedTextField(
                    value = configKey,
                    onValueChange = { configKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Key") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = configValue,
                    onValueChange = { configValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Value") },
                    singleLine = true,
                )
                if (message.isNotBlank()) {
                    AssistChip(onClick = {}, label = { Text(message) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
