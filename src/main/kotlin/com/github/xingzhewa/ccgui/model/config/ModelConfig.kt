package com.github.xingzhewa.ccgui.model.config

import com.google.gson.JsonObject

/**
 * 模型配置
 *
 * @param provider 提供者 (anthropic/openai/deepseek等)
 * @param model 模型名称
 * @param apiKey API密钥（已加密存储）
 * @param baseUrl API基础URL
 * @param maxTokens 最大token数
 * @param temperature 温度参数
 * @param topP Top-P采样
 * @param maxRetries 最大重试次数
 */
data class ModelConfig(
    val provider: String = "anthropic",
    val model: String = "claude-sonnet-4-20250514",
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val maxTokens: Int = 8192,
    val temperature: Double = 1.0,
    val topP: Double = 0.999,
    val maxRetries: Int = 3
) {

    companion object {
        /**
         * 从 JSON 反序列化
         */
        fun fromJson(json: JsonObject): ModelConfig {
            return ModelConfig(
                provider = json.get("provider")?.asString ?: "anthropic",
                model = json.get("model")?.asString ?: "claude-sonnet-4-20250514",
                apiKey = json.get("apiKey")?.asString,
                baseUrl = json.get("baseUrl")?.asString,
                maxTokens = json.get("maxTokens")?.asInt ?: 8192,
                temperature = json.get("temperature")?.asDouble ?: 1.0,
                topP = json.get("topP")?.asDouble ?: 0.999,
                maxRetries = json.get("maxRetries")?.asInt ?: 3
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
            apiKey?.let { addProperty("apiKey", it) }
            baseUrl?.let { addProperty("baseUrl", it) }
            addProperty("maxTokens", maxTokens)
            addProperty("temperature", temperature)
            addProperty("topP", topP)
            addProperty("maxRetries", maxRetries)
        }
    }
}