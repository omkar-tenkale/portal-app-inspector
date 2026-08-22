package io.github.portalappinspector.app.features.network

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NetworkPanelState(
    appId: String,
) {
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var mockSyncError by mutableStateOf<String?>(null)
    var lastTimestamp by mutableStateOf(0L)
    var bodySheetCall by mutableStateOf<PortalNetworkCall?>(null)
    var mockEditorInitial by mutableStateOf<PortalNetworkMock?>(null)
    var mocksSheetOpen by mutableStateOf(false)
    var filterMode by mutableStateOf(NetworkFilterMode.Include)
    var filterType by mutableStateOf(NetworkFilterTypes.first())
    var filterValue by mutableStateOf("")
    var filters by mutableStateOf(emptyList<NetworkFilterRule>())
    val calls = mutableStateListOf<PortalNetworkCall>()
    
    var mocks by mutableStateOf(PortalNetworkMockStore.load(appId))
}
