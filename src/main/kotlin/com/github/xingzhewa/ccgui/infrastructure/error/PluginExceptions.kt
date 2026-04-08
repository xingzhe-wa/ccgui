package com.github.xingzhewa.ccgui.infrastructure.error

import com.github.xingzhewa.ccgui.util.logger

/**
 * 插件异常基类
 */
open class PluginException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * 获取错误代码
     */
    open val errorCode: String = "PLUGIN_ERROR"
}

/**
 * 配置错误
 */
class ConfigurationErrorException(
    message: String,
    cause: Throwable? = null
) : PluginException(message, cause) {
    override val errorCode: String = "CONFIG_ERROR"
}

/**
 * 网络错误
 */
class NetworkErrorException(
    message: String,
    cause: Throwable? = null
) : PluginException(message, cause) {
    override val errorCode: String = "NETWORK_ERROR"
}

/**
 * 验证错误
 */
class ValidationErrorException(
    message: String,
    cause: Throwable? = null
) : PluginException(message, cause) {
    override val errorCode: String = "VALIDATION_ERROR"
}

/**
 * SDK 错误
 */
class SdkErrorException(
    message: String,
    val sdkErrorCode: String? = null,
    cause: Throwable? = null
) : PluginException(message, cause) {
    override val errorCode: String = "SDK_ERROR"
}

/**
 * CLI 不可用错误
 */
class CliUnavailableException(
    message: String = "Claude CLI is not installed or not available",
    cause: Throwable? = null
) : PluginException(message, cause) {
    override val errorCode: String = "CLI_UNAVAILABLE"
}

/**
 * 会话错误
 */
class SessionErrorException(
    message: String,
    val sessionId: String? = null,
    cause: Throwable? = null
) : PluginException(message, cause) {
    override val errorCode: String = "SESSION_ERROR"
}

/**
 * 会话未找到错误
 */
class SessionNotFoundException(
    val sessionId: String,
    cause: Throwable? = null
) : PluginException("Session not found: $sessionId", cause) {
    override val errorCode: String = "SESSION_NOT_FOUND"
}

/**
 * 流式输出错误
 */
class StreamingErrorException(
    message: String,
    cause: Throwable? = null
) : PluginException(message, cause) {
    override val errorCode: String = "STREAMING_ERROR"
}

/**
 * 权限错误
 */
class PermissionDeniedException(
    message: String,
    val toolName: String? = null,
    cause: Throwable? = null
) : PluginException(message, cause) {
    override val errorCode: String = "PERMISSION_DENIED"
}

/**
 * MCP 服务器错误
 */
class McpServerException(
    message: String,
    val serverId: String? = null,
    cause: Throwable? = null
) : PluginException(message, cause) {
    override val errorCode: String = "MCP_SERVER_ERROR"
}

/**
 * MCP 服务器连接失败
 */
class McpConnectionFailedException(
    val serverId: String,
    cause: Throwable? = null
) : PluginException("Failed to connect to MCP server: $serverId", cause) {
    override val errorCode: String = "MCP_CONNECTION_FAILED"
}

/**
 * 存储错误
 */
class StorageException(
    message: String,
    cause: Throwable? = null
) : PluginException(message, cause) {
    override val errorCode: String = "STORAGE_ERROR"
}

/**
 * 未知错误
 */
class UnknownErrorException(
    message: String,
    override val errorCode: String = "UNKNOWN_ERROR",
    cause: Throwable? = null
) : PluginException(message, cause)

/**
 * 插件异常工厂
 */
object ExceptionFactory {

    private val logger = logger<ExceptionFactory>()

    /**
     * 根据错误代码创建异常
     */
    fun create(errorCode: String, message: String, cause: Throwable? = null): PluginException {
        return when (errorCode) {
            "CONFIG_ERROR" -> ConfigurationErrorException(message, cause)
            "NETWORK_ERROR" -> NetworkErrorException(message, cause)
            "VALIDATION_ERROR" -> ValidationErrorException(message, cause)
            "SDK_ERROR" -> SdkErrorException(message, null, cause)
            "CLI_UNAVAILABLE" -> CliUnavailableException(message, cause)
            "SESSION_ERROR" -> SessionErrorException(message, null, cause)
            "SESSION_NOT_FOUND" -> SessionNotFoundException(message, cause)
            "STREAMING_ERROR" -> StreamingErrorException(message, cause)
            "PERMISSION_DENIED" -> PermissionDeniedException(message, null, cause)
            "MCP_SERVER_ERROR" -> McpServerException(message, null, cause)
            "MCP_CONNECTION_FAILED" -> McpConnectionFailedException(message, cause)
            "STORAGE_ERROR" -> StorageException(message, cause)
            else -> UnknownErrorException(message, errorCode, cause)
        }
    }

    /**
     * 将异常包装为插件异常
     */
    fun wrap(throwable: Throwable, errorCode: String? = null): PluginException {
        return when (throwable) {
            is PluginException -> throwable
            else -> {
                logger.warn("Wrapping exception: ${throwable.message}")
                UnknownErrorException(
                    throwable.message ?: "Unknown error",
                    errorCode ?: "WRAPPED_ERROR",
                    throwable
                )
            }
        }
    }
}
