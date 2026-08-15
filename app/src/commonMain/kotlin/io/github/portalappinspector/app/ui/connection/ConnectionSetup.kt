package io.github.portalappinspector.app.ui.connection

import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.graphicsLayer
import io.github.portalappinspector.app.data.*
import io.github.portalappinspector.app.ui.*
import io.github.portalappinspector.app.ui.icons.*
import io.github.portalappinspector.app.ui.toast.*
import io.github.portalappinspector.app.ui.topbar.*
import io.github.portalappinspector.app.util.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun WelcomeConnectionPanel(
    connection: PortalConnection,
    appIconPngBase64: String?,
    animateAppIconReveal: Boolean,
    showSetupRequired: Boolean,
    isEmulator: Boolean,
) {
    val appIconScale = remember { Animatable(1f) }
    val appIconAlpha by animateFloatAsState(
        targetValue = if (appIconPngBase64 == null) 0f else 1f,
        animationSpec = tween(180),
    )
    val placeholderScale by animateFloatAsState(
        targetValue = if (appIconPngBase64 == null) 1f else 0.82f,
        animationSpec = tween(220),
    )
    val placeholderAlpha by animateFloatAsState(
        targetValue = if (appIconPngBase64 == null) 1f else 0f,
        animationSpec = tween(180),
    )
    val showSetupChrome = showSetupRequired && connection.isValid
    val setupScale by animateFloatAsState(
        targetValue = if (showSetupChrome) 1f else 0.96f,
        animationSpec = tween(260),
    )

    LaunchedEffect(appIconPngBase64, animateAppIconReveal) {
        if (appIconPngBase64 == null || !animateAppIconReveal) {
            appIconScale.snapTo(1f)
        } else {
            appIconScale.snapTo(1.28f)
            appIconScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(520),
            )
        }
    }

    Column(
        modifier = Modifier
            .width(520.dp)
            .graphicsLayer {
                scaleX = setupScale
                scaleY = setupScale
            }
            .background(if (showSetupChrome) PortalColors.card else Color.Transparent, RoundedCornerShape(8.dp))
            .border(1.dp, if (showSetupChrome) PortalColors.border else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            PortalMark(Modifier.size(42.dp))
            PulsatingDots(
                color = PortalColors.accent,
                dotDiameter = 7.dp,
                horizontalSpace = 6.dp,
                modifier = Modifier.size(width = 36.dp, height = 22.dp),
            )
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppIconPlaceholder(
                    Modifier
                        .size(42.dp)
                        .graphicsLayer {
                            alpha = placeholderAlpha
                            scaleX = placeholderScale
                            scaleY = placeholderScale
                        },
                )
                if (appIconPngBase64 != null) {
                    AppIcon(
                        iconPngBase64 = appIconPngBase64,
                        fallbackText = "",
                        modifier = Modifier
                            .size(42.dp)
                            .graphicsLayer {
                                alpha = appIconAlpha
                                scaleX = appIconScale.value
                                scaleY = appIconScale.value
                            },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = showSetupRequired && connection.isValid,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(120)),
        ) {
            ConnectionSetupSteps(
                connection = connection,
                isEmulator = isEmulator,
            )
        }
        AnimatedVisibility(
            visible = !connection.isValid,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(120)),
        ) {
            Text(
                text = "Open Portal from a source app to start an inspection session.",
                color = PortalColors.muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun MobileConnectionState(
    connecting: Boolean,
    error: String?,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            connecting -> PulsatingDots(
                color = PortalColors.accent,
                modifier = Modifier.size(width = 48.dp, height = 18.dp),
            )

            error != null -> Text(
                text = error,
                color = PortalColors.error,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(16.dp),
            )

            else -> Text(
                text = "No source connected",
                color = PortalColors.muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
internal fun ConnectionSetupSteps(
    connection: PortalConnection,
    isEmulator: Boolean,
) {
    var networkIssuesExpanded by remember { mutableStateOf(false) }
    var troubleshootingVisible by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(260))
            .padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "Setup required",
                color = PortalColors.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${connection.host}:${connection.port} is not reachable.",
                color = PortalColors.muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        SetupRule()
        if (troubleshootingVisible) {
            TroubleshootConnectionContent(
                onBack = { troubleshootingVisible = false },
            )
        } else {
            if (isEmulator) {
                SetupOptionCard(
                    icon = PortalTabIcons.Usb,
                    title = "Use ADB forwarding",
                    body = "Run ADB forwarding for the emulator, then keep this page open while Portal reconnects.",
                ) {
                    CopyCommandRow(connection.adbForwardCommand())
                }
            } else {
                SetupOptionCard(
                    icon = PortalTabIcons.Wifi,
                    title = "Connect over Wi-Fi",
                    body = "Ensure your computer and Android device are on the same wifi network.",
                    expanded = networkIssuesExpanded,
                    onToggleExpanded = { networkIssuesExpanded = !networkIssuesExpanded },
                )
                SetupOptionCard(
                    icon = PortalTabIcons.Usb,
                    title = "Use ADB forwarding",
                    body = "If device is connected with USB debugging enabled, forward the port directly.",
                ) {
                    CopyCommandRow(connection.adbForwardCommand())
                }
            }
            QuietTextButton(
                text = "Need help?",
                onClick = { troubleshootingVisible = true },
            )
        }
    }
}

@Composable
internal fun TroubleshootConnectionContent(
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        QuietTextButton(
            text = "Go back",
            onClick = onBack,
        )
        Text(
            text = "Hello world",
            color = PortalColors.text,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SetupOptionCard(
    icon: ImageVector,
    title: String,
    body: String,
    expanded: Boolean = false,
    onToggleExpanded: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PortalColors.card, RoundedCornerShape(8.dp))
            .border(1.dp, PortalColors.border, RoundedCornerShape(8.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(24.dp),
            colorFilter = ColorFilter.tint(PortalColors.accent),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    color = PortalColors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp,
                )
                Text(
                    text = body,
                    color = PortalColors.muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            if (onToggleExpanded != null) {
                SetupRule()
                ConnectionIssuesToggle(
                    expanded = expanded,
                    onClick = onToggleExpanded,
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(animationSpec = tween(180)),
                    exit = fadeOut(animationSpec = tween(120)),
                ) {
                    Text(
                        text = "If network blocks traffic between devices, connect your computer to your phone's hotspot.",
                        color = PortalColors.muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(start = 26.dp),
                    )
                }
            }
            content?.invoke()
        }
    }
}

@Composable
internal fun ConnectionIssuesToggle(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DropdownChevron(
            expanded = expanded,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Having connection issues?",
            color = PortalColors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun SetupRule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(PortalColors.border.copy(alpha = 0.65f)),
    )
}

@Composable
internal fun CopyCommandRow(
    command: String,
    modifier: Modifier = Modifier,
) {
    val toastHost = LocalToastHost.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PortalColors.background, RoundedCornerShape(7.dp))
            .border(1.dp, PortalColors.border, RoundedCornerShape(7.dp))
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = command,
            color = PortalColors.text,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        QuietTextButton(
            text = "Copy",
            icon = null,
            onClick = {
                copyTextToClipboard(command)
                toastHost.show("Command copied", ToastKind.Info, durationMillis = 1_600L)
            },
        )
    }
}

@Composable
internal fun QuietTextButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor by animateColorAsState(
        targetValue = if (hovered) PortalColors.button.copy(alpha = 0.62f) else Color.Transparent,
    )
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (icon != null) {
                Image(
                    painter = rememberVectorPainter(icon),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(PortalColors.muted),
                )
            }
            Text(
                text = text,
                color = PortalColors.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
