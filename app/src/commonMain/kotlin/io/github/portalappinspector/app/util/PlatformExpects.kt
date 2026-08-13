package io.github.portalappinspector.app.util

import androidx.compose.ui.graphics.ImageBitmap

internal expect fun platformGetUrlQueryString(): String
internal expect fun platformDecodeURIComponent(value: String): String
internal expect fun platformEncodeURIComponent(value: String): String
internal expect fun platformNavigateToApp(appId: String)

internal expect fun platformLoadFromStorage(key: String): String?
internal expect fun platformSaveToStorage(key: String, value: String)

internal expect fun platformDecodeBase64ToImageBitmap(base64: String): ImageBitmap?
internal expect fun platformDecodeByteArrayToImageBitmap(bytes: ByteArray): ImageBitmap?

internal expect fun platformNowEpochMillis(): Double
internal expect fun platformTimezoneOffsetMinutes(): Double
internal expect fun platformUses12HourClock(): Boolean

internal expect fun platformCopyTextToClipboard(text: String)
internal expect fun platformDownloadBase64File(base64: String, fileName: String)