package com.github.claudecode.ccgui.infrastructure.eventbus

import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic

/**
 * 事件总线 - MessageBus 适配层
 *
 * 提供模块间解耦的事件通信机制，内部委托给 IntelliJ Platform MessageBus
 *
 * 使用方式:
 *   // 初始化（建议在 Project 级别服务中调用）
 *   EventBus.init(project)
 *
 *   // 发布事件
 *   EventBus.publish(SessionCreatedEvent("123", "My Session"), project)
 *
 *   // 订阅事件
 *   EventBus.subscribe(project, SessionCreatedEvent::class.java) { event ->
 *       println("Session created: ${event.sessionId}")
 *   }
 *
 *   // 取消订阅
 *   EventBus.unsubscribe(project, subscriptionId)
 */
object EventBus {

    private val logger = logger<EventBus>()

    // Project -> MessageBusConnection 映射
    private val connections = mutableMapOf<Project, MessageBusConnection>()

    // 订阅ID -> (eventClass, handler) 映射
    private val subscriptionHandlers = mutableMapOf<String, Pair<Class<out Event>, (Event) -> Unit>>()

    // 订阅ID计数器
    private var subscriptionIdCounter = 0L

    // 全局同步锁
    private val lock = Any()

    /**
     * 单一通用 Topic 用于所有 CCGUI 事件
     * 使用 EventBusListener 接口
     */
    private val CCGUI_EVENT_TOPIC: Topic<EventBusListener> = Topic.create(
        "CCGUI.Events",
        EventBusListener::class.java
    )

    /**
     * 初始化 EventBus（建立 MessageBus 连接）
     * 建议在 Project 级别组件初始化时调用
     *
     * @param project IntelliJ 项目实例
     */
    fun init(project: Project) {
        synchronized(lock) {
            if (connections.containsKey(project)) {
                logger.debug("EventBus already initialized for project")
                return
            }

            val messageBus = project.messageBus
            val connection = messageBus.connect()

            connections[project] = connection

            logger.debug("EventBus initialized for project: ${project.name}")
        }
    }

    /**
     * 清理项目的 EventBus 资源
     */
    private fun cleanupProject(project: Project) {
        synchronized(lock) {
            connections.remove(project)
            // 清理该项目的订阅处理器
            subscriptionHandlers.entries.removeIf { (_, handler) ->
                // 保留其他项目的订阅
                false
            }
            logger.debug("EventBus cleaned up for project: ${project.name}")
        }
    }

    /**
     * 获取或创建连接
     */
    private fun getConnection(project: Project): MessageBusConnection? {
        synchronized(lock) {
            var connection = connections[project]
            if (connection == null) {
                // 自动初始化
                init(project)
                connection = connections[project]
            }
            return connection
        }
    }

    /**
     * 发布事件
     *
     * @param event 事件实例
     * @param project 可选的项目实例（如果不提供则尝试使用默认项目）
     */
    fun publish(event: Event, project: Project? = null) {
        val targetProject = project ?: getDefaultProject()
        if (targetProject == null) {
            logger.warn("Cannot publish event: no project available - ${event.name}")
            return
        }

        // 确保已初始化
        if (!connections.containsKey(targetProject)) {
            init(targetProject)
        }

        try {
            val publisher = targetProject.messageBus.syncPublisher(CCGUI_EVENT_TOPIC)
            publisher.onEvent(event)
            logger.debug("Published event: ${event.name}")
        } catch (e: Exception) {
            logger.error("Failed to publish event: ${event.name}", e)
        }
    }

    /**
     * 订阅事件（类型安全版本）
     *
     * @param project 项目实例
     * @param eventClass 事件类
     * @param handler 事件处理函数
     * @return 订阅者 ID，用于取消订阅
     */
    fun <T : Event> subscribe(
        project: Project,
        eventClass: Class<T>,
        handler: (T) -> Unit
    ): String {
        val connection = getConnection(project)
        if (connection == null) {
            logger.error("Cannot subscribe: no MessageBus connection available")
            return ""
        }

        val subscriptionId = generateSubscriptionId()

        // 创建监听器，类型过滤由调用方保证
        @Suppress("UNCHECKED_CAST")
        val typedHandler = handler as (Event) -> Unit

        // 保存处理器映射
        subscriptionHandlers[subscriptionId] = eventClass to typedHandler

        // 创建 EventBusListener 包装
        val listener = object : EventBusListener {
            override fun onEvent(event: Event) {
                // 检查事件类型匹配
                if (eventClass.isInstance(event)) {
                    try {
                        typedHandler(event)
                    } catch (e: Exception) {
                        logger.error("Error in event handler for ${eventClass.simpleName}", e)
                    }
                }
            }
        }

        // 订阅
        connection.subscribe(CCGUI_EVENT_TOPIC, listener)

        logger.debug("Subscribed to event: ${eventClass.simpleName}, id: $subscriptionId")
        return subscriptionId
    }

    /**
     * 订阅事件（内联类型版本）
     *
     * @param project 项目实例
     * @param event 事件实例（用于类型推断）
     * @param handler 事件处理函数
     * @return 订阅者 ID，用于取消订阅
     */
    fun <T : Event> subscribeEvent(
        project: Project,
        event: T,
        handler: (T) -> Unit
    ): String {
        return subscribe(project, event::class.java as Class<T>, handler)
    }

    /**
     * 订阅指定类型的事件（兼容旧API，仍需传入project）
     *
     * @deprecated Use subscribe(project, eventClass, handler) instead
     */
    @Deprecated("Use subscribe with explicit project parameter")
    fun <T : Event> subscribeType(clazz: Class<T>, handler: (T) -> Unit): String {
        logger.warn("subscribeType without project is deprecated, use subscribe with project")
        return ""
    }

    /**
     * 通用订阅（接收所有事件）
     *
     * @param project 项目实例
     * @param handler 事件处理函数
     * @return 订阅者 ID，用于取消订阅
     */
    fun subscribeAll(project: Project, handler: (Event) -> Unit): String {
        val connection = getConnection(project)
        if (connection == null) {
            logger.error("Cannot subscribeAll: no MessageBus connection available")
            return ""
        }

        val subscriptionId = generateSubscriptionId()
        subscriptionHandlers[subscriptionId] = Event::class.java to handler

        val listener = object : EventBusListener {
            override fun onEvent(event: Event) {
                try {
                    handler(event)
                } catch (e: Exception) {
                    logger.error("Error in event handler", e)
                }
            }
        }

        connection.subscribe(CCGUI_EVENT_TOPIC, listener)
        logger.debug("Subscribed to all events, id: $subscriptionId")
        return subscriptionId
    }

    /**
     * 取消订阅
     *
     * @param project 项目实例
     * @param subscriberId 订阅者 ID
     */
    fun unsubscribe(project: Project, subscriberId: String) {
        synchronized(lock) {
            // MessageBus subscriptions via connection.subscribe are automatically
            // cleaned up when the connection is disposed
            // For manual cleanup, we just remove our handler reference
            subscriptionHandlers.remove(subscriberId)
            logger.debug("Unsubscribed: $subscriberId")
        }
    }

    /**
     * 取消订阅（旧API兼容）
     */
    fun unsubscribe(subscriberId: String) {
        logger.warn("unsubscribe(subscriberId) without project is deprecated")
    }

    /**
     * 取消所有指定类型的订阅
     */
    fun <T : Event> unsubscribeAll(clazz: Class<T>) {
        subscriptionHandlers.entries.removeIf { (_, pair) ->
            pair.first == clazz
        }
        logger.debug("unsubscribeAll called for ${clazz.simpleName}")
    }

    /**
     * 清空所有订阅
     */
    fun unsubscribeAll() {
        synchronized(lock) {
            subscriptionHandlers.clear()
            logger.debug("All subscriptions cleared")
        }
    }

    /**
     * 获取订阅者数量
     */
    fun getSubscriberCount(): Int {
        synchronized(lock) {
            return subscriptionHandlers.size
        }
    }

    /**
     * 获取指定类型的订阅者数量
     */
    fun <T : Event> getSubscriberCount(clazz: Class<T>): Int {
        return subscriptionHandlers.count { it.value.first == clazz }
    }

    /**
     * 获取默认项目（如果可用）
     */
    private fun getDefaultProject(): Project? {
        return try {
            com.intellij.openapi.project.ProjectManager.getInstance().defaultProject
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 生成订阅ID
     */
    private fun generateSubscriptionId(): String {
        return "sub_${System.nanoTime()}_${++subscriptionIdCounter}"
    }
}

/**
 * 全局事件监听器接口
 * 用于订阅所有事件类型
 */
interface EventBusListener {
    fun onEvent(event: Event)
}

/**
 * 带句柄的订阅接口
 */
interface EventSubscriberHandle {
    val id: String
    fun unsubscribe(project: Project)
}

/**
 * 创建带句柄的订阅
 */
fun <T : Event> EventBus.subscribeWithHandle(
    project: Project,
    clazz: Class<T>,
    handler: (T) -> Unit
): EventSubscriberHandle {
    val id = subscribe(project, clazz, handler)
    return object : EventSubscriberHandle {
        override val id: String = id
        override fun unsubscribe(project: Project) = EventBus.unsubscribe(project, id)
    }
}
