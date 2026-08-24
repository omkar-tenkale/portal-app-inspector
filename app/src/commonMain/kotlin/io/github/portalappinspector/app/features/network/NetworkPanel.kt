package io.github.portalappinspector.app.features.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.portalappinspector.app.data.PortalApi
import io.github.portalappinspector.app.data.StorageJson
import io.github.portalappinspector.app.ui.NetworkGapRow
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.StatusCard
import io.github.portalappinspector.app.ui.icons.PortalTabIcons
import io.github.portalappinspector.app.util.jsTimezoneOffsetMinutes
import io.github.portalappinspector.app.util.jsUses12HourClock
import io.github.portalappinspector.app.util.nowEpochMillis
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import io.github.portalappinspector.app.ui.ResponsiveRow

internal const val NetworkPluginId = "portal-network"

@Composable
internal fun NetworkPanel(
    api: PortalApi,
    onOpenResponseTab: (PortalNetworkCall) -> Unit,
) {
    val enabled = api.hasPlugin(NetworkPluginId)
    val appId = api.appId
    val state = remember(api, appId) { NetworkPanelState(appId) }
    val networkListState = rememberLazyListState()
    val timezoneOffsetMinutes = remember { jsTimezoneOffsetMinutes().toLong() }
    val use12HourClock = remember { jsUses12HourClock() }
    var networkDisplayNowEpochMillis by remember { mutableStateOf(nowEpochMillis()) }
    val mockScope = rememberCoroutineScope()
    
    val filteredCalls = state.calls.asReversed().filter { call -> state.filters.matchesNetworkCall(call) }
    val networkRows = remember(filteredCalls, networkDisplayNowEpochMillis) {
        filteredCalls.toNetworkRows(networkDisplayNowEpochMillis)
    }

    suspend fun syncMocks(nextMocks: List<PortalNetworkMock> = state.mocks) {
        if (!enabled) return
        runCatching {
            api.request(
                pluginId = NetworkPluginId,
                payload = buildJsonObject {
                    put("type", "setupMocks")
                    put("mocks", StorageJson.encodeToJsonElement(nextMocks.filter { it.enabled }))
                },
            )
        }.onSuccess {
            state.mockSyncError = null
        }.onFailure { throwable ->
            state.mockSyncError = throwable.message ?: throwable::class.simpleName
        }
    }

    fun updateMocks(nextMocks: List<PortalNetworkMock>) {
        val saved = PortalNetworkMockStore.save(appId, nextMocks)
        state.mocks = saved
        mockScope.launch {
            syncMocks(saved)
        }
    }

    suspend fun loadNewCalls() {
        state.loading = true
        runCatching {
            api.request(
                pluginId = NetworkPluginId,
                payload = buildJsonObject {
                    put("type", "listAfter")
                    put("afterTimestampEpochMillis", state.lastTimestamp)
                },
            )
        }.onSuccess { payload ->
            state.error = null
            val nextCalls = payload["items"]?.jsonArray?.map({
                networkJson.decodeFromJsonElement<PortalNetworkCall>(it)
            }).orEmpty()
            val existingIds = state.calls.mapTo(mutableSetOf()) { it.id }
            state.calls += nextCalls.filter { existingIds.add(it.id) }
            state.lastTimestamp = state.calls.maxOfOrNull { it.timestampEpochMillis } ?: state.lastTimestamp
        }.onFailure { throwable ->
            state.error = throwable.message ?: throwable::class.simpleName
        }
        state.loading = false
    }

    LaunchedEffect(enabled, api, appId) {
        state.calls.clear()
        state.lastTimestamp = 0L
        if (!enabled) return@LaunchedEffect

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
            ResponsiveRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, top = 6.dp, end = 10.dp),
                leftContent = {
                    NetworkFilterBar(
                        filters = state.filters,
                        filterMode = state.filterMode,
                        filterType = state.filterType,
                        filterValue = state.filterValue,
                        onFilterModeChange = { state.filterMode = it },
                        onFilterTypeChange = {
                            state.filterType = it
                            state.filterValue = ""
                        },
                        onFilterValueChange = { state.filterValue = it },
                        onAddFilter = { selectedMode, selectedType ->
                            val nextFilter = NetworkFilterRule(
                                mode = selectedMode,
                                matcher = PortalNetworkMockMatcher(
                                    type = selectedType,
                                    value = state.filterValue.trim(),
                                ),
                            )
                            if (nextFilter.matcher.value.isNotBlank()) {
                                state.filters = state.filters + nextFilter
                                state.filterValue = ""
                            }
                        },
                        onDeleteFilter = { deleteIndex ->
                            state.filters = state.filters.filterIndexed { index, _ -> index != deleteIndex }
                        },
                        modifier = Modifier,
                    )
                },
                rightContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NetworkDetailActionButton(
                            icon = PortalTabIcons.Mock,
                            text = "Mocks ${state.mocks.count { it.enabled }}",
                            onClick = { state.mocksSheetOpen = true },
                            enabled = enabled,
                            modifier = Modifier.height(28.dp),
                        )
                        NetworkDetailActionButton(
                            icon = PortalTabIcons.Delete,
                            text = "Clear",
                            onClick = {
                                state.lastTimestamp = state.calls.maxOfOrNull { it.timestampEpochMillis } ?: state.lastTimestamp
                                state.calls.clear()
                            },
                            enabled = state.calls.isNotEmpty(),
                            modifier = Modifier.height(28.dp),
                        )
                    }
                }
            )
            if (!enabled) {
                StatusCard("Network plugin unavailable", "Install the portal-network plugin in the source app.", PortalColors.warning)
                return@Column
            }
            state.error?.let {
                StatusCard("Network request failed", it, PortalColors.error)
            }
            state.mockSyncError?.let {
                StatusCard("Mock sync failed", it, PortalColors.warning)
            }
            if (state.calls.isEmpty() && !state.loading) {
                StatusCard("No traffic captured", "Trigger a Retrofit request in the source app.", PortalColors.muted)
            }
            if (state.calls.isNotEmpty() && filteredCalls.isEmpty()) {
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
                            highlight = state.filters.firstHighlightFor(row.call),
                            onViewBody = { state.bodySheetCall = row.call },
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
        state.bodySheetCall?.let { call ->
            ResponseBodySheet(
                call = call,
                onDismiss = { state.bodySheetCall = null },
                onOpenTab = {
                    state.bodySheetCall = null
                    onOpenResponseTab(call)
                },
                onMock = {
                    state.bodySheetCall = null
                    state.mockEditorInitial = createMockFromCall(call)
                },
            )
        }
        if (state.mocksSheetOpen) {
            NetworkMocksSheet(
                mocks = state.mocks,
                onDismiss = { state.mocksSheetOpen = false },
                onAdd = {
                    state.mocksSheetOpen = false
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
                    state.mockEditorInitial = createBlankMock()
                },
                onEdit = { mock ->
                    state.mocksSheetOpen = false
                    state.mockEditorInitial = mock
                },
                onToggle = { mock, enabled ->
                    updateMocks(
                        state.mocks.map {
                            if (it.id == mock.id) {
                                it.copy(enabled = enabled, updatedAtEpochMillis = nowEpochMillis())
                            } else {
                                it
                            }
                        },
                    )
                },
                onDelete = { mock ->
                    updateMocks(state.mocks.filterNot { it.id == mock.id })
                },
            )
        }
        state.mockEditorInitial?.let { initialMock ->
            NetworkMockEditorSheet(
                initialMock = initialMock,
                onDismiss = { state.mockEditorInitial = null },
                onSave = { savedMock ->
                    updateMocks(state.mocks.filterNot { it.id == savedMock.id } + savedMock)
                },
            )
        }
    }
}
