package com.myflowhub.android
// 本文件覆盖 Android 宿主中与 `FileProtocolSupportTest` 相关的行为。

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class FileProtocolSupportTest {
    @Test
    fun normalizeDir_trimsRootSeparators() {
        assertEquals("", FileProtocolSupport.normalizeDir("/"))
        assertEquals("", FileProtocolSupport.normalizeDir(" . "))
        assertEquals("demo/logs", FileProtocolSupport.normalizeDir(" /demo//logs/ "))
    }

    @Test
    fun requireFolderName_rejectsInvalidNames() {
        assertEquals("config", FileProtocolSupport.requireFolderName(" config "))

        assertThrows(IllegalStateException::class.java) {
            FileProtocolSupport.requireFolderName("")
        }
        assertThrows(IllegalStateException::class.java) {
            FileProtocolSupport.requireFolderName("..")
        }
        assertThrows(IllegalStateException::class.java) {
            FileProtocolSupport.requireFolderName("bad/name")
        }
    }

    @Test
    fun parseList_sortsDirectoriesBeforeFiles() {
        val result = FileProtocolSupport.parseList(
            """
            {
              "code": 1,
              "dir": "/demo",
              "dirs": ["logs", "config"],
              "files": ["z.txt", "a.txt"]
            }
            """.trimIndent(),
        )

        assertEquals("demo", result.dir)
        assertEquals(4, result.entries.size)
        assertTrue(result.entries[0].isDir)
        assertEquals("config", result.entries[0].name)
        assertTrue(result.entries[1].isDir)
        assertEquals("logs", result.entries[1].name)
        assertFalse(result.entries[2].isDir)
        assertEquals("a.txt", result.entries[2].name)
    }

    @Test
    fun parseReadText_keepsSizeAndTruncatedState() {
        val result = FileProtocolSupport.parseReadText(
            """
            {
              "code": 1,
              "dir": "demo",
              "name": "readme.txt",
              "size": 120,
              "text": "hello",
              "truncated": true
            }
            """.trimIndent(),
        )

        assertEquals("demo", result.dir)
        assertEquals("readme.txt", result.name)
        assertEquals(120L, result.size)
        assertEquals("hello", result.text)
        assertTrue(result.truncated)
    }

    @Test
    fun parseWriteSuccess_rejectsFailureCode() {
        val error = assertThrows(IllegalStateException::class.java) {
            FileProtocolSupport.parseWriteSuccess("""{"code":409,"msg":"already exists","op":"mkdir"}""")
        }
        assertEquals("already exists", error.message)
    }

    @Test
    fun resolveDownloadRoot_prefersExternalDownloadsDir() {
        val filesDir = File("/data/user/0/com.myflowhub.android/files")
        val external = File("/storage/emulated/0/Android/data/com.myflowhub.android/files/Download")

        val result = FileProtocolSupport.resolveDownloadRoot(filesDir, external)

        assertEquals(File(external, "myflowhub").absolutePath, result)
    }

    @Test
    fun resolveUploadRoot_usesAppPrivateRoot() {
        val filesDir = File("/data/user/0/com.myflowhub.android/files")
        val external = File("/storage/emulated/0/Android/data/com.myflowhub.android/files")

        val result = FileProtocolSupport.resolveUploadRoot(filesDir, external)

        assertEquals(File(external, "myflowhub/upload-staging").absolutePath, result)
    }

    @Test
    fun parsePullStart_readsLocalPath() {
        val result = FileProtocolSupport.parsePullStart(
            """
            {
              "code": 1,
              "op": "pull",
              "dir": "demo",
              "name": "app.log",
              "size": 18,
              "session_id": "00112233-4455-6677-8899-aabbccddeeff",
              "local_base_dir": "/tmp/myflowhub",
              "local_path": "/tmp/myflowhub/demo/app.log"
            }
            """.trimIndent(),
        )

        assertEquals("demo", result.dir)
        assertEquals("app.log", result.name)
        assertEquals(18L, result.size)
        assertEquals("/tmp/myflowhub", result.localBaseDir)
        assertEquals("/tmp/myflowhub/demo/app.log", result.localPath)
    }

    @Test
    fun parseOfferStart_readsLocalPathAndResumeOffset() {
        val result = FileProtocolSupport.parseOfferStart(
            """
            {
              "code": 1,
              "op": "offer",
              "dir": "demo",
              "name": "app.log",
              "size": 18,
              "session_id": "00112233-4455-6677-8899-aabbccddeeff",
              "resume_from": 4,
              "local_base_dir": "/tmp/upload-staging",
              "local_path": "/tmp/upload-staging/demo/app.log"
            }
            """.trimIndent(),
        )

        assertEquals("demo", result.dir)
        assertEquals("app.log", result.name)
        assertEquals(18L, result.size)
        assertEquals(4L, result.resumeFrom)
        assertEquals("/tmp/upload-staging", result.localBaseDir)
        assertEquals("/tmp/upload-staging/demo/app.log", result.localPath)
    }

    @Test
    fun expectedLocalPath_rejectsInvalidName() {
        assertThrows(IllegalStateException::class.java) {
            FileProtocolSupport.expectedLocalPath("/tmp/myflowhub", "demo", "../bad")
        }
    }

    @Test
    fun expectedUploadStagePath_rejectsTraversalDir() {
        assertThrows(IllegalStateException::class.java) {
            FileProtocolSupport.expectedUploadStagePath("/tmp/upload-staging", "../bad", "demo.txt")
        }
    }

    @Test
    fun requireFileName_rejectsSeparators() {
        assertEquals("demo.txt", FileProtocolSupport.requireFileName(" demo.txt "))
        assertThrows(IllegalStateException::class.java) {
            FileProtocolSupport.requireFileName("../bad.txt")
        }
    }
}
