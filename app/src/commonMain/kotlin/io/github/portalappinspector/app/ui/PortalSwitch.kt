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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun PortalSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier.Companion,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val trackColor by animateColorAsState(
        when {
            checked -> PortalColors.success
            hovered -> PortalColors.button.copy(alpha = 0.82f)
            else -> PortalColors.button
        },
    )
    val borderColor by animateColorAsState(if (checked) PortalColors.success else PortalColors.border)
    Box(
        modifier = modifier
            .size(width = 34.dp, height = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(trackColor)
            .border(1.dp, borderColor, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier.Companion
                .size(14.dp)
                .align(if (checked) Alignment.Companion.CenterEnd else Alignment.Companion.CenterStart)
                .background(
                    if (checked) PortalColors.submitText else PortalColors.muted,
                    androidx.compose.foundation.shape.RoundedCornerShape(7.dp)
                ),
        )
    }
}


@Preview
@Composable
private fun PortalSwitchPreview() {
    Box(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        PortalSwitch(checked = true, onCheckedChange = {})
    }
}
