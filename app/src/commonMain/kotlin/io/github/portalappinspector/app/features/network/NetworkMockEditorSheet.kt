package io.github.portalappinspector.app.features.network

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.ui.JsonText
import io.github.portalappinspector.app.ui.JsonTextMode
import io.github.portalappinspector.app.ui.PortalCheckbox
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.PortalSwitch
import io.github.portalappinspector.app.ui.PortalTextField
import io.github.portalappinspector.app.ui.RowDivider
import io.github.portalappinspector.app.ui.StatusCard
import io.github.portalappinspector.app.ui.Text
import io.github.portalappinspector.app.ui.icons.PortalTabIcons
import io.github.portalappinspector.app.util.formatJsonOrNull
import io.github.portalappinspector.app.util.nowEpochMillis
import io.github.portalappinspector.app.util.toHeaderLines
import io.github.portalappinspector.app.util.toHeaderMap
import kotlinx.coroutines.delay
import io.github.portalappinspector.app.ui.ResponsiveRow

@Composable
internal fun NetworkMockEditorSheet(
    initialMock: PortalNetworkMock,
    onDismiss: () -> Unit,
    onSave: (PortalNetworkMock) -> Unit,
) {
    var enabled by remember(initialMock.id) { mutableStateOf(initialMock.enabled) }
    var matchers by remember(initialMock.id) {
        mutableStateOf(initialMock.expectation.matchers.toSimpleMockMatchers())
    }
    var delayEnabled by remember(initialMock.id) { mutableStateOf(initialMock.expectation.delayMs > 0L) }
    var delaySeconds by remember(initialMock.id) {
        mutableStateOf(
            (initialMock.expectation.delayMs.takeIf { it > 0L }?.div(1000L) ?: 2L).toString()
        )
    }
    var conditionType by remember(initialMock.id) { mutableStateOf("urlContains") }
    var conditionValue by remember(initialMock.id) { mutableStateOf("") }
    var addConditionVisible by remember(initialMock.id) { mutableStateOf(false) }
    var responseMode by remember(initialMock.id) {
        mutableStateOf(if (initialMock.response.error != null) "error" else "body")
    }
    var responseCode by remember(initialMock.id) {
        mutableStateOf((initialMock.response.body?.code ?: 200).toString())
    }
    var responseContentType by remember(initialMock.id) {
        mutableStateOf(initialMock.response.body?.contentType ?: "application/json")
    }
    var responseBody by remember(initialMock.id) {
        mutableStateOf(initialMock.response.body?.body.orEmpty())
    }
    var responseBodyMode by remember(initialMock.id) { mutableStateOf("txt") }
    var responseEditorTab by remember(initialMock.id) { mutableStateOf("body") }
    var responseHeadersText by remember(initialMock.id) {
        mutableStateOf(initialMock.response.body?.headers?.toHeaderLines().orEmpty())
    }
    var errorType by remember(initialMock.id) {
        mutableStateOf(initialMock.response.error?.type ?: PortalNetworkErrorTypes.first())
    }
    var validationError by remember(initialMock.id) { mutableStateOf<String?>(null) }
    var autoSaveReady by remember(initialMock.id) { mutableStateOf(false) }
    var savedIndicatorVisible by remember(initialMock.id) { mutableStateOf(false) }

    fun nextSavedMock(): PortalNetworkMock? {
        val code = responseCode.toIntOrNull()
        val delayMs = if (delayEnabled) {
            (delaySeconds.toLongOrNull() ?: 0L).coerceAtLeast(0L) * 1000L
        } else {
            0L
        }
        val cleanMatchers = matchers.filter { it.value.isNotBlank() }
        validationError = when {
            cleanMatchers.isEmpty() -> "Add at least one matcher."
            responseMode == "body" && code == null -> "Status code must be a number."
            responseMode == "body" && responseContentType.isBlank() -> "Content type is required."
            else -> null
        }
        if (validationError != null) return null
        val now = nowEpochMillis()
        return initialMock.copy(
            enabled = enabled,
            updatedAtEpochMillis = now,
            expectation = PortalNetworkMockExpectation(
                delayMs = delayMs,
                matchers = cleanMatchers,
            ),
            response = if (responseMode == "body") {
                PortalNetworkMockResponse(
                    body = PortalNetworkBodyResponse(
                        code = code ?: 200,
                        body = responseBody,
                        contentType = responseContentType,
                        headers = responseHeadersText.toHeaderMap(),
                    ),
                )
            } else {
                PortalNetworkMockResponse(error = PortalNetworkErrorResponse(errorType))
            },
        )
    }

    LaunchedEffect(
        enabled,
        matchers,
        delayEnabled,
        delaySeconds,
        responseMode,
        responseCode,
        responseContentType,
        responseBody,
        responseHeadersText,
        errorType,
    ) {
        if (!autoSaveReady) {
            autoSaveReady = true
            return@LaunchedEffect
        }
        delay(350L)
        val savedMock = nextSavedMock() ?: return@LaunchedEffect
        onSave(savedMock)
        savedIndicatorVisible = true
        delay(1_300L)
        savedIndicatorVisible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.46f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(640.dp)
                .background(PortalColors.card, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .border(
                    1.dp,
                    PortalColors.border,
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            NetworkMockEditorTopBar(
                enabled = enabled,
                savedIndicatorVisible = savedIndicatorVisible,
                onEnabledChange = { enabled = it },
                onDismiss = onDismiss,
            )
            RowDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Set up fake responses to test your app in different conditions",
                    color = PortalColors.muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                validationError?.let { StatusCard("Mock not saved", it, PortalColors.error) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        MockEditorSection(
                            title = "Conditions",
                        ) {
                            ConditionFlow(
                                matchers = matchers,
                                onDelete = { deleteIndex ->
                                    matchers =
                                        matchers.filterIndexed { index, _ -> index != deleteIndex }
                                },
                            ) {
                                if (addConditionVisible) {
                                    ConditionAddRow(
                                        type = conditionType,
                                        value = conditionValue,
                                        onTypeChange = {
                                            conditionType = it
                                            conditionValue = ""
                                        },
                                        onValueChange = { conditionValue = it },
                                        onCancel = {
                                            conditionValue = ""
                                            addConditionVisible = false
                                        },
                                        onAdd = { selectedType ->
                                            val nextMatcher = PortalNetworkMockMatcher(
                                                type = selectedType,
                                                value = conditionValue,
                                            )
                                            if (nextMatcher.value.isNotBlank()) {
                                                matchers = matchers + nextMatcher
                                                conditionValue = ""
                                                addConditionVisible = false
                                            }
                                        },
                                    )
                                } else {
                                    AddConditionPill(onClick = { addConditionVisible = true })
                                }
                            }
                        }
                    }
                    item {
                        MockEditorSection(
                            title = "Response",
                            trailing = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    DelayCycleText(
                                        enabled = delayEnabled,
                                        seconds = delaySeconds,
                                        onEnabledChange = { delayEnabled = it },
                                        onSecondsChange = { delaySeconds = it },
                                    )
                                    ResponseModeTabs(
                                        responseMode = responseMode,
                                        onChange = { responseMode = it })
                                }
                            },
                        ) {
                            if (responseMode == "body") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CompactTextField(
                                            "Code",
                                            responseCode,
                                            { responseCode = it.filter(Char::isDigit).take(3) },
                                            Modifier.width(96.dp)
                                        )
                                        CompactTextField(
                                            "Content-Type",
                                            responseContentType,
                                            { responseContentType = it },
                                            Modifier.weight(1f)
                                        )
                                    }
                                    MockResponseEditorTabs(
                                        selected = responseEditorTab,
                                        onChange = { responseEditorTab = it },
                                    )
                                    if (responseEditorTab == "headers") {
                                        PortalTextField(
                                            value = responseHeadersText,
                                            onValueChange = { responseHeadersText = it },
                                            label = "Headers, one per line: Key: Value",
                                            minLines = 8,
                                            maxLines = 12,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        BodyEditor(
                                            value = responseBody,
                                            onValueChange = { responseBody = it },
                                            mode = responseBodyMode,
                                            onModeChange = { responseBodyMode = it },
                                            onFormatValue = { responseBody = it },
                                            label = null,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PortalNetworkErrorTypes.forEach { type ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { errorType = type }
                                                .padding(horizontal = 4.dp, vertical = 5.dp),
                                        ) {
                                            PortalCheckbox(
                                                checked = errorType == type,
                                                onCheckedChange = { if (it) errorType = type })
                                            Text(
                                                type,
                                                color = if (errorType == type) PortalColors.text else PortalColors.muted,
                                                fontSize = 13.sp,
                                                fontWeight = if (errorType == type) FontWeight.Medium else null,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkMockEditorTopBar(
    enabled: Boolean,
    savedIndicatorVisible: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ResponsiveRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        leftContent = {
            Text(
                text = "Setup Mock",
                color = PortalColors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        rightContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (savedIndicatorVisible) {
                    Text(
                        text = "Saved!",
                        color = PortalColors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                PortalSwitch(checked = enabled, onCheckedChange = onEnabledChange)
                NetworkPanelToolbarDivider()
                NetworkDetailIconButton(
                    icon = PortalTabIcons.Close,
                    onClick = onDismiss,
                )
            }
        }
    )
}

@Composable
internal fun MockEditorSection(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PortalColors.border),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = PortalColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            trailing?.invoke()
        }
        content()
    }
}

@Composable
internal fun DelayCycleText(
    enabled: Boolean,
    seconds: String,
    onEnabledChange: (Boolean) -> Unit,
    onSecondsChange: (String) -> Unit,
) {
    val normalizedSeconds = seconds.toLongOrNull()?.takeIf { it == 2L || it == 5L } ?: 2L
    val text = if (enabled) "Delay ${normalizedSeconds}s" else "No delay"
    Text(
        text = text,
        color = if (enabled) PortalColors.accent else PortalColors.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                when {
                    !enabled -> {
                        onEnabledChange(true)
                        onSecondsChange("2")
                    }
                    normalizedSeconds == 2L -> {
                        onEnabledChange(true)
                        onSecondsChange("5")
                    }
                    else -> {
                        onEnabledChange(false)
                        onSecondsChange("2")
                    }
                }
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
internal fun MockResponseEditorTabs(
    selected: String,
    onChange: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PortalColors.input)
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(8.dp))
            .padding(2.dp),
    ) {
        ResponseModeTab(text = "Body", selected = selected == "body", onClick = { onChange("body") })
        ResponseModeTab(text = "Headers", selected = selected == "headers", onClick = { onChange("headers") })
    }
}

@Composable
internal fun BodyEditor(
    value: String,
    onValueChange: (String) -> Unit,
    mode: String,
    onModeChange: (String) -> Unit,
    onFormatValue: (String) -> Unit,
    label: String? = "Body",
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let {
            Text(it, color = PortalColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        JsonText(
            jsonString = value,
            onTextChange = onValueChange,
            initialMode = if (mode == "json") JsonTextMode.Json else JsonTextMode.Text,
            onModeChange = { nextMode ->
                if (nextMode == JsonTextMode.Text && mode == "json") {
                    formatJsonOrNull(value)?.let(onFormatValue)
                }
                onModeChange(if (nextMode == JsonTextMode.Json) "json" else "txt")
            },
            containerColor = PortalColors.input,
            borderColor = PortalColors.inputBorder,
            textStyle = TextStyle(
                color = PortalColors.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(214.dp),
        )
    }
}

@Composable
internal fun BodyCodeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        minLines = 9,
        maxLines = 13,
        textStyle = TextStyle(
            color = PortalColors.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        ),
        cursorBrush = SolidColor(PortalColors.accent),
        modifier = modifier
            .background(PortalColors.codeInput, RoundedCornerShape(6.dp))
            .border(1.dp, PortalColors.codeInputBorder, RoundedCornerShape(6.dp)),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (value.isEmpty()) {
                    Text(
                        text = "{\n  \n}",
                        color = PortalColors.muted.copy(alpha = 0.42f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
internal fun BodyModeSelector(
    mode: String,
    jsonRejected: Boolean,
    onTxtClick: () -> Unit,
    onJsonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(vertical = 1.dp),
    ) {
        BodyModeOption(
            text = "TXT",
            selected = mode == "txt",
            enabled = true,
            invalid = false,
            onClick = onTxtClick,
        )
        Text("|", color = PortalColors.inputBorder, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
        BodyModeOption(
            text = "JSON",
            selected = mode == "json",
            enabled = true,
            invalid = jsonRejected,
            onClick = onJsonClick,
        )
    }
}

@Composable
internal fun BodyModeOption(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    invalid: Boolean,
    onClick: () -> Unit,
) {
    val color = when {
        invalid -> PortalColors.error.copy(alpha = 0.78f)
        selected -> PortalColors.accent
        else -> PortalColors.muted
    }
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
    )
}

@Composable
internal fun ResponseModeTabs(
    responseMode: String,
    onChange: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PortalColors.input)
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(8.dp))
            .padding(2.dp),
    ) {
        ResponseModeTab(text = "Body", selected = responseMode == "body", onClick = { onChange("body") })
        ResponseModeTab(text = "Error", selected = responseMode == "error", onClick = { onChange("error") })
    }
}

@Composable
internal fun ResponseModeTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) PortalColors.button else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) PortalColors.accent else PortalColors.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun CompactTextField(
    label: String?,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PortalTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = true,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun NetworkMockEditorSheetPreview() {
    Box(Modifier.background(PortalColors.background).fillMaxSize()) {
        NetworkMockEditorSheet(
            initialMock = PortalNetworkMock(id = "1"), onDismiss = {}, onSave = {}
        )
    }
}
