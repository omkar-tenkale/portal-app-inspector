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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NetworkFilterBar(
    filters: List<NetworkFilterRule>,
    filterMode: NetworkFilterMode,
    filterType: String,
    filterValue: String,
    onFilterModeChange: (NetworkFilterMode) -> Unit,
    onFilterTypeChange: (String) -> Unit,
    onFilterValueChange: (String) -> Unit,
    onAddFilter: (NetworkFilterMode, String) -> Unit,
    onDeleteFilter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        filters.withIndex().forEach { indexedFilter ->
            NetworkFilterChip(rule = indexedFilter.value, onDelete = { onDeleteFilter(indexedFilter.index) })
        }
        NetworkFilterAddRow(
            mode = filterMode,
            type = filterType,
            value = filterValue,
            onModeChange = onFilterModeChange,
            onTypeChange = onFilterTypeChange,
            onValueChange = onFilterValueChange,
            onAdd = onAddFilter,
        )
    }
}

@Composable
internal fun NetworkFilterAddRow(
    mode: NetworkFilterMode,
    type: String,
    value: String,
    onModeChange: (NetworkFilterMode) -> Unit,
    onTypeChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onAdd: (NetworkFilterMode, String) -> Unit,
) {
    val selectedType = type.takeIf { it in NetworkFilterTypes } ?: NetworkFilterTypes.first()
    LaunchedEffect(type) {
        if (type !in NetworkFilterTypes) {
            onTypeChange(NetworkFilterTypes.first())
        }
    }
    var activeMenu by remember { mutableStateOf(NetworkFilterMenu.Mode) }
    var menuVisible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(30.dp)
                .fillMaxWidth()
                .background(PortalColors.button, RoundedCornerShape(14.dp))
                .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
                .padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
        ) {
            NetworkFilterModeSelector(
                selected = mode,
                expanded = menuVisible && activeMenu == NetworkFilterMenu.Mode,
                onClick = {
                    if (activeMenu == NetworkFilterMenu.Mode) {
                        menuVisible = !menuVisible
                    } else {
                        activeMenu = NetworkFilterMenu.Mode
                        menuVisible = true
                    }
                },
            )
            InlineConditionTextField(
                value = value,
                onValueChange = onValueChange,
                onSubmit = { if (value.isNotBlank()) onAdd(mode, selectedType) },
                modifier = Modifier.weight(1f),
            )
            Text("in", color = PortalColors.muted, fontSize = 12.sp)
            ConditionTypeSelector(
                selected = selectedType,
                expanded = menuVisible && activeMenu == NetworkFilterMenu.Type,
                onClick = {
                    if (activeMenu == NetworkFilterMenu.Type) {
                        menuVisible = !menuVisible
                    } else {
                        activeMenu = NetworkFilterMenu.Type
                        menuVisible = true
                    }
                },
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = value.isNotBlank()) { onAdd(mode, selectedType) },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = rememberVectorPainter(PortalTabIcons.Plus),
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    colorFilter = ColorFilter.tint(if (value.isBlank()) PortalColors.muted else PortalColors.text),
                )
            }
        }
        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn(animationSpec = tween(120)) + expandVertically(animationSpec = tween(140)),
            exit = fadeOut(animationSpec = tween(90)) + shrinkVertically(animationSpec = tween(120)),
        ) {
            when (activeMenu) {
                NetworkFilterMenu.Mode -> NetworkFilterModeMenu(
                    selected = mode,
                    onSelect = {
                        menuVisible = false
                        onModeChange(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                NetworkFilterMenu.Type -> ConditionTypeMenu(
                    options = NetworkFilterTypes,
                    selected = selectedType,
                    onSelect = {
                        menuVisible = false
                        onTypeChange(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private enum class NetworkFilterMenu { Mode, Type }

@Composable
internal fun NetworkFilterModeSelector(
    selected: NetworkFilterMode,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
    ) {
        Text(selected.label, color = PortalColors.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Image(
            painter = rememberVectorPainter(if (expanded) PortalTabIcons.ArrowUp else PortalTabIcons.ArrowDown),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            colorFilter = ColorFilter.tint(PortalColors.muted),
        )
    }
}

@Composable
internal fun NetworkFilterModeMenu(
    selected: NetworkFilterMode,
    onSelect: (NetworkFilterMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .background(PortalColors.card, RoundedCornerShape(14.dp))
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        NetworkFilterMode.values().forEach { option ->
            Text(
                text = option.label,
                color = if (option == selected) PortalColors.accent else PortalColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .background(if (option == selected) PortalColors.button else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
internal fun ConditionTypeSelector(
    selected: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
    ) {
        Text(selected.matcherLabel(), color = PortalColors.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Image(
            painter = rememberVectorPainter(if (expanded) PortalTabIcons.ArrowUp else PortalTabIcons.ArrowDown),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            colorFilter = ColorFilter.tint(PortalColors.muted),
        )
    }
}

@Composable
internal fun ConditionTypeMenu(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .background(PortalColors.card, RoundedCornerShape(14.dp))
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        options.forEach { option ->
            Text(
                text = option.matcherLabel(),
                color = if (option == selected) PortalColors.accent else PortalColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .background(if (option == selected) PortalColors.button else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
internal fun NetworkFilterChip(
    rule: NetworkFilterRule,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(30.dp)
            .widthIn(max = 300.dp)
            .background(PortalColors.button, RoundedCornerShape(14.dp))
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Text(
            text = "${rule.mode.label} ${rule.matcher.conditionChipLabel()}",
            color = PortalColors.text,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberVectorPainter(PortalTabIcons.Close),
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                colorFilter = ColorFilter.tint(PortalColors.muted),
            )
        }
    }
}

@Composable
internal fun ConditionChip(
    matcher: PortalNetworkMockMatcher,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(30.dp)
            .widthIn(max = 260.dp)
            .background(PortalColors.button, RoundedCornerShape(14.dp))
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Text(
            text = matcher.conditionChipLabel(),
            color = PortalColors.text,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberVectorPainter(PortalTabIcons.Close),
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                colorFilter = ColorFilter.tint(PortalColors.muted),
            )
        }
    }
}

@Composable
internal fun AddConditionPill(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(30.dp)
            .background(PortalColors.button, RoundedCornerShape(14.dp))
            .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Text("Add another", color = PortalColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = rememberVectorPainter(PortalTabIcons.Plus),
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                colorFilter = ColorFilter.tint(PortalColors.muted),
            )
        }
    }
}

@Composable
internal fun ConditionAddRow(
    type: String,
    value: String,
    onTypeChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit,
    onAdd: (String) -> Unit,
) {
    val selectedType = type.takeIf { it in MockConditionTypes } ?: MockConditionTypes.first()
    LaunchedEffect(type) {
        if (type !in MockConditionTypes) {
            onTypeChange(MockConditionTypes.first())
        }
    }
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 460.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(30.dp)
                .widthIn(max = 460.dp)
                .background(PortalColors.button, RoundedCornerShape(14.dp))
                .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
                .padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
        ) {
            InlineConditionTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(110.dp),
            )
            Text("in", color = PortalColors.muted, fontSize = 12.sp)
            ConditionTypeSelector(
                selected = selectedType,
                expanded = expanded,
                onClick = { expanded = !expanded },
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { if (value.isBlank()) onCancel() else onAdd(selectedType) },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = rememberVectorPainter(if (value.isBlank()) PortalTabIcons.Close else PortalTabIcons.Plus),
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    colorFilter = ColorFilter.tint(if (value.isBlank()) PortalColors.muted else PortalColors.text),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(120)) + expandVertically(animationSpec = tween(140)),
            exit = fadeOut(animationSpec = tween(90)) + shrinkVertically(animationSpec = tween(120)),
        ) {
            ConditionTypeMenu(
                options = MockConditionTypes,
                selected = selectedType,
                onSelect = {
                    expanded = false
                    onTypeChange(it)
                },
                modifier = Modifier.widthIn(max = 460.dp),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConditionFlow(
    matchers: List<PortalNetworkMockMatcher>,
    onDelete: (Int) -> Unit,
    trailing: @Composable () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        matchers.withIndex().forEach { indexedMatcher ->
            ConditionChip(matcher = indexedMatcher.value, onDelete = { onDelete(indexedMatcher.index) })
        }
        trailing()
    }
}

@Preview
@Composable
private fun NetworkFilterBarPreview() {
    Box(Modifier.background(PortalColors.background).padding(16.dp)) {
        NetworkFilterBar(
            filters = listOf(NetworkFilterRule(NetworkFilterMode.Include, PortalNetworkMockMatcher("urlContains", null, "users"))),
            filterMode = NetworkFilterMode.Include,
            filterType = "urlContains",
            filterValue = "",
            onFilterModeChange = {},
            onFilterTypeChange = {},
            onFilterValueChange = {},
            onAddFilter = { _, _ -> },
            onDeleteFilter = {}
        )
    }
}
