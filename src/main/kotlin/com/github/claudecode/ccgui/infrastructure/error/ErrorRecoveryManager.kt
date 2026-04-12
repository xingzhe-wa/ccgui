package com.github.claudecode.ccgui.infrastructure.error

import com.github.claudecode.ccgui.infrastructure.eventbus.EventBus
import com.github.claudecode.ccgui.infrastructure.eventbus.SdkErrorEvent
import com.github.claudecode.ccgui.util.logger

/**
 * 错误恢复策略
 */
enum class RecoveryStrategy {
    /** 重试 */
    RETRY,

    /** 回退到备用方案 */
    FALLBACK,

    /** 忽略并继续 */
    IGNORE,

    /** 通知用户 */
    NOTIFY_USER,

    /** 终止操作 */
    ABORT
}

/**
 * 错误恢复动作
 */
data class RecoveryAction(
    val strategy: RecoveryStrategy,
    val message: String? = null,
    val retryCount: Int = 0,
    val delayMs: Long = 0
)

/**
 * 错误上下文
 */
data class ErrorContext(
    val error: Throwable,
    val operation: String,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 错误恢复管理器
 *
 * 负责处理错误、决定恢复策略、执行恢复动作
 */
class ErrorRecoveryManager {

    private val logger = logger<ErrorRecoveryManager>()

    /**
     * 错误处理器
     */
    fun handleError(context: ErrorContext): RecoveryAction {
        val error = context.error

        return when {
            // CLI 不可用
            error is CliUnavailableException -> {
                logger.error("CLI unavailable: ${error.message}")
                RecoveryAction(
                    strategy = RecoveryStrategy.NOTIFY_USER,
                    message = "请安装 Claude CLI: https://docs.anthropic.com/en/docs/claude-code/overview"
                )
            }

            // 会话未找到
            error is SessionNotFoundException -> {
                logger.warn("Session not found: ${error.sessionId}")
                RecoveryAction(
                    strategy = RecoveryStrategy.FALLBACK,
                    message = "会话已失效，已切换到新会话"
                )
            }

            // 权限拒绝
            error is PermissionDeniedException -> {
                logger.warn("Permission denied: ${error.toolName}")
                RecoveryAction(
                    strategy = RecoveryStrategy.NOTIFY_USER,
                    message = "操作被拒绝: ${error.toolName}"
                )
            }

            // MCP 连接失败
            error is McpConnectionFailedException -> {
                logger.error("MCP connection failed: ${error.serverId}", error)
                RecoveryAction(
                    strategy = RecoveryStrategy.FALLBACK,
                    message = "MCP 服务器连接失败，已禁用该服务器"
                )
            }

            // 流式输出错误
            error is StreamingErrorException -> {
                logger.error("Streaming error: ${error.message}", error)
                if (context.retryCount < context.maxRetries) {
                    RecoveryAction(
                        strategy = RecoveryStrategy.RETRY,
                        message = "正在重试...",
                        retryCount = context.retryCount + 1,
                        delayMs = 1000L * (context.retryCount + 1)
                    )
                } else {
                    RecoveryAction(
                        strategy = RecoveryStrategy.NOTIFY_USER,
                        message = "输出中断，请重试"
                    )
                }
            }

            // SDK 错误
            error is SdkErrorException -> {
                logger.error("SDK error: ${error.message}", error)
                if (context.retryCount < context.maxRetries) {
                    RecoveryAction(
                        strategy = RecoveryStrategy.RETRY,
                        retryCount = context.retryCount + 1,
                        delayMs = 2000L
                    )
                } else {
                    RecoveryAction(
                        strategy = RecoveryStrategy.NOTIFY_USER,
                        message = "SDK 错误: ${error.message}"
                    )
                }
            }

            // 网络错误
            error is NetworkErrorException -> {
                logger.warn("Network error: ${error.message}", error)
                if (context.retryCount < context.maxRetries) {
                    RecoveryAction(
                        strategy = RecoveryStrategy.RETRY,
                        retryCount = context.retryCount + 1,
                        delayMs = 3000L * (context.retryCount + 1)
                    )
                } else {
                    RecoveryAction(
                        strategy = RecoveryStrategy.NOTIFY_USER,
                        message = "网络错误，请检查网络连接"
                    )
                }
            }

            // 其他错误
            else -> {
                logger.error("Unexpected error in ${context.operation}: ${error.message}", error)
                // 发布错误事件
                EventBus.publish(SdkErrorEvent(null, error.message ?: "Unknown error"))

                if (context.retryCount < context.maxRetries) {
                    RecoveryAction(
                        strategy = RecoveryStrategy.RETRY,
                        retryCount = context.retryCount + 1,
                        delayMs = 1000L
                    )
                } else {
                    RecoveryAction(
                        strategy = RecoveryStrategy.NOTIFY_USER,
                        message = "发生错误: ${error.message}"
                    )
                }
            }
        }
    }

    /**
     * 执行恢复动作
     */
    suspend fun executeRecovery(
        action: RecoveryAction,
        operation: suspend () -> Unit
    ): Boolean {
        return when (action.strategy) {
            RecoveryStrategy.RETRY -> {
                if (action.delayMs > 0) {
                    kotlinx.coroutines.delay(action.delayMs)
                }
                try {
                    operation()
                    true
                } catch (e: Exception) {
                    logger.error("Recovery retry failed: ${e.message}", e)
                    false
                }
            }

            RecoveryStrategy.FALLBACK -> {
                logger.info("Executing fallback: ${action.message}")
                try {
                    operation()
                    true
                } catch (e: Exception) {
                    logger.error("Fallback failed: ${e.message}", e)
                    false
                }
            }

            RecoveryStrategy.IGNORE -> {
                logger.warn("Error ignored for operation")
                true
            }

            RecoveryStrategy.NOTIFY_USER -> {
                logger.info("Notifying user: ${action.message}")
                // UI 通知由上层处理
                false
            }

            RecoveryStrategy.ABORT -> {
                logger.error("Operation aborted: ${action.message}")
                false
            }
        }
    }

    companion object {
        @Volatile
        private var instance: ErrorRecoveryManager? = null

        fun getInstance(): ErrorRecoveryManager {
            return instance ?: synchronized(this) {
                instance ?: ErrorRecoveryManager().also { instance = it }
            }
        }
    }
}
