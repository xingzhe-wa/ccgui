package com.github.claudecode.ccgui.infrastructure.storage

import com.github.claudecode.ccgui.model.config.AppConfig
import com.github.claudecode.ccgui.model.config.ModelConfig
import com.github.claudecode.ccgui.model.config.ThemeConfig
import com.github.claudecode.ccgui.util.JsonUtils
import com.intellij.testFramework.LightPlatformTestCase

/**
 * ConfigStorage 单元测试
 */
class ConfigStorageTest : LightPlatformTestCase() {

    private lateinit var configStorage: ConfigStorage

    override fun setUp() {
        super.setUp()
        configStorage = ConfigStorage.getInstance(project)
    }

    fun testGetStateShouldReturnNullForNonExistentKey() {
        val result = configStorage.getState("non.existent.key")
        assertNull(result)
    }

    fun testSetStateAndGetStateShouldPersistValue() {
        configStorage.setState("test.key", "test.value")
        val result = configStorage.getState("test.key")
        assertEquals("test.value", result)
    }

    fun testSetStateShouldOverwriteExistingValue() {
        configStorage.setState("test.key", "value1")
        configStorage.setState("test.key", "value2")
        val result = configStorage.getState("test.key")
        assertEquals("value2", result)
    }

    fun testRemoveStateShouldRemoveKey() {
        configStorage.setState("test.key", "test.value")
        configStorage.removeState("test.key")
        val result = configStorage.getState("test.key")
        assertNull(result)
    }

    fun testAppConfigShouldReturnDefaultConfig() {
        val appConfig = configStorage.appConfig
        assertNotNull(appConfig)
        assertNotNull(appConfig.modelConfig)
        assertTrue(appConfig.providerProfiles.isNotEmpty())
    }

    fun testSaveAppConfigShouldPersistConfig() {
        val newConfig = AppConfig(
            modelConfig = ModelConfig(
                provider = "test-provider",
                model = "test-model"
            )
        )
        configStorage.saveAppConfig(newConfig)

        val loaded = configStorage.appConfig
        assertEquals("test-provider", loaded.modelConfig.provider)
        assertEquals("test-model", loaded.modelConfig.model)
    }

    fun testCurrentThemeIdShouldDefaultToJetbrainsDark() {
        assertEquals("jetbrains-dark", configStorage.currentThemeId)
    }

    fun testSetCurrentThemeIdShouldUpdateTheme() {
        configStorage.currentThemeId = "test-theme"
        assertEquals("test-theme", configStorage.currentThemeId)
    }

    fun testThemesShouldReturnPresetThemes() {
        val themes = configStorage.themes
        assertTrue(themes.isNotEmpty())
        assertTrue(themes.any { it.id == "jetbrains-dark" })
        assertTrue(themes.any { it.id == "jetbrains-light" })
    }

    fun testAddCustomThemeShouldAddToCustomThemes() {
        val customTheme = ThemeConfig(
            id = "test-custom",
            name = "Test Custom",
            isDark = false
        )

        configStorage.addCustomTheme(customTheme)
        val customThemes = configStorage.customThemes

        assertTrue(customThemes.any { it.id == "test-custom" })
    }

    fun testDeleteCustomThemeShouldRemoveFromCustomThemes() {
        val customTheme = ThemeConfig(
            id = "test-custom",
            name = "Test Custom",
            isDark = false
        )

        configStorage.addCustomTheme(customTheme)
        configStorage.deleteCustomTheme("test-custom")

        val customThemes = configStorage.customThemes
        assertTrue(customThemes.none { it.id == "test-custom" })
    }

    fun testResetToDefaultsShouldResetAllConfigs() {
        // 修改一些配置
        configStorage.currentThemeId = "custom-theme"
        configStorage.addCustomTheme(
            ThemeConfig(id = "test", name = "Test", isDark = false)
        )

        // 重置
        configStorage.resetToDefaults()

        // 验证重置
        assertEquals("jetbrains-dark", configStorage.currentThemeId)
        assertTrue(configStorage.customThemes.isEmpty())
    }

    fun testExtraDataShouldPersistCustomKeyValuePairs() {
        configStorage.setState("custom.test.key", "custom.test.value")
        configStorage.setState("custom.test.number", "123")

        assertEquals("custom.test.value", configStorage.getState("custom.test.key"))
        assertEquals("123", configStorage.getState("custom.test.number"))
    }

    fun testExtraDataShouldHandleSpecialCharacters() {
        val specialValue = "value with \"quotes\" and 'apostrophes'"
        configStorage.setState("special.key", specialValue)

        assertEquals(specialValue, configStorage.getState("special.key"))
    }

    fun testExtraDataShouldHandleUnicodeCharacters() {
        val unicodeValue = "测试中文 🎉"
        configStorage.setState("unicode.key", unicodeValue)

        assertEquals(unicodeValue, configStorage.getState("unicode.key"))
    }

    fun testExtraDataShouldHandleEmptyValues() {
        configStorage.setState("empty.key", "")
        assertEquals("", configStorage.getState("empty.key"))
    }

    fun testGetAllThemesShouldIncludeCustomThemes() {
        val customTheme = ThemeConfig(
            id = "test-custom",
            name = "Test Custom",
            isDark = false
        )

        configStorage.addCustomTheme(customTheme)
        val allThemes = configStorage.getAllThemes()

        assertTrue(allThemes.any { it.id == "test-custom" })
        assertTrue(allThemes.any { it.id == "jetbrains-dark" })
    }

    fun testGetCurrentThemeShouldReturnValidTheme() {
        val theme = configStorage.getCurrentTheme()
        assertNotNull(theme)
        assertEquals("jetbrains-dark", theme.id)
    }

    fun testGetCurrentThemeShouldReturnCustomThemeWhenSet() {
        val customTheme = ThemeConfig(
            id = "my-custom",
            name = "My Custom",
            isDark = true
        )

        configStorage.addCustomTheme(customTheme)
        configStorage.currentThemeId = "my-custom"

        val theme = configStorage.getCurrentTheme()
        assertEquals("my-custom", theme.id)
    }

    fun testStatePersistenceShouldSurviveComponentReload() {
        // 设置状态
        configStorage.setState("persist.test", "persist.value")

        // 模拟组件重新加载
        configStorage.loadState(configStorage.getState())

        // 验证状态保持
        assertEquals("persist.value", configStorage.getState("persist.test"))
    }
}
