package io.github.portalappinspector.app.data

import androidx.compose.runtime.key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import io.ktor.client.call.body
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
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

internal fun parseFileItem(value: kotlinx.serialization.json.JsonElement): PortalFileItem {
    val item = value.jsonObject
    return PortalFileItem(
        name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        path = item["path"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        absolutePath = item["absolutePath"]?.jsonPrimitive?.contentOrNull,
        directory = item["directory"]?.jsonPrimitive?.contentOrNull == "true",
        sizeBytes = item["sizeBytes"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
    )
}

internal fun parseSharedPrefItem(value: JsonElement): PortalSharedPrefItem {
    val item = value.jsonObject
    return PortalSharedPrefItem(
        key = item["key"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        valueType = item["valueType"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        value = item["value"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    )
}

internal fun parseNetworkCall(value: kotlinx.serialization.json.JsonElement): PortalNetworkCall {
    val item = value.jsonObject
    return PortalNetworkCall(
        id = item["id"]?.jsonPrimitive?.longOrNull ?: 0L,
        timestampEpochMillis = item["timestampEpochMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
        method = item["method"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        url = item["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        endpoint = item["endpoint"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        statusCode = item["statusCode"]?.jsonPrimitive?.intOrNull,
        durationMillis = item["durationMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
        error = item["error"]?.jsonPrimitive?.contentOrNull,
        responseBody = item["responseBody"]?.jsonPrimitive?.contentOrNull,
        responseContentType = item["responseContentType"]?.jsonPrimitive?.contentOrNull,
        responseBodySizeBytes = item["responseBodySizeBytes"]?.jsonPrimitive?.longOrNull,
        responseBodyTruncated = item["responseBodyTruncated"]?.jsonPrimitive?.booleanOrNull == true,
        isMocked = item["isMocked"]?.jsonPrimitive?.booleanOrNull == true,
    )
}

internal fun parseLogEntry(value: kotlinx.serialization.json.JsonElement): PortalLogEntry {
    val item = value.jsonObject
    val callSites = item["callSites"]?.jsonArray?.mapNotNull(::parseLogCallSite).orEmpty()
    return PortalLogEntry(
        id = item["id"]?.jsonPrimitive?.longOrNull ?: 0L,
        timestampEpochMillis = item["timestampEpochMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
        level = item["level"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        source = item["source"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        stream = item["stream"]?.jsonPrimitive?.contentOrNull,
        tag = item["tag"]?.jsonPrimitive?.contentOrNull,
        message = item["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        throwable = item["throwable"]?.jsonPrimitive?.contentOrNull,
        threadName = item["threadName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        location = item["location"]?.let(::parseLogCallSite) ?: callSites.firstOrNull(),
        callSites = callSites,
    )
}

internal fun parseLogCallSite(value: JsonElement): PortalLogCallSite? {
    val item = runCatching { value.jsonObject }.getOrNull() ?: return null
    return PortalLogCallSite(
        className = item["className"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        methodName = item["methodName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        fileName = item["fileName"]?.jsonPrimitive?.contentOrNull,
        lineNumber = item["lineNumber"]?.jsonPrimitive?.intOrNull,
    ).takeIf { it.className.isNotBlank() || it.fileName?.isNotBlank() == true }
}

internal fun createMockFromCall(call: PortalNetworkCall): PortalNetworkMock {
    val now = nowEpochMillis()
    val urlParts = call.url.networkUrlParts()
    return PortalNetworkMock(
        id = newMockId(),
        enabled = true,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
        expectation = PortalNetworkMockExpectation(
            matchers = listOf(
                PortalNetworkMockMatcher(type = "urlContains", value = call.url.ifBlank { urlParts.domain }),
                PortalNetworkMockMatcher(type = "methodContains", value = call.method),
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

internal fun createBlankMock(): PortalNetworkMock {
    val now = nowEpochMillis()
    return PortalNetworkMock(
        id = newMockId(),
        enabled = true,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
        expectation = PortalNetworkMockExpectation(
            matchers = listOf(PortalNetworkMockMatcher(type = "methodContains", value = "GET")),
        ),
        response = PortalNetworkMockResponse(
            body = PortalNetworkBodyResponse(),
        ),
    )
}

internal fun newMockId(): String =
    "mock-${nowEpochMillis()}-${Random.nextInt(100_000, 999_999)}"

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
    "urlContains",
    "headersContainText",
    "requestBodyContainsTextNormalized",
    "methodContains",
)

internal val NetworkFilterTypes = listOf(
    "urlContains",
    "methodContains",
    "requestBodyContainsTextNormalized",
)

internal fun PortalNetworkMockMatcher.matches(call: PortalNetworkCall): Boolean {
    val needle = value.trim()
    if (needle.isBlank()) return true
    return when (type) {
        "urlContains" -> call.url.contains(needle, ignoreCase = true) ||
            call.endpoint.contains(needle, ignoreCase = true)
        "methodContains" -> call.method.contains(needle, ignoreCase = true)
        "requestBodyContainsTextNormalized" -> {
            val normalizedBody = call.responseBody?.withoutNetworkFilterInvisibleChars() ?: return false
            normalizedBody.contains(needle.withoutNetworkFilterInvisibleChars(), ignoreCase = true)
        }
        else -> false
    }
}

internal fun String.withoutNetworkFilterInvisibleChars(): String =
    filterNot { it.isWhitespace() }

internal fun PortalNetworkMockMatcher.toSimpleMockMatcher(): PortalNetworkMockMatcher =
    when (type) {
        "urlContains" -> this
        "headersContainText" -> this
        "requestBodyContainsTextNormalized" -> this
        "methodContains" -> this
        "domain",
        "urlPathContains" -> copy(type = "urlContains", key = null)
        "queryEquals" -> copy(type = "urlContains", key = null, value = listOfNotNull(key, value).joinToString("="))
        "requestBodyContainsText" -> copy(type = "requestBodyContainsTextNormalized", key = null)
        "headerContains" -> copy(type = "headersContainText", key = null, value = listOfNotNull(key, value).joinToString(":"))
        "httpMethod" -> copy(type = "methodContains", key = null)
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
        "domain" -> "url"
        "httpMethod" -> "method"
        "urlPathContains" -> "url"
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

internal fun List<PortalNetworkCall>.toNetworkRows(gapThresholdMillis: Long): List<PortalNetworkRow> {
    if (isEmpty()) return emptyList()
    val rows = mutableListOf<PortalNetworkRow>()
    forEachIndexed { index, call ->
        if (index > 0) {
            val previous = this[index - 1]
            val gap = previous.timestampEpochMillis - call.timestampEpochMillis
            if (gap >= gapThresholdMillis) {
                rows += PortalNetworkRow.Gap(
                    beforeId = previous.id,
                    afterId = call.id,
                    durationMillis = gap,
                )
            }
        }
        rows += PortalNetworkRow.Call(call)
    }
    return rows
}

internal fun List<String>.toJsonArray(): JsonArray =
    buildJsonArray { forEach { add(it) } }
