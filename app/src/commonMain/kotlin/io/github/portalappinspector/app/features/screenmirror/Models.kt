package io.github.portalappinspector.app.features.screenmirror

internal data class PortalScreenMirrorFrame(
    val updatedAtEpochMillis: Long,
    val base64: String,
    val mimeType: String,
    val format: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)

