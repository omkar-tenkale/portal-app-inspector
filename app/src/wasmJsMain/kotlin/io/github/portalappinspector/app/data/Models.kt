package io.github.portalappinspector.app.data

import androidx.compose.runtime.key
import androidx.compose.ui.input.key.key
import kotlinx.serialization.Serializable
import androidx.compose.ui.input.key.type
import io.ktor.client.call.body
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
internal data class PortalPinnedFileItem(
    val name: String,
    val path: String,
    val absolutePath: String? = null,
    val directory: Boolean,
    val sizeBytes: Long?,
    val pinnedAtEpochMillis: Long,
) {
    fun toFileItem(): PortalFileItem =
        PortalFileItem(
            name = name,
            path = path,
            absolutePath = absolutePath,
            directory = directory,
            sizeBytes = sizeBytes,
        )
}

internal data class PortalFileItem(
    val name: String,
    val path: String,
    val absolutePath: String?,
    val directory: Boolean,
    val sizeBytes: Long?,
)

internal data class PortalViewedFile(
    val item: PortalFileItem,
    val text: String,
)

internal data class PortalSharedPrefItem(
    val key: String,
    val valueType: String,
    val value: String,
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

internal data class PortalLogEntry(
    val id: Long,
    val timestampEpochMillis: Long,
    val level: String,
    val source: String,
    val stream: String?,
    val tag: String?,
    val message: String,
    val throwable: String?,
    val threadName: String,
    val location: PortalLogCallSite?,
    val callSites: List<PortalLogCallSite>,
)

internal data class PortalLogCallSite(
    val className: String,
    val methodName: String,
    val fileName: String?,
    val lineNumber: Int?,
)

internal data class PortalScreenMirrorFrame(
    val updatedAtEpochMillis: Long,
    val base64: String,
    val mimeType: String,
    val format: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)

internal sealed interface PortalLogRow {
    val key: String

    data class Entry(
        val entry: PortalLogEntry,
    ) : PortalLogRow {
        override val key: String = "log-entry-${entry.id}"
    }

    data class Gap(
        val beforeId: Long,
        val afterId: Long,
        val durationMillis: Long,
    ) : PortalLogRow {
        override val key: String = "log-gap-$beforeId-$afterId"
    }
}

internal data class PortalLogsFilter(
    val contains: List<String> = emptyList(),
    val levels: Set<String> = emptySet(),
    val sources: Set<String> = emptySet(),
) {
    fun toJson(): JsonObject =
        buildJsonObject {
            put("contains", contains.toJsonArray())
            put("levels", levels.toList().toJsonArray())
            put("sources", sources.toList().toJsonArray())
        }

    fun signature(): String =
        listOf(
            contains.joinToString("\u001F"),
            levels.sorted().joinToString("\u001F"),
            sources.sorted().joinToString("\u001F"),
        ).joinToString("\u001E")

    fun hasActiveFilters(): Boolean =
        contains.isNotEmpty() || levels.isNotEmpty() || sources.isNotEmpty()
}

internal val PortalLogLevels = listOf(
    "verbose",
    "debug",
    "info",
    "warning",
    "error",
    "assert",
)

internal val PortalLogSources = listOf(
    "androidLog",
    "systemPrint",
)

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

@Serializable
internal data class PortalNetworkErrorResponse(
    val type: String,
)

internal val PortalNetworkErrorTypes = listOf(
    "ioException",
    "socketTimeout",
    "unknownHost",
    "connectionRefused",
    "sslHandshake",
    "jsonParse",
)
