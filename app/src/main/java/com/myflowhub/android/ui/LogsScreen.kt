package com.myflowhub.android.ui
// 本文件实现 Android 客户端中与 `LogsScreen` 界面相关的宿主逻辑。

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun LogsScreen(
    modifier: Modifier = Modifier,
    go: GoClientBridge?,
    goError: String,
) {
    val scope = rememberCoroutineScope()

    var cursor by remember { mutableStateOf("0") }
    var limit by remember { mutableStateOf("200") }
    var status by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }

    fun pull(reset: Boolean) {
        if (go == null) {
            status = "Go AAR unavailable: ${goError.ifBlank { "unknown error" }}"
            return
        }
        scope.launch {
            status = ""
            try {
                val cur = if (reset) "0" else cursor
                val resp = withContext(Dispatchers.IO) { go.logsPull(cur, limit) }
                val obj = JSONObject(resp)
                val next = obj.optLong("next_cursor", obj.optLong("nextCursor", 0)).toString()
                val arr = obj.optJSONArray("lines") ?: JSONArray()
                val out = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val it = arr.optJSONObject(i) ?: continue
                    val line = it.optString("line", "")
                    if (line.isNotBlank()) out.add(line)
                }
                cursor = next
                lines = if (reset) out else (lines + out).takeLast(10_000)
                status = "Pulled ${out.size} lines."
            } catch (t: Throwable) {
                status = t.message ?: t.toString()
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
            Text("Go AAR unavailable: ${goError.ifBlank { "unknown error" }}")
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = go != null, onClick = { pull(reset = true) }) { Text("Reload") }
            Button(enabled = go != null, onClick = { pull(reset = false) }) { Text("Pull") }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = cursor,
                onValueChange = { cursor = it },
                modifier = Modifier.weight(1f),
                label = { Text("Cursor") },
                singleLine = true,
            )
            OutlinedTextField(
                value = limit,
                onValueChange = { limit = it },
                modifier = Modifier.weight(1f),
                label = { Text("Limit") },
                singleLine = true,
            )
        }

        if (status.isNotBlank()) {
            Text("Status: $status")
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            lines.takeLast(400).forEach { line ->
                Text(line)
            }
            if (lines.size > 400) {
                Text("… (${lines.size} lines, showing last 400)")
            }
        }
    }
}

