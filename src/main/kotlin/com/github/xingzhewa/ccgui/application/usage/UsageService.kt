package com.github.xingzhewa.ccgui.application.usage

import com.github.xingzhewa.ccgui.infrastructure.storage.StorageService
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * 用量统计服务
 *
 * 追踪和统计 API 调用的 Token 消耗，支持多维度统计、成本估算和预算管理
 */
class UsageService(private val project: Project) {

    private val log = logger<UsageService>()
    private val storageService = StorageService.getInstance(project)
    private val scope = CoroutineScope(Dispatchers.IO)

    /** 用量记录 */
    private val records = ConcurrentHashMap<String, UsageRecord>()

    /** 当前会话用量 */
    private val _currentSessionUsage = MutableStateFlow(SessionUsage())
    val currentSessionUsage: StateFlow<SessionUsage> = _currentSessionUsage.asStateFlow()

    /** 项目总用量 */
    private val _projectUsage = MutableStateFlow(ProjectUsage())
    val projectUsage: StateFlow<ProjectUsage> = _projectUsage.asStateFlow()

    init {
        loadHistoricalUsage()
        log.info("UsageService initialized")
    }

    /**
     * 用量记录
     */
    data class UsageRecord(
        val id: String,
        val sessionId: String,
        val projectId: String,
        val timestamp: Instant,
        val model: String,
        val inputTokens: Int,
        val outputTokens: Int,
        val cacheCreationTokens: Int? = null,
        val cacheReadTokens: Int? = null,
        val cost: Double,
        val requestCount: Int = 1
    )

    /**
     * 会话用量
     */
    data class SessionUsage(
        val sessionId: String = "",
        val inputTokens: Long = 0,
        val outputTokens: Long = 0,
        val totalTokens: Long = 0,
        val cost: Double = 0.0,
        val requestCount: Int = 0
    )

    /**
     * 项目用量
     */
    data class ProjectUsage(
        val projectId: String = "",
        val totalInputTokens: Long = 0,
        val totalOutputTokens: Long = 0,
        val totalTokens: Long = 0,
        val totalCost: Double = 0.0,
        val byModel: Map<String, ModelUsage> = emptyMap(),
        val dailyUsage: Map<String, DailyUsage> = emptyMap()
    )

    /**
     * 模型用量
     */
    data class ModelUsage(
        val modelId: String,
        val inputTokens: Long,
        val outputTokens: Long,
        val cost: Double,
        val requestCount: Int
    )

    /**
     * 每日用量
     */
    data class DailyUsage(
        val date: String,
        val inputTokens: Long,
        val outputTokens: Long,
        val cost: Double,
        val requestCount: Int
    )

    /**
     * 时间周期
     */
    enum class TimePeriod {
        TODAY, LAST_7_DAYS, LAST_30_DAYS, ALL_TIME
    }

    /**
     * 收集用量
     */
    fun collect(
        sessionId: String,
        model: String,
        inputTokens: Int,
        outputTokens: Int,
        cacheCreationTokens: Int? = null,
        cacheReadTokens: Int? = null
    ) {
        val cost = calculateCost(model, inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens)

        val record = UsageRecord(
            id = java.util.UUID.randomUUID().toString(),
            sessionId = sessionId,
            projectId = project.name,
            timestamp = Instant.now(),
            model = model,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheCreationTokens = cacheCreationTokens,
            cacheReadTokens = cacheReadTokens,
            cost = cost
        )

        records[record.id] = record
        updateCurrentSessionUsage(sessionId, record)
        updateProjectUsage()

        // 异步保存
        scope.launch {
            storageService.saveUsageRecord(record)
        }

        log.debug("Collected usage: $inputTokens in, $outputTokens out, cost: $$cost")
    }

    /**
     * 计算成本
     */
    fun calculateCost(
        model: String,
        inputTokens: Int,
        outputTokens: Int,
        cacheCreationTokens: Int? = null,
        cacheReadTokens: Int? = null
    ): Double {
        val pricing = getPricing(model)

        val inputCost = (inputTokens / 1_000_000.0) * pricing.inputPrice
        val outputCost = (outputTokens / 1_000_000.0) * pricing.outputPrice
        val cacheWriteCost = ((cacheCreationTokens ?: 0) / 1_000_000.0) * pricing.cacheWritePrice
        val cacheReadCost = ((cacheReadTokens ?: 0) / 1_000_000.0) * pricing.cacheReadPrice

        return inputCost + outputCost + cacheWriteCost + cacheReadCost
    }

    /**
     * 模型定价
     */
    private data class ModelPricing(
        val inputPrice: Double,
        val outputPrice: Double,
        val cacheWritePrice: Double,
        val cacheReadPrice: Double
    )

    /**
     * 获取模型定价
     */
    private fun getPricing(model: String): ModelPricing {
        return pricingCache[model] ?: defaultPricing
    }

    private val defaultPricing = ModelPricing(
        inputPrice = 3.0,
        outputPrice = 15.0,
        cacheWritePrice = 3.75,
        cacheReadPrice = 0.30
    )

    private val pricingCache = mapOf(
        "claude-sonnet-4-20250514" to ModelPricing(
            inputPrice = 3.0,
            outputPrice = 15.0,
            cacheWritePrice = 3.75,
            cacheReadPrice = 0.30
        ),
        "claude-opus-4-20250514" to ModelPricing(
            inputPrice = 15.0,
            outputPrice = 75.0,
            cacheWritePrice = 18.75,
            cacheReadPrice = 1.50
        ),
        "claude-3-5-sonnet-20241022" to ModelPricing(
            inputPrice = 3.0,
            outputPrice = 15.0,
            cacheWritePrice = 3.75,
            cacheReadPrice = 0.30
        )
    )

    /**
     * 获取会话用量
     */
    fun getSessionUsage(sessionId: String): SessionUsage {
        val sessionRecords = records.values.filter { it.sessionId == sessionId }
        return SessionUsage(
            sessionId = sessionId,
            inputTokens = sessionRecords.sumOf { it.inputTokens.toLong() },
            outputTokens = sessionRecords.sumOf { it.outputTokens.toLong() },
            totalTokens = sessionRecords.sumOf { (it.inputTokens + it.outputTokens).toLong() },
            cost = sessionRecords.sumOf { it.cost },
            requestCount = sessionRecords.size
        )
    }

    /**
     * 获取项目用量摘要
     */
    fun getProjectUsageSummary(period: TimePeriod = TimePeriod.ALL_TIME): ProjectUsage {
        val startTime = when (period) {
            TimePeriod.TODAY -> Instant.now().truncatedTo(ChronoUnit.DAYS)
            TimePeriod.LAST_7_DAYS -> Instant.now().minus(7, ChronoUnit.DAYS)
            TimePeriod.LAST_30_DAYS -> Instant.now().minus(30, ChronoUnit.DAYS)
            TimePeriod.ALL_TIME -> Instant.EPOCH
        }

        val filteredRecords = records.values.filter { it.timestamp >= startTime }

        val byModel = filteredRecords
            .groupBy { it.model }
            .mapValues { (_, recs) ->
                ModelUsage(
                    modelId = recs.first().model,
                    inputTokens = recs.sumOf { it.inputTokens.toLong() },
                    outputTokens = recs.sumOf { it.outputTokens.toLong() },
                    cost = recs.sumOf { it.cost },
                    requestCount = recs.size
                )
            }

        val dailyUsage = filteredRecords
            .groupBy { it.timestamp.toString().substringBefore("T") }
            .mapValues { (_, recs) ->
                DailyUsage(
                    date = recs.first().timestamp.toString().substringBefore("T"),
                    inputTokens = recs.sumOf { it.inputTokens.toLong() },
                    outputTokens = recs.sumOf { it.outputTokens.toLong() },
                    cost = recs.sumOf { it.cost },
                    requestCount = recs.size
                )
            }

        return ProjectUsage(
            projectId = project.name,
            totalInputTokens = filteredRecords.sumOf { it.inputTokens.toLong() },
            totalOutputTokens = filteredRecords.sumOf { it.outputTokens.toLong() },
            totalTokens = filteredRecords.sumOf { (it.inputTokens + it.outputTokens).toLong() },
            totalCost = filteredRecords.sumOf { it.cost },
            byModel = byModel,
            dailyUsage = dailyUsage
        )
    }

    /**
     * 重置会话用量
     */
    fun resetSessionUsage(sessionId: String) {
        if (_currentSessionUsage.value.sessionId == sessionId) {
            _currentSessionUsage.value = SessionUsage()
        }
    }

    /**
     * 清空历史数据
     */
    fun clearHistory() {
        records.clear()
        _projectUsage.value = ProjectUsage()
        _currentSessionUsage.value = SessionUsage()
    }

    private fun loadHistoricalUsage() {
        // 从存储加载历史数据
        scope.launch {
            try {
                val historical = storageService.loadUsageRecords(project.name)
                historical.forEach { record ->
                    records[record.id] = record
                }
                updateProjectUsage()
                log.info("Loaded ${records.size} historical usage records")
            } catch (e: Exception) {
                log.warn("Failed to load historical usage: ${e.message}")
            }
        }
    }

    private fun updateCurrentSessionUsage(sessionId: String, record: UsageRecord) {
        val current = _currentSessionUsage.value
        if (current.sessionId != sessionId) {
            _currentSessionUsage.value = SessionUsage(
                sessionId = sessionId,
                inputTokens = record.inputTokens.toLong(),
                outputTokens = record.outputTokens.toLong(),
                totalTokens = (record.inputTokens + record.outputTokens).toLong(),
                cost = record.cost,
                requestCount = 1
            )
        } else {
            _currentSessionUsage.value = current.copy(
                inputTokens = current.inputTokens + record.inputTokens,
                outputTokens = current.outputTokens + record.outputTokens,
                totalTokens = current.totalTokens + record.inputTokens + record.outputTokens,
                cost = current.cost + record.cost,
                requestCount = current.requestCount + 1
            )
        }
    }

    private fun updateProjectUsage() {
        _projectUsage.value = getProjectUsageSummary()
    }

    companion object {
        fun getInstance(project: Project): UsageService =
            project.getService(UsageService::class.java)
    }
}
