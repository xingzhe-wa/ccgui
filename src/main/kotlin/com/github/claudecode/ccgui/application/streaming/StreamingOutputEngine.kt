package com.github.claudecode.ccgui.application.streaming

import com.github.claudecode.ccgui.browser.CefBrowserPanel
import com.github.claudecode.ccgui.infrastructure.eventbus.EventBus
import com.github.claudecode.ccgui.infrastructure.eventbus.SdkTextDeltaEvent
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

/**
 * 流式输出引擎
 *
 * 将流式数据桥接到 JCEF 前端
 * 实现自适应节流机制（对应架构文档）
 */
@Service(Service.Level.PROJECT)
class StreamingOutputEngine(private val project: Project) : Disposable {

    private val log = logger<StreamingOutputEngine>()
    private var cefPanel: CefBrowserPanel? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 是否正在流式输出 */
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    /** 当前流式内容缓冲 */
    private val _buffer = MutableStateFlow("")
    val buffer: StateFlow<String> = _buffer.asStateFlow()

    /** 累积的内容块（用于节流） */
    private var accumulatedChunk = StringBuilder()

    /** 最后发送时间 */
    private var lastSendTime = 0L

    /** 待处理的发送任务 */
    private var pendingSendJob: Job? = null

    companion object {
        /**
         * 自适应节流间隔配置
         *
         * 设计原则：
         * - 小内容优先保证流畅性（约 60fps）
         * - 大内容优先防止前端过载
         * - 流式输出应让用户感觉"即时响应"
         */

        /** 流畅间隔 (~60fps，约 16ms）用于小内容 */
        private const val THROTTLE_INTERVAL_SMOOTH_MS = 16L

        /** 平衡间隔 用于中等内容 */
        private const val THROTTLE_INTERVAL_BALANCED_MS = 50L

        /** 节流间隔 用于较大内容 */
        private const val THROTTLE_INTERVAL_THROTTLED_MS = 150L

        /** 重节流间隔 用于大内容 */
        private const val THROTTLE_INTERVAL_HEAVY_MS = 300L

        /** 小内容阈值 (8KB) - 享受流畅刷新 */
        private const val PAYLOAD_THRESHOLD_SMALL = 8 * 1024

        /** 中等内容阈值 (32KB) */
        private const val PAYLOAD_THRESHOLD_MEDIUM = 32 * 1024

        /** 大内容阈值 (128KB) */
        private const val PAYLOAD_THRESHOLD_LARGE = 128 * 1024

        /** 心跳间隔（10秒） */
        private const val HEARTBEAT_INTERVAL_MS = 10_000L

        /**
         * 计算节流间隔
         *
         * 根据 payload 大小动态调整推送间隔
         * - 小内容 (<8KB): 16ms (~60fps) 保证流畅的实时感
         * - 中等内容 (8-32KB): 50ms 平衡响应与性能
         * - 较大内容 (32-128KB): 150ms 节流防止过载
         * - 大内容 (>128KB): 300ms 重节流保证前端稳定
         *
         * 对应架构文档中的自适应节流机制
         */
        private fun calculateThrottleInterval(payloadSize: Int): Long {
            return when {
                payloadSize < PAYLOAD_THRESHOLD_SMALL -> THROTTLE_INTERVAL_SMOOTH_MS
                payloadSize < PAYLOAD_THRESHOLD_MEDIUM -> THROTTLE_INTERVAL_BALANCED_MS
                payloadSize < PAYLOAD_THRESHOLD_LARGE -> THROTTLE_INTERVAL_THROTTLED_MS
                else -> THROTTLE_INTERVAL_HEAVY_MS
            }
        }

        /**
         * 获取服务实例
         */
        fun getInstance(project: Project): StreamingOutputEngine =
            project.getService(StreamingOutputEngine::class.java)
    }

    init {
        log.info("StreamingOutputEngine initialized")
    }

    /**
     * 设置 CefBrowserPanel
     */
    fun setCefPanel(panel: CefBrowserPanel) {
        cefPanel = panel
    }

    /**
     * 开始流式输出
     */
    fun startStreaming(sessionId: String) {
        _isStreaming.value = true
        _buffer.value = ""
        accumulatedChunk.clear()
        lastSendTime = System.currentTimeMillis()
        log.debug("Streaming started for session: $sessionId")

        // 启动心跳
        startHeartbeat(sessionId)
    }

    /**
     * 心跳机制
     * 10秒间隔，防止工具执行阶段 stream stall
     */
    private fun startHeartbeat(sessionId: String) {
        scope.launch {
            while (_isStreaming.value) {
                delay(HEARTBEAT_INTERVAL_MS)

                if (_isStreaming.value) {
                    log.debug("Sending streaming heartbeat")
                    cefPanel?.sendToJavaScript("streaming:heartbeat", mapOf(
                        "sessionId" to sessionId,
                        "timestamp" to System.currentTimeMillis()
                    ))
                }
            }
        }
    }

    /**
     * 追加内容块（带自适应节流）
     * @param chunk 文本增量
     * @param messageId 消息ID（用于前端路由）
     */
    fun appendChunk(chunk: String, messageId: String = "") {
        if (!_isStreaming.value) {
            _isStreaming.value = true
        }

        // 累积到缓冲
        _buffer.value += chunk
        accumulatedChunk.append(chunk)

        // 发送内部事件
        EventBus.publish(SdkTextDeltaEvent(messageId, chunk), project)

        // 计算当前累积内容的大小
        val currentPayloadSize = accumulatedChunk.toString().toByteArray(StandardCharsets.UTF_8).size

        // 计算节流间隔
        val throttleInterval = calculateThrottleInterval(currentPayloadSize)

        // 计算距离上次发送的时间
        val timeSinceLastSend = System.currentTimeMillis() - lastSendTime

        if (timeSinceLastSend >= throttleInterval) {
            // 立即发送
            flushChunk(messageId)
        } else {
            // 延迟发送
            scheduleDelayedSend(messageId, throttleInterval - timeSinceLastSend)
        }
    }

    /**
     * 安排延迟发送
     */
    private fun scheduleDelayedSend(messageId: String, delay: Long) {
        // 取消之前的待处理任务
        pendingSendJob?.cancel()

        pendingSendJob = scope.launch {
            delay(delay)
            if (_isStreaming.value) {
                flushChunk(messageId)
            }
        }
    }

    /**
     * 刷新累积的内容块到前端
     */
    private fun flushChunk(messageId: String) {
        if (accumulatedChunk.isEmpty()) return

        val chunkToSend = accumulatedChunk.toString()
        accumulatedChunk.clear()
        lastSendTime = System.currentTimeMillis()

        // 发送到前端
        cefPanel?.sendToJavaScript("streaming:chunk", mapOf(
            "messageId" to messageId,
            "chunk" to chunkToSend
        ))

        log.debug("Flushed chunk: ${chunkToSend.length} chars")
    }

    /**
     * 完成流式输出
     * @param messageId 消息ID
     */
    fun finishStreaming(messageId: String = "") {
        // 刷新剩余内容
        if (accumulatedChunk.isNotEmpty()) {
            flushChunk(messageId)
        }

        val content = _buffer.value
        _isStreaming.value = false

        // 取消待处理的任务
        pendingSendJob?.cancel()
        pendingSendJob = null

        log.debug("Streaming finished, total length: ${content.length}")

        // 发送到前端
        cefPanel?.sendToJavaScript("streaming:complete", mapOf("messageId" to messageId))
    }

    /**
     * 取消流式输出
     * @param messageId 消息ID
     */
    fun cancelStreaming(messageId: String = "") {
        _isStreaming.value = false

        // 取消待处理的任务
        pendingSendJob?.cancel()
        pendingSendJob = null

        // 清空累积内容
        accumulatedChunk.clear()

        log.info("Streaming cancelled")

        // 发送到前端
        cefPanel?.sendToJavaScript("streaming:cancel", mapOf("messageId" to messageId))
    }

    /**
     * 设置错误
     * @param error 错误信息
     * @param messageId 消息ID
     */
    fun setError(error: String, messageId: String = "") {
        _isStreaming.value = false

        // 取消待处理的任务
        pendingSendJob?.cancel()
        pendingSendJob = null

        // 清空累积内容
        accumulatedChunk.clear()

        log.error("Streaming error: $error")

        // 发送到前端
        cefPanel?.sendToJavaScript("streaming:error", mapOf(
            "messageId" to messageId,
            "error" to error
        ))
    }

    /**
     * 清空缓冲
     */
    fun clearBuffer() {
        _buffer.value = ""
        accumulatedChunk.clear()
    }

    override fun dispose() {
        scope.cancel()
        pendingSendJob?.cancel()
        cefPanel = null
    }

    /**
     * 获取当前节流配置信息（用于调试）
     */
    fun getThrottleInfo(): Map<String, Any> {
        val currentPayloadSize = accumulatedChunk.toString().toByteArray(StandardCharsets.UTF_8).size
        return mapOf(
            "currentPayloadSize" to currentPayloadSize,
            "throttleInterval" to calculateThrottleInterval(currentPayloadSize),
            "isStreaming" to _isStreaming.value,
            "bufferLength" to _buffer.value.length,
            "accumulatedLength" to accumulatedChunk.length
        )
    }
}
