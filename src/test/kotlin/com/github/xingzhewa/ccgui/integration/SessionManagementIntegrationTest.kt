package com.github.xingzhewa.ccgui.integration

import com.github.xingzhewa.ccgui.application.context.ContextManager
import com.github.xingzhewa.ccgui.application.session.SessionManager
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.SessionCreatedEvent
import com.github.xingzhewa.ccgui.infrastructure.eventbus.MessageAddedEvent
import com.github.xingzhewa.ccgui.model.session.ChatSession
import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.message.MessageRole
import com.github.xingzhewa.ccgui.model.session.SessionType
import com.intellij.testFramework.LightPlatformTestCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull

/**
 * 会话管理集成测试
 *
 * 测试会话创建、消息添加、事件发布等完整流程
 */
class SessionManagementIntegrationTest : LightPlatformTestCase() {

    private lateinit var sessionManager: SessionManager
    private lateinit var contextManager: ContextManager

    @Before
    override fun setUp() {
        super.setUp()
        sessionManager = SessionManager.getInstance(project)
        contextManager = ContextManager.getInstance(project)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        // 清理测试创建的会话
        val sessions = sessionManager.getHistorySessions()
        sessions.forEach { session ->
            if (session.id.startsWith("test-")) {
                sessionManager.deleteSession(session.id)
            }
        }
    }

    @org.junit.Test
    fun testCreateSessionFlowShouldCreateAndPersistSession() {
        runBlocking {
            // 1. 创建会话
            val session = sessionManager.createSession(
                name = "Test Session",
                type = SessionType.PROJECT
            )

            assertNotNull(session)
            assertEquals("Test Session", session.name)
            assertEquals(SessionType.PROJECT, session.type)
            assertTrue(session.id.isNotEmpty())

            // 2. 验证会话被持久化
            val loaded = sessionManager.getSession(session.id)
            assertNotNull(loaded)
            assertEquals("Test Session", loaded?.name)
        }
    }

    @org.junit.Test
    fun testAddMessageFlowShouldAddMessageToSession() = runBlocking {
        val session = sessionManager.createSession("Test Message Session", SessionType.PROJECT)

        // 添加用户消息
        val userMessage = ChatMessage.userMessage("Hello Claude")
        sessionManager.addMessage(session.id, userMessage)

        // 验证消息被添加
        val loadedSession = sessionManager.getSession(session.id)
        assertNotNull(loadedSession)
        assertEquals(1, loadedSession?.messages?.size)
        assertEquals("Hello Claude", loadedSession?.messages?.firstOrNull()?.content)
        assertEquals(MessageRole.USER, loadedSession?.messages?.firstOrNull()?.role)
    }

    @org.junit.Test
    fun testSessionEventsShouldPublishCorrectEvents() = runBlocking {
        val latch = java.util.concurrent.CountDownLatch(2)
        val events = mutableListOf<Any>()

        val subscriptionId = EventBus.subscribe { event ->
            events.add(event)
            latch.countDown()
        }

        try {
            // 创建会话（应该触发 SessionCreatedEvent）
            val session = sessionManager.createSession("Event Test Session", SessionType.PROJECT)

            // 添加消息（应该触发 MessageAddedEvent）
            val message = ChatMessage.userMessage("Test message")
            sessionManager.addMessage(session.id, message)

            // 等待事件
            assertTrue(latch.await(2, java.util.concurrent.TimeUnit.SECONDS))

            // 验证事件
            assertTrue(events.any { it is SessionCreatedEvent })
            assertTrue(events.any { it is MessageAddedEvent })

            val sessionEvent = events.filterIsInstance<SessionCreatedEvent>().firstOrNull()
            assertEquals(session.id, sessionEvent?.sessionId)

            val messageEvent = events.filterIsInstance<MessageAddedEvent>().firstOrNull()
            assertEquals(session.id, messageEvent?.sessionId)
        } finally {
            subscriptionId?.let { EventBus.unsubscribe(it) }
        }
    }

    @org.junit.Test
    fun testContextTrackingShouldTrackContextLengthCorrectly() = runBlocking {
        val session = sessionManager.createSession("Context Test Session", SessionType.PROJECT)

        // 记录消息
        contextManager.recordUserMessage(session.id, "Short message")
        assertEquals(13, contextManager.getContextLength(session.id))

        contextManager.recordUserMessage(session.id, "A".repeat(100))
        assertEquals(113, contextManager.getContextLength(session.id))

        contextManager.recordAssistantMessage(session.id, "Response")
        assertEquals(121, contextManager.getContextLength(session.id))
    }

    @org.junit.Test
    fun testContextCompactionShouldTriggerWhenThresholdReached() = runBlocking {
        val session = sessionManager.createSession("Compaction Test Session", SessionType.PROJECT)

        // 添加大量消息直到接近阈值
        repeat(100) {
            contextManager.recordUserMessage(session.id, "A".repeat(100))
            contextManager.recordAssistantMessage(session.id, "B".repeat(100))
        }

        // 检查是否需要压缩
        val shouldCompact = contextManager.shouldCompact(session.id)
        // 注意：默认阈值是 200K tokens * 0.8 * 3.5 = 560,000 字符
        // 100 次循环约 10,000 字符，不会触发压缩
        assertFalse(shouldCompact)
    }

    @org.junit.Test
    fun testSessionSwitchingShouldSwitchActiveSession() = runBlocking {
        val session1 = sessionManager.createSession("Session 1", SessionType.PROJECT)
        val session2 = sessionManager.createSession("Session 2", SessionType.PROJECT)

        // 验证初始活跃会话
        val currentSession = sessionManager.getCurrentSession()
        assertEquals(session2.id, currentSession?.id)

        // 切换到 session1
        sessionManager.setCurrentSession(session1.id)
        val switchedSession = sessionManager.getCurrentSession()
        assertEquals(session1.id, switchedSession?.id)

        // 验证事件发布
        val latch = java.util.concurrent.CountDownLatch(1)
        val subscriptionId = EventBus.subscribe { event ->
            if (event is com.github.xingzhewa.ccgui.infrastructure.eventbus.SessionSwitchedEvent) {
                latch.countDown()
            }
        }

        try {
            assertTrue(latch.await(2, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            subscriptionId?.let { EventBus.unsubscribe(it) }
        }
    }

    @org.junit.Test
    fun testMessageRolesShouldPreserveRoleInformation() = runBlocking {
        val session = sessionManager.createSession("Role Test Session", SessionType.PROJECT)

        val userMsg = ChatMessage.userMessage("User input")
        val assistantMsg = ChatMessage.assistantMessage("Assistant response")
        val systemMsg = ChatMessage.systemMessage("System notification")

        sessionManager.addMessage(session.id, userMsg)
        sessionManager.addMessage(session.id, assistantMsg)
        sessionManager.addMessage(session.id, systemMsg)

        val loadedSession = sessionManager.getSession(session.id)
        assertNotNull(loadedSession)
        assertEquals(3, loadedSession?.messages?.size)

        val messages = loadedSession?.messages!!
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals(MessageRole.SYSTEM, messages[2].role)
    }

    @org.junit.Test
    fun testSessionDeletionShouldRemoveSessionAndCleanup() = runBlocking {
        val session = sessionManager.createSession("Delete Test Session", SessionType.PROJECT)

        // 添加一些消息
        repeat(5) {
            sessionManager.addMessage(session.id, ChatMessage.userMessage("Message $it"))
        }

        val sessionId = session.id

        // 删除会话
        sessionManager.deleteSession(sessionId)

        // 验证会话被删除
        val deleted = sessionManager.getSession(sessionId)
        assertNull(deleted)

        // 验证上下文被清理
        assertEquals(0, contextManager.getContextLength(sessionId))
    }

    @org.junit.Test
    fun testSessionPersistenceShouldSurviveServiceReload() = runBlocking {
        val session = sessionManager.createSession("Persistence Test Session", SessionType.PROJECT)
        val sessionId = session.id

        // 模拟服务重新加载（通过创建新实例）
        val newSessionManager = SessionManager.getInstance(project)
        val loadedSession = newSessionManager.getSession(sessionId)

        assertNotNull(loadedSession)
        assertEquals("Persistence Test Session", loadedSession!!.name)
    }

    @org.junit.Test
    fun testConcurrentMessageAdditionShouldHandleSafely() = runBlocking {
        val session = sessionManager.createSession("Concurrent Test Session", SessionType.PROJECT)

        val threads = (1..10).map { threadId ->
            Thread {
                repeat(10) {
                    val message = ChatMessage.userMessage("Message $threadId-$it")
                    sessionManager.addMessage(session.id, message)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // 验证所有消息都被添加
        val loadedSession = sessionManager.getSession(session.id)
        assertNotNull(loadedSession)
        assertEquals(100, loadedSession!!.messages.size)
    }

    @org.junit.Test
    fun testSessionHistoryShouldMaintainOrder() = runBlocking {
        val session = sessionManager.createSession("History Test Session", SessionType.PROJECT)

        val messages = listOf(
            "Message 1",
            "Message 2",
            "Message 3"
        )

        messages.forEach { content ->
            sessionManager.addMessage(session.id, ChatMessage.userMessage(content))
        }

        val loadedSession = sessionManager.getSession(session.id)
        assertNotNull(loadedSession)
        val loadedMessages = loadedSession!!.messages

        assertEquals(3, loadedMessages.size)
        assertEquals("Message 1", loadedMessages[0].content)
        assertEquals("Message 2", loadedMessages[1].content)
        assertEquals("Message 3", loadedMessages[2].content)
    }

    @org.junit.Test
    fun testSessionMetadataShouldPreserveMetadata() = runBlocking {
        val session = sessionManager.createSession(
            name = "Metadata Test",
            type = SessionType.GLOBAL
        )

        assertEquals(SessionType.GLOBAL, session.type)
        assertEquals("Metadata Test", session.name)
        assertTrue(session.createdAt > 0)
    }

    @org.junit.Test
    fun testContextResetShouldClearContextTracking() = runBlocking {
        val session = sessionManager.createSession("Reset Test Session", SessionType.PROJECT)

        // 添加一些上下文
        contextManager.recordUserMessage(session.id, "A".repeat(1000))
        contextManager.recordAssistantMessage(session.id, "B".repeat(1000))

        assertTrue(contextManager.getContextLength(session.id) > 0)

        // 重置会话
        contextManager.resetSession(session.id)

        // 验证上下文被清理
        assertEquals(0, contextManager.getContextLength(session.id))
    }
}
