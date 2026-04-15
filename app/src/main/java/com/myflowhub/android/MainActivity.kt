package com.myflowhub.android
// 本文件实现 Android 宿主中与 `MainActivity` 相关的逻辑。

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import com.myflowhub.android.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val requestNotifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* ignore */ }

    // 宿主启动时先处理前台通知权限，再挂载整个 Compose 根界面。
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        setContent {
            MaterialTheme {
                AppRoot()
            }
        }
    }

    // Android 13+ 需要显式申请通知权限，否则前台服务通知无法正常展示。
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

