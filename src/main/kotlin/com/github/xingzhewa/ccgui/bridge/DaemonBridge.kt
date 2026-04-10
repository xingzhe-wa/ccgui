package com.github.xingzhewa.ccgui.bridge

import com.github.xingzhewa.ccgui.util.logger
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * DaemonBridge - 进程桥接器
 *
 * 负责：
 * 1. 管理 daemon.js 进程生命周期
 * 2. stdin/stdout NDJSON 通信
 * 3. 心跳检测与自动重启
 * 4. 请求/响应路由
 *
 * 架构变更 (v3.2):
 * - 原 AgentSdkBridge + HTTP + SSE 方案已废弃
 * - 采用 DaemonBridge + stdin/stdout + NDJSON 方案
 */
class DaemonBridge(private val project: Project) : Disposable {

    private val log = logger<DaemonBridge>()

    /** Node.js 进程 */
    private var daemonProcess: Process? = null

    /** stdin writer */
    private var daemonStdin: BufferedWriter? = null

    /** stdout reader 线程 */
    private var readerThread: Thread? = null

    /** 心跳线程 */
    private var heartbeatThread: Thread? = null

    /** 运行状态标志 */
    private val isRunning = AtomicBoolean(false)

    /** Daemon 就绪标志 */
    private val daemonReady = AtomicBoolean(false)

    /** 请求 ID 计数器 */
    private val requestIdCounter = AtomicInteger(1)

    /** 待处理请求映射 */
    private val pendingRequests = ConcurrentHashMap<String, RequestHandler>()

    /** 上次心跳响应时间 */
    private val lastHeartbeatResponse = AtomicLong(0)

    /** 重启次数 */
    private val restartAttempts = AtomicInteger(0)

    /** JSON 解析 */
    private val gson = Gson()

    /** 协程作用域 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    enum class DaemonState {
        IDLE,           // 未启动
        STARTING,       // 启动中
        READY,          // 就绪
        STREAMING,      // 流式输出中
        ERROR           // 错误
    }

    private val _connectionState = MutableStateFlow(DaemonState.IDLE)
    val connectionState: StateFlow<DaemonState> = _connectionState.asStateFlow()

    /**
     * 启动 daemon 进程
     */
    fun start(): Boolean {
        if (_connectionState.value == DaemonState.READY) {
            return true
        }

        _connectionState.value = DaemonState.STARTING

        try {
            // 1. 找到 daemon.js 路径
            val bridgeDir = getBridgeDir()
            val daemonScript = java.io.File(bridgeDir, "daemon.js")

            if (!daemonScript.exists()) {
                log.error("daemon.js not found at: ${daemonScript.absolutePath}")
                _connectionState.value = DaemonState.ERROR
                return false
            }

            // 2. 找到 Node.js 可执行文件
            val nodePath = findNodeExecutable()
            if (nodePath == null) {
                log.error("Node.js not found")
                _connectionState.value = DaemonState.ERROR
                return false
            }

            log.info("Starting daemon: $nodePath ${daemonScript.absolutePath}")

            // 3. 构造进程
            val pb = ProcessBuilder(nodePath, daemonScript.absolutePath)
            pb.directory(java.io.File(bridgeDir))
            pb.redirectErrorStream(false)

            // 4. 启动进程
            daemonProcess = pb.start()
            isRunning.set(true)

            // 5. 获取 stdin writer
            daemonStdin = BufferedWriter(
                OutputStreamWriter(daemonProcess!!.outputStream, StandardCharsets.UTF_8)
            )

            // 6. 启动 stdout 读取线程
            startReaderThread()

            // 7. 启动 stderr 读取线程
            startStderrReaderThread()

            // 8. 等待 "ready" 事件
            val ready = awaitReady(30_000)
            if (!ready) {
                log.error("Daemon failed to become ready within timeout")
                _connectionState.value = DaemonState.ERROR
                return false
            }

            // 9. 启动心跳线程
            startHeartbeatThread()

            _connectionState.value = DaemonState.READY
            log.info("Daemon started successfully")
            return true

        } catch (e: Exception) {
            log.error("Failed to start daemon", e)
            _connectionState.value = DaemonState.ERROR
            return false
        }
    }

    /**
     * 获取 Bridge 目录
     */
    private fun getBridgeDir(): String {
        // daemon.js 位于项目根目录的 daemon/dist 下
        val projectBase = project.basePath ?: ""
        return "$projectBase/daemon/dist"
    }

    /**
     * 查找 Node.js 可执行文件
     */
    private fun findNodeExecutable(): String? {
        // Windows 优先级路径
        val windowsPaths = listOf(
            "${System.getenv("ProgramFiles")}\\nodejs\\node.exe",
            "${System.getenv("ProgramFiles(x86)")}\\nodejs\\node.exe",
            "${System.getProperty("user.home")}\\AppData\\Roaming\\npm\\node.exe",
            "node"  // PATH 中的 node
        )

        // Unix 常见路径
        val unixPaths = listOf(
            "/usr/local/bin/node",
            "/usr/bin/node",
            "/opt/homebrew/bin/node"
        )

        val paths = if (System.getProperty("os.name").lowercase().contains("windows")) {
            windowsPaths
        } else {
            unixPaths
        }

        for (path in paths) {
            if (path == "node") {
                // 检查 PATH 中是否有 node
                try {
                    val result = ProcessBuilder("node", "--version").start()
                    if (result.waitFor() == 0) {
                        return "node"
                    }
                } catch (e: Exception) {
                    // continue
                }
            } else if (java.io.File(path).exists()) {
                return path
            }
        }

        return null
    }

    /**
     * 等待 Daemon 就绪
     */
    private fun awaitReady(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (daemonReady.get()) return true
            Thread.sleep(100)
        }
        return false
    }

    /**
     * 启动 stdout 读取线程
     */
    private fun startReaderThread() {
        readerThread = Thread({
            try {
                BufferedReader(InputStreamReader(daemonProcess!!.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    var line: String? = reader.readLine()
                    while (isRunning.get() && line != null) {
                        handleLine(line)
                        line = reader.readLine()
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    log.error("Reader error", e)
                }
            }
            if (isRunning.get()) {
                handleDaemonDeath()
            }
        }, "DaemonBridge-Reader")
        readerThread!!.isDaemon = true
        readerThread!!.start()
    }

    /**
     * 启动 stderr 读取线程（仅调试）
     */
    private fun startStderrReaderThread() {
        Thread({
            try {
                BufferedReader(InputStreamReader(daemonProcess!!.errorStream, StandardCharsets.UTF_8)).use { reader ->
                    var line: String? = reader.readLine()
                    while (isRunning.get() && line != null) {
                        log.debug("[Daemon stderr] $line")
                        line = reader.readLine()
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }, "DaemonBridge-Stderr").start()
    }

    /**
     * 启动心跳线程
     */
    private fun startHeartbeatThread() {
        lastHeartbeatResponse.set(System.currentTimeMillis())

        heartbeatThread = Thread({
            while (isRunning.get()) {
                try {
                    Thread.sleep(15_000)  // 15秒

                    if (!isRunning.get()) break

                    // 发送心跳
                    val heartbeatId = "hb-${System.currentTimeMillis()}"
                    val heartbeatRequest = JsonObject().apply {
                        addProperty("id", heartbeatId)
                        addProperty("method", "heartbeat")
                    }

                    synchronized(daemonStdin!!) {
                        daemonStdin!!.write(heartbeatRequest.toString())
                        daemonStdin!!.newLine()
                        daemonStdin!!.flush()
                    }

                    // 检查超时
                    val heartbeatAge = System.currentTimeMillis() - lastHeartbeatResponse.get()
                    if (heartbeatAge > 45_000) {
                        log.warn("Heartbeat timeout (${heartbeatAge}ms), restarting daemon...")
                        handleDaemonDeath()
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        log.error("Heartbeat error", e)
                    }
                }
            }
        }, "DaemonBridge-Heartbeat")
        heartbeatThread!!.isDaemon = true
        heartbeatThread!!.start()
    }

    /**
     * 发送命令（NDJSON 格式）
     */
    fun sendCommand(
        method: String,
        params: JsonObject,
        callback: DaemonOutputCallback
    ): CompletableFuture<Boolean> {
        // 1. 确保进程存活
        if (!isRunning.get()) {
            return CompletableFuture.failedFuture(IOException("Daemon not running"))
        }

        // 2. 生成请求 ID
        val requestId = requestIdCounter.incrementAndGet().toString()

        // 3. 创建 Future + Handler
        val future = CompletableFuture<Boolean>()
        val handler = RequestHandler(callback, future)
        pendingRequests[requestId] = handler

        // 4. 构造 NDJSON 请求
        val request = JsonObject().apply {
            addProperty("id", requestId)
            addProperty("method", method)
            add("params", params)
        }

        // 5. 写入 stdin（同步锁保证原子性）
        try {
            synchronized(daemonStdin!!) {
                daemonStdin!!.write(request.toString())
                daemonStdin!!.newLine()
                daemonStdin!!.flush()
            }
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            return CompletableFuture.failedFuture(e)
        }

        return future
    }

    /**
     * 发送消息（流式）
     */
    fun sendMessage(
        content: String,
        sessionId: String? = null,
        cwd: String? = null,
        model: String? = null
    ): CompletableFuture<Boolean> {
        val params = JsonObject().apply {
            addProperty("message", content)
            sessionId?.let { addProperty("sessionId", it) }
            cwd?.let { addProperty("cwd", it) }
            model?.let { addProperty("model", it) }
        }

        return sendCommand("claude.send", params, object : DaemonOutputCallback {
            override fun onLine(tag: String, content: String?) {
                // 标签解析在外部处理
            }

            override fun onComplete(success: Boolean, error: String?) {
                // 完成处理在外部处理
            }
        })
    }

    /**
     * 发送关闭命令
     */
    fun shutdown() {
        if (!isRunning.get()) return

        try {
            val request = JsonObject().apply {
                addProperty("id", "shutdown-${System.currentTimeMillis()}")
                addProperty("method", "shutdown")
            }
            synchronized(daemonStdin!!) {
                daemonStdin!!.write(request.toString())
                daemonStdin!!.newLine()
                daemonStdin!!.flush()
            }
        } catch (e: Exception) {
            log.error("Failed to send shutdown command", e)
        }
    }

    /**
     * 处理 Daemon 死亡
     */
    private fun handleDaemonDeath() {
        isRunning.set(false)
        daemonReady.set(false)

        // 杀掉残留进程
        daemonProcess?.destroyForcibly()

        // 所有 pending 请求标记失败
        pendingRequests.forEach { (_, handler) ->
            try {
                handler.callback.onComplete(false, "Daemon died")
                handler.future.complete(false)
            } catch (e: Exception) {
                // ignore
            }
        }
        pendingRequests.clear()

        // 重启计数
        val attempts = restartAttempts.incrementAndGet()
        if (attempts <= 3) {
            log.info("Auto-restarting daemon (attempt $attempts/3)")
            _connectionState.value = DaemonState.STARTING
            start()
        } else {
            log.error("Max restart attempts (3) reached, giving up")
            _connectionState.value = DaemonState.ERROR
        }
    }

    /**
     * 解析 NDJSON 行
     */
    private fun handleLine(jsonLine: String) {
        try {
            val obj = JsonParser.parseString(jsonLine).asJsonObject

            // Daemon 生命周期事件
            if (obj.has("type")) {
                val type = obj.get("type").asString
                if (type == "daemon") {
                    handleDaemonEvent(obj)
                    return
                }
            }

            val id = obj.get("id").asString

            // 心跳响应
            if (id.startsWith("hb-")) {
                lastHeartbeatResponse.set(System.currentTimeMillis())
                return
            }

            // 请求完成
            if (obj.has("done")) {
                val success = obj.get("success").asBoolean
                pendingRequests.remove(id)?.let { handler ->
                    try {
                        handler.callback.onComplete(success, null)
                        handler.future.complete(success)
                    } catch (e: Exception) {
                        log.warn("Error completing request $id", e)
                    }
                }
                return
            }

            // 输出行：解析标签
            if (obj.has("line")) {
                val tagLine = obj.get("line").asString
                pendingRequests[id]?.let { handler ->
                    parseAndDispatch(tagLine, handler.callback)
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to parse line: $jsonLine", e)
        }
    }

    /**
     * 处理 Daemon 事件
     */
    private fun handleDaemonEvent(obj: JsonObject) {
        val event = obj.get("event").asString
        when (event) {
            "starting" -> {
                log.info("Daemon starting, pid: ${obj.get("pid")}")
            }
            "sdk_loaded" -> {
                log.info("SDK loaded, provider: ${obj.get("provider")}")
            }
            "ready" -> {
                log.info("Daemon ready, pid: ${obj.get("pid")}")
                daemonReady.set(true)
                restartAttempts.set(0)
            }
            "shutting_down" -> {
                log.info("Daemon shutting down")
            }
            "log" -> {
                val message = obj.get("message")?.asString ?: ""
                log.debug("[Daemon log] $message")
            }
            "error" -> {
                val message = obj.get("message")?.asString ?: "Unknown error"
                log.error("[Daemon error] $message")
            }
        }
    }

    /**
     * 解析并分发标签
     */
    private fun parseAndDispatch(line: String, callback: DaemonOutputCallback) {
        when {
            line.startsWith("[MESSAGE_START]") -> callback.onLine("message_start", null)
            line.startsWith("[STREAM_START]") -> callback.onLine("stream_start", null)
            line.startsWith("[CONTENT_DELTA]") -> {
                val content = unescapeJsonString(line.substring(15))
                callback.onLine("content_delta", content)
            }
            line.startsWith("[THINKING_DELTA]") -> {
                val content = unescapeJsonString(line.substring(17))
                callback.onLine("thinking_delta", content)
            }
            line.startsWith("[TOOL_USE]") -> {
                val json = line.substring(10)
                callback.onLine("tool_use", json)
            }
            line.startsWith("[TOOL_RESULT]") -> {
                val json = line.substring(13)
                callback.onLine("tool_result", json)
            }
            line.startsWith("[SESSION_ID]") -> {
                val sessionId = line.substring(12).trim()
                callback.onLine("session_id", sessionId)
            }
            line.startsWith("[USAGE]") -> {
                val json = line.substring(8)
                callback.onLine("usage", json)
            }
            line.startsWith("[MESSAGE_END]") -> callback.onLine("message_end", null)
            line.startsWith("[SEND_ERROR]") -> {
                val json = line.substring(13)
                callback.onLine("error", json)
            }
            line.startsWith("[MESSAGE]") -> {
                val json = line.substring(9)
                callback.onLine("message", json)
            }
        }
    }

    /**
     * 解析 JSON 字符串（去除转义）
     */
    private fun unescapeJsonString(s: String): String {
        return try {
            JsonParser.parseString(s.trim()).asString
        } catch (e: Exception) {
            s.trim()
        }
    }

    /**
     * 停止 daemon
     */
    fun stop() {
        isRunning.set(false)
        daemonReady.set(false)

        try {
            shutdown()
        } catch (e: Exception) {
            // ignore
        }

        daemonProcess?.destroyForcibly()
        daemonProcess = null
        daemonStdin = null

        _connectionState.value = DaemonState.IDLE
        log.info("Daemon stopped")
    }

    override fun dispose() {
        stop()
        scope.cancel()
    }

    // ---- 接口定义 ----

    interface DaemonOutputCallback {
        fun onLine(tag: String, content: String?)
        fun onComplete(success: Boolean, error: String?)
    }

    class RequestHandler(
        val callback: DaemonOutputCallback,
        val future: CompletableFuture<Boolean>
    )

    companion object {
        private const val MAX_RESTART_ATTEMPTS = 3
    }
}
