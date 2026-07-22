package io.github.portalappinspector.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import androidx.compose.ui.zIndex
import io.github.docklayout.DockLayout
import io.github.docklayout.DockLayoutTab
import io.github.docklayout.dockLayout
import io.github.docklayout.rememberDockState
import io.github.docklayout.tabRenderers
import io.github.portalappinspector.PortalHealth
import io.github.portalappinspector.PortalManifest
import io.github.portalappinspector.PortalPluginRequest
import io.github.portalappinspector.PortalRpcBatchRequest
import io.github.portalappinspector.PortalRpcBatchResponse
import com.sebastianneubauer.jsontree.JsonTree
import com.sebastianneubauer.jsontree.defaultDarkColors
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.Image as SkiaImage

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        PortalApp()
    }
}

private sealed interface PortalTab : DockLayoutTab

private data object FilesTab : PortalTab {
    override val title: String = "Files"
}

private data object NetworkTab : PortalTab {
    override val title: String = "Network"
}

private data class NetworkResponseTab(
    val call: PortalNetworkCall,
) : PortalTab {
    override val title: String = "Response #${call.id}"
}

private data class UnsupportedPluginTab(
    val pluginId: String,
) : PortalTab {
    override val title: String = pluginId
}

internal val LocalToastHost = staticCompositionLocalOf { ToastHostState() }

internal enum class ToastKind {
    Success,
    Info,
    Warning,
    Error,
}

internal class ToastHostState {
    internal val toasts = mutableStateListOf<PortalToast>()
    private var nextId = 0L

    fun show(
        message: String,
        kind: ToastKind = ToastKind.Success,
        durationMillis: Long = 5_000L,
    ) {
        nextId += 1
        toasts.add(
            index = 0,
            element = PortalToast(
                id = nextId,
                message = message,
                kind = kind,
                durationMillis = durationMillis,
            ),
        )
    }

    internal fun remove(id: Long) {
        toasts.removeAll { it.id == id }
    }
}

internal class PortalToast(
    val id: Long,
    val message: String,
    val kind: ToastKind,
    val durationMillis: Long,
) {
    var visible by mutableStateOf(false)
}

@Composable
private fun PortalApp() {
    MaterialTheme {
        val toastHost = remember { ToastHostState() }
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif),
            LocalToastHost provides toastHost,
        ) {
        val launchParams = remember { PortalLaunchParams.fromUrl() }
        val mobileView = launchParams.mobileView
        val initialConnection = remember(launchParams) {
            launchParams.connection
                ?: PortalConnectionStore.latest()?.toPortalConnection()
                ?: PortalConnection("", "")
        }
        var connection by remember { mutableStateOf(initialConnection) }
        val client = remember { PortalSourceClient() }
        var health by remember { mutableStateOf<PortalHealth?>(null) }
        var manifest by remember { mutableStateOf<PortalManifest?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var connecting by remember { mutableStateOf(false) }
        var layoutRevision by remember { mutableStateOf(0) }
        var savedConnections by remember { mutableStateOf(PortalConnectionStore.load()) }
        val responseTabs = remember { mutableStateListOf<NetworkResponseTab>() }
        val scope = rememberCoroutineScope()

        fun openResponseTab(call: PortalNetworkCall) {
            responseTabs.removeAll { it.call.id == call.id }
            responseTabs += NetworkResponseTab(call)
            layoutRevision += 1
        }

        fun connect() {
            connecting = true
            error = null
            scope.launch {
                runCatching {
                    val nextHealth = client.health(connection)
                    val nextManifest = client.manifest(connection)
                    health = nextHealth
                    manifest = nextManifest
                    savedConnections = PortalConnectionStore.upsert(nextManifest, connection)
                }.onFailure { throwable ->
                    health = null
                    manifest = null
                    error = throwable.message ?: throwable::class.simpleName
                }
                connecting = false
            }
        }

        LaunchedEffect(connection) {
            if (connection.isValid) {
                connect()
            }
        }

        LaunchedEffect(Unit) {
            delay(2_000L)
            toastHost.show("Welcome to Portal")
            delay(2_000L)
            toastHost.show("Another toast !")
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PortalColors.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PortalColors.background),
            ) {
                if (!mobileView) {
                    TopBar(
                        manifest = manifest,
                        health = health,
                        savedConnections = savedConnections,
                        onSelectConnection = { saved ->
                            connection = saved.toPortalConnection()
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = manifest != null,
                        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
                            animationSpec = tween(260),
                            initialOffsetY = { it / 36 },
                        ),
                        exit = fadeOut(animationSpec = tween(160)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        key(layoutRevision) {
                            val layoutState = rememberDockState<PortalTab>(
                                dockLayout {
                                    panel {
                                        if (responseTabs.isEmpty()) {
                                            tab(FilesTab)
                                            tab(NetworkTab)
                                        } else {
                                            responseTabs.asReversed().forEach { tab(it) }
                                            tab(NetworkTab)
                                            tab(FilesTab)
                                        }
                                    }
                                }
                            )

                            DockLayout(
                                state = layoutState,
                                renderers = tabRenderers {
                                    renderer<FilesTab> { _, _ ->
                                        FilesPanel(
                                            connection = connection,
                                            client = client,
                                            enabled = manifest?.plugins?.any { it.id == "portal:files" } == true,
                                        )
                                    }
                                    renderer<NetworkTab> { _, _ ->
                                        NetworkPanel(
                                            connection = connection,
                                            client = client,
                                            enabled = manifest?.plugins?.any { it.id == "portal:network" } == true,
                                            onOpenResponseTab = ::openResponseTab,
                                        )
                                    }
                                    renderer<NetworkResponseTab> { tab, _ ->
                                        NetworkResponsePanel(tab.call)
                                    }
                                    renderer<UnsupportedPluginTab> { tab, _ ->
                                        UnsupportedPluginPanel(tab.pluginId)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(if (mobileView) 0.dp else 8.dp),
                            )
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = manifest == null && !mobileView,
                        enter = fadeIn(animationSpec = tween(260)) + slideInVertically(
                            animationSpec = tween(320),
                            initialOffsetY = { it / 18 },
                        ),
                        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(
                            animationSpec = tween(220),
                            targetOffsetY = { -it / 28 },
                        ),
                    ) {
                        WelcomeConnectionPanel(
                            connection = connection,
                            connecting = connecting,
                            error = error,
                            onConnect = ::connect,
                        )
                    }
                    if (manifest == null && mobileView) {
                        MobileConnectionState(
                            connecting = connecting,
                            error = error,
                        )
                    }
                }
            }
            ToastHost(
                state = toastHost,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(20f),
            )
        }
    }
}
}

@Composable
private fun ToastHost(
    state: ToastHostState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    fun dismiss(toast: PortalToast) {
        if (!toast.visible) return
        toast.visible = false
        scope.launch {
            delay(220L)
            state.remove(toast.id)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.toasts.forEach { toast ->
            key(toast.id) {
                LaunchedEffect(toast.id) {
                    toast.visible = true
                    delay(toast.durationMillis)
                    dismiss(toast)
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = toast.visible,
                    enter = fadeIn(animationSpec = tween(180)) + slideInVertically(
                        animationSpec = tween(220),
                        initialOffsetY = { -it },
                    ),
                    exit = fadeOut(animationSpec = tween(160)) + slideOutVertically(
                        animationSpec = tween(180),
                        targetOffsetY = { -it },
                    ),
                ) {
                    ToastCard(
                        toast = toast,
                        onDismiss = { dismiss(toast) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToastCard(
    toast: PortalToast,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .shadow(8.dp, shape)
            .background(PortalColors.card, shape)
            .border(1.dp, PortalColors.border, shape)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToastIcon(
            kind = toast.kind,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = toast.message,
            color = PortalColors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ToastIcon(
    kind: ToastKind,
    modifier: Modifier = Modifier,
) {
    val color = when (kind) {
        ToastKind.Success -> PortalColors.success
        ToastKind.Info -> PortalColors.accent
        ToastKind.Warning -> PortalColors.warning
        ToastKind.Error -> PortalColors.error
    }
    Canvas(modifier = modifier) {
        val strokeWidth = 1.8.dp.toPx()
        drawCircle(color = color)
        when (kind) {
            ToastKind.Success -> {
                drawLine(
                    color = PortalColors.submitText,
                    start = Offset(size.width * 0.28f, size.height * 0.52f),
                    end = Offset(size.width * 0.44f, size.height * 0.68f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = PortalColors.submitText,
                    start = Offset(size.width * 0.44f, size.height * 0.68f),
                    end = Offset(size.width * 0.74f, size.height * 0.34f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            else -> drawCircle(
                color = PortalColors.submitText,
                radius = size.minDimension * 0.12f,
                center = center,
            )
        }
    }
}

private enum class PortalButtonKind {
    Primary,
    Secondary,
}

@Composable
private fun PortalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: PortalButtonKind = PortalButtonKind.Secondary,
) {
    val primary = kind == PortalButtonKind.Primary
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) PortalColors.success else PortalColors.button,
            contentColor = if (primary) PortalColors.submitText else PortalColors.text,
            disabledContainerColor = PortalColors.button.copy(alpha = 0.55f),
            disabledContentColor = PortalColors.muted,
        ),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun TopBar(
    manifest: PortalManifest?,
    health: PortalHealth?,
    savedConnections: List<SavedPortalConnection>,
    onSelectConnection: (SavedPortalConnection) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .zIndex(1f)
            .background(PortalColors.topBar),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSessionButton(
                manifest = manifest,
                expanded = expanded,
                onClick = {
                    if (manifest != null || savedConnections.isNotEmpty()) {
                        expanded = !expanded
                    }
                },
            )
            Spacer(Modifier.weight(1f))
            SourceStatus(health)
        }
        if (expanded) {
            AppSessionDropdown(
                savedConnections = savedConnections,
                currentPackageName = manifest?.sourcePackageName,
                onSelectConnection = {
                    expanded = false
                    onSelectConnection(it)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 12.dp, y = 46.dp),
            )
        }
    }
}

@Composable
private fun AppSessionButton(
    manifest: PortalManifest?,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .background(PortalColors.card, RoundedCornerShape(7.dp))
            .border(1.dp, PortalColors.border, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (manifest == null) {
            PortalMark(Modifier.width(24.dp).height(24.dp))
        } else {
            AppIcon(
                iconPngBase64 = manifest.appIconPngBase64,
                fallbackText = manifest.appName,
                modifier = Modifier.size(24.dp),
            )
        }
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
            manifest?.sourcePackageName?.let {
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
            PulsatingDots(
                color = PortalColors.accent.copy(alpha = 0.55f),
                dotDiameter = 4.dp,
                horizontalSpace = 3.dp,
                modifier = Modifier.size(width = 24.dp, height = 12.dp),
            )
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
private fun AppSessionDropdown(
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
                        if (saved.packageName == currentPackageName) PortalColors.background else PortalColors.card,
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
                        text = "${saved.connection.host}:${saved.connection.port} - ${saved.packageName}",
                        color = PortalColors.muted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (saved.packageName == currentPackageName) {
                    Text("Active", color = PortalColors.success, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AppIcon(
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
private fun DropdownChevron(expanded: Boolean, modifier: Modifier = Modifier) {
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
private fun PortalMark(modifier: Modifier = Modifier) {
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

@Composable
private fun SourceStatus(health: PortalHealth?) {
    val connected = health != null
    Row(
        modifier = Modifier
            .height(32.dp)
            .background(PortalColors.card, RoundedCornerShape(7.dp))
            .border(1.dp, PortalColors.border, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(if (connected) PortalColors.success else PortalColors.muted, RoundedCornerShape(99.dp)),
        )
        Text(
            text = health?.sourceName ?: "No source connected",
            color = if (connected) PortalColors.text else PortalColors.muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WelcomeConnectionPanel(
    connection: PortalConnection,
    connecting: Boolean,
    error: String?,
    onConnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(460.dp)
            .background(PortalColors.card, RoundedCornerShape(8.dp))
            .border(1.dp, PortalColors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 28.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PortalMark(Modifier.size(56.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Welcome to Portal",
                color = PortalColors.text,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (connection.isValid) {
                    "A source app link was detected. Connect to begin inspecting it."
                } else {
                    "Open Portal from a source app to start an inspection session."
                },
                color = PortalColors.muted,
                fontSize = 14.sp,
            )
        }
        ConnectionTarget(connection)
        PortalButton(
            text = when {
                connecting -> "Connecting..."
                error != null -> "Retry connection"
                connection.isValid -> "Connect to source"
                else -> "Waiting for source link"
            },
            onClick = onConnect,
            enabled = !connecting && connection.isValid,
            kind = PortalButtonKind.Primary,
            modifier = Modifier
                .fillMaxWidth()
        )
        if (connecting) {
            PulsatingDots(color = PortalColors.success, modifier = Modifier.size(width = 44.dp, height = 18.dp))
        }
        error?.let {
            StatusCard("Connection failed", it, PortalColors.error)
        }
    }
}

@Composable
private fun MobileConnectionState(
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
private fun ConnectionTarget(connection: PortalConnection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PortalColors.background, RoundedCornerShape(7.dp))
            .border(1.dp, PortalColors.border, RoundedCornerShape(7.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "Source",
                color = PortalColors.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (connection.isValid) "${connection.host}:${connection.port}" else "No source detected",
                color = PortalColors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (connection.isValid) "Ready" else "Missing source",
            color = if (connection.isValid) PortalColors.success else PortalColors.warning,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun FilesPanel(
    connection: PortalConnection,
    client: PortalSourceClient,
    enabled: Boolean,
) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentPath by remember { mutableStateOf<String?>(null) }
    val fileItems = remember { mutableStateListOf<PortalFileItem>() }
    val backStack = remember { mutableStateListOf<String?>() }
    val scope = rememberCoroutineScope()

    fun load(path: String?) {
        loading = true
        error = null
        scope.launch {
            runCatching {
                val payload = if (path == null) {
                    client.request(
                        connection = connection,
                        pluginId = "portal:files",
                        payload = buildJsonObject { put("type", "listRoots") },
                    )
                } else {
                    client.request(
                        connection = connection,
                        pluginId = "portal:files",
                        payload = buildJsonObject {
                            put("type", "listChildren")
                            put("path", path)
                        },
                    )
                }
                currentPath = path
                fileItems.clear()
                fileItems += payload["items"]?.jsonArray?.map(::parseFileItem).orEmpty()
            }.onFailure { throwable ->
                error = throwable.message ?: throwable::class.simpleName
            }
            loading = false
        }
    }

    LaunchedEffect(enabled, connection) {
        if (enabled && connection.isValid) {
            load(null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Files", color = PortalColors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            PortalButton(
                text = "Back",
                onClick = {
                    val previous = backStack.removeLastOrNull()
                    load(previous)
                },
                enabled = backStack.isNotEmpty() && !loading,
            )
            Spacer(Modifier.width(8.dp))
            PortalButton(
                text = "Refresh",
                onClick = { load(currentPath) },
                enabled = enabled && !loading,
            )
        }
        if (!enabled) {
            StatusCard("Files plugin unavailable", "Install the portal:files plugin in the source app.", PortalColors.warning)
            return@Column
        }
        error?.let {
            StatusCard("Files request failed", it, PortalColors.error)
        }
        if (loading) {
            PulsatingDots(color = PortalColors.accent, modifier = Modifier.size(width = 48.dp, height = 18.dp))
        }
        Text(currentPath ?: "Roots", color = PortalColors.muted, fontSize = 13.sp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            items(fileItems, key = { it.path }) { item ->
                FileRow(
                    item = item,
                    onClick = {
                        if (item.directory) {
                            backStack += currentPath
                            load(item.path)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FileRow(item: PortalFileItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = item.directory, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (item.directory) "DIR" else "FILE",
                color = if (item.directory) PortalColors.accent else PortalColors.muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(34.dp),
            )
            Text(
                text = item.name,
                color = PortalColors.text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatSize(item.sizeBytes),
                color = PortalColors.muted,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        RowDivider()
    }
}

@Composable
private fun NetworkPanel(
    connection: PortalConnection,
    client: PortalSourceClient,
    enabled: Boolean,
    onOpenResponseTab: (PortalNetworkCall) -> Unit,
) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastTimestamp by remember(connection) { mutableStateOf(0L) }
    var bodySheetCall by remember { mutableStateOf<PortalNetworkCall?>(null) }
    val calls = remember(connection) { mutableStateListOf<PortalNetworkCall>() }

    suspend fun loadNewCalls() {
        loading = true
        runCatching {
            client.request(
                connection = connection,
                pluginId = "portal:network",
                payload = buildJsonObject {
                    put("type", "listAfter")
                    put("afterTimestampEpochMillis", lastTimestamp)
                },
            )
        }.onSuccess { payload ->
            error = null
            val nextCalls = payload["items"]?.jsonArray?.map(::parseNetworkCall).orEmpty()
            val existingIds = calls.mapTo(mutableSetOf()) { it.id }
            calls += nextCalls.filter { existingIds.add(it.id) }
            lastTimestamp = calls.maxOfOrNull { it.timestampEpochMillis } ?: lastTimestamp
        }.onFailure { throwable ->
            error = throwable.message ?: throwable::class.simpleName
        }
        loading = false
    }

    LaunchedEffect(enabled, connection) {
        calls.clear()
        lastTimestamp = 0L
        if (!enabled || !connection.isValid) return@LaunchedEffect

        while (true) {
            loadNewCalls()
            delay(2_000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Network", color = PortalColors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${calls.size} calls",
                        color = PortalColors.muted,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                PortalButton(
                    text = "Clear",
                    onClick = {
                        lastTimestamp = calls.maxOfOrNull { it.timestampEpochMillis } ?: lastTimestamp
                        calls.clear()
                    },
                    enabled = calls.isNotEmpty(),
                )
            }
            if (!enabled) {
                StatusCard("Network plugin unavailable", "Install the portal:network plugin in the source app.", PortalColors.warning)
                return@Column
            }
            error?.let {
                StatusCard("Network request failed", it, PortalColors.error)
            }
            if (loading && calls.isEmpty()) {
                PulsatingDots(color = PortalColors.accent, modifier = Modifier.size(width = 48.dp, height = 18.dp))
            }
            if (calls.isEmpty() && !loading) {
                StatusCard("No traffic captured", "Trigger a Retrofit request in the source app.", PortalColors.muted)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(calls.reversed(), key = { "network-call-${it.id}" }) { call ->
                    NetworkCallRow(
                        call = call,
                        onViewBody = { bodySheetCall = call },
                    )
                }
            }
        }
        bodySheetCall?.let { call ->
            ResponseBodySheet(
                call = call,
                onDismiss = { bodySheetCall = null },
                onOpenTab = {
                    bodySheetCall = null
                    onOpenResponseTab(call)
                },
            )
        }
    }
}

@Composable
private fun NetworkCallRow(
    call: PortalNetworkCall,
    onViewBody: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MethodText(call.method)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = call.endpoint,
                    color = PortalColors.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                call.error?.let { error ->
                    Text(
                        text = error,
                        color = PortalColors.error,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            StatusText(call.statusCode)
            NetworkMetaText(
                text = formatDuration(call.durationMillis),
                color = latencyColor(call.durationMillis),
                width = 50.dp,
            )
            NetworkMetaText(
                text = formatShortTime(call.timestampEpochMillis),
                color = PortalColors.muted,
                width = 58.dp,
            )
            BodyButton(
                enabled = call.responseBody != null,
                onClick = onViewBody,
            )
        }
        RowDivider()
    }
}

@Composable
private fun MethodText(method: String) {
    Text(
        text = method.take(6),
        color = PortalColors.accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.width(46.dp),
    )
}

@Composable
private fun StatusText(statusCode: Int?) {
    val color = when {
        statusCode == null -> PortalColors.error
        statusCode in 200..299 -> PortalColors.success
        statusCode in 300..399 -> PortalColors.warning
        else -> PortalColors.error
    }
    NetworkMetaText(
        text = statusCode?.toString() ?: "ERR",
        color = color,
        width = 42.dp,
        emphasized = true,
    )
}

@Composable
private fun NetworkMetaText(
    text: String,
    color: Color,
    width: androidx.compose.ui.unit.Dp,
    emphasized: Boolean = false,
) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun BodyButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = if (enabled) "Body" else "-",
        color = if (enabled) PortalColors.text else PortalColors.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .width(38.dp)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(PortalColors.border.copy(alpha = 0.65f)),
    )
}

@Composable
private fun PulsatingDots(
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
private fun ResponseBodySheet(
    call: PortalNetworkCall,
    onDismiss: () -> Unit,
    onOpenTab: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.46f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .background(PortalColors.card, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .border(1.dp, PortalColors.border, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .clickable(onClick = {})
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ResponseBodyHeader(
                call = call,
                actionText = "Open in new tab",
                onAction = onOpenTab,
                secondaryActionText = "Close",
                onSecondaryAction = onDismiss,
            )
            ResponseBodyContent(call = call, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun NetworkResponsePanel(call: PortalNetworkCall) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ResponseBodyHeader(
            call = call,
            actionText = "Response tab",
            onAction = {},
            actionEnabled = false,
        )
        ResponseBodyContent(call = call, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ResponseBodyHeader(
    call: PortalNetworkCall,
    actionText: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "${call.method} ${call.endpoint}",
                color = PortalColors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(call.statusCode?.toString() ?: "ERR")
                    append(" | ")
                    append(call.durationMillis)
                    append(" ms")
                    call.responseContentType?.let {
                        append(" | ")
                        append(it)
                    }
                    if (call.responseBodyTruncated) append(" | truncated")
                },
                color = PortalColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PortalButton(
            text = actionText,
            onClick = onAction,
            enabled = actionEnabled,
            kind = PortalButtonKind.Primary,
        )
        if (secondaryActionText != null && onSecondaryAction != null) {
            PortalButton(
                text = secondaryActionText,
                onClick = onSecondaryAction,
            )
        }
    }
}

@Composable
private fun ResponseBodyContent(
    call: PortalNetworkCall,
    modifier: Modifier = Modifier,
) {
    val body = call.responseBody
    if (body == null) {
        StatusCard("No response body", "This network call did not include a captured response body.", PortalColors.muted)
        return
    }

    val jsonBody = remember(body) { formatJsonOrNull(body) }
    if (jsonBody != null) {
        JsonTree(
            modifier = modifier
                .background(PortalColors.background, RoundedCornerShape(6.dp))
                .border(1.dp, PortalColors.border, RoundedCornerShape(6.dp)),
            json = jsonBody,
            onLoading = { PulsatingDots(color = PortalColors.accent, modifier = Modifier.size(width = 42.dp, height = 16.dp)) },
            contentPadding = PaddingValues(12.dp),
            colors = defaultDarkColors,
            textStyle = LocalTextStyle.current.copy(
                color = PortalColors.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            ),
            showIndices = true,
        )
    } else {
        val lines = remember(body) { body.lines() }
        LazyColumn(
            modifier = modifier
                .background(PortalColors.background, RoundedCornerShape(6.dp))
                .border(1.dp, PortalColors.border, RoundedCornerShape(6.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    color = PortalColors.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
private fun UnsupportedPluginPanel(pluginId: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        StatusCard(pluginId, "Installed in Source, but this Portal UI does not support it yet.", PortalColors.warning)
    }
}

@Composable
private fun StatusCard(title: String, message: String, color: Color) {
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

private class PortalSourceClient {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }
    private var nextRequestId = 0

    suspend fun health(connection: PortalConnection): PortalHealth =
        httpClient.get("${connection.baseUrl}/portal/health").body()

    suspend fun manifest(connection: PortalConnection): PortalManifest =
        httpClient.get("${connection.baseUrl}/portal/manifest").body()

    suspend fun request(
        connection: PortalConnection,
        pluginId: String,
        payload: JsonObject,
    ): JsonObject {
        val request = PortalPluginRequest(
            id = nextId(),
            pluginId = pluginId,
            payload = payload,
        )
        val batchResponse: PortalRpcBatchResponse =
            httpClient.post("${connection.baseUrl}/portal/rpc") {
                contentType(ContentType.Application.Json)
                setBody(PortalRpcBatchRequest(listOf(request)))
            }.body()
        val response = batchResponse.responses.firstOrNull { it.id == request.id }
            ?: error("Source did not return a response for request ${request.id}.")
        if (!response.ok) {
            error(response.error?.message ?: response.error?.code ?: "Request failed.")
        }
        return response.payload ?: JsonObject(emptyMap())
    }

    private fun nextId(): Int {
        nextRequestId += 1
        return nextRequestId
    }
}

@Serializable
private data class PortalConnection(
    val host: String,
    val port: String,
) {
    val isValid: Boolean
        get() = host.isNotBlank() && port.isNotBlank()

    val baseUrl: String
        get() = "http://$host:$port"

    companion object {
        @OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
        fun cleanConnectUrl(mobileView: Boolean) {
            val path = window.location.pathname
            val cleanPath = when {
                path.endsWith("/connect") -> path.removeSuffix("connect")
                path.endsWith("/connect/") -> path.removeSuffix("connect/")
                else -> path
            }.ifBlank { "/" }
            val cleanQuery = if (mobileView) "?mobileView=true" else ""
            val cleanUrl = cleanPath + cleanQuery + window.location.hash
            window.history.replaceState(null, "", cleanUrl)
        }
    }
}

private data class PortalLaunchParams(
    val connection: PortalConnection?,
    val mobileView: Boolean,
) {
    companion object {
        fun fromUrl(): PortalLaunchParams {
            val params = window.location.search
                .removePrefix("?")
                .split("&")
                .filter { it.contains("=") }
                .associate {
                    val key = it.substringBefore("=")
                    val value = it.substringAfter("=")
                    key to value
                }
            val mobileView = params.any { (key, value) ->
                key.normalizedQueryKey() == "mobileview" && value.isTruthyQueryValue()
            }
            val connection = params["host"]
                ?.takeIf { it.isNotBlank() }
                ?.let { host ->
                    PortalConnection(
                        host = host,
                        port = params["port"] ?: "4896",
                    )
                }
            PortalConnection.cleanConnectUrl(mobileView)
            return PortalLaunchParams(
                connection = connection,
                mobileView = mobileView,
            )
        }

        private fun String.normalizedQueryKey(): String =
            lowercase()
                .replace("%20", "")
                .replace("+", "")
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "")

        private fun String.isTruthyQueryValue(): Boolean =
            lowercase() in setOf("true", "1", "yes", "y")
    }
}

@Serializable
private data class SavedPortalConnection(
    val packageName: String,
    val appName: String,
    val appIconPngBase64: String? = null,
    val connection: PortalConnection,
    val updatedAtEpochMillis: Long,
) {
    fun toPortalConnection(): PortalConnection =
        connection
}

private object PortalConnectionStore {
    private const val StorageKey = "portal.app.inspector.connections"

    fun load(): List<SavedPortalConnection> =
        runCatching {
            val raw = window.localStorage.getItem(StorageKey) ?: return emptyList()
            StorageJson.decodeFromString<List<SavedPortalConnection>>(raw)
                .sortedByDescending { it.updatedAtEpochMillis }
        }.getOrDefault(emptyList())

    fun latest(): SavedPortalConnection? =
        load().maxByOrNull { it.updatedAtEpochMillis }

    fun upsert(
        manifest: PortalManifest,
        connection: PortalConnection,
    ): List<SavedPortalConnection> {
        val next = SavedPortalConnection(
            packageName = manifest.sourcePackageName,
            appName = manifest.appName,
            appIconPngBase64 = manifest.appIconPngBase64,
            connection = connection,
            updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )
        val updated = (load().filterNot { it.packageName == next.packageName } + next)
            .sortedByDescending { it.updatedAtEpochMillis }
        runCatching {
            window.localStorage.setItem(StorageKey, StorageJson.encodeToString(updated))
        }
        return updated
    }
}

private val StorageJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private data class PortalFileItem(
    val name: String,
    val path: String,
    val directory: Boolean,
    val sizeBytes: Long?,
)

private data class PortalNetworkCall(
    val id: Long,
    val timestampEpochMillis: Long,
    val method: String,
    val url: String,
    val endpoint: String,
    val statusCode: Int?,
    val durationMillis: Long,
    val error: String?,
    val responseBody: String?,
    val responseContentType: String?,
    val responseBodyTruncated: Boolean,
)

private fun parseFileItem(value: kotlinx.serialization.json.JsonElement): PortalFileItem {
    val item = value.jsonObject
    return PortalFileItem(
        name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        path = item["path"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        directory = item["directory"]?.jsonPrimitive?.contentOrNull == "true",
        sizeBytes = item["sizeBytes"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
    )
}

private fun parseNetworkCall(value: kotlinx.serialization.json.JsonElement): PortalNetworkCall {
    val item = value.jsonObject
    return PortalNetworkCall(
        id = item["id"]?.jsonPrimitive?.longOrNull ?: 0L,
        timestampEpochMillis = item["timestampEpochMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
        method = item["method"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        url = item["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        endpoint = item["endpoint"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        statusCode = item["statusCode"]?.jsonPrimitive?.intOrNull,
        durationMillis = item["durationMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
        error = item["error"]?.jsonPrimitive?.contentOrNull,
        responseBody = item["responseBody"]?.jsonPrimitive?.contentOrNull,
        responseContentType = item["responseContentType"]?.jsonPrimitive?.contentOrNull,
        responseBodyTruncated = item["responseBodyTruncated"]?.jsonPrimitive?.booleanOrNull == true,
    )
}

private val PrettyJson = Json {
    prettyPrint = true
}

private fun formatJsonOrNull(value: String): String? =
    runCatching {
        val element: JsonElement = Json.parseToJsonElement(value)
        PrettyJson.encodeToString(JsonElement.serializer(), element)
    }.getOrNull()

@OptIn(ExperimentalEncodingApi::class)
private fun String.toImageBitmapOrNull(): ImageBitmap? =
    runCatching {
        SkiaBitmap.makeFromImage(SkiaImage.makeFromEncoded(Base64.decode(this))).asComposeImageBitmap()
    }.getOrNull()

private fun formatSize(sizeBytes: Long?): String =
    when {
        sizeBytes == null -> ""
        sizeBytes < 1024 -> "$sizeBytes B"
        sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
        else -> "${sizeBytes / (1024 * 1024)} MB"
    }

private fun formatDuration(durationMillis: Long): String =
    if (durationMillis < 1_000L) "${durationMillis}ms" else "${durationMillis / 1_000L}.${(durationMillis % 1_000L) / 100L}s"

private fun latencyColor(durationMillis: Long): Color =
    when {
        durationMillis >= 1_000L -> PortalColors.error
        durationMillis >= 400L -> PortalColors.warning
        else -> PortalColors.muted
    }

private fun formatShortTime(timestampEpochMillis: Long): String {
    val totalSeconds = timestampEpochMillis / 1_000L
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = (totalSeconds / 3_600L) % 24L
    return "${hours.twoDigits()}:${minutes.twoDigits()}:${seconds.twoDigits()}"
}

private fun formatTime(timestampEpochMillis: Long): String {
    val totalSeconds = timestampEpochMillis / 1_000L
    val millis = timestampEpochMillis % 1_000L
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = (totalSeconds / 3_600L) % 24L
    return "${hours.twoDigits()}:${minutes.twoDigits()}:${seconds.twoDigits()}.${millis.threeDigits()}"
}

private fun Long.twoDigits(): String = toString().padStart(2, '0')

private fun Long.threeDigits(): String = toString().padStart(3, '0')

private object PortalColors {
    val background = Color(0xFF0F0F0F)
    val topBar = Color(0xFF0F0F0F)
    val card = Color(0xFF262626)
    val border = Color(0xFF404041)
    val text = Color(0xFFF5F5F5)
    val muted = Color(0xFFA8A8A8)
    val accent = Color(0xFFFFA116)
    val logo = Color(0xFFFFC01E)
    val success = Color(0xFF2DB55D)
    val warning = Color(0xFFFFA116)
    val error = Color(0xFFFF6B6B)
    val button = Color(0xFF333333)
    val submitText = Color(0xFFFFFFFF)
}
