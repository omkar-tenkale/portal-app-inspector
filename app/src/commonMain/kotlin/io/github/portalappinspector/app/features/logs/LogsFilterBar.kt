package io.github.portalappinspector.app.features.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.features.network.NetworkFilterMode
import io.github.portalappinspector.app.ui.InlineConditionTextField
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.Text
import io.github.portalappinspector.app.ui.icons.PortalTabIcons
import io.github.portalappinspector.app.ui.ResponsiveRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogsFilterBar(
    filters: List<LogFilterRule>,
    filterMode: NetworkFilterMode,
    filterType: String,
    filterValue: String,
    onFilterModeChange: (NetworkFilterMode) -> Unit,
    onFilterTypeChange: (String) -> Unit,
    onFilterValueChange: (String) -> Unit,
    onAddFilter: (NetworkFilterMode, String) -> Unit,
    onDeleteFilter: (Int) -> Unit,
    onClear: () -> Unit,
    clearEnabled: Boolean,
) {
    ResponsiveRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = 6.dp, end = 8.dp),
        leftContent = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                filters.withIndex().forEach { indexedFilter ->
                    LogFilterChip(
                        rule = indexedFilter.value,
                        onDelete = { onDeleteFilter(indexedFilter.index) },
                    )
                }
                LogFilterAddRow(
                    mode = filterMode,
                    type = filterType,
                    value = filterValue,
                    onModeChange = onFilterModeChange,
                    onTypeChange = onFilterTypeChange,
                    onValueChange = onFilterValueChange,
                    onAdd = onAddFilter,
                )
            }
        },
        rightContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogsToolbarDivider()
                LogsToolbarActionButton(
                    icon = PortalTabIcons.Delete,
                    text = "Clear",
                    onClick = onClear,
                    enabled = clearEnabled,
                    modifier = Modifier.height(28.dp).padding(start = 10.dp),
                )
            }
        }
    )
}

@Composable
internal fun LogFilterAddRow(
    mode: NetworkFilterMode,
    type: String,
    value: String,
    onModeChange: (NetworkFilterMode) -> Unit,
    onTypeChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onAdd: (NetworkFilterMode, String) -> Unit,
) {
    val selectedType = type.takeIf { it in LogFilterTypes } ?: LogFilterTypes.first()
    LaunchedEffect(type) {
        if (type !in LogFilterTypes) {
            onTypeChange(LogFilterTypes.first())
        }
    }
    var activeMenu by remember { mutableStateOf(LogFilterMenu.Mode) }
    var menuVisible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 420.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(30.dp)
                .widthIn(max = 360.dp)
                .background(PortalColors.button, RoundedCornerShape(14.dp))
                .border(1.dp, PortalColors.inputBorder, RoundedCornerShape(14.dp))
                .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
        ) {
            LogFilterModeSelector(
                selected = mode,
                expanded = menuVisible && activeMenu == LogFilterMenu.Mode,
                onClick = {
                    if (activeMenu == LogFilterMenu.Mode) {
                        menuVisible = !menuVisible
                    } else {
                        activeMenu = LogFilterMenu.Mode
                        menuVisible = true
                    }
                },
            )
            InlineConditionTextField(
                value = value,
                onValueChange = onValueChange,
                onSubmit = { if (value.isNotBlank()) onAdd(mode, selectedType) },
                modifier = Modifier.width(110.dp),
            )
            Text("in", color = PortalColors.muted, fontSize = 12.sp)
            LogFilterTypeSelector(
                selected = selectedType,
                expanded = menuVisible && activeMenu == LogFilterMenu.Type,
                onClick = {
                    if (activeMenu == LogFilterMenu.Type) {
                        menuVisible = !menuVisible
                    } else {
                        activeMenu = LogFilterMenu.Type
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
                LogFilterMenu.Mode -> LogFilterModeMenu(
                    selected = mode,
                    onSelect = {
                        menuVisible = false
                        onModeChange(it)
                    },
                    modifier = Modifier.widthIn(max = 360.dp),
                )
                LogFilterMenu.Type -> LogFilterTypeMenu(
                    selected = selectedType,
                    onSelect = {
                        menuVisible = false
                        onTypeChange(it)
                    },
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            }
        }
    }
}

@Composable
internal fun LogFilterChip(
    rule: LogFilterRule,
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
            .clip(RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Text(
            text = rule.label(),
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
internal fun LogFilterModeSelector(
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
internal fun LogFilterModeMenu(
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
internal fun LogFilterTypeSelector(
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
        Text(selected.logFilterTypeLabel(), color = PortalColors.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Image(
            painter = rememberVectorPainter(if (expanded) PortalTabIcons.ArrowUp else PortalTabIcons.ArrowDown),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            colorFilter = ColorFilter.tint(PortalColors.muted),
        )
    }
}

@Composable
internal fun LogFilterTypeMenu(
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
        LogFilterTypes.forEach { option ->
            Text(
                text = option.logFilterTypeLabel(),
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
internal fun LogsToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp, vertical = 5.dp)
            .width(1.dp)
            .height(18.dp)
            .background(PortalColors.border.copy(alpha = 0.85f)),
    )
}

@Composable
internal fun LogsToolbarActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val active = enabled && hovered
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (active) PortalColors.button.copy(alpha = 0.72f) else Color.Transparent)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val contentColor = when {
            active -> PortalColors.text
            enabled -> PortalColors.muted
            else -> PortalColors.muted.copy(alpha = 0.46f)
        }
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            colorFilter = ColorFilter.tint(contentColor),
        )
        Text(
            text = text,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Preview
@Composable
private fun LogsFilterBarPreview() {
    Box(Modifier.background(PortalColors.background).padding(16.dp)) {
        LogsFilterBar(
            filters = listOf(LogFilterRule(NetworkFilterMode.Include, "tag", "Sample")),
            filterMode = NetworkFilterMode.Include,
            filterType = "tag",
            filterValue = "",
            onFilterModeChange = {},
            onFilterTypeChange = {},
            onFilterValueChange = {},
            onAddFilter = {_,_->},
            onDeleteFilter = {},
            onClear = {},
            clearEnabled = true
        )
    }
}
