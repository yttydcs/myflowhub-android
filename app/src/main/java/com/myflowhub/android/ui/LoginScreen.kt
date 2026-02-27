package com.myflowhub.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.myflowhub.android.GoClientBridge
import com.myflowhub.android.Prefs
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
    cfg: Prefs.ClientConfig,
    notify: (String) -> Unit,
    onCfgChange: (Prefs.ClientConfig) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(false) }
    var lastAddr by remember { mutableStateOf("") }
    var lastError by remember { mutableStateOf("") }
    var lastMessage by remember { mutableStateOf("") }
    var opJob by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun refreshConn() {
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
        connected = snapshot.first
        lastAddr = snapshot.second
        lastError = snapshot.third
    }

    LaunchedEffect(go) {
        refreshConn()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Login")
        Text("WorkDir: $workDir")
        if (go == null) {
            Text("Go AAR unavailable: ${goError.ifBlank { "unknown error" }}")
        }

        OutlinedTextField(
            value = cfg.targetAddr,
            onValueChange = { onCfgChange(cfg.copy(targetAddr = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Target addr (ip:port)") },
            singleLine = true,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = go != null && !busy,
                onClick = {
                    val g = go ?: run {
                        notify("Go 不可用：${goError.ifBlank { "unknown error" }}")
                        return@Button
                    }
                    if (cfg.targetAddr.isBlank()) {
                        notify("Target addr 不能为空，例如 192.168.1.10:9000 或 127.0.0.1:9000")
                        return@Button
                    }
                    opJob?.cancel()
                    busy = true
                    notify("正在连接：${cfg.targetAddr}")
                    val job = scope.launch {
                        val localJob = kotlinx.coroutines.currentCoroutineContext()[Job]
                        try {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { g.connect(cfg.targetAddr) }
                            }
                            result.onFailure { t ->
                                lastMessage = t.message ?: t.toString()
                                notify("连接失败：${lastMessage}")
                            }.onSuccess {
                                lastMessage = "Connected."
                                notify("连接成功：${cfg.targetAddr}")
                            }
                            refreshConn()
                        } finally {
                            if (opJob === localJob) busy = false
                        }
                    }
                    opJob = job
                },
            ) { Text("Connect") }

            Button(
                enabled = go != null && !busy,
                onClick = {
                    val g = go ?: run {
                        notify("Go 不可用：${goError.ifBlank { "unknown error" }}")
                        return@Button
                    }
                    opJob?.cancel()
                    busy = true
                    notify("正在断开连接…")
                    val job = scope.launch {
                        val localJob = kotlinx.coroutines.currentCoroutineContext()[Job]
                        try {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { g.close() }
                            }
                            result.onFailure { t ->
                                lastMessage = t.message ?: t.toString()
                                notify("断开失败：${lastMessage}")
                            }.onSuccess {
                                lastMessage = "Disconnected."
                                notify("已断开")
                            }
                            refreshConn()
                        } finally {
                            if (opJob === localJob) busy = false
                        }
                    }
                    opJob = job
                },
            ) { Text("Disconnect") }

            Button(
                enabled = go != null && !busy,
                onClick = {
                    if (go == null) {
                        notify("Go 不可用：${goError.ifBlank { "unknown error" }}")
                        return@Button
                    }
                    opJob?.cancel()
                    busy = true
                    val job = scope.launch {
                        val localJob = kotlinx.coroutines.currentCoroutineContext()[Job]
                        try {
                            refreshConn()
                            notify("已刷新")
                        } finally {
                            if (opJob === localJob) busy = false
                        }
                    }
                    opJob = job
                },
            ) { Text("Refresh") }
        }

        Text("Connected: $connected")
        if (lastAddr.isNotBlank()) {
            Text("LastAddr: $lastAddr")
        }
        if (lastError.isNotBlank()) {
            Text("LastError: $lastError")
        }

        OutlinedTextField(
            value = cfg.deviceId,
            onValueChange = { onCfgChange(cfg.copy(deviceId = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Device ID") },
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
            Button(
                enabled = go != null && !busy,
                onClick = {
                    val g = go ?: run {
                        notify("Go 不可用：${goError.ifBlank { "unknown error" }}")
                        return@Button
                    }
                    opJob?.cancel()
                    busy = true
                    notify("正在确保密钥…")
                    val job = scope.launch {
                        val localJob = kotlinx.coroutines.currentCoroutineContext()[Job]
                        try {
                            val result = withContext(Dispatchers.IO) { runCatching { g.ensureKeys() } }
                            result.onFailure { t ->
                                lastMessage = t.message ?: t.toString()
                                notify("EnsureKeys 失败：${lastMessage}")
                            }.onSuccess {
                                lastMessage = "Keys ensured."
                                notify("密钥已确保")
                            }
                        } finally {
                            if (opJob === localJob) busy = false
                        }
                    }
                    opJob = job
                },
            ) { Text("EnsureKeys") }

            Button(
                enabled = go != null && !busy,
                onClick = {
                    val g = go ?: run {
                        notify("Go 不可用：${goError.ifBlank { "unknown error" }}")
                        return@Button
                    }
                    if (!connected) {
                        notify("请先 Connect")
                        return@Button
                    }
                    if (cfg.deviceId.isBlank()) {
                        notify("Device ID 不能为空")
                        return@Button
                    }
                    opJob?.cancel()
                    busy = true
                    notify("正在注册…")
                    val job = scope.launch {
                        val localJob = kotlinx.coroutines.currentCoroutineContext()[Job]
                        try {
                            val respResult = withContext(Dispatchers.IO) { runCatching { g.register(cfg.deviceId) } }
                            val resp = respResult.onFailure { t ->
                                lastMessage = t.message ?: t.toString()
                                notify("注册失败：${lastMessage}")
                            }.getOrDefault("")

                            if (resp.isBlank()) {
                                return@launch
                            }

                            val parsed = runCatching {
                                val obj = JSONObject(resp)
                                val nodeId = obj.optLong("node_id", 0)
                                val hubId = obj.optLong("hub_id", 0)
                                val role = obj.optString("role", "")
                                val msg = obj.optString("msg", "")
                                Triple(nodeId, hubId, Pair(role, msg))
                            }

                            parsed.onFailure { t ->
                                lastMessage = t.message ?: t.toString()
                                notify("注册返回解析失败：${lastMessage}")
                            }.onSuccess { (nodeId, hubId, roleMsg) ->
                                val (role, msg) = roleMsg
                                onCfgChange(
                                    cfg.copy(
                                        nodeId = if (nodeId > 0) nodeId.toString() else cfg.nodeId,
                                        hubId = if (hubId > 0) hubId.toString() else cfg.hubId,
                                        role = role.ifBlank { cfg.role },
                                    ),
                                )
                                lastMessage = msg.ifBlank { "Registered." }
                                notify(lastMessage)
                            }
                        } finally {
                            if (opJob === localJob) busy = false
                        }
                    }
                    opJob = job
                },
            ) { Text("Register") }

            Button(
                enabled = go != null && !busy,
                onClick = {
                    val g = go ?: run {
                        notify("Go 不可用：${goError.ifBlank { "unknown error" }}")
                        return@Button
                    }
                    if (!connected) {
                        notify("请先 Connect")
                        return@Button
                    }
                    if (cfg.deviceId.isBlank()) {
                        notify("Device ID 不能为空")
                        return@Button
                    }
                    if (cfg.nodeId.isBlank()) {
                        notify("Node ID 不能为空（可先 Register 获取）")
                        return@Button
                    }
                    opJob?.cancel()
                    busy = true
                    notify("正在登录…")
                    val job = scope.launch {
                        val localJob = kotlinx.coroutines.currentCoroutineContext()[Job]
                        try {
                            val respResult = withContext(Dispatchers.IO) { runCatching { g.login(cfg.deviceId, cfg.nodeId) } }
                            val resp = respResult.onFailure { t ->
                                lastMessage = t.message ?: t.toString()
                                notify("登录失败：${lastMessage}")
                            }.getOrDefault("")

                            if (resp.isBlank()) {
                                return@launch
                            }

                            val parsed = runCatching {
                                val obj = JSONObject(resp)
                                val nodeId = obj.optLong("node_id", 0)
                                val hubId = obj.optLong("hub_id", 0)
                                val role = obj.optString("role", "")
                                val msg = obj.optString("msg", "")
                                Triple(nodeId, hubId, Pair(role, msg))
                            }

                            parsed.onFailure { t ->
                                lastMessage = t.message ?: t.toString()
                                notify("登录返回解析失败：${lastMessage}")
                            }.onSuccess { (nodeId, hubId, roleMsg) ->
                                val (role, msg) = roleMsg
                                onCfgChange(
                                    cfg.copy(
                                        nodeId = if (nodeId > 0) nodeId.toString() else cfg.nodeId,
                                        hubId = if (hubId > 0) hubId.toString() else cfg.hubId,
                                        role = role.ifBlank { cfg.role },
                                    ),
                                )
                                lastMessage = msg.ifBlank { "Logged in." }
                                notify(lastMessage)
                            }
                        } finally {
                            if (opJob === localJob) busy = false
                        }
                    }
                    opJob = job
                },
            ) { Text("Login") }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = go != null && !busy,
                onClick = {
                    val g = go ?: run {
                        notify("Go 不可用：${goError.ifBlank { "unknown error" }}")
                        return@Button
                    }
                    opJob?.cancel()
                    busy = true
                    notify("正在清除登录状态…")
                    val job = scope.launch {
                        val localJob = kotlinx.coroutines.currentCoroutineContext()[Job]
                        try {
                            withContext(Dispatchers.IO) { runCatching { g.clearAuth() } }
                            onCfgChange(cfg.copy(nodeId = "", hubId = "", role = ""))
                            lastMessage = "Cleared auth."
                            notify("已清除")
                        } finally {
                            if (opJob === localJob) busy = false
                        }
                    }
                    opJob = job
                },
            ) { Text("ClearAuth") }
        }

        if (cfg.hubId.isNotBlank()) {
            Text("Hub ID: ${cfg.hubId}")
        }
        if (cfg.role.isNotBlank()) {
            Text("Role: ${cfg.role}")
        }
        if (lastMessage.isNotBlank()) {
            Text("Message: $lastMessage")
        }
    }
}
