package com.myflowhub.android

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val requestNotifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* ignore */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        setContent {
            MaterialTheme {
                HubScreen()
            }
        }
    }

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

@Composable
private fun HubScreen() {
    val context = LocalContext.current
    var cfg by remember { mutableStateOf(Prefs.load(context)) }
    var svc by remember { mutableStateOf<HubService?>(null) }
    var state by remember { mutableStateOf(HubState()) }

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
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("MyFlowHub (Android M0)")

        OutlinedTextField(
            value = cfg.addr,
            onValueChange = { cfg = cfg.copy(addr = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Listen addr") },
            singleLine = true,
        )
        OutlinedTextField(
            value = cfg.parentAddr,
            onValueChange = { cfg = cfg.copy(parentAddr = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Parent addr (optional)") },
            singleLine = true,
        )
        OutlinedTextField(
            value = cfg.selfId,
            onValueChange = { cfg = cfg.copy(selfId = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Self ID") },
            singleLine = true,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                Prefs.save(context, cfg)
                startHubService(context, cfg)
                state = svc?.getState() ?: state
            }) { Text("Start") }

            Button(onClick = {
                stopHubService(context)
                state = svc?.getState() ?: HubState(running = false)
            }) { Text("Stop") }
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

