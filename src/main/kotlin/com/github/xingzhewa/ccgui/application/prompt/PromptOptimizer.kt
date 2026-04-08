package com.github.xingzhewa.ccgui.application.prompt

import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.message.ContentPart
import com.github.xingzhewa.ccgui.model.session.ChatSession
import com.github.xingzhewa.ccgui.model.session.SessionContext
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * 提示词优化器
 *
 * 负责优化用户输入的提示词，提高模型理解能力
 *
 * 功能:
 * - 上下文增强：添加相关代码片段、文件内容
 * - 格式规范化：统一格式，提高解析准确率
 * - 相关信息注入：注入项目结构、最近文件等信息
 * - 会话历史整合：智能整合历史对话
 * - 代码引用处理：处理代码块引用和格式化
 *
 * @param project IntelliJ项目实例
 */
@Service(Service.Level.PROJECT)
class PromptOptimizer(private val project: Project) {

    private val log = logger<PromptOptimizer>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 优化配置
     */
    data class OptimizationConfig(
        val includeContext: Boolean = true,
        val includeRecentFiles: Boolean = true,
        val includeProjectStructure: Boolean = false,
        val maxHistoryLength: Int = 10,
        val maxCodeLength: Int = 50000,  // 单个代码块最大长度
        val maxTotalCodeLength: Int = 100000  // 总代码长度限制
    )

    /**
     * 优化结果
     */
    data class OptimizationResult(
        val optimizedPrompt: String,
        val addedContext: List<ContextInfo>,
        val truncatedInfo: List<String> = emptyList()
    )

    /**
     * 上下文信息
     */
    data class ContextInfo(
        val type: ContextType,
        val content: String,
        val metadata: Map<String, Any> = emptyMap()
    )

    /**
     * 上下文类型
     */
    enum class ContextType {
        CODE_REFERENCE,
        FILE_CONTENT,
        PROJECT_STRUCTURE,
        RECENT_FILES,
        SESSION_HISTORY,
        USER_CONTEXT
    }

    // ==================== 核心API ====================

    /**
     * 优化提示词
     *
     * @param prompt 原始提示词
     * @param session 当前会话（可选）
     * @param config 优化配置
     * @return 优化结果
     */
    suspend fun optimizePrompt(
        prompt: String,
        session: ChatSession? = null,
        config: OptimizationConfig = OptimizationConfig()
    ): OptimizationResult = withContext(Dispatchers.Default) {
        val addedContext = mutableListOf<ContextInfo>()
        val truncatedInfo = mutableListOf<String>()
        var currentCodeLength = 0

        // 1. 处理代码引用
        val (processedPrompt, _, newLength) = processCodeReferences(prompt, config, addedContext, truncatedInfo, currentCodeLength)
        currentCodeLength = newLength

        // 2. 添加会话上下文
        val withSessionContext = if (config.includeContext && session != null) {
            addSessionContext(processedPrompt, session, config, addedContext)
        } else {
            processedPrompt
        }

        // 3. 添加项目结构（可选）
        val withProjectInfo = if (config.includeProjectStructure) {
            addProjectStructure(withSessionContext, addedContext)
        } else {
            withSessionContext
        }

        // 4. 格式规范化
        val finalPrompt = normalizePrompt(withProjectInfo)

        OptimizationResult(
            optimizedPrompt = finalPrompt,
            addedContext = addedContext,
            truncatedInfo = truncatedInfo
        )
    }

    /**
     * 批量优化提示词
     *
     * @param prompts 提示词列表
     * @param session 当前会话
     * @param config 优化配置
     * @return 优化结果列表
     */
    suspend fun optimizePrompts(
        prompts: List<String>,
        session: ChatSession? = null,
        config: OptimizationConfig = OptimizationConfig()
    ): List<OptimizationResult> = withContext(Dispatchers.Default) {
        prompts.map { prompt ->
            optimizePrompt(prompt, session, config)
        }
    }

    /**
     * 创建增强上下文提示词
     *
     * @param basePrompt 基础提示词
     * @param contextItems 上下文项
     * @return 增强后的提示词
     */
    fun buildContextualPrompt(
        basePrompt: String,
        contextItems: List<ContextInfo>
    ): String {
        if (contextItems.isEmpty()) return basePrompt

        val contextBuilder = StringBuilder()
        contextBuilder.append("<context>\n")

        contextItems.forEach { item ->
            when (item.type) {
                ContextType.CODE_REFERENCE -> {
                    contextBuilder.append("```")
                    contextBuilder.append(item.metadata["language"] as? String ?: "")
                    contextBuilder.append("\n")
                    contextBuilder.append(item.content)
                    contextBuilder.append("\n```\n")
                }
                ContextType.FILE_CONTENT -> {
                    contextBuilder.append("File: ")
                    contextBuilder.append(item.metadata["filePath"] as? String ?: "unknown")
                    contextBuilder.append("\n")
                    contextBuilder.append("```\n")
                    contextBuilder.append(item.content)
                    contextBuilder.append("\n```\n")
                }
                ContextType.SESSION_HISTORY -> {
                    contextBuilder.append("<previous_conversation>\n")
                    contextBuilder.append(item.content)
                    contextBuilder.append("\n</previous_conversation>\n")
                }
                else -> {
                    contextBuilder.append(item.content)
                    contextBuilder.append("\n")
                }
            }
        }

        contextBuilder.append("</context>\n\n")
        contextBuilder.append(basePrompt)

        return contextBuilder.toString()
    }

    // ==================== 内部方法 ====================

    /**
     * 处理代码引用
     */
    private fun processCodeReferences(
        prompt: String,
        config: OptimizationConfig,
        addedContext: MutableList<ContextInfo>,
        truncatedInfo: MutableList<String>,
        currentCodeLength: Int
    ): Triple<String, List<ContextInfo>, Int> {
        var processedPrompt = prompt
        var totalCodeLength = currentCodeLength

        // 检测代码块引用
        val codeBlockRegex = """```(\w*)\n([\s\S]*?)```""".toRegex()
        val codeBlocks = codeBlockRegex.findAll(processedPrompt).toList()

        codeBlocks.forEach { match ->
            val language = match.groupValues[1]
            val code = match.groupValues[2]
            val codeLength = code.length

            if (totalCodeLength + codeLength > config.maxTotalCodeLength) {
                // 超出限制，截断代码
                val remainingSpace = (config.maxTotalCodeLength - totalCodeLength).coerceAtLeast(0)
                val truncatedCode = code.take(remainingSpace)
                processedPrompt = processedPrompt.replace(match.value, "```$language\n${truncatedCode}\n... [truncated]\n```")
                truncatedInfo.add("Code block truncated (${code.length - remainingSpace} chars)")
                totalCodeLength += remainingSpace
            } else {
                // 添加代码上下文
                addedContext.add(ContextInfo(
                    type = ContextType.CODE_REFERENCE,
                    content = code,
                    metadata = mapOf("language" to language, "length" to codeLength)
                ))
                totalCodeLength += codeLength
            }
        }

        return Triple(processedPrompt, addedContext, totalCodeLength)
    }

    /**
     * 添加会话上下文
     */
    private fun addSessionContext(
        prompt: String,
        session: ChatSession,
        config: OptimizationConfig,
        addedContext: MutableList<ContextInfo>
    ): String {
        if (session.messages.isEmpty()) return prompt

        // 获取最近的消息
        val recentMessages = session.messages.takeLast(config.maxHistoryLength)
        val historyBuilder = StringBuilder()

        historyBuilder.append("<conversation_history>\n")
        recentMessages.forEach { msg ->
            historyBuilder.append("${msg.role.name}: ${msg.content.take(500)}")
            if (msg.content.length > 500) {
                historyBuilder.append("...")
            }
            historyBuilder.append("\n")
        }
        historyBuilder.append("</conversation_history>\n\n")

        addedContext.add(ContextInfo(
            type = ContextType.SESSION_HISTORY,
            content = historyBuilder.toString(),
            metadata = mapOf("messageCount" to recentMessages.size)
        ))

        return historyBuilder.toString() + prompt
    }

    /**
     * 添加项目结构
     */
    private fun addProjectStructure(
        prompt: String,
        addedContext: MutableList<ContextInfo>
    ): String {
        // 简化版项目结构
        val basePath = project.basePath ?: return prompt
        val structure = buildProjectStructure(basePath)

        addedContext.add(ContextInfo(
            type = ContextType.PROJECT_STRUCTURE,
            content = structure,
            metadata = mapOf("basePath" to basePath)
        ))

        return "<project_structure>\n$structure\n</project_structure>\n\n$prompt"
    }

    /**
     * 构建项目结构
     */
    private fun buildProjectStructure(basePath: String): String {
        val builder = StringBuilder()
        try {
            val projectDir = java.io.File(basePath)
            buildDirectoryStructure(projectDir, "", builder, maxDepth = 3)
        } catch (e: Exception) {
            log.warn("Failed to build project structure: ${e.message}")
        }
        return builder.toString()
    }

    /**
     * 递归构建目录结构
     */
    private fun buildDirectoryStructure(
        dir: java.io.File,
        prefix: String,
        builder: StringBuilder,
        maxDepth: Int,
        currentDepth: Int = 0
    ) {
        if (currentDepth >= maxDepth) return

        val files = dir.listFiles() ?: return
        val directories = files.filter { it.isDirectory }.sortedBy { it.name }
        val sourceFiles = files.filter { it.isFile && isSourceFile(it) }

        directories.forEach { subDir ->
            val relativePath = if (prefix.isEmpty()) subDir.name else "$prefix/${subDir.name}"
            builder.append("$relativePath/\n")
            buildDirectoryStructure(subDir, relativePath, builder, maxDepth, currentDepth + 1)
        }

        sourceFiles.forEach { file ->
            val relativePath = if (prefix.isEmpty()) file.name else "$prefix/${file.name}"
            builder.append("$relativePath\n")
        }
    }

    /**
     * 判断是否为源代码文件
     */
    private fun isSourceFile(file: java.io.File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".kt") || name.endsWith(".java") ||
               name.endsWith(".py") || name.endsWith(".js") ||
               name.endsWith(".ts") || name.endsWith(".tsx") ||
               name.endsWith(".json") || name.endsWith(".xml") ||
               name.endsWith(".gradle")
    }

    /**
     * 格式规范化
     */
    private fun normalizePrompt(prompt: String): String {
        var normalized = prompt

        // 1. 规范化空行
        normalized = normalized.replace(Regex("\n{3,}"), "\n\n")

        // 2. 移除首尾空白
        normalized = normalized.trim()

        // 3. 规范化代码块语言标识
        normalized = normalizeCodeBlocks(normalized)

        return normalized
    }

    /**
     * 规范化代码块
     */
    private fun normalizeCodeBlocks(prompt: String): String {
        // 确保代码块有语言标识
        return prompt.replace(Regex("```\n(?![a-z]+)"), "```\n")
    }

    companion object {
        fun getInstance(project: Project): PromptOptimizer =
            project.getService(PromptOptimizer::class.java)
    }
}