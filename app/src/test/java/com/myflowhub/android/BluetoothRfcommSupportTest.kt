package com.myflowhub.android

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothRfcommSupportTest {
    @Test
    fun usesRfcommEndpoint_acceptsScheme_caseInsensitive() {
        assertTrue(BluetoothRfcommSupport.usesRfcommEndpoint("bt+rfcomm://AA:BB:CC:DD:EE:FF"))
        assertTrue(BluetoothRfcommSupport.usesRfcommEndpoint("  BT+RFCOMM://AA:BB:CC:DD:EE:FF  "))
    }

    @Test
    fun usesRfcommEndpoint_rejectsTcpAndLegacyAddr() {
        assertFalse(BluetoothRfcommSupport.usesRfcommEndpoint("127.0.0.1:9000"))
        assertFalse(BluetoothRfcommSupport.usesRfcommEndpoint("tcp://127.0.0.1:9000"))
        assertFalse(BluetoothRfcommSupport.usesRfcommEndpoint(""))
    }

    @Test
    fun usesAnyRfcommEndpoint_matchesAnyInput() {
        assertTrue(
            BluetoothRfcommSupport.usesAnyRfcommEndpoint(
                "",
                "tcp://127.0.0.1:9000",
                "bt+rfcomm://AA:BB:CC:DD:EE:FF",
            ),
        )
        assertFalse(
            BluetoothRfcommSupport.usesAnyRfcommEndpoint(
                "127.0.0.1:9000",
                "tcp://127.0.0.1:9000",
            ),
        )
    }

    @Test
    fun requiredRuntimePermissions_onlyNeededOnAndroid12Plus() {
        assertEquals(emptyList<String>(), BluetoothRfcommSupport.requiredRuntimePermissions(30))
        assertEquals(listOf(Manifest.permission.BLUETOOTH_CONNECT), BluetoothRfcommSupport.requiredRuntimePermissions(31))
        assertEquals(listOf(Manifest.permission.BLUETOOTH_CONNECT), BluetoothRfcommSupport.requiredRuntimePermissions(34))
    }

    @Test
    fun requiresBluetoothPermissionForHub_matchesParentOrListener() {
        assertFalse(BluetoothRfcommSupport.requiresBluetoothPermissionForHub("", rfcommListenEnabled = false))
        assertTrue(BluetoothRfcommSupport.requiresBluetoothPermissionForHub("bt+rfcomm://AA:BB", rfcommListenEnabled = false))
        assertTrue(BluetoothRfcommSupport.requiresBluetoothPermissionForHub("", rfcommListenEnabled = true))
    }

    @Test
    fun normalizeServiceUuid_usesDefaultForBlankAndCanonicalizesValidInput() {
        assertEquals(
            BluetoothRfcommSupport.defaultServiceUuid(),
            BluetoothRfcommSupport.normalizeServiceUuid("   "),
        )
        assertEquals(
            "0eef65b8-9374-42ea-b992-6ee2d0699f5c",
            BluetoothRfcommSupport.normalizeServiceUuid("0EEF65B8-9374-42EA-B992-6EE2D0699F5C"),
        )
    }

    @Test
    fun isValidServiceUuid_rejectsInvalidUuid() {
        assertTrue(BluetoothRfcommSupport.isValidServiceUuid(BluetoothRfcommSupport.defaultServiceUuid()))
        assertFalse(BluetoothRfcommSupport.isValidServiceUuid("not-a-uuid"))
    }
}
