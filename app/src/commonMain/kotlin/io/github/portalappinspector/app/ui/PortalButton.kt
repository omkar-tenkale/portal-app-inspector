package io.github.portalappinspector.app.ui

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PortalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.Companion,
    enabled: Boolean = true,
    kind: PortalButtonKind = PortalButtonKind.Secondary,
) {
    val primary = kind == PortalButtonKind.Primary
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> PortalColors.button.copy(alpha = 0.55f)
            primary -> PortalColors.success
            hovered -> PortalColors.button.copy(alpha = 0.82f)
            else -> PortalColors.button
        },
    )
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Companion.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> PortalColors.muted
                primary -> PortalColors.submitText
                else -> PortalColors.text
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Companion.Medium,
            maxLines = 1,
        )
    }
}

internal enum class PortalButtonKind {
    Primary,
    Secondary,
}


@Preview
@Composable
private fun PortalButtonPreview() {
    Row(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        PortalButton(text = "Cancel", kind = PortalButtonKind.Secondary, onClick = {})
        Spacer(Modifier.width(8.dp))
        PortalButton(text = "Submit", kind = PortalButtonKind.Primary, onClick = {})
    }
}
