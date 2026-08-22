package io.github.portalappinspector.app.data

import io.github.portalappinspector.PortalManifest
import io.github.portalappinspector.app.util.nowEpochMillis
import io.github.portalappinspector.app.util.platformGetUrlQueryString
import io.github.portalappinspector.app.util.platformLoadFromStorage
import io.github.portalappinspector.app.util.platformNavigateToApp
import io.github.portalappinspector.app.util.platformSaveToStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val StorageJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
internal data class PortalConnection(
    val host: String,
    val port: String,
) {
    val isValid: Boolean
        get() = host.isNotBlank() && port.isNotBlank()

    val baseUrl: String
        get() = "http://$host:$port"

    fun adbForwardCommand(): String =
        "adb forward tcp:$port tcp:$port"
}

internal data class PortalLaunchParams(
    val connection: PortalConnection?,
    val mobileView: Boolean,
    val isEmulator: Boolean,
    val appId: String?,
    val platform: String?,
) {
    companion object {
        fun fromUrl(): PortalLaunchParams {
            val params = platformGetUrlQueryString()
                .removePrefix("?")
                .split("&")
                .filter { it.contains("=") }
                .associate {
                    it.substringBefore("=") to it.substringAfter("=")
                }

            val mobileView = params["mobileView"] == "true"
            val isEmulator = params["isEmulator"] == "true"
            val appId = params["appId"]
            val platform = params["platform"]
            val connection = params["host"]
                ?.takeIf { it.isNotBlank() }
                ?.let { host ->
                    PortalConnection(
                        host = host,
                        port = params["port"] ?: "4896",
                    )
                }

            return PortalLaunchParams(
                connection = connection,
                mobileView = mobileView,
                isEmulator = isEmulator,
                appId = appId,
                platform = platform,
            )
        }
    }
}

@Serializable
internal data class SavedPortalConnection(
    val appId: String,
    val appName: String,
    val platform: String,
    val appIconPngBase64: String? = null,
    val connection: PortalConnection,
    val updatedAt: Long,
) {
    fun toPortalConnection(): PortalConnection =
        connection
}

internal object PortalConnectionStore {
    internal const val StorageKey = "portal-app-inspector/connections"

    fun load(): List<SavedPortalConnection> =
        runCatching {
            val raw = platformLoadFromStorage(StorageKey) ?: return emptyList()
            StorageJson.decodeFromString<List<SavedPortalConnection>>(raw)
                .sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())

    fun latest(): SavedPortalConnection? =
        load().maxByOrNull { it.updatedAt }

    fun upsert(
        manifest: PortalManifest,
        connection: PortalConnection,
    ): List<SavedPortalConnection> {
        val next = SavedPortalConnection(
            appId = manifest.appId,
            appName = manifest.appName,
            platform = manifest.platform,
            appIconPngBase64 = manifest.appIconPngBase64,
            connection = connection,
            updatedAt = nowEpochMillis(),
        )
        val updated = (load().filterNot { it.appId == next.appId } + next)
            .sortedByDescending { it.updatedAt }
        runCatching {
            platformSaveToStorage(StorageKey, StorageJson.encodeToString(updated))
        }
        return updated
    }
}

internal fun navigateToApp(appId: String) {
    platformNavigateToApp(appId)
}