package io.github.portalappinspector.app.features.sharedprefs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.type
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.data.*
import io.github.portalappinspector.app.ui.*
import io.github.portalappinspector.app.ui.tabs.*
import io.github.portalappinspector.app.ui.toast.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    val savingKeys = remember(tab.file.path) { mutableStateListOf<String>() }
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
                val nextPrefs = payload["items"]?.jsonArray?.map(::parseSharedPrefItem).orEmpty()
                prefs = nextPrefs
                editedValues = nextPrefs.associate { it.key to it.value }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
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
                onClick = ::loadPrefs,
                enabled = enabled && connection.isValid && !loading,
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
        SharedPrefsHeaderRow()
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
                    enabled = !loading,
                    onValueChange = { value ->
                        editedValues = editedValues + (pref.key to value)
                    },
                    onDebouncedSave = { value -> savePref(pref, value) },
                )
            }
        }
    }
}

@Composable
internal fun SharedPrefsHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Key", color = PortalColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(220.dp))
        Text("Type", color = PortalColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(86.dp))
        Text("Value", color = PortalColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("State", color = PortalColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(86.dp))
    }
    RowDivider()
}

@Composable
internal fun SharedPrefsRow(
    pref: PortalSharedPrefItem,
    value: String,
    saving: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onDebouncedSave: (String) -> Unit,
) {
    val multiline = pref.valueType == "stringSet"
    val invalid = !pref.isValidValue(value)
    LaunchedEffect(pref.key, value, enabled, invalid, saving) {
        if (!enabled || invalid || saving || value == pref.value) return@LaunchedEffect
        delay(650L)
        onDebouncedSave(value)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (multiline) 86.dp else 48.dp)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = pref.key,
                color = PortalColors.text,
                fontSize = 13.sp,
                maxLines = if (multiline) 3 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(220.dp),
            )
            Text(
                text = pref.valueType,
                color = PortalColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(86.dp),
            )
            PortalTextField(
                value = value,
                onValueChange = onValueChange,
                invalid = invalid,
                singleLine = !multiline,
                minLines = if (multiline) 3 else 1,
                maxLines = if (multiline) 3 else 1,
                modifier = Modifier
                    .weight(1f)
                    .height(if (multiline) 72.dp else 36.dp),
                textStyle = TextStyle(
                    color = PortalColors.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                ),
            )
            SharedPrefsSaveState(
                saving = saving,
                invalid = invalid,
                changed = value != pref.value,
                modifier = Modifier.width(86.dp),
            )
        }
        RowDivider()
    }
}

@Composable
internal fun SharedPrefsSaveState(
    saving: Boolean,
    invalid: Boolean,
    changed: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(28.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        when {
            saving -> PulsatingDots(color = PortalColors.accent, modifier = Modifier.size(width = 34.dp, height = 14.dp))
            invalid -> Text("Invalid", color = PortalColors.error, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            changed -> Text("Pending", color = PortalColors.warning, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            else -> Text("Saved", color = PortalColors.success, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

internal fun PortalSharedPrefItem.isValidValue(value: String): Boolean =
    when (valueType) {
        "string" -> true
        "boolean" -> value.toBooleanStrictOrNull() != null
        "int" -> value.toIntOrNull() != null
        "long" -> value.toLongOrNull() != null
        "float" -> value.toFloatOrNull() != null
        "stringSet" -> true
        else -> false
    }
