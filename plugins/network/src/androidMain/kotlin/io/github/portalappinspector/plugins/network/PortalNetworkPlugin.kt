package io.github.portalappinspector.plugins.network

import android.os.SystemClock
import android.util.Log
import io.github.portalappinspector.PortalPlugin
import io.github.portalappinspector.PortalPluginRequest
import io.github.portalappinspector.PortalPluginResponse
import io.github.portalappinspector.portalPluginErrorResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class PortalNetworkPlugin : PortalPlugin {
    override val id: String = "portal:network"
    override val name: String = "Network"
    override val version: String = "0.1.0"

    init {
        NetworkTrafficStore.installHooks()
    }

    override suspend fun handle(request: PortalPluginRequest): PortalPluginResponse {
        val operation = request.payload["type"]?.jsonPrimitive?.contentOrNull
            ?: return portalPluginErrorResponse(request, "missing_operation", "Network request payload is missing type.")

        return when (operation) {
            "listAfter" -> {
                val afterTimestamp = request.payload["afterTimestampEpochMillis"]
                    ?.jsonPrimitive
                    ?.longOrNull
                    ?: 0L
                success(request, NetworkTrafficStore.listAfterPayload(afterTimestamp))
            }
            else -> portalPluginErrorResponse(request, "unknown_operation", "Unknown network operation: $operation")
        }
    }

    private fun success(request: PortalPluginRequest, payload: kotlinx.serialization.json.JsonObject): PortalPluginResponse =
        PortalPluginResponse(
            id = request.id,
            pluginId = request.pluginId,
            ok = true,
            payload = payload,
        )
}

private object NetworkTrafficStore {
    private const val LogTag = "PortalNetworkPlugin"
    private const val MaxCalls = 500
    private const val MaxBodyBytes = 256L * 1024L

    private val hooksInstalled = AtomicBoolean(false)
    private val nextId = AtomicLong(0L)
    private val executeCalls = ConcurrentHashMap<Any, HookExtras>()
    private val responseBodies = ConcurrentHashMap<Any, ResponseBodySnapshot>()
    private val lock = Any()
    private val calls = ArrayDeque<NetworkCall>()

    fun installHooks() {
        if (!hooksInstalled.compareAndSet(false, true)) return

        runCatching {
            val okHttpCallClass = Class.forName("retrofit2.OkHttpCall")
            Pine.hook(
                okHttpCallClass.getDeclaredMethod("enqueue", Callback::class.java),
                object : MethodHook() {
                    override fun beforeCall(callFrame: Pine.CallFrame) {
                        val call = callFrame.thisObject as? Call<*> ?: return
                        @Suppress("UNCHECKED_CAST")
                        val originalCallback = callFrame.args.firstOrNull() as? Callback<Any?> ?: return
                        val startedAtElapsed = SystemClock.elapsedRealtime()
                        val request = call.requestSnapshot()
                        callFrame.args[0] = RecordingCallback(
                            original = originalCallback,
                            request = request,
                            startedAtElapsed = startedAtElapsed,
                        )
                    }
                },
            )
            Pine.hook(
                okHttpCallClass.getDeclaredMethod("execute"),
                object : MethodHook() {
                    override fun beforeCall(callFrame: Pine.CallFrame) {
                        val call = callFrame.thisObject as? Call<*> ?: return
                        executeCalls[call] = HookExtras(
                            request = call.requestSnapshot(),
                            startedAtElapsed = SystemClock.elapsedRealtime(),
                        )
                    }

                    override fun afterCall(callFrame: Pine.CallFrame) {
                        val call = callFrame.thisObject ?: return
                        val extras = executeCalls.remove(call) ?: return
                        val response = callFrame.getResult() as? Response<*>
                        record(
                            request = extras.request,
                            statusCode = response?.code(),
                            completedAtElapsed = SystemClock.elapsedRealtime(),
                            startedAtElapsed = extras.startedAtElapsed,
                            error = callFrame.getThrowable()?.message,
                            responseBody = responseBodies.remove(call),
                        )
                    }
                },
            )
            Pine.hook(
                okHttpCallClass.getDeclaredMethod("parseResponse", okhttp3.Response::class.java),
                object : MethodHook() {
                    override fun beforeCall(callFrame: Pine.CallFrame) {
                        val call = callFrame.thisObject ?: return
                        val rawResponse = callFrame.args.firstOrNull() as? okhttp3.Response ?: return
                        rawResponse.bodySnapshot()?.let { responseBodies[call] = it }
                    }
                },
            )
        }.onFailure { error ->
            Log.w(LogTag, "Unable to install Retrofit hooks", error)
        }
    }

    fun listAfterPayload(afterTimestampEpochMillis: Long) = buildJsonObject {
        val snapshot = synchronized(lock) {
            calls.filter { it.timestampEpochMillis > afterTimestampEpochMillis }
        }
        put("type", "listAfterResult")
        put("items", snapshot.map { it.toJson() }.toJsonArray())
    }

    private fun record(
        request: RequestSnapshot,
        statusCode: Int?,
        completedAtElapsed: Long,
        startedAtElapsed: Long,
        error: String?,
        responseBody: ResponseBodySnapshot?,
    ) {
        val call = NetworkCall(
            id = nextId.incrementAndGet(),
            timestampEpochMillis = System.currentTimeMillis(),
            method = request.method,
            url = request.url,
            endpoint = request.endpoint,
            statusCode = statusCode,
            durationMillis = (completedAtElapsed - startedAtElapsed).coerceAtLeast(0L),
            error = error,
            responseBody = responseBody?.body,
            responseContentType = responseBody?.contentType,
            responseBodyTruncated = responseBody?.truncated ?: false,
        )
        synchronized(lock) {
            calls.addLast(call)
            while (calls.size > MaxCalls) {
                calls.removeFirst()
            }
        }
    }

    private fun Call<*>.requestSnapshot(): RequestSnapshot =
        runCatching {
            val request = request()
            val url = request.url()
            val query = url.encodedQuery()?.let { "?$it" }.orEmpty()
            RequestSnapshot(
                method = request.method(),
                url = url.toString(),
                endpoint = "${url.encodedPath()}$query",
            )
        }.getOrElse { error ->
            RequestSnapshot(
                method = "HTTP",
                url = "",
                endpoint = error.message ?: "Unable to create request",
            )
        }

    private class RecordingCallback(
        private val original: Callback<Any?>,
        private val request: RequestSnapshot,
        private val startedAtElapsed: Long,
    ) : Callback<Any?> {
        override fun onResponse(call: Call<Any?>, response: Response<Any?>) {
            record(
                request = request,
                statusCode = response.code(),
                completedAtElapsed = SystemClock.elapsedRealtime(),
                startedAtElapsed = startedAtElapsed,
                error = null,
                responseBody = responseBodies.remove(call),
            )
            original.onResponse(call, response)
        }

        override fun onFailure(call: Call<Any?>, t: Throwable) {
            record(
                request = request,
                statusCode = null,
                completedAtElapsed = SystemClock.elapsedRealtime(),
                startedAtElapsed = startedAtElapsed,
                error = t.message,
                responseBody = responseBodies.remove(call),
            )
            original.onFailure(call, t)
        }
    }

    private data class HookExtras(
        val request: RequestSnapshot,
        val startedAtElapsed: Long,
    )

    private data class RequestSnapshot(
        val method: String,
        val url: String,
        val endpoint: String,
    )

    private data class NetworkCall(
        val id: Long,
        val timestampEpochMillis: Long,
        val method: String,
        val url: String,
        val endpoint: String,
        val statusCode: Int?,
        val durationMillis: Long,
        val error: String?,
        val responseBody: String?,
        val responseContentType: String?,
        val responseBodyTruncated: Boolean,
    ) {
        fun toJson() = buildJsonObject {
            put("id", id)
            put("timestampEpochMillis", timestampEpochMillis)
            put("method", method)
            put("url", url)
            put("endpoint", endpoint)
            if (statusCode == null) {
                put("statusCode", JsonNull)
            } else {
                put("statusCode", statusCode)
            }
            put("durationMillis", durationMillis)
            if (error == null) {
                put("error", JsonNull)
            } else {
                put("error", error)
            }
            if (responseBody == null) {
                put("responseBody", JsonNull)
            } else {
                put("responseBody", responseBody)
            }
            if (responseContentType == null) {
                put("responseContentType", JsonNull)
            } else {
                put("responseContentType", responseContentType)
            }
            put("responseBodyTruncated", responseBodyTruncated)
        }
    }

    private data class ResponseBodySnapshot(
        val body: String,
        val contentType: String?,
        val truncated: Boolean,
    )

    private fun okhttp3.Response.bodySnapshot(): ResponseBodySnapshot? =
        runCatching {
            val body = body() ?: return null
            val contentLength = body.contentLength()
            val peeked: ResponseBody = peekBody(MaxBodyBytes)
            ResponseBodySnapshot(
                body = peeked.string(),
                contentType = body.contentType()?.toString(),
                truncated = contentLength > MaxBodyBytes,
            )
        }.getOrNull()

    private fun List<kotlinx.serialization.json.JsonObject>.toJsonArray(): JsonArray =
        buildJsonArray { forEach(::add) }
}
