package io.github.portalappinspector.app.util

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal fun formatRelativeLogTime(
    epochMillis: Long,
    nowEpochMillis: Long,
): String? {
    val ageSeconds = ((nowEpochMillis - epochMillis) / 1_000L).coerceAtLeast(0L)
    return when {
        ageSeconds < 10L -> "just now"
        ageSeconds < 20L -> "10 sec"
        ageSeconds < 30L -> "20 sec"
        ageSeconds < 60L -> "30 sec"
        ageSeconds < 360L -> "${(ageSeconds / 60L).coerceAtMost(5L)} min"
        else -> null
    }
}

internal fun formatAbsoluteLogTime(
    epochMillis: Long,
    timezoneOffsetMinutes: Long,
    use12HourClock: Boolean,
): String {
    val localEpochMillis = epochMillis - (timezoneOffsetMinutes * 60_000L)
    val secondsOfDay = ((localEpochMillis / 1_000L) % 86_400L + 86_400L) % 86_400L
    val hours = secondsOfDay / 3_600L
    val minutes = (secondsOfDay / 60L) % 60L
    val seconds = secondsOfDay % 60L
    if (use12HourClock) {
        val hour12 = (hours % 12L).takeIf { it != 0L } ?: 12L
        return "$hour12:${minutes.twoDigits()}:${seconds.twoDigits()}"
    }
    return "${hours.twoDigits()}:${minutes.twoDigits()}:${seconds.twoDigits()}"
}

internal fun formatTimeHHMM(
    epochMillis: Long,
    timezoneOffsetMinutes: Long,
    use12HourClock: Boolean,
): String {
    val localEpochMillis = epochMillis - (timezoneOffsetMinutes * 60_000L)
    val secondsOfDay = ((localEpochMillis / 1_000L) % 86_400L + 86_400L) % 86_400L
    val hours = secondsOfDay / 3_600L
    val minutes = (secondsOfDay / 60L) % 60L
    if (use12HourClock) {
        val hour12 = (hours % 12L).takeIf { it != 0L } ?: 12L
        val amPm = if (hours >= 12L) "PM" else "AM"
        return "$hour12:${minutes.twoDigits()} $amPm"
    }
    return "${hours.twoDigits()}:${minutes.twoDigits()}"
}

internal fun formatLogGap(durationMillis: Long): String {
    val seconds = (durationMillis / 1_000L).coerceAtLeast(1L)
    val minutes = seconds / 60L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        seconds < 60L -> "$seconds sec"
        minutes < 60L -> "$minutes min"
        hours < 24L -> "$hours hr"
        else -> "$days day"
    }
}

internal fun Long.twoDigits(): String =
    toString().padStart(2, '0')

internal fun Map<String, String>.toHeaderLines(): String =
    entries.joinToString("\n") { (key, value) -> "$key: $value" }

internal fun String.toHeaderMap(): Map<String, String> =
    lineSequence()
        .mapNotNull { line ->
            val index = line.indexOf(':')
            if (index <= 0) {
                null
            } else {
                val key = line.substring(0, index).trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                key to line.substring(index + 1).trim()
            }
        }
        .toMap()

internal val PrettyJson = Json {
    prettyPrint = true
}

internal fun formatJsonOrNull(value: String): String? =
    runCatching {
        val element: JsonElement = Json.parseToJsonElement(value)
        PrettyJson.encodeToString(JsonElement.serializer(), element)
    }.getOrNull()

internal fun String.toImageBitmapOrNull(): ImageBitmap? =
    platformDecodeBase64ToImageBitmap(this)

internal fun ByteArray.toImageBitmapOrNull(): ImageBitmap? =
    platformDecodeByteArrayToImageBitmap(this)

@OptIn(ExperimentalEncodingApi::class)
internal fun decodeBase64ToText(value: String): String =
    runCatching { Base64.decode(value).decodeToString() }
        .getOrElse { "" }

internal fun formatSize(sizeBytes: Long?): String =
    when {
        sizeBytes == null -> ""
        sizeBytes < 1024 -> "$sizeBytes B"
        sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
        else -> "${sizeBytes / (1024 * 1024)} MB"
    }

internal fun formatNetworkSize(sizeBytes: Long?): String {
    if (sizeBytes == null) return "-"
    val tenths = ((sizeBytes * 10L) + 512L) / 1024L
    return if (tenths < 10L) {
        "0.${tenths} KB"
    } else {
        "${tenths / 10L}.${tenths % 10L} KB"
    }
}

internal fun jsDateNow(): Double = platformNowEpochMillis()

internal fun jsTimezoneOffsetMinutes(): Double = platformTimezoneOffsetMinutes()

internal fun jsUses12HourClock(): Boolean = platformUses12HourClock()

internal fun copyTextToClipboard(text: String) {
    platformCopyTextToClipboard(text)
}

internal fun downloadBase64File(base64: String, fileName: String) {
    platformDownloadBase64File(base64, fileName)
}

internal fun nowEpochMillis(): Long =
    jsDateNow().toLong()