package io.github.portalappinspector.app.data

import kotlinx.browser.window
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.foundation.lazy.items
import io.github.portalappinspector.app.util.*

internal object PortalNetworkMockStore {
    internal const val StorageKey = "portal.app.inspector.plugin-data.portal-network.network-mocks"

    fun load(packageName: String): List<PortalNetworkMock> =
        loadAll()[packageName].orEmpty().sortedByDescending { it.updatedAtEpochMillis }

    fun save(packageName: String, mocks: List<PortalNetworkMock>): List<PortalNetworkMock> {
        val cleaned = mocks.distinctBy { it.id }.sortedByDescending { it.updatedAtEpochMillis }
        val updated = loadAll() + (packageName to cleaned)
        runCatching {
            window.localStorage.setItem(StorageKey, StorageJson.encodeToString(updated))
        }
        return cleaned
    }

    internal fun loadAll(): Map<String, List<PortalNetworkMock>> =
        runCatching {
            val raw = window.localStorage.getItem(StorageKey) ?: return emptyMap()
            StorageJson.decodeFromString<Map<String, List<PortalNetworkMock>>>(raw)
        }.getOrDefault(emptyMap())
}

internal object PortalFilePinStore {
    internal const val StorageKey = "portal.app.inspector.plugin-data.portal-files.pinned-paths"

    fun load(): List<PortalPinnedFileItem> =
        runCatching {
            val raw = window.localStorage.getItem(StorageKey) ?: return emptyList()
            StorageJson.decodeFromString<List<PortalPinnedFileItem>>(raw)
        }.getOrDefault(emptyList())

    fun toggle(item: PortalFileItem): List<PortalPinnedFileItem> {
        val existing = load()
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
        return save(updated)
    }

    fun remove(path: String): List<PortalPinnedFileItem> =
        save(load().filterNot { it.path == path || it.path.startsWith("$path/") })

    internal fun save(items: List<PortalPinnedFileItem>): List<PortalPinnedFileItem> {
        val cleaned = items.distinctBy { it.path }.sortedByDescending { it.pinnedAtEpochMillis }
        runCatching {
            window.localStorage.setItem(StorageKey, StorageJson.encodeToString(cleaned))
        }
        return cleaned
    }
}

internal val StorageJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
