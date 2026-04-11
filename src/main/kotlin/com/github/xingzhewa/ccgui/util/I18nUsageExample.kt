package com.github.xingzhewa.ccgui.util

import com.github.xingzhewa.ccgui.application.config.LocaleConfigManager
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.LocaleChangedEvent
import com.intellij.openapi.project.Project
import java.util.Locale

/**
 * 国际化使用示例
 *
 * 展示如何在代码中使用统一的国际化驱动
 */
object I18nUsageExample {

    /**
     * 示例 1：基本用法 - 直接获取本地化文本
     */
    fun exampleBasicUsage() {
        // 获取 MyBundle 中的文本
        val pluginName = I18nManager.message("plugin.name")
        println("Plugin name: $pluginName")

        // 获取 Commands 中的文本
        val newSessionCommand = I18nManager.command("command.session.new")
        println("New session command: $newSessionCommand")
    }

    /**
     * 示例 2：带参数的文本
     */
    fun exampleWithParameters(projectId: String) {
        // 使用参数格式化
        val serviceMessage = I18nManager.message("service.project", projectId)
        println("Service message: $serviceMessage")
    }

    /**
     * 示例 3：延迟解析（I18nText）
     */
    fun exampleLazyResolution() {
        // 创建 I18nText 对象，延迟解析
        val i18nText = I18nManager.I18nText.message("plugin.description")

        // 在需要时才解析
        val text = i18nText.resolve()
        println("Description: $text")

        // 带参数的延迟解析
        val formattedText = i18nText.resolveFormatted("CC Assistant")
        println("Formatted: $formattedText")
    }

    /**
     * 示例 4：使用 I18nService（项目级服务）
     */
    fun exampleServiceUsage(project: Project) {
        val i18nService = I18nService.getInstance(project)

        // 通过服务获取文本
        val loadingText = i18nService.getText("messages.MyBundle", "message.loading")
        println("Loading: $loadingText")
    }

    /**
     * 示例 5：切换语言
     */
    fun exampleSwitchLocale(project: Project) {
        val localeConfig = LocaleConfigManager.getInstance(project)

        // 切换到英文
        localeConfig.setLocale(Locale.ENGLISH)

        // 通过语言代码切换
        localeConfig.setLocaleByCode("zh")

        // 切换到日语
        localeConfig.setLocale(Locale.JAPANESE)

        // 重置为系统默认
        localeConfig.resetToSystemLocale()
    }

    /**
     * 示例 6：监听语言变更事件
     */
    fun exampleLocaleChangeListener() {
        val subscriptionId = EventBus.subscribeType(LocaleChangedEvent::class.java) { event ->
            println("Locale changed to: ${event.locale.displayName}")
            // 在这里更新 UI 或重新加载资源
        }

        // 取消订阅
        // EventBus.unsubscribe(subscriptionId)
    }

    /**
     * 示例 7：检查当前语言
     */
    fun exampleCheckLocale() {
        // 检查是否为中文
        if (I18nManager.isChinese()) {
            println("当前是中文环境")
        }

        // 检查是否为英文
        if (I18nManager.isEnglish()) {
            println("当前是英文环境")
        }

        // 获取语言代码
        val langCode = I18nManager.getLanguage()
        println("Language code: $langCode")
    }

    /**
     * 示例 8：在 UI 中使用
     */
    fun exampleInUI(project: Project) {
        val localeConfig = LocaleConfigManager.getInstance(project)

        // 获取支持的语言列表
        val supportedLocales = localeConfig.getSupportedLocales()
        supportedLocales.forEach { locale ->
            println("Supported: ${locale.displayName} (${locale.locale.language})")
        }

        // 获取当前语言
        val currentLocale = localeConfig.getCurrentLocale()
        println("Current locale: ${currentLocale.displayName}")
    }

    /**
     * 示例 9：错误处理
     */
    fun exampleErrorHandling() {
        // 当键不存在时，返回默认值
        val missingKey = I18nManager.message("non.existent.key", "Default Value")
        println("Missing key result: $missingKey")

        // 使用 I18nText 的默认值
        val i18nText = I18nManager.I18nText(
            bundleName = "messages.MyBundle",
            key = "some.key",
            defaultValue = "Fallback text"
        )
        println("Fallback: ${i18nText.resolve()}")
    }

    /**
     * 示例 10：性能优化 - 预加载资源
     */
    fun examplePreloadBundles() {
        // 预加载所有资源束到缓存
        I18nManager.preloadBundles()

        // 查看缓存统计
        val stats = I18nManager.getCacheStats()
        println("Cache stats: $stats")
    }

    /**
     * 示例 11：清空缓存
     */
    fun exampleClearCache() {
        // 清空所有缓存，强制重新加载
        I18nManager.clearCache()
        println("I18n cache cleared")
    }

    /**
     * 示例 12：在资源文件中使用占位符
     *
     * MyBundle.properties:
     *   greeting=Hello, {0}!
     *   welcome=Welcome to {0}, version {1}
     */
    fun examplePlaceholders() {
        // 单个参数
        val greeting = I18nManager.message("greeting", "User")
        println("Greeting: $greeting")  // Hello, User!

        // 多个参数
        val welcome = I18nManager.message("welcome", "CC Assistant", "1.0.0")
        println("Welcome: $welcome")  // Welcome to CC Assistant, version 1.0.0
    }
}
