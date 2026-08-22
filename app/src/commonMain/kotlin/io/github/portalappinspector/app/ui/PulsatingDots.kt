package io.github.portalappinspector.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun PulsatingDots(
    modifier: Modifier = Modifier.Companion,
    color: Color = PortalColors.text,
    dotDiameter: Dp = 6.dp,
    horizontalSpace: Dp = 5.dp,
    animationDurationMillis: Int = 600,
    minScale: Float = 0.35f,
    maxScale: Float = 1f,
) {
    val dotsCount = 3
    val scales = (0 until dotsCount).map { index ->
        var scale by remember(index) { mutableStateOf(maxScale) }

        LaunchedEffect(index, animationDurationMillis, minScale, maxScale) {
            delay(animationDurationMillis / dotsCount * index.toLong())
            animate(
                initialValue = minScale,
                targetValue = maxScale,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = animationDurationMillis,
                        easing = LinearEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
            ) { value, _ ->
                scale = value
            }
        }
        scale
    }

    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        val dotPx = with(density) { dotDiameter.toPx() }
        val spacePx = with(density) { horizontalSpace.toPx() }
        val totalWidth = dotsCount * dotPx + (dotsCount - 1) * spacePx
        val startX = (size.width - totalWidth) / 2f + dotPx / 2f
        val centerY = size.height / 2f

        for (index in 0 until dotsCount) {
            drawCircle(
                color = color,
                radius = (dotPx / 2f) * scales[index],
                center = Offset(
                    x = startX + index * (dotPx + spacePx),
                    y = centerY,
                ),
            )
        }
    }
}


@Preview
@Composable
private fun PulsatingDotsPreview() {
    Box(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        PulsatingDots(color = PortalColors.accent)
    }
}
