# Phase 4: 功能模块 (Feature Modules)

**优先级**: P1
**预估工期**: 10人天
**前置依赖**: Phase 1 + Phase 2 + Phase 3
**阶段目标**: 实现交互增强、多模态输入、对话引用、任务进度等PRD定义的功能模块后端逻辑

---

> **⚠️ SDK集成架构修订 (2026-04-08)**
>
> 本阶段文档需进行以下关键适配。Phase 2.5 SDK 集成方案改变了底层通信模型。
>
> ### ★ 必须移除的任务
>
> | 任务 | 原因 | 处理方式 |
> |------|------|----------|
> | **T4.6 完善多供应商实现** (T4.6.1 OpenAIProvider + T4.6.2 DeepSeekProvider) | Phase 2.5 已废弃直接HTTP API调用，SDK通过CLI子进程统一管理 | **整个T4.6任务移除**，节省2人天 |
>
> ### ★ 必须修改的任务
>
> | 任务 | 原方案 | SDK适配方案 |
> |------|--------|-------------|
> | **T4.1 InteractiveRequestEngine** | 从AI响应文本中用正则提取问题 | 改为接收 `SdkAssistantMessage`，通过 `ContentBlock.ToolUse` 检测交互请求 |
> | **T4.2 PromptOptimizer** | 调用 `providerAdapter.chat()` 直接HTTP | 改为调用 `ClaudeCodeClient.sendMessage()` 通过SDK |
>
> ### InteractiveRequestEngine 修改指引
>
> ```kotlin
> // 旧：从文本中正则匹配（删除）
> private fun extractQuestions(response: String): List<InteractiveQuestion> { ... }
>
> // 新：从SDK tool_use块中提取
> fun handleAssistantMessage(message: SdkMessageTypes.SdkAssistantMessage): ProcessResult {
>     val toolUses = message.extractToolUses()
>     val questions = toolUses
>         .filter { it.name == "ask_user" || it.name == "request_input" }
>         .map { toolUse ->
>             InteractiveQuestion(
>                 questionId = toolUse.id,
>                 question = toolUse.input.get("question")?.asString ?: "",
>                 questionType = QuestionType.SINGLE_CHOICE,
>                 options = parseOptionsFromInput(toolUse.input)
>             )
>         }
>     return if (questions.isNotEmpty()) ProcessResult.WaitingForUser(questions)
>            else ProcessResult.Completed(message.extractText())
> }
> ```
>
> ### PromptOptimizer 修改指引
>
> ```kotlin
> // 旧（删除）:
> class PromptOptimizer(private val providerAdapter: MultiProviderAdapter)
>
> // 新（替换）:
> class PromptOptimizer(private val project: Project) {
>     private val claudeClient: ClaudeCodeClient by lazy { ClaudeCodeClient.getInstance(project) }
>
>     suspend fun optimizePrompt(originalPrompt: String): Result<OptimizedPrompt> {
>         return claudeClient.sendMessage(
>             "Optimize this prompt: $originalPrompt",
>             SdkOptions(systemPrompt = optimizationSystemPrompt, maxTurns = 1)
>         ).map { result ->
>             parseOptimizedResponse(originalPrompt, result.result ?: "")
>         }
>     }
> }
> ```
>
> ### 无需修改的任务
>
> | 任务 | 说明 |
> |------|------|
> | T4.3 MultimodalInputHandler | 文件处理逻辑不变，结果作为附件传入 `ChatMessage` |
> | T4.4 ConversationReferenceSystem | 引用构建逻辑不变 |
> | T4.5 TaskParser + TaskProgressTracker | 任务解析和追踪逻辑不变 |
>
> ### 调整后的文件清单
>
> | 序号 | 文件路径 | 类型 |
> |------|----------|------|
> | 1 | `application/interaction/InteractiveRequestEngine.kt` | 核心服务 |
> | 2 | `application/prompt/PromptOptimizer.kt` | 优化器 |
> | 3 | `application/multimodal/MultimodalInputHandler.kt` | 处理器 |
> | 4 | `application/reference/ConversationReferenceSystem.kt` | 引用系统 |
> | 5 | `application/task/TaskParser.kt` | 解析器 |
> | 6 | `application/task/TaskProgressTracker.kt` | 追踪器 |
>
> ~~T4.6.1 OpenAIProvider.kt~~ 已废弃 | ~~T4.6.2 DeepSeekProvider.kt~~ 已废弃
>
> **共计**: 6个文件（原为 6+2完善=8，移除2个废弃文件后为6个）

---

## 1. 阶段概览

本阶段在核心服务之上构建**用户可感知的功能模块**：

1. **InteractiveRequestEngine**: AI主动提问的交互式引擎
2. **PromptOptimizer**: 提示词优化
3. **MultimodalInputHandler**: 多模态输入处理
4. **ConversationReferenceSystem**: 对话引用
5. **TaskProgressTracker + TaskParser**: 任务进度追踪
6. **OpenAIProvider / DeepSeekProvider**: 完善多供应商实现

---

## 2. 任务清单

### T4.1 交互式请求引擎 (3人天) — PRD核心功能

#### T4.1.1 `application/interaction/InteractiveRequestEngine.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.interaction

import com.github.xingzhewa.ccgui.application.orchestrator.ChatOrchestrator
import com.github.xingzhewa.ccgui.application.session.SessionManager
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.QuestionAskedEvent
import com.github.xingzhewa.ccgui.infrastructure.eventbus.QuestionAnsweredEvent
import com.github.xingzhewa.ccgui.model.interaction.InteractiveQuestion
import com.github.xingzhewa.ccgui.model.interaction.QuestionOption
import com.github.xingzhewa.ccgui.model.interaction.QuestionType
import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.message.MessageRole
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 交互式请求引擎 — PRD核心功能
 *
 * 职责:
 *   1. 检测AI响应中的交互式请求（问题）
 *   2. 暂停当前处理流程
 *   3. 向用户展示问题
 *   4. 接收用户回答后继续处理
 *
 * 状态机:
 *   IDLE → PROCESSING → WAITING_FOR_USER → PROCESSING → COMPLETED
 *                                        ↘ ERROR
 *
 * 扩展埋点:
 *   - 后续可支持多种问题检测策略（NLP/正则/JSON标记）
 *   - 后续可添加超时自动跳过
 */
@Service(Service.Level.PROJECT)
class InteractiveRequestEngine(
    private val project: Project
) {
    private val log = logger<InteractiveRequestEngine>()
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(project) }

    /** 待回答的问题: questionId -> InteractiveQuestion */
    private val pendingQuestions = ConcurrentHashMap<String, InteractiveQuestion>()

    /** 交互状态 */
    private val _interactionState = MutableStateFlow(InteractionState.IDLE)
    val interactionState: StateFlow<InteractionState> = _interactionState.asStateFlow()

    /** 当前等待的问题列表 */
    private val _pendingQuestionsList = MutableStateFlow<List<InteractiveQuestion>>(emptyList())
    val pendingQuestionsList: StateFlow<List<InteractiveQuestion>> = _pendingQuestionsList.asStateFlow()

    enum class InteractionState {
        IDLE,
        PROCESSING,
        WAITING_FOR_USER,
        ERROR
    }

    sealed class ProcessResult {
        data class Completed(val content: String) : ProcessResult()
        data class WaitingForUser(val questions: List<InteractiveQuestion>) : ProcessResult()
        data class Error(val message: String) : ProcessResult()
    }

    /**
     * 处理AI响应
     * 检测其中是否包含需要用户回答的问题
     */
    suspend fun handleAIResponse(response: String): ProcessResult {
        _interactionState.value = InteractionState.PROCESSING

        val questions = extractQuestions(response)

        return if (questions.isNotEmpty()) {
            // 暂停处理，向用户展示问题
            questions.forEach { question ->
                pendingQuestions[question.questionId] = question
            }

            _pendingQuestionsList.value = questions
            _interactionState.value = InteractionState.WAITING_FOR_USER

            questions.forEach { question ->
                EventBus.publish(QuestionAskedEvent(question.questionId, question.question))
            }

            ProcessResult.WaitingForUser(questions)
        } else {
            _interactionState.value = InteractionState.IDLE
            ProcessResult.Completed(response)
        }
    }

    /**
     * 提交用户回答
     */
    suspend fun submitAnswer(questionId: String, answer: Any): Result<Unit> {
        val question = pendingQuestions.remove(questionId)
            ?: return Result.failure(IllegalArgumentException("Question not found: $questionId"))

        // 更新待回答列表
        _pendingQuestionsList.value = _pendingQuestionsList.value.filter { it.questionId != questionId }
        if (_pendingQuestionsList.value.isEmpty()) {
            _interactionState.value = InteractionState.IDLE
        }

        EventBus.publish(QuestionAnsweredEvent(questionId))

        // 构建上下文更新
        val contextUpdate = buildContextUpdate(question, answer)
        val session = sessionManager.getCurrentSession()

        // 添加用户回答到会话
        session?.addMessage(ChatMessage(
            role = MessageRole.USER,
            content = contextUpdate,
            metadata = mutableMapOf("isQuestionAnswer" to true, "questionId" to questionId)
        ))

        log.info("User answered question $questionId: $answer")
        return Result.success(Unit)
    }

    /**
     * 跳过问题
     */
    fun skipQuestion(questionId: String) {
        pendingQuestions.remove(questionId)
        _pendingQuestionsList.value = _pendingQuestionsList.value.filter { it.questionId != questionId }
        if (_pendingQuestionsList.value.isEmpty()) {
            _interactionState.value = InteractionState.IDLE
        }
    }

    /**
     * 提取AI响应中的问题
     * 策略: 正则匹配 + JSON结构解析
     */
    private fun extractQuestions(response: String): List<InteractiveQuestion> {
        val questions = mutableListOf<InteractiveQuestion>()

        // 策略1: 检测特殊标记 [QUESTION:id]...[/QUESTION]
        val markerPattern = Regex(
            """\[QUESTION:([^\]]+)\]\s*(.*?)\s*(?:OPTIONS:\s*(.*?))?\[/QUESTION\]""",
            RegexOption.DOT_MATCHES_ALL
        )
        markerPattern.findAll(response).forEach { match ->
            val id = match.groupValues[1]
            val text = match.groupValues[2]
            val optionsStr = match.groupValues[3]

            questions.add(InteractiveQuestion(
                questionId = id,
                question = text.trim(),
                questionType = if (optionsStr.isNotBlank()) QuestionType.SINGLE_CHOICE else QuestionType.TEXT_INPUT,
                options = parseOptionList(optionsStr)
            ))
        }

        // 策略2: 检测JSON格式的问题
        if (questions.isEmpty() && response.contains("\"question\":")) {
            // 尝试解析JSON格式的问题
            tryParseJsonQuestions(response, questions)
        }

        return questions
    }

    private fun parseOptionList(optionsStr: String): List<QuestionOption> {
        if (optionsStr.isBlank()) return emptyList()
        return optionsStr.split("|").mapIndexed { index, option ->
            QuestionOption(
                id = "opt_$index",
                label = option.trim()
            )
        }
    }

    private fun tryParseJsonQuestions(response: String, questions: MutableList<InteractiveQuestion>) {
        // JSON解析逻辑，后续实现
    }

    private fun buildContextUpdate(question: InteractiveQuestion, answer: Any): String {
        return when (question.questionType) {
            QuestionType.SINGLE_CHOICE -> {
                val option = question.options.find { it.id == answer.toString() }
                "For the question \"${question.question}\", I choose: ${option?.label ?: answer}"
            }
            QuestionType.TEXT_INPUT -> "For the question \"${question.question}\", my answer: $answer"
            QuestionType.CONFIRMATION -> "For the question \"${question.question}\", I confirm: $answer"
            else -> "Answer: $answer"
        }
    }

    companion object {
        fun getInstance(project: Project): InteractiveRequestEngine =
            project.getService(InteractiveRequestEngine::class.java)
    }
}
```

---

### T4.2 提示词优化 (1.5人天)

#### T4.2.1 `application/prompt/PromptOptimizer.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.prompt

import com.github.xingzhewa.ccgui.adaptation.provider.MultiProviderAdapter
import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.message.MessageRole
import com.github.xingzhewa.ccgui.model.provider.ChatRequest
import com.github.xingzhewa.ccgui.util.logger

/**
 * 提示词优化器
 * 调用AI优化用户的提示词，使其更清晰、具体、结构化
 *
 * 扩展埋点:
 *   - 后续可支持多种优化策略模板
 *   - 后续可添加优化质量评分
 */
class PromptOptimizer(
    private val providerAdapter: MultiProviderAdapter
) {
    private val log = logger<PromptOptimizer>()

    data class OptimizedPrompt(
        val original: String,
        val optimized: String,
        val improvements: List<String>,
        val confidence: Double
    )

    private val optimizationSystemPrompt = """
        You are a prompt engineering expert. Your task is to optimize the user's prompt to make it clearer, more specific, and structured.

        Rules:
        1. Keep the original intent
        2. Add specific context and constraints
        3. Structure the prompt with clear sections
        4. Add output format requirements

        Return the optimized prompt in this JSON format:
        {"optimized": "...", "improvements": ["..."], "confidence": 0.95}
    """.trimIndent()

    /**
     * 优化提示词
     */
    suspend fun optimizePrompt(
        originalPrompt: String,
        provider: String = "anthropic",
        model: String = "claude-sonnet-4-20250514"
    ): Result<OptimizedPrompt> {
        return try {
            val response = providerAdapter.chat(
                provider,
                ChatRequest(
                    model = model,
                    messages = listOf(
                        ChatMessage(role = MessageRole.SYSTEM, content = optimizationSystemPrompt),
                        ChatMessage(role = MessageRole.USER, content = originalPrompt)
                    ),
                    maxTokens = 2048,
                    systemPrompt = optimizationSystemPrompt
                )
            )

            // 解析优化结果
            parseOptimizedResponse(originalPrompt, response.content)
        } catch (e: Exception) {
            log.error("Prompt optimization failed", e)
            Result.failure(e)
        }
    }

    private fun parseOptimizedResponse(original: String, response: String): Result<OptimizedPrompt> {
        // 尝试从响应中提取JSON
        val jsonPattern = Regex("""\{[\s\S]*"optimized"[\s\S]*\}""")
        val jsonMatch = jsonPattern.find(response)

        return if (jsonMatch != null) {
            try {
                val json = com.github.xingzhewa.ccgui.util.JsonUtils.parseObject(jsonMatch.value)
                Result.success(OptimizedPrompt(
                    original = original,
                    optimized = json.get("optimized")?.asString ?: response,
                    improvements = json.getAsJsonArray("improvements")?.map { it.asString } ?: emptyList(),
                    confidence = json.get("confidence")?.asDouble ?: 0.8
                ))
            } catch (e: Exception) {
                Result.success(OptimizedPrompt(original, response, emptyList(), 0.5))
            }
        } else {
            Result.success(OptimizedPrompt(original, response, emptyList(), 0.5))
        }
    }
}
```

---

### T4.3 多模态输入处理 (2人天)

#### T4.3.1 `application/multimodal/MultimodalInputHandler.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.multimodal

import com.github.xingzhewa.ccgui.model.message.ContentPart
import com.github.xingzhewa.ccgui.util.logger
import java.io.File
import java.nio.file.Files
import java.util.Base64

/**
 * 多模态输入处理器
 * 处理图片、附件、代码文件等非文本输入
 *
 * 支持格式:
 *   - 图片: PNG, JPG, JPEG, GIF, WebP
 *   - 文档: PDF, DOCX, TXT, MD, JSON, XML, YAML
 *   - 代码: 所有常见编程语言文件
 *
 * 扩展埋点:
 *   - 后续可添加音频输入
 *   - 后续可添加URL内容提取
 */
class MultimodalInputHandler {

    private val log = logger<MultimodalInputHandler>()

    /** 支持的图片格式 */
    private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp")

    /** 支持的文档格式 */
    private val documentExtensions = setOf("pdf", "docx", "txt", "md", "json", "xml", "yaml", "yml")

    /** 图片大小限制: 10MB */
    private val maxImageSize = 10 * 1024 * 1024L

    /**
     * 处理文件输入
     * 根据文件类型自动选择处理方式
     */
    fun handleFile(file: File): Result<ContentPart> {
        return when {
            isImage(file) -> handleImage(file)
            isDocument(file) -> handleDocument(file)
            else -> handleTextFile(file)
        }
    }

    /**
     * 处理图片文件
     */
    fun handleImage(file: File): Result<ContentPart.Image> {
        if (file.length() > maxImageSize) {
            return Result.failure(IllegalArgumentException("Image too large: ${file.length()} bytes (max: $maxImageSize)"))
        }

        return try {
            val base64Data = Base64.getEncoder().encodeToString(file.readBytes())
            val mimeType = Files.probeContentType(file.toPath()) ?: "image/png"

            Result.success(ContentPart.Image(
                mimeType = mimeType,
                data = base64Data,
                fileName = file.name
            ))
        } catch (e: Exception) {
            log.error("Failed to process image: ${file.name}", e)
            Result.failure(e)
        }
    }

    /**
     * 处理文档文件
     */
    fun handleDocument(file: File): Result<ContentPart.Text> {
        return try {
            val content = when (file.extension.lowercase()) {
                "pdf" -> "[PDF file: ${file.name}] (PDF content extraction not yet implemented)"
                "docx" -> "[DOCX file: ${file.name}] (DOCX content extraction not yet implemented)"
                else -> file.readText()
            }

            Result.success(ContentPart.Text(
                text = "[File: ${file.name}]\n$content",
                language = file.extension
            ))
        } catch (e: Exception) {
            log.error("Failed to process document: ${file.name}", e)
            Result.failure(e)
        }
    }

    /**
     * 处理文本/代码文件
     */
    fun handleTextFile(file: File): Result<ContentPart.Text> {
        return try {
            val content = file.readText()
            Result.success(ContentPart.Text(
                text = "[File: ${file.name}]\n```${file.extension}\n$content\n```",
                language = file.extension
            ))
        } catch (e: Exception) {
            log.error("Failed to process text file: ${file.name}", e)
            Result.failure(e)
        }
    }

    /**
     * 处理剪贴板图片（Base64格式）
     */
    fun handleClipboardImage(base64Data: String, mimeType: String = "image/png"): ContentPart.Image {
        return ContentPart.Image(
            mimeType = mimeType,
            data = base64Data
        )
    }

    private fun isImage(file: File): Boolean =
        file.extension.lowercase() in imageExtensions

    private fun isDocument(file: File): Boolean =
        file.extension.lowercase() in documentExtensions
}
```

---

### T4.4 对话引用系统 (1.5人天)

#### T4.4.1 `application/reference/ConversationReferenceSystem.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.reference

import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.message.MessageRole
import com.github.xingzhewa.ccgui.util.logger
import java.text.SimpleDateFormat
import java.util.*

/**
 * 对话引用系统
 * 支持引用历史消息内容，方便上下文延续
 *
 * 扩展埋点:
 *   - 后续可支持跨会话引用
 *   - 后续可添加智能推荐相关消息
 */
class ConversationReferenceSystem {

    private val log = logger<ConversationReferenceSystem>()

    data class MessageReference(
        val messageId: String,
        val excerpt: String,
        val timestamp: Long,
        val sender: MessageRole
    ) {
        fun formatTimestamp(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    /**
     * 从消息创建引用
     */
    fun createReference(message: ChatMessage): MessageReference {
        return MessageReference(
            messageId = message.id,
            excerpt = message.content.take(200).let {
                if (message.content.length > 200) "$it..." else it
            },
            timestamp = message.timestamp,
            sender = message.role
        )
    }

    /**
     * 构建带引用的Prompt
     */
    fun buildPromptWithReferences(
        userPrompt: String,
        references: List<MessageReference>
    ): String {
        if (references.isEmpty()) return userPrompt

        val referenceBlock = references.joinToString("\n\n") { ref ->
            """
            [Reference @${ref.messageId}] (${ref.formatTimestamp()}, ${ref.sender.name}):
            ${ref.excerpt}
            """.trimIndent()
        }

        return """
            $referenceBlock

            ---

            User question: $userPrompt
        """.trimIndent()
    }

    /**
     * 解析prompt中的消息ID引用 (@msg_xxx)
     */
    fun extractMessageReferences(prompt: String): List<String> {
        val pattern = Regex("""@msg_[a-f0-9\-]+""")
        return pattern.findAll(prompt).map { it.value.removePrefix("@") }.toList()
    }
}
```

---

### T4.5 任务进度追踪 (2人天)

#### T4.5.1 `application/task/TaskParser.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.task

import com.github.xingzhewa.ccgui.model.task.TaskStep
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger

/**
 * 任务解析器
 * 从AI响应中提取任务分解信息
 *
 * 支持的解析策略:
 *   1. JSON结构化输出: {"tasks": [{"name": "...", "description": "..."}]}
 *   2. 标记格式: [Task 1: Step name]
 *   3. 编号列表: 1. Step name
 *
 * 扩展埋点: 后续可添加更多解析策略
 */
class TaskParser {

    private val log = logger<TaskParser>()

    /**
     * 解析AI响应中的任务分解
     */
    fun parseTaskDecomposition(response: String): List<TaskStep> {
        val steps = mutableListOf<TaskStep>()

        // 策略1: JSON格式
        if (response.contains("\"tasks\"")) {
            steps.addAll(parseJsonTasks(response))
        }

        // 策略2: 标记格式 [Task N: name]
        if (steps.isEmpty()) {
            steps.addAll(parseMarkerTasks(response))
        }

        // 策略3: 编号列表 1. name
        if (steps.isEmpty()) {
            steps.addAll(parseNumberedList(response))
        }

        return steps
    }

    private fun parseJsonTasks(response: String): List<TaskStep> {
        return try {
            val jsonPattern = Regex("""\{[\s\S]*"tasks"[\s\S]*\}""")
            val jsonMatch = jsonPattern.find(response) ?: return emptyList()
            val json = JsonUtils.parseObject(jsonMatch.value)
            val tasksArray = json.getAsJsonArray("tasks") ?: return emptyList()

            tasksArray.mapIndexed { index, element ->
                val task = element.asJsonObject
                TaskStep(
                    id = "step_${index + 1}",
                    name = task.get("name")?.asString ?: "Step ${index + 1}",
                    description = task.get("description")?.asString ?: ""
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseMarkerTasks(response: String): List<TaskStep> {
        val pattern = Regex("""\[Task\s+(\d+)[:\s]+([^\]]+)\]""")
        return pattern.findAll(response).map { match ->
            TaskStep(
                id = "step_${match.groupValues[1]}",
                name = match.groupValues[2].trim()
            )
        }.toList()
    }

    private fun parseNumberedList(response: String): List<TaskStep> {
        val pattern = Regex("""^\s*(\d+)\.\s+(.+)$""", RegexOption.MULTILINE)
        return pattern.findAll(response).take(20).map { match ->
            TaskStep(
                id = "step_${match.groupValues[1]}",
                name = match.groupValues[2].trim().take(100)
            )
        }.toList()
    }
}
```

---

#### T4.5.2 `application/task/TaskProgressTracker.kt`

```kotlin
package com.github.xingzhewa.ccgui.application.task

import com.github.xingzhewa.ccgui.browser.CefBrowserPanel
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.model.task.*
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 任务进度追踪器
 * 追踪AI任务执行进度，推送到JCEF前端展示
 *
 * 扩展埋点:
 *   - 后续可添加预估时间算法
 *   - 后续可添加任务历史统计
 */
class TaskProgressTracker(
    private val browserPanel: CefBrowserPanel
) {
    private val log = logger<TaskProgressTracker>()
    private val activeTasks = ConcurrentHashMap<String, TaskProgress>()

    private val _activeTaskList = MutableStateFlow<List<TaskProgress>>(emptyList())
    val activeTaskList: StateFlow<List<TaskProgress>> = _activeTaskList.asStateFlow()

    private val taskParser = TaskParser()

    /**
     * 从AI响应创建任务
     */
    fun createTaskFromResponse(response: String): TaskProgress? {
        val steps = taskParser.parseTaskDecomposition(response)
        if (steps.isEmpty()) return null

        val taskId = com.github.xingzhewa.ccgui.util.IdGenerator.taskId()
        val task = TaskProgress(
            taskId = taskId,
            totalSteps = steps.size,
            steps = steps
        )

        activeTasks[taskId] = task
        updateActiveTaskList()
        notifyProgressUpdate(task)

        EventBus.publish(com.github.xingzhewa.ccgui.infrastructure.eventbus.TaskCreatedEvent(taskId, steps.size))
        return task
    }

    /**
     * 更新步骤状态
     */
    fun updateStep(taskId: String, stepId: String, status: StepStatus, output: String? = null) {
        val task = activeTasks[taskId] ?: return

        val updatedSteps = task.steps.map { step ->
            if (step.id == stepId) {
                step.copy(
                    status = status,
                    output = output,
                    startTime = if (status == StepStatus.IN_PROGRESS) System.currentTimeMillis() else step.startTime,
                    endTime = if (status == StepStatus.COMPLETED || status == StepStatus.FAILED) System.currentTimeMillis() else step.endTime
                )
            } else step
        }

        val currentStep = updatedSteps.indexOfFirst { it.id == stepId }
        val taskStatus = when {
            updatedSteps.all { it.status == StepStatus.COMPLETED } -> TaskStatus.COMPLETED
            updatedSteps.any { it.status == StepStatus.FAILED } -> TaskStatus.FAILED
            updatedSteps.any { it.status == StepStatus.IN_PROGRESS } -> TaskStatus.IN_PROGRESS
            else -> TaskStatus.PENDING
        }

        val updatedTask = task.copy(
            steps = updatedSteps,
            currentStep = if (status == StepStatus.COMPLETED) currentStep + 1 else currentStep,
            status = taskStatus
        )

        activeTasks[taskId] = updatedTask
        updateActiveTaskList()
        notifyProgressUpdate(updatedTask)

        EventBus.publish(com.github.xingzhewa.ccgui.infrastructure.eventbus.TaskStepUpdatedEvent(taskId, stepId, status.name))
    }

    /**
     * 完成任务
     */
    fun completeTask(taskId: String) {
        activeTasks.remove(taskId)
        updateActiveTaskList()
        EventBus.publish(com.github.xingzhewa.ccgui.infrastructure.eventbus.TaskCompletedEvent(taskId))
    }

    private fun notifyProgressUpdate(progress: TaskProgress) {
        browserPanel.sendToJavaScript("taskProgress", JsonUtils.toJson(progress))
    }

    private fun updateActiveTaskList() {
        _activeTaskList.value = activeTasks.values.toList()
    }
}
```

---

### T4.6 完善多供应商实现 (2人天)

#### T4.6.1 完善 `OpenAIProvider.kt`

**文件**: `adaptation/provider/OpenAIProvider.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.provider

import com.github.xingzhewa.ccgui.infrastructure.storage.SecureStorage
import com.github.xingzhewa.ccgui.model.provider.*
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI API供应商实现
 * 支持GPT-4、GPT-4 Turbo、GPT-3.5 Turbo
 */
class OpenAIProvider : AIProvider {

    override val name = "openai"
    override val availableModels = listOf("gpt-4", "gpt-4-turbo", "gpt-3.5-turbo")

    private val log = logger<OpenAIProvider>()
    private val baseUrl = "https://api.openai.com/v1/chat/completions"
    private val storage get() = SecureStorage.getInstance()

    override suspend fun chat(request: ChatRequest): ChatResponse {
        val startTime = System.currentTimeMillis()
        val apiKey = storage.getApiKey(name)
            ?: throw IllegalStateException("OpenAI API key not configured")

        val connection = URL(baseUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.doOutput = true

        val body = buildRequestBody(request)
        connection.outputStream.write(body.toByteArray())

        if (connection.responseCode != 200) {
            val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw RuntimeException("OpenAI API error (${connection.responseCode}): $error")
        }

        val response = connection.inputStream.bufferedReader().readText()
        val json = JsonUtils.parseObject(response)

        val choice = json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
        val content = choice?.getAsJsonObject("message")?.get("content")?.asString ?: ""
        val usage = json.getAsJsonObject("usage")

        return ChatResponse(
            content = content,
            model = json.get("model")?.asString ?: request.model,
            tokensUsed = TokenUsage(
                promptTokens = usage?.get("prompt_tokens")?.asInt ?: 0,
                completionTokens = usage?.get("completion_tokens")?.asInt ?: 0
            ),
            executionTimeMs = System.currentTimeMillis() - startTime,
            requestId = json.get("id")?.asString
        )
    }

    override suspend fun streamChat(request: ChatRequest): Flow<ChatChunk> = flow {
        val apiKey = storage.getApiKey(name)
            ?: throw IllegalStateException("OpenAI API key not configured")

        val connection = URL(baseUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.doOutput = true

        val body = buildRequestBody(request).let {
            val json = JsonUtils.parseObject(it)
            json.addProperty("stream", true)
            json.toString()
        }
        connection.outputStream.write(body.toByteArray())

        val reader = BufferedReader(InputStreamReader(connection.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line!!
            if (!l.startsWith("data: ")) continue
            val data = l.removePrefix("data: ").trim()
            if (data == "[DONE]") {
                emit(ChatChunk(content = "", isComplete = true))
                break
            }
            try {
                val eventJson = JsonUtils.parseObject(data)
                val delta = eventJson.getAsJsonArray("choices")
                    ?.firstOrNull()?.asJsonObject?.getAsJsonObject("delta")
                val content = delta?.get("content")?.asString ?: ""
                if (content.isNotEmpty()) {
                    emit(ChatChunk(content = content))
                }
            } catch (e: Exception) {
                log.warn("Failed to parse SSE event: $data", e)
            }
        }
        reader.close()
    }

    private fun buildRequestBody(request: ChatRequest): String {
        val json = JsonObject().apply {
            addProperty("model", request.model)
            addProperty("max_tokens", request.maxTokens)

            val messagesArray = JsonArray()
            request.messages.forEach { msg ->
                messagesArray.add(JsonObject().apply {
                    addProperty("role", msg.role.toApiString())
                    addProperty("content", msg.content)
                })
            }
            add("messages", messagesArray)
        }
        return json.toString()
    }
}
```

---

#### T4.6.2 完善 `DeepSeekProvider.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.provider

// DeepSeek使用OpenAI兼容协议
// 基本结构同OpenAIProvider，只需更改baseUrl和header
// 此处省略重复代码，实现时继承或委托OpenAIProvider即可

class DeepSeekProvider : AIProvider {
    override val name = "deepseek"
    override val availableModels = listOf("deepseek-chat", "deepseek-coder")

    private val baseUrl = "https://api.deepseek.com/v1/chat/completions"
    // 实现同OpenAIProvider，仅baseUrl不同
    // TODO: 实现时复用OpenAIProvider逻辑

    override suspend fun chat(request: com.github.xingzhewa.ccgui.model.provider.ChatRequest): com.github.xingzhewa.ccgui.model.provider.ChatResponse {
        TODO("复用OpenAI协议实现")
    }

    override suspend fun streamChat(request: com.github.xingzhewa.ccgui.model.provider.ChatRequest): kotlinx.coroutines.flow.Flow<com.github.xingzhewa.ccgui.model.provider.ChatChunk> {
        TODO("复用OpenAI协议实现")
    }
}
```

---

## 3. plugin.xml 新增注册

```xml
<!-- Phase 4 新增 -->
<projectService serviceImplementation="com.github.xingzhewa.ccgui.application.interaction.InteractiveRequestEngine"/>
```

---

## 4. 任务依赖

```
T4.1 InteractiveRequestEngine ← 依赖 SessionManager + EventBus(Phase3)
  ↓ (可并行)
T4.2 PromptOptimizer ← 依赖 MultiProviderAdapter(Phase2)
T4.3 MultimodalInputHandler ← 独立
T4.4 ConversationReferenceSystem ← 独立
T4.5 TaskProgressTracker ← 依赖 CefBrowserPanel(Phase1)
T4.5 TaskParser ← 独立
T4.6 OpenAIProvider ← 依赖 SecureStorage(Phase1)
T4.6 DeepSeekProvider ← 依赖 OpenAIProvider
```

---

## 5. 验收标准

| 验收项 | 标准 |
|--------|------|
| 交互式请求 | AI可主动提问，用户回答后继续处理 |
| 提示词优化 | 优化后提示词质量提升可感知 |
| 多模态输入 | 可处理图片(Base64)和文本文件 |
| 对话引用 | 可引用历史消息构建上下文 |
| 任务进度 | AI任务可被解析为步骤并追踪 |
| OpenAI | 可调用GPT-4 API |
| DeepSeek | 可调用DeepSeek API |

---

## 6. 文件清单汇总

| 序号 | 文件路径 | 类型 |
|------|----------|------|
| 1 | `application/interaction/InteractiveRequestEngine.kt` | 核心服务 |
| 2 | `application/prompt/PromptOptimizer.kt` | 优化器 |
| 3 | `application/multimodal/MultimodalInputHandler.kt` | 处理器 |
| 4 | `application/reference/ConversationReferenceSystem.kt` | 引用系统 |
| 5 | `application/task/TaskParser.kt` | 解析器 |
| 6 | `application/task/TaskProgressTracker.kt` | 追踪器 |

**共计**: 6个新文件 + 2个完善文件（OpenAIProvider、DeepSeekProvider）
