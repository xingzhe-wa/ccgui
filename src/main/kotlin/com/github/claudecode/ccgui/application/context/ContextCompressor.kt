package com.github.claudecode.ccgui.application.context

import com.github.claudecode.ccgui.adaptation.sdk.ClaudeCodeClient
import com.github.claudecode.ccgui.adaptation.sdk.SdkOptions
import com.github.claudecode.ccgui.model.message.ChatMessage
import com.github.claudecode.ccgui.model.message.MessageRole
import com.github.claudecode.ccgui.model.message.MessageStatus
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 轻量化上下文压缩模块
 *
 * 负责在上下文即将溢出时智能压缩对话历史，保留关键信息并生成摘要。
 *
 * 压缩策略：
 * - 消息数 < 10：不压缩
 * - 10 <= 消息数 < 50：保留首尾各3条，中间生成摘要替换
 * - 消息数 >= 50：保留首尾各5条，中间按滑动窗口压缩
 *
 * 关键消息类型（不压缩）：
 * - 用户消息
 * - 工具调用消息
 * - 错误消息
 *
 * @param project IntelliJ项目实例
 */
@Service(Service.Level.PROJECT)
class ContextCompressor(private val project: Project) {

    private val log = logger<ContextCompressor>()

    /** Token 估算器 */
    private val tokenEstimator = TokenEstimator.getInstance()

    /** Claude Code 客户端 */
    private val claudeClient by lazy { ClaudeCodeClient.getInstance(project) }

    /** 压缩阈值配置 */
    private val compactThreshold = CompactThreshold()

    /**
     * 压缩阈值配置
     */
    data class CompactThreshold(
        val smallMessageCount: Int = 10,           // 小消息量阈值（不压缩）
        val mediumMessageCount: Int = 50,           // 中等消息量阈值（滑动窗口）
        val smallHeadTailCount: Int = 3,            // 小/中等消息量保留的首尾条数
        val largeHeadTailCount: Int = 5,            // 大消息量保留的首尾条数
        val summaryWindowSize: Int = 10             // 摘要窗口大小
    )

    /**
     * 压缩结果
     */
    sealed class CompressionResult {
        data class Success(
            val originalCount: Int,
            val compressedCount: Int,
            val summaryMessage: ChatMessage? = null,
            val removedCount: Int = originalCount - compressedCount
        ) : CompressionResult()

        data class Skipped(val reason: String) : CompressionResult()
        data class Failed(val error: String) : CompressionResult()
    }

    /**
     * 压缩消息列表
     *
     * @param messages 原始消息列表
     * @param sessionId 会话ID（用于日志和上下文追踪）
     * @return 压缩后的消息列表
     */
    suspend fun compress(messages: List<ChatMessage>, sessionId: String? = null): CompressionResult {
        if (messages.isEmpty()) {
            return CompressionResult.Skipped("Empty message list")
        }

        val messageCount = messages.size
        log.debug("ContextCompressor: Starting compression for $messageCount messages (session=$sessionId)")

        // 策略1：消息数 < 10，不压缩
        if (messageCount < compactThreshold.smallMessageCount) {
            log.debug("ContextCompressor: Message count $messageCount < ${compactThreshold.smallMessageCount}, skipping compression")
            return CompressionResult.Skipped("Message count below threshold ($messageCount < ${compactThreshold.smallMessageCount})")
        }

        // 策略2：10 <= 消息数 < 50，保留首尾各3条，中间生成摘要
        if (messageCount < compactThreshold.mediumMessageCount) {
            return compressMediumSize(messages, sessionId)
        }

        // 策略3：消息数 >= 50，保留首尾各5条，中间按滑动窗口压缩
        return compressLargeSize(messages, sessionId)
    }

    /**
     * 中等规模压缩策略
     *
     * 保留首尾各3条，中间生成摘要替换
     */
    private suspend fun compressMediumSize(messages: List<ChatMessage>, sessionId: String?): CompressionResult {
        val headCount = compactThreshold.smallHeadTailCount
        val head = messages.take(headCount)
        val tail = messages.takeLast(headCount)
        val middle = messages.drop(headCount).dropLast(headCount)

        log.debug("ContextCompressor: Medium compression - head=$headCount, tail=$headCount, middle=${middle.size}")

        // 生成摘要（同步方式，无超时保护）
        val summaryMessage = generateSummary(middle, sessionId)

        val result = head + summaryMessage + tail
        return CompressionResult.Success(
            originalCount = messages.size,
            compressedCount = result.size,
            summaryMessage = summaryMessage
        )
    }

    /**
     * 大规模压缩策略
     *
     * 保留首尾各5条，中间按滑动窗口压缩
     */
    private suspend fun compressLargeSize(messages: List<ChatMessage>, sessionId: String?): CompressionResult {
        val headCount = compactThreshold.largeHeadTailCount
        val head = messages.take(headCount)
        val tail = messages.takeLast(headCount)
        val middle = messages.drop(headCount).dropLast(headCount)

        log.debug("ContextCompressor: Large compression - head=$headCount, tail=$headCount, middle=${middle.size}")

        // 按滑动窗口生成多个摘要
        val windowSize = compactThreshold.summaryWindowSize
        val summaries = mutableListOf<ChatMessage>()

        var windowStart = 0
        while (windowStart < middle.size) {
            val windowEnd = minOf(windowStart + windowSize, middle.size)
            val window = middle.subList(windowStart, windowEnd)

            // 过滤掉关键消息（它们会被单独保留）
            val nonCriticalInWindow = filterCriticalMessages(window)
            if (nonCriticalInWindow.isNotEmpty()) {
                val windowSummary = generateSummary(nonCriticalInWindow, sessionId)
                summaries.add(windowSummary)
            }

            windowStart = windowEnd
        }

        // 合并头尾、摘要和关键消息
        val criticalMessages = extractCriticalMessages(middle)
        val result = head + summaries + criticalMessages + tail

        return CompressionResult.Success(
            originalCount = messages.size,
            compressedCount = result.size,
            summaryMessage = summaries.firstOrNull()
        )
    }

    /**
     * 生成中间消息的摘要
     *
     * @param messages 需要摘要的消息列表
     * @param sessionId 会话ID
     * @return 摘要消息
     */
    private suspend fun generateSummary(messages: List<ChatMessage>, sessionId: String?): ChatMessage {
        if (messages.isEmpty()) {
            return createSummaryMessage("（空对话历史）")
        }

        // 过滤掉空消息
        val validMessages = messages.filter { it.content.isNotBlank() }
        if (validMessages.isEmpty()) {
            return createSummaryMessage("（无有效内容）")
        }

        // 尝试调用 Claude API 生成摘要
        return try {
            val summaryPrompt = buildSummaryPrompt(validMessages)
            val summaryText = callClaudeForSummary(summaryPrompt)
            createSummaryMessage(summaryText)
        } catch (e: Exception) {
            log.warn("ContextCompressor: Failed to generate summary via Claude API: ${e.message}")
            // 降级：生成简单摘要
            createFallbackSummary(validMessages)
        }
    }

    /**
     * 调用 Claude API 生成摘要
     */
    private suspend fun callClaudeForSummary(prompt: String): String {
        // 使用较短的超时时间，因为摘要生成应该是快速的
        val result = withTimeoutOrNull(30_000L) {
            claudeClient.sendMessage(
                prompt = prompt,
                options = SdkOptions(maxTurns = 1, allowedTools = emptyList())
            )
        }

        return if (result?.isSuccess == true) {
            result.getOrNull()?.result?.trim() ?: "（摘要生成失败）"
        } else {
            throw RuntimeException(result?.exceptionOrNull()?.message ?: "Unknown error")
        }
    }

    /**
     * 构建摘要生成提示
     */
    private fun buildSummaryPrompt(messages: List<ChatMessage>): String {
        val messageSummary = messages.joinToString("\n---\n") { msg ->
            val role = msg.role.name.lowercase()
            val content = msg.content.take(500) // 限制每条消息的长度
            "[$role] $content"
        }

        return """
            |请为以下对话历史生成一个简短的摘要（不超过100字），概括主要讨论内容和关键结论。
            |
            |对话历史：
            |$messageSummary
            |
            |摘要：
        """.trimMargin()
    }

    /**
     * 创建摘要消息
     */
    private fun createSummaryMessage(summaryText: String): ChatMessage {
        return ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "[上下文摘要] $summaryText",
            status = MessageStatus.COMPLETED
        )
    }

    /**
     * 生成回退摘要（当 Claude API 不可用时）
     */
    private fun createFallbackSummary(messages: List<ChatMessage>): ChatMessage {
        val totalChars = messages.sumOf { it.content.length }
        val roles = messages.map { it.role }.groupingBy { it }.eachCount()
        val summaryText = "（${messages.size}条消息，约${totalChars}字符，${roles.entries.joinToString("、") { "${it.value}条${it.key.name.lowercase()}" }}）"
        return createSummaryMessage(summaryText)
    }

    /**
     * 过滤关键消息
     *
     * 关键消息包括：用户消息、工具调用消息、错误消息
     */
    private fun filterCriticalMessages(messages: List<ChatMessage>): List<ChatMessage> {
        return messages.filter { !isCriticalMessage(it) }
    }

    /**
     * 提取关键消息
     */
    private fun extractCriticalMessages(messages: List<ChatMessage>): List<ChatMessage> {
        return messages.filter { isCriticalMessage(it) }
    }

    /**
     * 判断是否为关键消息
     *
     * 关键消息不压缩，需要完整保留
     */
    fun isCriticalMessage(message: ChatMessage): Boolean {
        // 用户消息必须保留
        if (message.role == MessageRole.USER) {
            return true
        }

        // 错误消息必须保留
        if (message.status == MessageStatus.FAILED) {
            return true
        }

        // 工具调用消息必须保留
        if (isToolCallMessage(message)) {
            return true
        }

        return false
    }

    /**
     * 判断是否为工具调用消息
     *
     * 通过内容特征判断：
     * - 包含 "Using tool:" 或 "使用工具:"
     * - 包含 "Tool:" 或 "工具:"
     * - 包含 "function call" 或 "函数调用"
     */
    private fun isToolCallMessage(message: ChatMessage): Boolean {
        val content = message.content
        val toolIndicators = listOf(
            "Using tool:",
            "使用工具:",
            "Tool:",
            "工具:",
            "function call",
            "函数调用",
            "Invoking tool",
            "调用工具"
        )
        return toolIndicators.any { content.contains(it, ignoreCase = true) }
    }

    /**
     * 计算消息列表的预估 token 数
     */
    fun estimateTokens(messages: List<ChatMessage>): Int {
        return messages.sumOf { tokenEstimator.estimateTokens(it.content) }
    }

    /**
     * 检查是否需要压缩
     *
     * @param messages 消息列表
     * @param threshold token 阈值
     * @return true 如果需要压缩
     */
    fun needsCompression(messages: List<ChatMessage>, threshold: Int = 150_000): Boolean {
        return estimateTokens(messages) > threshold
    }

    companion object {
        fun getInstance(project: Project): ContextCompressor =
            project.getService(ContextCompressor::class.java)
    }
}