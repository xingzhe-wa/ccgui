package com.github.claudecode.ccgui.model.config

/**
 * 对话模式枚举
 *
 * @see <a href="https://docs.anthropic.com/en/docs/claude-code/modes">Claude Code Modes</a>
 */
enum class ConversationMode(
    val displayName: String,
    val description: String
) {
    /** 自动模式 - 快速响应，直接执行，适合简单问答 */
    AUTO(
        displayName = "自动模式",
        description = "快速响应，直接执行。适合简单问答、代码补全、快速修复等轻量任务。Claude Code 会立即执行命令，无需额外确认。"
    ),

    /** 思考模式 - 深度思考，逐步推理，适合复杂问题分析 */
    THINKING(
        displayName = "思考模式",
        description = "深度思考，逐步推理。适合复杂问题分析、架构设计、代码审查等需要深入思考的任务。Claude 会先展示思考过程再给出最终答案。"
    ),

    /** 规划模式 - 先规划后执行，适合大型任务分解 */
    PLANNING(
        displayName = "规划模式",
        description = "先规划后执行，适合大型任务分解。适合复杂的多步骤任务，如重构大型模块、编写完整功能、批量修改等。Claude 会先制定执行计划，经确认后再逐步执行。"
    );

    companion object {
        /**
         * 获取所有模式的描述信息
         */
        fun getAllModeDescriptions(): List<Map<String, String>> {
            return entries.map { mode ->
                mapOf(
                    "mode" to mode.name,
                    "displayName" to mode.displayName,
                    "description" to mode.description
                )
            }
        }
    }
}
