package io.github.portalappinspector.app.features.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.Text
import io.github.portalappinspector.app.util.formatAbsoluteLogTime
import io.github.portalappinspector.app.util.formatRelativeLogTime

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

@Preview
@Composable
private fun LogEntryRowPreview() {
    val sampleEntry = PortalLogEntry(
        id = 1L,
        timestampEpochMillis = 1700000000000L,
        level = "info",
        source = "androidLog",
        stream = "main",
        tag = "AppTag",
        message = "This is a sample log message",
        throwable = null,
        threadName = "main",
        location = PortalLogCallSite("com.example.MainActivity", "onCreate", "MainActivity.kt", 42),
        callSites = emptyList()
    )
    Box(Modifier.background(PortalColors.background).padding(16.dp)) {
        LogEntryRow(
            entry = sampleEntry,
            nowEpochMillis = 1700000000000L,
            timezoneOffsetMinutes = 0L,
            use12HourClock = false,
            highlight = null
        )
    }
}
