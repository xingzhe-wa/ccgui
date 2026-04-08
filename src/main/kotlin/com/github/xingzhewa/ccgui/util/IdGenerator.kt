package com.github.xingzhewa.ccgui.util

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * ID 生成器
 *
 * 提供多种 ID 生成策略：
 * - UUID: 全球唯一标识符
 * - 自增 ID: 数字序列
 * - 时间戳 ID: 基于时间的唯一标识
 * - 短 ID: URL 安全的短字符串
 *
 * 扩展埋点:
 *   - 后续可添加分布式 ID 生成策略
 *   - 后续可添加自定义前缀支持
 */
object IdGenerator {

    private val counter = AtomicLong(0)
    private val startupTime = System.currentTimeMillis()

    // ==================== UUID 生成 ====================

    /**
     * 生成标准 UUID
     *
     * @return UUID 字符串（如 "550e8400-e29b-41d4-a716-446655440000"）
     */
    fun uuid(): String = UUID.randomUUID().toString()

    /**
     * 生成无连字符的 UUID
     *
     * @return UUID 字符串（如 "550e8400e29b41d4a716446655440000"）
     */
    fun uuidWithoutDash(): String = uuid().replace("-", "")

    /**
     * 生成指定前缀的 UUID
     *
     * @param prefix 前缀（如 "msg_"）
     * @return 带前缀的 UUID
     */
    fun prefixedUuid(prefix: String): String = "$prefix${uuid()}"

    // ==================== 自增 ID 生成 ====================

    /**
     * 生成自增数字 ID
     *
     * @return 自增数字
     */
    fun nextId(): Long = counter.incrementAndGet()

    /**
     * 生成指定前缀的自增 ID
     *
     * @param prefix 前缀
     * @return 带前缀的自增 ID（如 "session_123"）
     */
    fun prefixedId(prefix: String): String = "$prefix${counter.incrementAndGet()}"

    /**
     * 重置计数器（仅用于测试）
     */
    fun resetCounter() {
        counter.set(0)
    }

    // ==================== 时间戳 ID 生成 ====================

    /**
     * 生成基于时间戳的 ID
     *
     * @return 时间戳 ID（纳秒级）
     */
    fun timestampId(): Long = System.nanoTime()

    /**
     * 生成基于时间的唯一 ID
     *
     * 格式: {timestamp}_{random}
     *
     * @return 时间唯一 ID
     */
    fun uniqueTimeId(): String = "${System.currentTimeMillis()}_${uuidWithoutDash().take(8)}"

    /**
     * 生成会话 ID
     *
     * @return 会话 ID（格式: sess_{uuid}）
     */
    fun sessionId(): String = prefixedUuid("sess_")

    /**
     * 生成消息 ID
     *
     * @return 消息 ID（格式: msg_{timestamp}）
     */
    fun messageId(): String = "msg_${System.currentTimeMillis()}_${uuidWithoutDash().take(8)}"

    /**
     * 生成技能 ID
     *
     * @return 技能 ID（格式: skill_{uuid}）
     */
    fun skillId(): String = prefixedUuid("skill_")

    /**
     * 生成 Agent ID
     *
     * @return Agent ID（格式: agent_{uuid}）
     */
    fun agentId(): String = prefixedUuid("agent_")

    /**
     * 生成 MCP 服务器 ID
     *
     * @return MCP 服务器 ID（格式: mcp_{uuid}）
     */
    fun mcpServerId(): String = prefixedUuid("mcp_")

    /**
     * 生成任务 ID
     *
     * @return 任务 ID（格式: task_{timestamp}）
     */
    fun taskId(): String = "task_${System.currentTimeMillis()}_${uuidWithoutDash().take(8)}"

    // ==================== 短 ID 生成 ====================

    /**
     * 可 URL 安全的字符集
     */
    private const val URL_SAFE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"

    /**
     * 生成指定长度的短 ID
     *
     * @param length 长度（默认 8）
     * @return 短 ID
     */
    fun shortId(length: Int = 8): String {
        val sb = StringBuilder(length)
        val random = java.security.SecureRandom()
        for (i in 0 until length) {
            sb.append(URL_SAFE_CHARS[random.nextInt(URL_SAFE_CHARS.length)])
        }
        return sb.toString()
    }

    /**
     * 生成带前缀的短 ID
     *
     * @param prefix 前缀
     * @param length ID 长度
     * @return 带前缀的短 ID
     */
    fun prefixedShortId(prefix: String, length: Int = 8): String {
        return "$prefix${shortId(length)}"
    }

    // ==================== 查询 ID 生成 ====================

    private val queryCounter = AtomicLong(0)

    /**
     * 生成查询 ID
     *
     * @return 查询 ID（格式: q_{递增数字}）
     */
    fun queryId(): Long = queryCounter.incrementAndGet()

    // ==================== 工具方法 ====================

    /**
     * 验证 UUID 格式
     *
     * @param id 待验证的字符串
     * @return true 表示是有效的 UUID
     */
    fun isValidUuid(id: String): Boolean {
        return try {
            UUID.fromString(id)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * 从会话 ID 中提取会话标识部分
     *
     * @param sessionId 完整会话 ID
     * @return 提取后的标识
     */
    fun extractSessionIdentifier(sessionId: String): String {
        return sessionId.removePrefix("sess_")
    }
}
