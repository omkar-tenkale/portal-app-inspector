package io.github.portalappinspector.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun SearchIcon(
    color: Color,
    modifier: Modifier = Modifier.Companion,
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(
            width = size.minDimension / 8f,
            cap = StrokeCap.Companion.Round,
            join = StrokeJoin.Companion.Round
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.28f,
            center = Offset(size.width * 0.42f, size.height * 0.42f),
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.64f, size.height * 0.64f),
            end = Offset(size.width * 0.9f, size.height * 0.9f),
            strokeWidth = size.minDimension / 8f,
            cap = StrokeCap.Companion.Round,
        )
    }
}


@Preview
@Composable
private fun SearchIconPreview() {
    Box(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        SearchIcon(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
    }
}
