package com.github.xingzhewa.ccgui.application.prompt

import com.github.xingzhewa.ccgui.adaptation.sdk.ClaudeCodeClient
import com.github.xingzhewa.ccgui.adaptation.sdk.SdkOptions
import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.message.ContentPart
import com.github.xingzhewa.ccgui.model.session.ChatSession
import com.github.xingzhewa.ccgui.model.session.SessionContext
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.google.gson.JsonObject
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
     *
     * @param optimizedPrompt 优化后的提示词
     * @param addedContext 添加的上下文信息列表
     * @param truncatedInfo 截断提示信息列表
     * @param improvements AI 生成的改进点列表（AI-driven optimization 时填充）
     * @param confidence AI 优化置信度 0.0-1.0（AI-driven optimization 时填充）
     */
    data class OptimizationResult(
        val optimizedPrompt: String,
        val addedContext: List<ContextInfo>,
        val truncatedInfo: List<String> = emptyList(),
        val improvements: List<String> = emptyList(),
        val confidence: Double = 0.0
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

        // 5. AI 驱动的优化（新增 Step 2）
        val aiOptimizationResult = performAiOptimization(finalPrompt, addedContext)

        OptimizationResult(
            optimizedPrompt = aiOptimizationResult.first,
            addedContext = addedContext,
            truncatedInfo = truncatedInfo,
            improvements = aiOptimizationResult.second,
            confidence = aiOptimizationResult.third
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

    /**
     * AI 驱动的提示词优化
     *
     * 调用 Claude CLI 生成优化后的提示词、改进点列表和置信度
     *
     * @param enrichedPrompt 经过上下文增强后的提示词
     * @param addedContext 已添加的上下文信息
     * @return Triple(优化后的提示词, 改进点列表, 置信度)
     */
    private suspend fun performAiOptimization(
        enrichedPrompt: String,
        addedContext: List<ContextInfo>
    ): Triple<String, List<String>, Double> = withContext(Dispatchers.IO) {
        try {
            val claudeClient = ClaudeCodeClient.getInstance(project)

            // 构建优化请求提示词
            val optimizationPrompt = buildOptimizationPrompt(enrichedPrompt)

            val result = claudeClient.sendMessage(
                prompt = optimizationPrompt,
                options = SdkOptions(
                    maxTurns = 1,
                    allowedTools = emptyList()
                )
            )

            result.getOrNull()?.let { sdkResult ->
                val responseText = sdkResult.result ?: ""
                if (responseText.isNotEmpty()) {
                    parseOptimizationResponse(responseText)
                } else {
                    Triple(enrichedPrompt, emptyList(), 0.0)
                }
            } ?: run {
                // CLI 调用失败时，返回原始提示词，空改进点
                log.warn("AI optimization failed, returning enriched prompt")
                Triple(enrichedPrompt, emptyList(), 0.0)
            }
        } catch (e: Exception) {
            log.warn("AI optimization error: ${e.message}, returning enriched prompt")
            Triple(enrichedPrompt, emptyList(), 0.0)
        }
    }

    /**
     * 构建优化请求提示词
     */
    private fun buildOptimizationPrompt(enrichedPrompt: String): String {
        return """
请你优化以下提示词，使其更清晰、具体、结构化。

请以 JSON 格式返回优化结果：
{
    "optimizedPrompt": "优化后的提示词（保持原意但更清晰）",
    "improvements": ["改进点1", "改进点2", "改进点3"],
    "confidence": 0.95
}

原始提示词：
$enrichedPrompt

要求：
- optimizedPrompt：保持原意，改进表达清晰度和结构化程度
- improvements：列出 2-5 个关键改进点，简短描述
- confidence：0.0-1.0，表示优化置信度
        """.trimIndent()
    }

    /**
     * 解析优化响应
     */
    private fun parseOptimizationResponse(responseText: String): Triple<String, List<String>, Double> {
        return try {
            // 尝试从响应中提取 JSON
            val jsonText = extractJson(responseText)
            val json = JsonUtils.fromJson(jsonText, JsonObject::class.java)
                ?: throw IllegalStateException("Failed to parse JSON")

            val optimizedPrompt = json.get("optimizedPrompt")?.asString ?: ""
            val improvements = json.getAsJsonArray("improvements")?.map { it.asString } ?: emptyList()
            val confidence = json.get("confidence")?.asDouble ?: 0.0

            Triple(optimizedPrompt, improvements, confidence)
        } catch (e: Exception) {
            log.warn("Failed to parse optimization response: ${e.message}")
            // 解析失败时尝试直接返回响应文本作为优化后的提示词
            val cleaned = responseText.trim().take(2000)
            Triple(cleaned, emptyList(), 0.0)
        }
    }

    /**
     * 从响应文本中提取 JSON
     */
    private fun extractJson(text: String): String {
        // 尝试找 JSON 代码块
        val jsonBlockRegex = """```json\s*([\s\S]*?)\s*```""".toRegex()
        val blockMatch = jsonBlockRegex.find(text)
        if (blockMatch != null) {
            return blockMatch.groupValues[1].trim()
        }

        // 尝试找普通代码块
        val codeBlockRegex = """```\s*([\s\S]*?)\s*```""".toRegex()
        val codeMatch = codeBlockRegex.find(text)
        if (codeMatch != null) {
            return codeMatch.groupValues[1].trim()
        }

        // 尝试直接解析整个文本
        return text.trim()
    }

    companion object {
        fun getInstance(project: Project): PromptOptimizer =
            project.getService(PromptOptimizer::class.java)
    }
}