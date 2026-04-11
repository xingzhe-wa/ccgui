package com.github.xingzhewa.ccgui.application.chat

import com.github.xingzhewa.ccgui.model.config.ConversationMode
import com.github.xingzhewa.ccgui.util.logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 聊天配置管理器
 *
 * 存储当前聊天会话的配置，包括：
 * - 当前选中的 Agent ID
 * - 当前对话模式 (AUTO / THINKING / PLANNING)
 * - 流式输出开关
 *
 * 这些配置会影响消息发送时的 SDK 调用参数
 */
class ChatConfigManager {

    private val log = logger<ChatConfigManager>()

    /** 当前选中的 Agent ID（null 表示使用默认配置） */
    private val _currentAgentId = MutableStateFlow<String?>(null)
    val currentAgentId: StateFlow<String?> = _currentAgentId.asStateFlow()

    /** 当前对话模式 */
    private val _conversationMode = MutableStateFlow(ConversationMode.AUTO)
    val conversationMode: StateFlow<ConversationMode> = _conversationMode.asStateFlow()

    /** 流式输出是否启用 */
    private val _streamingEnabled = MutableStateFlow(true)
    val streamingEnabled: StateFlow<Boolean> = _streamingEnabled.asStateFlow()

    /**
     * 设置当前 Agent
     */
    fun setCurrentAgent(agentId: String?) {
        _currentAgentId.value = agentId
        log.info("Current agent set: $agentId")
    }

    /**
     * 设置对话模式
     */
    fun setConversationMode(mode: ConversationMode) {
        _conversationMode.value = mode
        log.info("Conversation mode set: $mode")
    }

    /**
     * 设置流式输出开关
     */
    fun setStreamingEnabled(enabled: Boolean) {
        _streamingEnabled.value = enabled
        log.info("Streaming enabled: $enabled")
    }

    /**
     * 获取当前 Agent ID
     */
    fun getCurrentAgentId(): String? = _currentAgentId.value

    /**
     * 获取当前对话模式
     */
    fun getConversationMode(): ConversationMode = _conversationMode.value

    /**
     * 是否启用流式输出
     */
    fun isStreamingEnabled(): Boolean = _streamingEnabled.value

    companion object {
        @Volatile
        private var instance: ChatConfigManager? = null

        fun getInstance(): ChatConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ChatConfigManager().also { instance = it }
            }
        }
    }
}
