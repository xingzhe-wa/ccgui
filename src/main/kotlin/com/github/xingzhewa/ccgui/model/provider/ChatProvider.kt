package com.github.xingzhewa.ccgui.model.provider

import com.github.xingzhewa.ccgui.model.message.ContentPart
import com.github.xingzhewa.ccgui.model.message.MessageRole
import com.google.gson.JsonObject

/**
 * 聊天请求
 *
 * 注意：此类在 SDK 集成模式下主要用作内部消息传递，
 * 不再直接用于 API 调用
 */
data class ChatRequest(
    val messages: List<ChatMessageInput> = emptyList(),
    val model: String = "claude-sonnet-4-20250514",
    val maxTokens: Int = 8192,
    val temperature: Double = 1.0,
    val topP: Double = 0.9,
    val systemPrompt: String? = null,
    val stopSequences: List<String> = emptyList()
) {

    /**
     * 聊天消息输入
     */
    data class ChatMessageInput(
        val role: MessageRole,
        val content: String,
        val attachments: List<ContentPart> = emptyList()
    )

    companion object {
        fun fromJson(json: JsonObject): ChatRequest? {
            return try {
                ChatRequest(
                    messages = json.getAsJsonArray("messages")?.map {
                        val obj = it.asJsonObject
                        ChatMessageInput(
                            role = MessageRole.valueOf(obj.get("role")?.asString ?: "USER"),
                            content = obj.get("content")?.asString ?: "",
                            attachments = obj.getAsJsonArray("attachments")?.mapNotNull {
                                ContentPart.fromJson(it.asJsonObject)
                            } ?: emptyList()
                        )
                    } ?: emptyList(),
                    model = json.get("model")?.asString ?: "claude-sonnet-4-20250514",
                    maxTokens = json.get("maxTokens")?.asInt ?: 8192,
                    temperature = json.get("temperature")?.asDouble ?: 1.0,
                    topP = json.get("topP")?.asDouble ?: 0.9,
                    systemPrompt = json.get("systemPrompt")?.asString,
                    stopSequences = json.getAsJsonArray("stopSequences")?.map {
                        it.asString
                    } ?: emptyList()
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): JsonObject {
        return JsonObject().apply {
            add("messages", com.google.gson.JsonArray().apply {
                messages.forEach { msg ->
                    add(JsonObject().apply {
                        addProperty("role", msg.role.name)
                        addProperty("content", msg.content)
                        if (msg.attachments.isNotEmpty()) {
                            add("attachments", com.google.gson.JsonArray().apply {
                                msg.attachments.forEach { add(it.toJson()) }
                            })
                        }
                    })
                }
            })
            addProperty("model", model)
            addProperty("maxTokens", maxTokens)
            addProperty("temperature", temperature)
            addProperty("topP", topP)
            systemPrompt?.let { addProperty("systemPrompt", it) }
            if (stopSequences.isNotEmpty()) {
                add("stopSequences", com.google.gson.JsonArray().apply {
                    stopSequences.forEach { add(it) }
                })
            }
        }
    }
}

/**
 * 聊天响应
 */
data class ChatResponse(
    val content: String,
    val tokensUsed: Int = 0,
    val executionTime: Long = 0,
    val model: String = "",
    val finishReason: FinishReason = FinishReason.STOP,
    val cached: Boolean = false
) {

    /**
     * 完成原因
     */
    enum class FinishReason {
        STOP,
        LENGTH,
        ERROR,
        CANCELLED
    }

    companion object {
        fun fromJson(json: JsonObject): ChatResponse? {
            return try {
                ChatResponse(
                    content = json.get("content")?.asString ?: "",
                    tokensUsed = json.get("tokensUsed")?.asInt ?: 0,
                    executionTime = json.get("executionTime")?.asLong ?: 0,
                    model = json.get("model")?.asString ?: "",
                    finishReason = json.get("finishReason")?.asString?.let {
                        FinishReason.valueOf(it)
                    } ?: FinishReason.STOP,
                    cached = json.get("cached")?.asBoolean ?: false
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("content", content)
            addProperty("tokensUsed", tokensUsed)
            addProperty("executionTime", executionTime)
            addProperty("model", model)
            addProperty("finishReason", finishReason.name)
            addProperty("cached", cached)
        }
    }
}

/**
 * 流式输出块
 */
data class StreamingChunk(
    val chunk: String,
    val index: Int = 0,
    val isComplete: Boolean = false
)

/**
 * Token 使用统计
 */
data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
    val costUsd: Double = 0.0
)
