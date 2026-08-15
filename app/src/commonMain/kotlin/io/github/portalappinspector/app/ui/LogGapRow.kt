package io.github.portalappinspector.app.ui

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.util.formatLogGap

@Composable
internal fun LogGapRow(durationMillis: Long) {
    val seconds = (durationMillis / 1_000L).coerceAtLeast(1L)
    val markerHeight = (24 + (seconds.coerceAtMost(60L) * 34L / 60L).toInt()).dp
    Row(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(end = 6.dp),
        verticalAlignment = Alignment.Companion.CenterVertically,
    ) {
        Spacer(Modifier.Companion.width(92.dp))
        Spacer(Modifier.Companion.width(10.dp))
        Box(
            modifier = Modifier.Companion
                .width(34.dp)
                .height(markerHeight),
        ) {
            Box(
                modifier = Modifier.Companion
                    .align(Alignment.Companion.Center)
                    .width(1.dp)
                    .height(markerHeight)
                    .background(PortalColors.border.copy(alpha = 0.55f)),
            )
            TimelineGapBars(seconds)
        }
        Spacer(Modifier.Companion.width(12.dp))
        Text(
            text = formatLogGap(durationMillis),
            color = PortalColors.muted,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.Companion.weight(1f),
        )
    }
}


@Preview
@Composable
private fun LogGapRowPreview() {
    Box(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        LogGapRow(durationMillis = 15000L)
    }
}
