package com.github.xingzhewa.ccgui.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.Locale
import java.util.ResourceBundle
import java.util.concurrent.ConcurrentHashMap

/**
 * 国际化管理器
 *
 * 提供统一的国际化文本获取服务，支持多语言切换和缓存优化
 *
 * 功能特性：
 * - 支持多资源束（Bundle）管理
 * - 自动缓存已加载的文本，减少 ResourceBundle 查找开销
 * - 支持运行时语言切换
 * - 提供默认值回退机制
 */
object I18nManager {

    private val logger = logger<I18nManager>()

    /** 资源束缓存 */
    private val bundleCache = ConcurrentHashMap<String, ResourceBundle>()

    /** 文本缓存 */
    private val textCache = ConcurrentHashMap<String, String>()

    /** 当前语言环境 */
    @Volatile
    private var currentLocale: Locale = Locale.getDefault()

    /** 支持的资源束 */
    private val supportedBundles = listOf(
        "messages.MyBundle",
        "messages.Commands"
    )

    /**
     * 支持的语言
     */
    enum class SupportedLocale(val locale: Locale, val displayName: String) {
        ZH_CN(Locale.SIMPLIFIED_CHINESE, "简体中文"),
        EN_US(Locale.ENGLISH, "English"),
        JA_JP(Locale.JAPANESE, "日本語"),
        KO_KR(Locale.KOREAN, "한국어"),
        ES_ES(Locale("es", "ES"), "Español"),
        FR_FR(Locale.FRANCE, "Français"),
        DE_DE(Locale.GERMANY, "Deutsch"),
        PT_BR(Locale("pt", "BR"), "Português")
    }

    init {
        logger.info("I18nManager initialized with locale: ${currentLocale.displayName}")
    }

    /**
     * 获取本地化文本
     *
     * @param bundleName 资源束名称（如 "messages.MyBundle"）
     * @param key 文本键
     * @param defaultValue 默认值（当键不存在时返回）
     * @return 本地化后的文本
     */
    fun getText(
        bundleName: String,
        key: String,
        defaultValue: String = key
    ): String {
        // 生成缓存键
        val cacheKey = "${bundleName}:${currentLocale.language}:$key"

        // 检查缓存
        textCache[cacheKey]?.let { return it }

        return try {
            // 获取资源束
            val bundle = getBundle(bundleName)

            // 尝试获取文本
            val text = bundle.getString(key)

            // 缓存结果
            textCache[cacheKey] = text

            text
        } catch (e: Exception) {
            logger.debug("Text not found for key: $key in bundle: $bundleName, using default")
            // 使用默认值并缓存
            textCache[cacheKey] = defaultValue
            defaultValue
        }
    }

    /**
     * 获取本地化文本（带参数格式化）
     *
     * @param bundleName 资源束名称
     * @param key 文本键
     * @param params 格式化参数
     * @return 格式化后的本地化文本
     */
    fun getTextFormatted(
        bundleName: String,
        key: String,
        vararg params: Any
    ): String {
        val pattern = getText(bundleName, key)
        return try {
            String.format(currentLocale, pattern, *params)
        } catch (e: Exception) {
            logger.warn("Failed to format text for key: $key", e)
            pattern
        }
    }

    /**
     * 获取 MyBundle 的本地化文本
     *
     * @param key 文本键
     * @param params 格式化参数
     * @return 本地化后的文本
     */
    fun message(key: String, vararg params: Any): String {
        return if (params.isEmpty()) {
            getText("messages.MyBundle", key)
        } else {
            getTextFormatted("messages.MyBundle", key, *params)
        }
    }

    /**
     * 获取 Commands 的本地化文本
     *
     * @param key 文本键
     * @return 本地化后的文本
     */
    fun command(key: String): String {
        return getText("messages.Commands", key)
    }

    /**
     * 设置当前语言环境
     *
     * @param locale 新的语言环境
     */
    fun setLocale(locale: Locale) {
        if (currentLocale != locale) {
            logger.info("Locale changed from ${currentLocale.displayName} to ${locale.displayName}")
            currentLocale = locale
            // 清空缓存，强制重新加载
            clearCache()
        }
    }

    /**
     * 获取当前语言环境
     *
     * @return 当前语言环境
     */
    fun getLocale(): Locale = currentLocale

    /**
     * 获取当前语言代码
     *
     * @return 语言代码（如 "zh", "en"）
     */
    fun getLanguage(): String = currentLocale.language

    /**
     * 检查是否为中文环境
     */
    fun isChinese(): Boolean = currentLocale.language == "zh"

    /**
     * 检查是否为英文环境
     */
    fun isEnglish(): Boolean = currentLocale.language == "en"

    /**
     * 获取资源束
     *
     * @param bundleName 资源束名称
     * @return ResourceBundle 实例
     */
    private fun getBundle(bundleName: String): ResourceBundle {
        // 检查缓存
        bundleCache[bundleName]?.let { return it }

        return try {
            // 尝试加载指定语言的资源束
            val bundle = ResourceBundle.getBundle(
                bundleName,
                currentLocale,
                I18nManager::class.java.classLoader
            )
            // 缓存资源束
            bundleCache[bundleName] = bundle
            bundle
        } catch (e: Exception) {
            logger.warn("Failed to load bundle: $bundleName for locale: ${currentLocale.displayName}", e)
            // 尝试使用默认语言
            try {
                val defaultBundle = ResourceBundle.getBundle(
                    bundleName,
                    Locale.ROOT,
                    I18nManager::class.java.classLoader
                )
                bundleCache[bundleName] = defaultBundle
                defaultBundle
            } catch (e2: Exception) {
                logger.error("Failed to load default bundle: $bundleName", e2)
                // 返回空的 ResourceBundle
                ResourceBundle.getBundle(bundleName)
            }
        }
    }

    /**
     * 清空所有缓存
     */
    fun clearCache() {
        bundleCache.clear()
        textCache.clear()
        logger.debug("I18n cache cleared")
    }

    /**
     * 预加载所有资源束
     *
     * 在应用启动时调用，预先加载所有资源束到缓存
     */
    fun preloadBundles() {
        supportedBundles.forEach { bundleName ->
            try {
                getBundle(bundleName)
                logger.debug("Preloaded bundle: $bundleName")
            } catch (e: Exception) {
                logger.warn("Failed to preload bundle: $bundleName", e)
            }
        }
    }

    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): Map<String, Int> {
        return mapOf(
            "bundleCacheSize" to bundleCache.size,
            "textCacheSize" to textCache.size
        )
    }

    /**
     * 国际化文本数据类
     *
     * 用于延迟解析国际化文本
     */
    data class I18nText(
        val bundleName: String,
        val key: String,
        val defaultValue: String = key
    ) {
        /**
         * 获取本地化后的文本
         */
        fun resolve(): String {
            return I18nManager.getText(bundleName, key, defaultValue)
        }

        /**
         * 获取格式化后的本地化文本
         */
        fun resolveFormatted(vararg params: Any): String {
            return I18nManager.getTextFormatted(bundleName, key, *params)
        }

        companion object {
            /**
             * 创建 MyBundle 的国际化文本
             */
            fun message(key: String, defaultValue: String = key): I18nText {
                return I18nText("messages.MyBundle", key, defaultValue)
            }

            /**
             * 创建 Commands 的国际化文本
             */
            fun command(key: String, defaultValue: String = key): I18nText {
                return I18nText("messages.Commands", key, defaultValue)
            }
        }
    }
}

/**
 * 项目级国际化服务
 *
 * 提供项目特定的国际化服务
 */
@Service(Service.Level.PROJECT)
class I18nService(private val project: Project) {

    private val manager = I18nManager

    /**
     * 获取本地化文本
     */
    fun getText(bundleName: String, key: String, defaultValue: String = key): String {
        return manager.getText(bundleName, key, defaultValue)
    }

    /**
     * 获取本地化文本（带参数）
     */
    fun getTextFormatted(bundleName: String, key: String, vararg params: Any): String {
        return manager.getTextFormatted(bundleName, key, *params)
    }

    /**
     * 设置语言环境
     */
    fun setLocale(locale: Locale) {
        manager.setLocale(locale)
    }

    /**
     * 获取当前语言环境
     */
    fun getLocale(): Locale = manager.getLocale()

    companion object {
        fun getInstance(project: Project): I18nService =
            project.getService(I18nService::class.java)
    }
}
