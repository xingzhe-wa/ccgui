package com.github.xingzhewa.ccgui.bridge

import com.github.xingzhewa.ccgui.adaptation.sdk.ClaudeCodeClient
import com.github.xingzhewa.ccgui.adaptation.sdk.SdkMessageTypes
import com.github.xingzhewa.ccgui.adaptation.sdk.SdkOptions
import com.github.xingzhewa.ccgui.adaptation.sdk.SdkSessionManager
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 桥接管理器
 *
 * 桥接前端 API 调用到 ClaudeCodeClient
 * 保持与 MyToolWindowFactory 的兼容接口
 */
@Service(Service.Level.PROJECT)
class BridgeManager(private val project: Project) : Disposable {

    private val log = logger<BridgeManager>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val claudeClient: ClaudeCodeClient by lazy { ClaudeCodeClient.getInstance(project) }
    private val sdkSessionManager: SdkSessionManager by lazy { SdkSessionManager.getInstance(project) }

    /** 连接状态 — 使用Mutex保证线程安全 */
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** 状态更新锁 */
    private val stateMutex = Mutex()

    // ---- 前端 API 实现 ----

    /**
     * 发送消息
     * @param message 消息内容
     * @param sessionId 会话ID
     * @param callback StreamCallback（用于流式事件推送）
     * @param onResponse 响应回调（用于发送 'response' 事件以 resolve/reject 前端 promise）
     *                    第一个参数: result（成功时），第二个参数: error（失败时，可为 null）
     */
    fun sendMessage(
        message: String,
        sessionId: String,
        callback: StreamCallback,
        onResponse: ((result: Any?, error: String?) -> Unit)? = null
    ): Result<Unit> {
        // CLI 可用性检查
        if (!claudeClient.isCliAvailable()) {
            val errorMsg = "Claude CLI is not installed or not available. Please install it from https://docs.anthropic.com/en/docs/claude-code/overview"
            callback.onStreamError(errorMsg)
            onResponse?.invoke(null, errorMsg)
            return Result.failure(IllegalStateException(errorMsg))
        }

        val options = sdkSessionManager.buildResumeOptions(sessionId)

        // 使用ensureActive来检查scope是否被取消
        scope.launch {
            ensureActive()
            stateMutex.withLock {
                _connectionState.value = ConnectionState.CONNECTED
            }
            try {
                val result = claudeClient.sendMessage(message, options, object : ClaudeCodeClient.SdkEventListener {
                    override fun onTextDelta(text: String) {
                        // 检查job是否还在运行
                        if (isActive) {
                            callback.onLineReceived(text)
                        }
                    }

                    override fun onResult(message: SdkMessageTypes.SdkResultMessage) {
                        if (isActive) {
                            callback.onStreamComplete(emptyList())
                            // 发送 'response' 事件以 resolve 前端 javaBridge.invoke() 的 promise
                            val result = mapOf(
                                "content" to "",
                                "tokensUsed" to 0,
                                "model" to "claude"
                            )
                            onResponse?.invoke(result, null)
                        }
                    }

                    override fun onError(error: String) {
                        if (isActive) {
                            callback.onStreamError(error)
                            onResponse?.invoke(null, error)
                        }
                    }
                })

                // 检查结果并处理错误
                if (!result.isSuccess && isActive) {
                    val errMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    callback.onStreamError(errMsg)
                    onResponse?.invoke(null, errMsg)
                }
            } catch (e: CancellationException) {
                callback.onStreamCancelled()
                throw e
            } catch (e: Exception) {
                if (isActive) {
                    val errMsg = e.message ?: "Unknown error"
                    callback.onStreamError(errMsg)
                    onResponse?.invoke(null, errMsg)
                }
            } finally {
                stateMutex.withLock {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }

        return Result.success(Unit)
    }

    /**
     * 流式发送消息
     */
    fun streamMessage(
        message: String,
        sessionId: String,
        callback: StreamCallback,
        onResponse: ((result: Any?, error: String?) -> Unit)? = null
    ) {
        // CLI 可用性检查
        if (!claudeClient.isCliAvailable()) {
            val errorMsg = "Claude CLI is not installed or not available. Please install it from https://docs.anthropic.com/en/docs/claude-code/overview"
            callback.onStreamError(errorMsg)
            onResponse?.invoke(null, errorMsg)
            return
        }

        val options = sdkSessionManager.buildResumeOptions(sessionId)
        callback.onStreamStart()

        scope.launch {
            ensureActive()
            stateMutex.withLock {
                _connectionState.value = ConnectionState.CONNECTED
            }
            try {
                val result = claudeClient.sendMessage(message, options, object : ClaudeCodeClient.SdkEventListener {
                    override fun onTextDelta(text: String) {
                        if (isActive) {
                            callback.onLineReceived(text)
                        }
                    }

                    override fun onResult(message: SdkMessageTypes.SdkResultMessage) {
                        if (isActive) {
                            callback.onStreamComplete(emptyList())
                            onResponse?.invoke(null, null)
                        }
                    }

                    override fun onError(error: String) {
                        if (isActive) {
                            callback.onStreamError(error)
                            onResponse?.invoke(null, error)
                        }
                    }
                })

                // 检查结果并处理错误
                if (!result.isSuccess && isActive) {
                    val errMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    callback.onStreamError(errMsg)
                    onResponse?.invoke(null, errMsg)
                }
            } catch (e: CancellationException) {
                callback.onStreamCancelled()
                throw e
            } catch (e: Exception) {
                if (isActive) {
                    val errMsg = e.message ?: "Unknown error"
                    callback.onStreamError(errMsg)
                    onResponse?.invoke(null, errMsg)
                }
            } finally {
                stateMutex.withLock {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }
    }

    /**
     * 取消流式输出
     */
    fun cancelStreaming(sessionId: String) {
        claudeClient.cancelCurrentRequest()
    }

    /**
     * 处理前端响应
     */
    fun handleResponse(queryId: Int, result: Any?, error: String? = null) {
        // 这个方法由 CefBrowserPanel 调用，用于响应前端的请求
        log.debug("Handling response for queryId: $queryId")
    }

    override fun dispose() {
        scope.cancel()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    companion object {
        fun getInstance(project: Project): BridgeManager =
            project.getService(BridgeManager::class.java)
    }
}

/**
 * 连接状态枚举
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
