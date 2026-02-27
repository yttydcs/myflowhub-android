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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myflowhub.android.GoClientBridge
import com.myflowhub.android.Prefs
import org.json.JSONObject

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    go: GoClientBridge?,
    goError: String,
    workDir: String,
    cfg: Prefs.ClientConfig,
    onCfgChange: (Prefs.ClientConfig) -> Unit,
) {
    var connected by remember { mutableStateOf(false) }
    var lastAddr by remember { mutableStateOf("") }
    var lastError by remember { mutableStateOf("") }
    var lastMessage by remember { mutableStateOf("") }

    val refreshConn: () -> Unit = {
        if (go == null) {
            connected = false
            lastAddr = ""
            lastError = ""
        } else {
            connected = runCatching { go.isConnected() }.getOrDefault(false)
            lastAddr = runCatching { go.lastAddr() }.getOrDefault("")
            lastError = runCatching { go.lastError() }.getOrDefault("")
        }
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
                enabled = go != null,
                onClick = {
                    lastMessage = ""
                    runCatching {
                        go?.connect(cfg.targetAddr)
                    }.onFailure { t ->
                        lastMessage = t.message ?: t.toString()
                    }
                    refreshConn()
                },
            ) { Text("Connect") }

            Button(
                enabled = go != null,
                onClick = {
                    lastMessage = ""
                    runCatching {
                        go?.close()
                    }.onFailure { t ->
                        lastMessage = t.message ?: t.toString()
                    }
                    refreshConn()
                },
            ) { Text("Disconnect") }

            Button(
                enabled = go != null,
                onClick = { refreshConn() },
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
                enabled = go != null,
                onClick = {
                    lastMessage = ""
                    runCatching {
                        go?.ensureKeys()
                    }.onFailure { t ->
                        lastMessage = t.message ?: t.toString()
                    }.onSuccess {
                        lastMessage = "Keys ensured."
                    }
                },
            ) { Text("EnsureKeys") }

            Button(
                enabled = go != null,
                onClick = {
                    lastMessage = ""
                    val resp = runCatching { go?.register(cfg.deviceId).orEmpty() }
                        .onFailure { t -> lastMessage = t.message ?: t.toString() }
                        .getOrDefault("")
                    if (resp.isNotBlank()) {
                        runCatching {
                            val obj = JSONObject(resp)
                            val nodeId = obj.optLong("node_id", 0)
                            val hubId = obj.optLong("hub_id", 0)
                            val role = obj.optString("role", "")
                            val msg = obj.optString("msg", "")
                            onCfgChange(
                                cfg.copy(
                                    nodeId = if (nodeId > 0) nodeId.toString() else cfg.nodeId,
                                    hubId = if (hubId > 0) hubId.toString() else cfg.hubId,
                                    role = role.ifBlank { cfg.role },
                                ),
                            )
                            lastMessage = msg.ifBlank { "Registered." }
                        }.onFailure { t ->
                            lastMessage = t.message ?: t.toString()
                        }
                    }
                },
            ) { Text("Register") }

            Button(
                enabled = go != null,
                onClick = {
                    lastMessage = ""
                    val resp = runCatching { go?.login(cfg.deviceId, cfg.nodeId).orEmpty() }
                        .onFailure { t -> lastMessage = t.message ?: t.toString() }
                        .getOrDefault("")
                    if (resp.isNotBlank()) {
                        runCatching {
                            val obj = JSONObject(resp)
                            val nodeId = obj.optLong("node_id", 0)
                            val hubId = obj.optLong("hub_id", 0)
                            val role = obj.optString("role", "")
                            val msg = obj.optString("msg", "")
                            onCfgChange(
                                cfg.copy(
                                    nodeId = if (nodeId > 0) nodeId.toString() else cfg.nodeId,
                                    hubId = if (hubId > 0) hubId.toString() else cfg.hubId,
                                    role = role.ifBlank { cfg.role },
                                ),
                            )
                            lastMessage = msg.ifBlank { "Logged in." }
                        }.onFailure { t ->
                            lastMessage = t.message ?: t.toString()
                        }
                    }
                },
            ) { Text("Login") }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = go != null,
                onClick = {
                    lastMessage = ""
                    runCatching { go?.clearAuth() }
                    onCfgChange(cfg.copy(nodeId = "", hubId = "", role = ""))
                    lastMessage = "Cleared auth."
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
