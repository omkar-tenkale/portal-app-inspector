package io.github.portalappinspector.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import com.sebastianneubauer.jsontree.TreeColors

internal val PortalJsonTreeColors = TreeColors(
    keyColor = Color(0xFF9CDCFE),
    stringValueColor = Color(0xFFCE9178),
    numberValueColor = Color(0xFFB5CEA8),
    booleanValueColor = Color(0xFF569CD6),
    nullValueColor = Color(0xFF569CD6),
    indexColor = Color(0xFF858585),
    symbolColor = Color(0xFFD4D4D4),
    iconColor = Color(0xFF9AA0A6),
    highlightColor = Color(0x664B5563),
    selectedHighlightColor = Color(0xFF264F78),
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
