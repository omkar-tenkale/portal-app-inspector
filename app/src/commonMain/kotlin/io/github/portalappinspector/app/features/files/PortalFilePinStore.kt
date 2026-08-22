package io.github.portalappinspector.app.features.files

import io.github.portalappinspector.app.data.StorageJson
import io.github.portalappinspector.app.util.nowEpochMillis
import io.github.portalappinspector.app.util.platformLoadFromStorage
import io.github.portalappinspector.app.util.platformSaveToStorage

internal object PortalFilePinStore {

    private fun storageKey(appId: String) = "portal-app-inspector/apps/$appId/plugin-data/portal-files/pinned-paths"

    fun load(appId: String): List<PortalPinnedFileItem> =
        runCatching {
            val raw = platformLoadFromStorage(storageKey(appId)) ?: return emptyList()
            StorageJson.decodeFromString<List<PortalPinnedFileItem>>(raw)
        }.getOrDefault(emptyList())

    fun toggle(appId: String, item: PortalFileItem): List<PortalPinnedFileItem> {
        val existing = load(appId)
        val updated = if (existing.any { it.path == item.path }) {
            existing.filterNot { it.path == item.path }
        } else {
            listOf(
                PortalPinnedFileItem(
                    name = item.name,
                    path = item.path,
                    absolutePath = item.absolutePath,
                    directory = item.directory,
                    sizeBytes = item.sizeBytes,
                    pinnedAtEpochMillis = nowEpochMillis(),
                ),
            ) + existing
        }
        return save(appId, updated)
    }

    fun remove(appId: String, path: String): List<PortalPinnedFileItem> =
        save(appId, load(appId).filterNot { it.path == path || it.path.startsWith("$path/") })

    internal fun save(appId: String, items: List<PortalPinnedFileItem>): List<PortalPinnedFileItem> {
        val cleaned = items.distinctBy { it.path }.sortedByDescending { it.pinnedAtEpochMillis }
        runCatching {
            platformSaveToStorage(storageKey(appId), StorageJson.encodeToString(cleaned))
        }
        return cleaned
    }
}
