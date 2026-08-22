package io.github.portalappinspector.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PortalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.Companion,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = TextStyle(
        color = PortalColors.text,
        fontFamily = FontFamily.Companion.SansSerif,
        fontSize = 13.sp,
    ),
    invalid: Boolean = false,
) {
    val borderColor by animateColorAsState(
        if (invalid) PortalColors.error.copy(alpha = 0.74f) else PortalColors.inputBorder,
    )
    val backgroundColor by animateColorAsState(
        if (invalid) PortalColors.error.copy(alpha = 0.08f) else PortalColors.input,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = textStyle,
        cursorBrush = SolidColor(PortalColors.accent),
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
        decorationBox = { innerTextField ->
            Column(
                modifier = Modifier.Companion.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        color = PortalColors.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Companion.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Companion.Ellipsis,
                    )
                }
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            color = PortalColors.muted.copy(alpha = 0.72f),
                            fontSize = textStyle.fontSize.takeIf { it != TextUnit.Companion.Unspecified }
                                ?: 13.sp,
                            fontFamily = textStyle.fontFamily ?: FontFamily.Companion.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Companion.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}


@Preview
@Composable
private fun PortalTextFieldPreview() {
    Box(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        PortalTextField(
            value = "Search network calls...",
            onValueChange = {},
            label = "Filter",
            placeholder = "Type here..."
        )
    }
}
