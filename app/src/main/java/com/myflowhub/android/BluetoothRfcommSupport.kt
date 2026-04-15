package com.myflowhub.android
// 本文件实现 Android 宿主中与 `BluetoothRfcommSupport` 相关的逻辑。

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.UUID

object BluetoothRfcommSupport {
    private const val RFCOMM_SCHEME = "bt+rfcomm://"
    private const val DEFAULT_SERVICE_UUID = "0eef65b8-9374-42ea-b992-6ee2d0699f5c"

    fun usesRfcommEndpoint(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.startsWith(RFCOMM_SCHEME, ignoreCase = true)
    }

    fun usesAnyRfcommEndpoint(vararg endpoints: String): Boolean {
        return endpoints.any { usesRfcommEndpoint(it) }
    }

    fun requiresBluetoothPermissionForHub(parentAddr: String, rfcommListenEnabled: Boolean): Boolean {
        return rfcommListenEnabled || usesRfcommEndpoint(parentAddr)
    }

    fun defaultServiceUuid(): String = DEFAULT_SERVICE_UUID

    fun normalizeServiceUuid(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return DEFAULT_SERVICE_UUID
        }
        return UUID.fromString(trimmed).toString()
    }

    fun isValidServiceUuid(raw: String): Boolean {
        return runCatching { normalizeServiceUuid(raw) }.isSuccess
    }

    fun requiredRuntimePermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> {
        return if (sdkInt >= 31) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyList()
        }
    }

    fun missingRuntimePermissions(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): List<String> {
        return requiredRuntimePermissions(sdkInt).filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasRuntimePermissions(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        return missingRuntimePermissions(context, sdkInt).isEmpty()
    }

    fun permissionDeniedMessage(): String {
        return "RFCOMM 需要蓝牙权限（Android 12+ 为 BLUETOOTH_CONNECT），请先授权后重试。"
    }
}
