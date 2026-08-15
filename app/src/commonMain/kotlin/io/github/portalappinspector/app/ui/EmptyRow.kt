package io.github.portalappinspector.app.ui

import androidx.compose.foundation.background
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EmptyRow(message: String) {
    Column(Modifier.Companion.fillMaxWidth()) {
        Box(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Companion.CenterStart,
        ) {
            Text(
                text = message,
                color = PortalColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        RowDivider()
    }
}


@Preview
@Composable
private fun EmptyRowPreview() {
    Box(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        EmptyRow(message = "No network requests found.")
    }
}
