package io.github.portalappinspector.plugins.screenmirror

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PixelCopy
import io.github.portalappinspector.PortalPlugin
import io.github.portalappinspector.PortalPluginRequest
import io.github.portalappinspector.PortalPluginResponse
import io.github.portalappinspector.PortalPluginStream
import io.github.portalappinspector.PortalStreamRequest
import io.github.portalappinspector.PortalStreamSink
import io.github.portalappinspector.PortalStreamingPlugin
import io.github.portalappinspector.android.PortalAndroidContext
import io.github.portalappinspector.portalPluginErrorResponse
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class PortalScreenMirrorPlugin : PortalStreamingPlugin {
    override val id: String = "portal-screen-mirror"
    override val name: String = "Screen Mirror"
    override val version: String = "0.1.0"

    init {
        ScreenMirrorStore.installHooks()
    }

    override suspend fun handle(request: PortalPluginRequest): PortalPluginResponse {
        val operation = request.payload["type"]?.jsonPrimitive?.contentOrNull
            ?: return portalPluginErrorResponse(request, "missing_operation", "Screen Mirror request payload is missing type.")

        return when (operation) {
            "getFrame" -> {
                val afterFrameEpochMillis = request.payload["afterFrameEpochMillis"]
                    ?.jsonPrimitive
                    ?.longOrNull
                    ?: 0L
                val scale = request.payload["scale"]
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?: DefaultScale
                val maxFps = request.payload["maxFps"]
                    ?.jsonPrimitive
                    ?.intOrNull
                    ?: DefaultMaxFps
                ScreenMirrorStore.ensureActive(scale, maxFps)
                success(request, ScreenMirrorStore.framePayload(afterFrameEpochMillis))
            }
            "sendTouch" -> {
                val action = request.payload["action"]?.jsonPrimitive?.contentOrNull
                    ?: return portalPluginErrorResponse(request, "missing_action", "sendTouch requires action.")
                val xRatio = request.payload["x"]?.jsonPrimitive?.doubleOrNull
                    ?: return portalPluginErrorResponse(request, "missing_x", "sendTouch requires x.")
                val yRatio = request.payload["y"]?.jsonPrimitive?.doubleOrNull
                    ?: return portalPluginErrorResponse(request, "missing_y", "sendTouch requires y.")
                ScreenMirrorStore.sendTouch(action, xRatio, yRatio)
                success(
                    request,
                    buildJsonObject {
                        put("type", "sendTouchResult")
                    },
                )
            }
            else -> portalPluginErrorResponse(request, "unknown_operation", "Unknown screen mirror operation: $operation")
        }
    }

    override fun openStream(request: PortalStreamRequest): PortalPluginStream? =
        when (request.path) {
            "frames" -> ScreenMirrorStore.openFrameStream(request)
            "input" -> ScreenMirrorStore.openInputStream()
            "mirror" -> ScreenMirrorStore.openMirrorStream(request)
            else -> null
        }

    private fun success(request: PortalPluginRequest, payload: JsonObject): PortalPluginResponse =
        PortalPluginResponse(
            id = request.id,
            pluginId = request.pluginId,
            ok = true,
            payload = payload,
        )

    private companion object {
        const val DefaultScale = 0.5
        const val DefaultMaxFps = 2
    }
}

private object ScreenMirrorStore {
    private const val LogTag = "PortalScreenMirror"
    private const val InactiveAfterMillis = 2_000L
    private const val MinScale = 0.1
    private const val MaxScale = 1.0
    private const val MinFps = 1
    private const val MaxFps = 30
    private const val WebpQuality = 72
    private const val JpegQuality = 72

    private val hooksInstalled = AtomicBoolean(false)
    private val samplerRunning = AtomicBoolean(false)
    private val captureInFlight = AtomicBoolean(false)
    private val encoderExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PortalScreenMirrorEncoder").apply {
            isDaemon = true
        }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var currentActivity: WeakReference<Activity>? = null
    private var lastRequestElapsedMillis = 0L
    private var scale = 0.5
    private var captureIntervalMillis = 500L
    private var latestFrame: ScreenMirrorFrame? = null
    private var touchDownTimeMillis = 0L

    fun openFrameStream(request: PortalStreamRequest): PortalPluginStream {
        val requestedScale = request.query["scale"]?.toDoubleOrNull() ?: 0.5
        val requestedMaxFps = request.query["maxFps"]?.toIntOrNull() ?: 2
        return ScreenMirrorFrameStream(
            scale = requestedScale.coerceIn(MinScale, MaxScale),
            maxFps = requestedMaxFps.coerceIn(MinFps, MaxFps),
        )
    }

    fun openInputStream(): PortalPluginStream =
        ScreenMirrorInputStream()

    fun openMirrorStream(request: PortalStreamRequest): PortalPluginStream {
        val requestedScale = request.query["scale"]?.toDoubleOrNull() ?: 0.5
        val requestedMaxFps = request.query["maxFps"]?.toIntOrNull() ?: 2
        return ScreenMirrorMirrorStream(
            scale = requestedScale.coerceIn(MinScale, MaxScale),
            maxFps = requestedMaxFps.coerceIn(MinFps, MaxFps),
        )
    }

    fun ensureActive(requestedScale: Double, requestedMaxFps: Int) {
        installHooks()
        synchronized(lock) {
            scale = requestedScale.coerceIn(MinScale, MaxScale)
            captureIntervalMillis = 1_000L / requestedMaxFps.coerceIn(MinFps, MaxFps)
            lastRequestElapsedMillis = SystemClock.elapsedRealtime()
        }
        if (samplerRunning.compareAndSet(false, true)) {
            mainHandler.post(captureRunnable)
        }
    }

    fun latestFrameAfter(afterEpochMillis: Long): ScreenMirrorFrame? =
        synchronized(lock) {
            latestFrame?.takeIf { it.updatedAtEpochMillis > afterEpochMillis }
        }

    fun sendTouch(action: String, xRatio: Double, yRatio: Double) {
        installHooks()
        val normalizedX = xRatio.coerceIn(0.0, 1.0).toFloat()
        val normalizedY = yRatio.coerceIn(0.0, 1.0).toFloat()
        mainHandler.post {
            dispatchTouch(action, normalizedX, normalizedY)
        }
    }

    fun framePayload(afterFrameEpochMillis: Long) = buildJsonObject {
        put("type", "getFrameResult")
        val frame = latestFrameAfter(afterFrameEpochMillis)
        if (frame == null) {
            put("frame", JsonNull)
        } else {
            put(
                "frame",
                buildJsonObject {
                    put("updatedAtEpochMillis", frame.updatedAtEpochMillis)
                    put("base64", Base64.encodeToString(frame.bytes, Base64.NO_WRAP))
                    put("mimeType", frame.mimeType)
                    put("format", frame.format)
                    put("width", frame.width)
                    put("height", frame.height)
                    put("sizeBytes", frame.bytes.size)
                },
            )
        }
    }

    private val captureRunnable = object : Runnable {
        override fun run() {
            val active = synchronized(lock) {
                SystemClock.elapsedRealtime() - lastRequestElapsedMillis <= InactiveAfterMillis
            }
            if (!active) {
                samplerRunning.set(false)
                return
            }

            captureCurrentActivity()
            mainHandler.postDelayed(this, synchronized(lock) { captureIntervalMillis })
        }
    }

    fun installHooks() {
        if (!hooksInstalled.compareAndSet(false, true)) return

        runCatching {
            Pine.hook(
                Activity::class.java.getDeclaredMethod("onPostResume"),
                object : MethodHook() {
                    override fun afterCall(callFrame: Pine.CallFrame) {
                        val activity = callFrame.thisObject as? Activity ?: return
                        currentActivity = WeakReference(activity)
                    }
                },
            )
            Pine.hook(
                Activity::class.java.getDeclaredMethod("onDestroy"),
                object : MethodHook() {
                    override fun beforeCall(callFrame: Pine.CallFrame) {
                        val activity = callFrame.thisObject as? Activity ?: return
                        if (currentActivity?.get() === activity) {
                            currentActivity = null
                        }
                    }
                },
            )
        }.onFailure { error ->
            Log.w(LogTag, "Unable to install activity hooks", error)
        }
    }

    private fun captureCurrentActivity() {
        if (!captureInFlight.compareAndSet(false, true)) return
        val activity = currentActivity?.get() ?: PortalAndroidContext.currentActivity()
        if (activity == null) {
            captureInFlight.set(false)
            return
        }
        val root = activity.window?.decorView?.rootView
        if (root == null) {
            captureInFlight.set(false)
            return
        }
        if (!root.isAttachedToWindow || root.width <= 0 || root.height <= 0) {
            captureInFlight.set(false)
            return
        }

        val requestedScale = synchronized(lock) { scale }
        val width = max(1, (root.width * requestedScale).toInt())
        val height = max(1, (root.height * requestedScale).toInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                PixelCopy.request(activity.window, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        encodeCapturedBitmap(bitmap, width, height)
                    } else {
                        drawRootFallback(root, bitmap, width, height)
                    }
                }, mainHandler)
            }.onFailure { error ->
                Log.w(LogTag, "Unable to start PixelCopy screen mirror capture", error)
                drawRootFallback(root, bitmap, width, height)
            }
        } else {
            drawRootFallback(root, bitmap, width, height)
        }
    }

    private fun drawRootFallback(root: android.view.View, bitmap: Bitmap, width: Int, height: Int) {
        runCatching {
            val canvas = Canvas(bitmap).apply {
                scale(width.toFloat() / root.width.toFloat(), height.toFloat() / root.height.toFloat())
            }
            root.draw(canvas)
            encodeCapturedBitmap(bitmap, width, height)
        }.onFailure { error ->
            bitmap.recycle()
            captureInFlight.set(false)
            Log.w(LogTag, "Unable to draw screen mirror fallback frame", error)
        }
    }

    private fun encodeCapturedBitmap(bitmap: Bitmap, width: Int, height: Int) {
        encoderExecutor.execute {
            runCatching {
                val encoded = bitmap.smallestEncoding() ?: return@runCatching
                synchronized(lock) {
                    latestFrame = ScreenMirrorFrame(
                        bytes = encoded.bytes,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                        mimeType = encoded.mimeType,
                        format = encoded.format,
                        width = width,
                        height = height,
                    )
                }
            }.onFailure { error ->
                Log.w(LogTag, "Unable to encode screen mirror frame", error)
            }
            bitmap.recycle()
            captureInFlight.set(false)
        }
    }

    private fun dispatchTouch(action: String, xRatio: Float, yRatio: Float) {
        val activity = currentActivity?.get() ?: PortalAndroidContext.currentActivity() ?: return
        val root = activity.window?.decorView ?: return
        if (!root.isAttachedToWindow || root.width <= 0 || root.height <= 0) return

        val motionAction = when (action) {
            "down" -> MotionEvent.ACTION_DOWN
            "move" -> MotionEvent.ACTION_MOVE
            "up" -> MotionEvent.ACTION_UP
            "cancel" -> MotionEvent.ACTION_CANCEL
            else -> return
        }
        val eventTime = SystemClock.uptimeMillis()
        if (motionAction == MotionEvent.ACTION_DOWN || touchDownTimeMillis == 0L) {
            touchDownTimeMillis = eventTime
        }

        val event = MotionEvent.obtain(
            touchDownTimeMillis,
            eventTime,
            motionAction,
            root.width * xRatio,
            root.height * yRatio,
            0,
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        runCatching {
            root.dispatchTouchEvent(event)
        }.onFailure { error ->
            Log.w(LogTag, "Unable to dispatch screen mirror touch", error)
        }
        event.recycle()

        if (motionAction == MotionEvent.ACTION_UP || motionAction == MotionEvent.ACTION_CANCEL) {
            touchDownTimeMillis = 0L
        }
    }

    private fun Bitmap.smallestEncoding(): EncodedFrame? =
        listOfNotNull(
            encodeWebp(),
            encode(Bitmap.CompressFormat.JPEG, JpegQuality, "image/jpeg", "jpeg"),
            encode(Bitmap.CompressFormat.PNG, 100, "image/png", "png"),
        ).minByOrNull { it.bytes.size }

    @Suppress("DEPRECATION")
    private fun Bitmap.encodeWebp(): EncodedFrame? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            encode(Bitmap.CompressFormat.WEBP_LOSSY, WebpQuality, "image/webp", "webp")
        } else {
            encode(Bitmap.CompressFormat.WEBP, WebpQuality, "image/webp", "webp")
        }

    private fun Bitmap.encode(
        format: Bitmap.CompressFormat,
        quality: Int,
        mimeType: String,
        formatName: String,
    ): EncodedFrame? =
        ByteArrayOutputStream().use { stream ->
            if (!compress(format, quality, stream)) return null
            EncodedFrame(
                bytes = stream.toByteArray(),
                mimeType = mimeType,
                format = formatName,
            )
        }

    data class ScreenMirrorFrame(
        val bytes: ByteArray,
        val updatedAtEpochMillis: Long,
        val mimeType: String,
        val format: String,
        val width: Int,
        val height: Int,
    )

    private data class EncodedFrame(
        val bytes: ByteArray,
        val mimeType: String,
        val format: String,
    )

    private class ScreenMirrorFrameStream(
        private val scale: Double,
        private val maxFps: Int,
    ) : PortalPluginStream {
        private val active = AtomicBoolean(false)
        private var streamThread: Thread? = null

        override fun onOpen(sink: PortalStreamSink) {
            if (!active.compareAndSet(false, true)) return
            streamThread = Thread(
                {
                    var lastSentEpochMillis = 0L
                    val frameIntervalMillis = 1_000L / maxFps.coerceIn(MinFps, MaxFps)
                    while (active.get()) {
                        ensureActive(scale, maxFps)
                        val frame = latestFrameAfter(lastSentEpochMillis)
                        if (frame != null) {
                            sink.sendText(frame.metadataText())
                            sink.sendBinary(frame.bytes)
                            lastSentEpochMillis = frame.updatedAtEpochMillis
                        }
                        runCatching { Thread.sleep(frameIntervalMillis) }
                    }
                },
                "PortalScreenMirrorStream",
            ).apply {
                isDaemon = true
                start()
            }
        }

        override fun onClose() {
            stop()
        }

        override fun onError(error: Throwable) {
            stop()
        }

        private fun stop() {
            active.set(false)
            streamThread?.interrupt()
            streamThread = null
        }

        private fun ScreenMirrorFrame.metadataText(): String =
            "frame|$updatedAtEpochMillis|$mimeType|$format|$width|$height|${bytes.size}"
    }

    private class ScreenMirrorInputStream : PortalPluginStream {
        override fun onOpen(sink: PortalStreamSink) = Unit

        override fun onBinary(bytes: ByteArray) {
            val event = bytes.toTouchEvent() ?: return
            sendTouch(event.action, event.x.toDouble(), event.y.toDouble())
        }
    }

    private class ScreenMirrorMirrorStream(
        private val scale: Double,
        private val maxFps: Int,
    ) : PortalPluginStream {
        private val frameStream = ScreenMirrorFrameStream(scale, maxFps)
        private val inputStream = ScreenMirrorInputStream()

        override fun onOpen(sink: PortalStreamSink) {
            frameStream.onOpen(sink)
        }

        override fun onBinary(bytes: ByteArray) {
            inputStream.onBinary(bytes)
        }

        override fun onClose() {
            frameStream.onClose()
        }

        override fun onError(error: Throwable) {
            frameStream.onError(error)
        }
    }

    private fun ByteArray.toTouchEvent(): TouchEvent? {
        if (size < 9) return null
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        val action = when (buffer.get().toInt()) {
            0 -> "down"
            1 -> "move"
            2 -> "up"
            3 -> "cancel"
            else -> return null
        }
        return TouchEvent(
            action = action,
            x = buffer.float.coerceIn(0f, 1f),
            y = buffer.float.coerceIn(0f, 1f),
        )
    }

    private data class TouchEvent(
        val action: String,
        val x: Float,
        val y: Float,
    )
}
