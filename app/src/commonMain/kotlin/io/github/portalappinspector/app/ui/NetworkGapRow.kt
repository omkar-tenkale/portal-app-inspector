package io.github.portalappinspector.app.ui

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.features.network.NetworkGapType
import io.github.portalappinspector.app.util.formatTimeHHMM

@Composable
internal fun NetworkGapRow(
    type: NetworkGapType,
    nowEpochMillis: Long,
    timezoneOffsetMinutes: Long,
    use12HourClock: Boolean,
) {
    val labelText = when (type) {
        NetworkGapType.OneMinuteAgo -> formatTimeHHMM(
            epochMillis = nowEpochMillis - 60_000L,
            timezoneOffsetMinutes = timezoneOffsetMinutes,
            use12HourClock = use12HourClock,
        )
        NetworkGapType.Yesterday -> "Yesterday"
        NetworkGapType.ALongTimeAgo -> "A long time ago"
    }

    Row(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 0.dp),
        verticalAlignment = Alignment.Companion.CenterVertically,
    ) {
        Spacer(Modifier.Companion.width(92.dp))
        Spacer(Modifier.Companion.width(10.dp))
        Box(
            modifier = Modifier.Companion
                .width(34.dp)
                .height(24.dp),
            contentAlignment = Alignment.Companion.Center,
        ) {
            Box(
                modifier = Modifier.Companion
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(PortalColors.border.copy(alpha = 0.55f)),
            )
        }
        Spacer(Modifier.Companion.width(12.dp))
        Text(
            text = labelText,
            color = PortalColors.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Companion.Medium,
            fontFamily = if (type == NetworkGapType.OneMinuteAgo) FontFamily.Companion.Monospace else FontFamily.Companion.SansSerif,
            maxLines = 1,
            modifier = Modifier.Companion.weight(1f),
        )
    }
}


@Preview
@Composable
private fun NetworkGapRowPreview() {
    Box(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        NetworkGapRow(
            type = NetworkGapType.Yesterday,
            nowEpochMillis = 1690000000000L,
            timezoneOffsetMinutes = 330L,
            use12HourClock = true
        )
    }
}
