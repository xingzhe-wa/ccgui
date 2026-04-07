package com.github.xingzhewa.ccgui.adaptation.sdk

import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.project.Project
import com.google.gson.JsonObject
import java.io.File

/**
 * Claude Code SDK 配置构建器
 *
 * 职责:
 *   1. 构建CLI命令行参数
 *   2. 生成MCP配置临时文件
 *   3. 管理系统提示模板
 *
 * CLI命令格式:
 *   claude -p <prompt> --output-format stream-json [options]
 */
class SdkConfigBuilder(private val project: Project) {

    private val log = logger<SdkConfigBuilder>()

    companion object {
        const val CLI_COMMAND = "claude"
        const val OUTPUT_FORMAT = "stream-json"
    }

    /**
     * 构建完整的CLI命令列表
     *
     * @param prompt 用户输入
     * @param options SDK选项
     * @return 命令列表，可直接传给 ProcessBuilder
     */
    fun buildCommand(prompt: String, options: SdkOptions): List<String> {
        val command = mutableListOf<String>()

        // 基础命令
        command.add(CLI_COMMAND)
        command.add("-p")
        command.add(prompt)
        command.add("--output-format")
        command.add(OUTPUT_FORMAT)

        // 会话恢复
        options.resumeSessionId?.let {
            command.add("--resume")
            command.add(it)
        }

        // 继续最近对话
        if (options.continueRecent && options.resumeSessionId == null) {
            command.add("--continue")
        }

        // 系统提示
        options.systemPrompt?.let {
            command.add("--system-prompt")
            command.add(it)
        }

        // 指定模型
        options.model?.let {
            command.add("--model")
            command.add(it)
        }

        // 允许的工具
        options.allowedTools?.let { tools ->
            if (tools.isNotEmpty()) {
                command.add("--allowedTools")
                command.add(tools.joinToString(","))
            }
        }

        // 最大轮次
        options.maxTurns?.let {
            command.add("--max-turns")
            command.add(it.toString())
        }

        // 权限提示工具
        options.permissionPromptTool?.let {
            command.add("--permission-prompt-tool")
            command.add(it)
        }

        return command
    }

    /**
     * 将MCP配置写入临时文件
     * SDK通过 --mcp-config <file> 读取
     *
     * JSON格式:
     * {
     *   "mcpServers": {
     *     "server-name": {
     *       "command": "...",
     *       "args": [...],
     *       "env": {...}
     *     }
     *   }
     * }
     *
     * @return 临时配置文件
     */
    fun writeMcpConfigTempFile(config: McpServersConfig): File {
        val json = JsonObject()
        val serversObj = JsonObject()

        config.servers.forEach { (name, entry) ->
            val serverObj = JsonObject().apply {
                addProperty("command", entry.command)
                add("args", JsonUtils.toJsonArray(entry.args))
                add("env", JsonUtils.toJsonObject(entry.env))
            }
            serversObj.add(name, serverObj)
        }

        json.add("mcpServers", serversObj)

        // 写入临时文件
        val tempFile = File.createTempFile("ccgui-mcp-", ".json")
        tempFile.writeText(json.toString())
        tempFile.deleteOnExit()

        log.info("MCP config written to: ${tempFile.absolutePath}")
        return tempFile
    }

    /**
     * 构建默认系统提示
     */
    fun buildDefaultSystemPrompt(): String {
        return buildString {
            append("You are Claude, an AI assistant integrated into JetBrains IDE via CC Assistant plugin. ")
            append("You help developers with coding, debugging, refactoring, and other software engineering tasks. ")
            append("Provide clear, concise, and actionable responses. ")
            append("When generating code, always include the language identifier in code blocks. ")
            append("Project: ${project.name}")
        }
    }

    /**
     * 构建带上下文的系统提示
     * 注入当前文件信息、选中代码等IDE上下文
     */
    fun buildContextualSystemPrompt(
        currentFile: String? = null,
        selectedText: String? = null,
        language: String? = null
    ): String {
        return buildString {
            append(buildDefaultSystemPrompt())
            append("\n\n--- IDE Context ---\n")

            currentFile?.let {
                append("Current file: $it")
                language?.let { lang -> append(" ($lang)") }
                append("\n")
            }

            selectedText?.let {
                append("Selected code:\n```\n$it\n```\n")
            }
        }
    }
}

/**
 * SDK请求选项
 */
data class SdkOptions(
    /** 恢复已有会话（传入SDK session_id） */
    val resumeSessionId: String? = null,
    /** 继续最近的对话 */
    val continueRecent: Boolean = false,
    /** 指定模型（如 claude-sonnet-4-20250514） */
    val model: String? = null,
    /** 自定义系统提示 */
    val systemPrompt: String? = null,
    /** MCP配置 */
    val mcpConfig: McpServersConfig? = null,
    /** 允许的工具列表 */
    val allowedTools: List<String>? = null,
    /** 最大对话轮次 */
    val maxTurns: Int? = null,
    /** 额外环境变量 */
    val env: Map<String, String> = emptyMap(),
    /** 权限提示工具名称 */
    val permissionPromptTool: String? = null
)

/**
 * MCP服务器配置
 * 用于生成 --mcp-config 参数的JSON文件
 */
data class McpServersConfig(
    val servers: Map<String, McpServerEntry>
) {
    data class McpServerEntry(
        val command: String,
        val args: List<String> = emptyList(),
        val env: Map<String, String> = emptyMap()
    )
}
