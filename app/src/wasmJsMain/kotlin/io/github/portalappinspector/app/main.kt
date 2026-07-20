package io.github.portalappinspector.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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

private data class UnsupportedPluginTab(
    val pluginId: String,
) : PortalTab {
    override val title: String = pluginId
}

@Composable
private fun PortalApp() {
    MaterialTheme {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif),
        ) {
        val initialConnection = remember { PortalConnection.fromUrl() }
        var connection by remember { mutableStateOf(initialConnection) }
        val client = remember { PortalSourceClient() }
        var health by remember { mutableStateOf<PortalHealth?>(null) }
        var manifest by remember { mutableStateOf<PortalManifest?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var connecting by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val layoutState = rememberDockState<PortalTab>(
            dockLayout {
                panel { tab(FilesTab) }
            }
        )

        fun connect() {
            connecting = true
            error = null
            scope.launch {
                runCatching {
                    val nextHealth = client.health(connection)
                    val nextManifest = client.manifest(connection)
                    health = nextHealth
                    manifest = nextManifest
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PortalColors.background),
        ) {
            TopBar(health = health)
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
                            renderer<UnsupportedPluginTab> { tab, _ ->
                                UnsupportedPluginPanel(tab.pluginId)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = manifest == null,
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
            }
        }
    }
}
}

@Composable
private fun TopBar(health: PortalHealth?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(PortalColors.topBar)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortalMark(Modifier.width(28.dp).height(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Portal App Inspector",
            color = PortalColors.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        SourceStatus(health)
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
        Button(
            onClick = onConnect,
            enabled = !connecting && connection.isValid,
            colors = ButtonDefaults.buttonColors(
                containerColor = PortalColors.success,
                contentColor = PortalColors.submitText,
                disabledContainerColor = PortalColors.button,
                disabledContentColor = PortalColors.muted,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
        ) {
            Text(
                text = when {
                    connecting -> "Connecting..."
                    error != null -> "Retry connection"
                    connection.isValid -> "Connect to source"
                    else -> "Waiting for source link"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (connecting) {
            CircularProgressIndicator(color = PortalColors.success, modifier = Modifier.size(22.dp))
        }
        error?.let {
            StatusCard("Connection failed", it, PortalColors.error)
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
    val items = remember { mutableStateListOf<PortalFileItem>() }
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
                items.clear()
                items += payload["items"]?.jsonArray?.map(::parseFileItem).orEmpty()
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
            Button(
                onClick = {
                    val previous = backStack.removeLastOrNull()
                    load(previous)
                },
                enabled = backStack.isNotEmpty() && !loading,
            ) {
                Text("Back")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { load(currentPath) }, enabled = enabled && !loading) {
                Text("Refresh")
            }
        }
        if (!enabled) {
            StatusCard("Files plugin unavailable", "Install the portal:files plugin in the source app.", PortalColors.warning)
            return@Column
        }
        error?.let {
            StatusCard("Files request failed", it, PortalColors.error)
        }
        if (loading) {
            CircularProgressIndicator()
        }
        Text(currentPath ?: "Roots", color = PortalColors.muted, fontSize = 13.sp)
        items.forEach { item ->
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

@Composable
private fun FileRow(item: PortalFileItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PortalColors.card, RoundedCornerShape(6.dp))
            .border(1.dp, PortalColors.border, RoundedCornerShape(6.dp))
            .clickable(enabled = item.directory, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (item.directory) "dir" else "file",
            color = if (item.directory) PortalColors.accent else PortalColors.muted,
            fontSize = 12.sp,
            modifier = Modifier.width(38.dp),
        )
        Text(
            text = item.name,
            color = PortalColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(formatSize(item.sizeBytes), color = PortalColors.muted, fontSize = 12.sp)
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

private data class PortalConnection(
    val host: String,
    val port: String,
) {
    val isValid: Boolean
        get() = host.isNotBlank() && port.isNotBlank()

    val baseUrl: String
        get() = "http://$host:$port"

    companion object {
        fun fromUrl(): PortalConnection {
            val params = window.location.search
                .removePrefix("?")
                .split("&")
                .filter { it.contains("=") }
                .associate {
                    val key = it.substringBefore("=")
                    val value = it.substringAfter("=")
                    key to value
                }
            val connection = PortalConnection(
                host = params["host"].orEmpty(),
                port = params["port"] ?: "4896",
            )
            cleanConnectUrl()
            return connection
        }

        @OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
        private fun cleanConnectUrl() {
            val path = window.location.pathname
            val cleanPath = when {
                path.endsWith("/connect") -> path.removeSuffix("connect")
                path.endsWith("/connect/") -> path.removeSuffix("connect/")
                else -> path
            }.ifBlank { "/" }
            val cleanUrl = cleanPath + window.location.hash
            window.history.replaceState(null, "", cleanUrl)
        }
    }
}

private data class PortalFileItem(
    val name: String,
    val path: String,
    val directory: Boolean,
    val sizeBytes: Long?,
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

private fun formatSize(sizeBytes: Long?): String =
    when {
        sizeBytes == null -> ""
        sizeBytes < 1024 -> "$sizeBytes B"
        sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
        else -> "${sizeBytes / (1024 * 1024)} MB"
    }

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
