package com.github.claudecode.ccgui.application.config

import com.github.claudecode.ccgui.infrastructure.storage.ConfigStorage
import com.github.claudecode.ccgui.util.I18nManager
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.Locale

/**
 * 语言配置管理器
 *
 * 管理应用的语言设置，支持运行时切换语言
 */
@Service(Service.Level.PROJECT)
class LocaleConfigManager(private val project: Project) {

    private val log = logger<LocaleConfigManager>()
    private val configStorage: ConfigStorage by lazy { ConfigStorage.getInstance(project) }

    init {
        // 应用保存的语言设置
        applySavedLocale()
        log.info("LocaleConfigManager initialized with locale: ${I18nManager.getLocale().displayName}")
    }

    /**
     * 获取当前语言
     */
    fun getCurrentLocale(): Locale {
        return I18nManager.getLocale()
    }

    /**
     * 设置当前语言
     *
     * @param locale 新的语言环境
     */
    fun setLocale(locale: Locale) {
        log.info("Setting locale to: ${locale.displayName}")

        // 更新 I18nManager 的语言
        I18nManager.setLocale(locale)

        // 保存到配置
        saveLocale(locale)

        // 发布语言变更事件
        com.github.claudecode.ccgui.infrastructure.eventbus.EventBus.publish(
            com.github.claudecode.ccgui.infrastructure.eventbus.LocaleChangedEvent(locale)
        )
    }

    /**
     * 通过语言代码设置语言
     *
     * @param languageCode 语言代码（如 "zh", "en", "ja"）
     */
    fun setLocaleByCode(languageCode: String) {
        val locale = when (languageCode.lowercase()) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            "ja" -> Locale.JAPANESE
            "ko" -> Locale.KOREAN
            "es" -> Locale("es", "ES")
            "fr" -> Locale.FRANCE
            "de" -> Locale.GERMANY
            "pt" -> Locale("pt", "BR")
            else -> {
                log.warn("Unknown language code: $languageCode, using default")
                Locale.getDefault()
            }
        }
        setLocale(locale)
    }

    /**
     * 获取支持的语言列表
     */
    fun getSupportedLocales(): List<I18nManager.SupportedLocale> {
        return I18nManager.SupportedLocale.entries.toList()
    }

    /**
     * 获取当前语言代码
     */
    fun getCurrentLanguageCode(): String {
        return I18nManager.getLanguage()
    }

    /**
     * 检查是否为中文环境
     */
    fun isChinese(): Boolean {
        return I18nManager.isChinese()
    }

    /**
     * 检查是否为英文环境
     */
    fun isEnglish(): Boolean {
        return I18nManager.isEnglish()
    }

    /**
     * 重置为系统默认语言
     */
    fun resetToSystemLocale() {
        val systemLocale = Locale.getDefault()
        log.info("Resetting to system locale: ${systemLocale.displayName}")
        setLocale(systemLocale)
    }

    /**
     * 保存语言设置到配置
     */
    private fun saveLocale(locale: Locale) {
        val languageTag = locale.toLanguageTag()
        configStorage.setState("locale", languageTag)
        log.debug("Saved locale to config: $languageTag")
    }

    /**
     * 从配置加载并应用语言设置
     */
    private fun applySavedLocale() {
        val savedLocale = configStorage.getState("locale")
        if (savedLocale != null) {
            try {
                val locale = parseLocale(savedLocale)
                I18nManager.setLocale(locale)
                log.info("Applied saved locale: ${locale.displayName}")
            } catch (e: Exception) {
                log.warn("Failed to apply saved locale: $savedLocale", e)
            }
        }
    }

    /**
     * 解析语言标签为 Locale 对象
     */
    private fun parseLocale(languageTag: String): Locale {
        val parts = languageTag.split("-")
        return when (parts.size) {
            1 -> Locale(parts[0])
            2 -> Locale(parts[0], parts[1])
            else -> Locale(parts[0], parts[1], parts.getOrElse(2) { "" })
        }
    }

    companion object {
        fun getInstance(project: Project): LocaleConfigManager =
            project.getService(LocaleConfigManager::class.java)
    }
}
