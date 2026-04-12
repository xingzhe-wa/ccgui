package com.github.claudecode.ccgui.application.mcp

import com.github.claudecode.ccgui.model.agent.AgentScope
import com.github.claudecode.ccgui.model.mcp.McpScope
import com.github.claudecode.ccgui.model.skill.SkillScope
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 作用域管理器
 *
 * 负责:
 * - 管理全局/项目/会话级别的作用域
 * - 作用域继承与覆盖规则
 * - 作用域优先级处理
 * - 作用域数据隔离
 *
 * @param project IntelliJ项目实例
 */
@Service(Service.Level.PROJECT)
class ScopeManager(private val project: Project) {

    private val log = logger<ScopeManager>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * 作用域类型
     */
    enum class ScopeType {
        /** 全局作用域 - 所有项目共享 */
        GLOBAL,

        /** 项目作用域 - 当前项目专属 */
        PROJECT,

        /** 会话作用域 - 当前会话专属 */
        SESSION
    }

    /**
     * 作用域数据
     */
    data class ScopeData(
        val scopeType: ScopeType,
        val scopeId: String,
        val data: MutableMap<String, Any> = mutableMapOf(),
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    ) {
        /**
         * 获取值
         */
        fun get(key: String): Any? = data[key]

        /**
         * 设置值
         */
        fun set(key: String, value: Any) {
            data[key] = value
        }

        /**
         * 删除值
         */
        fun remove(key: String): Any? = data.remove(key)

        /**
         * 清空所有值
         */
        fun clear() {
            data.clear()
        }

        /**
         * 获取所有键
         */
        fun keys(): Set<String> = data.keys

        /**
         * 是否包含键
         */
        fun contains(key: String): Boolean = data.containsKey(key)

        /**
         * 创建副本
         */
        fun copy(): ScopeData {
            return ScopeData(
                scopeType = scopeType,
                scopeId = scopeId,
                data = data.toMutableMap(),
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }

    /** 作用域数据存储 (key: scopeType_scopeId) */
    private val scopes = ConcurrentHashMap<String, ScopeData>()

    /** 当前活跃的会话作用域 ID */
    private val _currentSessionScopeId = MutableStateFlow<String?>(null)
    val currentSessionScopeId: StateFlow<String?> = _currentSessionScopeId.asStateFlow()

    // ==================== 核心 API ====================

    /**
     * 获取或创建作用域
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域 ID
     * @return 作用域数据
     */
    fun getOrCreateScope(scopeType: ScopeType, scopeId: String = ""): ScopeData {
        val key = buildScopeKey(scopeType, scopeId)
        return scopes.getOrPut(key) {
            ScopeData(
                scopeType = scopeType,
                scopeId = if (scopeId.isEmpty()) getDefaultScopeId(scopeType) else scopeId
            )
        }
    }

    /**
     * 获取作用域
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域 ID
     * @return 作用域数据（如果存在）
     */
    fun getScope(scopeType: ScopeType, scopeId: String = ""): ScopeData? {
        val key = buildScopeKey(scopeType, scopeId)
        return scopes[key]
    }

    /**
     * 删除作用域
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域 ID
     * @return 是否成功
     */
    fun deleteScope(scopeType: ScopeType, scopeId: String = ""): Boolean {
        val key = buildScopeKey(scopeType, scopeId)
        val removed = scopes.remove(key) != null

        if (removed) {
            log.info("Scope deleted: $key")
        }

        return removed
    }

    /**
     * 设置当前会话作用域
     *
     * @param sessionId 会话 ID
     */
    fun setCurrentSessionScope(sessionId: String) {
        _currentSessionScopeId.value = sessionId
        log.debug("Current session scope set to: $sessionId")
    }

    /**
     * 清除当前会话作用域
     */
    fun clearCurrentSessionScope() {
        _currentSessionScopeId.value = null
        log.debug("Current session scope cleared")
    }

    /**
     * 获取值（按作用域优先级）
     *
     * 优先级: 会话 > 项目 > 全局
     *
     * @param key 键
     * @return 值
     */
    fun get(key: String): Any? {
        // 1. 尝试从会话作用域获取
        val sessionId = _currentSessionScopeId.value
        if (sessionId != null) {
            val sessionScope = getScope(ScopeType.SESSION, sessionId)
            if (sessionScope?.contains(key) == true) {
                return sessionScope.get(key)
            }
        }

        // 2. 尝试从项目作用域获取
        val projectScope = getScope(ScopeType.PROJECT, project.name)
        if (projectScope?.contains(key) == true) {
            return projectScope.get(key)
        }

        // 3. 尝试从全局作用域获取
        val globalScope = getScope(ScopeType.GLOBAL)
        if (globalScope?.contains(key) == true) {
            return globalScope.get(key)
        }

        return null
    }

    /**
     * 设置值到指定作用域
     *
     * @param key 键
     * @param value 值
     * @param scopeType 作用域类型
     * @param scopeId 作用域 ID
     */
    fun set(key: String, value: Any, scopeType: ScopeType, scopeId: String = "") {
        val scope = getOrCreateScope(scopeType, scopeId)
        scope.set(key, value)
        log.debug("Set value in scope: $scopeType/$scopeId - $key")
    }

    /**
     * 删除指定作用域的值
     *
     * @param key 键
     * @param scopeType 作用域类型
     * @param scopeId 作用域 ID
     * @return 被删除的值
     */
    fun remove(key: String, scopeType: ScopeType, scopeId: String = ""): Any? {
        val scope = getScope(scopeType, scopeId) ?: return null
        return scope.remove(key)
    }

    /**
     * 删除所有作用域中的值
     *
     * @param key 键
     * @return 被删除的值列表
     */
    fun removeFromAllScopes(key: String): List<Any> {
        val removed = mutableListOf<Any>()

        scopes.values.forEach { scope ->
            val value = scope.remove(key)
            if (value != null) {
                removed.add(value)
            }
        }

        log.debug("Removed value from all scopes: $key - ${removed.size} occurrences")
        return removed
    }

    /**
     * 获取所有作用域中的值
     *
     * @param key 键
     * @return 作用域到值的映射
     */
    fun getAllValues(key: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        scopes.forEach { (scopeKey, scope) ->
            val value = scope.get(key)
            if (value != null) {
                result[scopeKey] = value
            }
        }

        return result
    }

    /**
     * 复制作用域数据
     *
     * @param sourceScopeType 源作用域类型
     * @param sourceScopeId 源作用域 ID
     * @param targetScopeType 目标作用域类型
     * @param targetScopeId 目标作用域 ID
     * @param overwrite 是否覆盖已存在的值
     */
    fun copyScopeData(
        sourceScopeType: ScopeType,
        sourceScopeId: String,
        targetScopeType: ScopeType,
        targetScopeId: String,
        overwrite: Boolean = false
    ) {
        val sourceScope = getScope(sourceScopeType, sourceScopeId) ?: return
        val targetScope = getOrCreateScope(targetScopeType, targetScopeId)

        sourceScope.keys().forEach { key ->
            if (overwrite || !targetScope.contains(key)) {
                targetScope.set(key, sourceScope.get(key)!!)
            }
        }

        log.info("Copied scope data: $sourceScopeType/$sourceScopeId -> $targetScopeType/$targetScopeId")
    }

    /**
     * 合并作用域数据
     *
     * @param sourceScopes 源作用域列表
     * @param targetScopeType 目标作用域类型
     * @param targetScopeId 目标作用域 ID
     */
    fun mergeScopeData(
        sourceScopes: List<Pair<ScopeType, String>>,
        targetScopeType: ScopeType,
        targetScopeId: String
    ) {
        val targetScope = getOrCreateScope(targetScopeType, targetScopeId)

        sourceScopes.forEach { (scopeType, scopeId) ->
            val sourceScope = getScope(scopeType, scopeId)
            if (sourceScope != null) {
                sourceScope.keys().forEach { key ->
                    if (!targetScope.contains(key)) {
                        targetScope.set(key, sourceScope.get(key)!!)
                    }
                }
            }
        }

        log.info("Merged scope data into: $targetScopeType/$targetScopeId")
    }

    /**
     * 清空作用域
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域 ID
     */
    fun clearScope(scopeType: ScopeType, scopeId: String = "") {
        val scope = getScope(scopeType, scopeId)
        scope?.clear()
        log.debug("Cleared scope: $scopeType/$scopeId")
    }

    /**
     * 清空所有作用域
     */
    fun clearAllScopes() {
        scopes.values.forEach { it.clear() }
        log.info("Cleared all scopes")
    }

    /**
     * 获取所有作用域
     *
     * @return 作用域列表
     */
    fun getAllScopes(): List<ScopeData> {
        return scopes.values.toList()
    }

    /**
     * 按类型获取作用域
     *
     * @param scopeType 作用域类型
     * @return 作用域列表
     */
    fun getScopesByType(scopeType: ScopeType): List<ScopeData> {
        return scopes.values.filter { it.scopeType == scopeType }
    }

    /**
     * 导出作用域数据
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域 ID
     * @return 数据 Map
     */
    fun exportScopeData(scopeType: ScopeType, scopeId: String = ""): Map<String, Any> {
        val scope = getScope(scopeType, scopeId)
        return scope?.data?.toMap() ?: emptyMap()
    }

    /**
     * 导入作用域数据
     *
     * @param data 数据 Map
     * @param scopeType 作用域类型
     * @param scopeId 作用域 ID
     * @param overwrite 是否覆盖已存在的值
     */
    fun importScopeData(
        data: Map<String, Any>,
        scopeType: ScopeType,
        scopeId: String = "",
        overwrite: Boolean = false
    ) {
        val scope = getOrCreateScope(scopeType, scopeId)

        data.forEach { (key, value) ->
            if (overwrite || !scope.contains(key)) {
                scope.set(key, value)
            }
        }

        log.info("Imported ${data.size} items into scope: $scopeType/$scopeId")
    }

    // ==================== 内部方法 ====================

    /**
     * 构建作用域键
     */
    private fun buildScopeKey(scopeType: ScopeType, scopeId: String): String {
        val effectiveScopeId = if (scopeId.isEmpty()) getDefaultScopeId(scopeType) else scopeId
        return "${scopeType.name}_$effectiveScopeId"
    }

    /**
     * 获取默认作用域 ID
     */
    private fun getDefaultScopeId(scopeType: ScopeType): String {
        return when (scopeType) {
            ScopeType.GLOBAL -> "global"
            ScopeType.PROJECT -> project.name
            ScopeType.SESSION -> _currentSessionScopeId.value ?: "default"
        }
    }

    /**
     * 销毁资源
     */
    fun dispose() {
        scope.cancel()
        scopes.clear()
        _currentSessionScopeId.value = null
    }

    companion object {
        fun getInstance(project: Project): ScopeManager =
            project.getService(ScopeManager::class.java)

        /**
         * 将 SkillScope 转换为 ScopeType
         */
        fun fromSkillScope(skillScope: SkillScope): ScopeType {
            return when (skillScope) {
                SkillScope.GLOBAL -> ScopeType.GLOBAL
                SkillScope.PROJECT -> ScopeType.PROJECT
            }
        }

        /**
         * 将 AgentScope 转换为 ScopeType
         */
        fun fromAgentScope(agentScope: AgentScope): ScopeType {
            return when (agentScope) {
                AgentScope.GLOBAL -> ScopeType.GLOBAL
                AgentScope.PROJECT -> ScopeType.PROJECT
                AgentScope.SESSION -> ScopeType.SESSION
            }
        }

        /**
         * 将 McpScope 转换为 ScopeType
         */
        fun fromMcpScope(mcpScope: McpScope): ScopeType {
            return when (mcpScope) {
                McpScope.GLOBAL -> ScopeType.GLOBAL
                McpScope.PROJECT -> ScopeType.PROJECT
            }
        }
    }
}
