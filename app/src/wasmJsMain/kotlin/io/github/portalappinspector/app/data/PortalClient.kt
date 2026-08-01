package io.github.portalappinspector.app.data

import io.github.portalappinspector.PortalHealth
import io.github.portalappinspector.PortalManifest
import io.ktor.client.request.get
import io.ktor.client.call.body
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.JsonObject
import io.github.portalappinspector.PortalPluginRequest
import io.github.portalappinspector.PortalRpcBatchRequest
import io.github.portalappinspector.PortalRpcBatchResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import kotlinx.serialization.json.Json

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
    internal var nextRequestId = 0

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

    internal fun nextId(): Int {
        nextRequestId += 1
        return nextRequestId
    }
}
