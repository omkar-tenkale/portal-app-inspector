package io.github.portalappinspector.app.features.logs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.portalappinspector.app.features.network.NetworkFilterMode

internal class LogsPanelState {
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var lastTimestamp by mutableStateOf(0L)
    var filterMode by mutableStateOf(NetworkFilterMode.Include)
    var filterType by mutableStateOf(LogFilterTypes.first())
    var filterValue by mutableStateOf("")
    var filters by mutableStateOf(emptyList<LogFilterRule>())
    val logs = mutableStateListOf<PortalLogEntry>()
}
