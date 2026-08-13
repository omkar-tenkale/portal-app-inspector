package io.github.portalappinspector.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.portalappinspector.app.ui.Text
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun Preview() {
    Box(Modifier.fillMaxSize().background(Color(0xFF262626))){
        Box(Modifier.align(Alignment.Center)){
            PreviewContent()
        }
    }
}

@Preview
@Composable
fun PreviewContent() {
    Text(text = "Click Me!")
}