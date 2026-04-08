package com.github.xingzhewa.ccgui.model.agent

import com.github.xingzhewa.ccgui.util.IdGenerator
import com.google.gson.JsonObject

/**
 * Agent 能力枚举
 */
enum class AgentCapability {
    CODE_GENERATION,
    CODE_REVIEW,
    REFACTORING,
    TESTING,
    DOCUMENTATION,
    DEBUGGING,
    FILE_OPERATION,
    TERMINAL_OPERATION
}

/**
 * 约束类型枚举
 */
enum class ConstraintType {
    MAX_TOKENS,
    ALLOWED_FILE_TYPES,
    FORBIDDEN_PATTERNS,
    RESOURCE_LIMITS
}

/**
 * Agent 模式枚举
 */
enum class AgentMode {
    /** 仅提供建议 */
    CAUTIOUS,

    /** 建议为主，低风险自动执行 */
    BALANCED,

    /** 自动执行高风险操作 */
    AGGRESSIVE
}

/**
 * Agent 作用域枚举
 */
enum class AgentScope {
    GLOBAL,
    PROJECT,
    SESSION
}

/**
 * Agent 约束
 */
data class AgentConstraint(
    val type: ConstraintType,
    val description: String,
    val parameters: Map<String, Any> = emptyMap()
) {
    companion object {
        fun fromJson(json: JsonObject): AgentConstraint {
            return AgentConstraint(
                type = ConstraintType.valueOf(
                    json.get("type")?.asString ?: "MAX_TOKENS"
                ),
                description = json.get("description")?.asString ?: "",
                parameters = json.getAsJsonObject("parameters")?.let { obj ->
                    obj.entrySet().associate { it.key to it.value.toString() }
                } ?: emptyMap()
            )
        }
    }

    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("type", type.name)
            addProperty("description", description)
            add("parameters", com.google.gson.JsonArray().apply {
                parameters.forEach { (k, v) -> add(com.google.gson.JsonPrimitive(v.toString())) }
            })
        }
    }
}

/**
 * Agent 任务
 */
data class AgentTask(
    val id: String = IdGenerator.prefixedShortId("task_"),
    val description: String,
    val requiredCapability: AgentCapability,
    val context: Map<String, Any> = emptyMap()
) {
    companion object {
        fun fromJson(json: JsonObject): AgentTask? {
            return try {
                AgentTask(
                    id = json.get("id")?.asString ?: IdGenerator.prefixedShortId("task_"),
                    description = json.get("description")?.asString ?: "",
                    requiredCapability = AgentCapability.valueOf(
                        json.get("requiredCapability")?.asString ?: "CODE_GENERATION"
                    ),
                    context = json.getAsJsonObject("context")?.let { obj ->
                        obj.entrySet().associate { it.key to it.value.toString() }
                    } ?: emptyMap()
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("description", description)
            addProperty("requiredCapability", requiredCapability.name)
        }
    }
}

/**
 * Agent 定义
 *
 * @param id Agent ID
 * @param name Agent 名称
 * @param description Agent 描述
 * @param avatar 头像
 * @param systemPrompt 系统提示词
 * @param capabilities 能力列表
 * @param constraints 约束列表
 * @param tools 可用工具列表
 * @param mode 运行模式
 * @param scope 作用域
 * @param enabled 是否启用
 */
data class Agent(
    val id: String = IdGenerator.agentId(),
    val name: String,
    val description: String,
    val avatar: String = "🤖",
    val systemPrompt: String,
    val capabilities: List<AgentCapability> = emptyList(),
    val constraints: List<AgentConstraint> = emptyList(),
    val tools: List<String> = emptyList(),
    val mode: AgentMode = AgentMode.BALANCED,
    val scope: AgentScope = AgentScope.PROJECT,
    val enabled: Boolean = true
) {

    /**
     * 是否支持指定能力
     */
    fun hasCapability(capability: AgentCapability): Boolean {
        return capability in capabilities
    }

    /**
     * 是否为谨慎模式
     */
    val isCautious: Boolean get() = mode == AgentMode.CAUTIOUS

    /**
     * 是否为平衡模式
     */
    val isBalanced: Boolean get() = mode == AgentMode.BALANCED

    /**
     * 是否为激进模式
     */
    val isAggressive: Boolean get() = mode == AgentMode.AGGRESSIVE

    companion object {
        /**
         * 创建代码审查 Agent
         */
        fun codeReviewer(): Agent {
            return Agent(
                name = "代码审查员",
                description = "专业的代码审查助手，帮助发现代码中的问题",
                avatar = "🔍",
                systemPrompt = "你是一个专业的代码审查员...",
                capabilities = listOf(
                    AgentCapability.CODE_REVIEW,
                    AgentCapability.DEBUGGING
                ),
                tools = listOf("Read", "Glob", "Grep"),
                mode = AgentMode.CAUTIOUS
            )
        }

        /**
         * 从 JSON 反序列化
         */
        fun fromJson(json: JsonObject): Agent? {
            return try {
                Agent(
                    id = json.get("id")?.asString ?: IdGenerator.agentId(),
                    name = json.get("name")?.asString ?: "",
                    description = json.get("description")?.asString ?: "",
                    avatar = json.get("avatar")?.asString ?: "🤖",
                    systemPrompt = json.get("systemPrompt")?.asString ?: "",
                    capabilities = json.getAsJsonArray("capabilities")?.map {
                        AgentCapability.valueOf(it.asString)
                    } ?: emptyList(),
                    constraints = json.getAsJsonArray("constraints")?.map {
                        AgentConstraint.fromJson(it.asJsonObject)
                    } ?: emptyList(),
                    tools = json.getAsJsonArray("tools")?.map { it.asString } ?: emptyList(),
                    mode = AgentMode.valueOf(
                        json.get("mode")?.asString ?: "BALANCED"
                    ),
                    scope = AgentScope.valueOf(
                        json.get("scope")?.asString ?: "PROJECT"
                    ),
                    enabled = json.get("enabled")?.asBoolean ?: true
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
            addProperty("avatar", avatar)
            addProperty("systemPrompt", systemPrompt)
            add("capabilities", com.google.gson.JsonArray().apply {
                capabilities.forEach { add(it.name) }
            })
            add("constraints", com.google.gson.JsonArray().apply {
                constraints.forEach { add(it.toJson()) }
            })
            add("tools", com.google.gson.JsonArray().apply {
                tools.forEach { add(it) }
            })
            addProperty("mode", mode.name)
            addProperty("scope", scope.name)
            addProperty("enabled", enabled)
        }
    }
}

/**
 * Agent 结果
 */
data class AgentResult(
    val agentId: String,
    val suggestion: Any? = null,
    val executedActions: List<Any> = emptyList(),
    val pendingActions: List<Any> = emptyList(),
    val success: Boolean = true,
    val error: String? = null
)
