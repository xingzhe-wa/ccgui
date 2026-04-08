package com.github.xingzhewa.ccgui.model.message

/**
 * 消息角色枚举
 *
 * 定义聊天消息的发送者角色
 */
enum class MessageRole {
    /** 用户发送的消息 */
    USER,

    /** AI 助手回复的消息 */
    ASSISTANT,

    /** 系统消息 */
    SYSTEM
}
