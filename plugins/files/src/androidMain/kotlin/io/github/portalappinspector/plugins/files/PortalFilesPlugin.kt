package io.github.portalappinspector.plugins.files

import io.github.portalappinspector.PortalPlugin
import io.github.portalappinspector.PortalPluginRequest
import io.github.portalappinspector.PortalPluginResponse
import io.github.portalappinspector.android.PortalAndroidContext
import io.github.portalappinspector.portalPluginErrorResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

class PortalFilesPlugin : PortalPlugin {
    override val id: String = "portal:files"
    override val name: String = "Files"
    override val version: String = "0.1.0"

    override suspend fun handle(request: PortalPluginRequest): PortalPluginResponse {
        val operation = request.payload["type"]?.jsonPrimitive?.contentOrNull
            ?: return portalPluginErrorResponse(request, "missing_operation", "Files request payload is missing type.")

        return when (operation) {
            "listRoots" -> success(request, listRootsPayload())
            "listChildren" -> {
                val path = request.payload["path"]?.jsonPrimitive?.contentOrNull
                    ?: return portalPluginErrorResponse(request, "missing_path", "listChildren requires path.")
                success(request, listChildrenPayload(path))
            }
            else -> portalPluginErrorResponse(request, "unknown_operation", "Unknown files operation: $operation")
        }
    }

    private fun listRootsPayload() = buildJsonObject {
        put("type", "listRootsResult")
        put("items", roots().map { root ->
            fileItem(
                name = root.name,
                path = "${root.key}:/",
                file = root.file,
                directory = true,
            )
        }.toJsonArray())
    }

    private fun listChildrenPayload(path: String) = buildJsonObject {
        val resolved = resolveLogicalPath(path)
        if (resolved == null) {
            put("type", "listChildrenResult")
            put("path", path)
            put("canRead", false)
            put("items", JsonArray(emptyList()))
            put("error", "Unknown or unsafe path.")
            return@buildJsonObject
        }

        val (root, file) = resolved
        val children = file
            .listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .orEmpty()

        put("type", "listChildrenResult")
        put("path", path)
        put("canRead", file.canRead())
        put("items", children.map { child ->
            fileItem(
                name = child.name,
                path = root.toLogicalPath(child),
                file = child,
                directory = child.isDirectory,
            )
        }.toJsonArray())
    }

    private fun roots(): List<PortalFileRoot> {
        val context = PortalAndroidContext.requireApplicationContext()
        return listOfNotNull(
            PortalFileRoot("app-files", "App files", context.filesDir),
            PortalFileRoot("app-cache", "App cache", context.cacheDir),
            PortalFileRoot("app-no-backup", "No backup", context.noBackupFilesDir),
            context.getExternalFilesDir(null)?.let { PortalFileRoot("external-files", "External files", it) },
            context.externalCacheDir?.let { PortalFileRoot("external-cache", "External cache", it) },
        )
    }

    private fun resolveLogicalPath(path: String): Pair<PortalFileRoot, File>? {
        val separator = path.indexOf(":/")
        if (separator <= 0) return null

        val key = path.substring(0, separator)
        val relative = path.substring(separator + 2).trimStart('/')
        val root = roots().firstOrNull { it.key == key } ?: return null
        val target = if (relative.isEmpty()) root.file else File(root.file, relative)

        val rootCanonical = root.file.canonicalFile
        val targetCanonical = target.canonicalFile
        val rootPath = rootCanonical.path
        val targetPath = targetCanonical.path
        val insideRoot = targetPath == rootPath || targetPath.startsWith("$rootPath${File.separator}")
        return if (insideRoot && targetCanonical.isDirectory) {
            root to targetCanonical
        } else {
            null
        }
    }

    private fun PortalFileRoot.toLogicalPath(file: File): String {
        val relative = file.canonicalFile.relativeTo(this.file.canonicalFile).invariantSeparatorsPath
        return if (relative.isEmpty()) "$key:/" else "$key:/$relative"
    }

    private fun success(request: PortalPluginRequest, payload: kotlinx.serialization.json.JsonObject): PortalPluginResponse =
        PortalPluginResponse(
            id = request.id,
            pluginId = request.pluginId,
            ok = true,
            payload = payload,
        )

    private fun fileItem(
        name: String,
        path: String,
        file: File,
        directory: Boolean,
    ) = buildJsonObject {
        put("name", name)
        put("path", path)
        put("directory", directory)
        if (directory) {
            put("sizeBytes", JsonNull)
        } else {
            put("sizeBytes", file.length())
        }
        put("createdAtEpochMillis", JsonNull)
        val modified = file.lastModified().takeIf { it > 0L }
        if (modified == null) {
            put("modifiedAtEpochMillis", JsonNull)
        } else {
            put("modifiedAtEpochMillis", modified)
        }
    }

    private fun List<kotlinx.serialization.json.JsonObject>.toJsonArray(): JsonArray =
        buildJsonArray { forEach(::add) }

    private data class PortalFileRoot(
        val key: String,
        val name: String,
        val file: File,
    )
}
