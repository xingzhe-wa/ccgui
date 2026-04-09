package com.github.xingzhewa.ccgui.model.config

/**
 * 对话模式枚举
 *
 * @see <a href="https://docs.anthropic.com/en/docs/claude-code/modes">Claude Code Modes</a>
 */
enum class ConversationMode {
    /** 自动模式 - 快速响应，直接执行，适合简单问答 */
    AUTO,

    /** 思考模式 - 深度思考，逐步推理，适合复杂问题分析 */
    THINKING,

    /** 规划模式 - 先规划后执行，适合大型任务分解 */
    PLANNING
}
