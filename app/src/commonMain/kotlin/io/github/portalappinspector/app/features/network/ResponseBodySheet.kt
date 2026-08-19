package io.github.portalappinspector.app.features.network

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.focus.*
import kotlinx.coroutines.*
import io.github.portalappinspector.app.data.*
import io.github.portalappinspector.app.ui.*
import io.github.portalappinspector.app.ui.icons.*
import io.github.portalappinspector.app.util.*
import kotlinx.serialization.json.*

@Composable
internal fun ResponseBodySheet(
    call: PortalNetworkCall,
    onDismiss: () -> Unit,
    onOpenTab: () -> Unit,
    onMock: () -> Unit,
) {
    var selectedSection by remember(call.id) { mutableStateOf(NetworkDetailSection.Overview) }
    var menuExpanded by remember(call.id) { mutableStateOf(false) }

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
                .height(520.dp)
                .background(PortalColors.card, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .border(1.dp, PortalColors.border, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            NetworkDetailTopBar(
                selectedSection = selectedSection,
                menuExpanded = menuExpanded,
                onToggleMenu = { menuExpanded = !menuExpanded },
                onMock = onMock,
                onOpenTab = onOpenTab,
                onDismiss = onDismiss,
            )
            RowDivider()
            AnimatedVisibility(
                visible = menuExpanded,
                enter = fadeIn(animationSpec = tween(120)) + expandVertically(animationSpec = tween(140)),
                exit = fadeOut(animationSpec = tween(90)) + shrinkVertically(animationSpec = tween(120)),
            ) {
                NetworkDetailSectionMenu(
                    selected = selectedSection,
                    onSelect = {
                        selectedSection = it
                        menuExpanded = false
                    },
                )
            }
            NetworkDetailContent(
                call = call,
                selectedSection = selectedSection,
                onSectionSelect = { selectedSection = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
            )
        }
    }
}

private enum class NetworkDetailSection(val label: String) {
    Overview("Overview"),
    Headers("Headers"),
    RequestBody("Request body"),
    ResponseBody("Response body"),
}

@Composable
private fun NetworkDetailTopBar(
    selectedSection: NetworkDetailSection,
    menuExpanded: Boolean,
    onToggleMenu: () -> Unit,
    onMock: () -> Unit,
    onOpenTab: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkDetailSectionSelector(
            selectedSection = selectedSection,
            expanded = menuExpanded,
            onClick = onToggleMenu,
        )
        Spacer(Modifier.weight(1f))
        NetworkDetailActionButton(
            icon = PortalTabIcons.Mock,
            text = "Mock",
            onClick = onMock,
            modifier = Modifier.height(28.dp),
        )
        NetworkPanelToolbarDivider()
        NetworkDetailIconButton(
            icon = PortalTabIcons.OpenInNew,
            onClick = onOpenTab,
        )
        NetworkDetailIconButton(
            icon = PortalTabIcons.Close,
            onClick = onDismiss,
        )
    }
}

@Composable
private fun NetworkDetailSectionSelector(
    selectedSection: NetworkDetailSection,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (hovered) PortalColors.button.copy(alpha = 0.7f) else Color.Transparent)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = selectedSection.label,
            color = PortalColors.text,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Image(
            painter = rememberVectorPainter(if (expanded) PortalTabIcons.ArrowUp else PortalTabIcons.ArrowDown),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            colorFilter = ColorFilter.tint(PortalColors.muted),
        )
    }
}

@Composable
private fun NetworkDetailSectionMenu(
    selected: NetworkDetailSection,
    onSelect: (NetworkDetailSection) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .padding(start = 10.dp, bottom = 8.dp)
            .widthIn(max = 640.dp)
            .background(PortalColors.card, RoundedCornerShape(14.dp))
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        NetworkDetailSection.values().forEach { section ->
            Text(
                text = section.label,
                color = if (section == selected) PortalColors.accent else PortalColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .background(
                        if (section == selected) PortalColors.button else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(section) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun NetworkDetailContent(
    call: PortalNetworkCall,
    selectedSection: NetworkDetailSection,
    onSectionSelect: (NetworkDetailSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (selectedSection) {
        NetworkDetailSection.Overview -> NetworkRequestOverview(
            call = call,
            onResponseBodyClick = { onSectionSelect(NetworkDetailSection.ResponseBody) },
            modifier = modifier,
        )
        NetworkDetailSection.Headers -> NetworkHeadersPreview(
            requestHeaders = call.requestHeaders,
            responseHeaders = call.responseHeaders,
            modifier = modifier,
        )
        NetworkDetailSection.RequestBody -> NetworkBodyPreview(
            body = call.requestBody,
            contentType = call.requestContentType,
            emptyTitle = "No request body",
            emptyMessage = "This network call did not include a captured request body.",
            truncated = call.requestBodyTruncated,
            modifier = modifier,
        )
        NetworkDetailSection.ResponseBody -> NetworkBodyPreview(
            body = call.responseBody,
            contentType = call.responseContentType,
            emptyTitle = "No response body",
            emptyMessage = "This network call did not include a captured response body.",
            truncated = call.responseBodyTruncated,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NetworkRequestOverview(
    call: PortalNetworkCall,
    onResponseBodyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timezoneOffsetMinutes = remember { jsTimezoneOffsetMinutes().toLong() }
    val use12HourClock = remember { jsUses12HourClock() }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = "${call.method} ${call.endpoint}",
                color = PortalColors.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = call.url,
                color = PortalColors.muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NetworkOverviewMetric("Status", call.statusCode?.toString() ?: "ERR", networkStatusColor(call.statusCode))
            NetworkOverviewMetric("Duration", "${call.durationMillis} ms")
            NetworkOverviewMetric(
                label = "Response size",
                value = formatNetworkSize(call.responseBodySizeBytes),
                onClick = onResponseBodyClick,
            )
            NetworkOverviewMetric("Request size", formatNetworkSize(call.requestBodySizeBytes))
            NetworkOverviewMetric(
                label = "Time",
                value = formatAbsoluteLogTime(
                    epochMillis = call.timestampEpochMillis,
                    timezoneOffsetMinutes = timezoneOffsetMinutes,
                    use12HourClock = use12HourClock,
                ),
            )
            NetworkOverviewMetric("Response type", call.responseContentType ?: "-")
            NetworkOverviewMetric("Request type", call.requestContentType ?: "-")
            NetworkOverviewMetric("Mocked", if (call.isMocked) "Yes" else "No")
        }
        call.error?.let { error ->
            StatusCard("Request error", error, PortalColors.error)
        }
        if (call.responseBodyTruncated || call.requestBodyTruncated) {
            StatusCard("Captured body truncated", "One or more captured bodies were shortened by the source plugin.", PortalColors.warning)
        }
    }
}

@Composable
private fun NetworkOverviewMetric(
    label: String,
    value: String,
    valueColor: Color = PortalColors.text,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Column(
        modifier = Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (hovered && onClick != null) PortalColors.button else PortalColors.input.copy(alpha = 0.72f),
            )
            .border(
                1.dp,
                if (onClick != null) PortalColors.accent.copy(alpha = if (hovered) 0.7f else 0.38f) else PortalColors.inputBorder.copy(alpha = 0.7f),
                RoundedCornerShape(7.dp),
            )
            .hoverable(interactionSource = interactionSource, enabled = onClick != null)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = onClick != null,
                onClick = { onClick?.invoke() },
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = if (hovered && onClick != null) PortalColors.text else PortalColors.muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onClick != null) {
                Image(
                    painter = rememberVectorPainter(PortalTabIcons.ArrowRight),
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    colorFilter = ColorFilter.tint(if (hovered) PortalColors.accent else PortalColors.muted),
                )
            }
        }
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NetworkHeadersPreview(
    requestHeaders: Map<String, List<String>>,
    responseHeaders: Map<String, List<String>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NetworkLabeledTextPreview(
            label = "Request headers",
            value = requestHeaders.toNetworkHeaderText(),
            emptyText = "No request headers captured.",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        NetworkLabeledTextPreview(
            label = "Response headers",
            value = responseHeaders.toNetworkHeaderText(),
            emptyText = "No response headers captured.",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun NetworkLabeledTextPreview(
    label: String,
    value: String,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = PortalColors.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        NetworkPlainTextPreview(
            value = value,
            emptyText = emptyText,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun NetworkBodyPreview(
    body: String?,
    contentType: String?,
    emptyTitle: String,
    emptyMessage: String,
    truncated: Boolean,
    modifier: Modifier = Modifier,
) {
    if (body == null) {
        StatusCard(emptyTitle, emptyMessage, PortalColors.muted)
        return
    }
    if (isJsonContentType(contentType)) {
        JsonText(
            jsonString = body,
            modifier = modifier,
            initialMode = JsonTextMode.Json,
            containerColor = PortalColors.codeInput,
            borderColor = if (truncated) PortalColors.warning.copy(alpha = 0.74f) else PortalColors.codeInputBorder,
        )
    } else {
        NetworkPlainTextPreview(
            value = body,
            emptyText = "",
            modifier = modifier,
            borderColor = if (truncated) PortalColors.warning.copy(alpha = 0.74f) else PortalColors.codeInputBorder,
        )
    }
}

@Composable
private fun NetworkPlainTextPreview(
    value: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    borderColor: Color = PortalColors.codeInputBorder,
) {
    val textValue = value.ifBlank { emptyText }
    val textScrollState = rememberScrollState()
    BasicTextField(
        value = textValue,
        onValueChange = {},
        readOnly = true,
        textStyle = TextStyle(
            color = if (value.isBlank()) PortalColors.muted else PortalColors.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        ),
        cursorBrush = SolidColor(PortalColors.accent),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PortalColors.codeInput)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .verticalScroll(textScrollState),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {
                innerTextField()
            }
        },
    )
}

internal fun Map<String, List<String>>.toNetworkHeaderText(): String =
    entries.joinToString("\n") { (name, values) ->
        "$name: ${values.joinToString(", ")}"
    }

internal fun isJsonContentType(contentType: String?): Boolean {
    val normalized = contentType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?: return false
    return normalized == "application/json" || normalized.endsWith("+json")
}

@Preview
@Composable
private fun ResponseBodySheetPreview() {
    val call = PortalNetworkCall(
        id = 1L, timestampEpochMillis = 1690000000000L, method = "GET", url = "https://api.test.com/v1/users", endpoint = "/v1/users",
        statusCode = 200, durationMillis = 145L, error = null, responseBody = "{\"status\": \"ok\"}", responseContentType = "application/json",
        responseBodySizeBytes = 24L, responseBodyTruncated = false, isMocked = true
    )
    Box(Modifier.background(PortalColors.background).fillMaxSize()) {
        ResponseBodySheet(call = call, onDismiss = {}, onOpenTab = {}, onMock = {})
    }
}
