# Phase 1: 基础设施与数据模型 (Foundation)

> **⚠️ SDK集成架构注释 (2026-04-08)**
>
> Phase 1 的数据模型和基础设施组件基本不受SDK集成影响，但以下几项需要注意：
>
> ### 受影响的模型/组件
>
> | 组件 | 影响说明 |
> |------|----------|
> | `AIProvider` 接口 + `ChatRequest`/`ChatResponse` | **降级为辅助类型**。SDK架构下不再直接调用HTTP API，但保留这些类型用于内部消息传递。`ChatChunk` 在 `ChatOrchestrator` → JCEF 之间仍用作流式传输单元 |
> | `ModelConfig.apiKey` 字段 | SDK通过环境变量 `ANTHROPIC_API_KEY` 管理认证，`apiKey` 字段实际不在插件中使用。保留以兼容配置UI |
> | `SecureStorage` | **用途缩减**。SDK自行管理API密钥认证，SecureStorage 仅用于存储非SDK配置（如自定义Provider的密钥，如果将来需要） |
> | `ConfigStorage.State` | **缺失字段修复**：`topP` 字段未持久化（与 `ModelConfig.topP` 不对应），`checkpointEnabled` 未在 `fromAppConfig()` 中回写。实现时需补齐 |
> | `EventBus.publish()` | **实现Bug**：如果 `subscribe()` 尚未被调用，`publish()` 会静默丢弃事件。实现时需在 `publish()` 中使用 `getOrPut` 确保 channel 存在 |
> | `ChatSession` data class | `updatedAt: var` 和 `isActive: var` 混用 `val`/`var`，`equals()/hashCode()` 行为可能不符预期。建议将这两个字段移到 `metadata` 或使用单独的状态管理 |
> | `SkillVariable.defaultValue: Any?` | Gson 反序列化会产生 `LinkedTreeMap` 而非实际类型，运行时可能 `ClassCastException`。建议改为 `String?` 或添加 TypeAdapter |

**优先级**: P0 (最高，所有后续Phase的基础)
**预估工期**: 8人天
**前置依赖**: 无
**阶段目标**: 建立完整的共享数据模型体系、基础设施组件和工具类，使项目可编译运行

---

## 1. 阶段概览

本阶段解决的核心问题是：**项目当前无法编译**。`MyToolWindowFactory.kt` 引用了 `logger`、`CefBrowserPanel` 等未实现的类。本阶段将：

1. 创建所有共享数据模型（model包）
2. 实现基础设施组件（infrastructure包）
3. 补全被引用的工具类（util/logger等）
4. 实现JCEF浏览器面板封装（browser包）
5. 实现插件配置持久化（config包）

**完成标志**: `./gradlew build` 编译通过，`./gradlew runIde` 可启动并显示空白ToolWindow

---

## 2. 任务清单

### T1.1 工具类与通用组件 (1人天)

#### T1.1.1 `util/logger.kt` — 日志工具函数

**文件**: `src/main/kotlin/com/github/xingzhewa/ccgui/util/logger.kt`

```kotlin
package com.github.xingzhewa.ccgui.util

import com.intellij.openapi.diagnostic.Logger

/**
 * 内联工具函数，用于获取Logger实例
 * 用法: private val log = logger<MyClass>()
 */
inline fun <reified T> logger(): Logger = Logger.getInstance(T::class.java)
```

**设计要点**:
- 使用 `reified` 泛型避免传 `::class.java`
- 返回 IntelliJ 原生 `Logger`
- 与 `MyToolWindowFactory.kt` 中的 `logger<MyToolWindowFactory>()` 调用兼容

---

#### T1.1.2 `util/JsonUtils.kt` — JSON工具类

**文件**: `src/main/kotlin/com/github/xingzhewa/ccgui/util/JsonUtils.kt`

```kotlin
package com.github.xingzhewa.ccgui.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object JsonUtils {
    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    fun parseObject(json: String): JsonObject =
        JsonParser.parseString(json).asJsonObject

    fun toJson(obj: Any): String = gson.toJson(obj)

    fun <T> fromJson(json: String, clazz: Class<T>): T = gson.fromJson(json, clazz)
}
```

**设计要点**:
- 全局单例Gson实例（线程安全）
- 提供常用JSON操作的快捷方法
- 扩展埋点: 后续可注册自定义 TypeAdapter

---

#### T1.1.3 `util/IdGenerator.kt` — ID生成器

**文件**: `src/main/kotlin/com/github/xingzhewa/ccgui/util/IdGenerator.kt`

```kotlin
package com.github.xingzhewa.ccgui.util

import java.util.UUID

object IdGenerator {
    fun sessionId(): String = "sess_${UUID.randomUUID()}"
    fun messageId(): String = "msg_${UUID.randomUUID()}"
    fun taskId(): String = "task_${UUID.randomUUID()}"
    fun questionId(): String = "q_${UUID.randomUUID()}"
    fun skillId(): String = "skill_${UUID.randomUUID()}"
    fun agentId(): String = "agent_${UUID.randomUUID()}"
    fun mcpId(): String = "mcp_${UUID.randomUUID()}"
}
```

**设计要点**:
- 带前缀的ID，方便日志排查
- 扩展埋点: 后续可替换为雪花算法等分布式ID

---

### T1.2 核心数据模型 (2人天)

#### T1.2.1 `model/message/` — 消息模型

**文件**: `model/message/MessageRole.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.message

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM;

    fun toApiString(): String = name.lowercase()
}
```

**文件**: `model/message/ContentPart.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.message

/**
 * 多模态内容部分
 * 扩展埋点: 后续可添加 Audio, Video 等子类
 */
sealed class ContentPart {
    data class Text(
        val text: String,
        val language: String? = null
    ) : ContentPart()

    data class Image(
        val mimeType: String,   // "image/png", "image/jpeg" 等
        val data: String,       // Base64编码
        val fileName: String? = null
    ) : ContentPart()

    data class File(
        val name: String,
        val content: String,
        val mimeType: String
    ) : ContentPart()
}
```

**文件**: `model/message/ChatMessage.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.message

import com.github.xingzhewa.ccgui.util.IdGenerator
import java.util.UUID

data class ChatMessage(
    val id: String = IdGenerator.messageId(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<ContentPart> = emptyList(),
    val metadata: MutableMap<String, Any> = mutableMapOf()
) {
    /** 添加附件 */
    fun withAttachment(part: ContentPart): ChatMessage =
        copy(attachments = attachments + part)

    /** 添加元数据 */
    fun withMetadata(key: String, value: Any): ChatMessage {
        metadata[key] = value
        return this
    }
}
```

---

#### T1.2.2 `model/session/` — 会话模型

**文件**: `model/session/SessionType.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.session

enum class SessionType {
    PROJECT,     // 绑定项目
    GLOBAL,      // 跨项目
    TEMPORARY    // 临时会话
}
```

**文件**: `model/session/SessionContext.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.session

import com.github.xingzhewa.ccgui.model.config.ModelConfig

data class SessionContext(
    val modelConfig: ModelConfig = ModelConfig.default(),
    val enabledSkills: List<String> = emptyList(),
    val enabledMcpServers: List<String> = emptyList(),
    val mode: ConversationMode = ConversationMode.AUTO,
    val metadata: MutableMap<String, Any> = mutableMapOf()
)
```

**文件**: `model/session/ChatSession.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.session

import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.util.IdGenerator

data class ChatSession(
    val id: String = IdGenerator.sessionId(),
    val name: String = "New Session",
    val type: SessionType = SessionType.PROJECT,
    val projectId: String? = null,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val context: SessionContext = SessionContext(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var isActive: Boolean = false
) {
    fun touch() {
        updatedAt = System.currentTimeMillis()
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        touch()
    }
}
```

---

#### T1.2.3 `model/config/` — 配置模型

**文件**: `model/config/ModelConfig.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.config

data class ModelConfig(
    val provider: String = "anthropic",
    val model: String = "claude-sonnet-4-20250514",
    val apiKey: String = "",
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val topP: Double = 0.9
) {
    companion object {
        fun default() = ModelConfig()
    }
}
```

**文件**: `model/config/ThemeConfig.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.config

data class ThemeConfig(
    val id: String = "jetbrains-dark",
    val name: String = "JetBrains Dark",
    val isDark: Boolean = true,
    val colors: ColorScheme = ColorScheme.defaultDark(),
    val typography: Typography = Typography(),
    val spacing: Spacing = Spacing(),
    val borderRadius: BorderRadius = BorderRadius()
)

data class ColorScheme(
    val primary: String = "#007AFF",
    val background: String = "#1E1E1E",
    val userMessage: String = "#2563EB",
    val aiMessage: String = "#374151",
    val codeBlock: String = "#111827",
    val border: String = "#374151",
    val textPrimary: String = "#F9FAFB",
    val textSecondary: String = "#9CA3AF"
) {
    companion object {
        fun defaultDark() = ColorScheme()
        fun defaultLight() = ColorScheme(
            background = "#FFFFFF",
            userMessage = "#DBEAFE",
            aiMessage = "#F3F4F6",
            codeBlock = "#F8FAFC",
            border = "#E5E7EB",
            textPrimary = "#111827",
            textSecondary = "#6B7280"
        )
    }
}

data class Typography(
    val messageFont: String = "Inter",
    val codeFont: String = "JetBrains Mono",
    val fontSize: Int = 14
)

data class Spacing(
    val messageSpacing: Int = 16,
    val codeBlockPadding: Int = 12,
    val headerHeight: Int = 48
)

data class BorderRadius(
    val messageBubble: Int = 8,
    val codeBlock: Int = 6
)
```

**文件**: `model/config/ConversationMode.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.config

enum class ConversationMode {
    THINKING,   // 深度思考
    PLANNING,   // 先规划后执行
    AUTO        // 快速响应
}
```

**文件**: `model/config/AppConfig.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.config

/**
 * 应用总配置
 * 扩展埋点: 新增配置项只需在此添加字段
 */
data class AppConfig(
    val version: String = "0.0.1",
    val theme: ThemeConfig = ThemeConfig(),
    val model: ModelConfig = ModelConfig.default(),
    val conversationMode: ConversationMode = ConversationMode.AUTO,
    val hotReloadEnabled: Boolean = true,
    val checkpointEnabled: Boolean = true,
    val checkpointInterval: Long = 5000L
)
```

---

#### T1.2.4 `model/interaction/` — 交互模型

**文件**: `model/interaction/QuestionType.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.interaction

enum class QuestionType {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    TEXT_INPUT,
    CONFIRMATION,
    CODE_REVIEW
}
```

**文件**: `model/interaction/InteractiveQuestion.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.interaction

data class InteractiveQuestion(
    val questionId: String,
    val question: String,
    val questionType: QuestionType,
    val options: List<QuestionOption> = emptyList(),
    val allowMultiple: Boolean = false,
    val context: Map<String, Any> = emptyMap()
)

data class QuestionOption(
    val id: String,
    val label: String,
    val description: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)
```

---

#### T1.2.5 `model/task/` — 任务进度模型

**文件**: `model/task/TaskStep.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.task

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}

data class TaskStep(
    val id: String,
    val name: String,
    val description: String = "",
    val status: StepStatus = StepStatus.PENDING,
    val output: String? = null,
    val error: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null
)
```

**文件**: `model/task/TaskProgress.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.task

import com.github.xingzhewa.ccgui.util.IdGenerator

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TaskProgress(
    val taskId: String = IdGenerator.taskId(),
    val totalSteps: Int,
    val currentStep: Int = 0,
    val steps: List<TaskStep>,
    val status: TaskStatus = TaskStatus.PENDING,
    val estimatedTimeRemaining: Long? = null,
    val startTime: Long = System.currentTimeMillis()
)
```

---

#### T1.2.6 `model/provider/` — AI供应商模型

**文件**: `model/provider/AIProvider.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.provider

import kotlinx.coroutines.flow.Flow

/**
 * AI供应商统一接口
 * 扩展埋点: 新增供应商只需实现此接口
 */
interface AIProvider {
    val name: String
    val availableModels: List<String>

    suspend fun chat(request: ChatRequest): ChatResponse
    suspend fun streamChat(request: ChatRequest): Flow<ChatChunk>

    /** 供应商健康检查 */
    suspend fun healthCheck(): Boolean = true
}
```

**文件**: `model/provider/ChatRequest.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.provider

import com.github.xingzhewa.ccgui.model.message.ChatMessage

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val systemPrompt: String? = null,
    val requestId: String? = null
)
```

**文件**: `model/provider/ChatResponse.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.provider

data class ChatResponse(
    val content: String,
    val model: String,
    val tokensUsed: TokenUsage,
    val executionTimeMs: Long,
    val finishReason: String? = null,
    val requestId: String? = null
)

data class ChatChunk(
    val content: String,
    val isComplete: Boolean = false,
    val tokensUsed: TokenUsage? = null
)

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)
```

---

#### T1.2.7 `model/skill/`、`model/agent/`、`model/mcp/` — 生态模型

**文件**: `model/skill/Skill.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.skill

enum class SkillCategory {
    CODE_GENERATION, CODE_REVIEW, REFACTORING,
    TESTING, DOCUMENTATION, DEBUGGING, PERFORMANCE
}

enum class SkillScope { GLOBAL, PROJECT }

data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val icon: String = "⚡",
    val category: SkillCategory = SkillCategory.CODE_GENERATION,
    val prompt: String,
    val variables: List<SkillVariable> = emptyList(),
    val shortcut: String? = null,
    val enabled: Boolean = true,
    val scope: SkillScope = SkillScope.GLOBAL
)
```

**文件**: `model/skill/SkillVariable.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.skill

enum class VariableType { TEXT, NUMBER, ENUM, BOOLEAN, CODE }

data class SkillVariable(
    val name: String,
    val type: VariableType = VariableType.TEXT,
    val defaultValue: Any? = null,
    val required: Boolean = true,
    val options: List<Any>? = null
)
```

**文件**: `model/agent/Agent.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.agent

enum class AgentMode { CAUTIOUS, BALANCED, AGGRESSIVE }
enum class AgentScope { GLOBAL, PROJECT, SESSION }
enum class AgentCapability {
    CODE_GENERATION, CODE_REVIEW, REFACTORING, TESTING,
    DOCUMENTATION, DEBUGGING, FILE_OPERATION, TERMINAL_OPERATION
}

data class Agent(
    val id: String,
    val name: String,
    val description: String,
    val avatar: String = "🤖",
    val systemPrompt: String,
    val capabilities: List<AgentCapability> = emptyList(),
    val constraints: List<AgentConstraint> = emptyList(),
    val tools: List<String> = emptyList(),
    val mode: AgentMode = AgentMode.BALANCED,
    val scope: AgentScope = AgentScope.PROJECT
)
```

**文件**: `model/agent/AgentConstraint.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.agent

enum class ConstraintType {
    MAX_TOKENS, ALLOWED_FILE_TYPES, FORBIDDEN_PATTERNS, RESOURCE_LIMITS
}

data class AgentConstraint(
    val type: ConstraintType,
    val description: String,
    val parameters: Map<String, Any> = emptyMap()
)
```

**文件**: `model/mcp/McpServer.kt`
```kotlin
package com.github.xingzhewa.ccgui.model.mcp

enum class McpServerStatus { CONNECTED, DISCONNECTED, ERROR, CONNECTING }
enum class McpScope { GLOBAL, PROJECT }

data class McpServer(
    val id: String,
    val name: String,
    val description: String,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val status: McpServerStatus = McpServerStatus.DISCONNECTED,
    val capabilities: List<String> = emptyList(),
    val scope: McpScope = McpScope.PROJECT
)
```

---

### T1.3 基础设施组件 (3人天)

#### T1.3.1 `infrastructure/eventbus/` — 事件总线

**文件**: `infrastructure/eventbus/Event.kt`
```kotlin
package com.github.xingzhewa.ccgui.infrastructure.eventbus

/**
 * 事件基类
 * 所有事件继承此类
 */
abstract class Event(
    val type: String,
    val timestamp: Long = System.currentTimeMillis()
)
```

**文件**: `infrastructure/eventbus/Events.kt`
```kotlin
package com.github.xingzhewa.ccgui.infrastructure.eventbus

// ============================================================
// 会话事件
// ============================================================
class SessionChangedEvent(val sessionId: String) : Event("session.changed")
class SessionCreatedEvent(val sessionId: String, val sessionName: String) : Event("session.created")
class SessionDeletedEvent(val sessionId: String) : Event("session.deleted")

// ============================================================
// 消息事件
// ============================================================
class MessageReceivedEvent(val sessionId: String, val messageId: String) : Event("message.received")
class StreamingChunkEvent(val sessionId: String, val messageId: String, val chunk: String) : Event("streaming.chunk")
class StreamingCompleteEvent(val sessionId: String, val messageId: String) : Event("streaming.complete")
class StreamingErrorEvent(val sessionId: String, val error: String) : Event("streaming.error")

// ============================================================
// 配置事件
// ============================================================
class ConfigChangedEvent(val configKey: String, val newValue: Any?) : Event("config.changed")
class ThemeChangedEvent(val themeId: String) : Event("theme.changed")
class ModelChangedEvent(val provider: String, val model: String) : Event("model.changed")

// ============================================================
// 任务事件
// ============================================================
class TaskCreatedEvent(val taskId: String, val totalSteps: Int) : Event("task.created")
class TaskStepUpdatedEvent(val taskId: String, val stepId: String, val status: String) : Event("task.step.updated")
class TaskCompletedEvent(val taskId: String) : Event("task.completed")

// ============================================================
// 交互事件
// ============================================================
class QuestionAskedEvent(val questionId: String, val question: String) : Event("interaction.question")
class QuestionAnsweredEvent(val questionId: String) : Event("interaction.answered")

// ============================================================
// MCP事件
// ============================================================
class McpServerStatusEvent(val serverId: String, val status: String) : Event("mcp.status")

// ============================================================
// 扩展埋点: 新增事件只需在此文件添加新的Event子类
// ============================================================
```

**文件**: `infrastructure/eventbus/EventBus.kt`
```kotlin
package com.github.xingzhewa.ccgui.infrastructure.eventbus

import com.intellij.openapi.Disposable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 轻量级事件总线
 *
 * 使用方法:
 *   // 订阅事件
 *   EventBus.subscribe<SessionChangedEvent>(parentDisposable) { event ->
 *       println("Session changed: ${event.sessionId}")
 *   }
 *
 *   // 发布事件
 *   EventBus.publish(SessionChangedEvent("sess_123"))
 */
object EventBus {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val channels = ConcurrentHashMap<KClass<out Event>, MutableSharedFlow<Event>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Event> getOrCreateChannel(clazz: KClass<T>): MutableSharedFlow<T> {
        return channels.getOrPut(clazz) { MutableSharedFlow() } as MutableSharedFlow<T>
    }

    /**
     * 发布事件
     */
    fun <T : Event> publish(event: T) {
        val clazz = event::class
        @Suppress("UNCHECKED_CAST")
        (channels[clazz] as? MutableSharedFlow<T>)?.tryEmit(event)
    }

    /**
     * 订阅事件，绑定到Disposable生命周期
     */
    fun <T : Event> subscribe(
        eventType: KClass<T>,
        parent: Disposable,
        handler: (T) -> Unit
    ) {
        val flow = getOrCreateChannel(eventType)
        val job = flow.onEach { handler(it) }
            .catch { e -> /* 日志记录但不中断 */ }
            .launchIn(scope)

        // 绑定生命周期
        val disposer = com.intellij.openapi.util.Disposer.newDisposable("EventBusSubscription")
        com.intellij.openapi.util.Disposer.register(parent, disposer)
        com.intellij.openapi.util.Disposer.register(disposer) {
            job.cancel()
        }
    }

    // 便捷扩展函数
    inline fun <reified T : Event> subscribe(
        parent: Disposable,
        noinline handler: (T) -> Unit
    ) = subscribe(T::class, parent, handler)
}

// 需要的import
import kotlin.reflect.KClass
```

---

#### T1.3.2 `infrastructure/storage/` — 存储组件

**文件**: `infrastructure/storage/SecureStorage.kt`
```kotlin
package com.github.xingzhewa.ccgui.infrastructure.storage

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager

/**
 * 安全存储服务
 * 使用 IntelliJ PasswordSafe API 加密存储敏感信息
 *
 * 扩展埋点: 支持按provider存储多个API Key
 */
@Service(Service.Level.APP)
class SecureStorage {

    private val keyMap = mutableMapOf<String, String>()  // 临时方案，后续接入PasswordSafe

    fun saveApiKey(provider: String, key: String) {
        keyMap[provider] = key
    }

    fun getApiKey(provider: String): String? = keyMap[provider]

    fun removeApiKey(provider: String) {
        keyMap.remove(provider)
    }

    fun hasApiKey(provider: String): Boolean = keyMap.containsKey(provider)

    companion object {
        fun getInstance(): SecureStorage =
            ApplicationManager.getApplication().getService(SecureStorage::class.java)
    }
}
```

**文件**: `infrastructure/storage/ConfigStorage.kt`
```kotlin
package com.github.xingzhewa.ccgui.infrastructure.storage

import com.github.xingzhewa.ccgui.model.config.AppConfig
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * 配置持久化组件
 * 使用 IntelliJ PersistentStateComponent 机制
 * 自动持久化到 .idea/ccgui.xml
 */
@Service(Service.Level.PROJECT)
@State(
    name = "com.github.xingzhewa.ccgui.CCGuiConfigStorage",
    storages = [Storage("ccgui.xml")]
)
class ConfigStorage : PersistentStateComponent<ConfigStorage.State> {

    data class State(
        var themeId: String = "jetbrains-dark",
        var provider: String = "anthropic",
        var model: String = "claude-sonnet-4-20250514",
        var maxTokens: Int = 4096,
        var temperature: Double = 0.7,
        var conversationMode: String = "AUTO",
        var hotReloadEnabled: Boolean = true,
        var checkpointEnabled: Boolean = true
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    fun toAppConfig(): AppConfig = AppConfig(
        model = com.github.xingzhewa.ccgui.model.config.ModelConfig(
            provider = state.provider,
            model = state.model,
            maxTokens = state.maxTokens,
            temperature = state.temperature
        ),
        conversationMode = com.github.xingzhewa.ccgui.model.config.ConversationMode.valueOf(state.conversationMode),
        hotReloadEnabled = state.hotReloadEnabled,
        checkpointEnabled = state.checkpointEnabled
    )

    fun fromAppConfig(config: AppConfig) {
        state.provider = config.model.provider
        state.model = config.model.model
        state.maxTokens = config.model.maxTokens
        state.temperature = config.model.temperature
        state.conversationMode = config.conversationMode.name
        state.hotReloadEnabled = config.hotReloadEnabled
    }

    companion object {
        fun getInstance(project: Project): ConfigStorage =
            project.getService(ConfigStorage::class.java)
    }
}
```

**文件**: `infrastructure/storage/SessionStorage.kt`
```kotlin
package com.github.xingzhewa.ccgui.infrastructure.storage

import com.github.xingzhewa.ccgui.model.session.ChatSession
import com.github.xingzhewa.ccgui.util.JsonUtils
import java.io.File

/**
 * 会话持久化
 * 存储到项目目录下的 .idea/ccgui-sessions/ 目录
 *
 * 扩展埋点: 后续可切换为数据库存储
 */
class SessionStorage(private val baseDir: File) {

    private val sessionsDir = File(baseDir, "ccgui-sessions").also { it.mkdirs() }

    fun saveSession(session: ChatSession) {
        val file = File(sessionsDir, "${session.id}.json")
        file.writeText(JsonUtils.toJson(session))
    }

    fun loadSession(sessionId: String): ChatSession? {
        val file = File(sessionsDir, "$sessionId.json")
        if (!file.exists()) return null
        return try {
            JsonUtils.fromJson(file.readText(), ChatSession::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun loadAllSessions(): List<ChatSession> {
        return sessionsDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { loadSession(it.nameWithoutExtension) }
            ?: emptyList()
    }

    fun deleteSession(sessionId: String) {
        File(sessionsDir, "$sessionId.json").delete()
    }
}
```

---

#### T1.3.3 `infrastructure/state/` — 状态管理

**文件**: `infrastructure/state/StateManager.kt`
```kotlin
package com.github.xingzhewa.ccgui.infrastructure.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通用状态容器
 * 提供线程安全的状态读写和变更通知
 */
class StateManager<T>(initialValue: T) {
    private val _state = MutableStateFlow(initialValue)
    val state: StateFlow<T> = _state.asStateFlow()

    val value: T get() = _state.value

    fun update(transform: (T) -> T) {
        _state.value = transform(_state.value)
    }

    fun set(value: T) {
        _state.value = value
    }
}
```

**文件**: `infrastructure/state/DisposableHelper.kt`
```kotlin
package com.github.xingzhewa.ccgui.infrastructure.state

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 创建绑定到Disposable生命周期的CoroutineScope
 */
fun createDisposableScope(parent: Disposable): CoroutineScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    Disposer.register(parent) { scope.cancel() }
    return scope
}
```

---

#### T1.3.4 `infrastructure/error/` — 异常体系

**文件**: `infrastructure/error/PluginExceptions.kt`
```kotlin
package com.github.xingzhewa.ccgui.infrastructure.error

/**
 * 插件异常体系
 * 扩展埋点: 按需添加新的异常子类
 */
sealed class PluginException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ConfigurationError(message: String) : PluginException(message)
    class NetworkError(message: String, cause: Throwable) : PluginException(message, cause)
    class ValidationError(message: String) : PluginException(message)
    class ProviderNotFound(provider: String) : PluginException("Provider not found: $provider")
    class ModelNotAvailable(model: String, provider: String) : PluginException("Model $model not available for $provider")
    class SessionNotFound(sessionId: String) : PluginException("Session not found: $sessionId")
    class BridgeError(message: String, cause: Throwable? = null) : PluginException(message, cause)
    class McpServerError(message: String, cause: Throwable? = null) : PluginException(message, cause)
    class SkillError(message: String, cause: Throwable? = null) : PluginException(message, cause)
    class AgentError(message: String, cause: Throwable? = null) : PluginException(message, cause)
}
```

**文件**: `infrastructure/error/ErrorRecoveryManager.kt`
```kotlin
package com.github.xingzhewa.ccgui.infrastructure.error

import com.github.xingzhewa.ccgui.util.logger
import kotlinx.coroutines.delay

/**
 * 错误恢复管理器
 * 提供重试策略和降级处理
 */
class ErrorRecoveryManager {

    private val log = logger<ErrorRecoveryManager>()

    /**
     * 带重试的执行
     * @param maxRetries 最大重试次数
     * @param initialDelayMs 初始重试延迟(ms)
     * @param factor 延迟倍增因子
     */
    suspend fun <T> withRetry(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): Result<T> {
        var currentDelay = initialDelayMs
        repeat(maxRetries) { attempt ->
            try {
                return Result.success(block())
            } catch (e: Exception) {
                log.warn("Attempt ${attempt + 1}/$maxRetries failed: ${e.message}")
                if (attempt < maxRetries - 1) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong()
                } else {
                    return Result.failure(e)
                }
            }
        }
        return Result.failure(IllegalStateException("Should not reach here"))
    }
}
```

---

#### T1.3.5 `infrastructure/cache/` — 缓存管理

**文件**: `infrastructure/cache/CacheManager.kt`
```kotlin
package com.github.xingzhewa.ccgui.infrastructure.cache

import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * LRU缓存管理器
 * 扩展埋点: 后续可替换为Caffeine等成熟方案
 */
class CacheManager<K, V>(private val maxSize: Int = 100) {

    private val cache = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun get(key: K): V? = cache[key]

    @Synchronized
    fun put(key: K, value: V) { cache[key] = value }

    @Synchronized
    fun remove(key: K) { cache.remove(key) }

    @Synchronized
    fun clear() { cache.clear() }

    @Synchronized
    fun size(): Int = cache.size
}
```

---

### T1.4 浏览器封装 (1人天)

#### T1.4.1 `browser/CefBrowserPanel.kt` — JCEF面板

**文件**: `browser/CefBrowserPanel.kt`

这是 `MyToolWindowFactory.kt` 的核心依赖，必须与已有调用方式兼容：

```kotlin
package com.github.xingzhewa.ccgui.browser

import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.JPanel

/**
 * JCEF浏览器面板封装
 *
 * 提供两个核心能力:
 * 1. loadHtmlPage(html: String) — 加载HTML内容
 * 2. sendToJavaScript(event: String, data: Any) — 向JS发送事件
 *
 * 构造参数messageHandler: (event: String, data: String?) -> JBCefJSQuery.Response?
 *   用于接收JS端发来的消息
 */
class CefBrowserPanel(
    private val project: Project,
    private val messageHandler: (String, String?) -> JBCefJSQuery.Response?
) : JPanel(BorderLayout()), Disposable {

    private val log = logger<CefBrowserPanel>()
    private val browser: JBCefBrowser = JBCefBrowserBuilder().build()
    private val jsQuery: JBCefJSQuery = JBCefJSQuery.create(browser)

    init {
        // 添加浏览器组件到面板
        add(browser.component, BorderLayout.CENTER)

        // 注册JS → Java通信端点
        setupJsQuery()

        // 注入JS端通信对象
        injectBridge()
    }

    /**
     * 加载HTML页面
     */
    fun loadHtmlPage(html: String) {
        browser.loadHTML(html)
    }

    /**
     * 向JavaScript发送事件
     */
    fun sendToJavaScript(event: String, data: Any) {
        try {
            val json = JsonUtils.toJson(mapOf("event" to event, "data" to data))
            browser.cefBrowser.executeJavaScript(
                "if(window.ccEvents && window.ccEvents.emit) { window.ccEvents.emit($json); }",
                null, 0
            )
        } catch (e: Exception) {
            log.error("Failed to send to JavaScript: $event", e)
        }
    }

    private fun setupJsQuery() {
        jsQuery.addHandler { request ->
            try {
                val json = JsonUtils.parseObject(request)
                val event = json.get("event")?.asString ?: return@addHandler null
                val data = json.get("data")?.toString
                messageHandler(event, data)
            } catch (e: Exception) {
                log.error("Failed to handle JS query", e)
                null
            }
        }
    }

    private fun injectBridge() {
        browser.cefBrowser.executeJavaScript(
            """
            window.ccBackend = window.ccBackend || {};
            window.ccEvents = {
                _listeners: {},
                on: function(event, callback) {
                    if (!this._listeners[event]) this._listeners[event] = [];
                    this._listeners[event].push(callback);
                },
                emit: function(eventOrObj, data) {
                    var eventName, eventData;
                    if (typeof eventOrObj === 'object') {
                        eventName = eventOrObj.event;
                        eventData = eventOrObj.data;
                    } else {
                        eventName = eventOrObj;
                        eventData = data;
                    }
                    var listeners = this._listeners[eventName] || [];
                    listeners.forEach(function(cb) { cb(eventData); });
                }
            };
            window.ccBackend.send = function(event, data) {
                return ${jsQuery.inject("JSON.stringify({event: event, data: data})")};
            };
            """.trimIndent(),
            null, 0
        )
    }

    override fun dispose() {
        jsQuery.dispose()
        browser.dispose()
    }
}
```

---

### T1.5 插件配置 (0.5人天)

#### T1.5.1 `config/CCGuiConfig.kt` — 插件配置服务

**文件**: `config/CCGuiConfig.kt`

此文件在 `plugin.xml` 中被引用，必须存在：

```kotlin
package com.github.xingzhewa.ccgui.config

import com.github.xingzhewa.ccgui.infrastructure.storage.ConfigStorage
import com.github.xingzhewa.ccgui.model.config.AppConfig
import com.github.xingzhewa.ccgui.model.config.ModelConfig
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * CCGUI插件配置服务
 * 对外提供统一的配置读写接口
 */
@Service(Service.Level.PROJECT)
class CCGuiConfig(private val project: Project) {

    private val storage get() = ConfigStorage.getInstance(project)

    /** 获取当前配置 */
    fun getConfig(): AppConfig = storage.toAppConfig()

    /** 更新配置 */
    fun updateConfig(config: AppConfig) {
        storage.fromAppConfig(config)
    }

    /** 获取模型配置 */
    fun getModelConfig(): ModelConfig = storage.toAppConfig().model

    companion object {
        fun getInstance(project: Project): CCGuiConfig =
            project.getService(CCGuiConfig::class.java)
    }
}
```

---

### T1.6 plugin.xml 更新

需要在 `plugin.xml` 中注册新增的Service：

```xml
<!-- 新增注册 -->
<applicationService serviceImplementation="com.github.xingzhewa.ccgui.infrastructure.storage.SecureStorage"/>
<projectService serviceImplementation="com.github.xingzhewa.ccgui.infrastructure.storage.ConfigStorage"/>
<projectService serviceImplementation="com.github.xingzhewa.ccgui.config.CCGuiConfig"/>
```

---

## 3. 任务依赖与执行顺序

```
T1.1 工具类 (logger, JsonUtils, IdGenerator)
  ↓
T1.2 数据模型 (message → session → config → interaction → task → provider → skill/agent/mcp)
  ↓
T1.3 基础设施 (EventBus → Exceptions → StateManager → Storage → CacheManager)
  ↓ (可并行)
T1.4 浏览器封装 (CefBrowserPanel) ← 依赖 T1.1 logger + JsonUtils
  ↓
T1.5 插件配置 (CCGuiConfig) ← 依赖 T1.3 ConfigStorage + T1.2 AppConfig
```

---

## 4. 验收标准

| 验收项 | 标准 |
|--------|------|
| 编译 | `./gradlew build` 零错误 |
| 启动 | `./gradlew runIde` 可启动IDE |
| ToolWindow | 可打开ToolWindow，显示空白/错误页面（无crash） |
| 数据模型 | 所有model类可正常序列化/反序列化 |
| EventBus | publish/subscribe 可正常收发事件 |
| 存储服务 | ConfigStorage 可持久化和恢复配置 |
| CefBrowserPanel | 可加载HTML，双向通信正常 |

---

## 5. 文件清单汇总

| 序号 | 文件路径 | 类型 |
|------|----------|------|
| 1 | `util/logger.kt` | 工具 |
| 2 | `util/JsonUtils.kt` | 工具 |
| 3 | `util/IdGenerator.kt` | 工具 |
| 4 | `model/message/MessageRole.kt` | 枚举 |
| 5 | `model/message/ContentPart.kt` | 密封类 |
| 6 | `model/message/ChatMessage.kt` | 数据类 |
| 7 | `model/session/SessionType.kt` | 枚举 |
| 8 | `model/session/SessionContext.kt` | 数据类 |
| 9 | `model/session/ChatSession.kt` | 数据类 |
| 10 | `model/config/ModelConfig.kt` | 数据类 |
| 11 | `model/config/ThemeConfig.kt` | 数据类 |
| 12 | `model/config/ConversationMode.kt` | 枚举 |
| 13 | `model/config/AppConfig.kt` | 数据类 |
| 14 | `model/interaction/QuestionType.kt` | 枚举 |
| 15 | `model/interaction/InteractiveQuestion.kt` | 数据类 |
| 16 | `model/task/TaskStep.kt` | 数据+枚举 |
| 17 | `model/task/TaskProgress.kt` | 数据+枚举 |
| 18 | `model/provider/AIProvider.kt` | 接口 |
| 19 | `model/provider/ChatRequest.kt` | 数据类 |
| 20 | `model/provider/ChatResponse.kt` | 数据类 |
| 21 | `model/skill/Skill.kt` | 数据+枚举 |
| 22 | `model/skill/SkillVariable.kt` | 数据+枚举 |
| 23 | `model/agent/Agent.kt` | 数据+枚举 |
| 24 | `model/agent/AgentConstraint.kt` | 数据+枚举 |
| 25 | `model/mcp/McpServer.kt` | 数据+枚举 |
| 26 | `infrastructure/eventbus/Event.kt` | 抽象类 |
| 27 | `infrastructure/eventbus/Events.kt` | 事件定义 |
| 28 | `infrastructure/eventbus/EventBus.kt` | 单例 |
| 29 | `infrastructure/storage/SecureStorage.kt` | 服务 |
| 30 | `infrastructure/storage/ConfigStorage.kt` | 持久化 |
| 31 | `infrastructure/storage/SessionStorage.kt` | 文件存储 |
| 32 | `infrastructure/state/StateManager.kt` | 泛型类 |
| 33 | `infrastructure/state/DisposableHelper.kt` | 工具函数 |
| 34 | `infrastructure/error/PluginExceptions.kt` | 异常体系 |
| 35 | `infrastructure/error/ErrorRecoveryManager.kt` | 错误恢复 |
| 36 | `infrastructure/cache/CacheManager.kt` | 缓存 |
| 37 | `browser/CefBrowserPanel.kt` | UI组件 |
| 38 | `config/CCGuiConfig.kt` | 服务 |

**共计**: 38个文件
