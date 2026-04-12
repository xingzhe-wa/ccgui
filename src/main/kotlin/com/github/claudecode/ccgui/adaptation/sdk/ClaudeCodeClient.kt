package com.github.claudecode.ccgui.adaptation.sdk

import com.github.claudecode.ccgui.adaptation.sdk.SdkMessageTypes.SdkInitMessage
import com.github.claudecode.ccgui.adaptation.sdk.SdkMessageTypes.SdkAssistantMessage
import com.github.claudecode.ccgui.adaptation.sdk.SdkMessageTypes.SdkResultMessage
import com.github.claudecode.ccgui.adaptation.sdk.SdkMessageTypes.SdkMessage
import com.github.claudecode.ccgui.application.config.ConfigService
import com.github.claudecode.ccgui.infrastructure.eventbus.EventBus
import com.github.claudecode.ccgui.infrastructure.eventbus.SdkSessionInitEvent
import com.github.claudecode.ccgui.util.JsonUtils
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Claude Code SDK 核心客户端
 *
 * 通过ProcessBuilder启动 `claude` CLI子进程，使用 stream-json 协议通信。
 * 这是整个插件与Claude Code交互的唯一入口。
 */
@Service(Service.Level.PROJECT)
class ClaudeCodeClient(private val project: Project) : Disposable {

    private val log = logger<ClaudeCodeClient>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val parser = StreamJsonParser()
    private val configBuilder = SdkConfigBuilder(project)

    /** 活跃的CLI进程: sessionId -> CliSession */
    private val sessions = ConcurrentHashMap<String, CliSession>()

    /** 客户端状态 */
    private val _state = MutableStateFlow(ClientState.IDLE)
    val state: StateFlow<ClientState> = _state.asStateFlow()

    /** 当前会话ID (由SDK init消息返回) */
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    /** 请求锁：同一时间只允许一个CLI请求 */
    private val requestMutex = Mutex()

    enum class ClientState {
        IDLE,           // 空闲
        STARTING,       // CLI进程启动中
        STREAMING,      // 流式输出中
        WAITING_INPUT,  // 等待用户输入（权限请求等）
        ERROR           // 错误状态
    }

    /**
     * CLI会话封装
     */
    data class CliSession(
        val processId: String,
        val process: Process,
        val sdkSessionId: String?,  // SDK返回的session_id（用于resume）
        val reader: BufferedReader,
        val scope: CoroutineScope,
        val isAlive: AtomicBoolean = AtomicBoolean(true)
    )

    // ---- 消息回调接口 ----

    /**
     * SDK事件监听器
     * 上层（ChatOrchestrator/StreamingOutputEngine）通过此接口接收SDK事件
     */
    interface SdkEventListener {
        /** 收到init消息 */
        fun onInit(message: SdkInitMessage) {}
        /** 收到assistant消息（流式） */
        fun onAssistant(message: SdkAssistantMessage) {}
        /** 收到result消息（完成） */
        fun onResult(message: SdkResultMessage) {}
        /** 收到错误 */
        fun onError(error: String) {}
        /** 流式文本增量 — 用于直接推送到前端 */
        fun onTextDelta(text: String) {}
        /** 工具使用通知 */
        fun onToolUse(toolName: String, input: com.google.gson.JsonObject) {}
    }

    // ---- 核心API ----

    /**
     * 发送消息并接收流式回复
     */
    suspend fun sendMessage(
        prompt: String,
        options: SdkOptions = SdkOptions(),
        listener: SdkEventListener? = null
    ): Result<SdkResultMessage> = requestMutex.withLock {
        if (_state.value == ClientState.STREAMING) {
            return Result.failure(IllegalStateException("Another request is in progress"))
        }

        _state.value = ClientState.STARTING

        var process: Process? = null
        try {
            // 0. 读取活跃的模型配置（用于环境变量和模型）
            val modelConfig = ConfigService.getInstance(project).getActiveModelConfig()

            // 如果禁用，直接返回错误
            if (modelConfig.provider == "disabled" || modelConfig.model.isBlank()) {
                val errorMsg = "No active provider configured. Please set up your API key in settings."
                listener?.onError(errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
            }

            // 填充 SdkOptions 中未设置的字段（使用配置中的值）
            val effectiveOptions = if (options.model.isNullOrBlank()) {
                options.copy(model = modelConfig.model)
            } else options

            // 1. 构建CLI命令
            val command = configBuilder.buildCommand(prompt, effectiveOptions)
            log.info("Starting Claude CLI: ${command.joinToString(" ").take(200)}")

            // 2. 写入临时MCP配置文件（如有）
            val mcpConfigFile = options.mcpConfig?.let {
                configBuilder.writeMcpConfigTempFile(it)
            }

            val finalCommand = if (mcpConfigFile != null) {
                command + listOf("--mcp-config", mcpConfigFile.absolutePath)
            } else command

            // 3. 启动子进程
            val pb = ProcessBuilder(finalCommand)
                .redirectErrorStream(false)
                .directory(project.basePath?.let { File(it) })

            // 4. 设置环境变量（优先使用 options 中的值，其次使用配置中的值）
            // 从配置中获取环境变量
            val configEnv = mutableMapOf<String, String>()
            modelConfig.apiKey?.let { configEnv["ANTHROPIC_AUTH_TOKEN"] = it }
            modelConfig.baseUrl?.let { configEnv["ANTHROPIC_BASE_URL"] = it }

            // 合并：options.env 覆盖 configEnv
            (configEnv + options.env).forEach { (k, v) -> pb.environment()[k] = v }

            log.info("Using API config: provider=${modelConfig.provider}, model=${modelConfig.model}, " +
                    "hasApiKey=${modelConfig.apiKey != null}, hasBaseUrl=${modelConfig.baseUrl != null}")

            process = pb.start()
            _state.value = ClientState.STREAMING

            // 4. 读取并解析stdout
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var resultMessage: SdkResultMessage? = null
            var sdkSessionId: String? = null

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parsed = parser.parseLine(line!!)
                if (parsed == null) continue

                when (parsed) {
                    is SdkInitMessage -> {
                        sdkSessionId = parsed.sessionId
                        _currentSessionId.value = parsed.sessionId
                        sessions[parsed.sessionId] = CliSession(
                            processId = parsed.sessionId,
                            process = process,
                            sdkSessionId = parsed.sessionId,
                            reader = reader,
                            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                        )
                        listener?.onInit(parsed)
                        EventBus.publish(SdkSessionInitEvent(parsed.sessionId, parsed.tools), project)
                    }
                    is SdkAssistantMessage -> {
                        listener?.onAssistant(parsed)
                        val text = parsed.extractText()
                        if (text.isNotEmpty()) {
                            listener?.onTextDelta(text)
                        }
                        parsed.extractToolUses().forEach { toolUse ->
                            listener?.onToolUse(toolUse.name, toolUse.input)
                        }
                    }
                    is SdkResultMessage -> {
                        resultMessage = parsed
                        listener?.onResult(parsed)
                    }
                    else -> {
                        log.debug("Unhandled SDK message type: ${parsed::class.simpleName}")
                    }
                }
            }

            // 5. 读取stderr（用于调试）
            val stderr = process.errorStream.bufferedReader().readText()
            if (stderr.isNotBlank()) {
                log.debug("CLI stderr: ${stderr.take(500)}")
            }

            // 6. 等待进程结束
            // 不设置超时限制，让模型有足够时间完成推理
            process.waitFor()

            // 7. 更新SDK会话使用信息
            sdkSessionId?.let { sid ->
                SdkSessionService.getInstance(project).updateSessionUsage(
                    sid, costUsd = resultMessage?.costUsd, messageCount = 1
                )
            }

            val finalState = if (resultMessage?.isError == true) ClientState.ERROR else ClientState.IDLE
            _state.value = finalState

            if (resultMessage != null) {
                log.info("CLI completed: subtype=${resultMessage.subtype}, " +
                    "cost=${resultMessage.costUsd}, duration=${resultMessage.durationMs}ms")
                Result.success(resultMessage)
            } else {
                log.warn("CLI completed without result message")
                Result.failure(RuntimeException("CLI completed without result message"))
            }

        } catch (e: CancellationException) {
            _state.value = ClientState.IDLE
            throw e
        } catch (e: Exception) {
            _state.value = ClientState.ERROR
            log.error("ClaudeCodeClient.sendMessage failed", e)
            listener?.onError(e.message ?: "Unknown error")
            Result.failure(e)
        } finally {
            _currentSessionId.value?.let { sessions.remove(it) }
            process?.destroyForcibly()
        }
    }

    /**
     * 流式发送消息（返回Flow）
     */
    fun streamMessage(
        prompt: String,
        options: SdkOptions = SdkOptions()
    ): kotlinx.coroutines.flow.Flow<SdkMessage> = kotlinx.coroutines.flow.flow {
        val command = configBuilder.buildCommand(prompt, options)
        log.info("Streaming Claude CLI: ${command.joinToString(" ").take(200)}")

        val mcpConfigFile = options.mcpConfig?.let {
            configBuilder.writeMcpConfigTempFile(it)
        }
        val finalCommand = if (mcpConfigFile != null) {
            command + listOf("--mcp-config", mcpConfigFile.absolutePath)
        } else command

        val pb = ProcessBuilder(finalCommand)
            .redirectErrorStream(false)
            .directory(project.basePath?.let { File(it) })
        options.env.forEach { (k, v) -> pb.environment()[k] = v }

        val process = pb.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parsed = parser.parseLine(line!!)
                if (parsed != null) {
                    emit(parsed)
                }
            }
            // 不设置超时限制，让模型有足够时间完成推理
            process.waitFor()
        } finally {
            process.destroyForcibly()
        }
    }

    /**
     * 取消当前请求
     */
    fun cancelCurrentRequest() {
        sessions.values.forEach { session ->
            if (session.isAlive.get()) {
                session.process.destroyForcibly()
                session.isAlive.set(false)
            }
        }
        _state.value = ClientState.IDLE
        log.info("Current request cancelled")
    }

    /**
     * 检查CLI是否可用
     * 添加超时限制防止永久挂起
     */
    fun isCliAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("claude", "--version")
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return false
            }
            process.exitValue() == 0
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取CLI版本信息
     * 添加超时限制防止永久挂起
     */
    fun getCliVersion(): String? {
        return try {
            val process = ProcessBuilder("claude", "--version")
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return null
            }
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            output
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (e: Exception) {
            null
        }
    }

    override fun dispose() {
        scope.cancel()
        sessions.values.forEach { session ->
            session.process.destroyForcibly()
        }
        sessions.clear()
        _state.value = ClientState.IDLE
    }

    companion object {
        fun getInstance(project: Project): ClaudeCodeClient =
            project.getService(ClaudeCodeClient::class.java)
    }
}
