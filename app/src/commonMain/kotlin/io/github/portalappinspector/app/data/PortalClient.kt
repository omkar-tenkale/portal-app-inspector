package io.github.portalappinspector.app.data

import io.github.portalappinspector.PortalHealth
import io.github.portalappinspector.PortalManifest
import io.github.portalappinspector.PortalPluginRequest
import io.github.portalappinspector.PortalRpcBatchRequest
import io.github.portalappinspector.PortalRpcBatchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

interface PortalApi {
    val appId: String
    fun hasPlugin(pluginId: String): Boolean
    suspend fun request(pluginId: String, payload: JsonObject): JsonObject
    suspend fun webSocket(pluginId: String, streamPath: String, query: String? = null, block: suspend DefaultClientWebSocketSession.() -> Unit)
}

internal class PortalSession(
    val connection: PortalConnection,
    val manifest: PortalManifest,
    private val client: PortalSourceClient,
    private val onConnectionFailing: () -> Unit
) : PortalApi {
    override val appId: String get() = manifest.appId

    private var consecutiveFailures = 0

    override fun hasPlugin(pluginId: String): Boolean =
        manifest.plugins.any { it.id == pluginId }

    override suspend fun request(pluginId: String, payload: JsonObject): JsonObject {
        return try {
            val res = client.request(connection, pluginId, payload)
            consecutiveFailures = 0
            res
        } catch (e: Exception) {
            consecutiveFailures++
            if (consecutiveFailures >= 5) {
                onConnectionFailing()
            }
            throw e
        }
    }

    override suspend fun webSocket(pluginId: String, streamPath: String, query: String?, block: suspend DefaultClientWebSocketSession.() -> Unit) {
        val baseUrl = connection.baseUrl.trimEnd('/')
        val wsBaseUrl = when {
            baseUrl.startsWith("https://") -> "wss://${baseUrl.removePrefix("https://")}"
            baseUrl.startsWith("http://") -> "ws://${baseUrl.removePrefix("http://")}"
            else -> "ws://$baseUrl"
        }
        val q = query?.let { "?$it" } ?: ""
        val url = "$wsBaseUrl/portal/plugins/$pluginId/streams/$streamPath$q"

        client.streamClient.webSocket(url) {
            block()
        }
    }
}

internal class PortalSourceClient {
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    internal val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 1_000L
            connectTimeoutMillis = 1_000L
            socketTimeoutMillis = 1_000L
        }
        install(ContentNegotiation) {
            json(json)
        }
    }
    internal val streamClient = HttpClient {
        install(WebSockets) {
            maxFrameSize = Int.MAX_VALUE.toLong()
        }
    }
    internal var nextRequestId = 0L

//    suspend fun health(connection: PortalConnection): PortalHealth =
//        httpClient.get("${connection.baseUrl}/portal/health").body()

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

    internal fun nextId(): Long {
        nextRequestId += 1
        return nextRequestId
    }
}
