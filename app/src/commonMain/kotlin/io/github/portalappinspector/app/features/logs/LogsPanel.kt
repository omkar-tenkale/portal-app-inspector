package io.github.portalappinspector.app.features.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.portalappinspector.app.data.FilteredLogGapThresholdMillis
import io.github.portalappinspector.app.data.LogGapThresholdMillis
import io.github.portalappinspector.app.data.LogsPluginId
import io.github.portalappinspector.app.data.PortalConnection
import io.github.portalappinspector.app.data.PortalSourceClient
import io.github.portalappinspector.app.data.networkJson
import io.github.portalappinspector.app.data.toLogRows
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

@Composable
internal fun LogsPanel(
    connection: PortalConnection,
    client: PortalSourceClient,
    enabled: Boolean,
) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastTimestamp by remember(connection) { mutableStateOf(0L) }
    var filterMode by remember(connection) { mutableStateOf(NetworkFilterMode.Include) }
    var filterType by remember(connection) { mutableStateOf(LogFilterTypes.first()) }
    var filterValue by remember(connection) { mutableStateOf("") }
    var filters by remember(connection) { mutableStateOf(emptyList<LogFilterRule>()) }
    val logs = remember(connection) { mutableStateListOf<PortalLogEntry>() }
    val logListState = rememberLazyListState()
    val timezoneOffsetMinutes = remember { jsTimezoneOffsetMinutes().toLong() }
    val use12HourClock = remember { jsUses12HourClock() }
    var logDisplayNowEpochMillis by remember { mutableStateOf(nowEpochMillis()) }
    val sourceFilter = remember { PortalLogsFilter(sources = setOf(AndroidLogSource)) }
    val filterKey = sourceFilter.signature()
    val filteredLogs = logs.asReversed().filter { entry ->
        entry.source == AndroidLogSource && filters.matchesLogEntry(entry)
    }
    val logGapThresholdMillis = if (filters.any { it.mode != NetworkFilterMode.Highlight }) {
        FilteredLogGapThresholdMillis
    } else {
        LogGapThresholdMillis
    }
    val logRows = remember(logs.size, logs.firstOrNull()?.id, logs.lastOrNull()?.id, filters.signature(), logGapThresholdMillis) {
        filteredLogs.toLogRows(logGapThresholdMillis)
    }

    fun addFilter(selectedMode: NetworkFilterMode, selectedType: String) {
        val value = filterValue.trim()
        if (value.isBlank()) return
        filters = (filters + LogFilterRule(selectedMode, selectedType, value)).distinct()
        filterValue = ""
    }

    fun removeFilter(index: Int) {
        filters = filters.filterIndexed { filterIndex, _ -> filterIndex != index }
    }

    suspend fun loadNewLogs() {
        loading = true
        runCatching {
            client.request(
                connection = connection,
                pluginId = LogsPluginId,
                payload = buildJsonObject {
                    put("type", "listAfter")
                    put("logsAfterEpochMillis", lastTimestamp)
                    put("filter", sourceFilter.toJson())
                },
            )
        }.onSuccess { payload ->
            error = null
            val nextLogs = payload["items"]?.jsonArray?.map({
                networkJson.decodeFromJsonElement<PortalLogEntry>(it)
            }).orEmpty()
            val existingIds = logs.mapTo(mutableSetOf()) { it.id }
            logs += nextLogs.filter { existingIds.add(it.id) }
            lastTimestamp = logs.maxOfOrNull { it.timestampEpochMillis } ?: lastTimestamp
        }.onFailure { throwable ->
            error = throwable.message ?: throwable::class.simpleName
        }
        loading = false
    }

    LaunchedEffect(enabled, connection, filterKey) {
        logs.clear()
        lastTimestamp = 0L
        if (!enabled || !connection.isValid) return@LaunchedEffect

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
            onAddFilter = ::addFilter,
            onDeleteFilter = ::removeFilter,
            onClear = {
                lastTimestamp = logs.maxOfOrNull { it.timestampEpochMillis } ?: lastTimestamp
                logs.clear()
            },
            clearEnabled = logs.isNotEmpty(),
        )
        LogsTimelineHeader(loading)
        error?.let {
            StatusCard("Logs request failed", it, PortalColors.error)
        }
        if (logs.isEmpty() && !loading && error == null) {
            StatusCard("No logs captured", "Trigger Android Log calls in the source app.", PortalColors.muted)
        }
        if (logs.isNotEmpty() && filteredLogs.isEmpty()) {
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
                        highlight = filters.firstHighlightFor(row.entry),
                    )
                    is PortalLogRow.Gap -> LogGapRow(row.durationMillis)
                }
            }
        }
    }
}

@Preview
@Composable
private fun LogsPanelPreview() {
    Box(Modifier.background(PortalColors.background).fillMaxSize()) {
        LogsPanel(
            connection = PortalConnection("127.0.0.1", "8080"),
            client = PortalSourceClient(),
            enabled = true
        )
    }
}
