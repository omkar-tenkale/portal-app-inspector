package io.github.portalappinspector.app.features.logs

import io.github.portalappinspector.app.data.toJsonArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@Serializable
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

@Serializable
internal data class PortalLogCallSite(
    val className: String,
    val methodName: String,
    val fileName: String?,
    val lineNumber: Int?,
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