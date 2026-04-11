package com.github.xingzhewa.ccgui.infrastructure.storage

import com.github.xingzhewa.ccgui.application.usage.UsageService
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import java.time.Instant

/**
 * 统一存储服务
 *
 * 管理所有持久化存储，包括会话、配置和用量记录
 */
@State(name = "CCGuiStorage", storages = [Storage("ccgui-storage.xml")])
class StorageService(private val project: Project) : PersistentStateComponent<StorageService.State> {

    private val log = logger<StorageService>()

    /**
     * 存储状态
     */
    data class State(
        var usageRecords: String = "[]",
        var version: Int = 1
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    // ==================== 用量记录操作 ====================

    /**
     * 保存用量记录
     */
    fun saveUsageRecord(record: UsageService.UsageRecord) {
        val records = loadUsageRecordsInternal().toMutableList()
        records.add(record)
        state.usageRecords = JsonUtils.toJson(records)
        log.debug("Saved usage record: ${record.id}")
    }

    /**
     * 批量保存用量记录
     */
    fun saveUsageRecords(records: List<UsageService.UsageRecord>) {
        val existing = loadUsageRecordsInternal().toMutableList()
        existing.addAll(records)
        state.usageRecords = JsonUtils.toJson(existing)
        log.debug("Saved ${records.size} usage records")
    }

    /**
     * 加载指定项目的用量记录
     */
    fun loadUsageRecords(projectId: String): List<UsageService.UsageRecord> {
        return loadUsageRecordsInternal().filter { it.projectId == projectId }
    }

    /**
     * 加载所有用量记录（内部使用）
     */
    private fun loadUsageRecordsInternal(): List<UsageService.UsageRecord> {
        return try {
            val array = JsonUtils.parseArray(state.usageRecords) ?: return emptyList()
            array.mapNotNull { element ->
                val obj = element.asJsonObject
                UsageService.UsageRecord(
                    id = obj.get("id")?.asString ?: return@mapNotNull null,
                    sessionId = obj.get("sessionId")?.asString ?: "",
                    projectId = obj.get("projectId")?.asString ?: "",
                    timestamp = try {
                        Instant.parse(obj.get("timestamp")?.asString ?: "")
                    } catch (e: Exception) {
                        Instant.now()
                    },
                    model = obj.get("model")?.asString ?: "",
                    inputTokens = obj.get("inputTokens")?.asInt ?: 0,
                    outputTokens = obj.get("outputTokens")?.asInt ?: 0,
                    cacheCreationTokens = obj.get("cacheCreationTokens")?.asInt,
                    cacheReadTokens = obj.get("cacheReadTokens")?.asInt,
                    cost = obj.get("cost")?.asDouble ?: 0.0,
                    requestCount = obj.get("requestCount")?.asInt ?: 1
                )
            }
        } catch (e: Exception) {
            log.warn("Failed to parse usage records: ${e.message}")
            emptyList()
        }
    }

    /**
     * 删除用量记录
     */
    fun deleteUsageRecord(recordId: String) {
        val records = loadUsageRecordsInternal().filter { it.id != recordId }
        state.usageRecords = JsonUtils.toJson(records)
        log.debug("Deleted usage record: $recordId")
    }

    /**
     * 清空指定项目的用量记录
     */
    fun clearUsageRecords(projectId: String) {
        val records = loadUsageRecordsInternal().filter { it.projectId != projectId }
        state.usageRecords = JsonUtils.toJson(records)
        log.info("Cleared usage records for project: $projectId")
    }

    /**
     * 清空所有用量记录
     */
    fun clearAllUsageRecords() {
        state.usageRecords = "[]"
        log.info("Cleared all usage records")
    }

    /**
     * 获取用量记录数量
     */
    fun getUsageRecordCount(): Int {
        return try {
            val array = JsonUtils.parseArray(state.usageRecords)
            if (array != null) array.size() else 0
        } catch (e: Exception) {
            0
        }
    }

    companion object {
        fun getInstance(project: Project): StorageService =
            project.getService(StorageService::class.java)
    }
}
