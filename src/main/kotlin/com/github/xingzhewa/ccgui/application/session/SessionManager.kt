package com.github.xingzhewa.ccgui.application.session

import com.github.xingzhewa.ccgui.application.context.ContextManager
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.SessionCreatedEvent
import com.github.xingzhewa.ccgui.infrastructure.eventbus.SessionDeletedEvent
import com.github.xingzhewa.ccgui.infrastructure.eventbus.SessionSwitchedEvent
import com.github.xingzhewa.ccgui.infrastructure.storage.SearchFilters
import com.github.xingzhewa.ccgui.infrastructure.storage.SessionStorage
import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.session.ChatSession
import com.github.xingzhewa.ccgui.model.session.SessionType
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 会话管理器
 *
 * 管理会话的 CRUD、切换、持久化
 */
@Service(Service.Level.PROJECT)
class SessionManager(private val project: Project) : Disposable {

    private val log = logger<SessionManager>()
    private val sessionStorage: SessionStorage by lazy { SessionStorage.getInstance(project) }
    private val contextManager: ContextManager by lazy { ContextManager.getInstance(project) }

    /** 所有会话 */
    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    /** 当前会话 ID */
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    init {
        // 从存储加载会话
        loadSessions()
        log.info("SessionManager initialized with ${_sessions.value.size} sessions")
    }

    /**
     * 从存储加载会话
     */
    private fun loadSessions() {
        val allSessions = sessionStorage.getAllSessions()
        _sessions.value = allSessions
        _currentSessionId.value = sessionStorage.getActiveSessionId()
    }

    /**
     * 创建会话
     */
    fun createSession(name: String, type: SessionType): ChatSession {
        val session = when (type) {
            SessionType.PROJECT -> ChatSession.createProjectSession(name, project.name)
            SessionType.GLOBAL -> ChatSession.createGlobalSession(name)
            SessionType.TEMPORARY -> ChatSession.createTemporarySession(name)
        }

        sessionStorage.saveSession(session)
        _sessions.value = sessionStorage.getAllSessions()
        setCurrentSession(session.id)

        EventBus.publish(SessionCreatedEvent(session.id, session.name))
        log.info("Created session: ${session.id}")
        return session
    }

    /**
     * 获取会话
     */
    fun getSession(sessionId: String): ChatSession? {
        return sessionStorage.getSession(sessionId)
    }

    /**
     * 获取当前会话
     */
    fun getCurrentSession(): ChatSession? {
        return _currentSessionId.value?.let { sessionStorage.getSession(it) }
    }

    /**
     * 切换当前会话
     */
    fun setCurrentSession(sessionId: String?) {
        _currentSessionId.value?.let { oldId ->
            if (oldId != sessionId) {
                sessionStorage.getSession(oldId)?.let {
                    sessionStorage.updateSession(it.withDeactivated())
                }
            }
        }

        _currentSessionId.value = sessionId
        sessionStorage.setActiveSession(sessionId)

        sessionId?.let {
            sessionStorage.getSession(it)?.let { session ->
                sessionStorage.updateSession(session.withActivated())
            }
            EventBus.publish(SessionSwitchedEvent(it))
        }

        _sessions.value = sessionStorage.getAllSessions()
        log.info("Switched to session: $sessionId")
    }

    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String) {
        sessionStorage.deleteSession(sessionId)
        contextManager.removeSession(sessionId)

        if (_currentSessionId.value == sessionId) {
            // 切换到另一个会话
            val remaining = sessionStorage.getAllSessions()
            _currentSessionId.value = remaining.firstOrNull()?.id
            sessionStorage.setActiveSession(_currentSessionId.value)
        }

        _sessions.value = sessionStorage.getAllSessions()
        EventBus.publish(SessionDeletedEvent(sessionId))
        log.info("Deleted session: $sessionId")
    }

    /**
     * 添加消息到会话
     */
    fun addMessage(sessionId: String, message: ChatMessage) {
        val session = sessionStorage.getSession(sessionId) ?: return
        sessionStorage.updateSession(session.withMessage(message))
        _sessions.value = sessionStorage.getAllSessions()
    }

    /**
     * 更新会话中的消息
     */
    fun updateMessage(sessionId: String, messageId: String, content: String) {
        val session = sessionStorage.getSession(sessionId) ?: return
        val updatedMessages = session.messages.map { msg ->
            if (msg.id == messageId) msg.copy(content = content) else msg
        }
        sessionStorage.updateSession(session.copy(messages = updatedMessages))
        _sessions.value = sessionStorage.getAllSessions()
    }

    /**
     * 清空会话消息
     */
    fun clearSession(sessionId: String) {
        val session = sessionStorage.getSession(sessionId) ?: return
        sessionStorage.updateSession(session.withClearedMessages())
        contextManager.resetSession(sessionId)
        _sessions.value = sessionStorage.getAllSessions()
    }

    /**
     * 搜索会话（支持过滤器）
     *
     * @param query 搜索关键词（向后兼容）
     * @param filters 搜索过滤器（可选）
     */
    fun searchSessions(query: String, filters: SearchFilters? = null): List<ChatSession> {
        val effectiveFilters = filters?.copy(query = query) ?: SearchFilters(query = query)
        return sessionStorage.searchSessions(effectiveFilters)
    }

    /**
     * 搜索会话（仅过滤器）
     *
     * @param filters 搜索过滤器
     */
    fun searchSessions(filters: SearchFilters): List<ChatSession> {
        return sessionStorage.searchSessions(filters)
    }

    /**
     * 获取会话数量
     */
    fun getSessionCount(): Int = sessionStorage.getSessionCount()

    /**
     * 获取指定类型的会话
     */
    fun getSessionsByType(type: SessionType): List<ChatSession> {
        return sessionStorage.getSessionsByType(type)
    }

    /**
     * 导入会话（从 JSON）
     */
    fun importSession(jsonContent: String): ChatSession? {
        return try {
            val json = com.google.gson.JsonParser.parseString(jsonContent).asJsonObject
            val session = ChatSession.fromJson(json) ?: return null
            sessionStorage.saveSession(session)
            _sessions.value = sessionStorage.getAllSessions()
            log.info("Imported session: ${session.id}")
            session
        } catch (e: Exception) {
            log.warn("Failed to import session: ${e.message}")
            null
        }
    }

    override fun dispose() {
        // 持久化未保存的更改
        sessionStorage.flush()
    }

    companion object {
        fun getInstance(project: Project): SessionManager =
            project.getService(SessionManager::class.java)
    }
}
