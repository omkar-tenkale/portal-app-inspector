package io.github.portalappinspector.app.data

import io.github.portalappinspector.app.features.files.PortalFileItem
import io.github.portalappinspector.app.features.logs.PortalLogCallSite
import io.github.portalappinspector.app.features.logs.PortalLogEntry
import io.github.portalappinspector.app.features.logs.PortalLogRow
import io.github.portalappinspector.app.features.network.NetworkFilterMode
import io.github.portalappinspector.app.features.network.NetworkFilterRule
import io.github.portalappinspector.app.features.network.NetworkGapType
import io.github.portalappinspector.app.features.network.NetworkHighlightMatch
import io.github.portalappinspector.app.features.network.PortalNetworkBodyResponse
import io.github.portalappinspector.app.features.network.PortalNetworkCall
import io.github.portalappinspector.app.features.network.PortalNetworkMock
import io.github.portalappinspector.app.features.network.PortalNetworkMockExpectation
import io.github.portalappinspector.app.features.network.PortalNetworkMockMatcher
import io.github.portalappinspector.app.features.network.PortalNetworkMockResponse
import io.github.portalappinspector.app.features.network.PortalNetworkRow
import io.github.portalappinspector.app.features.screenmirror.PortalScreenMirrorFrame
import io.github.portalappinspector.app.features.sharedprefs.PortalSharedPrefItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.random.Random
import io.github.portalappinspector.app.util.*
import io.viascom.nanoid.NanoId
import kotlinx.serialization.json.Json

internal val networkJson = Json {
    ignoreUnknownKeys = true // Ignore extra keys coming from incoming requests
    encodeDefaults = true    // Include properties with default values when serializing
    prettyPrint = false      // Keep payload minimal for network transport
}

internal fun createMockFromCall(call: PortalNetworkCall): PortalNetworkMock {
    val now = nowEpochMillis()
    val urlParts = call.url.networkUrlParts()
    return PortalNetworkMock(
        id = NanoId.generate(),
        enabled = true,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
        expectation = PortalNetworkMockExpectation(
            matchers = listOf(
                PortalNetworkMockMatcher(type = "domain", value = urlParts.domain),
                PortalNetworkMockMatcher(type = "httpMethod", value = call.method),
                PortalNetworkMockMatcher(type = "urlPathContains", value = urlParts.path),
            ).filter { it.value.isNotBlank() },
        ),
        response = PortalNetworkMockResponse(
            body = PortalNetworkBodyResponse(
                code = call.statusCode ?: 200,
                body = call.responseBody.orEmpty(),
                contentType = call.responseContentType ?: "application/json",
            ),
        ),
    )
}

internal data class NetworkUrlParts(
    val domain: String,
    val path: String,
)

internal fun String.networkUrlParts(): NetworkUrlParts {
    val afterScheme = substringAfter("://", this)
    val host = afterScheme.substringBefore("/").substringBefore("?")
    val pathAndQuery = afterScheme.substringAfter("/", "")
    return NetworkUrlParts(
        domain = host.substringBefore(":"),
        path = if (pathAndQuery.isBlank()) "/" else "/${pathAndQuery.substringBefore("?")}",
    )
}

internal fun PortalNetworkMock.displayLabel(): String {
    val values = expectation.matchers
        .mapNotNull { matcher ->
            matcher.toSimpleMockMatcher().conditionChipLabel().takeIf { matcher.value.isNotBlank() }
        }
        .filter { it.isNotBlank() }
    return values.take(3).joinToString(" • ").ifBlank { "Untitled mock" }
}

internal fun List<PortalNetworkMockMatcher>.toSimpleMockMatchers(): List<PortalNetworkMockMatcher> =
    map { it.toSimpleMockMatcher() }

internal val MockConditionTypes = listOf(
    "domain",
    "urlPathContains",
    "httpMethod",
    "urlContains",
    "headersContainText",
    "requestBodyContainsTextNormalized",
)

internal val NetworkFilterTypes = listOf(
    "urlContains",
    "methodContains",
    "requestBodyContainsTextNormalized",
    "headersContainText",
)

internal fun PortalNetworkMockMatcher.matches(call: PortalNetworkCall): Boolean {
    val needle = value.trim()
    if (needle.isBlank()) return true
    return when (type) {
        "urlContains" -> call.url.contains(needle, ignoreCase = true) ||
            call.endpoint.contains(needle, ignoreCase = true)
        "domain" -> call.url.networkUrlParts().domain.contains(needle, ignoreCase = true)
        "urlPathContains" -> call.url.networkUrlParts().path.contains(needle, ignoreCase = true) ||
            call.endpoint.substringBefore('?').contains(needle, ignoreCase = true)
        "httpMethod" -> call.method.equals(needle, ignoreCase = true)
        "methodContains" -> call.method.contains(needle, ignoreCase = true)
        "requestBodyContainsTextNormalized" -> {
            val normalizedNeedle = needle.withoutNetworkFilterInvisibleChars()
            call.networkBodies().any { body ->
                body.withoutNetworkFilterInvisibleChars().contains(normalizedNeedle, ignoreCase = true)
            }
        }
        "headersContainText" -> call.networkHeaderLines().any { it.contains(needle, ignoreCase = true) }
        else -> false
    }
}

internal fun List<NetworkFilterRule>.matchesNetworkCall(call: PortalNetworkCall): Boolean {
    val includeRules = rulesFor(NetworkFilterMode.Include)
    val excludeRules = rulesFor(NetworkFilterMode.Exclude)
    return includeRules.matchesGrouped(call, emptyRulesMatch = true) &&
        !excludeRules.any { it.matcher.matches(call) }
}

internal fun List<NetworkFilterRule>.firstHighlightFor(call: PortalNetworkCall): NetworkHighlightMatch? =
    rulesFor(NetworkFilterMode.Highlight)
        .firstOrNull { it.matcher.matches(call) }
        ?.let { rule ->
            NetworkHighlightMatch(
                query = rule.matcher.value.trim(),
                snippet = call.matchSnippet(rule.matcher) ?: rule.matcher.value.trim(),
            )
        }

private fun List<NetworkFilterRule>.rulesFor(mode: NetworkFilterMode): List<NetworkFilterRule> =
    filter { it.mode == mode && it.matcher.value.isNotBlank() }

private fun List<NetworkFilterRule>.matchesGrouped(
    call: PortalNetworkCall,
    emptyRulesMatch: Boolean,
): Boolean {
    if (isEmpty()) return emptyRulesMatch
    return groupBy { it.matcher.type }.all { (_, fieldRules) ->
        fieldRules.any { it.matcher.matches(call) }
    }
}

private fun PortalNetworkCall.networkBodies(): List<String> =
    listOfNotNull(requestBody, responseBody).filter { it.isNotBlank() }

private fun PortalNetworkCall.networkHeaderLines(): List<String> =
    buildList {
        requestHeaders.addHeaderLinesTo(this)
        responseHeaders.addHeaderLinesTo(this)
    }

private fun Map<String, List<String>>.addHeaderLinesTo(target: MutableList<String>) {
    entries.forEach { (name, values) ->
        if (values.isEmpty()) {
            target += name
        } else {
            values.forEach { value -> target += "$name: $value" }
        }
    }
}

private fun PortalNetworkCall.matchSnippet(matcher: PortalNetworkMockMatcher): String? {
    val needle = matcher.value.trim()
    if (needle.isBlank()) return null
    return when (matcher.type) {
        "urlContains" -> listOf(url, endpoint).firstSnippet(needle)
        "domain" -> listOf(url.networkUrlParts().domain).firstSnippet(needle)
        "urlPathContains" -> listOf(url.networkUrlParts().path, endpoint.substringBefore('?')).firstSnippet(needle)
        "httpMethod" -> listOf(method).firstSnippet(needle)
        "methodContains" -> listOf(method).firstSnippet(needle)
        "requestBodyContainsTextNormalized" -> networkBodies().firstSnippet(needle)
        "headersContainText" -> networkHeaderLines().firstSnippet(needle)
        else -> null
    }
}

private fun List<String>.firstSnippet(needle: String): String? =
    firstNotNullOfOrNull { candidate -> candidate.snippetAround(needle) }

private fun String.snippetAround(needle: String, context: Int = 14): String? {
    val index = indexOf(needle, ignoreCase = true)
    if (index < 0) return null
    val start = (index - context).coerceAtLeast(0)
    val end = (index + needle.length + context).coerceAtMost(length)
    val prefix = if (start > 0) ".." else ""
    val suffix = if (end < length) ".." else ""
    return prefix + substring(start, end).replace('\n', ' ').replace('\r', ' ') + suffix
}

internal fun String.withoutNetworkFilterInvisibleChars(): String =
    filterNot { it.isWhitespace() }

private fun JsonElement?.parseStringListMap(): Map<String, List<String>> {
    val json = runCatching { this?.jsonObject }.getOrNull() ?: return emptyMap()
    return json.mapValues { (_, value) ->
        runCatching {
            value.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrElse {
            listOfNotNull(value.jsonPrimitive.contentOrNull)
        }
    }
}

internal fun PortalNetworkMockMatcher.toSimpleMockMatcher(): PortalNetworkMockMatcher =
    when (type) {
        "urlContains" -> this
        "domain" -> this
        "urlPathContains" -> this
        "httpMethod" -> this
        "headersContainText" -> this
        "requestBodyContainsTextNormalized" -> this
        "methodContains" -> this
        "queryEquals" -> copy(type = "urlContains", key = null, value = listOfNotNull(key, value).joinToString("="))
        "requestBodyContainsText" -> copy(type = "requestBodyContainsTextNormalized", key = null)
        "headerContains" -> copy(type = "headersContainText", key = null, value = listOfNotNull(key, value).joinToString(":"))
        else -> copy(type = "urlContains", key = null)
    }

internal fun PortalNetworkMockMatcher.conditionChipLabel(): String =
    "\"$value\" in ${type.matcherLabel()}"

internal fun String.matcherLabel(): String =
    when (this) {
        "urlContains" -> "url"
        "headersContainText" -> "headers"
        "requestBodyContainsTextNormalized" -> "body"
        "methodContains" -> "method"
        "domain" -> "domain"
        "httpMethod" -> "method"
        "urlPathContains" -> "path"
        "queryEquals" -> "url"
        "requestBodyContainsText" -> "body"
        "headerContains" -> "headers"
        else -> this
    }

internal fun String.logSourceLabel(): String =
    when (this) {
        "androidLog" -> "Android Log"
        "systemPrint" -> "System print"
        else -> this
    }

internal fun PortalLogEntry.logLine(): String {
    val line = message.takeIf { it.isNotBlank() }
        ?: throwable?.takeIf { it.isNotBlank() }
        ?: "(empty)"
    return line.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "(empty)" }
}

internal fun PortalLogCallSite.shortLabel(): String {
    val file = fileName?.takeIf { it.isNotBlank() }
    return when {
        file != null && lineNumber != null -> "$file:$lineNumber"
        file != null -> file
        methodName.isNotBlank() -> "${className.substringAfterLast('.')}.$methodName"
        else -> className.substringAfterLast('.')
    }
}

internal fun PortalLogCallSite.detailLabel(): String {
    val location = shortLabel()
    val method = methodName.takeIf { it.isNotBlank() } ?: return location
    val owner = className.substringAfterLast('.').takeIf { it.isNotBlank() }
    return if (owner == null) {
        "$location in $method"
    } else {
        "$location in $owner.$method"
    }
}

internal fun List<PortalLogEntry>.toLogRows(gapThresholdMillis: Long): List<PortalLogRow> {
    if (isEmpty()) return emptyList()
    val rows = mutableListOf<PortalLogRow>()
    forEachIndexed { index, entry ->
        if (index > 0) {
            val previous = this[index - 1]
            val gap = previous.timestampEpochMillis - entry.timestampEpochMillis
            if (gap >= gapThresholdMillis) {
                rows += PortalLogRow.Gap(
                    beforeId = previous.id,
                    afterId = entry.id,
                    durationMillis = gap,
                )
            }
        }
        rows += PortalLogRow.Entry(entry)
    }
    return rows
}

internal fun List<PortalNetworkCall>.toNetworkRows(nowEpochMillis: Long): List<PortalNetworkRow> {
    if (isEmpty()) return emptyList()

    val t1 = nowEpochMillis - 60_000L
    val t2 = nowEpochMillis - 86_400_000L
    val t3 = nowEpochMillis - 172_800_000L

    val newestTs = first().timestampEpochMillis
    val oldestTs = last().timestampEpochMillis

    val allowOneMinGap = newestTs >= t1 && oldestTs < t1
    val allowYesterdayGap = newestTs >= t2 && oldestTs < t2
    val allowLongTimeAgoGap = newestTs >= t3 && oldestTs < t3

    val rows = mutableListOf<PortalNetworkRow>()
    var addedOneMinGap = false
    var addedYesterdayGap = false
    var addedLongTimeAgoGap = false

    forEach { call ->
        val ts = call.timestampEpochMillis

        if (allowOneMinGap && !addedOneMinGap && ts < t1) {
            rows += PortalNetworkRow.Gap(NetworkGapType.OneMinuteAgo)
            addedOneMinGap = true
        }

        if (allowYesterdayGap && !addedYesterdayGap && ts < t2) {
            rows += PortalNetworkRow.Gap(NetworkGapType.Yesterday)
            addedYesterdayGap = true
        }

        if (allowLongTimeAgoGap && !addedLongTimeAgoGap && ts < t3) {
            rows += PortalNetworkRow.Gap(NetworkGapType.ALongTimeAgo)
            addedLongTimeAgoGap = true
        }

        rows += PortalNetworkRow.Call(call)
    }

    return rows
}

internal fun List<String>.toJsonArray(): JsonArray =
    buildJsonArray { forEach { add(it) } }
