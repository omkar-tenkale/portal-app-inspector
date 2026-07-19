package io.github.portalappinspector

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

interface PortalPlugin {
    val id: String
    val name: String
    val version: String

    suspend fun handle(request: PortalPluginRequest): PortalPluginResponse
}

@Serializable
data class PortalPluginRequest(
    val id: Int,
    val pluginId: String,
    val payload: JsonObject,
)

@Serializable
data class PortalPluginResponse(
    val id: Int,
    val pluginId: String,
    val ok: Boolean,
    val payload: JsonObject? = null,
    val error: PortalError? = null,
)

@Serializable
data class PortalError(
    val code: String,
    val message: String,
)

@Serializable
data class PortalRpcBatchRequest(
    val requests: List<PortalPluginRequest>,
)

@Serializable
data class PortalRpcBatchResponse(
    val responses: List<PortalPluginResponse>,
)

@Serializable
data class PortalManifest(
    val protocolVersion: Int,
    val sourceName: String,
    val plugins: List<PortalPluginManifest>,
)

@Serializable
data class PortalPluginManifest(
    val id: String,
    val name: String,
    val version: String,
)

@Serializable
data class PortalHealth(
    val ok: Boolean,
    val sourceName: String,
    val protocolVersion: Int,
)

object PortalProtocol {
    const val Version = 1
}

fun portalPluginErrorResponse(
    request: PortalPluginRequest,
    code: String,
    message: String,
): PortalPluginResponse =
    PortalPluginResponse(
        id = request.id,
        pluginId = request.pluginId,
        ok = false,
        error = PortalError(code, message),
    )
