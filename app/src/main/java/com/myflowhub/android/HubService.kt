package com.myflowhub.android
// 本文件实现 Android 宿主中与 `HubService` 相关的逻辑。

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
        // 暴露真实 service 实例，供页面轮询状态和发起控制。
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

    // 允许前台页面绑定到 service，读取当前 Hub 运行状态。
    override fun onBind(intent: Intent?): IBinder = binder

    // 统一接收 start/stop/restore 三类入口，并保持 service 可被系统拉起恢复。
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(buildConfig(intent))
            ACTION_STOP -> handleStop()
            else -> handleRestoreOrRefresh()
        }
        return START_STICKY
    }

    // 销毁时停止轮询协程，避免后台继续占用资源。
    override fun onDestroy() {
        stopStatusMonitor()
        serviceScope.cancel()
        super.onDestroy()
    }

    // 对外返回最新状态快照，供 Compose 页面展示。
    fun getState(): HubState = refreshState()

    // 按最新配置启动 Go Hub，并同步 desiredRunning 与通知状态。
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

    // 显式停止 Hub，并清理前台 service 占位状态。
    private fun handleStop() {
        Prefs.setHubDesiredRunning(this, false)
        stopStatusMonitor()
        state = bridge.stop()
        foregroundStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // 处理系统重建 service 后的恢复场景，优先尝试使用最近一次启动快照。
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

    // 从 Go 桥接刷新状态，并在“停止但无新错误”时保留上一条错误信息方便排障。
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

    // 当前只负责把最新状态同步到前台通知，方便用户在后台观察。
    private fun publishState(updateNotification: Boolean) {
        if (updateNotification) {
            showForegroundState(HubServiceSupport.notificationText(state))
        }
    }

    // 低频轮询 Go 侧状态，驱动通知和 desiredRunning 的恢复/回落。
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

    // 停止后台状态轮询，避免重复协程同时工作。
    private fun stopStatusMonitor() {
        monitorJob?.cancel()
        monitorJob = null
    }

    // 从 Intent 还原 HubConfig，并补齐运行时目录等宿主侧信息。
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

    // 统一约定 Android 侧 Hub 工作目录，供 keys/logs/runtime 复用。
    private fun hubWorkDir(): String = File(filesDir, "hub").absolutePath

    // 维护前台通知文本，确保 Android 不会因后台运行而回收 service。
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

    // Android 8+ 需要先创建 channel，后续通知更新才能稳定落到同一分组。
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

    // 统一取系统通知管理器，避免散落强转。
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
