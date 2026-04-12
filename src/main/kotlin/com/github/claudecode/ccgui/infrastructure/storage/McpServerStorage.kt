package com.github.claudecode.ccgui.infrastructure.storage

import com.github.claudecode.ccgui.model.mcp.McpScope
import com.github.claudecode.ccgui.model.mcp.McpServer
import com.github.claudecode.ccgui.model.mcp.McpServerStatus
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * MCP 服务器持久化存储
 *
 * 负责 MCP 服务器配置的持久化存储
 * 存储格式: JSON数组，每个服务器包含id/name/command/args/env/scope/enabled
 *
 * @param project IntelliJ项目实例
 */
@Service(Service.Level.PROJECT)
@State(name = "McpServerStorage", storages = [Storage("mcp-servers.xml")])
class McpServerStorage(private val project: Project) : PersistentStateComponent<McpServerStorage.State> {

    private val log = logger<McpServerStorage>()

    /**
     * 存储状态
     */
    data class State(
        var servers: String = "[]"
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    // ==================== 服务器存储操作 ====================

    /**
     * 保存所有服务器
     *
     * @param servers 服务器列表
     */
    fun saveServers(servers: List<McpServer>) {
        val jsonArray = JsonArray()
        servers.forEach { server ->
            jsonArray.add(server.toStorageJson())
        }
        state.servers = jsonArray.toString()
        log.debug("Saved ${servers.size} MCP servers to storage")
    }

    /**
     * 加载所有服务器
     *
     * @return 服务器列表
     */
    fun loadServers(): List<McpServer> {
        return try {
            val jsonArray = com.google.gson.JsonParser.parseString(state.servers).asJsonArray
            jsonArray.mapNotNull { element ->
                try {
                    element.asJsonObject.let { json ->
                        McpServer(
                            id = json.get("id")?.asString ?: return@mapNotNull null,
                            name = json.get("name")?.asString ?: "",
                            description = json.get("description")?.asString ?: "",
                            command = json.get("command")?.asString ?: "",
                            args = json.getAsJsonArray("args")?.map { it.asString } ?: emptyList(),
                            env = json.getAsJsonObject("env")?.let { obj ->
                                obj.entrySet().associate { it.key to it.value.asString }
                            } ?: emptyMap(),
                            enabled = json.get("enabled")?.asBoolean ?: true,
                            status = McpServerStatus.DISCONNECTED,
                            capabilities = emptyList(),
                            scope = try {
                                McpScope.valueOf(json.get("scope")?.asString ?: "PROJECT")
                            } catch (e: Exception) {
                                McpScope.PROJECT
                            },
                            lastConnected = null,
                            error = null
                        )
                    }
                } catch (e: Exception) {
                    log.warn("Failed to parse server from storage: ${e.message}")
                    null
                }
            }.also {
                log.info("Loaded ${it.size} MCP servers from storage")
            }
        } catch (e: Exception) {
            log.error("Failed to load MCP servers from storage", e)
            emptyList()
        }
    }

    /**
     * 添加单个服务器
     *
     * @param server 服务器
     */
    fun addServer(server: McpServer) {
        val servers = loadServers().toMutableList()
        // Remove existing server with same id if exists
        servers.removeAll { it.id == server.id }
        servers.add(server)
        saveServers(servers)
        log.debug("Added MCP server to storage: ${server.id}")
    }

    /**
     * 更新单个服务器
     *
     * @param server 更新后的服务器
     */
    fun updateServer(server: McpServer) {
        val servers = loadServers().toMutableList()
        val index = servers.indexOfFirst { it.id == server.id }
        if (index >= 0) {
            servers[index] = server
            saveServers(servers)
            log.debug("Updated MCP server in storage: ${server.id}")
        }
    }

    /**
     * 删除服务器
     *
     * @param serverId 服务器 ID
     */
    fun deleteServer(serverId: String) {
        val servers = loadServers().toMutableList()
        servers.removeAll { it.id == serverId }
        saveServers(servers)
        log.debug("Deleted MCP server from storage: $serverId")
    }

    /**
     * 清空所有服务器
     */
    fun clearAll() {
        state.servers = "[]"
        log.info("Cleared all MCP servers from storage")
    }

    companion object {
        fun getInstance(project: Project): McpServerStorage =
            project.getService(McpServerStorage::class.java)
    }
}

/**
 * MCP 服务器存储格式扩展
 *
 * 将 McpServer 转换为存储格式（不包含运行时状态）
 */
fun McpServer.toStorageJson(): JsonObject {
    return JsonObject().apply {
        addProperty("id", id)
        addProperty("name", name)
        addProperty("description", description)
        addProperty("command", command)
        add("args", com.google.gson.JsonArray().apply {
            args.forEach { add(it) }
        })
        add("env", com.google.gson.JsonArray().apply {
            env.forEach { (k, v) -> add(com.google.gson.JsonPrimitive("$k=$v")) }
        })
        addProperty("enabled", enabled)
        addProperty("scope", scope.name)
    }
}