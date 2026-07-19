package io.github.portalappinspector.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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

private data object ConnectionTab : PortalTab {
    override val title: String = "Connection"
}

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
                row {
                    panel(weight = 0.32f) { tab(ConnectionTab) }
                    panel(weight = 0.68f) { tab(FilesTab) }
                }
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

        LaunchedEffect(Unit) {
            if (connection.sessionToken.isNotBlank()) {
                connect()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PortalColors.background),
        ) {
            TopBar()
            DockLayout(
                state = layoutState,
                renderers = tabRenderers {
                    renderer<ConnectionTab> { _, _ ->
                        ConnectionPanel(
                            connection = connection,
                            onConnectionChange = { connection = it },
                            health = health,
                            manifest = manifest,
                            error = error,
                            connecting = connecting,
                            onConnect = ::connect,
                        )
                    }
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
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(PortalColors.topBar)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Portal App Inspector",
            color = PortalColors.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Source browser",
            color = PortalColors.muted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ConnectionPanel(
    connection: PortalConnection,
    onConnectionChange: (PortalConnection) -> Unit,
    health: PortalHealth?,
    manifest: PortalManifest?,
    error: String?,
    connecting: Boolean,
    onConnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Source connection", color = PortalColors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = connection.host,
            onValueChange = { onConnectionChange(connection.copy(host = it)) },
            label = { Text("Host") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = connection.port,
            onValueChange = { onConnectionChange(connection.copy(port = it.filter(Char::isDigit))) },
            label = { Text("Port") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = connection.sessionToken,
            onValueChange = { onConnectionChange(connection.copy(sessionToken = it)) },
            label = { Text("Session token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onConnect, enabled = !connecting && connection.isValid) {
            Text(if (connecting) "Connecting..." else "Connect")
        }
        if (connecting) {
            CircularProgressIndicator()
        }
        error?.let {
            StatusCard("Connection failed", it, PortalColors.error)
        }
        health?.let {
            StatusCard("Connected", "${it.sourceName} · protocol ${it.protocolVersion}", PortalColors.success)
        }
        manifest?.let {
            Text("Plugins", color = PortalColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            it.plugins.forEach { plugin ->
                Text("${plugin.id} · ${plugin.version}", color = PortalColors.muted, fontSize = 13.sp)
            }
        }
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
        httpClient.get("${connection.baseUrl}/portal/health") {
            auth(connection)
        }.body()

    suspend fun manifest(connection: PortalConnection): PortalManifest =
        httpClient.get("${connection.baseUrl}/portal/manifest") {
            auth(connection)
        }.body()

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
                auth(connection)
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

    private fun io.ktor.client.request.HttpRequestBuilder.auth(connection: PortalConnection) {
        header(HttpHeaders.Authorization, "Bearer ${connection.sessionToken}")
    }
}

private data class PortalConnection(
    val host: String,
    val port: String,
    val sessionToken: String,
) {
    val isValid: Boolean
        get() = host.isNotBlank() && port.isNotBlank() && sessionToken.isNotBlank()

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
            return PortalConnection(
                host = params["host"].orEmpty(),
                port = params["port"] ?: "4896",
                sessionToken = params["sessionToken"].orEmpty(),
            )
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
    val background = Color(0xFF101113)
    val topBar = Color(0xFF191B20)
    val card = Color(0xFF20232A)
    val border = Color(0xFF343842)
    val text = Color(0xFFF4F6FA)
    val muted = Color(0xFF9AA3B2)
    val accent = Color(0xFF5EA1FF)
    val success = Color(0xFF40C463)
    val warning = Color(0xFFFFC857)
    val error = Color(0xFFFF6B6B)
}
