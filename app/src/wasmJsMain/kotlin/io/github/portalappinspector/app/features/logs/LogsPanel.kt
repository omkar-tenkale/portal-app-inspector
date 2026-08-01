package io.github.portalappinspector.app.features.logs

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
import androidx.compose.ui.input.key.type
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonArray
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import io.github.portalappinspector.app.data.*
import io.github.portalappinspector.app.ui.*
import io.github.portalappinspector.app.ui.icons.*
import io.github.portalappinspector.app.util.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun LogsPanel(
    connection: PortalConnection,
    client: PortalSourceClient,
    enabled: Boolean,
) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastTimestamp by remember(connection) { mutableStateOf(0L) }
    var containsDraft by remember { mutableStateOf("") }
    var filter by remember(connection) { mutableStateOf(PortalLogsFilter()) }
    val logs = remember(connection) { mutableStateListOf<PortalLogEntry>() }
    val logListState = rememberLazyListState()
    val timezoneOffsetMinutes = remember { jsTimezoneOffsetMinutes().toLong() }
    val use12HourClock = remember { jsUses12HourClock() }
    var logDisplayNowEpochMillis by remember { mutableStateOf(nowEpochMillis()) }
    val filterKey = filter.signature()
    val logGapThresholdMillis = if (filter.hasActiveFilters()) {
        FilteredLogGapThresholdMillis
    } else {
        LogGapThresholdMillis
    }
    val logRows = remember(logs.size, logs.firstOrNull()?.id, logs.lastOrNull()?.id, filterKey) {
        logs.reversed().toLogRows(logGapThresholdMillis)
    }

    fun addContainsFilter() {
        val value = containsDraft.trim()
        if (value.isBlank()) return
        filter = filter.copy(contains = (filter.contains + value).distinct())
        containsDraft = ""
    }

    fun removeContainsFilter(value: String) {
        filter = filter.copy(contains = filter.contains.filterNot { it == value })
    }

    fun toggleLevel(level: String) {
        filter = filter.copy(
            levels = if (level in filter.levels) filter.levels - level else filter.levels + level,
        )
    }

    fun toggleSource(source: String) {
        filter = filter.copy(
            sources = if (source in filter.sources) filter.sources - source else filter.sources + source,
        )
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
                    put("filter", filter.toJson())
                },
            )
        }.onSuccess { payload ->
            error = null
            val nextLogs = payload["items"]?.jsonArray?.map(::parseLogEntry).orEmpty()
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
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!enabled) {
            StatusCard("Logs plugin unavailable", "Install the portal-logs plugin in the source app.", PortalColors.warning)
            return@Column
        }
        LogsFilterBar(
            filter = filter,
            containsDraft = containsDraft,
            onContainsDraftChange = { containsDraft = it },
            onAddContains = ::addContainsFilter,
            onRemoveContains = ::removeContainsFilter,
            onToggleLevel = ::toggleLevel,
            onToggleSource = ::toggleSource,
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
            StatusCard("No logs captured", "Trigger Android Log or System print calls in the source app.", PortalColors.muted)
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
                    )
                    is PortalLogRow.Gap -> LogGapRow(row.durationMillis)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogsFilterBar(
    filter: PortalLogsFilter,
    containsDraft: String,
    onContainsDraftChange: (String) -> Unit,
    onAddContains: () -> Unit,
    onRemoveContains: (String) -> Unit,
    onToggleLevel: (String) -> Unit,
    onToggleSource: (String) -> Unit,
    onClear: () -> Unit,
    clearEnabled: Boolean,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        filter.contains.forEach { value ->
            LogFilterChip(
                text = "contains:$value",
                selected = true,
                onClick = { onRemoveContains(value) },
            )
        }
        LogContainsFilterField(
            value = containsDraft,
            onValueChange = onContainsDraftChange,
            onAdd = onAddContains,
        )
        PortalLogLevels.forEach { level ->
            LogFilterChip(
                text = "level:$level",
                selected = level in filter.levels,
                onClick = { onToggleLevel(level) },
            )
        }
        PortalLogSources.forEach { source ->
            LogFilterChip(
                text = "source:${source.logSourceLabel()}",
                selected = source in filter.sources,
                onClick = { onToggleSource(source) },
            )
        }
        PortalButton(
            text = "Clear",
            onClick = onClear,
            enabled = clearEnabled,
            modifier = Modifier.height(30.dp),
        )
    }
}

@Composable
internal fun LogContainsFilterField(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(30.dp)
            .widthIn(min = 190.dp, max = 260.dp)
            .background(PortalColors.input, RoundedCornerShape(14.dp))
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Text("contains", color = PortalColors.muted, fontSize = 12.sp, maxLines = 1)
        InlineConditionTextField(
            value = value,
            onValueChange = onValueChange,
            onSubmit = onAdd,
            showUnderline = true,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = value.isNotBlank(), onClick = onAdd),
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
}

@Composable
internal fun LogFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(30.dp)
            .widthIn(max = 220.dp)
            .background(if (selected) PortalColors.button else Color.Transparent, RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (selected) PortalColors.accent.copy(alpha = 0.72f) else PortalColors.inputBorder,
                RoundedCornerShape(14.dp),
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Text(
            text = text,
            color = if (selected) PortalColors.text else PortalColors.muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected && text.startsWith("contains:")) {
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
internal fun LogsTimelineHeader(loading: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PortalColors.border.copy(alpha = 0.72f)),
        )
        LogsListeningIndicator(loading)
    }
}

@Composable
internal fun LogsListeningIndicator(loading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 6.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(92.dp))
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.size(width = 34.dp, height = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            PulsatingDots(
                color = if (loading) PortalColors.accent else PortalColors.muted,
                dotDiameter = 4.dp,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Listening for logs",
            color = PortalColors.muted.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun LogEntryRow(
    entry: PortalLogEntry,
    nowEpochMillis: Long,
    timezoneOffsetMinutes: Long,
    use12HourClock: Boolean,
) {
    var expanded by remember(entry.id) { mutableStateOf(false) }
    var showActualTime by remember(entry.id) { mutableStateOf(false) }
    val relativeTime = formatRelativeLogTime(entry.timestampEpochMillis, nowEpochMillis)
    val timeText = if (showActualTime || relativeTime == null) {
        formatAbsoluteLogTime(
            epochMillis = entry.timestampEpochMillis,
            timezoneOffsetMinutes = timezoneOffsetMinutes,
            use12HourClock = use12HourClock,
        )
    } else {
        relativeTime
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable { expanded = !expanded }
                .padding(end = 6.dp),
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
            TimelineLogMarker(entry.level)
            Spacer(Modifier.width(12.dp))
            Text(
                text = entry.logLine(),
                color = PortalColors.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            entry.location?.let { location ->
                Spacer(Modifier.width(10.dp))
                Text(
                    text = location.shortLabel(),
                    color = PortalColors.muted.copy(alpha = 0.82f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 170.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(120)) + expandVertically(animationSpec = tween(140)),
            exit = fadeOut(animationSpec = tween(90)) + shrinkVertically(animationSpec = tween(120)),
        ) {
            LogEntryDetails(entry)
        }
    }
}

internal fun logLevelColor(level: String): Color =
    when (level) {
        "verbose" -> Color(0xFF8A8F98)
        "debug" -> Color(0xFF4EA1FF)
        "info" -> Color(0xFF2DB55D)
        "warning" -> Color(0xFFFFB020)
        "error" -> Color(0xFFFF5C7A)
        "assert" -> Color(0xFFC084FC)
        else -> Color(0xFF8A8F98)
    }

internal fun logLevelInitial(level: String): String =
    when (level) {
        "verbose" -> "V"
        "debug" -> "D"
        "info" -> "I"
        "warning" -> "W"
        "error" -> "E"
        "assert" -> "A"
        else -> "?"
    }

@Composable
internal fun TimelineLogMarker(level: String) {
    val levelColor = logLevelColor(level)
    val markerShape = RoundedCornerShape(50)
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
                .size(22.dp)
                .background(levelColor.copy(alpha = 0.12f), markerShape)
                .border(1.dp, levelColor.copy(alpha = 0.42f), markerShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = logLevelInitial(level),
                color = levelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun LogEntryDetails(entry: PortalLogEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 148.dp, end = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LogDetailLine("level", entry.level)
        LogDetailLine("source", entry.source.logSourceLabel())
        entry.stream?.takeIf { it.isNotBlank() }?.let { LogDetailLine("stream", it) }
        entry.tag?.takeIf { it.isNotBlank() }?.let { LogDetailLine("tag", it) }
        entry.location?.let { LogDetailLine("location", it.detailLabel()) }
        entry.callSites.drop(1).takeIf { it.isNotEmpty() }?.let { callSites ->
            LogDetailLine("call sites", callSites.joinToString(" <- ") { it.detailLabel() })
        }
        LogDetailLine("message", entry.message.ifBlank { "(empty)" })
        entry.throwable?.takeIf { it.isNotBlank() }?.let { LogDetailLine("throwable", it) }
        entry.threadName.takeIf { it.isNotBlank() }?.let { LogDetailLine("thread", it) }
    }
}

@Composable
internal fun LogDetailLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$label:",
            color = PortalColors.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.width(68.dp),
        )
        Text(
            text = value,
            color = PortalColors.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
