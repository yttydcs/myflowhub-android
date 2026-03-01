package com.myflowhub.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.surfaceColorAtElevation
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppTab(val label: String) {
    Login("Login"),
    Hub("Hub"),
    Devices("Devices"),
    VarStore("VarStore"),
    Logs("Logs"),
    Protocols("Protocols"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    showMenu: Boolean,
    onMenuClick: () -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    TopAppBar(
        title = { Text("MyFlowHub") },
        navigationIcon = {
            if (showMenu) {
                IconButton(onClick = onMenuClick) {
                    Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val workDir = remember { File(context.filesDir, "hub").absolutePath }

    val identity = remember { Prefs.ensureIdentity(context) }
    var hubCfg by remember { mutableStateOf(Prefs.load(context)) }
    var clientCfg by remember { mutableStateOf(Prefs.loadClient(context)) }

    var tab by rememberSaveable { mutableStateOf(AppTab.Login.name) }

    var go by remember { mutableStateOf<GoClientBridge?>(null) }
    var goError by remember { mutableStateOf("") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val snackEvents = remember {
        MutableSharedFlow<SnackEvent>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }
    val ui = remember {
        UiNotifier { event ->
            snackEvents.tryEmit(event)
        }
    }

    LaunchedEffect(identity.migration) {
        val mig = identity.migration ?: return@LaunchedEffect
        ui.info("已自动迁移身份：Hub=${mig.hubSelfId} / UI=${mig.uiDeviceId}。登录信息已清空，请重新注册/登录。")
    }

    LaunchedEffect(snackbarHostState) {
        snackEvents.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            try {
                snackbarHostState.showSnackbar(
                    message = event.message,
                    duration = event.duration,
                )
            } catch (_: CancellationException) {
                // Replaced by a newer snackbar.
            }
        }
    }

    LaunchedEffect(Unit) {
        val bridgeResult = withContext(Dispatchers.IO) { runCatching { GoClientBridge() } }
        bridgeResult.onSuccess { bridge ->
            go = bridge
            val initResult = withContext(Dispatchers.IO) { runCatching { bridge.ensureInit(workDir) } }
            initResult.onFailure { t ->
                goError = t.message ?: t.toString()
                ui.error("Go 初始化失败：${goError}")
            }
        }.onFailure { t ->
            go = null
            goError = t.message ?: t.toString()
            ui.error("Go 不可用：${goError}")
        }
    }

    val current = runCatching { AppTab.valueOf(tab) }.getOrNull() ?: AppTab.Login

    @Composable
    fun Content(contentModifier: Modifier) {
        when (current) {
            AppTab.Login -> LoginScreen(
                modifier = contentModifier,
                go = go,
                goError = goError,
                workDir = workDir,
                hubSelfId = hubCfg.selfId,
                cfg = clientCfg,
                ui = ui,
                onCfgChange = { updated ->
                    clientCfg = updated
                    Prefs.saveClient(context, updated)
                },
            )

            AppTab.Hub -> HubScreen(
                modifier = contentModifier,
                cfg = hubCfg,
                ui = ui,
                onCfgChange = { updated ->
                    hubCfg = updated
                    Prefs.save(context, updated)
                },
            )

            AppTab.Devices -> DevicesScreen(
                modifier = contentModifier,
                go = go,
                goError = goError,
                cfg = clientCfg,
                ui = ui,
            )

            AppTab.VarStore -> VarStoreScreen(
                modifier = contentModifier,
                go = go,
                goError = goError,
                cfg = clientCfg,
                ui = ui,
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 900.dp
        // 背景（内容区）使用轻微灰阶，分区块 Card 使用更白的 surface，实现“前景块/背景”层次交换。
        val chromeContainerColor = MaterialTheme.colorScheme.surface
        val contentContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(containerColor = chromeContainerColor) {
                    AppTab.entries.forEach { entry ->
                        NavigationRailItem(
                            selected = tab == entry.name,
                            onClick = { tab = entry.name },
                            icon = { Text(entry.label.take(1)) },
                            label = { Text(entry.label) },
                        )
                    }
                }
                Scaffold(
                    topBar = {
                        AppTopBar(showMenu = false, onMenuClick = {})
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = contentContainerColor,
                ) { padding ->
                    Content(contentModifier = Modifier.padding(padding).imePadding())
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = chromeContainerColor) {
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
                        AppTopBar(
                            showMenu = true,
                            onMenuClick = { scope.launch { drawerState.open() } },
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = contentContainerColor,
                ) { padding ->
                    Content(contentModifier = Modifier.padding(padding).imePadding())
                }
            }
        }
    }
}
