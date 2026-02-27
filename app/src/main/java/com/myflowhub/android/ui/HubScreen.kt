package com.myflowhub.android.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.myflowhub.android.HubConfig
import com.myflowhub.android.HubService
import com.myflowhub.android.HubState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Composable
fun HubScreen(
    modifier: Modifier = Modifier,
    cfg: HubConfig,
    notify: (String) -> Unit,
    onCfgChange: (HubConfig) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var svc by remember { mutableStateOf<HubService?>(null) }
    var state by remember { mutableStateOf(HubState()) }
    var opJob by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val b = service as? HubService.LocalBinder ?: return
                svc = b.getService()
                state = svc?.getState() ?: HubState()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                svc = null
            }
        }
        val intent = Intent(context, HubService::class.java)
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        onDispose {
            context.unbindService(conn)
        }
    }

    LaunchedEffect(svc) {
        state = svc?.getState() ?: HubState()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Hub")

        OutlinedTextField(
            value = cfg.addr,
            onValueChange = { onCfgChange(cfg.copy(addr = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Listen addr") },
            singleLine = true,
        )
        OutlinedTextField(
            value = cfg.parentAddr,
            onValueChange = { onCfgChange(cfg.copy(parentAddr = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Parent addr (optional)") },
            singleLine = true,
        )
        OutlinedTextField(
            value = cfg.selfId,
            onValueChange = { onCfgChange(cfg.copy(selfId = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Device ID (SelfID)") },
            singleLine = true,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = !busy && svc != null,
                onClick = {
                    if (cfg.addr.isBlank()) {
                        notify("Listen addr 不能为空，例如 :9000")
                        return@Button
                    }
                    opJob?.cancel()
                    val previousError = state.lastError
                    busy = true
                    notify("正在启动…")
                    val job = scope.launch {
                        val localJob = kotlinx.coroutines.currentCoroutineContext()[Job]
                        try {
                            try {
                                startHubService(context, cfg)
                            } catch (t: Throwable) {
                                notify("启动失败：${t.message ?: t}")
                                return@launch
                            }

                            val result = runCatching {
                                withTimeout(5_000) {
                                    while (true) {
                                        val polled = pollState(svc)
                                        if (polled != null) {
                                            state = polled
                                            if (polled.running) {
                                                return@withTimeout polled
                                            }
                                            if (!polled.running && polled.lastError.isNotBlank() && polled.lastError != previousError) {
                                                throw IllegalStateException(polled.lastError)
                                            }
                                        }
                                        delay(200)
                                    }
                                }
                            }

                            result.onSuccess {
                                notify("启动成功")
                            }.onFailure { t ->
                                if (t is TimeoutCancellationException) {
                                    notify("启动超时（5s），可稍后查看通知/日志")
                                } else {
                                    notify("启动失败：${t.message ?: t}")
                                }
                            }
                        } finally {
                            if (opJob === localJob) {
                                busy = false
                            }
                        }
                    }
                    opJob = job
                },
            ) { Text("Start") }

            Button(
                enabled = !busy && svc != null,
                onClick = {
                    opJob?.cancel()
                    val previousError = state.lastError
                    busy = true
                    notify("正在停止…")
                    val job = scope.launch {
                        val localJob = kotlinx.coroutines.currentCoroutineContext()[Job]
                        try {
                            try {
                                stopHubService(context)
                            } catch (t: Throwable) {
                                notify("停止失败：${t.message ?: t}")
                                return@launch
                            }

                            val result = runCatching {
                                withTimeout(5_000) {
                                    while (true) {
                                        val polled = pollState(svc)
                                        if (polled != null) {
                                            state = polled
                                            if (!polled.running) {
                                                return@withTimeout polled
                                            }
                                            if (polled.lastError.isNotBlank() && polled.lastError != previousError) {
                                                throw IllegalStateException(polled.lastError)
                                            }
                                        } else if (svc == null) {
                                            state = HubState(running = false)
                                            return@withTimeout HubState(running = false)
                                        }
                                        delay(200)
                                    }
                                }
                            }

                            result.onSuccess {
                                notify("已停止")
                            }.onFailure { t ->
                                if (t is TimeoutCancellationException) {
                                    notify("停止超时（5s），可稍后查看通知/日志")
                                } else {
                                    notify("停止失败：${t.message ?: t}")
                                }
                            }
                        } finally {
                            if (opJob === localJob) {
                                busy = false
                            }
                        }
                    }
                    opJob = job
                },
            ) { Text("Stop") }
        }

        StatusBlock(state)
    }
}

@Composable
private fun StatusBlock(state: HubState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Running: ${state.running}")
        Text("NodeID: ${state.nodeId}")
        Text("Parent connected: ${state.parentConnected}")
        if (state.lastError.isNotBlank()) {
            Text("Last error: ${state.lastError}")
        }
    }
}

private fun startHubService(context: Context, cfg: HubConfig) {
    val intent = Intent(context, HubService::class.java).apply {
        action = HubService.ACTION_START
        putExtra(HubService.EXTRA_ADDR, cfg.addr)
        putExtra(HubService.EXTRA_PARENT, cfg.parentAddr)
        putExtra(HubService.EXTRA_SELF_ID, cfg.selfId)
    }
    ContextCompat.startForegroundService(context, intent)
}

private fun stopHubService(context: Context) {
    val intent = Intent(context, HubService::class.java).apply {
        action = HubService.ACTION_STOP
    }
    context.startService(intent)
}

private suspend fun pollState(svc: HubService?): HubState? {
    if (svc == null) return null
    return withContext(Dispatchers.IO) {
        runCatching { svc.getState() }.getOrNull()
    }
}
