package io.github.portalappinspector.app.data

import androidx.compose.runtime.key
import androidx.compose.ui.input.key.key
import io.github.portalappinspector.PortalManifest
import io.ktor.client.request.get
import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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

    companion object {
        @OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
        fun cleanConnectUrl(mobileView: Boolean) {
            val path = window.location.pathname
            val cleanPath = when {
                path.endsWith("/connect") -> path.removeSuffix("connect")
                path.endsWith("/connect/") -> path.removeSuffix("connect/")
                else -> path
            }.ifBlank { "/" }
            val cleanQuery = if (mobileView) "?mobileView=true" else ""
            val cleanUrl = cleanPath + cleanQuery + window.location.hash
            window.history.replaceState(null, "", cleanUrl)
        }
    }
}

internal data class PortalLaunchParams(
    val connection: PortalConnection?,
    val mobileView: Boolean,
    val isEmulator: Boolean,
    val sourcePackageName: String?,
) {
    companion object {
        fun fromUrl(): PortalLaunchParams {
            val params = window.location.search
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
            val sourcePackageName = normalizedParams["sourcepackagename"]
                ?: normalizedParams["packagename"]
                ?: normalizedParams["appid"]
            val connection = normalizedParams["host"]
                ?.takeIf { it.isNotBlank() }
                ?.let { host ->
                    PortalConnection(
                        host = host,
                        port = normalizedParams["port"] ?: "4896",
                    )
                }
            PortalConnection.cleanConnectUrl(mobileView)
            return PortalLaunchParams(
                connection = connection,
                mobileView = mobileView,
                isEmulator = isEmulator,
                sourcePackageName = sourcePackageName,
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
    runCatching { decodeURIComponent(replace("+", "%20")) }.getOrDefault(this)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
internal fun decodeURIComponent(value: String): String =
    js("decodeURIComponent(value)")

@Serializable
internal data class SavedPortalConnection(
    val packageName: String,
    val appName: String,
    val appIconPngBase64: String? = null,
    val connection: PortalConnection,
    val updatedAtEpochMillis: Long,
) {
    fun toPortalConnection(): PortalConnection =
        connection
}

internal object PortalConnectionStore {
    internal const val StorageKey = "portal.app.inspector.connections"

    fun load(): List<SavedPortalConnection> =
        runCatching {
            val raw = window.localStorage.getItem(StorageKey) ?: return emptyList()
            StorageJson.decodeFromString<List<SavedPortalConnection>>(raw)
                .sortedByDescending { it.updatedAtEpochMillis }
        }.getOrDefault(emptyList())

    fun latest(): SavedPortalConnection? =
        load().maxByOrNull { it.updatedAtEpochMillis }

    fun upsert(
        manifest: PortalManifest,
        connection: PortalConnection,
    ): List<SavedPortalConnection> {
        val next = SavedPortalConnection(
            packageName = manifest.sourcePackageName,
            appName = manifest.appName,
            appIconPngBase64 = manifest.appIconPngBase64,
            connection = connection,
            updatedAtEpochMillis = nowEpochMillis(),
        )
        val updated = (load().filterNot { it.packageName == next.packageName } + next)
            .sortedByDescending { it.updatedAtEpochMillis }
        runCatching {
            window.localStorage.setItem(StorageKey, StorageJson.encodeToString(updated))
        }
        return updated
    }
}

internal fun List<SavedPortalConnection>.matchingPackage(packageName: String): SavedPortalConnection? =
    firstOrNull {
        it.packageName == packageName
    }
