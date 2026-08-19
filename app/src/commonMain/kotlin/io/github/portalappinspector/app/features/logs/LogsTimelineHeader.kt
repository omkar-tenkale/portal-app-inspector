package io.github.portalappinspector.app.features.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.PulsatingDots
import io.github.portalappinspector.app.ui.Text

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

@Preview
@Composable
private fun LogsTimelineHeaderPreview() {
    Box(Modifier.background(PortalColors.background).padding(16.dp)) {
        Column {
            LogsTimelineHeader(loading = true)
            Spacer(Modifier.height(16.dp))
            LogsTimelineHeader(loading = false)
        }
    }
}
