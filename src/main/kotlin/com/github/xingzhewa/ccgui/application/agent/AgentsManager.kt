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
import java.io.File
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
        // Agents should be loaded from persistent storage
        // No hardcoded builtin agents - all agents are user-defined
        loadAgentsFromFileSystem()
        loadAgentsFromStorage()
        log.info("AgentsManager initialized for project: ${project.name}")
    }

    /**
     * 从存储加载 Agents
     *
     * 使用 IntelliJ PropertiesComponent 持久化用户自定义 Agents。
     */
    private fun loadAgentsFromStorage() {
        log.debug("Loading agents from storage...")
        try {
            val properties = com.intellij.openapi.project.ProjectManager.getInstance().defaultProject
                .getService(com.intellij.ide.util.PropertiesComponent::class.java)
            val storedJson = properties.getValue("ccgui.custom.agents") ?: return

            val array = JsonUtils.parseArray(storedJson) ?: return
            var loadedCount = 0
            array.forEach { element ->
                try {
                    val agent = Agent.fromJson(element.asJsonObject) ?: return@forEach
                    if (!agents.containsKey(agent.id)) {
                        addAgent(agent)
                        loadedCount++
                    }
                } catch (e: Exception) {
                    log.warn("Failed to load agent from storage: ${e.message}")
                }
            }
            log.info("Loaded $loadedCount agents from storage")
        } catch (e: Exception) {
            log.error("Failed to load agents from storage", e)
        }
    }

    /**
     * 从文件系统加载 Agents
     *
     * 从项目 `.claude/agents/` 目录读取 `.md/.json` 文件。
     * 与 Claude CLI 原生目录结构兼容。
     */
    private fun loadAgentsFromFileSystem() {
        log.debug("Loading agents from filesystem...")
        try {
            val basePath = project.basePath ?: run {
                log.warn("Project base path is null, skipping filesystem loading")
                return
            }

            val agentsDir = File(basePath, ".claude/agents")
            if (!agentsDir.exists() || !agentsDir.isDirectory) {
                log.debug("No .claude/agents directory found at: ${agentsDir.absolutePath}")
                return
            }

            val files = agentsDir.listFiles { file ->
                file.isFile && (file.extension == "json" || file.extension == "md")
            } ?: return

            var loadedCount = 0
            files.forEach { file ->
                try {
                    val agent = parseAgentFromFile(file)
                    if (agent != null && !agents.containsKey(agent.id)) {
                        addAgent(agent)
                        loadedCount++
                    }
                } catch (e: Exception) {
                    log.warn("Failed to load agent from file ${file.name}: ${e.message}")
                }
            }
            log.info("Loaded $loadedCount agents from filesystem")
        } catch (e: Exception) {
            log.error("Failed to load agents from filesystem", e)
        }
    }

    /**
     * 从文件解析 Agent
     *
     * 支持 .json 和 .md 文件格式。
     * .md 文件使用 front-matter 格式或纯文本描述。
     *
     * @param file 文件
     * @return Agent 或 null
     */
    private fun parseAgentFromFile(file: File): Agent? {
        return when (file.extension) {
            "json" -> parseAgentFromJson(file)
            "md" -> parseAgentFromMarkdown(file)
            else -> null
        }
    }

    /**
     * 从 JSON 文件解析 Agent
     */
    private fun parseAgentFromJson(file: File): Agent? {
        val content = file.readText()
        val json = JsonUtils.gson.fromJson(content, com.google.gson.JsonObject::class.java)
        return Agent.fromJson(json)
    }

    /**
     * 从 Markdown 文件解析 Agent
     *
     * 支持 front-matter 格式:
     * ---
     * name: architect
     * description: System architecture design specialist
     * tools: [file_read, file_write]
     * ---
     * System prompt content here...
     */
    private fun parseAgentFromMarkdown(file: File): Agent? {
        val content = file.readText()

        // Check for front-matter format
        if (!content.trimStart().startsWith("---")) {
            // Plain markdown without front-matter, use filename as name
            return Agent(
                name = file.nameWithoutExtension,
                description = content.take(200),
                systemPrompt = content
            )
        }

        val parts = content.split("---", limit = 3)
        if (parts.size < 3) {
            return null
        }

        val frontMatter = parts[1]
        val body = parts[2].trim()

        val name = extractFrontMatterField(frontMatter, "name") ?: file.nameWithoutExtension
        val description = extractFrontMatterField(frontMatter, "description") ?: ""
        val tools = extractFrontMatterField(frontMatter, "tools")?.let { toolsStr ->
            toolsStr.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"", "'") }
                .filter { it.isNotEmpty() }
        } ?: emptyList()

        return Agent(
            name = name,
            description = description,
            systemPrompt = body,
            tools = tools
        )
    }

    /**
     * 提取 front-matter 字段
     */
    private fun extractFrontMatterField(frontMatter: String, field: String): String? {
        val pattern = Regex("$field:\\s*(.+?)(?:\\n|$)", RegexOption.IGNORE_CASE)
        return pattern.find(frontMatter)?.groupValues?.get(1)?.trim()
    }

    /**
     * 持久化所有 Agents 到存储
     */
    private fun persistAgents() {
        try {
            val jsonArray = com.google.gson.JsonArray()
            agents.values.forEach { agent ->
                jsonArray.add(agent.toJson())
            }
            val properties = com.intellij.openapi.project.ProjectManager.getInstance().defaultProject
                .getService(com.intellij.ide.util.PropertiesComponent::class.java)
            properties.setValue("ccgui.custom.agents", jsonArray.toString())
            log.debug("Persisted ${agents.size} agents")
        } catch (e: Exception) {
            log.error("Failed to persist agents", e)
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
        persistAgents()

        log.info("Agent added: ${agent.id} - ${agent.name}")

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

        persistAgents()

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

        persistAgents()

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
