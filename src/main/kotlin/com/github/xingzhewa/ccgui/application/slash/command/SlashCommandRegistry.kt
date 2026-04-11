package com.github.xingzhewa.ccgui.application.slash.command

import com.github.xingzhewa.ccgui.util.I18nManager
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.project.Project
import java.util.Locale
import java.util.ResourceBundle
import java.util.concurrent.ConcurrentHashMap

/**
 * 斜杠命令注册表
 *
 * 管理和注册所有原生指令，支持国际化描述
 */
class SlashCommandRegistry(private val project: Project) {

    private val log = logger<SlashCommandRegistry>()

    /** 命令定义映射 */
    private val commands = ConcurrentHashMap<String, CommandDefinition>()

    /** 别名到主命令名的映射 */
    private val aliasToCommand = ConcurrentHashMap<String, String>()

    init {
        log.info("SlashCommandRegistry initialized")
    }

    /**
     * 命令定义
     */
    data class CommandDefinition(
        val name: String,
        val aliases: List<String> = emptyList(),
        val description: I18nText,
        val longDescription: I18nText? = null,
        val parameters: List<CommandParameter> = emptyList(),
        val category: CommandCategory,
        val scope: CommandScope = CommandScope.ALL,
        val handler: suspend (CommandContext) -> CommandResult
    )

    /**
     * 命令参数定义
     */
    data class CommandParameter(
        val name: String,
        val type: ParameterType,
        val required: Boolean = true,
        val description: I18nText,
        val defaultValue: String? = null,
        val suggestions: suspend (String) -> List<String> = { emptyList() }
    )

    /**
     * 国际化文本
     *
     * 使用统一的 I18nManager 进行文本解析
     */
    data class I18nText(
        val key: String,
        val defaultText: String
    ) {
        /**
         * 获取本地化文本
         */
        fun getText(locale: Locale = I18nManager.getLocale()): String {
            return I18nManager.getText("messages.Commands", key, defaultText)
        }
    }

    /**
     * 获取国际化文本
     *
     * @param i18nText 国际化文本对象
     * @param locale 语言环境（可选，默认使用当前环境）
     * @return 本地化后的文本
     */
    fun getI18nText(i18nText: I18nText, locale: Locale = I18nManager.getLocale()): String {
        return i18nText.getText(locale)
    }

    /**
     * 参数类型
     */
    enum class ParameterType {
        STRING, INT, BOOLEAN, ENUM, FILE, MODEL, SESSION
    }

    /**
     * 命令分类
     */
    enum class CommandCategory {
        SESSION, CONTEXT, MODE, CONFIG, TOOLS, WORKFLOW
    }

    /**
     * 命令作用域
     */
    enum class CommandScope {
        ALL, PROJECT_ONLY, SESSION_ONLY
    }

    /**
     * 命令执行结果
     */
    sealed class CommandResult {
        data class Success(val message: String, val data: Any? = null) : CommandResult()
        data class Error(val message: String, val throwable: Throwable? = null) : CommandResult()
        object Continue : CommandResult()
    }

    /**
     * 命令执行上下文
     */
    data class CommandContext(
        val project: Project,
        val sessionId: String?,
        val rawInput: String,
        val parameters: Map<String, String>
    ) {
        fun getParameter(name: String): String? = parameters[name]
    }

    /**
     * 注册命令
     */
    fun register(command: CommandDefinition) {
        commands[command.name] = command

        // 注册别名
        command.aliases.forEach { alias ->
            aliasToCommand[alias.lowercase()] = command.name
        }

        log.debug("Registered command: ${command.name} with aliases: ${command.aliases}")
    }

    /**
     * 批量注册命令
     */
    fun registerAll(commands: List<CommandDefinition>) {
        commands.forEach { register(it) }
    }

    /**
     * 查找命令（按前缀）
     */
    fun find(prefix: String): List<CommandDefinition> {
        val lowerPrefix = prefix.lowercase()
        return commands.values
            .distinctBy { it.name }
            .filter { cmd ->
                cmd.name.startsWith(lowerPrefix, ignoreCase = true) ||
                cmd.aliases.any { it.startsWith(lowerPrefix, ignoreCase = true) }
            }
    }

    /**
     * 获取命令
     */
    fun get(name: String): CommandDefinition? {
        return commands[name] ?: aliasToCommand[name.lowercase()]?.let { commands[it] }
    }

    /**
     * 获取所有命令
     */
    fun getAll(): List<CommandDefinition> {
        return commands.values.distinctBy { it.name }
    }

    /**
     * 按分类获取命令
     */
    fun getByCategory(category: CommandCategory): List<CommandDefinition> {
        return commands.values.filter { it.category == category }.distinctBy { it.name }
    }

    /**
     * 移除命令
     */
    fun unregister(name: String) {
        commands.remove(name)?.let { cmd ->
            cmd.aliases.forEach { aliasToCommand.remove(it.lowercase()) }
        }
    }

    /**
     * 清空所有命令
     */
    fun clear() {
        commands.clear()
        aliasToCommand.clear()
    }

    companion object {
        fun getInstance(project: Project): SlashCommandRegistry =
            project.getService(SlashCommandRegistry::class.java)
    }
}
