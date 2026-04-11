package com.github.xingzhewa.ccgui.application.tool.registry

import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 工具注册表
 *
 * 统一管理工具的注册、发现和执行
 */
class ToolRegistry(private val project: Project) {

    private val log = logger<ToolRegistry>()

    /** 工具定义映射 */
    private val tools = ConcurrentHashMap<String, ToolDefinition>()

    /** 按分类分组 */
    private val toolsByCategory = ConcurrentHashMap<ToolCategory, MutableList<String>>()

    /** 工具执行器映射 */
    private val executors = ConcurrentHashMap<String, ToolExecutor<*>>()

    /** 工具状态 */
    private val _toolStates = MutableStateFlow<Map<String, ToolState>>(emptyMap())
    val toolStates: StateFlow<Map<String, ToolState>> = _toolStates.asStateFlow()

    init {
        log.info("ToolRegistry initialized")
    }

    /**
     * 工具定义
     */
    data class ToolDefinition(
        val name: String,
        val displayName: String,
        val description: String,
        val category: ToolCategory,
        val inputSchema: Map<String, Any>,
        val outputSchema: Map<String, Any>? = null,
        val permissions: List<String> = emptyList(),
        val enabled: Boolean = true
    )

    /**
     * 工具分类
     */
    enum class ToolCategory {
        FILE, SEARCH, TERMINAL, WEB, GIT, EDITOR, CUSTOM
    }

    /**
     * 工具状态
     */
    enum class ToolState {
        IDLE, RUNNING, SUCCESS, FAILED, DISABLED
    }

    /**
     * 工具执行器接口
     */
    interface ToolExecutor<T> {
        val toolName: String
        suspend fun execute(input: T): ToolResult
        fun validateInput(input: Any): Boolean
    }

    /**
     * 工具执行结果
     */
    data class ToolResult(
        val success: Boolean,
        val output: Any? = null,
        val error: String? = null,
        val metadata: Map<String, Any> = emptyMap()
    )

    /**
     * 工具调用请求
     */
    data class ToolCall(
        val toolName: String,
        val input: Any,
        val callId: String? = null
    )

    /**
     * 工具调用事件
     */
    data class ToolCallEvent(
        val call: ToolCall,
        val status: ToolState,
        val result: ToolResult? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * 注册工具
     */
    fun register(definition: ToolDefinition, executor: ToolExecutor<*>) {
        tools[definition.name] = definition
        executors[definition.name] = executor

        toolsByCategory.getOrPut(definition.category) { mutableListOf() }
            .add(definition.name)

        _toolStates.value = _toolStates.value.toMutableMap().apply {
            put(definition.name, ToolState.IDLE)
        }

        log.debug("Registered tool: ${definition.name} in category: ${definition.category}")
    }

    /**
     * 批量注册工具
     */
    fun registerAll(definitions: List<Pair<ToolDefinition, ToolExecutor<*>>>) {
        definitions.forEach { (def, exec) -> register(def, exec) }
    }

    /**
     * 获取工具定义
     */
    fun getTool(name: String): ToolDefinition? = tools[name]

    /**
     * 获取所有工具
     */
    fun getAllTools(): List<ToolDefinition> = tools.values.toList()

    /**
     * 按分类获取工具
     */
    fun getToolsByCategory(category: ToolCategory): List<ToolDefinition> {
        return toolsByCategory[category]?.mapNotNull { tools[it] } ?: emptyList()
    }

    /**
     * 获取启用的工具
     */
    fun getEnabledTools(): List<ToolDefinition> {
        return tools.values.filter { it.enabled }
    }

    /**
     * 查找工具（按名称或描述搜索）
     */
    fun findTools(query: String): List<ToolDefinition> {
        val lowerQuery = query.lowercase()
        return tools.values.filter { tool ->
            tool.name.contains(lowerQuery, ignoreCase = true) ||
            tool.displayName.contains(lowerQuery, ignoreCase = true) ||
            tool.description.contains(lowerQuery, ignoreCase = true)
        }
    }

    /**
     * 执行工具
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun executeTool(call: ToolCall): ToolResult {
        val definition = tools[call.toolName]
        if (definition == null) {
            return ToolResult(
                success = false,
                error = "Tool not found: ${call.toolName}"
            )
        }

        if (!definition.enabled) {
            return ToolResult(
                success = false,
                error = "Tool is disabled: ${call.toolName}"
            )
        }

        val executor = executors[call.toolName]
        if (executor == null) {
            return ToolResult(
                success = false,
                error = "No executor for tool: ${call.toolName}"
            )
        }

        // 验证输入
        if (!executor.validateInput(call.input)) {
            return ToolResult(
                success = false,
                error = "Invalid input for tool: ${call.toolName}"
            )
        }

        // 更新状态
        updateToolState(call.toolName, ToolState.RUNNING)
        log.debug("Executing tool: ${call.toolName}")

        return try {
            @Suppress("UNCHECKED_CAST")
            val result = (executor as ToolExecutor<Any>).execute(call.input)
            updateToolState(
                call.toolName,
                if (result.success) ToolState.SUCCESS else ToolState.FAILED
            )
            result
        } catch (e: Exception) {
            log.error("Tool execution failed: ${call.toolName}", e)
            updateToolState(call.toolName, ToolState.FAILED)
            ToolResult(
                success = false,
                error = e.message ?: "Unknown error",
                metadata = mapOf("exception" to e.javaClass.name)
            )
        }
    }

    /**
     * 执行多个工具调用
     */
    suspend fun executeTools(calls: List<ToolCall>): List<ToolResult> {
        return calls.map { executeTool(it) }
    }

    /**
     * 更新工具状态
     */
    private fun updateToolState(toolName: String, state: ToolState) {
        _toolStates.value = _toolStates.value.toMutableMap().apply {
            put(toolName, state)
        }
    }

    /**
     * 启用/禁用工具
     */
    fun setToolEnabled(name: String, enabled: Boolean) {
        tools[name]?.let { tool ->
            tools[name] = tool.copy(enabled = enabled)
            updateToolState(name, if (enabled) ToolState.IDLE else ToolState.DISABLED)
        }
    }

    /**
     * 移除工具
     */
    fun unregister(name: String) {
        tools.remove(name)
        executors.remove(name)
        toolsByCategory.values.forEach { it.remove(name) }
        _toolStates.value = _toolStates.value.toMutableMap().apply {
            remove(name)
        }
    }

    /**
     * 清空所有工具
     */
    fun clear() {
        tools.clear()
        executors.clear()
        toolsByCategory.clear()
        _toolStates.value = emptyMap()
    }

    /**
     * 获取工具统计信息
     */
    fun getStats(): ToolStats {
        val allTools = tools.values
        return ToolStats(
            total = allTools.size,
            enabled = allTools.count { it.enabled },
            byCategory = ToolCategory.entries.associateWith { category ->
                toolsByCategory[category]?.size ?: 0
            },
            byState = _toolStates.value.values.groupingBy { it }.eachCount()
        )
    }

    data class ToolStats(
        val total: Int,
        val enabled: Int,
        val byCategory: Map<ToolCategory, Int>,
        val byState: Map<ToolState, Int>
    )

    companion object {
        fun getInstance(project: Project): ToolRegistry =
            project.getService(ToolRegistry::class.java)
    }
}
