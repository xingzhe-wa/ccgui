package com.github.claudecode.ccgui.infrastructure.eventbus

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * EventBus 单元测试
 */
class EventBusTest {

    private var subscriptionId: String? = null

    @Before
    fun setup() {
        EventBus.unsubscribeAll()
    }

    @After
    fun tearDown() {
        subscriptionId?.let { EventBus.unsubscribe(it) }
        EventBus.unsubscribeAll()
    }

    @Test
    fun testPublishAndSubscribeShouldReceiveEvent() {
        val latch = CountDownLatch(1)
        var receivedEvent: SessionCreatedEvent? = null

        subscriptionId = EventBus.subscribe { event ->
            if (event is SessionCreatedEvent) {
                receivedEvent = event
                latch.countDown()
            }
        }

        val testEvent = SessionCreatedEvent("test-session-123", "Test Session")
        EventBus.publish(testEvent)

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertNotNull(receivedEvent)
        assertEquals("test-session-123", receivedEvent?.sessionId)
    }

    @Test
    fun testSubscribeTypeShouldReceiveOnlySpecificType() {
        val latch = CountDownLatch(1)
        var receivedEvent: LocaleChangedEvent? = null

        subscriptionId = EventBus.subscribeType(LocaleChangedEvent::class.java) { event ->
            receivedEvent = event
            latch.countDown()
        }

        val testEvent = LocaleChangedEvent(Locale.ENGLISH)
        EventBus.publish(testEvent)

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertNotNull(receivedEvent)
        assertEquals(Locale.ENGLISH, receivedEvent?.locale)
    }

    @Test
    fun testUnsubscribeShouldStopReceivingEvents() {
        val latch = CountDownLatch(1)
        var receivedCount = 0

        subscriptionId = EventBus.subscribe {
            receivedCount++
            latch.countDown()
        }

        // 取消订阅
        subscriptionId?.let { EventBus.unsubscribe(it) }

        // 发布事件
        EventBus.publish(SessionCreatedEvent("test", "test"))

        // 等待一小段时间，不应该收到事件
        Thread.sleep(100)
        assertEquals(0, receivedCount)
    }

    @Test
    fun testUnsubscribeAllShouldClearAllSubscribers() {
        // 添加多个订阅者
        val id1 = EventBus.subscribe { }
        val id2 = EventBus.subscribe { }

        EventBus.unsubscribeAll()

        // 检查订阅者数量
        assertEquals(0, EventBus.getSubscriberCount())
    }

    @Test
    fun testGetSubscriberCountShouldReturnCorrectCount() {
        val count1 = EventBus.subscribe { }
        val count2 = EventBus.subscribe { }

        val totalCount = EventBus.getSubscriberCount()
        assertEquals(2, totalCount)

        // 清理
        EventBus.unsubscribe(count1)
        EventBus.unsubscribe(count2)
    }

    @Test
    fun testMultipleSubscribersAllShouldReceiveEvent() {
        val latch = CountDownLatch(2)
        var count1 = 0
        var count2 = 0

        EventBus.subscribe { count1++ }
        EventBus.subscribe { count2++ }

        val testEvent = SessionCreatedEvent("test", "test")
        EventBus.publish(testEvent)

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(1, count1)
        assertEquals(1, count2)
    }

    @Test
    fun testEventPropertiesShouldBePreserved() {
        val latch = CountDownLatch(1)
        var receivedEvent: SessionCreatedEvent? = null

        EventBus.subscribe { event ->
            if (event is SessionCreatedEvent) {
                receivedEvent = event
                latch.countDown()
            }
        }

        val testEvent = SessionCreatedEvent("session-abc", "Session Name")
        EventBus.publish(testEvent)

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals("session-abc", receivedEvent?.sessionId)
        assertEquals("Session Name", receivedEvent?.sessionName)
    }

    @Test
    fun testSubscribeWithHandleShouldReturnValidHandle() {
        val handle = EventBus.subscribeWithHandle(SessionCreatedEvent::class.java) { event ->
            // 处理事件
        }

        assertNotNull(handle.id)
        assertEquals("EventSubscriberHandle", handle::class.simpleName)

        // 清理
        handle.unsubscribe()
    }

    @Test
    fun testEventNameShouldReturnCorrectType() {
        val event = SessionCreatedEvent("test", "test")
        assertEquals("session.created", event.name)
    }

    @Test
    fun testConcurrentPublishShouldHandleSafely() {
        val latch = CountDownLatch(10)
        var receivedCount = 0

        repeat(10) {
            EventBus.subscribe {
                receivedCount++
                latch.countDown()
            }
        }

        // 并发发布事件
        repeat(10) {
            Thread {
                EventBus.publish(SessionCreatedEvent("test", "test"))
            }.start()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertEquals(10, receivedCount)
    }
}
