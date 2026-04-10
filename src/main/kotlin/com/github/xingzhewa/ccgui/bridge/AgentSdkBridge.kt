package com.github.xingzhewa.ccgui.bridge

import com.github.xingzhewa.ccgui.util.logger
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Agent SDK Bridge
 *
 * 负责与 daemon.js (Node.js) 进程的 HTTP 通信：
 * 1. 管理 daemon 进程生命周期
 * 2. 调用 daemon HTTP API
 * 3. 解析 SSE 流式响应
 */
class AgentSdkBridge(private val project: Project) : Disposable {

    private val log = logger<AgentSdkBridge>()

    /** HTTP 客户端 */
    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            gson()
        }
        engine {
            config {
                followRedirects(true)
                connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(0, java.util.concurrent.TimeUnit.SECONDS) // 无超时（流式）
            }
        }
    }

    /** Daemon 进程 */
    private var daemonProcess: Process? = null

    /** Daemon 端口 */
    private var daemonPort: Int = DEFAULT_PORT

    /** 当前会话 ID */
    private var currentSessionId: String? = null

    /** Daemon 进程工作目录 */
    private val daemonScriptPath: String
        get() = "${project.basePath ?: ""}/daemon/dist/index.js"

    /** 连接状态 */
    private val _connectionState = MutableStateFlow(DaemonState.IDLE)
    val connectionState: StateFlow<DaemonState> = _connectionState.asStateFlow()

    /** 协程作用域 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** JSON 解析 */
    private val gson = Gson()

    enum class DaemonState {
        IDLE,           // 未启动
        STARTING,        // 启动中
        READY,           // 就绪
        STREAMING,       // 流式输出中
        ERROR            // 错误
    }

    /**
     * SSE 事件类型
     */
    enum class SseEventType {
        MESSAGE_START,
        CONTENT_BLOCK_START,
        CONTENT_BLOCK_DELTA,
        CONTENT_BLOCK_STOP,
        MESSAGE_STOP,
        USAGE,
        ERROR
    }

    /**
     * SSE 事件数据类
     */
    data class SseEvent(
        val type: SseEventType,
        val data: Map<String, Any?>
    )

    init {
        log.info("AgentSdkBridge initialized")
    }

    /**
     * 启动 daemon 进程
     */
    suspend fun startDaemon(): Result<Unit> = withContext(Dispatchers.IO) {
        if (_connectionState.value == DaemonState.READY) {
            return@withContext Result.success(Unit)
        }

        _connectionState.value = DaemonState.STARTING

        try {
            // 1. 查找可用端口
            daemonPort = findAvailablePort()
            log.info("Starting daemon on port $daemonPort")

            // 2. 启动 Node.js 进程
            val processBuilder = ProcessBuilder(
                "node",
                daemonScriptPath,
                "--port", daemonPort.toString()
            )
                .directory(project.basePath?.let { java.io.File(it) })

            daemonProcess = processBuilder.start()
            log.info("Daemon process started with PID: ${daemonProcess?.pid()}")

            // 3. 等待 daemon 就绪
            val startTimeout = 10_000L // 10秒超时
            val deadline = System.currentTimeMillis() + startTimeout

            while (System.currentTimeMillis() < deadline) {
                if (healthCheck()) {
                    _connectionState.value = DaemonState.READY
                    log.info("Daemon started successfully on port $daemonPort")
                    return@withContext Result.success(Unit)
                }
                delay(100)
            }

            _connectionState.value = DaemonState.ERROR
            Result.failure(DaemonStartTimeoutException("Daemon failed to start within $startTimeout ms"))

        } catch (e: Exception) {
            _connectionState.value = DaemonState.ERROR
            log.error("Failed to start daemon", e)
            Result.failure(e)
        }
    }

    /**
     * 查找可用端口
     */
    private fun findAvailablePort(): Int {
        return java.net.ServerSocket(0).use { socket ->
            socket.localPort
        }
    }

    /**
     * 健康检查
     */
    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get("http://localhost:$daemonPort/api/v1/health")
            return@withContext response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            return@withContext false
        }
    }

    /**
     * 启动新会话
     */
    suspend fun startSession(
        model: String = "claude-sonnet-4-20250514",
        systemPrompt: String? = null
    ): Result<SessionStartResult> = withContext(Dispatchers.IO) {
        try {
            // 确保 daemon 已启动
            if (_connectionState.value != DaemonState.READY) {
                startDaemon()
            }

            val requestBody = JsonObject().apply {
                addProperty("model", model)
                systemPrompt?.let { addProperty("systemPrompt", it) }
            }

            val response = httpClient.post("http://localhost:$daemonPort/api/v1/session/start") {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            if (response.status != HttpStatusCode.OK) {
                return@withContext Result.failure(Exception("Failed to start session: ${response.status}"))
            }

            val json = JsonParser.parseString(response.bodyAsText()).asJsonObject
            val sessionId = json.get("sessionId").asString

            currentSessionId = sessionId
            log.info("Session started: $sessionId")

            Result.success(SessionStartResult(
                sessionId = sessionId,
                status = json.get("status").asString,
                supportedTools = json.getAsJsonArray("supportedTools")?.map { it.asString } ?: emptyList()
            ))

        } catch (e: Exception) {
            log.error("Failed to start session", e)
            Result.failure(e)
        }
    }

    /**
     * 发送消息（流式）
     */
    fun streamMessage(
        content: String,
        attachments: List<Attachment> = emptyList()
    ): Flow<SseEvent> = flow {
        val sessionId = currentSessionId ?: run {
            val result = startSession()
            if (result.isFailure) {
                emit(SseEvent(SseEventType.ERROR, mapOf("message" to result.exceptionOrNull()?.message)))
                return@flow
            }
            result.getOrNull()?.sessionId
        }

        if (sessionId == null) {
            emit(SseEvent(SseEventType.ERROR, mapOf("message" to "Failed to get session ID")))
            return@flow
        }

        _connectionState.value = DaemonState.STREAMING

        try {
            // 构建请求体
            val requestBody = JsonObject().apply {
                addProperty("content", content)
                if (attachments.isNotEmpty()) {
                    val attachmentsArray = JsonArray()
                    attachments.forEach { att ->
                        attachmentsArray.add(JsonObject().apply {
                            addProperty("type", att.type)
                            att.name?.let { addProperty("name", it) }
                            addProperty("content", att.content)
                        })
                    }
                    add("attachments", attachmentsArray)
                }
            }

            // 发送请求（使用流式读取）
            val connection = URL("http://localhost:$daemonPort/api/v1/session/$sessionId/message")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.connectTimeout = 30000
            connection.readTimeout = 0

            // 写入请求体
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }

            // 读取响应流
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                var line: String?
                var currentEventType: String? = null

                while (reader.readLine().also { line = it } != null) {
                    when {
                        line!!.startsWith("event: ") -> {
                            currentEventType = line!!.removePrefix("event: ").trim()
                        }
                        line!!.startsWith("data: ") -> {
                            val dataStr = line!!.removePrefix("data: ").trim()
                            if (dataStr.isNotEmpty() && currentEventType != null) {
                                try {
                                    val data = JsonParser.parseString(dataStr).asJsonObject
                                    val eventType = parseEventType(currentEventType!!)
                                    if (eventType != null) {
                                        emit(SseEvent(eventType, jsonObjectToMap(data)))
                                    }
                                } catch (e: Exception) {
                                    log.warn("Failed to parse SSE data: $dataStr", e)
                                }
                            }
                        }
                        line!!.isEmpty() -> {
                            // 空行，重置事件类型
                            currentEventType = null
                        }
                    }
                }
            }

            _connectionState.value = DaemonState.READY

        } catch (e: Exception) {
            _connectionState.value = DaemonState.ERROR
            log.error("Stream message failed", e)
            emit(SseEvent(SseEventType.ERROR, mapOf("message" to (e.message ?: "Unknown error"))))
        }
    }

    /**
     * 解析 SSE 事件类型
     */
    private fun parseEventType(type: String): SseEventType? {
        return when (type) {
            "message_start" -> SseEventType.MESSAGE_START
            "content_block_start" -> SseEventType.CONTENT_BLOCK_START
            "content_block_delta" -> SseEventType.CONTENT_BLOCK_DELTA
            "content_block_stop" -> SseEventType.CONTENT_BLOCK_STOP
            "message_stop" -> SseEventType.MESSAGE_STOP
            "usage" -> SseEventType.USAGE
            "error" -> SseEventType.ERROR
            else -> null
        }
    }

    /**
     * 将 JsonObject 转换为 Map
     */
    private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> {
        return obj.entrySet().associate { (key, value) ->
            key to when {
                value.isJsonNull -> null
                value.isJsonPrimitive -> value.asJsonPrimitive.let { prim ->
                    when {
                        prim.isBoolean -> prim.asBoolean
                        prim.isNumber -> prim.asNumber
                        else -> prim.asString
                    }
                }
                value.isJsonArray -> value.asJsonArray.map { it.asString }
                value.isJsonObject -> jsonObjectToMap(value.asJsonObject)
                else -> value.toString()
            }
        }
    }

    /**
     * 取消当前请求
     */
    suspend fun cancelCurrentRequest(): Result<Unit> = withContext(Dispatchers.IO) {
        val sessionId = currentSessionId
        if (sessionId == null) {
            return@withContext Result.success(Unit)
        }

        try {
            httpClient.post("http://localhost:$daemonPort/api/v1/session/$sessionId/cancel")
            _connectionState.value = DaemonState.READY
            Result.success(Unit)
        } catch (e: Exception) {
            log.error("Failed to cancel request", e)
            Result.failure(e)
        }
    }

    /**
     * 结束会话
     */
    suspend fun endSession(): Result<Unit> = withContext(Dispatchers.IO) {
        val sessionId = currentSessionId
        if (sessionId == null) {
            return@withContext Result.success(Unit)
        }

        try {
            httpClient.post("http://localhost:$daemonPort/api/v1/session/$sessionId/end")
            currentSessionId = null
            _connectionState.value = DaemonState.IDLE
            Result.success(Unit)
        } catch (e: Exception) {
            log.error("Failed to end session", e)
            Result.failure(e)
        }
    }

    /**
     * 停止 daemon 进程
     */
    fun stopDaemon() {
        daemonProcess?.destroyForcibly()
        daemonProcess = null
        currentSessionId = null
        _connectionState.value = DaemonState.IDLE
        log.info("Daemon stopped")
    }

    override fun dispose() {
        scope.cancel()
        stopDaemon()
        httpClient.close()
        log.info("AgentSdkBridge disposed")
    }

    // ---- 数据类 ----

    data class SessionStartResult(
        val sessionId: String,
        val status: String,
        val supportedTools: List<String>
    )

    data class Attachment(
        val type: String,  // "text", "image", "file"
        val name: String? = null,
        val content: String
    )

    // ---- 异常类 ----

    class DaemonStartTimeoutException(message: String) : Exception(message)

    companion object {
        private const val DEFAULT_PORT = 9229
    }
}
