package com.myflowhub.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.myflowhub.android.GoClientBridge
import com.myflowhub.android.Prefs
import java.io.File

private enum class AppTab(val label: String) {
    Login("Login"),
    Hub("Hub"),
    Devices("Devices"),
    Logs("Logs"),
    Protocols("Protocols"),
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val workDir = remember { File(context.filesDir, "hub").absolutePath }

    var hubCfg by remember { mutableStateOf(Prefs.load(context)) }
    var clientCfg by remember { mutableStateOf(Prefs.loadClient(context)) }

    var tab by rememberSaveable { mutableStateOf(AppTab.Login.name) }

    var go by remember { mutableStateOf<GoClientBridge?>(null) }
    var goError by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            GoClientBridge()
        }.onSuccess { bridge ->
            go = bridge
            runCatching { bridge.ensureInit(workDir) }.onFailure { t ->
                goError = t.message ?: t.toString()
            }
        }.onFailure { t ->
            go = null
            goError = t.message ?: t.toString()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry.name,
                        onClick = { tab = entry.name },
                        icon = { Text(entry.label) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        val current = runCatching { AppTab.valueOf(tab) }.getOrNull() ?: AppTab.Login
        when (current) {
            AppTab.Login -> LoginScreen(
                modifier = contentModifier,
                go = go,
                goError = goError,
                workDir = workDir,
                cfg = clientCfg,
                onCfgChange = { updated ->
                    clientCfg = updated
                    Prefs.saveClient(context, updated)
                    hubCfg = hubCfg.copy(selfId = updated.deviceId)
                    Prefs.save(context, hubCfg)
                },
            )

            AppTab.Hub -> HubScreen(
                modifier = contentModifier,
                cfg = hubCfg,
                onCfgChange = { updated ->
                    hubCfg = updated
                    Prefs.save(context, updated)
                    clientCfg = clientCfg.copy(deviceId = updated.selfId)
                    Prefs.saveClient(context, clientCfg)
                },
            )

            AppTab.Devices -> DevicesScreen(
                modifier = contentModifier,
                go = go,
                goError = goError,
                cfg = clientCfg,
            )

            AppTab.Logs -> LogsScreen(
                modifier = contentModifier,
                go = go,
                goError = goError,
            )

            AppTab.Protocols -> ProtocolsScreen(
                modifier = contentModifier,
                go = go,
                goError = goError,
                cfg = clientCfg,
            )
        }
    }
}
