package com.github.claudecode.ccgui.application.skill

import com.github.claudecode.ccgui.application.orchestrator.ChatOrchestrator
import com.github.claudecode.ccgui.infrastructure.eventbus.EventBus
import com.github.claudecode.ccgui.infrastructure.eventbus.SkillExecutedEvent
import com.github.claudecode.ccgui.model.message.ChatMessage
import com.github.claudecode.ccgui.model.message.ContentPart
import com.github.claudecode.ccgui.model.message.MessageRole
import com.github.claudecode.ccgui.model.skill.ExecutionContext
import com.github.claudecode.ccgui.model.skill.Skill
import com.github.claudecode.ccgui.model.skill.SkillResult
import com.github.claudecode.ccgui.model.skill.SkillVariable
import com.github.claudecode.ccgui.model.skill.VariableType
import com.github.claudecode.ccgui.model.session.ChatSession
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * Skill 执行器
 *
 * 负责:
 * - 执行 Skill 并返回结果
 * - 处理 Skill 变量替换
 * - 管理 Skill 执行超时
 * - 收集 Skill 执行统计
 *
 * @param project IntelliJ项目实例
 */
@Service(Service.Level.PROJECT)
class SkillExecutor(private val project: Project) : Disposable {

    private val log = logger<SkillExecutor>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 执行统计 */
    private val executionStats = ConcurrentHashMap<String, ExecutionStats>()

    /** 当前活跃执行 */
    private val activeExecutions = ConcurrentHashMap<String, String>() // executionId -> skillId

    /**
     * 执行统计
     */
    data class ExecutionStats(
        var totalCount: Int = 0,
        var successCount: Int = 0,
        var failureCount: Int = 0,
        var totalTime: Long = 0,
        var averageTime: Long = 0
    ) {
        @Synchronized
        fun recordSuccess(time: Long) {
            totalCount++
            successCount++
            totalTime += time
            averageTime = totalTime / totalCount
        }

        @Synchronized
        fun recordFailure(time: Long) {
            totalCount++
            failureCount++
            totalTime += time
            averageTime = totalTime / totalCount
        }
    }

    // ==================== 核心 API ====================

    /**
     * 执行 Skill
     *
     * @param skill Skill
     * @param context 执行上下文
     * @param timeout 超时时间（毫秒）
     * @return 执行结果
     */
    suspend fun executeSkill(
        skill: Skill,
        context: ExecutionContext = ExecutionContext(),
        timeout: Long = 60000L
    ): SkillResult = withContext(Dispatchers.Default) {
        val executionId = "${skill.id}_${System.currentTimeMillis()}"
        val startTime = System.currentTimeMillis()

        try {
            // 1. 记录活跃执行
            activeExecutions[executionId] = skill.id

            // 3. 构建 prompt（替换变量）
            val prompt = buildPrompt(skill, context)

            // 4. 创建消息
            val message = ChatMessage(
                role = MessageRole.USER,
                content = prompt,
                attachments = context.attachments.filterIsInstance<ContentPart>()
            )

            // 5. 获取 ChatOrchestrator
            val orchestrator = ChatOrchestrator.getInstance(project)

            // 6. 执行（带超时）
            val result = withTimeout(timeout) {
                orchestrator.sendMessage(prompt)
            }

            val executionTime = System.currentTimeMillis() - startTime

            // 7. 记录成功
            recordExecution(skill.id, result.isSuccess, executionTime)

            // 8. 发布事件
            EventBus.publish(SkillExecutedEvent(skill.id, result.isSuccess, executionTime), project)

            val responseText = if (result.isSuccess) "Skill '${skill.name}' executed successfully"
                               else result.exceptionOrNull()?.message ?: "Unknown error"

            SkillResult(
                skillId = skill.id,
                response = responseText,
                executionTime = executionTime,
                success = result.isSuccess
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime

            // 记录失败
            recordExecution(skill.id, false, executionTime)

            // 发布事件
            EventBus.publish(SkillExecutedEvent(skill.id, false, executionTime), project)

            log.error("Skill execution failed: ${skill.id}", e)
            SkillResult(
                skillId = skill.id,
                executionTime = executionTime,
                success = false,
                error = e.message
            )
        } finally {
            // 清理活跃执行
            activeExecutions.remove(executionId)
        }
    }

    /**
     * 批量执行 Skills
     *
     * @param skills Skills 列表
     * @param context 执行上下文
     * @return 执行结果列表
     */
    suspend fun executeSkills(
        skills: List<Skill>,
        context: ExecutionContext = ExecutionContext()
    ): List<SkillResult> = withContext(Dispatchers.Default) {
        skills.map { skill ->
            executeSkill(skill, context)
        }
    }

    /**
     * 使用 Skill ID 执行
     *
     * @param skillId Skill ID
     * @param context 执行上下文
     * @return 执行结果
     */
    suspend fun executeSkillById(
        skillId: String,
        context: ExecutionContext = ExecutionContext()
    ): SkillResult? {
        val skillsManager = SkillsManager.getInstance(project)
        val skill = skillsManager.getSkill(skillId) ?: return null
        return executeSkill(skill, context)
    }

    /**
     * 验证 Skill 变量
     *
     * @param skill Skill
     * @param context 执行上下文
     * @return 验证结果
     */
    fun validateVariables(skill: Skill, context: ExecutionContext): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        skill.variables.forEach { variable ->
            val value = context.variables[variable.name]

            // 检查必填变量
            if (variable.required && value == null) {
                errors.add("Required variable '${variable.name}' is missing")
            }

            // 检查变量类型
            if (value != null && !isTypeValid(variable, value)) {
                errors.add("Variable '${variable.name}' has invalid type: expected ${variable.type}, got ${value::class.simpleName}")
            }

            // 检查枚举选项
            if (variable.type == VariableType.ENUM && value != null) {
                if (value !in variable.options) {
                    warnings.add("Variable '${variable.name}' value '$value' is not in predefined options")
                }
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * 获取执行统计
     *
     * @param skillId Skill ID
     * @return 执行统计
     */
    fun getExecutionStats(skillId: String): ExecutionStats? {
        return executionStats[skillId]
    }

    /**
     * 获取所有执行统计
     *
     * @return 执行统计 Map
     */
    fun getAllExecutionStats(): Map<String, ExecutionStats> {
        return executionStats.toMap()
    }

    /**
     * 重置执行统计
     *
     * @param skillId Skill ID（可选，不传则重置所有）
     */
    fun resetExecutionStats(skillId: String? = null) {
        if (skillId != null) {
            executionStats.remove(skillId)
        } else {
            executionStats.clear()
        }
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
     * 构建 prompt（替换变量）
     */
    private fun buildPrompt(skill: Skill, context: ExecutionContext): String {
        var prompt = skill.prompt

        // 替换变量
        skill.variables.forEach { variable ->
            val placeholder = "{${variable.name}}"
            val value = context.variables[variable.name]

            val replacement = when {
                value != null -> formatValue(variable, value)
                variable.defaultValue != null -> formatValue(variable, variable.defaultValue!!)
                variable.placeholder != null -> variable.placeholder!!
                else -> ""
            }

            prompt = prompt.replace(placeholder, replacement)
        }

        return prompt
    }

    /**
     * 格式化变量值
     */
    private fun formatValue(variable: SkillVariable, value: Any): String {
        return when (variable.type) {
            VariableType.CODE -> "```\n$value\n```"
            VariableType.ENUM -> value.toString()
            VariableType.BOOLEAN -> value.toString()
            VariableType.NUMBER -> value.toString()
            VariableType.TEXT -> value.toString()
        }
    }

    /**
     * 验证变量类型
     */
    private fun isTypeValid(variable: SkillVariable, value: Any): Boolean {
        return when (variable.type) {
            VariableType.TEXT -> value is String
            VariableType.NUMBER -> value is Number
            VariableType.BOOLEAN -> value is Boolean
            VariableType.ENUM -> value is String
            VariableType.CODE -> value is String
        }
    }

    /**
     * 记录执行
     */
    private fun recordExecution(skillId: String, success: Boolean, time: Long) {
        val stats = executionStats.getOrPut(skillId) { ExecutionStats() }
        if (success) {
            stats.recordSuccess(time)
        } else {
            stats.recordFailure(time)
        }
    }

    /**
     * 验证结果
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    )

    override fun dispose() {
        scope.cancel()
        activeExecutions.clear()
        log.info("SkillExecutor disposed")
    }

    companion object {
        fun getInstance(project: Project): SkillExecutor =
            project.getService(SkillExecutor::class.java)
    }
}
