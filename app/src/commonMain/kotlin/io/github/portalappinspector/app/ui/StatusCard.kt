package io.github.portalappinspector.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StatusCard(title: String, message: String, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PortalColors.card)
            .border(1.dp, PortalColors.border)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status Indicator Dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )

            Text(
                text = title,
                color = PortalColors.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        Text(
            text = message,
            color = PortalColors.muted,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Preview
@Composable
private fun StatusCardPreview() {
    Box(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        StatusCard(
            title = "Connection Lost",
            message = "The application disconnected from the portal. Please ensure the target process is still running.",
            color = PortalColors.error
        )
    }
}