package io.github.portalappinspector.app.features.network

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.Text
import io.github.portalappinspector.app.ui.icons.PortalTabIcons

@Composable
internal fun NetworkPanelToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp, vertical = 5.dp)
            .width(1.dp)
            .height(18.dp)
            .background(PortalColors.border.copy(alpha = 0.85f)),
    )
}

@Composable
internal fun NetworkDetailActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val active = enabled && hovered
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (active) PortalColors.button.copy(alpha = 0.72f) else Color.Transparent)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            colorFilter = ColorFilter.tint(
                when {
                    active -> PortalColors.text
                    enabled -> PortalColors.muted
                    else -> PortalColors.muted.copy(alpha = 0.46f)
                },
            ),
        )
        Text(
            text = text,
            color = when {
                active -> PortalColors.text
                enabled -> PortalColors.muted
                else -> PortalColors.muted.copy(alpha = 0.46f)
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
internal fun NetworkDetailIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (hovered) PortalColors.button.copy(alpha = 0.72f) else Color.Transparent)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            colorFilter = ColorFilter.tint(tint ?: if (hovered) PortalColors.text else PortalColors.muted),
        )
    }
}

@Preview
@Composable
private fun NetworkSharedUIPreview() {
    Row(
        modifier = Modifier.padding(16.dp).background(PortalColors.background).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetworkDetailActionButton(icon = PortalTabIcons.Mock, text = "Mock", onClick = {})
        NetworkPanelToolbarDivider()
        NetworkDetailIconButton(icon = PortalTabIcons.Close, onClick = {})
    }
}
