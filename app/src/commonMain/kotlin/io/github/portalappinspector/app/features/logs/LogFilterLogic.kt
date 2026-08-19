package io.github.portalappinspector.app.features.logs

import io.github.portalappinspector.app.data.logLine
import io.github.portalappinspector.app.features.network.NetworkFilterMode

internal const val AndroidLogSource = "androidLog"

internal val LogFilterTypes = listOf(
    "content",
    "tag",
    "level",
)

internal enum class LogFilterMenu {
    Mode,
    Type,
}

internal data class LogFilterRule(
    val mode: NetworkFilterMode,
    val type: String,
    val value: String,
)

internal data class LogHighlightMatch(
    val query: String,
)

internal fun List<LogFilterRule>.signature(): String =
    joinToString("\u001E") { "${it.mode.name}\u001F${it.type}\u001F${it.value}" }

internal fun List<LogFilterRule>.matchesLogEntry(entry: PortalLogEntry): Boolean {
    val includeRules = rulesFor(NetworkFilterMode.Include)
    val excludeRules = rulesFor(NetworkFilterMode.Exclude)
    return includeRules.matchesGrouped(entry, emptyRulesMatch = true) &&
        !excludeRules.any { it.matches(entry) }
}

internal fun List<LogFilterRule>.firstHighlightFor(entry: PortalLogEntry): LogHighlightMatch? =
    rulesFor(NetworkFilterMode.Highlight)
        .firstOrNull { it.matches(entry) }
        ?.let { LogHighlightMatch(query = it.value.trim()) }

internal fun List<LogFilterRule>.rulesFor(mode: NetworkFilterMode): List<LogFilterRule> =
    filter { it.mode == mode && it.value.isNotBlank() }

internal fun List<LogFilterRule>.matchesGrouped(
    entry: PortalLogEntry,
    emptyRulesMatch: Boolean,
): Boolean {
    if (isEmpty()) return emptyRulesMatch
    return groupBy { it.type }.all { (_, fieldRules) ->
        fieldRules.any { it.matches(entry) }
    }
}

internal fun LogFilterRule.matches(entry: PortalLogEntry): Boolean {
    val needle = value.trim()
    if (needle.isBlank()) return true
    return when (type) {
        "content" -> entry.logContentFields().any { it.contains(needle, ignoreCase = true) }
        "tag" -> entry.tag.orEmpty().contains(needle, ignoreCase = true)
        "level" -> entry.level.contains(needle, ignoreCase = true) ||
            logLevelInitial(entry.level).equals(needle, ignoreCase = true)
        else -> false
    }
}

internal fun PortalLogEntry.logContentFields(): List<String> =
    listOfNotNull(
        message,
        throwable,
        logLine(),
        threadName,
    ).filter { it.isNotBlank() }

internal fun LogFilterRule.label(): String =
    "${mode.label} \"$value\" in ${type.logFilterTypeLabel()}"

internal fun String.logFilterTypeLabel(): String =
    when (this) {
        "content" -> "content"
        "tag" -> "tag"
        "level" -> "level"
        else -> this
    }
