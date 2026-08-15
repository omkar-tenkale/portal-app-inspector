package io.github.portalappinspector.app.features.files

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.portalappinspector.app.ui.PortalColors

internal enum class PortalFileIconKind {
    ParentDirectory,
    Folder,
    DefaultFile,
    ImageFile,
    TextFile,
}

@Composable
internal fun PortalFileIcon(
    kind: PortalFileIconKind,
    modifier: Modifier = Modifier,
) {
    val color = when (kind) {
        PortalFileIconKind.ParentDirectory -> PortalColors.accent
        PortalFileIconKind.Folder -> PortalColors.accent
        PortalFileIconKind.ImageFile -> PortalColors.success
        PortalFileIconKind.TextFile -> PortalColors.text
        PortalFileIconKind.DefaultFile -> PortalColors.muted
    }

    Canvas(modifier = modifier) {
        when (kind) {
            PortalFileIconKind.ParentDirectory -> drawParentDirectoryIcon(color)
            PortalFileIconKind.Folder -> drawFolderIcon(color)
            PortalFileIconKind.DefaultFile -> drawDefaultFileIcon(color)
            PortalFileIconKind.ImageFile -> drawImageFileIcon(color)
            PortalFileIconKind.TextFile -> drawTextFileIcon(color)
        }
    }
}

internal fun DrawScope.drawParentDirectoryIcon(color: Color) {
    val stroke = Stroke(width = size.minDimension / 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun x(value: Float) = size.width * value / 48f
    fun y(value: Float) = size.height * value / 48f

    val arrow = Path().apply {
        moveTo(x(12f), y(16f))
        lineTo(x(20f), y(8f))
        lineTo(x(28f), y(16f))
    }
    val stem = Path().apply {
        moveTo(x(36f), y(40f))
        lineTo(x(23f), y(40f))
        cubicTo(x(21.3431f), y(40f), x(20f), y(38.6569f), x(20f), y(37f))
        lineTo(x(20f), y(8f))
    }

    drawPath(arrow, color = color, style = stroke)
    drawPath(stem, color = color, style = stroke)
}

internal fun DrawScope.drawFolderIcon(color: Color) {
    val stroke = Stroke(width = size.minDimension / 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun x(value: Float) = size.width * value / 48f
    fun y(value: Float) = size.height * value / 48f

    val back = Path().apply {
        moveTo(x(4f), y(9f))
        lineTo(x(4f), y(41f))
        lineTo(x(9f), y(21f))
        lineTo(x(39.5f), y(21f))
        lineTo(x(39.5f), y(15f))
        cubicTo(x(39.5f), y(13.8954f), x(38.6046f), y(13f), x(37.5f), y(13f))
        lineTo(x(24f), y(13f))
        lineTo(x(19f), y(7f))
        lineTo(x(6f), y(7f))
        cubicTo(x(4.89543f), y(7f), x(4f), y(7.89543f), x(4f), y(9f))
        close()
    }
    val front = Path().apply {
        moveTo(x(40f), y(41f))
        lineTo(x(44f), y(21f))
        lineTo(x(8.8125f), y(21f))
        lineTo(x(4f), y(41f))
        close()
    }

    drawPath(back, color = color, style = stroke)
    drawPath(front, color = color, style = stroke)
}

internal fun DrawScope.drawDefaultFileIcon(color: Color) {
    drawFileOutline(color, viewBox = 24f, strokeWidth = 2f)
}

internal fun DrawScope.drawTextFileIcon(color: Color) {
    drawFileOutline(color, viewBox = 24f, strokeWidth = 2f)
    val stroke = Stroke(width = size.minDimension / 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun x(value: Float) = size.width * value / 24f
    fun y(value: Float) = size.height * value / 24f

    drawLine(color, Offset(x(8.5f), y(14f)), Offset(x(15.5f), y(14f)), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(x(8.5f), y(18f)), Offset(x(15.5f), y(18f)), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

internal fun DrawScope.drawImageFileIcon(color: Color) {
    drawFileOutline(color, viewBox = 48f, strokeWidth = 4f)
    val stroke = Stroke(width = size.minDimension / 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun x(value: Float) = size.width * value / 48f
    fun y(value: Float) = size.height * value / 48f

    drawCircle(
        color = color,
        radius = size.minDimension * 4f / 48f,
        center = Offset(x(18f), y(17f)),
        style = stroke,
    )
    val image = Path().apply {
        moveTo(x(15f), y(28f))
        lineTo(x(15f), y(37f))
        lineTo(x(33f), y(37f))
        lineTo(x(33f), y(21f))
        lineTo(x(23.4894f), y(31.5f))
        lineTo(x(15f), y(28f))
        close()
    }
    drawPath(image, color = color, style = stroke)
}

internal fun DrawScope.drawFileOutline(
    color: Color,
    viewBox: Float,
    strokeWidth: Float,
) {
    val stroke = Stroke(width = size.minDimension * strokeWidth / viewBox, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun x(value: Float) = size.width * value / viewBox
    fun y(value: Float) = size.height * value / viewBox

    val outline = if (viewBox == 24f) {
        Path().apply {
            moveTo(x(5f), y(22f))
            lineTo(x(19f), y(22f))
            cubicTo(x(19.5523f), y(22f), x(20f), y(21.5523f), x(20f), y(21f))
            lineTo(x(20f), y(7f))
            lineTo(x(15f), y(7f))
            lineTo(x(15f), y(2f))
            lineTo(x(5f), y(2f))
            cubicTo(x(4.44771f), y(2f), x(4f), y(2.44771f), x(4f), y(3f))
            lineTo(x(4f), y(21f))
            cubicTo(x(4f), y(21.5523f), x(4.44771f), y(22f), x(5f), y(22f))
            close()
        }
    } else {
        Path().apply {
            moveTo(x(10f), y(44f))
            lineTo(x(38f), y(44f))
            cubicTo(x(39.1046f), y(44f), x(40f), y(43.1046f), x(40f), y(42f))
            lineTo(x(40f), y(14f))
            lineTo(x(30f), y(14f))
            lineTo(x(30f), y(4f))
            lineTo(x(10f), y(4f))
            cubicTo(x(8.89543f), y(4f), x(8f), y(4.89543f), x(8f), y(6f))
            lineTo(x(8f), y(42f))
            cubicTo(x(8f), y(43.1046f), x(8.89543f), y(44f), x(10f), y(44f))
            close()
        }
    }
    val fold = if (viewBox == 24f) {
        Path().apply {
            moveTo(x(15f), y(2f))
            lineTo(x(20f), y(7f))
        }
    } else {
        Path().apply {
            moveTo(x(30f), y(4f))
            lineTo(x(40f), y(14f))
        }
    }

    drawPath(outline, color = color, style = stroke)
    drawPath(fold, color = color, style = stroke)
}
