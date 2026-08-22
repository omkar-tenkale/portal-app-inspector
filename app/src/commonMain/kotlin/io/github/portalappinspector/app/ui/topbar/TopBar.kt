package io.github.portalappinspector.app.ui.topbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.portalappinspector.PortalManifest
import io.github.portalappinspector.app.data.SavedPortalConnection
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.PortalButton
import io.github.portalappinspector.app.ui.PortalButtonKind
import io.github.portalappinspector.app.ui.Text
import io.github.portalappinspector.app.ui.icons.PortalTabIcons
import io.github.portalappinspector.app.util.toImageBitmapOrNull

@Composable
internal fun TopBar(
    manifest: PortalManifest?,
    savedConnections: List<SavedPortalConnection>,
    onSelectConnection: (SavedPortalConnection) -> Unit,
    connectionFailing: Boolean = false,
    onFixConnection: () -> Unit = {}
) {
    var infoPopupExpanded by remember { mutableStateOf(false) }
    var appSessionExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .zIndex(20f)
            .background(PortalColors.topBar),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PortalBrandIcon(onClick = {
                infoPopupExpanded = !infoPopupExpanded
                appSessionExpanded = false
            })
            TopBarVerticalDivider()
            AppSessionButton(
                manifest = manifest,
                expanded = appSessionExpanded,
                onClick = {
                    if (manifest != null || savedConnections.isNotEmpty()) {
                        appSessionExpanded = !appSessionExpanded
                        infoPopupExpanded = false
                    }
                },
            )
            TopBarVerticalDivider()
            if (connectionFailing) {
                Spacer(Modifier.width(8.dp))
                PortalButton(
                    text = "Connection issues (Fix)",
                    onClick = onFixConnection,
                    kind = PortalButtonKind.Secondary
                )
                Spacer(Modifier.width(8.dp))
                TopBarVerticalDivider()
            }
            Spacer(Modifier.weight(1f))
        }
        if (infoPopupExpanded) {
            AppInfoPopup(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 0.dp, y = 46.dp)
                    .zIndex(30f),
            )
        }
        if (appSessionExpanded) {
            AppSessionDropdown(
                savedConnections = savedConnections,
                currentPackageName = manifest?.appId,
                onSelectConnection = {
                    appSessionExpanded = false
                    onSelectConnection(it)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 62.dp, y = 46.dp)
                    .zIndex(30f),
            )
        }
    }
}

@Composable
internal fun PortalBrandIcon(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val hoverBackground by animateColorAsState(
        targetValue = if (hovered) PortalColors.button.copy(alpha = 0.56f) else Color.Transparent,
    )

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(46.dp)
            .background(hoverBackground)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        PortalMark(Modifier.fillMaxSize())
    }
}

@Composable
internal fun TopBarVerticalDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .padding(vertical = 8.dp)
            .background(PortalColors.border.copy(alpha = 0.68f)),
    )
}

@Composable
internal fun AppSessionButton(
    manifest: PortalManifest?,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val hoverBackground by animateColorAsState(
        targetValue = if (hovered) PortalColors.button.copy(alpha = 0.56f) else Color.Transparent,
    )

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .background(hoverBackground)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        AppIcon(
            iconPngBase64 = manifest?.appIconPngBase64,
            fallbackText = manifest?.appName ?: "Portal App Inspector",
            modifier = Modifier.size(32.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = manifest?.appName ?: "Portal App Inspector",
                color = PortalColors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(if (manifest == null) 160.dp else 180.dp),
            )
            manifest?.appId?.let {
                Text(
                    text = it,
                    color = PortalColors.muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(180.dp),
                )
            }
        }
        Box(
            modifier = Modifier.size(width = 32.dp, height = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            DropdownChevron(
                expanded = expanded,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(14.dp),
            )
        }
    }
}

@Composable
internal fun AppSessionDropdown(
    savedConnections: List<SavedPortalConnection>,
    currentPackageName: String?,
    onSelectConnection: (SavedPortalConnection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(340.dp)
            .background(PortalColors.card, RoundedCornerShape(7.dp))
            .border(1.dp, PortalColors.border, RoundedCornerShape(7.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (savedConnections.isEmpty()) {
            Text(
                text = "No saved sessions",
                color = PortalColors.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(10.dp),
            )
        }
        savedConnections.forEach { saved ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (saved.appId == currentPackageName) PortalColors.background else PortalColors.card,
                        RoundedCornerShape(5.dp),
                    )
                    .clickable { onSelectConnection(saved) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                AppIcon(
                    iconPngBase64 = saved.appIconPngBase64,
                    fallbackText = saved.appName,
                    modifier = Modifier.size(24.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = saved.appName,
                        color = PortalColors.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${saved.connection.host}:${saved.connection.port} - ${saved.appId}",
                        color = PortalColors.muted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (saved.appId == currentPackageName) {
                    Text("Active", color = PortalColors.success, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun AppInfoPopup(
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(13.dp)
    Column(
        modifier = modifier
            .wrapContentSize(align = Alignment.TopStart, unbounded = true)
            .requiredWidth(360.dp)
            .requiredHeight(372.dp)
            .shadow(24.dp, shape, clip = false)
            .background(PortalColors.popup, shape)
            .border(1.dp, PortalColors.border, shape)
            .clip(shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 1.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                PortalMark(Modifier.size(42.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Portal App inspector",
                    color = PortalColors.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    lineHeight = 21.sp,
                )
                Text(
                    text = "A modern, powerful inspection tool for developers and testers",
                    color = PortalColors.accent,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PopupMenuItem(PortalTabIcons.Edit, "Try New Features")
            PopupMenuItem(PortalTabIcons.CopyPath, "Orders")
            PopupMenuItem(PortalTabIcons.Plus, "My Playgrounds")
            PopupMenuItem(PortalTabIcons.Settings, "Settings")
            PopupMenuItem(PortalTabIcons.ArrowRight, "Appearance")
            PopupMenuItem(PortalTabIcons.Unsupported, "Sign Out")
        }
    }
}

@Composable
internal fun PopupMenuDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(PortalColors.border.copy(alpha = 0.72f)),
    )
}

@Composable
internal fun PopupMenuItem(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(PortalColors.muted),
        )
        Text(
            text = label,
            color = PortalColors.text,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun AppIcon(
    iconPngBase64: String?,
    fallbackText: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(7.dp)
    val image = remember(iconPngBase64) { iconPngBase64?.toImageBitmapOrNull() }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .clip(shape)
                .background(PortalColors.background, shape)
                .border(1.dp, PortalColors.border, shape),
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(PortalColors.background, shape)
                .border(1.dp, PortalColors.border, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = fallbackText.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = PortalColors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun AppIconPlaceholder(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Canvas(
        modifier = modifier
            .clip(shape)
            .background(PortalColors.background, shape)
            .border(1.dp, PortalColors.border, shape)
            .padding(12.dp),
    ) {
        val strokeWidth = 1.7.dp.toPx()
        val corner = size.minDimension * 0.18f
        drawRoundRect(
            color = PortalColors.muted.copy(alpha = 0.14f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        )
        drawRoundRect(
            color = PortalColors.muted.copy(alpha = 0.72f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = PortalColors.muted.copy(alpha = 0.42f),
            start = Offset(size.width * 0.26f, size.height * 0.36f),
            end = Offset(size.width * 0.74f, size.height * 0.36f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = PortalColors.muted.copy(alpha = 0.42f),
            start = Offset(size.width * 0.30f, size.height * 0.58f),
            end = Offset(size.width * 0.62f, size.height * 0.58f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
internal fun DropdownChevron(expanded: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.8.dp.toPx()
        val left = Offset(size.width * 0.26f, size.height * if (expanded) 0.62f else 0.38f)
        val center = Offset(size.width * 0.5f, size.height * if (expanded) 0.38f else 0.62f)
        val right = Offset(size.width * 0.74f, size.height * if (expanded) 0.62f else 0.38f)
        drawLine(
            color = PortalColors.muted,
            start = left,
            end = center,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = PortalColors.muted,
            start = center,
            end = right,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
internal fun PortalMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val scale = size.minDimension / 512f
        fun x(value: Float) = value * scale
        fun y(value: Float) = value * scale

        fun drawPortalOval(
            centerX: Float,
            centerY: Float,
            radiusX: Float,
            radiusY: Float,
            rotation: Float,
            color: Color,
            strokeWidth: Float,
        ) {
            rotate(
                degrees = rotation,
                pivot = androidx.compose.ui.geometry.Offset(x(centerX), y(centerY)),
            ) {
                drawOval(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(x(centerX - radiusX), y(centerY - radiusY)),
                    size = androidx.compose.ui.geometry.Size(x(radiusX * 2), y(radiusY * 2)),
                    style = Stroke(width = x(strokeWidth)),
                )
            }
        }

        drawRoundRect(
            color = PortalColors.topBar,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(x(88f), y(88f)),
        )
        drawPortalOval(216f, 256f, 96f, 140f, 15f, PortalColors.text, 26f)
        drawPortalOval(316f, 256f, 76f, 110f, 30f, PortalColors.logo, 26f)
        clipRect(top = 0f, bottom = y(256f)) {
            drawPortalOval(216f, 256f, 96f, 140f, 15f, PortalColors.topBar, 52f)
            drawPortalOval(216f, 256f, 96f, 140f, 15f, PortalColors.text, 26f)
        }
        clipRect(top = y(256f), bottom = y(512f)) {
            drawPortalOval(316f, 256f, 76f, 110f, 30f, PortalColors.topBar, 52f)
            drawPortalOval(316f, 256f, 76f, 110f, 30f, PortalColors.logo, 26f)
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview
@Composable
private fun TopBarPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(PortalColors.background)
    ) {
        TopBar(
            manifest = null,
            savedConnections = emptyList(),
            onSelectConnection = {}
        )
    }
}
