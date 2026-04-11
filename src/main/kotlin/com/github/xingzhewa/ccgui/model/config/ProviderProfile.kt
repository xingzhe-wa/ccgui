package com.github.xingzhewa.ccgui.model.config

import com.google.gson.JsonObject
import com.google.gson.JsonArray

/**
 * 特殊供应商 ID 常量
 *
 * 参考 jetbrains-cc-gui 的特殊供应商设计
 */
object SpecialProviderIds {
    /** 禁用供应商 */
    const val DISABLED = "__disabled__"

    /** 本地 Settings.json 供应商（使用 ~/.claude/settings.json） */
    const val LOCAL_SETTINGS = "__local_settings_json__"

    /** CLI Login 供应商（使用 Claude CLI 原生 OAuth 登录） */
    const val CLI_LOGIN = "__cli_login__"
}

/**
 * 供应商配置 Profile
 *
 * 支持多配置切换，每个 profile 包含完整的模型配置
 *
 * @param id 唯一标识
 * @param name 显示名称
 * @param provider 提供者 (anthropic/openai/deepseek/custom/special)
 * @param source 配置来源 (local/cc-switch/cli-login/disabled)
 * @param model 默认模型
 * @param apiKey API密钥 (已加密存储)
 * @param baseUrl API基础URL
 * @param sonnetModel Per-mode 模型映射：AUTO 模式模型
 * @param opusModel Per-mode 模型映射：THINKING 模式模型
 * @param maxModel Per-mode 模型映射：PLANNING 模式模型
 * @param maxRetries 最大重试次数
 * @param createdAt 创建时间戳
 * @param updatedAt 更新时间戳
 */
data class ProviderProfile(
    val id: String,
    val name: String,
    val provider: String = "anthropic",
    val source: String = "local",
    val model: String = "claude-sonnet-4-20250514",
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val sonnetModel: String? = null,
    val opusModel: String? = null,
    val maxModel: String? = null,
    val maxRetries: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {

    companion object {
        /**
         * 从 JSON 反序列化
         */
        fun fromJson(json: JsonObject): ProviderProfile {
            return ProviderProfile(
                id = json.get("id")?.asString ?: return ProviderProfile("", "Unnamed"),
                name = json.get("name")?.asString ?: "Unnamed",
                provider = json.get("provider")?.asString ?: "anthropic",
                source = json.get("source")?.asString ?: "local",
                model = json.get("model")?.asString ?: "claude-sonnet-4-20250514",
                apiKey = json.get("apiKey")?.asString,
                baseUrl = json.get("baseUrl")?.asString,
                sonnetModel = json.get("sonnetModel")?.asString,
                opusModel = json.get("opusModel")?.asString,
                maxModel = json.get("maxModel")?.asString,
                maxRetries = json.get("maxRetries")?.asInt ?: 3,
                createdAt = json.get("createdAt")?.asLong ?: System.currentTimeMillis(),
                updatedAt = json.get("updatedAt")?.asLong ?: System.currentTimeMillis()
            )
        }

        /**
         * 创建特殊供应商配置
         */
        fun createSpecialProvider(type: String): ProviderProfile {
            return when (type) {
                SpecialProviderIds.LOCAL_SETTINGS -> ProviderProfile(
                    id = SpecialProviderIds.LOCAL_SETTINGS,
                    name = "Local Settings",
                    provider = "special",
                    source = "local",
                    model = ""
                )
                SpecialProviderIds.CLI_LOGIN -> ProviderProfile(
                    id = SpecialProviderIds.CLI_LOGIN,
                    name = "CLI Login",
                    provider = "special",
                    source = "cli-login",
                    model = ""
                )
                SpecialProviderIds.DISABLED -> ProviderProfile(
                    id = SpecialProviderIds.DISABLED,
                    name = "Disabled",
                    provider = "special",
                    source = "disabled",
                    model = ""
                )
                else -> throw IllegalArgumentException("Unknown special provider type: $type")
            }
        }

        /**
         * 判断是否为特殊供应商 ID
         */
        fun isSpecialProviderId(id: String): Boolean {
            return id in listOf(
                SpecialProviderIds.DISABLED,
                SpecialProviderIds.LOCAL_SETTINGS,
                SpecialProviderIds.CLI_LOGIN
            )
        }

        /**
         * 从 Claude SDK settings.json 格式导入
         *
         * 格式：
         * {
         *   "providers": [
         *     {
         *       "id": "my-provider",
         *       "name": "显示名称",
         *       "settingsConfig": {
         *         "env": {
         *           "ANTHROPIC_AUTH_TOKEN": "sk-xxx",
         *           "ANTHROPIC_BASE_URL": "https://api.anthropic.com",
         *           "ANTHROPIC_DEFAULT_SONNET_MODEL": "claude-sonnet-4-20250514"
         *         }
         *       }
         *     }
         *   ]
         * }
         */
        fun fromSettingsJson(json: JsonObject): ProviderProfile? {
            val providersArray = json.getAsJsonArray("providers") ?: return null
            if (providersArray.size() == 0) return null

            val providerObj = providersArray.get(0).asJsonObject
            val id = providerObj.get("id")?.asString ?: return null
            val name = providerObj.get("name")?.asString ?: return null
            val settingsConfig = providerObj.getAsJsonObject("settingsConfig") ?: return null
            val env = settingsConfig.getAsJsonObject("env") ?: return null

            // 解析环境变量
            val authToken = env.get("ANTHROPIC_AUTH_TOKEN")?.asString
            val baseUrl = env.get("ANTHROPIC_BASE_URL")?.asString
            val sonnetModel = env.get("ANTHROPIC_DEFAULT_SONNET_MODEL")?.asString
            val opusModel = env.get("ANTHROPIC_DEFAULT_OPUS_MODEL")?.asString ?: env.get("ANTHROPIC_OPUS_MODEL")?.asString
            val maxModel = env.get("ANTHROPIC_DEFAULT_MAX_MODEL")?.asString ?: env.get("ANTHROPIC_MAX_MODEL")?.asString

            // 推断 provider 类型
            val provider = when {
                baseUrl?.contains("anthropic.com") == true -> "anthropic"
                baseUrl?.contains("openai.com") == true -> "openai"
                baseUrl?.contains("deepseek") == true -> "deepseek"
                else -> "anthropic"
            }

            return ProviderProfile(
                id = id,
                name = name,
                provider = provider,
                model = sonnetModel ?: "claude-sonnet-4-20250514",
                apiKey = authToken,
                baseUrl = baseUrl,
                sonnetModel = sonnetModel,
                opusModel = opusModel,
                maxModel = maxModel,
                maxRetries = 3,
                source = "local"
            )
        }

        /**
         * 从 cc-switch 导入创建 Profile
         */
        fun fromCcSwitch(
            id: String,
            name: String,
            authToken: String?,
            baseUrl: String?,
            sonnetModel: String?,
            opusModel: String?,
            maxModel: String?
        ): ProviderProfile {
            // 推断 provider 类型
            val provider = when {
                baseUrl?.contains("anthropic") == true -> "anthropic"
                baseUrl?.contains("openai") == true -> "openai"
                baseUrl?.contains("deepseek") == true -> "deepseek"
                else -> "anthropic"
            }

            return ProviderProfile(
                id = id,
                name = name,
                provider = provider,
                source = "cc-switch",
                model = sonnetModel ?: "claude-sonnet-4-20250514",
                apiKey = authToken,
                baseUrl = baseUrl,
                sonnetModel = sonnetModel,
                opusModel = opusModel,
                maxModel = maxModel,
                maxRetries = 3
            )
        }

        /**
         * 创建默认 Profile
         */
        fun createDefault(): ProviderProfile {
            return ProviderProfile(
                id = "default",
                name = "Default"
            )
        }

        /**
         * 从 ModelConfig 创建一个 Profile
         */
        fun fromModelConfig(id: String, name: String, config: ModelConfig): ProviderProfile {
            return ProviderProfile(
                id = id,
                name = name,
                provider = config.provider,
                model = config.model,
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                sonnetModel = config.sonnetModel,
                opusModel = config.opusModel,
                maxModel = config.maxModel,
                maxRetries = config.maxRetries
            )
        }
    }

    /**
     * 判断是否为特殊供应商
     */
    fun isSpecialProvider(): Boolean {
        return isSpecialProviderId(id)
    }

    /**
     * 判断是否可编辑
     *
     * 特殊供应商不可编辑
     * cc-switch 导入的配置需要先转换才能编辑
     */
    fun isEditable(): Boolean {
        return when (id) {
            SpecialProviderIds.DISABLED,
            SpecialProviderIds.LOCAL_SETTINGS,
            SpecialProviderIds.CLI_LOGIN -> false
            else -> source != "cc-switch"
        }
    }

    /**
     * 判断是否可删除
     *
     * 特殊供应商不可删除
     */
    fun isDeletable(): Boolean {
        return !isSpecialProvider()
    }

    /**
     * 判断是否可排序
     *
     * 特殊供应商固定在顶部，不可排序
     */
    fun isSortable(): Boolean {
        return !isSpecialProvider()
    }

    /**
     * 转换为本地配置（用于 cc-switch 配置转换）
     */
    fun toLocalProfile(): ProviderProfile {
        return copy(
            id = "${id}_local",
            name = "$name (Local)",
            source = "local",
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 序列化为 JSON
     */
    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("name", name)
            addProperty("provider", provider)
            addProperty("source", source)
            addProperty("model", model)
            apiKey?.let { addProperty("apiKey", it) }
            baseUrl?.let { addProperty("baseUrl", it) }
            sonnetModel?.let { addProperty("sonnetModel", it) }
            opusModel?.let { addProperty("opusModel", it) }
            maxModel?.let { addProperty("maxModel", it) }
            addProperty("maxRetries", maxRetries)
            addProperty("createdAt", createdAt)
            addProperty("updatedAt", updatedAt)
        }
    }

    /**
     * 转换为 Claude SDK settings.json 格式
     *
     * 输出格式：
     * {
     *   "providers": [
     *     {
     *       "id": "my-provider",
     *       "name": "显示名称",
     *       "settingsConfig": {
     *         "env": {
     *           "ANTHROPIC_AUTH_TOKEN": "sk-xxx",
     *           "ANTHROPIC_BASE_URL": "https://api.anthropic.com",
     *           "ANTHROPIC_DEFAULT_SONNET_MODEL": "claude-sonnet-4-20250514"
     *         }
     *       }
     *     }
     *   ]
     * }
     */
    fun toSettingsJson(): String {
        val providerObj = JsonObject().apply {
            addProperty("id", id)
            addProperty("name", name)

            val settingsConfig = JsonObject().apply {
                val env = JsonObject().apply {
                    apiKey?.let { addProperty("ANTHROPIC_AUTH_TOKEN", it) }
                    baseUrl?.let { addProperty("ANTHROPIC_BASE_URL", it) }
                    sonnetModel?.let { addProperty("ANTHROPIC_DEFAULT_SONNET_MODEL", it) }
                    opusModel?.let { addProperty("ANTHROPIC_DEFAULT_OPUS_MODEL", it) }
                    maxModel?.let { addProperty("ANTHROPIC_DEFAULT_MAX_MODEL", it) }
                }
                add("env", env)
            }
            add("settingsConfig", settingsConfig)
        }

        val providersArray = JsonArray().apply {
            add(providerObj)
        }

        val root = JsonObject().apply {
            add("providers", providersArray)
        }

        return root.toString()
    }

    /**
     * 转为 ModelConfig
     */
    fun toModelConfig(): ModelConfig {
        return ModelConfig(
            provider = provider,
            model = model,
            apiKey = apiKey,
            baseUrl = baseUrl,
            sonnetModel = sonnetModel,
            opusModel = opusModel,
            maxModel = maxModel,
            maxRetries = maxRetries
        )
    }
}
