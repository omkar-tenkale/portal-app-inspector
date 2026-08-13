package io.github.portalappinspector.app.features.screenmirror

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.data.PortalConnection
import io.github.portalappinspector.app.data.PortalSourceClient
import io.github.portalappinspector.app.data.ScreenMirrorPluginId
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.PortalButton
import io.github.portalappinspector.app.ui.PortalButtonKind
import io.github.portalappinspector.app.ui.PulsatingDots
import io.github.portalappinspector.app.ui.RowDivider
import io.github.portalappinspector.app.ui.StatusCard
import io.github.portalappinspector.app.ui.Text
import io.github.portalappinspector.app.util.formatSize
import io.github.portalappinspector.app.util.toImageBitmapOrNull
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

@Composable
internal fun ScreenMirrorPanel(
    connection: PortalConnection,
    client: PortalSourceClient,
    enabled: Boolean,
) {
    var frame by remember(connection) { mutableStateOf<ScreenMirrorFrameMetadata?>(null) }
    var frameBytes by remember(connection) { mutableStateOf<ByteArray?>(null) }
    var error by remember(connection) { mutableStateOf<String?>(null) }
    var fpsIndex by remember(connection) { mutableStateOf(0) }
    var inputConnected by remember(connection) { mutableStateOf(false) }
    val fps = ScreenMirrorFpsLevels[fpsIndex]
    val bitmap = remember(frameBytes) { frameBytes?.toImageBitmapOrNull() }
    val touchEvents = remember(connection) { Channel<ScreenMirrorTouchEvent>(Channel.UNLIMITED) }

    fun queueTouch(action: String, position: Offset, size: IntSize) {
        if (!enabled || !connection.isValid || !inputConnected || size.width <= 0 || size.height <= 0) return
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

    LaunchedEffect(enabled, connection, fps) {
        error = null
        if (!enabled || !connection.isValid) return@LaunchedEffect

        while (true) {
            runCatching {
                client.streamClient.webSocket(screenMirrorSocketUrl(connection, "frames", fps)) {
                    var pendingFrame: ScreenMirrorFrameMetadata? = null
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
                                    error = null
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }.onFailure { throwable ->
                error = throwable.message ?: throwable::class.simpleName
            }
            delay(500L)
        }
    }

    LaunchedEffect(enabled, connection) {
        if (!enabled || !connection.isValid) return@LaunchedEffect

        while (true) {
            runCatching {
                client.streamClient.webSocket(screenMirrorSocketUrl(connection, "input", fps = null)) {
                    inputConnected = true
                    try {
                        for (touch in touchEvents) {
                            outgoing.send(Frame.Binary(true, touch.toBinaryPacket()))
                        }
                    } finally {
                        inputConnected = false
                    }
                }
            }.onFailure { throwable ->
                inputConnected = false
                error = throwable.message ?: throwable::class.simpleName
            }
            delay(500L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!enabled) {
            StatusCard("Screen Mirror plugin unavailable", "Install the portal-screen-mirror plugin in the source app.", PortalColors.warning)
            return@Column
        }

        ScreenMirrorHeader(
            frame = frame,
            fps = fps,
            onToggleFps = {
                fpsIndex = (fpsIndex + 1) % ScreenMirrorFpsLevels.size
            },
        )
        RowDivider()
        error?.let {
            StatusCard("Screen Mirror request failed", it, PortalColors.error)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black)
                .border(1.dp, PortalColors.border, RoundedCornerShape(6.dp))
                .pointerInput(enabled, connection, bitmap != null) {
                    if (!enabled || bitmap == null) return@pointerInput
                    var activePointerId: PointerId? = null
                    var lastPosition = Offset.Zero
                    var lastSize = IntSize.Zero
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val bounds = size
                                val activeChange = activePointerId?.let { id ->
                                    event.changes.firstOrNull { it.id == id }
                                }
                                if (activeChange == null) {
                                    val down = event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                                    if (down != null) {
                                        activePointerId = down.id
                                        lastPosition = down.position
                                        lastSize = bounds
                                        queueTouch("down", down.position, bounds)
                                        down.consume()
                                    }
                                } else {
                                    lastPosition = activeChange.position
                                    lastSize = bounds
                                    when {
                                        activeChange.changedToUpIgnoreConsumed() -> {
                                            queueTouch("up", activeChange.position, bounds)
                                            activePointerId = null
                                            activeChange.consume()
                                        }
                                        !activeChange.pressed -> {
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
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (error == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PulsatingDots(color = PortalColors.accent, modifier = Modifier.size(width = 42.dp, height = 18.dp))
                    Text("Waiting for first frame", color = PortalColors.muted, fontSize = 13.sp)
                }
            }
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

private val ScreenMirrorFpsLevels = listOf(2, 5, 10, 15, 30)

private fun screenMirrorSocketUrl(connection: PortalConnection, streamPath: String, fps: Int?): String {
    val baseUrl = connection.baseUrl.trimEnd('/')
    val wsBaseUrl = when {
        baseUrl.startsWith("https://") -> "wss://${baseUrl.removePrefix("https://")}"
        baseUrl.startsWith("http://") -> "ws://${baseUrl.removePrefix("http://")}"
        else -> "ws://$baseUrl"
    }
    val query = fps?.let { "?scale=0.5&maxFps=$it" }.orEmpty()
    return "$wsBaseUrl/portal/plugins/$ScreenMirrorPluginId/streams/$streamPath$query"
}

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
    onToggleFps: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = frame?.let {
                    "${it.width} x ${it.height} • ${it.format.uppercase()} • ${formatSize(it.sizeBytes)}"
                } ?: "-",
                color = PortalColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        PortalButton(
            text = "$fps FPS",
            onClick = onToggleFps,
            kind = PortalButtonKind.Primary,
            modifier = Modifier.height(26.dp),
        )
    }
}
