package io.github.portalappinspector.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import kotlinx.coroutines.delay
import androidx.compose.ui.input.key.type
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import io.github.portalappinspector.app.util.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal enum class PortalButtonKind {
    Primary,
    Secondary,
}

@Composable
internal fun PortalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> PortalColors.muted
                primary -> PortalColors.submitText
                else -> PortalColors.text
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PortalColors.text,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = FontFamily.SansSerif,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            lineHeight = lineHeight,
            textAlign = textAlign ?: TextAlign.Unspecified,
        ),
        overflow = overflow,
        maxLines = maxLines,
    )
}

@Composable
internal fun PortalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = TextStyle(
        color = PortalColors.text,
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
    ),
    invalid: Boolean = false,
) {
    val borderColor by animateColorAsState(
        if (invalid) PortalColors.error.copy(alpha = 0.74f) else PortalColors.inputBorder,
    )
    val backgroundColor by animateColorAsState(
        if (invalid) PortalColors.error.copy(alpha = 0.08f) else PortalColors.input,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = textStyle,
        cursorBrush = SolidColor(PortalColors.accent),
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp)),
        decorationBox = { innerTextField ->
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        color = PortalColors.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            color = PortalColors.muted.copy(alpha = 0.72f),
                            fontSize = textStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 13.sp,
                            fontFamily = textStyle.fontFamily ?: FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
internal fun PortalCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(if (checked) PortalColors.success else PortalColors.button)
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .border(1.dp, if (checked) PortalColors.success else PortalColors.border, RoundedCornerShape(4.dp))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Canvas(Modifier.size(12.dp)) {
                drawLine(
                    color = PortalColors.submitText,
                    start = Offset(size.width * 0.18f, size.height * 0.54f),
                    end = Offset(size.width * 0.42f, size.height * 0.78f),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = PortalColors.submitText,
                    start = Offset(size.width * 0.42f, size.height * 0.78f),
                    end = Offset(size.width * 0.84f, size.height * 0.22f),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
internal fun PortalSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
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
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .background(if (checked) PortalColors.submitText else PortalColors.muted, RoundedCornerShape(7.dp)),
        )
    }
}

@Composable
internal fun PortalVectorIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = PortalColors.text,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor by animateColorAsState(
        if (enabled && hovered) PortalColors.button.copy(alpha = 0.82f) else Color.Transparent,
    )
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

@Composable
internal fun NewBadge() {
    Text(
        text = "NEW",
        color = PortalColors.background,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .background(PortalColors.accent, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
internal fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(PortalColors.border.copy(alpha = 0.65f)),
    )
}

@Composable
internal fun PulsatingDots(
    modifier: Modifier = Modifier,
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

@Composable
internal fun UnsupportedPluginPanel(pluginId: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        StatusCard(pluginId, "Installed in Source, but this Portal UI does not support it yet.", PortalColors.warning)
    }
}

@Composable
internal fun StatusCard(title: String, message: String, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PortalColors.card, RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(12.dp),
    ) {
        Text(title, color = color, fontWeight = FontWeight.SemiBold)
        Text(message, color = PortalColors.muted, fontSize = 13.sp)
    }
}

@Composable
internal fun SearchIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension / 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
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
            cap = StrokeCap.Round,
        )
    }
}

@Composable
internal fun InlineConditionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (() -> Unit)? = null,
    showUnderline: Boolean = false,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = PortalColors.text,
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp,
        ),
        cursorBrush = SolidColor(PortalColors.accent),
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && onSubmit != null) {
                    onSubmit()
                    true
                } else {
                    false
                }
            },
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty() || showUnderline) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .align(Alignment.BottomStart)
                            .background(PortalColors.muted.copy(alpha = 0.62f)),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
internal fun LogGapRow(durationMillis: Long) {
    val seconds = (durationMillis / 1_000L).coerceAtLeast(1L)
    val markerHeight = (24 + (seconds.coerceAtMost(60L) * 34L / 60L).toInt()).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(92.dp))
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(markerHeight),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(1.dp)
                    .height(markerHeight)
                    .background(PortalColors.border.copy(alpha = 0.55f)),
            )
            TimelineGapBars(seconds)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatLogGap(durationMillis),
            color = PortalColors.muted,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun TimelineGapBars(seconds: Long) {
    val segmentHeight = (6 + (seconds.coerceAtMost(60L) * 16L / 60L).toInt()).dp
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(segmentHeight)
                .background(PortalColors.border.copy(alpha = 0.9f), RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
internal fun EmptyRow(message: String) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = message,
                color = PortalColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        RowDivider()
    }
}
