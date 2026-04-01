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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.myflowhub.android.BluetoothRfcommSupport
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
    ui: UiNotifier,
    hasBluetoothPermission: Boolean,
    requestBluetoothPermission: () -> Unit,
    onCfgChange: (HubConfig) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var svc by remember { mutableStateOf<HubService?>(null) }
    var state by remember { mutableStateOf(HubState()) }
    var opJob by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    val needsBluetoothPermission = BluetoothRfcommSupport.requiresBluetoothPermissionForHub(
        parentAddr = cfg.parentAddr,
        rfcommListenEnabled = cfg.rfcommListenEnabled,
    )

    DisposableEffect(Unit) {
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val b = service as? HubService.LocalBinder ?: return
                svc = b.getService()
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
        if (svc == null) {
            state = HubState()
            return@LaunchedEffect
        }
        while (true) {
            state = pollState(svc) ?: HubState()
            delay(1_000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("配置")
                OutlinedTextField(
                    value = cfg.addr,
                    onValueChange = { onCfgChange(cfg.copy(addr = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Listen addr") },
                    placeholder = { Text(":9000") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = cfg.parentAddr,
                    onValueChange = { onCfgChange(cfg.copy(parentAddr = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Parent endpoint (optional)") },
                    placeholder = { Text("127.0.0.1:9000 或 bt+rfcomm://AA:BB:CC:DD:EE:FF?uuid=...") },
                    singleLine = true,
                )
                Text("Listen addr 仍为 TCP；可选额外开启 RFCOMM listener。Parent 支持 tcp://host:port、host:port、bt+rfcomm://...")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Enable RFCOMM listener")
                        Text("启用后会在保留 TCP listener 的同时，增加一个 Bluetooth Classic RFCOMM listener。")
                    }
                    Switch(
                        checked = cfg.rfcommListenEnabled,
                        onCheckedChange = { enabled ->
                            val nextUuid = if (enabled && cfg.rfcommServiceUuid.isBlank()) {
                                BluetoothRfcommSupport.defaultServiceUuid()
                            } else {
                                cfg.rfcommServiceUuid
                            }
                            onCfgChange(
                                cfg.copy(
                                    rfcommListenEnabled = enabled,
                                    rfcommServiceUuid = nextUuid,
                                ),
                            )
                        },
                    )
                }
                if (cfg.rfcommListenEnabled) {
                    OutlinedTextField(
                        value = cfg.rfcommServiceUuid,
                        onValueChange = { onCfgChange(cfg.copy(rfcommServiceUuid = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("RFCOMM service UUID") },
                        placeholder = { Text(BluetoothRfcommSupport.defaultServiceUuid()) },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = cfg.rfcommInsecure,
                            onCheckedChange = { checked -> onCfgChange(cfg.copy(rfcommInsecure = checked)) },
                        )
                        Text("Use insecure RFCOMM listener（不推荐）")
                    }
                    if (!BluetoothRfcommSupport.isValidServiceUuid(cfg.rfcommServiceUuid)) {
                        Text("RFCOMM UUID 格式非法，请填写标准 UUID，或留空以使用默认值。")
                    }
                }
                if (needsBluetoothPermission && !hasBluetoothPermission) {
                    Text(BluetoothRfcommSupport.permissionDeniedMessage())
                }
                OutlinedTextField(
                    value = cfg.selfId,
                    onValueChange = { onCfgChange(cfg.copy(selfId = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hub SelfID (-hub)") },
                    singleLine = true,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("操作")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        enabled = !busy,
                        onClick = {
                            if (cfg.addr.isBlank()) {
                                ui.info("Listen addr 不能为空，例如 :9000")
                                return@FilledTonalButton
                            }
                            val currentSelfId = cfg.selfId.trim()
                            if (currentSelfId.isBlank()) {
                                ui.info("Hub SelfID 不能为空（建议以 -hub 结尾）")
                                return@FilledTonalButton
                            }
                            var effectiveCfg = cfg
                            val updates = mutableListOf<String>()
                            val normalizedSelfId = when {
                                currentSelfId.endsWith("-hub") -> currentSelfId
                                currentSelfId.endsWith("-ui") -> currentSelfId.removeSuffix("-ui") + "-hub"
                                else -> "${currentSelfId}-hub"
                            }
                            if (normalizedSelfId != currentSelfId) {
                                effectiveCfg = effectiveCfg.copy(selfId = normalizedSelfId)
                                updates += "已自动修正 Hub SelfID：$normalizedSelfId"
                            }
                            if (effectiveCfg.rfcommListenEnabled) {
                                val normalizedUuid = runCatching {
                                    BluetoothRfcommSupport.normalizeServiceUuid(effectiveCfg.rfcommServiceUuid)
                                }.getOrNull()
                                if (normalizedUuid == null) {
                                    ui.info("RFCOMM UUID 格式非法，请填写标准 UUID，或留空以使用默认值。")
                                    return@FilledTonalButton
                                }
                                if (normalizedUuid != effectiveCfg.rfcommServiceUuid) {
                                    effectiveCfg = effectiveCfg.copy(rfcommServiceUuid = normalizedUuid)
                                    updates += "已规范化 RFCOMM UUID：$normalizedUuid"
                                }
                            }
                            if (effectiveCfg != cfg) {
                                onCfgChange(effectiveCfg)
                            }
                            updates.forEach(ui::info)
                            if (BluetoothRfcommSupport.requiresBluetoothPermissionForHub(effectiveCfg.parentAddr, effectiveCfg.rfcommListenEnabled) &&
                                !hasBluetoothPermission
                            ) {
                                ui.info("RFCOMM 监听或父链需要蓝牙权限，正在请求授权…")
                                requestBluetoothPermission()
                                return@FilledTonalButton
                            }
                            opJob?.cancel()
                            val previousError = state.lastError
                            busy = true
                            ui.progress("正在启动…")
                            val job = scope.launch {
                                val localJob = kotlinx.coroutines.currentCoroutineContext()[Job]
                                try {
                                    try {
                                        startHubService(context, effectiveCfg)
                                    } catch (t: Throwable) {
                                        ui.error("启动失败：${t.message ?: t}")
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
                                        ui.success("启动成功")
                                    }.onFailure { t ->
                                        if (t is TimeoutCancellationException) {
                                            ui.error("启动超时（5s），可稍后查看通知/日志")
                                        } else {
                                            ui.error("启动失败：${t.message ?: t}")
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

                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            opJob?.cancel()
                            val previousError = state.lastError
                            busy = true
                            ui.progress("正在停止…")
                            val job = scope.launch {
                                val localJob = kotlinx.coroutines.currentCoroutineContext()[Job]
                                try {
                                    try {
                                        stopHubService(context)
                                    } catch (t: Throwable) {
                                        ui.error("停止失败：${t.message ?: t}")
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
                                        ui.success("已停止")
                                    }.onFailure { t ->
                                        if (t is TimeoutCancellationException) {
                                            ui.error("停止超时（5s），可稍后查看通知/日志")
                                        } else {
                                            ui.error("停止失败：${t.message ?: t}")
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
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("状态")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (state.running) "Running" else "Stopped") },
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(if (state.parentConnected) "Parent: connected" else "Parent: -") },
                    )
                }
                if (state.nodeId.isNotBlank()) {
                    Text("NodeID: ${state.nodeId}")
                }
                if (state.lastError.isNotBlank()) {
                    Text("Last error: ${state.lastError}")
                }
            }
        }
    }
}

private fun startHubService(context: Context, cfg: HubConfig) {
    val intent = Intent(context, HubService::class.java).apply {
        action = HubService.ACTION_START
        putExtra(HubService.EXTRA_ADDR, cfg.addr)
        putExtra(HubService.EXTRA_PARENT, cfg.parentAddr)
        putExtra(HubService.EXTRA_SELF_ID, cfg.selfId)
        putExtra(HubService.EXTRA_RFCOMM_ENABLE, cfg.rfcommListenEnabled)
        putExtra(HubService.EXTRA_RFCOMM_UUID, cfg.rfcommServiceUuid)
        putExtra(HubService.EXTRA_RFCOMM_INSECURE, cfg.rfcommInsecure)
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
