package com.github.claudecode.ccgui.model.message

import com.github.claudecode.ccgui.infrastructure.storage.MessageContentStorage
import com.github.claudecode.ccgui.util.IdGenerator
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
 * @param version 消息版本号（用于快照和回滚）
 * @param parentVersion 父版本号（指向创建此版本的消息版本）
 */
data class ChatMessage(
    val id: String = IdGenerator.messageId(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<ContentPart> = emptyList(),
    val references: List<MessageReference> = emptyList(),
    val metadata: MessageMetadata = MessageMetadata(),
    val status: MessageStatus = MessageStatus.COMPLETED,
    val version: Long = 0L,
    val parentVersion: Long? = null
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

    /**
     * 是否为快照版本
     */
    val isSnapshot: Boolean get() = parentVersion != null

    /**
     * 创建新版本的消息
     *
     * @param newVersion 新版本号
     * @return 新版本消息
     */
    fun createNewVersion(newVersion: Long): ChatMessage {
        return copy(
            version = newVersion,
            parentVersion = version.takeIf { it > 0 },
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * 将当前消息标记为快照
     *
     * @param parentVersion 父版本号
     * @return 快照消息
     */
    fun markAsSnapshot(parentVersion: Long): ChatMessage {
        return copy(parentVersion = parentVersion)
    }

    companion object {
        /**
         * 消息引用格式正则表达式
         * 匹配格式: [msg_xxx] 或 [sess_xxx]
         */
        private val MESSAGE_REFERENCE_PATTERN = Regex("""\[(msg_[a-zA-Z0-9_]+|sess_[a-zA-Z0-9_]+)\]""")

        /**
         * 从内容中提取消息引用ID
         *
         * 检测 `[messageId]` 格式并返回消息ID列表
         *
         * @param content 消息内容
         * @return 消息ID列表
         */
        fun extractMessageIds(content: String): List<String> {
            return MESSAGE_REFERENCE_PATTERN.findAll(content)
                .map { it.groupValues[1] }
                .distinct()
                .toList()
        }

        /**
         * 检查内容是否包含消息引用
         *
         * @param content 消息内容
         * @return true if content contains message references
         */
        fun hasMessageReferences(content: String): Boolean {
            return MESSAGE_REFERENCE_PATTERN.containsMatchIn(content)
        }

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
                    status = MessageStatus.valueOf(json.get("status")?.asString ?: "COMPLETED"),
                    version = json.get("version")?.asLong ?: 0L,
                    parentVersion = json.get("parentVersion")?.asLong?.takeIf { it > 0L }
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
            addProperty("version", version)
            parentVersion?.let { addProperty("parentVersion", it) }
        }
    }
}
