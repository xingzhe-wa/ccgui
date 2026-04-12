package com.github.claudecode.ccgui.infrastructure.storage

import com.github.claudecode.ccgui.model.session.ChatSession
import com.github.claudecode.ccgui.model.session.SessionType
import com.github.claudecode.ccgui.util.JsonUtils
import com.github.claudecode.ccgui.util.logger
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
        var version: Long = 1L,
        /** 消息版本快照存储: version -> snapshot JSON */
        var messageSnapshots: String = "{}"
    )

    private var state = State()

    /**
     * 内存缓存
     */
    private val sessionsCache = ConcurrentHashMap<String, ChatSession>()

    /**
     * 快照缓存: version -> (sessionId -> ChatSession snapshot)
     */
    private val snapshotsCache = ConcurrentHashMap<Long, Map<String, ChatSession>>()

    /**
     * 消息文件管理器引用
     * 用于处理大消息内容的文件存储
     */
    private val messageFileManager: MessageFileManager by lazy {
        MessageFileManager.getInstance(project)
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        // 重建缓存
        rebuildCache()
        rebuildSnapshotsCache()
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
     * 重建快照缓存
     */
    private fun rebuildSnapshotsCache() {
        snapshotsCache.clear()
        if (state.messageSnapshots.isEmpty() || state.messageSnapshots == "{}") {
            return
        }
        try {
            val jsonObj = JsonUtils.parseObject(state.messageSnapshots) ?: return
            jsonObj.entrySet().forEach { entry ->
                val version = entry.key.toLongOrNull() ?: return@forEach
                val sessionsMap = mutableMapOf<String, ChatSession>()
                val sessionsObj = entry.value.asJsonObject
                sessionsObj.entrySet().forEach { sessionEntry ->
                    val session = ChatSession.fromJson(sessionEntry.value.asJsonObject)
                    if (session != null) {
                        sessionsMap[sessionEntry.key] = session
                    }
                }
                snapshotsCache[version] = sessionsMap
            }
            logger.info("Rebuilt snapshots cache with ${snapshotsCache.size} versions")
        } catch (e: Exception) {
            logger.warn("Failed to rebuild snapshots cache: ${e.message}")
        }
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
        persistSnapshots()
    }

    // ==================== 快照管理 ====================

    /**
     * 持久化快照到存储
     */
    private fun persistSnapshots() {
        val jsonObj = com.google.gson.JsonObject()
        snapshotsCache.forEach { (version, sessionsMap) ->
            val sessionsObj = com.google.gson.JsonObject()
            sessionsMap.forEach { (sessionId, session) ->
                sessionsObj.add(sessionId, session.toJson())
            }
            jsonObj.add(version.toString(), sessionsObj)
        }
        state.messageSnapshots = JsonUtils.toJson(jsonObj)
    }

    /**
     * 创建当前会话状态的快照
     *
     * @return 创建的快照版本号
     */
    fun createSnapshot(): Long {
        val version = state.version
        // 创建当前所有会话的快照
        val snapshot = sessionsCache.values.associate { it.id to it }
        snapshotsCache[version] = snapshot
        state.version = version + 1
        persistSnapshots()
        persistSessions()
        logger.info("Created snapshot at version $version with ${snapshot.size} sessions")
        return version
    }

    /**
     * 回滚到指定版本
     *
     * @param version 目标版本号
     * @return true if rollback successful, false if version not found
     */
    fun rollbackTo(version: Long): Boolean {
        val snapshot = snapshotsCache[version]
        if (snapshot == null) {
            logger.warn("Snapshot version $version not found")
            return false
        }
        // 恢复快照中的会话
        snapshot.forEach { (sessionId, session) ->
            sessionsCache[sessionId] = session
        }
        // 删除比目标版本更新的快照
        val versionsToRemove = snapshotsCache.keys.filter { it > version }.toList()
        versionsToRemove.forEach { snapshotsCache.remove(it) }
        // 更新当前版本号
        state.version = version + 1
        persistSnapshots()
        persistSessions()
        logger.info("Rolled back to version $version, restored ${snapshot.size} sessions")
        return true
    }

    /**
     * 获取可用的快照版本列表
     *
     * @return 版本号列表（按版本号升序）
     */
    fun getSnapshotVersions(): List<Long> {
        return snapshotsCache.keys.sorted()
    }

    /**
     * 获取指定版本的快照会话
     *
     * @param version 快照版本号
     * @return 会话映射，如果版本不存在则返回 null
     */
    fun getSnapshot(version: Long): Map<String, ChatSession>? {
        return snapshotsCache[version]
    }

    /**
     * 获取当前版本号
     *
     * @return 当前版本号
     */
    fun getCurrentVersion(): Long {
        return state.version - 1
    }

    /**
     * 删除指定版本的快照
     *
     * @param version 要删除的版本号
     */
    fun deleteSnapshot(version: Long) {
        if (snapshotsCache.remove(version) != null) {
            persistSnapshots()
            logger.info("Deleted snapshot version $version")
        }
    }

    /**
     * 清空所有快照
     */
    fun clearSnapshots() {
        snapshotsCache.clear()
        state.version = 1L
        persistSnapshots()
        logger.info("Cleared all snapshots")
    }

    // ==================== 大消息内容管理 ====================

    /**
     * 准备消息内容进行存储
     *
     * 如果消息内容超过阈值，自动保存到文件并返回文件引用
     *
     * @param sessionId 会话 ID
     * @param messageId 消息 ID
     * @param content 消息内容
     * @return Pair(是否使用文件引用, 内容/引用)
     */
    suspend fun prepareMessageForStorage(sessionId: String, messageId: String, content: String): Pair<Boolean, String> {
        if (content.length >= MessageFileManager.SMALL_MESSAGE_THRESHOLD) {
            val fileRef = messageFileManager.saveMessage(sessionId, messageId, content)
            if (fileRef != null) {
                logger.debug("Message $messageId saved to file (${content.length} chars)")
                return Pair(true, fileRef)
            }
            logger.warn("Failed to save message $messageId to file, storing inline")
        }
        return Pair(false, content)
    }

    /**
     * 解析消息内容
     *
     * 如果内容是文件引用，自动从文件加载
     *
     * @param content 消息内容（可能是文件引用）
     * @return 实际消息内容
     */
    suspend fun resolveMessageContent(content: String): String {
        return messageFileManager.resolveContent(content)
    }

    /**
     * 处理会话中所有消息的内容解析
     *
     * 在加载会话后调用，将所有文件引用解析为实际内容
     *
     * @param session 包含消息的会话
     * @return 解析后的会话
     */
    suspend fun resolveSessionMessages(session: ChatSession): ChatSession {
        val resolvedMessages = session.messages.map { message ->
            val resolvedContent = resolveMessageContent(message.content)
            message.copy(content = resolvedContent)
        }
        return session.copy(messages = resolvedMessages)
    }

    /**
     * 删除会话的所有消息文件
     *
     * @param sessionId 会话 ID
     */
    suspend fun deleteSessionMessageFiles(sessionId: String) {
        messageFileManager.deleteSessionMessages(sessionId)
    }

    companion object {
        fun getInstance(project: Project): SessionStorage =
            project.getService(SessionStorage::class.java)
    }
}
