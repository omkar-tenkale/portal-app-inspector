package io.github.portalappinspector.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun UnsupportedPluginPanel(pluginId: String) {
    Box(Modifier.Companion.fillMaxSize(), contentAlignment = Alignment.Companion.Center) {
        StatusCard(
            pluginId,
            "Installed in Source, but this Portal UI does not support it yet.",
            PortalColors.warning
        )
    }
}


@Preview
@Composable
private fun UnsupportedPluginPanelPreview() {
    Box(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        UnsupportedPluginPanel(pluginId = "inspector.database")
    }
}
