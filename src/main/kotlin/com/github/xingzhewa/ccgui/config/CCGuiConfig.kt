package com.github.xingzhewa.ccgui.config

import com.github.xingzhewa.ccgui.infrastructure.storage.ConfigStorage
import com.github.xingzhewa.ccgui.model.config.AppConfig
import com.github.xingzhewa.ccgui.model.config.ModelConfig
import com.github.xingzhewa.ccgui.model.config.ThemeConfig
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.project.Project

/**
 * CCGui 统一配置门面
 *
 * 由 plugin.xml 注册为 ProjectService。
 * 委托给 ConfigStorage 实现实际的持久化。
 * 兼容 MyToolWindowFactory 的配置访问需求。
 */
@Service(Service.Level.PROJECT)
class CCGuiConfig(private val project: Project) {

    private val log = logger<CCGuiConfig>()

    private val configStorage: ConfigStorage by lazy { ConfigStorage.getInstance(project) }

    /**
     * 获取应用配置
     */
    fun getConfig(): AppConfig = configStorage.appConfig

    /**
     * 更新应用配置
     */
    fun updateConfig(config: AppConfig) {
        configStorage.saveAppConfig(config)
        log.info("Config updated")
    }

    /**
     * 获取模型配置
     */
    fun getModelConfig(): ModelConfig = configStorage.appConfig.modelConfig

    /**
     * 更新模型配置
     */
    fun updateModelConfig(modelConfig: ModelConfig) {
        val current = configStorage.appConfig
        configStorage.saveAppConfig(current.copy(modelConfig = modelConfig))
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
        log.info("Theme set to: $themeId")
    }

    /**
     * 获取所有主题
     */
    fun getAllThemes(): List<ThemeConfig> = configStorage.getAllThemes()

    /**
     * 重置为默认配置
     */
    fun resetToDefaults() {
        configStorage.resetToDefaults()
        log.info("Config reset to defaults")
    }

    companion object {
        fun getInstance(project: Project): CCGuiConfig =
            project.getService(CCGuiConfig::class.java)
    }
}
