package com.github.claudecode.ccgui.application.usage

import com.github.claudecode.ccgui.application.config.ConfigService
import com.github.claudecode.ccgui.model.config.AppConfig
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.project.Project
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 预算告警类型
 *
 * 跟踪日度和月度用量的关键阈值告警
 */
enum class BudgetAlert {
    /** 日用量达到 80% */
    DAILY_80_PERCENT,
    /** 日用量达到 90% */
    DAILY_90_PERCENT,
    /** 月用量达到 80% */
    MONTHLY_80_PERCENT,
    /** 月用量达到 90% */
    MONTHLY_90_PERCENT,
    /** 月用量达到 100% */
    MONTHLY_100_PERCENT
}

/**
 * 预算状态数据类
 *
 * 包含当前日度和月度用量、限制及百分比信息
 */
data class BudgetStatus(
    /** 当前日用量 (美元) */
    val dailyUsed: Double,
    /** 每日限制 (美元) */
    val dailyLimit: Double,
    /** 日用量百分比 (0.0 - 1.0+) */
    val dailyPercentage: Double,
    /** 当前月用量 (美元) */
    val monthlyUsed: Double,
    /** 每月限制 (美元) */
    val monthlyLimit: Double,
    /** 月用量百分比 (0.0 - 1.0+) */
    val monthlyPercentage: Double,
    /** 当前触发的告警列表 */
    val alerts: List<BudgetAlert>
)

/**
 * 预算管理器
 *
 * 负责监控 API 使用量，检查是否超出预算阈值，并触发相应的告警
 *
 * @param project 项目实例，用于获取 ConfigService 和 UsageService
 */
class BudgetManager(private val project: Project) {

    private val log = logger<BudgetManager>()

    /** 配置管理器 */
    private val configManager: ConfigService by lazy { ConfigService.getInstance(project) }

    /** 用量服务 */
    private val usageService: UsageService by lazy { UsageService.getInstance(project) }

    /** 当前已触发的告警列表 */
    private val alerts = mutableListOf<BudgetAlert>()

    /** 缓存的预算状态 */
    private var cachedBudgetStatus: BudgetStatus? = null

    init {
        log.info("BudgetManager initialized")
    }

    /**
     * 获取应用配置
     */
    private fun getAppConfig(): AppConfig = configManager.getAppConfig()

    /**
     * 获取当前预算状态
     *
     * @return BudgetStatus 包含日度和月度用量信息及告警
     */
    fun getBudgetStatus(): BudgetStatus {
        return cachedBudgetStatus ?: checkBudget().also { cachedBudgetStatus = it }
    }

    /**
     * 检查预算使用情况
     *
     * 计算当前日度和月度用量，与配置中的限制进行比较，
     * 并检查是否需要触发新的告警
     *
     * @return BudgetStatus 包含完整的预算状态信息
     */
    fun checkBudget(): BudgetStatus {
        val config = getAppConfig()
        val dailyLimit = config.dailyBudget
        val monthlyLimit = config.monthlyBudget

        val dailyUsage = getDailyUsage()
        val monthlyUsage = getMonthlyUsage()

        val newAlerts = checkAlerts(dailyUsage, dailyLimit, monthlyUsage, monthlyLimit)

        // 更新告警列表
        updateAlerts(newAlerts)

        val status = BudgetStatus(
            dailyUsed = dailyUsage,
            dailyLimit = dailyLimit,
            dailyPercentage = if (dailyLimit > 0) dailyUsage / dailyLimit else 0.0,
            monthlyUsed = monthlyUsage,
            monthlyLimit = monthlyLimit,
            monthlyPercentage = if (monthlyLimit > 0) monthlyUsage / monthlyLimit else 0.0,
            alerts = alerts.toList()
        )

        cachedBudgetStatus = status

        log.debug("Budget check: daily=${String.format("%.2f", dailyUsage)}/${dailyLimit}, " +
                "monthly=${String.format("%.2f", monthlyUsage)}/${monthlyLimit}, " +
                "alerts=${alerts.size}")

        return status
    }

    /**
     * 获取日用量
     *
     * 计算从今天开始的所有用量成本
     *
     * @return 当日总用量 (美元)
     */
    private fun getDailyUsage(): Double {
        val today = LocalDate.now()

        return usageService.getProjectUsageSummary(UsageService.TimePeriod.TODAY)
            .dailyUsage
            .filter { (_, dailyUsage) ->
                try {
                    val date = LocalDate.parse(dailyUsage.date)
                    !date.isBefore(today)
                } catch (e: Exception) {
                    false
                }
            }
            .values
            .sumOf { it.cost }
    }

    /**
     * 获取月用量
     *
     * 计算从当月开始的所有用量成本
     *
     * @return 当月总用量 (美元)
     */
    private fun getMonthlyUsage(): Double {
        val now = Instant.now()
        val startOfMonth = now.atZone(ZoneId.systemDefault())
            .withDayOfMonth(1)
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant()

        val projectUsage = usageService.getProjectUsageSummary(UsageService.TimePeriod.ALL_TIME)

        return projectUsage.dailyUsage
            .filter { (dateStr, _) ->
                try {
                    val date = LocalDate.parse(dateStr)
                    val startOfMonthDate = startOfMonth.atZone(ZoneId.systemDefault()).toLocalDate()
                    !date.isBefore(startOfMonthDate)
                } catch (e: Exception) {
                    false
                }
            }
            .values
            .sumOf { it.cost }
    }

    /**
     * 检查告警
     *
     * 根据当前用量和限制判断需要触发哪些告警
     *
     * @param dailyUsage 当前日用量
     * @param dailyLimit 每日限制
     * @param monthlyUsage 当前月用量
     * @param monthlyLimit 每月限制
     * @return 需要触发的告警列表
     */
    private fun checkAlerts(
        dailyUsage: Double,
        dailyLimit: Double,
        monthlyUsage: Double,
        monthlyLimit: Double
    ): List<BudgetAlert> {
        val newAlerts = mutableListOf<BudgetAlert>()

        // 日用量告警检查 (按优先级，90% 优先于 80%)
        if (dailyLimit > 0) {
            val dailyPercentage = dailyUsage / dailyLimit
            when {
                dailyPercentage >= 0.9 -> {
                    newAlerts.add(BudgetAlert.DAILY_90_PERCENT)
                    newAlerts.add(BudgetAlert.DAILY_80_PERCENT)  // 90% 包含 80%
                }
                dailyPercentage >= 0.8 -> {
                    newAlerts.add(BudgetAlert.DAILY_80_PERCENT)
                }
            }
        }

        // 月用量告警检查 (按优先级，100% > 90% > 80%)
        if (monthlyLimit > 0) {
            val monthlyPercentage = monthlyUsage / monthlyLimit
            when {
                monthlyPercentage >= 1.0 -> {
                    newAlerts.add(BudgetAlert.MONTHLY_100_PERCENT)
                    newAlerts.add(BudgetAlert.MONTHLY_90_PERCENT)  // 100% 包含 90%
                    newAlerts.add(BudgetAlert.MONTHLY_80_PERCENT)  // 100% 包含 80%
                }
                monthlyPercentage >= 0.9 -> {
                    newAlerts.add(BudgetAlert.MONTHLY_90_PERCENT)
                    newAlerts.add(BudgetAlert.MONTHLY_80_PERCENT)  // 90% 包含 80%
                }
                monthlyPercentage >= 0.8 -> {
                    newAlerts.add(BudgetAlert.MONTHLY_80_PERCENT)
                }
            }
        }

        return newAlerts
    }

    /**
     * 更新告警列表
     *
     * 仅添加新的告警，不移除已触发的告警（告警会持续显示直到用量降低）
     *
     * @param newAlerts 新检查出的告警列表
     */
    private fun updateAlerts(newAlerts: List<BudgetAlert>) {
        // 添加新告警但不去重（同一告警可以重复触发）
        for (alert in newAlerts) {
            if (!alerts.contains(alert)) {
                alerts.add(alert)
                log.info("Budget alert triggered: $alert")
            }
        }
    }

    /**
     * 设置预算限制
     *
     * 更新每日和每月的预算限制
     *
     * @param dailyBudget 新的每日限制 (美元)
     * @param monthlyBudget 新的每月限制 (美元)
     */
    fun setBudgetLimits(dailyBudget: Double, monthlyBudget: Double) {
        val currentConfig = getAppConfig()
        val newConfig = currentConfig.copy(
            dailyBudget = dailyBudget,
            monthlyBudget = monthlyBudget
        )
        configManager.saveAppConfig(newConfig)

        // 清除缓存，强制重新计算
        cachedBudgetStatus = null

        log.info("Budget limits updated: daily=${dailyBudget}, monthly=${monthlyBudget}")
    }

    /**
     * 清除所有告警
     *
     * 手动清除所有已触发的告警，通常在用户确认后调用
     */
    fun clearAlerts() {
        val clearedAlerts = alerts.toList()
        alerts.clear()
        cachedBudgetStatus = null

        log.info("Cleared ${clearedAlerts.size} budget alerts: $clearedAlerts")
    }

    /**
     * 清除缓存
     *
     * 强制重新计算预算状态，下次调用时会重新获取用量数据
     */
    fun invalidateCache() {
        cachedBudgetStatus = null
    }

    companion object {
        /**
         * 获取 BudgetManager 实例
         *
         * @param project 项目实例
         * @return BudgetManager 实例
         */
        fun getInstance(project: Project): BudgetManager =
            project.getService(BudgetManager::class.java)
    }
}