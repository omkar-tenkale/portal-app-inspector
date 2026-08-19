package io.github.portalappinspector.app.features.sharedprefs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.RowDivider
import io.github.portalappinspector.app.ui.Text
import io.github.portalappinspector.app.ui.icons.PortalTabIcons
import kotlinx.coroutines.delay

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

@Preview
@Composable
private fun SharedPrefsRowPreview_Collapsed() {
    Box(Modifier.background(PortalColors.background)) {
        SharedPrefsRow(
            pref = PortalSharedPrefItem("username", "string", "admin"),
            value = "admin",
            saving = false,
            updated = false,
            enabled = true,
            expanded = false,
            onToggle = {},
            onValueChange = {},
            onDebouncedSave = {}
        )
    }
}

@Preview
@Composable
private fun SharedPrefsRowPreview_Expanded() {
    Box(Modifier.background(PortalColors.background)) {
        SharedPrefsRow(
            pref = PortalSharedPrefItem("username", "string", "admin"),
            value = "admin2",
            saving = false,
            updated = false,
            enabled = true,
            expanded = true,
            onToggle = {},
            onValueChange = {},
            onDebouncedSave = {}
        )
    }
}

@Preview
@Composable
private fun SharedPrefsRowPreview_Invalid() {
    Box(Modifier.background(PortalColors.background)) {
        SharedPrefsRow(
            pref = PortalSharedPrefItem("launch_count", "int", "42"),
            value = "forty-two",
            saving = false,
            updated = false,
            enabled = true,
            expanded = true,
            onToggle = {},
            onValueChange = {},
            onDebouncedSave = {}
        )
    }
}
