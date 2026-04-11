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
//    val maxTokens: Int = 8192,
//    val temperature: Double = 1.0,
//    val topP: Double = 0.999,
    val maxRetries: Int = 3,
    /** Per-mode 模型映射：AUTO 模式模型 */
    val sonnetModel: String? = null,
    /** Per-mode 模型映射：THINKING 模式模型 */
    val opusModel: String? = null,
    /** Per-mode 模型映射：PLANNING 模式模型 */
    val maxModel: String? = null
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
                maxRetries = json.get("maxRetries")?.asInt ?: 3,
                sonnetModel = json.get("sonnetModel")?.asString,
                opusModel = json.get("opusModel")?.asString,
                maxModel = json.get("maxModel")?.asString
            )
        }

        /**
         * 供应商支持的模型列表
         * 当用户选择供应商时，前端可以根据此列表动态更新模型选择
         */
        fun getProviderModels(provider: String): List<Map<String, String>> {
            return when (provider.lowercase()) {
                "anthropic" -> listOf(
                    mapOf("id" to "claude-sonnet-4-20250514", "name" to "Claude Sonnet 4"),
                    mapOf("id" to "claude-3-5-sonnet-20241022", "name" to "Claude 3.5 Sonnet"),
                    mapOf("id" to "claude-3-5-haiku-20241022", "name" to "Claude 3.5 Haiku"),
                    mapOf("id" to "claude-3-opus-20240229", "name" to "Claude 3 Opus"),
                    mapOf("id" to "claude-3-sonnet-20240229", "name" to "Claude 3 Sonnet"),
                    mapOf("id" to "claude-3-haiku-20240229", "name" to "Claude 3 Haiku")
                )
                "openai" -> listOf(
                    mapOf("id" to "gpt-4o", "name" to "GPT-4o"),
                    mapOf("id" to "gpt-4o-mini", "name" to "GPT-4o Mini"),
                    mapOf("id" to "gpt-4-turbo", "name" to "GPT-4 Turbo"),
                    mapOf("id" to "gpt-4", "name" to "GPT-4"),
                    mapOf("id" to "gpt-3.5-turbo", "name" to "GPT-3.5 Turbo")
                )
                "deepseek" -> listOf(
                    mapOf("id" to "deepseek-chat", "name" to "DeepSeek Chat"),
                    mapOf("id" to "deepseek-coder", "name" to "DeepSeek Coder")
                )
                "custom" -> listOf(
                    mapOf("id" to "custom", "name" to "自定义模型")
                )
                else -> listOf(
                    mapOf("id" to provider, "name" to provider)
                )
            }
        }

        /**
         * 获取所有支持的供应商列表
         */
        fun getAllProviders(): List<Map<String, String>> {
            return listOf(
                mapOf("id" to "anthropic", "name" to "Anthropic", "description" to "Claude 系列模型"),
                mapOf("id" to "openai", "name" to "OpenAI", "description" to "GPT 系列模型"),
                mapOf("id" to "deepseek", "name" to "DeepSeek", "description" to "DeepSeek 系列模型"),
                mapOf("id" to "custom", "name" to "自定义", "description" to "自定义 API 端点")
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
            addProperty("maxRetries", maxRetries)
            sonnetModel?.let { addProperty("sonnetModel", it) }
            opusModel?.let { addProperty("opusModel", it) }
            maxModel?.let { addProperty("maxModel", it) }
        }
    }
}