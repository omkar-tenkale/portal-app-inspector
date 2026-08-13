package io.github.portalappinspector.app.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLTextAreaElement
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.Image as SkiaImage

internal actual fun platformGetUrlQueryString(): String = window.location.search

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
internal actual fun platformDecodeURIComponent(value: String): String = js("decodeURIComponent(value)")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
internal actual fun platformEncodeURIComponent(value: String): String = js("encodeURIComponent(value)")

internal actual fun platformNavigateToApp(appId: String) {
    val nextPath = "/?appId=${platformEncodeURIComponent(appId)}"
    window.history.replaceState(null, "", nextPath)
}

internal actual fun platformLoadFromStorage(key: String): String? = window.localStorage.getItem(key)

internal actual fun platformSaveToStorage(key: String, value: String) {
    window.localStorage.setItem(key, value)
}

@OptIn(ExperimentalEncodingApi::class)
internal actual fun platformDecodeBase64ToImageBitmap(base64: String): ImageBitmap? = runCatching {
    SkiaBitmap.makeFromImage(SkiaImage.makeFromEncoded(Base64.decode(base64))).asComposeImageBitmap()
}.getOrNull()

internal actual fun platformDecodeByteArrayToImageBitmap(bytes: ByteArray): ImageBitmap? = runCatching {
    SkiaBitmap.makeFromImage(SkiaImage.makeFromEncoded(bytes)).asComposeImageBitmap()
}.getOrNull()

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
internal actual fun platformNowEpochMillis(): Double = js("Date.now()")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
internal actual fun platformTimezoneOffsetMinutes(): Double = js("new Date().getTimezoneOffset()")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
internal actual fun platformUses12HourClock(): Boolean = js("(() => { const hourCycle = new Intl.DateTimeFormat(undefined, { hour: 'numeric' }).resolvedOptions().hourCycle; return hourCycle === 'h11' || hourCycle === 'h12'; })()")

internal actual fun platformCopyTextToClipboard(text: String) {
    val input = document.createElement("textarea") as HTMLTextAreaElement
    input.value = text
    input.style.position = "fixed"
    input.style.opacity = "0"
    document.body?.appendChild(input)
    input.select()
    document.execCommand("copy")
    document.body?.removeChild(input)
}

internal actual fun platformDownloadBase64File(base64: String, fileName: String) {
    val link = document.createElement("a") as HTMLAnchorElement
    link.href = "data:application/octet-stream;base64,$base64"
    link.download = fileName.ifBlank { "download" }
    document.body?.appendChild(link)
    link.click()
    document.body?.removeChild(link)
}