package io.github.portalappinspector.plugins.sharedprefs

import android.content.Context
import io.github.portalappinspector.PortalPlugin
import io.github.portalappinspector.PortalPluginRequest
import io.github.portalappinspector.PortalPluginResponse
import io.github.portalappinspector.android.PortalAndroidContext
import io.github.portalappinspector.portalPluginErrorResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

class PortalSharedPrefsPlugin : PortalPlugin {
    override val id: String = "portal-shared-prefs"
    override val name: String = "Shared Preferences"
    override val version = 260809L

    override suspend fun handle(request: PortalPluginRequest): PortalPluginResponse {
        val operation = request.payload["type"]?.jsonPrimitive?.contentOrNull
            ?: return portalPluginErrorResponse(request, "missing_operation", "Shared prefs payload is missing type.")

        return when (operation) {
            "load" -> {
                val path = request.payload["path"]?.jsonPrimitive?.contentOrNull
                    ?: return portalPluginErrorResponse(request, "missing_path", "load requires path.")
                loadPayload(path)?.let { success(request, it) }
                    ?: portalPluginErrorResponse(request, "invalid_path", "Unknown, unsafe, or non-shared-prefs path.")
            }
            "update" -> {
                val path = request.payload["path"]?.jsonPrimitive?.contentOrNull
                    ?: return portalPluginErrorResponse(request, "missing_path", "update requires path.")
                val key = request.payload["key"]?.jsonPrimitive?.contentOrNull
                    ?: return portalPluginErrorResponse(request, "missing_key", "update requires key.")
                val valueType = request.payload["valueType"]?.jsonPrimitive?.contentOrNull
                    ?: return portalPluginErrorResponse(request, "missing_value_type", "update requires valueType.")
                val value = request.payload["value"]?.jsonPrimitive?.contentOrNull.orEmpty()
                updatePayload(path, key, valueType, value)?.let { success(request, it) }
                    ?: portalPluginErrorResponse(request, "invalid_value", "Value could not be saved for the requested type.")
            }
            else -> portalPluginErrorResponse(request, "unknown_operation", "Unknown shared prefs operation: $operation")
        }
    }

    private fun loadPayload(path: String): JsonObject? {
        val target = resolveSharedPrefsFile(path) ?: return null
        val preferences = target.context.getSharedPreferences(target.name, Context.MODE_PRIVATE)
        val items = preferences.all.entries
            .sortedBy { it.key }
            .map { entry ->
                val value = entry.value
                buildJsonObject {
                    put("key", entry.key)
                    put("valueType", value.portalValueType())
                    put("value", value.portalValueString())
                }
            }

        return buildJsonObject {
            put("type", "sharedPrefsResult")
            put("path", path)
            put("name", target.name)
            put("items", items.toJsonArray())
        }
    }

    private fun updatePayload(path: String, key: String, valueType: String, value: String): JsonObject? {
        if (key.isBlank()) return null
        val target = resolveSharedPrefsFile(path) ?: return null
        val preferences = target.context.getSharedPreferences(target.name, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        when (valueType) {
            "string" -> editor.putString(key, value)
            "boolean" -> editor.putBoolean(key, value.toBooleanStrictOrNull() ?: return null)
            "int" -> editor.putInt(key, value.toIntOrNull() ?: return null)
            "long" -> editor.putLong(key, value.toLongOrNull() ?: return null)
            "float" -> editor.putFloat(key, value.toFloatOrNull() ?: return null)
            "stringSet" -> editor.putStringSet(
                key,
                value.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet(),
            )
            else -> return null
        }
        if (!editor.commit()) return null

        return buildJsonObject {
            put("type", "updateSharedPrefResult")
            put("path", path)
            put("key", key)
            put("valueType", valueType)
            put("value", value)
        }
    }

    private fun resolveSharedPrefsFile(path: String): SharedPrefsTarget? {
        val separator = path.indexOf(":/")
        if (separator <= 0) return null
        val key = path.substring(0, separator)
        if (key != SharedPrefsRootKey) return null

        val relative = path.substring(separator + 2).trimStart('/')
        if (relative.isBlank() || relative.contains('/')) return null
        val context = PortalAndroidContext.requireApplicationContext()
        val root = File(context.applicationInfo.dataDir, "shared_prefs").canonicalFile
        val target = File(root, relative).canonicalFile
        if (target.parentFile?.canonicalFile != root) return null
        if (!target.name.endsWith(".xml")) return null
        if (!target.isFile) return null
        val name = target.name.removeSuffix(".xml")
        if (name.isBlank()) return null
        return SharedPrefsTarget(context = context, name = name)
    }

    private fun Any?.portalValueType(): String =
        when (this) {
            is String -> "string"
            is Boolean -> "boolean"
            is Int -> "int"
            is Long -> "long"
            is Float -> "float"
            is Set<*> -> "stringSet"
            else -> "string"
        }

    private fun Any?.portalValueString(): String =
        when (this) {
            is Set<*> -> filterIsInstance<String>().sorted().joinToString("\n")
            null -> ""
            else -> toString()
        }

    private fun success(request: PortalPluginRequest, payload: JsonObject): PortalPluginResponse =
        PortalPluginResponse(
            id = request.id,
            pluginId = request.pluginId,
            ok = true,
            payload = payload,
        )

    private fun List<JsonObject>.toJsonArray(): JsonArray =
        buildJsonArray { forEach(::add) }

    private data class SharedPrefsTarget(
        val context: Context,
        val name: String,
    )

    private companion object {
        const val SharedPrefsRootKey = "shared-prefs"
    }
}
