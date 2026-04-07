# Phase 5: 生态集成 (Ecosystem Integration)

**优先级**: P1
**预估工期**: 10人天
**前置依赖**: Phase 3 (核心服务层)
**阶段目标**: 实现Claude Code生态系统的可视化管理——Skills/Agents/MCP + 作用域配置

---

> **⚠️ SDK集成架构修订 (2026-04-08)**
>
> ### ★ McpServerManager 必须重构
>
> 原设计中 `McpServerManager` 直接用 `ProcessBuilder` 管理 MCP 服务器进程。**这与SDK的 `--mcp-config` 机制矛盾**——Claude CLI 自己管理 MCP 服务器生命周期。
>
> **重构方向**：从"进程管理器"变为"配置管理器"
>
> | 原设计（删除） | 新设计 |
> |---------------|--------|
> | `processes: ConcurrentHashMap<String, Process>` | 删除，CLI管理进程 |
> | `startServer()` 启动 `ProcessBuilder` | 删除 |
> | `stopServer()` 销毁进程 | 删除 |
> | `testConnection()` spawn测试进程 | 生成配置JSON给CLI验证 |
> | 直接管理进程生命周期 | 生成 `McpServersConfig` → `SdkConfigBuilder.writeMcpConfigTempFile()` |
>
> **新增方法**：
> ```kotlin
> /** 生成SDK可用的MCP配置 */
> fun buildMcpConfig(): McpServersConfig {
>     return McpServersConfig(
>         servers = servers.values
>             .filter { it.enabled }
>             .associate { it.id to McpServersConfig.McpServerEntry(it.command, it.args, it.env) }
>     )
> }
> ```
>
> ### ★ SkillExecutor / AgentExecutor 签名适配
>
> `ChatOrchestrator.sendMessage()` 在SDK架构下返回 `Result<SdkResultMessage>` 而非 `Result<ChatResponse>`。
> SkillExecutor 和 AgentExecutor 需适配：
> - `chatResponse.tokensUsed.totalTokens` → `sdkResult.costUsd`
> - `chatResponse.executionTimeMs` → `sdkResult.durationMs`
> - `chatResponse.content` → `sdkResult.result`
>
> ### 依赖更新
>
> ```
> T5.1 SkillsManager ← Phase 1 模型（不变）
> T5.1 SkillExecutor ← SkillsManager + ChatOrchestrator(Phase3) + ClaudeCodeClient(Phase2.5) ★
> T5.2 AgentsManager ← Phase 1 模型（不变）
> T5.2 AgentExecutor ← AgentsManager + ChatOrchestrator(Phase3) + ClaudeCodeClient(Phase2.5) ★
> T5.3 McpServerManager ← SdkConfigBuilder(Phase2.5) ★（不再是独立进程管理）
> T5.4 ScopeManager ← SkillsManager + McpServerManager + SdkOptions(Phase2.5) ★
> ```

---

## 1. 阶段概览

本阶段实现PRD第3.5节定义的**Claude Code生态集成**：

1. **SkillsManager + SkillExecutor**: 技能模板管理和执行
2. **AgentsManager + AgentExecutor**: AI代理配置和行为执行
3. **McpServerManager**: MCP服务器连接管理
4. **ScopeManager**: 全局/项目/会话三级作用域配置合并

---

## 2. 任务清单

### T5.1 Skills管理 (3人天)

#### T5.1.1 `application/skill/SkillsManager.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.skill

import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.ConfigChangedEvent
import com.github.xingzhewa.ccgui.infrastructure.storage.ConfigStorage
import com.github.xingzhewa.ccgui.model.skill.Skill
import com.github.xingzhewa.ccgui.model.skill.SkillCategory
import com.github.xingzhewa.ccgui.model.skill.SkillScope
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Skills技能管理器
 * 管理Prompt技能模板的CRUD、导入导出、分类过滤
 *
 * 扩展埋点:
 *   - 后续可添加技能市场（在线下载）
 *   - 后续可添加技能版本管理
 */
@Service(Service.Level.PROJECT)
class SkillsManager(private val project: Project) {

    private val log = logger<SkillsManager>()
    private val skills = ConcurrentHashMap<String, Skill>()

    private val _skillsList = MutableStateFlow<List<Skill>>(emptyList())
    val skillsList: StateFlow<List<Skill>> = _skillsList.asStateFlow()

    init {
        loadDefaultSkills()
        loadCustomSkills()
    }

    /**
     * 获取所有技能
     */
    fun getAllSkills(): List<Skill> = skills.values.toList()

    /**
     * 获取已启用的技能
     */
    fun getEnabledSkills(): List<Skill> = skills.values.filter { it.enabled }

    /**
     * 按分类获取技能
     */
    fun getSkillsByCategory(category: SkillCategory): List<Skill> =
        skills.values.filter { it.category == category }

    /**
     * 获取单个技能
     */
    fun getSkill(id: String): Skill? = skills[id]

    /**
     * 保存/更新技能
     */
    fun saveSkill(skill: Skill) {
        skills[skill.id] = skill
        updateSkillsList()
        persistSkill(skill)

        EventBus.publish(ConfigChangedEvent("skills", skills.values.toList()))
        log.info("Skill saved: ${skill.id} (${skill.name})")
    }

    /**
     * 删除技能
     */
    fun deleteSkill(id: String) {
        skills.remove(id)
        updateSkillsList()
        log.info("Skill deleted: $id")
    }

    /**
     * 启用/禁用技能
     */
    fun toggleSkill(id: String, enabled: Boolean) {
        skills[id]?.let {
            skills[id] = it.copy(enabled = enabled)
            updateSkillsList()
        }
    }

    /**
     * 导出技能为JSON
     */
    fun exportSkill(id: String): String? {
        val skill = skills[id] ?: return null
        return JsonUtils.toJson(skill)
    }

    /**
     * 导入技能
     */
    fun importSkill(json: String): Result<Skill> {
        return try {
            val skill = JsonUtils.fromJson(json, Skill::class.java)
            saveSkill(skill)
            Result.success(skill)
        } catch (e: Exception) {
            log.error("Failed to import skill", e)
            Result.failure(e)
        }
    }

    /**
     * 加载默认技能
     */
    private fun loadDefaultSkills() {
        val defaults = listOf(
            Skill(
                id = "explain-code",
                name = "Explain Code",
                description = "Explain the selected code's functionality, logic, and potential issues",
                icon = "📖",
                category = SkillCategory.CODE_REVIEW,
                prompt = "Please explain the following code in detail, including its functionality, key logic, and potential issues:\n\n{{code}}",
                variables = listOf(com.github.xingzhewa.ccgui.model.skill.SkillVariable(name = "code", required = true)),
                shortcut = "ctrl shift E",
                scope = SkillScope.GLOBAL
            ),
            Skill(
                id = "generate-tests",
                name = "Generate Tests",
                description = "Generate comprehensive unit tests for the selected code",
                icon = "🧪",
                category = SkillCategory.TESTING,
                prompt = "Generate comprehensive unit tests for the following code. Include edge cases and error scenarios:\n\n{{code}}",
                variables = listOf(com.github.xingzhewa.ccgui.model.skill.SkillVariable(name = "code", required = true)),
                shortcut = "ctrl shift T",
                scope = SkillScope.GLOBAL
            ),
            Skill(
                id = "optimize-code",
                name = "Optimize Code",
                description = "Optimize the selected code for performance and readability",
                icon = "⚡",
                category = SkillCategory.PERFORMANCE,
                prompt = "Optimize the following code for performance and readability. Explain the changes:\n\n{{code}}",
                variables = listOf(com.github.xingzhewa.ccgui.model.skill.SkillVariable(name = "code", required = true)),
                shortcut = "ctrl shift O",
                scope = SkillScope.GLOBAL
            ),
            Skill(
                id = "find-bugs",
                name = "Find Bugs",
                description = "Analyze the selected code for potential bugs and security issues",
                icon = "🐛",
                category = SkillCategory.DEBUGGING,
                prompt = "Analyze the following code for potential bugs, security issues, and edge cases:\n\n{{code}}",
                variables = listOf(com.github.xingzhewa.ccgui.model.skill.SkillVariable(name = "code", required = true)),
                shortcut = "ctrl shift B",
                scope = SkillScope.GLOBAL
            ),
            Skill(
                id = "add-comments",
                name = "Add Comments",
                description = "Add clear and helpful comments to the selected code",
                icon = "💬",
                category = SkillCategory.DOCUMENTATION,
                prompt = "Add clear and helpful comments to the following code:\n\n{{code}}",
                variables = listOf(com.github.xingzhewa.ccgui.model.skill.SkillVariable(name = "code", required = true)),
                shortcut = "ctrl shift /",
                scope = SkillScope.GLOBAL
            ),
            Skill(
                id = "refactor-code",
                name = "Refactor Code",
                description = "Refactor the selected code to improve structure and maintainability",
                icon = "🔧",
                category = SkillCategory.REFACTORING,
                prompt = "Refactor the following code to improve its structure, maintainability, and adherence to best practices:\n\n{{code}}",
                variables = listOf(com.github.xingzhewa.ccgui.model.skill.SkillVariable(name = "code", required = true)),
                shortcut = "ctrl shift R",
                scope = SkillScope.GLOBAL
            )
        )

        defaults.forEach { skills[it.id] = it }
        updateSkillsList()
    }

    private fun loadCustomSkills() {
        // TODO: 从项目目录加载自定义Skills
    }

    private fun persistSkill(skill: Skill) {
        // TODO: 持久化到项目配置
    }

    private fun updateSkillsList() {
        _skillsList.value = skills.values.sortedBy { it.name }
    }

    companion object {
        fun getInstance(project: Project): SkillsManager =
            project.getService(SkillsManager::class.java)
    }
}
```

---

#### T5.1.2 `application/skill/SkillExecutor.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.skill

import com.github.xingzhewa.ccgui.application.orchestrator.ChatOrchestrator
import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.message.MessageRole
import com.github.xingzhewa.ccgui.model.provider.ChatResponse
import com.github.xingzhewa.ccgui.model.skill.Skill
import com.github.xingzhewa.ccgui.model.skill.SkillVariable
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.project.Project

/**
 * 技能执行器
 * 解析变量、构建消息、调用ChatOrchestrator执行
 */
class SkillExecutor(private val project: Project) {

    private val log = logger<SkillExecutor>()

    data class SkillResult(
        val skillId: String,
        val response: String,
        val tokensUsed: Int = 0,
        val executionTimeMs: Long = 0
    )

    /**
     * 执行技能
     */
    suspend fun executeSkill(
        skill: Skill,
        variables: Map<String, Any>
    ): Result<SkillResult> {
        return try {
            // 1. 解析变量
            val resolvedPrompt = resolveVariables(skill.prompt, skill.variables, variables)

            // 2. 构建消息
            val message = ChatMessage(
                role = MessageRole.USER,
                content = resolvedPrompt
            )

            // 3. 通过ChatOrchestrator发送
            val orchestrator = ChatOrchestrator.getInstance(project)
            val response = orchestrator.sendMessage(message)

            response.map { chatResponse ->
                SkillResult(
                    skillId = skill.id,
                    response = chatResponse.content,
                    tokensUsed = chatResponse.tokensUsed.totalTokens,
                    executionTimeMs = chatResponse.executionTimeMs
                )
            }
        } catch (e: Exception) {
            log.error("Skill execution failed: ${skill.id}", e)
            Result.failure(e)
        }
    }

    /**
     * 解析模板变量
     * 将 {{varName}} 替换为实际值
     */
    private fun resolveVariables(
        prompt: String,
        variableDefs: List<SkillVariable>,
        values: Map<String, Any>
    ): String {
        var resolved = prompt
        variableDefs.forEach { varDef ->
            val value = values[varDef.name]
                ?: varDef.defaultValue
                ?: if (varDef.required) throw IllegalArgumentException("Required variable not provided: ${varDef.name}") else ""

            resolved = resolved.replace("{{${varDef.name}}}", value.toString())
        }
        return resolved
    }
}
```

---

### T5.2 Agents管理 (3人天)

#### T5.2.1 `application/agent/AgentsManager.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.agent

import com.github.xingzhewa.ccgui.model.agent.*
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * AI代理管理器
 * 管理Agent配置的CRUD和行为模式
 *
 * 扩展埋点:
 *   - 后续可添加Agent市场
 *   - 后续可添加Agent组合编排
 */
@Service(Service.Level.PROJECT)
class AgentsManager(private val project: Project) {

    private val log = logger<AgentsManager>()
    private val agents = ConcurrentHashMap<String, Agent>()

    private val _agentsList = MutableStateFlow<List<Agent>>(emptyList())
    val agentsList: StateFlow<List<Agent>> = _agentsList.asStateFlow()

    init {
        loadDefaultAgents()
    }

    fun getAgent(id: String): Agent? = agents[id]
    fun getAllAgents(): List<Agent> = agents.values.toList()
    fun getAgentsByCapability(capability: AgentCapability): List<Agent> =
        agents.values.filter { capability in it.capabilities }

    fun saveAgent(agent: Agent) {
        agents[agent.id] = agent
        _agentsList.value = agents.values.toList()
    }

    fun deleteAgent(id: String) {
        agents.remove(id)
        _agentsList.value = agents.values.toList()
    }

    private fun loadDefaultAgents() {
        val defaults = listOf(
            Agent(
                id = "code-assistant",
                name = "Code Assistant",
                description = "General-purpose coding assistant",
                systemPrompt = "You are a helpful coding assistant integrated into JetBrains IDE. Help with coding, debugging, and refactoring.",
                capabilities = listOf(AgentCapability.CODE_GENERATION, AgentCapability.CODE_REVIEW, AgentCapability.DEBUGGING),
                mode = AgentMode.BALANCED,
                scope = AgentScope.PROJECT
            ),
            Agent(
                id = "code-reviewer",
                name = "Code Reviewer",
                description = "Focused on code review and quality analysis",
                systemPrompt = "You are a senior code reviewer. Analyze code for bugs, performance issues, security vulnerabilities, and suggest improvements.",
                capabilities = listOf(AgentCapability.CODE_REVIEW),
                constraints = listOf(AgentConstraint(ConstraintType.MAX_TOKENS, "Limit response length", mapOf("maxTokens" to 2048))),
                mode = AgentMode.CAUTIOUS,
                scope = AgentScope.PROJECT
            ),
            Agent(
                id = "test-generator",
                name = "Test Generator",
                description = "Automated test generation specialist",
                systemPrompt = "You are a test engineering expert. Generate comprehensive, well-structured unit tests with high coverage.",
                capabilities = listOf(AgentCapability.TESTING, AgentCapability.CODE_GENERATION),
                mode = AgentMode.BALANCED,
                scope = AgentScope.PROJECT
            )
        )
        defaults.forEach { agents[it.id] = it }
        _agentsList.value = agents.values.toList()
    }

    companion object {
        fun getInstance(project: Project): AgentsManager =
            project.getService(AgentsManager::class.java)
    }
}
```

---

#### T5.2.2 `application/agent/AgentExecutor.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.agent

import com.github.xingzhewa.ccgui.application.orchestrator.ChatOrchestrator
import com.github.xingzhewa.ccgui.model.agent.*
import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.message.MessageRole
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.project.Project

/**
 * 代理执行器
 * 根据Agent的行为模式（Cautious/Balanced/Aggressive）执行任务
 *
 * 行为模式说明:
 *   - Cautious: 仅提供建议，不做任何自动操作
 *   - Balanced: 低风险操作自动执行，高风险需确认
 *   - Aggressive: 自动执行所有操作
 */
class AgentExecutor(private val project: Project) {

    private val log = logger<AgentExecutor>()

    data class AgentResult(
        val agentId: String,
        val content: String,
        val mode: AgentMode,
        val actions: List<String> = emptyList()
    )

    data class AgentTask(
        val description: String,
        val requiredCapability: AgentCapability? = null,
        val context: Map<String, Any> = emptyMap()
    )

    /**
     * 执行代理任务
     */
    suspend fun executeAgent(agent: Agent, task: AgentTask): Result<AgentResult> {
        // 1. 检查能力
        if (task.requiredCapability != null && task.requiredCapability !in agent.capabilities) {
            return Result.failure(IllegalArgumentException(
                "Agent ${agent.name} lacks capability: ${task.requiredCapability}"
            ))
        }

        // 2. 根据模式执行
        return try {
            val orchestrator = ChatOrchestrator.getInstance(project)

            // 发送系统提示 + 任务
            val messages = listOf(
                ChatMessage(role = MessageRole.SYSTEM, content = agent.systemPrompt),
                ChatMessage(role = MessageRole.USER, content = task.description)
            )

            val response = orchestrator.sendMessage(messages.last())

            response.map { chatResponse ->
                AgentResult(
                    agentId = agent.id,
                    content = chatResponse.content,
                    mode = agent.mode
                )
            }
        } catch (e: Exception) {
            log.error("Agent execution failed: ${agent.id}", e)
            Result.failure(e)
        }
    }

    companion object {
        fun getInstance(project: Project): AgentExecutor =
            AgentExecutor(project)
    }
}
```

---

### T5.3 MCP服务器管理 (2.5人天)

#### T5.3.1 `application/mcp/McpServerManager.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.mcp

import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.McpServerStatusEvent
import com.github.xingzhewa.ccgui.model.mcp.McpScope
import com.github.xingzhewa.ccgui.model.mcp.McpServer
import com.github.xingzhewa.ccgui.model.mcp.McpServerStatus
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * MCP服务器管理器
 * 管理MCP服务器进程的启动/停止/状态监控
 *
 * 扩展埋点:
 *   - 后续可添加MCP服务器自动发现
 *   - 后续可添加连接池管理
 */
@Service(Service.Level.PROJECT)
class McpServerManager(private val project: Project) {

    private val log = logger<McpServerManager>()
    private val servers = ConcurrentHashMap<String, McpServer>()
    private val processes = ConcurrentHashMap<String, Process>()

    private val _serversList = MutableStateFlow<List<McpServer>>(emptyList())
    val serversList: StateFlow<List<McpServer>> = _serversList.asStateFlow()

    fun addServer(server: McpServer) {
        servers[server.id] = server
        _serversList.value = servers.values.toList()
    }

    fun removeServer(id: String) {
        stopServer(id)
        servers.remove(id)
        _serversList.value = servers.values.toList()
    }

    fun getServer(id: String): McpServer? = servers[id]
    fun getAllServers(): List<McpServer> = servers.values.toList()
    fun getEnabledServers(): List<McpServer> = servers.values.filter { it.enabled }

    /**
     * 启动MCP服务器
     */
    fun startServer(id: String): Result<Unit> {
        val server = servers[id] ?: return Result.failure(IllegalArgumentException("Server not found: $id"))
        if (processes.containsKey(id)) return Result.success(Unit) // 已启动

        return try {
            val processBuilder = ProcessBuilder(listOf(server.command) + server.args)
            server.env.forEach { (k, v) -> processBuilder.environment()[k] = v }

            val process = processBuilder.start()
            processes[id] = process

            // 更新状态
            updateServerStatus(id, McpServerStatus.CONNECTED)

            log.info("MCP server started: ${server.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            updateServerStatus(id, McpServerStatus.ERROR)
            log.error("Failed to start MCP server: ${server.name}", e)
            Result.failure(e)
        }
    }

    /**
     * 停止MCP服务器
     */
    fun stopServer(id: String) {
        processes.remove(id)?.destroy()
        updateServerStatus(id, McpServerStatus.DISCONNECTED)
    }

    /**
     * 测试连接
     */
    fun testConnection(server: McpServer): Result<List<String>> {
        return try {
            // 启动进程并发送初始化请求
            val process = ProcessBuilder(listOf(server.command) + server.args).start()
            // 简单测试：进程是否可启动
            Thread.sleep(2000)
            val alive = process.isAlive
            process.destroy()

            if (alive) {
                Result.success(listOf("connection_ok"))
            } else {
                Result.failure(RuntimeException("Process exited immediately"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 启动所有已启用的服务器
     */
    fun startAllEnabled() {
        servers.values.filter { it.enabled }.forEach { server ->
            startServer(server.id)
        }
    }

    /**
     * 停止所有服务器
     */
    fun stopAll() {
        processes.keys.toList().forEach { stopServer(it) }
    }

    private fun updateServerStatus(id: String, status: McpServerStatus) {
        servers[id]?.let { server ->
            val updated = server.copy(status = status)
            servers[id] = updated
            _serversList.value = servers.values.toList()
            EventBus.publish(McpServerStatusEvent(id, status.name))
        }
    }

    companion object {
        fun getInstance(project: Project): McpServerManager =
            project.getService(McpServerManager::class.java)
    }
}
```

---

### T5.4 作用域管理 (1.5人天)

#### T5.4.1 `application/mcp/ScopeManager.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.mcp

import com.github.xingzhewa.ccgui.application.agent.AgentsManager
import com.github.xingzhewa.ccgui.application.skill.SkillsManager
import com.github.xingzhewa.ccgui.model.agent.AgentScope
import com.github.xingzhewa.ccgui.model.config.ModelConfig
import com.github.xingzhewa.ccgui.model.mcp.McpScope
import com.github.xingzhewa.ccgui.model.skill.Skill
import com.github.xingzhewa.ccgui.model.skill.SkillScope
import com.github.xingzhewa.ccgui.model.mcp.McpServer
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.project.Project

/**
 * 作用域管理器
 * 管理全局/项目/会话三级配置的优先级和合并策略
 *
 * 优先级: 会话 > 项目 > 全局
 *
 * 扩展埋点:
 *   - 后续可添加团队级作用域
 *   - 后续可添加配置继承可视化
 */
class ScopeManager(private val project: Project) {

    private val log = logger<ScopeManager>()

    data class EffectiveConfig(
        val skills: List<Skill>,
        val mcpServers: List<McpServer>,
        val modelConfig: ModelConfig
    )

    /**
     * 获取合并后的有效配置
     */
    fun getEffectiveConfig(sessionSkills: List<String>? = null): EffectiveConfig {
        val skillsManager = SkillsManager.getInstance(project)
        val mcpManager = McpServerManager.getInstance(project)

        // 合并Skills
        val skills = mergeSkills(
            all = skillsManager.getAllSkills(),
            sessionFilter = sessionSkills
        )

        // 合并MCP服务器
        val mcpServers = mcpManager.getEnabledServers()

        // 模型配置使用默认值
        val modelConfig = ModelConfig.default()

        return EffectiveConfig(
            skills = skills,
            mcpServers = mcpServers,
            modelConfig = modelConfig
        )
    }

    /**
     * 合并Skills
     * 会话级过滤 > 项目级覆盖 > 全局级默认
     */
    private fun mergeSkills(
        all: List<Skill>,
        projectSkills: List<Skill>? = null,
        sessionFilter: List<String>? = null
    ): List<Skill> {
        val merged = all.toMutableList()

        // 项目级别覆盖
        projectSkills?.forEach { projectSkill ->
            val index = merged.indexOfFirst { it.id == projectSkill.id }
            if (index >= 0) {
                merged[index] = projectSkill
            } else {
                merged.add(projectSkill)
            }
        }

        // 会话级别过滤
        return if (sessionFilter != null) {
            merged.filter { it.id in sessionFilter }
        } else {
            merged.filter { it.enabled }
        }
    }

    companion object {
        fun getInstance(project: Project): ScopeManager =
            ScopeManager(project)
    }
}
```

---

## 3. plugin.xml 新增注册

```xml
<!-- Phase 5 新增 -->
<projectService serviceImplementation="com.github.xingzhewa.ccgui.application.skill.SkillsManager"/>
<projectService serviceImplementation="com.github.xingzhewa.ccgui.application.agent.AgentsManager"/>
<projectService serviceImplementation="com.github.xingzhewa.ccgui.application.mcp.McpServerManager"/>
```

---

## 4. 任务依赖

```
T5.1 SkillsManager ← 独立（仅需Phase1模型）
T5.1 SkillExecutor ← 依赖 SkillsManager + ChatOrchestrator(Phase3)
  ↓
T5.2 AgentsManager ← 独立
T5.2 AgentExecutor ← 依赖 AgentsManager + ChatOrchestrator(Phase3)
  ↓
T5.3 McpServerManager ← 独立（仅需Phase1 Process管理）
  ↓
T5.4 ScopeManager ← 依赖 SkillsManager + McpServerManager
```

---

## 5. 验收标准

| 验收项 | 标准 |
|--------|------|
| Skills CRUD | 可创建/编辑/删除/导入/导出技能 |
| Skills执行 | 技能可正确解析变量并执行 |
| 内置Skills | 6个默认技能可用 |
| Agents CRUD | 可管理Agent配置 |
| Agent执行 | 3种模式可正确执行 |
| MCP管理 | 可启动/停止/测试MCP服务器 |
| 作用域 | 全局/项目/会话配置正确合并 |

---

## 6. 文件清单汇总

| 序号 | 文件路径 | 类型 |
|------|----------|------|
| 1 | `application/skill/SkillsManager.kt` | 服务 |
| 2 | `application/skill/SkillExecutor.kt` | 执行器 |
| 3 | `application/agent/AgentsManager.kt` | 服务 |
| 4 | `application/agent/AgentExecutor.kt` | 执行器 |
| 5 | `application/mcp/McpServerManager.kt` | 服务 |
| 6 | `application/mcp/ScopeManager.kt` | 管理器 |

**共计**: 6个文件
