package com.myflowhub.android
// Context: This file supports the Android app or gomobile host flow around HubService.

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HubService : Service() {
    class LocalBinder(private val service: HubService) : Binder() {
        fun getService(): HubService = service
    }

    private val binder = LocalBinder(this)
    private val bridge: HubBridge = try {
        GoHubBridge()
    } catch (_: Throwable) {
        StubHubBridge()
    }

    @Volatile
    private var state: HubState = HubState()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var foregroundStarted = false
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(buildConfig(intent))
            ACTION_STOP -> handleStop()
            else -> handleRestoreOrRefresh()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopStatusMonitor()
        serviceScope.cancel()
        super.onDestroy()
    }

    fun getState(): HubState = refreshState()

    private fun handleStart(cfg: HubConfig) {
        Prefs.saveHubRunSnapshot(this, cfg)
        Prefs.setHubDesiredRunning(this, true)
        showForegroundState("Starting…")
        state = bridge.start(cfg)
        if (!state.running) {
            Prefs.setHubDesiredRunning(this, false)
        }
        publishState(updateNotification = true)
        if (state.running) {
            startStatusMonitor()
        } else {
            stopStatusMonitor()
        }
    }

    private fun handleStop() {
        Prefs.setHubDesiredRunning(this, false)
        stopStatusMonitor()
        state = bridge.stop()
        foregroundStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleRestoreOrRefresh() {
        val desiredRunning = Prefs.isHubDesiredRunning(this)
        val cfg = try {
            HubServiceSupport.restoreConfig(Prefs.loadHubRunSnapshot(this), desiredRunning, hubWorkDir())
        } catch (t: Throwable) {
            Prefs.setHubDesiredRunning(this, false)
            state = HubState(running = false, lastError = "Restore failed: ${t.message ?: t}")
            showForegroundState(HubServiceSupport.notificationText(state))
            stopStatusMonitor()
            return
        }

        if (desiredRunning && cfg == null) {
            Prefs.setHubDesiredRunning(this, false)
            state = HubState(running = false, lastError = "Restore failed: missing saved start config")
            showForegroundState(HubServiceSupport.notificationText(state))
            stopStatusMonitor()
            return
        }

        if (cfg != null) {
            showForegroundState("Restoring…")
            state = bridge.start(cfg)
            if (!state.running) {
                Prefs.setHubDesiredRunning(this, false)
            }
            publishState(updateNotification = true)
            if (state.running) {
                startStatusMonitor()
            } else {
                stopStatusMonitor()
            }
            return
        }

        state = refreshState()
        if (state.running) {
            showForegroundState(HubServiceSupport.notificationText(state))
            startStatusMonitor()
        } else {
            stopStatusMonitor()
            stopSelf()
        }
    }

    private fun refreshState(): HubState {
        val latest = bridge.status()
        state = if (!latest.running && latest.lastError.isBlank() && state.lastError.isNotBlank()) {
            state.copy(
                running = false,
                nodeId = latest.nodeId,
                parentConnected = latest.parentConnected,
            )
        } else {
            latest
        }
        return state
    }

    private fun publishState(updateNotification: Boolean) {
        if (updateNotification) {
            showForegroundState(HubServiceSupport.notificationText(state))
        }
    }

    private fun startStatusMonitor() {
        if (monitorJob?.isActive == true) {
            return
        }
        monitorJob = serviceScope.launch {
            while (isActive) {
                val previous = state
                val latest = refreshState()
                if (latest != previous) {
                    showForegroundState(HubServiceSupport.notificationText(latest))
                }
                if (!latest.running) {
                    Prefs.setHubDesiredRunning(this@HubService, false)
                    return@launch
                }
                delay(2_000)
            }
        }
    }

    private fun stopStatusMonitor() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun buildConfig(intent: Intent): HubConfig {
        return HubServiceSupport.runtimeConfig(
            HubConfig(
                addr = intent.getStringExtra(EXTRA_ADDR) ?: ":9000",
                parentAddr = intent.getStringExtra(EXTRA_PARENT) ?: "",
                selfId = intent.getStringExtra(EXTRA_SELF_ID) ?: "",
                rfcommListenEnabled = intent.getBooleanExtra(EXTRA_RFCOMM_ENABLE, false),
                rfcommServiceUuid = intent.getStringExtra(EXTRA_RFCOMM_UUID) ?: "",
                rfcommInsecure = intent.getBooleanExtra(EXTRA_RFCOMM_INSECURE, false),
            ),
            hubWorkDir(),
        )
    }

    private fun hubWorkDir(): String = File(filesDir, "hub").absolutePath

    private fun showForegroundState(text: String) {
        createChannelIfNeeded()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyFlowHub")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
        if (!foregroundStarted) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            foregroundStarted = true
            return
        }
        notificationManager().notify(NOTIFICATION_ID, notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val nm = notificationManager()
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

    private fun notificationManager(): NotificationManager {
        return getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        const val ACTION_START = "com.myflowhub.android.action.START"
        const val ACTION_STOP = "com.myflowhub.android.action.STOP"

        const val EXTRA_ADDR = "addr"
        const val EXTRA_PARENT = "parent"
        const val EXTRA_SELF_ID = "self_id"
        const val EXTRA_RFCOMM_ENABLE = "rfcomm_enable"
        const val EXTRA_RFCOMM_UUID = "rfcomm_uuid"
        const val EXTRA_RFCOMM_INSECURE = "rfcomm_insecure"

        private const val CHANNEL_ID = "myflowhub"
        private const val NOTIFICATION_ID = 1
    }
}
