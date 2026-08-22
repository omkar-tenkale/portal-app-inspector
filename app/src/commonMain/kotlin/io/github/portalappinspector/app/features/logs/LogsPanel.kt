package io.github.portalappinspector.app.features.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.portalappinspector.app.data.PortalApi
import io.github.portalappinspector.app.features.network.networkJson
import io.github.portalappinspector.app.features.network.NetworkFilterMode
import io.github.portalappinspector.app.ui.LogGapRow
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.StatusCard
import io.github.portalappinspector.app.util.jsTimezoneOffsetMinutes
import io.github.portalappinspector.app.util.jsUses12HourClock
import io.github.portalappinspector.app.util.nowEpochMillis
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

internal const val LogsPluginId = "portal-logs"

internal const val LogGapThresholdMillis = 10_000L
internal const val FilteredLogGapThresholdMillis = 1_000L


@Composable
internal fun LogsPanel(
    api: PortalApi,
) {
    val enabled = api.hasPlugin(LogsPluginId)
    val state = remember { LogsPanelState() }
    val logListState = rememberLazyListState()
    val timezoneOffsetMinutes = remember { jsTimezoneOffsetMinutes().toLong() }
    val use12HourClock = remember { jsUses12HourClock() }
    var logDisplayNowEpochMillis by remember { mutableStateOf(nowEpochMillis()) }
    val sourceFilter = remember { PortalLogsFilter(sources = setOf(AndroidLogSource)) }
    val filterKey = sourceFilter.signature()
    val filteredLogs = state.logs.asReversed().filter { entry ->
        entry.source == AndroidLogSource && state.filters.matchesLogEntry(entry)
    }
    val logGapThresholdMillis = if (state.filters.any { it.mode != NetworkFilterMode.Highlight }) {
        FilteredLogGapThresholdMillis
    } else {
        LogGapThresholdMillis
    }
    val logRows = remember(state.logs.size, state.logs.firstOrNull()?.id, state.logs.lastOrNull()?.id, state.filters.signature(), logGapThresholdMillis) {
        filteredLogs.toLogRows(logGapThresholdMillis)
    }

    fun addFilter(selectedMode: NetworkFilterMode, selectedType: String) {
        val value = state.filterValue.trim()
        if (value.isBlank()) return
        state.filters = (state.filters + LogFilterRule(selectedMode, selectedType, value)).distinct()
        state.filterValue = ""
    }

    fun removeFilter(index: Int) {
        state.filters = state.filters.filterIndexed { filterIndex, _ -> filterIndex != index }
    }

    suspend fun loadNewLogs() {
        state.loading = true
        runCatching {
            api.request(
                pluginId = LogsPluginId,
                payload = buildJsonObject {
                    put("type", "listAfter")
                    put("logsAfterEpochMillis", state.lastTimestamp)
                    put("filter", sourceFilter.toJson())
                },
            )
        }.onSuccess { payload ->
            state.error = null
            val nextLogs = payload["items"]?.jsonArray?.map({
                networkJson.decodeFromJsonElement<PortalLogEntry>(it)
            }).orEmpty()
            val existingIds = state.logs.mapTo(mutableSetOf()) { it.id }
            state.logs += nextLogs.filter { existingIds.add(it.id) }
            state.lastTimestamp = state.logs.maxOfOrNull { it.timestampEpochMillis } ?: state.lastTimestamp
        }.onFailure { throwable ->
            state.error = throwable.message ?: throwable::class.simpleName
        }
        state.loading = false
    }

    LaunchedEffect(enabled, api, filterKey) {
        state.logs.clear()
        state.lastTimestamp = 0L
        if (!enabled) return@LaunchedEffect

        while (true) {
            loadNewLogs()
            delay(2_000L)
        }
    }

    LaunchedEffect(logRows.firstOrNull()?.key) {
        if (logRows.isNotEmpty()) {
            logListState.scrollToItem(0)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            logDisplayNowEpochMillis = nowEpochMillis()
            delay(1_000L)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!enabled) {
            StatusCard("Logs plugin unavailable", "Install the portal-logs plugin in the source app.", PortalColors.warning)
            return@Column
        }
        LogsFilterBar(
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
            onAddFilter = ::addFilter,
            onDeleteFilter = ::removeFilter,
            onClear = {
                state.lastTimestamp = state.logs.maxOfOrNull { it.timestampEpochMillis } ?: state.lastTimestamp
                state.logs.clear()
            },
            clearEnabled = state.logs.isNotEmpty(),
        )
        LogsTimelineHeader(state.loading)
        state.error?.let {
            StatusCard("Logs request failed", it, PortalColors.error)
        }
        if (state.logs.isEmpty() && !state.loading && state.error == null) {
            StatusCard("No logs captured", "Trigger Android Log calls in the source app.", PortalColors.muted)
        }
        if (state.logs.isNotEmpty() && filteredLogs.isEmpty()) {
            StatusCard("No matching logs", "Adjust the log filter.", PortalColors.muted)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = logListState,
        ) {
            items(logRows, key = { it.key }) { row ->
                when (row) {
                    is PortalLogRow.Entry -> LogEntryRow(
                        entry = row.entry,
                        nowEpochMillis = logDisplayNowEpochMillis,
                        timezoneOffsetMinutes = timezoneOffsetMinutes,
                        use12HourClock = use12HourClock,
                        highlight = state.filters.firstHighlightFor(row.entry),
                    )
                    is PortalLogRow.Gap -> LogGapRow(row.durationMillis)
                }
            }
        }
    }
}
