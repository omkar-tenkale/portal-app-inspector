package io.github.portalappinspector.app.data

import io.github.portalappinspector.PortalManifest
import kotlinx.serialization.Serializable
import io.github.portalappinspector.app.util.*

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
                    val key = it.substringBefore("=").queryDecoded()
                    val value = it.substringAfter("=").queryDecoded()
                    key to value
                }
            val normalizedParams = params
                .mapKeys { (key, _) -> key.normalizedQueryKey() }
            val mobileView = normalizedParams["mobileview"]?.isTruthyQueryValue() == true
            val isEmulator = normalizedParams["isemulator"]?.isTruthyQueryValue() == true
            val appId = normalizedParams["appid"]
            val platform = normalizedParams["platform"]
            val connection = normalizedParams["host"]
                ?.takeIf { it.isNotBlank() }
                ?.let { host ->
                    PortalConnection(
                        host = host,
                        port = normalizedParams["port"] ?: "4896",
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

        internal fun String.normalizedQueryKey(): String =
            lowercase()
                .replace("%20", "")
                .replace("+", "")
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "")

        internal fun String.isTruthyQueryValue(): Boolean =
            lowercase() in setOf("true", "1", "yes", "y")
    }
}

internal fun String.queryDecoded(): String =
    runCatching { platformDecodeURIComponent(replace("+", "%20")) }.getOrDefault(this)

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
    internal const val StorageKey = "portal.app.inspector.connections"

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

internal fun List<SavedPortalConnection>.matchingPackage(appId: String): SavedPortalConnection? =
    firstOrNull {
        it.appId == appId
    }

internal fun navigateToApp(appId: String) {
    platformNavigateToApp(appId)
}

internal fun queryEncoded(value: String): String =
    platformEncodeURIComponent(value)