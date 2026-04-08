package com.github.xingzhewa.ccgui.infrastructure.eventbus

import com.github.xingzhewa.ccgui.model.interaction.InteractiveQuestion
import com.google.gson.JsonObject

/**
 * 所有事件定义
 *
 * 使用 object 继承 Event，定义单例事件类型
 */

// ==================== SDK Events ====================

/**
 * SDK 会话初始化事件
 * CLI 进程启动并返回 session_id 时触发
 */
data class SdkSessionInitEvent(
    val sessionId: String,
    val tools: List<String>
) : Event("sdk.session.init") {
    override val type: String = "sdk.session.init"
}

/**
 * SDK 权限请求事件
 * CLI 请求用户确认权限时触发
 */
data class PermissionRequestEvent(
    val project: com.intellij.openapi.project.Project,
    val requestId: String,
    val toolName: String,
    val description: String,
    val question: InteractiveQuestion
) : Event("sdk.permission.request") {
    override val type: String = "sdk.permission.request"
}

/**
 * SDK 流式文本增量事件
 * 每收到一个 assistant 文本块时触发
 */
data class SdkTextDeltaEvent(
    val sessionId: String,
    val text: String
) : Event("sdk.text.delta") {
    override val type: String = "sdk.text.delta"
}

/**
 * SDK 工具使用事件
 */
data class SdkToolUseEvent(
    val sessionId: String,
    val toolName: String,
    val input: JsonObject
) : Event("sdk.tool.use") {
    override val type: String = "sdk.tool.use"
}

/**
 * SDK 结果事件
 */
data class SdkResultEvent(
    val sessionId: String,
    val subtype: String,
    val costUsd: Double?,
    val durationMs: Long?
) : Event("sdk.result") {
    override val type: String = "sdk.result"
}

/**
 * SDK 错误事件
 */
data class SdkErrorEvent(
    val sessionId: String?,
    val error: String
) : Event("sdk.error") {
    override val type: String = "sdk.error"
}

// ==================== Session Events ====================

/**
 * 会话创建事件
 */
data class SessionCreatedEvent(
    val sessionId: String,
    val sessionName: String
) : Event("session.created") {
    override val type: String = "session.created"
}

/**
 * 会话切换事件
 */
data class SessionSwitchedEvent(
    val sessionId: String
) : Event("session.switched") {
    override val type: String = "session.switched"
}

/**
 * 会话删除事件
 */
data class SessionDeletedEvent(
    val sessionId: String
) : Event("session.deleted") {
    override val type: String = "session.deleted"
}

/**
 * 会话消息添加事件
 */
data class MessageAddedEvent(
    val sessionId: String,
    val messageId: String,
    val role: String
) : Event("session.message.added") {
    override val type: String = "session.message.added"
}

/**
 * 会话消息更新事件
 */
data class MessageUpdatedEvent(
    val sessionId: String,
    val messageId: String,
    val content: String?
) : Event("session.message.updated") {
    override val type: String = "session.message.updated"
}

// ==================== Config Events ====================

/**
 * 配置变更事件
 */
data class ConfigChangedEvent(
    val key: String,
    val value: Any?
) : Event("config.changed") {
    override val type: String = "config.changed"
}

/**
 * 主题变更事件
 */
data class ThemeChangedEvent(
    val themeId: String
) : Event("theme.changed") {
    override val type: String = "theme.changed"
}

/**
 * 模型切换事件
 */
data class ModelSwitchedEvent(
    val modelId: String
) : Event("model.switched") {
    override val type: String = "model.switched"
}

// ==================== UI Events ====================

/**
 * 工具窗口显示事件
 */
data class ToolWindowShownEvent(
    val projectId: String
) : Event("ui.toolwindow.shown") {
    override val type: String = "ui.toolwindow.shown"
}

/**
 * 工具窗口隐藏事件
 */
data class ToolWindowHiddenEvent(
    val projectId: String
) : Event("ui.toolwindow.hidden") {
    override val type: String = "ui.toolwindow.hidden"
}

/**
 * 模态框打开事件
 */
data class ModalOpenedEvent(
    val modalId: String
) : Event("ui.modal.opened") {
    override val type: String = "ui.modal.opened"
}

/**
 * 模态框关闭事件
 */
data class ModalClosedEvent(
    val modalId: String
) : Event("ui.modal.closed") {
    override val type: String = "ui.modal.closed"
}

// ==================== Task Events ====================

/**
 * 任务开始事件
 */
data class TaskStartedEvent(
    val taskId: String,
    val taskName: String
) : Event("task.started") {
    override val type: String = "task.started"
}

/**
 * 任务进度更新事件
 */
data class TaskProgressEvent(
    val taskId: String,
    val progress: Int,
    val currentStep: String?
) : Event("task.progress") {
    override val type: String = "task.progress"
}

/**
 * 任务完成事件
 */
data class TaskCompletedEvent(
    val taskId: String,
    val success: Boolean
) : Event("task.completed") {
    override val type: String = "task.completed"
}

// ==================== Interaction Events ====================

/**
 * 交互式问题事件
 */
data class InteractiveQuestionEvent(
    val question: InteractiveQuestion
) : Event("interaction.question") {
    override val type: String = "interaction.question"
}

/**
 * 交互式问题回答事件
 */
data class QuestionAnsweredEvent(
    val questionId: String,
    val answer: Any
) : Event("interaction.question.answered") {
    override val type: String = "interaction.question.answered"
}

// ==================== Skill Events ====================

/**
 * Skill 执行事件
 */
data class SkillExecutedEvent(
    val skillId: String,
    val success: Boolean,
    val executionTime: Long
) : Event("skill.executed") {
    override val type: String = "skill.executed"
}

// ==================== Agent Events ====================

/**
 * Agent 启动事件
 */
data class AgentStartedEvent(
    val agentId: String,
    val taskId: String
) : Event("agent.started") {
    override val type: String = "agent.started"
}

/**
 * Agent 完成事件
 */
data class AgentCompletedEvent(
    val agentId: String,
    val taskId: String,
    val success: Boolean
) : Event("agent.completed") {
    override val type: String = "agent.completed"
}

// ==================== MCP Events ====================

/**
 * MCP 服务器连接事件
 */
data class McpServerConnectedEvent(
    val serverId: String,
    val capabilities: List<String>
) : Event("mcp.server.connected") {
    override val type: String = "mcp.server.connected"
}

/**
 * MCP 服务器断开事件
 */
data class McpServerDisconnectedEvent(
    val serverId: String,
    val error: String?
) : Event("mcp.server.disconnected") {
    override val type: String = "mcp.server.disconnected"
}
