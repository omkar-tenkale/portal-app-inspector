package io.github.portalappinspector.app.ui

import androidx.compose.foundation.background
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier.Companion,
    color: Color = PortalColors.text,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = FontFamily.Companion.SansSerif,
    lineHeight: TextUnit = TextUnit.Companion.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Companion.Clip,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            lineHeight = lineHeight,
            textAlign = textAlign ?: TextAlign.Companion.Unspecified,
        ),
        overflow = overflow,
        maxLines = maxLines,
    )
}


@Preview
@Composable
private fun TextPreview() {
    Box(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        Text(text = "Hello Portal Inspector!")
    }
}
