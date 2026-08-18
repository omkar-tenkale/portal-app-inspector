package io.github.portalappinspector.app.features.network

import kotlinx.serialization.Serializable

@Serializable
internal data class PortalNetworkErrorResponse(
    val type: String,
)

internal data class PortalNetworkCall(
    val id: Long,
    val timestampEpochMillis: Long,
    val method: String,
    val url: String,
    val endpoint: String,
    val requestHeaders: Map<String, List<String>> = emptyMap(),
    val requestBody: String? = null,
    val requestContentType: String? = null,
    val requestBodySizeBytes: Long? = null,
    val requestBodyTruncated: Boolean = false,
    val statusCode: Int?,
    val durationMillis: Long,
    val error: String?,
    val responseHeaders: Map<String, List<String>> = emptyMap(),
    val responseBody: String?,
    val responseContentType: String?,
    val responseBodySizeBytes: Long?,
    val responseBodyTruncated: Boolean,
    val isMocked: Boolean,
)

internal enum class NetworkFilterMode(val label: String) {
    Include("Include"),
    Exclude("Exclude"),
    Highlight("Highlight"),
}

internal data class NetworkFilterRule(
    val mode: NetworkFilterMode,
    val matcher: PortalNetworkMockMatcher,
)

internal data class NetworkHighlightMatch(
    val query: String,
    val snippet: String,
)

internal enum class NetworkGapType {
    OneMinuteAgo,
    Yesterday,
    ALongTimeAgo,
}

internal sealed interface PortalNetworkRow {
    val key: String

    data class Call(
        val call: PortalNetworkCall,
    ) : PortalNetworkRow {
        override val key: String = "network-call-${call.id}"
    }

    data class Gap(
        val type: NetworkGapType,
    ) : PortalNetworkRow {
        override val key: String = "network-gap-${type.name}"
    }
}

@Serializable
internal data class PortalNetworkMock(
    val id: String,
    val enabled: Boolean = true,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
    val expectation: PortalNetworkMockExpectation = PortalNetworkMockExpectation(),
    val response: PortalNetworkMockResponse = PortalNetworkMockResponse(),
)

@Serializable
internal data class PortalNetworkMockExpectation(
    val delayMs: Long = 0L,
    val matchers: List<PortalNetworkMockMatcher> = emptyList(),
)

@Serializable
internal data class PortalNetworkMockMatcher(
    val type: String,
    val key: String? = null,
    val value: String = "",
)

@Serializable
internal data class PortalNetworkMockResponse(
    val body: PortalNetworkBodyResponse? = null,
    val error: PortalNetworkErrorResponse? = null,
) {
    fun label(): String =
        error?.let { "Error ${it.type}" } ?: "HTTP ${body?.code ?: 200}"
}

@Serializable
internal data class PortalNetworkBodyResponse(
    val code: Int = 200,
    val body: String = "",
    val contentType: String = "application/json",
    val headers: Map<String, String> = emptyMap(),
)

internal val PortalNetworkErrorTypes = listOf(
    "ioException",
    "socketTimeout",
    "unknownHost",
    "connectionRefused",
    "sslHandshake",
    "jsonParse",
)