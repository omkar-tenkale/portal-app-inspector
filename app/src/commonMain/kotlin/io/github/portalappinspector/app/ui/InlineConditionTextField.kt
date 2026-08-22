package io.github.portalappinspector.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun InlineConditionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (() -> Unit)? = null,
    showUnderline: Boolean = false,
    modifier: Modifier = Modifier.Companion,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = PortalColors.text,
            fontFamily = FontFamily.Companion.SansSerif,
            fontSize = 12.sp,
        ),
        cursorBrush = SolidColor(PortalColors.accent),
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.Companion.KeyDown && event.key == Key.Companion.Enter && onSubmit != null) {
                    onSubmit()
                    true
                } else {
                    false
                }
            },
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.Companion.fillMaxWidth()) {
                if (value.isEmpty() || showUnderline) {
                    Box(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .height(1.dp)
                            .align(Alignment.Companion.BottomStart)
                            .background(PortalColors.muted.copy(alpha = 0.62f)),
                    )
                }
                innerTextField()
            }
        },
    )
}


@Preview
@Composable
private fun InlineConditionTextFieldPreview() {
    Box(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        InlineConditionTextField(
            value = "status == 200",
            onValueChange = {},
            showUnderline = true
        )
    }
}
