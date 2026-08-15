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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogsFilterBar(
    filters: List<LogFilterRule>,
    filterMode: NetworkFilterMode,
    filterType: String,
    filterValue: String,
    onFilterModeChange: (NetworkFilterMode) -> Unit,
    onFilterTypeChange: (String) -> Unit,
    onFilterValueChange: (String) -> Unit,
    onAddFilter: (NetworkFilterMode, String) -> Unit,
    onDeleteFilter: (Int) -> Unit,
    onClear: () -> Unit,
    clearEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            filters.withIndex().forEach { indexedFilter ->
                LogFilterChip(
                    rule = indexedFilter.value,
                    onDelete = { onDeleteFilter(indexedFilter.index) },
                )
            }
            LogFilterAddRow(
                mode = filterMode,
                type = filterType,
                value = filterValue,
                onModeChange = onFilterModeChange,
                onTypeChange = onFilterTypeChange,
                onValueChange = onFilterValueChange,
                onAdd = onAddFilter,
            )
        }
        LogsToolbarDivider()
        LogsToolbarActionButton(
            icon = PortalTabIcons.Delete,
            text = "Clear",
            onClick = onClear,
            enabled = clearEnabled,
            modifier = Modifier.height(28.dp),
        )
    }
}

@Composable
internal fun LogFilterAddRow(
    mode: NetworkFilterMode,
    type: String,
    value: String,
    onModeChange: (NetworkFilterMode) -> Unit,
    onTypeChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onAdd: (NetworkFilterMode, String) -> Unit,
) {
    val selectedType = type.takeIf { it in LogFilterTypes } ?: LogFilterTypes.first()
    LaunchedEffect(type) {
        if (type !in LogFilterTypes) {
            onTypeChange(LogFilterTypes.first())
        }
    }
    var activeMenu by remember { mutableStateOf(LogFilterMenu.Mode) }
    var menuVisible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 420.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(30.dp)
                .widthIn(max = 360.dp)
                .background(PortalColors.button, RoundedCornerShape(14.dp))
                .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
                .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
        ) {
            LogFilterModeSelector(
                selected = mode,
                expanded = menuVisible && activeMenu == LogFilterMenu.Mode,
                onClick = {
                    if (activeMenu == LogFilterMenu.Mode) {
                        menuVisible = !menuVisible
                    } else {
                        activeMenu = LogFilterMenu.Mode
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
            LogFilterTypeSelector(
                selected = selectedType,
                expanded = menuVisible && activeMenu == LogFilterMenu.Type,
                onClick = {
                    if (activeMenu == LogFilterMenu.Type) {
                        menuVisible = !menuVisible
                    } else {
                        activeMenu = LogFilterMenu.Type
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
                LogFilterMenu.Mode -> LogFilterModeMenu(
                    selected = mode,
                    onSelect = {
                        menuVisible = false
                        onModeChange(it)
                    },
                    modifier = Modifier.widthIn(max = 360.dp),
                )
                LogFilterMenu.Type -> LogFilterTypeMenu(
                    selected = selectedType,
                    onSelect = {
                        menuVisible = false
                        onTypeChange(it)
                    },
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            }
        }
    }
}

@Composable
internal fun LogFilterChip(
    rule: LogFilterRule,
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
            .clip(RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Text(
            text = rule.label(),
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
internal fun LogFilterModeSelector(
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
internal fun LogFilterModeMenu(
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
internal fun LogFilterTypeSelector(
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
        Text(selected.logFilterTypeLabel(), color = PortalColors.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Image(
            painter = rememberVectorPainter(if (expanded) PortalTabIcons.ArrowUp else PortalTabIcons.ArrowDown),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            colorFilter = ColorFilter.tint(PortalColors.muted),
        )
    }
}

@Composable
internal fun LogFilterTypeMenu(
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
        LogFilterTypes.forEach { option ->
            Text(
                text = option.logFilterTypeLabel(),
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
private fun LogsToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp, vertical = 5.dp)
            .width(1.dp)
            .height(18.dp)
            .background(PortalColors.border.copy(alpha = 0.85f)),
    )
}

@Composable
private fun LogsToolbarActionButton(
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
        val contentColor = when {
            active -> PortalColors.text
            enabled -> PortalColors.muted
            else -> PortalColors.muted.copy(alpha = 0.46f)
        }
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            colorFilter = ColorFilter.tint(contentColor),
        )
        Text(
            text = text,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private const val AndroidLogSource = "androidLog"

private val LogFilterTypes = listOf(
    "content",
    "tag",
    "level",
)

private enum class LogFilterMenu {
    Mode,
    Type,
}

internal data class LogFilterRule(
    val mode: NetworkFilterMode,
    val type: String,
    val value: String,
)

internal data class LogHighlightMatch(
    val query: String,
)

private fun List<LogFilterRule>.signature(): String =
    joinToString("\u001E") { "${it.mode.name}\u001F${it.type}\u001F${it.value}" }

private fun List<LogFilterRule>.matchesLogEntry(entry: PortalLogEntry): Boolean {
    val includeRules = rulesFor(NetworkFilterMode.Include)
    val excludeRules = rulesFor(NetworkFilterMode.Exclude)
    return includeRules.matchesGrouped(entry, emptyRulesMatch = true) &&
        !excludeRules.any { it.matches(entry) }
}

private fun List<LogFilterRule>.firstHighlightFor(entry: PortalLogEntry): LogHighlightMatch? =
    rulesFor(NetworkFilterMode.Highlight)
        .firstOrNull { it.matches(entry) }
        ?.let { LogHighlightMatch(query = it.value.trim()) }

private fun List<LogFilterRule>.rulesFor(mode: NetworkFilterMode): List<LogFilterRule> =
    filter { it.mode == mode && it.value.isNotBlank() }

private fun List<LogFilterRule>.matchesGrouped(
    entry: PortalLogEntry,
    emptyRulesMatch: Boolean,
): Boolean {
    if (isEmpty()) return emptyRulesMatch
    return groupBy { it.type }.all { (_, fieldRules) ->
        fieldRules.any { it.matches(entry) }
    }
}

private fun LogFilterRule.matches(entry: PortalLogEntry): Boolean {
    val needle = value.trim()
    if (needle.isBlank()) return true
    return when (type) {
        "content" -> entry.logContentFields().any { it.contains(needle, ignoreCase = true) }
        "tag" -> entry.tag.orEmpty().contains(needle, ignoreCase = true)
        "level" -> entry.level.contains(needle, ignoreCase = true) ||
            logLevelInitial(entry.level).equals(needle, ignoreCase = true)
        else -> false
    }
}

private fun PortalLogEntry.logContentFields(): List<String> =
    listOfNotNull(
        message,
        throwable,
        logLine(),
        threadName,
    ).filter { it.isNotBlank() }

private fun LogFilterRule.label(): String =
    "${mode.label} \"$value\" in ${type.logFilterTypeLabel()}"

private fun String.logFilterTypeLabel(): String =
    when (this) {
        "content" -> "content"
        "tag" -> "tag"
        "level" -> "level"
        else -> this
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
    highlight: LogHighlightMatch?,
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
    val rowBackground = if (highlight != null) {
        PortalColors.warning.copy(alpha = 0.10f)
    } else {
        Color.Transparent
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(rowBackground)
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
