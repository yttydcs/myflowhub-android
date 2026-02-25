package com.myflowhub.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class HubService : Service() {
    class LocalBinder(private val service: HubService) : Binder() {
        fun getService(): HubService = service
    }

    private val binder = LocalBinder(this)
    private val bridge: HubBridge = StubHubBridge()

    @Volatile
    private var state: HubState = HubState()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val cfg = HubConfig(
                    addr = intent.getStringExtra(EXTRA_ADDR) ?: ":9000",
                    parentAddr = intent.getStringExtra(EXTRA_PARENT) ?: "",
                    selfId = intent.getStringExtra(EXTRA_SELF_ID) ?: "",
                )
                startForegroundWithState("Starting…")
                state = bridge.start(cfg)
                startForegroundWithState(if (state.running) "Running" else "Stopped")
            }
            ACTION_STOP -> {
                state = bridge.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // no-op
            }
        }
        return START_STICKY
    }

    fun getState(): HubState = bridge.status()

    private fun startForegroundWithState(text: String) {
        createChannelIfNeeded()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyFlowHub")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            return
        }
        val ch = NotificationChannel(
            CHANNEL_ID,
            "MyFlowHub",
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(ch)
    }

    companion object {
        const val ACTION_START = "com.myflowhub.android.action.START"
        const val ACTION_STOP = "com.myflowhub.android.action.STOP"

        const val EXTRA_ADDR = "addr"
        const val EXTRA_PARENT = "parent"
        const val EXTRA_SELF_ID = "self_id"

        private const val CHANNEL_ID = "myflowhub"
        private const val NOTIFICATION_ID = 1
    }
}

