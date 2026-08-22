package io.github.portalappinspector.app.features.network

import io.github.portalappinspector.app.data.StorageJson
import io.github.portalappinspector.app.util.platformLoadFromStorage
import io.github.portalappinspector.app.util.platformSaveToStorage

internal object PortalNetworkMockStore {
    private fun storageKey(appId: String) = "portal-app-inspector/apps/$appId/plugin-data/portal-network/network-mocks"

    fun load(appId: String): List<PortalNetworkMock> =
        runCatching {
            val raw = platformLoadFromStorage(storageKey(appId)) ?: return emptyList()
            StorageJson.decodeFromString<List<PortalNetworkMock>>(raw)
        }.getOrDefault(emptyList()).sortedByDescending { it.updatedAtEpochMillis }

    fun save(appId: String, mocks: List<PortalNetworkMock>): List<PortalNetworkMock> {
        val cleaned = mocks.distinctBy { it.id }.sortedByDescending { it.updatedAtEpochMillis }
        runCatching {
            platformSaveToStorage(storageKey(appId), StorageJson.encodeToString(cleaned))
        }
        return cleaned
    }
}
