package io.github.portalappinspector.app.features.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.portalappinspector.app.ui.JsonText
import io.github.portalappinspector.app.ui.PortalButton
import io.github.portalappinspector.app.ui.PortalButtonKind
import io.github.portalappinspector.app.ui.PortalColors
import io.github.portalappinspector.app.ui.StatusCard
import io.github.portalappinspector.app.ui.Text

@Composable
internal fun NetworkResponsePanel(call: PortalNetworkCall) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ResponseBodyHeader(
            call = call,
            actionText = "Response tab",
            onAction = {},
            actionEnabled = false,
        )
        ResponseBodyContent(call = call, modifier = Modifier.fillMaxSize())
    }
}

@Composable
internal fun ResponseBodyHeader(
    call: PortalNetworkCall,
    actionText: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
    extraActionText: String? = null,
    onExtraAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "${call.method} ${call.endpoint}",
                color = PortalColors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(call.statusCode?.toString() ?: "ERR")
                    append(" | ")
                    append(call.durationMillis)
                    append(" ms")
                    call.responseContentType?.let {
                        append(" | ")
                        append(it)
                    }
                    if (call.responseBodyTruncated) append(" | truncated")
                },
                color = PortalColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (extraActionText != null && onExtraAction != null) {
            PortalButton(
                text = extraActionText,
                onClick = onExtraAction,
                kind = PortalButtonKind.Primary,
            )
        }
        PortalButton(
            text = actionText,
            onClick = onAction,
            enabled = actionEnabled,
            kind = PortalButtonKind.Primary,
        )
        if (secondaryActionText != null && onSecondaryAction != null) {
            PortalButton(
                text = secondaryActionText,
                onClick = onSecondaryAction,
            )
        }
    }
}

@Composable
internal fun ResponseBodyContent(
    call: PortalNetworkCall,
    modifier: Modifier = Modifier,
) {
    val body = call.responseBody
    if (body == null) {
        StatusCard("No response body", "This network call did not include a captured response body.", PortalColors.muted)
        return
    }

    JsonText(
        jsonString = body,
        modifier = modifier,
        containerColor = PortalColors.codeInput,
        borderColor = PortalColors.codeInputBorder,
    )
}

@Preview
@Composable
private fun NetworkResponsePanelPreview() {
    val call = io.github.portalappinspector.app.PreviewFixtures.dummyNetworkCall
    Box(Modifier.background(PortalColors.background).fillMaxSize()) {
        NetworkResponsePanel(call = call)
    }
}
