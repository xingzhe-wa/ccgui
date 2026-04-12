package com.github.claudecode.ccgui.model.session

import com.github.claudecode.ccgui.model.config.ModelConfig
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
            add("metadata", JsonObject().apply {
                metadata.forEach { (key, value) ->
                    addProperty(key, value.toString())
                }
            })
        }
    }
}
