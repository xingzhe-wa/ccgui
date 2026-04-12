package com.github.claudecode.ccgui.application.orchestrator

import com.github.claudecode.ccgui.adaptation.sdk.ClaudeCodeClient
import com.github.claudecode.ccgui.adaptation.sdk.McpServersConfig
import com.github.claudecode.ccgui.adaptation.sdk.SdkOptions
import com.github.claudecode.ccgui.adaptation.sdk.SdkSessionService
import com.github.claudecode.ccgui.application.agent.AgentsManager
import com.github.claudecode.ccgui.application.chat.ChatConfigManager
import com.github.claudecode.ccgui.application.context.ContextManager
import com.github.claudecode.ccgui.application.mcp.McpServerManager
import com.github.claudecode.ccgui.application.session.SessionService
import com.github.claudecode.ccgui.application.streaming.StreamingOutputEngine
import com.github.claudecode.ccgui.bridge.StreamCallback
import com.github.claudecode.ccgui.infrastructure.eventbus.EventBus
import com.github.claudecode.ccgui.infrastructure.eventbus.MessageAddedEvent
import com.github.claudecode.ccgui.model.agent.Agent
import com.github.claudecode.ccgui.model.message.ChatMessage
import com.github.claudecode.ccgui.model.message.MessageRole
import com.github.claudecode.ccgui.model.message.MessageStatus
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 聊天编排器
 *
 * 核心编排中枢，协调 SessionService、ClaudeCodeClient、StreamingOutputEngine
 */
@Service(Service.Level.PROJECT)
class ChatOrchestrator(private val project: Project) : Disposable {

    private val log = logger<ChatOrchestrator>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val claudeClient: ClaudeCodeClient by lazy { ClaudeCodeClient.getInstance(project) }
    private val sessionManager: SessionService by lazy { SessionService.getInstance(project) }
    private val streamingEngine: StreamingOutputEngine by lazy { StreamingOutputEngine.getInstance(project) }
    private val sdkSessionService: SdkSessionService by lazy { SdkSessionService.getInstance(project) }
    private val contextManager: ContextManager by lazy { ContextManager.getInstance(project) }
    private val agentsManager: AgentsManager by lazy { AgentsManager.getInstance(project) }
    private val mcpServerManager: McpServerManager by lazy { McpServerManager.getInstance(project) }
    private val chatConfigManager: ChatConfigManager by lazy { ChatConfigManager.getInstance() }

    /** 是否正在处理消息 */
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    /** 当前消息 */
    private val _currentMessage = MutableStateFlow<ChatMessage?>(null)
    val currentMessage: StateFlow<ChatMessage?> = _currentMessage.asStateFlow()

    init {
        log.info("ChatOrchestrator initialized")
    }

    /**
     * 发送消息（流式，带回调）
     *
     * 核心消息发送方法，协调 SessionService、ClaudeClient、StreamingOutputEngine
     * 统一入口：前端和内部服务都应通过此方法发送消息
     *
     * @param content 消息内容
     * @param sessionId 会话ID（如果为空则使用当前会话）
     * @param callback StreamCallback 用于流式事件回调
     * @param onResponse 响应回调（可选）
     */
    fun sendMessage(
        content: String,
        sessionId: String,
        callback: StreamCallback,
        onResponse: ((result: Any?, error: String?) -> Unit)? = null
    ) {
        if (_isProcessing.value) {
            callback.onStreamError("Already processing a message")
            onResponse?.invoke(null, "Already processing a message")
            return
        }

        _isProcessing.value = true
        val effectiveSessionId = sessionId.ifEmpty { sessionManager.getCurrentSession()?.id ?: "" }
        if (effectiveSessionId.isEmpty()) {
            callback.onStreamError("No active session")
            onResponse?.invoke(null, "No active session")
            _isProcessing.value = false
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                // 0. 记录用户消息长度（用于上下文追踪）
                contextManager.recordUserMessage(effectiveSessionId, content)

                // 0.5. 检查上下文长度，必要时触发压缩
                if (contextManager.shouldCompact(effectiveSessionId)) {
                    log.info("ContextManager: Context threshold reached for session $effectiveSessionId, triggering compaction")
                    contextManager.compact(effectiveSessionId)
                }

                // 1. 添加用户消息
                val userMessage = ChatMessage.userMessage(content)
                sessionManager.addMessage(effectiveSessionId, userMessage)
                EventBus.publish(MessageAddedEvent(effectiveSessionId, userMessage.id, userMessage.role.name), project)

                // 2. 创建助手消息占位
                val assistantMessage = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = "",
                    status = MessageStatus.STREAMING
                )
                val messageId = assistantMessage.id
                _currentMessage.value = assistantMessage

                // 3. 构建 SDK 选项（包含 Agent 配置和 MCP 配置）
                val session = sessionManager.getSession(effectiveSessionId)
                val sdkOptions = session?.let { buildSdkOptions(it.id, it.context) }
                    ?: SdkOptions()

                callback.onStreamStart()

                // 4. 发送消息并处理流式响应
                var fullContent = ""
                val listener = object : ClaudeCodeClient.SdkEventListener {
                    override fun onTextDelta(text: String) {
                        fullContent += text
                        _currentMessage.value = assistantMessage.copy(content = fullContent)
                        streamingEngine.appendChunk(text, messageId)
                        callback.onLineReceived(text)
                    }

                    override fun onResult(result: com.github.claudecode.ccgui.adaptation.sdk.SdkMessageTypes.SdkResultMessage) {
                        _currentMessage.value = null
                        callback.onStreamComplete(emptyList())
                    }

                    override fun onError(error: String) {
                        log.error("SDK error: $error")
                        _currentMessage.value = assistantMessage.copy(
                            content = "Error: $error",
                            status = MessageStatus.FAILED
                        )
                        streamingEngine.setError(error, messageId)
                        callback.onStreamError(error)
                    }
                }

                val sendResult = claudeClient.sendMessage(content, sdkOptions, listener)

                // 5. 处理结果
                if (sendResult.isSuccess) {
                    // 记录助手消息长度（用于上下文追踪）
                    contextManager.recordAssistantMessage(effectiveSessionId, fullContent)

                    // 保存助手消息
                    val finalMessage = assistantMessage.copy(
                        content = fullContent,
                        status = MessageStatus.COMPLETED
                    )
                    sessionManager.addMessage(effectiveSessionId, finalMessage)
                    EventBus.publish(MessageAddedEvent(effectiveSessionId, finalMessage.id, finalMessage.role.name), project)

                    onResponse?.invoke(mapOf(
                        "messageId" to finalMessage.id,
                        "content" to fullContent
                    ), null)
                } else {
                    val error = sendResult.exceptionOrNull()?.message ?: "Unknown error"
                    onResponse?.invoke(null, error)
                }
            } catch (e: CancellationException) {
                callback.onStreamCancelled()
                throw e
            } catch (e: Exception) {
                log.error("Failed to send message", e)
                callback.onStreamError(e.message ?: "Unknown error")
                onResponse?.invoke(null, e.message ?: "Unknown error")
            } finally {
                _isProcessing.value = false
                val messageId = _currentMessage.value?.id ?: ""
                streamingEngine.finishStreaming(messageId)
            }
        }
    }

    /**
     * 发送消息（协程版本，供内部服务使用）
     *
     * @param content 消息内容
     * @return Result 处理结果
     */
    suspend fun sendMessage(content: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (_isProcessing.value) {
            return@withContext Result.failure(IllegalStateException("Already processing a message"))
        }

        val session = sessionManager.getCurrentSession()
            ?: return@withContext Result.failure(IllegalStateException("No active session"))

        sendMessage(content, session.id, object : StreamCallback {
            override fun onStreamStart() {}
            override fun onLineReceived(line: String) {}
            override fun onStreamComplete(messages: List<String>) {}
            override fun onStreamError(error: String) { log.error("Stream error: $error") }
            override fun onStreamCancelled() {}
        }, null)

        Result.success(Unit)
    }

    /**
     * 取消当前消息
     */
    fun cancelCurrentMessage() {
        if (_isProcessing.value) {
            claudeClient.cancelCurrentRequest()
            _isProcessing.value = false
            val messageId = _currentMessage.value?.id ?: ""
            _currentMessage.value = null
            streamingEngine.cancelStreaming(messageId)
            log.info("Current message cancelled")
        }
    }

    /**
     * 构建 SDK 选项（包含 Agent 配置和 MCP 配置）
     */
    private fun buildSdkOptions(sessionId: String, context: com.github.claudecode.ccgui.model.session.SessionContext): SdkOptions {
        // 获取基础系统提示
        val baseSystemPrompt = buildSystemPrompt(context)

        // 获取当前选中的 Agent
        val currentAgentId = chatConfigManager.getCurrentAgentId()
        val agent = currentAgentId?.let { agentsManager.getAgent(it) }

        // 合并 Agent 的系统提示
        val systemPrompt = if (agent != null && agent.systemPrompt.isNotBlank()) {
            buildString {
                append(baseSystemPrompt)
                append("\n\n--- Agent Configuration ---\n")
                append("Agent: ${agent.name}\n")
                append(agent.systemPrompt)
                if (agent.capabilities.isNotEmpty()) {
                    append("\nCapabilities: ${agent.capabilities.joinToString(", ") { it.name }}")
                }
            }
        } else {
            baseSystemPrompt
        }

        // 构建 MCP 配置
        val mcpConfig = buildMcpConfig()

        // 获取允许的工具列表
        val allowedTools = agent?.tools?.takeIf { it.isNotEmpty() }

        // 构建 SDK 选项
        return sdkSessionService.buildResumeOptions(sessionId, SdkOptions(
            systemPrompt = systemPrompt,
            mcpConfig = mcpConfig,
            allowedTools = allowedTools
        ))
    }

    /**
     * 构建 MCP 服务器配置
     */
    private fun buildMcpConfig(): McpServersConfig? {
        try {
            // 获取已启用且已连接的 MCP 服务器
            val enabledServers = mcpServerManager.getAllServers().filter { it.enabled && it.isConnected }
            if (enabledServers.isEmpty()) {
                return null
            }

            val servers = enabledServers.associate { server ->
                server.name to McpServersConfig.McpServerEntry(
                    command = server.command,
                    args = server.args,
                    env = server.env
                )
            }

            return McpServersConfig(servers)
        } catch (e: Exception) {
            log.warn("Failed to build MCP config: ${e.message}")
            return null
        }
    }

    /**
     * 构建系统提示
     */
    private fun buildSystemPrompt(context: com.github.claudecode.ccgui.model.session.SessionContext): String {
        return buildString {
            append("You are Claude, an AI assistant integrated into JetBrains IDE via CC Assistant plugin. ")
            append("You help developers with coding, debugging, refactoring, and other software engineering tasks. ")
            append("Provide clear, concise, and actionable responses. ")
            append("When generating code, always include the language identifier in code blocks. ")
            append("Project: ${project.name}")
        }
    }

    override fun dispose() {
        scope.cancel()
    }

    companion object {
        fun getInstance(project: Project): ChatOrchestrator =
            project.getService(ChatOrchestrator::class.java)
    }
}
