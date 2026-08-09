package io.github.portalappinspector.android

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot

internal object PortalOverlayController {
    const val OverlayTag = "portal_app_inspector_overlay"
    const val PortalButtonTag = "portal_app_inspector_button"
    const val PortalDrawerTag = "portal_app_inspector_drawer"
    const val PortalDismissLayerTag = "portal_app_inspector_dismiss_layer"
    const val PortalPermissionPromptTag = "portal_app_inspector_permission_prompt"
    const val ExpandedSurfaceHiddenScale = 0.96f
    val ExpandedBackdropTintColor = Color.argb(68, 15, 23, 42)

    fun addWelcomeOverlay(activity: Activity) {
        val decorView = activity.window?.decorView as? ViewGroup ?: return
        if (decorView.findTaggedChild(OverlayTag) != null) return

        val overlay = FrameLayout(activity).apply {
            tag = OverlayTag
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val buttonSize = activity.dp(56)
        val margin = activity.dp(16)
        val button = PortalFloatingButton(activity).apply {
            tag = PortalButtonTag
            elevation = activity.dp(14).toFloat()
            alpha = 0f
            scaleX = 0.86f
            scaleY = 0.86f
        }

        overlay.addView(
            button,
            FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = margin
            },
        )

        decorView.addView(overlay)
        overlay.applyInitialSystemBarPadding(decorView)

        button.post {
            val topInset = overlay.topSystemInset()
            val bottomInset = overlay.bottomSystemInset()
            button.x = (overlay.width - overlay.paddingRight - button.width - margin)
                .toFloat()
                .coerceXInParent(overlay, button.width, margin)
            button.y = (
                topInset +
                    (overlay.height - topInset - bottomInset - button.height) / 2f
                ).coerceYInParent(overlay, button.height, margin)
            button.attachFloatingBehavior(overlay)
            if (!PortalAppInspector.hasNotificationPermission(activity)) {
                showNotificationPermissionPrompt(overlay, button)
            }
            button.springMotion.springScaleTo(1f)
            button.animate()
                .alpha(1f)
                .setDuration(120L)
                .start()
        }
    }

    private fun PortalFloatingButton.attachFloatingBehavior(parent: FrameLayout) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        springMotion.onUpdate = {
            parent.findTaggedChild(PortalPermissionPromptTag)?.let { prompt ->
                positionPermissionPrompt(parent, this, prompt)
            }
        }
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        var velocityTracker: VelocityTracker? = null

        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent.requestDisallowInterceptTouchEvent(true)
                    springMotion.cancel()
                    springMotion.springScaleTo(0.92f)
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = view.x
                    startY = view.y
                    dragging = false
                    velocityTracker = VelocityTracker.obtain().apply {
                        addMovement(event)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!isExpanded && !dragging && hypot(dx, dy) > touchSlop) {
                        dragging = true
                    }
                    if (dragging) {
                        view.x = (startX + dx).coerceXInParent(parent, view.width)
                        view.y = (startY + dy).coerceYInParent(parent, view.height)
                        parent.findTaggedChild(PortalPermissionPromptTag)?.let { prompt ->
                            positionPermissionPrompt(parent, view, prompt)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val xVelocity = velocityTracker?.xVelocity ?: 0f
                    val yVelocity = velocityTracker?.yVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null
                    springMotion.springScaleTo(1f)
                    if (dragging) {
                        snapButtonToEdge(parent, view, springMotion, xVelocity, yVelocity)
                    } else if (isExpanded) {
                        view.performClick()
                        parent.findTaggedChild(PortalDrawerTag)?.let { drawer ->
                            hidePortalDrawer(parent, drawer)
                        }
                    } else if (!PortalAppInspector.hasNotificationPermission(parent.context)) {
                        view.performClick()
                        PortalAppInspector.requestNotificationPermission(parent.context)
                        PortalAppInspector.pollNotificationPermission(parent)
                    } else {
                        view.performClick()
                        parent.findTaggedChild(PortalPermissionPromptTag)?.let { parent.removeView(it) }
                        PortalAppInspector.startRuntimeIfReady(parent.context)
                        showPortalDrawer(parent, this)
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    springMotion.springScaleTo(1f)
                    if (dragging && !isExpanded) snapButtonToEdge(parent, view, springMotion)
                    true
                }

                else -> false
            }
        }
    }

    private fun showNotificationPermissionPrompt(parent: FrameLayout, anchor: View) {
        if (parent.findTaggedChild(PortalPermissionPromptTag) != null) return

        val prompt = TextView(parent.context).apply {
            tag = PortalPermissionPromptTag
            text = "Allow notification permission to start"
            setTextColor(Color.rgb(15, 23, 42))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(context.dp(14), context.dp(10), context.dp(14), context.dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(14).toFloat()
                setColor(Color.WHITE)
                setStroke(context.dp(1), Color.rgb(226, 232, 240))
            }
            elevation = context.dp(9).toFloat()
            alpha = 0f
            scaleX = 0.96f
            scaleY = 0.96f
        }

        parent.addView(
            prompt,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        prompt.post {
            positionPermissionPrompt(parent, anchor, prompt)
            prompt.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(140L)
                .start()
        }
    }

    private fun positionPermissionPrompt(parent: FrameLayout, anchor: View, prompt: View) {
        val margin = parent.context.dp(12)
        val gap = parent.context.dp(10)
        val centeredX = anchor.x + anchor.width / 2f - prompt.width / 2f
        prompt.x = centeredX.coerceXInParent(parent, prompt.width, margin)

        val aboveY = anchor.y - prompt.height - gap
        prompt.y = if (aboveY >= parent.paddingTop + margin) {
            aboveY
        } else {
            anchor.y + anchor.height + gap
        }.coerceYInParent(parent, prompt.height, margin)
    }

    fun showPortalDrawer(parent: FrameLayout, button: PortalFloatingButton) {
        val existingDrawer = parent.findTaggedChild(PortalDrawerTag) as? PortalDrawerView
        if (existingDrawer?.visibility == View.VISIBLE) return

        if (!button.isExpanded) {
            button.minimizedX = button.x
            button.minimizedY = button.y
        }
        button.isExpanded = true

        val sideMargin = parent.context.dp(12)
        val surfaceGap = parent.context.dp(10)
        val surfaceHorizontalMargin = parent.context.dp(8)
        val surfaceBottomMargin = parent.context.dp(8)
        val topInset = parent.topSystemInset()
        val bottomInset = parent.bottomSystemInset()
        val expandedX = (parent.width - parent.paddingRight - button.width - sideMargin)
            .toFloat()
            .coerceXInParent(parent, button.width, sideMargin)
        val expandedY = topInset
            .toFloat()
            .coerceYInParent(parent, button.height)
        val surfaceTopMargin = (topInset + button.height + surfaceGap)
            .coerceAtMost(parent.height - bottomInset)
        val surfaceHeight = (parent.height - surfaceTopMargin - bottomInset - surfaceBottomMargin)
            .coerceAtLeast(0)

        lateinit var drawer: PortalDrawerView
        drawer = existingDrawer ?: PortalDrawerView(
            context = parent.context,
            desktopPortalUrl = PortalAppInspector.state?.portalUrl,
            localPortalUrl = PortalAppInspector.state?.localPortalUrl,
            onSwipeBack = { hidePortalDrawer(parent, drawer) },
        ).apply {
            tag = PortalDrawerTag
            attachSwipeBackBehavior(parent, consumeEvents = true)
        }
        drawer.apply {
            springMotion.cancel()
            visibility = View.VISIBLE
            alpha = 0f
            scaleX = ExpandedSurfaceHiddenScale
            scaleY = ExpandedSurfaceHiddenScale
            translationY = parent.context.dp(18).toFloat()
            pivotX = expandedX - surfaceHorizontalMargin + button.width / 2f
            pivotY = 0f
        }

        parent.findTaggedChild(PortalDismissLayerTag)?.let { parent.removeView(it) }
        val dismissLayer = View(parent.context).apply {
            tag = PortalDismissLayerTag
            alpha = 0f
            isClickable = true
            isFocusable = false
            setBackgroundColor(ExpandedBackdropTintColor)
            setOnClickListener { hidePortalDrawer(parent, drawer) }
        }
        parent.addView(
            dismissLayer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        val drawerLayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            surfaceHeight,
        ).apply {
            topMargin = surfaceTopMargin
            leftMargin = surfaceHorizontalMargin
            rightMargin = surfaceHorizontalMargin
        }
        if (existingDrawer == null) {
            parent.addView(drawer, drawerLayoutParams)
        } else {
            drawer.layoutParams = drawerLayoutParams
            drawer.bringToFront()
        }
        drawer.springMotion.springAlphaTo(1f)
        drawer.springMotion.springScaleTo(1f)
        drawer.springMotion.springTranslationYTo(0f)
        dismissLayer.animate()
            .alpha(1f)
            .setDuration(120L)
            .start()
        button.springMotion.springPositionTo(expandedX, expandedY)
        button.bringToFront()
    }

    private fun View.attachSwipeBackBehavior(parent: FrameLayout, consumeEvents: Boolean) {
        val swipeThreshold = context.dp(80)
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var dragging = false
        var velocityTracker: VelocityTracker? = null

        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    (view as? PortalDrawerView)?.springMotion?.cancel()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragging = false
                    velocityTracker = VelocityTracker.obtain().apply {
                        addMovement(event)
                    }
                    consumeEvents
                }

                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && dx > touchSlop && abs(dx) > abs(dy)) {
                        dragging = true
                    }
                    if (dragging) {
                        (view as? PortalDrawerView)?.let { drawer ->
                            setExpandedSurfaceDismissProgress(
                                drawer = drawer,
                                progress = dx.coerceAtLeast(0f) / parent.width.coerceAtLeast(1),
                            )
                        }
                        true
                    } else {
                        consumeEvents
                    }
                }

                MotionEvent.ACTION_UP -> {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val xVelocity = velocityTracker?.xVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (
                        (dragging && ((1f - view.alpha) * parent.width > swipeThreshold || xVelocity > context.dp(700))) ||
                        (dx > swipeThreshold && abs(dx) > abs(dy))
                    ) {
                        hidePortalDrawer(parent, parent.findTaggedChild(PortalDrawerTag) ?: view)
                    } else if (dragging) {
                        (view as? PortalDrawerView)?.let { springOpenSurface(it) }
                    }
                    dragging || consumeEvents
                }

                MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    if (dragging) {
                        (view as? PortalDrawerView)?.let { springOpenSurface(it) }
                    }
                    dragging || consumeEvents
                }
                else -> consumeEvents
            }
        }
    }

    fun hidePortalDrawer(parent: FrameLayout, drawer: View) {
        val button = parent.findTaggedChild(PortalButtonTag) as? PortalFloatingButton
        val dismissLayer = parent.findTaggedChild(PortalDismissLayerTag)
        val endAction = {
            if (drawer.parent === parent) {
                drawer.visibility = View.GONE
            }
            dismissLayer?.let { layer ->
                if (layer.parent === parent) parent.removeView(layer)
            }
            button?.isExpanded = false
        }
        button?.let {
            it.springMotion.springPositionTo(it.minimizedX, it.minimizedY)
        }
        dismissLayer?.animate()
            ?.alpha(0f)
            ?.setDuration(140L)
            ?.start()
        (drawer as? PortalDrawerView)?.let { springCloseSurface(it, onEnd = endAction) }
            ?: run {
                drawer.animate()
                    .alpha(0f)
                    .setDuration(180L)
                    .withEndAction(endAction)
                    .start()
            }
    }

    private fun snapButtonToEdge(
        parent: FrameLayout,
        button: View,
        motion: SpringyViewMotion,
        initialVelocityX: Float = 0f,
        initialVelocityY: Float = 0f,
    ) {
        val margin = parent.context.dp(12)
        val movingTowardRight = initialVelocityX > parent.context.dp(180)
        val movingTowardLeft = initialVelocityX < -parent.context.dp(180)
        val targetRight = (button.x + button.width / 2f >= parent.width / 2f && !movingTowardLeft) || movingTowardRight
        val targetX = if (!targetRight) {
            (parent.paddingLeft + margin).toFloat()
        } else {
            (parent.width - parent.paddingRight - button.width - margin).toFloat()
        }.coerceXInParent(parent, button.width, margin)
        val targetY = button.y.coerceYInParent(parent, button.height, margin)
        motion.springPositionTo(targetX, targetY, initialVelocityX, initialVelocityY)
    }

    fun setExpandedSurfaceDismissProgress(drawer: PortalDrawerView, progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        drawer.alpha = 1f - clamped
        val scale = 1f - ((1f - ExpandedSurfaceHiddenScale) * clamped)
        drawer.scaleX = scale
        drawer.scaleY = scale
        drawer.translationY = drawer.context.dp(18) * clamped
    }

    fun springOpenSurface(drawer: PortalDrawerView) {
        drawer.springMotion.springAlphaTo(1f)
        drawer.springMotion.springScaleTo(1f)
        drawer.springMotion.springTranslationYTo(0f)
    }

    fun springCloseSurface(drawer: PortalDrawerView, onEnd: () -> Unit) {
        drawer.springMotion.springAlphaTo(0f)
        drawer.springMotion.springScaleTo(ExpandedSurfaceHiddenScale)
        drawer.springMotion.springTranslationYTo(drawer.context.dp(18).toFloat(), onEnd = onEnd)
    }
}
