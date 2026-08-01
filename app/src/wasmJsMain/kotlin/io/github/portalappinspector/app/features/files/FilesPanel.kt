package io.github.portalappinspector.app.features.files

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sebastianneubauer.jsontree.JsonTree
import com.sebastianneubauer.jsontree.TreeState
import io.github.portalappinspector.app.data.*
import io.github.portalappinspector.app.ui.*
import io.github.portalappinspector.app.ui.icons.*
import io.github.portalappinspector.app.ui.toast.*
import io.github.portalappinspector.app.util.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun FilesPanel(
    state: FilesPanelState,
    connection: PortalConnection,
    client: PortalSourceClient,
    enabled: Boolean,
    sharedPrefsEnabled: Boolean,
    onOpenSharedPrefsTab: (PortalFileItem) -> Unit,
) {
    val fileItems = state.fileItems
    val backStack = state.backStack
    val listState = rememberLazyListState()
    val fileListAlpha = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val toastHost = LocalToastHost.current
    val visibleFileItems = fileItems.filter { item ->
        state.fileFilter.isBlank() || item.name.contains(state.fileFilter.trim(), ignoreCase = true)
    }
    val visiblePinnedFiles = state.pinnedFiles
        .takeIf { state.currentPath == null && state.viewedFile == null }
        .orEmpty()
        .filter { item -> state.fileFilter.isBlank() || item.name.contains(state.fileFilter.trim(), ignoreCase = true) }
        .map { it.toFileItem() }

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
                fileItems.clear()
                fileItems += payload["items"]?.jsonArray?.map(::parseFileItem).orEmpty()
                state.fileListVersion += 1
            }.onFailure { throwable ->
                state.error = throwable.message ?: throwable::class.simpleName
            }
            state.loading = false
        }
    }

    fun openDirectory(path: String) {
        if (state.loading) return
        backStack += state.currentPath
        load(path)
    }

    fun goToParentDirectory() {
        if (backStack.isEmpty() || state.loading) return
        val previous = backStack.removeLastOrNull()
        load(previous)
    }

    fun openBreadcrumb(path: String?) {
        if (state.loading || path == state.currentPath) return
        if (state.viewedFile == null) {
            backStack += state.currentPath
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
                    backStack += state.currentPath
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
            focusRequester.requestFocus()
        }
    }

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
                    goToParentDirectory()
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
            onPathClick = ::openBreadcrumb,
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
                onParentClick = ::goToParentDirectory,
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
                            onClick = ::goToParentDirectory,
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
                                openDirectory(item.path)
                            } else if (item.isViewableFile()) {
                                viewFile(item)
                            }
                        },
                        onDownload = { downloadFile(item) },
                        onCopyPath = { copyPath(item) },
                        onDelete = { deletePath(item) },
                        onTogglePinned = { togglePinned(item) },
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
                                openDirectory(item.path)
                            } else if (item.isViewableFile()) {
                                viewFile(item)
                            }
                        },
                        onDownload = { downloadFile(item) },
                        onCopyPath = { copyPath(item) },
                        onDelete = { deletePath(item) },
                        onTogglePinned = { togglePinned(item) },
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

@Composable
internal fun FilesRootsHeader(
    path: String?,
    loading: Boolean,
    filter: String,
    onFilterChange: (String) -> Unit,
    onPathClick: (String?) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            filePathSegments(path).forEachIndexed { index, segment ->
                if (index > 0) {
                    Image(
                        painter = rememberVectorPainter(PortalTabIcons.ArrowRight),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        colorFilter = ColorFilter.tint(PortalColors.muted.copy(alpha = 0.7f)),
                    )
                }
                FileBreadcrumbSegment(
                    segment = segment,
                    selected = segment.path == path,
                    onClick = { onPathClick(segment.path) },
                )
            }
        }
        FilesSearchField(
            value = filter,
            onValueChange = onFilterChange,
            modifier = Modifier.width(180.dp),
        )
        Box(
            modifier = Modifier.size(width = 42.dp, height = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (loading) {
                PulsatingDots(color = PortalColors.accent, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
internal fun FileBreadcrumbSegment(
    segment: FilePathSegment,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background by animateColorAsState(
        if (hovered && !selected) PortalColors.button.copy(alpha = 0.58f) else Color.Transparent,
    )
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = segment.label,
            color = if (selected || segment.path == null) PortalColors.text else PortalColors.muted,
            fontSize = 13.sp,
            fontWeight = if (selected || segment.path == null) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
internal fun FilesSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = PortalColors.text,
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp,
        ),
        cursorBrush = SolidColor(PortalColors.accent),
        modifier = modifier.height(28.dp),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SearchIcon(
                    color = PortalColors.muted,
                    modifier = Modifier.size(14.dp),
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Search...",
                            color = PortalColors.muted.copy(alpha = 0.72f),
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

internal data class FilePathSegment(
    val label: String,
    val path: String?,
)

internal fun filePathSegments(path: String?): List<FilePathSegment> {
    if (path.isNullOrBlank()) return listOf(FilePathSegment(label = "Roots", path = null))
    val trimmed = path.trim()
    val root = trimmed.substringBefore("/", missingDelimiterValue = trimmed)
    val rootSegment = if (trimmed.contains("/")) "$root/" else root
    val children = trimmed
        .substringAfter("/", missingDelimiterValue = "")
        .split("/")
        .filter { it.isNotBlank() }
    return buildList {
        add(FilePathSegment(label = rootSegment, path = rootSegment))
        children.fold(rootSegment.trimEnd('/')) { currentPath, child ->
            val nextPath = "$currentPath/$child"
            add(FilePathSegment(label = child, path = nextPath))
            nextPath
        }
    }
}

internal fun String.isRootFilePath(): Boolean =
    endsWith(":/")

@Composable
internal fun ParentDirectoryRow(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background by animateColorAsState(
        targetValue = if (enabled && hovered) PortalColors.card.copy(alpha = 0.58f) else Color.Transparent,
    )

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FileRowHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(background)
                .hoverable(interactionSource = interactionSource, enabled = enabled)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PortalFileIcon(
                kind = PortalFileIconKind.ParentDirectory,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "..",
                color = PortalColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        RowDivider()
    }
}

@Composable
internal fun FileRow(
    item: PortalFileItem,
    pinned: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onCopyPath: () -> Unit,
    onDelete: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val rowClickable = item.directory || item.isViewableFile()
    val background by animateColorAsState(
        targetValue = if (hovered) PortalColors.card.copy(alpha = 0.58f) else Color.Transparent,
    )
    var deleteArmed by remember(item.path) { mutableStateOf(false) }
    val actionsAlpha by animateFloatAsState(
        targetValue = if (hovered || deleteArmed) 1f else 0f,
        animationSpec = tween(durationMillis = 130),
    )
    val sizeAlpha by animateFloatAsState(
        targetValue = if (hovered || deleteArmed) 0f else 1f,
        animationSpec = tween(durationMillis = 130),
    )

    LaunchedEffect(deleteArmed) {
        if (deleteArmed) {
            delay(2_000L)
            deleteArmed = false
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FileRowHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(background)
                .hoverable(interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = rowClickable,
                    onClick = onClick,
                )
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PortalFileIcon(
                kind = item.iconKind(),
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = item.name,
                color = PortalColors.text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.width(FileRowMetaWidth),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = formatSize(item.sizeBytes),
                    color = PortalColors.muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.alpha(sizeAlpha),
                )
                FileRowActions(
                    item = item,
                    pinned = pinned,
                    deleteArmed = deleteArmed,
                    alpha = actionsAlpha,
                    interactionSource = interactionSource,
                    onDownload = onDownload,
                    onCopyPath = onCopyPath,
                    onDelete = {
                        if (deleteArmed) {
                            deleteArmed = false
                            onDelete()
                        } else {
                            deleteArmed = true
                        }
                    },
                    onTogglePinned = onTogglePinned,
                )
            }
        }
    }
}

@Composable
internal fun FileRowActions(
    item: PortalFileItem,
    pinned: Boolean,
    deleteArmed: Boolean,
    alpha: Float,
    interactionSource: MutableInteractionSource,
    onDownload: () -> Unit,
    onCopyPath: () -> Unit,
    onDelete: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    Row(
        modifier = Modifier
            .alpha(alpha)
            .hoverable(interactionSource = interactionSource),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val actionsEnabled = alpha > 0.01f
        FileRowActionButton(
            icon = PortalTabIcons.Download,
            interactionSource = interactionSource,
            enabled = actionsEnabled,
            onClick = onDownload,
        )
        FileRowActionButton(
            icon = PortalTabIcons.CopyPath,
            interactionSource = interactionSource,
            enabled = actionsEnabled,
            onClick = onCopyPath,
        )
        FileRowActionButton(
            icon = if (pinned) PortalTabIcons.StarFilled else PortalTabIcons.Star,
            interactionSource = interactionSource,
            enabled = actionsEnabled,
            onClick = onTogglePinned,
        )
        if (!item.path.isRootFilePath()) {
            FileRowActionButton(
                icon = PortalTabIcons.Delete,
                tint = if (deleteArmed) PortalColors.error else PortalColors.text,
                interactionSource = interactionSource,
                enabled = actionsEnabled,
                onClick = onDelete,
            )
        }
    }
}

@Composable
internal fun FileRowActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = PortalColors.text,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

@Composable
internal fun FileTextViewer(
    file: PortalViewedFile,
    showParentRow: Boolean,
    onParentClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val lines = remember(file.text) { file.text.lines().ifEmpty { listOf("") } }
    val jsonBody = remember(file.text) { formatJsonOrNull(file.text) }
    Column(modifier = modifier) {
        if (showParentRow) {
            ParentDirectoryRow(
                onClick = onParentClick,
                enabled = enabled,
            )
        }
        if (jsonBody != null) {
            JsonTree(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(PortalColors.codeInput, RoundedCornerShape(6.dp))
                    .border(1.dp, PortalColors.codeInputBorder, RoundedCornerShape(6.dp)),
                json = jsonBody,
                onLoading = { PulsatingDots(color = PortalColors.accent, modifier = Modifier.size(width = 42.dp, height = 16.dp)) },
                initialState = TreeState.EXPANDED,
                contentPadding = PaddingValues(12.dp),
                colors = PortalJsonTreeColors,
                textStyle = TextStyle(
                    color = PortalColors.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                ),
                showIndices = true,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(PortalColors.codeInput, RoundedCornerShape(6.dp))
                    .border(1.dp, PortalColors.codeInputBorder, RoundedCornerShape(6.dp)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                itemsIndexed(lines) { index, line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            color = PortalColors.muted.copy(alpha = 0.55f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(42.dp),
                        )
                        Text(
                            text = line.ifEmpty { " " },
                            color = PortalColors.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FilesSectionRow(title: String) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                color = PortalColors.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        RowDivider()
    }
}

internal val FileRowHeight = 40.dp

internal val FileRowMetaWidth = 160.dp

internal const val FileViewMaxBytes = 512L * 1024L

internal enum class PortalFileIconKind {
    ParentDirectory,
    Folder,
    DefaultFile,
    ImageFile,
    TextFile,
}

internal fun PortalFileItem.iconKind(): PortalFileIconKind =
    when {
        directory -> PortalFileIconKind.Folder
        name.isImageFileName() -> PortalFileIconKind.ImageFile
        name.isTextReadableFileName() -> PortalFileIconKind.TextFile
        else -> PortalFileIconKind.DefaultFile
    }

internal fun PortalFileItem.isViewableFile(): Boolean =
    !directory && (sizeBytes ?: Long.MAX_VALUE) <= FileViewMaxBytes

internal fun PortalFileItem.isSharedPrefsFile(): Boolean =
    !directory && path.startsWith("shared-prefs:/") && name.fileExtension() == "xml"

internal fun String.fileExtension(): String =
    substringAfterLast('.', missingDelimiterValue = "").lowercase()

internal fun String.isImageFileName(): Boolean =
    fileExtension() in setOf(
        "apng",
        "avif",
        "bmp",
        "gif",
        "heic",
        "heif",
        "ico",
        "jpeg",
        "jpg",
        "png",
        "svg",
        "tif",
        "tiff",
        "webp",
    )

internal fun String.isTextReadableFileName(): Boolean {
    val lowerName = lowercase()
    if (lowerName in setOf(
            ".env",
            ".gitignore",
            ".gitattributes",
            ".editorconfig",
            "license",
            "readme",
            "todo",
        )
    ) {
        return true
    }

    return fileExtension() in setOf(
        "c",
        "cc",
        "conf",
        "cpp",
        "cs",
        "css",
        "csv",
        "dart",
        "gradle",
        "graphql",
        "h",
        "hpp",
        "htm",
        "html",
        "java",
        "js",
        "json",
        "jsx",
        "kt",
        "kts",
        "log",
        "md",
        "mjs",
        "properties",
        "py",
        "rb",
        "rs",
        "sh",
        "sql",
        "swift",
        "toml",
        "ts",
        "tsx",
        "txt",
        "xml",
        "yaml",
        "yml",
    )
}

@Composable
internal fun PortalFileIcon(
    kind: PortalFileIconKind,
    modifier: Modifier = Modifier,
) {
    val color = when (kind) {
        PortalFileIconKind.ParentDirectory -> PortalColors.accent
        PortalFileIconKind.Folder -> PortalColors.accent
        PortalFileIconKind.ImageFile -> PortalColors.success
        PortalFileIconKind.TextFile -> PortalColors.text
        PortalFileIconKind.DefaultFile -> PortalColors.muted
    }

    Canvas(modifier = modifier) {
        when (kind) {
            PortalFileIconKind.ParentDirectory -> drawParentDirectoryIcon(color)
            PortalFileIconKind.Folder -> drawFolderIcon(color)
            PortalFileIconKind.DefaultFile -> drawDefaultFileIcon(color)
            PortalFileIconKind.ImageFile -> drawImageFileIcon(color)
            PortalFileIconKind.TextFile -> drawTextFileIcon(color)
        }
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParentDirectoryIcon(color: Color) {
    val stroke = Stroke(width = size.minDimension / 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun x(value: Float) = size.width * value / 48f
    fun y(value: Float) = size.height * value / 48f

    val arrow = Path().apply {
        moveTo(x(12f), y(16f))
        lineTo(x(20f), y(8f))
        lineTo(x(28f), y(16f))
    }
    val stem = Path().apply {
        moveTo(x(36f), y(40f))
        lineTo(x(23f), y(40f))
        cubicTo(x(21.3431f), y(40f), x(20f), y(38.6569f), x(20f), y(37f))
        lineTo(x(20f), y(8f))
    }

    drawPath(arrow, color = color, style = stroke)
    drawPath(stem, color = color, style = stroke)
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFolderIcon(color: Color) {
    val stroke = Stroke(width = size.minDimension / 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun x(value: Float) = size.width * value / 48f
    fun y(value: Float) = size.height * value / 48f

    val back = Path().apply {
        moveTo(x(4f), y(9f))
        lineTo(x(4f), y(41f))
        lineTo(x(9f), y(21f))
        lineTo(x(39.5f), y(21f))
        lineTo(x(39.5f), y(15f))
        cubicTo(x(39.5f), y(13.8954f), x(38.6046f), y(13f), x(37.5f), y(13f))
        lineTo(x(24f), y(13f))
        lineTo(x(19f), y(7f))
        lineTo(x(6f), y(7f))
        cubicTo(x(4.89543f), y(7f), x(4f), y(7.89543f), x(4f), y(9f))
        close()
    }
    val front = Path().apply {
        moveTo(x(40f), y(41f))
        lineTo(x(44f), y(21f))
        lineTo(x(8.8125f), y(21f))
        lineTo(x(4f), y(41f))
        close()
    }

    drawPath(back, color = color, style = stroke)
    drawPath(front, color = color, style = stroke)
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDefaultFileIcon(color: Color) {
    drawFileOutline(color, viewBox = 24f, strokeWidth = 2f)
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTextFileIcon(color: Color) {
    drawFileOutline(color, viewBox = 24f, strokeWidth = 2f)
    val stroke = Stroke(width = size.minDimension / 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun x(value: Float) = size.width * value / 24f
    fun y(value: Float) = size.height * value / 24f

    drawLine(color, Offset(x(8.5f), y(14f)), Offset(x(15.5f), y(14f)), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(x(8.5f), y(18f)), Offset(x(15.5f), y(18f)), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawImageFileIcon(color: Color) {
    drawFileOutline(color, viewBox = 48f, strokeWidth = 4f)
    val stroke = Stroke(width = size.minDimension / 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun x(value: Float) = size.width * value / 48f
    fun y(value: Float) = size.height * value / 48f

    drawCircle(
        color = color,
        radius = size.minDimension * 4f / 48f,
        center = Offset(x(18f), y(17f)),
        style = stroke,
    )
    val image = Path().apply {
        moveTo(x(15f), y(28f))
        lineTo(x(15f), y(37f))
        lineTo(x(33f), y(37f))
        lineTo(x(33f), y(21f))
        lineTo(x(23.4894f), y(31.5f))
        lineTo(x(15f), y(28f))
        close()
    }
    drawPath(image, color = color, style = stroke)
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFileOutline(
    color: Color,
    viewBox: Float,
    strokeWidth: Float,
) {
    val stroke = Stroke(width = size.minDimension * strokeWidth / viewBox, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun x(value: Float) = size.width * value / viewBox
    fun y(value: Float) = size.height * value / viewBox

    val outline = if (viewBox == 24f) {
        Path().apply {
            moveTo(x(5f), y(22f))
            lineTo(x(19f), y(22f))
            cubicTo(x(19.5523f), y(22f), x(20f), y(21.5523f), x(20f), y(21f))
            lineTo(x(20f), y(7f))
            lineTo(x(15f), y(7f))
            lineTo(x(15f), y(2f))
            lineTo(x(5f), y(2f))
            cubicTo(x(4.44771f), y(2f), x(4f), y(2.44771f), x(4f), y(3f))
            lineTo(x(4f), y(21f))
            cubicTo(x(4f), y(21.5523f), x(4.44771f), y(22f), x(5f), y(22f))
            close()
        }
    } else {
        Path().apply {
            moveTo(x(10f), y(44f))
            lineTo(x(38f), y(44f))
            cubicTo(x(39.1046f), y(44f), x(40f), y(43.1046f), x(40f), y(42f))
            lineTo(x(40f), y(14f))
            lineTo(x(30f), y(14f))
            lineTo(x(30f), y(4f))
            lineTo(x(10f), y(4f))
            cubicTo(x(8.89543f), y(4f), x(8f), y(4.89543f), x(8f), y(6f))
            lineTo(x(8f), y(42f))
            cubicTo(x(8f), y(43.1046f), x(8.89543f), y(44f), x(10f), y(44f))
            close()
        }
    }
    val fold = if (viewBox == 24f) {
        Path().apply {
            moveTo(x(15f), y(2f))
            lineTo(x(20f), y(7f))
        }
    } else {
        Path().apply {
            moveTo(x(30f), y(4f))
            lineTo(x(40f), y(14f))
        }
    }

    drawPath(outline, color = color, style = stroke)
    drawPath(fold, color = color, style = stroke)
}

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
