package com.github.xingzhewa.ccgui.application.agent

import com.github.xingzhewa.ccgui.infrastructure.eventbus.AgentCompletedEvent
import com.github.xingzhewa.ccgui.infrastructure.eventbus.AgentStartedEvent
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.model.agent.Agent
import com.github.xingzhewa.ccgui.model.agent.AgentCapability
import com.github.xingzhewa.ccgui.model.agent.AgentMode
import com.github.xingzhewa.ccgui.model.agent.AgentScope
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
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
 * Agents 管理器
 *
 * 负责:
 * - Agent 的增删改查
 * - Agent 能力管理
 * - Agent 作用域管理（全局/项目/会话）
 * - Agent 运行模式管理
 * - 内置 Agent 管理
 *
 * @param project IntelliJ项目实例
 */
@Service(Service.Level.PROJECT)
class AgentsManager(private val project: Project) : Disposable {

    private val log = logger<AgentsManager>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 所有 Agents (key: agentId) */
    private val agents = ConcurrentHashMap<String, Agent>()

    /** 按能力索引的 Agents */
    private val agentsByCapability = ConcurrentHashMap<AgentCapability, MutableSet<String>>()

    /** 按作用域索引的 Agents */
    private val agentsByScope = ConcurrentHashMap<AgentScope, MutableSet<String>>()

    /** 当前活跃的 Agent 任务 */
    private val _activeTasks = MutableStateFlow<Map<String, String>>(emptyMap()) // agentId -> taskId
    val activeTasks: StateFlow<Map<String, String>> = _activeTasks.asStateFlow()

    /** 所有 Agents 列表 */
    private val _allAgents = MutableStateFlow<List<Agent>>(emptyList())
    val allAgents: StateFlow<List<Agent>> = _allAgents.asStateFlow()

    // ==================== 初始化 ====================

    init {
        loadBuiltinAgents()
        loadAgentsFromStorage()
        log.info("AgentsManager initialized for project: ${project.name}")
    }

    /**
     * 加载内置 Agents
     */
    private fun loadBuiltinAgents() {
        val builtinAgents = listOf(
            Agent.codeReviewer(),
            Agent(
                name = "代码生成器",
                description = "根据需求生成高质量代码",
                avatar = "⚡",
                systemPrompt = "你是一个专业的代码生成助手。根据用户需求生成清晰、高效、可维护的代码。",
                capabilities = listOf(
                    AgentCapability.CODE_GENERATION,
                    AgentCapability.DOCUMENTATION
                ),
                mode = AgentMode.BALANCED
            ),
            Agent(
                name = "重构专家",
                description = "重构代码以提升质量和可维护性",
                avatar = "🔧",
                systemPrompt = "你是一个代码重构专家。分析代码并提供重构建议，重点关注代码质量、性能和可维护性。",
                capabilities = listOf(
                    AgentCapability.REFACTORING,
                    AgentCapability.CODE_REVIEW
                ),
                mode = AgentMode.CAUTIOUS
            ),
            Agent(
                name = "测试工程师",
                description = "为代码生成完整的单元测试",
                avatar = "🧪",
                systemPrompt = "你是一个测试工程师。为代码生成完整的单元测试，覆盖主要场景和边界情况。",
                capabilities = listOf(
                    AgentCapability.TESTING,
                    AgentCapability.CODE_GENERATION
                ),
                mode = AgentMode.BALANCED
            ),
            Agent(
                name = "文档助手",
                description = "为代码生成清晰的文档",
                avatar = "📚",
                systemPrompt = "你是一个文档助手。为代码生成清晰、完整的文档，包括功能说明、参数说明和使用示例。",
                capabilities = listOf(
                    AgentCapability.DOCUMENTATION,
                    AgentCapability.CODE_REVIEW
                ),
                mode = AgentMode.BALANCED
            ),
            Agent(
                name = "调试专家",
                description = "诊断和修复代码中的问题",
                avatar = "🐛",
                systemPrompt = "你是一个调试专家。分析代码中的问题，提供诊断和修复建议。",
                capabilities = listOf(
                    AgentCapability.DEBUGGING,
                    AgentCapability.CODE_REVIEW
                ),
                mode = AgentMode.CAUTIOUS
            ),
            Agent(
                name = "全能助手",
                description = "具备多种能力的综合助手",
                avatar = "🤖",
                systemPrompt = "你是一个全能的代码助手，可以帮助完成各种编程任务。",
                capabilities = AgentCapability.entries.toList(),
                tools = listOf("Read", "Write", "Glob", "Grep", "Bash"),
                mode = AgentMode.BALANCED
            )
        )

        builtinAgents.forEach { agent ->
            addAgent(agent)
        }

        log.info("Loaded ${builtinAgents.size} builtin agents")
    }

    /** 内置 Agent IDs，用于区分用户自定义 Agents */
    private val builtinAgentIds = mutableSetOf<String>()

    /**
     * 从存储加载 Agents
     *
     * 使用 IntelliJ PropertiesComponent 持久化用户自定义 Agents。
     * 内置 Agents 始终作为默认存在，不会被存储覆盖。
     */
    private fun loadAgentsFromStorage() {
        log.debug("Loading agents from storage...")
        // 记录已加载的内置 Agent IDs
        builtinAgentIds.addAll(agents.keys)
        try {
            val properties = com.intellij.openapi.project.ProjectManager.getInstance().defaultProject
                .getService(com.intellij.ide.util.PropertiesComponent::class.java)
            val storedJson = properties.getValue("ccgui.custom.agents") ?: return

            val array = JsonUtils.parseArray(storedJson) ?: return
            var loadedCount = 0
            array.forEach { element ->
                try {
                    val agent = Agent.fromJson(element.asJsonObject) ?: return@forEach
                    // 只加载非内置（用户自定义）的 Agents，内置 Agents 已在 loadBuiltinAgents 中加载
                    if (!agents.containsKey(agent.id)) {
                        addAgent(agent)
                        loadedCount++
                    }
                } catch (e: Exception) {
                    log.warn("Failed to load agent from storage: ${e.message}")
                }
            }
            log.info("Loaded $loadedCount custom agents from storage")
        } catch (e: Exception) {
            log.error("Failed to load agents from storage", e)
        }
    }

    /**
     * 持久化用户自定义 Agents 到存储
     */
    private fun persistCustomAgents() {
        try {
            val customAgents = agents.values.filter { it.id !in builtinAgentIds }
            val jsonArray = com.google.gson.JsonArray()
            customAgents.forEach { agent ->
                jsonArray.add(agent.toJson())
            }
            val properties = com.intellij.openapi.project.ProjectManager.getInstance().defaultProject
                .getService(com.intellij.ide.util.PropertiesComponent::class.java)
            properties.setValue("ccgui.custom.agents", jsonArray.toString())
            log.debug("Persisted ${customAgents.size} custom agents")
        } catch (e: Exception) {
            log.error("Failed to persist custom agents", e)
        }
    }

    // ==================== 核心 API ====================

    /**
     * 添加 Agent
     *
     * @param agent Agent
     * @return 是否成功
     */
    fun addAgent(agent: Agent): Boolean {
        if (agents.containsKey(agent.id)) {
            log.warn("Agent already exists: ${agent.id}")
            return false
        }

        agents[agent.id] = agent

        // 更新索引
        agent.capabilities.forEach { capability ->
            agentsByCapability.getOrPut(capability) { mutableSetOf() }.add(agent.id)
        }
        agentsByScope.getOrPut(agent.scope) { mutableSetOf() }.add(agent.id)

        updateAllAgents()

        log.info("Agent added: ${agent.id} - ${agent.name}")

        if (agent.id !in builtinAgentIds) {
            persistCustomAgents()
        }

        return true
    }

    /**
     * 更新 Agent
     *
     * @param agent 更新后的 Agent
     * @return 是否成功
     */
    fun updateAgent(agent: Agent): Boolean {
        if (!agents.containsKey(agent.id)) {
            log.warn("Agent not found: ${agent.id}")
            return false
        }

        val oldAgent = agents[agent.id]!!

        // 更新能力索引
        val oldCapabilities = oldAgent.capabilities.toSet()
        val newCapabilities = agent.capabilities.toSet()

        oldCapabilities.forEach { capability ->
            if (capability !in newCapabilities) {
                agentsByCapability[capability]?.remove(agent.id)
            }
        }

        newCapabilities.forEach { capability ->
            if (capability !in oldCapabilities) {
                agentsByCapability.getOrPut(capability) { mutableSetOf() }.add(agent.id)
            }
        }

        // 更新作用域索引
        if (oldAgent.scope != agent.scope) {
            agentsByScope[oldAgent.scope]?.remove(agent.id)
            agentsByScope.getOrPut(agent.scope) { mutableSetOf() }.add(agent.id)
        }

        agents[agent.id] = agent
        updateAllAgents()

        log.info("Agent updated: ${agent.id}")

        persistCustomAgents()

        return true
    }

    /**
     * 删除 Agent
     *
     * @param agentId Agent ID
     * @return 是否成功
     */
    fun deleteAgent(agentId: String): Boolean {
        val agent = agents.remove(agentId) ?: return false

        // 更新索引
        agent.capabilities.forEach { capability ->
            agentsByCapability[capability]?.remove(agentId)
        }
        agentsByScope[agent.scope]?.remove(agentId)

        updateAllAgents()

        log.info("Agent deleted: $agentId")

        persistCustomAgents()

        return true
    }

    /**
     * 获取 Agent
     *
     * @param agentId Agent ID
     * @return Agent
     */
    fun getAgent(agentId: String): Agent? {
        return agents[agentId]
    }

    /**
     * 按 ID 列表获取 Agents
     *
     * @param agentIds Agent ID 列表
     * @return Agent 列表
     */
    fun getAgents(agentIds: List<String>): List<Agent> {
        return agentIds.mapNotNull { agents[it] }
    }

    /**
     * 获取所有 Agents
     *
     * @return 所有 Agents
     */
    fun getAllAgents(): List<Agent> {
        return agents.values.toList()
    }

    /**
     * 按能力获取 Agents
     *
     * @param capability 能力
     * @return Agents
     */
    fun getAgentsByCapability(capability: AgentCapability): List<Agent> {
        val agentIds = agentsByCapability[capability] ?: return emptyList()
        return agentIds.mapNotNull { agents[it] }
    }

    /**
     * 按能力列表获取 Agents（支持任意匹配）
     *
     * @param capabilities 能力列表
     * @return 支持任意能力的 Agents
     */
    fun getAgentsByAnyCapability(capabilities: List<AgentCapability>): List<Agent> {
        return agents.values.filter { agent ->
            capabilities.any { it in agent.capabilities }
        }
    }

    /**
     * 按能力列表获取 Agents（支持全部匹配）
     *
     * @param capabilities 能力列表
     * @return 支持所有能力的 Agents
     */
    fun getAgentsByAllCapabilities(capabilities: List<AgentCapability>): List<Agent> {
        return agents.values.filter { agent ->
            capabilities.all { it in agent.capabilities }
        }
    }

    /**
     * 按作用域获取 Agents
     *
     * @param scope 作用域
     * @return Agents
     */
    fun getAgentsByScope(scope: AgentScope): List<Agent> {
        val agentIds = agentsByScope[scope] ?: return emptyList()
        return agentIds.mapNotNull { agents[it] }
    }

    /**
     * 获取启用的 Agents
     *
     * @return 启用的 Agents
     */
    fun getEnabledAgents(): List<Agent> {
        return agents.values.filter { it.enabled }
    }

    /**
     * 按模式获取 Agents
     *
     * @param mode 运行模式
     * @return Agents
     */
    fun getAgentsByMode(mode: AgentMode): List<Agent> {
        return agents.values.filter { it.mode == mode }
    }

    /**
     * 启用/禁用 Agent
     *
     * @param agentId Agent ID
     * @param enabled 是否启用
     * @return 是否成功
     */
    fun setAgentEnabled(agentId: String, enabled: Boolean): Boolean {
        val agent = agents[agentId] ?: return false
        return updateAgent(agent.copy(enabled = enabled))
    }

    /**
     * 搜索 Agents
     *
     * @param query 搜索关键词
     * @return 匹配的 Agents
     */
    fun searchAgents(query: String): List<Agent> {
        val lowerQuery = query.lowercase()
        return agents.values.filter { agent ->
            agent.name.lowercase().contains(lowerQuery) ||
            agent.description.lowercase().contains(lowerQuery) ||
            agent.capabilities.any { it.name.lowercase().contains(lowerQuery) }
        }
    }

    /**
     * 查找最适合的 Agent（基于能力匹配）
     *
     * @param requiredCapabilities 需要的能力
     * @return 最佳匹配的 Agent
     */
    fun findBestAgent(requiredCapabilities: List<AgentCapability>): Agent? {
        val candidates = getAgentsByAllCapabilities(requiredCapabilities).filter { it.enabled }
        return candidates.maxByOrNull { it.capabilities.size }
    }

    /**
     * 导出 Agent
     *
     * @param agentId Agent ID
     * @return JSON 字符串
     */
    fun exportAgent(agentId: String): String? {
        val agent = agents[agentId] ?: return null
        return JsonUtils.gson.toJson(agent.toJson())
    }

    /**
     * 导入 Agent
     *
     * @param json JSON 字符串
     * @return 是否成功
     */
    fun importAgent(json: String): Boolean {
        return try {
            val jsonObject = JsonUtils.gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val agent = Agent.fromJson(jsonObject) ?: return false
            addAgent(agent)
        } catch (e: Exception) {
            log.error("Failed to import agent", e)
            false
        }
    }

    /**
     * 批量导入 Agents
     *
     * @param jsons JSON 字符串列表
     * @return 成功导入数量
     */
    fun importAgents(jsons: List<String>): Int {
        var count = 0
        jsons.forEach { json ->
            if (importAgent(json)) count++
        }
        return count
    }

    /**
     * 标记 Agent 任务开始
     *
     * @param agentId Agent ID
     * @param taskId 任务 ID
     */
    fun markTaskStarted(agentId: String, taskId: String) {
        val currentTasks = _activeTasks.value.toMutableMap()
        currentTasks[agentId] = taskId
        _activeTasks.value = currentTasks

        log.info("Agent task started: $agentId - $taskId")
        EventBus.publish(AgentStartedEvent(agentId, taskId))
    }

    /**
     * 标记 Agent 任务完成
     *
     * @param agentId Agent ID
     * @param success 是否成功
     */
    fun markTaskCompleted(agentId: String, success: Boolean = true) {
        val currentTasks = _activeTasks.value.toMutableMap()
        currentTasks.remove(agentId)
        _activeTasks.value = currentTasks

        log.info("Agent task completed: $agentId - success=$success")
        EventBus.publish(AgentCompletedEvent(agentId, "", success))
    }

    // ==================== 内部方法 ====================

    /**
     * 更新所有 Agents 列表
     */
    private fun updateAllAgents() {
        _allAgents.value = agents.values.toList()
    }

    override fun dispose() {
        scope.cancel()
        agents.clear()
        agentsByCapability.clear()
        agentsByScope.clear()
    }

    companion object {
        fun getInstance(project: Project): AgentsManager =
            project.getService(AgentsManager::class.java)
    }
}

/**
 * Agent 事件
 */
sealed class AgentEvent {
    data class Added(val agent: Agent) : AgentEvent()
    data class Updated(val agent: Agent) : AgentEvent()
    data class Deleted(val agentId: String) : AgentEvent()
    data class Started(val agentId: String, val taskId: String) : AgentEvent()
    data class Completed(val agentId: String, val result: com.github.xingzhewa.ccgui.model.agent.AgentResult) : AgentEvent()
}
