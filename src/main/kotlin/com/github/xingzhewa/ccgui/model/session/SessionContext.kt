package com.github.xingzhewa.ccgui.model.session

import com.google.gson.JsonObject

/**
 * 会话上下文
 *
 * 存储会话的配置信息和状态
 */
data class SessionContext(
    val modelConfig: ModelConfig = ModelConfig(),
    val enabledSkills: List<String> = emptyList(),
    val enabledMcpServers: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
) {

    companion object {
        /**
         * 从 JSON 反序列化
         */
        fun fromJson(json: JsonObject): SessionContext {
            return SessionContext(
                modelConfig = json.getAsJsonObject("modelConfig")?.let {
                    ModelConfig.fromJson(it)
                } ?: ModelConfig(),
                enabledSkills = json.getAsJsonArray("enabledSkills")?.map {
                    it.asString
                } ?: emptyList(),
                enabledMcpServers = json.getAsJsonArray("enabledMcpServers")?.map {
                    it.asString
                } ?: emptyList(),
                metadata = json.getAsJsonObject("metadata")?.let { obj ->
                    obj.entrySet().associate { entry ->
                        entry.key to when (val value = entry.value) {
                            is com.google.gson.JsonPrimitive -> {
                                if (value.isNumber) value.asNumber else value.asString
                            }
                            else -> value.toString()
                        }
                    }
                } ?: emptyMap()
            )
        }
    }

    /**
     * 序列化为 JSON
     */
    fun toJson(): JsonObject {
        return JsonObject().apply {
            add("modelConfig", modelConfig.toJson())
            add("enabledSkills", com.google.gson.JsonArray().apply {
                enabledSkills.forEach { add(it) }
            })
            add("enabledMcpServers", com.google.gson.JsonArray().apply {
                enabledMcpServers.forEach { add(it) }
            })
            add("metadata", com.google.gson.JsonArray().apply {
                metadata.forEach { (key, value) ->
                    add(com.google.gson.JsonPrimitive(value.toString()))
                }
            })
        }
    }
}

/**
 * 模型配置
 */
data class ModelConfig(
    val provider: String = "anthropic",
    val model: String = "claude-sonnet-4-20250514",
    val maxTokens: Int = 8192,
    val temperature: Double = 1.0,
    val topP: Double = 0.9
) {

    companion object {
        /**
         * 从 JSON 反序列化
         */
        fun fromJson(json: JsonObject): ModelConfig {
            return ModelConfig(
                provider = json.get("provider")?.asString ?: "anthropic",
                model = json.get("model")?.asString ?: "claude-sonnet-4-20250514",
                maxTokens = json.get("maxTokens")?.asInt ?: 8192,
                temperature = json.get("temperature")?.asDouble ?: 1.0,
                topP = json.get("topP")?.asDouble ?: 0.9
            )
        }
    }

    /**
     * 序列化为 JSON
     */
    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("provider", provider)
            addProperty("model", model)
            addProperty("maxTokens", maxTokens)
            addProperty("temperature", temperature)
            addProperty("topP", topP)
        }
    }
}
