package com.github.xingzhewa.ccgui.infrastructure.storage

import com.github.xingzhewa.ccgui.model.config.AppConfig
import com.github.xingzhewa.ccgui.model.config.ThemeConfig
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * 配置存储
 *
 * 使用 IntelliJ PersistentStateComponent 持久化配置
 */
@State(name = "CCGuiConfig", storages = [Storage("ccgui-config.xml")])
class ConfigStorage(private val project: Project) : PersistentStateComponent<ConfigStorage.State> {

    private val logger = logger<ConfigStorage>()

    /**
     * 配置状态
     */
    data class State(
        var appConfig: String = "{}",
        var themesConfig: String = "[]",
        var customThemes: String = "[]",
        var lastThemeId: String = "jetbrains-dark",
        var version: Int = 1
    )

    private var state = State()

    // 应用配置
    private var _appConfig: AppConfig? = null
    val appConfig: AppConfig
        get() {
            if (_appConfig == null) {
                _appConfig = JsonUtils.fromJson(state.appConfig, AppConfig::class.java) ?: AppConfig()
            }
            return _appConfig!!
        }

    // 主题配置
    private var _themes: List<ThemeConfig>? = null
    val themes: List<ThemeConfig>
        get() {
            if (_themes == null) {
                _themes = parseThemeList(state.themesConfig) ?: ThemeConfig.getPresets()
            }
            return _themes!!
        }

    // 自定义主题
    private var _customThemes: List<ThemeConfig>? = null
    val customThemes: List<ThemeConfig>
        get() {
            if (_customThemes == null) {
                _customThemes = parseThemeList(state.customThemes) ?: emptyList()
            }
            return _customThemes!!
        }

    // 当前主题 ID
    var currentThemeId: String
        get() = state.lastThemeId
        set(value) {
            state.lastThemeId = value
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        // 清除缓存
        _appConfig = null
        _themes = null
        _customThemes = null
    }

    /**
     * 解析主题列表
     */
    private fun parseThemeList(json: String): List<ThemeConfig>? {
        return try {
            val array = JsonUtils.parseArray(json) ?: return null
            array.mapNotNull { ThemeConfig.fromJson(it.asJsonObject) }
        } catch (e: Exception) {
            logger.warn("Failed to parse theme list: ${e.message}")
            null
        }
    }

    /**
     * 保存应用配置
     */
    fun saveAppConfig(config: AppConfig) {
        _appConfig = config
        state.appConfig = JsonUtils.toJson(config)
    }

    /**
     * 保存主题列表
     */
    fun saveThemes(themes: List<ThemeConfig>) {
        _themes = themes
        state.themesConfig = JsonUtils.toJson(themes)
    }

    /**
     * 保存自定义主题
     */
    fun saveCustomThemes(themes: List<ThemeConfig>) {
        _customThemes = themes
        state.customThemes = JsonUtils.toJson(themes)
    }

    /**
     * 添加自定义主题
     */
    fun addCustomTheme(theme: ThemeConfig) {
        val current = customThemes.toMutableList()
        current.removeAll { it.id == theme.id }
        current.add(theme)
        saveCustomThemes(current)
    }

    /**
     * 删除自定义主题
     */
    fun deleteCustomTheme(themeId: String) {
        val current = customThemes.toMutableList()
        current.removeAll { it.id == themeId }
        saveCustomThemes(current)
    }

    /**
     * 获取当前主题
     */
    fun getCurrentTheme(): ThemeConfig {
        val theme = themes.find { it.id == currentThemeId }
            ?: customThemes.find { it.id == currentThemeId }
            ?: ThemeConfig.JETBRAINS_DARK
        return theme
    }

    /**
     * 获取所有可用的主题
     */
    fun getAllThemes(): List<ThemeConfig> {
        return themes + customThemes
    }

    /**
     * 重置为默认配置
     */
    fun resetToDefaults() {
        _appConfig = AppConfig()
        _themes = ThemeConfig.getPresets()
        _customThemes = emptyList()
        state.lastThemeId = "jetbrains-dark"

        saveAppConfig(_appConfig!!)
        saveThemes(_themes!!)
        saveCustomThemes(_customThemes!!)
    }

    companion object {
        @Volatile
        private var instance: ConfigStorage? = null

        fun getInstance(project: Project): ConfigStorage {
            return instance ?: synchronized(this) {
                instance ?: project.getService(ConfigStorage::class.java).also { instance = it }
            }
        }
    }
}
