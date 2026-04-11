package com.github.xingzhewa.ccgui.model.message

import com.github.xingzhewa.ccgui.infrastructure.storage.MessageContentStorage
import com.github.xingzhewa.ccgui.util.IdGenerator
import com.google.gson.JsonObject

/**
 * 消息状态枚举
 */
enum class MessageStatus {
    /** 发送中 */
    PENDING,

    /** 已发送 */
    SENT,

    /** 流式输出中 */
    STREAMING,

    /** 已完成 */
    COMPLETED,

    /** 失败 */
    FAILED
}

/**
 * 消息引用
 */
data class MessageReference(
    val messageId: String,
    val excerpt: String,
    val timestamp: Long,
    val sender: MessageRole
)

/**
 * 消息元数据
 */
data class MessageMetadata(
    val tokensUsed: Int? = null,
    val model: String? = null,
    val provider: String? = null,
    val executionTime: Long? = null,
    val cost: Double? = null
)

/**
 * 聊天消息
 *
 * @param id 消息 ID
 * @param role 消息角色
 * @param content 消息内容
 * @param timestamp 时间戳
 * @param attachments 附件列表
 * @param references 引用列表
 * @param metadata 元数据
 * @param status 消息状态
 */
data class ChatMessage(
    val id: String = IdGenerator.messageId(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<ContentPart> = emptyList(),
    val references: List<MessageReference> = emptyList(),
    val metadata: MessageMetadata = MessageMetadata(),
    val status: MessageStatus = MessageStatus.COMPLETED
) {

    /**
     * 是否为用户消息
     */
    val isUser: Boolean get() = role == MessageRole.USER

    /**
     * 是否为助手消息
     */
    val isAssistant: Boolean get() = role == MessageRole.ASSISTANT

    /**
     * 是否为系统消息
     */
    val isSystem: Boolean get() = role == MessageRole.SYSTEM

    /**
     * 是否正在流式输出
     */
    val isStreaming: Boolean get() = status == MessageStatus.STREAMING

    /**
     * 是否已完成
     */
    val isCompleted: Boolean get() = status == MessageStatus.COMPLETED

    /**
     * 是否发送失败
     */
    val isFailed: Boolean get() = status == MessageStatus.FAILED

    /**
     * 内容是否为文件引用
     */
    val isFileReference: Boolean get() = MessageContentStorage.isFileReference(content)

    companion object {
        /**
         * 创建用户消息
         */
        fun userMessage(content: String, attachments: List<ContentPart> = emptyList()): ChatMessage {
            return ChatMessage(
                role = MessageRole.USER,
                content = content,
                attachments = attachments,
                status = MessageStatus.SENT
            )
        }

        /**
         * 创建助手消息
         */
        fun assistantMessage(content: String): ChatMessage {
            return ChatMessage(
                role = MessageRole.ASSISTANT,
                content = content
            )
        }

        /**
         * 创建系统消息
         */
        fun systemMessage(content: String): ChatMessage {
            return ChatMessage(
                role = MessageRole.SYSTEM,
                content = content
            )
        }

        /**
         * 从 JSON 反序列化
         */
        fun fromJson(json: JsonObject): ChatMessage? {
            return try {
                ChatMessage(
                    id = json.get("id")?.asString ?: IdGenerator.messageId(),
                    role = MessageRole.valueOf(json.get("role")?.asString ?: "USER"),
                    content = json.get("content")?.asString ?: "",
                    timestamp = json.get("timestamp")?.asLong ?: System.currentTimeMillis(),
                    attachments = json.getAsJsonArray("attachments")?.mapNotNull {
                        ContentPart.fromJson(it.asJsonObject)
                    } ?: emptyList(),
                    references = json.getAsJsonArray("references")?.mapNotNull {
                        val obj = it.asJsonObject
                        MessageReference(
                            messageId = obj.get("messageId")?.asString ?: "",
                            excerpt = obj.get("excerpt")?.asString ?: "",
                            timestamp = obj.get("timestamp")?.asLong ?: 0L,
                            sender = MessageRole.valueOf(obj.get("sender")?.asString ?: "USER")
                        )
                    } ?: emptyList(),
                    metadata = json.getAsJsonObject("metadata")?.let {
                        MessageMetadata(
                            tokensUsed = it.get("tokensUsed")?.asInt,
                            model = it.get("model")?.asString,
                            provider = it.get("provider")?.asString,
                            executionTime = it.get("executionTime")?.asLong,
                            cost = it.get("cost")?.asDouble
                        )
                    } ?: MessageMetadata(),
                    status = MessageStatus.valueOf(json.get("status")?.asString ?: "COMPLETED")
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 序列化为 JSON
     */
    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("role", role.name)
            addProperty("content", content)
            addProperty("timestamp", timestamp)
            if (attachments.isNotEmpty()) {
                add("attachments", com.google.gson.JsonArray().apply {
                    attachments.forEach { add(it.toJson()) }
                })
            }
            if (references.isNotEmpty()) {
                add("references", com.google.gson.JsonArray().apply {
                    references.forEach { ref ->
                        add(JsonObject().apply {
                            addProperty("messageId", ref.messageId)
                            addProperty("excerpt", ref.excerpt)
                            addProperty("timestamp", ref.timestamp)
                            addProperty("sender", ref.sender.name)
                        })
                    }
                })
            }
            add("metadata", JsonObject().apply {
                metadata.tokensUsed?.let { addProperty("tokensUsed", it) }
                metadata.model?.let { addProperty("model", it) }
                metadata.provider?.let { addProperty("provider", it) }
                metadata.executionTime?.let { addProperty("executionTime", it) }
                metadata.cost?.let { addProperty("cost", it) }
            })
            addProperty("status", status.name)
        }
    }
}
