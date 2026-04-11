package com.github.xingzhewa.ccgui.util

import org.junit.Test
import org.junit.Before
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * I18nManager 单元测试
 */
class I18nManagerTest {

    @Before
    fun setup() {
        // 清空缓存以确保测试隔离
        I18nManager.clearCache()
        I18nManager.setLocale(Locale.SIMPLIFIED_CHINESE)
    }

    @Test
    fun testMessageShouldReturnChineseText() {
        val result = I18nManager.message("plugin.name")
        assertEquals("CC 助手", result)
    }

    @Test
    fun testMessageWithParameters() {
        val result = I18nManager.message("service.project", "MyProject")
        assertEquals("项目服务: MyProject", result)
    }

    @Test
    fun testMessageWithFallbackToDefaultValue() {
        val result = I18nManager.message("non.existent.key", "Default Value")
        assertEquals("Default Value", result)
    }

    @Test
    fun testGetTextFormattedShouldFormatParameters() {
        val result = I18nManager.getTextFormatted("messages.MyBundle", "service.project", "TestProject")
        assertTrue(result.contains("TestProject"))
    }

    @Test
    fun testCommandShouldReturnCommandText() {
        val result = I18nManager.command("command.session.new")
        assertEquals("新建会话", result)
    }

    @Test
    fun testSetLocaleShouldChangeLocale() {
        I18nManager.setLocale(Locale.ENGLISH)
        assertEquals(Locale.ENGLISH, I18nManager.getLocale())
        assertEquals("en", I18nManager.getLanguage())
    }

    @Test
    fun testIsChineseShouldReturnCorrectStatus() {
        I18nManager.setLocale(Locale.SIMPLIFIED_CHINESE)
        assertTrue(I18nManager.isChinese())
        assertTrue(!I18nManager.isEnglish())
    }

    @Test
    fun testIsEnglishShouldReturnCorrectStatus() {
        I18nManager.setLocale(Locale.ENGLISH)
        assertTrue(I18nManager.isEnglish())
        assertTrue(!I18nManager.isChinese())
    }

    @Test
    fun testClearCacheShouldClearAllCaches() {
        // 预加载资源以建立缓存
        I18nManager.preloadBundles()
        val statsBefore = I18nManager.getCacheStats()
        assertTrue(statsBefore["bundleCacheSize"]!! > 0)

        // 清空缓存
        I18nManager.clearCache()
        val statsAfter = I18nManager.getCacheStats()
        assertEquals(0, statsAfter["bundleCacheSize"])
        assertEquals(0, statsAfter["textCacheSize"])
    }

    @Test
    fun testGetCacheStatsShouldReturnValidStats() {
        val stats = I18nManager.getCacheStats()
        assertNotNull(stats["bundleCacheSize"])
        assertNotNull(stats["textCacheSize"])
    }

    @Test
    fun testI18nTextResolveShouldReturnTranslatedText() {
        val i18nText = I18nManager.I18nText.message("plugin.name")
        val result = i18nText.resolve()
        assertEquals("CC 助手", result)
    }

    @Test
    fun testI18nTextResolveFormattedShouldFormatParameters() {
        val i18nText = I18nManager.I18nText.message("service.project")
        val result = i18nText.resolveFormatted("TestProject")
        assertTrue(result.contains("TestProject"))
    }

    @Test
    fun testSupportedLocalesShouldContainAllDefinedLocales() {
        val locales = I18nManager.SupportedLocale.entries
        assertTrue(locales.size >= 2) // 至少有中英文
        assertTrue(locales.any { it.locale == Locale.SIMPLIFIED_CHINESE })
        assertTrue(locales.any { it.locale == Locale.ENGLISH })
    }
}
