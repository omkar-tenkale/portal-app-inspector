package io.github.portalappinspector.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun RowDivider() {
    Box(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .height(1.dp)
            .background(PortalColors.border.copy(alpha = 0.65f)),
    )
}


@Preview
@Composable
private fun RowDividerPreview() {
    Box(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        RowDivider()
    }
}
