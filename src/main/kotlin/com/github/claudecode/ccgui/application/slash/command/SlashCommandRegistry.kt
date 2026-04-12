package com.github.claudecode.ccgui.application.slash.command

import com.github.claudecode.ccgui.util.I18nManager
import com.github.claudecode.ccgui.util.logger
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
        registerAll(getDefaultCommands())
    }

    /**
     * 获取默认命令列表（17条指令）
     */
    private fun getDefaultCommands(): List<CommandDefinition> = listOf(
        // 系统类
        CommandDefinition(
            name = "init",
            description = I18nText("command.init.description", "Initialize project"),
            category = CommandCategory.WORKFLOW,
            handler = { ctx ->
                log.info("Executing /init command: ${ctx.rawInput}")
                CommandResult.Success("Project initialized")
            }
        ),
        // 会话类
        CommandDefinition(
            name = "compact",
            description = I18nText("command.compact.description", "Compact context"),
            category = CommandCategory.SESSION,
            handler = { ctx ->
                log.info("Executing /compact command: ${ctx.rawInput}")
                CommandResult.Success("Context compacted")
            }
        ),
        CommandDefinition(
            name = "clear",
            description = I18nText("command.clear.description", "Clear session"),
            category = CommandCategory.SESSION,
            handler = { ctx ->
                log.info("Executing /clear command: ${ctx.rawInput}")
                CommandResult.Success("Session cleared")
            }
        ),
        CommandDefinition(
            name = "resume",
            description = I18nText("command.resume.description", "Resume session"),
            category = CommandCategory.SESSION,
            handler = { ctx ->
                log.info("Executing /resume command: ${ctx.rawInput}")
                CommandResult.Success("Session resumed")
            }
        ),
        // 信息类
        CommandDefinition(
            name = "context",
            description = I18nText("command.context.description", "View context"),
            category = CommandCategory.CONTEXT,
            handler = { ctx ->
                log.info("Executing /context command: ${ctx.rawInput}")
                CommandResult.Success("Context displayed")
            }
        ),
        CommandDefinition(
            name = "cost",
            description = I18nText("command.cost.description", "View cost"),
            category = CommandCategory.CONTEXT,
            handler = { ctx ->
                log.info("Executing /cost command: ${ctx.rawInput}")
                CommandResult.Success("Cost displayed")
            }
        ),
        CommandDefinition(
            name = "help",
            description = I18nText("command.help.description", "Show help"),
            category = CommandCategory.CONTEXT,
            handler = { ctx ->
                log.info("Executing /help command: ${ctx.rawInput}")
                CommandResult.Success("Help displayed")
            }
        ),
        // 诊断类
        CommandDefinition(
            name = "doctor",
            description = I18nText("command.doctor.description", "Diagnose issues"),
            category = CommandCategory.TOOLS,
            handler = { ctx ->
                log.info("Executing /doctor command: ${ctx.rawInput}")
                CommandResult.Success("Diagnosis complete")
            }
        ),
        // 配置类
        CommandDefinition(
            name = "model",
            description = I18nText("command.model.description", "Switch model"),
            category = CommandCategory.CONFIG,
            handler = { ctx ->
                log.info("Executing /model command: ${ctx.rawInput}")
                CommandResult.Success("Model switched")
            }
        ),
        CommandDefinition(
            name = "config",
            description = I18nText("command.config.description", "Manage configuration"),
            category = CommandCategory.CONFIG,
            handler = { ctx ->
                log.info("Executing /config command: ${ctx.rawInput}")
                CommandResult.Success("Configuration managed")
            }
        ),
        CommandDefinition(
            name = "permissions",
            description = I18nText("command.permissions.description", "Manage permissions"),
            category = CommandCategory.CONFIG,
            handler = { ctx ->
                log.info("Executing /permissions command: ${ctx.rawInput}")
                CommandResult.Success("Permissions managed")
            }
        ),
        // 模式类
        CommandDefinition(
            name = "think",
            description = I18nText("command.think.description", "Think mode"),
            category = CommandCategory.MODE,
            handler = { ctx ->
                log.info("Executing /think command: ${ctx.rawInput}")
                CommandResult.Success("Think mode enabled")
            }
        ),
        CommandDefinition(
            name = "plan",
            description = I18nText("command.plan.description", "Plan mode"),
            category = CommandCategory.MODE,
            handler = { ctx ->
                log.info("Executing /plan command: ${ctx.rawInput}")
                CommandResult.Success("Plan mode enabled")
            }
        ),
        CommandDefinition(
            name = "auto",
            description = I18nText("command.auto.description", "Auto mode"),
            category = CommandCategory.MODE,
            handler = { ctx ->
                log.info("Executing /auto command: ${ctx.rawInput}")
                CommandResult.Success("Auto mode enabled")
            }
        ),
        // 管理类
        CommandDefinition(
            name = "mcp",
            description = I18nText("command.mcp.description", "MCP management"),
            category = CommandCategory.TOOLS,
            handler = { ctx ->
                log.info("Executing /mcp command: ${ctx.rawInput}")
                CommandResult.Success("MCP managed")
            }
        ),
        CommandDefinition(
            name = "skill",
            description = I18nText("command.skill.description", "Skills management"),
            category = CommandCategory.TOOLS,
            handler = { ctx ->
                log.info("Executing /skill command: ${ctx.rawInput}")
                CommandResult.Success("Skills managed")
            }
        ),
        CommandDefinition(
            name = "agent",
            description = I18nText("command.agent.description", "Agent management"),
            category = CommandCategory.TOOLS,
            handler = { ctx ->
                log.info("Executing /agent command: ${ctx.rawInput}")
                CommandResult.Success("Agent managed")
            }
        )
    )

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
