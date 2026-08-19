package io.github.portalappinspector.app.features.network

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.focus.*
import kotlinx.coroutines.*
import io.github.portalappinspector.app.data.*
import io.github.portalappinspector.app.ui.*
import io.github.portalappinspector.app.ui.icons.*
import io.github.portalappinspector.app.util.*
import kotlinx.serialization.json.*

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
