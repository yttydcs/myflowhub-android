package com.myflowhub.android
// Context: This file supports the Android app or gomobile host flow around FileProtocolSupport.

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class FileEntry(
    val name: String,
    val isDir: Boolean,
)

data class FileListResult(
    val dir: String,
    val entries: List<FileEntry>,
    val message: String,
)

data class FileTextResult(
    val dir: String,
    val name: String,
    val size: Long,
    val text: String,
    val truncated: Boolean,
    val message: String,
)

data class FilePullStartResult(
    val dir: String,
    val name: String,
    val size: Long,
    val sessionId: String,
    val localBaseDir: String,
    val localPath: String,
    val message: String,
)

data class FileOfferStartResult(
    val dir: String,
    val name: String,
    val size: Long,
    val sessionId: String,
    val resumeFrom: Long,
    val localBaseDir: String,
    val localPath: String,
    val message: String,
)

object FileProtocolSupport {
    private const val OP_OFFER = "offer"
    private const val OP_PULL = "pull"
    private const val OP_MKDIR = "mkdir"
    private val nameComparator = compareBy<String>({ it.lowercase() }, { it })

    fun normalizeDir(dir: String): String {
        val text = dir.trim().replace('\\', '/')
        if (text.isBlank() || text == "/" || text == ".") {
            return ""
        }
        return text
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." }
            .joinToString("/")
    }

    fun parentDir(dir: String): String {
        val parts = normalizeDir(dir).split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            return ""
        }
        return parts.dropLast(1).joinToString("/")
    }

    fun joinDir(base: String, child: String): String {
        val cleanChild = child.trim().trim('/').trim('\\')
        if (cleanChild.isBlank()) {
            return normalizeDir(base)
        }
        val cleanBase = normalizeDir(base)
        return if (cleanBase.isBlank()) cleanChild else "$cleanBase/$cleanChild"
    }

    fun displayDir(dir: String): String {
        val normalized = normalizeDir(dir)
        return if (normalized.isBlank()) "/" else "/$normalized"
    }

    fun displayPath(dir: String, name: String): String {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return displayDir(dir)
        }
        val normalizedDir = normalizeDir(dir)
        return if (normalizedDir.isBlank()) "/$cleanName" else "/$normalizedDir/$cleanName"
    }

    fun resolveDownloadRoot(filesDir: File, externalDownloadsDir: File?): String {
        val parent = externalDownloadsDir ?: File(filesDir, "downloads")
        return File(parent, "myflowhub").absolutePath
    }

    fun resolveUploadRoot(filesDir: File, externalFilesDir: File?): String {
        val parent = externalFilesDir ?: filesDir
        return File(parent, "myflowhub/upload-staging").absolutePath
    }

    fun expectedLocalPath(localBaseDir: String, dir: String, name: String): String {
        return expectedTransferPath(localBaseDir, dir, name, "Local download root is required.")
    }

    fun expectedUploadStagePath(localBaseDir: String, dir: String, name: String): String {
        return expectedTransferPath(localBaseDir, dir, name, "Local upload staging root is required.")
    }

    fun requirePositiveNodeId(raw: String, label: String = "Node ID"): Long {
        val value = raw.trim().toLongOrNull() ?: throw IllegalStateException("$label must be a positive integer.")
        if (value <= 0) {
            throw IllegalStateException("$label must be a positive integer.")
        }
        return value
    }

    fun requireFolderName(raw: String): String {
        val name = raw.trim()
        if (name.isBlank()) {
            throw IllegalStateException("Folder name is required.")
        }
        if (name == "." || name == "..") {
            throw IllegalStateException("Invalid folder name.")
        }
        if (name.any { it == '/' || it == '\\' || it == '\u0000' }) {
            throw IllegalStateException("Folder name cannot contain path separators.")
        }
        return name
    }

    fun requireFileName(raw: String): String = requireTransferName(raw)

    fun parseList(raw: String): FileListResult {
        val obj = requireSuccess(JSONObject(raw), "File list")
        val dirs = collectNames(obj.optJSONArray("dirs"))
        val files = collectNames(obj.optJSONArray("files"))
        val entries = buildList {
            dirs.sortedWith(nameComparator).forEach { add(FileEntry(name = it, isDir = true)) }
            files.sortedWith(nameComparator).forEach { add(FileEntry(name = it, isDir = false)) }
        }
        return FileListResult(
            dir = normalizeDir(obj.optString("dir", "")),
            entries = entries,
            message = obj.optString("msg", "").trim(),
        )
    }

    fun parseReadText(raw: String): FileTextResult {
        val obj = requireSuccess(JSONObject(raw), "File read")
        return FileTextResult(
            dir = normalizeDir(obj.optString("dir", "")),
            name = obj.optString("name", "").trim(),
            size = obj.optLong("size", 0),
            text = obj.optString("text", ""),
            truncated = obj.optBoolean("truncated", false),
            message = obj.optString("msg", "").trim(),
        )
    }

    fun parseWriteSuccess(raw: String, expectedOp: String = OP_MKDIR): String {
        val obj = requireSuccess(JSONObject(raw), "File write")
        val actualOp = obj.optString("op", "").trim()
        if (expectedOp.isNotBlank() && actualOp.isNotBlank() && actualOp != expectedOp) {
            throw IllegalStateException("Unexpected write op: $actualOp")
        }
        return obj.optString("msg", "").trim()
    }

    fun parsePullStart(raw: String): FilePullStartResult {
        val obj = requireSuccess(JSONObject(raw), "File pull")
        val actualOp = obj.optString("op", "").trim()
        if (actualOp.isNotBlank() && actualOp != OP_PULL) {
            throw IllegalStateException("Unexpected read op: $actualOp")
        }
        val dir = normalizeDir(obj.optString("dir", ""))
        val name = requireTransferName(obj.optString("name", ""))
        val localBaseDir = obj.optString("local_base_dir", "").trim().ifBlank {
            throw IllegalStateException("Local download root is missing.")
        }
        val localPath = obj.optString("local_path", "").trim().ifBlank {
            expectedLocalPath(localBaseDir, dir, name)
        }
        return FilePullStartResult(
            dir = dir,
            name = name,
            size = obj.optLong("size", 0),
            sessionId = obj.optString("session_id", "").trim(),
            localBaseDir = localBaseDir,
            localPath = localPath,
            message = obj.optString("msg", "").trim(),
        )
    }

    fun parseOfferStart(raw: String): FileOfferStartResult {
        val obj = requireSuccess(JSONObject(raw), "File offer")
        val actualOp = obj.optString("op", "").trim()
        if (actualOp.isNotBlank() && actualOp != OP_OFFER) {
            throw IllegalStateException("Unexpected write op: $actualOp")
        }
        val dir = normalizeDir(obj.optString("dir", ""))
        val name = requireTransferName(obj.optString("name", ""))
        val localBaseDir = obj.optString("local_base_dir", "").trim().ifBlank {
            throw IllegalStateException("Local upload staging root is missing.")
        }
        val localPath = obj.optString("local_path", "").trim().ifBlank {
            expectedUploadStagePath(localBaseDir, dir, name)
        }
        return FileOfferStartResult(
            dir = dir,
            name = name,
            size = obj.optLong("size", 0),
            sessionId = obj.optString("session_id", "").trim(),
            resumeFrom = obj.optLong("resume_from", 0),
            localBaseDir = localBaseDir,
            localPath = localPath,
            message = obj.optString("msg", "").trim(),
        )
    }

    private fun requireSuccess(obj: JSONObject, label: String): JSONObject {
        val code = obj.optInt("code", 0)
        if (code == 1) {
            return obj
        }
        val message = obj.optString("msg", "").trim().ifBlank { "$label failed (code=$code)" }
        throw IllegalStateException(message)
    }

    private fun expectedTransferPath(localBaseDir: String, dir: String, name: String, emptyBaseMessage: String): String {
        val base = localBaseDir.trim().ifBlank {
            throw IllegalStateException(emptyBaseMessage)
        }
        val cleanName = requireTransferName(name)
        val cleanDir = requireTransferDir(dir)
        val dirFile = if (cleanDir.isBlank()) {
            File(base)
        } else {
            File(base, cleanDir.replace('/', File.separatorChar))
        }
        return File(dirFile, cleanName).absolutePath
    }

    private fun requireTransferDir(raw: String): String {
        val normalized = raw.trim().replace('\\', '/')
        if (normalized.isBlank() || normalized == "/" || normalized == ".") {
            return ""
        }
        if (normalized.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(normalized)) {
            throw IllegalStateException("Invalid file directory.")
        }
        val parts = normalized
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) {
            throw IllegalStateException("Invalid file directory.")
        }
        return parts.joinToString("/")
    }

    private fun requireTransferName(raw: String): String {
        val cleanName = raw.trim()
        if (cleanName.isBlank()) {
            throw IllegalStateException("File name is required.")
        }
        if (cleanName == "." || cleanName == ".." || cleanName.any { it == '/' || it == '\\' || it == '\u0000' }) {
            throw IllegalStateException("Invalid file name.")
        }
        return cleanName
    }

    private fun collectNames(array: JSONArray?): List<String> {
        if (array == null) {
            return emptyList()
        }
        val seen = linkedSetOf<String>()
        for (i in 0 until array.length()) {
            val name = array.optString(i, "").trim()
            if (name.isBlank()) {
                continue
            }
            seen += name
        }
        return seen.toList()
    }
}
