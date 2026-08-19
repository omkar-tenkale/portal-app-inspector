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

@Preview
@Composable
private fun NetworkCallRowPreview() {
    val call = io.github.portalappinspector.app.PreviewFixtures.dummyNetworkCall
    Box(Modifier.background(PortalColors.background).padding(16.dp)) {
        NetworkCallRow(
            call = call, nowEpochMillis = 1690000000000L, timezoneOffsetMinutes = 0L, use12HourClock = false, highlight = null, onViewBody = {}
        )
    }
}
