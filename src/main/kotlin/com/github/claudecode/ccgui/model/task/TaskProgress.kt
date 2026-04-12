package com.github.claudecode.ccgui.model.task

import com.github.claudecode.ccgui.util.IdGenerator
import com.google.gson.JsonObject

/**
 * 任务状态枚举
 */
enum class TaskStatus {
    /** 等待中 */
    PENDING,

    /** 运行中 */
    RUNNING,

    /** 已完成 */
    COMPLETED,

    /** 已取消 */
    CANCELLED,

    /** 失败 */
    FAILED
}

/**
 * 任务步骤
 */
data class TaskStep(
    val id: String = IdGenerator.prefixedShortId("step_"),
    val name: String,
    val description: String? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val progress: Int = 0,  // 0-100
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val metadata: Map<String, Any> = emptyMap()
) {

    val isPending: Boolean get() = status == TaskStatus.PENDING
    val isRunning: Boolean get() = status == TaskStatus.RUNNING
    val isCompleted: Boolean get() = status == TaskStatus.COMPLETED
    val isFailed: Boolean get() = status == TaskStatus.FAILED
    val isCancelled: Boolean get() = status == TaskStatus.CANCELLED

    fun withStarted(): TaskStep = copy(
        status = TaskStatus.RUNNING,
        startedAt = System.currentTimeMillis()
    )

    fun withProgress(progress: Int): TaskStep = copy(progress = progress.coerceIn(0, 100))

    fun withCompleted(): TaskStep = copy(
        status = TaskStatus.COMPLETED,
        progress = 100,
        completedAt = System.currentTimeMillis()
    )

    fun withFailed(): TaskStep = copy(
        status = TaskStatus.FAILED,
        completedAt = System.currentTimeMillis()
    )

    companion object {
        fun fromJson(json: JsonObject): TaskStep? {
            return try {
                TaskStep(
                    id = json.get("id")?.asString ?: IdGenerator.prefixedShortId("step_"),
                    name = json.get("name")?.asString ?: "",
                    description = json.get("description")?.asString,
                    status = TaskStatus.valueOf(json.get("status")?.asString ?: "PENDING"),
                    progress = json.get("progress")?.asInt ?: 0,
                    startedAt = json.get("startedAt")?.asLong,
                    completedAt = json.get("completedAt")?.asLong,
                    metadata = json.getAsJsonObject("metadata")?.let { obj ->
                        obj.entrySet().associate { it.key to it.value.toString() }
                    } ?: emptyMap()
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("name", name)
            description?.let { addProperty("description", it) }
            addProperty("status", status.name)
            addProperty("progress", progress)
            startedAt?.let { addProperty("startedAt", it) }
            completedAt?.let { addProperty("completedAt", it) }
        }
    }
}

/**
 * 任务进度
 *
 * @param taskId 任务 ID
 * @param name 任务名称
 * @param description 任务描述
 * @param status 任务状态
 * @param steps 任务步骤列表
 * @param currentStepIndex 当前步骤索引
 * @param progress 总体进度（0-100）
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param completedAt 完成时间
 * @param error 错误信息
 */
data class TaskProgress(
    val taskId: String = IdGenerator.taskId(),
    val name: String,
    val description: String? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val steps: List<TaskStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val progress: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val error: String? = null
) {

    /**
     * 是否正在运行
     */
    val isRunning: Boolean get() = status == TaskStatus.RUNNING

    /**
     * 是否已完成
     */
    val isCompleted: Boolean get() = status == TaskStatus.COMPLETED

    /**
     * 是否失败
     */
    val isFailed: Boolean get() = status == TaskStatus.FAILED

    /**
     * 是否已取消
     */
    val isCancelled: Boolean get() = status == TaskStatus.CANCELLED

    /**
     * 获取当前步骤
     */
    val currentStep: TaskStep? get() = steps.getOrNull(currentStepIndex)

    /**
     * 获取步骤数量
     */
    val stepCount: Int get() = steps.size

    /**
     * 添加步骤
     */
    fun withStep(step: TaskStep): TaskProgress {
        return copy(
            steps = steps + step,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 更新当前步骤
     */
    fun withCurrentStep(update: (TaskStep) -> TaskStep): TaskProgress {
        if (steps.isEmpty()) return this
        return copy(
            steps = steps.mapIndexed { index, step ->
                if (index == currentStepIndex) update(step) else step
            },
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 推进到下一步
     */
    fun withNextStep(): TaskProgress {
        val nextIndex = (currentStepIndex + 1).coerceAtMost(steps.size - 1)
        val overallProgress = if (steps.isNotEmpty()) {
            ((nextIndex.toDouble() / steps.size) * 100).toInt()
        } else 0

        return copy(
            currentStepIndex = nextIndex,
            progress = overallProgress,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 标记为运行中
     */
    fun withStarted(): TaskProgress {
        return copy(
            status = TaskStatus.RUNNING,
            steps = steps.mapIndexed { index, step ->
                if (index == 0) step.withStarted() else step
            },
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 标记为完成
     */
    fun withCompleted(): TaskProgress {
        return copy(
            status = TaskStatus.COMPLETED,
            progress = 100,
            completedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 标记为失败
     */
    fun withFailed(error: String): TaskProgress {
        return copy(
            status = TaskStatus.FAILED,
            error = error,
            completedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 标记为取消
     */
    fun withCancelled(): TaskProgress {
        return copy(
            status = TaskStatus.CANCELLED,
            completedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    companion object {
        fun fromJson(json: JsonObject): TaskProgress? {
            return try {
                TaskProgress(
                    taskId = json.get("taskId")?.asString ?: IdGenerator.taskId(),
                    name = json.get("name")?.asString ?: "",
                    description = json.get("description")?.asString,
                    status = TaskStatus.valueOf(json.get("status")?.asString ?: "PENDING"),
                    steps = json.getAsJsonArray("steps")?.mapNotNull {
                        TaskStep.fromJson(it.asJsonObject)
                    } ?: emptyList(),
                    currentStepIndex = json.get("currentStepIndex")?.asInt ?: 0,
                    progress = json.get("progress")?.asInt ?: 0,
                    createdAt = json.get("createdAt")?.asLong ?: System.currentTimeMillis(),
                    updatedAt = json.get("updatedAt")?.asLong ?: System.currentTimeMillis(),
                    completedAt = json.get("completedAt")?.asLong,
                    error = json.get("error")?.asString
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("taskId", taskId)
            addProperty("name", name)
            description?.let { addProperty("description", it) }
            addProperty("status", status.name)
            add("steps", com.google.gson.JsonArray().apply {
                steps.forEach { add(it.toJson()) }
            })
            addProperty("currentStepIndex", currentStepIndex)
            addProperty("progress", progress)
            addProperty("createdAt", createdAt)
            addProperty("updatedAt", updatedAt)
            completedAt?.let { addProperty("completedAt", it) }
            error?.let { addProperty("error", it) }
        }
    }
}
