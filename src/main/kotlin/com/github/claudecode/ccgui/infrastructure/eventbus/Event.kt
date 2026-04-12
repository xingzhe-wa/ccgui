package com.github.claudecode.ccgui.infrastructure.eventbus

/**
 * 事件基类
 *
 * 所有事件都必须继承此类
 */
abstract class Event(
    open val name: String,
    open val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 获取事件类型标识
     */
    abstract val type: String
}
