package io.github.portalappinspector.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.portalappinspector.app.ui.ResponsiveRow

@Composable
internal fun ResponsiveRow(
    modifier: Modifier = Modifier,
    breakpoint: Dp = 600.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    columnCrossAxisAlignment: Alignment.Horizontal = Alignment.End,
    spacing: Dp = 8.dp,
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        if (maxWidth >= breakpoint) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = verticalAlignment,
                horizontalArrangement = horizontalArrangement
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    leftContent()
                }
                Spacer(modifier = Modifier.width(spacing))
                Box {
                    rightContent()
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                Box {
                    leftContent()
                }
                Box(modifier = Modifier.align(columnCrossAxisAlignment)) {
                    rightContent()
                }
            }
        }
    }
}