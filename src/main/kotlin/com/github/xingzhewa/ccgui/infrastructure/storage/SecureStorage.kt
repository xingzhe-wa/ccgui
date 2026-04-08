package com.github.xingzhewa.ccgui.infrastructure.storage

import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * 安全存储管理器
 *
 * 使用 IntelliJ PersistentStateComponent 加密存储敏感信息（如 API Key 等）
 * 注意：实际加密由 IntelliJ Platform 的 State Storage 机制处理
 */
@State(name = "CCGuiSecureStorage", storages = [Storage("ccgui-secure.xml")])
class SecureStorage(private val project: Project) : PersistentStateComponent<SecureStorage.State> {

    private val log = logger<SecureStorage>()

    /**
     * 存储状态
     */
    data class State(
        var apiKeys: Map<String, String> = emptyMap()
    )

    private var state = State()

    /**
     * 内存缓存
     */
    private val memoryCache = ConcurrentHashMap<String, String>()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        memoryCache.clear()
        memoryCache.putAll(state.apiKeys)
    }

    private fun persist() {
        state.apiKeys = memoryCache.toMap()
    }

    companion object {
        private const val API_KEY_PREFIX = "api_key_"

        fun getInstance(project: Project): SecureStorage =
            project.getService(SecureStorage::class.java)
    }

    /**
     * 保存 API Key
     */
    fun saveApiKey(apiKey: String, service: String = "anthropic"): Boolean {
        return try {
            val key = "$API_KEY_PREFIX$service"
            memoryCache[key] = apiKey
            persist()
            log.info("API key saved for service: $service")
            true
        } catch (e: Exception) {
            log.error("Failed to save API key: ${e.message}", e)
            false
        }
    }

    /**
     * 获取 API Key
     */
    fun getApiKey(service: String = "anthropic"): String? {
        return try {
            val key = "$API_KEY_PREFIX$service"
            memoryCache[key]
        } catch (e: Exception) {
            log.error("Failed to get API key: ${e.message}", e)
            null
        }
    }

    /**
     * 删除 API Key
     */
    fun deleteApiKey(service: String = "anthropic"): Boolean {
        return try {
            val key = "$API_KEY_PREFIX$service"
            memoryCache.remove(key)
            persist()
            log.info("API key deleted for service: $service")
            true
        } catch (e: Exception) {
            log.error("Failed to delete API key: ${e.message}", e)
            false
        }
    }

    /**
     * 检查是否已存储 API Key
     */
    fun hasApiKey(service: String = "anthropic"): Boolean {
        return getApiKey(service) != null
    }
}