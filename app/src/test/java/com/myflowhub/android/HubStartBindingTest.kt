package com.myflowhub.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HubStartBindingTest {
    @Test
    fun resolve_prefersModernStartSignature() {
        val binding = HubStartBinding.resolve(ModernHubmobileApi::class.java)

        assertTrue(binding.supportsRfcommListener)
        assertEquals(
            ":9000||node-hub|/tmp|true|0eef65b8-9374-42ea-b992-6ee2d0699f5c|true",
            binding.invoke(
                HubConfig(
                    addr = ":9000",
                    parentAddr = "",
                    selfId = "node-hub",
                    workDir = "/tmp",
                    rfcommListenEnabled = true,
                    rfcommServiceUuid = BluetoothRfcommSupport.defaultServiceUuid(),
                    rfcommInsecure = true,
                ),
            ),
        )
    }

    @Test
    fun resolve_usesLegacyStartForTcpOnlyConfig() {
        val binding = HubStartBinding.resolve(LegacyHubmobileApi::class.java)

        assertFalse(binding.supportsRfcommListener)
        assertEquals(
            ":9000||node-hub|/tmp",
            binding.invoke(
                HubConfig(
                    addr = ":9000",
                    parentAddr = "",
                    selfId = "node-hub",
                    workDir = "/tmp",
                ),
            ),
        )
    }

    @Test
    fun resolve_rejectsLegacyStartWhenRfcommListenerRequested() {
        val binding = HubStartBinding.resolve(LegacyHubmobileApi::class.java)

        try {
            binding.invoke(
                HubConfig(
                    addr = ":9000",
                    parentAddr = "",
                    selfId = "node-hub",
                    rfcommListenEnabled = true,
                ),
            )
            fail("expected RFCOMM listener compatibility error")
        } catch (t: IllegalStateException) {
            assertTrue(t.message.orEmpty().contains("AAR"))
        }
    }

    class ModernHubmobileApi {
        companion object {
            @JvmStatic
            fun Start(
                addr: String,
                parentAddr: String,
                selfId: String,
                workDir: String,
                rfcommEnable: Boolean,
                rfcommUuid: String,
                rfcommInsecure: Boolean,
            ): String {
                return listOf(addr, parentAddr, selfId, workDir, rfcommEnable, rfcommUuid, rfcommInsecure).joinToString("|")
            }
        }
    }

    class LegacyHubmobileApi {
        companion object {
            @JvmStatic
            fun Start(addr: String, parentAddr: String, selfId: String, workDir: String): String {
                return listOf(addr, parentAddr, selfId, workDir).joinToString("|")
            }
        }
    }
}
