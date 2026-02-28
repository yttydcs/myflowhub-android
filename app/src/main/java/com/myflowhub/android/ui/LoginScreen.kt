package com.myflowhub.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    go: GoClientBridge?,
    goError: String,
    workDir: String,
    hubSelfId: String,
    cfg: Prefs.ClientConfig,
    ui: UiNotifier,
    onCfgChange: (Prefs.ClientConfig) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(false) }
    var lastAddr by remember { mutableStateOf("") }
    var lastError by remember { mutableStateOf("") }
    var lastMessage by remember { mutableStateOf("") }
    var opJob by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("") }
    var opSeq by remember { mutableStateOf(0) }

    fun baseOf(id: String): String {
        val trimmed = id.trim()
        var base = trimmed
        base = when {
            base.endsWith("-hub") -> base.removeSuffix("-hub")
            base.endsWith("-ui") -> base.removeSuffix("-ui")
            else -> base
        }
        base = base.trim().trimEnd('-')
        return if (base.isBlank()) trimmed else base
    }

    fun normalizeUiDeviceId(id: String): String {
        val current = id.trim()
        if (current.isBlank()) return ""
        val hubTrimmed = hubSelfId.trim()
        val base = baseOf(if (hubTrimmed.isNotBlank()) hubTrimmed else current)
        return when {
            hubTrimmed.isNotBlank() && current == hubTrimmed && base.isNotBlank() -> "${base}-ui"
            current.endsWith("-hub") -> "${baseOf(current)}-ui"
            current.endsWith("-ui") -> current
            else -> "${current}-ui"
        }
    }

    suspend fun refreshConn(token: Int? = null) {
        val g = go
        if (g == null) {
            connected = false
            lastAddr = ""
            lastError = ""
            return
        }
        val snapshot = withContext(Dispatchers.IO) {
            Triple(
                runCatching { g.isConnected() }.getOrDefault(false),
                runCatching { g.lastAddr() }.getOrDefault(""),
                runCatching { g.lastError() }.getOrDefault(""),
            )
        }
        if (token != null && opSeq != token) return
        connected = snapshot.first
        lastAddr = snapshot.second
        lastError = snapshot.third
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

    LaunchedEffect(go) {
        refreshConn()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (busyLabel.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = busyLabel, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { cancelOp() }) { Text("Cancel") }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

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
                Text("连接", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = cfg.targetAddr,
                    onValueChange = { onCfgChange(cfg.copy(targetAddr = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Target addr (ip:port)") },
                    placeholder = { Text("127.0.0.1:9000") },
                    singleLine = true,
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        enabled = go != null && !busy,
                        onClick = {
                    val g = go ?: run {
                        ui.error("Go 不可用：${goError.ifBlank { "unknown error" }}")
                        return@FilledTonalButton
                    }
                    if (cfg.targetAddr.isBlank()) {
                        ui.info("Target addr 不能为空，例如 192.168.1.10:9000 或 127.0.0.1:9000")
                        return@FilledTonalButton
                    }
                    val token = beginOp("正在连接：${cfg.targetAddr}")
                    val job = scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { g.connect(cfg.targetAddr) }
                            }
                            if (opSeq != token) return@launch
                            result.onFailure { t ->
                                lastMessage = t.message ?: t.toString()
                                ui.error("连接失败：${lastMessage}")
                            }.onSuccess {
                                lastMessage = "Connected."
                                ui.success("连接成功：${cfg.targetAddr}")
                            }
                            refreshConn(token)
                        } catch (_: CancellationException) {
                            // ignore
                        } finally {
                            endOp(token)
                        }
                    }
                    opJob = job
                },
                    ) { Text("Connect") }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = go != null && !busy,
                        onClick = {
                    val g = go ?: run {
                        ui.error("Go 不可用：${goError.ifBlank { "unknown error" }}")
                        return@OutlinedButton
                    }
                    val token = beginOp("正在断开连接…")
                    val job = scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { g.close() }
                            }
                            if (opSeq != token) return@launch
                            result.onFailure { t ->
                                lastMessage = t.message ?: t.toString()
                                ui.error("断开失败：${lastMessage}")
                            }.onSuccess {
                                lastMessage = "Disconnected."
                                ui.success("已断开")
                            }
                            refreshConn(token)
                        } catch (_: CancellationException) {
                            // ignore
                        } finally {
                            endOp(token)
                        }
                    }
                    opJob = job
                },
                    ) { Text("Disconnect") }
                }

                OutlinedButton(
                    enabled = go != null && !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                    if (go == null) {
                        ui.error("Go 不可用：${goError.ifBlank { "unknown error" }}")
                        return@OutlinedButton
                    }
                    val token = beginOp("正在刷新…")
                    val job = scope.launch {
                        try {
                            refreshConn(token)
                            if (opSeq != token) return@launch
                            ui.success("已刷新")
                        } catch (_: CancellationException) {
                            // ignore
                        } finally {
                            endOp(token)
                        }
                    }
                    opJob = job
                },
                ) { Text("Refresh") }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(if (connected) "Connected" else "Disconnected") })
                    if (lastAddr.isNotBlank()) {
                        AssistChip(onClick = {}, label = { Text(lastAddr) })
                    }
                }
                if (lastError.isNotBlank()) {
                    Text("LastError: $lastError")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("身份与认证", fontWeight = FontWeight.SemiBold)

                if (hubSelfId.isNotBlank()) {
                    Text("Hub SelfID (-hub): $hubSelfId")
                }

                OutlinedTextField(
                    value = cfg.deviceId,
                    onValueChange = { onCfgChange(cfg.copy(deviceId = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("UI DeviceID (-ui)") },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = cfg.nodeId,
                    onValueChange = { onCfgChange(cfg.copy(nodeId = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Node ID (for login)") },
                    singleLine = true,
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        enabled = go != null && !busy,
                        onClick = {
                            val g = go ?: run {
                                ui.error("Go 不可用：${goError.ifBlank { "unknown error" }}")
                                return@FilledTonalButton
                            }
                            val token = beginOp("正在确保密钥…")
                            val job = scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) { runCatching { g.ensureKeys() } }
                                    if (opSeq != token) return@launch
                                    result.onFailure { t ->
                                        lastMessage = t.message ?: t.toString()
                                        ui.error("EnsureKeys 失败：${lastMessage}")
                                    }.onSuccess {
                                        lastMessage = "Keys ensured."
                                        ui.success("密钥已确保")
                                    }
                                } catch (_: CancellationException) {
                                    // ignore
                                } finally {
                                    endOp(token)
                                }
                            }
                            opJob = job
                        },
                    ) { Text("EnsureKeys") }

                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        enabled = go != null && !busy,
                        onClick = {
                            val g = go ?: run {
                                ui.error("Go 不可用：${goError.ifBlank { "unknown error" }}")
                                return@FilledTonalButton
                            }
                            if (!connected) {
                                ui.info("请先 Connect")
                                return@FilledTonalButton
                            }
                            val currentDeviceId = cfg.deviceId.trim()
                            if (currentDeviceId.isBlank()) {
                                ui.info("UI DeviceID 不能为空")
                                return@FilledTonalButton
                            }
                            val normalizedDeviceId = normalizeUiDeviceId(currentDeviceId)

                            if (normalizedDeviceId != currentDeviceId) {
                                onCfgChange(cfg.copy(deviceId = normalizedDeviceId, nodeId = "", hubId = "", role = ""))
                                ui.info("已自动修正 UI DeviceID：${normalizedDeviceId}（已清空登录信息）")
                            }

                            val token = beginOp("正在注册…")
                            val job = scope.launch {
                                try {
                                    val respResult = withContext(Dispatchers.IO) { runCatching { g.register(normalizedDeviceId) } }
                                    if (opSeq != token) return@launch

                                    val resp = respResult.getOrNull()
                                    if (resp == null) {
                                        val t = respResult.exceptionOrNull()
                                        lastMessage = t?.message ?: t.toString()
                                        ui.error("注册失败：${lastMessage}")
                                        return@launch
                                    }

                                    val raw = resp.trim()
                                    if (raw.isBlank()) {
                                        ui.error("注册失败：返回为空")
                                        return@launch
                                    }

                                    val obj = runCatching { JSONObject(raw) }.getOrElse { t ->
                                        lastMessage = t.message ?: t.toString()
                                        ui.error("注册返回解析失败：${lastMessage}")
                                        return@launch
                                    }

                                    if (opSeq != token) return@launch

                                    val nodeId = obj.optLong("node_id", 0)
                                    val hubId = obj.optLong("hub_id", 0)
                                    val role = obj.optString("role", "").trim()
                                    val msg = obj.optString("msg", "").trim()

                                    if (nodeId <= 0) {
                                        ui.error("注册失败：返回缺少 node_id")
                                        return@launch
                                    }
                                    if (hubId <= 0) {
                                        ui.error("注册失败：返回缺少 hub_id")
                                        return@launch
                                    }

                                    onCfgChange(
                                        cfg.copy(
                                            deviceId = normalizedDeviceId,
                                            nodeId = nodeId.toString(),
                                            hubId = hubId.toString(),
                                            role = role,
                                        ),
                                    )
                                    lastMessage = msg.ifBlank { "Registered." }
                                    ui.success(lastMessage)
                                } catch (_: CancellationException) {
                                    // ignore
                                } finally {
                                    endOp(token)
                                }
                            }
                            opJob = job
                        },
                    ) { Text("Register") }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        enabled = go != null && !busy,
                        onClick = {
                            val g = go ?: run {
                                ui.error("Go 不可用：${goError.ifBlank { "unknown error" }}")
                                return@FilledTonalButton
                            }
                            if (!connected) {
                                ui.info("请先 Connect")
                                return@FilledTonalButton
                            }
                            val currentDeviceId = cfg.deviceId.trim()
                            if (currentDeviceId.isBlank()) {
                                ui.info("UI DeviceID 不能为空")
                                return@FilledTonalButton
                            }
                            val normalizedDeviceId = normalizeUiDeviceId(currentDeviceId)
                            if (normalizedDeviceId != currentDeviceId) {
                                onCfgChange(cfg.copy(deviceId = normalizedDeviceId, nodeId = "", hubId = "", role = ""))
                                ui.info("已自动修正 UI DeviceID：${normalizedDeviceId}（已清空登录信息），请先 Register")
                                return@FilledTonalButton
                            }

                            val nodeIdInput = cfg.nodeId.trim()
                            if (nodeIdInput.isBlank()) {
                                ui.info("Node ID 不能为空（可先 Register 获取）")
                                return@FilledTonalButton
                            }
                            val token = beginOp("正在登录…")
                            val job = scope.launch {
                                try {
                                    val respResult = withContext(Dispatchers.IO) {
                                        runCatching { g.login(normalizedDeviceId, nodeIdInput) }
                                    }
                                    if (opSeq != token) return@launch

                                    val resp = respResult.getOrNull()
                                    if (resp == null) {
                                        val t = respResult.exceptionOrNull()
                                        lastMessage = t?.message ?: t.toString()
                                        ui.error("登录失败：${lastMessage}")
                                        return@launch
                                    }

                                    val raw = resp.trim()
                                    if (raw.isBlank()) {
                                        ui.error("登录失败：返回为空")
                                        return@launch
                                    }

                                    val obj = runCatching { JSONObject(raw) }.getOrElse { t ->
                                        lastMessage = t.message ?: t.toString()
                                        ui.error("登录返回解析失败：${lastMessage}")
                                        return@launch
                                    }

                                    if (opSeq != token) return@launch

                                    val nodeId = obj.optLong("node_id", 0)
                                    val hubId = obj.optLong("hub_id", 0)
                                    val role = obj.optString("role", "").trim()
                                    val msg = obj.optString("msg", "").trim()

                                    if (nodeId <= 0) {
                                        ui.error("登录失败：返回缺少 node_id")
                                        return@launch
                                    }
                                    if (hubId <= 0) {
                                        ui.error("登录失败：返回缺少 hub_id")
                                        return@launch
                                    }

                                    onCfgChange(
                                        cfg.copy(
                                            deviceId = normalizedDeviceId,
                                            nodeId = nodeId.toString(),
                                            hubId = hubId.toString(),
                                            role = role,
                                        ),
                                    )
                                    lastMessage = msg.ifBlank { "Logged in." }
                                    ui.success(lastMessage)
                                } catch (_: CancellationException) {
                                    // ignore
                                } finally {
                                    endOp(token)
                                }
                            }
                            opJob = job
                        },
                    ) { Text("Login") }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = go != null && !busy,
                        onClick = {
                            val g = go ?: run {
                                ui.error("Go 不可用：${goError.ifBlank { "unknown error" }}")
                                return@OutlinedButton
                            }
                            val token = beginOp("正在清除登录状态…")
                            val job = scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { runCatching { g.clearAuth() } }
                                    if (opSeq != token) return@launch
                                    onCfgChange(cfg.copy(nodeId = "", hubId = "", role = ""))
                                    lastMessage = "Cleared auth."
                                    ui.success("已清除")
                                } catch (_: CancellationException) {
                                    // ignore
                                } finally {
                                    endOp(token)
                                }
                            }
                            opJob = job
                        },
                    ) { Text("ClearAuth") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cfg.hubId.isNotBlank()) {
                        AssistChip(onClick = {}, label = { Text("Hub ${cfg.hubId}") })
                    }
                    if (cfg.role.isNotBlank()) {
                        AssistChip(onClick = {}, label = { Text(cfg.role) })
                    }
                }

                if (lastMessage.isNotBlank()) {
                    Text("Message: $lastMessage")
                }
            }
        }

        Text("WorkDir: $workDir")
    }
}
