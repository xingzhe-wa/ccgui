package com.github.xingzhewa.ccgui.infrastructure.eventbus

import com.github.xingzhewa.ccgui.util.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * 事件总线
 *
 * 提供模块间解耦的事件通信机制
 *
 * 使用方式:
 *   // 发布事件
 *   EventBus.publish(SessionCreatedEvent("123", "My Session"))
 *
 *   // 订阅事件
 *   EventBus.subscribe<SessionCreatedEvent> { event ->
 *       println("Session created: ${event.sessionId}")
 *   }
 *
 * 扩展埋点:
 *   - 后续可添加事件历史记录
 *   - 后续可添加事件重播机制
 */
object EventBus {

    private val logger = logger<EventBus>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 64
    )
    private val events: SharedFlow<Event> = _events.asSharedFlow()

    /**
     * 订阅者记录
     * 使用 WeakReference 防止内存泄漏
     */
    private val subscribers = mutableMapOf<String, MutableList<EventSubscriber>>()

    /**
     * 事件订阅者
     * 使用 WeakReference 包装 handler，防止持有订阅者对象导致内存泄漏
     */
    data class EventSubscriber(
        val id: String,
        val handlerRef: WeakReference<(Event) -> Unit>,
        val eventType: Class<out Event>? = null
    ) {
        /**
         * 获取handler，如果引用已清除则返回null
         */
        fun getHandler(): ((Event) -> Unit)? = handlerRef.get()

        /**
         * 检查引用是否有效
         */
        fun isValid(): Boolean = handlerRef.get() != null
    }

    init {
        // 启动事件转发协程
        scope.launch {
            events.collect { event ->
                notifySubscribers(event)
            }
        }

        // 启动定期清理无效订阅者的任务
        scope.launch {
            kotlinx.coroutines.delay(60000) // 每分钟清理一次
            cleanupInvalidSubscribers()
        }
    }

    /**
     * 发布事件
     *
     * @param event 事件实例
     */
    fun publish(event: Event) {
        logger.debug("Publishing event: ${event.name}")
        scope.launch {
            _events.emit(event)
        }
    }

    /**
     * 订阅事件（通用订阅，接收所有事件）
     *
     * @param handler 事件处理函数
     * @return 订阅者 ID，用于取消订阅
     */
    fun subscribe(handler: (Event) -> Unit): String {
        val id = generateSubscriberId()
        synchronized(subscribers) {
            subscribers.getOrPut("general") { mutableListOf() }
                .add(EventSubscriber(id, WeakReference(handler)))
        }
        return id
    }

    /**
     * 订阅指定类型的事件
     *
     * @param handler 事件处理函数
     * @return 订阅者 ID，用于取消订阅
     */
    fun <T : Event> subscribeType(clazz: Class<T>, handler: (T) -> Unit): String {
        val id = generateSubscriberId()
        synchronized(subscribers) {
            subscribers.getOrPut(clazz.name) { mutableListOf() }
                .add(EventSubscriber(id, WeakReference({ event -> handler(event as T) }), clazz))
        }
        return id
    }

    /**
     * 取消订阅
     *
     * @param subscriberId 订阅者 ID
     */
    fun unsubscribe(subscriberId: String) {
        synchronized(subscribers) {
            subscribers.values.forEach { list ->
                list.removeAll { it.id == subscriberId }
            }
        }
    }

    /**
     * 取消指定类型的全部订阅
     */
    fun <T : Event> unsubscribeAll(clazz: Class<T>) {
        synchronized(subscribers) {
            subscribers.remove(clazz.name)
        }
    }

    /**
     * 清空所有订阅
     */
    fun unsubscribeAll() {
        synchronized(subscribers) {
            subscribers.clear()
        }
    }

    /**
     * 通知订阅者
     * 自动清理无效的订阅者
     */
    private fun notifySubscribers(event: Event) {
        val eventClassName = event::class.java.name

        // 获取通用订阅者
        val generalSubscribers = synchronized(subscribers) {
            subscribers["general"]?.toList() ?: emptyList()
        }

        // 获取类型特定订阅者
        val typedSubscribers = synchronized(subscribers) {
            subscribers[eventClassName]?.toList() ?: emptyList()
        }

        // 通知所有相关订阅者
        (generalSubscribers + typedSubscribers).forEach { subscriber ->
            try {
                subscriber.getHandler()?.invoke(event)
            } catch (e: Exception) {
                logger.error("Error in event subscriber ${subscriber.id}: ${e.message}", e)
            }
        }
    }

    /**
     * 清理无效的订阅者（引用已被GC）
     */
    private fun cleanupInvalidSubscribers() {
        synchronized(subscribers) {
            var cleanedCount = 0
            subscribers.forEach { (key, list) ->
                val beforeSize = list.size
                list.removeAll { !it.isValid() }
                cleanedCount += (beforeSize - list.size)
            }
            // 移除空的订阅列表
            subscribers.entries.removeIf { it.value.isEmpty() }
            if (cleanedCount > 0) {
                logger.debug("Cleaned up $cleanedCount invalid event subscribers")
            }
        }
    }

    /**
     * 生成订阅者 ID
     */
    private fun generateSubscriberId(): String {
        return "sub_${System.nanoTime()}_${(Math.random() * 1000).toInt()}"
    }

    /**
     * 获取订阅者数量
     */
    fun getSubscriberCount(): Int {
        synchronized(subscribers) {
            return subscribers.values.sumOf { it.size }
        }
    }

    /**
     * 获取指定类型的订阅者数量
     */
    fun <T : Event> getSubscriberCount(clazz: Class<T>): Int {
        synchronized(subscribers) {
            return subscribers[clazz.name]?.size ?: 0
        }
    }
}

/**
 * 事件订阅者接口
 * 用于需要取消订阅的场景
 */
interface EventSubscriberHandle {
    val id: String
    fun unsubscribe()
}

/**
 * 创建带句柄的订阅
 */
fun <T : Event> EventBus.subscribeWithHandle(
    clazz: Class<T>,
    handler: (T) -> Unit
): EventSubscriberHandle {
    val id = subscribeType(clazz, handler)
    return object : EventSubscriberHandle {
        override val id: String = id
        override fun unsubscribe() = EventBus.unsubscribe(id)
    }
}
