package io.github.portalappinspector.app.features.files

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.type
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.github.portalappinspector.app.data.PortalConnection
import io.github.portalappinspector.app.data.PortalFileItem
import io.github.portalappinspector.app.data.PortalFilePinStore
import io.github.portalappinspector.app.data.PortalSourceClient
import io.github.portalappinspector.app.data.PortalViewedFile
import io.github.portalappinspector.app.data.parseFileItem
import io.github.portalappinspector.app.ui.EmptyRow
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.RowDivider
import io.github.portalappinspector.app.ui.StatusCard
import io.github.portalappinspector.app.ui.toast.LocalToastHost
import io.github.portalappinspector.app.ui.toast.ToastKind
import io.github.portalappinspector.app.util.copyTextToClipboard
import io.github.portalappinspector.app.util.decodeBase64ToText
import io.github.portalappinspector.app.util.downloadBase64File
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Composable
internal fun FilesPanel(
    state: FilesPanelState,
    connection: PortalConnection,
    client: PortalSourceClient,
    enabled: Boolean,
    sharedPrefsEnabled: Boolean,
    onOpenSharedPrefsTab: (PortalFileItem) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toastHost = LocalToastHost.current

    fun load(path: String?) {
        state.loading = true
        state.error = null
        state.viewedFile = null
        scope.launch {
            runCatching {
                val payload = if (path == null) {
                    client.request(
                        connection = connection,
                        pluginId = "portal:files",
                        payload = buildJsonObject { put("type", "listRoots") },
                    )
                } else {
                    client.request(
                        connection = connection,
                        pluginId = "portal:files",
                        payload = buildJsonObject {
                            put("type", "listChildren")
                            put("path", path)
                        },
                    )
                }
                state.currentPath = path
                state.fileItems.clear()
                state.fileItems += payload["items"]?.jsonArray?.map(::parseFileItem).orEmpty()
                state.fileListVersion += 1
            }.onFailure { throwable ->
                state.error = throwable.message ?: throwable::class.simpleName
            }
            state.loading = false
        }
    }

    fun openDirectory(path: String) {
        if (state.loading) return
        state.backStack += state.currentPath
        load(path)
    }

    fun goToParentDirectory() {
        if (state.backStack.isEmpty() || state.loading) return
        val previous = state.backStack.removeLastOrNull()
        load(previous)
    }

    fun openBreadcrumb(path: String?) {
        if (state.loading || path == state.currentPath) return
        if (state.viewedFile == null) {
            state.backStack += state.currentPath
        }
        load(path)
    }

    fun copyPath(item: PortalFileItem) {
        copyTextToClipboard(item.absolutePath ?: item.path)
        toastHost.show("Copied path", ToastKind.Info, durationMillis = 1_600L)
    }

    fun togglePinned(item: PortalFileItem) {
        state.pinnedFiles = PortalFilePinStore.toggle(item)
        toastHost.show(
            message = if (state.pinnedFiles.any { it.path == item.path }) "Pinned path" else "Unpinned path",
            kind = ToastKind.Info,
            durationMillis = 1_600L,
        )
    }

    fun downloadFile(item: PortalFileItem) {
        if (state.loading) return
        if (item.directory) {
            toastHost.show("Folder download unavailable", ToastKind.Warning, durationMillis = 1_800L)
            return
        }
        scope.launch {
            runCatching {
                client.request(
                    connection = connection,
                    pluginId = "portal:files",
                    payload = buildJsonObject {
                        put("type", "readFile")
                        put("path", item.path)
                    },
                )
            }.onSuccess { payload ->
                val base64 = payload["base64"]?.jsonPrimitive?.contentOrNull
                if (base64.isNullOrBlank()) {
                    toastHost.show("Download failed", ToastKind.Error, durationMillis = 2_400L)
                } else {
                    downloadBase64File(base64, payload["name"]?.jsonPrimitive?.contentOrNull ?: item.name)
                    toastHost.show("Download started", ToastKind.Info, durationMillis = 1_600L)
                }
            }.onFailure { throwable ->
                toastHost.show(throwable.message ?: "Download failed", ToastKind.Error, durationMillis = 2_400L)
            }
        }
    }

    fun viewFile(item: PortalFileItem) {
        if (state.loading || !item.isViewableFile()) return
        if (item.isSharedPrefsFile()) {
            if (sharedPrefsEnabled) {
                onOpenSharedPrefsTab(item)
            } else {
                toastHost.show("Shared preferences plugin unavailable", ToastKind.Warning, durationMillis = 2_200L)
            }
            return
        }
        state.loading = true
        state.error = null
        scope.launch {
            runCatching {
                client.request(
                    connection = connection,
                    pluginId = "portal:files",
                    payload = buildJsonObject {
                        put("type", "readFile")
                        put("path", item.path)
                    },
                )
            }.onSuccess { payload ->
                val base64 = payload["base64"]?.jsonPrimitive?.contentOrNull
                if (base64.isNullOrBlank()) {
                    toastHost.show("File view failed", ToastKind.Error, durationMillis = 2_400L)
                } else {
                    state.backStack += state.currentPath
                    state.currentPath = item.path
                    state.viewedFile = PortalViewedFile(
                        item = item,
                        text = decodeBase64ToText(base64),
                    )
                    state.fileListVersion += 1
                }
            }.onFailure { throwable ->
                toastHost.show(throwable.message ?: "File view failed", ToastKind.Error, durationMillis = 2_400L)
            }
            state.loading = false
        }
    }

    fun deletePath(item: PortalFileItem) {
        if (state.loading) return
        scope.launch {
            runCatching {
                client.request(
                    connection = connection,
                    pluginId = "portal:files",
                    payload = buildJsonObject {
                        put("type", "deletePath")
                        put("path", item.path)
                    },
                )
            }.onSuccess { payload ->
                val deleted = payload["deleted"]?.jsonPrimitive?.booleanOrNull == true
                if (deleted) {
                    state.pinnedFiles = PortalFilePinStore.remove(item.path)
                    toastHost.show("Deleted path", ToastKind.Info, durationMillis = 1_600L)
                    load(state.currentPath)
                } else {
                    toastHost.show("Delete failed", ToastKind.Error, durationMillis = 2_400L)
                }
            }.onFailure { throwable ->
                toastHost.show(throwable.message ?: "Delete failed", ToastKind.Error, durationMillis = 2_400L)
            }
        }
    }

    LaunchedEffect(enabled, connection) {
        if (enabled && connection.isValid && state.loadedConnection != connection) {
            state.loadedConnection = connection
            load(null)
        }
    }

    FilesPanelContent(
        state = state,
        enabled = enabled,
        onLoadPath = ::load,
        onOpenDirectory = ::openDirectory,
        onGoToParentDirectory = ::goToParentDirectory,
        onOpenBreadcrumb = ::openBreadcrumb,
        onCopyPath = ::copyPath,
        onTogglePinned = ::togglePinned,
        onDownloadFile = ::downloadFile,
        onViewFile = ::viewFile,
        onDeletePath = ::deletePath,
    )
}

@Composable
internal fun FilesPanelContent(
    state: FilesPanelState,
    enabled: Boolean,
    onLoadPath: (String?) -> Unit,
    onOpenDirectory: (String) -> Unit,
    onGoToParentDirectory: () -> Unit,
    onOpenBreadcrumb: (String?) -> Unit,
    onCopyPath: (PortalFileItem) -> Unit,
    onTogglePinned: (PortalFileItem) -> Unit,
    onDownloadFile: (PortalFileItem) -> Unit,
    onViewFile: (PortalFileItem) -> Unit,
    onDeletePath: (PortalFileItem) -> Unit,
) {
    val fileItems = state.fileItems
    val backStack = state.backStack
    val listState = rememberLazyListState()
    val fileListAlpha = remember { Animatable(1f) }
    val focusRequester = remember { FocusRequester() }

    val visibleFileItems = fileItems.filter { item ->
        state.fileFilter.isBlank() || item.name.contains(state.fileFilter.trim(), ignoreCase = true)
    }
    val visiblePinnedFiles = state.pinnedFiles
        .takeIf { state.currentPath == null && state.viewedFile == null }
        .orEmpty()
        .filter { item -> state.fileFilter.isBlank() || item.name.contains(state.fileFilter.trim(), ignoreCase = true) }
        .map { it.toFileItem() }

    LaunchedEffect(state.currentPath, state.fileFilter) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(state.fileListVersion) {
        if (state.fileListVersion > 0) {
            fileListAlpha.snapTo(0.58f)
            fileListAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.Escape &&
                    backStack.isNotEmpty() &&
                    !state.loading
                ) {
                    onGoToParentDirectory()
                    true
                } else {
                    false
                }
            }
            .focusRequester(focusRequester)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        FilesRootsHeader(
            path = state.currentPath,
            loading = state.loading,
            filter = state.fileFilter,
            onFilterChange = { state.fileFilter = it },
            onPathClick = onOpenBreadcrumb,
        )
        RowDivider()
        if (!enabled) {
            StatusCard("Files plugin unavailable", "Install the portal:files plugin in the source app.", PortalColors.warning)
            return@Column
        }
        state.error?.let {
            StatusCard("Files request failed", it, PortalColors.error)
        }
        state.viewedFile?.let { file ->
            FileTextViewer(
                file = file,
                showParentRow = backStack.isNotEmpty(),
                onParentClick = onGoToParentDirectory,
                enabled = !state.loading,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(fileListAlpha.value),
            )
        } ?: run {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(fileListAlpha.value),
            ) {
                if (backStack.isNotEmpty()) {
                    item(key = "file-row-parent-directory") {
                        ParentDirectoryRow(
                            onClick = onGoToParentDirectory,
                            enabled = !state.loading,
                        )
                    }
                }
                items(visibleFileItems, key = { it.path }) { item ->
                    FileRow(
                        item = item,
                        pinned = state.pinnedFiles.any { it.path == item.path },
                        onClick = {
                            if (item.directory) {
                                onOpenDirectory(item.path)
                            } else if (item.isViewableFile()) {
                                onViewFile(item)
                            }
                        },
                        onDownload = { onDownloadFile(item) },
                        onCopyPath = { onCopyPath(item) },
                        onDelete = { onDeletePath(item) },
                        onTogglePinned = { onTogglePinned(item) },
                    )
                }
                if (visiblePinnedFiles.isNotEmpty()) {
                    item(key = "file-row-starred-section") {
                        FilesSectionRow("Starred")
                    }
                }
                items(visiblePinnedFiles, key = { "pinned:${it.path}" }) { item ->
                    FileRow(
                        item = item,
                        pinned = true,
                        onClick = {
                            if (item.directory) {
                                onOpenDirectory(item.path)
                            } else if (item.isViewableFile()) {
                                onViewFile(item)
                            }
                        },
                        onDownload = { onDownloadFile(item) },
                        onCopyPath = { onCopyPath(item) },
                        onDelete = { onDeletePath(item) },
                        onTogglePinned = { onTogglePinned(item) },
                    )
                }
                if (fileItems.isEmpty() && visiblePinnedFiles.isEmpty() && !state.loading && state.error == null) {
                    item(key = "file-row-empty") {
                        EmptyRow("No files in this directory")
                    }
                } else if (visibleFileItems.isEmpty() && visiblePinnedFiles.isEmpty() && state.fileFilter.isNotBlank()) {
                    item(key = "file-row-empty-filter") {
                        EmptyRow("No matches")
                    }
                }
            }
        }
    }
}
