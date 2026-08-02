package io.github.portalappinspector.app.util

import androidx.compose.runtime.key
import androidx.compose.ui.input.key.key
import kotlinx.serialization.encodeToString
import io.ktor.client.call.body
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlinx.browser.document
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLTextAreaElement
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.Image as SkiaImage

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
        val suffix = if (hours < 12L) "AM" else "PM"
        return "$hour12:${minutes.twoDigits()}:${seconds.twoDigits()} $suffix"
    }
    return "${hours.twoDigits()}:${minutes.twoDigits()}:${seconds.twoDigits()}"
}

internal fun formatLogGap(durationMillis: Long): String {
    val seconds = (durationMillis / 1_000L).coerceAtLeast(1L)
    return if (seconds < 60L) {
        "$seconds sec"
    } else {
        "${seconds / 60L} min"
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

@OptIn(ExperimentalEncodingApi::class)
internal fun String.toImageBitmapOrNull(): ImageBitmap? =
    runCatching {
        SkiaBitmap.makeFromImage(SkiaImage.makeFromEncoded(Base64.decode(this))).asComposeImageBitmap()
    }.getOrNull()

internal fun ByteArray.toImageBitmapOrNull(): ImageBitmap? =
    runCatching {
        SkiaBitmap.makeFromImage(SkiaImage.makeFromEncoded(this)).asComposeImageBitmap()
    }.getOrNull()

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

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
internal fun jsDateNow(): Double =
    js("Date.now()")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
internal fun jsTimezoneOffsetMinutes(): Double =
    js("new Date().getTimezoneOffset()")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
internal fun jsUses12HourClock(): Boolean =
    js("(() => { const hourCycle = new Intl.DateTimeFormat(undefined, { hour: 'numeric' }).resolvedOptions().hourCycle; return hourCycle === 'h11' || hourCycle === 'h12'; })()")

internal fun copyTextToClipboard(text: String) {
    val input = document.createElement("textarea") as HTMLTextAreaElement
    input.value = text
    input.style.position = "fixed"
    input.style.opacity = "0"
    document.body?.appendChild(input)
    input.select()
    document.execCommand("copy")
    document.body?.removeChild(input)
}

internal fun downloadBase64File(base64: String, fileName: String) {
    val link = document.createElement("a") as HTMLAnchorElement
    link.href = "data:application/octet-stream;base64,$base64"
    link.download = fileName.ifBlank { "download" }
    document.body?.appendChild(link)
    link.click()
    document.body?.removeChild(link)
}

internal fun nowEpochMillis(): Long =
    jsDateNow().toLong()
