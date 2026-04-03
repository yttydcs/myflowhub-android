package com.myflowhub.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myflowhub.android.FileEntry
import com.myflowhub.android.FileProtocolSupport
import com.myflowhub.android.FileTextResult
import com.myflowhub.android.GoClientBridge
import com.myflowhub.android.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FileScreen(
    modifier: Modifier = Modifier,
    go: GoClientBridge?,
    goError: String,
    cfg: Prefs.ClientConfig,
) {
    val scope = rememberCoroutineScope()

    var browseNodeId by rememberSaveable { mutableStateOf(cfg.hubId) }
    var currentDir by rememberSaveable { mutableStateOf("") }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }

    var previewOpen by remember { mutableStateOf(false) }
    var previewLoading by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf("") }
    var previewResult by remember { mutableStateOf<FileTextResult?>(null) }
    var previewTitle by remember { mutableStateOf("") }

    var newFolderOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    LaunchedEffect(cfg.hubId) {
        if (browseNodeId.isBlank()) {
            browseNodeId = cfg.hubId
        }
    }

    fun requireIds(): Triple<String, String, String> {
        if (go == null) {
            val detail = goError.ifBlank { "unknown error" }
            throw IllegalStateException("Go AAR unavailable: $detail")
        }
        val sourceId = FileProtocolSupport.requirePositiveNodeId(cfg.nodeId, "Login Node ID").toString()
        val hubId = FileProtocolSupport.requirePositiveNodeId(cfg.hubId, "Hub ID").toString()
        val targetId = FileProtocolSupport.requirePositiveNodeId(browseNodeId, "Browse Node ID").toString()
        return Triple(sourceId, hubId, targetId)
    }

    fun loadDirectory(nextDir: String = currentDir) {
        if (busy) {
            return
        }
        scope.launch {
            busy = true
            status = ""
            try {
                val (sourceId, hubId, targetId) = requireIds()
                val raw = withContext(Dispatchers.IO) {
                    go!!.fileList(sourceId, hubId, targetId, nextDir)
                }
                val result = FileProtocolSupport.parseList(raw)
                currentDir = result.dir
                entries = result.entries
                status = result.message.ifBlank { "Loaded ${result.entries.size} item(s)." }
            } catch (t: Throwable) {
                status = t.message ?: t.toString()
            } finally {
                busy = false
            }
        }
    }

    fun openEntry(entry: FileEntry) {
        if (entry.isDir) {
            loadDirectory(FileProtocolSupport.joinDir(currentDir, entry.name))
            return
        }
        scope.launch {
            previewTitle = entry.name
            previewResult = null
            previewError = ""
            previewLoading = true
            previewOpen = true
            try {
                val (sourceId, hubId, targetId) = requireIds()
                val raw = withContext(Dispatchers.IO) {
                    go!!.fileReadText(sourceId, hubId, targetId, currentDir, entry.name)
                }
                previewResult = FileProtocolSupport.parseReadText(raw)
            } catch (t: Throwable) {
                previewError = t.message ?: t.toString()
            } finally {
                previewLoading = false
            }
        }
    }

    fun createFolder() {
        if (busy) {
            return
        }
        scope.launch {
            busy = true
            status = ""
            try {
                val (sourceId, hubId, targetId) = requireIds()
                val normalizedName = FileProtocolSupport.requireFolderName(newFolderName)
                val raw = withContext(Dispatchers.IO) {
                    go!!.fileCreateDir(sourceId, hubId, targetId, currentDir, normalizedName)
                }
                FileProtocolSupport.parseWriteSuccess(raw)
                newFolderOpen = false
                newFolderName = ""
                status = "Folder created."
                val listRaw = withContext(Dispatchers.IO) {
                    go!!.fileList(sourceId, hubId, targetId, currentDir)
                }
                val result = FileProtocolSupport.parseList(listRaw)
                currentDir = result.dir
                entries = result.entries
            } catch (t: Throwable) {
                status = t.message ?: t.toString()
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Browse")
                Text("Login Node ID: ${cfg.nodeId.ifBlank { "-" }}")
                Text("Hub ID: ${cfg.hubId.ifBlank { "-" }}")
                OutlinedTextField(
                    value = browseNodeId,
                    onValueChange = {
                        browseNodeId = it
                        currentDir = ""
                        entries = emptyList()
                        status = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Browse Node ID") },
                    singleLine = true,
                )
                Text("Current dir: ${FileProtocolSupport.displayDir(currentDir)}")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        enabled = go != null && !busy,
                        onClick = { loadDirectory(currentDir) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Load") }
                    OutlinedButton(
                        enabled = go != null && !busy && currentDir.isNotBlank(),
                        onClick = { loadDirectory(FileProtocolSupport.parentDir(currentDir)) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Up") }
                    OutlinedButton(
                        enabled = go != null && !busy,
                        onClick = {
                            newFolderName = ""
                            newFolderOpen = true
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("New Folder") }
                }

                if (go == null) {
                    Text("Go AAR unavailable: ${goError.ifBlank { "unknown error" }}")
                } else if (cfg.nodeId.isBlank() || cfg.hubId.isBlank()) {
                    Text("Login required before using File.")
                }

                if (status.isNotBlank()) {
                    Text("Status: $status")
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Directory Listing")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (entries.isEmpty()) {
                        Text("No items loaded.")
                    }
                    entries.forEach { entry ->
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { openEntry(entry) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(if (entry.isDir) "[DIR] ${entry.name}" else entry.name)
                                Text(if (entry.isDir) "Open directory" else "Preview text")
                            }
                        }
                    }
                }
            }
        }
    }

    if (newFolderOpen) {
        AlertDialog(
            onDismissRequest = { newFolderOpen = false },
            title = { Text("New Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Current dir: ${FileProtocolSupport.displayDir(currentDir)}")
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Folder Name") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { createFolder() }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { newFolderOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (previewOpen) {
        AlertDialog(
            onDismissRequest = { previewOpen = false },
            title = { Text("Preview $previewTitle") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        previewLoading -> Text("Loading...")
                        previewError.isNotBlank() -> Text(previewError)
                        previewResult != null -> {
                            val info = buildString {
                                append("Path: ")
                                append(FileProtocolSupport.displayPath(previewResult!!.dir, previewResult!!.name))
                                append("  size=")
                                append(previewResult!!.size)
                                if (previewResult!!.truncated) {
                                    append(" (truncated)")
                                }
                            }
                            Text(info)
                            Text(previewResult!!.text)
                        }
                        else -> Text("No preview.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { previewOpen = false }) {
                    Text("Close")
                }
            },
        )
    }
}
