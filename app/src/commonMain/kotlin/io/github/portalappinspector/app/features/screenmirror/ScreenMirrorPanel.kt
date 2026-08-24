package io.github.portalappinspector.app.features.screenmirror

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.data.PortalApi
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.PulsatingDots
import io.github.portalappinspector.app.ui.ResponsiveRow
import io.github.portalappinspector.app.ui.RowDivider
import io.github.portalappinspector.app.ui.StatusCard
import io.github.portalappinspector.app.ui.Text
import io.github.portalappinspector.app.util.formatSize
import io.github.portalappinspector.app.util.toImageBitmapOrNull
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

internal const val ScreenMirrorPluginId = "portal-screen-mirror"

@Composable
internal fun ScreenMirrorPanel(
    api: PortalApi,
    focused: Boolean,
) {
    val enabled = api.hasPlugin(ScreenMirrorPluginId)
    var frame by remember(api) { mutableStateOf<ScreenMirrorFrameMetadata?>(null) }
    var frameBytes by remember(api) { mutableStateOf<ByteArray?>(null) }
    var error by remember(api) { mutableStateOf<String?>(null) }
    var displayedError by remember(api) { mutableStateOf<String?>(null) }
    var isReconnecting by remember(api) { mutableStateOf(false) }
    var inputConnected by remember(api) { mutableStateOf(false) }

    var pointerPosition by remember { mutableStateOf<Offset?>(null) }
    var isPointerPressed by remember { mutableStateOf(false) }

    val fps = if (pointerPosition != null || isPointerPressed) 30 else 2
    val bitmap = remember(frameBytes) { frameBytes?.toImageBitmapOrNull() }
    val touchEvents = remember(api) { Channel<ScreenMirrorTouchEvent>(Channel.UNLIMITED) }

    fun queueTouch(action: String, position: Offset, size: IntSize) {
        if (!enabled || !inputConnected || size.width <= 0 || size.height <= 0) return
        val normalized = position.normalizedInFittedFrame(
            containerSize = size,
            frame = frame,
            clampOutsideFrame = action != "down",
        ) ?: return
        touchEvents.trySend(
            ScreenMirrorTouchEvent(
                action = action,
                x = normalized.x,
                y = normalized.y,
            )
        )
    }

    // Increased error overlay threshold (1.5s) to eliminate quick status flashes
    LaunchedEffect(error) {
        if (error != null) {
            delay(1500L)
            displayedError = error
        } else {
            displayedError = null
        }
    }

    LaunchedEffect(enabled, api, fps) {
        if (!enabled) return@LaunchedEffect

        while (true) {
            runCatching {
                api.webSocket(ScreenMirrorPluginId, "frames", fps.let { "scale=0.5&maxFps=$it" }) {
                    var pendingFrame: ScreenMirrorFrameMetadata? = null
                    isReconnecting = false
                    error = null

                    for (message in incoming) {
                        when (message) {
                            is Frame.Text -> {
                                pendingFrame = parseScreenMirrorFrameMetadata(message.readText())
                            }
                            is Frame.Binary -> {
                                val metadata = pendingFrame
                                if (metadata != null) {
                                    frame = metadata
                                    frameBytes = message.readBytes()
                                    pendingFrame = null
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }.onFailure { throwable ->
                isReconnecting = true
                error = throwable.message ?: throwable::class.simpleName
            }
            delay(500L)
        }
    }

    LaunchedEffect(enabled, api) {
        if (!enabled) return@LaunchedEffect

        while (true) {
            runCatching {
                api.webSocket(ScreenMirrorPluginId, "input") {
                    inputConnected = true
                    try {
                        for (touch in touchEvents) {
                            outgoing.send(Frame.Binary(true, touch.toBinaryPacket()))
                        }
                    } finally {
                        inputConnected = false
                    }
                }
            }.onFailure {
                inputConnected = false
            }
            delay(500L)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!enabled) {
            StatusCard("Screen Mirror plugin unavailable", "Install the portal-screen-mirror plugin in the source app.", PortalColors.warning)
            return@Column
        }

        ScreenMirrorHeader(
            frame = frame,
            fps = fps,
            isReconnecting = isReconnecting,
        )
        RowDivider()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Main Mirror Surface
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black)
                    .border(1.dp, PortalColors.border, RoundedCornerShape(6.dp))
                    .pointerHoverIcon(if (pointerPosition != null) PointerIcon.Crosshair else PointerIcon.Default)
                    .pointerInput(enabled, api, bitmap != null) {
                        if (!enabled || bitmap == null) return@pointerInput
                        var activePointerId: PointerId? = null
                        var lastPosition = Offset.Zero
                        var lastSize = IntSize.Zero
                        try {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val bounds = size
                                    val firstChange = event.changes.firstOrNull()
                                    if (firstChange != null) {
                                        val normalized = firstChange.position.normalizedInFittedFrame(bounds, frame, clampOutsideFrame = false)
                                        if (normalized != null && event.type != androidx.compose.ui.input.pointer.PointerEventType.Exit) {
                                            pointerPosition = firstChange.position
                                        } else {
                                            pointerPosition = null
                                        }
                                    }
                                    val activeChange = activePointerId?.let { id ->
                                        event.changes.firstOrNull { it.id == id }
                                    }
                                    if (activeChange == null) {
                                        val down = event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                                        if (down != null) {
                                            activePointerId = down.id
                                            lastPosition = down.position
                                            lastSize = bounds
                                            isPointerPressed = true
                                            queueTouch("down", down.position, bounds)
                                            down.consume()
                                        }
                                    } else {
                                        lastPosition = activeChange.position
                                        lastSize = bounds
                                        when {
                                            activeChange.changedToUpIgnoreConsumed() -> {
                                                isPointerPressed = false
                                                queueTouch("up", activeChange.position, bounds)
                                                activePointerId = null
                                                activeChange.consume()
                                            }
                                            !activeChange.pressed -> {
                                                isPointerPressed = false
                                                queueTouch("cancel", activeChange.position, bounds)
                                                activePointerId = null
                                                activeChange.consume()
                                            }
                                            activeChange.positionChangedIgnoreConsumed() -> {
                                                queueTouch("move", activeChange.position, bounds)
                                                activeChange.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        } finally {
                            if (activePointerId != null) {
                                queueTouch("cancel", lastPosition, lastSize)
                            }
                            isPointerPressed = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (isReconnecting) 0.85f else 1.0f),
                    )
                    pointerPosition?.let { pos ->
                        val pointerScale by animateFloatAsState(
                            targetValue = if (isPointerPressed) 0.7f else 1.0f,
                            label = "pointerScale"
                        )

                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            val baseRadius = 24.dp.toPx()
                            val currentRadius = baseRadius * pointerScale

                            drawCircle(
                                color = Color.White.copy(alpha = 0.1f),
                                radius = currentRadius,
                                center = pos
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.5f),
                                radius = currentRadius,
                                center = pos,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PulsatingDots(color = PortalColors.accent, modifier = Modifier.size(width = 42.dp, height = 18.dp))
                        Text("Waiting for first frame", color = PortalColors.muted, fontSize = 13.sp)
                    }
                }
            }

            ScreenMirrorErrorOverlay(
                displayedError = displayedError,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .fillMaxWidth(0.7f)
            )
        }
    }
}

@Composable
private fun ScreenMirrorErrorOverlay(
    displayedError: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = displayedError != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
        modifier = modifier
    ) {
        displayedError?.let { msg ->
            StatusCard("Connection Interrupted", msg, PortalColors.error)
        }
    }
}

private fun Offset.normalizedInFittedFrame(
    containerSize: IntSize,
    frame: ScreenMirrorFrameMetadata?,
    clampOutsideFrame: Boolean,
): Offset? {
    if (frame == null || frame.width <= 0 || frame.height <= 0) return null
    val containerWidth = containerSize.width.toFloat()
    val containerHeight = containerSize.height.toFloat()
    if (containerWidth <= 0f || containerHeight <= 0f) return null

    val frameAspectRatio = frame.width.toFloat() / frame.height.toFloat()
    val containerAspectRatio = containerWidth / containerHeight
    val fittedWidth: Float
    val fittedHeight: Float
    val offsetX: Float
    val offsetY: Float
    if (containerAspectRatio > frameAspectRatio) {
        fittedHeight = containerHeight
        fittedWidth = fittedHeight * frameAspectRatio
        offsetX = (containerWidth - fittedWidth) / 2f
        offsetY = 0f
    } else {
        fittedWidth = containerWidth
        fittedHeight = fittedWidth / frameAspectRatio
        offsetX = 0f
        offsetY = (containerHeight - fittedHeight) / 2f
    }

    val localX = x - offsetX
    val localY = y - offsetY
    if (!clampOutsideFrame && (localX < 0f || localY < 0f || localX > fittedWidth || localY > fittedHeight)) {
        return null
    }
    return Offset(
        x = (localX.coerceIn(0f, fittedWidth) / fittedWidth).coerceIn(0f, 1f),
        y = (localY.coerceIn(0f, fittedHeight) / fittedHeight).coerceIn(0f, 1f),
    )
}

private data class ScreenMirrorTouchEvent(
    val action: String,
    val x: Float,
    val y: Float,
)

private data class ScreenMirrorFrameMetadata(
    val updatedAtEpochMillis: Long,
    val mimeType: String,
    val format: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)

private fun parseScreenMirrorFrameMetadata(value: String): ScreenMirrorFrameMetadata? {
    val parts = value.split('|')
    if (parts.size != 7 || parts[0] != "frame") return null
    return ScreenMirrorFrameMetadata(
        updatedAtEpochMillis = parts[1].toLongOrNull() ?: return null,
        mimeType = parts[2],
        format = parts[3],
        width = parts[4].toIntOrNull() ?: return null,
        height = parts[5].toIntOrNull() ?: return null,
        sizeBytes = parts[6].toLongOrNull() ?: return null,
    )
}

private fun ScreenMirrorTouchEvent.toBinaryPacket(): ByteArray {
    val bytes = ByteArray(9)
    bytes[0] = when (action) {
        "down" -> 0
        "move" -> 1
        "up" -> 2
        "cancel" -> 3
        else -> 3
    }.toByte()
    x.toBits().writeLittleEndian(bytes, 1)
    y.toBits().writeLittleEndian(bytes, 5)
    return bytes
}

private fun Int.writeLittleEndian(target: ByteArray, offset: Int) {
    target[offset] = (this and 0xFF).toByte()
    target[offset + 1] = ((this ushr 8) and 0xFF).toByte()
    target[offset + 2] = ((this ushr 16) and 0xFF).toByte()
    target[offset + 3] = ((this ushr 24) and 0xFF).toByte()
}

@Composable
private fun ScreenMirrorHeader(
    frame: ScreenMirrorFrameMetadata?,
    fps: Int,
    isReconnecting: Boolean,
) {
    ResponsiveRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        leftContent = {
            Text(
                text = frame?.let {
                    "${it.width} x ${it.height} • ${it.format.uppercase()} • ${formatSize(it.sizeBytes)}"
                } ?: "-",
                color = PortalColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        },
        rightContent = {
            val statusText = when {
                isReconnecting -> "Reconnecting..."
                fps >= 30 -> "30 FPS"
                else -> "2 FPS"
            }
            Text(
                text = statusText,
                color = if (isReconnecting) PortalColors.warning else PortalColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    )
}