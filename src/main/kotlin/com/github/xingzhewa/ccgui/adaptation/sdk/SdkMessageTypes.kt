package com.github.xingzhewa.ccgui.adaptation.sdk

import com.google.gson.JsonObject

/**
 * Claude Code SDK stream-json 消息类型定义
 *
 * stream-json 协议: 每行一个JSON对象，通过 "type" 字段区分消息类型
 */
object SdkMessageTypes {

    /**
     * SDK消息基类
     * 所有从CLI stdout解析出的消息都继承此接口
     */
    sealed class SdkMessage {
        abstract val rawJson: String
        abstract val timestamp: Long
    }

    /**
     * Init消息 — CLI进程启动时发送
     * 包含会话ID和工具列表
     *
     * 示例:
     * {"type":"init","session_id":"sess_abc123","tools":["Bash","Read","Write",...]}
     */
    data class SdkInitMessage(
        val sessionId: String,
        val tools: List<String>,
        val model: String? = null,
        override val rawJson: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SdkMessage()

    /**
     * User消息 — 用户发送的消息回显
     *
     * 示例:
     * {"type":"user","content":"请帮我优化这段代码","session_id":"sess_abc123"}
     */
    data class SdkUserMessage(
        val content: String,
        val sessionId: String?,
        override val rawJson: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SdkMessage()

    /**
     * Assistant消息 — AI的流式回复
     * 包含多种content block类型
     *
     * 示例 (text):
     * {"type":"assistant","message":{"content":[{"type":"text","text":"Hello"}]}}
     *
     * 示例 (tool_use):
     * {"type":"assistant","message":{"content":[{"type":"tool_use","name":"Read","input":{"file":"..."}]}]}
     */
    data class SdkAssistantMessage(
        val contentBlocks: List<ContentBlock>,
        val stopReason: String? = null,
        val model: String? = null,
        val sessionId: String? = null,
        override val rawJson: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SdkMessage() {

        /**
         * 提取纯文本内容
         */
        fun extractText(): String = contentBlocks
            .filterIsInstance<ContentBlock.Text>()
            .joinToString("") { it.text }

        /**
         * 提取工具调用
         */
        fun extractToolUses(): List<ContentBlock.ToolUse> = contentBlocks
            .filterIsInstance<ContentBlock.ToolUse>()

        /**
         * 是否包含工具调用
         */
        fun hasToolUse(): Boolean = contentBlocks.any { it is ContentBlock.ToolUse }
    }

    /**
     * Result消息 — 对话回合结束
     *
     * 示例:
     * {"type":"result","subtype":"success","cost_usd":0.003,"duration_ms":1500,"duration_api_ms":1200,"num_turns":1,"session_id":"sess_abc123"}
     */
    data class SdkResultMessage(
        val subtype: String,         // success, error, cancelled
        val costUsd: Double?,
        val durationMs: Long?,
        val durationApiMs: Long?,
        val numTurns: Int?,
        val sessionId: String?,
        val result: String?,         // 最终文本结果
        override val rawJson: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SdkMessage() {

        val isSuccess: Boolean get() = subtype == "success"
        val isError: Boolean get() = subtype == "error"
        val isCancelled: Boolean get() = subtype == "cancelled"
    }

    /**
     * Content Block 类型 — assistant消息中的内容块
     */
    sealed class ContentBlock {
        /**
         * 文本内容块
         */
        data class Text(
            val text: String
        ) : ContentBlock()

        /**
         * 工具使用内容块
         */
        data class ToolUse(
            val id: String,
            val name: String,
            val input: JsonObject
        ) : ContentBlock()

        /**
         * 工具执行结果
         */
        data class ToolResult(
            val toolUseId: String,
            val content: String,
            val isError: Boolean = false
        ) : ContentBlock()

        /**
         * 思考内容块 (extended thinking)
         */
        data class Thinking(
            val text: String
        ) : ContentBlock()
    }

    /**
     * 未知消息类型 — 兜底处理
     */
    data class SdkUnknownMessage(
        val type: String,
        override val rawJson: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SdkMessage()
}
