package io.github.portalappinspector.plugins.logs

import android.util.Log
import io.github.portalappinspector.PortalPlugin
import io.github.portalappinspector.PortalPluginRequest
import io.github.portalappinspector.PortalPluginResponse
import io.github.portalappinspector.android.PortalAndroidContext
import io.github.portalappinspector.portalPluginErrorResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import top.canyie.pine.Pine
import top.canyie.pine.PineConfig
import top.canyie.pine.callback.MethodHook
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class PortalLogsPlugin : PortalPlugin {
    override val id: String = "portal-logs"
    override val name: String = "Logs"
    override val version: String = "0.1.0"

    override suspend fun handle(request: PortalPluginRequest): PortalPluginResponse {
        val operation = request.payload["type"]?.jsonPrimitive?.contentOrNull
            ?: return portalPluginErrorResponse(request, "missing_operation", "Logs request payload is missing type.")

        return when (operation) {
            "listAfter" -> {
                LogsStore.installHooks()
                val logsAfter = request.payload["logsAfterEpochMillis"]
                    ?.jsonPrimitive
                    ?.longOrNull
                    ?: 0L
                val filter = LogFilter.fromJson(request.payload["filter"] as? JsonObject)
                success(request, LogsStore.listAfterPayload(logsAfter, filter))
            }
            else -> portalPluginErrorResponse(request, "unknown_operation", "Unknown logs operation: $operation")
        }
    }

    private fun success(request: PortalPluginRequest, payload: JsonObject): PortalPluginResponse =
        PortalPluginResponse(
            id = request.id,
            pluginId = request.pluginId,
            ok = true,
            payload = payload,
        )
}

private object LogsStore {
    private const val MaxLogs = 5_000
    private const val MaxCallSites = 3

    private val hooksInstalled = AtomicBoolean(false)
    private val recording = object : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean = false
    }
    private val nextId = AtomicLong(0L)
    private val lock = Any()
    private val logs = ArrayDeque<LogEntry>()
    private var lastTimestampEpochMillis = 0L

    fun installHooks() {
        if (!hooksInstalled.compareAndSet(false, true)) return
        // Pine debug logging calls android.util.Log from its bridge on every hooked method call.
        // If Log.* is hooked while PineConfig.debug is true, Pine's own bridge logs can recurse
        // through this plugin and pin startup threads until the foreground service ANRs.
        // Disable Pine debug logging before installing any Log hooks; keep hook install lazy so
        // app startup can finish before this plugin touches global logging methods.
        PineConfig.debug = false
        installAndroidLogHooks()
        installPrintStreamHooks()
    }

    fun listAfterPayload(logsAfterEpochMillis: Long, filter: LogFilter) = buildJsonObject {
        val snapshot = synchronized(lock) {
            logs.filter { entry ->
                entry.timestampEpochMillis > logsAfterEpochMillis && filter.matches(entry)
            }
        }
        put("type", "listAfterResult")
        put("items", snapshot.map { it.toJson() }.toJsonArray())
    }

    private fun installAndroidLogHooks() {
        val logClass = Log::class.java
        hookAndroidLogMethod(logClass, "v", LogLevel.Verbose, String::class.java, String::class.java)
        hookAndroidLogMethod(logClass, "v", LogLevel.Verbose, String::class.java, String::class.java, Throwable::class.java)
        hookAndroidLogMethod(logClass, "d", LogLevel.Debug, String::class.java, String::class.java)
        hookAndroidLogMethod(logClass, "d", LogLevel.Debug, String::class.java, String::class.java, Throwable::class.java)
        hookAndroidLogMethod(logClass, "i", LogLevel.Info, String::class.java, String::class.java)
        hookAndroidLogMethod(logClass, "i", LogLevel.Info, String::class.java, String::class.java, Throwable::class.java)
        hookAndroidLogMethod(logClass, "w", LogLevel.Warning, String::class.java, String::class.java)
        hookAndroidLogMethod(logClass, "w", LogLevel.Warning, String::class.java, Throwable::class.java)
        hookAndroidLogMethod(logClass, "w", LogLevel.Warning, String::class.java, String::class.java, Throwable::class.java)
        hookAndroidLogMethod(logClass, "e", LogLevel.Error, String::class.java, String::class.java)
        hookAndroidLogMethod(logClass, "e", LogLevel.Error, String::class.java, String::class.java, Throwable::class.java)
        hookAndroidLogMethod(logClass, "wtf", LogLevel.Assert, String::class.java, String::class.java)
        hookAndroidLogMethod(logClass, "wtf", LogLevel.Assert, String::class.java, Throwable::class.java)
        hookAndroidLogMethod(logClass, "wtf", LogLevel.Assert, String::class.java, String::class.java, Throwable::class.java)
        runCatching {
            Pine.hook(
                logClass.getDeclaredMethod("println", Int::class.javaPrimitiveType!!, String::class.java, String::class.java),
                object : MethodHook() {
                    override fun beforeCall(callFrame: Pine.CallFrame) {
                        val tag = callFrame.args.getOrNull(1) as? String
                        if (tag == "Pine") return
                        recordAndroidLog(
                            level = priorityToLevel(callFrame.args.getOrNull(0) as? Int),
                            tag = tag,
                            message = callFrame.args.getOrNull(2) as? String,
                            throwable = null,
                        )
                    }
                },
            )
        }
    }

    private fun hookAndroidLogMethod(
        logClass: Class<*>,
        methodName: String,
        level: LogLevel,
        vararg parameterTypes: Class<*>,
    ) {
        runCatching {
            Pine.hook(
                logClass.getDeclaredMethod(methodName, *parameterTypes),
                object : MethodHook() {
                    override fun beforeCall(callFrame: Pine.CallFrame) {
                        val tag = callFrame.args.getOrNull(0) as? String
                        if (tag == "Pine") return
                        val throwable = callFrame.args.firstOrNull { it is Throwable } as? Throwable
                        val message = (callFrame.args.getOrNull(1) as? String) ?: throwable?.message
                        recordAndroidLog(
                            level = level,
                            tag = tag,
                            message = message,
                            throwable = throwable,
                        )
                    }
                },
            )
        }
    }

    private fun installPrintStreamHooks() {
        hookPrintStreamMethod("print", String::class.java)
        hookPrintStreamMethod("println", String::class.java)
        hookPrintStreamMethod("println")
    }

    private fun hookPrintStreamMethod(methodName: String, vararg parameterTypes: Class<*>) {
        runCatching {
            Pine.hook(
                PrintStream::class.java.getMethod(methodName, *parameterTypes),
                object : MethodHook() {
                    override fun beforeCall(callFrame: Pine.CallFrame) {
                        val stream = when (callFrame.thisObject) {
                            System.out -> LogStream.Stdout
                            System.err -> LogStream.Stderr
                            else -> return
                        }
                        recordSystemPrint(
                            stream = stream,
                            message = callFrame.args.firstOrNull() as? String ?: "",
                        )
                    }
                },
            )
        }
    }

    private fun recordAndroidLog(
        level: LogLevel,
        tag: String?,
        message: String?,
        throwable: Throwable?,
    ) {
        record(
            level = level,
            source = LogSource.AndroidLog,
            stream = null,
            tag = tag,
            message = message.orEmpty(),
            throwable = throwable?.stackTraceToString(),
        )
    }

    private fun recordSystemPrint(
        stream: LogStream,
        message: String,
    ) {
        record(
            level = if (stream == LogStream.Stderr) LogLevel.Error else LogLevel.Info,
            source = LogSource.SystemPrint,
            stream = stream,
            tag = null,
            message = message,
            throwable = null,
        )
    }

    private fun record(
        level: LogLevel,
        source: LogSource,
        stream: LogStream?,
        tag: String?,
        message: String,
        throwable: String?,
    ) {
        if (recording.get()) return
        recording.set(true)
        try {
            recordLocked(level, source, stream, tag, message, throwable)
        } finally {
            recording.set(false)
        }
    }

    private fun recordLocked(
        level: LogLevel,
        source: LogSource,
        stream: LogStream?,
        tag: String?,
        message: String,
        throwable: String?,
    ) {
        val timestamp = nextTimestampEpochMillis()
        val callSites = extractCallSites()
        val entry = LogEntry(
            id = nextId.incrementAndGet(),
            timestampEpochMillis = timestamp,
            level = level,
            source = source,
            stream = stream,
            tag = tag,
            message = message,
            throwable = throwable,
            threadName = Thread.currentThread().name.orEmpty(),
            callSites = callSites,
        )
        synchronized(lock) {
            logs.addLast(entry)
            while (logs.size > MaxLogs) {
                logs.removeFirst()
            }
        }
    }

    private fun nextTimestampEpochMillis(): Long =
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val next = if (now <= lastTimestampEpochMillis) lastTimestampEpochMillis + 1L else now
            lastTimestampEpochMillis = next
            next
        }

    private fun List<JsonObject>.toJsonArray(): JsonArray =
        buildJsonArray { forEach(::add) }

    private fun priorityToLevel(priority: Int?): LogLevel =
        when (priority) {
            Log.VERBOSE -> LogLevel.Verbose
            Log.DEBUG -> LogLevel.Debug
            Log.INFO -> LogLevel.Info
            Log.WARN -> LogLevel.Warning
            Log.ERROR -> LogLevel.Error
            Log.ASSERT -> LogLevel.Assert
            else -> LogLevel.Info
        }

    private fun extractCallSites(): List<LogCallSite> {
        val sourcePackageName = runCatching {
            PortalAndroidContext.requireApplicationContext().packageName
        }.getOrNull()
        // Stack walking allocates, so keep this dev-only path bounded: immediately reduce the
        // raw stack to at most three app-relevant call sites and never store the full trace.
        val candidates = Throwable()
            .stackTrace
            .asSequence()
            .filterNot(::isIgnoredStackFrame)
            .filter { it.className.isNotBlank() }
            .toList()
        val appFrames = sourcePackageName
            ?.let { packageName ->
                candidates.filter { frame ->
                    frame.className == packageName || frame.className.startsWith("$packageName.")
                }
            }
            .orEmpty()
        return (appFrames.ifEmpty { candidates })
            .take(MaxCallSites)
            .map { it.toLogCallSite() }
    }

    private fun isIgnoredStackFrame(frame: StackTraceElement): Boolean {
        val className = frame.className
        return className.startsWith("io.github.portalappinspector.plugins.logs.") ||
            className.startsWith("io.github.portalappinspector.android.") ||
            className.startsWith("top.canyie.pine.") ||
            className == "android.util.Log" ||
            className == "java.io.PrintStream" ||
            className == "java.lang.Throwable" ||
            className.startsWith("java.lang.reflect.") ||
            className.startsWith("kotlin.") ||
            className.startsWith("kotlinx.coroutines.") ||
            className.startsWith("dalvik.system.")
    }

    private fun StackTraceElement.toLogCallSite(): LogCallSite =
        LogCallSite(
            className = className,
            methodName = methodName,
            fileName = fileName,
            lineNumber = lineNumber.takeIf { it >= 0 },
        )
}

private data class LogCallSite(
    val className: String,
    val methodName: String,
    val fileName: String?,
    val lineNumber: Int?,
) {
    fun toJson() = buildJsonObject {
        put("className", className)
        put("methodName", methodName)
        if (fileName == null) {
            put("fileName", JsonNull)
        } else {
            put("fileName", fileName)
        }
        if (lineNumber == null) {
            put("lineNumber", JsonNull)
        } else {
            put("lineNumber", lineNumber)
        }
    }
}

private data class LogEntry(
    val id: Long,
    val timestampEpochMillis: Long,
    val level: LogLevel,
    val source: LogSource,
    val stream: LogStream?,
    val tag: String?,
    val message: String,
    val throwable: String?,
    val threadName: String,
    val callSites: List<LogCallSite>,
) {
    private val searchableText: String = listOfNotNull(
        level.value,
        source.value,
        stream?.value,
        tag,
        message,
        throwable,
        threadName,
        callSites.joinToString("\n") { site ->
            listOfNotNull(site.fileName, site.className, site.methodName, site.lineNumber?.toString()).joinToString(":")
        },
    ).joinToString("\n").lowercase()

    fun containsAll(values: List<String>): Boolean =
        values.all { value -> searchableText.contains(value.lowercase()) }

    fun toJson() = buildJsonObject {
        put("id", id)
        put("timestampEpochMillis", timestampEpochMillis)
        put("level", level.value)
        put("source", source.value)
        if (stream == null) {
            put("stream", JsonNull)
        } else {
            put("stream", stream.value)
        }
        if (tag == null) {
            put("tag", JsonNull)
        } else {
            put("tag", tag)
        }
        put("message", message)
        if (throwable == null) {
            put("throwable", JsonNull)
        } else {
            put("throwable", throwable)
        }
        put("threadName", threadName)
        val location = callSites.firstOrNull()
        if (location == null) {
            put("location", JsonNull)
        } else {
            put("location", location.toJson())
        }
        put("callSites", buildJsonArray { callSites.forEach { add(it.toJson()) } })
    }
}

private data class LogFilter(
    val contains: List<String> = emptyList(),
    val levels: Set<String> = emptySet(),
    val sources: Set<String> = emptySet(),
) {
    fun matches(entry: LogEntry): Boolean {
        if (contains.isNotEmpty() && !entry.containsAll(contains)) return false
        if (levels.isNotEmpty() && entry.level.value !in levels) return false
        if (sources.isNotEmpty() && entry.source.value !in sources) return false
        return true
    }

    companion object {
        fun fromJson(json: JsonObject?): LogFilter {
            if (json == null) return LogFilter()
            return LogFilter(
                contains = json.stringList("contains"),
                levels = json.stringList("levels").toSet(),
                sources = json.stringList("sources").toSet(),
            )
        }
    }
}

private enum class LogLevel(val value: String) {
    Verbose("verbose"),
    Debug("debug"),
    Info("info"),
    Warning("warning"),
    Error("error"),
    Assert("assert"),
}

private enum class LogSource(val value: String) {
    AndroidLog("androidLog"),
    SystemPrint("systemPrint"),
}

private enum class LogStream(val value: String) {
    Stdout("stdout"),
    Stderr("stderr"),
}

private fun JsonObject.stringList(key: String): List<String> =
    this[key]
        ?.jsonArray
        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
        .orEmpty()
