package io.github.portalappinspector.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.docklayout.DockLayout
import io.github.docklayout.rememberDockState
import io.github.docklayout.tabRenderers
import io.github.portalappinspector.PortalManifest
import io.github.portalappinspector.app.data.PortalConnectionStore
import io.github.portalappinspector.app.data.PortalLaunchParams
import io.github.portalappinspector.app.data.PortalSession
import io.github.portalappinspector.app.data.PortalSourceClient
import io.github.portalappinspector.app.data.navigateToApp
import io.github.portalappinspector.app.features.files.FilesPanel
import io.github.portalappinspector.app.features.files.FilesPanelState
import io.github.portalappinspector.app.features.files.PortalFileItem
import io.github.portalappinspector.app.features.logs.LogsPanel
import io.github.portalappinspector.app.features.network.NetworkPanel
import io.github.portalappinspector.app.features.network.NetworkResponsePanel
import io.github.portalappinspector.app.features.network.PortalNetworkCall
import io.github.portalappinspector.app.features.screenmirror.ScreenMirrorPanel
import io.github.portalappinspector.app.features.sharedprefs.SharedPrefsPanel
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.PortalVectorIconButton
import io.github.portalappinspector.app.ui.Text
import io.github.portalappinspector.app.ui.UnsupportedPluginPanel
import io.github.portalappinspector.app.ui.connection.WelcomeConnectionPanel
import io.github.portalappinspector.app.ui.tabs.FilesTab
import io.github.portalappinspector.app.ui.tabs.LogsTab
import io.github.portalappinspector.app.ui.tabs.NetworkResponseTab
import io.github.portalappinspector.app.ui.tabs.NetworkTab
import io.github.portalappinspector.app.ui.tabs.PortalTab
import io.github.portalappinspector.app.ui.tabs.PortalTabIcon
import io.github.portalappinspector.app.ui.tabs.ScreenMirrorTab
import io.github.portalappinspector.app.ui.tabs.SharedPrefsTab
import io.github.portalappinspector.app.ui.tabs.UnsupportedPluginTab
import io.github.portalappinspector.app.ui.icons.PortalTabIcons
import io.github.portalappinspector.app.ui.toast.LocalToastHost
import io.github.portalappinspector.app.ui.toast.ToastHost
import io.github.portalappinspector.app.ui.toast.ToastHostState
import io.github.portalappinspector.app.ui.topbar.TopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun PortalApp() {
    val toastHost = remember { ToastHostState() }
    CompositionLocalProvider(LocalToastHost provides toastHost) {
        val launchParams = remember { PortalLaunchParams.fromUrl() }
        var savedConnections by remember { mutableStateOf(PortalConnectionStore.load()) }
        var connectionCandidate by remember {
            mutableStateOf(launchParams.connection ?: savedConnections.firstOrNull()?.connection)
        }
        var activeSession by remember { mutableStateOf<PortalSession?>(null) }
        var connectionFailing by remember { mutableStateOf(false) }
        var showOverlayConnectionDialog by remember { mutableStateOf(false) }
        var showSetupRequired by remember { mutableStateOf(false) }

        var layoutRevision by remember { mutableStateOf(0) }
        val dynamicTabs = remember { mutableStateListOf<PortalTab>() }
        var activeTabRequest by remember { mutableStateOf<PortalTab?>(null) }

        fun openResponseTab(call: PortalNetworkCall) {
            val tab = NetworkResponseTab(call)
            dynamicTabs.removeAll { it is NetworkResponseTab && it.call.id == call.id }
            dynamicTabs += tab
            activeTabRequest = tab
            layoutRevision += 1
        }

        fun openSharedPrefsTab(file: PortalFileItem) {
            val tab = SharedPrefsTab(file)
            dynamicTabs.removeAll { it is SharedPrefsTab && it.file.path == file.path }
            dynamicTabs += tab
            activeTabRequest = tab
            layoutRevision += 1
        }

        LaunchedEffect(connectionCandidate) {
            val client = PortalSourceClient()
            val target = connectionCandidate ?: return@LaunchedEffect
            showSetupRequired = false
            val setupTimer = launch {
                delay(1_000L)
                showSetupRequired = true
            }
            while (true) {
                val success = runCatching {
                    val manifest = client.manifest(target)
                    savedConnections = PortalConnectionStore.upsert(manifest, target)

                    val session = PortalSession(
                        connection = target,
                        manifest = manifest,
                        client = client,
                        onConnectionFailing = { connectionFailing = true }
                    )
                    activeSession = session
                    showOverlayConnectionDialog = false
                    connectionFailing = false
                    navigateToApp(manifest.appId)
                    setupTimer.cancel()
                }.isSuccess
                if (success) break
                delay(1_000L)
                showSetupRequired = true
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PortalColors.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PortalColors.background),
            ) {
                if (activeSession == null) {
                    if (connectionCandidate == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Paste a URL copied from the app into the browser to get started.",
                                color = PortalColors.muted,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            WelcomeConnectionPanel(
                                connection = connectionCandidate!!,
                                animateAppIconReveal = true,
                                showSetupRequired = showSetupRequired,
                                isEmulator = launchParams.isEmulator,
                            )
                        }
                    }
                } else {
                    val session = activeSession!!
                    val currentAppId = session.appId
                    var persistedLayout by remember(currentAppId) { mutableStateOf(DockLayoutPersistence.load(currentAppId)) }
                    val filesPanelState = remember(currentAppId) { FilesPanelState(currentAppId) }

                    if (launchParams.mobileView.not()) {
                        TopBar(
                            manifest = session.manifest,
                            savedConnections = savedConnections,
                            onSelectConnection = { saved ->
                                connectionCandidate = saved.toPortalConnection()
                            },
                            connectionFailing = connectionFailing,
                            onFixConnection = { showOverlayConnectionDialog = true }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        key(layoutRevision) {
                            val dockTabs = listOf(NetworkTab, LogsTab, ScreenMirrorTab, FilesTab) + dynamicTabs
                            val initialDockLayout = remember(layoutRevision, dockTabs, activeTabRequest, persistedLayout, currentAppId) {
                                DockLayoutPersistence.buildInitialLayout(
                                    restored = persistedLayout,
                                    tabs = dockTabs,
                                    activeTab = activeTabRequest,
                                )
                            }
                            val layoutState = rememberDockState<PortalTab>(
                                initialLayout = initialDockLayout,
                                onLayoutChanged = { layout ->
                                    persistedLayout = layout
                                    DockLayoutPersistence.save(currentAppId, layout)
                                },
                            )

                            DockLayout(
                                state = layoutState,
                                renderers = tabRenderers {
                                    renderer<FilesTab> { _, _ ->
                                        FilesPanel(
                                            state = filesPanelState,
                                            api = session,
                                            onOpenSharedPrefsTab = ::openSharedPrefsTab,
                                        )
                                    }
                                    renderer<NetworkTab> { _, _ ->
                                        NetworkPanel(
                                            api = session,
                                            onOpenResponseTab = ::openResponseTab,
                                        )
                                    }
                                    renderer<LogsTab> { _, _ ->
                                        LogsPanel(
                                            api = session,
                                        )
                                    }
                                    renderer<ScreenMirrorTab> { _, _ ->
                                        ScreenMirrorPanel(
                                            api = session,
                                        )
                                    }
                                    renderer<NetworkResponseTab> { tab, _ ->
                                        NetworkResponsePanel(tab.call)
                                    }
                                    renderer<SharedPrefsTab> { tab, _ ->
                                        SharedPrefsPanel(
                                            tab = tab,
                                            api = session,
                                        )
                                    }
                                    renderer<UnsupportedPluginTab> { tab, _ ->
                                        UnsupportedPluginPanel(tab.pluginId)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                tabIcon = { tab, selected -> PortalTabIcon(tab, selected) },
                            )
                        }

                        if (showOverlayConnectionDialog) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .zIndex(100f),
                                contentAlignment = Alignment.Center
                            ) {
                                WelcomeConnectionPanel(
                                    connection = session.connection,
                                    animateAppIconReveal = false,
                                    showSetupRequired = true,
                                    isEmulator = launchParams.isEmulator
                                )
                                PortalVectorIconButton(
                                    icon = PortalTabIcons.Close,
                                    onClick = { showOverlayConnectionDialog = false },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            ToastHost(
                state = toastHost,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(20f),
            )
        }
    }
}
