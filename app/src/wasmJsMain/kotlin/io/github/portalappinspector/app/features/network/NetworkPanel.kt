package io.github.portalappinspector.app.features.network

import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import io.ktor.client.call.body
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonArray
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.serialization.json.encodeToJsonElement
import io.github.portalappinspector.app.data.*
import io.github.portalappinspector.app.ui.*
import io.github.portalappinspector.app.ui.icons.*
import io.github.portalappinspector.app.util.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun NetworkPanel(
    connection: PortalConnection,
    client: PortalSourceClient,
    enabled: Boolean,
    appId: String?,
    onOpenResponseTab: (PortalNetworkCall) -> Unit,
) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var mockSyncError by remember { mutableStateOf<String?>(null) }
    var lastTimestamp by remember(connection) { mutableStateOf(0L) }
    var bodySheetCall by remember { mutableStateOf<PortalNetworkCall?>(null) }
    var mockEditorInitial by remember { mutableStateOf<PortalNetworkMock?>(null) }
    var mocksSheetOpen by remember { mutableStateOf(false) }
    var filterMode by remember(connection) { mutableStateOf(NetworkFilterMode.Include) }
    var filterType by remember(connection) { mutableStateOf(NetworkFilterTypes.first()) }
    var filterValue by remember(connection) { mutableStateOf("") }
    var filters by remember(connection) { mutableStateOf(emptyList<NetworkFilterRule>()) }
    val calls = remember(connection) { mutableStateListOf<PortalNetworkCall>() }
    val networkListState = rememberLazyListState()
    val timezoneOffsetMinutes = remember { jsTimezoneOffsetMinutes().toLong() }
    val use12HourClock = remember { jsUses12HourClock() }
    var networkDisplayNowEpochMillis by remember { mutableStateOf(nowEpochMillis()) }
    val mockScope = rememberCoroutineScope()
    val mockPackageKey = appId ?: connection.baseUrl
    var mocks by remember(mockPackageKey) { mutableStateOf(PortalNetworkMockStore.load(mockPackageKey)) }
    val filteredCalls = calls.asReversed().filter { call -> filters.matchesNetworkCall(call) }
    val networkGapThresholdMillis = if (filters.any { it.mode != NetworkFilterMode.Highlight }) {
        FilteredNetworkGapThresholdMillis
    } else {
        NetworkGapThresholdMillis
    }
    val networkRows = remember(filteredCalls, networkGapThresholdMillis) {
        filteredCalls.toNetworkRows(networkGapThresholdMillis)
    }

    suspend fun syncMocks(nextMocks: List<PortalNetworkMock> = mocks) {
        if (!enabled || !connection.isValid) return
        runCatching {
            client.request(
                connection = connection,
                pluginId = NetworkPluginId,
                payload = buildJsonObject {
                    put("type", "setupMocks")
                    put("mocks", StorageJson.encodeToJsonElement(nextMocks.filter { it.enabled }))
                },
            )
        }.onSuccess {
            mockSyncError = null
        }.onFailure { throwable ->
            mockSyncError = throwable.message ?: throwable::class.simpleName
        }
    }

    fun updateMocks(nextMocks: List<PortalNetworkMock>) {
        val saved = PortalNetworkMockStore.save(mockPackageKey, nextMocks)
        mocks = saved
        mockScope.launch {
            syncMocks(saved)
        }
    }

    suspend fun loadNewCalls() {
        loading = true
        runCatching {
            client.request(
                connection = connection,
                pluginId = NetworkPluginId,
                payload = buildJsonObject {
                    put("type", "listAfter")
                    put("afterTimestampEpochMillis", lastTimestamp)
                },
            )
        }.onSuccess { payload ->
            error = null
            val nextCalls = payload["items"]?.jsonArray?.map(::parseNetworkCall).orEmpty()
            val existingIds = calls.mapTo(mutableSetOf()) { it.id }
            calls += nextCalls.filter { existingIds.add(it.id) }
            lastTimestamp = calls.maxOfOrNull { it.timestampEpochMillis } ?: lastTimestamp
        }.onFailure { throwable ->
            error = throwable.message ?: throwable::class.simpleName
        }
        loading = false
    }

    LaunchedEffect(enabled, connection, mockPackageKey) {
        calls.clear()
        lastTimestamp = 0L
        if (!enabled || !connection.isValid) return@LaunchedEffect

        syncMocks()
        while (true) {
            loadNewCalls()
            delay(2_000L)
        }
    }

    LaunchedEffect(networkRows.firstOrNull()?.key) {
        if (networkRows.isNotEmpty()) {
            networkListState.scrollToItem(0)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            networkDisplayNowEpochMillis = nowEpochMillis()
            delay(1_000L)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, top = 6.dp, end = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NetworkFilterBar(
                    filters = filters,
                    filterMode = filterMode,
                    filterType = filterType,
                    filterValue = filterValue,
                    onFilterModeChange = { filterMode = it },
                    onFilterTypeChange = {
                        filterType = it
                        filterValue = ""
                    },
                    onFilterValueChange = { filterValue = it },
                    onAddFilter = { selectedMode, selectedType ->
                        val nextFilter = NetworkFilterRule(
                            mode = selectedMode,
                            matcher = PortalNetworkMockMatcher(
                                type = selectedType,
                                value = filterValue.trim(),
                            ),
                        )
                        if (nextFilter.matcher.value.isNotBlank()) {
                            filters = filters + nextFilter
                            filterValue = ""
                        }
                    },
                    onDeleteFilter = { deleteIndex ->
                        filters = filters.filterIndexed { index, _ -> index != deleteIndex }
                    },
                    modifier = Modifier.weight(1f),
                )
                NetworkPanelToolbarDivider()
                NetworkDetailActionButton(
                    icon = PortalTabIcons.Mock,
                    text = "Mocks ${mocks.count { it.enabled }}",
                    onClick = { mocksSheetOpen = true },
                    enabled = enabled,
                    modifier = Modifier.height(28.dp),
                )
                NetworkDetailActionButton(
                    icon = PortalTabIcons.Delete,
                    text = "Clear",
                    onClick = {
                        lastTimestamp = calls.maxOfOrNull { it.timestampEpochMillis } ?: lastTimestamp
                        calls.clear()
                    },
                    enabled = calls.isNotEmpty(),
                    modifier = Modifier.height(28.dp),
                )
            }
            if (!enabled) {
                StatusCard("Network plugin unavailable", "Install the portal-network plugin in the source app.", PortalColors.warning)
                return@Column
            }
            error?.let {
                StatusCard("Network request failed", it, PortalColors.error)
            }
            mockSyncError?.let {
                StatusCard("Mock sync failed", it, PortalColors.warning)
            }
            if (calls.isEmpty() && !loading) {
                StatusCard("No traffic captured", "Trigger a Retrofit request in the source app.", PortalColors.muted)
            }
            if (calls.isNotEmpty() && filteredCalls.isEmpty()) {
                StatusCard("No matching traffic", "Adjust the network request filter.", PortalColors.muted)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = networkListState,
            ) {
                filteredCalls.firstOrNull()?.let { latestCall ->
                    item(key = "network-live-gap") {
                        LogGapRow(networkDisplayNowEpochMillis - latestCall.timestampEpochMillis)
                    }
                }
                items(networkRows, key = { it.key }) { row ->
                    when (row) {
                        is PortalNetworkRow.Call -> NetworkCallRow(
                            call = row.call,
                            nowEpochMillis = networkDisplayNowEpochMillis,
                            timezoneOffsetMinutes = timezoneOffsetMinutes,
                            use12HourClock = use12HourClock,
                            highlight = filters.firstHighlightFor(row.call),
                            onViewBody = { bodySheetCall = row.call },
                        )
                        is PortalNetworkRow.Gap -> LogGapRow(row.durationMillis)
                    }
                }
            }
        }
        bodySheetCall?.let { call ->
            ResponseBodySheet(
                call = call,
                onDismiss = { bodySheetCall = null },
                onOpenTab = {
                    bodySheetCall = null
                    onOpenResponseTab(call)
                },
                onMock = {
                    bodySheetCall = null
                    mockEditorInitial = createMockFromCall(call)
                },
            )
        }
        if (mocksSheetOpen) {
            NetworkMocksSheet(
                mocks = mocks,
                onDismiss = { mocksSheetOpen = false },
                onAdd = {
                    mocksSheetOpen = false
                    mockEditorInitial = createBlankMock()
                },
                onEdit = { mock ->
                    mocksSheetOpen = false
                    mockEditorInitial = mock
                },
                onToggle = { mock, enabled ->
                    updateMocks(
                        mocks.map {
                            if (it.id == mock.id) {
                                it.copy(enabled = enabled, updatedAtEpochMillis = nowEpochMillis())
                            } else {
                                it
                            }
                        },
                    )
                },
                onDelete = { mock ->
                    updateMocks(mocks.filterNot { it.id == mock.id })
                },
            )
        }
        mockEditorInitial?.let { initialMock ->
            NetworkMockEditorSheet(
                initialMock = initialMock,
                onDismiss = { mockEditorInitial = null },
                onSave = { savedMock ->
                    updateMocks(mocks.filterNot { it.id == savedMock.id } + savedMock)
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NetworkFilterBar(
    filters: List<NetworkFilterRule>,
    filterMode: NetworkFilterMode,
    filterType: String,
    filterValue: String,
    onFilterModeChange: (NetworkFilterMode) -> Unit,
    onFilterTypeChange: (String) -> Unit,
    onFilterValueChange: (String) -> Unit,
    onAddFilter: (NetworkFilterMode, String) -> Unit,
    onDeleteFilter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        filters.withIndex().forEach { indexedFilter ->
            NetworkFilterChip(rule = indexedFilter.value, onDelete = { onDeleteFilter(indexedFilter.index) })
        }
        NetworkFilterAddRow(
            mode = filterMode,
            type = filterType,
            value = filterValue,
            onModeChange = onFilterModeChange,
            onTypeChange = onFilterTypeChange,
            onValueChange = onFilterValueChange,
            onAdd = onAddFilter,
        )
    }
}

@Composable
internal fun NetworkFilterAddRow(
    mode: NetworkFilterMode,
    type: String,
    value: String,
    onModeChange: (NetworkFilterMode) -> Unit,
    onTypeChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onAdd: (NetworkFilterMode, String) -> Unit,
) {
    val selectedType = type.takeIf { it in NetworkFilterTypes } ?: NetworkFilterTypes.first()
    LaunchedEffect(type) {
        if (type !in NetworkFilterTypes) {
            onTypeChange(NetworkFilterTypes.first())
        }
    }
    var activeMenu by remember { mutableStateOf(NetworkFilterMenu.Mode) }
    var menuVisible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 460.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(30.dp)
                .widthIn(max = 390.dp)
                .background(PortalColors.button, RoundedCornerShape(14.dp))
                .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
                .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
        ) {
            NetworkFilterModeSelector(
                selected = mode,
                expanded = menuVisible && activeMenu == NetworkFilterMenu.Mode,
                onClick = {
                    if (activeMenu == NetworkFilterMenu.Mode) {
                        menuVisible = !menuVisible
                    } else {
                        activeMenu = NetworkFilterMenu.Mode
                        menuVisible = true
                    }
                },
            )
            InlineConditionTextField(
                value = value,
                onValueChange = onValueChange,
                onSubmit = { if (value.isNotBlank()) onAdd(mode, selectedType) },
                modifier = Modifier.width(110.dp),
            )
            Text("in", color = PortalColors.muted, fontSize = 12.sp)
            ConditionTypeSelector(
                selected = selectedType,
                expanded = menuVisible && activeMenu == NetworkFilterMenu.Type,
                onClick = {
                    if (activeMenu == NetworkFilterMenu.Type) {
                        menuVisible = !menuVisible
                    } else {
                        activeMenu = NetworkFilterMenu.Type
                        menuVisible = true
                    }
                },
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = value.isNotBlank()) { onAdd(mode, selectedType) },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = rememberVectorPainter(PortalTabIcons.Plus),
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    colorFilter = ColorFilter.tint(if (value.isBlank()) PortalColors.muted else PortalColors.text),
                )
            }
        }
        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn(animationSpec = tween(120)) + expandVertically(animationSpec = tween(140)),
            exit = fadeOut(animationSpec = tween(90)) + shrinkVertically(animationSpec = tween(120)),
        ) {
            when (activeMenu) {
                NetworkFilterMenu.Mode -> NetworkFilterModeMenu(
                    selected = mode,
                    onSelect = {
                        menuVisible = false
                        onModeChange(it)
                    },
                    modifier = Modifier.widthIn(max = 390.dp),
                )
                NetworkFilterMenu.Type -> ConditionTypeMenu(
                    options = NetworkFilterTypes,
                    selected = selectedType,
                    onSelect = {
                        menuVisible = false
                        onTypeChange(it)
                    },
                    modifier = Modifier.widthIn(max = 390.dp),
                )
            }
        }
    }
}

private enum class NetworkFilterMenu {
    Mode,
    Type,
}

@Composable
internal fun NetworkCallRow(
    call: PortalNetworkCall,
    nowEpochMillis: Long,
    timezoneOffsetMinutes: Long,
    use12HourClock: Boolean,
    highlight: NetworkHighlightMatch?,
    onViewBody: () -> Unit,
) {
    var showActualTime by remember(call.id) { mutableStateOf(false) }
    val relativeTime = formatRelativeLogTime(call.timestampEpochMillis, nowEpochMillis)
    val timeText = if (showActualTime || relativeTime == null) {
        formatAbsoluteLogTime(
            epochMillis = call.timestampEpochMillis,
            timezoneOffsetMinutes = timezoneOffsetMinutes,
            use12HourClock = use12HourClock,
        )
    } else {
        relativeTime
    }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val rowBackground = when {
        highlight != null && (hovered || pressed) -> PortalColors.warning.copy(alpha = 0.16f)
        highlight != null -> PortalColors.warning.copy(alpha = 0.10f)
        hovered || pressed -> PortalColors.card
        else -> Color.Transparent
    }

    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBackground)
                .hoverable(interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onViewBody,
                )
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = timeText,
                    color = PortalColors.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(92.dp)
                        .clickable(enabled = relativeTime != null && !showActualTime) {
                            showActualTime = true
                        },
                )
                Spacer(Modifier.width(10.dp))
                TimelineNetworkMarker(call.statusCode)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = call.method.take(8),
                    color = PortalColors.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.width(7.dp))
                if (call.isMocked) {
                    MockedBadge()
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = call.endpoint,
                    color = PortalColors.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            highlight?.let {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(92.dp))
                    Spacer(Modifier.width(10.dp))
                    Spacer(Modifier.width(34.dp))
                    Spacer(Modifier.width(12.dp))
                    HighlightBadge(match = it, modifier = Modifier.weight(1f))
                }
            }
        }
        RowDivider()
    }
}

@Composable
internal fun HighlightBadge(
    match: NetworkHighlightMatch,
    modifier: Modifier = Modifier,
) {
    val highlightText = remember(match) {
        buildAnnotatedString {
            append(match.snippet)
            val index = match.snippet.indexOf(match.query, ignoreCase = true)
            if (index >= 0) {
                addStyle(
                    style = SpanStyle(
                        color = PortalColors.warning,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    start = index,
                    end = index + match.query.length,
                )
            }
        }
    }
    BasicText(
        text = highlightText,
        style = TextStyle(
            color = PortalColors.text,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
internal fun TimelineNetworkMarker(statusCode: Int?) {
    val color = networkStatusColor(statusCode)
    val markerShape = RoundedCornerShape(50)
    val markerText = statusCode?.toString() ?: "ERR"
    Box(
        modifier = Modifier
            .width(34.dp)
            .height(34.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(34.dp)
                .background(PortalColors.border.copy(alpha = 0.55f)),
        )
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 22.dp)
                .background(color.copy(alpha = 0.12f), markerShape)
                .border(1.dp, color.copy(alpha = 0.42f), markerShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = markerText,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun StatusBadge(statusCode: Int?) {
    val color = networkStatusColor(statusCode)
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 18.dp)
            .background(color.copy(alpha = 0.88f), RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = statusCode?.toString() ?: "ERR",
            color = PortalColors.submitText,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

internal fun networkStatusColor(statusCode: Int?): Color =
    when {
        statusCode == null -> PortalColors.error
        statusCode in 200..299 -> PortalColors.success
        statusCode in 300..399 -> PortalColors.warning
        else -> PortalColors.error
    }

@Composable
internal fun MockedBadge() {
    Text(
        text = "Mocked",
        color = PortalColors.submitText,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .background(PortalColors.warning.copy(alpha = 0.9f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
internal fun ResponseBodySheet(
    call: PortalNetworkCall,
    onDismiss: () -> Unit,
    onOpenTab: () -> Unit,
    onMock: () -> Unit,
) {
    var selectedSection by remember(call.id) { mutableStateOf(NetworkDetailSection.Overview) }
    var menuExpanded by remember(call.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.46f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .background(PortalColors.card, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .border(1.dp, PortalColors.border, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            NetworkDetailTopBar(
                selectedSection = selectedSection,
                menuExpanded = menuExpanded,
                onToggleMenu = { menuExpanded = !menuExpanded },
                onMock = onMock,
                onOpenTab = onOpenTab,
                onDismiss = onDismiss,
            )
            RowDivider()
            AnimatedVisibility(
                visible = menuExpanded,
                enter = fadeIn(animationSpec = tween(120)) + expandVertically(animationSpec = tween(140)),
                exit = fadeOut(animationSpec = tween(90)) + shrinkVertically(animationSpec = tween(120)),
            ) {
                NetworkDetailSectionMenu(
                    selected = selectedSection,
                    onSelect = {
                        selectedSection = it
                        menuExpanded = false
                    },
                )
            }
            NetworkDetailContent(
                call = call,
                selectedSection = selectedSection,
                onSectionSelect = { selectedSection = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
            )
        }
    }
}

private enum class NetworkDetailSection(val label: String) {
    Overview("Overview"),
    Headers("Headers"),
    RequestBody("Request body"),
    ResponseBody("Response body"),
}

@Composable
private fun NetworkDetailTopBar(
    selectedSection: NetworkDetailSection,
    menuExpanded: Boolean,
    onToggleMenu: () -> Unit,
    onMock: () -> Unit,
    onOpenTab: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkDetailSectionSelector(
            selectedSection = selectedSection,
            expanded = menuExpanded,
            onClick = onToggleMenu,
        )
        Spacer(Modifier.weight(1f))
        NetworkDetailActionButton(
            icon = PortalTabIcons.Mock,
            text = "Mock",
            onClick = onMock,
            modifier = Modifier.height(28.dp),
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 7.dp)
                .width(1.dp)
                .height(18.dp)
                .background(PortalColors.border.copy(alpha = 0.85f)),
        )
        NetworkDetailIconButton(
            icon = PortalTabIcons.OpenInNew,
            onClick = onOpenTab,
        )
        NetworkDetailIconButton(
            icon = PortalTabIcons.Close,
            onClick = onDismiss,
        )
    }
}

@Composable
private fun NetworkDetailSectionSelector(
    selectedSection: NetworkDetailSection,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (hovered) PortalColors.button.copy(alpha = 0.7f) else Color.Transparent)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = selectedSection.label,
            color = PortalColors.text,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Image(
            painter = rememberVectorPainter(if (expanded) PortalTabIcons.ArrowUp else PortalTabIcons.ArrowDown),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            colorFilter = ColorFilter.tint(PortalColors.muted),
        )
    }
}

@Composable
private fun NetworkDetailSectionMenu(
    selected: NetworkDetailSection,
    onSelect: (NetworkDetailSection) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .padding(start = 10.dp, bottom = 8.dp)
            .widthIn(max = 640.dp)
            .background(PortalColors.card, RoundedCornerShape(14.dp))
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        NetworkDetailSection.values().forEach { section ->
            Text(
                text = section.label,
                color = if (section == selected) PortalColors.accent else PortalColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .background(
                        if (section == selected) PortalColors.button else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(section) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun NetworkPanelToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp, vertical = 5.dp)
            .width(1.dp)
            .height(18.dp)
            .background(PortalColors.border.copy(alpha = 0.85f)),
    )
}

@Composable
private fun NetworkDetailActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val active = enabled && hovered
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (active) PortalColors.button.copy(alpha = 0.72f) else Color.Transparent)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            colorFilter = ColorFilter.tint(
                when {
                    active -> PortalColors.text
                    enabled -> PortalColors.muted
                    else -> PortalColors.muted.copy(alpha = 0.46f)
                },
            ),
        )
        Text(
            text = text,
            color = when {
                active -> PortalColors.text
                enabled -> PortalColors.muted
                else -> PortalColors.muted.copy(alpha = 0.46f)
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun NetworkDetailIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (hovered) PortalColors.button.copy(alpha = 0.72f) else Color.Transparent)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            colorFilter = ColorFilter.tint(if (hovered) PortalColors.text else PortalColors.muted),
        )
    }
}

@Composable
private fun NetworkDetailContent(
    call: PortalNetworkCall,
    selectedSection: NetworkDetailSection,
    onSectionSelect: (NetworkDetailSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (selectedSection) {
        NetworkDetailSection.Overview -> NetworkRequestOverview(
            call = call,
            onResponseBodyClick = { onSectionSelect(NetworkDetailSection.ResponseBody) },
            modifier = modifier,
        )
        NetworkDetailSection.Headers -> NetworkHeadersPreview(
            requestHeaders = call.requestHeaders,
            responseHeaders = call.responseHeaders,
            modifier = modifier,
        )
        NetworkDetailSection.RequestBody -> NetworkBodyPreview(
            body = call.requestBody,
            contentType = call.requestContentType,
            emptyTitle = "No request body",
            emptyMessage = "This network call did not include a captured request body.",
            truncated = call.requestBodyTruncated,
            modifier = modifier,
        )
        NetworkDetailSection.ResponseBody -> NetworkBodyPreview(
            body = call.responseBody,
            contentType = call.responseContentType,
            emptyTitle = "No response body",
            emptyMessage = "This network call did not include a captured response body.",
            truncated = call.responseBodyTruncated,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NetworkRequestOverview(
    call: PortalNetworkCall,
    onResponseBodyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timezoneOffsetMinutes = remember { jsTimezoneOffsetMinutes().toLong() }
    val use12HourClock = remember { jsUses12HourClock() }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = "${call.method} ${call.endpoint}",
                color = PortalColors.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = call.url,
                color = PortalColors.muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NetworkOverviewMetric("Status", call.statusCode?.toString() ?: "ERR", networkStatusColor(call.statusCode))
            NetworkOverviewMetric("Duration", "${call.durationMillis} ms")
            NetworkOverviewMetric(
                label = "Response size",
                value = formatNetworkSize(call.responseBodySizeBytes),
                onClick = onResponseBodyClick,
            )
            NetworkOverviewMetric("Request size", formatNetworkSize(call.requestBodySizeBytes))
            NetworkOverviewMetric(
                label = "Time",
                value = formatAbsoluteLogTime(
                    epochMillis = call.timestampEpochMillis,
                    timezoneOffsetMinutes = timezoneOffsetMinutes,
                    use12HourClock = use12HourClock,
                ),
            )
            NetworkOverviewMetric("Response type", call.responseContentType ?: "-")
            NetworkOverviewMetric("Request type", call.requestContentType ?: "-")
            NetworkOverviewMetric("Mocked", if (call.isMocked) "Yes" else "No")
        }
        call.error?.let { error ->
            StatusCard("Request error", error, PortalColors.error)
        }
        if (call.responseBodyTruncated || call.requestBodyTruncated) {
            StatusCard("Captured body truncated", "One or more captured bodies were shortened by the source plugin.", PortalColors.warning)
        }
    }
}

@Composable
private fun NetworkOverviewMetric(
    label: String,
    value: String,
    valueColor: Color = PortalColors.text,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Column(
        modifier = Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (hovered && onClick != null) PortalColors.button else PortalColors.input.copy(alpha = 0.72f),
            )
            .border(
                1.dp,
                if (onClick != null) PortalColors.accent.copy(alpha = if (hovered) 0.7f else 0.38f) else PortalColors.inputBorder.copy(alpha = 0.7f),
                RoundedCornerShape(7.dp),
            )
            .hoverable(interactionSource = interactionSource, enabled = onClick != null)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = onClick != null,
                onClick = { onClick?.invoke() },
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = if (hovered && onClick != null) PortalColors.text else PortalColors.muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onClick != null) {
                Image(
                    painter = rememberVectorPainter(PortalTabIcons.ArrowRight),
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    colorFilter = ColorFilter.tint(if (hovered) PortalColors.accent else PortalColors.muted),
                )
            }
        }
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NetworkHeadersPreview(
    requestHeaders: Map<String, List<String>>,
    responseHeaders: Map<String, List<String>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NetworkLabeledTextPreview(
            label = "Request headers",
            value = requestHeaders.toNetworkHeaderText(),
            emptyText = "No request headers captured.",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        NetworkLabeledTextPreview(
            label = "Response headers",
            value = responseHeaders.toNetworkHeaderText(),
            emptyText = "No response headers captured.",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun NetworkLabeledTextPreview(
    label: String,
    value: String,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = PortalColors.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        NetworkPlainTextPreview(
            value = value,
            emptyText = emptyText,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun NetworkBodyPreview(
    body: String?,
    contentType: String?,
    emptyTitle: String,
    emptyMessage: String,
    truncated: Boolean,
    modifier: Modifier = Modifier,
) {
    if (body == null) {
        StatusCard(emptyTitle, emptyMessage, PortalColors.muted)
        return
    }
    if (isJsonContentType(contentType)) {
        JsonText(
            jsonString = body,
            modifier = modifier,
            initialMode = JsonTextMode.Json,
            containerColor = PortalColors.codeInput,
            borderColor = if (truncated) PortalColors.warning.copy(alpha = 0.74f) else PortalColors.codeInputBorder,
        )
    } else {
        NetworkPlainTextPreview(
            value = body,
            emptyText = "",
            modifier = modifier,
            borderColor = if (truncated) PortalColors.warning.copy(alpha = 0.74f) else PortalColors.codeInputBorder,
        )
    }
}

@Composable
private fun NetworkPlainTextPreview(
    value: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    borderColor: Color = PortalColors.codeInputBorder,
) {
    val textValue = value.ifBlank { emptyText }
    val textScrollState = rememberScrollState()
    BasicTextField(
        value = textValue,
        onValueChange = {},
        readOnly = true,
        textStyle = TextStyle(
            color = if (value.isBlank()) PortalColors.muted else PortalColors.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        ),
        cursorBrush = SolidColor(PortalColors.accent),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PortalColors.codeInput)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .verticalScroll(textScrollState),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {
                innerTextField()
            }
        },
    )
}

private fun Map<String, List<String>>.toNetworkHeaderText(): String =
    entries.joinToString("\n") { (name, values) ->
        "$name: ${values.joinToString(", ")}"
    }

private fun isJsonContentType(contentType: String?): Boolean {
    val normalized = contentType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?: return false
    return normalized == "application/json" || normalized.endsWith("+json")
}

@Composable
internal fun NetworkResponsePanel(call: PortalNetworkCall) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ResponseBodyHeader(
            call = call,
            actionText = "Response tab",
            onAction = {},
            actionEnabled = false,
        )
        ResponseBodyContent(call = call, modifier = Modifier.fillMaxSize())
    }
}

@Composable
internal fun ResponseBodyHeader(
    call: PortalNetworkCall,
    actionText: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
    extraActionText: String? = null,
    onExtraAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "${call.method} ${call.endpoint}",
                color = PortalColors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(call.statusCode?.toString() ?: "ERR")
                    append(" | ")
                    append(call.durationMillis)
                    append(" ms")
                    call.responseContentType?.let {
                        append(" | ")
                        append(it)
                    }
                    if (call.responseBodyTruncated) append(" | truncated")
                },
                color = PortalColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (extraActionText != null && onExtraAction != null) {
            PortalButton(
                text = extraActionText,
                onClick = onExtraAction,
                kind = PortalButtonKind.Primary,
            )
        }
        PortalButton(
            text = actionText,
            onClick = onAction,
            enabled = actionEnabled,
            kind = PortalButtonKind.Primary,
        )
        if (secondaryActionText != null && onSecondaryAction != null) {
            PortalButton(
                text = secondaryActionText,
                onClick = onSecondaryAction,
            )
        }
    }
}

@Composable
internal fun ResponseBodyContent(
    call: PortalNetworkCall,
    modifier: Modifier = Modifier,
) {
    val body = call.responseBody
    if (body == null) {
        StatusCard("No response body", "This network call did not include a captured response body.", PortalColors.muted)
        return
    }

    JsonText(
        jsonString = body,
        modifier = modifier,
        containerColor = PortalColors.codeInput,
        borderColor = PortalColors.codeInputBorder,
    )
}

@Composable
internal fun NetworkMocksSheet(
    mocks: List<PortalNetworkMock>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (PortalNetworkMock) -> Unit,
    onToggle: (PortalNetworkMock, Boolean) -> Unit,
    onDelete: (PortalNetworkMock) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.46f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp)
                .background(PortalColors.card, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .border(1.dp, PortalColors.border, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mocks", color = PortalColors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("${mocks.count { it.enabled }} enabled / ${mocks.size} total", color = PortalColors.muted, fontSize = 12.sp)
                }
                PortalButton(text = "Add", onClick = onAdd, kind = PortalButtonKind.Primary)
                Spacer(Modifier.width(8.dp))
                PortalButton(text = "Close", onClick = onDismiss)
            }
            if (mocks.isEmpty()) {
                StatusCard("No mocks", "Create a mock from a captured response body.", PortalColors.muted)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(mocks, key = { "mock-${it.id}" }) { mock ->
                        MockListRow(
                            mock = mock,
                            onEdit = { onEdit(mock) },
                            onToggle = { onToggle(mock, it) },
                            onDelete = { onDelete(mock) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MockListRow(
    mock: PortalNetworkMock,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var deleteArmed by remember(mock.id) { mutableStateOf(false) }

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
                .clickable(onClick = onEdit)
                .padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PortalSwitch(checked = mock.enabled, onCheckedChange = onToggle)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(mock.displayLabel(), color = PortalColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(mock.response.label(), color = PortalColors.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PortalVectorIconButton(icon = PortalTabIcons.Edit, onClick = onEdit)
            PortalVectorIconButton(
                icon = PortalTabIcons.Delete,
                onClick = {
                    if (deleteArmed) {
                        onDelete()
                    } else {
                        deleteArmed = true
                    }
                },
                tint = if (deleteArmed) PortalColors.error else PortalColors.text,
            )
        }
        RowDivider()
    }
}

@Composable
internal fun NetworkMockEditorSheet(
    initialMock: PortalNetworkMock,
    onDismiss: () -> Unit,
    onSave: (PortalNetworkMock) -> Unit,
) {
    var enabled by remember(initialMock.id) { mutableStateOf(initialMock.enabled) }
    var matchers by remember(initialMock.id) {
        mutableStateOf(initialMock.expectation.matchers.toSimpleMockMatchers())
    }
    var delayEnabled by remember(initialMock.id) { mutableStateOf(initialMock.expectation.delayMs > 0L) }
    var delaySeconds by remember(initialMock.id) {
        mutableStateOf((initialMock.expectation.delayMs.takeIf { it > 0L }?.div(1000L) ?: 2L).toString())
    }
    var conditionType by remember(initialMock.id) { mutableStateOf("urlContains") }
    var conditionValue by remember(initialMock.id) { mutableStateOf("") }
    var addConditionVisible by remember(initialMock.id) { mutableStateOf(false) }
    var responseMode by remember(initialMock.id) {
        mutableStateOf(if (initialMock.response.error != null) "error" else "body")
    }
    var responseCode by remember(initialMock.id) {
        mutableStateOf((initialMock.response.body?.code ?: 200).toString())
    }
    var responseContentType by remember(initialMock.id) {
        mutableStateOf(initialMock.response.body?.contentType ?: "application/json")
    }
    var responseBody by remember(initialMock.id) {
        mutableStateOf(initialMock.response.body?.body.orEmpty())
    }
    var responseBodyMode by remember(initialMock.id) { mutableStateOf("txt") }
    var responseEditorTab by remember(initialMock.id) { mutableStateOf("body") }
    var responseHeadersText by remember(initialMock.id) {
        mutableStateOf(initialMock.response.body?.headers?.toHeaderLines().orEmpty())
    }
    var errorType by remember(initialMock.id) {
        mutableStateOf(initialMock.response.error?.type ?: PortalNetworkErrorTypes.first())
    }
    var validationError by remember(initialMock.id) { mutableStateOf<String?>(null) }
    var autoSaveReady by remember(initialMock.id) { mutableStateOf(false) }
    var savedIndicatorVisible by remember(initialMock.id) { mutableStateOf(false) }

    fun nextSavedMock(): PortalNetworkMock? {
        val code = responseCode.toIntOrNull()
        val delayMs = if (delayEnabled) {
            (delaySeconds.toLongOrNull() ?: 0L).coerceAtLeast(0L) * 1000L
        } else {
            0L
        }
        val cleanMatchers = matchers.filter { it.value.isNotBlank() }
        validationError = when {
            cleanMatchers.isEmpty() -> "Add at least one matcher."
            responseMode == "body" && code == null -> "Status code must be a number."
            responseMode == "body" && responseContentType.isBlank() -> "Content type is required."
            else -> null
        }
        if (validationError != null) return null
        val now = nowEpochMillis()
        return initialMock.copy(
            enabled = enabled,
            updatedAtEpochMillis = now,
            expectation = PortalNetworkMockExpectation(
                delayMs = delayMs,
                matchers = cleanMatchers,
            ),
            response = if (responseMode == "body") {
                PortalNetworkMockResponse(
                    body = PortalNetworkBodyResponse(
                        code = code ?: 200,
                        body = responseBody,
                        contentType = responseContentType,
                        headers = responseHeadersText.toHeaderMap(),
                    ),
                )
            } else {
                PortalNetworkMockResponse(error = PortalNetworkErrorResponse(errorType))
            },
        )
    }

    LaunchedEffect(
        enabled,
        matchers,
        delayEnabled,
        delaySeconds,
        responseMode,
        responseCode,
        responseContentType,
        responseBody,
        responseHeadersText,
        errorType,
    ) {
        if (!autoSaveReady) {
            autoSaveReady = true
            return@LaunchedEffect
        }
        delay(350L)
        val savedMock = nextSavedMock() ?: return@LaunchedEffect
        onSave(savedMock)
        savedIndicatorVisible = true
        delay(1_300L)
        savedIndicatorVisible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.46f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(640.dp)
                .background(PortalColors.card, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .border(1.dp, PortalColors.border, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "Setup Mock",
                        color = PortalColors.text.copy(alpha = 0.88f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Set up fake responses to test your app in different conditions",
                        color = PortalColors.muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (savedIndicatorVisible) {
                        Text("Saved!", color = PortalColors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    PortalSwitch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
            validationError?.let { StatusCard("Mock not saved", it, PortalColors.error) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    MockEditorSection(
                        title = "Conditions",
                    ) {
                        ConditionFlow(
                            matchers = matchers,
                            onDelete = { deleteIndex ->
                                matchers = matchers.filterIndexed { index, _ -> index != deleteIndex }
                            },
                        ) {
                            if (addConditionVisible) {
                                ConditionAddRow(
                                    type = conditionType,
                                    value = conditionValue,
                                    onTypeChange = {
                                        conditionType = it
                                        conditionValue = ""
                                    },
                                    onValueChange = { conditionValue = it },
                                    onCancel = {
                                        conditionValue = ""
                                        addConditionVisible = false
                                    },
                                    onAdd = { selectedType ->
                                        val nextMatcher = PortalNetworkMockMatcher(
                                            type = selectedType,
                                            value = conditionValue,
                                        )
                                        if (nextMatcher.value.isNotBlank()) {
                                            matchers = matchers + nextMatcher
                                            conditionValue = ""
                                            addConditionVisible = false
                                        }
                                    },
                                )
                            } else {
                                AddConditionPill(onClick = { addConditionVisible = true })
                            }
                        }
                    }
                }
                item {
                    MockEditorSection(
                        title = "Response",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DelayCycleText(
                                    enabled = delayEnabled,
                                    seconds = delaySeconds,
                                    onEnabledChange = { delayEnabled = it },
                                    onSecondsChange = { delaySeconds = it },
                                )
                                ResponseModeTabs(responseMode = responseMode, onChange = { responseMode = it })
                            }
                        },
                    ) {
                        if (responseMode == "body") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CompactTextField("Code", responseCode, { responseCode = it.filter(Char::isDigit).take(3) }, Modifier.width(96.dp))
                                    CompactTextField("Content-Type", responseContentType, { responseContentType = it }, Modifier.weight(1f))
                                }
                                MockResponseEditorTabs(
                                    selected = responseEditorTab,
                                    onChange = { responseEditorTab = it },
                                )
                                if (responseEditorTab == "headers") {
                                    PortalTextField(
                                        value = responseHeadersText,
                                        onValueChange = { responseHeadersText = it },
                                        label = "Headers, one per line: Key: Value",
                                        minLines = 8,
                                        maxLines = 12,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    BodyEditor(
                                        value = responseBody,
                                        onValueChange = { responseBody = it },
                                        mode = responseBodyMode,
                                        onModeChange = { responseBodyMode = it },
                                        onFormatValue = { responseBody = it },
                                        label = null,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PortalNetworkErrorTypes.forEach { type ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { errorType = type }
                                            .padding(horizontal = 4.dp, vertical = 5.dp),
                                    ) {
                                        PortalCheckbox(checked = errorType == type, onCheckedChange = { if (it) errorType = type })
                                        Text(
                                            type,
                                            color = if (errorType == type) PortalColors.text else PortalColors.muted,
                                            fontSize = 13.sp,
                                            fontWeight = if (errorType == type) FontWeight.Medium else null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MockEditorSection(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PortalColors.border),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = PortalColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            trailing?.invoke()
        }
        content()
    }
}

@Composable
internal fun DelayCycleText(
    enabled: Boolean,
    seconds: String,
    onEnabledChange: (Boolean) -> Unit,
    onSecondsChange: (String) -> Unit,
) {
    val normalizedSeconds = seconds.toLongOrNull()?.takeIf { it == 2L || it == 5L } ?: 2L
    val text = if (enabled) "Delay ${normalizedSeconds}s" else "No delay"
    Text(
        text = text,
        color = if (enabled) PortalColors.accent else PortalColors.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                when {
                    !enabled -> {
                        onEnabledChange(true)
                        onSecondsChange("2")
                    }
                    normalizedSeconds == 2L -> {
                        onEnabledChange(true)
                        onSecondsChange("5")
                    }
                    else -> {
                        onEnabledChange(false)
                        onSecondsChange("2")
                    }
                }
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
internal fun MockResponseEditorTabs(
    selected: String,
    onChange: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PortalColors.input)
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(8.dp))
            .padding(2.dp),
    ) {
        ResponseModeTab(text = "Body", selected = selected == "body", onClick = { onChange("body") })
        ResponseModeTab(text = "Headers", selected = selected == "headers", onClick = { onChange("headers") })
    }
}

@Composable
internal fun BodyEditor(
    value: String,
    onValueChange: (String) -> Unit,
    mode: String,
    onModeChange: (String) -> Unit,
    onFormatValue: (String) -> Unit,
    label: String? = "Body",
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let {
            Text(it, color = PortalColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        JsonText(
            jsonString = value,
            onTextChange = onValueChange,
            initialMode = if (mode == "json") JsonTextMode.Json else JsonTextMode.Text,
            onModeChange = { nextMode ->
                if (nextMode == JsonTextMode.Text && mode == "json") {
                    formatJsonOrNull(value)?.let(onFormatValue)
                }
                onModeChange(if (nextMode == JsonTextMode.Json) "json" else "txt")
            },
            containerColor = PortalColors.input,
            borderColor = PortalColors.inputBorder,
            textStyle = TextStyle(
                color = PortalColors.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(214.dp),
        )
    }
}

@Composable
internal fun BodyCodeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        minLines = 9,
        maxLines = 13,
        textStyle = TextStyle(
            color = PortalColors.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        ),
        cursorBrush = SolidColor(PortalColors.accent),
        modifier = modifier
            .background(PortalColors.codeInput, RoundedCornerShape(6.dp))
            .border(1.dp, PortalColors.codeInputBorder, RoundedCornerShape(6.dp)),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (value.isEmpty()) {
                    Text(
                        text = "{\n  \n}",
                        color = PortalColors.muted.copy(alpha = 0.42f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
internal fun BodyModeSelector(
    mode: String,
    jsonRejected: Boolean,
    onTxtClick: () -> Unit,
    onJsonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(vertical = 1.dp),
    ) {
        BodyModeOption(
            text = "TXT",
            selected = mode == "txt",
            enabled = true,
            invalid = false,
            onClick = onTxtClick,
        )
        Text("|", color = PortalColors.inputBorder, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
        BodyModeOption(
            text = "JSON",
            selected = mode == "json",
            enabled = true,
            invalid = jsonRejected,
            onClick = onJsonClick,
        )
    }
}

@Composable
internal fun BodyModeOption(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    invalid: Boolean,
    onClick: () -> Unit,
) {
    val color = when {
        invalid -> PortalColors.error.copy(alpha = 0.78f)
        selected -> PortalColors.accent
        else -> PortalColors.muted
    }
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConditionFlow(
    matchers: List<PortalNetworkMockMatcher>,
    onDelete: (Int) -> Unit,
    trailing: @Composable () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        matchers.withIndex().forEach { indexedMatcher ->
            ConditionChip(matcher = indexedMatcher.value, onDelete = { onDelete(indexedMatcher.index) })
        }
        trailing()
    }
}

@Composable
internal fun NetworkFilterChip(
    rule: NetworkFilterRule,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(30.dp)
            .widthIn(max = 300.dp)
            .background(PortalColors.button, RoundedCornerShape(14.dp))
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Text(
            text = "${rule.mode.label} ${rule.matcher.conditionChipLabel()}",
            color = PortalColors.text,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberVectorPainter(PortalTabIcons.Close),
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                colorFilter = ColorFilter.tint(PortalColors.muted),
            )
        }
    }
}

@Composable
internal fun ConditionChip(
    matcher: PortalNetworkMockMatcher,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(30.dp)
            .widthIn(max = 260.dp)
            .background(PortalColors.button, RoundedCornerShape(14.dp))
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Text(
            text = matcher.conditionChipLabel(),
            color = PortalColors.text,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberVectorPainter(PortalTabIcons.Close),
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                colorFilter = ColorFilter.tint(PortalColors.muted),
            )
        }
    }
}

@Composable
internal fun AddConditionPill(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(30.dp)
            .background(PortalColors.button, RoundedCornerShape(14.dp))
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Text("Add another", color = PortalColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = rememberVectorPainter(PortalTabIcons.Plus),
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                colorFilter = ColorFilter.tint(PortalColors.muted),
            )
        }
    }
}

@Composable
internal fun ConditionAddRow(
    type: String,
    value: String,
    onTypeChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit,
    onAdd: (String) -> Unit,
) {
    val selectedType = type.takeIf { it in MockConditionTypes } ?: MockConditionTypes.first()
    LaunchedEffect(type) {
        if (type !in MockConditionTypes) {
            onTypeChange(MockConditionTypes.first())
        }
    }
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 460.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(30.dp)
                .widthIn(max = 460.dp)
                .background(PortalColors.button, RoundedCornerShape(14.dp))
                .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
                .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
        ) {
            InlineConditionTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(110.dp),
            )
            Text("in", color = PortalColors.muted, fontSize = 12.sp)
            ConditionTypeSelector(
                selected = selectedType,
                expanded = expanded,
                onClick = { expanded = !expanded },
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { if (value.isBlank()) onCancel() else onAdd(selectedType) },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = rememberVectorPainter(if (value.isBlank()) PortalTabIcons.Close else PortalTabIcons.Plus),
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    colorFilter = ColorFilter.tint(if (value.isBlank()) PortalColors.muted else PortalColors.text),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(120)) + expandVertically(animationSpec = tween(140)),
            exit = fadeOut(animationSpec = tween(90)) + shrinkVertically(animationSpec = tween(120)),
        ) {
            ConditionTypeMenu(
                options = MockConditionTypes,
                selected = selectedType,
                onSelect = {
                    expanded = false
                    onTypeChange(it)
                },
                modifier = Modifier.widthIn(max = 460.dp),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
internal fun NetworkFilterModeSelector(
    selected: NetworkFilterMode,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
    ) {
        Text(selected.label, color = PortalColors.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Image(
            painter = rememberVectorPainter(if (expanded) PortalTabIcons.ArrowUp else PortalTabIcons.ArrowDown),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            colorFilter = ColorFilter.tint(PortalColors.muted),
        )
    }
}

@Composable
internal fun NetworkFilterModeMenu(
    selected: NetworkFilterMode,
    onSelect: (NetworkFilterMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .background(PortalColors.card, RoundedCornerShape(14.dp))
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        NetworkFilterMode.values().forEach { option ->
            Text(
                text = option.label,
                color = if (option == selected) PortalColors.accent else PortalColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .background(if (option == selected) PortalColors.button else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
internal fun ConditionTypeSelector(
    selected: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
    ) {
        Text(selected.matcherLabel(), color = PortalColors.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Image(
            painter = rememberVectorPainter(if (expanded) PortalTabIcons.ArrowUp else PortalTabIcons.ArrowDown),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            colorFilter = ColorFilter.tint(PortalColors.muted),
        )
    }
}

@Composable
internal fun ConditionTypeMenu(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .background(PortalColors.card, RoundedCornerShape(14.dp))
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        options.forEach { option ->
            Text(
                text = option.matcherLabel(),
                color = if (option == selected) PortalColors.accent else PortalColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .background(if (option == selected) PortalColors.button else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
internal fun ResponseModeTabs(
    responseMode: String,
    onChange: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PortalColors.input)
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(8.dp))
            .padding(2.dp),
    ) {
        ResponseModeTab(text = "Body", selected = responseMode == "body", onClick = { onChange("body") })
        ResponseModeTab(text = "Error", selected = responseMode == "error", onClick = { onChange("error") })
    }
}

@Composable
internal fun ResponseModeTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) PortalColors.button else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) PortalColors.accent else PortalColors.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun CompactTextField(
    label: String?,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PortalTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = true,
        modifier = modifier,
    )
}
