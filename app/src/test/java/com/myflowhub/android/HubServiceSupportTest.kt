package com.myflowhub.android
// Context: This file supports the Android app or gomobile host flow around HubServiceSupportTest.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubServiceSupportTest {
    @Test
    fun restoreConfig_returnsNullWhenNotDesired() {
        val snapshot = HubConfig(
            addr = ":9000",
            parentAddr = "127.0.0.1:9001",
            selfId = "demo-hub",
            rfcommListenEnabled = true,
            rfcommServiceUuid = "",
            rfcommInsecure = true,
        )

        assertNull(HubServiceSupport.restoreConfig(snapshot, desiredRunning = false, workDir = "/tmp/hub"))
    }

    @Test
    fun restoreConfig_normalizesSnapshotForRuntime() {
        val snapshot = HubConfig(
            addr = "",
            parentAddr = " 127.0.0.1:9001 ",
            selfId = " demo-hub ",
            rfcommListenEnabled = true,
            rfcommServiceUuid = "",
            rfcommInsecure = false,
        )

        val restored = HubServiceSupport.restoreConfig(snapshot, desiredRunning = true, workDir = "/tmp/hub")

        assertEquals(":9000", restored?.addr)
        assertEquals("127.0.0.1:9001", restored?.parentAddr)
        assertEquals("demo-hub", restored?.selfId)
        assertEquals("/tmp/hub", restored?.workDir)
        assertEquals(BluetoothRfcommSupport.defaultServiceUuid(), restored?.rfcommServiceUuid)
        assertTrue(restored?.rfcommListenEnabled == true)
    }

    @Test
    fun notificationText_includesRunningDetails() {
        val text = HubServiceSupport.notificationText(
            HubState(
                running = true,
                nodeId = "42",
                parentConnected = true,
            ),
        )

        assertEquals("Running | node 42 | parent connected", text)
    }

    @Test
    fun notificationText_includesStoppedErrorSummary() {
        val text = HubServiceSupport.notificationText(
            HubState(
                running = false,
                lastError = "permission denied while reopening saved hub snapshot",
            ),
        )

        assertTrue(text.startsWith("Stopped: permission denied"))
    }
}
