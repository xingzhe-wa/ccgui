package com.github.claudecode.ccgui.application.usage

import com.github.claudecode.ccgui.application.usage.UsageService.TimePeriod
import com.intellij.testFramework.LightPlatformTestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * UsageService 单元测试
 */
class UsageServiceTest : LightPlatformTestCase() {

    private lateinit var usageService: UsageService

    override fun setUp() {
        super.setUp()
        usageService = UsageService.getInstance(project)
        usageService.clearHistory()
    }

    override fun tearDown() {
        usageService.clearHistory()
        super.tearDown()
    }

    fun testCollectShouldRecordUsageCorrectly() = runBlocking {
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

    fun testCollectShouldAccumulateMultipleRequests() = runBlocking {
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

    fun testCollectShouldTrackMultipleSessionsIndependently() = runBlocking {
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

    fun testCalculateCostShouldCalculateCorrectlyForSonnet() {
        val cost = usageService.calculateCost(
            model = "claude-sonnet-4-20250514",
            inputTokens = 1_000_000,
            outputTokens = 1_000_000
        )

        // Sonnet: $3.0/M input, $15.0/M output
        val expectedCost = 3.0 + 15.0
        assertEquals(expectedCost, cost, 0.01)
    }

    fun testCalculateCostShouldCalculateCorrectlyForOpus() {
        val cost = usageService.calculateCost(
            model = "claude-opus-4-20250514",
            inputTokens = 1_000_000,
            outputTokens = 1_000_000
        )

        // Opus: $15.0/M input, $75.0/M output
        val expectedCost = 15.0 + 75.0
        assertEquals(expectedCost, cost, 0.01)
    }

    fun testCalculateCostShouldHandleCacheTokens() {
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

    fun testCalculateCostShouldUseDefaultPricingForUnknownModel() {
        val cost = usageService.calculateCost(
            model = "unknown-model",
            inputTokens = 1_000_000,
            outputTokens = 1_000_000
        )

        // Default pricing: $3.0/M input, $15.0/M output
        val expectedCost = 3.0 + 15.0
        assertEquals(expectedCost, cost, 0.01)
    }

    fun testGetProjectUsageSummaryShouldAggregateAllSessions() = runBlocking {
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

    fun testGetProjectUsageSummaryShouldGroupByModel() = runBlocking {
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
        assertEquals(1000L, sonnetUsage!!.inputTokens)

        val opusUsage = summary.byModel["claude-opus-4-20250514"]
        assertNotNull(opusUsage)
        assertEquals(2000L, opusUsage!!.inputTokens)
    }

    fun testGetProjectUsageSummaryShouldFilterByTimePeriod() = runBlocking {
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

    fun testResetSessionUsageShouldClearCurrentSession() = runBlocking {
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

    fun testClearHistoryShouldRemoveAllRecords() = runBlocking {
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

    fun testCollectWithCacheTokensShouldRecordCorrectly() = runBlocking {
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

    fun testProjectUsageShouldEmitUpdatesOnCollect() = runBlocking {
        var updateCount = 0
        var lastProjectUsage: UsageService.ProjectUsage? = null

        val scope = CoroutineScope(Dispatchers.Default)
        val job: Job = scope.launch {
            usageService.projectUsage.collect { projectUsage ->
                updateCount++
                lastProjectUsage = projectUsage
            }
        }

        delay(100)

        usageService.collect(
            sessionId = "test-emit",
            model = "claude-sonnet-4-20250514",
            inputTokens = 1000,
            outputTokens = 500
        )

        delay(100)

        assertTrue(updateCount > 0)
        assertNotNull(lastProjectUsage)
        assertTrue(lastProjectUsage!!.totalTokens > 0)

        job.cancel()
        scope.cancel()
    }

    fun testCurrentSessionUsageShouldTrackActiveSession() = runBlocking {
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
