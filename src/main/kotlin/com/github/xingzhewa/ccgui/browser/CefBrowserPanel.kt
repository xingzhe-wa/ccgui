package com.github.xingzhewa.ccgui.browser

import com.github.xingzhewa.ccgui.application.agent.AgentsManager
import com.github.xingzhewa.ccgui.application.agent.AgentExecutor
import com.github.xingzhewa.ccgui.application.config.ConfigManager
import com.github.xingzhewa.ccgui.application.context.ContextManager
import com.github.xingzhewa.ccgui.application.interaction.InteractiveRequestEngine
import com.github.xingzhewa.ccgui.application.mcp.McpServerManager
import com.github.xingzhewa.ccgui.application.multimodal.MultimodalInputHandler
import com.github.xingzhewa.ccgui.application.prompt.PromptOptimizer
import com.github.xingzhewa.ccgui.application.session.SessionManager
import com.github.xingzhewa.ccgui.infrastructure.storage.SessionStorage
import com.github.xingzhewa.ccgui.application.skill.SkillExecutor
import com.github.xingzhewa.ccgui.application.skill.SkillsManager
import com.github.xingzhewa.ccgui.application.task.TaskProgressTracker
import com.github.xingzhewa.ccgui.bridge.BridgeManager
import com.github.xingzhewa.ccgui.bridge.ConnectionState
import com.github.xingzhewa.ccgui.model.agent.Agent
import com.github.xingzhewa.ccgui.model.config.ConversationMode
import com.github.xingzhewa.ccgui.model.message.MessageRole
import com.github.xingzhewa.ccgui.model.interaction.QuestionType
import com.github.xingzhewa.ccgui.model.mcp.McpServer
import com.github.xingzhewa.ccgui.model.session.ChatSession
import com.github.xingzhewa.ccgui.model.session.SessionType
import com.github.xingzhewa.ccgui.model.skill.Skill
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JCEF 浏览器面板封装
 *
 * 提供 Java → JS 和 JS → Java 双向通信
 */
class CefBrowserPanel(private val project: Project) : Disposable {

    private val log = logger<CefBrowserPanel>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** JCEF 浏览器实例 */
    private var browser: JBCefBrowser? = null

    /** JS 查询处理器 */
    private var jsQuery: JBCefJSQuery? = null

    /** JS 调用后端的全局函数引用，由 injectBackendJavaScript() 设置 */
    private var jsQueryInvoker: java.util.function.Function<String, JBCefJSQuery.Response>? = null

    /** 页面加载完成标志 */
    private var isPageLoaded = false

    /** 页面加载完成监听器列表 */
    private val loadListeners = mutableListOf<Runnable>()

    /** 页面加载监听器是否已设置 */
    private var loadListenerSetup = false

    /** BridgeManager */
    private val bridgeManager: BridgeManager by lazy { BridgeManager.getInstance(project) }

    /** SessionManager */
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(project) }

    /** ConfigManager */
    private val configManager: ConfigManager by lazy { ConfigManager.getInstance(project) }

    /** InteractiveRequestEngine */
    private val interactiveEngine: InteractiveRequestEngine by lazy { InteractiveRequestEngine.getInstance(project) }

    /** SkillsManager */
    private val skillsManager: SkillsManager by lazy { SkillsManager.getInstance(project) }

    /** SkillExecutor */
    private val skillExecutor: SkillExecutor by lazy { SkillExecutor.getInstance(project) }

    /** AgentsManager */
    private val agentsManager: AgentsManager by lazy { AgentsManager.getInstance(project) }

    /** AgentExecutor */
    private val agentExecutor: AgentExecutor by lazy { AgentExecutor.getInstance(project) }

    /** McpServerManager */
    private val mcpServerManager: McpServerManager by lazy { McpServerManager.getInstance(project) }

    /** PromptOptimizer */
    private val promptOptimizer: PromptOptimizer by lazy { PromptOptimizer.getInstance(project) }

    /** MultimodalInputHandler */
    private val multimodalInputHandler: MultimodalInputHandler by lazy { MultimodalInputHandler.getInstance(project) }

    /** ContextManager */
    private val contextManager: ContextManager by lazy { ContextManager.getInstance(project) }

    /** TaskProgressTracker */
    private val taskProgressTracker: TaskProgressTracker by lazy { TaskProgressTracker.getInstance(project) }

    /** SessionStorage */
    private val sessionStorage: SessionStorage by lazy { SessionStorage.getInstance(project) }

    /** 内存中的 Chat 配置（不持久化） */
    private val chatConfig: MutableMap<String, Any?> = mutableMapOf(
        "conversationMode" to ConversationMode.AUTO.name,
        "currentAgentId" to null as String?,
        "streamingEnabled" to true
    )

    /** 连接状态 */
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** 是否已初始化 */
    private var isInitialized = false

    /**
     * 初始化浏览器
     */
    fun init(): JBCefBrowser {
        if (browser != null) {
            return browser!!
        }

        browser = JBCefBrowser()

        // 设置 JS 查询回调
        setupJsQuery()

        // 设置页面加载监听
        setupLoadListener()

        isInitialized = true
        log.info("CefBrowserPanel initialized")

        return browser!!
    }

    /**
     * 设置页面加载监听
     * 使用 JBCefClient 的 addLoadHandler 回调
     */
    private fun setupLoadListener() {
        if (loadListenerSetup) return
        browser?.let { b ->
            try {
                val client = b.jbCefClient
                // 使用反射查找合适的 load handler 接口
                val handlerClass = Class.forName("org.cef.handler.CefLoadHandler")
                val handler = java.lang.reflect.Proxy.newProxyInstance(
                    handlerClass.classLoader,
                    arrayOf(handlerClass)
                ) { _, method, args ->
                    when (method.name) {
                        "onLoadingStateChange" -> {
                            if (args.size >= 4) {
                                val isLoading = args[0] as Boolean
                                val url = args[1] as String
                                if (!isLoading && url.isNotEmpty()) {
                                    log.debug("Page loaded: $url")
                                    isPageLoaded = true
                                    loadListeners.forEach { it.run() }
                                    loadListeners.clear()
                                }
                            }
                            null
                        }
                        else -> null
                    }
                }
                val addLoadHandlerMethod = client.javaClass.getMethod(
                    "addLoadHandler",
                    Class.forName("org.cef.handler.CefLoadHandler"),
                    Class.forName("org.cef.browser.CefBrowser")
                )
                addLoadHandlerMethod.invoke(client, handler, b.getCefBrowser())
                loadListenerSetup = true
            } catch (e: Exception) {
                log.warn("Failed to setup load listener: ${e.message}")
            }
        }
    }

    /**
     * 等待页面加载完成后执行
     */
    private fun executeWhenPageLoaded(runnable: Runnable) {
        if (isPageLoaded) {
            runnable.run()
        } else {
            loadListeners.add(runnable)
        }
    }

    /**
     * 设置 JS 查询回调
     */
    private fun setupJsQuery() {
        browser?.let { b ->
            jsQueryInvoker = java.util.function.Function<String, JBCefJSQuery.Response> { request ->
                handleJsRequest(request)
            }
            jsQuery = JBCefJSQuery.create(b).also {
                it.addHandler(jsQueryInvoker!!)
            }
        }
    }

    /**
     * 处理来自 JavaScript 的请求
     *
     * @param request JSON 格式的请求字符串
     * @return JBCefJSQuery.Response
     */
    private fun handleJsRequest(request: String): JBCefJSQuery.Response {
        return try {
            val json = JsonUtils.parseObject(request) ?: return JBCefJSQuery.Response("")
            val queryId = json.get("queryId")?.asInt ?: 0
            val action = json.get("action")?.asString ?: ""
            val params = json.get("params")

            log.debug("JS request: action=$action, queryId=$queryId")

            // 处理各种 action
            val response = when (action) {
                // Messaging
                "sendMessage" -> handleSendMessage(queryId, params)
                "streamMessage" -> handleStreamMessage(queryId, params)
                "cancelStreaming" -> handleCancelStreaming(queryId, params)
                "sendMultimodalMessage" -> handleSendMultimodalMessage(queryId, params)
                // Config
                "getConfig" -> handleGetConfig(queryId, params)
                "setConfig" -> handleSetConfig(queryId, params)
                "updateConfig" -> handleUpdateConfig(queryId, params)
                "getModelConfig" -> handleGetModelConfig(queryId, params)
                "updateModelConfig" -> handleUpdateModelConfig(queryId, params)
                "getProviders" -> handleGetProviders(queryId)
                "getProviderModels" -> handleGetProviderModels(queryId, params)
                // Provider Profiles
                "getProviderProfiles" -> handleGetProviderProfiles(queryId)
                "createProviderProfile" -> handleCreateProviderProfile(queryId, params)
                "updateProviderProfile" -> handleUpdateProviderProfile(queryId, params)
                "deleteProviderProfile" -> handleDeleteProviderProfile(queryId, params)
                "setActiveProviderProfile" -> handleSetActiveProviderProfile(queryId, params)
                "reorderProviderProfiles" -> handleReorderProviderProfiles(queryId, params)
                "convertCcSwitchProfile" -> handleConvertCcSwitchProfile(queryId, params)
                // Theme
                "updateTheme" -> handleUpdateTheme(queryId, params)
                "getThemes" -> handleGetThemes(queryId)
                "saveCustomTheme" -> handleSaveCustomTheme(queryId, params)
                "deleteCustomTheme" -> handleDeleteCustomTheme(queryId, params)
                "optimizePrompt" -> handleOptimizePrompt(queryId, params)
                // IDE Theme
                "getIdeTheme" -> handleGetIdeTheme(queryId)
                // Session
                "createSession" -> handleCreateSession(queryId, params)
                "switchSession" -> handleSwitchSession(queryId, params)
                "deleteSession" -> handleDeleteSession(queryId, params)
                "searchSessions" -> handleSearchSessions(queryId, params)
                "exportSession" -> handleExportSession(queryId, params)
                "importSession" -> handleImportSession(queryId, params)
                // Skills
                "getSkills" -> handleGetSkills(queryId)
                "saveSkill" -> handleSaveSkill(queryId, params)
                "deleteSkill" -> handleDeleteSkill(queryId, params)
                "executeSkill" -> handleExecuteSkill(queryId, params)
                // Agents
                "getAgents" -> handleGetAgents(queryId)
                "saveAgent" -> handleSaveAgent(queryId, params)
                "deleteAgent" -> handleDeleteAgent(queryId, params)
                "startAgent" -> handleStartAgent(queryId, params)
                "stopAgent" -> handleStopAgent(queryId, params)
                // MCP
                "getMcpServers" -> handleGetMcpServers(queryId)
                "saveMcpServer" -> handleSaveMcpServer(queryId, params)
                "deleteMcpServer" -> handleDeleteMcpServer(queryId, params)
                "startMcpServer" -> handleStartMcpServer(queryId, params)
                "stopMcpServer" -> handleStopMcpServer(queryId, params)
                "testMcpServer" -> handleTestMcpServer(queryId, params)
                // Interactive
                "submitAnswer" -> handleSubmitAnswer(queryId, params)
                // Chat Config
                "getChatConfig" -> handleGetChatConfig(queryId)
                "updateChatConfig" -> handleUpdateChatConfig(queryId, params)
                // Conversation Mode
                "getConversationModes" -> handleGetConversationModes(queryId)
                // Settings
                "openSettings" -> handleOpenSettings(queryId, params)
                // Session
                "getHistorySessions" -> handleGetHistorySessions(queryId)
                "confirmSession" -> handleConfirmSession(queryId, params)
                // Task Status
                "getTaskStatus" -> handleGetTaskStatus(queryId)
                // Slash Commands
                "executeSlashCommand" -> handleExecuteSlashCommand(queryId, params)
                // Editor Integration
                "getSelectedText" -> handleGetSelectedText(queryId)
                "replaceSelectedText" -> handleReplaceSelectedText(queryId, params)
                else -> {
                    log.warn("Unknown action: $action")
                    null
                }
            }

            // 通过事件系统发送响应（使用 action 名作为事件名，JS EventBus 监听具体 action）
            sendResponseToJs(action, queryId, response)
            // 返回空响应表示我们已经通过事件系统处理了响应
            JBCefJSQuery.Response("")
        } catch (e: Exception) {
            log.error("Error handling JS request: ${e.message}", e)
            JBCefJSQuery.Response("")
        }
    }

    /**
     * 发送响应到 JavaScript
     * @param action  原始 action 名（用于调试）
     * @param queryId 查询 ID
     * @param response 响应数据（null 表示错误）
     * @param error 错误信息（可选）
     */
    private fun sendResponseToJs(action: String, queryId: Int, response: Any?, error: String? = null) {
        // java-bridge.ts 监听 'response' 事件，格式: {queryId, result, error}
        val data: Map<String, Any> = when {
            error != null -> mapOf("queryId" to queryId, "error" to error)
            response != null -> mapOf("queryId" to queryId, "result" to response)
            else -> mapOf("queryId" to queryId)
        }
        sendToJavaScript("response", data)
    }

    // ---- Action Handlers ----

    private fun handleSendMessage(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        // 解析前端发送的参数：可能是 { message: "{...}" } 或直接是 {...}
        var sessionId = ""
        var content = ""
        var messageId = ""

        if (params?.isJsonObject == true) {
            val jsonParams = params.asJsonObject
            // 检查是否有 message 字段（JSON 字符串格式）
            val messageStr = jsonParams.get("message")?.asString
            if (messageStr != null) {
                // 解析内层 JSON
                val payload = JsonUtils.parseObject(messageStr)?.asJsonObject
                sessionId = payload?.get("sessionId")?.asString ?: ""
                content = payload?.get("content")?.asString ?: ""
                messageId = payload?.get("messageId")?.asString ?: ""
            } else {
                // 直接字段格式
                sessionId = jsonParams.get("sessionId")?.asString ?: ""
                content = jsonParams.get("content")?.asString ?: ""
                messageId = jsonParams.get("messageId")?.asString ?: ""
            }
        } else {
            content = params?.asString ?: ""
        }

        // 保存 messageId 到线程上下文，供流式回调使用
        val contextMessageId = messageId.ifEmpty { "default" }

        bridgeManager.sendMessage(
            message = content,
            sessionId = sessionId,
            callback = object : com.github.xingzhewa.ccgui.bridge.StreamCallback {
                override fun onLineReceived(line: String) {
                    // JS EventBus.STREAMING_CHUNK = "streaming:chunk"
                    sendToJavaScript("streaming:chunk", mapOf("messageId" to contextMessageId, "chunk" to line))
                }

                override fun onStreamComplete(messages: List<String>) {
                    // JS EventBus.STREAMING_COMPLETE = "streaming:complete"
                    sendToJavaScript("streaming:complete", mapOf("messageId" to contextMessageId, "messages" to messages))
                }

                override fun onStreamError(error: String) {
                    // JS EventBus.STREAMING_ERROR = "streaming:error"
                    sendToJavaScript("streaming:error", mapOf("messageId" to contextMessageId, "error" to error))
                }
            },
            // 流结束后通过 'response' 事件 resolve/reject 前端 javaBridge.invoke() 的 promise
            onResponse = { result, error ->
                sendResponseToJs("sendMessage", queryId, result, error)
            }
        )
        return null
    }

    private fun handleStreamMessage(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        // 解析参数（与 handleSendMessage 相同）
        var sessionId = ""
        var content = ""
        var messageId = ""

        if (params?.isJsonObject == true) {
            val jsonParams = params.asJsonObject
            val messageStr = jsonParams.get("message")?.asString
            if (messageStr != null) {
                val payload = JsonUtils.parseObject(messageStr)?.asJsonObject
                sessionId = payload?.get("sessionId")?.asString ?: ""
                content = payload?.get("content")?.asString ?: ""
                messageId = payload?.get("messageId")?.asString ?: ""
            } else {
                sessionId = jsonParams.get("sessionId")?.asString ?: ""
                content = jsonParams.get("content")?.asString ?: ""
                messageId = jsonParams.get("messageId")?.asString ?: ""
            }
        } else {
            content = params?.asString ?: ""
        }

        val contextMessageId = messageId.ifEmpty { "default" }

        bridgeManager.streamMessage(
            message = content,
            sessionId = sessionId,
            callback = object : com.github.xingzhewa.ccgui.bridge.StreamCallback {
                override fun onLineReceived(line: String) {
                    sendToJavaScript("streaming:chunk", mapOf("messageId" to contextMessageId, "chunk" to line))
                }

                override fun onStreamComplete(messages: List<String>) {
                    sendToJavaScript("streaming:complete", mapOf("messageId" to contextMessageId, "messages" to messages))
                }

                override fun onStreamError(error: String) {
                    sendToJavaScript("streaming:error", mapOf("messageId" to contextMessageId, "error" to error))
                }
            },
            onResponse = { result, error ->
                sendResponseToJs("streamMessage", queryId, result, error)
            }
        )
        return null
    }

    private fun handleCancelStreaming(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val sessionId = params?.asJsonObject?.get("sessionId")?.asString ?: ""
        bridgeManager.cancelStreaming(sessionId)
        return null
    }

    private fun handleGetConfig(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val config = configManager.getAppConfig()
        return mapOf(
            "currentThemeId" to config.currentThemeId,
            "conversationMode" to config.conversationMode.name,
            "modelConfig" to mapOf(
                "provider" to config.modelConfig.provider,
                "model" to config.modelConfig.model
            ),
            "autoConnect" to config.autoConnect,
            "streamOutput" to config.streamOutput,
            "showLineNumbers" to config.showLineNumbers,
            "enableSpellCheck" to config.enableSpellCheck,
            "maxSessionHistory" to config.maxSessionHistory
        )
    }

    private fun handleSetConfig(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val current = configManager.getAppConfig()
        val updated = current.copy(
            currentThemeId = jsonObj.get("currentThemeId")?.asString ?: current.currentThemeId,
            autoConnect = jsonObj.get("autoConnect")?.asBoolean ?: current.autoConnect,
            streamOutput = jsonObj.get("streamOutput")?.asBoolean ?: current.streamOutput,
            showLineNumbers = jsonObj.get("showLineNumbers")?.asBoolean ?: current.showLineNumbers,
            enableSpellCheck = jsonObj.get("enableSpellCheck")?.asBoolean ?: current.enableSpellCheck
        )
        configManager.saveAppConfig(updated)
        return mapOf("success" to true)
    }

    private fun handleCreateSession(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject
        val name = jsonObj?.get("name")?.asString ?: "New Session"
        val typeStr = jsonObj?.get("type")?.asString ?: "PROJECT"
        val type = try { SessionType.valueOf(typeStr) } catch (_: Exception) { SessionType.PROJECT }
        val session = sessionManager.createSession(name, type)
        return mapOf("id" to session.id, "name" to session.name, "type" to session.type.name)
    }

    private fun handleSwitchSession(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val sessionId = params?.asJsonObject?.get("sessionId")?.asString ?: return null
        sessionManager.setCurrentSession(sessionId)
        return mapOf("success" to true, "currentSessionId" to sessionId)
    }

    private fun handleDeleteSession(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val sessionId = params?.asJsonObject?.get("sessionId")?.asString ?: return null
        sessionManager.deleteSession(sessionId)
        return mapOf("success" to true)
    }

    private fun handleGetThemes(queryId: Int): Any? {
        val themes = configManager.getAllThemes()
        return themes.map { theme ->
            mapOf(
                "id" to theme.id,
                "name" to theme.name,
                "isDark" to theme.isDark
            )
        }
    }

    private fun handleSubmitAnswer(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val questionId = jsonObj.get("questionId")?.asString ?: return null
        val typeStr = jsonObj.get("type")?.asString ?: "text"
        val answer = when (typeStr) {
            "confirmation" -> {
                val allowed = jsonObj.get("answer")?.asBoolean ?: false
                InteractiveRequestEngine.QuestionAnswer.Confirmation(allowed)
            }
            "singleChoice" -> {
                val optionStr = jsonObj.get("answer")?.asString ?: return null
                val option = com.github.xingzhewa.ccgui.model.interaction.QuestionOption(optionStr, optionStr)
                InteractiveRequestEngine.QuestionAnswer.SingleChoice(option)
            }
            "multipleChoice" -> {
                val optionsArr = jsonObj.get("answer")?.asJsonArray ?: return null
                val options = optionsArr.map {
                    val s = it.asString
                    com.github.xingzhewa.ccgui.model.interaction.QuestionOption(s, s)
                }
                InteractiveRequestEngine.QuestionAnswer.MultipleChoice(options)
            }
            "number" -> {
                val num = jsonObj.get("answer")?.asDouble ?: return null
                InteractiveRequestEngine.QuestionAnswer.NumberInput(num)
            }
            else -> {
                val answerStr = jsonObj.get("answer")?.asString ?: return null
                InteractiveRequestEngine.QuestionAnswer.TextInput(answerStr)
            }
        }
        interactiveEngine.submitAnswer(questionId, answer)
        return mapOf("success" to true)
    }

    // ---- Multimodal ----

    private fun handleSendMultimodalMessage(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonParams = params?.asJsonObject ?: return null
        val messageObj = jsonParams.get("message")?.asJsonObject ?: return null
        val sessionId = messageObj.get("sessionId")?.asString ?: ""
        val content = messageObj.get("content")?.asString ?: ""
        val attachments = messageObj.get("attachments")?.asJsonArray

        if (attachments == null || attachments.size() == 0) {
            // No attachments, delegate to regular send
            return handleSendMessage(queryId, params)
        }

        // Build text representation of attachments appended to content
        // Claude Code CLI doesn't natively support inline images,
        // so we format attachments as text references in the prompt
        val attachmentText = buildString {
            append("\n\n[Attachments]\n")
            for (i in 0 until attachments.size()) {
                val attachment = attachments.get(i).asJsonObject
                when (attachment.get("type")?.asString) {
                    "image" -> {
                        val mimeType = attachment.get("mimeType")?.asString ?: "image/png"
                        val data = attachment.get("data")?.asString ?: ""
                        val sizeKB = data.length / 1024
                        append("[Image #$i: data:$mimeType;base64,$data]\n")
                        append("<!-- Image $i (${sizeKB}KB) - base64 encoded -->\n")
                    }
                    "file" -> {
                        val name = attachment.get("name")?.asString ?: "file"
                        val fileContent = attachment.get("content")?.asString ?: ""
                        append("[$name]\n$fileContent\n[/$name]\n")
                    }
                }
            }
            append("[/Attachments]\n")
        }

        val fullContent = content + attachmentText

        // Send via streamMessage (same as regular message but with attachment text)
        bridgeManager.streamMessage(
            message = fullContent,
            sessionId = sessionId,
            callback = object : com.github.xingzhewa.ccgui.bridge.StreamCallback {
                override fun onLineReceived(line: String) {
                    sendToJavaScript("streaming:chunk", mapOf("chunk" to line))
                }

                override fun onStreamComplete(messages: List<String>) {
                    sendToJavaScript("streaming:complete", mapOf("messages" to messages))
                }

                override fun onStreamError(error: String) {
                    sendToJavaScript("streaming:error", mapOf("error" to error))
                }
            },
            onResponse = { result, error ->
                sendResponseToJs("sendMultimodalMessage", queryId, result, error)
            }
        )
        return null
    }

    // ---- Model Config ----

    private fun handleGetModelConfig(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        // 优先从激活的 Profile 读取，兼容无 Profile 场景
        val modelConfig = configManager.getActiveModelConfig()
        return mapOf(
            "provider" to modelConfig.provider,
            "model" to modelConfig.model,
            "apiKey" to (modelConfig.apiKey ?: ""),
            "baseUrl" to (modelConfig.baseUrl ?: ""),
            "maxRetries" to modelConfig.maxRetries
        )
    }

    private fun handleUpdateModelConfig(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val currentAppConfig = configManager.getAppConfig()
        val activeProfileId = currentAppConfig.activeProfileId
        val currentModelConfig = configManager.getActiveModelConfig()

        val updatedModelConfig = com.github.xingzhewa.ccgui.model.config.ModelConfig(
            provider = jsonObj.get("provider")?.asString ?: currentModelConfig.provider,
            model = jsonObj.get("model")?.asString ?: currentModelConfig.model,
            apiKey = jsonObj.get("apiKey")?.asString?.takeIf { it.isNotEmpty() } ?: currentModelConfig.apiKey,
            baseUrl = jsonObj.get("baseUrl")?.asString?.takeIf { it.isNotEmpty() } ?: currentModelConfig.baseUrl,
            maxRetries = jsonObj.get("maxRetries")?.asInt ?: currentModelConfig.maxRetries
        )

        // 如果有激活的 Profile，更新 Profile；否则更新默认 modelConfig
        if (activeProfileId != null) {
            val profile = currentAppConfig.providerProfiles.find { it.id == activeProfileId }
            if (profile != null) {
                configManager.saveProviderProfile(profile.copy(
                    provider = updatedModelConfig.provider,
                    model = updatedModelConfig.model,
                    apiKey = updatedModelConfig.apiKey,
                    baseUrl = updatedModelConfig.baseUrl,
                    maxRetries = updatedModelConfig.maxRetries
                ))
            }
        } else {
            configManager.updateModelConfig(updatedModelConfig)
        }
        return mapOf("success" to true)
    }

    private fun handleGetProviders(queryId: Int): Any? {
        return com.github.xingzhewa.ccgui.model.config.ModelConfig.getAllProviders()
    }

    private fun handleGetProviderModels(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val provider = params?.asJsonObject?.get("provider")?.asString ?: "anthropic"
        return com.github.xingzhewa.ccgui.model.config.ModelConfig.getProviderModels(provider)
    }

    // ---- Provider Profiles ----

    private fun handleGetProviderProfiles(queryId: Int): Any? {
        val profiles = configManager.getProviderProfiles()
        val activeId = configManager.getActiveProfileId()
        val profilesData = profiles.map { profile ->
            mapOf(
                "id" to profile.id,
                "name" to profile.name,
                "provider" to profile.provider,
                "source" to profile.source,
                "model" to profile.model,
                "apiKey" to (profile.apiKey ?: ""),
                "baseUrl" to (profile.baseUrl ?: ""),
                "sonnetModel" to (profile.sonnetModel ?: ""),
                "opusModel" to (profile.opusModel ?: ""),
                "maxModel" to (profile.maxModel ?: ""),
                "maxRetries" to profile.maxRetries,
                "createdAt" to profile.createdAt,
                "updatedAt" to profile.updatedAt
            )
        }
        return mapOf(
            "profiles" to profilesData,
            "activeProfileId" to activeId
        )
    }

    private fun handleCreateProviderProfile(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val profile = com.github.xingzhewa.ccgui.model.config.ProviderProfile.fromJson(jsonObj)
        configManager.saveProviderProfile(profile)
        return mapOf("success" to true, "id" to profile.id)
    }

    private fun handleUpdateProviderProfile(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val profile = com.github.xingzhewa.ccgui.model.config.ProviderProfile.fromJson(jsonObj)
        configManager.saveProviderProfile(profile)
        return mapOf("success" to true)
    }

    private fun handleDeleteProviderProfile(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val profileId = params?.asJsonObject?.get("profileId")?.asString ?: return null
        configManager.deleteProviderProfile(profileId)
        return mapOf("success" to true)
    }

    private fun handleSetActiveProviderProfile(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val profileId = jsonObj.get("profileId")?.asString?.takeIf { it.isNotEmpty() }
        configManager.setActiveProviderProfile(profileId)
        return mapOf("success" to true)
    }

    /**
     * 重新排序 Provider Profiles
     */
    private fun handleReorderProviderProfiles(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val orderedIdsArray = jsonObj.get("orderedIds")?.asJsonArray ?: return null
        val orderedIds = orderedIdsArray.map { it.asString }
        configManager.reorderProviderProfiles(orderedIds)
        return mapOf("success" to true)
    }

    /**
     * 转换 cc-switch 配置为本地配置
     */
    private fun handleConvertCcSwitchProfile(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val profileId = params?.asJsonObject?.get("profileId")?.asString ?: return null
        val newProfile = configManager.convertCcSwitchProfile(profileId)
        return if (newProfile != null) {
            mapOf("success" to true, "profile" to mapOf(
                "id" to newProfile.id,
                "name" to newProfile.name,
                "provider" to newProfile.provider,
                "source" to newProfile.source,
                "model" to newProfile.model,
                "apiKey" to (newProfile.apiKey ?: ""),
                "baseUrl" to (newProfile.baseUrl ?: ""),
                "sonnetModel" to (newProfile.sonnetModel ?: ""),
                "opusModel" to (newProfile.opusModel ?: ""),
                "maxModel" to (newProfile.maxModel ?: ""),
                "maxRetries" to newProfile.maxRetries,
                "createdAt" to newProfile.createdAt,
                "updatedAt" to newProfile.updatedAt
            ))
        } else {
            mapOf("success" to false, "error" to "Failed to convert profile")
        }
    }

    // ---- Chat Config ----

    private fun handleGetChatConfig(queryId: Int): Any? {
        return chatConfig.toMap()
    }

    private fun handleUpdateChatConfig(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        jsonObj.get("conversationMode")?.asString?.let {
            chatConfig["conversationMode"] = it
        }
        jsonObj.get("currentAgentId")?.let { el ->
            chatConfig["currentAgentId"] = if (el.isJsonNull) null else el.asString
        }
        jsonObj.get("streamingEnabled")?.asBoolean?.let {
            chatConfig["streamingEnabled"] = it
        }
        return mapOf("success" to true)
    }

    // ---- Conversation Mode ----

    private fun handleGetConversationModes(queryId: Int): Any? {
        return ConversationMode.getAllModeDescriptions()
    }

    // ---- Settings ----

    private fun handleOpenSettings(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val tabId = params?.asJsonObject?.get("tabId")?.asString
        // 发送事件通知前端打开设置面板
        sendToJavaScript("ui.settings.open", mapOf("tabId" to (tabId ?: "")))
        return mapOf("success" to true)
    }

    // ---- Session ----

    private fun handleGetHistorySessions(queryId: Int): Any? {
        val sessions = sessionManager.getHistorySessions()
        return sessions.map { session ->
            mapOf(
                "id" to session.id,
                "name" to session.name,
                "type" to session.type.name,
                "messageCount" to session.messages.size,
                "createdAt" to session.createdAt,
                "updatedAt" to session.updatedAt,
                "isPending" to session.isPending
            )
        }
    }

    private fun handleConfirmSession(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val sessionId = params?.asJsonObject?.get("sessionId")?.asString ?: return null
        sessionManager.confirmSession(sessionId)
        return mapOf("success" to true)
    }

    // ---- Task Status ----

    private fun handleGetTaskStatus(queryId: Int): Any? {
        val tasks = taskProgressTracker.getActiveTasks().map { task ->
            mapOf(
                "taskId" to task.taskId,
                "name" to task.name,
                "status" to task.status.name,
                "currentStep" to task.currentStepIndex,
                "totalSteps" to task.steps.size,
                "progress" to task.progress
            )
        }
        val subagents = agentExecutor.getActiveExecutionSummaries()
        return mapOf(
            "tasks" to tasks,
            "activeSubagents" to subagents,
            "diffRecords" to emptyList<Map<String, Any>>()  // Diff 解析后续在 Phase 4 实现
        )
    }

    // ---- Slash Commands ----

    private fun handleExecuteSlashCommand(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val command = jsonObj.get("command")?.asString ?: return null
        val sessionId = sessionManager.getCurrentSession()?.id ?: return null

        return when {
            command.startsWith("/compact") -> {
                scope.launch {
                    contextManager.compact(sessionId)
                }
                mapOf("success" to true, "message" to "Context compaction triggered")
            }
            command.startsWith("/clear") -> {
                scope.launch {
                    sessionManager.clearSession(sessionId)
                }
                mapOf("success" to true, "message" to "Session cleared")
            }
            command.startsWith("/retry") -> {
                // 重试上一条消息：从当前会话获取最后一条用户消息，重新发送
                val lastUserMessage = sessionManager.getSession(sessionId)
                    ?.messages
                    ?.filter { it.role == MessageRole.USER }
                    ?.lastOrNull()
                if (lastUserMessage != null) {
                    bridgeManager.streamMessage(
                        message = lastUserMessage.content,
                        sessionId = sessionId,
                        callback = object : com.github.xingzhewa.ccgui.bridge.StreamCallback {
                            override fun onStreamStart() {}
                            override fun onLineReceived(line: String) {}
                            override fun onStreamComplete(leaves: List<String>) {}
                            override fun onStreamError(error: String) {}
                            override fun onStreamCancelled() {}
                        },
                        onResponse = null
                    )
                }
                mapOf("success" to true, "message" to "Retry triggered")
            }
            command.startsWith("/export") -> {
                val session = sessionManager.getSession(sessionId)
                mapOf(
                    "success" to true,
                    "session" to mapOf(
                        "id" to session?.id,
                        "name" to session?.name,
                        "messageCount" to (session?.messages?.size ?: 0)
                    )
                )
            }
            else -> {
                mapOf("success" to false, "error" to "Unknown command: $command")
            }
        }
    }

    // ---- Editor Integration ----

    private fun handleGetSelectedText(queryId: Int): Any? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val selectedText = editor?.selectionModel?.selectedText ?: ""
        val fileName = editor?.virtualFile?.name ?: ""
        val language = editor?.virtualFile?.extension ?: ""
        return mapOf(
            "text" to selectedText,
            "fileName" to fileName,
            "language" to language,
            "hasSelection" to selectedText.isNotEmpty()
        )
    }

    private fun handleReplaceSelectedText(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val newText = jsonObj.get("text")?.asString ?: return null
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) {
            return mapOf("success" to false, "error" to "No text selected")
        }
        val start = selectionModel.selectionStart
        val end = selectionModel.selectionEnd
        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.replaceString(start, end, newText)
            selectionModel.removeSelection()
        }
        return mapOf("success" to true)
    }

    // ---- Config ----

    private fun handleUpdateConfig(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val current = configManager.getAppConfig()
        val updated = current.copy(
            currentThemeId = jsonObj.get("currentThemeId")?.asString ?: current.currentThemeId,
            autoConnect = jsonObj.get("autoConnect")?.asBoolean ?: current.autoConnect,
            streamOutput = jsonObj.get("streamOutput")?.asBoolean ?: current.streamOutput,
            showLineNumbers = jsonObj.get("showLineNumbers")?.asBoolean ?: current.showLineNumbers,
            enableSpellCheck = jsonObj.get("enableSpellCheck")?.asBoolean ?: current.enableSpellCheck
        )
        configManager.saveAppConfig(updated)
        return mapOf("success" to true)
    }

    private fun handleUpdateTheme(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val themeId = params?.asJsonObject?.get("themeId")?.asString ?: return null
        configManager.setCurrentTheme(themeId)
        return mapOf("success" to true)
    }

    private fun handleSaveCustomTheme(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val id = jsonObj.get("id")?.asString ?: return null
        val name = jsonObj.get("name")?.asString ?: return null
        val isDark = jsonObj.get("isDark")?.asBoolean ?: false
        val colors = jsonObj.get("colors")?.asJsonObject
        if (colors == null) return null
        val theme = com.github.xingzhewa.ccgui.model.config.ThemeConfig(
            id = id,
            name = name,
            isDark = isDark,
            colors = com.github.xingzhewa.ccgui.model.config.ColorScheme(
                primary = colors.get("primary")?.asString ?: "#3b82f6",
                background = colors.get("background")?.asString ?: "#ffffff",
                foreground = colors.get("foreground")?.asString ?: "#333333",
                muted = colors.get("muted")?.asString ?: "#f5f5f5",
                mutedForeground = colors.get("mutedForeground")?.asString ?: "#666666",
                accent = colors.get("accent")?.asString ?: "#3b82f6",
                accentForeground = colors.get("accentForeground")?.asString ?: "#ffffff",
                destructive = colors.get("destructive")?.asString ?: "#ef4444",
                border = colors.get("border")?.asString ?: "#e5e5e5",
                userMessage = colors.get("userMessage")?.asString ?: "#3b82f6",
                aiMessage = colors.get("aiMessage")?.asString ?: "#f1f5f9",
                systemMessage = colors.get("systemMessage")?.asString ?: "#f59e0b",
                codeBackground = colors.get("codeBackground")?.asString ?: "#1e1e1e",
                codeForeground = colors.get("codeForeground")?.asString ?: "#d4d4d4"
            )
        )
        configManager.saveCustomTheme(theme)
        return mapOf("success" to true)
    }

    private fun handleDeleteCustomTheme(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val themeId = params?.asJsonObject?.get("themeId")?.asString ?: return null
        configManager.deleteCustomTheme(themeId)
        return mapOf("success" to true)
    }

    private fun handleOptimizePrompt(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val prompt = params?.asJsonObject?.get("prompt")?.asString ?: ""
        scope.launch {
            try {
                val result = promptOptimizer.optimizePrompt(prompt)
                sendResponseToJs("optimizePrompt", queryId, mapOf(
                    "optimizedPrompt" to result.optimizedPrompt,
                    "addedContextCount" to result.addedContext.size,
                    "improvements" to result.improvements,
                    "confidence" to result.confidence
                ))
            } catch (e: Exception) {
                log.warn("Optimize prompt failed: ${e.message}")
                sendResponseToJs("optimizePrompt", queryId, null, e.message ?: "优化失败")
            }
        }
        return null  // async response
    }

    // ---- IDE Theme ----

    private fun handleGetIdeTheme(queryId: Int): Any? {
        val isDark = com.intellij.util.ui.UIUtil.isUnderDarcula()
        return mapOf("isDark" to isDark)
    }

    // ---- Session ----

    private fun handleSearchSessions(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val query = params?.asJsonObject?.get("query")?.asString ?: ""
        val sessions = sessionManager.searchSessions(query)
        return sessions.map { session ->
            mapOf(
                "id" to session.id,
                "name" to session.name,
                "type" to session.type.name,
                "messageCount" to session.messages.size,
                "createdAt" to session.createdAt,
                "updatedAt" to session.updatedAt
            )
        }
    }

    private fun handleExportSession(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val sessionId = params?.asJsonObject?.get("sessionId")?.asString ?: return null
        val session = sessionManager.getSession(sessionId) ?: return null
        return session.toJson()
    }

    private fun handleImportSession(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val jsonContent = jsonObj.get("data")?.asString ?: return null
        val session = sessionManager.importSession(jsonContent) ?: return null
        return mapOf("id" to session.id, "name" to session.name)
    }

    // ---- Skills ----

    private fun handleGetSkills(queryId: Int): Any? {
        val skills = skillsManager.getAllSkills()
        return skills.map { skill ->
            mapOf(
                "id" to skill.id,
                "name" to skill.name,
                "description" to skill.description,
                "category" to skill.category.name,
                "enabled" to skill.enabled
            )
        }
    }

    private fun handleSaveSkill(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val id = jsonObj.get("id")?.asString ?: return null
        val name = jsonObj.get("name")?.asString ?: return null
        val description = jsonObj.get("description")?.asString ?: ""
        val prompt = jsonObj.get("prompt")?.asString ?: ""
        val categoryStr = jsonObj.get("category")?.asString ?: "CODE_GENERATION"
        val category = try {
            com.github.xingzhewa.ccgui.model.skill.SkillCategory.valueOf(categoryStr)
        } catch (_: Exception) {
            com.github.xingzhewa.ccgui.model.skill.SkillCategory.CODE_GENERATION
        }
        val skill = Skill(
            id = id,
            name = name,
            description = description,
            prompt = prompt,
            category = category
        )
        val success = skillsManager.addSkill(skill)
        return mapOf("success" to success)
    }

    private fun handleDeleteSkill(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val skillId = params?.asJsonObject?.get("skillId")?.asString ?: return null
        val success = skillsManager.deleteSkill(skillId)
        return mapOf("success" to success)
    }

    private fun handleExecuteSkill(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val skillId = jsonObj.get("skillId")?.asString ?: return null
        val skill = skillsManager.getSkill(skillId) ?: return mapOf("error" to "Skill not found")
        val context = com.github.xingzhewa.ccgui.model.skill.ExecutionContext()
        // SkillExecutor.executeSkill 是 suspend，需要在协程中调用
        // 这里返回成功提交，实际异步执行通过事件系统通知前端
        scope.launch {
            try {
                val result = skillExecutor.executeSkill(skill, context)
                sendToJavaScript("skill:result", mapOf("skillId" to skillId, "result" to result.toString()))
            } catch (e: Exception) {
                sendToJavaScript("skill:error", mapOf("skillId" to skillId, "error" to (e.message ?: "Unknown error")))
            }
        }
        return mapOf("submitted" to true)
    }

    // ---- Agents ----

    private fun handleGetAgents(queryId: Int): Any? {
        val agents = agentsManager.getAllAgents()
        return agents.map { agent ->
            mapOf(
                "id" to agent.id,
                "name" to agent.name,
                "description" to agent.description,
                "capabilities" to agent.capabilities.map { it.name },
                "enabled" to agent.enabled
            )
        }
    }

    private fun handleSaveAgent(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val id = jsonObj.get("id")?.asString ?: return null
        val name = jsonObj.get("name")?.asString ?: return null
        val description = jsonObj.get("description")?.asString ?: ""
        val systemPrompt = jsonObj.get("systemPrompt")?.asString ?: ""
        val agent = Agent(
            id = id,
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            capabilities = emptyList()
        )
        val success = agentsManager.addAgent(agent)
        return mapOf("success" to success)
    }

    private fun handleDeleteAgent(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val agentId = params?.asJsonObject?.get("agentId")?.asString ?: return null
        val success = agentsManager.deleteAgent(agentId)
        return mapOf("success" to success)
    }

    private fun handleStartAgent(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val agentId = jsonObj.get("agentId")?.asString ?: return null
        val taskDesc = jsonObj.get("task")?.asString ?: ""
        val agent = agentsManager.getAgent(agentId) ?: return mapOf("error" to "Agent not found")
        val capability = agent.capabilities.firstOrNull() ?: com.github.xingzhewa.ccgui.model.agent.AgentCapability.CODE_GENERATION
        val task = com.github.xingzhewa.ccgui.model.agent.AgentTask(
            description = taskDesc,
            requiredCapability = capability
        )
        scope.launch {
            try {
                val result = agentExecutor.executeAgent(agent, task)
                sendToJavaScript("agent:result", mapOf("agentId" to agentId, "result" to result.toString()))
            } catch (e: Exception) {
                sendToJavaScript("agent:error", mapOf("agentId" to agentId, "error" to (e.message ?: "Unknown error")))
            }
        }
        return mapOf("submitted" to true)
    }

    private fun handleStopAgent(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val agentId = params?.asJsonObject?.get("agentId")?.asString ?: return null
        agentsManager.markTaskCompleted(agentId, false)
        return mapOf("success" to true)
    }

    // ---- MCP ----

    private fun handleGetMcpServers(queryId: Int): Any? {
        val servers = mcpServerManager.getAllServers()
        return servers.map { server ->
            mapOf(
                "id" to server.id,
                "name" to server.name,
                "description" to server.description,
                "command" to server.command,
                "enabled" to server.enabled,
                "status" to server.status.name
            )
        }
    }

    private fun handleSaveMcpServer(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val jsonObj = params?.asJsonObject ?: return null
        val id = jsonObj.get("id")?.asString ?: return null
        val name = jsonObj.get("name")?.asString ?: return null
        val description = jsonObj.get("description")?.asString ?: ""
        val command = jsonObj.get("command")?.asString ?: return null
        val server = McpServer(
            id = id,
            name = name,
            description = description,
            command = command,
            args = jsonObj.get("args")?.asJsonArray?.map { it.asString } ?: emptyList(),
            env = emptyMap(),
            scope = com.github.xingzhewa.ccgui.model.mcp.McpScope.PROJECT
        )
        val success = mcpServerManager.addServer(server)
        return mapOf("success" to success)
    }

    private fun handleDeleteMcpServer(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val serverId = params?.asJsonObject?.get("serverId")?.asString ?: return null
        val success = mcpServerManager.deleteServer(serverId)
        return mapOf("success" to success)
    }

    private fun handleStartMcpServer(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val serverId = params?.asJsonObject?.get("serverId")?.asString ?: return null
        scope.launch {
            val success = mcpServerManager.startServer(serverId)
            sendToJavaScript("mcp:serverStatus", mapOf("serverId" to serverId, "success" to success))
        }
        return mapOf("submitted" to true)
    }

    private fun handleStopMcpServer(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val serverId = params?.asJsonObject?.get("serverId")?.asString ?: return null
        scope.launch {
            val success = mcpServerManager.stopServer(serverId)
            sendToJavaScript("mcp:serverStatus", mapOf("serverId" to serverId, "success" to success))
        }
        return mapOf("submitted" to true)
    }

    private fun handleTestMcpServer(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val serverId = params?.asJsonObject?.get("serverId")?.asString ?: return null
        scope.launch {
            val result = mcpServerManager.testServer(serverId)
            val isSuccess = result is com.github.xingzhewa.ccgui.model.mcp.TestResult
            sendToJavaScript("mcp:testResult", mapOf("serverId" to serverId, "success" to isSuccess, "result" to result.toString()))
        }
        return mapOf("submitted" to true)
    }

    // ---- Response Helpers ----

    private fun createErrorResponse(queryId: Int, error: String): String {
        return JsonObject().apply {
            addProperty("queryId", queryId)
            addProperty("error", error)
        }.toString()
    }

    // ---- JavaScript Injection ----

    /**
     * 注入后端 JavaScript Bridge
     *
     * JS→Java 通信方案：
     * 我们不依赖 JBCefJSQuery 的内部机制（其函数名随机且不可预测）。
     * 改用自定义 iframe + postMessage 通道：
     * - 注入一个隐藏 iframe，加载 data URL，内含 JS 脚本
     * - 该脚本定义 __ccJavaRequest(request) 函数
     * - 通过 iframe.contentWindow.postMessage 发送请求到主页面
     * - 主页面收到消息后，调用 window.javaRequestCallback（由 Kotlin 设置）
     * - Kotlin handler 执行后，通过 sendToJavaScript("actionResponse:xxx", data) 返回结果
     *
     * Java→JS 通信：sendToJavaScript() 直接调用 window.ccEvents.emit()
     */
    private fun injectBackendJavaScript() {
        browser?.let { b ->
            // iframe 的 data URL：内嵌脚本监听 postMessage，收到后调用主页面回调
            val iframeDataUrl = (
                "data:text/html;charset=utf-8," +
                "<script>" +
                "window.addEventListener('message',function(e){" +
                "if(e.data&&e.data.type==='cc-java-request'){" +
                "var req=e.data.request;" +
                "var result='';" +
                "if(typeof window.javaRequestCallback==='function'){" +
                "result=window.javaRequestCallback(req);" +
                "}" +
                "e.source.postMessage({type:'cc-java-response',requestId:e.data.requestId,result:result},'*');" +
                "}" +
                "});" +
                "window.parent.postMessage({type:'cc-bridge-ready'},'*');" +
                "</script>"
                ).replace("\n", "")

            // 主页面 bridge 脚本：设置 ccBackend.send() 和 ccEvents
            val bridgeScript = """
                (function() {
                    if (window.ccBackend && window.ccEvents) return;

                    var pendingRequests = {};
                    var requestCounter = 0;
                    var bridgeReady = false;

                    // 监听 iframe 就绪信号
                    window.addEventListener('message', function(e) {
                        if (e.data && e.data.type === 'cc-bridge-ready') {
                            bridgeReady = true;
                            console.log('[CCBackend] Bridge ready');
                        }
                        // 接收来自 iframe 的响应
                        if (e.data && e.data.type === 'cc-java-response' && e.data.requestId) {
                            var callback = pendingRequests[e.data.requestId];
                            if (callback) {
                                delete pendingRequests[e.data.requestId];
                                callback(e.data.result);
                            }
                        }
                    });

                    // Hook ccEvents.emit()：拦截 'response' 事件，将响应路由到 pendingRequests
                    // java-bridge.ts 发送: ccEvents.on('response', ({queryId, result, error}) => ...)
                    if (typeof window.ccEvents !== 'undefined') {
                        var origEmit = window.ccEvents.emit.bind(window.ccEvents);
                        var responseInterceptor = function(event, data) {
                            if (event === 'response' && data && data.queryId) {
                                var cb = pendingRequests[data.queryId];
                                if (cb) {
                                    delete pendingRequests[data.queryId];
                                    cb(data.result);
                                }
                            }
                        };
                        window.ccEvents.emit = function(event, data) {
                            responseInterceptor(event, data);
                            return origEmit(event, data);
                        };
                    }

                    // ccBackend.send()：通过隐藏 iframe 发送请求到 Kotlin
                    window.ccBackend = {
                        send: function(request) {
                            if (!bridgeReady) {
                                console.warn('[CCBackend] Bridge not ready, request dropped');
                                return;
                            }
                            var iframe = document.getElementById('__cc_bridge_iframe__');
                            if (!iframe || !iframe.contentWindow) {
                                console.warn('[CCBackend] Bridge iframe not found');
                                return;
                            }
                            var requestId = ++requestCounter;
                            // 等待响应（iframe 通过 postMessage 返回）
                            pendingRequests[requestId] = function(result) {
                                // 响应由 Kotlin sendToJavaScript("actionResponse:xxx", data) 处理
                                // 这里不做任何事，因为所有响应都通过 ccEvents.emit() 推送
                            };
                            iframe.contentWindow.postMessage({
                                type: 'cc-java-request',
                                requestId: requestId,
                                request: typeof request === 'string' ? request : JSON.stringify(request)
                            }, '*');
                        }
                    };

                    // ccEvents：事件总线，Kotlin sendToJavaScript() 直接调用
                    // 同时使用 CustomEvent 桥接到前端 eventBus
                    window.ccEvents = {
                        handlers: {},
                        on: function(event, handler) {
                            if (!this.handlers[event]) this.handlers[event] = [];
                            this.handlers[event].push(handler);
                            return function() {
                                if (window.ccEvents && window.ccEvents.handlers && window.ccEvents.handlers[event]) {
                                    window.ccEvents.handlers[event] = window.ccEvents.handlers[event].filter(function(h) { return h !== handler; });
                                }
                            };
                        },
                        off: function(event, handler) {
                            if (this.handlers[event]) {
                                this.handlers[event] = this.handlers[event].filter(function(h) { return h !== handler; });
                            }
                        },
                        emit: function(event, data) {
                            if (this.handlers[event]) {
                                this.handlers[event].forEach(function(h) { h(data); });
                            }
                            // 桥接到前端 CustomEvent（让 eventBus 监听 window 事件）
                            window.dispatchEvent(new CustomEvent(event, { detail: data }));
                        }
                    };
                })();
            """.trimIndent()

            // Step 1: 创建隐藏 iframe（用于 JS→Kotlin 通信）
            b.getCefBrowser().executeJavaScript(
                """
                (function() {
                    if (document.getElementById('__cc_bridge_iframe__')) return;
                    var iframe = document.createElement('iframe');
                    iframe.id = '__cc_bridge_iframe__';
                    iframe.src = '$iframeDataUrl';
                    iframe.style.display = 'none';
                    iframe.width = '0';
                    iframe.height = '0';
                    iframe.setAttribute('sandbox', 'allow-scripts');
                    document.body ? document.body.appendChild(iframe) : document.addEventListener('DOMContentLoaded', function() { document.body.appendChild(iframe); });
                })();
                """.trimIndent(),
                b.getCefBrowser().getURL(),
                0
            )

            // Step 2: 注入 ccBackend 和 ccEvents
            b.getCefBrowser().executeJavaScript(bridgeScript, b.getCefBrowser().getURL(), 0)

            // Step 3: 设置 window.javaRequestCallback（JS→Kotlin 的实际触发点）
            // 使用 loadURL/javascript: 机制触发 JBCefJSQuery
            // JBCefJSQuery 的注入脚本会创建 __jcef_query_<id>__ 函数
            // 我们找到它并通过 location.href=javascript: 调用它
            b.getCefBrowser().executeJavaScript(
                """
                (function() {
                    var retryCount = 0;
                    function setupCallback() {
                        // 查找 JBCefJSQuery 注入的 __jcef_query_<id>__ 函数
                        var keys = Object.keys(window).filter(function(k) { return k.indexOf('__jcef_query__') === 0; });
                        if (keys.length > 0) {
                            var fn = window[keys[0]];
                            if (typeof fn === 'function') {
                                window.javaRequestCallback = function(request) {
                                    try {
                                        var result = fn(request);
                                        return typeof result === 'string' ? result : JSON.stringify(result || '');
                                    } catch(ex) {
                                        console.error('[CCBackend] javaRequestCallback error:', ex);
                                        return '';
                                    }
                                };
                                console.log('[CCBackend] javaRequestCallback connected to JBCefJSQuery');
                                return true;
                            }
                        }
                        if (retryCount < 30) {
                            retryCount++;
                            setTimeout(setupCallback, 100);
                        } else {
                            console.warn('[CCBackend] JBCefJSQuery not found after 3s');
                        }
                        return false;
                    }
                    setupCallback();
                })();
                """.trimIndent(),
                b.getCefBrowser().getURL(),
                0
            )
        }
    }

    // ---- Java → JS Communication ----

    /**
     * 发送数据到 JavaScript
     * 使用 loadURL 执行 JavaScript 代码
     *
     * @param event 事件名称
     * @param data 数据
     */
    fun sendToJavaScript(event: String, data: Map<String, Any>) {
        browser?.let { b ->
            val jsonData = JsonUtils.toJson(data)
            // Escape single quotes and backslashes in event name to prevent injection
            val safeEvent = event.replace("\\", "\\\\").replace("'", "\\'")
            // Escape JSON string for safe embedding in JS single-quoted string
            val safeJsonForJs = jsonData
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "")
            val js = "window.ccEvents && window.ccEvents.emit('$safeEvent', JSON.parse('$safeJsonForJs'));"
            // Use getCefBrowser().executeJavaScript() to avoid XSS via loadURL("javascript:...")
            b.getCefBrowser().executeJavaScript(js, b.getCefBrowser().getURL(), 0)
        }
    }

    /**
     * 加载 HTML 页面
     */
    fun loadHtmlPage(url: String) {
        // 重置加载状态
        isPageLoaded = false
        browser?.loadURL(url)
        // 等待页面加载完成后注入 Bridge（带后备超时）
        executeWhenPageLoaded(Runnable { injectBackendJavaScript() })
        // 后备：如果3秒后仍未加载完成，强制注入
        scope.launch {
            kotlinx.coroutines.delay(3000)
            if (!isPageLoaded) {
                log.warn("Page load timeout, forcing bridge injection")
                isPageLoaded = true
                injectBackendJavaScript()
            }
        }
    }

    /**
     * 加载本地 HTML 内容
     */
    fun loadHtmlContent(htmlContent: String, baseUrl: String = "http://localhost/") {
        // 重置加载状态
        isPageLoaded = false
        browser?.loadHTML(htmlContent, baseUrl)
        // 等待页面加载完成后注入 Bridge（带后备超时）
        executeWhenPageLoaded(Runnable { injectBackendJavaScript() })
        // 后备：如果3秒后仍未加载完成，强制注入
        scope.launch {
            kotlinx.coroutines.delay(3000)
            if (!isPageLoaded) {
                log.warn("Page load timeout, forcing bridge injection")
                isPageLoaded = true
                injectBackendJavaScript()
            }
        }
    }

    // ---- Lifecycle ----

    override fun dispose() {
        scope.cancel()
        jsQuery?.dispose()
        browser?.dispose()
        browser = null
        jsQuery = null
        isInitialized = false
        log.info("CefBrowserPanel disposed")
    }
}