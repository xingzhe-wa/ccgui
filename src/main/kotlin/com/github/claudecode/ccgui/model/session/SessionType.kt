package com.github.claudecode.ccgui.model.session

/**
 * 会话类型枚举
 */
enum class SessionType {
    /** 项目会话 - 绑定到特定项目 */
    PROJECT,

    /** 全局会话 - 跨项目共享 */
    GLOBAL,

    /** 临时会话 - 仅当前会话有效 */
    TEMPORARY
}
