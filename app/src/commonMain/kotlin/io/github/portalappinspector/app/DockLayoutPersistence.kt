// commonMain/kotlin/io/github/portalappinspector/app/DockLayoutPersistence.kt
package io.github.portalappinspector.app

import io.github.docklayout.DockColumn
import io.github.docklayout.DockNode
import io.github.docklayout.DockPanel
import io.github.docklayout.DockRow
import io.github.portalappinspector.app.data.StorageJson
import io.github.portalappinspector.app.features.files.PortalFileItem
import io.github.portalappinspector.app.features.network.PortalNetworkCall
import io.github.portalappinspector.app.ui.tabs.FilesTab
import io.github.portalappinspector.app.ui.tabs.LogsTab
import io.github.portalappinspector.app.ui.tabs.NetworkResponseTab
import io.github.portalappinspector.app.ui.tabs.NetworkTab
import io.github.portalappinspector.app.ui.tabs.PortalTab
import io.github.portalappinspector.app.ui.tabs.ScreenMirrorTab
import io.github.portalappinspector.app.ui.tabs.SharedPrefsTab
import io.github.portalappinspector.app.ui.tabs.UnsupportedPluginTab
import io.github.portalappinspector.app.util.platformLoadFromStorage
import io.github.portalappinspector.app.util.platformSaveToStorage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal object DockLayoutPersistence {
        fun load(appId: String): DockNode<PortalTab>? =
        runCatching {
            val raw = platformLoadFromStorage(storageKey(appId)) ?: return null
            StorageJson.decodeFromString<PortalDockNodeSnapshot?>(raw)?.toDockNode()
        }.getOrNull()

    fun save(appId: String, layout: DockNode<PortalTab>?) {
        runCatching {
            platformSaveToStorage(storageKey(appId), StorageJson.encodeToString(layout.toSnapshot()))
        }
    }

    fun buildInitialLayout(
        restored: DockNode<PortalTab>?,
        tabs: List<PortalTab>,
        activeTab: PortalTab?,
    ): DockNode<PortalTab>? {
        val merged = restored.ensureTabs(tabs)
        return merged.withActiveTab(activeTab)
    }

    private fun DockNode<PortalTab>?.toSnapshot(): PortalDockNodeSnapshot? =
        when (this) {
            is DockRow -> PortalDockNodeSnapshot.Row(
                weight = weight,
                children = children.map { it.toSnapshot() },
            )
            is DockColumn -> PortalDockNodeSnapshot.Column(
                weight = weight,
                children = children.map { it.toSnapshot() },
            )
            is DockPanel -> PortalDockNodeSnapshot.Panel(
                weight = weight,
                activeIndex = activeIndex,
                folded = folded,
                tabs = tabs.map { it.toSnapshot() },
            )
            null -> null
        }

    private fun PortalDockNodeSnapshot.toDockNode(): DockNode<PortalTab> =
        when (this) {
            is PortalDockNodeSnapshot.Row -> DockRow(
                weight = weight,
                children = children.mapNotNull { it?.toDockNode() },
            )
            is PortalDockNodeSnapshot.Column -> DockColumn(
                weight = weight,
                children = children.mapNotNull { it?.toDockNode() },
            )
            is PortalDockNodeSnapshot.Panel -> DockPanel(
                weight = weight,
                activeIndex = activeIndex,
                folded = folded,
                tabs = tabs.map { it.toTab() },
            )
        }

    private fun PortalTab.toSnapshot(): PortalTabSnapshot =
        when (this) {
            FilesTab -> PortalTabSnapshot.Files
            NetworkTab -> PortalTabSnapshot.Network
            LogsTab -> PortalTabSnapshot.Logs
            ScreenMirrorTab -> PortalTabSnapshot.ScreenMirror
            is NetworkResponseTab -> PortalTabSnapshot.NetworkResponse(call.toSnapshot())
            is SharedPrefsTab -> PortalTabSnapshot.SharedPrefs(file.toSnapshot())
            is UnsupportedPluginTab -> PortalTabSnapshot.UnsupportedPlugin(pluginId)
        }

    private fun PortalTabSnapshot.toTab(): PortalTab =
        when (this) {
            PortalTabSnapshot.Files -> FilesTab
            PortalTabSnapshot.Network -> NetworkTab
            PortalTabSnapshot.Logs -> LogsTab
            PortalTabSnapshot.ScreenMirror -> ScreenMirrorTab
            is PortalTabSnapshot.NetworkResponse -> NetworkResponseTab(call.toCall())
            is PortalTabSnapshot.SharedPrefs -> SharedPrefsTab(file.toFileItem())
            is PortalTabSnapshot.UnsupportedPlugin -> UnsupportedPluginTab(pluginId)
        }

    private fun DockNode<PortalTab>?.ensureTabs(tabs: List<PortalTab>): DockNode<PortalTab>? {
        val existingTabs = collectTabs()
        val missingTabs = tabs.filterNot { candidate -> existingTabs.any { it == candidate } }
        if (missingTabs.isEmpty()) return this

        return when (val node = this) {
            null -> DockPanel(tabs = tabs)
            is DockPanel -> node.copy(tabs = node.tabs + missingTabs)
            is DockRow -> node.copy(children = node.children.withTabs(missingTabs))
            is DockColumn -> node.copy(children = node.children.withTabs(missingTabs))
        }
    }

    private fun List<DockNode<PortalTab>>.withTabs(missingTabs: List<PortalTab>): List<DockNode<PortalTab>> {
        if (missingTabs.isEmpty()) return this
        val firstPanelIndex = indexOfFirst { it is DockPanel<*> }
        return if (firstPanelIndex >= 0) {
            mapIndexed { index, child ->
                if (index == firstPanelIndex) {
                    val panel = child as DockPanel<PortalTab>
                    panel.copy(tabs = panel.tabs + missingTabs)
                } else {
                    child
                }
            }
        } else {
            listOf(DockPanel(tabs = missingTabs)) + this
        }
    }

    private fun DockNode<PortalTab>?.collectTabs(): List<PortalTab> =
        when (this) {
            is DockPanel -> tabs
            is DockRow -> children.flatMap { it.collectTabs() }
            is DockColumn -> children.flatMap { it.collectTabs() }
            null -> emptyList()
        }

    private fun DockNode<PortalTab>?.withActiveTab(activeTab: PortalTab?): DockNode<PortalTab>? {
        if (activeTab == null) return this
        return when (val node = this) {
            null -> null
            is DockPanel -> {
                val index = node.tabs.indexOfFirst { it == activeTab }
                if (index < 0) node else node.copy(activeIndex = index)
            }
            is DockRow -> node.copy(children = node.children.map { child -> child.withActiveTab(activeTab) ?: child })
            is DockColumn -> node.copy(children = node.children.map { child -> child.withActiveTab(activeTab) ?: child })
        }
    }

    private fun PortalNetworkCall.toSnapshot(): PortalNetworkCallSnapshot =
        PortalNetworkCallSnapshot(
            id = id,
            timestampEpochMillis = timestampEpochMillis,
            method = method,
            url = url,
            endpoint = endpoint,
            requestHeaders = requestHeaders,
            requestBody = requestBody,
            requestContentType = requestContentType,
            requestBodySizeBytes = requestBodySizeBytes,
            requestBodyTruncated = requestBodyTruncated,
            statusCode = statusCode,
            durationMillis = durationMillis,
            error = error,
            responseHeaders = responseHeaders,
            responseBody = responseBody,
            responseContentType = responseContentType,
            responseBodySizeBytes = responseBodySizeBytes,
            responseBodyTruncated = responseBodyTruncated,
            isMocked = isMocked,
        )

    private fun PortalFileItem.toSnapshot(): PortalFileItemSnapshot =
        PortalFileItemSnapshot(
            name = name,
            path = path,
            absolutePath = absolutePath,
            directory = directory,
            sizeBytes = sizeBytes,
        )

    @Serializable
    private sealed interface PortalDockNodeSnapshot {
        val weight: Float

        @Serializable
        @SerialName("row")
        data class Row(
            override val weight: Float,
            val children: List<PortalDockNodeSnapshot?>,
        ) : PortalDockNodeSnapshot

        @Serializable
        @SerialName("column")
        data class Column(
            override val weight: Float,
            val children: List<PortalDockNodeSnapshot?>,
        ) : PortalDockNodeSnapshot

        @Serializable
        @SerialName("panel")
        data class Panel(
            override val weight: Float,
            val activeIndex: Int,
            val folded: Boolean,
            val tabs: List<PortalTabSnapshot>,
        ) : PortalDockNodeSnapshot
    }

    @Serializable
    private sealed interface PortalTabSnapshot {
        @Serializable
        @SerialName("files")
        data object Files : PortalTabSnapshot

        @Serializable
        @SerialName("network")
        data object Network : PortalTabSnapshot

        @Serializable
        @SerialName("logs")
        data object Logs : PortalTabSnapshot

        @Serializable
        @SerialName("screen_mirror")
        data object ScreenMirror : PortalTabSnapshot

        @Serializable
        @SerialName("network_response")
        data class NetworkResponse(val call: PortalNetworkCallSnapshot) : PortalTabSnapshot

        @Serializable
        @SerialName("shared_prefs")
        data class SharedPrefs(val file: PortalFileItemSnapshot) : PortalTabSnapshot

        @Serializable
        @SerialName("unsupported_plugin")
        data class UnsupportedPlugin(val pluginId: String) : PortalTabSnapshot
    }

    @Serializable
    private data class PortalFileItemSnapshot(
        val name: String,
        val path: String,
        val absolutePath: String?,
        val directory: Boolean,
        val sizeBytes: Long?,
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
    private data class PortalNetworkCallSnapshot(
        val id: Long,
        val timestampEpochMillis: Long,
        val method: String,
        val url: String,
        val endpoint: String,
        val requestHeaders: Map<String, List<String>> = emptyMap(),
        val requestBody: String? = null,
        val requestContentType: String? = null,
        val requestBodySizeBytes: Long? = null,
        val requestBodyTruncated: Boolean = false,
        val statusCode: Int?,
        val durationMillis: Long,
        val error: String?,
        val responseHeaders: Map<String, List<String>> = emptyMap(),
        val responseBody: String?,
        val responseContentType: String?,
        val responseBodySizeBytes: Long?,
        val responseBodyTruncated: Boolean,
        val isMocked: Boolean,
    ) {
        fun toCall(): PortalNetworkCall =
            PortalNetworkCall(
                id = id,
                timestampEpochMillis = timestampEpochMillis,
                method = method,
                url = url,
                endpoint = endpoint,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                requestContentType = requestContentType,
                requestBodySizeBytes = requestBodySizeBytes,
                requestBodyTruncated = requestBodyTruncated,
                statusCode = statusCode,
                durationMillis = durationMillis,
                error = error,
                responseHeaders = responseHeaders,
                responseBody = responseBody,
                responseContentType = responseContentType,
                responseBodySizeBytes = responseBodySizeBytes,
                responseBodyTruncated = responseBodyTruncated,
                isMocked = isMocked,
            )
    }

    private fun storageKey(appId: String): String =
        "portal-app-inspector/apps/$appId/dock-layout"
}