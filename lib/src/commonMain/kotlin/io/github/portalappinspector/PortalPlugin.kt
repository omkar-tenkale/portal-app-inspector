package io.github.portalappinspector

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

interface PortalPlugin {
    val id: String
    val name: String
    val version: Long

    suspend fun handle(request: PortalPluginRequest): PortalPluginResponse
}

interface PortalStreamingPlugin : PortalPlugin {
    fun openStream(request: PortalStreamRequest): PortalPluginStream?
}

data class PortalStreamRequest(
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
)

interface PortalPluginStream {
    fun onOpen(sink: PortalStreamSink)
    fun onText(message: String) {}
    fun onBinary(bytes: ByteArray) {}
    fun onClose() {}
    fun onError(error: Throwable) {}
}

interface PortalStreamSink {
    fun sendText(message: String)
    fun sendBinary(bytes: ByteArray)
    fun close()
}

@Serializable
data class PortalPluginRequest(
    val id: Long,
    val pluginId: String,
    val payload: JsonObject,
)

@Serializable
data class PortalPluginResponse(
    val id: Long,
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
    val appId: String,
    val appName: String,
    val platform: String,
    val appIconPngBase64: String? = null,
    val plugins: List<PortalPluginManifest>,
)

@Serializable
data class PortalPluginManifest(
    val id: String,
    val name: String,
    val version: Long,
)

@Serializable
data class PortalHealth(
    val ok: Boolean,
    val appId: String,
    val appName: String,
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
