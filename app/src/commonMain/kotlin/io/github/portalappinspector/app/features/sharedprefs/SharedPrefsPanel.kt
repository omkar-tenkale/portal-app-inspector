package io.github.portalappinspector.app.features.sharedprefs

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.data.*
import io.github.portalappinspector.app.ui.*
import io.github.portalappinspector.app.ui.icons.PortalTabIcons
import io.github.portalappinspector.app.ui.tabs.*
import io.github.portalappinspector.app.ui.toast.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
                    onToggle = {
                        expandedKey = if (expandedKey == pref.key) null else pref.key
                    },
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
internal fun SharedPrefsRow(
    pref: PortalSharedPrefItem,
    value: String,
    saving: Boolean,
    updated: Boolean,
    enabled: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onValueChange: (String) -> Unit,
    onDebouncedSave: (String) -> Unit,
) {
    val growsWithContent = pref.valueType == "string" || pref.valueType == "stringSet"
    val invalid = !pref.isValidValue(value)
    LaunchedEffect(pref.key, value, enabled, invalid, saving) {
        if (!enabled || invalid || saving || value == pref.value) return@LaunchedEffect
        delay(650L)
        onDebouncedSave(value)
    }

    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(horizontal = 18.dp, vertical = if (expanded) 8.dp else 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SharedPrefEntryIcon()
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = pref.key,
                        color = PortalColors.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!expanded) {
                        Text(
                            text = pref.value,
                            color = PortalColors.muted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 50.dp, end = 18.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                SharedPrefsValueField(
                    value = value,
                    onValueChange = onValueChange,
                    invalid = invalid,
                    singleLine = !growsWithContent,
                    maxLines = if (growsWithContent) 4 else 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 30.dp, max = if (growsWithContent) 92.dp else 30.dp),
                )
                SharedPrefsSaveState(
                    saving = saving,
                    invalid = invalid,
                    changed = value != pref.value,
                    updated = updated,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        RowDivider()
    }
}

@Composable
internal fun SharedPrefEntryIcon() {
    Image(
        painter = rememberVectorPainter(PortalTabIcons.SharedPrefsEntry),
        contentDescription = null,
        colorFilter = ColorFilter.tint(PortalColors.muted),
        modifier = Modifier.size(22.dp),
    )
}

@Composable
internal fun SharedPrefsValueField(
    value: String,
    onValueChange: (String) -> Unit,
    invalid: Boolean,
    singleLine: Boolean,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(0, value.length)))
    }

    LaunchedEffect(Unit) {
        fieldValue = TextFieldValue(value, selection = TextRange(0, value.length))
        focusRequester.requestFocus()
    }
    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, selection = TextRange(0, value.length))
        }
    }

    Column(modifier = modifier) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { nextValue ->
                fieldValue = nextValue
                if (nextValue.text != value) {
                    onValueChange(nextValue.text)
                }
            },
            singleLine = singleLine,
            minLines = 1,
            maxLines = maxLines,
            textStyle = TextStyle(
                color = PortalColors.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            ),
            cursorBrush = SolidColor(PortalColors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(bottom = 5.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (invalid) PortalColors.error else PortalColors.inputBorder),
        )
    }
}

@Composable
internal fun SharedPrefsSaveState(
    saving: Boolean,
    invalid: Boolean,
    changed: Boolean,
    updated: Boolean,
    modifier: Modifier = Modifier,
) {
    val text = when {
        saving -> "Updating"
        invalid -> "Invalid"
        changed -> "Pending"
        updated -> "Updated"
        else -> null
    } ?: return
    val color = when {
        invalid -> PortalColors.error
        updated -> PortalColors.success
        else -> PortalColors.warning
    }
    Box(
        modifier = modifier.height(15.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
