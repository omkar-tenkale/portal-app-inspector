package io.github.portalappinspector.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun TimelineGapBars(seconds: Long) {
    val segmentHeight = (6 + (seconds.coerceAtMost(60L) * 16L / 60L).toInt()).dp
    Box(
        modifier = Modifier.Companion.fillMaxSize(),
        contentAlignment = Alignment.Companion.Center,
    ) {
        Box(
            modifier = Modifier.Companion
                .width(6.dp)
                .height(segmentHeight)
                .background(PortalColors.border.copy(alpha = 0.9f), RoundedCornerShape(4.dp)),
        )
    }
}


@Preview
@Composable
private fun TimelineGapBarsPreview() {
    Box(modifier = Modifier.padding(16.dp).height(50.dp).background(PortalColors.background)) {
        TimelineGapBars(seconds = 45L)
    }
}
