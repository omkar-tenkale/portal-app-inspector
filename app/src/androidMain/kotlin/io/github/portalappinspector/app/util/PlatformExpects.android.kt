package io.github.portalappinspector.app.util

import androidx.compose.ui.graphics.ImageBitmap

internal actual fun platformGetUrlQueryString(): String = ""
internal actual fun platformDecodeURIComponent(value: String): String = value
internal actual fun platformEncodeURIComponent(value: String): String = value
internal actual fun platformNavigateToApp(appId: String) {}

internal actual fun platformLoadFromStorage(key: String): String? = null
internal actual fun platformSaveToStorage(key: String, value: String) {}

internal actual fun platformDecodeBase64ToImageBitmap(base64: String): ImageBitmap? = null
internal actual fun platformDecodeByteArrayToImageBitmap(bytes: ByteArray): ImageBitmap? = null

internal actual fun platformNowEpochMillis(): Double = System.currentTimeMillis().toDouble()
internal actual fun platformTimezoneOffsetMinutes(): Double = 0.0
internal actual fun platformUses12HourClock(): Boolean = false

internal actual fun platformCopyTextToClipboard(text: String) {}
internal actual fun platformDownloadBase64File(base64: String, fileName: String) {}