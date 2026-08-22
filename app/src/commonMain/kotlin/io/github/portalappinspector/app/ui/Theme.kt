package io.github.portalappinspector.app.ui

import androidx.compose.ui.graphics.Color
import com.sebastianneubauer.jsontree.TreeColors

internal val PortalJsonTreeColors = TreeColors(
    keyColor = Color(0xFFDCEBFA),
    stringValueColor = Color(0xFFA6D854),
    numberValueColor = Color(0xFF6CB6FF),
    booleanValueColor = Color(0xFF6CB6FF),
    nullValueColor = Color(0xFF6CB6FF),
    indexColor = Color(0xFF8A949E),
    symbolColor = Color(0xFFD4D9DE),
    iconColor = Color(0xFFC8D0D8),
    highlightColor = Color(0xFF755C18),
    selectedHighlightColor = Color(0xFFFFC01E),
)

internal object PortalColors {
    val background = Color(0xFF0F0F0F)
    val topBar = Color(0xFF0F0F0F)
    val card = Color(0xFF262626)
    val popup = Color(0xFF282828)
    val border = Color(0xFF404041)
    val text = Color(0xFFF5F5F5)
    val muted = Color(0xFFA8A8A8)
    val accent = Color(0xFFFFA116)
    val logo = Color(0xFFFFC01E)
    val success = Color(0xFF2DB55D)
    val warning = Color(0xFFFFA116)
    val error = Color(0xFFFF6B6B)
    val button = Color(0xFF333333)
    val input = Color(0xFF303030)
    val inputBorder = Color(0xFF4A4A4A)
    val codeInput = Color(0xFF1E1E1E)
    val codeInputBorder = Color(0xFF4B5563)
    val submitText = Color(0xFFFFFFFF)
}
