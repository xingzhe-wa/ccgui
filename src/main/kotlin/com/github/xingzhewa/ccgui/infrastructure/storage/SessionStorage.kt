package com.github.xingzhewa.ccgui.infrastructure.storage

import com.github.xingzhewa.ccgui.model.session.ChatSession
import com.github.xingzhewa.ccgui.model.session.SessionType
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * 日期范围过滤器
 */
data class DateRange(
    val start: Long,
    val end: Long
)

/**
 * 会话搜索过滤器
 *
 * @param query 搜索关键词（名称/消息内容）
 * @param sessionType 会话类型过滤（可选）
 * @param dateRange 日期范围过滤（可选）
 * @param tags 标签过滤（可选）
 */
data class SearchFilters(
    val query: String = "",
    val sessionType: SessionType? = null,
    val dateRange: DateRange? = null,
    val tags: List<String>? = null
)

/**
 * 会话存储
 *
 * 管理会话的持久化存储
 */
@State(name = "CCGuiSessions", storages = [Storage("ccgui-sessions.xml")])
class SessionStorage(private val project: Project) : PersistentStateComponent<SessionStorage.State> {

    private val logger = logger<SessionStorage>()

    /**
     * 存储状态
     */
    data class State(
        var sessions: String = "[]",
        var activeSessionId: String? = null,
        var version: Int = 1
    )

    private var state = State()

    /**
     * 内存缓存
     */
    private val sessionsCache = ConcurrentHashMap<String, ChatSession>()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        // 重建缓存
        rebuildCache()
    }

    /**
     * 重建内存缓存
     */
    private fun rebuildCache() {
        sessionsCache.clear()
        val sessions = parseSessionList(state.sessions)

        sessions.forEach { session ->
            sessionsCache[session.id] = session
        }
        logger.info("Rebuilt session cache with ${sessionsCache.size} sessions")
    }

    /**
     * 解析会话列表
     */
    private fun parseSessionList(json: String): List<ChatSession> {
        return try {
            val array = JsonUtils.parseArray(json) ?: return emptyList()
            array.mapNotNull { ChatSession.fromJson(it.asJsonObject) }
        } catch (e: Exception) {
            logger.warn("Failed to parse session list: ${e.message}")
            emptyList()
        }
    }

    /**
     * 保存所有会话到持久化
     */
    private fun persistSessions() {
        state.sessions = JsonUtils.toJson(sessionsCache.values.toList())
    }

    // ==================== 会话操作 ====================

    /**
     * 获取所有会话
     */
    fun getAllSessions(): List<ChatSession> {
        return sessionsCache.values.toList()
            .sortedByDescending { it.updatedAt }
    }

    /**
     * 获取指定类型的会话
     */
    fun getSessionsByType(type: SessionType): List<ChatSession> {
        return sessionsCache.values
            .filter { it.type == type }
            .sortedByDescending { it.updatedAt }
    }

    /**
     * 获取会话
     */
    fun getSession(sessionId: String): ChatSession? {
        return sessionsCache[sessionId]
    }

    /**
     * 保存会话
     */
    fun saveSession(session: ChatSession) {
        sessionsCache[session.id] = session
        persistSessions()
        logger.debug("Saved session: ${session.id}")
    }

    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String) {
        sessionsCache.remove(sessionId)
        if (state.activeSessionId == sessionId) {
            state.activeSessionId = null
        }
        persistSessions()
        logger.debug("Deleted session: $sessionId")
    }

    /**
     * 更新会话
     * 保留传入session的updatedAt时间戳（如果有的话）
     */
    fun updateSession(session: ChatSession) {
        // 如果传入的session有更新的时间戳，使用它；否则更新为当前时间
        val updatedSession = if (session.updatedAt > (sessionsCache[session.id]?.updatedAt ?: 0)) {
            session
        } else {
            session.copy(updatedAt = System.currentTimeMillis())
        }
        sessionsCache[session.id] = updatedSession
        persistSessions()
    }

    /**
     * 获取活跃会话 ID
     */
    fun getActiveSessionId(): String? {
        return state.activeSessionId
    }

    /**
     * 设置活跃会话
     */
    fun setActiveSession(sessionId: String?) {
        state.activeSessionId = sessionId
    }

    /**
     * 获取活跃会话
     */
    fun getActiveSession(): ChatSession? {
        return state.activeSessionId?.let { sessionsCache[it] }
    }

    /**
     * 搜索会话（过滤器版本）
     */
    fun searchSessions(filters: SearchFilters): List<ChatSession> {
        return sessionsCache.values.filter { session ->
            // 类型过滤
            if (filters.sessionType != null && session.type != filters.sessionType) {
                return@filter false
            }

            // 日期范围过滤
            if (filters.dateRange != null) {
                val inRange = session.createdAt >= filters.dateRange.start &&
                    session.createdAt <= filters.dateRange.end
                if (!inRange) return@filter false
            }

            // 关键词过滤（名称 + 消息内容）
            if (filters.query.isNotBlank()) {
                val lowerQuery = filters.query.lowercase()
                val matchesName = session.name.lowercase().contains(lowerQuery)
                val matchesMessage = session.messages.any {
                    it.content.lowercase().contains(lowerQuery)
                }
                if (!matchesName && !matchesMessage) return@filter false
            }

            true
        }.sortedByDescending { it.updatedAt }
    }

    /**
     * 获取会话数量
     */
    fun getSessionCount(): Int = sessionsCache.size

    /**
     * 获取会话数量（按类型）
     */
    fun getSessionCount(type: SessionType): Int {
        return sessionsCache.values.count { it.type == type }
    }

    /**
     * 清空所有会话
     */
    fun clearAllSessions() {
        sessionsCache.clear()
        state.activeSessionId = null
        persistSessions()
        logger.info("Cleared all sessions")
    }

    /**
     * 清空指定类型的会话
     */
    fun clearSessions(type: SessionType) {
        val toRemove = sessionsCache.values.filter { it.type == type }.map { it.id }
        toRemove.forEach { sessionsCache.remove(it) }
        if (state.activeSessionId in toRemove) {
            state.activeSessionId = null
        }
        persistSessions()
        logger.info("Cleared ${toRemove.size} sessions of type $type")
    }

    /**
     * 强制刷新持久化
     */
    fun flush() {
        persistSessions()
    }

    companion object {
        fun getInstance(project: Project): SessionStorage =
            project.getService(SessionStorage::class.java)
    }
}
