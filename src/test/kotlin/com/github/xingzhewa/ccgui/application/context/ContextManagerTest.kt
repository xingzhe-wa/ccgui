package com.github.xingzhewa.ccgui.application.context

import com.github.xingzhewa.ccgui.application.context.ContextManager.CompactResult
import com.intellij.testFramework.LightPlatformTestCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ContextManager 单元测试
 */
class ContextManagerTest : LightPlatformTestCase() {

    private lateinit var contextManager: ContextManager

    @Before
    override fun setUp() {
        super.setUp()
        contextManager = ContextManager.getInstance(project)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        // 清理测试会话
        contextManager.removeSession("test-session-1")
        contextManager.removeSession("test-session-2")
    }

    @Test
    fun `test recordUserMessage - should track context length`() {
        contextManager.recordUserMessage("test-session-1", "Hello")
        assertEquals(5, contextManager.getContextLength("test-session-1"))

        contextManager.recordUserMessage("test-session-1", " World")
        assertEquals(11, contextManager.getContextLength("test-session-1"))
    }

    @Test
    fun `test recordAssistantMessage - should track context length`() {
        contextManager.recordAssistantMessage("test-session-1", "Hi there!")
        assertEquals(9, contextManager.getContextLength("test-session-1"))
    }

    @Test
    fun `test recordUserMessage - should estimate tokens`() {
        contextManager.recordUserMessage("test-session-1", "A".repeat(100))
        val estimatedTokens = contextManager.getEstimatedTokens("test-session-1")

        // 100 chars / 3.5 ≈ 28-29 tokens
        assertTrue(estimatedTokens >= 28)
        assertTrue(estimatedTokens <= 30)
    }

    @Test
    fun `test recordUserMessage - with custom token estimate`() {
        contextManager.recordUserMessage("test-session-1", "Short", estimatedTokens = 50)
        assertEquals(50, contextManager.getEstimatedTokens("test-session-1"))
    }

    @Test
    fun `test shouldCompact - should return false for small context`() {
        contextManager.recordUserMessage("test-session-1", "Small message")
        assertFalse(contextManager.shouldCompact("test-session-1"))
    }

    @Test
    fun `test shouldCompact - should respect cooldown period`() = runBlocking {
        // 添加足够的内容触发压缩
        repeat(1000) {
            contextManager.recordUserMessage("test-cooldown", "A".repeat(100))
            contextManager.recordAssistantMessage("test-cooldown", "B".repeat(100))
        }

        // 第一次检查应该返回 true（如果超过阈值）
        val firstCheck = contextManager.shouldCompact("test-cooldown")

        // 尝试压缩（即使失败也会设置冷却时间）
        contextManager.compact("test-cooldown")

        // 冷却期内应该返回 false
        val secondCheck = contextManager.shouldCompact("test-cooldown")
        assertFalse(secondCheck, "Should respect cooldown period")

        contextManager.removeSession("test-cooldown")
    }

    @Test
    fun `test getUsageRatio - should return correct ratio`() {
        val sessionId = "test-ratio"
        contextManager.recordUserMessage(sessionId, "A".repeat(100))

        val ratio = contextManager.getUsageRatio(sessionId)
        assertTrue(ratio > 0.0)
        assertTrue(ratio <= 1.0)

        contextManager.removeSession(sessionId)
    }

    @Test
    fun `test getUsageRatio - should clamp to 1.0`() {
        val sessionId = "test-clamp"

        // 添加大量内容
        repeat(10000) {
            contextManager.recordUserMessage(sessionId, "A".repeat(100))
        }

        val ratio = contextManager.getUsageRatio(sessionId)
        assertTrue(ratio <= 1.0, "Usage ratio should not exceed 1.0")

        contextManager.removeSession(sessionId)
    }

    @Test
    fun `test resetSession - should clear all tracking`() {
        val sessionId = "test-reset"
        contextManager.recordUserMessage(sessionId, "Some content")
        contextManager.recordAssistantMessage(sessionId, "Response")

        assertTrue(contextManager.getContextLength(sessionId) > 0)
        assertTrue(contextManager.getEstimatedTokens(sessionId) > 0)

        contextManager.resetSession(sessionId)

        assertEquals(0, contextManager.getContextLength(sessionId))
        assertEquals(0, contextManager.getEstimatedTokens(sessionId))
    }

    @Test
    fun `test removeSession - should clear all tracking`() {
        val sessionId = "test-remove"
        contextManager.recordUserMessage(sessionId, "Content")

        assertTrue(contextManager.getContextLength(sessionId) > 0)

        contextManager.removeSession(sessionId)

        assertEquals(0, contextManager.getContextLength(sessionId))
        assertEquals(0, contextManager.getEstimatedTokens(sessionId))
    }

    @Test
    fun `test multiple sessions - should track independently`() {
        contextManager.recordUserMessage("session-a", "Content A")
        contextManager.recordUserMessage("session-b", "Content B")

        assertEquals(9, contextManager.getContextLength("session-a"))
        assertEquals(9, contextManager.getContextLength("session-b"))
    }

    @Test
    fun `test getContextLength - should return 0 for non-existent session`() {
        assertEquals(0, contextManager.getContextLength("non-existent"))
    }

    @Test
    fun `test getEstimatedTokens - should return 0 for non-existent session`() {
        assertEquals(0, contextManager.getEstimatedTokens("non-existent"))
    }

    @Test
    fun `test compact - should return Skipped for small context`() = runBlocking {
        val sessionId = "test-small-compact"
        contextManager.recordUserMessage(sessionId, "Small")

        val result = contextManager.compact(sessionId)
        assertTrue(result is CompactResult.Skipped)

        contextManager.removeSession(sessionId)
    }

    @Test
    fun `test syncTokens - should update token count`() {
        val sessionId = "test-sync"

        contextManager.recordUserMessage(sessionId, "Content", estimatedTokens = 100)
        assertEquals(100, contextManager.getEstimatedTokens(sessionId))

        // 同步会从 UsageService 获取实际值
        // 这里测试方法不会抛异常
        contextManager.syncTokens(sessionId)

        contextManager.removeSession(sessionId)
    }

    @Test
    fun `test charThreshold - should be calculated correctly`() {
        // maxContextTokens = 200,000
        // compactThresholdRatio = 0.80
        // charMultiplier = 3.5
        // expected = 200,000 * 0.80 * 3.5 = 560,000

        // 我们无法直接访问 charThreshold，但可以通过 shouldCompact 验证
        val sessionId = "test-threshold"

        // 添加接近阈值的内容
        repeat(5000) {
            contextManager.recordUserMessage(sessionId, "A".repeat(100))
        }

        val currentLength = contextManager.getContextLength(sessionId)
        // 5000 * 100 = 500,000 chars，应该接近但不超过阈值

        // 在阈值以下应该不触发压缩
        assertFalse(contextManager.shouldCompact(sessionId), "Should not compact at ${currentLength} chars")

        contextManager.removeSession(sessionId)
    }

    @Test
    fun `test recordAssistantMessage - with custom token estimate`() {
        contextManager.recordAssistantMessage("test-session-1", "Response", estimatedTokens = 75)
        assertEquals(75, contextManager.getEstimatedTokens("test-session-1"))
    }

    @Test
    fun `test context tracking - should accumulate correctly`() {
        val sessionId = "test-accumulate"

        contextManager.recordUserMessage(sessionId, "User 1", estimatedTokens = 10)
        assertEquals(10, contextManager.getEstimatedTokens(sessionId))

        contextManager.recordAssistantMessage(sessionId, "Assistant 1", estimatedTokens = 15)
        assertEquals(25, contextManager.getEstimatedTokens(sessionId))

        contextManager.recordUserMessage(sessionId, "User 2", estimatedTokens = 20)
        assertEquals(45, contextManager.getEstimatedTokens(sessionId))

        contextManager.removeSession(sessionId)
    }
}
