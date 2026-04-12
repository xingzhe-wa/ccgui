package com.github.claudecode.ccgui.infrastructure.storage

import com.github.claudecode.ccgui.model.config.AppConfig
import com.github.claudecode.ccgui.model.config.ModelConfig
import com.github.claudecode.ccgui.model.config.ThemeConfig
import com.github.claudecode.ccgui.util.JsonUtils
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Before
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ConfigStorage 单元测试
 */
class ConfigStorageTest : LightPlatformTestCase() {

    private lateinit var configStorage: ConfigStorage

    @Before
    override fun setUp() {
        super.setUp()
        configStorage = ConfigStorage.getInstance(project)
    }

    @Test
    fun `test getState - should return null for non-existent key`() {
        val result = configStorage.getState("non.existent.key")
        assertNull(result)
    }

    @Test
    fun `test setState and getState - should persist value`() {
        configStorage.setState("test.key", "test.value")
        val result = configStorage.getState("test.key")
        assertEquals("test.value", result)
    }

    @Test
    fun `test setState - should overwrite existing value`() {
        configStorage.setState("test.key", "value1")
        configStorage.setState("test.key", "value2")
        val result = configStorage.getState("test.key")
        assertEquals("value2", result)
    }

    @Test
    fun `test removeState - should remove key`() {
        configStorage.setState("test.key", "test.value")
        configStorage.removeState("test.key")
        val result = configStorage.getState("test.key")
        assertNull(result)
    }

    @Test
    fun `test appConfig - should return default config`() {
        val appConfig = configStorage.appConfig
        assertNotNull(appConfig)
        assertNotNull(appConfig.modelConfig)
        assertTrue(appConfig.providerProfiles.isNotEmpty())
    }

    @Test
    fun `test saveAppConfig - should persist config`() {
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

    @Test
    fun `test currentThemeId - should default to jetbrains-dark`() {
        assertEquals("jetbrains-dark", configStorage.currentThemeId)
    }

    @Test
    fun `test setCurrentThemeId - should update theme`() {
        configStorage.currentThemeId = "test-theme"
        assertEquals("test-theme", configStorage.currentThemeId)
    }

    @Test
    fun `test themes - should return preset themes`() {
        val themes = configStorage.themes
        assertTrue(themes.isNotEmpty())
        assertTrue(themes.any { it.id == "jetbrains-dark" })
        assertTrue(themes.any { it.id == "jetbrains-light" })
    }

    @Test
    fun `test addCustomTheme - should add to custom themes`() {
        val customTheme = ThemeConfig(
            id = "test-custom",
            name = "Test Custom",
            isDark = false
        )

        configStorage.addCustomTheme(customTheme)
        val customThemes = configStorage.customThemes

        assertTrue(customThemes.any { it.id == "test-custom" })
    }

    @Test
    fun `test deleteCustomTheme - should remove from custom themes`() {
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

    @Test
    fun `test resetToDefaults - should reset all configs`() {
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

    @Test
    fun `test extraData - should persist custom key-value pairs`() {
        configStorage.setState("custom.test.key", "custom.test.value")
        configStorage.setState("custom.test.number", "123")

        assertEquals("custom.test.value", configStorage.getState("custom.test.key"))
        assertEquals("123", configStorage.getState("custom.test.number"))
    }

    @Test
    fun `test extraData - should handle special characters`() {
        val specialValue = "value with \"quotes\" and 'apostrophes'"
        configStorage.setState("special.key", specialValue)

        assertEquals(specialValue, configStorage.getState("special.key"))
    }

    @Test
    fun `test extraData - should handle unicode characters`() {
        val unicodeValue = "测试中文 🎉"
        configStorage.setState("unicode.key", unicodeValue)

        assertEquals(unicodeValue, configStorage.getState("unicode.key"))
    }

    @Test
    fun `test extraData - should handle empty values`() {
        configStorage.setState("empty.key", "")
        assertEquals("", configStorage.getState("empty.key"))
    }

    @Test
    fun `test getAllThemes - should include custom themes`() {
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

    @Test
    fun `test getCurrentTheme - should return valid theme`() {
        val theme = configStorage.getCurrentTheme()
        assertNotNull(theme)
        assertEquals("jetbrains-dark", theme.id)
    }

    @Test
    fun `test getCurrentTheme - should return custom theme when set`() {
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

    @Test
    fun `test state persistence - should survive component reload`() {
        // 设置状态
        configStorage.setState("persist.test", "persist.value")

        // 模拟组件重新加载
        configStorage.loadState(configStorage.getState())

        // 验证状态保持
        assertEquals("persist.value", configStorage.getState("persist.test"))
    }
}
