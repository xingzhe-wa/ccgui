package com.github.xingzhewa.ccgui.application.task

import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.TaskCompletedEvent
import com.github.xingzhewa.ccgui.infrastructure.eventbus.TaskProgressEvent
import com.github.xingzhewa.ccgui.infrastructure.eventbus.TaskStartedEvent
import com.github.xingzhewa.ccgui.model.task.TaskProgress
import com.github.xingzhewa.ccgui.model.task.TaskStep
import com.github.xingzhewa.ccgui.model.task.TaskStatus
import com.github.xingzhewa.ccgui.util.IdGenerator
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
 * 任务进度追踪器
 *
 * 负责追踪和管理长时间运行任务的进度：
 * - 创建和跟踪任务
 * - 管理任务步骤
 * - 更新任务状态
 * - 提供进度查询API
 * - 发布任务事件
 *
 * @param project IntelliJ项目实例
 */
@Service(Service.Level.PROJECT)
class TaskProgressTracker(private val project: Project) : Disposable {

    private val log = logger<TaskProgressTracker>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 活跃任务 */
    private val activeTasks = ConcurrentHashMap<String, TaskProgress>()

    /** 任务历史 */
    private val taskHistory = mutableListOf<TaskProgress>()

    /** 最大历史记录数 */
    private val maxHistorySize = 100

    /** 当前活跃任务ID */
    private val _activeTaskId = MutableStateFlow<String?>(null)
    val activeTaskId: StateFlow<String?> = _activeTaskId.asStateFlow()

    /** 所有任务状态 */
    private val _allTasks = MutableStateFlow<List<TaskProgress>>(emptyList())
    val allTasks: StateFlow<List<TaskProgress>> = _allTasks.asStateFlow()

    // ==================== 核心API ====================

    /**
     * 创建任务
     *
     * @param name 任务名称
     * @param description 任务描述
     * @param steps 任务步骤（可选）
     * @return TaskProgress
     */
    fun createTask(
        name: String,
        description: String? = null,
        steps: List<TaskStep> = emptyList()
    ): TaskProgress {
        val task = TaskProgress(
            taskId = IdGenerator.taskId(),
            name = name,
            description = description,
            steps = steps
        )

        activeTasks[task.taskId] = task
        _activeTaskId.value = task.taskId
        updateAllTasks()

        log.info("Task created: ${task.taskId} - ${task.name}")
        EventBus.publish(TaskStartedEvent(task.taskId, task.name))

        return task
    }

    /**
     * 创建简单任务（无预定义步骤）
     *
     * @param name 任务名称
     * @return TaskProgress
     */
    fun createSimpleTask(name: String): TaskProgress {
        return createTask(name)
    }

    /**
     * 开始任务
     *
     * @param task 任务
     * @return 任务
     */
    fun startTask(task: TaskProgress): TaskProgress {
        val startedTask = task.withStarted()
        activeTasks[task.taskId] = startedTask
        _activeTaskId.value = task.taskId
        updateAllTasks()

        log.info("Task started: ${task.taskId}")
        return startedTask
    }

    /**
     * 更新任务进度
     *
     * @param taskId 任务ID
     * @param progress 进度（0-100）
     * @param currentStep 当前步骤描述
     */
    fun updateProgress(taskId: String, progress: Int, currentStep: String? = null) {
        val task = activeTasks[taskId]
        if (task != null) {
            val updatedTask = task.copy(
                progress = progress.coerceIn(0, 100),
                updatedAt = System.currentTimeMillis()
            )

            activeTasks[taskId] = updatedTask
            updateAllTasks()

            EventBus.publish(TaskProgressEvent(taskId, updatedTask.progress, currentStep))

            log.debug("Task progress updated: $taskId - $progress%")
        }
    }

    /**
     * 推进到下一步
     *
     * @param taskId 任务ID
     * @return 更新后的任务
     */
    fun advanceToNextStep(taskId: String): TaskProgress? {
        val task = activeTasks[taskId] ?: return null

        // 更新当前步骤状态为完成
        val updatedTask = if (task.currentStepIndex < task.stepCount) {
            task.withCurrentStep { it.withCompleted() }
        } else {
            task
        }

        // 推进到下一步
        val nextTask = updatedTask.withNextStep()
        activeTasks[taskId] = nextTask
        updateAllTasks()

        log.info("Task advanced to step ${nextTask.currentStepIndex}: $taskId")

        return nextTask
    }

    /**
     * 完成当前步骤
     *
     * @param taskId 任务ID
     * @param result 步骤结果（可选）
     * @return 更新后的任务
     */
    fun completeCurrentStep(taskId: String, result: Map<String, Any>? = null): TaskProgress? {
        val task = activeTasks[taskId] ?: return null

        val updatedTask = task.withCurrentStep { step ->
            step.copy(
                progress = 100,
                completedAt = System.currentTimeMillis()
            )
        }

        activeTasks[taskId] = updatedTask
        updateAllTasks()

        log.info("Current step completed: $taskId - Step ${updatedTask.currentStepIndex}")

        return updatedTask
    }

    /**
     * 添加步骤到任务
     *
     * @param taskId 任务ID
     * @param step 步骤
     * @return 更新后的任务
     */
    fun addStep(taskId: String, step: TaskStep): TaskProgress? {
        val task = activeTasks[taskId] ?: return null

        val updatedTask = task.withStep(step)
        activeTasks[taskId] = updatedTask
        updateAllTasks()

        log.info("Step added to task: $taskId - ${step.name}")

        return updatedTask
    }

    /**
     * 完成任务
     *
     * @param taskId 任务ID
     * @param success 是否成功
     */
    fun completeTask(taskId: String, success: Boolean = true) {
        val task = activeTasks.remove(taskId)
        if (task != null) {
            val completedTask = if (success) {
                task.withCompleted()
            } else {
                task.withFailed("Task failed")
            }

            // 添加到历史
            addToHistory(completedTask)

            // 清理活跃任务
            if (_activeTaskId.value == taskId) {
                _activeTaskId.value = null
            }

            updateAllTasks()

            log.info("Task completed: $taskId - success=$success")
            EventBus.publish(TaskCompletedEvent(taskId, success))
        }
    }

    /**
     * 标记任务失败
     *
     * @param taskId 任务ID
     * @param error 错误信息
     */
    fun failTask(taskId: String, error: String) {
        val task = activeTasks.remove(taskId)
        if (task != null) {
            val failedTask = task.withFailed(error)

            // 添加到历史
            addToHistory(failedTask)

            // 清理活跃任务
            if (_activeTaskId.value == taskId) {
                _activeTaskId.value = null
            }

            updateAllTasks()

            log.error("Task failed: $taskId - $error")
            EventBus.publish(TaskCompletedEvent(taskId, false))
        }
    }

    /**
     * 取消任务
     *
     * @param taskId 任务ID
     */
    fun cancelTask(taskId: String) {
        val task = activeTasks.remove(taskId)
        if (task != null) {
            val cancelledTask = task.withCancelled()

            // 添加到历史
            addToHistory(cancelledTask)

            // 清理活跃任务
            if (_activeTaskId.value == taskId) {
                _activeTaskId.value = null
            }

            updateAllTasks()

            log.info("Task cancelled: $taskId")
        }
    }

    /**
     * 获取任务
     *
     * @param taskId 任务ID
     * @return 任务
     */
    fun getTask(taskId: String): TaskProgress? {
        return activeTasks[taskId]
    }

    /**
     * 获取当前活跃任务
     */
    fun getActiveTask(): TaskProgress? {
        return _activeTaskId.value?.let { activeTasks[it] }
    }

    /**
     * 获取所有活跃任务
     */
    fun getActiveTasks(): List<TaskProgress> {
        return activeTasks.values.toList()
            .sortedBy { it.createdAt }
    }

    /**
     * 获取任务历史
     */
    fun getTaskHistory(): List<TaskProgress> {
        return taskHistory.toList()
            .sortedByDescending { it.createdAt }
    }

    /**
     * 搜索任务
     *
     * @param query 搜索关键词
     * @return 匹配的任务列表
     */
    fun searchTasks(query: String): List<TaskProgress> {
        val lowerQuery = query.lowercase()
        val allTasks = getActiveTasks() + getTaskHistory()

        return allTasks.filter { task ->
            task.name.lowercase().contains(lowerQuery) ||
            (task.description?.lowercase()?.contains(lowerQuery) ?: false) ||
            task.steps.any { it.name.lowercase().contains(lowerQuery) }
        }
    }

    /**
     * 清理已完成的历史任务
     */
    fun cleanupHistory(olderThanMillis: Long = 24 * 60 * 60 * 1000) {
        val now = System.currentTimeMillis()
        val toRemove = taskHistory.filter {
            val completedAt = it.completedAt
            completedAt != null && (now - completedAt > olderThanMillis)
        }

        toRemove.forEach { task ->
            taskHistory.remove(task)
        }

        if (toRemove.isNotEmpty()) {
            log.info("Cleaned up ${toRemove.size} old tasks")
        }
    }

    /**
     * 清理所有历史任务
     */
    fun clearHistory() {
        taskHistory.clear()
        log.info("Cleared all task history")
    }

    // ==================== 内部方法 ====================

    /**
     * 更新所有任务列表
     */
    private fun updateAllTasks() {
        _allTasks.value = (getActiveTasks() + getTaskHistory().take(50))
            .sortedByDescending { it.createdAt }
    }

    /**
     * 添加到历史
     */
    private fun addToHistory(task: TaskProgress) {
        taskHistory.add(task)

        // 限制历史大小
        if (taskHistory.size > maxHistorySize) {
            taskHistory.removeAt(0)
        }
    }

    override fun dispose() {
        scope.cancel()
        activeTasks.clear()
        taskHistory.clear()
    }

    companion object {
        fun getInstance(project: Project): TaskProgressTracker =
            project.getService(TaskProgressTracker::class.java)
    }
}