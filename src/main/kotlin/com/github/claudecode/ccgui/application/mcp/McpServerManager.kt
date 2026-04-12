package com.github.claudecode.ccgui.application.mcp

import com.github.claudecode.ccgui.infrastructure.eventbus.EventBus
import com.github.claudecode.ccgui.infrastructure.eventbus.McpServerConnectedEvent
import com.github.claudecode.ccgui.infrastructure.eventbus.McpServerDisconnectedEvent
import com.github.claudecode.ccgui.infrastructure.storage.McpServerStorage
import com.github.claudecode.ccgui.model.mcp.McpScope
import com.github.claudecode.ccgui.model.mcp.McpServer
import com.github.claudecode.ccgui.model.mcp.McpServerStatus
import com.github.claudecode.ccgui.model.mcp.TestResult
import com.github.claudecode.ccgui.util.JsonUtils
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess

/**
 * MCP 服务器管理器
 *
 * 负责:
 * - MCP 服务器的增删改查
 * - MCP 服务器连接管理
 * - MCP 服务器测试
 * - MCP 作用域管理（全局/项目）
 *
 * @param project IntelliJ项目实例
 */
@Service(Service.Level.PROJECT)
class McpServerManager(private val project: Project) : Disposable {

    private val log = logger<McpServerManager>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** MCP 服务器存储服务 */
    private val storage = McpServerStorage.getInstance(project)

    /** 所有 MCP 服务器 (key: serverId) */
    private val servers = ConcurrentHashMap<String, McpServer>()

    /** 按作用域索引的服务器 */
    private val serversByScope = ConcurrentHashMap<McpScope, MutableSet<String>>()

    /** 活跃的进程 (key: serverId) */
    private val activeProcesses = ConcurrentHashMap<String, Process>()

    /** 所有服务器列表 */
    private val _allServers = MutableStateFlow<List<McpServer>>(emptyList())
    val allServers: StateFlow<List<McpServer>> = _allServers.asStateFlow()

    /** 连接状态 */
    private val _connectionStates = MutableStateFlow<Map<String, McpServerStatus>>(emptyMap())
    val connectionStates: StateFlow<Map<String, McpServerStatus>> = _connectionStates.asStateFlow()

    // ==================== 初始化 ====================

    init {
        // MCP servers are loaded from persistent storage only
        // No hardcoded builtin servers
        loadServersFromStorage()
        log.info("McpServerManager initialized for project: ${project.name}")
    }

    /**
     * 从存储加载服务器
     */
    private fun loadServersFromStorage() {
        log.debug("Loading MCP servers from storage...")
        try {
            val loadedServers = storage.loadServers()
            loadedServers.forEach { server ->
                servers[server.id] = server
                serversByScope.getOrPut(server.scope) { mutableSetOf() }.add(server.id)
            }
            updateAllServers()
            updateConnectionStates()
            log.info("Loaded ${loadedServers.size} MCP servers from storage")
        } catch (e: Exception) {
            log.error("Failed to load MCP servers from storage", e)
        }
    }

    // ==================== 核心 API ====================

    /**
     * 添加服务器
     *
     * @param server 服务器
     * @return 是否成功
     */
    fun addServer(server: McpServer): Boolean {
        if (servers.containsKey(server.id)) {
            log.warn("MCP server already exists: ${server.id}")
            return false
        }

        servers[server.id] = server
        serversByScope.getOrPut(server.scope) { mutableSetOf() }.add(server.id)
        storage.addServer(server)

        updateAllServers()
        updateConnectionStates()

        log.info("MCP server added: ${server.id} - ${server.name}")
        return true
    }

    /**
     * 更新服务器
     *
     * @param server 更新后的服务器
     * @return 是否成功
     */
    fun updateServer(server: McpServer): Boolean {
        if (!servers.containsKey(server.id)) {
            log.warn("MCP server not found: ${server.id}")
            return false
        }

        val oldServer = servers[server.id]!!

        // 更新作用域索引
        if (oldServer.scope != server.scope) {
            serversByScope[oldServer.scope]?.remove(server.id)
            serversByScope.getOrPut(server.scope) { mutableSetOf() }.add(server.id)
        }

        servers[server.id] = server
        storage.updateServer(server)
        updateAllServers()
        updateConnectionStates()

        log.info("MCP server updated: ${server.id}")
        return true
    }

    /**
     * 删除服务器
     *
     * @param serverId 服务器 ID
     * @return 是否成功
     */
    fun deleteServer(serverId: String): Boolean {
        val server = servers.remove(serverId) ?: return false

        // 停止服务器（如果正在运行）
        scope.launch {
            stopServer(serverId)
        }

        // 更新索引
        serversByScope[server.scope]?.remove(serverId)
        storage.deleteServer(serverId)

        updateAllServers()
        updateConnectionStates()

        log.info("MCP server deleted: $serverId")
        return true
    }

    /**
     * 获取服务器
     *
     * @param serverId 服务器 ID
     * @return 服务器
     */
    fun getServer(serverId: String): McpServer? {
        return servers[serverId]
    }

    /**
     * 获取所有服务器
     *
     * @return 所有服务器
     */
    fun getAllServers(): List<McpServer> {
        return servers.values.toList()
    }

    /**
     * 按作用域获取服务器
     *
     * @param scope 作用域
     * @return 服务器列表
     */
    fun getServersByScope(scope: McpScope): List<McpServer> {
        val serverIds = serversByScope[scope] ?: return emptyList()
        return serverIds.mapNotNull { servers[it] }
    }

    /**
     * 获取启用的服务器
     *
     * @return 启用的服务器
     */
    fun getEnabledServers(): List<McpServer> {
        return servers.values.filter { it.enabled }
    }

    /**
     * 按状态获取服务器
     *
     * @param status 状态
     * @return 服务器列表
     */
    fun getServersByStatus(status: McpServerStatus): List<McpServer> {
        return servers.values.filter { it.status == status }
    }

    /**
     * 启用/禁用服务器
     *
     * @param serverId 服务器 ID
     * @param enabled 是否启用
     * @return 是否成功
     */
    fun setServerEnabled(serverId: String, enabled: Boolean): Boolean {
        val server = servers[serverId] ?: return false
        return updateServer(server.copy(enabled = enabled))
    }

    /**
     * 搜索服务器
     *
     * @param query 搜索关键词
     * @return 匹配的服务器列表
     */
    fun searchServers(query: String): List<McpServer> {
        val lowerQuery = query.lowercase()
        return servers.values.filter { server ->
            server.name.lowercase().contains(lowerQuery) ||
            server.description.lowercase().contains(lowerQuery) ||
            server.command.lowercase().contains(lowerQuery)
        }
    }

    /**
     * 启动服务器
     *
     * @param serverId 服务器 ID
     * @return 是否成功
     */
    suspend fun startServer(serverId: String): Boolean = withContext(Dispatchers.IO) {
        val server = servers[serverId] ?: return@withContext false

        if (!server.enabled) {
            log.warn("Cannot start disabled server: $serverId")
            return@withContext false
        }

        if (activeProcesses.containsKey(serverId)) {
            log.warn("Server already running: $serverId")
            return@withContext false
        }

        try {
            // 更新状态为连接中
            updateServer(server.copy(status = McpServerStatus.CONNECTING))

            // 构建进程命令
            val processBuilder = ProcessBuilder(
                listOf(server.command) + server.args
            )

            // 设置环境变量
            val environment = processBuilder.environment()
            server.env.forEach { (key, value) ->
                environment[key] = value
            }

            // 启动进程
            val process = processBuilder.start()
            activeProcesses[serverId] = process

            // 等待进程启动
            kotlinx.coroutines.delay(1000)

            // 检查进程是否存活
            if (!process.isAlive) {
                val error = process.errorStream.bufferedReader().use { it.readText() }
                updateServer(server.withError("Process exited: $error"))
                return@withContext false
            }

            // 更新状态为已连接
            val connectedServer = server.withConnected()
            updateServer(connectedServer)

            log.info("MCP server started: $serverId")
            EventBus.publish(McpServerConnectedEvent(serverId, server.capabilities))

            true
        } catch (e: Exception) {
            log.error("Failed to start MCP server: $serverId", e)
            updateServer(server.withError(e.message ?: "Unknown error"))
            false
        }
    }

    /**
     * 停止服务器
     *
     * @param serverId 服务器 ID
     * @return 是否成功
     */
    suspend fun stopServer(serverId: String): Boolean = withContext(Dispatchers.IO) {
        val process = activeProcesses.remove(serverId) ?: return@withContext false

        try {
            // 优雅关闭
            process.destroy()

            // 等待进程结束
            val exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)

            if (!exited) {
                // 强制关闭
                process.destroyForcibly()
            }

            // 更新状态
            val server = servers[serverId]
            if (server != null) {
                updateServer(server.withDisconnected())
                EventBus.publish(McpServerDisconnectedEvent(serverId, null))
            }

            log.info("MCP server stopped: $serverId")
            true
        } catch (e: Exception) {
            log.error("Failed to stop MCP server: $serverId", e)
            false
        }
    }

    /**
     * 重启服务器
     *
     * @param serverId 服务器 ID
     * @return 是否成功
     */
    suspend fun restartServer(serverId: String): Boolean {
        stopServer(serverId)
        kotlinx.coroutines.delay(1000)
        return startServer(serverId)
    }

    /**
     * 测试服务器连接
     *
     * @param serverId 服务器 ID
     * @param timeout 超时时间（毫秒）
     * @return 测试结果
     */
    suspend fun testServer(serverId: String, timeout: Long = 10000L): TestResult = withContext(Dispatchers.IO) {
        val server = servers[serverId] ?: return@withContext TestResult.Failure("Server not found")

        try {
            // 构建测试命令
            val processBuilder = ProcessBuilder(
                listOf(server.command) + server.args + listOf("--test")
            )

            // 设置环境变量
            val environment = processBuilder.environment()
            server.env.forEach { (key, value) ->
                environment[key] = value
            }

            // 启动进程
            val process = withTimeout(timeout) {
                processBuilder.start()
            }

            // 读取输出
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }

            // 等待进程结束
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                // 解析能力列表
                val capabilities = parseCapabilities(output)
                TestResult.Success(capabilities)
            } else {
                TestResult.Failure(error ?: "Exit code: $exitCode")
            }
        } catch (e: Exception) {
            log.error("Failed to test MCP server: $serverId", e)
            TestResult.Failure(e.message ?: "Unknown error")
        }
    }

    /**
     * 导出服务器
     *
     * @param serverId 服务器 ID
     * @return JSON 字符串
     */
    fun exportServer(serverId: String): String? {
        val server = servers[serverId] ?: return null
        return JsonUtils.gson.toJson(server.toJson())
    }

    /**
     * 导入服务器
     *
     * @param json JSON 字符串
     * @return 是否成功
     */
    fun importServer(json: String): Boolean {
        return try {
            val jsonObject = JsonUtils.gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val server = McpServer.fromJson(jsonObject) ?: return false
            addServer(server)
        } catch (e: Exception) {
            log.error("Failed to import MCP server", e)
            false
        }
    }

    /**
     * 批量导入服务器
     *
     * @param jsons JSON 字符串列表
     * @return 成功导入数量
     */
    fun importServers(jsons: List<String>): Int {
        var count = 0
        jsons.forEach { json ->
            if (importServer(json)) count++
        }
        return count
    }

    // ==================== 内部方法 ====================

    /**
     * 解析能力列表
     *
     * MCP 服务器输出格式（JSON-RPC）：
     * {
     *   "result": {
     *     "capabilities": {
     *       "resources": {},
     *       "tools": {},
     *       "prompts": {}
     *     },
     *     "serverInfo": {
     *       "name": "server-name",
     *       "version": "1.0.0"
     *     }
     *   }
     * }
     *
     * 或者简单格式（一行一个能力）：
     * resource:fs:read
     * tool:execute_command
     *
     * @param output 服务器输出
     * @return 能力列表
     */
    private fun parseCapabilities(output: String): List<String> {
        if (output.isBlank()) return emptyList()

        return try {
            // 尝试解析 JSON-RPC 格式
            val jsonObject = JsonUtils.gson.fromJson(output, com.google.gson.JsonObject::class.java)
            val result = jsonObject.getAsJsonObject("result")
            if (result != null) {
                val capabilities = result.getAsJsonObject("capabilities")
                if (capabilities != null) {
                    val parsed = mutableListOf<String>()

                    // 解析 resources
                    capabilities.getAsJsonObject("resources")?.let { resources ->
                        resources.keySet().forEach { key ->
                            parsed.add("resource:$key")
                        }
                    }

                    // 解析 tools
                    capabilities.getAsJsonObject("tools")?.let { tools ->
                        tools.keySet().forEach { key ->
                            parsed.add("tool:$key")
                        }
                    }

                    // 解析 prompts
                    capabilities.getAsJsonObject("prompts")?.let { prompts ->
                        prompts.keySet().forEach { key ->
                            parsed.add("prompt:$key")
                        }
                    }

                    // 解析 logging
                    if (capabilities.has("logging")) {
                        parsed.add("logging")
                    }

                    if (parsed.isNotEmpty()) {
                        log.debug("Parsed capabilities from JSON-RPC: $parsed")
                        return parsed
                    }
                }
            }

            // 如果 JSON 解析失败，尝试简单格式（一行一个）
            output.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .also { log.debug("Parsed capabilities from simple format: $it") }

        } catch (e: Exception) {
            log.warn("Failed to parse capabilities as JSON, trying simple format: ${e.message}")
            // 降级：尝试简单格式（一行一个能力）
            output.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("{") }
                .also {
                    if (it.isNotEmpty()) {
                        log.debug("Parsed capabilities from simple format: $it")
                    } else {
                        log.warn("No capabilities found in output")
                    }
                }
        }
    }

    /**
     * 更新所有服务器列表
     */
    private fun updateAllServers() {
        scope.launch {
            _allServers.value = servers.values.toList()
        }
    }

    /**
     * 更新连接状态
     */
    private fun updateConnectionStates() {
        scope.launch {
            _connectionStates.value = servers.mapValues { it.value.status }
        }
    }

    override fun dispose() {
        scope.cancel()

        // 停止所有活跃进程
        activeProcesses.forEach { (serverId, process) ->
            process.destroyForcibly()
        }
        activeProcesses.clear()

        servers.clear()
        serversByScope.clear()
    }

    companion object {
        fun getInstance(project: Project): McpServerManager =
            project.getService(McpServerManager::class.java)
    }
}
