package io.github.portalappinspector.app.features.network

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.focus.*
import kotlinx.coroutines.*
import io.github.portalappinspector.app.data.*
import io.github.portalappinspector.app.ui.*
import io.github.portalappinspector.app.ui.icons.*
import io.github.portalappinspector.app.util.*
import kotlinx.serialization.json.*
import io.viascom.nanoid.NanoId

@Composable
internal fun NetworkPanel(
    connection: PortalConnection,
    client: PortalSourceClient,
    enabled: Boolean,
    appId: String?,
    onOpenResponseTab: (PortalNetworkCall) -> Unit,
    mobileView: Boolean = false,
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
    val networkRows = remember(filteredCalls, networkDisplayNowEpochMillis) {
        filteredCalls.toNetworkRows(networkDisplayNowEpochMillis)
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
            val nextCalls = payload["items"]?.jsonArray?.map({
                networkJson.decodeFromJsonElement<PortalNetworkCall>(it)
            }).orEmpty()
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
            if (mobileView) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 6.dp, end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        NetworkDetailActionButton(
                            icon = PortalTabIcons.Mock,
                            text = "Mocks ${mocks.count { it.enabled }}",
                            onClick = { mocksSheetOpen = true },
                            enabled = enabled,
                            modifier = Modifier.height(28.dp),
                        )
                        Spacer(Modifier.width(8.dp))
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 6.dp, end = 10.dp),
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
                        is PortalNetworkRow.Gap -> NetworkGapRow(
                            type = row.type,
                            nowEpochMillis = networkDisplayNowEpochMillis,
                            timezoneOffsetMinutes = timezoneOffsetMinutes,
                            use12HourClock = use12HourClock,
                        )
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
                    fun createBlankMock(): PortalNetworkMock {
                        val now = nowEpochMillis()
                        return PortalNetworkMock(
                            id = NanoId.generate(),
                            enabled = true,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now,
                            expectation = PortalNetworkMockExpectation(
                                matchers = listOf(PortalNetworkMockMatcher(type = "methodContains", value = "GET")),
                            ),
                            response = PortalNetworkMockResponse(
                                body = PortalNetworkBodyResponse(),
                            ),
                        )
                    }
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

@Preview
@Composable
private fun NetworkPanelPreview() {
    Box(Modifier.background(PortalColors.background).fillMaxSize()) {
        NetworkPanel(
            connection = PortalConnection("localhost", "4896"),
            client = PortalSourceClient(),
            enabled = true,
            appId = "com.example.app",
            onOpenResponseTab = {}
        )
    }
}
