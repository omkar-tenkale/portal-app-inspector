package io.github.portalappinspector.app.features.network

import io.github.portalappinspector.app.data.StorageJson
import io.github.portalappinspector.app.util.platformLoadFromStorage
import io.github.portalappinspector.app.util.platformSaveToStorage
import kotlinx.serialization.json.Json

internal object PortalNetworkMockStore {
    internal const val StorageKey = "portal.app.inspector.plugin-data.portal-network.network-mocks"

    fun load(packageName: String): List<PortalNetworkMock> =
        loadAll()[packageName].orEmpty().sortedByDescending { it.updatedAtEpochMillis }

    fun save(packageName: String, mocks: List<PortalNetworkMock>): List<PortalNetworkMock> {
        val cleaned = mocks.distinctBy { it.id }.sortedByDescending { it.updatedAtEpochMillis }
        val updated = loadAll() + (packageName to cleaned)
        runCatching {
            platformSaveToStorage(StorageKey, StorageJson.encodeToString(updated))
        }
        return cleaned
    }

    internal fun loadAll(): Map<String, List<PortalNetworkMock>> =
        runCatching {
            val raw = platformLoadFromStorage(StorageKey) ?: return emptyMap()
            StorageJson.decodeFromString<Map<String, List<PortalNetworkMock>>>(raw)
        }.getOrDefault(emptyMap())
}