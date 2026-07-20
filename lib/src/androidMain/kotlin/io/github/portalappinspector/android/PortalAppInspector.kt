package io.github.portalappinspector.android

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
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
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicBoolean

object PortalAppInspector {
    private const val FirstPort = 4896
    private const val RequestTimeoutMillis = 5_000L
    private const val NotificationId = 4896
    private const val NotificationChannelId = "portal_app_inspector"
    private const val PortalUrlBase = "https://omkar-tenkale.github.io/portal-app-inspector/connect"
    private const val LogTag = "PortalAppInspector"
    private const val OverlayTag = "portal_app_inspector_overlay"
    private const val PortalButtonTag = "portal_app_inspector_button"
    private const val PortalDrawerTag = "portal_app_inspector_drawer"

    private val started = AtomicBoolean(false)
    private val activityOverlayHookInstalled = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var server: Any? = null
    private var state: PortalRuntimeState? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        PortalAndroidContext.install(appContext)
        installActivityOverlayHook(appContext)

        scope.launch {
            val plugins = loadPlugins()
            val port = findOpenPort()
            val host = findLanAddress() ?: "127.0.0.1"
            val nextState = PortalRuntimeState(
                context = appContext,
                sourceName = appContext.packageName,
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
            elevation = activity.dp(8).toFloat()
        }

        overlay.addView(
            button,
            FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = margin
            },
        )

        decorView.addView(overlay)

        button.post {
            button.x = (overlay.width - button.width - margin).toFloat()
            button.y = ((overlay.height - button.height) / 2).toFloat()
            button.attachFloatingBehavior(overlay)
        }
    }

    private fun PortalFloatingButton.attachFloatingBehavior(parent: FrameLayout) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false

        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = view.x
                    startY = view.y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && dx * dx + dy * dy > touchSlop * touchSlop) {
                        dragging = true
                    }
                    if (dragging) {
                        view.x = (startX + dx).coerceToParent(parent.width, view.width)
                        view.y = (startY + dy).coerceToParent(parent.height, view.height)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        snapButtonToEdge(parent, view)
                    } else {
                        showPortalDrawer(parent)
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) snapButtonToEdge(parent, view)
                    true
                }

                else -> false
            }
        }
    }

    private fun showPortalDrawer(parent: FrameLayout) {
        if (parent.findTaggedChild(PortalDrawerTag) != null) return

        lateinit var drawer: PortalDrawerView
        drawer = PortalDrawerView(
            context = parent.context,
            portalUrl = state?.portalUrl,
            onSwipeBack = { hidePortalDrawer(parent, drawer) },
        ).apply {
            tag = PortalDrawerTag
            translationX = parent.width.toFloat()
            elevation = parent.context.dp(12).toFloat()
            attachSwipeBackBehavior(parent, consumeEvents = true)
        }

        parent.addView(
            drawer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        drawer.animate()
            .translationX(0f)
            .setDuration(220L)
            .start()
    }

    private fun View.attachSwipeBackBehavior(parent: FrameLayout, consumeEvents: Boolean) {
        val swipeThreshold = context.dp(80)
        var downRawX = 0f
        var downRawY = 0f

        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    consumeEvents
                }

                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (dx > swipeThreshold && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        hidePortalDrawer(parent, parent.findTaggedChild(PortalDrawerTag) ?: view)
                    }
                    consumeEvents
                }

                MotionEvent.ACTION_CANCEL -> consumeEvents
                else -> consumeEvents
            }
        }
    }

    private fun hidePortalDrawer(parent: FrameLayout, drawer: View) {
        drawer.animate()
            .translationX(parent.width.toFloat())
            .setDuration(200L)
            .withEndAction {
                (drawer as? PortalDrawerView)?.destroyWebView()
                parent.removeView(drawer)
            }
            .start()
    }

    private fun snapButtonToEdge(parent: FrameLayout, button: View) {
        val margin = parent.context.dp(12)
        val targetX = if (button.x + button.width / 2f < parent.width / 2f) {
            margin.toFloat()
        } else {
            (parent.width - button.width - margin).toFloat()
        }.coerceToParent(parent.width, button.width, margin)
        val targetY = button.y.coerceToParent(parent.height, button.height, margin)
        button.animate()
            .x(targetX)
            .y(targetY)
            .setDuration(180L)
            .start()
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

    private fun Float.coerceToParent(parentSize: Int, childSize: Int, margin: Int = 0): Float {
        val min = margin.toFloat()
        val max = (parentSize - childSize - margin).toFloat()
        return if (max <= min) min else coerceIn(min, max)
    }

    private class PortalDrawerView(
        context: Context,
        portalUrl: String?,
        onSwipeBack: () -> Unit,
    ) : LinearLayout(context) {
        private var webView: WebView? = null

        init {
            orientation = VERTICAL
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.rgb(248, 250, 252))

            addView(
                FrameLayout(context).apply {
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        setStroke(context.dp(1), Color.rgb(226, 232, 240))
                    }
                    addView(
                        TextView(context).apply {
                            text = "Welcome to Portal"
                            setTextColor(Color.rgb(15, 23, 42))
                            textSize = 22f
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(context.dp(24), 0, context.dp(24), 0)
                        },
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                },
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    context.dp(88),
                ),
            )

            addView(
                if (portalUrl == null) {
                    TextView(context).apply {
                        text = "Portal is starting..."
                        setTextColor(Color.rgb(71, 85, 105))
                        textSize = 16f
                        gravity = Gravity.CENTER
                    }
                } else {
                    WebView(context).apply {
                        webView = this
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        attachWebViewSwipeBack(onSwipeBack)
                        loadUrl(portalUrl)
                    }
                },
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    0,
                    1f,
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

        private fun View.attachWebViewSwipeBack(onSwipeBack: () -> Unit) {
            val swipeThreshold = context.dp(80)
            var downRawX = 0f
            var downRawY = 0f

            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                    }

                    MotionEvent.ACTION_UP -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (dx > swipeThreshold && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                            onSwipeBack()
                        }
                    }
                }
                false
            }
        }
    }

    private class PortalFloatingButton(context: Context) : View(context) {
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
                            protocolVersion = PortalProtocol.Version,
                        )
                    )
                }
                get("/portal/manifest") {
                    call.respond(
                        PortalManifest(
                            protocolVersion = PortalProtocol.Version,
                            sourceName = runtime.sourceName,
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

    private data class PortalRuntimeState(
        val context: Context,
        val sourceName: String,
        val host: String,
        val port: Int,
        val plugins: Map<String, PortalPlugin>,
    ) {
        val portalUrl: String =
            "$PortalUrlBase?host=$host&port=$port"
    }
}
