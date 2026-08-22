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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.PortalSwitch
import io.github.portalappinspector.app.ui.RowDivider
import io.github.portalappinspector.app.ui.StatusCard
import io.github.portalappinspector.app.ui.Text
import io.github.portalappinspector.app.ui.icons.PortalTabIcons
import kotlinx.coroutines.delay

@Composable
internal fun NetworkMocksSheet(
    mocks: List<PortalNetworkMock>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (PortalNetworkMock) -> Unit,
    onToggle: (PortalNetworkMock, Boolean) -> Unit,
    onDelete: (PortalNetworkMock) -> Unit,
) {
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
                .height(460.dp)
                .background(PortalColors.card, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .border(1.dp, PortalColors.border, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            NetworkMocksTopBar(
                enabledCount = mocks.count { it.enabled },
                totalCount = mocks.size,
                onAdd = onAdd,
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
                if (mocks.isEmpty()) {
                    StatusCard("No mocks", "Create a mock from a captured response body.", PortalColors.muted)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(mocks, key = { "mock-${it.id}" }) { mock ->
                            MockListRow(
                                mock = mock,
                                onEdit = { onEdit(mock) },
                                onToggle = { onToggle(mock, it) },
                                onDelete = { onDelete(mock) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkMocksTopBar(
    enabledCount: Int,
    totalCount: Int,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Mocks",
                color = PortalColors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$enabledCount enabled / $totalCount total",
                color = PortalColors.muted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        NetworkDetailActionButton(
            icon = PortalTabIcons.Plus,
            text = "Add",
            onClick = onAdd,
            modifier = Modifier.height(28.dp),
        )
        NetworkPanelToolbarDivider()
        NetworkDetailIconButton(
            icon = PortalTabIcons.Close,
            onClick = onDismiss,
        )
    }
}

@Composable
internal fun MockListRow(
    mock: PortalNetworkMock,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var deleteArmed by remember(mock.id) { mutableStateOf(false) }

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
                .clickable(onClick = onEdit)
                .padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PortalSwitch(checked = mock.enabled, onCheckedChange = onToggle)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(mock.displayLabel(), color = PortalColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(mock.response.label(), color = PortalColors.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            NetworkDetailIconButton(icon = PortalTabIcons.Edit, onClick = onEdit)
            NetworkDetailIconButton(
                icon = PortalTabIcons.Delete,
                onClick = {
                    if (deleteArmed) {
                        onDelete()
                    } else {
                        deleteArmed = true
                    }
                },
                tint = if (deleteArmed) PortalColors.error else null,
            )
        }
        RowDivider()
    }
}

@Preview
@Composable
private fun NetworkMocksSheetPreview() {
    Box(Modifier.background(PortalColors.background).fillMaxSize()) {
        NetworkMocksSheet(
            mocks = emptyList(), onDismiss = {}, onAdd = {}, onEdit = {}, onToggle = { _, _ -> }, onDelete = {}
        )
    }
}
