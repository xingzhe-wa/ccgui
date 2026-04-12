package com.github.claudecode.ccgui.model.mcp

import com.github.claudecode.ccgui.util.IdGenerator
import com.google.gson.JsonObject

/**
 * MCP 服务器状态枚举
 */
enum class McpServerStatus {
    CONNECTED,
    DISCONNECTED,
    ERROR,
    CONNECTING
}

/**
 * MCP 作用域枚举
 */
enum class McpScope {
    GLOBAL,
    PROJECT
}

/**
 * MCP 服务器配置
 *
 * @param id 服务器 ID
 * @param name 服务器名称
 * @param description 服务器描述
 * @param command 启动命令
 * @param args 命令参数
 * @param env 环境变量
 * @param enabled 是否启用
 * @param status 当前状态
 * @param capabilities 支持的能力列表
 * @param scope 作用域
 * @param lastConnected 最后连接时间
 * @param error 错误信息
 */
data class McpServer(
    val id: String = IdGenerator.mcpServerId(),
    val name: String,
    val description: String = "",
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val status: McpServerStatus = McpServerStatus.DISCONNECTED,
    val capabilities: List<String> = emptyList(),
    val scope: McpScope = McpScope.PROJECT,
    val lastConnected: Long? = null,
    val error: String? = null
) {

    /**
     * 是否已连接
     */
    val isConnected: Boolean get() = status == McpServerStatus.CONNECTED

    /**
     * 是否正在连接
     */
    val isConnecting: Boolean get() = status == McpServerStatus.CONNECTING

    /**
     * 是否有错误
     */
    val hasError: Boolean get() = status == McpServerStatus.ERROR

    /**
     * 获取连接状态描述
     */
    val statusDescription: String
        get() = when (status) {
            McpServerStatus.CONNECTED -> "已连接"
            McpServerStatus.DISCONNECTED -> "未连接"
            McpServerStatus.CONNECTING -> "连接中..."
            McpServerStatus.ERROR -> error ?: "连接错误"
        }

    /**
     * 标记为已连接
     */
    fun withConnected(): McpServer {
        return copy(
            status = McpServerStatus.CONNECTED,
            lastConnected = System.currentTimeMillis(),
            error = null
        )
    }

    /**
     * 标记为断开连接
     */
    fun withDisconnected(): McpServer {
        return copy(status = McpServerStatus.DISCONNECTED)
    }

    /**
     * 标记为连接错误
     */
    fun withError(error: String): McpServer {
        return copy(
            status = McpServerStatus.ERROR,
            error = error
        )
    }

    companion object {
        /**
         * 创建文件系统 MCP 服务器
         */
        fun fileSystem(): McpServer {
            return McpServer(
                name = "文件系统",
                description = "提供文件系统操作能力",
                command = "npx",
                args = listOf("-y", "@modelcontextprotocol/server-filesystem"),
                capabilities = listOf("read_file", "write_file", "list_directory"),
                scope = McpScope.PROJECT
            )
        }

        /**
         * 从 JSON 反序列化
         */
        fun fromJson(json: JsonObject): McpServer? {
            return try {
                McpServer(
                    id = json.get("id")?.asString ?: IdGenerator.mcpServerId(),
                    name = json.get("name")?.asString ?: "",
                    description = json.get("description")?.asString ?: "",
                    command = json.get("command")?.asString ?: "",
                    args = json.getAsJsonArray("args")?.map { it.asString } ?: emptyList(),
                    env = json.getAsJsonObject("env")?.let { obj ->
                        obj.entrySet().associate { it.key to it.value.asString }
                    } ?: emptyMap(),
                    enabled = json.get("enabled")?.asBoolean ?: true,
                    status = McpServerStatus.valueOf(
                        json.get("status")?.asString ?: "DISCONNECTED"
                    ),
                    capabilities = json.getAsJsonArray("capabilities")?.map {
                        it.asString
                    } ?: emptyList(),
                    scope = McpScope.valueOf(
                        json.get("scope")?.asString ?: "PROJECT"
                    ),
                    lastConnected = json.get("lastConnected")?.asLong,
                    error = json.get("error")?.asString
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 序列化为 JSON
     */
    fun toJson(): JsonObject {
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
            addProperty("status", status.name)
            add("capabilities", com.google.gson.JsonArray().apply {
                capabilities.forEach { add(it) }
            })
            addProperty("scope", scope.name)
            lastConnected?.let { addProperty("lastConnected", it) }
            error?.let { addProperty("error", it) }
        }
    }
}

/**
 * MCP 服务器连接测试结果
 */
sealed class TestResult {
    data class Success(val capabilities: List<String>) : TestResult()
    data class Failure(val error: String) : TestResult()
}
