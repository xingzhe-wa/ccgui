package com.github.xingzhewa.ccgui.ui

import com.github.xingzhewa.ccgui.application.config.LocaleConfigManager
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.LocaleChangedEvent
import com.github.xingzhewa.ccgui.util.I18nManager
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ItemEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * 语言选择组件
 *
 * 提供语言切换 UI，支持运行时切换应用语言
 */
class LocaleSelectorComponent(private val project: Project) {

    private val log = logger<LocaleSelectorComponent>()
    private val localeConfigManager = LocaleConfigManager.getInstance(project)

    private val localeComboBox = ComboBox<I18nManager.SupportedLocale>()

    private val mainPanel = JBPanel<JBPanel<*>>().apply {
        val formBuilder = FormBuilder.createFormBuilder()
        formBuilder.addLabeledComponent(
            JBLabel(I18nManager.message("config.language") + ":"),
            localeComboBox
        )
        add(formBuilder.panel, BorderLayout.NORTH)
    }

    init {
        setupComboBox()
        setupEventListeners()
        log.debug("LocaleSelectorComponent initialized")
    }

    /**
     * 设置下拉框
     */
    private fun setupComboBox() {
        // 填充支持的语言
        val supportedLocales = localeConfigManager.getSupportedLocales()
        supportedLocales.forEach { localeComboBox.addItem(it) }

        // 设置当前选中的语言
        val currentLocale = localeConfigManager.getCurrentLocale()
        val currentSupported = supportedLocales.find { it.locale == currentLocale }
        currentSupported?.let { localeComboBox.selectedItem = it }

        // 自定义渲染器
        localeComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val component = super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus
                ) as JBLabel
                if (value is I18nManager.SupportedLocale) {
                    text = "${value.displayName} (${value.locale.language})"
                }
                return component
            }
        }
    }

    /**
     * 设置事件监听器
     */
    private fun setupEventListeners() {
        localeComboBox.addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                val selected = event.item as? I18nManager.SupportedLocale
                selected?.let {
                    log.info("User selected locale: ${it.displayName}")
                    localeConfigManager.setLocale(it.locale)
                }
            }
        }

        // 监听语言变更事件（从其他地方更改语言时更新 UI）
        EventBus.subscribeType(LocaleChangedEvent::class.java) { event ->
            updateSelection(event.locale)
        }
    }

    /**
     * 更新选中状态
     */
    private fun updateSelection(locale: java.util.Locale) {
        val item = localeComboBox.itemCount.downTo(0).firstNotNullOfOrNull { i ->
            (localeComboBox.getItemAt(i) as? I18nManager.SupportedLocale)?.takeIf { it.locale == locale }
        }
        item?.let { localeComboBox.selectedItem = it }
    }

    /**
     * 获取主面板
     */
    fun getPanel(): JBPanel<*> = mainPanel

    /**
     * 获取当前选择的语言
     */
    fun getSelectedLocale(): I18nManager.SupportedLocale? {
        return localeComboBox.selectedItem as? I18nManager.SupportedLocale
    }
}

/**
 * 语言状态显示组件
 *
 * 显示当前语言和语言代码
 */
class LocaleStatusComponent(private val project: Project) {

    private val localeConfigManager = LocaleConfigManager.getInstance(project)
    private val label = JBLabel()

    private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(label, BorderLayout.WEST)
    }

    init {
        updateLabel()
        label.border = javax.swing.border.EmptyBorder(2, 5, 2, 5)

        // 监听语言变更
        EventBus.subscribeType(LocaleChangedEvent::class.java) { event ->
            updateLabel()
        }
    }

    private fun updateLabel() {
        val locale = localeConfigManager.getCurrentLocale()
        val displayName = locale.getDisplayName(locale)
        label.text = "${I18nManager.message("config.language")}: $displayName (${locale.language})"
    }

    fun getPanel(): JBPanel<*> = mainPanel
}
