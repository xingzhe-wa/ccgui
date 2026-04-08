package com.github.xingzhewa.ccgui.model.config

/**
 * 对话模式枚举
 */
enum class ConversationMode {
    /** 自动模式 - SDK 自动决定最佳行为 */
    AUTO,

    /** 助手模式 - 仅提供建议，不自动执行操作 */
    ASSISTANT,

    /** 自动执行模式 - 低风险操作自动执行 */
    AUTO_EXEC,

    /** 调试模式 - 详细日志输出 */
    DEBUG
}
