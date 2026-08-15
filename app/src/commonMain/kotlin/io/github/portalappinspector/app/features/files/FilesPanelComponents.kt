package io.github.portalappinspector.app.features.files

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.data.PortalFileItem
import io.github.portalappinspector.app.data.PortalViewedFile
import io.github.portalappinspector.app.ui.JsonText
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.PulsatingDots
import io.github.portalappinspector.app.ui.RowDivider
import io.github.portalappinspector.app.ui.SearchIcon
import io.github.portalappinspector.app.ui.Text
import io.github.portalappinspector.app.ui.icons.PortalTabIcons
import io.github.portalappinspector.app.util.formatSize
import kotlinx.coroutines.delay

internal val FileRowHeight = 40.dp
internal val FileRowMetaWidth = 160.dp

@Composable
internal fun FilesRootsHeader(
    path: String?,
    loading: Boolean,
    filter: String,
    onFilterChange: (String) -> Unit,
    onPathClick: (String?) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            filePathSegments(path).forEachIndexed { index, segment ->
                if (index > 0) {
                    Image(
                        painter = rememberVectorPainter(PortalTabIcons.ArrowRight),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        colorFilter = ColorFilter.tint(PortalColors.muted.copy(alpha = 0.7f)),
                    )
                }
                FileBreadcrumbSegment(
                    segment = segment,
                    selected = segment.path == path,
                    onClick = { onPathClick(segment.path) },
                )
            }
        }
        FilesSearchField(
            value = filter,
            onValueChange = onFilterChange,
            modifier = Modifier.width(180.dp),
        )
        Box(
            modifier = Modifier.size(width = 42.dp, height = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (loading) {
                PulsatingDots(color = PortalColors.accent, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
internal fun FileBreadcrumbSegment(
    segment: FilePathSegment,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background by animateColorAsState(
        if (hovered && !selected) PortalColors.button.copy(alpha = 0.58f) else Color.Transparent,
    )
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = segment.label,
            color = if (selected || segment.path == null) PortalColors.text else PortalColors.muted,
            fontSize = 13.sp,
            fontWeight = if (selected || segment.path == null) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
internal fun FilesSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = PortalColors.text,
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp,
        ),
        cursorBrush = SolidColor(PortalColors.accent),
        modifier = modifier.height(28.dp),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SearchIcon(
                    color = PortalColors.muted,
                    modifier = Modifier.size(14.dp),
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Search...",
                            color = PortalColors.muted.copy(alpha = 0.72f),
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
internal fun ParentDirectoryRow(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background by animateColorAsState(
        targetValue = if (enabled && hovered) PortalColors.card.copy(alpha = 0.58f) else Color.Transparent,
    )

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FileRowHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(background)
                .hoverable(interactionSource = interactionSource, enabled = enabled)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PortalFileIcon(
                kind = PortalFileIconKind.ParentDirectory,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "..",
                color = PortalColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        RowDivider()
    }
}

@Composable
internal fun FileRow(
    item: PortalFileItem,
    pinned: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onCopyPath: () -> Unit,
    onDelete: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val rowClickable = item.directory || item.isViewableFile()
    val background by animateColorAsState(
        targetValue = if (hovered) PortalColors.card.copy(alpha = 0.58f) else Color.Transparent,
    )
    var deleteArmed by remember(item.path) { mutableStateOf(false) }
    val actionsAlpha by animateFloatAsState(
        targetValue = if (hovered || deleteArmed) 1f else 0f,
        animationSpec = tween(durationMillis = 130),
    )
    val sizeAlpha by animateFloatAsState(
        targetValue = if (hovered || deleteArmed) 0f else 1f,
        animationSpec = tween(durationMillis = 130),
    )

    LaunchedEffect(deleteArmed) {
        if (deleteArmed) {
            delay(2_000L)
            deleteArmed = false
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FileRowHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(background)
                .hoverable(interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = rowClickable,
                    onClick = onClick,
                )
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PortalFileIcon(
                kind = item.iconKind(),
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = item.name,
                color = PortalColors.text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.width(FileRowMetaWidth),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = formatSize(item.sizeBytes),
                    color = PortalColors.muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.alpha(sizeAlpha),
                )
                FileRowActions(
                    item = item,
                    pinned = pinned,
                    deleteArmed = deleteArmed,
                    alpha = actionsAlpha,
                    interactionSource = interactionSource,
                    onDownload = onDownload,
                    onCopyPath = onCopyPath,
                    onDelete = {
                        if (deleteArmed) {
                            deleteArmed = false
                            onDelete()
                        } else {
                            deleteArmed = true
                        }
                    },
                    onTogglePinned = onTogglePinned,
                )
            }
        }
    }
}

@Composable
internal fun FileRowActions(
    item: PortalFileItem,
    pinned: Boolean,
    deleteArmed: Boolean,
    alpha: Float,
    interactionSource: MutableInteractionSource,
    onDownload: () -> Unit,
    onCopyPath: () -> Unit,
    onDelete: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    Row(
        modifier = Modifier
            .alpha(alpha)
            .hoverable(interactionSource = interactionSource),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val actionsEnabled = alpha > 0.01f
        FileRowActionButton(
            icon = PortalTabIcons.Download,
            interactionSource = interactionSource,
            enabled = actionsEnabled,
            onClick = onDownload,
        )
        FileRowActionButton(
            icon = PortalTabIcons.CopyPath,
            interactionSource = interactionSource,
            enabled = actionsEnabled,
            onClick = onCopyPath,
        )
        FileRowActionButton(
            icon = if (pinned) PortalTabIcons.StarFilled else PortalTabIcons.Star,
            interactionSource = interactionSource,
            enabled = actionsEnabled,
            onClick = onTogglePinned,
        )
        if (!item.path.isRootFilePath()) {
            FileRowActionButton(
                icon = PortalTabIcons.Delete,
                tint = if (deleteArmed) PortalColors.error else PortalColors.text,
                interactionSource = interactionSource,
                enabled = actionsEnabled,
                onClick = onDelete,
            )
        }
    }
}

@Composable
internal fun FileRowActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = PortalColors.text,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

@Composable
internal fun FileTextViewer(
    file: PortalViewedFile,
    showParentRow: Boolean,
    onParentClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (showParentRow) {
            ParentDirectoryRow(
                onClick = onParentClick,
                enabled = enabled,
            )
        }
        JsonText(
            jsonString = file.text,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
    }
}

@Composable
internal fun FilesSectionRow(title: String) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                color = PortalColors.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        RowDivider()
    }
}
