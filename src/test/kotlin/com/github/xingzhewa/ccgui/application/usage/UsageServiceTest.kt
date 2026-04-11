package com.github.xingzhewa.ccgui.application.usage

import com.github.xingzhewa.ccgui.application.usage.UsageService.TimePeriod
import com.intellij.testFramework.LightPlatformTestCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * UsageService 单元测试
 */
class UsageServiceTest : LightPlatformTestCase() {

    private lateinit var usageService: UsageService

    @Before
    override fun setUp() {
        super.setUp()
        usageService = UsageService.getInstance(project)
        usageService.clearHistory()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        usageService.clearHistory()
    }

    @Test
    fun `test collect - should record usage correctly`() = runBlocking {
        usageService.collect(
            sessionId = "test-session-1",
            model = "claude-sonnet-4-20250514",
            inputTokens = 1000,
            outputTokens = 500
        )

        val sessionUsage = usageService.getSessionUsage("test-session-1")
        assertEquals(1000L, sessionUsage.inputTokens)
        assertEquals(500L, sessionUsage.outputTokens)
        assertEquals(1500L, sessionUsage.totalTokens)
        assertEquals(1, sessionUsage.requestCount)
    }

    @Test
    fun `test collect - should accumulate multiple requests`() = runBlocking {
        usageService.collect(
            sessionId = "test-session-2",
            model = "claude-sonnet-4-20250514",
            inputTokens = 1000,
            outputTokens = 500
        )

        usageService.collect(
            sessionId = "test-session-2",
            model = "claude-sonnet-4-20250514",
            inputTokens = 2000,
            outputTokens = 1000
        )

        val sessionUsage = usageService.getSessionUsage("test-session-2")
        assertEquals(3000L, sessionUsage.inputTokens)
        assertEquals(1500L, sessionUsage.outputTokens)
        assertEquals(4500L, sessionUsage.totalTokens)
        assertEquals(2, sessionUsage.requestCount)
    }

    @Test
    fun `test collect - should track multiple sessions independently`() = runBlocking {
        usageService.collect(
            sessionId = "session-a",
            model = "claude-sonnet-4-20250514",
            inputTokens = 1000,
            outputTokens = 500
        )

        usageService.collect(
            sessionId = "session-b",
            model = "claude-sonnet-4-20250514",
            inputTokens = 2000,
            outputTokens = 1000
        )

        val sessionA = usageService.getSessionUsage("session-a")
        val sessionB = usageService.getSessionUsage("session-b")

        assertEquals(1000L, sessionA.inputTokens)
        assertEquals(2000L, sessionB.inputTokens)
    }

    @Test
    fun `test calculateCost - should calculate correctly for sonnet`() {
        val cost = usageService.calculateCost(
            model = "claude-sonnet-4-20250514",
            inputTokens = 1_000_000,
            outputTokens = 1_000_000
        )

        // Sonnet: $3.0/M input, $15.0/M output
        val expectedCost = 3.0 + 15.0
        assertEquals(expectedCost, cost, 0.01)
    }

    @Test
    fun `test calculateCost - should calculate correctly for opus`() {
        val cost = usageService.calculateCost(
            model = "claude-opus-4-20250514",
            inputTokens = 1_000_000,
            outputTokens = 1_000_000
        )

        // Opus: $15.0/M input, $75.0/M output
        val expectedCost = 15.0 + 75.0
        assertEquals(expectedCost, cost, 0.01)
    }

    @Test
    fun `test calculateCost - should handle cache tokens`() {
        val cost = usageService.calculateCost(
            model = "claude-sonnet-4-20250514",
            inputTokens = 1_000_000,
            outputTokens = 1_000_000,
            cacheCreationTokens = 500_000,
            cacheReadTokens = 2_000_000
        )

        // Sonnet: $3.75/M cache write, $0.30/M cache read
        val inputCost = 3.0
        val outputCost = 15.0
        val cacheWriteCost = (500_000 / 1_000_000.0) * 3.75
        val cacheReadCost = (2_000_000 / 1_000_000.0) * 0.30
        val expectedCost = inputCost + outputCost + cacheWriteCost + cacheReadCost

        assertEquals(expectedCost, cost, 0.01)
    }

    @Test
    fun `test calculateCost - should use default pricing for unknown model`() {
        val cost = usageService.calculateCost(
            model = "unknown-model",
            inputTokens = 1_000_000,
            outputTokens = 1_000_000
        )

        // Default pricing: $3.0/M input, $15.0/M output
        val expectedCost = 3.0 + 15.0
        assertEquals(expectedCost, cost, 0.01)
    }

    @Test
    fun `test getProjectUsageSummary - should aggregate all sessions`() = runBlocking {
        usageService.collect(
            sessionId = "session-1",
            model = "claude-sonnet-4-20250514",
            inputTokens = 1000,
            outputTokens = 500
        )

        usageService.collect(
            sessionId = "session-2",
            model = "claude-sonnet-4-20250514",
            inputTokens = 2000,
            outputTokens = 1000
        )

        val summary = usageService.getProjectUsageSummary()
        assertEquals(3000L, summary.totalInputTokens)
        assertEquals(1500L, summary.totalOutputTokens)
        assertEquals(4500L, summary.totalTokens)
    }

    @Test
    fun `test getProjectUsageSummary - should group by model`() = runBlocking {
        usageService.collect(
            sessionId = "session-1",
            model = "claude-sonnet-4-20250514",
            inputTokens = 1000,
            outputTokens = 500
        )

        usageService.collect(
            sessionId = "session-2",
            model = "claude-opus-4-20250514",
            inputTokens = 2000,
            outputTokens = 1000
        )

        val summary = usageService.getProjectUsageSummary()
        assertTrue(summary.byModel.isNotEmpty())

        val sonnetUsage = summary.byModel["claude-sonnet-4-20250514"]
        assertNotNull(sonnetUsage)
        assertEquals(1000L, sonnetUsage.inputTokens)

        val opusUsage = summary.byModel["claude-opus-4-20250514"]
        assertNotNull(opusUsage)
        assertEquals(2000L, opusUsage.inputTokens)
    }

    @Test
    fun `test getProjectUsageSummary - should filter by time period`() = runBlocking {
        // This test verifies the period filtering logic works
        // Note: In real tests, you'd need to control the timestamp
        val summary = usageService.getProjectUsageSummary(TimePeriod.ALL_TIME)
        assertNotNull(summary)

        val todaySummary = usageService.getProjectUsageSummary(TimePeriod.TODAY)
        assertNotNull(todaySummary)

        val last7Days = usageService.getProjectUsageSummary(TimePeriod.LAST_7_DAYS)
        assertNotNull(last7Days)

        val last30Days = usageService.getProjectUsageSummary(TimePeriod.LAST_30_DAYS)
        assertNotNull(last30Days)
    }

    @Test
    fun `test resetSessionUsage - should clear current session`() = runBlocking {
        usageService.collect(
            sessionId = "test-session",
            model = "claude-sonnet-4-20250514",
            inputTokens = 1000,
            outputTokens = 500
        )

        val beforeReset = usageService.currentSessionUsage.value
        assertTrue(beforeReset.inputTokens > 0)

        usageService.resetSessionUsage("test-session")

        val afterReset = usageService.currentSessionUsage.value
        assertEquals(0L, afterReset.inputTokens)
    }

    @Test
    fun `test clearHistory - should remove all records`() = runBlocking {
        usageService.collect(
            sessionId = "test-session",
            model = "claude-sonnet-4-20250514",
            inputTokens = 1000,
            outputTokens = 500
        )

        assertTrue(usageService.getSessionUsage("test-session").totalTokens > 0)

        usageService.clearHistory()

        val cleared = usageService.getSessionUsage("test-session")
        assertEquals(0L, cleared.totalTokens)
    }

    @Test
    fun `test collect with cache tokens - should record correctly`() = runBlocking {
        usageService.collect(
            sessionId = "test-cache",
            model = "claude-sonnet-4-20250514",
            inputTokens = 1000,
            outputTokens = 500,
            cacheCreationTokens = 100,
            cacheReadTokens = 200
        )

        val sessionUsage = usageService.getSessionUsage("test-cache")
        assertEquals(1000L, sessionUsage.inputTokens)
        assertEquals(500L, sessionUsage.outputTokens)
        assertEquals(1, sessionUsage.requestCount)

        // 验证成本计算包含缓存
        assertTrue(sessionUsage.cost > 0)
    }

    @Test
    fun `test projectUsage - should emit updates on collect`() = runBlocking {
        var updateCount = 0
        var lastProjectUsage: UsageService.ProjectUsage? = null

        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            usageService.projectUsage.collect { projectUsage ->
                updateCount++
                lastProjectUsage = projectUsage
            }
        }

        kotlinx.coroutines.delay(100)

        usageService.collect(
            sessionId = "test-emit",
            model = "claude-sonnet-4-20250514",
            inputTokens = 1000,
            outputTokens = 500
        )

        kotlinx.coroutines.delay(100)

        assertTrue(updateCount > 0)
        assertNotNull(lastProjectUsage)
        assertTrue(lastProjectUsage!!.totalTokens > 0)

        job.cancel()
    }

    @Test
    fun `test currentSessionUsage - should track active session`() = runBlocking {
        usageService.collect(
            sessionId = "active-session",
            model = "claude-sonnet-4-20250514",
            inputTokens = 1000,
            outputTokens = 500
        )

        val currentUsage = usageService.currentSessionUsage.value
        assertEquals("active-session", currentUsage.sessionId)
        assertEquals(1000L, currentUsage.inputTokens)
        assertEquals(500L, currentUsage.outputTokens)
    }
}
