package com.github.xingzhewa.ccgui.application.viewmodel

import com.github.xingzhewa.ccgui.application.agent.AgentsManager
import com.github.xingzhewa.ccgui.application.agent.AgentExecutor
import com.github.xingzhewa.ccgui.application.chat.ChatConfigManager
import com.github.xingzhewa.ccgui.application.config.ConfigManager
import com.github.xingzhewa.ccgui.application.context.ContextManager
import com.github.xingzhewa.ccgui.application.interaction.InteractiveRequestEngine
import com.github.xingzhewa.ccgui.application.mcp.McpServerManager
import com.github.xingzhewa.ccgui.application.multimodal.MultimodalInputHandler
import com.github.xingzhewa.ccgui.application.prompt.PromptOptimizer
import com.github.xingzhewa.ccgui.application.session.SessionManager
import com.github.xingzhewa.ccgui.application.skill.SkillExecutor
import com.github.xingzhewa.ccgui.application.skill.SkillsManager
import com.github.xingzhewa.ccgui.application.task.TaskProgressTracker
import com.github.xingzhewa.ccgui.bridge.BridgeManager
import com.github.xingzhewa.ccgui.bridge.ConnectionState
import com.github.xingzhewa.ccgui.bridge.StreamCallback
import com.github.xingzhewa.ccgui.model.agent.Agent
import com.github.xingzhewa.ccgui.model.agent.AgentTask
import com.github.xingzhewa.ccgui.model.config.ConversationMode
import com.github.xingzhewa.ccgui.model.interaction.QuestionType
import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.message.MessageRole
import com.github.xingzhewa.ccgui.model.mcp.McpServer
import com.github.xingzhewa.ccgui.model.mcp.TestResult
import com.github.xingzhewa.ccgui.model.session.ChatSession
import com.github.xingzhewa.ccgui.model.session.SessionType
import com.github.xingzhewa.ccgui.model.skill.ExecutionContext
import com.github.xingzhewa.ccgui.model.skill.Skill
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 聊天视图模型
 *
 * 封装聊天相关业务逻辑，实现与 UI（CefBrowserPanel）的分离
 */
class ChatViewModel(private val project: Project) {

    private val log = logger<ChatViewModel>()

    // 依赖的服务
    private val sessionManager by lazy { SessionManager.getInstance(project) }
    private val contextManager by lazy { ContextManager.getInstance(project) }
    private val bridgeManager by lazy { BridgeManager.getInstance(project) }
    private val configManager by lazy { ConfigManager.getInstance(project) }
    private val chatConfigManager by lazy { ChatConfigManager.getInstance() }
    private val promptOptimizer by lazy { PromptOptimizer.getInstance(project) }
    private val skillExecutor by lazy { SkillExecutor.getInstance(project) }
    private val skillsManager by lazy { SkillsManager.getInstance(project) }
    private val agentsManager by lazy { AgentsManager.getInstance(project) }
    private val agentExecutor by lazy { AgentExecutor.getInstance(project) }
    private val mcpServerManager by lazy { McpServerManager.getInstance(project) }
    private val taskProgressTracker by lazy { TaskProgressTracker.getInstance(project) }
    private val multimodalInputHandler by lazy { MultimodalInputHandler.getInstance(project) }

    private val scope = CoroutineScope(Dispatchers.Main)

    // 状态
    val connectionState: StateFlow<ConnectionState> = bridgeManager.connectionState

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    init {
        log.info("ChatViewModel initialized")
    }

    // ==================== 消息发送 ====================

    /**
     * 发送消息
     */
    fun sendMessage(
        content: String,
        sessionId: String,
        messageId: String = "",
        callback: StreamCallback? = null,
        onResponse: ((Any?, String?) -> Unit)? = null
    ) {
        val effectiveMessageId = messageId.ifEmpty { "default" }

        bridgeManager.sendMessage(
            message = content,
            sessionId = sessionId,
            callback = callback ?: createDefaultStreamCallback(effectiveMessageId),
            onResponse = onResponse
        )
    }

    /**
     * 流式发送消息
     */
    fun streamMessage(
        content: String,
        sessionId: String,
        messageId: String = "",
        callback: StreamCallback? = null,
        onResponse: ((Any?, String?) -> Unit)? = null
    ) {
        val effectiveMessageId = messageId.ifEmpty { "default" }

        bridgeManager.streamMessage(
            message = content,
            sessionId = sessionId,
            callback = callback ?: createDefaultStreamCallback(effectiveMessageId),
            onResponse = onResponse
        )
    }

    /**
     * 取消流式输出
     */
    fun cancelStreaming(sessionId: String) {
        bridgeManager.cancelStreaming(sessionId)
    }

    private fun createDefaultStreamCallback(messageId: String): StreamCallback {
        return object : StreamCallback {
            override fun onLineReceived(line: String) {
                // 默认实现由 StreamingOutputEngine 处理
            }

            override fun onStreamComplete(messages: List<String>) {}

            override fun onStreamError(error: String) {
                log.error("Stream error: $error")
            }
        }
    }

    // ==================== 会话管理 ====================

    /**
     * 创建会话
     */
    fun createSession(name: String, type: SessionType): ChatSession {
        return sessionManager.createSession(name, type)
    }

    /**
     * 切换会话
     */
    fun switchSession(sessionId: String) {
        sessionManager.setCurrentSession(sessionId)
        _currentSession.value = sessionManager.getCurrentSession()
        _messages.value = _currentSession.value?.messages ?: emptyList()
    }

    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String) {
        sessionManager.deleteSession(sessionId)
        if (_currentSession.value?.id == sessionId) {
            _currentSession.value = sessionManager.getCurrentSession()
            _messages.value = _currentSession.value?.messages ?: emptyList()
        }
    }

    /**
     * 清空会话消息
     */
    fun clearSession(sessionId: String) {
        sessionManager.clearSession(sessionId)
        if (_currentSession.value?.id == sessionId) {
            _messages.value = emptyList()
        }
    }

    /**
     * 确认会话
     */
    fun confirmSession(sessionId: String): Boolean {
        return sessionManager.confirmSession(sessionId)
    }

    /**
     * 获取历史会话
     */
    fun getHistorySessions(): List<ChatSession> {
        return sessionManager.getHistorySessions()
    }

    /**
     * 搜索会话
     */
    fun searchSessions(query: String): List<ChatSession> {
        return sessionManager.searchSessions(query)
    }

    /**
     * 导出会话
     */
    fun exportSession(sessionId: String): String? {
        val session = sessionManager.getSession(sessionId) ?: return null
        return JsonUtils.toJson(session)
    }

    /**
     * 导入会话
     */
    fun importSession(jsonContent: String): ChatSession? {
        return sessionManager.importSession(jsonContent)
    }

    // ==================== 配置管理 ====================

    /**
     * 获取应用配置（JSON格式）
     */
    fun getAppConfigJson(): String {
        return JsonUtils.toJson(configManager.getAppConfig()) ?: "{}"
    }

    /**
     * 获取聊天配置
     */
    fun getChatConfig(): Map<String, Any?> {
        return mapOf(
            "currentAgentId" to chatConfigManager.getCurrentAgentId(),
            "conversationMode" to chatConfigManager.getConversationMode().name,
            "streamingEnabled" to chatConfigManager.isStreamingEnabled()
        )
    }

    /**
     * 更新聊天配置
     */
    fun updateChatConfig(config: Map<String, Any?>) {
        config["currentAgentId"]?.let { chatConfigManager.setCurrentAgent(it as? String) }
        config["conversationMode"]?.let {
            val mode = try { ConversationMode.valueOf(it as String) } catch (e: Exception) { null }
            mode?.let { chatConfigManager.setConversationMode(it) }
        }
        config["streamingEnabled"]?.let { chatConfigManager.setStreamingEnabled(it as? Boolean ?: true) }
    }

    /**
     * 获取主题列表
     */
    fun getThemes(): List<Map<String, Any>> {
        return configManager.getAllThemes().map { theme ->
            mapOf(
                "id" to theme.id,
                "name" to theme.name,
                "isDark" to theme.isDark
            )
        }
    }

    /**
     * 更新主题
     */
    fun updateTheme(themeId: String) {
        configManager.setCurrentTheme(themeId)
    }

    /**
     * 保存自定义主题
     */
    fun saveCustomTheme(theme: Map<String, Any>): Boolean {
        return try {
            val themeConfig = com.github.xingzhewa.ccgui.model.config.ThemeConfig(
                id = theme["id"] as? String ?: return false,
                name = theme["name"] as? String ?: return false,
                isDark = theme["isDark"] as? Boolean ?: true
            )
            configManager.saveCustomTheme(themeConfig)
            true
        } catch (e: Exception) {
            log.error("Failed to save custom theme", e)
            false
        }
    }

    /**
     * 删除自定义主题
     */
    fun deleteCustomTheme(themeId: String): Boolean {
        return try {
            configManager.deleteCustomTheme(themeId)
            true
        } catch (e: Exception) {
            log.error("Failed to delete custom theme", e)
            false
        }
    }

    /**
     * 获取 IDE 主题
     */
    fun getIdeTheme(): Map<String, Any> {
        // 返回一个默认的 IDE 主题映射
        return mapOf(
            "isDark" to true,
            "name" to "IDE Default"
        )
    }

    // ==================== 模型配置 ====================

    /**
     * 获取模型配置
     */
    fun getModelConfig(): Map<String, Any?> {
        val modelConfig = configManager.getActiveModelConfig()
        return mapOf(
            "provider" to modelConfig.provider,
            "model" to modelConfig.model,
            "apiKey" to (if (modelConfig.apiKey?.isNotEmpty() == true) "****" else null),
            "baseUrl" to modelConfig.baseUrl
        )
    }

    /**
     * 更新模型配置（通过保存 ProviderProfile）
     */
    fun updateModelConfig(config: Map<String, Any?>) {
        val current = configManager.getActiveModelConfig()
        val updated = current.copy(
            provider = config["provider"] as? String ?: current.provider,
            model = config["model"] as? String ?: current.model,
            apiKey = config["apiKey"] as? String ?: current.apiKey,
            baseUrl = config["baseUrl"] as? String ?: current.baseUrl
        )
        configManager.updateModelConfig(updated)
    }

    /**
     * 获取供应商列表
     */
    fun getProviders(): List<Map<String, Any>> {
        return configManager.getProviderProfiles().map { profile ->
            mapOf(
                "id" to profile.id,
                "name" to profile.name,
                "provider" to profile.provider
            )
        }
    }

    /**
     * 获取供应商模型
     */
    fun getProviderModels(providerId: String): List<String> {
        val profile = configManager.getProviderProfiles().find { it.id == providerId }
        return listOfNotNull(profile?.model)
    }

    /**
     * 获取供应商配置
     */
    fun getProviderProfiles(): List<Map<String, Any>> {
        return configManager.getProviderProfiles().map { profile ->
            mapOf(
                "id" to profile.id,
                "name" to profile.name,
                "provider" to profile.provider,
                "model" to profile.model,
                "source" to profile.source
            )
        }
    }

    /**
     * 创建供应商配置
     */
    fun createProviderProfile(profile: Map<String, Any>): Boolean {
        return try {
            val providerProfile = com.github.xingzhewa.ccgui.model.config.ProviderProfile(
                id = profile["id"] as? String ?: return false,
                name = profile["name"] as? String ?: return false,
                provider = profile["provider"] as? String ?: "anthropic",
                source = profile["source"] as? String ?: "manual",
                model = profile["model"] as? String ?: "",
                apiKey = profile["apiKey"] as? String,
                baseUrl = profile["baseUrl"] as? String
            )
            configManager.saveProviderProfile(providerProfile)
            true
        } catch (e: Exception) {
            log.error("Failed to create provider profile", e)
            false
        }
    }

    /**
     * 更新供应商配置
     */
    fun updateProviderProfile(profileId: String, profile: Map<String, Any>): Boolean {
        return try {
            val existing = configManager.getProviderProfiles().find { it.id == profileId } ?: return false
            val updated = existing.copy(
                name = profile["name"] as? String ?: existing.name,
                provider = profile["provider"] as? String ?: existing.provider,
                model = profile["model"] as? String ?: existing.model,
                apiKey = profile["apiKey"] as? String ?: existing.apiKey,
                baseUrl = profile["baseUrl"] as? String ?: existing.baseUrl
            )
            configManager.saveProviderProfile(updated)
            true
        } catch (e: Exception) {
            log.error("Failed to update provider profile", e)
            false
        }
    }

    /**
     * 删除供应商配置
     */
    fun deleteProviderProfile(profileId: String): Boolean {
        return try {
            configManager.deleteProviderProfile(profileId)
            true
        } catch (e: Exception) {
            log.error("Failed to delete provider profile", e)
            false
        }
    }

    /**
     * 设置活跃供应商配置
     */
    fun setActiveProviderProfile(profileId: String?): Boolean {
        return try {
            configManager.setActiveProviderProfile(profileId)
            true
        } catch (e: Exception) {
            log.error("Failed to set active provider profile", e)
            false
        }
    }

    /**
     * 重新排序供应商配置
     */
    fun reorderProviderProfiles(profileIds: List<String>): Boolean {
        return try {
            configManager.reorderProviderProfiles(profileIds)
            true
        } catch (e: Exception) {
            log.error("Failed to reorder provider profiles", e)
            false
        }
    }

    /**
     * 转换 Claude Code 切换配置
     */
    fun convertCcSwitchProfile(profileId: String): Boolean {
        return configManager.convertCcSwitchProfile(profileId) != null
    }

    /**
     * 获取对话模式
     */
    fun getConversationModes(): List<String> {
        return ConversationMode.entries.map { it.name }
    }

    // ==================== Skills 管理 ====================

    /**
     * 获取 Skills
     */
    fun getSkills(): List<Skill> {
        return skillsManager.getAllSkills()
    }

    /**
     * 保存 Skill（更新或创建）
     */
    fun saveSkill(skill: Skill): Boolean {
        return skillsManager.updateSkill(skill)
    }

    /**
     * 删除 Skill
     */
    fun deleteSkill(skillId: String): Boolean {
        return skillsManager.deleteSkill(skillId)
    }

    /**
     * 执行 Skill
     */
    fun executeSkill(skillId: String, sessionId: String, context: Map<String, Any>): Any? {
        var result: Any? = null
        scope.launch {
            val skill = skillsManager.getSkill(skillId)
            if (skill != null) {
                val execContext = ExecutionContext(variables = context + ("sessionId" to sessionId))
                val execResult = skillExecutor.executeSkill(skill, execContext)
                result = execResult
            }
        }
        return result
    }

    // ==================== Agents 管理 ====================

    /**
     * 获取 Agents
     */
    fun getAgents(): List<Agent> {
        return agentsManager.getAllAgents()
    }

    /**
     * 保存 Agent
     */
    fun saveAgent(agent: Agent): Boolean {
        return agentsManager.updateAgent(agent)
    }

    /**
     * 删除 Agent
     */
    fun deleteAgent(agentId: String): Boolean {
        return agentsManager.deleteAgent(agentId)
    }

    /**
     * 启动 Agent
     */
    fun startAgent(agentId: String, task: String, sessionId: String) {
        scope.launch {
            val agent = agentsManager.getAgent(agentId)
            if (agent != null) {
                val agentTask = AgentTask(
                    description = task,
                    requiredCapability = agent.capabilities.firstOrNull() ?: com.github.xingzhewa.ccgui.model.agent.AgentCapability.CODE_GENERATION
                )
                agentExecutor.executeAgent(agent, agentTask)
            }
        }
    }

    /**
     * 停止 Agent
     */
    fun stopAgent(agentId: String) {
        agentExecutor.stopAllExecutions(agentId)
    }

    // ==================== MCP 管理 ====================

    /**
     * 获取 MCP 服务器
     */
    fun getMcpServers(): List<McpServer> {
        return mcpServerManager.getAllServers()
    }

    /**
     * 保存 MCP 服务器
     */
    fun saveMcpServer(server: McpServer): Boolean {
        return mcpServerManager.updateServer(server)
    }

    /**
     * 删除 MCP 服务器
     */
    fun deleteMcpServer(serverId: String): Boolean {
        return mcpServerManager.deleteServer(serverId)
    }

    /**
     * 启动 MCP 服务器
     */
    fun startMcpServer(serverId: String) {
        scope.launch {
            mcpServerManager.startServer(serverId)
        }
    }

    /**
     * 停止 MCP 服务器
     */
    fun stopMcpServer(serverId: String) {
        scope.launch {
            mcpServerManager.stopServer(serverId)
        }
    }

    /**
     * 测试 MCP 服务器
     */
    fun testMcpServer(serverId: String, timeout: Long = 5000): Map<String, Any> {
        // 注意：这是一个挂起函数，返回空结果。实际测试结果通过回调或 Flow 传递
        return mapOf("pending" to true)
    }

    /**
     * 异步测试 MCP 服务器
     */
    fun testMcpServerAsync(serverId: String, timeout: Long = 5000, callback: (Map<String, Any>) -> Unit) {
        scope.launch {
            val testResult = mcpServerManager.testServer(serverId, timeout)
            val sr = testResult
            if (sr is TestResult.Success) {
                callback(mapOf("success" to true, "message" to "Connection successful"))
            } else if (sr is TestResult.Failure) {
                callback(mapOf("success" to false, "message" to sr.error))
            }
        }
    }

    // ==================== 任务进度 ====================

    /**
     * 获取当前任务状态
     */
    fun getTaskStatus(): Map<String, Any> {
        val activeTask = taskProgressTracker.getActiveTask()
        return if (activeTask != null) {
            mapOf(
                "hasActive" to true,
                "taskId" to activeTask.taskId,
                "name" to activeTask.name,
                "status" to activeTask.status.name,
                "progress" to activeTask.progress
            )
        } else {
            mapOf("hasActive" to false)
        }
    }

    // ==================== 提示词优化 ====================

    /**
     * 优化提示词
     *
     * 注意：这是挂起函数，这里返回原文本，异步优化请使用 optimizePromptAsync
     */
    fun optimizePrompt(prompt: String, context: Map<String, Any>?): String {
        return prompt
    }

    /**
     * 异步优化提示词
     */
    fun optimizePromptAsync(prompt: String, context: Map<String, Any>? = null, callback: (String) -> Unit) {
        scope.launch {
            val session = _currentSession.value
            val result = promptOptimizer.optimizePrompt(prompt, session)
            callback(result.optimizedPrompt)
        }
    }

    // ==================== 编辑器操作 ====================

    /**
     * 获取选中文本
     *
     * 注意：当前版本通过编辑器 API 获取，后续实现
     */
    fun getSelectedText(): String? {
        // TODO: 通过 IntelliJ 编辑器 API 获取选中文本
        return null
    }

    /**
     * 替换选中文本
     *
     * 注意：当前版本通过编辑器 API 替换，后续实现
     */
    fun replaceSelectedText(text: String): Boolean {
        // TODO: 通过 IntelliJ 编辑器 API 替换选中文本
        return false
    }

    // ==================== 交互式请求 ====================

    /**
     * 提交交互式问题答案
     */
    fun submitAnswer(questionId: String, answer: String, sessionId: String) {
        val questionAnswer = InteractiveRequestEngine.QuestionAnswer.TextInput(answer)
        InteractiveRequestEngine.getInstance(project).submitAnswer(questionId, questionAnswer)
    }

    companion object {
        fun getInstance(project: Project): ChatViewModel =
            project.getService(ChatViewModel::class.java)
    }
}
