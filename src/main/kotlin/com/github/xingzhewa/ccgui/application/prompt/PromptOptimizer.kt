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
 * - 模板匹配：根据关键词匹配预设模板（如"/explain", "/review", "/test"）
 * - 代码检测：识别代码语言、格式化为代码块
 *
 * @param project IntelliJ项目实例
 */
@Service(Service.Level.PROJECT)
class PromptOptimizer(private val project: Project) {

    private val log = logger<PromptOptimizer>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ==================== 提示词模板 ====================

    /**
     * 提示词模板枚举
     *
     * 对应 QuickActionsPanel 中的快捷操作
     */
    enum class PromptTemplate(val keyword: String, val description: String, val template: String) {
        EXPLAIN(
            keyword = "/explain",
            description = "解释代码逻辑",
            template = """请解释以下代码的功能和实现原理：

```{language}
{code}
```

要求：
- 说明代码的整体功能
- 解释关键逻辑和算法
- 指出需要注意的重点"""
        ),
        REVIEW(
            keyword = "/review",
            description = "代码质量审查",
            template = """请对以下代码进行质量审查：

```{language}
{code}
```

要求：
- 指出代码存在的问题和风险
- 提出改进建议
- 评估代码的可读性、可维护性和性能"""
        ),
        TEST(
            keyword = "/test",
            description = "生成单元测试",
            template = """请为以下代码生成单元测试：

```{language}
{code}
```

要求：
- 使用 {testFramework} 框架
- 覆盖主要功能路径
- 包含边界条件测试
- 测试代码应该可以直接运行"""
        ),
        OPTIMIZE(
            keyword = "/optimize",
            description = "性能优化建议",
            template = """请对以下代码进行性能优化：

```{language}
{code}
```

要求：
- 识别性能瓶颈
- 提供具体的优化方案
- 给出优化前后的对比（如有可能）"""
        ),
        DEBUG(
            keyword = "/debug",
            description = "添加调试代码",
            template = """请为以下代码添加调试代码：

```{language}
{code}
```

要求：
- 添加合适的日志输出
- 包含错误处理和异常捕获
- 便于问题定位和追踪"""
        ),
        POLISH(
            keyword = "/polish",
            description = "优化代码表达",
            template = """请优化以下代码的表达方式：

```{language}
{code}
```

要求：
- 提高代码可读性
- 遵循最佳实践和编码规范
- 保持原有功能不变"""
        ),
        TRANSLATE(
            keyword = "/translate",
            description = "中英文互转",
            template = """请翻译以下内容：

{content}

要求：
- 保持原文格式
- 翻译准确、流畅
- 符合目标语言的表达习惯"""
        ),
        REFACTOR(
            keyword = "/refactor",
            description = "代码重构",
            template = """请对以下代码进行重构：

```{language}
{code}
```

要求：
- 改善代码结构
- 提高可维护性
- 保持功能不变
- 解释重构的动机和效果"""
        );

        companion object {
            /**
             * 根据关键词查找匹配的模板
             */
            fun findByKeyword(keyword: String): PromptTemplate? {
                val normalizedKeyword = keyword.trim().lowercase()
                return entries.find { it.keyword == normalizedKeyword }
            }

            /**
             * 检查输入是否包含模板关键词
             */
            fun matchTemplate(input: String): PromptTemplate? {
                val trimmed = input.trim()
                // 优先精确匹配
                entries.forEach { template ->
                    if (trimmed.startsWith(template.keyword, ignoreCase = true)) {
                        return template
                    }
                }
                return null
            }
        }
    }

    /**
     * 代码语言检测结果
     */
    data class CodeLanguageDetection(
        val language: String,
        val confidence: Double,
        val isDetected: Boolean
    )

    /**
     * 提示词上下文（文档规定的接口）
     *
     * 包含优化所需的全部上下文信息
     */
    data class PromptContext(
        /** 当前项目信息 */
        val projectInfo: ProjectInfo = ProjectInfo(),
        /** 选中的代码（来自编辑器） */
        val selectedCode: SelectedCode? = null,
        /** 相关文件列表 */
        val relatedFiles: List<RelatedFile> = emptyList(),
        /** 对话历史摘要 */
        val conversationHistory: ConversationSummary = ConversationSummary(),
        /** 原始输入 */
        val originalInput: String = "",
        /** 额外元数据 */
        val metadata: Map<String, Any> = emptyMap()
    ) {
        /**
         * 项目信息
         */
        data class ProjectInfo(
            val name: String = "",
            val path: String = "",
            val language: String = "",
            val framework: String = ""
        )

        /**
         * 选中的代码
         */
        data class SelectedCode(
            val code: String,
            val language: String = "",
            val filePath: String = "",
            val startLine: Int = 0,
            val endLine: Int = 0
        )

        /**
         * 相关文件
         */
        data class RelatedFile(
            val path: String,
            val content: String,
            val relevance: Double = 0.0
        )

        /**
         * 对话历史摘要
         */
        data class ConversationSummary(
            val messages: List<ChatMessage> = emptyList(),
            val totalTokens: Int = 0,
            val lastTopic: String = ""
        )
    }

    /**
     * 优化后的提示词（文档规定的数据类）
     */
    data class OptimizedPrompt(
        /** 优化后的提示词内容 */
        val content: String,
        /** 使用的模板（如果有） */
        val matchedTemplate: PromptTemplate? = null,
        /** 检测到的代码语言 */
        val detectedLanguage: CodeLanguageDetection? = null,
        /** 添加的上下文信息 */
        val addedContext: List<ContextInfo> = emptyList(),
        /** 截断信息 */
        val truncatedInfo: List<String> = emptyList(),
        /** 改进点列表 */
        val improvements: List<String> = emptyList(),
        /** 优化置信度 0.0-1.0 */
        val confidence: Double = 0.0
    )

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
        USER_CONTEXT,
        TEMPLATE_ENHANCED,
        LANGUAGE_DETECTED
    }

    // ==================== 核心API ====================

    /**
     * 优化提示词（文档规定的主要接口）
     *
     * @param input 原始输入
     * @param context 提示词上下文
     * @return 优化后的提示词
     */
    fun optimize(input: String, context: PromptContext): OptimizedPrompt {
        val addedContext = mutableListOf<ContextInfo>()
        val truncatedInfo = mutableListOf<String>()

        var processedInput = input

        // 1. 模板匹配
        val matchedTemplate = PromptTemplate.matchTemplate(input)

        // 2. 代码检测与格式化
        val codeDetection = detectAndFormatCode(processedInput, addedContext)
        processedInput = codeDetection.first
        val detectedLanguage = codeDetection.second

        // 3. 上下文注入
        processedInput = injectContext(processedInput, context, addedContext)

        // 4. 如果匹配到模板，应用模板
        if (matchedTemplate != null) {
            processedInput = applyTemplate(processedInput, matchedTemplate, context, addedContext)
        }

        // 5. 格式规范化
        processedInput = normalizePrompt(processedInput)

        return OptimizedPrompt(
            content = processedInput,
            matchedTemplate = matchedTemplate,
            detectedLanguage = detectedLanguage,
            addedContext = addedContext,
            truncatedInfo = truncatedInfo,
            improvements = emptyList(),  // 同步模式不支持AI优化
            confidence = if (matchedTemplate != null) 0.9 else 0.7
        )
    }

    /**
     * 优化提示词（异步版本，支持AI增强）
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

        // 5. AI 驱动的优化
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
     * 使用 PromptContext 异步优化
     */
    suspend fun optimizeAsync(
        input: String,
        context: PromptContext
    ): OptimizedPrompt = withContext(Dispatchers.Default) {
        val addedContext = mutableListOf<ContextInfo>()
        val truncatedInfo = mutableListOf<String>()

        var processedInput = input

        // 1. 模板匹配
        val matchedTemplate = PromptTemplate.matchTemplate(input)

        // 2. 代码检测与格式化
        val codeDetection = detectAndFormatCode(processedInput, addedContext)
        processedInput = codeDetection.first
        val detectedLanguage = codeDetection.second

        // 3. 上下文注入
        processedInput = injectContext(processedInput, context, addedContext)

        // 4. 如果匹配到模板，应用模板
        if (matchedTemplate != null) {
            processedInput = applyTemplate(processedInput, matchedTemplate, context, addedContext)
        }

        // 5. 格式规范化
        processedInput = normalizePrompt(processedInput)

        // 6. AI 驱动的优化
        val aiResult = performAiOptimization(processedInput, addedContext)

        OptimizedPrompt(
            content = aiResult.first,
            matchedTemplate = matchedTemplate,
            detectedLanguage = detectedLanguage,
            addedContext = addedContext,
            truncatedInfo = truncatedInfo,
            improvements = aiResult.second,
            confidence = aiResult.third
        )
    }

    // ==================== 模板应用 ====================

    /**
     * 应用模板到输入
     */
    private fun applyTemplate(
        input: String,
        template: PromptTemplate,
        context: PromptContext,
        addedContext: MutableList<ContextInfo>
    ): String {
        // 提取模板关键词后的内容
        var content = input.substringAfter(template.keyword).trim()

        // 检测代码语言
        val language = context.selectedCode?.language
            ?: detectLanguage(content)
            ?: "plaintext"

        // 根据模板类型构建提示词
        val enhancedContent = when (template) {
            PromptTemplate.EXPLAIN,
            PromptTemplate.REVIEW,
            PromptTemplate.TEST,
            PromptTemplate.OPTIMIZE,
            PromptTemplate.DEBUG,
            PromptTemplate.POLISH,
            PromptTemplate.REFACTOR -> {
                // 如果没有代码块包装，则添加
                if (!content.contains("```")) {
                    "```$language\n$content\n```"
                } else {
                    content
                }
            }
            PromptTemplate.TRANSLATE -> content
        }

        // 记录模板使用
        addedContext.add(ContextInfo(
            type = ContextType.TEMPLATE_ENHANCED,
            content = "Used template: ${template.keyword} - ${template.description}",
            metadata = mapOf("template" to template.name)
        ))

        // 构建带模板的完整提示词
        val templated = template.template
            .replace("{language}", language)
            .replace("{code}", extractCodeWithoutWrapper(content, language))
            .replace("{content}", content)
            .replace("{testFramework}", getTestFramework(language))

        return buildString {
            append(templated)
            if (context.selectedCode?.filePath?.isNotEmpty() == true) {
                append("\n\n文件路径: ${context.selectedCode.filePath}")
            }
        }
    }

    /**
     * 提取代码内容（去掉代码块包装）
     */
    private fun extractCodeWithoutWrapper(content: String, language: String): String {
        val codeBlockRegex = """```$language\n([\s\S]*?)```""".toRegex()
        val match = codeBlockRegex.find(content)
        return if (match != null) {
            match.groupValues[1]
        } else {
            // 尝试不带语言标识
            val plainCodeRegex = """```\n([\s\S]*?)```""".toRegex()
            val plainMatch = plainCodeRegex.find(content)
            plainMatch?.groupValues?.get(1) ?: content
        }
    }

    /**
     * 获取测试框架
     */
    private fun getTestFramework(language: String): String {
        return when (language.lowercase()) {
            "kotlin", "kt" -> "Kotlin Test / JUnit"
            "java" -> "JUnit"
            "javascript", "js" -> "Jest / Mocha"
            "typescript", "ts" -> "Jest"
            "python", "py" -> "pytest / unittest"
            "go" -> "testing"
            else -> "JUnit / standard testing library"
        }
    }

    // ==================== 代码检测与格式化 ====================

    /**
     * 检测代码语言并格式化
     *
     * @return Pair(处理后的输入, 检测结果)
     */
    private fun detectAndFormatCode(
        input: String,
        addedContext: MutableList<ContextInfo>
    ): Pair<String, CodeLanguageDetection?> {
        var processed = input
        var detection: CodeLanguageDetection? = null

        // 检测是否有未包装的代码
        val codePatterns = listOf(
            // 函数定义
            Regex("""(fun |def |function |func |class |interface |struct )\w*\s*\("""),
            // 常见语法结构
            Regex("""(val |var |let |const |int |String |bool |if |for |while )\w*"""),
            // 常见关键字
            Regex("""(import |package |using |require |include )"""),
            // 括号和分号组合
            Regex("""\{[\s\S]*;\s*\}""")
        )

        // 检查是否需要添加代码块
        val isLikelyCode = codePatterns.any { it.containsMatchIn(input) }
        val hasCodeBlock = input.contains("```")

        if (isLikelyCode && !hasCodeBlock) {
            // 检测语言
            val lang = detectLanguage(input)
            if (lang != null) {
                processed = "```$lang\n$input\n```"
                detection = CodeLanguageDetection(
                    language = lang,
                    confidence = 0.85,
                    isDetected = true
                )
                addedContext.add(ContextInfo(
                    type = ContextType.LANGUAGE_DETECTED,
                    content = "Detected language: $lang",
                    metadata = mapOf("language" to lang, "confidence" to 0.85)
                ))
            }
        }

        // 规范化已有的代码块
        if (hasCodeBlock) {
            processed = normalizeCodeBlocks(processed)
            // 尝试检测代码块语言
            val langInBlock = Regex("""```(\w*)\n""").find(processed)?.groupValues?.get(1)
            if (!langInBlock.isNullOrEmpty()) {
                detection = CodeLanguageDetection(
                    language = langInBlock,
                    confidence = 1.0,
                    isDetected = true
                )
            }
        }

        return Pair(processed, detection)
    }

    /**
     * 检测代码语言
     */
    fun detectLanguage(code: String): String? {
        if (code.isBlank()) return null

        val trimmed = code.trim()

        // Kotlin 检测
        if (trimmed.contains("fun ") && (trimmed.contains("val ") || trimmed.contains("var "))) {
            return "kotlin"
        }

        // Java 检测
        if (trimmed.contains("public class ") || trimmed.contains("public interface ")) {
            return "java"
        }

        // JavaScript/TypeScript 检测
        if (trimmed.contains("function ") || trimmed.contains("const ") || trimmed.contains("let ")) {
            if (trimmed.contains(": ") && (trimmed.contains("string") || trimmed.contains("number") || trimmed.contains("boolean"))) {
                return "typescript"
            }
            return "javascript"
        }

        // Python 检测
        if (trimmed.contains("def ") && trimmed.contains(":") && !trimmed.contains("{")) {
            return "python"
        }

        // Go 检测
        if (trimmed.contains("func ") && trimmed.contains("package ")) {
            return "go"
        }

        // JSON 检测
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                // 简单验证是否为有效JSON
                if (trimmed.contains("\"") && trimmed.contains(":")) {
                    return "json"
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // XML 检测
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<") && trimmed.endsWith(">")) {
            return "xml"
        }

        // SQL 检测
        if (trimmed.contains("SELECT ", ignoreCase = true) &&
            trimmed.contains("FROM ", ignoreCase = true)) {
            return "sql"
        }

        // HTML 检测
        if (trimmed.contains("<html", ignoreCase = true) ||
            trimmed.contains("<div", ignoreCase = true) ||
            trimmed.contains("<!DOCTYPE", ignoreCase = true)) {
            return "html"
        }

        // CSS 检测
        if (trimmed.contains("{") && trimmed.contains(":") &&
            (trimmed.contains("color") || trimmed.contains("margin") || trimmed.contains("padding"))) {
            return "css"
        }

        // Shell/Bash 检测
        if (trimmed.contains("#!/bin/bash") || trimmed.contains("echo ") ||
            (trimmed.contains("$") && trimmed.contains("="))) {
            return "bash"
        }

        return null
    }

    // ==================== 上下文注入 ====================

    /**
     * 注入上下文信息
     */
    private fun injectContext(
        input: String,
        context: PromptContext,
        addedContext: MutableList<ContextInfo>
    ): String {
        val builder = StringBuilder()

        // 1. 添加项目信息
        if (context.projectInfo.name.isNotEmpty()) {
            builder.append("<project>\n")
            builder.append("名称: ${context.projectInfo.name}\n")
            if (context.projectInfo.path.isNotEmpty()) {
                builder.append("路径: ${context.projectInfo.path}\n")
            }
            if (context.projectInfo.language.isNotEmpty()) {
                builder.append("语言: ${context.projectInfo.language}\n")
            }
            if (context.projectInfo.framework.isNotEmpty()) {
                builder.append("框架: ${context.projectInfo.framework}\n")
            }
            builder.append("</project>\n\n")
        }

        // 2. 添加选中代码信息
        context.selectedCode?.let { selected ->
            if (selected.code.isNotEmpty()) {
                builder.append("<selected_code>\n")
                if (selected.filePath.isNotEmpty()) {
                    builder.append("文件: ${selected.filePath}\n")
                }
                if (selected.language.isNotEmpty()) {
                    builder.append("语言: ${selected.language}\n")
                }
                builder.append("代码:\n```${selected.language.ifEmpty { "plaintext" }}\n${selected.code}\n```\n")
                builder.append("</selected_code>\n\n")
            }
        }

        // 3. 添加相关文件
        if (context.relatedFiles.isNotEmpty()) {
            builder.append("<related_files>\n")
            context.relatedFiles.take(5).forEach { file ->
                builder.append("文件: ${file.path}\n")
                builder.append("内容:\n```\n${file.content.take(2000)}\n```\n\n")
            }
            builder.append("</related_files>\n\n")
        }

        // 4. 添加对话历史摘要
        if (context.conversationHistory.messages.isNotEmpty()) {
            builder.append("<conversation_history>\n")
            val recentMessages = context.conversationHistory.messages.takeLast(5)
            recentMessages.forEach { msg ->
                val role = msg.role.name.lowercase()
                val content = msg.content.take(300)
                builder.append("$role: $content\n")
            }
            builder.append("</conversation_history>\n\n")
        }

        builder.append(input)
        return builder.toString()
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