package io.github.portalappinspector.android

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Base64
import android.text.TextUtils
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.portalappinspector.PortalHealth
import io.github.portalappinspector.PortalManifest
import io.github.portalappinspector.PortalPlugin
import io.github.portalappinspector.PortalPluginManifest
import io.github.portalappinspector.PortalPluginRequest
import io.github.portalappinspector.PortalPluginResponse
import io.github.portalappinspector.PortalProtocol
import io.github.portalappinspector.PortalRpcBatchRequest
import io.github.portalappinspector.PortalRpcBatchResponse
import io.github.portalappinspector.portalPluginErrorResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import top.canyie.pine.Pine
import top.canyie.pine.PineConfig
import top.canyie.pine.callback.MethodHook
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.io.ByteArrayOutputStream
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.hypot

object PortalAppInspector {
    private const val FirstPort = 4896
    private const val RequestTimeoutMillis = 5_000L
    private const val NotificationId = 4896
    private const val NotificationChannelId = "portal_app_inspector"
    private const val PortalUrlBase = "https://omkar-tenkale.github.io/portal-app-inspector/connect"
//    private const val PortalUrlBase = "http://10.0.2.2:8081/connect"
    private const val LogTag = "PortalAppInspector"
    private const val OverlayTag = "portal_app_inspector_overlay"
    private const val PortalButtonTag = "portal_app_inspector_button"
    private const val PortalDrawerTag = "portal_app_inspector_drawer"
    private const val PortalDismissLayerTag = "portal_app_inspector_dismiss_layer"
    private const val PortalPermissionPromptTag = "portal_app_inspector_permission_prompt"
    private const val NotificationPermissionRequestCode = 4896
    private const val NotificationPermissionPollMillis = 500L

    private val started = AtomicBoolean(false)
    private val runtimeStarted = AtomicBoolean(false)
    private val activityOverlayHookInstalled = AtomicBoolean(false)
    private val notificationPermissionPolling = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var server: Any? = null
    private var state: PortalRuntimeState? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        PortalAndroidContext.install(appContext)
        installActivityOverlayHook(appContext)
        startRuntimeIfReady(appContext)
    }

    private fun startRuntimeIfReady(context: Context) {
        val appContext = context.applicationContext
        if (!hasNotificationPermission(appContext)) return
        if (!runtimeStarted.compareAndSet(false, true)) return

        startForegroundService(appContext)
        scope.launch {
            val plugins = loadPlugins()
            val port = findOpenPort()
            val host = findLanAddress() ?: "127.0.0.1"
            val nextState = PortalRuntimeState(
                context = appContext,
                sourceName = appContext.packageName,
                sourcePackageName = appContext.packageName,
                appName = appContext.portalAppName(),
                appIconPngBase64 = appContext.portalAppIconPngBase64(),
                host = host,
                port = port,
                plugins = plugins.associateBy { it.id },
            )
            state = nextState
            acquireRuntimeLocks(appContext)
            server = startServer(nextState)
            Log.i(LogTag, "Portal URL: ${nextState.portalUrl}")
            showNotification(nextState)
        }
    }

    private fun installActivityOverlayHook(context: Context) {
        if (!activityOverlayHookInstalled.compareAndSet(false, true)) return

        val applicationInfo = context.applicationInfo
        PineConfig.debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        runCatching {
            Pine.hook(
                Activity::class.java.getDeclaredMethod("onPostResume"),
                object : MethodHook() {
                    override fun afterCall(callFrame: Pine.CallFrame) {
                        val activity = callFrame.thisObject as? Activity ?: return
                        installWelcomeOverlay(activity)
                    }
                },
            )
        }.onFailure { error ->
            Log.w(LogTag, "Unable to install activity overlay hook", error)
        }
    }

    private fun installWelcomeOverlay(activity: Activity) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            addWelcomeOverlay(activity)
        } else {
            activity.runOnUiThread { addWelcomeOverlay(activity) }
        }
    }

    private fun addWelcomeOverlay(activity: Activity) {
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
            if (!hasNotificationPermission(activity)) {
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
                    } else if (!hasNotificationPermission(parent.context)) {
                        view.performClick()
                        requestNotificationPermission(parent.context)
                        pollNotificationPermission(parent)
                    } else {
                        view.performClick()
                        parent.findTaggedChild(PortalPermissionPromptTag)?.let { parent.removeView(it) }
                        startRuntimeIfReady(parent.context)
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

    private fun requestNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasNotificationPermission(context)) return

        val activity = context as? Activity ?: return
        runCatching {
            activity.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NotificationPermissionRequestCode,
            )
        }.onFailure { error ->
            Log.w(LogTag, "Unable to request notification permission", error)
        }
    }

    private fun pollNotificationPermission(parent: FrameLayout) {
        if (!notificationPermissionPolling.compareAndSet(false, true)) return

        val appContext = parent.context.applicationContext
        val poll = object : Runnable {
            override fun run() {
                if (hasNotificationPermission(appContext)) {
                    notificationPermissionPolling.set(false)
                    parent.findTaggedChild(PortalPermissionPromptTag)?.let { parent.removeView(it) }
                    startRuntimeIfReady(appContext)
                    return
                }
                mainHandler.postDelayed(this, NotificationPermissionPollMillis)
            }
        }
        mainHandler.postDelayed(poll, NotificationPermissionPollMillis)
    }

    private fun showPortalDrawer(parent: FrameLayout, button: PortalFloatingButton) {
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
            desktopPortalUrl = state?.portalUrl,
            localPortalUrl = state?.localPortalUrl,
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
                        (view as? PortalDrawerView)?.springOpenSurface()
                    }
                    dragging || consumeEvents
                }

                MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    if (dragging) {
                        (view as? PortalDrawerView)?.springOpenSurface()
                    }
                    dragging || consumeEvents
                }
                else -> consumeEvents
            }
        }
    }

    private fun hidePortalDrawer(parent: FrameLayout, drawer: View) {
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
        (drawer as? PortalDrawerView)?.springCloseSurface(onEnd = endAction)
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

    private fun setExpandedSurfaceDismissProgress(drawer: PortalDrawerView, progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        drawer.alpha = 1f - clamped
        val scale = 1f - ((1f - ExpandedSurfaceHiddenScale) * clamped)
        drawer.scaleX = scale
        drawer.scaleY = scale
        drawer.translationY = drawer.context.dp(18) * clamped
    }

    private fun PortalDrawerView.springOpenSurface() {
        springMotion.springAlphaTo(1f)
        springMotion.springScaleTo(1f)
        springMotion.springTranslationYTo(0f)
    }

    private fun PortalDrawerView.springCloseSurface(onEnd: () -> Unit) {
        springMotion.springAlphaTo(0f)
        springMotion.springScaleTo(ExpandedSurfaceHiddenScale)
        springMotion.springTranslationYTo(context.dp(18).toFloat(), onEnd = onEnd)
    }

    private fun ViewGroup.findTaggedChild(tag: Any): View? {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.tag == tag) return child
        }
        return null
    }

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun LinearLayout.LayoutParams.withTopMargin(value: Int): LinearLayout.LayoutParams =
        apply { topMargin = value }

    private fun LinearLayout.LayoutParams.withLeftMargin(value: Int): LinearLayout.LayoutParams =
        apply { leftMargin = value }

    private fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun FrameLayout.applyInitialSystemBarPadding(source: View) {
        val insets = rootWindowInsets ?: source.rootWindowInsets ?: return
        val systemBars = insets.systemBarInsets()
        setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        clipToPadding = false
    }

    @Suppress("DEPRECATION")
    private fun WindowInsets.systemBarInsets(): SystemBarInsets =
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

    private fun Float.coerceXInParent(parent: ViewGroup, childWidth: Int, margin: Int = 0): Float {
        val min = (parent.paddingLeft + margin).toFloat()
        val max = (parent.width - parent.paddingRight - childWidth - margin).toFloat()
        return if (max <= min) min else coerceIn(min, max)
    }

    private fun Float.coerceYInParent(parent: ViewGroup, childHeight: Int, margin: Int = 0): Float {
        val topInset = (parent as? FrameLayout)?.topSystemInset() ?: parent.paddingTop
        val bottomInset = (parent as? FrameLayout)?.bottomSystemInset() ?: parent.paddingBottom
        val min = (topInset + margin).toFloat()
        val max = (parent.height - bottomInset - childHeight - margin).toFloat()
        return if (max <= min) min else coerceIn(min, max)
    }

    private fun FrameLayout.topSystemInset(): Int =
        paddingTop.takeIf { it > 0 }
            ?: rootWindowInsets?.systemBarInsets()?.top?.takeIf { it > 0 }
            ?: context.statusBarHeight()

    private fun FrameLayout.bottomSystemInset(): Int =
        paddingBottom.takeIf { it > 0 }
            ?: rootWindowInsets?.systemBarInsets()?.bottom?.takeIf { it > 0 }
            ?: 0

    private fun Context.statusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private data class SystemBarInsets(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private const val ExpandedSurfaceHiddenScale = 0.96f
    private val ExpandedBackdropTintColor = Color.argb(68, 15, 23, 42)

    private class SpringyViewMotion(private val view: View) : Choreographer.FrameCallback {
        var onUpdate: (() -> Unit)? = null

        private val choreographer = Choreographer.getInstance()
        private val axes = mutableMapOf<SpringProperty, SpringAxis>()
        private var running = false
        private var lastFrameNanos = 0L
        private var endAction: (() -> Unit)? = null

        fun springPositionTo(
            targetX: Float,
            targetY: Float,
            velocityX: Float = 0f,
            velocityY: Float = 0f,
            onEnd: (() -> Unit)? = null,
        ) {
            putAxis(SpringProperty.X, targetX, velocityX, PositionSpringConfig)
            putAxis(SpringProperty.Y, targetY, velocityY, PositionSpringConfig)
            start(onEnd)
        }

        fun springTranslationYTo(
            target: Float,
            velocity: Float = 0f,
            onEnd: (() -> Unit)? = null,
        ) {
            putAxis(SpringProperty.TranslationY, target, velocity, DrawerSpringConfig)
            start(onEnd)
        }

        fun springAlphaTo(
            target: Float,
            velocity: Float = 0f,
            onEnd: (() -> Unit)? = null,
        ) {
            putAxis(SpringProperty.Alpha, target, velocity, AlphaSpringConfig)
            start(onEnd)
        }

        fun springScaleTo(target: Float) {
            putAxis(SpringProperty.Scale, target, 0f, ScaleSpringConfig)
            start()
        }

        fun cancel() {
            axes.clear()
            endAction = null
            if (running) {
                choreographer.removeFrameCallback(this)
            }
            running = false
            lastFrameNanos = 0L
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrameNanos == 0L) {
                1f / 60f
            } else {
                ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.032f)
            }
            lastFrameNanos = frameTimeNanos

            val iterator = axes.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val axis = entry.value
                val displacement = axis.value - axis.target
                val acceleration = -axis.config.stiffness * displacement - axis.config.damping * axis.velocity
                axis.velocity += acceleration * dt
                axis.value += axis.velocity * dt

                if (abs(axis.target - axis.value) < axis.config.restDistance && abs(axis.velocity) < axis.config.restVelocity) {
                    axis.value = axis.target
                    axis.velocity = 0f
                    iterator.remove()
                }
                entry.key.write(view, axis.value)
            }

            onUpdate?.invoke()
            if (axes.isEmpty()) {
                running = false
                lastFrameNanos = 0L
                endAction?.invoke()
                endAction = null
            } else {
                choreographer.postFrameCallback(this)
            }
        }

        private fun putAxis(
            property: SpringProperty,
            target: Float,
            velocity: Float,
            config: SpringConfig,
        ) {
            axes[property] = SpringAxis(
                value = property.read(view),
                target = target,
                velocity = velocity,
                config = config,
            )
        }

        private fun start(onEnd: (() -> Unit)? = null) {
            if (onEnd != null) {
                endAction = onEnd
            }
            if (!running) {
                running = true
                lastFrameNanos = 0L
                choreographer.postFrameCallback(this)
            }
        }
    }

    private enum class SpringProperty {
        X,
        Y,
        TranslationY,
        Alpha,
        Scale;

        fun read(view: View): Float =
            when (this) {
                X -> view.x
                Y -> view.y
                TranslationY -> view.translationY
                Alpha -> view.alpha
                Scale -> view.scaleX
            }

        fun write(view: View, value: Float) {
            when (this) {
                X -> view.x = value
                Y -> view.y = value
                TranslationY -> view.translationY = value
                Alpha -> view.alpha = value.coerceIn(0f, 1f)
                Scale -> {
                    view.scaleX = value
                    view.scaleY = value
                }
            }
        }
    }

    private data class SpringAxis(
        var value: Float,
        var target: Float,
        var velocity: Float,
        val config: SpringConfig,
    )

    private data class SpringConfig(
        val stiffness: Float,
        val damping: Float,
        val restDistance: Float,
        val restVelocity: Float,
    )

    private val PositionSpringConfig = SpringConfig(
        stiffness = 620f,
        damping = 34f,
        restDistance = 0.5f,
        restVelocity = 8f,
    )
    private val DrawerSpringConfig = SpringConfig(
        stiffness = 520f,
        damping = 35f,
        restDistance = 0.75f,
        restVelocity = 10f,
    )
    private val ScaleSpringConfig = SpringConfig(
        stiffness = 760f,
        damping = 32f,
        restDistance = 0.002f,
        restVelocity = 0.02f,
    )
    private val AlphaSpringConfig = SpringConfig(
        stiffness = 680f,
        damping = 36f,
        restDistance = 0.004f,
        restVelocity = 0.04f,
    )

    private class PortalDrawerView(
        context: Context,
        desktopPortalUrl: String?,
        localPortalUrl: String?,
        onSwipeBack: () -> Unit,
    ) : FrameLayout(context) {
        val springMotion = SpringyViewMotion(this)
        private var webView: WebView? = null

        init {
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(18).toFloat()
                setColor(Color.rgb(248, 250, 252))
            }
            clipToOutline = true

            addView(
                createEntryPanel(
                    context = context,
                    desktopPortalUrl = desktopPortalUrl,
                    localPortalUrl = localPortalUrl,
                    onOpenHere = { url -> showWebView(url, onSwipeBack) },
                ),
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                ),
            )
        }

        fun destroyWebView() {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webView = null
        }

        override fun onDetachedFromWindow() {
            destroyWebView()
            super.onDetachedFromWindow()
        }

        private fun showWebView(portalUrl: String, onSwipeBack: () -> Unit) {
            removeAllViews()
            addView(
                WebView(context).apply {
                    webView = this
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    attachWebViewSwipeBack(onSwipeBack)
                    loadUrl(portalUrl)
                },
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                ),
            )
        }

        private fun createEntryPanel(
            context: Context,
            desktopPortalUrl: String?,
            localPortalUrl: String?,
            onOpenHere: (String) -> Unit,
        ): View {
            val outer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(context.dp(22), context.dp(34), context.dp(22), context.dp(28))
            }

            outer.addView(
                PortalMarkView(context),
                LinearLayout.LayoutParams(
                    context.dp(44),
                    context.dp(44),
                ),
            )

            outer.addView(
                TextView(context).apply {
                    text = "Portal"
                    setTextColor(Color.rgb(15, 23, 42))
                    textSize = 24f
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).withTopMargin(context.dp(12)),
            )

            outer.addView(
                TextView(context).apply {
                    text = "Inspect this app from your browser or continue here."
                    setTextColor(Color.rgb(71, 85, 105))
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(0, context.dp(8), 0, 0)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )

            outer.addView(
                createUrlPill(context, desktopPortalUrl ?: "Portal is starting..."),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    context.dp(44),
                ).withTopMargin(context.dp(24)),
            )

            outer.addView(
                createActionRow(context, desktopPortalUrl),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).withTopMargin(context.dp(14)),
            )

            outer.addView(
                createDivider(context),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).withTopMargin(context.dp(30)),
            )

            outer.addView(
                createOpenHereSection(context, localPortalUrl, onOpenHere),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).withTopMargin(context.dp(26)),
            )

            return outer
        }

        private fun createUrlPill(context: Context, textValue: String): View =
            TextView(context).apply {
                text = textValue
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.MIDDLE
                setTextColor(Color.rgb(30, 41, 59))
                textSize = 13f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(14), 0, context.dp(14), 0)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = context.dp(22).toFloat()
                    setColor(Color.WHITE)
                    setStroke(context.dp(1), Color.rgb(203, 213, 225))
                }
            }

        private fun createActionRow(context: Context, desktopPortalUrl: String?): View =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER

                addView(
                    createThinButton(context, "Desktop URL") {
                        desktopPortalUrl?.let { copyToClipboard(context, it) }
                    },
                    LinearLayout.LayoutParams(0, context.dp(38), 1f),
                )
                addView(
                    createThinButton(context, "Copy URL") {
                        desktopPortalUrl?.let { copyToClipboard(context, it) }
                    },
                    LinearLayout.LayoutParams(0, context.dp(38), 1f).withLeftMargin(context.dp(10)),
                )
            }

        private fun createDivider(context: Context): View =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addView(createDividerLine(context), LinearLayout.LayoutParams(0, context.dp(1), 1f))
                addView(
                    TextView(context).apply {
                        text = "or"
                        setTextColor(Color.rgb(100, 116, 139))
                        textSize = 12f
                        gravity = Gravity.CENTER
                    },
                    LinearLayout.LayoutParams(context.dp(42), LinearLayout.LayoutParams.WRAP_CONTENT),
                )
                addView(createDividerLine(context), LinearLayout.LayoutParams(0, context.dp(1), 1f))
            }

        private fun createDividerLine(context: Context): View =
            View(context).apply {
                setBackgroundColor(Color.rgb(226, 232, 240))
            }

        private fun createOpenHereSection(
            context: Context,
            localPortalUrl: String?,
            onOpenHere: (String) -> Unit,
        ): View =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL

                addView(
                    TextView(context).apply {
                        text = "Open here"
                        setTextColor(Color.rgb(15, 23, 42))
                        textSize = 18f
                        gravity = Gravity.CENTER
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    TextView(context).apply {
                        text = "Launch the portal inside this overlay."
                        setTextColor(Color.rgb(71, 85, 105))
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setPadding(0, context.dp(6), 0, 0)
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    createPrimaryThinButton(context, "Open portal") {
                        localPortalUrl?.let(onOpenHere)
                    }.apply {
                        isEnabled = localPortalUrl != null
                        alpha = if (localPortalUrl == null) 0.55f else 1f
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        context.dp(40),
                    ).withTopMargin(context.dp(16)),
                )
            }

        private fun createThinButton(context: Context, label: String, onClick: () -> Unit): TextView =
            TextView(context).apply {
                text = label
                setTextColor(Color.rgb(30, 41, 59))
                textSize = 13f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = context.dp(10).toFloat()
                    setColor(Color.WHITE)
                    setStroke(context.dp(1), Color.rgb(203, 213, 225))
                }
                setOnClickListener { onClick() }
            }

        private fun createPrimaryThinButton(context: Context, label: String, onClick: () -> Unit): TextView =
            TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 13f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = context.dp(10).toFloat()
                    setColor(Color.rgb(31, 41, 55))
                }
                setOnClickListener { onClick() }
            }

        private fun copyToClipboard(context: Context, value: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Portal URL", value))
        }

        private fun View.attachWebViewSwipeBack(onSwipeBack: () -> Unit) {
            val swipeThreshold = context.dp(80)
            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            var downRawX = 0f
            var downRawY = 0f
            var dragging = false
            var velocityTracker: VelocityTracker? = null

            setOnTouchListener { view, event ->
                val drawer = view.parent as? PortalDrawerView
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        drawer?.springMotion?.cancel()
                        downRawX = event.rawX
                        downRawY = event.rawY
                        dragging = false
                        velocityTracker = VelocityTracker.obtain().apply {
                            addMovement(event)
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        velocityTracker?.addMovement(event)
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!dragging && dx > touchSlop && abs(dx) > abs(dy)) {
                            dragging = true
                        }
                        if (dragging) {
                            drawer?.let {
                                setExpandedSurfaceDismissProgress(
                                    drawer = it,
                                    progress = dx.coerceAtLeast(0f) / it.width.coerceAtLeast(1),
                                )
                            }
                            return@setOnTouchListener true
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
                            (dragging && ((1f - (drawer?.alpha ?: 1f)) * (drawer?.width ?: 0) > swipeThreshold || xVelocity > context.dp(700))) ||
                            (dx > swipeThreshold && abs(dx) > abs(dy))
                        ) {
                            onSwipeBack()
                            return@setOnTouchListener true
                        } else if (dragging) {
                            drawer?.springOpenSurface()
                            return@setOnTouchListener true
                        }
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        velocityTracker?.recycle()
                        velocityTracker = null
                        if (dragging) {
                            drawer?.springOpenSurface()
                            return@setOnTouchListener true
                        }
                    }
                }
                false
            }
        }
    }

    private class PortalFloatingButton(context: Context) : View(context) {
        val springMotion = SpringyViewMotion(this)
        var minimizedX = 0f
        var minimizedY = 0f
        var isExpanded = false
        private val mark = PortalMarkPainter()

        init {
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(20, 24, 31))
                setStroke(context.dp(1), Color.argb(72, 255, 255, 255))
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            mark.draw(canvas, width, height)
        }
    }

    private class PortalMarkView(context: Context) : View(context) {
        private val mark = PortalMarkPainter()

        init {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(20, 24, 31))
                setStroke(context.dp(1), Color.argb(72, 255, 255, 255))
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            mark.draw(canvas, width, height)
        }
    }

    private class PortalMarkPainter {
        private val leftRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val rightRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 192, 30)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val ringBounds = RectF()

        fun draw(canvas: Canvas, width: Int, height: Int) {
            val size = width.coerceAtMost(height).toFloat()
            val stroke = size * 0.08f
            leftRingPaint.strokeWidth = stroke
            rightRingPaint.strokeWidth = stroke

            canvas.save()
            canvas.rotate(15f, width * 0.43f, height * 0.5f)
            ringBounds.set(width * 0.25f, height * 0.2f, width * 0.63f, height * 0.8f)
            canvas.drawOval(ringBounds, leftRingPaint)
            canvas.restore()

            canvas.save()
            canvas.rotate(30f, width * 0.59f, height * 0.5f)
            ringBounds.set(width * 0.43f, height * 0.27f, width * 0.75f, height * 0.73f)
            canvas.drawOval(ringBounds, rightRingPaint)
            canvas.restore()
        }
    }

    fun startForegroundService(context: Context) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context.applicationContext, PortalAppInspectorService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(intent)
            } else {
                context.applicationContext.startService(intent)
            }
        }.onFailure { error ->
            Log.w(LogTag, "Unable to start foreground service", error)
        }
    }

    internal fun promoteToForeground(service: Service) {
        ensureNotificationChannel(service)
        val runtime = state
        val notification = if (runtime == null) {
            buildNotification(
                context = service,
                contentText = "Starting source server",
                portalUrl = null,
            )
        } else {
            buildNotification(
                context = service,
                contentText = "${runtime.host}:${runtime.port}",
                portalUrl = runtime.portalUrl,
            )
        }
        service.startForeground(NotificationId, notification)
    }

    private fun startServer(runtime: PortalRuntimeState) =
        embeddedServer(CIO, host = "0.0.0.0", port = runtime.port) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowHeader(HttpHeaders.ContentType)
            }
            routing {
                get("/portal/health") {
                    call.respond(
                        PortalHealth(
                            ok = true,
                            sourceName = runtime.sourceName,
                            sourcePackageName = runtime.sourcePackageName,
                            appName = runtime.appName,
                            protocolVersion = PortalProtocol.Version,
                        )
                    )
                }
                get("/portal/manifest") {
                    call.respond(
                        PortalManifest(
                            protocolVersion = PortalProtocol.Version,
                            sourceName = runtime.sourceName,
                            sourcePackageName = runtime.sourcePackageName,
                            appName = runtime.appName,
                            appIconPngBase64 = runtime.appIconPngBase64,
                            plugins = runtime.plugins.values
                                .sortedBy { it.id }
                                .map { plugin ->
                                    PortalPluginManifest(
                                        id = plugin.id,
                                        name = plugin.name,
                                        version = plugin.version,
                                    )
                                },
                        )
                    )
                }
                post("/portal/rpc") {
                    val batch = runCatching { call.receive<PortalRpcBatchRequest>() }.getOrElse {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                    val responses = batch.requests.map { request ->
                        async { runtime.dispatch(request) }
                    }.awaitAll()
                    call.respond(PortalRpcBatchResponse(responses))
                }
            }
        }.start(wait = false)

    @Suppress("DEPRECATION")
    private fun acquireRuntimeLocks(context: Context) {
        if (wakeLock?.isHeld != true) {
            wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$LogTag:SourceServer")
                .apply {
                    setReferenceCounted(false)
                    runCatching { acquire() }
                        .onFailure { error -> Log.w(LogTag, "Unable to acquire wake lock", error) }
                }
        }

        if (wifiLock?.isHeld != true) {
            wifiLock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "$LogTag:SourceServer")
                .apply {
                    setReferenceCounted(false)
                    runCatching { acquire() }
                        .onFailure { error -> Log.w(LogTag, "Unable to acquire Wi-Fi lock", error) }
                }
        }
    }

    private suspend fun PortalRuntimeState.dispatch(request: PortalPluginRequest): PortalPluginResponse =
        runCatching {
            val plugin = plugins[request.pluginId]
                ?: return portalPluginErrorResponse(
                    request = request,
                    code = "plugin_not_found",
                    message = "Plugin ${request.pluginId} is not installed in this source.",
                )

            withTimeout(RequestTimeoutMillis) {
                plugin.handle(request)
            }
        }.getOrElse { error ->
            val code = if (error is kotlinx.coroutines.TimeoutCancellationException) {
                "plugin_timeout"
            } else {
                "plugin_error"
            }
            portalPluginErrorResponse(
                request = request,
                code = code,
                message = error.message ?: error::class.simpleName.orEmpty(),
            )
        }

    private fun loadPlugins(): List<PortalPlugin> =
        ServiceLoader.load(PortalPlugin::class.java).toList()

    private fun findOpenPort(): Int {
        var port = FirstPort
        while (true) {
            if (isPortAvailable(port)) return port
            port += 1
        }
    }

    private fun isPortAvailable(port: Int): Boolean =
        runCatching {
            ServerSocket(port).use { socket ->
                socket.reuseAddress = true
            }
        }.isSuccess

    private fun findLanAddress(): String? =
        NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
            .flatMap { networkInterface ->
                networkInterface.inetAddresses.asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .map { address -> networkInterface.name to address }
            }
            .sortedWith(
                compareByDescending<Pair<String, Inet4Address>> { (name, _) -> name.startsWith("wlan") }
                    .thenByDescending { (name, _) -> name.startsWith("rmnet") }
                    .thenBy { (name, _) -> name },
            )
            .firstOrNull()
            ?.second
            ?.hostAddress

    private fun showNotification(runtime: PortalRuntimeState) {
        val manager = runtime.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureNotificationChannel(runtime.context)

        runCatching {
            manager.notify(
                NotificationId,
                buildNotification(
                    context = runtime.context,
                    contentText = "${runtime.host}:${runtime.port}",
                    portalUrl = runtime.portalUrl,
                ),
            )
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    NotificationChannelId,
                    "Portal App Inspector",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    private fun buildNotification(
        context: Context,
        contentText: String,
        portalUrl: String?,
    ): Notification {
        val copyIntent = Intent(context, PortalCopyUrlReceiver::class.java)
            .setAction(PortalCopyUrlReceiver.ActionCopyUrl)
            .putExtra(PortalCopyUrlReceiver.ExtraUrl, portalUrl)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val copyPendingIntent = PendingIntent.getBroadcast(context, 0, copyIntent, flags)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, NotificationChannelId)
        } else {
            Notification.Builder(context)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Portal App Inspector active")
            .setContentText(contentText)
            .setOngoing(true)
            .apply {
                if (portalUrl != null) {
                    addAction(
                        Notification.Action.Builder(
                            android.R.drawable.ic_menu_save,
                            "Copy URL",
                            copyPendingIntent,
                        ).build()
                    )
                }
            }
            .build()
    }

    private fun Context.portalAppName(): String =
        runCatching {
            val label = packageManager.getApplicationLabel(applicationInfo).toString()
            label.ifBlank { packageName }
        }.getOrDefault(packageName)

    private fun Context.portalAppIconPngBase64(): String? =
        runCatching {
            val icon = packageManager.getApplicationIcon(packageName)
            val density = resources.displayMetrics.density
            val size = (48f * density + 0.5f).toInt().coerceAtLeast(48)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawAppIconSquare(icon, size)
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            }
        }.getOrNull()

    private fun Canvas.drawAppIconSquare(icon: Drawable, size: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable) {
            icon.background?.let { drawable ->
                drawable.setBounds(0, 0, size, size)
                drawable.draw(this)
            }
            icon.foreground?.let { drawable ->
                drawable.setBounds(0, 0, size, size)
                drawable.draw(this)
            }
        } else {
            icon.setBounds(0, 0, size, size)
            icon.draw(this)
        }
    }

    private data class PortalRuntimeState(
        val context: Context,
        val sourceName: String,
        val sourcePackageName: String,
        val appName: String,
        val appIconPngBase64: String?,
        val host: String,
        val port: Int,
        val plugins: Map<String, PortalPlugin>,
    ) {
        val portalUrl: String =
            "$PortalUrlBase?host=$host&port=$port&mobileView=true"
        val localPortalUrl: String =
            "$PortalUrlBase?host=localhost&port=$port&mobileView=true"
    }
}
