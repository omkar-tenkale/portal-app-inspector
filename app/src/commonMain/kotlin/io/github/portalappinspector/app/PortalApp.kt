package io.github.portalappinspector.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.zIndex
import io.github.docklayout.DockLayout
import io.github.docklayout.rememberDockState
import io.github.docklayout.tabRenderers
import io.github.portalappinspector.PortalHealth
import io.github.portalappinspector.PortalManifest
import io.github.portalappinspector.app.data.LogsPluginId
import io.github.portalappinspector.app.data.NetworkPluginId
import io.github.portalappinspector.app.data.PortalConnection
import io.github.portalappinspector.app.data.PortalConnectionStore
import io.github.portalappinspector.app.data.PortalLaunchParams
import io.github.portalappinspector.app.data.PortalSourceClient
import io.github.portalappinspector.app.data.ScreenMirrorPluginId
import io.github.portalappinspector.app.data.SharedPrefsPluginId
import io.github.portalappinspector.app.data.matchingPackage
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
import io.github.portalappinspector.app.ui.UnsupportedPluginPanel
import io.github.portalappinspector.app.ui.connection.MobileConnectionState
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
        val mobileView = launchParams.mobileView
        val appId = launchParams.appId
        val initialConnection = remember(launchParams) {
            launchParams.connection
                ?: PortalConnectionStore.latest()?.toPortalConnection()
                ?: PortalConnection("", "")
        }
        var connection by remember { mutableStateOf(initialConnection) }
        val client = remember { PortalSourceClient() }
        var health by remember { mutableStateOf<PortalHealth?>(null) }
        var manifest by remember { mutableStateOf<PortalManifest?>(null) }
        var connectingManifest by remember { mutableStateOf<PortalManifest?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var connecting by remember { mutableStateOf(false) }
        var showSetupRequired by remember { mutableStateOf(false) }
        var currentAppId by remember { mutableStateOf(appId) }
        var layoutRevision by remember { mutableStateOf(0) }
        var savedConnections by remember { mutableStateOf(PortalConnectionStore.load()) }
        val filesPanelState = remember(currentAppId) { FilesPanelState(currentAppId ?: "default") }
        val dynamicTabs = remember { mutableStateListOf<PortalTab>() }
        var activeTabRequest by remember { mutableStateOf<PortalTab?>(null) }
        var persistedLayout by remember(currentAppId) { mutableStateOf(DockLayoutPersistence.load(currentAppId)) }

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

        suspend fun connectOnce(targetConnection: PortalConnection): Boolean =
            runCatching {
                val nextHealth = client.health(targetConnection)
                val nextManifest = client.manifest(targetConnection)
                health = nextHealth
                connectingManifest = nextManifest
                savedConnections = PortalConnectionStore.upsert(nextManifest, targetConnection)
                currentAppId = nextManifest.appId
                persistedLayout = DockLayoutPersistence.load(nextManifest.appId)
                navigateToApp(nextManifest.appId)
                delay(520L)
                manifest = nextManifest
            }.onFailure { throwable ->
                health = null
                connectingManifest = null
                error = throwable.message ?: throwable::class.simpleName
            }.isSuccess

        LaunchedEffect(connection) {
            if (connection.isValid) {
                val targetConnection = connection
                connecting = true
                showSetupRequired = false
                error = null
                health = null
                manifest = null
                connectingManifest = null
                val setupTimer = launch {
                    delay(1_000L)
                    if (manifest == null && connectingManifest == null) {
                        showSetupRequired = true
                    }
                }
                while (manifest == null) {
                    if (connectOnce(targetConnection)) break
                    delay(1_000L)
                    showSetupRequired = true
                }
                setupTimer.cancel()
                connecting = false
            } else {
                connecting = false
                showSetupRequired = false
                health = null
                manifest = null
                connectingManifest = null
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
                if (!mobileView && manifest != null) {
                    TopBar(
                        manifest = manifest,
                        savedConnections = savedConnections,
                        onSelectConnection = { saved ->
                            connection = saved.toPortalConnection()
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = manifest != null,
                        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
                            animationSpec = tween(260),
                            initialOffsetY = { it / 36 },
                        ),
                        exit = fadeOut(animationSpec = tween(160)),
                        modifier = Modifier.fillMaxSize(),
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
                                            connection = connection,
                                            client = client,
                                            enabled = manifest?.plugins?.any { it.id == "portal:files" } == true,
                                            sharedPrefsEnabled = manifest?.plugins?.any { it.id == SharedPrefsPluginId } == true,
                                            onOpenSharedPrefsTab = ::openSharedPrefsTab,
                                        )
                                    }
                                    renderer<NetworkTab> { _, _ ->
                                        NetworkPanel(
                                            connection = connection,
                                            client = client,
                                            enabled = manifest?.plugins?.any { it.id == NetworkPluginId } == true,
                                            appId = manifest?.appId,
                                            onOpenResponseTab = ::openResponseTab,
                                            mobileView = mobileView,
                                        )
                                    }
                                    renderer<LogsTab> { _, _ ->
                                        LogsPanel(
                                            connection = connection,
                                            client = client,
                                            enabled = manifest?.plugins?.any { it.id == LogsPluginId } == true,
                                        )
                                    }
                                    renderer<ScreenMirrorTab> { _, _ ->
                                        ScreenMirrorPanel(
                                            connection = connection,
                                            client = client,
                                            enabled = manifest?.plugins?.any { it.id == ScreenMirrorPluginId } == true,
                                        )
                                    }
                                    renderer<NetworkResponseTab> { tab, _ ->
                                        NetworkResponsePanel(tab.call)
                                    }
                                    renderer<SharedPrefsTab> { tab, _ ->
                                        SharedPrefsPanel(
                                            tab = tab,
                                            connection = connection,
                                            client = client,
                                            enabled = manifest?.plugins?.any { it.id == SharedPrefsPluginId } == true,
                                        )
                                    }
                                    renderer<UnsupportedPluginTab> { tab, _ ->
                                        UnsupportedPluginPanel(tab.pluginId)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize(),
                                tabIcon = { tab, selected -> PortalTabIcon(tab, selected) },
                            )
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = manifest == null && !mobileView,
                        enter = fadeIn(animationSpec = tween(260)) + slideInVertically(
                            animationSpec = tween(320),
                            initialOffsetY = { it / 18 },
                        ),
                        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(
                            animationSpec = tween(220),
                            targetOffsetY = { -it / 28 },
                        ),
                    ) {
                        val persistedAppIconPngBase64 = appId
                            ?.let { savedConnections.matchingPackage(it)?.appIconPngBase64 }
                        WelcomeConnectionPanel(
                            connection = connection,
                            appIconPngBase64 = connectingManifest?.appIconPngBase64 ?: persistedAppIconPngBase64,
                            animateAppIconReveal = connectingManifest?.appIconPngBase64 != null &&
                                persistedAppIconPngBase64 == null,
                            showSetupRequired = showSetupRequired,
                            isEmulator = launchParams.isEmulator,
                        )
                    }
                    if (manifest == null && mobileView) {
                        MobileConnectionState(
                            connecting = connecting,
                            error = error,
                        )
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
