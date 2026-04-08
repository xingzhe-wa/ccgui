package com.github.xingzhewa.ccgui.application.orchestrator

import com.github.xingzhewa.ccgui.adaptation.sdk.ClaudeCodeClient
import com.github.xingzhewa.ccgui.adaptation.sdk.SdkOptions
import com.github.xingzhewa.ccgui.adaptation.sdk.SdkSessionManager
import com.github.xingzhewa.ccgui.application.session.SessionManager
import com.github.xingzhewa.ccgui.application.streaming.StreamingOutputEngine
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.MessageAddedEvent
import com.github.xingzhewa.ccgui.infrastructure.eventbus.SessionSwitchedEvent
import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.message.MessageRole
import com.github.xingzhewa.ccgui.model.message.MessageStatus
import com.github.xingzhewa.ccgui.util.logger
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
 * 核心编排中枢，协调 SessionManager、ClaudeCodeClient、StreamingOutputEngine
 */
@Service(Service.Level.PROJECT)
class ChatOrchestrator(private val project: Project) : Disposable {

    private val log = logger<ChatOrchestrator>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val claudeClient: ClaudeCodeClient by lazy { ClaudeCodeClient.getInstance(project) }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(project) }
    private val streamingEngine: StreamingOutputEngine by lazy { StreamingOutputEngine.getInstance(project) }
    private val sdkSessionManager: SdkSessionManager by lazy { SdkSessionManager.getInstance(project) }

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
     * 发送消息（流式）
     */
    suspend fun sendMessage(content: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (_isProcessing.value) {
            return@withContext Result.failure(IllegalStateException("Already processing a message"))
        }

        _isProcessing.value = true

        try {
            val session = sessionManager.getCurrentSession()
                ?: return@withContext Result.failure(IllegalStateException("No active session"))

            // 1. 添加用户消息
            val userMessage = ChatMessage.userMessage(content)
            sessionManager.addMessage(session.id, userMessage)
            EventBus.publish(MessageAddedEvent(session.id, userMessage.id, userMessage.role.name))

            // 2. 创建助手消息占位
            val assistantMessage = ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.STREAMING
            )
            _currentMessage.value = assistantMessage

            // 3. 构建 SDK 选项
            val sdkOptions = sdkSessionManager.buildResumeOptions(session.id, SdkOptions(
                systemPrompt = buildSystemPrompt(session.context)
            ))

            // 4. 发送消息并处理流式响应
            var fullContent = ""
            claudeClient.sendMessage(content, sdkOptions, object : ClaudeCodeClient.SdkEventListener {
                override fun onTextDelta(text: String) {
                    fullContent += text
                    _currentMessage.value = assistantMessage.copy(content = fullContent)
                    streamingEngine.appendChunk(text)
                }

                override fun onResult(result: com.github.xingzhewa.ccgui.adaptation.sdk.SdkMessageTypes.SdkResultMessage) {
                    _currentMessage.value = null
                }

                override fun onError(error: String) {
                    log.error("SDK error: $error")
                    _currentMessage.value = assistantMessage.copy(
                        content = "Error: $error",
                        status = MessageStatus.FAILED
                    )
                }
            })

            // 5. 保存助手消息
            val finalMessage = assistantMessage.copy(
                content = fullContent,
                status = MessageStatus.COMPLETED
            )
            sessionManager.addMessage(session.id, finalMessage)
            EventBus.publish(MessageAddedEvent(session.id, finalMessage.id, finalMessage.role.name))

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Failed to send message", e)
            Result.failure(e)
        } finally {
            _isProcessing.value = false
            streamingEngine.finishStreaming()
        }
    }

    /**
     * 取消当前消息
     */
    fun cancelCurrentMessage() {
        if (_isProcessing.value) {
            claudeClient.cancelCurrentRequest()
            _isProcessing.value = false
            _currentMessage.value = null
            streamingEngine.cancelStreaming()
            log.info("Current message cancelled")
        }
    }

    /**
     * 构建系统提示
     */
    private fun buildSystemPrompt(context: com.github.xingzhewa.ccgui.model.session.SessionContext): String {
        return buildString {
            append("You are Claude, an AI assistant integrated into JetBrains IDE via ClaudeCodeJet plugin. ")
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
