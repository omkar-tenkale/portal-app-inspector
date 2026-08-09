package io.github.portalappinspector.android

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout

internal fun ViewGroup.findTaggedChild(tag: Any): View? {
    for (index in 0 until childCount) {
        val child = getChildAt(index)
        if (child.tag == tag) return child
    }
    return null
}

internal fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density + 0.5f).toInt()

internal fun LinearLayout.LayoutParams.withTopMargin(value: Int): LinearLayout.LayoutParams =
    apply { topMargin = value }

internal fun LinearLayout.LayoutParams.withLeftMargin(value: Int): LinearLayout.LayoutParams =
    apply { leftMargin = value }

internal fun FrameLayout.applyInitialSystemBarPadding(source: View) {
    val insets = currentRootWindowInsets() ?: source.currentRootWindowInsets() ?: return
    val systemBars = insets.systemBarInsets()
    setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
    clipToPadding = false
}

internal fun View.currentRootWindowInsets(): WindowInsets? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) rootWindowInsets else null

@Suppress("DEPRECATION")
internal fun WindowInsets.systemBarInsets(): SystemBarInsets =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        getInsets(WindowInsets.Type.systemBars()).let { insets ->
            SystemBarInsets(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom,
            )
        }
    } else {
        SystemBarInsets(
            left = systemWindowInsetLeft,
            top = systemWindowInsetTop,
            right = systemWindowInsetRight,
            bottom = systemWindowInsetBottom,
        )
    }

internal fun Float.coerceXInParent(parent: ViewGroup, childWidth: Int, margin: Int = 0): Float {
    val min = (parent.paddingLeft + margin).toFloat()
    val max = (parent.width - parent.paddingRight - childWidth - margin).toFloat()
    return if (max <= min) min else coerceIn(min, max)
}

internal fun Float.coerceYInParent(parent: ViewGroup, childHeight: Int, margin: Int = 0): Float {
    val topInset = (parent as? FrameLayout)?.topSystemInset() ?: parent.paddingTop
    val bottomInset = (parent as? FrameLayout)?.bottomSystemInset() ?: parent.paddingBottom
    val min = (topInset + margin).toFloat()
    val max = (parent.height - bottomInset - childHeight - margin).toFloat()
    return if (max <= min) min else coerceIn(min, max)
}

internal fun FrameLayout.topSystemInset(): Int =
    paddingTop.takeIf { it > 0 }
        ?: currentRootWindowInsets()?.systemBarInsets()?.top?.takeIf { it > 0 }
        ?: context.statusBarHeight()

internal fun FrameLayout.bottomSystemInset(): Int =
    paddingBottom.takeIf { it > 0 }
        ?: currentRootWindowInsets()?.systemBarInsets()?.bottom?.takeIf { it > 0 }
        ?: 0

internal fun Context.statusBarHeight(): Int {
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
}

internal data class SystemBarInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
