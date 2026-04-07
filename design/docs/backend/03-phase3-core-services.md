# Phase 3: 核心应用服务 (Core Services)

**优先级**: P0
**预估工期**: 12人天
**前置依赖**: Phase 1 + Phase 2
**阶段目标**: 实现完整的聊天核心链路——会话管理、聊天编排、流式输出、配置管理和上下文提供

---

> **⚠️ SDK集成架构修订 (2026-04-08)**
>
> 本阶段文档基于旧的直接HTTP API方案编写，需进行以下关键适配。标注 ★ 的为必须修改项。
>
> ### 必须修改的组件
>
> | 组件 | 原方案（错误） | SDK适配方案 | 详见 |
> |------|---------------|-------------|------|
> | ★ `ChatOrchestrator` | 调用 `MultiProviderAdapter.chat()` | 调用 `ClaudeCodeClient.sendMessage()` | [Phase 2.5 Section 5](02-phase2.5-claude-code-sdk.md) |
> | ★ `ChatOrchestrator.streamMessage()` | 返回 `Flow<ChatChunk>` 来自 HTTP SSE | 返回 `Flow<ChatChunk>` 来自 CLI stream-json | 同上 |
> | ★ `ModelSwitcher` | 调用 `providerAdapter.getProvider()` | 调用 `ModelInfoRegistry.getModel()` | [Phase 2.5 Section 3.3](02-phase2.5-claude-code-sdk.md) |
> | ★ `StreamingOutputEngine` | 接收 `String` chunk | 需兼容 `SdkAssistantMessage` 和 `SdkResultMessage` | [Phase 2.5 Section 9](02-phase2.5-claude-code-sdk.md) |
>
> ### 无需修改的组件
>
> | 组件 | 说明 |
> |------|------|
> | `SessionManager` | 会话管理逻辑不变，仅 SessionStorage 持久化方式不变 |
> | `SessionInterruptRecovery` | 中断恢复逻辑不变 |
> | `ConfigManager` | 配置管理不变，但 ModelConfig.apiKey 字段实际由 SDK 管理 |
> | `ConfigHotReloadManager` | 热更新机制不变 |
> | `ThemeManager` | 主题管理不变 |
> | `ContextProvider` | 上下文收集不变，结果传给 `SdkOptions.systemPrompt` |
> | `ConversationModeManager` | 模式管理不变，不同模式对应不同 system-prompt |
>
> ### ChatOrchestrator 核心修改指引
>
> ```kotlin
> // 旧代码（删除）:
> private val providerAdapter: MultiProviderAdapter by lazy { MultiProviderAdapter.getInstance() }
>
> // 新代码（替换）:
> private val claudeClient: ClaudeCodeClient by lazy { ClaudeCodeClient.getInstance(project) }
> private val sdkSessionManager: SdkSessionManager by lazy { SdkSessionManager.getInstance(project) }
> ```
>
> `sendMessage()` 方法改为：
> ```kotlin
> suspend fun sendMessage(message: ChatMessage): Result<SdkMessageTypes.SdkResultMessage> {
>     val session = sessionManager.getCurrentSession()
>         ?: return Result.failure(IllegalStateException("No active session"))
>     session.addMessage(message)
>     val options = sdkSessionManager.buildResumeOptions(session.id, SdkOptions(
>         model = session.context.modelConfig.model,
>         systemPrompt = buildSystemPrompt(session.context)
>     ))
>     return claudeClient.sendMessage(message.content, options, object : ClaudeCodeClient.SdkEventListener {
>         override fun onAssistant(msg: SdkMessageTypes.SdkAssistantMessage) {
>             // 可选：中间状态更新
>         }
>     }).also { result ->
>         result.getOrNull()?.let { msg ->
>             session.addMessage(ChatMessage(role = MessageRole.ASSISTANT,
>                 content = msg.result ?: "", metadata = mutableMapOf(
>                     "costUsd" to (msg.costUsd ?: 0.0),
>                     "durationMs" to (msg.durationMs ?: 0L)
>                 )))
>         }
>     }
> }
> ```

---

## 1. 阶段概览

本阶段构建**Application Layer的核心服务**，将Phase 2的通信能力封装为上层业务逻辑：

1. **SessionManager**: 多会话创建/切换/持久化
2. **ChatOrchestrator**: 聊天消息编排和流程控制
3. **StreamingOutputEngine**: 流式输出到JCEF的桥接
4. **ConfigManager + ConfigHotReloadManager**: 配置管理和热更新
5. **ContextProvider**: 自动收集代码上下文
6. **ThemeManager**: 主题管理
7. **ModelSwitcher**: 模型切换

**完成标志**: 可创建会话、发送消息、接收流式回复、切换模型、配置热更新

---

## 2. 任务清单

### T3.1 会话管理 (3人天)

#### T3.1.1 `application/session/SessionManager.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.session

import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.*
import com.github.xingzhewa.ccgui.infrastructure.error.PluginException
import com.github.xingzhewa.ccgui.infrastructure.state.createDisposableScope
import com.github.xingzhewa.ccgui.infrastructure.storage.SessionStorage
import com.github.xingzhewa.ccgui.model.session.*
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 多会话管理器
 * 项目级服务，管理所有聊天会话的生命周期
 *
 * 核心能力:
 *   - 创建/切换/删除会话
 *   - 会话状态持久化
 *   - 会话搜索
 *   - 会话隔离（独立上下文）
 *
 * 性能要求:
 *   - 会话切换延迟 < 100ms
 *   - 支持10+并发会话
 *
 * 扩展埋点:
 *   - 后续可添加会话自动命名
 *   - 后续可添加会话导入导出
 */
@Service(Service.Level.PROJECT)
class SessionManager(private val project: Project) : Disposable {

    private val log = logger<SessionManager>()
    private val scope = createDisposableScope(this)
    private val sessions = ConcurrentHashMap<String, ChatSession>()
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _sessionList = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessionList: StateFlow<List<ChatSession>> = _sessionList.asStateFlow()

    init {
        // 加载持久化的会话
        loadPersistedSessions()
    }

    /**
     * 创建新会话
     */
    fun createSession(
        name: String = "New Session",
        type: SessionType = SessionType.PROJECT,
        projectId: String? = project.name
    ): ChatSession {
        val session = ChatSession(
            name = name,
            type = type,
            projectId = projectId
        )
        sessions[session.id] = session
        updateSessionList()
        switchToSession(session.id)

        EventBus.publish(SessionCreatedEvent(session.id, session.name))
        log.info("Session created: ${session.id} (${session.name})")
        return session
    }

    /**
     * 切换到指定会话
     */
    fun switchToSession(sessionId: String) {
        val session = sessions[sessionId]
            ?: throw PluginException.SessionNotFound(sessionId)

        // 取消当前会话的活跃状态
        sessions[_currentSessionId.value]?.let { it.isActive = false }

        // 激活新会话
        session.isActive = true
        _currentSessionId.value = sessionId

        EventBus.publish(SessionChangedEvent(sessionId))
        log.info("Switched to session: $sessionId")
    }

    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String) {
        sessions.remove(sessionId)

        // 如果删除的是当前会话，切换到第一个可用会话
        if (_currentSessionId.value == sessionId) {
            val firstSession = sessions.values.firstOrNull()
            if (firstSession != null) {
                switchToSession(firstSession.id)
            } else {
                _currentSessionId.value = null
            }
        }

        updateSessionList()
        EventBus.publish(SessionDeletedEvent(sessionId))
        log.info("Session deleted: $sessionId")
    }

    /**
     * 获取当前会话
     */
    fun getCurrentSession(): ChatSession? {
        val id = _currentSessionId.value ?: return null
        return sessions[id]
    }

    /**
     * 获取指定会话
     */
    fun getSession(sessionId: String): ChatSession? = sessions[sessionId]

    /**
     * 重命名会话
     */
    fun renameSession(sessionId: String, newName: String) {
        sessions[sessionId]?.let {
            it.name = newName // ChatSession.name is var
            it.touch()
            updateSessionList()
        }
    }

    /**
     * 向会话添加消息
     */
    fun addMessage(sessionId: String, message: com.github.xingzhewa.ccgui.model.message.ChatMessage) {
        sessions[sessionId]?.addMessage(message)
    }

    /**
     * 搜索会话
     */
    fun searchSessions(query: String): List<ChatSession> {
        if (query.isBlank()) return sessions.values.toList()
        return sessions.values.filter { session ->
            session.name.contains(query, ignoreCase = true) ||
            session.messages.any { it.content.contains(query, ignoreCase = true) }
        }
    }

    /**
     * 持久化所有会话
     */
    fun persistSessions() {
        scope.launch {
            sessions.values.forEach { session ->
                // SessionStorage保存逻辑
            }
        }
    }

    private fun loadPersistedSessions() {
        // 从持久化存储加载会话
        // TODO: 接入SessionStorage
    }

    private fun updateSessionList() {
        _sessionList.value = sessions.values.sortedByDescending { it.updatedAt }
    }

    override fun dispose() {
        persistSessions()
        sessions.clear()
    }

    companion object {
        fun getInstance(project: Project): SessionManager =
            project.getService(SessionManager::class.java)
    }
}
```

---

#### T3.1.2 `application/session/SessionInterruptRecovery.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.session

import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.session.ChatSession
import com.github.xingzhewa.ccgui.model.session.SessionContext
import com.github.xingzhewa.ccgui.util.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 会话中断与恢复
 * 定期保存检查点，支持从中断点恢复
 *
 * 性能要求:
 *   - 检查点保存间隔: 5秒
 *   - 恢复成功率: > 95%
 */
class SessionInterruptRecovery(
    private val sessionManager: SessionManager
) {
    private val log = logger<SessionInterruptRecovery>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checkpoints = ConcurrentHashMap<String, SessionCheckpoint>()

    data class SessionCheckpoint(
        val sessionId: String,
        val messages: List<ChatMessage>,
        val context: SessionContext,
        val timestamp: Long
    )

    sealed class RecoveryResult {
        data class Success(val checkpoint: SessionCheckpoint) : RecoveryResult()
        object NoCheckpointFound : RecoveryResult()
        data class Corrupted(val error: String) : RecoveryResult()
    }

    private var checkpointJob: Job? = null

    /**
     * 启动定期检查点保存
     */
    fun startCheckpointing(sessionId: String, intervalMs: Long = 5000L) {
        checkpointJob?.cancel()
        checkpointJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                saveCheckpoint(sessionId)
            }
        }
    }

    /**
     * 停止检查点保存
     */
    fun stopCheckpointing() {
        checkpointJob?.cancel()
    }

    /**
     * 保存检查点
     */
    fun saveCheckpoint(sessionId: String) {
        val session = sessionManager.getSession(sessionId) ?: return
        checkpoints[sessionId] = SessionCheckpoint(
            sessionId = sessionId,
            messages = session.messages.toList(),
            context = session.context,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * 从检查点恢复
     */
    fun recoverSession(sessionId: String): RecoveryResult {
        val checkpoint = checkpoints[sessionId]
            ?: return RecoveryResult.NoCheckpointFound

        val session = sessionManager.getSession(sessionId)
            ?: return RecoveryResult.NoCheckpointFound

        return try {
            session.messages.clear()
            session.messages.addAll(checkpoint.messages)
            RecoveryResult.Success(checkpoint)
        } catch (e: Exception) {
            log.error("Failed to recover session: $sessionId", e)
            RecoveryResult.Corrupted(e.message ?: "Unknown error")
        }
    }

    fun dispose() {
        checkpointJob?.cancel()
        scope.cancel()
        checkpoints.clear()
    }
}
```

---

### T3.2 聊天编排 (3人天)

#### T3.2.1 `application/orchestrator/ChatOrchestrator.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.orchestrator

import com.github.xingzhewa.ccgui.adaptation.provider.MultiProviderAdapter
import com.github.xingzhewa.ccgui.application.session.SessionManager
import com.github.xingzhewa.ccgui.application.streaming.StreamingOutputEngine
import com.github.xingzhewa.ccgui.application.interaction.InteractiveRequestEngine
import com.github.xingzhewa.ccgui.infrastructure.error.withRetry
import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.message.MessageRole
import com.github.xingzhewa.ccgui.model.provider.ChatRequest
import com.github.xingzhewa.ccgui.model.provider.ChatResponse
import com.github.xingzhewa.ccgui.model.provider.ChatChunk
import com.github.xingzhewa.ccgui.model.session.SessionContext
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 聊天编排器 — 整个后端的核心枢纽
 *
 * 职责:
 *   1. 接收用户消息
 *   2. 构建完整上下文（历史消息 + 系统提示 + 模式配置）
 *   3. 调用AI供应商API
 *   4. 处理流式输出
 *   5. 处理交互式请求
 *   6. 更新会话状态
 *
 * 性能要求:
 *   - 消息响应延迟 < 300ms P95
 *   - 流式输出首字延迟 < 500ms
 *
 * 扩展埋点:
 *   - 后续可添加消息中间件链（日志、审计、过滤）
 *   - 后续可添加并发请求控制
 */
@Service(Service.Level.PROJECT)
class ChatOrchestrator(
    private val project: Project
) {
    private val log = logger<ChatOrchestrator>()

    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(project) }
    private val providerAdapter: MultiProviderAdapter by lazy { MultiProviderAdapter.getInstance() }

    /**
     * 发送消息（非流式）
     */
    suspend fun sendMessage(message: ChatMessage): Result<ChatResponse> {
        val session = sessionManager.getCurrentSession()
            ?: return Result.failure(IllegalStateException("No active session"))

        return try {
            // 1. 添加用户消息到会话
            session.addMessage(message)

            // 2. 构建完整上下文
            val context = buildFullContext(session)

            // 3. 调用AI模型
            val response = providerAdapter.chat(
                session.context.modelConfig.provider,
                ChatRequest(
                    model = session.context.modelConfig.model,
                    messages = context,
                    maxTokens = session.context.modelConfig.maxTokens,
                    systemPrompt = buildSystemPrompt(session.context)
                )
            )

            // 4. 添加AI回复到会话
            val aiMessage = ChatMessage(
                role = MessageRole.ASSISTANT,
                content = response.content,
                metadata = mutableMapOf(
                    "tokensUsed" to response.tokensUsed.totalTokens,
                    "executionTimeMs" to response.executionTimeMs
                )
            )
            session.addMessage(aiMessage)

            Result.success(response)
        } catch (e: Exception) {
            log.error("ChatOrchestrator.sendMessage failed", e)
            Result.failure(e)
        }
    }

    /**
     * 发送消息（流式）
     */
    suspend fun streamMessage(message: ChatMessage): Flow<ChatChunk> = flow {
        val session = sessionManager.getCurrentSession()
            ?: throw IllegalStateException("No active session")

        // 1. 添加用户消息
        session.addMessage(message)

        // 2. 创建AI消息占位符
        val aiMessage = ChatMessage(role = MessageRole.ASSISTANT, content = "")
        session.addMessage(aiMessage)

        // 3. 流式调用AI模型
        try {
            providerAdapter.streamChat(
                session.context.modelConfig.provider,
                ChatRequest(
                    model = session.context.modelConfig.model,
                    messages = buildFullContext(session),
                    maxTokens = session.context.modelConfig.maxTokens,
                    systemPrompt = buildSystemPrompt(session.context)
                )
            ).collect { chunk ->
                // 追加内容到AI消息
                aiMessage.metadata["streaming"] = true
                // 更新内容（注意：String不可变，需要通过metadata传递）
                emit(chunk)
            }
        } catch (e: Exception) {
            log.error("Stream failed", e)
            emit(ChatChunk(content = "\n[Error: ${e.message}]", isComplete = true))
        }
    }

    /**
     * 取消流式输出
     */
    fun cancelStreaming() {
        // 通过取消coroutine job来实现
    }

    /**
     * 构建完整上下文（历史消息列表）
     */
    private fun buildFullContext(session: com.github.xingzhewa.ccgui.model.session.ChatSession): List<ChatMessage> {
        // 取最近的消息作为上下文（避免超出token限制）
        val maxContextMessages = 50
        val messages = session.messages.takeLast(maxContextMessages)
        return messages
    }

    /**
     * 构建系统提示
     */
    private fun buildSystemPrompt(context: SessionContext): String {
        return buildString {
            append("You are Claude, an AI assistant integrated into JetBrains IDE via ClaudeCodeJet plugin. ")
            append("You help developers with coding, debugging, refactoring, and other software engineering tasks. ")
            append("Provide clear, concise, and actionable responses. ")
            append("When generating code, always include the language identifier in code blocks.")
        }
    }

    companion object {
        fun getInstance(project: Project): ChatOrchestrator =
            project.getService(ChatOrchestrator::class.java)
    }
}
```

---

### T3.3 流式输出引擎 (2人天)

#### T3.3.1 `application/streaming/StreamingOutputEngine.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.streaming

import com.github.xingzhewa.ccgui.browser.CefBrowserPanel
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.*
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger

/**
 * 流式输出引擎
 * 将AI模型的流式输出桥接到JCEF前端
 *
 * 职责:
 *   - 管理流式输出会话
 *   - 将chunk实时推送到前端
 *   - 处理流式完成和错误
 *
 * 性能要求:
 *   - 流式输出首字延迟 < 500ms
 *   - chunk推送延迟 < 50ms
 *
 * 扩展埋点:
 *   - 后续可添加消息缓冲和批量推送
 *   - 后续可添加Markdown实时解析
 */
class StreamingOutputEngine(
    private val browserPanel: CefBrowserPanel
) {
    private val log = logger<StreamingOutputEngine>()

    private var currentMessageId: String? = null
    private var currentSessionId: String? = null

    /**
     * 开始流式输出
     */
    fun startStreaming(sessionId: String, messageId: String) {
        currentMessageId = messageId
        currentSessionId = sessionId

        browserPanel.sendToJavaScript("streamStart", mapOf(
            "sessionId" to sessionId,
            "messageId" to messageId
        ))
    }

    /**
     * 追加chunk到当前消息
     */
    fun appendChunk(chunk: String) {
        browserPanel.sendToJavaScript("contentDelta", chunk)
    }

    /**
     * 完成流式输出
     */
    fun completeStreaming() {
        browserPanel.sendToJavaScript("streamEnd", mapOf(
            "sessionId" to (currentSessionId ?: ""),
            "messageId" to (currentMessageId ?: "")
        ))

        EventBus.publish(StreamingCompleteEvent(
            currentSessionId ?: "",
            currentMessageId ?: ""
        ))

        currentMessageId = null
        currentSessionId = null
    }

    /**
     * 流式输出错误
     */
    fun errorStreaming(error: String) {
        browserPanel.sendToJavaScript("error", mapOf("error" to error))

        EventBus.publish(StreamingErrorEvent(
            currentSessionId ?: "",
            error
        ))
    }
}
```

---

### T3.4 配置管理 (2人天)

#### T3.4.1 `application/config/ConfigManager.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.config

import com.github.xingzhewa.ccgui.config.CCGuiConfig
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.ConfigChangedEvent
import com.github.xingzhewa.ccgui.model.config.AppConfig
import com.github.xingzhewa.ccgui.model.config.ModelConfig
import com.github.xingzhewa.ccgui.model.config.ThemeConfig
import com.github.xingzhewa.ccgui.model.config.ConversationMode
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * 配置管理器
 * 提供统一的配置读写接口，支持配置变更通知
 *
 * 扩展埋点:
 *   - 新增配置项只需在AppConfig中添加字段
 *   - 监听器自动收到变更通知
 */
@Service(Service.Level.PROJECT)
class ConfigManager(private val project: Project) {

    private val log = logger<ConfigManager>()
    private val configService get() = CCGuiConfig.getInstance(project)

    /** 获取当前配置 */
    fun getConfig(): AppConfig = configService.getConfig()

    /** 更新完整配置 */
    fun updateConfig(config: AppConfig) {
        val old = getConfig()
        configService.updateConfig(config)

        // 检测哪些配置项发生了变化
        detectAndNotifyChanges(old, config)
    }

    /** 更新模型配置 */
    fun updateModelConfig(modelConfig: ModelConfig) {
        val config = getConfig()
        updateConfig(config.copy(model = modelConfig))
    }

    /** 更新主题配置 */
    fun updateThemeConfig(themeConfig: ThemeConfig) {
        val config = getConfig()
        updateConfig(config.copy(theme = themeConfig))
    }

    /** 更新对话模式 */
    fun updateConversationMode(mode: ConversationMode) {
        val config = getConfig()
        updateConfig(config.copy(conversationMode = mode))
    }

    private fun detectAndNotifyChanges(old: AppConfig, new: AppConfig) {
        if (old.model != new.model) {
            EventBus.publish(ConfigChangedEvent("model", new.model))
            EventBus.publish(com.github.xingzhewa.ccgui.infrastructure.eventbus.ModelChangedEvent(
                new.model.provider, new.model.model
            ))
        }
        if (old.theme != new.theme) {
            EventBus.publish(ConfigChangedEvent("theme", new.theme))
            EventBus.publish(com.github.xingzhewa.ccgui.infrastructure.eventbus.ThemeChangedEvent(new.theme.id))
        }
        if (old.conversationMode != new.conversationMode) {
            EventBus.publish(ConfigChangedEvent("conversationMode", new.conversationMode))
        }
    }

    companion object {
        fun getInstance(project: Project): ConfigManager =
            project.getService(ConfigManager::class.java)
    }
}
```

---

#### T3.4.2 `application/config/ConfigHotReloadManager.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.config

import com.github.xingzhewa.ccgui.infrastructure.eventbus.ConfigChangedEvent
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 配置热更新管理器
 * 监听配置变更，通知相关组件实时更新，无需重启IDE
 *
 * 性能要求:
 *   - 热更新延迟 < 100ms
 *   - 防抖: 300ms
 */
class ConfigHotReloadManager : Disposable {

    private val log = logger<ConfigHotReloadManager>()

    private val listeners = CopyOnWriteArrayList<ConfigChangeListener<*>>()
    private val debounceTimers = ConcurrentHashMap<String, Long>()
    private val debounceInterval = 300L // ms

    interface ConfigChangeListener<T> {
        val configKey: String
        fun onConfigChanged(oldValue: T, newValue: T)
    }

    /**
     * 监听指定配置项的变更
     */
    fun <T> watch(configKey: String, currentValue: T, onChange: (T) -> Unit): ConfigChangeListener<T> {
        val listener = object : ConfigChangeListener<T> {
            override val configKey: String = configKey
            override fun onConfigChanged(oldValue: T, newValue: T) {
                if (oldValue != newValue) {
                    // 防抖
                    val now = System.currentTimeMillis()
                    val lastTime = debounceTimers[configKey] ?: 0
                    if (now - lastTime < debounceInterval) return
                    debounceTimers[configKey] = now

                    onChange(newValue)
                }
            }
        }
        listeners.add(listener)
        return listener
    }

    /**
     * 移除监听器
     */
    fun <T> removeListener(listener: ConfigChangeListener<T>) {
        listeners.remove(listener)
    }

    /**
     * 通知配置变更
     */
    @Suppress("UNCHECKED_CAST")
    fun notifyConfigChanged(configKey: String, oldValue: Any?, newValue: Any?) {
        listeners.forEach { listener ->
            if (listener.configKey == configKey) {
                try {
                    (listener as ConfigChangeListener<Any>).onConfigChanged(oldValue, newValue)
                } catch (e: Exception) {
                    log.error("ConfigChangeListener error for key: $configKey", e)
                }
            }
        }
    }

    /**
     * 批量更新
     */
    fun batchUpdate(updates: Map<String, Pair<Any?, Any?>>) {
        updates.forEach { (key, values) ->
            notifyConfigChanged(key, values.first, values.second)
        }
    }

    override fun dispose() {
        listeners.clear()
        debounceTimers.clear()
    }
}
```

---

#### T3.4.3 `application/config/ThemeManager.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.config

import com.github.xingzhewa.ccgui.browser.CefBrowserPanel
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.ThemeChangedEvent
import com.github.xingzhewa.ccgui.model.config.*
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable

/**
 * 主题管理器
 * 管理预设主题和自定义主题，通知JCEF更新
 *
 * 性能要求: 主题切换延迟 < 100ms
 */
class ThemeManager(
    private val browserPanel: CefBrowserPanel
) : Disposable {

    private val log = logger<ThemeManager>()

    private var currentTheme: ThemeConfig = ThemePresets.JETBRAINS_DARK

    /** 预设主题列表 */
    val presetThemes: List<ThemeConfig> = ThemePresets.all()

    /** 获取当前主题 */
    fun getCurrentTheme(): ThemeConfig = currentTheme

    /** 应用主题 */
    fun applyTheme(theme: ThemeConfig) {
        val old = currentTheme
        currentTheme = theme

        // 通知JCEF更新主题
        browserPanel.sendToJavaScript("themeChanged", JsonUtils.toJson(theme))

        // 通知Swing组件
        EventBus.publish(ThemeChangedEvent(theme.id))

        log.info("Theme applied: ${theme.name}")
    }

    /** 应用预设主题 */
    fun applyPresetTheme(themeId: String) {
        ThemePresets.all().find { it.id == themeId }?.let { applyTheme(it) }
    }

    override fun dispose() {}
}

/** 预设主题集合 */
object ThemePresets {
    val JETBRAINS_DARK = ThemeConfig(
        id = "jetbrains-dark", name = "JetBrains Dark", isDark = true,
        colors = ColorScheme()
    )
    val GITHUB_DARK = ThemeConfig(
        id = "github-dark", name = "GitHub Dark", isDark = true,
        colors = ColorScheme(background = "#0D1117", primary = "#58A6FF")
    )
    val VSCODE_DARK = ThemeConfig(
        id = "vscode-dark", name = "VS Code Dark", isDark = true,
        colors = ColorScheme(background = "#1E1E1E", primary = "#007ACC")
    )
    val MONOKAI = ThemeConfig(
        id = "monokai", name = "Monokai", isDark = true,
        colors = ColorScheme(background = "#272822", primary = "#F92672")
    )
    val SOLARIZED_LIGHT = ThemeConfig(
        id = "solarized-light", name = "Solarized Light", isDark = false,
        colors = ColorScheme.defaultLight().copy(background = "#FDF6E3", primary = "#268BD2")
    )
    val NORD = ThemeConfig(
        id = "nord", name = "Nord", isDark = true,
        colors = ColorScheme(background = "#2E3440", primary = "#88C0D0")
    )

    fun all() = listOf(JETBRAINS_DARK, GITHUB_DARK, VSCODE_DARK, MONOKAI, SOLARIZED_LIGHT, NORD)
}
```

---

#### T3.4.4 `application/config/ModelSwitcher.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.config

import com.github.xingzhewa.ccgui.adaptation.provider.MultiProviderAdapter
import com.github.xingzhewa.ccgui.infrastructure.error.PluginException
import com.github.xingzhewa.ccgui.model.config.ModelConfig
import com.github.xingzhewa.ccgui.util.logger

/**
 * 模型切换器
 * 支持快捷切换不同AI供应商和模型
 *
 * 性能要求: 切换耗时 < 1s
 */
class ModelSwitcher(
    private val providerAdapter: MultiProviderAdapter,
    private val configManager: ConfigManager
) {
    private val log = logger<ModelSwitcher>()

    /**
     * 切换模型
     */
    fun switchModel(providerName: String, modelName: String) {
        val provider = providerAdapter.getProvider(providerName)
        if (modelName !in provider.availableModels) {
            throw PluginException.ModelNotAvailable(modelName, providerName)
        }

        val currentConfig = configManager.getConfig().model
        configManager.updateModelConfig(
            currentConfig.copy(provider = providerName, model = modelName)
        )
        log.info("Model switched to $providerName/$modelName")
    }

    /**
     * 获取所有可用模型
     */
    fun getAvailableModels(): Map<String, List<String>> {
        return providerAdapter.getAvailableProviders().associateWith {
            providerAdapter.getAvailableModels(it)
        }
    }
}
```

---

### T3.5 上下文提供 (1人天)

#### T3.5.1 `application/context/ContextProvider.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.context

import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * 上下文提供者
 * 自动收集IDE中的代码上下文，注入到对话中
 *
 * 扩展埋点:
 *   - 后续可添加PSI分析（类结构、方法签名）
 *   - 后续可添加VCS上下文（最近变更）
 *   - 后续可添加依赖信息（build.gradle内容）
 */
class ContextProvider(private val project: Project) {

    private val log = logger<ContextProvider>()

    data class IDEContext(
        val selectedText: String? = null,
        val currentFile: FileInfo? = null,
        val openFiles: List<FileInfo> = emptyList(),
        val projectStructure: String? = null
    )

    data class FileInfo(
        val name: String,
        val path: String,
        val language: String?,
        val content: String?
    )

    /**
     * 收集当前IDE上下文
     */
    fun collectContext(): IDEContext {
        return IDEContext(
            selectedText = getSelectedText(),
            currentFile = getCurrentFileInfo(),
            openFiles = getOpenFileInfos()
        )
    }

    /**
     * 获取选中的文本
     */
    fun getSelectedText(): String? {
        val editor = getActiveEditor() ?: return null
        return editor.selectionModel.selectedText
    }

    /**
     * 获取当前文件信息
     */
    fun getCurrentFileInfo(): FileInfo? {
        val editor = getActiveEditor() ?: return null
        val file = FileEditorManager.getInstance(project).selectedFiles?.firstOrNull() ?: return null
        return fileInfoFrom(file)
    }

    /**
     * 构建上下文prompt片段
     */
    fun buildContextPrompt(context: IDEContext): String {
        return buildString {
            context.currentFile?.let { file ->
                append("Current file: ${file.name} (${file.language ?: "unknown"})\n")
            }
            context.selectedText?.let { text ->
                append("Selected code:\n```\n$text\n```\n")
            }
        }
    }

    private fun getActiveEditor(): Editor? =
        FileEditorManager.getInstance(project).selectedTextEditor

    private fun getOpenFileInfos(): List<FileInfo> =
        FileEditorManager.getInstance(project).openFiles.mapNotNull { fileInfoFrom(it) }

    private fun fileInfoFrom(file: VirtualFile): FileInfo = FileInfo(
        name = file.name,
        path = file.path,
        language = file.extension,
        content = null // 不自动读取内容，避免性能问题
    )
}
```

---

### T3.6 对话模式管理 (1人天)

#### T3.6.1 `application/mode/ConversationModeManager.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.mode

import com.github.xingzhewa.ccgui.application.config.ConfigManager
import com.github.xingzhewa.ccgui.model.config.ConversationMode
import com.github.xingzhewa.ccgui.util.logger

/**
 * 对话模式管理器
 * 支持Thinking/Planning/Auto三种对话模式
 * 根据模式调整系统提示和回复格式
 */
class ConversationModeManager(
    private val configManager: ConfigManager
) {
    private val log = logger<ConversationModeManager>()

    /**
     * 设置对话模式
     */
    fun setMode(mode: ConversationMode) {
        configManager.updateConversationMode(mode)
        log.info("Conversation mode set to: $mode")
    }

    /**
     * 获取当前模式
     */
    fun getCurrentMode(): ConversationMode =
        configManager.getConfig().conversationMode

    /**
     * 根据模式调整用户prompt
     */
    fun applyModeToPrompt(originalPrompt: String, mode: ConversationMode): String {
        return when (mode) {
            ConversationMode.THINKING -> """
                Please think deeply about the following, step by step:

                $originalPrompt

                Format your response:
                1. Problem Analysis
                2. Thinking Process
                3. Solution
                4. Conclusion
            """.trimIndent()

            ConversationMode.PLANNING -> """
                Please create a detailed plan for the following task:

                $originalPrompt

                Format your response:
                1. Task Understanding
                2. Task Breakdown (step list)
                3. Execution Plan
                4. Risk Assessment
            """.trimIndent()

            ConversationMode.AUTO -> originalPrompt
        }
    }
}
```

---

## 3. plugin.xml 新增注册

```xml
<!-- Phase 3 新增 -->
<projectService serviceImplementation="com.github.xingzhewa.ccgui.application.session.SessionManager"/>
<projectService serviceImplementation="com.github.xingzhewa.ccgui.application.orchestrator.ChatOrchestrator"/>
<projectService serviceImplementation="com.github.xingzhewa.ccgui.application.config.ConfigManager"/>
```

---

## 4. 任务依赖与执行顺序

```
T3.1 SessionManager ← 依赖 Phase1 的 EventBus + Session模型 + SessionStorage
  ↓
T3.1 SessionInterruptRecovery ← 依赖 SessionManager
  ↓
T3.2 ChatOrchestrator ← 依赖 SessionManager + MultiProviderAdapter(Phase2)
  ↓
T3.3 StreamingOutputEngine ← 依赖 CefBrowserPanel(Phase1)
  ↓ (可并行)
T3.4 ConfigManager ← 依赖 CCGuiConfig(Phase1)
T3.4 ConfigHotReload ← 依赖 EventBus(Phase1)
T3.4 ThemeManager ← 依赖 CefBrowserPanel(Phase1)
T3.4 ModelSwitcher ← 依赖 MultiProviderAdapter(Phase2) + ConfigManager
  ↓ (可并行)
T3.5 ContextProvider ← 独立（仅需Platform API）
T3.6 ConversationModeManager ← 依赖 ConfigManager
```

---

## 5. 验收标准

| 验收项 | 标准 |
|--------|------|
| 会话创建 | 可创建多个会话，切换延迟 < 100ms |
| 会话持久化 | 会话数据可保存和恢复 |
| 消息发送 | 通过ChatOrchestrator可发送消息并收到回复 |
| 流式输出 | StreamingOutputEngine可将chunk推送到JCEF |
| 配置管理 | 配置变更可通过ConfigManager持久化 |
| 热更新 | 配置变更300ms内通知到所有监听器 |
| 主题切换 | 主题切换 < 100ms |
| 模型切换 | 可切换不同AI模型，耗时 < 1s |
| 上下文收集 | 可收集选中文本和当前文件信息 |

---

## 6. 文件清单汇总

| 序号 | 文件路径 | 类型 |
|------|----------|------|
| 1 | `application/session/SessionManager.kt` | 服务 |
| 2 | `application/session/SessionInterruptRecovery.kt` | 管理器 |
| 3 | `application/orchestrator/ChatOrchestrator.kt` | 核心服务 |
| 4 | `application/streaming/StreamingOutputEngine.kt` | 引擎 |
| 5 | `application/config/ConfigManager.kt` | 服务 |
| 6 | `application/config/ConfigHotReloadManager.kt` | 管理器 |
| 7 | `application/config/ThemeManager.kt` | 管理器 |
| 8 | `application/config/ModelSwitcher.kt` | 切换器 |
| 9 | `application/context/ContextProvider.kt` | 上下文 |
| 10 | `application/mode/ConversationModeManager.kt` | 模式管理 |

**共计**: 10个文件
