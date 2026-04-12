package com.github.claudecode.ccgui.browser.handler

import com.github.claudecode.ccgui.application.agent.AgentExecutor
import com.github.claudecode.ccgui.application.agent.AgentsManager
import com.github.claudecode.ccgui.application.chat.ChatConfigManager
import com.github.claudecode.ccgui.application.config.ConfigService
import com.github.claudecode.ccgui.application.context.ContextManager
import com.github.claudecode.ccgui.application.interaction.InteractiveRequestEngine
import com.github.claudecode.ccgui.application.mcp.McpServerManager
import com.github.claudecode.ccgui.application.multimodal.MultimodalInputHandler
import com.github.claudecode.ccgui.application.orchestrator.ChatOrchestrator
import com.github.claudecode.ccgui.application.prompt.PromptOptimizer
import com.github.claudecode.ccgui.application.session.SessionService
import com.github.claudecode.ccgui.application.skill.SkillExecutor
import com.github.claudecode.ccgui.application.skill.SkillsManager
import com.github.claudecode.ccgui.application.task.TaskProgressTracker
import com.github.claudecode.ccgui.bridge.BridgeManager
import com.github.claudecode.ccgui.bridge.StreamCallback
import com.github.claudecode.ccgui.model.agent.Agent
import com.github.claudecode.ccgui.model.config.ConversationMode
import com.github.claudecode.ccgui.model.interaction.QuestionOption
import com.github.claudecode.ccgui.model.mcp.McpServer
import com.github.claudecode.ccgui.model.message.MessageRole
import com.github.claudecode.ccgui.model.session.SessionType
import com.github.claudecode.ccgui.model.skill.Skill
import com.github.claudecode.ccgui.util.JsonUtils
import com.github.claudecode.ccgui.util.logger
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 回调类型：发送响应到 JS
 */
typealias ResponseCallback = (action: String, queryId: String, response: Any?, error: String?) -> Unit

/**
 * 回调类型：发送事件到 JS
 */
typealias EventCallback = (event: String, data: Map<String, Any>) -> Unit

/**
 * JS 请求处理器
 *
 * 负责处理所有来自 JavaScript 的请求（action 分发）
 * 遵循架构文档的 Handler 分离原则
 */
class JsRequestHandler(private val project: Project) {

    private val log = logger<JsRequestHandler>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 依赖的服务
    private val bridgeManager: BridgeManager by lazy { BridgeManager.getInstance(project) }
    private val chatOrchestrator: ChatOrchestrator by lazy { ChatOrchestrator.getInstance(project) }
    private val sessionManager: SessionService by lazy { SessionService.getInstance(project) }
    private val configManager: ConfigService by lazy { ConfigService.getInstance(project) }
    private val chatConfigService: ChatConfigManager by lazy { ChatConfigManager.getInstance() }
    private val promptOptimizer: PromptOptimizer by lazy { PromptOptimizer.getInstance(project) }
    private val skillExecutor: SkillExecutor by lazy { SkillExecutor.getInstance(project) }
    private val skillsManager: SkillsManager by lazy { SkillsManager.getInstance(project) }
    private val agentsManager: AgentsManager by lazy { AgentsManager.getInstance(project) }
    private val agentExecutor: AgentExecutor by lazy { AgentExecutor.getInstance(project) }
    private val mcpServerManager: McpServerManager by lazy { McpServerManager.getInstance(project) }
    private val taskProgressTracker: TaskProgressTracker by lazy { TaskProgressTracker.getInstance(project) }
    private val multimodalInputHandler: MultimodalInputHandler by lazy { MultimodalInputHandler.getInstance(project) }
    private val contextManager: ContextManager by lazy { ContextManager.getInstance(project) }

    private var responseCallback: ResponseCallback? = null
    private var eventCallback: EventCallback? = null

    fun setCallbacks(responseCallback: ResponseCallback, eventCallback: EventCallback) {
        this.responseCallback = responseCallback
        this.eventCallback = eventCallback
    }

    /**
     * 处理 JS 请求
     */
    fun handleRequest(request: String): Boolean {
        return try {
            val json = JsonUtils.parseObject(request) ?: return false
            val queryId = json.get("queryId")?.asString ?: ""
            val action = json.get("action")?.asString ?: ""
            val params = json.get("params")

            log.debug("JS request: action=$action, queryId=$queryId")

            when (action) {
                "sendMessage" -> handleSendMessage(queryId, params)
                "streamMessage" -> handleStreamMessage(queryId, params)
                "cancelStreaming" -> handleCancelStreaming(queryId, params)
                "sendMultimodalMessage" -> handleSendMultimodalMessage(queryId, params)
                "getConfig" -> handleGetConfig(queryId, params)
                "setConfig" -> handleSetConfig(queryId, params)
                "updateConfig" -> handleUpdateConfig(queryId, params)
                "getModelConfig" -> handleGetModelConfig(queryId, params)
                "updateModelConfig" -> handleUpdateModelConfig(queryId, params)
                "getProviders" -> handleGetProviders(queryId)
                "getProviderModels" -> handleGetProviderModels(queryId, params)
                "getProviderProfiles" -> handleGetProviderProfiles(queryId)
                "createProviderProfile" -> handleCreateProviderProfile(queryId, params)
                "updateProviderProfile" -> handleUpdateProviderProfile(queryId, params)
                "deleteProviderProfile" -> handleDeleteProviderProfile(queryId, params)
                "setActiveProviderProfile" -> handleSetActiveProviderProfile(queryId, params)
                "reorderProviderProfiles" -> handleReorderProviderProfiles(queryId, params)
                "convertCcSwitchProfile" -> handleConvertCcSwitchProfile(queryId, params)
                "updateTheme" -> handleUpdateTheme(queryId, params)
                "getThemes" -> handleGetThemes(queryId)
                "saveCustomTheme" -> handleSaveCustomTheme(queryId, params)
                "deleteCustomTheme" -> handleDeleteCustomTheme(queryId, params)
                "getIdeTheme" -> handleGetIdeTheme(queryId)
                "optimizePrompt" -> handleOptimizePrompt(queryId, params)
                "createSession" -> handleCreateSession(queryId, params)
                "switchSession" -> handleSwitchSession(queryId, params)
                "deleteSession" -> handleDeleteSession(queryId, params)
                "searchSessions" -> handleSearchSessions(queryId, params)
                "exportSession" -> handleExportSession(queryId, params)
                "importSession" -> handleImportSession(queryId, params)
                "getHistorySessions" -> handleGetHistorySessions(queryId)
                "confirmSession" -> handleConfirmSession(queryId, params)
                "getSkills" -> handleGetSkills(queryId)
                "saveSkill" -> handleSaveSkill(queryId, params)
                "deleteSkill" -> handleDeleteSkill(queryId, params)
                "executeSkill" -> handleExecuteSkill(queryId, params)
                "getAgents" -> handleGetAgents(queryId)
                "saveAgent" -> handleSaveAgent(queryId, params)
                "deleteAgent" -> handleDeleteAgent(queryId, params)
                "startAgent" -> handleStartAgent(queryId, params)
                "stopAgent" -> handleStopAgent(queryId, params)
                "getMcpServers" -> handleGetMcpServers(queryId)
                "saveMcpServer" -> handleSaveMcpServer(queryId, params)
                "deleteMcpServer" -> handleDeleteMcpServer(queryId, params)
                "startMcpServer" -> handleStartMcpServer(queryId, params)
                "stopMcpServer" -> handleStopMcpServer(queryId, params)
                "testMcpServer" -> handleTestMcpServer(queryId, params)
                "submitAnswer" -> handleSubmitAnswer(queryId, params)
                "getChatConfig" -> handleGetChatConfig(queryId)
                "updateChatConfig" -> handleUpdateChatConfig(queryId, params)
                "getConversationModes" -> handleGetConversationModes(queryId)
                "openSettings" -> handleOpenSettings(queryId, params)
                "getTaskStatus" -> handleGetTaskStatus(queryId)
                "executeSlashCommand" -> handleExecuteSlashCommand(queryId, params)
                "getSelectedText" -> handleGetSelectedText(queryId)
                "replaceSelectedText" -> handleReplaceSelectedText(queryId, params)
                else -> {
                    log.warn("Unknown action: $action")
                    false
                }
            }
        } catch (e: Exception) {
            log.error("Error handling JS request: ${e.message}", e)
            false
        }
    }

    // ==================== Messaging Handlers ====================

    private fun handleSendMessage(queryId: String, params: JsonElement?): Boolean {
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
        val eventCb = eventCallback

        chatOrchestrator.sendMessage(
            content = content,
            sessionId = sessionId,
            callback = object : StreamCallback {
                override fun onStreamStart() {
                    eventCb?.invoke("streaming:start", mapOf("messageId" to contextMessageId))
                }

                override fun onLineReceived(line: String) {
                    eventCb?.invoke("streaming:chunk", mapOf("messageId" to contextMessageId, "chunk" to line))
                }

                override fun onStreamComplete(messages: List<String>) {
                    eventCb?.invoke("streaming:complete", mapOf("messageId" to contextMessageId, "messages" to messages))
                }

                override fun onStreamError(error: String) {
                    eventCb?.invoke("streaming:error", mapOf("messageId" to contextMessageId, "error" to error))
                }
            },
            onResponse = { result, error ->
                responseCallback?.invoke("sendMessage", queryId, result, error)
            }
        )
        return true
    }

    private fun handleStreamMessage(queryId: String, params: JsonElement?): Boolean {
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
        val eventCb = eventCallback

        chatOrchestrator.sendMessage(
            content = content,
            sessionId = sessionId,
            callback = object : StreamCallback {
                override fun onStreamStart() {
                    eventCb?.invoke("streaming:start", mapOf("messageId" to contextMessageId))
                }

                override fun onLineReceived(line: String) {
                    eventCb?.invoke("streaming:chunk", mapOf("messageId" to contextMessageId, "chunk" to line))
                }

                override fun onStreamComplete(messages: List<String>) {
                    eventCb?.invoke("streaming:complete", mapOf("messageId" to contextMessageId, "messages" to messages))
                }

                override fun onStreamError(error: String) {
                    eventCb?.invoke("streaming:error", mapOf("messageId" to contextMessageId, "error" to error))
                }
            },
            onResponse = { result, error ->
                responseCallback?.invoke("streamMessage", queryId, result, error)
            }
        )
        return true
    }

    private fun handleCancelStreaming(queryId: String, params: JsonElement?): Boolean {
        val sessionId = params?.asJsonObject?.get("sessionId")?.asString ?: ""
        chatOrchestrator.cancelCurrentMessage()
        responseCallback?.invoke("cancelStreaming", queryId, mapOf("success" to true), null)
        return true
    }

    private fun handleSendMultimodalMessage(queryId: String, params: JsonElement?): Boolean {
        val jsonParams = params?.asJsonObject ?: return false
        val messageObj = jsonParams.get("message")?.asJsonObject ?: return false
        val sessionId = messageObj.get("sessionId")?.asString ?: ""
        val content = messageObj.get("content")?.asString ?: ""
        val attachments = messageObj.get("attachments")?.asJsonArray
        val eventCb = eventCallback

        if (attachments == null || attachments.size() == 0) {
            return handleSendMessage(queryId, params)
        }

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

        chatOrchestrator.sendMessage(
            content = fullContent,
            sessionId = sessionId,
            callback = object : StreamCallback {
                override fun onStreamStart() {
                    eventCb?.invoke("streaming:start", mapOf())
                }

                override fun onLineReceived(line: String) {
                    eventCb?.invoke("streaming:chunk", mapOf("chunk" to line))
                }

                override fun onStreamComplete(messages: List<String>) {
                    eventCb?.invoke("streaming:complete", mapOf("messages" to messages))
                }

                override fun onStreamError(error: String) {
                    eventCb?.invoke("streaming:error", mapOf("error" to error))
                }
            },
            onResponse = { result, error ->
                responseCallback?.invoke("sendMultimodalMessage", queryId, result, error)
            }
        )
        return true
    }

    // ==================== Config Handlers ====================

    private fun handleGetConfig(queryId: String, params: JsonElement?): Boolean {
        val config = configManager.getAppConfig()
        responseCallback?.invoke("getConfig", queryId, mapOf(
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
        ), null)
        return true
    }

    private fun handleSetConfig(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val current = configManager.getAppConfig()
        val updated = current.copy(
            currentThemeId = jsonObj.get("currentThemeId")?.asString ?: current.currentThemeId,
            autoConnect = jsonObj.get("autoConnect")?.asBoolean ?: current.autoConnect,
            streamOutput = jsonObj.get("streamOutput")?.asBoolean ?: current.streamOutput,
            showLineNumbers = jsonObj.get("showLineNumbers")?.asBoolean ?: current.showLineNumbers,
            enableSpellCheck = jsonObj.get("enableSpellCheck")?.asBoolean ?: current.enableSpellCheck
        )
        configManager.saveAppConfig(updated)
        responseCallback?.invoke("setConfig", queryId, mapOf("success" to true), null)
        return true
    }

    private fun handleUpdateConfig(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val current = configManager.getAppConfig()
        val updated = current.copy(
            currentThemeId = jsonObj.get("currentThemeId")?.asString ?: current.currentThemeId,
            autoConnect = jsonObj.get("autoConnect")?.asBoolean ?: current.autoConnect,
            streamOutput = jsonObj.get("streamOutput")?.asBoolean ?: current.streamOutput,
            showLineNumbers = jsonObj.get("showLineNumbers")?.asBoolean ?: current.showLineNumbers,
            enableSpellCheck = jsonObj.get("enableSpellCheck")?.asBoolean ?: current.enableSpellCheck
        )
        configManager.saveAppConfig(updated)
        responseCallback?.invoke("updateConfig", queryId, mapOf("success" to true), null)
        return true
    }

    private fun handleGetModelConfig(queryId: String, params: JsonElement?): Boolean {
        val modelConfig = configManager.getActiveModelConfig()
        responseCallback?.invoke("getModelConfig", queryId, mapOf(
            "provider" to modelConfig.provider,
            "model" to modelConfig.model,
            "apiKey" to (modelConfig.apiKey ?: ""),
            "baseUrl" to (modelConfig.baseUrl ?: ""),
            "maxRetries" to modelConfig.maxRetries
        ), null)
        return true
    }

    private fun handleUpdateModelConfig(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val currentAppConfig = configManager.getAppConfig()
        val activeProfileId = currentAppConfig.activeProfileId
        val currentModelConfig = configManager.getActiveModelConfig()

        val updatedModelConfig = com.github.claudecode.ccgui.model.config.ModelConfig(
            provider = jsonObj.get("provider")?.asString ?: currentModelConfig.provider,
            model = jsonObj.get("model")?.asString ?: currentModelConfig.model,
            apiKey = jsonObj.get("apiKey")?.asString?.takeIf { it.isNotEmpty() } ?: currentModelConfig.apiKey,
            baseUrl = jsonObj.get("baseUrl")?.asString?.takeIf { it.isNotEmpty() } ?: currentModelConfig.baseUrl,
            maxRetries = jsonObj.get("maxRetries")?.asInt ?: currentModelConfig.maxRetries
        )

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
        responseCallback?.invoke("updateModelConfig", queryId, mapOf("success" to true), null)
        return true
    }

    private fun handleGetProviders(queryId: String): Boolean {
        responseCallback?.invoke("getProviders", queryId, com.github.claudecode.ccgui.model.config.ModelConfig.getAllProviders(), null)
        return true
    }

    private fun handleGetProviderModels(queryId: String, params: JsonElement?): Boolean {
        val provider = params?.asJsonObject?.get("provider")?.asString ?: "anthropic"
        responseCallback?.invoke("getProviderModels", queryId, com.github.claudecode.ccgui.model.config.ModelConfig.getProviderModels(provider), null)
        return true
    }

    // ==================== Provider Profile Handlers ====================

    private fun handleGetProviderProfiles(queryId: String): Boolean {
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
        responseCallback?.invoke("getProviderProfiles", queryId, mapOf(
            "profiles" to profilesData,
            "activeProfileId" to activeId
        ), null)
        return true
    }

    private fun handleCreateProviderProfile(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val profile = com.github.claudecode.ccgui.model.config.ProviderProfile.fromJson(jsonObj)
        configManager.saveProviderProfile(profile)
        responseCallback?.invoke("createProviderProfile", queryId, mapOf("success" to true, "id" to profile.id), null)
        return true
    }

    private fun handleUpdateProviderProfile(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val profile = com.github.claudecode.ccgui.model.config.ProviderProfile.fromJson(jsonObj)
        configManager.saveProviderProfile(profile)
        responseCallback?.invoke("updateProviderProfile", queryId, mapOf("success" to true), null)
        return true
    }

    private fun handleDeleteProviderProfile(queryId: String, params: JsonElement?): Boolean {
        val profileId = params?.asJsonObject?.get("profileId")?.asString ?: return false
        configManager.deleteProviderProfile(profileId)
        responseCallback?.invoke("deleteProviderProfile", queryId, mapOf("success" to true), null)
        return true
    }

    private fun handleSetActiveProviderProfile(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val profileId = jsonObj.get("profileId")?.asString?.takeIf { it.isNotEmpty() }
        configManager.setActiveProviderProfile(profileId)
        responseCallback?.invoke("setActiveProviderProfile", queryId, mapOf("success" to true), null)
        return true
    }

    private fun handleReorderProviderProfiles(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val orderedIdsArray = jsonObj.get("orderedIds")?.asJsonArray ?: return false
        val orderedIds = orderedIdsArray.map { it.asString }
        configManager.reorderProviderProfiles(orderedIds)
        responseCallback?.invoke("reorderProviderProfiles", queryId, mapOf("success" to true), null)
        return true
    }

    private fun handleConvertCcSwitchProfile(queryId: String, params: JsonElement?): Boolean {
        val profileId = params?.asJsonObject?.get("profileId")?.asString ?: return false
        val newProfile = configManager.convertCcSwitchProfile(profileId)
        if (newProfile != null) {
            responseCallback?.invoke("convertCcSwitchProfile", queryId, mapOf("success" to true, "profile" to mapOf(
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
            )), null)
        } else {
            responseCallback?.invoke("convertCcSwitchProfile", queryId, null, "Failed to convert profile")
        }
        return true
    }

    // ==================== Theme Handlers ====================

    private fun handleGetThemes(queryId: String): Boolean {
        val themes = configManager.getAllThemes()
        responseCallback?.invoke("getThemes", queryId, themes.map { theme ->
            mapOf(
                "id" to theme.id,
                "name" to theme.name,
                "isDark" to theme.isDark
            )
        }, null)
        return true
    }

    private fun handleUpdateTheme(queryId: String, params: JsonElement?): Boolean {
        val themeId = params?.asJsonObject?.get("themeId")?.asString ?: return false
        configManager.setCurrentTheme(themeId)
        responseCallback?.invoke("updateTheme", queryId, mapOf("success" to true), null)
        return true
    }

    private fun handleSaveCustomTheme(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val id = jsonObj.get("id")?.asString ?: return false
        val name = jsonObj.get("name")?.asString ?: return false
        val isDark = jsonObj.get("isDark")?.asBoolean ?: false
        val colors = jsonObj.get("colors")?.asJsonObject ?: return false
        val theme = com.github.claudecode.ccgui.model.config.ThemeConfig(
            id = id,
            name = name,
            isDark = isDark,
            colors = com.github.claudecode.ccgui.model.config.ColorScheme(
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
        responseCallback?.invoke("saveCustomTheme", queryId, mapOf("success" to true), null)
        return true
    }

    private fun handleDeleteCustomTheme(queryId: String, params: JsonElement?): Boolean {
        val themeId = params?.asJsonObject?.get("themeId")?.asString ?: return false
        configManager.deleteCustomTheme(themeId)
        responseCallback?.invoke("deleteCustomTheme", queryId, mapOf("success" to true), null)
        return true
    }

    private fun handleGetIdeTheme(queryId: String): Boolean {
        val isDark = com.intellij.util.ui.UIUtil.isUnderDarcula()
        responseCallback?.invoke("getIdeTheme", queryId, mapOf("isDark" to isDark), null)
        return true
    }

    private fun handleOptimizePrompt(queryId: String, params: JsonElement?): Boolean {
        val prompt = params?.asJsonObject?.get("prompt")?.asString ?: ""
        scope.launch {
            try {
                val result = promptOptimizer.optimizePrompt(prompt)
                responseCallback?.invoke("optimizePrompt", queryId, mapOf(
                    "optimizedPrompt" to result.optimizedPrompt,
                    "addedContextCount" to result.addedContext.size,
                    "improvements" to result.improvements,
                    "confidence" to result.confidence
                ), null)
            } catch (e: Exception) {
                log.warn("Optimize prompt failed: ${e.message}")
                responseCallback?.invoke("optimizePrompt", queryId, null, e.message ?: "优化失败")
            }
        }
        return true
    }

    // ==================== Session Handlers ====================

    private fun handleCreateSession(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject
        val name = jsonObj?.get("name")?.asString ?: "New Session"
        val typeStr = jsonObj?.get("type")?.asString ?: "PROJECT"
        val type = try { SessionType.valueOf(typeStr) } catch (_: Exception) { SessionType.PROJECT }
        val session = sessionManager.createSession(name, type)
        responseCallback?.invoke("createSession", queryId, mapOf("id" to session.id, "name" to session.name, "type" to session.type.name), null)
        return true
    }

    private fun handleSwitchSession(queryId: String, params: JsonElement?): Boolean {
        val sessionId = params?.asJsonObject?.get("sessionId")?.asString ?: return false
        sessionManager.setCurrentSession(sessionId)
        responseCallback?.invoke("switchSession", queryId, mapOf("success" to true, "currentSessionId" to sessionId), null)
        return true
    }

    private fun handleDeleteSession(queryId: String, params: JsonElement?): Boolean {
        val sessionId = params?.asJsonObject?.get("sessionId")?.asString ?: return false
        sessionManager.deleteSession(sessionId)
        responseCallback?.invoke("deleteSession", queryId, mapOf("success" to true), null)
        return true
    }

    private fun handleSearchSessions(queryId: String, params: JsonElement?): Boolean {
        val query = params?.asJsonObject?.get("query")?.asString ?: ""
        val sessions = sessionManager.searchSessions(query)
        responseCallback?.invoke("searchSessions", queryId, sessions.map { session ->
            mapOf(
                "id" to session.id,
                "name" to session.name,
                "type" to session.type.name,
                "messageCount" to session.messages.size,
                "createdAt" to session.createdAt,
                "updatedAt" to session.updatedAt
            )
        }, null)
        return true
    }

    private fun handleExportSession(queryId: String, params: JsonElement?): Boolean {
        val sessionId = params?.asJsonObject?.get("sessionId")?.asString ?: return false
        val session = sessionManager.getSession(sessionId) ?: return false
        responseCallback?.invoke("exportSession", queryId, session.toJson(), null)
        return true
    }

    private fun handleImportSession(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val jsonContent = jsonObj.get("data")?.asString ?: return false
        val session = sessionManager.importSession(jsonContent) ?: return false
        responseCallback?.invoke("importSession", queryId, mapOf("id" to session.id, "name" to session.name), null)
        return true
    }

    private fun handleGetHistorySessions(queryId: String): Boolean {
        val sessions = sessionManager.getHistorySessions()
        responseCallback?.invoke("getHistorySessions", queryId, sessions.map { session ->
            mapOf(
                "id" to session.id,
                "name" to session.name,
                "type" to session.type.name,
                "messageCount" to session.messages.size,
                "createdAt" to session.createdAt,
                "updatedAt" to session.updatedAt,
                "isPending" to session.isPending
            )
        }, null)
        return true
    }

    private fun handleConfirmSession(queryId: String, params: JsonElement?): Boolean {
        val sessionId = params?.asJsonObject?.get("sessionId")?.asString ?: return false
        sessionManager.confirmSession(sessionId)
        responseCallback?.invoke("confirmSession", queryId, mapOf("success" to true), null)
        return true
    }

    // ==================== Skills Handlers ====================

    private fun handleGetSkills(queryId: String): Boolean {
        val skills = skillsManager.getAllSkills()
        responseCallback?.invoke("getSkills", queryId, skills.map { skill ->
            mapOf(
                "id" to skill.id,
                "name" to skill.name,
                "description" to skill.description,
                "category" to skill.category.name
            )
        }, null)
        return true
    }

    private fun handleSaveSkill(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val id = jsonObj.get("id")?.asString ?: return false
        val name = jsonObj.get("name")?.asString ?: return false
        val description = jsonObj.get("description")?.asString ?: ""
        val prompt = jsonObj.get("prompt")?.asString ?: ""
        val categoryStr = jsonObj.get("category")?.asString ?: "CODE_GENERATION"
        val category = try {
            com.github.claudecode.ccgui.model.skill.SkillCategory.valueOf(categoryStr)
        } catch (_: Exception) {
            com.github.claudecode.ccgui.model.skill.SkillCategory.CODE_GENERATION
        }
        val skill = Skill(
            id = id,
            name = name,
            description = description,
            prompt = prompt,
            category = category
        )
        val success = skillsManager.addSkill(skill)
        responseCallback?.invoke("saveSkill", queryId, mapOf("success" to success), null)
        return true
    }

    private fun handleDeleteSkill(queryId: String, params: JsonElement?): Boolean {
        val skillId = params?.asJsonObject?.get("skillId")?.asString ?: return false
        val success = skillsManager.deleteSkill(skillId)
        responseCallback?.invoke("deleteSkill", queryId, mapOf("success" to success), null)
        return true
    }

    private fun handleExecuteSkill(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val skillId = jsonObj.get("skillId")?.asString ?: return false
        val skill = skillsManager.getSkill(skillId) ?: return false
        val context = com.github.claudecode.ccgui.model.skill.ExecutionContext()
        val eventCb = eventCallback
        scope.launch {
            try {
                val result = skillExecutor.executeSkill(skill, context)
                eventCb?.invoke("skill:result", mapOf("skillId" to skillId, "result" to result.toString()))
            } catch (e: Exception) {
                eventCb?.invoke("skill:error", mapOf("skillId" to skillId, "error" to (e.message ?: "Unknown error")))
            }
        }
        responseCallback?.invoke("executeSkill", queryId, mapOf("submitted" to true), null)
        return true
    }

    // ==================== Agents Handlers ====================

    private fun handleGetAgents(queryId: String): Boolean {
        val agents = agentsManager.getAllAgents()
        responseCallback?.invoke("getAgents", queryId, agents.map { agent ->
            mapOf(
                "id" to agent.id,
                "name" to agent.name,
                "description" to agent.description,
                "capabilities" to agent.capabilities.map { it.name },
                "enabled" to agent.enabled
            )
        }, null)
        return true
    }

    private fun handleSaveAgent(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val id = jsonObj.get("id")?.asString ?: return false
        val name = jsonObj.get("name")?.asString ?: return false
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
        responseCallback?.invoke("saveAgent", queryId, mapOf("success" to success), null)
        return true
    }

    private fun handleDeleteAgent(queryId: String, params: JsonElement?): Boolean {
        val agentId = params?.asJsonObject?.get("agentId")?.asString ?: return false
        val success = agentsManager.deleteAgent(agentId)
        responseCallback?.invoke("deleteAgent", queryId, mapOf("success" to success), null)
        return true
    }

    private fun handleStartAgent(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val agentId = jsonObj.get("agentId")?.asString ?: return false
        val taskDesc = jsonObj.get("task")?.asString ?: ""
        val agent = agentsManager.getAgent(agentId) ?: return false
        val capability = agent.capabilities.firstOrNull() ?: com.github.claudecode.ccgui.model.agent.AgentCapability.CODE_GENERATION
        val task = com.github.claudecode.ccgui.model.agent.AgentTask(
            description = taskDesc,
            requiredCapability = capability
        )
        val eventCb = eventCallback
        scope.launch {
            try {
                val result = agentExecutor.executeAgent(agent, task)
                eventCb?.invoke("agent:result", mapOf("agentId" to agentId, "result" to result.toString()))
            } catch (e: Exception) {
                eventCb?.invoke("agent:error", mapOf("agentId" to agentId, "error" to (e.message ?: "Unknown error")))
            }
        }
        responseCallback?.invoke("startAgent", queryId, mapOf("submitted" to true), null)
        return true
    }

    private fun handleStopAgent(queryId: String, params: JsonElement?): Boolean {
        val agentId = params?.asJsonObject?.get("agentId")?.asString ?: return false
        agentsManager.markTaskCompleted(agentId, false)
        responseCallback?.invoke("stopAgent", queryId, mapOf("success" to true), null)
        return true
    }

    // ==================== MCP Handlers ====================

    private fun handleGetMcpServers(queryId: String): Boolean {
        val servers = mcpServerManager.getAllServers()
        responseCallback?.invoke("getMcpServers", queryId, servers.map { server ->
            mapOf(
                "id" to server.id,
                "name" to server.name,
                "description" to server.description,
                "command" to server.command,
                "enabled" to server.enabled,
                "status" to server.status.name
            )
        }, null)
        return true
    }

    private fun handleSaveMcpServer(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val id = jsonObj.get("id")?.asString ?: return false
        val name = jsonObj.get("name")?.asString ?: return false
        val description = jsonObj.get("description")?.asString ?: ""
        val command = jsonObj.get("command")?.asString ?: return false
        val server = McpServer(
            id = id,
            name = name,
            description = description,
            command = command,
            args = jsonObj.get("args")?.asJsonArray?.map { it.asString } ?: emptyList(),
            env = emptyMap(),
            scope = com.github.claudecode.ccgui.model.mcp.McpScope.PROJECT
        )
        val success = mcpServerManager.addServer(server)
        responseCallback?.invoke("saveMcpServer", queryId, mapOf("success" to success), null)
        return true
    }

    private fun handleDeleteMcpServer(queryId: String, params: JsonElement?): Boolean {
        val serverId = params?.asJsonObject?.get("serverId")?.asString ?: return false
        val success = mcpServerManager.deleteServer(serverId)
        responseCallback?.invoke("deleteMcpServer", queryId, mapOf("success" to success), null)
        return true
    }

    private fun handleStartMcpServer(queryId: String, params: JsonElement?): Boolean {
        val serverId = params?.asJsonObject?.get("serverId")?.asString ?: return false
        val eventCb = eventCallback
        scope.launch {
            val success = mcpServerManager.startServer(serverId)
            eventCb?.invoke("mcp:serverStatus", mapOf("serverId" to serverId, "success" to success))
        }
        responseCallback?.invoke("startMcpServer", queryId, mapOf("submitted" to true), null)
        return true
    }

    private fun handleStopMcpServer(queryId: String, params: JsonElement?): Boolean {
        val serverId = params?.asJsonObject?.get("serverId")?.asString ?: return false
        val eventCb = eventCallback
        scope.launch {
            val success = mcpServerManager.stopServer(serverId)
            eventCb?.invoke("mcp:serverStatus", mapOf("serverId" to serverId, "success" to success))
        }
        responseCallback?.invoke("stopMcpServer", queryId, mapOf("submitted" to true), null)
        return true
    }

    private fun handleTestMcpServer(queryId: String, params: JsonElement?): Boolean {
        val serverId = params?.asJsonObject?.get("serverId")?.asString ?: return false
        val eventCb = eventCallback
        scope.launch {
            val result = mcpServerManager.testServer(serverId)
            val isSuccess = result is com.github.claudecode.ccgui.model.mcp.TestResult
            eventCb?.invoke("mcp:testResult", mapOf("serverId" to serverId, "success" to isSuccess, "result" to result.toString()))
        }
        responseCallback?.invoke("testMcpServer", queryId, mapOf("submitted" to true), null)
        return true
    }

    // ==================== Interactive Handlers ====================

    private fun handleSubmitAnswer(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val questionId = jsonObj.get("questionId")?.asString ?: return false
        val typeStr = jsonObj.get("type")?.asString ?: "text"
        val answer = when (typeStr) {
            "confirmation" -> {
                val allowed = jsonObj.get("answer")?.asBoolean ?: false
                InteractiveRequestEngine.QuestionAnswer.Confirmation(allowed)
            }
            "singleChoice" -> {
                val optionStr = jsonObj.get("answer")?.asString ?: return false
                val option = QuestionOption(optionStr, optionStr)
                InteractiveRequestEngine.QuestionAnswer.SingleChoice(option)
            }
            "multipleChoice" -> {
                val optionsArr = jsonObj.get("answer")?.asJsonArray ?: return false
                val options = optionsArr.map {
                    val s = it.asString
                    QuestionOption(s, s)
                }
                InteractiveRequestEngine.QuestionAnswer.MultipleChoice(options)
            }
            "number" -> {
                val num = jsonObj.get("answer")?.asDouble ?: return false
                InteractiveRequestEngine.QuestionAnswer.NumberInput(num)
            }
            else -> {
                val answerStr = jsonObj.get("answer")?.asString ?: return false
                InteractiveRequestEngine.QuestionAnswer.TextInput(answerStr)
            }
        }
        InteractiveRequestEngine.getInstance(project).submitAnswer(questionId, answer)
        responseCallback?.invoke("submitAnswer", queryId, mapOf("success" to true), null)
        return true
    }

    // ==================== Chat Config Handlers ====================

    private fun handleGetChatConfig(queryId: String): Boolean {
        responseCallback?.invoke("getChatConfig", queryId, mapOf(
            "conversationMode" to chatConfigService.getConversationMode().name,
            "currentAgentId" to chatConfigService.getCurrentAgentId(),
            "streamingEnabled" to chatConfigService.isStreamingEnabled()
        ), null)
        return true
    }

    private fun handleUpdateChatConfig(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        jsonObj.get("conversationMode")?.asString?.let {
            chatConfigService.setConversationMode(ConversationMode.valueOf(it))
        }
        jsonObj.get("currentAgentId")?.let { el ->
            chatConfigService.setCurrentAgent(if (el.isJsonNull) null else el.asString)
        }
        jsonObj.get("streamingEnabled")?.asBoolean?.let {
            chatConfigService.setStreamingEnabled(it)
        }
        responseCallback?.invoke("updateChatConfig", queryId, mapOf("success" to true), null)
        return true
    }

    // ==================== Conversation Mode Handlers ====================

    private fun handleGetConversationModes(queryId: String): Boolean {
        responseCallback?.invoke("getConversationModes", queryId, ConversationMode.getAllModeDescriptions(), null)
        return true
    }

    // ==================== Settings Handlers ====================

    private fun handleOpenSettings(queryId: String, params: JsonElement?): Boolean {
        val tabId = params?.asJsonObject?.get("tabId")?.asString
        eventCallback?.invoke("ui.settings.open", mapOf("tabId" to (tabId ?: "")))
        responseCallback?.invoke("openSettings", queryId, mapOf("success" to true), null)
        return true
    }

    // ==================== Task Status Handlers ====================

    private fun handleGetTaskStatus(queryId: String): Boolean {
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
        responseCallback?.invoke("getTaskStatus", queryId, mapOf(
            "tasks" to tasks,
            "activeSubagents" to subagents,
            "diffRecords" to emptyList<Map<String, Any>>()
        ), null)
        return true
    }

    // ==================== Slash Command Handlers ====================

    private fun handleExecuteSlashCommand(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val command = jsonObj.get("command")?.asString ?: return false
        val sessionId = sessionManager.getCurrentSession()?.id ?: return false

        val result = when {
            command.startsWith("/compact") -> {
                scope.launch { contextManager.compact(sessionId) }
                mapOf("success" to true, "message" to "Context compaction triggered")
            }
            command.startsWith("/clear") -> {
                scope.launch { sessionManager.clearSession(sessionId) }
                mapOf("success" to true, "message" to "Session cleared")
            }
            command.startsWith("/retry") -> {
                val lastUserMessage = sessionManager.getSession(sessionId)
                    ?.messages
                    ?.filter { it.role == MessageRole.USER }
                    ?.lastOrNull()
                if (lastUserMessage != null) {
                    chatOrchestrator.sendMessage(
                        content = lastUserMessage.content,
                        sessionId = sessionId,
                        callback = object : StreamCallback {
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
        responseCallback?.invoke("executeSlashCommand", queryId, result, null)
        return true
    }

    // ==================== Editor Integration Handlers ====================

    private fun handleGetSelectedText(queryId: String): Boolean {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val selectedText = editor?.selectionModel?.selectedText ?: ""
        val fileName = editor?.virtualFile?.name ?: ""
        val language = editor?.virtualFile?.extension ?: ""
        responseCallback?.invoke("getSelectedText", queryId, mapOf(
            "text" to selectedText,
            "fileName" to fileName,
            "language" to language,
            "hasSelection" to selectedText.isNotEmpty()
        ), null)
        return true
    }

    private fun handleReplaceSelectedText(queryId: String, params: JsonElement?): Boolean {
        val jsonObj = params?.asJsonObject ?: return false
        val newText = jsonObj.get("text")?.asString ?: return false
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return false
        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) {
            responseCallback?.invoke("replaceSelectedText", queryId, null, "No text selected")
            return true
        }
        val start = selectionModel.selectionStart
        val end = selectionModel.selectionEnd
        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.replaceString(start, end, newText)
            selectionModel.removeSelection()
        }
        responseCallback?.invoke("replaceSelectedText", queryId, mapOf("success" to true), null)
        return true
    }
}