package com.myflowhub.android

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat

object BluetoothRfcommSupport {
    private const val RFCOMM_SCHEME = "bt+rfcomm://"

    fun usesRfcommEndpoint(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.startsWith(RFCOMM_SCHEME, ignoreCase = true)
    }

    fun usesAnyRfcommEndpoint(vararg endpoints: String): Boolean {
        return endpoints.any { usesRfcommEndpoint(it) }
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
