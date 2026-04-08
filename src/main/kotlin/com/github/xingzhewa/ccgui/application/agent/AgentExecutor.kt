package com.github.xingzhewa.ccgui.application.agent

import com.github.xingzhewa.ccgui.application.interaction.InteractiveRequestEngine
import com.github.xingzhewa.ccgui.application.orchestrator.ChatOrchestrator
import com.github.xingzhewa.ccgui.application.task.TaskProgressTracker
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.model.agent.Agent
import com.github.xingzhewa.ccgui.model.agent.AgentCapability
import com.github.xingzhewa.ccgui.model.agent.AgentConstraint
import com.github.xingzhewa.ccgui.model.agent.AgentMode
import com.github.xingzhewa.ccgui.model.agent.AgentResult
import com.github.xingzhewa.ccgui.model.agent.AgentTask
import com.github.xingzhewa.ccgui.model.interaction.QuestionType
import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.message.MessageRole
import com.github.xingzhewa.ccgui.model.session.ChatSession
import com.github.xingzhewa.ccgui.model.task.TaskProgress
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * Agent 执行器
 *
 * 负责:
 * - 执行 Agent 任务
 * - 管理 Agent 执行模式（谨慎/平衡/激进）
 * - 处理 Agent 约束
 * - 收集 Agent 执行统计
 *
 * @param project IntelliJ项目实例
 */
@Service(Service.Level.PROJECT)
class AgentExecutor(private val project: Project) : Disposable {

    private val log = logger<AgentExecutor>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 执行统计 */
    private val executionStats = ConcurrentHashMap<String, AgentExecutionStats>()

    /** 当前活跃执行 */
    private val activeExecutions = ConcurrentHashMap<String, ActiveExecution>()

    /**
     * Agent 执行统计
     */
    data class AgentExecutionStats(
        var totalTasks: Int = 0,
        var completedTasks: Int = 0,
        var failedTasks: Int = 0,
        var totalSuggestions: Int = 0,
        var totalExecutedActions: Int = 0,
        var averageTime: Long = 0
    ) {
        @Synchronized
        fun recordCompletion(time: Long, executedActions: Int) {
            totalTasks++
            completedTasks++
            totalExecutedActions += executedActions
            averageTime = (averageTime * (totalTasks - 1) + time) / totalTasks
        }

        @Synchronized
        fun recordFailure(time: Long) {
            totalTasks++
            failedTasks++
            averageTime = (averageTime * (totalTasks - 1) + time) / totalTasks
        }
    }

    /**
     * 活跃执行
     */
    data class ActiveExecution(
        val agentId: String,
        val taskId: String,
        val startTime: Long
    )

    // ==================== 核心 API ====================

    /**
     * 执行 Agent 任务
     *
     * @param agent Agent
     * @param task 任务
     * @param session 会话（可选）
     * @param timeout 超时时间（毫秒）
     * @return 执行结果
     */
    suspend fun executeAgent(
        agent: Agent,
        task: AgentTask,
        session: ChatSession? = null,
        timeout: Long = 300000L  // 默认 5 分钟
    ): AgentResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val executionId = "${agent.id}_${task.id}"

        try {
            // 1. 检查 Agent 是否启用
            if (!agent.enabled) {
                return@withContext AgentResult(
                    agentId = agent.id,
                    success = false,
                    error = "Agent is disabled"
                )
            }

            // 2. 检查能力是否匹配
            if (task.requiredCapability !in agent.capabilities) {
                return@withContext AgentResult(
                    agentId = agent.id,
                    success = false,
                    error = "Agent does not have required capability: ${task.requiredCapability}"
                )
            }

            // 3. 记录活跃执行
            activeExecutions[executionId] = ActiveExecution(agent.id, task.id, startTime)

            // 4. 获取管理器
            val agentsManager = AgentsManager.getInstance(project)
            val taskTracker = TaskProgressTracker.getInstance(project)

            // 5. 创建任务追踪
            val progress = taskTracker.createTask(
                name = "Agent: ${agent.name}",
                description = task.description
            )

            agentsManager.markTaskStarted(agent.id, task.id)

            // 6. 执行任务（根据模式）
            val result = when (agent.mode) {
                AgentMode.CAUTIOUS -> executeCautiously(agent, task, session, progress, timeout)
                AgentMode.BALANCED -> executeBalanced(agent, task, session, progress, timeout)
                AgentMode.AGGRESSIVE -> executeAggressively(agent, task, session, progress, timeout)
            }

            val executionTime = System.currentTimeMillis() - startTime

            // 7. 记录完成
            taskTracker.completeTask(progress.taskId, result.success)
            agentsManager.markTaskCompleted(agent.id, result.success)

            // 8. 记录统计
            recordExecution(agent.id, result, executionTime)

            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime

            log.error("Agent execution failed: ${agent.id}", e)

            AgentResult(
                agentId = agent.id,
                success = false,
                error = e.message
            ).also {
                recordExecution(agent.id, it, executionTime)
            }
        } finally {
            activeExecutions.remove(executionId)
        }
    }

    /**
     * 使用 Agent ID 执行
     *
     * @param agentId Agent ID
     * @param task 任务
     * @param session 会话（可选）
     * @return 执行结果
     */
    suspend fun executeAgentById(
        agentId: String,
        task: AgentTask,
        session: ChatSession? = null
    ): AgentResult? {
        val agentsManager = AgentsManager.getInstance(project)
        val agent = agentsManager.getAgent(agentId) ?: return null
        return executeAgent(agent, task, session)
    }

    /**
     * 获取执行统计
     *
     * @param agentId Agent ID
     * @return 执行统计
     */
    fun getExecutionStats(agentId: String): AgentExecutionStats? {
        return executionStats[agentId]
    }

    /**
     * 获取所有执行统计
     *
     * @return 执行统计 Map
     */
    fun getAllExecutionStats(): Map<String, AgentExecutionStats> {
        return executionStats.toMap()
    }

    /**
     * 获取活跃执行数量
     *
     * @return 活跃执行数量
     */
    fun getActiveExecutionCount(): Int {
        return activeExecutions.size
    }

    // ==================== 内部方法 ====================

    /**
     * 谨慎模式执行（仅提供建议）
     */
    private suspend fun executeCautiously(
        agent: Agent,
        task: AgentTask,
        session: ChatSession?,
        progress: TaskProgress,
        timeout: Long
    ): AgentResult {
        // 构建提示词
        val prompt = buildPrompt(agent, task)

        // 创建系统消息
        val systemMessage = ChatMessage(
            role = MessageRole.SYSTEM,
            content = agent.systemPrompt
        )

        // 创建用户消息
        val userMessage = ChatMessage(
            role = MessageRole.USER,
            content = prompt
        )

        // TODO: 调用 ChatOrchestrator 获取建议
        val orchestrator = ChatOrchestrator.getInstance(project)
        val result = orchestrator.sendMessage(prompt)
        val suggestion = if (result.isSuccess) {
            "Agent '${agent.name}' analyzed: ${task.description}"
        } else {
            "Error: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
        }

        return AgentResult(
            agentId = agent.id,
            suggestion = suggestion,
            pendingActions = emptyList(),
            success = true
        )
    }

    /**
     * 平衡模式执行（建议为主，低风险自动执行）
     */
    private suspend fun executeBalanced(
        agent: Agent,
        task: AgentTask,
        session: ChatSession?,
        progress: TaskProgress,
        timeout: Long
    ): AgentResult {
        // 先获取建议
        val cautiousResult = executeCautiously(agent, task, session, progress, timeout)

        // 分析建议中的低风险操作
        val executedActions = mutableListOf<String>()

        // TODO: 自动执行低风险操作
        // 例如：只读操作、文件查看等

        return AgentResult(
            agentId = agent.id,
            suggestion = cautiousResult.suggestion,
            executedActions = executedActions,
            pendingActions = emptyList(),
            success = true
        )
    }

    /**
     * 激进模式执行（自动执行高风险操作）
     */
    private suspend fun executeAggressively(
        agent: Agent,
        task: AgentTask,
        session: ChatSession?,
        progress: TaskProgress,
        timeout: Long
    ): AgentResult {
        val executedActions = mutableListOf<String>()

        // TODO: 自动执行所有建议的操作
        // 需要用户确认或交互式请求

        return AgentResult(
            agentId = agent.id,
            executedActions = executedActions,
            success = true
        )
    }

    /**
     * 构建提示词
     */
    private fun buildPrompt(agent: Agent, task: AgentTask): String {
        val prompt = StringBuilder()

        prompt.append("Task: ${task.description}\n\n")

        // 添加上下文
        if (task.context.isNotEmpty()) {
            prompt.append("Context:\n")
            task.context.forEach { (key, value) ->
                prompt.append("- $key: $value\n")
            }
            prompt.append("\n")
        }

        // 添加约束
        if (agent.constraints.isNotEmpty()) {
            prompt.append("Constraints:\n")
            agent.constraints.forEach { constraint ->
                prompt.append("- ${constraint.description}\n")
            }
            prompt.append("\n")
        }

        // 添加可用工具
        if (agent.tools.isNotEmpty()) {
            prompt.append("Available tools: ${agent.tools.joinToString(", ")}\n\n")
        }

        return prompt.toString()
    }

    /**
     * 验证约束
     */
    private fun validateConstraints(
        agent: Agent,
        task: AgentTask
    ): Boolean {
        // TODO: 实现约束验证逻辑
        return true
    }

    /**
     * 记录执行
     */
    private fun recordExecution(agentId: String, result: AgentResult, time: Long) {
        val stats = executionStats.getOrPut(agentId) { AgentExecutionStats() }
        if (result.success) {
            stats.recordCompletion(time, result.executedActions.size)
        } else {
            stats.recordFailure(time)
        }
    }

    /**
     * 停止 Agent 执行
     *
     * @param agentId Agent ID
     * @param taskId 任务 ID
     * @return 是否成功
     */
    fun stopExecution(agentId: String, taskId: String): Boolean {
        val execution = activeExecutions.values.find {
            it.agentId == agentId && it.taskId == taskId
        } ?: return false

        // TODO: 实现停止逻辑
        activeExecutions.remove("${agentId}_${taskId}")

        log.info("Agent execution stopped: $agentId - $taskId")
        return true
    }

    /**
     * 停止所有 Agent 执行
     *
     * @param agentId Agent ID
     * @return 停止的任务数量
     */
    fun stopAllExecutions(agentId: String): Int {
        var count = 0
        activeExecutions.entries.removeIf { (id, execution) ->
            if (execution.agentId == agentId) {
                count++
                true
            } else {
                false
            }
        }

        log.info("Stopped $count agent executions for: $agentId")
        return count
    }

    override fun dispose() {
        scope.cancel()
        activeExecutions.clear()
        log.info("AgentExecutor disposed")
    }

    companion object {
        fun getInstance(project: Project): AgentExecutor =
            project.getService(AgentExecutor::class.java)
    }
}
