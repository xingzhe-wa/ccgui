package com.github.xingzhewa.ccgui.application.config

import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.ConfigChangedEvent
import com.github.xingzhewa.ccgui.infrastructure.storage.ConfigStorage
import com.github.xingzhewa.ccgui.model.config.AppConfig
import com.github.xingzhewa.ccgui.model.config.ThemeConfig
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * 配置管理器
 *
 * 管理应用配置、主题配置等
 */
@Service(Service.Level.PROJECT)
class ConfigManager(private val project: Project) : Disposable {

    private val log = logger<ConfigManager>()
    private val configStorage: ConfigStorage by lazy { ConfigStorage.getInstance(project) }

    init {
        log.info("ConfigManager initialized")
    }

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
    fun updateModelConfig(modelConfig: com.github.xingzhewa.ccgui.model.config.ModelConfig) {
        val current = getAppConfig()
        saveAppConfig(current.copy(modelConfig = modelConfig))
        log.info("Model config updated: ${modelConfig.model}")
    }

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

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): ConfigManager =
            project.getService(ConfigManager::class.java)
    }
}
