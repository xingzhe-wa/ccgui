package com.github.xingzhewa.ccgui.application.context

import com.github.xingzhewa.ccgui.adaptation.sdk.ClaudeCodeClient
import com.github.xingzhewa.ccgui.adaptation.sdk.SdkOptions
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * 上下文管理器
 *
 * 负责监控对话上下文长度，在接近上下文窗口限制时自动触发压缩。
 *
 * Claude Code 默认上下文窗口约为 200K tokens，当上下文超过阈值（默认80%）时，
 * 自动发送 /compact 命令压缩对话历史，确保对话能够持续进行。
 *
 * @param project IntelliJ项目实例
 */
@Service(Service.Level.PROJECT)
class ContextManager(private val project: Project) {

    private val log = logger<ContextManager>()

    /** 每个会话的上下文长度追踪（字符数） */
    private val sessionContextLength = ConcurrentHashMap<String, Long>()

    /** 每个会话的最后压缩时间（防止频繁压缩） */
    private val lastCompactTime = ConcurrentHashMap<String, Long>()

    /** 配置：上下文窗口上限（tokens） */
    private val maxContextTokens = 200_000

    /** 配置：触发压缩的阈值比例 */
    private val compactThresholdRatio = 0.80

    /** 配置：最小压缩间隔（毫秒），避免过于频繁压缩 */
    private val minCompactIntervalMs = 30_000L // 30秒

    /**
     * 计算触发压缩的字符数阈值
     * 保守估计：1 token ≈ 3.5 字符（中文约2字符/token，英文约4字符/token）
     */
    private val charThreshold: Long
        get() = (maxContextTokens * compactThresholdRatio * 3.5).toLong()

    /**
     * 是否应该触发上下文压缩
     *
     * @param sessionId 会话ID
     * @return true 如果上下文长度超过阈值
     */
    fun shouldCompact(sessionId: String): Boolean {
        val currentLength = sessionContextLength[sessionId] ?: 0L
        val result = currentLength > charThreshold

        if (result) {
            // 检查是否在最小压缩间隔内
            val lastCompact = lastCompactTime[sessionId] ?: 0L
            val now = System.currentTimeMillis()
            if (now - lastCompact < minCompactIntervalMs) {
                log.debug("ContextManager: compact skipped for session $sessionId (within cooldown)")
                return false
            }
        }

        return result
    }

    /**
     * 执行上下文压缩
     *
     * 发送 /compact 命令到 Claude CLI，等待压缩完成。
     * 压缩成功后重置上下文长度追踪。
     *
     * @param sessionId 会话ID
     * @return true 如果压缩成功
     */
    suspend fun compact(sessionId: String): Boolean {
        val lastCompact = lastCompactTime[sessionId] ?: 0L
        val now = System.currentTimeMillis()
        if (now - lastCompact < minCompactIntervalMs) {
            log.debug("ContextManager: compact skipped for session $sessionId (within cooldown)")
            return false
        }

        log.info("ContextManager: Starting context compaction for session $sessionId")
        lastCompactTime[sessionId] = now

        return try {
            val claudeClient = ClaudeCodeClient.getInstance(project)

            // 发送 /compact 命令
            val result = withTimeoutOrNull(120_000L) { // 2分钟超时
                claudeClient.sendMessage(
                    prompt = "/compact",
                    options = SdkOptions(maxTurns = 1, allowedTools = emptyList())
                )
            }

            if (result != null && result.isSuccess) {
                // 压缩成功后，重置上下文长度追踪
                // CLI 的 /compact 会压缩历史，但不会告诉我们压缩后的大小
                // 这里做一个粗略的重置：假设压缩后剩余约 40% 的内容
                val currentLength = sessionContextLength[sessionId] ?: 0L
                val estimatedRemaining = (currentLength * 0.4).toLong()
                sessionContextLength[sessionId] = estimatedRemaining

                log.info("ContextManager: Context compaction succeeded for session $sessionId, estimated remaining chars: $estimatedRemaining")
                true
            } else {
                log.warn("ContextManager: Context compaction failed for session $sessionId: ${result?.exceptionOrNull()?.message}")
                // 压缩失败，不重置长度，但也不阻塞发送
                false
            }
        } catch (e: Exception) {
            log.warn("ContextManager: Context compaction error for session $sessionId: ${e.message}")
            false
        }
    }

    /**
     * 记录发送的消息内容长度
     *
     * @param sessionId 会话ID
     * @param content 消息内容（用户输入）
     */
    fun recordUserMessage(sessionId: String, content: String) {
        val currentLength = sessionContextLength.compute(sessionId) { _, existing ->
            (existing ?: 0L) + content.length
        }
        log.debug("ContextManager: session=$sessionId, userMsgLen=${content.length}, totalChars=$currentLength")
    }

    /**
     * 记录 AI 响应内容长度
     *
     * @param sessionId 会话ID
     * @param content AI 响应内容
     */
    fun recordAssistantMessage(sessionId: String, content: String) {
        val currentLength = sessionContextLength.compute(sessionId) { _, existing ->
            (existing ?: 0L) + content.length
        }
        log.debug("ContextManager: session=$sessionId, assistantMsgLen=${content.length}, totalChars=$currentLength")
    }

    /**
     * 获取当前会话的上下文长度（字符数）
     *
     * @param sessionId 会话ID
     * @return 当前字符数
     */
    fun getContextLength(sessionId: String): Long = sessionContextLength[sessionId] ?: 0L

    /**
     * 获取当前会话的预估 token 数
     *
     * @param sessionId 会话ID
     * @return 预估 token 数
     */
    fun getEstimatedTokens(sessionId: String): Long {
        val chars = sessionContextLength[sessionId] ?: 0L
        return (chars / 3.5).toLong()
    }

    /**
     * 获取上下文使用率
     *
     * @param sessionId 会话ID
     * @return 0.0-1.0 的使用率
     */
    fun getUsageRatio(sessionId: String): Double {
        val chars = sessionContextLength[sessionId] ?: 0L
        return (chars.toDouble() / charThreshold.toDouble()).coerceIn(0.0, 1.0)
    }

    /**
     * 重置会话的上下文追踪
     *
     * 在会话被清空或重置时调用
     *
     * @param sessionId 会话ID
     */
    fun resetSession(sessionId: String) {
        sessionContextLength.remove(sessionId)
        lastCompactTime.remove(sessionId)
        log.debug("ContextManager: Session context reset for $sessionId")
    }

    /**
     * 会话被删除时调用
     *
     * @param sessionId 会话ID
     */
    fun removeSession(sessionId: String) {
        sessionContextLength.remove(sessionId)
        lastCompactTime.remove(sessionId)
    }

    companion object {
        fun getInstance(project: Project): ContextManager =
            project.getService(ContextManager::class.java)
    }
}