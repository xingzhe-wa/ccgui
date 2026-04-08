package com.github.xingzhewa.ccgui.application.streaming

import com.github.xingzhewa.ccgui.browser.CefBrowserPanel
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.SdkTextDeltaEvent
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 流式输出引擎
 *
 * 将流式数据桥接到 JCEF 前端
 */
@Service(Service.Level.PROJECT)
class StreamingOutputEngine(private val project: Project) : Disposable {

    private val log = logger<StreamingOutputEngine>()
    private var cefPanel: CefBrowserPanel? = null

    /** 是否正在流式输出 */
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    /** 当前流式内容缓冲 */
    private val _buffer = MutableStateFlow("")
    val buffer: StateFlow<String> = _buffer.asStateFlow()

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
        log.debug("Streaming started for session: $sessionId")
    }

    /**
     * 追加内容块
     * @param chunk 文本增量
     * @param messageId 消息ID（用于前端路由）
     */
    fun appendChunk(chunk: String, messageId: String = "") {
        if (!_isStreaming.value) {
            _isStreaming.value = true
        }
        _buffer.value += chunk

        // 发送内部事件（用于 Kotlin 层监听，如日志、调试）
        EventBus.publish(SdkTextDeltaEvent(messageId, chunk))

        // 发送到前端 — 事件名匹配 JS EventBus.Events.STREAMING_CHUNK = "streaming:chunk"
        // 数据格式匹配 useStreaming.ts 期望: { messageId, chunk }
        cefPanel?.sendToJavaScript("streaming:chunk", mapOf("messageId" to messageId, "chunk" to chunk))
    }

    /**
     * 完成流式输出
     * @param messageId 消息ID
     */
    fun finishStreaming(messageId: String = "") {
        val content = _buffer.value
        _isStreaming.value = false
        log.debug("Streaming finished, total length: ${content.length}")

        // 发送到前端 — 事件名匹配 JS EventBus.Events.STREAMING_COMPLETE = "streaming:complete"
        cefPanel?.sendToJavaScript("streaming:complete", mapOf("messageId" to messageId))
    }

    /**
     * 取消流式输出
     * @param messageId 消息ID
     */
    fun cancelStreaming(messageId: String = "") {
        _isStreaming.value = false
        log.info("Streaming cancelled")

        // 发送到前端 — 事件名匹配 JS EventBus.Events.STREAMING_CANCEL = "streaming:cancel"
        cefPanel?.sendToJavaScript("streaming:cancel", mapOf("messageId" to messageId))
    }

    /**
     * 设置错误
     * @param error 错误信息
     * @param messageId 消息ID
     */
    fun setError(error: String, messageId: String = "") {
        _isStreaming.value = false
        log.error("Streaming error: $error")

        // 发送到前端 — 事件名匹配 JS EventBus.Events.STREAMING_ERROR = "streaming:error"
        cefPanel?.sendToJavaScript("streaming:error", mapOf("messageId" to messageId, "error" to error))
    }

    /**
     * 清空缓冲
     */
    fun clearBuffer() {
        _buffer.value = ""
    }

    override fun dispose() {
        cefPanel = null
    }

    companion object {
        fun getInstance(project: Project): StreamingOutputEngine =
            project.getService(StreamingOutputEngine::class.java)
    }
}
