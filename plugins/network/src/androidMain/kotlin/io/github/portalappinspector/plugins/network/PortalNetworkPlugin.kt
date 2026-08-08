package io.github.portalappinspector.plugins.network

import android.os.SystemClock
import android.util.Log
import io.github.portalappinspector.PortalPlugin
import io.github.portalappinspector.PortalPluginRequest
import io.github.portalappinspector.PortalPluginResponse
import io.github.portalappinspector.portalPluginErrorResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback as OkHttpCallback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import okio.Timeout
import retrofit2.Call as RetrofitCall
import retrofit2.Callback
import retrofit2.Response
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLHandshakeException

class PortalNetworkPlugin : PortalPlugin {
    override val id: String = "portal-network"
    override val name: String = "Network"
    override val version = 260809L

    init {
        NetworkTrafficStore.installHooks()
    }

    override suspend fun handle(request: PortalPluginRequest): PortalPluginResponse {
        val operation = request.payload["type"]?.jsonPrimitive?.contentOrNull
            ?: return portalPluginErrorResponse(request, "missing_operation", "Network request payload is missing type.")

        return when (operation) {
            "setupMocks" -> {
                val mocks = NetworkMockParser.parse(request.payload)
                NetworkTrafficStore.setupMocks(mocks)
                success(
                    request,
                    buildJsonObject {
                        put("type", "setupMocksResult")
                        put("count", mocks.size)
                    },
                )
            }
            "getMockState" -> {
                success(
                    request,
                    buildJsonObject {
                        put("type", "mockState")
                        put("count", NetworkTrafficStore.mockCount())
                    },
                )
            }
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

    private val mockExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val hooksInstalled = AtomicBoolean(false)
    private val nextId = AtomicLong(0L)
    private val executeCalls = ConcurrentHashMap<Any, HookExtras>()
    private val mockedRetrofitCalls = ConcurrentHashMap<Any, PortalNetworkMock>()
    private val responseBodies = ConcurrentHashMap<Any, ResponseBodySnapshot>()
    private val mocks = AtomicReference<List<PortalNetworkMock>>(emptyList())
    private val lock = Any()
    private val calls = ArrayDeque<NetworkCall>()

    fun setupMocks(nextMocks: List<PortalNetworkMock>) {
        mocks.set(nextMocks.filter { it.enabled })
    }

    fun mockCount(): Int = mocks.get().size

    fun installHooks() {
        if (!hooksInstalled.compareAndSet(false, true)) return

        runCatching {
            val okHttpCallClass = Class.forName("retrofit2.OkHttpCall")
            Pine.hook(
                okHttpCallClass.getDeclaredMethod("enqueue", Callback::class.java),
                object : MethodHook() {
                    override fun beforeCall(callFrame: Pine.CallFrame) {
                        val call = callFrame.thisObject as? RetrofitCall<*> ?: return
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
                        val call = callFrame.thisObject as? RetrofitCall<*> ?: return
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
                        mockedRetrofitCalls.remove(call)
                    }
                },
            )
            Pine.hook(
                okHttpCallClass.getDeclaredMethod("createRawCall").apply { isAccessible = true },
                object : MethodHook() {
                    override fun afterCall(callFrame: Pine.CallFrame) {
                        val realCall = callFrame.getResult() as? Call ?: return
                        val request = realCall.request()
                        val snapshot = request.mockMatchSnapshot()
                        val mock = findMatchingMock(snapshot) ?: return
                        mockedRetrofitCalls[callFrame.thisObject] = mock
                        callFrame.setResult(MockedOkHttpCall(request = request, mock = mock))
                    }
                },
            )
            Pine.hook(
                okHttpCallClass.getDeclaredMethod("parseResponse", okhttp3.Response::class.java),
                object : MethodHook() {
                    override fun beforeCall(callFrame: Pine.CallFrame) {
                        val call = callFrame.thisObject ?: return
                        val rawResponse = callFrame.args.firstOrNull() as? okhttp3.Response ?: return
                        responseBodies[call] = rawResponse.bodySnapshot()
                    }
                },
            )
        }.onFailure { error ->
            Log.w(LogTag, "Unable to install Retrofit hooks", error)
        }
    }

    private fun findMatchingMock(request: MockRequestSnapshot): PortalNetworkMock? =
        mocks.get().firstOrNull { mock ->
            mock.expectation.matchers.all { matcher -> matcher.matches(request) }
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
            requestHeaders = request.headers,
            requestBody = request.body?.body,
            requestContentType = request.body?.contentType,
            requestBodySizeBytes = request.body?.sizeBytes,
            requestBodyTruncated = request.body?.truncated ?: false,
            statusCode = statusCode,
            durationMillis = (completedAtElapsed - startedAtElapsed).coerceAtLeast(0L),
            error = error,
            responseHeaders = responseBody?.headers.orEmpty(),
            responseBody = responseBody?.body,
            responseContentType = responseBody?.contentType,
            responseBodySizeBytes = responseBody?.sizeBytes,
            responseBodyTruncated = responseBody?.truncated ?: false,
            isMocked = request.isMocked,
        )
        synchronized(lock) {
            calls.addLast(call)
            while (calls.size > MaxCalls) {
                calls.removeFirst()
            }
        }
    }

    private fun RetrofitCall<*>.requestSnapshot(): RequestSnapshot =
        runCatching {
            val request = request()
            val url = request.url()
            val query = url.encodedQuery()?.let { "?$it" }.orEmpty()
            RequestSnapshot(
                method = request.method(),
                url = url.toString(),
                endpoint = "${url.encodedPath()}$query",
                headers = request.headers().names().associateWith { name -> request.headers().values(name) },
                body = request.body()?.bodySnapshot(),
                isMocked = mockedRetrofitCalls.containsKey(this),
            )
        }.getOrElse { error ->
            RequestSnapshot(
                method = "HTTP",
                url = "",
                endpoint = error.message ?: "Unable to create request",
                headers = emptyMap(),
                body = null,
                isMocked = mockedRetrofitCalls.containsKey(this),
            )
        }

    private class RecordingCallback(
        private val original: Callback<Any?>,
        private val request: RequestSnapshot,
        private val startedAtElapsed: Long,
    ) : Callback<Any?> {
        override fun onResponse(call: RetrofitCall<Any?>, response: Response<Any?>) {
            record(
                request = request,
                statusCode = response.code(),
                completedAtElapsed = SystemClock.elapsedRealtime(),
                startedAtElapsed = startedAtElapsed,
                error = null,
                responseBody = responseBodies.remove(call),
            )
            mockedRetrofitCalls.remove(call)
            original.onResponse(call, response)
        }

        override fun onFailure(call: RetrofitCall<Any?>, t: Throwable) {
            record(
                request = request,
                statusCode = null,
                completedAtElapsed = SystemClock.elapsedRealtime(),
                startedAtElapsed = startedAtElapsed,
                error = t.message,
                responseBody = responseBodies.remove(call),
            )
            mockedRetrofitCalls.remove(call)
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
        val headers: Map<String, List<String>>,
        val body: RequestBodySnapshot?,
        val isMocked: Boolean,
    )

    private data class NetworkCall(
        val id: Long,
        val timestampEpochMillis: Long,
        val method: String,
        val url: String,
        val endpoint: String,
        val requestHeaders: Map<String, List<String>>,
        val requestBody: String?,
        val requestContentType: String?,
        val requestBodySizeBytes: Long?,
        val requestBodyTruncated: Boolean,
        val statusCode: Int?,
        val durationMillis: Long,
        val error: String?,
        val responseHeaders: Map<String, List<String>>,
        val responseBody: String?,
        val responseContentType: String?,
        val responseBodySizeBytes: Long?,
        val responseBodyTruncated: Boolean,
        val isMocked: Boolean,
    ) {
        fun toJson() = buildJsonObject {
            put("id", id)
            put("timestampEpochMillis", timestampEpochMillis)
            put("method", method)
            put("url", url)
            put("endpoint", endpoint)
            put("requestHeaders", requestHeaders.toJsonObject())
            if (requestBody == null) {
                put("requestBody", JsonNull)
            } else {
                put("requestBody", requestBody)
            }
            if (requestContentType == null) {
                put("requestContentType", JsonNull)
            } else {
                put("requestContentType", requestContentType)
            }
            if (requestBodySizeBytes == null) {
                put("requestBodySizeBytes", JsonNull)
            } else {
                put("requestBodySizeBytes", requestBodySizeBytes)
            }
            put("requestBodyTruncated", requestBodyTruncated)
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
            put("responseHeaders", responseHeaders.toJsonObject())
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
            if (responseBodySizeBytes == null) {
                put("responseBodySizeBytes", JsonNull)
            } else {
                put("responseBodySizeBytes", responseBodySizeBytes)
            }
            put("responseBodyTruncated", responseBodyTruncated)
            put("isMocked", isMocked)
        }
    }

    private data class RequestBodySnapshot(
        val body: String,
        val contentType: String?,
        val sizeBytes: Long?,
        val truncated: Boolean,
    )

    private data class ResponseBodySnapshot(
        val headers: Map<String, List<String>>,
        val body: String?,
        val contentType: String?,
        val sizeBytes: Long?,
        val truncated: Boolean,
    )

    private fun okhttp3.Response.bodySnapshot(): ResponseBodySnapshot =
        runCatching {
            val responseHeaders = headers().names().associateWith { name -> headers().values(name) }
            val body = body() ?: return ResponseBodySnapshot(
                headers = responseHeaders,
                body = null,
                contentType = null,
                sizeBytes = null,
                truncated = false,
            )
            val contentLength = body.contentLength()
            val peeked: ResponseBody = peekBody(MaxBodyBytes)
            val capturedBody = peeked.string()
            ResponseBodySnapshot(
                headers = responseHeaders,
                body = capturedBody,
                contentType = body.contentType()?.toString(),
                sizeBytes = contentLength.takeIf { it >= 0L } ?: capturedBody.encodeToByteArray().size.toLong(),
                truncated = contentLength > MaxBodyBytes,
            )
        }.getOrElse {
            ResponseBodySnapshot(
                headers = headers().names().associateWith { name -> headers().values(name) },
                body = null,
                contentType = null,
                sizeBytes = null,
                truncated = false,
            )
        }

    private fun List<kotlinx.serialization.json.JsonObject>.toJsonArray(): JsonArray =
        buildJsonArray { forEach(::add) }

    private fun Map<String, List<String>>.toJsonObject() = buildJsonObject {
        forEach { (name, values) ->
            put(name, buildJsonArray { values.forEach(::add) })
        }
    }

    private fun Request.mockMatchSnapshot(): MockRequestSnapshot {
        val url = url()
        val requestBodyText = if (mocks.get().any { it.expectation.needsRequestBody }) {
            body()?.bodySnapshot()?.body
        } else {
            null
        }
        return MockRequestSnapshot(
            method = method(),
            url = url.toString(),
            domain = url.host(),
            encodedPath = url.encodedPath(),
            queryValues = url.queryParameterNames().associateWith { name -> url.queryParameterValues(name) },
            headers = headers().names().associateWith { name -> headers().values(name) },
            body = requestBodyText,
        )
    }

    private fun okhttp3.RequestBody.bodySnapshot(): RequestBodySnapshot? =
        runCatching {
            val buffer = Buffer()
            writeTo(buffer)
            val originalSize = buffer.size()
            val capturedByteCount = originalSize.coerceAtMost(MaxBodyBytes)
            val capturedBody = buffer.readUtf8(capturedByteCount)
            RequestBodySnapshot(
                body = capturedBody,
                contentType = contentType()?.toString(),
                sizeBytes = contentLength().takeIf { it >= 0L } ?: originalSize,
                truncated = originalSize > MaxBodyBytes,
            )
        }.getOrNull()

    private class MockedOkHttpCall(
        private val request: Request,
        private val mock: PortalNetworkMock,
    ) : Call {
        private val executed = AtomicBoolean(false)
        private val canceled = AtomicBoolean(false)

        override fun request(): Request = request

        override fun execute(): okhttp3.Response {
            if (!executed.compareAndSet(false, true)) {
                throw IllegalStateException("Already Executed")
            }
            sleepDelay()
            if (canceled.get()) throw IOException("Canceled")
            mock.response.toThrowable()?.let { throw it }
            return mock.response.toOkHttpResponse(request)
        }

        override fun enqueue(responseCallback: OkHttpCallback) {
            if (!executed.compareAndSet(false, true)) {
                responseCallback.onFailure(this, IOException("Already Executed"))
                return
            }
            mockExecutor.execute {
                sleepDelay()
                if (canceled.get()) {
                    responseCallback.onFailure(this, IOException("Canceled"))
                    return@execute
                }
                val error = mock.response.toThrowable()
                if (error != null) {
                    responseCallback.onFailure(this, error)
                } else {
                    responseCallback.onResponse(this, mock.response.toOkHttpResponse(request))
                }
            }
        }

        override fun cancel() {
            canceled.set(true)
        }

        override fun isExecuted(): Boolean = executed.get()

        override fun isCanceled(): Boolean = canceled.get()

        override fun clone(): Call = MockedOkHttpCall(request = request, mock = mock)

        override fun timeout(): Timeout = Timeout.NONE

        private fun sleepDelay() {
            val delayMs = mock.expectation.delayMs
            if (delayMs <= 0L) return
            runCatching { Thread.sleep(delayMs) }
            if (Thread.currentThread().isInterrupted) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun PortalNetworkMockResponse.toThrowable(): IOException? {
        val errorType = error?.type ?: return null
        return when (errorType) {
            "ioException" -> IOException("PortalAppInspector mock I/O error")
            "socketTimeout" -> SocketTimeoutException("PortalAppInspector mock socket timeout")
            "unknownHost" -> UnknownHostException("PortalAppInspector mock unknown host")
            "connectionRefused" -> ConnectException("PortalAppInspector mock connection refused")
            "sslHandshake" -> SSLHandshakeException("PortalAppInspector mock SSL handshake failure")
            "jsonParse" -> IOException("PortalAppInspector mock JSON parse error")
            else -> IOException("PortalAppInspector mock error: $errorType")
        }
    }

    private fun PortalNetworkMockResponse.toOkHttpResponse(request: Request): okhttp3.Response {
        val bodyResponse = body ?: PortalNetworkBodyResponse()
        val mediaType = bodyResponse.contentType?.let(MediaType::parse)
        val responseBody = ResponseBody.create(mediaType, bodyResponse.body.orEmpty())
        return okhttp3.Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(bodyResponse.code ?: 200)
            .message(httpMessage(bodyResponse.code ?: 200))
            .body(responseBody)
            .apply {
                bodyResponse.contentType?.let { header("Content-Type", it) }
                bodyResponse.headers.forEach { (name, value) -> header(name, value) }
            }
            .build()
    }
}

private object NetworkMockParser {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun parse(payload: kotlinx.serialization.json.JsonObject): List<PortalNetworkMock> =
        payload["mocks"]
            ?.jsonArray
            ?.mapNotNull { element ->
                runCatching {
                    json.decodeFromJsonElement<PortalNetworkMock>(element).normalizedOrNull()
                }.getOrNull()
            }
            .orEmpty()
}

@Serializable
private data class PortalNetworkMock(
    val id: String,
    val enabled: Boolean = true,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
    val expectation: PortalNetworkExpectation = PortalNetworkExpectation(),
    val response: PortalNetworkMockResponse = PortalNetworkMockResponse(),
) {
    fun normalizedOrNull(): PortalNetworkMock? {
        val validResponse = response.body != null || response.error != null
        return takeIf { id.isNotBlank() && expectation.matchers.isNotEmpty() && validResponse }
    }
}

@Serializable
private data class PortalNetworkExpectation(
    val delayMs: Long = 0L,
    val matchers: List<PortalNetworkMatcher> = emptyList(),
) {
    val needsRequestBody: Boolean
        get() = matchers.any { it.type == "requestBodyContainsText" || it.type == "requestBodyContainsTextNormalized" }
}

@Serializable
private data class PortalNetworkMatcher(
    val type: String,
    val key: String? = null,
    val value: String = "",
) {
    fun matches(request: MockRequestSnapshot): Boolean =
        when (type) {
            "domain" -> value.equals(request.domain, ignoreCase = true)
            "httpMethod" -> value.equals(request.method, ignoreCase = true)
            "urlPathContains" -> request.encodedPath.contains(value)
            "queryEquals" -> {
                val queryKey = key ?: return false
                request.queryValues[queryKey]?.any { it == value } == true
            }
            "requestBodyContainsText" -> request.body?.contains(value) == true
            "headerContains" -> {
                val headerKey = key ?: return false
                request.headers.entries
                    .firstOrNull { it.key.equals(headerKey, ignoreCase = true) }
                    ?.value
                    ?.any { it.contains(value, ignoreCase = true) } == true
            }
            "urlContains" -> request.url.contains(value, ignoreCase = true)
            "headersContainText" -> request.headers.entries.any { (key, values) ->
                values.any { valueText ->
                    "$key:$valueText".contains(value, ignoreCase = true) ||
                        "$key: $valueText".contains(value, ignoreCase = true)
                }
            }
            "requestBodyContainsTextNormalized" -> {
                val normalizedBody = request.body?.withoutInvisibleChars() ?: return false
                normalizedBody.contains(value.withoutInvisibleChars(), ignoreCase = true)
            }
            "methodContains" -> request.method.contains(value, ignoreCase = true)
            else -> false
        }
}

private fun String.withoutInvisibleChars(): String =
    filterNot { it.isWhitespace() }

@Serializable
private data class PortalNetworkMockResponse(
    val body: PortalNetworkBodyResponse? = null,
    val error: PortalNetworkErrorResponse? = null,
)

@Serializable
private data class PortalNetworkBodyResponse(
    val code: Int? = 200,
    val body: String? = "",
    val contentType: String? = "application/json",
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
private data class PortalNetworkErrorResponse(
    val type: String,
)

private data class MockRequestSnapshot(
    val method: String,
    val url: String,
    val domain: String,
    val encodedPath: String,
    val queryValues: Map<String, List<String>>,
    val headers: Map<String, List<String>>,
    val body: String?,
)

private fun httpMessage(code: Int): String =
    when (code) {
        200 -> "OK"
        201 -> "Created"
        202 -> "Accepted"
        204 -> "No Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        409 -> "Conflict"
        422 -> "Unprocessable Entity"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        else -> "Mock Response"
    }
