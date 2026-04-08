package com.github.xingzhewa.ccgui.adaptation.sdk

import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * SDK会话管理器
 *
 * 管理Claude Code CLI的会话生命周期。
 * SDK通过 --resume <session_id> 恢复会话，session_id 由 init 消息返回。
 *
 * 与 SessionManager（应用层会话管理）的关系:
 *   - SessionManager: 管理UI层的会话（名称、消息列表、切换）
 *   - SdkSessionManager: 管理SDK层的会话（CLI session_id映射、恢复）
 *   - 一个应用层会话可对应一个SDK会话
 */
@Service(Service.Level.PROJECT)
class SdkSessionManager(private val project: Project) : Disposable {

    private val log = logger<SdkSessionManager>()

    /**
     * 应用层会话ID → SDK会话ID 的映射
     */
    private val sessionMapping = ConcurrentHashMap<String, String>()

    /**
     * SDK会话元数据
     */
    private val sessionMeta = ConcurrentHashMap<String, SdkSessionMeta>()

    data class SdkSessionMeta(
        val sdkSessionId: String,
        val appSessionId: String,
        val createdAt: Long,
        val lastUsedAt: Long,
        val totalMessages: Int = 0,
        val totalCostUsd: Double = 0.0,
        val tools: List<String> = emptyList()
    )

    private val _activeSdkSession = MutableStateFlow<String?>(null)
    val activeSdkSession: StateFlow<String?> = _activeSdkSession.asStateFlow()

    /**
     * 注册SDK会话映射
     * 当CLI返回init消息时调用
     */
    fun registerSession(appSessionId: String, sdkSessionId: String, tools: List<String>) {
        sessionMapping[appSessionId] = sdkSessionId
        sessionMeta[sdkSessionId] = SdkSessionMeta(
            sdkSessionId = sdkSessionId,
            appSessionId = appSessionId,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            tools = tools
        )
        _activeSdkSession.value = sdkSessionId

        log.info("SDK session registered: app=$appSessionId → sdk=$sdkSessionId, tools=${tools.size}")
    }

    /**
     * 获取SDK会话ID（用于 --resume 参数）
     */
    fun getSdkSessionId(appSessionId: String): String? = sessionMapping[appSessionId]

    /**
     * 获取当前活跃的SDK会话ID
     */
    fun getCurrentSdkSessionId(): String? = _activeSdkSession.value

    /**
     * 更新会话使用信息
     */
    fun updateSessionUsage(sdkSessionId: String, costUsd: Double? = null, messageCount: Int? = null) {
        sessionMeta[sdkSessionId]?.let { meta ->
            sessionMeta[sdkSessionId] = meta.copy(
                lastUsedAt = System.currentTimeMillis(),
                totalCostUsd = meta.totalCostUsd + (costUsd ?: 0.0),
                totalMessages = meta.totalMessages + (messageCount ?: 0)
            )
        }
    }

    /**
     * 构建恢复会话的SdkOptions
     */
    fun buildResumeOptions(appSessionId: String, extraOptions: SdkOptions = SdkOptions()): SdkOptions {
        val sdkSessionId = sessionMapping[appSessionId]
        return extraOptions.copy(
            resumeSessionId = sdkSessionId
        )
    }

    /**
     * 移除会话映射
     */
    fun removeSession(appSessionId: String) {
        sessionMapping.remove(appSessionId)?.let { sdkSessionId ->
            sessionMeta.remove(sdkSessionId)
        }
    }

    /**
     * 获取所有SDK会话信息
     */
    fun getAllSessionMeta(): List<SdkSessionMeta> = sessionMeta.values.toList()

    override fun dispose() {
        sessionMapping.clear()
        sessionMeta.clear()
    }

    companion object {
        fun getInstance(project: Project): SdkSessionManager =
            project.getService(SdkSessionManager::class.java)
    }
}
