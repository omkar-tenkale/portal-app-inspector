package io.github.portalappinspector.app.ui.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import io.github.docklayout.DockLayoutTab
import io.github.portalappinspector.app.features.files.PortalFileItem
import io.github.portalappinspector.app.features.network.PortalNetworkCall
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.icons.PortalTabIcons

internal sealed interface PortalTab : DockLayoutTab

internal data object FilesTab : PortalTab {
    override val title: String = "Files"
}

internal data object NetworkTab : PortalTab {
    override val title: String = "Network"
}

internal data object LogsTab : PortalTab {
    override val title: String = "Logs"
}

internal data object ScreenMirrorTab : PortalTab {
    override val title: String = "Screen Mirror"
}

internal data class NetworkResponseTab(
    val call: PortalNetworkCall,
) : PortalTab {
    override val title: String = "Response #${call.id}"
}

internal data class SharedPrefsTab(
    val file: PortalFileItem,
) : PortalTab {
    override val title: String = file.name.removeSuffix(".xml")
}

internal data class UnsupportedPluginTab(
    val pluginId: String,
) : PortalTab {
    override val title: String = pluginId
}

@Composable
internal fun PortalTabIcon(tab: PortalTab, selected: Boolean) {
    val (icon, color) = when (tab) {
        FilesTab -> PortalTabIcons.Folder to PortalColors.accent
        NetworkTab -> PortalTabIcons.Network to PortalColors.success
        LogsTab -> PortalTabIcons.Logs to PortalColors.warning
        ScreenMirrorTab -> PortalTabIcons.ScreenMirror to PortalColors.accent
        is NetworkResponseTab -> PortalTabIcons.Response to PortalColors.text
        is SharedPrefsTab -> PortalTabIcons.SharedPrefs to PortalColors.accent
        is UnsupportedPluginTab -> PortalTabIcons.Unsupported to PortalColors.warning
    }
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        modifier = Modifier.size(14.dp),
        colorFilter = ColorFilter.tint(if (selected) color else color.copy(alpha = 0.58f)),
    )
}
