package com.github.xingzhewa.ccgui.application.config

import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.ConfigChangedEvent
import com.github.xingzhewa.ccgui.infrastructure.storage.ConfigStorage
import com.github.xingzhewa.ccgui.model.config.AppConfig
import com.github.xingzhewa.ccgui.model.config.ModelConfig
import com.github.xingzhewa.ccgui.model.config.ProviderProfile
import com.github.xingzhewa.ccgui.model.config.SpecialProviderIds
import com.github.xingzhewa.ccgui.model.config.ThemeConfig
import com.github.xingzhewa.ccgui.util.LocalSettingsReader
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * 配置管理器
 *
 * 管理应用配置、主题配置等
 * 所有配置存储在项目级 ccgui-config.xml 中，每个项目独立
 */
@Service(Service.Level.PROJECT)
class ConfigManager(private val project: Project) : Disposable {

    private val log = logger<ConfigManager>()

    // 项目级存储
    private val configStorage: ConfigStorage by lazy { ConfigStorage.getInstance(project) }

    init {
        log.info("ConfigManager initialized")
    }

    // ===== 应用配置 =====

    /**
     * 获取应用配置
     */
    fun getAppConfig(): AppConfig = configStorage.appConfig

    /**
     * 保存应用配置
     */
    fun saveAppConfig(config: AppConfig) {
        configStorage.saveAppConfig(config)
        EventBus.publish(ConfigChangedEvent("appConfig", config))
        log.info("App config saved")
    }

    /**
     * 更新模型配置
     */
    fun updateModelConfig(modelConfig: ModelConfig) {
        val current = getAppConfig()
        saveAppConfig(current.copy(modelConfig = modelConfig))
        log.info("Model config updated: ${modelConfig.model}")
    }

    // ===== 主题配置 =====

    /**
     * 获取当前主题
     */
    fun getCurrentTheme(): ThemeConfig = configStorage.getCurrentTheme()

    /**
     * 设置当前主题
     */
    fun setCurrentTheme(themeId: String) {
        configStorage.currentThemeId = themeId
        EventBus.publish(ConfigChangedEvent("theme", themeId))
        log.info("Theme changed to: $themeId")
    }

    /**
     * 获取所有主题
     */
    fun getAllThemes(): List<ThemeConfig> = configStorage.getAllThemes()

    /**
     * 获取预设主题
     */
    fun getPresetThemes(): List<ThemeConfig> = configStorage.themes

    /**
     * 获取自定义主题
     */
    fun getCustomThemes(): List<ThemeConfig> = configStorage.customThemes

    /**
     * 保存自定义主题
     */
    fun saveCustomTheme(theme: ThemeConfig) {
        configStorage.addCustomTheme(theme)
        EventBus.publish(ConfigChangedEvent("customTheme", theme))
        log.info("Custom theme saved: ${theme.id}")
    }

    /**
     * 删除自定义主题
     */
    fun deleteCustomTheme(themeId: String) {
        configStorage.deleteCustomTheme(themeId)
        EventBus.publish(ConfigChangedEvent("customThemeDeleted", themeId))
        log.info("Custom theme deleted: $themeId")
    }

    /**
     * 重置为默认配置
     */
    fun resetToDefaults() {
        configStorage.resetToDefaults()
        EventBus.publish(ConfigChangedEvent("reset", null))
        log.info("Config reset to defaults")
    }

    // ===== Provider Profiles =====

    /**
     * 获取所有 Provider Profiles
     */
    fun getProviderProfiles(): List<ProviderProfile> {
        return getAppConfig().providerProfiles
    }

    /**
     * 获取激活的 Profile（返回 ModelConfig）
     * 如果有激活的 profile，从中读取；否则返回默认的 modelConfig
     *
     * 特殊处理：
     * - LOCAL_SETTINGS: 从 ~/.claude/settings.json 读取
     * - CLI_LOGIN: 从 Claude CLI 的认证状态读取（暂未实现）
     * - DISABLED: 返回禁用状态的配置
     */
    fun getActiveModelConfig(): ModelConfig {
        val config = getAppConfig()
        val activeProfileId = config.activeProfileId

        // 处理特殊供应商
        when (activeProfileId) {
            SpecialProviderIds.LOCAL_SETTINGS -> {
                // 从本地 settings.json 读取
                val localProvider = LocalSettingsReader.getAllProviders().firstOrNull()
                if (localProvider != null) {
                    // 推断 provider 类型
                    val provider = when {
                        localProvider.baseUrl?.contains("anthropic.com") == true -> "anthropic"
                        localProvider.baseUrl?.contains("openai.com") == true -> "openai"
                        localProvider.baseUrl?.contains("deepseek") == true -> "deepseek"
                        else -> "anthropic"
                    }
                    return ModelConfig(
                        provider = provider,
                        model = localProvider.sonnetModel ?: "claude-sonnet-4-20250514",
                        apiKey = localProvider.authToken,
                        baseUrl = localProvider.baseUrl,
                        sonnetModel = localProvider.sonnetModel,
                        opusModel = localProvider.opusModel,
                        maxModel = localProvider.maxModel
                    )
                }
                // 如果本地设置不存在或解析失败，返回默认配置
                log.warn("LOCAL_SETTINGS profile selected but no valid settings found")
                return config.modelConfig
            }
            SpecialProviderIds.DISABLED -> {
                // 禁用状态，返回空配置
                return ModelConfig(provider = "disabled", model = "")
            }
            SpecialProviderIds.CLI_LOGIN -> {
                // CLI Login 暂未实现，使用默认配置
                log.warn("CLI_LOGIN profile not yet implemented")
                return config.modelConfig
            }
        }

        // 普通 profile
        val activeProfile = config.providerProfiles.find { it.id == activeProfileId }
        return activeProfile?.toModelConfig() ?: config.modelConfig
    }

    /**
     * 获取激活的 Profile ID
     */
    fun getActiveProfileId(): String? {
        return getAppConfig().activeProfileId
    }

    /**
     * 创建或更新 Provider Profile
     */
    fun saveProviderProfile(profile: ProviderProfile) {
        val current = getAppConfig()
        val profiles = current.providerProfiles.toMutableList()
        val existingIndex = profiles.indexOfFirst { it.id == profile.id }
        if (existingIndex >= 0) {
            profiles[existingIndex] = profile
        } else {
            profiles.add(profile)
        }
        saveAppConfig(current.copy(providerProfiles = profiles))
        log.info("Provider profile saved: ${profile.id}")
    }

    /**
     * 删除 Provider Profile
     */
    fun deleteProviderProfile(profileId: String) {
        val current = getAppConfig()
        val profiles = current.providerProfiles.filter { it.id != profileId }
        val newActiveId = if (current.activeProfileId == profileId) {
            profiles.firstOrNull()?.id
        } else {
            current.activeProfileId
        }
        saveAppConfig(current.copy(providerProfiles = profiles, activeProfileId = newActiveId))
        log.info("Provider profile deleted: $profileId")
    }

    /**
     * 设置激活的 Profile
     */
    fun setActiveProviderProfile(profileId: String?) {
        val current = getAppConfig()
        saveAppConfig(current.copy(activeProfileId = profileId))
        log.info("Active provider profile set: $profileId")
    }

    /**
     * 确保特殊供应商 Profile 存在
     * 在应用启动时调用，确保 LOCAL_SETTINGS 等特殊 profile 存在
     */
    fun ensureSpecialProfiles() {
        val current = getAppConfig()
        val profiles = current.providerProfiles.toMutableList()
        var changed = false

        // 确保 LOCAL_SETTINGS profile 存在
        if (profiles.none { it.id == SpecialProviderIds.LOCAL_SETTINGS }) {
            profiles.add(ProviderProfile.createSpecialProvider(SpecialProviderIds.LOCAL_SETTINGS))
            changed = true
            log.info("Added LOCAL_SETTINGS profile")
        }

        // 如果没有激活的 profile 且本地设置存在，自动激活 LOCAL_SETTINGS
        if (current.activeProfileId == null && LocalSettingsReader.isSettingsFileExists()) {
            // 尝试激活第一个有效的 profile
            val firstValidProfile = profiles.firstOrNull { it.id != SpecialProviderIds.DISABLED }
            if (firstValidProfile != null) {
                saveAppConfig(current.copy(providerProfiles = profiles, activeProfileId = firstValidProfile.id))
                log.info("Auto-activated profile: ${firstValidProfile.id}")
                return
            }
        }

        if (changed) {
            saveAppConfig(current.copy(providerProfiles = profiles))
        }
    }

    /**
     * 检查本地设置文件是否存在
     */
    fun isLocalSettingsAvailable(): Boolean {
        return LocalSettingsReader.isSettingsFileExists()
    }

    /**
     * 获取本地设置文件路径
     */
    fun getLocalSettingsPath(): String {
        return LocalSettingsReader.getSettingsFilePath()
    }

    /**
     * 从本地设置创建 Profile
     *
     * @param providerId 本地设置中的 provider ID
     * @return 创建的 Profile
     */
    fun createProfileFromLocalSettings(providerId: String): ProviderProfile? {
        val localProvider = LocalSettingsReader.getAllProviders().find { it.id == providerId }
            ?: return null

        val provider = when {
            localProvider.baseUrl?.contains("anthropic.com") == true -> "anthropic"
            localProvider.baseUrl?.contains("openai.com") == true -> "openai"
            localProvider.baseUrl?.contains("deepseek") == true -> "deepseek"
            else -> "anthropic"
        }

        return ProviderProfile(
            id = "local_$providerId",
            name = localProvider.name,
            provider = provider,
            source = "local",
            model = localProvider.sonnetModel ?: "claude-sonnet-4-20250514",
            apiKey = localProvider.authToken,
            baseUrl = localProvider.baseUrl,
            sonnetModel = localProvider.sonnetModel,
            opusModel = localProvider.opusModel,
            maxModel = localProvider.maxModel
        )
    }

    /**
     * 重新排序 Provider Profiles
     *
     * @param orderedIds 按新顺序排列的 Profile ID 列表
     */
    fun reorderProviderProfiles(orderedIds: List<String>) {
        val current = getAppConfig()
        val profilesMap = current.providerProfiles.associateBy { it.id }
        val reorderedProfiles = orderedIds.mapNotNull { profilesMap[it] }

        // 添加未在 orderedIds 中的配置（如特殊供应商）
        val remainingProfiles = current.providerProfiles.filter { it.id !in orderedIds }
        val finalProfiles = reorderedProfiles + remainingProfiles

        saveAppConfig(current.copy(providerProfiles = finalProfiles))
        log.info("Provider profiles reordered")
    }

    /**
     * 转换 cc-switch 配置为本地配置
     *
     * @param profileId 要转换的 Profile ID
     * @return 转换后的新 Profile
     */
    fun convertCcSwitchProfile(profileId: String): ProviderProfile? {
        val current = getAppConfig()
        val profile = current.providerProfiles.find { it.id == profileId } ?: return null

        // 只有 cc-switch 来源的配置可以转换
        if (profile.source != "cc-switch") {
            log.warn("Cannot convert non-cc-switch profile: $profileId")
            return null
        }

        // 创建本地配置副本
        val localProfile = profile.toLocalProfile()

        // 保存新配置
        saveProviderProfile(localProfile)

        // 删除原配置
        deleteProviderProfile(profileId)

        // 如果原配置是激活的，激活新配置
        if (current.activeProfileId == profileId) {
            setActiveProviderProfile(localProfile.id)
        }

        log.info("Converted cc-switch profile to local: ${profileId} -> ${localProfile.id}")
        return localProfile
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): ConfigManager =
            project.getService(ConfigManager::class.java)
    }
}
