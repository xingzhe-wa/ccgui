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
     */
    fun appendChunk(chunk: String) {
        if (!_isStreaming.value) {
            _isStreaming.value = true
        }
        _buffer.value += chunk

        // 发送事件
        EventBus.publish(SdkTextDeltaEvent("", chunk))

        // 发送到前端
        cefPanel?.sendToJavaScript("streamChunk", mapOf("chunk" to chunk))
    }

    /**
     * 完成流式输出
     */
    fun finishStreaming() {
        val content = _buffer.value
        _isStreaming.value = false
        log.debug("Streaming finished, total length: ${content.length}")

        // 发送到前端
        cefPanel?.sendToJavaScript("streamComplete", mapOf("content" to content))
    }

    /**
     * 取消流式输出
     */
    fun cancelStreaming() {
        _isStreaming.value = false
        log.info("Streaming cancelled")

        // 发送到前端
        cefPanel?.sendToJavaScript("streamCancelled", emptyMap())
    }

    /**
     * 设置错误
     */
    fun setError(error: String) {
        _isStreaming.value = false
        log.error("Streaming error: $error")

        // 发送到前端
        cefPanel?.sendToJavaScript("streamError", mapOf("error" to error))
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
