package com.github.xingzhewa.ccgui.infrastructure.state

import com.github.xingzhewa.ccgui.util.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 应用状态
 */
data class AppState(
    val isInitialized: Boolean = false,
    val isConnected: Boolean = false,
    val currentProjectId: String? = null,
    val cliVersion: String? = null
)

/**
 * 连接状态
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * 状态管理器
 *
 * 管理全局应用状态和连接状态
 */
class StateManager {

    private val logger = logger<StateManager>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 应用状态
    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    // 连接状态
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // 错误状态
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        logger.info("StateManager initialized")
    }

    /**
     * 标记为已初始化
     */
    fun setInitialized(projectId: String) {
        _appState.value = _appState.value.copy(
            isInitialized = true,
            currentProjectId = projectId
        )
        logger.info("App initialized for project: $projectId")
    }

    /**
     * 设置 CLI 版本
     */
    fun setCliVersion(version: String) {
        _appState.value = _appState.value.copy(cliVersion = version)
        logger.info("CLI version set: $version")
    }

    /**
     * 设置连接状态
     */
    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
        _appState.value = _appState.value.copy(isConnected = state == ConnectionState.CONNECTED)
        logger.info("Connection state changed: $state")
    }

    /**
     * 设置错误
     */
    fun setError(error: String?) {
        _lastError.value = error
        if (error != null) {
            logger.error("App error: $error")
        }
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _lastError.value = null
    }

    /**
     * 获取是否已初始化
     */
    val isInitialized: Boolean get() = _appState.value.isInitialized

    /**
     * 获取是否已连接
     */
    val isConnected: Boolean get() = _connectionState.value == ConnectionState.CONNECTED

    /**
     * 获取 CLI 版本
     */
    val cliVersion: String? get() = _appState.value.cliVersion

    companion object {
        @Volatile
        private var instance: StateManager? = null

        fun getInstance(): StateManager {
            return instance ?: synchronized(this) {
                instance ?: StateManager().also { instance = it }
            }
        }
    }
}
