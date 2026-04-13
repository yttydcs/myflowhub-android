package com.myflowhub.android.ui
// Context: This file supports the Android app or gomobile host flow around FileScreen.

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myflowhub.android.FileEntry
import com.myflowhub.android.FileOfferStartResult
import com.myflowhub.android.FileProtocolSupport
import com.myflowhub.android.FilePullStartResult
import com.myflowhub.android.FileTextResult
import com.myflowhub.android.GoClientBridge
import com.myflowhub.android.Prefs
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class UploadSelection(
    val uri: Uri,
    val name: String,
    val size: Long?,
)

private fun resolveUploadSelection(context: Context, uri: Uri): UploadSelection {
    val resolver = context.contentResolver
    runCatching {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    var name = ""
    var size: Long? = null
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                name = cursor.getString(nameIndex).orEmpty()
            }
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                size = cursor.getLong(sizeIndex)
            }
        }
    }
    if (name.isBlank()) {
        name = uri.lastPathSegment.orEmpty().substringAfterLast('/').substringAfterLast(':')
    }
    val cleanName = FileProtocolSupport.requireFileName(name)
    return UploadSelection(
        uri = uri,
        name = cleanName,
        size = size?.takeIf { it >= 0 },
    )
}

private fun stageUploadSelection(context: Context, selection: UploadSelection, baseDir: String, dir: String): Long {
    val stagePath = FileProtocolSupport.expectedUploadStagePath(baseDir, dir, selection.name)
    val stageFile = File(stagePath)
    val parent = stageFile.parentFile ?: throw IllegalStateException("Upload staging path is invalid.")
    if (!parent.exists() && !parent.mkdirs()) {
        throw IllegalStateException("Failed to create upload staging directory.")
    }
    return try {
        val copied = context.contentResolver.openInputStream(selection.uri)?.use { input ->
            stageFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Selected file cannot be opened.")
        if (copied <= 0L) {
            throw IllegalStateException("Selected file is empty.")
        }
        copied
    } catch (t: Throwable) {
        stageFile.delete()
        throw t
    }
}

@Composable
fun FileScreen(
    modifier: Modifier = Modifier,
    go: GoClientBridge?,
    goError: String,
    cfg: Prefs.ClientConfig,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadRoot = remember(context) {
        FileProtocolSupport.resolveDownloadRoot(
            filesDir = context.filesDir,
            externalDownloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        )
    }
    val uploadRoot = remember(context) {
        FileProtocolSupport.resolveUploadRoot(
            filesDir = context.filesDir,
            externalFilesDir = context.getExternalFilesDir(null),
        )
    }

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

    var downloadEntry by remember { mutableStateOf<FileEntry?>(null) }
    var lastDownload by remember { mutableStateOf<FilePullStartResult?>(null) }
    var uploadSelection by remember { mutableStateOf<UploadSelection?>(null) }
    var lastUpload by remember { mutableStateOf<FileOfferStartResult?>(null) }
    val uploadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        runCatching { resolveUploadSelection(context, uri) }
            .onSuccess {
                uploadSelection = it
                status = ""
            }
            .onFailure { t ->
                status = t.message ?: t.toString()
            }
    }

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

    fun isRemoteBrowseTarget(): Boolean {
        val loginNodeId = cfg.nodeId.trim()
        val browseTarget = browseNodeId.trim()
        return loginNodeId.isNotBlank() && browseTarget.isNotBlank() && loginNodeId != browseTarget
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

    fun confirmDownload(entry: FileEntry) {
        if (entry.isDir) {
            return
        }
        if (!isRemoteBrowseTarget()) {
            status = "Download is only available for remote files."
            return
        }
        downloadEntry = entry
    }

    fun launchUploadPicker() {
        if (busy) {
            return
        }
        if (!isRemoteBrowseTarget()) {
            status = "Upload is only available for remote directories."
            return
        }
        uploadPicker.launch(arrayOf("*/*"))
    }

    fun startDownload(entry: FileEntry) {
        if (busy) {
            return
        }
        scope.launch {
            busy = true
            status = ""
            try {
                val (sourceId, hubId, targetId) = requireIds()
                if (sourceId == targetId) {
                    throw IllegalStateException("Download is only available for remote files.")
                }
                val raw = withContext(Dispatchers.IO) {
                    go!!.filePull(
                        sourceId = sourceId,
                        hubId = hubId,
                        targetId = targetId,
                        dir = currentDir,
                        name = entry.name,
                        localBaseDir = downloadRoot,
                    )
                }
                val result = FileProtocolSupport.parsePullStart(raw)
                lastDownload = result
                downloadEntry = null
                status = result.message.ifBlank { "Download started: ${result.localPath}" }
            } catch (t: Throwable) {
                status = t.message ?: t.toString()
            } finally {
                busy = false
            }
        }
    }

    fun startUpload(selection: UploadSelection) {
        if (busy) {
            return
        }
        scope.launch {
            busy = true
            status = ""
            try {
                val (sourceId, hubId, targetId) = requireIds()
                if (sourceId == targetId) {
                    throw IllegalStateException("Upload is only available for remote directories.")
                }
                withContext(Dispatchers.IO) {
                    stageUploadSelection(
                        context = context,
                        selection = selection,
                        baseDir = uploadRoot,
                        dir = currentDir,
                    )
                }
                val raw = withContext(Dispatchers.IO) {
                    go!!.fileOffer(
                        sourceId = sourceId,
                        hubId = hubId,
                        targetId = targetId,
                        dir = currentDir,
                        name = selection.name,
                        localBaseDir = uploadRoot,
                    )
                }
                val result = FileProtocolSupport.parseOfferStart(raw)
                lastUpload = result
                uploadSelection = null
                status = result.message.ifBlank {
                    "Upload started: ${FileProtocolSupport.displayPath(result.dir, result.name)}"
                }
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
                Text("Download root: $downloadRoot")
                Text("Upload staging root: $uploadRoot")
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
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        enabled = go != null && !busy,
                        onClick = {
                            newFolderName = ""
                            newFolderOpen = true
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("New Folder") }
                    Button(
                        enabled = go != null && !busy && isRemoteBrowseTarget(),
                        onClick = { launchUploadPicker() },
                        modifier = Modifier.weight(1f),
                    ) { Text("Upload") }
                }

                if (go == null) {
                    Text("Go AAR unavailable: ${goError.ifBlank { "unknown error" }}")
                } else if (cfg.nodeId.isBlank() || cfg.hubId.isBlank()) {
                    Text("Login required before using File.")
                } else if (!isRemoteBrowseTarget()) {
                    Text("Download/upload is only available when Browse Node ID differs from Login Node ID.")
                }

                if (status.isNotBlank()) {
                    Text("Status: $status")
                }
                lastDownload?.let { result ->
                    Text("Last download: ${result.localPath}")
                }
                lastUpload?.let { result ->
                    Text("Last upload: ${FileProtocolSupport.displayPath(result.dir, result.name)} <- ${result.localPath}")
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
                        if (entry.isDir) {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { openEntry(entry) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("[DIR] ${entry.name}")
                                    Text("Open directory")
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(entry.name)
                                    Text("Remote path: ${FileProtocolSupport.displayPath(currentDir, entry.name)}")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        OutlinedButton(
                                            enabled = !busy,
                                            onClick = { openEntry(entry) },
                                            modifier = Modifier.weight(1f),
                                        ) { Text("Preview") }
                                        Button(
                                            enabled = !busy && isRemoteBrowseTarget(),
                                            onClick = { confirmDownload(entry) },
                                            modifier = Modifier.weight(1f),
                                        ) { Text("Download") }
                                    }
                                }
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

    downloadEntry?.let { entry ->
        val expectedLocalPath = runCatching {
            FileProtocolSupport.expectedLocalPath(downloadRoot, currentDir, entry.name)
        }.getOrDefault("Unavailable")
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    downloadEntry = null
                }
            },
            title = { Text("Download ${entry.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Remote path: ${FileProtocolSupport.displayPath(currentDir, entry.name)}")
                    Text("Download root: $downloadRoot")
                    Text("Save to: $expectedLocalPath")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && isRemoteBrowseTarget(),
                    onClick = { startDownload(entry) },
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { downloadEntry = null },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    uploadSelection?.let { selection ->
        val expectedStagePath = runCatching {
            FileProtocolSupport.expectedUploadStagePath(uploadRoot, currentDir, selection.name)
        }.getOrDefault("Unavailable")
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    uploadSelection = null
                }
            },
            title = { Text("Upload ${selection.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Remote path: ${FileProtocolSupport.displayPath(currentDir, selection.name)}")
                    Text("Source size: ${selection.size?.toString() ?: "unknown"} bytes")
                    Text("Upload staging root: $uploadRoot")
                    Text("Stage to: $expectedStagePath")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && isRemoteBrowseTarget(),
                    onClick = { startUpload(selection) },
                ) {
                    Text("Upload")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { uploadSelection = null },
                ) {
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
