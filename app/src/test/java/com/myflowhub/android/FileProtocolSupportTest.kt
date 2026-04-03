package com.myflowhub.android

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
}
