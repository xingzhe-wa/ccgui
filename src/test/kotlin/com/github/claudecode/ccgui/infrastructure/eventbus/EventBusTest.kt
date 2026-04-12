package com.github.claudecode.ccgui.infrastructure.eventbus

import com.intellij.testFramework.LightPlatformTestCase
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * EventBus 单元测试
 */
class EventBusTest : LightPlatformTestCase() {

    private var subscriptionId: String? = null

    override fun setUp() {
        super.setUp()
        EventBus.init(project)
        EventBus.unsubscribeAll()
    }

    override fun tearDown() {
        subscriptionId?.let { EventBus.unsubscribe(project, it) }
        EventBus.unsubscribeAll()
        super.tearDown()
    }

    fun testPublishAndSubscribeShouldReceiveEvent() {
        val latch = CountDownLatch(1)
        var receivedEvent: SessionCreatedEvent? = null

        subscriptionId = EventBus.subscribe(project, SessionCreatedEvent::class.java) { event ->
            receivedEvent = event
            latch.countDown()
        }

        val testEvent = SessionCreatedEvent("test-session-123", "Test Session")
        EventBus.publish(testEvent, project)

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertNotNull(receivedEvent)
        assertEquals("test-session-123", receivedEvent!!.sessionId)
    }

    fun testSubscribeTypeShouldReceiveOnlySpecificType() {
        val latch = CountDownLatch(1)
        var receivedEvent: LocaleChangedEvent? = null

        subscriptionId = EventBus.subscribe(project, LocaleChangedEvent::class.java) { event ->
            receivedEvent = event
            latch.countDown()
        }

        val testEvent = LocaleChangedEvent(Locale.ENGLISH)
        EventBus.publish(testEvent, project)

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertNotNull(receivedEvent)
        assertEquals(Locale.ENGLISH, receivedEvent!!.locale)
    }

    fun testUnsubscribeShouldStopReceivingEvents() {
        val latch = CountDownLatch(1)
        var receivedCount = 0

        subscriptionId = EventBus.subscribeAll(project) {
            receivedCount++
            latch.countDown()
        }

        // 取消订阅
        subscriptionId?.let { EventBus.unsubscribe(project, it) }

        // 发布事件
        EventBus.publish(SessionCreatedEvent("test", "test"), project)

        // 等待一小段时间，不应该收到事件
        Thread.sleep(100)
        assertEquals(0, receivedCount)
    }

    fun testUnsubscribeAllShouldClearAllSubscribers() {
        // 添加多个订阅者
        val id1 = EventBus.subscribeAll(project) { }
        val id2 = EventBus.subscribeAll(project) { }

        EventBus.unsubscribeAll()

        // 检查订阅者数量
        assertEquals(0, EventBus.getSubscriberCount())

        // 清理
        EventBus.unsubscribe(project, id1)
        EventBus.unsubscribe(project, id2)
    }

    fun testGetSubscriberCountShouldReturnCorrectCount() {
        val count1 = EventBus.subscribeAll(project) { }
        val count2 = EventBus.subscribeAll(project) { }

        val totalCount = EventBus.getSubscriberCount()
        assertEquals(2, totalCount)

        // 清理
        EventBus.unsubscribe(project, count1)
        EventBus.unsubscribe(project, count2)
    }

    fun testMultipleSubscribersAllShouldReceiveEvent() {
        val latch = CountDownLatch(2)
        var count1 = 0
        var count2 = 0

        EventBus.subscribeAll(project) { count1++ }
        EventBus.subscribeAll(project) { count2++ }

        val testEvent = SessionCreatedEvent("test", "test")
        EventBus.publish(testEvent, project)

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(1, count1)
        assertEquals(1, count2)
    }

    fun testEventPropertiesShouldBePreserved() {
        val latch = CountDownLatch(1)
        var receivedEvent: SessionCreatedEvent? = null

        EventBus.subscribe(project, SessionCreatedEvent::class.java) { event ->
            receivedEvent = event
            latch.countDown()
        }

        val testEvent = SessionCreatedEvent("session-abc", "Session Name")
        EventBus.publish(testEvent, project)

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals("session-abc", receivedEvent!!.sessionId)
        assertEquals("Session Name", receivedEvent!!.sessionName)
    }

    fun testSubscribeWithHandleShouldReturnValidHandle() {
        val handle = EventBus.subscribeWithHandle(project, SessionCreatedEvent::class.java) { event ->
            // 处理事件
        }

        assertNotNull(handle.id)
        assertEquals("EventSubscriberHandle", handle::class.simpleName)

        // 清理
        handle.unsubscribe(project)
    }

    fun testEventNameShouldReturnCorrectType() {
        val event = SessionCreatedEvent("test", "test")
        assertEquals("session.created", event.name)
    }

    fun testConcurrentPublishShouldHandleSafely() {
        val latch = CountDownLatch(10)
        var receivedCount = 0

        repeat(10) {
            EventBus.subscribeAll(project) {
                receivedCount++
                latch.countDown()
            }
        }

        // 并发发布事件
        repeat(10) {
            Thread {
                EventBus.publish(SessionCreatedEvent("test", "test"), project)
            }.start()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertEquals(10, receivedCount)
    }
}
