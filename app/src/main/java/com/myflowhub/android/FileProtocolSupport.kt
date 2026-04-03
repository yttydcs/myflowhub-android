package com.myflowhub.android

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

object FileProtocolSupport {
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

    private fun requireSuccess(obj: JSONObject, label: String): JSONObject {
        val code = obj.optInt("code", 0)
        if (code == 1) {
            return obj
        }
        val message = obj.optString("msg", "").trim().ifBlank { "$label failed (code=$code)" }
        throw IllegalStateException(message)
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
