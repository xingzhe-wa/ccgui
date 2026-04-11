package com.github.xingzhewa.ccgui.model.session

import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.util.IdGenerator
import com.google.gson.JsonObject

/**
 * 会话状态枚举
 */
enum class SessionStatus {
    /** 空闲 */
    IDLE,

    /** 思考中 */
    THINKING,

    /** 流式输出中 */
    STREAMING,

    /** 等待用户输入 */
    WAITING_FOR_USER,

    /** 错误 */
    ERROR
}

/**
 * 聊天会话
 *
 * @param id 会话 ID
 * @param name 会话名称
 * @param type 会话类型
 * @param projectId 项目 ID（仅 PROJECT 类型）
 * @param messages 消息列表
 * @param context 会话上下文
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param isActive 是否激活
 * @param status 会话状态
 */
data class ChatSession(
    val id: String = IdGenerator.sessionId(),
    val name: String,
    val type: SessionType,
    val projectId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val context: SessionContext = SessionContext(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false,
    val status: SessionStatus = SessionStatus.IDLE,
    /** 是否为待确认会话：新会话在第一次提问前为 true，提问后改为 false 并加入历史 */
    val isPending: Boolean = true
) {

    /**
     * 是否为空会话
     */
    val isEmpty: Boolean get() = messages.isEmpty()

    /**
     * 消息数量
     */
    val messageCount: Int get() = messages.size

    /**
     * 获取最后一条消息
     */
    val lastMessage: ChatMessage? get() = messages.lastOrNull()

    /**
     * 是否为项目会话
     */
    val isProjectSession: Boolean get() = type == SessionType.PROJECT

    /**
     * 是否为全局会话
     */
    val isGlobalSession: Boolean get() = type == SessionType.GLOBAL

    /**
     * 是否正在处理
     */
    val isProcessing: Boolean get() = status == SessionStatus.STREAMING || status == SessionStatus.THINKING

    /**
     * 添加消息
     */
    fun withMessage(message: ChatMessage): ChatSession {
        return copy(
            messages = messages + message,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 添加多条消息
     * 使用 ArrayList 优化大量消息添加的性能
     */
    fun withMessages(newMessages: List<ChatMessage>): ChatSession {
        return if (newMessages.isEmpty()) {
            this
        } else {
            copy(
                messages = ArrayList(messages + newMessages),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * 批量添加消息（性能优化版本）
     * 适用于添加大量消息的场景
     */
    fun withMessagesBatch(newMessages: List<ChatMessage>): ChatSession {
        if (newMessages.isEmpty()) return this
        return copy(
            messages = ArrayList(messages).apply { addAll(newMessages) },
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 更新最后一条消息
     */
    fun withUpdatedLastMessage(update: (ChatMessage) -> ChatMessage): ChatSession {
        if (messages.isEmpty()) return this
        return copy(
            messages = messages.dropLast(1) + update(messages.last()),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 清空消息
     */
    fun withClearedMessages(): ChatSession {
        return copy(
            messages = emptyList(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 标记为激活
     */
    fun withActivated(): ChatSession = copy(isActive = true)

    /**
     * 标记为非激活
     */
    fun withDeactivated(): ChatSession = copy(isActive = false)

    /**
     * 更新状态
     */
    fun withStatus(newStatus: SessionStatus): ChatSession = copy(status = newStatus)

    /**
     * 更新名称
     */
    fun withName(newName: String): ChatSession = copy(name = newName, updatedAt = System.currentTimeMillis())

    /**
     * 更新上下文
     */
    fun withContext(newContext: SessionContext): ChatSession = copy(context = newContext, updatedAt = System.currentTimeMillis())

    /**
     * 标记会话已完成第一次提问
     * 调用此方法后，会话将从待确认状态变为正式会话，并加入历史记录
     */
    fun withConfirmed(): ChatSession = copy(isPending = false, updatedAt = System.currentTimeMillis())

    companion object {
        /**
         * 创建项目会话
         */
        fun createProjectSession(name: String, projectId: String): ChatSession {
            return ChatSession(
                name = name,
                type = SessionType.PROJECT,
                projectId = projectId
            )
        }

        /**
         * 创建全局会话
         */
        fun createGlobalSession(name: String): ChatSession {
            return ChatSession(
                name = name,
                type = SessionType.GLOBAL
            )
        }

        /**
         * 创建临时会话
         */
        fun createTemporarySession(name: String = "临时会话"): ChatSession {
            return ChatSession(
                name = name,
                type = SessionType.TEMPORARY
            )
        }

        /**
         * 从 JSON 反序列化
         */
        fun fromJson(json: JsonObject): ChatSession? {
            return try {
                ChatSession(
                    id = json.get("id")?.asString ?: IdGenerator.sessionId(),
                    name = json.get("name")?.asString ?: "未命名会话",
                    type = SessionType.valueOf(json.get("type")?.asString ?: "GLOBAL"),
                    projectId = json.get("projectId")?.asString,
                    messages = json.getAsJsonArray("messages")?.mapNotNull {
                        ChatMessage.fromJson(it.asJsonObject)
                    } ?: emptyList(),
                    context = json.getAsJsonObject("context")?.let {
                        SessionContext.fromJson(it)
                    } ?: SessionContext(),
                    createdAt = json.get("createdAt")?.asLong ?: System.currentTimeMillis(),
                    updatedAt = json.get("updatedAt")?.asLong ?: System.currentTimeMillis(),
                    isActive = json.get("isActive")?.asBoolean ?: false,
                    status = SessionStatus.valueOf(json.get("status")?.asString ?: "IDLE"),
                    isPending = json.get("isPending")?.asBoolean ?: true
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
            addProperty("name", name)
            addProperty("type", type.name)
            projectId?.let { addProperty("projectId", it) }
            add("messages", com.google.gson.JsonArray().apply {
                messages.forEach { add(it.toJson()) }
            })
            add("context", context.toJson())
            addProperty("createdAt", createdAt)
            addProperty("updatedAt", updatedAt)
            addProperty("isActive", isActive)
            addProperty("status", status.name)
            addProperty("isPending", isPending)
        }
    }
}
