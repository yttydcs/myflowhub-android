package com.myflowhub.android
// 本文件实现 Android 宿主中与 `FileProtocolSupport` 相关的逻辑。

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

    // 统一规范远端目录文本，去掉空段、`.` 和平台分隔符差异。
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

    // 计算上一层目录，供面包屑导航和“返回上级”按钮复用。
    fun parentDir(dir: String): String {
        val parts = normalizeDir(dir).split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            return ""
        }
        return parts.dropLast(1).joinToString("/")
    }

    // 在已规范化的 base/child 上拼接远端目录路径。
    fun joinDir(base: String, child: String): String {
        val cleanChild = child.trim().trim('/').trim('\\')
        if (cleanChild.isBlank()) {
            return normalizeDir(base)
        }
        val cleanBase = normalizeDir(base)
        return if (cleanBase.isBlank()) cleanChild else "$cleanBase/$cleanChild"
    }

    // 把内部空串根目录展示为 `/`，其余目录补齐前导斜杠。
    fun displayDir(dir: String): String {
        val normalized = normalizeDir(dir)
        return if (normalized.isBlank()) "/" else "/$normalized"
    }

    // 生成用于 UI 展示的完整路径字符串。
    fun displayPath(dir: String, name: String): String {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return displayDir(dir)
        }
        val normalizedDir = normalizeDir(dir)
        return if (normalizedDir.isBlank()) "/$cleanName" else "/$normalizedDir/$cleanName"
    }

    // 解析下载根目录，优先落到系统下载目录，回退到应用私有目录。
    fun resolveDownloadRoot(filesDir: File, externalDownloadsDir: File?): String {
        val parent = externalDownloadsDir ?: File(filesDir, "downloads")
        return File(parent, "myflowhub").absolutePath
    }

    // 解析上传暂存目录，保证本地 offer 文件有稳定 staging 根。
    fun resolveUploadRoot(filesDir: File, externalFilesDir: File?): String {
        val parent = externalFilesDir ?: filesDir
        return File(parent, "myflowhub/upload-staging").absolutePath
    }

    // 预测 pull 完成后本地文件的落点，供 UI 提前展示和校验。
    fun expectedLocalPath(localBaseDir: String, dir: String, name: String): String {
        return expectedTransferPath(localBaseDir, dir, name, "Local download root is required.")
    }

    // 预测 offer 暂存文件在本地 staging 根下的位置。
    fun expectedUploadStagePath(localBaseDir: String, dir: String, name: String): String {
        return expectedTransferPath(localBaseDir, dir, name, "Local upload staging root is required.")
    }

    // UI 输入里的节点 ID 最终要进协议头，这里先做正整数约束。
    fun requirePositiveNodeId(raw: String, label: String = "Node ID"): Long {
        val value = raw.trim().toLongOrNull() ?: throw IllegalStateException("$label must be a positive integer.")
        if (value <= 0) {
            throw IllegalStateException("$label must be a positive integer.")
        }
        return value
    }

    // 创建目录时只允许纯文件夹名，禁止把路径穿透带进协议请求。
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

    // 把 file:list 响应转换成 UI 友好的目录项列表。
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

    // 解析 file:read_text 的成功响应，保留截断标记和原始文本。
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

    // 校验 write 类响应的 op 是否符合预期，并返回供提示条展示的消息。
    fun parseWriteSuccess(raw: String, expectedOp: String = OP_MKDIR): String {
        val obj = requireSuccess(JSONObject(raw), "File write")
        val actualOp = obj.optString("op", "").trim()
        if (expectedOp.isNotBlank() && actualOp.isNotBlank() && actualOp != expectedOp) {
            throw IllegalStateException("Unexpected write op: $actualOp")
        }
        return obj.optString("msg", "").trim()
    }

    // 解析 pull 建链响应，并把本地根目录与预计文件路径补回 UI 侧 DTO。
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

    // 解析 offer 建链响应，并把 resumeFrom 等断点续传信息整理出来。
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
