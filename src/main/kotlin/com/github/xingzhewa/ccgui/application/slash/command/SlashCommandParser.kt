package com.github.xingzhewa.ccgui.application.slash.command

import com.github.xingzhewa.ccgui.util.logger

/**
 * 斜杠命令解析器
 *
 * 解析用户输入的 / 命令，支持参数提取和自动补全
 */
class SlashCommandParser(private val registry: SlashCommandRegistry) {

    private val log = logger<SlashCommandParser>()

    /**
     * 解析结果
     */
    data class ParseResult(
        val command: SlashCommandRegistry.CommandDefinition?,
        val commandName: String,
        val rawArgs: String,
        val parameters: Map<String, String>,
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    /**
     * 自动补全候选
     */
    data class CompletionCandidate(
        val command: String,
        val displayText: String,
        val description: String,
        val parameterHint: String?
    )

    /**
     * 解析命令输入
     *
     * @param input 用户输入（可能以 / 开头）
     * @return 解析结果
     */
    fun parse(input: String): ParseResult {
        val trimmed = input.trim()

        // 检查是否以 / 开头
        if (!trimmed.startsWith("/")) {
            return ParseResult(
                command = null,
                commandName = "",
                rawArgs = "",
                parameters = emptyMap(),
                isValid = false,
                errorMessage = "Command must start with /"
            )
        }

        // 提取命令名和参数部分
        val commandPart = trimmed.substringAfter("/", "")
        val parts = splitCommandParts(commandPart)

        if (parts.isEmpty()) {
            return ParseResult(
                command = null,
                commandName = "",
                rawArgs = "",
                parameters = emptyMap(),
                isValid = false,
                errorMessage = "Empty command"
            )
        }

        val commandName = parts[0].lowercase()
        val rawArgs = if (parts.size > 1) parts.subList(1, parts.size).joinToString(" ") else ""

        // 查找命令
        val command = registry.get(commandName)
        if (command == null) {
            return ParseResult(
                command = null,
                commandName = commandName,
                rawArgs = rawArgs,
                parameters = emptyMap(),
                isValid = false,
                errorMessage = "Unknown command: $commandName"
            )
        }

        // 解析参数
        val parameters = parseParameters(rawArgs, command.parameters)

        return ParseResult(
            command = command,
            commandName = commandName,
            rawArgs = rawArgs,
            parameters = parameters,
            isValid = true
        )
    }

    /**
     * 获取自动补全候选
     *
     * @param input 用户当前输入
     * @param maxResults 最大返回数量
     * @return 候选列表
     */
    fun getCompletions(input: String, maxResults: Int = 10): List<CompletionCandidate> {
        val trimmed = input.trim()

        // 如果为空或只有 /，返回所有可用命令
        if (trimmed.isEmpty() || trimmed == "/") {
            return registry.getAll()
                .take(maxResults)
                .map { cmd ->
                    CompletionCandidate(
                        command = "/${cmd.name}",
                        displayText = cmd.name,
                        description = registry.getI18nText(cmd.description),
                        parameterHint = formatParameterHint(cmd.parameters)
                    )
                }
        }

        // 提取前缀
        val prefix = if (trimmed.startsWith("/")) {
            trimmed.substringAfter("/")
        } else {
            trimmed
        }

        // 查找匹配的命令
        return registry.find(prefix)
            .take(maxResults)
            .map { cmd ->
                CompletionCandidate(
                    command = "/${cmd.name}",
                    displayText = cmd.name,
                    description = registry.getI18nText(cmd.description),
                    parameterHint = formatParameterHint(cmd.parameters)
                )
            }
    }

    /**
     * 检查输入是否看起来像命令（用于触发自动补全）
     */
    fun looksLikeCommand(input: String): Boolean {
        return input.startsWith("/") && !input.contains(" ")
    }

    /**
     * 验证参数
     *
     * @param parameters 解析的参数
     * @param expected 期望的参数定义
     * @return 验证结果
     */
    fun validateParameters(
        parameters: Map<String, String>,
        expected: List<SlashCommandRegistry.CommandParameter>
    ): List<String> {
        val errors = mutableListOf<String>()

        // 检查必需参数
        expected.filter { it.required }.forEach { param ->
            if (!parameters.containsKey(param.name) && param.defaultValue == null) {
                errors.add("Missing required parameter: ${param.name}")
            }
        }

        // 检查未知参数
        parameters.keys.forEach { key ->
            if (expected.none { it.name == key }) {
                errors.add("Unknown parameter: $key")
            }
        }

        return errors
    }

    /**
     * 分割命令部分（处理引号内的空格）
     */
    private fun splitCommandParts(input: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var quoteChar: Char? = null

        for (char in input) {
            when {
                char == '"' || char == '\'' -> {
                    if (inQuotes && char == quoteChar) {
                        inQuotes = false
                        quoteChar = null
                    } else if (!inQuotes) {
                        inQuotes = true
                        quoteChar = char
                    } else {
                        current.append(char)
                    }
                }
                char == ' ' && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        parts.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            parts.add(current.toString())
        }

        return parts
    }

    /**
     * 解析参数
     *
     * 支持格式：
     * - --param value
     * - --param=value
     * - positional value (对于没有命名的参数)
     */
    private fun parseParameters(
        rawArgs: String,
        paramDefinitions: List<SlashCommandRegistry.CommandParameter>
    ): Map<String, String> {
        val parameters = mutableMapOf<String, String>()

        if (rawArgs.isBlank()) return parameters

        // 先尝试键值对格式
        val parts = splitCommandParts(rawArgs)
        var i = 0
        var positionalIndex = 0

        while (i < parts.size) {
            val part = parts[i]

            if (part.startsWith("--")) {
                val keyValue = part.substringAfter("--")
                if (keyValue.contains("=")) {
                    val (key, value) = keyValue.split("=", limit = 2)
                    parameters[key] = value
                } else if (i + 1 < parts.size) {
                    parameters[keyValue] = parts[i + 1]
                    i++
                }
            } else {
                // 位置参数
                val positionalParams = paramDefinitions.filter { it.name.isNotEmpty() }
                if (positionalIndex < positionalParams.size) {
                    parameters[positionalParams[positionalIndex].name] = part
                    positionalIndex++
                }
            }
            i++
        }

        return parameters
    }

    /**
     * 格式化参数提示
     */
    private fun formatParameterHint(parameters: List<SlashCommandRegistry.CommandParameter>): String? {
        if (parameters.isEmpty()) return null

        val hints = parameters.map { param ->
            val prefix = if (param.required) "" else "["
            val suffix = if (param.required) "" else "]"
            "$prefix--${param.name}${suffix}"
        }

        return hints.joinToString(" ")
    }
}
