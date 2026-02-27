package com.myflowhub.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myflowhub.android.GoClientBridge
import com.myflowhub.android.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private typealias NodeInfoPair = Pair<Long, Boolean>

private enum class DevicesMode { Direct, Subtree }

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

    var mode by remember { mutableStateOf(DevicesMode.Direct) }
    var rootTargetId by remember { mutableStateOf(cfg.hubId) }
    var root by remember { mutableStateOf<DeviceTreeNode?>(null) }
    var status by remember { mutableStateOf("") }

    var selectedNodeId by remember { mutableStateOf(0L) }
    var nodeInfo by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var nodeInfoError by remember { mutableStateOf("") }

    var configKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var configKey by remember { mutableStateOf("") }
    var configValue by remember { mutableStateOf("") }
    var configMessage by remember { mutableStateOf("") }

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
        if (rootTargetId.isBlank()) {
            rootTargetId = hubId
        }
        return sourceId to hubId
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
        selectedNodeId = 0
        nodeInfo = emptyMap()
        nodeInfoError = ""
        configKeys = emptyList()
        configKey = ""
        configValue = ""
        configMessage = ""
    }

    fun loadRoot() {
        scope.launch {
            status = ""
            ui.progress("正在加载设备树…")
            try {
                val (sourceId, hubId) = ensureIdentity()
                val rootId = rootTargetId.trim().ifBlank { hubId }.toLong()
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
                    if (mode == DevicesMode.Subtree) {
                        go!!.listSubtree(sourceId, rootId.toString())
                    } else {
                        go!!.listNodes(sourceId, rootId.toString())
                    }
                }
                val children = parseNodes(resp).filter { it.first != rootId }
                rootNode.children = buildChildren(rootNode.key, children, rootId)
                rootNode.hasChildrenHint = rootNode.children?.isNotEmpty() == true
                rootNode.loading = false
                status = "Loaded root $rootId"
                ui.success("已加载 Root $rootId")
            } catch (t: Throwable) {
                val msg = toErrorMessage(t)
                status = msg
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
        if (sourceId.isBlank()) {
            node.error = "Login required."
            node.expanded = false
            return
        }
        loadChildren(node, sourceId)
    }

    fun select(nodeId: Long) {
        selectedNodeId = nodeId
        nodeInfo = emptyMap()
        nodeInfoError = ""
        configKeys = emptyList()
        configKey = ""
        configValue = ""
        configMessage = ""

        val sourceId = cfg.nodeId.trim()
        if (go == null || sourceId.isBlank() || nodeId <= 0) {
            return
        }
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    go.nodeInfo(sourceId, nodeId.toString())
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
                nodeInfo = map.toSortedMap()
            } catch (t: Throwable) {
                nodeInfoError = toErrorMessage(t)
            }
        }
    }

    fun configList() {
        val sourceId = cfg.nodeId.trim()
        val targetId = selectedNodeId
        if (go == null || sourceId.isBlank() || targetId <= 0) return
        scope.launch {
            configMessage = ""
            ui.progress("正在加载配置 Key 列表…")
            try {
                val resp = withContext(Dispatchers.IO) {
                    go.configList(sourceId, targetId.toString())
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
                configMessage = "Loaded ${configKeys.size} keys."
                ui.success("已加载 ${configKeys.size} 个 Key")
            } catch (t: Throwable) {
                configMessage = toErrorMessage(t)
                ui.error("加载失败：${configMessage}")
            }
        }
    }

    fun configGet() {
        val sourceId = cfg.nodeId.trim()
        val targetId = selectedNodeId
        val key = configKey.trim()
        if (go == null || sourceId.isBlank() || targetId <= 0 || key.isBlank()) return
        scope.launch {
            configMessage = ""
            ui.progress("正在读取配置…")
            try {
                val resp = withContext(Dispatchers.IO) {
                    go.configGet(sourceId, targetId.toString(), key)
                }
                val obj = JSONObject(resp)
                val code = obj.optInt("code", 0)
                if (code != 1) {
                    val msg = obj.optString("msg", "").ifBlank { "request failed (code=$code)" }
                    throw IllegalStateException(msg)
                }
                configValue = obj.optString("value", "")
                configMessage = "OK"
                ui.success("已读取")
            } catch (t: Throwable) {
                configMessage = toErrorMessage(t)
                ui.error("读取失败：${configMessage}")
            }
        }
    }

    fun configSet() {
        val sourceId = cfg.nodeId.trim()
        val targetId = selectedNodeId
        val key = configKey.trim()
        if (go == null || sourceId.isBlank() || targetId <= 0 || key.isBlank()) return
        scope.launch {
            configMessage = ""
            ui.progress("正在保存配置…")
            try {
                val resp = withContext(Dispatchers.IO) {
                    go.configSet(sourceId, targetId.toString(), key, configValue)
                }
                val obj = JSONObject(resp)
                val code = obj.optInt("code", 0)
                if (code != 1) {
                    val msg = obj.optString("msg", "").ifBlank { "request failed (code=$code)" }
                    throw IllegalStateException(msg)
                }
                configMessage = "Saved."
                ui.success("已保存")
            } catch (t: Throwable) {
                configMessage = toErrorMessage(t)
                ui.error("保存失败：${configMessage}")
            }
        }
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
                Text("浏览", fontWeight = FontWeight.SemiBold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (mode == DevicesMode.Direct) {
                        FilledTonalButton(modifier = Modifier.weight(1f), onClick = {}) { Text("Direct") }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = go != null,
                            onClick = { mode = DevicesMode.Direct },
                        ) { Text("Direct") }
                    }

                    if (mode == DevicesMode.Subtree) {
                        FilledTonalButton(modifier = Modifier.weight(1f), onClick = {}) { Text("Subtree") }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = go != null,
                            onClick = { mode = DevicesMode.Subtree },
                        ) { Text("Subtree") }
                    }

                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        enabled = go != null,
                        onClick = { loadRoot() },
                    ) { Text("Load") }
                }

                OutlinedTextField(
                    value = rootTargetId,
                    onValueChange = { rootTargetId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Root target node id") },
                    placeholder = { Text("默认使用 Hub ID") },
                    singleLine = true,
                )

                if (status.isNotBlank()) {
                    AssistChip(onClick = {}, label = { Text(status) })
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
            val isWide = maxWidth >= 900.dp
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DeviceTreePane(
                        modifier = Modifier.weight(0.45f).fillMaxHeight(),
                        root = root,
                        onToggle = { toggle(it) },
                        onSelect = { select(it) },
                    )
                    DeviceDetailsPane(
                        modifier = Modifier.weight(0.55f).fillMaxHeight(),
                        selectedNodeId = selectedNodeId,
                        nodeInfo = nodeInfo,
                        nodeInfoError = nodeInfoError,
                        configKeys = configKeys,
                        configKey = configKey,
                        configValue = configValue,
                        configMessage = configMessage,
                        onConfigKeyChange = { configKey = it },
                        onConfigValueChange = { configValue = it },
                        onConfigKeyPick = { picked ->
                            configKey = picked
                            configMessage = ""
                        },
                        onConfigList = { configList() },
                        onConfigGet = { configGet() },
                        onConfigSet = { configSet() },
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeviceTreePane(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
                        root = root,
                        onToggle = { toggle(it) },
                        onSelect = { select(it) },
                    )
                    DeviceDetailsPane(
                        modifier = Modifier.fillMaxWidth(),
                        selectedNodeId = selectedNodeId,
                        nodeInfo = nodeInfo,
                        nodeInfoError = nodeInfoError,
                        configKeys = configKeys,
                        configKey = configKey,
                        configValue = configValue,
                        configMessage = configMessage,
                        onConfigKeyChange = { configKey = it },
                        onConfigValueChange = { configValue = it },
                        onConfigKeyPick = { picked ->
                            configKey = picked
                            configMessage = ""
                        },
                        onConfigList = { configList() },
                        onConfigGet = { configGet() },
                        onConfigSet = { configSet() },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceTreePane(
    modifier: Modifier,
    root: DeviceTreeNode?,
    onToggle: (DeviceTreeNode) -> Unit,
    onSelect: (Long) -> Unit,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tree", fontWeight = FontWeight.SemiBold)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (root == null) {
                    Text("No data. Load to start.")
                } else {
                    TreeNodeView(node = root, depth = 0, onToggle = onToggle, onSelect = { onSelect(it.nodeId) })
                }
            }
        }
    }
}

@Composable
private fun DeviceDetailsPane(
    modifier: Modifier,
    selectedNodeId: Long,
    nodeInfo: Map<String, String>,
    nodeInfoError: String,
    configKeys: List<String>,
    configKey: String,
    configValue: String,
    configMessage: String,
    onConfigKeyChange: (String) -> Unit,
    onConfigValueChange: (String) -> Unit,
    onConfigKeyPick: (String) -> Unit,
    onConfigList: () -> Unit,
    onConfigGet: () -> Unit,
    onConfigSet: () -> Unit,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Details", fontWeight = FontWeight.SemiBold)
            AssistChip(
                onClick = {},
                label = { Text("Selected: ${if (selectedNodeId > 0) selectedNodeId.toString() else "-"}") },
            )

            if (nodeInfoError.isNotBlank()) {
                Text("NodeInfo error: $nodeInfoError")
            }
            if (nodeInfo.isNotEmpty()) {
                nodeInfo.entries.take(20).forEach { (k, v) ->
                    Text("$k: $v")
                }
                if (nodeInfo.size > 20) {
                    Text("… (${nodeInfo.size} items)")
                }
            }

            Text("Config", fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(enabled = selectedNodeId > 0, onClick = onConfigList, modifier = Modifier.weight(1f)) {
                    Text("List")
                }
                FilledTonalButton(enabled = selectedNodeId > 0, onClick = onConfigGet, modifier = Modifier.weight(1f)) {
                    Text("Get")
                }
                FilledTonalButton(enabled = selectedNodeId > 0, onClick = onConfigSet, modifier = Modifier.weight(1f)) {
                    Text("Set")
                }
            }

            if (configKeys.isNotEmpty()) {
                Text("Keys: ${configKeys.size}")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    configKeys.take(6).forEach { k ->
                        AssistChip(onClick = { onConfigKeyPick(k) }, label = { Text(k) })
                    }
                }
                if (configKeys.size > 6) {
                    Text("…")
                }
            }

            OutlinedTextField(
                value = configKey,
                onValueChange = { onConfigKeyChange(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Key") },
                singleLine = true,
            )
            OutlinedTextField(
                value = configValue,
                onValueChange = { onConfigValueChange(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Value") },
                singleLine = true,
            )
            if (configMessage.isNotBlank()) {
                AssistChip(onClick = {}, label = { Text(configMessage) })
            }
        }
    }
}

@Composable
private fun TreeNodeView(
    node: DeviceTreeNode,
    depth: Int,
    onToggle: (DeviceTreeNode) -> Unit,
    onSelect: (DeviceTreeNode) -> Unit,
) {
    val indent = (depth * 16).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val expandLabel = when {
            node.duplicate -> "dup"
            node.hasChildrenHint && !node.expanded -> "+"
            node.hasChildrenHint && node.expanded -> "-"
            else -> "•"
        }
        Text(
            text = expandLabel,
            modifier = Modifier
                .clickable(enabled = node.hasChildrenHint && !node.duplicate) { onToggle(node) }
                .padding(4.dp),
        )
        Text(
            text = "Node ${node.nodeId}" + if (node.duplicate) " (dup)" else "",
            modifier = Modifier
                .clickable { onSelect(node) }
                .padding(4.dp),
        )
    }
    if (node.loading) {
        Text("Loading…", modifier = Modifier.padding(start = indent + 16.dp))
    }
    if (node.error.isNotBlank()) {
        Text("Error: ${node.error}", modifier = Modifier.padding(start = indent + 16.dp))
    }
    val children = node.children
    if (node.expanded && children != null) {
        children.forEach { child ->
            TreeNodeView(node = child, depth = depth + 1, onToggle = onToggle, onSelect = onSelect)
        }
    }
}
