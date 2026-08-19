package io.github.portalappinspector.app.features.sharedprefs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.data.*
import io.github.portalappinspector.app.ui.*
import io.github.portalappinspector.app.ui.tabs.*
import io.github.portalappinspector.app.ui.toast.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import io.github.portalappinspector.app.features.files.PortalFileItem
import kotlinx.serialization.json.decodeFromJsonElement

@Composable
internal fun SharedPrefsPanel(
    tab: SharedPrefsTab,
    connection: PortalConnection,
    client: PortalSourceClient,
    enabled: Boolean,
) {
    var loading by remember(tab.file.path) { mutableStateOf(false) }
    var error by remember(tab.file.path) { mutableStateOf<String?>(null) }
    var prefs by remember(tab.file.path) { mutableStateOf<List<PortalSharedPrefItem>>(emptyList()) }
    var editedValues by remember(tab.file.path) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var expandedKey by remember(tab.file.path) { mutableStateOf<String?>(null) }
    val savingKeys = remember(tab.file.path) { mutableStateListOf<String>() }
    val updatedKeys = remember(tab.file.path) { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val toastHost = LocalToastHost.current

    fun loadPrefs() {
        if (!enabled || !connection.isValid || loading) return
        loading = true
        error = null
        scope.launch {
            runCatching {
                client.request(
                    connection = connection,
                    pluginId = SharedPrefsPluginId,
                    payload = buildJsonObject {
                        put("type", "load")
                        put("path", tab.file.path)
                    },
                )
            }.onSuccess { payload ->
                val nextPrefs = payload["items"]?.jsonArray?.map({
                    networkJson.decodeFromJsonElement<PortalSharedPrefItem>(it)
                }).orEmpty()
                prefs = nextPrefs
                editedValues = nextPrefs.associate { it.key to it.value }
                if (expandedKey !in nextPrefs.map { it.key }) {
                    expandedKey = null
                }
            }.onFailure { throwable ->
                error = throwable.message ?: "Shared preferences load failed"
                toastHost.show(error ?: "Shared preferences load failed", ToastKind.Error, durationMillis = 2_400L)
            }
            loading = false
        }
    }

    fun savePref(pref: PortalSharedPrefItem, nextValue: String) {
        if (!enabled || !connection.isValid || pref.key in savingKeys) return
        if (!pref.isValidValue(nextValue)) {
            toastHost.show("Invalid ${pref.valueType} value", ToastKind.Warning, durationMillis = 1_800L)
            return
        }
        savingKeys += pref.key
        scope.launch {
            runCatching {
                client.request(
                    connection = connection,
                    pluginId = SharedPrefsPluginId,
                    payload = buildJsonObject {
                        put("type", "update")
                        put("path", tab.file.path)
                        put("key", pref.key)
                        put("valueType", pref.valueType)
                        put("value", nextValue)
                    },
                )
            }.onSuccess { payload ->
                val savedValue = payload["value"]?.jsonPrimitive?.contentOrNull ?: nextValue
                prefs = prefs.map { item ->
                    if (item.key == pref.key) item.copy(value = savedValue) else item
                }
                if (editedValues[pref.key] == nextValue) {
                    editedValues = editedValues + (pref.key to savedValue)
                }
                if (pref.key !in updatedKeys) {
                    updatedKeys += pref.key
                }
                scope.launch {
                    delay(1_600L)
                    updatedKeys.remove(pref.key)
                }
                toastHost.show("Preference updated", ToastKind.Success, durationMillis = 1_600L)
            }.onFailure { throwable ->
                toastHost.show(throwable.message ?: "Preference update failed", ToastKind.Error, durationMillis = 2_400L)
            }
            savingKeys.remove(pref.key)
        }
    }

    LaunchedEffect(enabled, connection, tab.file.path) {
        if (enabled && connection.isValid) {
            loadPrefs()
        }
    }

    SharedPrefsPanelContent(
        tab = tab,
        enabled = enabled,
        loading = loading,
        error = error,
        prefs = prefs,
        editedValues = editedValues,
        expandedKey = expandedKey,
        savingKeys = savingKeys,
        updatedKeys = updatedKeys,
        onRefresh = ::loadPrefs,
        onToggle = { key -> expandedKey = if (expandedKey == key) null else key },
        onValueChange = { key, value -> editedValues = editedValues + (key to value) },
        onDebouncedSave = { pref, value -> savePref(pref, value) }
    )
}

@Composable
internal fun SharedPrefsPanelContent(
    tab: SharedPrefsTab,
    enabled: Boolean,
    loading: Boolean,
    error: String?,
    prefs: List<PortalSharedPrefItem>,
    editedValues: Map<String, String>,
    expandedKey: String?,
    savingKeys: List<String>,
    updatedKeys: List<String>,
    onRefresh: () -> Unit,
    onToggle: (String) -> Unit,
    onValueChange: (String, String) -> Unit,
    onDebouncedSave: (PortalSharedPrefItem, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = tab.file.name,
                    color = PortalColors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tab.file.path,
                    color = PortalColors.muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier.size(width = 42.dp, height = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    PulsatingDots(color = PortalColors.accent, modifier = Modifier.fillMaxSize())
                }
            }
            PortalButton(
                text = "Refresh",
                onClick = onRefresh,
                enabled = enabled && !loading,
            )
        }
        RowDivider()
        if (!enabled) {
            StatusCard("Shared preferences plugin unavailable", "Install the portal-shared-prefs plugin in the source app.", PortalColors.warning)
            return@Column
        }
        error?.let {
            StatusCard("Shared preferences request failed", it, PortalColors.error)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (prefs.isEmpty() && !loading && error == null) {
                item(key = "shared-prefs-empty") {
                    EmptyRow("No preferences in this file")
                }
            }
            items(prefs, key = { it.key }) { pref ->
                SharedPrefsRow(
                    pref = pref,
                    value = editedValues[pref.key] ?: pref.value,
                    saving = pref.key in savingKeys,
                    updated = pref.key in updatedKeys,
                    enabled = !loading,
                    expanded = expandedKey == pref.key,
                    onToggle = { onToggle(pref.key) },
                    onValueChange = { value -> onValueChange(pref.key, value) },
                    onDebouncedSave = { value -> onDebouncedSave(pref, value) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun SharedPrefsPanelContentPreview() {
    Box(Modifier.background(PortalColors.background)) {
        SharedPrefsPanelContent(
            tab = SharedPrefsTab(PortalFileItem("app_prefs.xml", "shared-prefs:/app_prefs.xml", null, false, 1024)),
            enabled = true,
            loading = false,
            error = null,
            prefs = listOf(
                PortalSharedPrefItem("is_logged_in", "boolean", "true"),
                PortalSharedPrefItem("username", "string", "admin"),
                PortalSharedPrefItem("launch_count", "int", "42")
            ),
            editedValues = mapOf("username" to "admin2"),
            expandedKey = "username",
            savingKeys = emptyList(),
            updatedKeys = emptyList(),
            onRefresh = {},
            onToggle = {},
            onValueChange = { _, _ -> },
            onDebouncedSave = { _, _ -> }
        )
    }
}
