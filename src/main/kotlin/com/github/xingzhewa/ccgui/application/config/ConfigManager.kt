package com.github.xingzhewa.ccgui.application.config

import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.ConfigChangedEvent
import com.github.xingzhewa.ccgui.infrastructure.storage.ConfigStorage
import com.github.xingzhewa.ccgui.model.config.AppConfig
import com.github.xingzhewa.ccgui.model.config.ModelConfig
import com.github.xingzhewa.ccgui.model.config.ProviderProfile
import com.github.xingzhewa.ccgui.model.config.ThemeConfig
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
     */
    fun getActiveModelConfig(): ModelConfig {
        val config = getAppConfig()
        val activeProfile = config.providerProfiles.find { it.id == config.activeProfileId }
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
