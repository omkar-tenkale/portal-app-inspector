package io.github.portalappinspector.app.features.files

import kotlinx.serialization.Serializable

@Serializable
internal data class PortalPinnedFileItem(
    val name: String,
    val path: String,
    val absolutePath: String? = null,
    val directory: Boolean,
    val sizeBytes: Long?,
    val pinnedAtEpochMillis: Long,
) {
    fun toFileItem(): PortalFileItem =
        PortalFileItem(
            name = name,
            path = path,
            absolutePath = absolutePath,
            directory = directory,
            sizeBytes = sizeBytes,
        )
}
@Serializable
internal data class PortalFileItem(
    val name: String,
    val path: String,
    val absolutePath: String?,
    val directory: Boolean,
    val sizeBytes: Long?,
)

internal data class PortalViewedFile(
    val item: PortalFileItem,
    val text: String,
)