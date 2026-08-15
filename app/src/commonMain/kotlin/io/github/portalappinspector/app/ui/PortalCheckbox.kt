package io.github.portalappinspector.app.ui

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
internal fun PortalCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier.Companion,
) {
    val color by animateColorAsState(if (checked) PortalColors.success else PortalColors.button)
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .border(
                1.dp,
                if (checked) PortalColors.success else PortalColors.border,
                androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Companion.Center,
    ) {
        if (checked) {
            Canvas(Modifier.Companion.size(12.dp)) {
                drawLine(
                    color = PortalColors.submitText,
                    start = Offset(size.width * 0.18f, size.height * 0.54f),
                    end = Offset(size.width * 0.42f, size.height * 0.78f),
                    strokeWidth = 2f,
                    cap = StrokeCap.Companion.Round,
                )
                drawLine(
                    color = PortalColors.submitText,
                    start = Offset(size.width * 0.42f, size.height * 0.78f),
                    end = Offset(size.width * 0.84f, size.height * 0.22f),
                    strokeWidth = 2f,
                    cap = StrokeCap.Companion.Round,
                )
            }
        }
    }
}


@Preview
@Composable
private fun PortalCheckboxPreview() {
    Box(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        PortalCheckbox(checked = true, onCheckedChange = {})
    }
}
