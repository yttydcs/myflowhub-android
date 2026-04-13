package com.myflowhub.android.ui
// Context: This file supports the Android app or gomobile host flow around ProtocolsScreen.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ProtocolDef(
    val name: String,
    val subProto: Int,
    val defaultAction: String = "",
    val defaultExpect: String = "",
    val defaultData: String = "{}",
)

private val protocols = listOf(
    ProtocolDef("auth", 2, defaultAction = "get_perms", defaultExpect = "get_perms_resp", defaultData = "{\"node_id\":1}"),
    ProtocolDef("varstore", 3, defaultAction = "list", defaultExpect = "list_resp", defaultData = "{}"),
    ProtocolDef("topicbus", 4, defaultAction = "list_subs", defaultExpect = "list_subs_resp", defaultData = "{}"),
    ProtocolDef("file", 5, defaultAction = "read", defaultExpect = "read_resp", defaultData = "{\"op\":\"list\",\"dir\":\".\"}"),
    ProtocolDef("flow", 6, defaultAction = "list", defaultExpect = "list_resp", defaultData = "{\"req_id\":\"demo\",\"executor_node\":0}"),
    ProtocolDef(
        "exec",
        7,
        defaultAction = "call",
        defaultExpect = "call_resp",
        defaultData = "{\"req_id\":\"demo\",\"executor_node\":0,\"target_node\":0,\"method\":\"echo\",\"args\":[\"hello\"],\"timeout_ms\":8000}",
    ),
)

@Composable
fun ProtocolsScreen(
    modifier: Modifier = Modifier,
    go: GoClientBridge?,
    goError: String,
    cfg: Prefs.ClientConfig,
) {
    var selected by remember { mutableStateOf<ProtocolDef?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (go == null) {
            Text("Go AAR unavailable: ${goError.ifBlank { "unknown error" }}")
        }

        if (selected == null) {
            protocols.forEach { p ->
                Button(enabled = go != null, onClick = { selected = p }) { Text(p.name) }
            }
            return
        }

        ProtocolConsole(
            go = go,
            cfg = cfg,
            proto = selected!!,
            onBack = { selected = null },
        )
    }
}

@Composable
private fun ProtocolConsole(
    go: GoClientBridge?,
    cfg: Prefs.ClientConfig,
    proto: ProtocolDef,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var targetId by remember { mutableStateOf(cfg.hubId) }
    var action by remember { mutableStateOf(proto.defaultAction) }
    var expect by remember { mutableStateOf(proto.defaultExpect) }
    var dataJson by remember { mutableStateOf(proto.defaultData) }
    var timeoutMs by remember { mutableStateOf("8000") }
    var resp by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) { Text("Back") }
            Text("SubProto=${proto.subProto}")
        }

        OutlinedTextField(
            value = cfg.nodeId,
            onValueChange = { /* readonly */ },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("SourceID (login node id)") },
            singleLine = true,
            enabled = false,
        )
        OutlinedTextField(
            value = targetId,
            onValueChange = { targetId = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("TargetID") },
            singleLine = true,
        )
        OutlinedTextField(
            value = action,
            onValueChange = { action = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Action") },
            singleLine = true,
        )
        OutlinedTextField(
            value = expect,
            onValueChange = { expect = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Expect action (optional)") },
            singleLine = true,
        )
        OutlinedTextField(
            value = timeoutMs,
            onValueChange = { timeoutMs = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Timeout ms") },
            singleLine = true,
        )
        OutlinedTextField(
            value = dataJson,
            onValueChange = { dataJson = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Data JSON") },
            singleLine = false,
        )

        Button(
            enabled = go != null,
            onClick = {
                status = ""
                resp = ""
                val sourceId = cfg.nodeId.trim()
                if (sourceId.isBlank()) {
                    status = "Login required."
                    return@Button
                }
                scope.launch {
                    try {
                        val out = withContext(Dispatchers.IO) {
                            go!!.sendAndAwait(
                                proto.subProto.toString(),
                                sourceId,
                                targetId,
                                action,
                                dataJson,
                                expect,
                                timeoutMs,
                            )
                        }
                        resp = out
                        status = "OK"
                    } catch (t: Throwable) {
                        status = t.message ?: t.toString()
                    }
                }
            },
        ) { Text("SendAndAwait") }

        if (status.isNotBlank()) {
            Text("Status: $status")
        }
        if (resp.isNotBlank()) {
            Text("Resp: $resp")
        }
    }
}
