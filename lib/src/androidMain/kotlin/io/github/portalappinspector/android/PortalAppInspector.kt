package io.github.portalappinspector.android

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import io.github.portalappinspector.PortalHealth
import io.github.portalappinspector.PortalManifest
import io.github.portalappinspector.PortalPlugin
import io.github.portalappinspector.PortalPluginManifest
import io.github.portalappinspector.PortalPluginRequest
import io.github.portalappinspector.PortalPluginResponse
import io.github.portalappinspector.PortalProtocol
import io.github.portalappinspector.PortalRpcBatchRequest
import io.github.portalappinspector.PortalRpcBatchResponse
import io.github.portalappinspector.PortalStreamRequest
import io.github.portalappinspector.PortalStreamSink
import io.github.portalappinspector.PortalStreamingPlugin
import io.github.portalappinspector.portalPluginErrorResponse
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import top.canyie.pine.Pine
import top.canyie.pine.PineConfig
import top.canyie.pine.callback.MethodHook
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLEncoder
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicBoolean

object PortalAppInspector {
    private const val FirstPort = 4896
    private const val RequestTimeoutMillis = 5_000L
    private const val NotificationId = 4896
    private const val NotificationChannelId = "portal_app_inspector"
    private const val PortalUrlBase = "http://10.0.2.2:8080/connect"
    private const val LogTag = "PortalAppInspector"
    private const val NotificationPermissionRequestCode = 4896
    private const val NotificationPermissionPollMillis = 500L
    private const val JsonMimeType = "application/json"

    private val started = AtomicBoolean(false)
    private val runtimeStarted = AtomicBoolean(false)
    private val activityOverlayHookInstalled = AtomicBoolean(false)
    private val notificationPermissionPolling = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var server: Any? = null
    internal var state: PortalRuntimeState? = null
        private set
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        PortalAndroidContext.install(appContext)
        installActivityOverlayHook(appContext)
        startRuntimeIfReady(appContext)
    }

    internal fun startRuntimeIfReady(context: Context) {
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
                appId = appContext.packageName,
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
        PortalAndroidContext.setCurrentActivity(activity)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            PortalOverlayController.addWelcomeOverlay(activity)
        } else {
            activity.runOnUiThread { PortalOverlayController.addWelcomeOverlay(activity) }
        }
    }

    internal fun requestNotificationPermission(context: Context) {
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

    internal fun pollNotificationPermission(parent: android.widget.FrameLayout) {
        if (!notificationPermissionPolling.compareAndSet(false, true)) return

        val appContext = parent.context.applicationContext
        val poll = object : Runnable {
            override fun run() {
                if (hasNotificationPermission(appContext)) {
                    notificationPermissionPolling.set(false)
                    parent.findTaggedChild(PortalOverlayController.PortalPermissionPromptTag)?.let { parent.removeView(it) }
                    startRuntimeIfReady(appContext)
                    return
                }
                mainHandler.postDelayed(this, NotificationPermissionPollMillis)
            }
        }
        mainHandler.postDelayed(poll, NotificationPermissionPollMillis)
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

    private fun startServer(runtime: PortalRuntimeState): NanoWSD =
        object : NanoWSD("0.0.0.0", runtime.port) {
            override fun serveHttp(session: IHTTPSession): Response =
                when {
                    session.method == Method.OPTIONS -> emptyResponse(Response.Status.OK)
                    session.method == Method.GET && session.uri == "/portal/health" -> jsonResponse(
                        PortalHealth(
                            ok = true,
                            appId = runtime.appId,
                            appName = runtime.appName,
                            protocolVersion = PortalProtocol.Version,
                        )
                    )
                    session.method == Method.GET && session.uri == "/portal/manifest" -> jsonResponse(
                        PortalManifest(
                            protocolVersion = PortalProtocol.Version,
                            appId = runtime.appId,
                            appName = runtime.appName,
                            platform = "android",
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
                    session.method == Method.POST && session.uri == "/portal/rpc" -> rpcResponse(runtime, session)
                    else -> emptyResponse(Response.Status.NOT_FOUND)
                }

            override fun openWebSocket(handshake: IHTTPSession): WebSocket =
                runtime.openPluginWebSocket(handshake)
        }.apply {
            start(SOCKET_READ_TIMEOUT, false)
        }

    private fun PortalRuntimeState.openPluginWebSocket(handshake: NanoHTTPD.IHTTPSession): NanoWSD.WebSocket {
        val streamRoute = parsePluginStreamRoute(handshake.uri)
        val stream = streamRoute
            ?.let { route ->
                (plugins[route.pluginId] as? PortalStreamingPlugin)?.openStream(
                    PortalStreamRequest(
                        path = route.streamPath,
                        query = handshake.parms.orEmpty(),
                        headers = handshake.headers.orEmpty(),
                    ),
                )
            }

        return object : NanoWSD.WebSocket(handshake) {
            override fun onOpen() {
                if (stream == null) {
                    closeQuietly(NanoWSD.WebSocketFrame.CloseCode.PolicyViolation, "Unknown stream.")
                    return
                }
                val socket = this
                stream.onOpen(
                    object : PortalStreamSink {
                        override fun sendText(message: String) {
                            runCatching { socket.send(message) }
                        }

                        override fun sendBinary(bytes: ByteArray) {
                            runCatching { socket.send(bytes) }
                        }

                        override fun close() {
                            runCatching {
                                socket.close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "", false)
                            }
                        }
                    },
                )
            }

            override fun onClose(
                code: NanoWSD.WebSocketFrame.CloseCode?,
                reason: String?,
                initiatedByRemote: Boolean,
            ) {
                stream?.onClose()
            }

            override fun onMessage(message: NanoWSD.WebSocketFrame) {
                if (stream == null) return
                when (message.opCode) {
                    NanoWSD.WebSocketFrame.OpCode.Text -> stream.onText(message.textPayload)
                    NanoWSD.WebSocketFrame.OpCode.Binary -> stream.onBinary(message.binaryPayload)
                    else -> Unit
                }
            }

            override fun onPong(pong: NanoWSD.WebSocketFrame) = Unit

            override fun onException(exception: IOException) {
                stream?.onError(exception)
            }

            private fun closeQuietly(code: NanoWSD.WebSocketFrame.CloseCode, reason: String) {
                runCatching { close(code, reason, false) }
            }
        }
    }

    private fun parsePluginStreamRoute(uri: String): PortalPluginStreamRoute? {
        val prefix = "/portal/plugins/"
        if (!uri.startsWith(prefix)) return null
        val route = uri.removePrefix(prefix)
        val streamSeparator = "/streams/"
        val separatorIndex = route.indexOf(streamSeparator)
        if (separatorIndex <= 0) return null
        val pluginId = route.substring(0, separatorIndex).takeIf { it.isNotBlank() } ?: return null
        val streamPath = route.substring(separatorIndex + streamSeparator.length)
            .trim('/')
            .takeIf { it.isNotBlank() }
            ?: return null
        return PortalPluginStreamRoute(pluginId, streamPath)
    }

    private fun rpcResponse(runtime: PortalRuntimeState, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val batch = runCatching {
            val body = mutableMapOf<String, String>()
            session.parseBody(body)
            json.decodeFromString<PortalRpcBatchRequest>(body["postData"].orEmpty())
        }.getOrElse {
            return emptyResponse(NanoHTTPD.Response.Status.BAD_REQUEST)
        }

        val responses = runBlocking {
            batch.requests.map { request ->
                async { runtime.dispatch(request) }
            }.awaitAll()
        }
        return jsonResponse(PortalRpcBatchResponse(responses))
    }

    private inline fun <reified T> jsonResponse(body: T): NanoHTTPD.Response =
        newCorsResponse(
            status = NanoHTTPD.Response.Status.OK,
            mimeType = JsonMimeType,
            body = json.encodeToString(body),
        )

    private fun emptyResponse(status: NanoHTTPD.Response.Status): NanoHTTPD.Response =
        newCorsResponse(status = status, mimeType = NanoHTTPD.MIME_PLAINTEXT, body = "")

    private fun newCorsResponse(
        status: NanoHTTPD.Response.Status,
        mimeType: String,
        body: String,
    ): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(status, mimeType, body).apply {
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            addHeader("Access-Control-Allow-Headers", "Content-Type")
        }

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

    internal data class PortalRuntimeState(
        val context: Context,
        val appId: String,
        val appName: String,
        val appIconPngBase64: String?,
        val host: String,
        val port: Int,
        val plugins: Map<String, PortalPlugin>,
    ) {
        val portalUrl: String =
            "$PortalUrlBase?host=$host&port=$port&mobileView=true&isEmulator=false&appId=${appId.portalUrlEncoded()}&platform=android"
        val localPortalUrl: String =
            "$PortalUrlBase?host=localhost&port=$port&mobileView=true&isEmulator=false&appId=${appId.portalUrlEncoded()}&platform=android"
    }

    private data class PortalPluginStreamRoute(
        val pluginId: String,
        val streamPath: String,
    )

    private fun String.portalUrlEncoded(): String =
        URLEncoder.encode(this, "UTF-8")

    internal fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
