package com.github.claudecode.ccgui.util

import com.google.gson.JsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Claude CLI 本地设置读取器
 *
 * 从 ~/.claude/settings.json 读取 Claude CLI 的配置信息
 * 同时支持检测 CLI Login Provider 的认证状态
 */
object LocalSettingsReader {

    private val log = logger<LocalSettingsReader>()

    /**
     * Claude CLI 设置文件路径
     */
    private val settingsFilePath: Path by lazy {
        val homeDir = System.getProperty("user.home")
        Paths.get(homeDir, ".claude", "settings.json")
    }

    /**
     * 检查 Claude CLI 是否已登录
     *
     * CLI Login 模式通过检查 settings.json 中的认证状态来判断
     * 如果存在有效的 ANTHROPIC_AUTH_TOKEN，则认为 CLI 已登录
     *
     * @return true 如果 CLI 已登录（存在有效认证令牌）
     */
    fun isCliLoggedIn(): Boolean {
        val token = getCliAuthToken()
        return !token.isNullOrBlank()
    }

    /**
     * 获取 CLI 认证令牌
     *
     * 从 settings.json 中读取 ANTHROPIC_AUTH_TOKEN
     * 这是 CLI Login Provider 使用的认证方式
     *
     * @return 认证令牌或 null
     */
    fun getCliAuthToken(): String? {
        return readSettingsJson()?.getAsJsonArray("providers")
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonObject("settingsConfig")
            ?.getAsJsonObject("env")
            ?.get("ANTHROPIC_AUTH_TOKEN")
            ?.asString
    }

    /**
     * 获取 CLI Login Provider 的配置
     *
     * 如果 CLI 已登录，返回对应的 Provider 配置
     * 否则返回 null
     *
     * @return CLI Provider 配置或 null
     */
    fun getCliProvider(): LocalProvider? {
        if (!isCliLoggedIn()) {
            log.info("CLI not logged in, no CLI provider available")
            return null
        }

        val json = readSettingsJson() ?: return null
        val providersArray = json.getAsJsonArray("providers") ?: return null
        val firstProvider = providersArray.firstOrNull()?.asJsonObject ?: return null

        val settingsConfig = firstProvider.getAsJsonObject("settingsConfig")
        val env = settingsConfig?.getAsJsonObject("env")

        return LocalProvider(
            id = "cli-login",
            name = "CLI Login",
            authToken = env?.get("ANTHROPIC_AUTH_TOKEN")?.asString,
            baseUrl = env?.get("ANTHROPIC_BASE_URL")?.asString,
            sonnetModel = env?.get("ANTHROPIC_DEFAULT_SONNET_MODEL")?.asString,
            opusModel = env?.get("ANTHROPIC_DEFAULT_OPUS_MODEL")?.asString
                ?: env?.get("ANTHROPIC_OPUS_MODEL")?.asString,
            maxModel = env?.get("ANTHROPIC_DEFAULT_MAX_MODEL")?.asString
                ?: env?.get("ANTHROPIC_MAX_MODEL")?.asString
        )
    }

    /**
     * 检查设置文件是否存在
     */
    fun isSettingsFileExists(): Boolean {
        return Files.exists(settingsFilePath)
    }

    /**
     * 获取设置文件路径
     */
    fun getSettingsFilePath(): String = settingsFilePath.toString()

    /**
     * 读取并解析本地设置文件
     *
     * @return JsonObject 或 null（文件不存在或解析失败）
     */
    fun readSettingsJson(): JsonObject? {
        return try {
            if (!Files.exists(settingsFilePath)) {
                log.warn("Local settings file not found: $settingsFilePath")
                return null
            }

            val content = Files.readString(settingsFilePath)
            if (content.isBlank()) {
                log.warn("Local settings file is empty: $settingsFilePath")
                return null
            }

            JsonUtils.parseObject(content).also {
                log.info("Successfully read local settings from: $settingsFilePath")
            }
        } catch (e: Exception) {
            log.error("Failed to read local settings file: $settingsFilePath", e)
            null
        }
    }

    /**
     * 从本地设置获取第一个 Provider 的 API Token
     *
     * @return API Token 或 null
     */
    fun getAuthToken(): String? {
        return readSettingsJson()?.getAsJsonArray("providers")
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonObject("settingsConfig")
            ?.getAsJsonObject("env")
            ?.get("ANTHROPIC_AUTH_TOKEN")
            ?.asString
    }

    /**
     * 从本地设置获取第一个 Provider 的 Base URL
     *
     * @return Base URL 或 null
     */
    fun getBaseUrl(): String? {
        return readSettingsJson()?.getAsJsonArray("providers")
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonObject("settingsConfig")
            ?.getAsJsonObject("env")
            ?.get("ANTHROPIC_BASE_URL")
            ?.asString
    }

    /**
     * 从本地设置获取第一个 Provider 的 Sonnet 模型
     *
     * @return Sonnet 模型名称或 null
     */
    fun getSonnetModel(): String? {
        return readSettingsJson()?.getAsJsonArray("providers")
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonObject("settingsConfig")
            ?.getAsJsonObject("env")
            ?.get("ANTHROPIC_DEFAULT_SONNET_MODEL")
            ?.asString
    }

    /**
     * 从本地设置获取第一个 Provider 的 Opus 模型
     *
     * @return Opus 模型名称或 null
     */
    fun getOpusModel(): String? {
        // 尝试 ANTHROPIC_DEFAULT_OPUS_MODEL 或 ANTHROPIC_OPUS_MODEL
        val json = readSettingsJson()?.getAsJsonArray("providers")
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonObject("settingsConfig")
            ?.getAsJsonObject("env")

        return json?.get("ANTHROPIC_DEFAULT_OPUS_MODEL")?.asString
            ?: json?.get("ANTHROPIC_OPUS_MODEL")?.asString
    }

    /**
     * 从本地设置获取第一个 Provider 的 Max 模型
     *
     * @return Max 模型名称或 null
     */
    fun getMaxModel(): String? {
        val json = readSettingsJson()?.getAsJsonArray("providers")
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonObject("settingsConfig")
            ?.getAsJsonObject("env")

        return json?.get("ANTHROPIC_DEFAULT_MAX_MODEL")?.asString
            ?: json?.get("ANTHROPIC_MAX_MODEL")?.asString
    }

    /**
     * 获取本地设置中的所有 Provider 信息
     *
     * @return Provider 列表，每个包含 id, name, 和环境变量
     */
    fun getAllProviders(): List<LocalProvider> {
        val json = readSettingsJson() ?: return emptyList()
        val providersArray = json.getAsJsonArray("providers") ?: return emptyList()

        return providersArray.mapNotNull { element ->
            try {
                val obj = element.asJsonObject
                val settingsConfig = obj.getAsJsonObject("settingsConfig")
                val env = settingsConfig?.getAsJsonObject("env")

                LocalProvider(
                    id = obj.get("id")?.asString ?: return@mapNotNull null,
                    name = obj.get("name")?.asString ?: obj.get("id")?.asString ?: "Unknown",
                    authToken = env?.get("ANTHROPIC_AUTH_TOKEN")?.asString,
                    baseUrl = env?.get("ANTHROPIC_BASE_URL")?.asString,
                    sonnetModel = env?.get("ANTHROPIC_DEFAULT_SONNET_MODEL")?.asString,
                    opusModel = env?.get("ANTHROPIC_DEFAULT_OPUS_MODEL")?.asString
                        ?: env?.get("ANROPIC_OPUS_MODEL")?.asString,
                    maxModel = env?.get("ANTHROPIC_DEFAULT_MAX_MODEL")?.asString
                        ?: env?.get("ANTHROPIC_MAX_MODEL")?.asString
                )
            } catch (e: Exception) {
                log.warn("Failed to parse provider from local settings: ${e.message}")
                null
            }
        }
    }

    /**
     * 本地 Provider 信息
     */
    data class LocalProvider(
        val id: String,
        val name: String,
        val authToken: String?,
        val baseUrl: String?,
        val sonnetModel: String?,
        val opusModel: String?,
        val maxModel: String?
    )
}