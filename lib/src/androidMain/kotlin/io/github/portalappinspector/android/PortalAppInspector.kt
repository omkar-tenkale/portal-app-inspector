package io.github.portalappinspector.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
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
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.Base64
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicBoolean

object PortalAppInspector {
    private const val FirstPort = 4896
    private const val RequestTimeoutMillis = 5_000L
    private const val NotificationId = 4896
    private const val NotificationChannelId = "portal_app_inspector"
    private const val PortalUrlBase = "https://omkar-tenkale.github.io/portal-app-inspector/connect"
    private const val LogTag = "PortalAppInspector"

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var server: Any? = null
    private var state: PortalRuntimeState? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        PortalAndroidContext.install(appContext)

        scope.launch {
            val plugins = loadPlugins()
            val sessionToken = generateSessionToken()
            val port = findOpenPort()
            val host = findLanAddress() ?: "127.0.0.1"
            val nextState = PortalRuntimeState(
                context = appContext,
                sourceName = appContext.packageName,
                host = host,
                port = port,
                sessionToken = sessionToken,
                plugins = plugins.associateBy { it.id },
            )
            state = nextState
            acquireRuntimeLocks(appContext)
            server = startServer(nextState)
            Log.i(LogTag, "Portal URL: ${nextState.portalUrl}")
            showNotification(nextState)
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
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.ContentType)
            }
            routing {
                get("/portal/health") {
                    if (!call.isAuthorized(runtime)) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@get
                    }
                    call.respond(
                        PortalHealth(
                            ok = true,
                            sourceName = runtime.sourceName,
                            protocolVersion = PortalProtocol.Version,
                        )
                    )
                }
                get("/portal/manifest") {
                    if (!call.isAuthorized(runtime)) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@get
                    }
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
                    if (!call.isAuthorized(runtime)) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@post
                    }
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

    private fun io.ktor.server.application.ApplicationCall.isAuthorized(runtime: PortalRuntimeState): Boolean {
        val expected = "Bearer ${runtime.sessionToken}"
        return request.headers[HttpHeaders.Authorization] == expected
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

    private fun generateSessionToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        } else {
            android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        }
    }

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
        val sessionToken: String,
        val plugins: Map<String, PortalPlugin>,
    ) {
        val portalUrl: String =
            "$PortalUrlBase?host=$host&port=$port&sessionToken=$sessionToken"
    }
}
