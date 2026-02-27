package com.myflowhub.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myflowhub.android.GoClientBridge
import com.myflowhub.android.Prefs
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppTab(val label: String) {
    Login("Login"),
    Hub("Hub"),
    Devices("Devices"),
    Logs("Logs"),
    Protocols("Protocols"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val workDir = remember { File(context.filesDir, "hub").absolutePath }

    var hubCfg by remember { mutableStateOf(Prefs.load(context)) }
    var clientCfg by remember { mutableStateOf(Prefs.loadClient(context)) }

    var tab by rememberSaveable { mutableStateOf(AppTab.Login.name) }

    var go by remember { mutableStateOf<GoClientBridge?>(null) }
    var goError by remember { mutableStateOf("") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { msg ->
        scope.launch {
            snackbarHostState.showSnackbar(message = msg)
        }
    }

    LaunchedEffect(Unit) {
        val bridgeResult = withContext(Dispatchers.IO) { runCatching { GoClientBridge() } }
        bridgeResult.onSuccess { bridge ->
            go = bridge
            val initResult = withContext(Dispatchers.IO) { runCatching { bridge.ensureInit(workDir) } }
            initResult.onFailure { t ->
                goError = t.message ?: t.toString()
                notify("Go 初始化失败：${goError}")
            }
        }.onFailure { t ->
            go = null
            goError = t.message ?: t.toString()
            notify("Go 不可用：${goError}")
        }
    }

    val current = runCatching { AppTab.valueOf(tab) }.getOrNull() ?: AppTab.Login

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("MyFlowHub")
                }
                AppTab.entries.forEach { entry ->
                    NavigationDrawerItem(
                        label = { Text(entry.label) },
                        selected = tab == entry.name,
                        onClick = {
                            tab = entry.name
                            scope.launch { drawerState.close() }
                        },
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(current.label) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            val contentModifier = Modifier.padding(padding)
            when (current) {
                AppTab.Login -> LoginScreen(
                    modifier = contentModifier,
                    go = go,
                    goError = goError,
                    workDir = workDir,
                    cfg = clientCfg,
                    notify = notify,
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
                    notify = notify,
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
}
