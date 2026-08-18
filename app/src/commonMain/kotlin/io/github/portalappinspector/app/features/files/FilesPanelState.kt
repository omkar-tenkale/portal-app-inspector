package io.github.portalappinspector.app.features.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.portalappinspector.app.data.PortalConnection
import io.github.portalappinspector.app.features.files.PortalFilePinStore

internal class FilesPanelState {
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loadedConnection by mutableStateOf<PortalConnection?>(null)
    var currentPath by mutableStateOf<String?>(null)
    var fileFilter by mutableStateOf("")
    var fileListVersion by mutableStateOf(0)
    var viewedFile by mutableStateOf<PortalViewedFile?>(null)
    var pinnedFiles by mutableStateOf(PortalFilePinStore.load())
    val fileItems = mutableStateListOf<PortalFileItem>()
    val backStack = mutableStateListOf<String?>()
}
