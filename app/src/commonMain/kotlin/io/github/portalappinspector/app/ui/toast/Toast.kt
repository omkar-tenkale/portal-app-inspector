package io.github.portalappinspector.app.ui.toast

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.shadow
import io.github.portalappinspector.app.ui.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal val LocalToastHost = staticCompositionLocalOf { ToastHostState() }

internal enum class ToastKind {
    Success,
    Info,
    Warning,
    Error,
}

internal class ToastHostState {
    internal val toasts = mutableStateListOf<PortalToast>()
    internal var nextId = 0L

    fun show(
        message: String,
        kind: ToastKind = ToastKind.Success,
        durationMillis: Long = 5_000L,
    ) {
        nextId += 1
        toasts.add(
            index = 0,
            element = PortalToast(
                id = nextId,
                message = message,
                kind = kind,
                durationMillis = durationMillis,
            ),
        )
    }

    internal fun remove(id: Long) {
        toasts.removeAll { it.id == id }
    }
}

internal class PortalToast(
    val id: Long,
    val message: String,
    val kind: ToastKind,
    val durationMillis: Long,
) {
    var visible by mutableStateOf(false)
}

@Composable
internal fun ToastHost(
    state: ToastHostState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    fun dismiss(toast: PortalToast) {
        if (!toast.visible) return
        toast.visible = false
        scope.launch {
            delay(220L)
            state.remove(toast.id)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.toasts.forEach { toast ->
            key(toast.id) {
                LaunchedEffect(toast.id) {
                    toast.visible = true
                    delay(toast.durationMillis)
                    dismiss(toast)
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = toast.visible,
                    enter = fadeIn(animationSpec = tween(180)) + slideInVertically(
                        animationSpec = tween(220),
                        initialOffsetY = { -it },
                    ),
                    exit = fadeOut(animationSpec = tween(160)) + slideOutVertically(
                        animationSpec = tween(180),
                        targetOffsetY = { -it },
                    ),
                ) {
                    ToastCard(
                        toast = toast,
                        onDismiss = { dismiss(toast) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ToastCard(
    toast: PortalToast,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .shadow(8.dp, shape)
            .background(PortalColors.card, shape)
            .border(1.dp, PortalColors.border, shape)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToastIcon(
            kind = toast.kind,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = toast.message,
            color = PortalColors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun ToastIcon(
    kind: ToastKind,
    modifier: Modifier = Modifier,
) {
    val color = when (kind) {
        ToastKind.Success -> PortalColors.success
        ToastKind.Info -> PortalColors.accent
        ToastKind.Warning -> PortalColors.warning
        ToastKind.Error -> PortalColors.error
    }
    Canvas(modifier = modifier) {
        val strokeWidth = 1.8.dp.toPx()
        drawCircle(color = color)
        when (kind) {
            ToastKind.Success -> {
                drawLine(
                    color = PortalColors.submitText,
                    start = Offset(size.width * 0.28f, size.height * 0.52f),
                    end = Offset(size.width * 0.44f, size.height * 0.68f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = PortalColors.submitText,
                    start = Offset(size.width * 0.44f, size.height * 0.68f),
                    end = Offset(size.width * 0.74f, size.height * 0.34f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            else -> drawCircle(
                color = PortalColors.submitText,
                radius = size.minDimension * 0.12f,
                center = center,
            )
        }
    }
}
