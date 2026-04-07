# Phase 2: 通信适配层 (Adaptation Layer)

**优先级**: P0
**预估工期**: 10人天
**前置依赖**: Phase 1 (所有model和infrastructure组件)
**阶段目标**: 实现CLI通信桥接、流式回调体系、消息解析、JCEF双向通信桥，使ToolWindow可正常通信

> **⚠️ 重要修订说明 (2026-04-08)**
>
> 本阶段中 **T2.5 多供应商适配** (AnthropicProvider/OpenAIProvider/DeepSeekProvider/MultiProviderAdapter)
> 的设计存在严重缺陷：**不应由插件直接调用各AI供应商的REST API**，而应通过 **Claude Code SDK (CLI子进程模式)** 统一管理。
>
> 正确方案已在 **[Phase 2.5: Claude Code SDK 集成](02-phase2.5-claude-code-sdk.md)** 中详细设计，具体变更：
>
> | 本阶段组件 | 处理方式 | 替代方案 |
> |-----------|---------|---------|
> | `AnthropicProvider.kt` | **废弃** | SDK统一管理API调用 |
> | `OpenAIProvider.kt` | **废弃** | SDK通过环境变量支持多后端 |
> | `DeepSeekProvider.kt` | **废弃** | 同上 |
> | `MultiProviderAdapter.kt` | **重构** | `ModelInfoRegistry` 仅提供模型信息查询 |
> | `StdioBridge.kt` | **重构协议** | 使用SDK标准 stream-json 协议 |
> | `BridgeManager.kt` | **重构** | 委托给 `ClaudeCodeClient` |
>
> **保留不变的组件**: StreamCallback体系、ProcessPool、MessageParser、VersionDetector

---

## 1. 阶段概览

本阶段是**最关键的通信层**，解决"前后端如何通信"的问题：

1. 实现BridgeManager和StdioBridge（CLI进程通信）
2. 实现StreamCallback体系（流式输出回调）
3. 实现MessageParser和StreamingResponseParser（消息协议解析）
4. 实现ProcessPool（多进程管理）
5. 实现VersionDetector（版本兼容性检测）
6. 实现MultiProviderAdapter和各AI供应商Provider

**完成标志**: 可通过UI发送消息，经BridgeManager到CLI，接收流式回复并显示

---

## 2. 任务清单

### T2.1 流式回调体系 (1.5人天)

#### T2.1.1 `adaptation/bridge/StreamCallback.kt`

**文件**: `src/main/kotlin/.../adaptation/bridge/StreamCallback.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.bridge

/**
 * 流式输出回调接口
 * 用于接收CLI进程的流式输出
 */
interface StreamCallback {
    /** 收到一行输出 */
    fun onLineReceived(line: String) {}

    /** 流式输出完成 */
    fun onStreamComplete(allLines: List<String>) {}

    /** 流式输出错误 */
    fun onStreamError(error: String) {}

    /** 收到进度信息 */
    fun onProgress(progress: Float) {}
}
```

#### T2.1.2 `adaptation/bridge/SimpleStreamCallback.kt`

**文件**: `src/main/kotlin/.../adaptation/bridge/SimpleStreamCallback.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.bridge

/**
 * StreamCallback的基类实现
 * 自动收集所有行，子类只需覆写关心的方法
 */
abstract class SimpleStreamCallback : StreamCallback {

    private val lines = mutableListOf<String>()

    override fun onLineReceived(line: String) {
        lines.add(line)
    }

    override fun onStreamComplete(allLines: List<String>) {
        // 默认空实现，子类可覆写
    }

    override fun onStreamError(error: String) {
        // 默认空实现，子类可覆写
    }

    fun getCollectedLines(): List<String> = lines.toList()
}
```

---

### T2.2 进程通信桥接 (4人天)

#### T2.2.1 `adaptation/bridge/StdioBridge.kt` — 核心通信

**文件**: `src/main/kotlin/.../adaptation/bridge/StdioBridge.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.bridge

import com.github.xingzhewa.ccgui.util.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stdio通信桥接
 * 负责与Claude Code CLI进程的stdin/stdout通信
 *
 * 通信协议:
 *   写入: JSON格式消息到stdin
 *   读取: 逐行从stdout读取，支持流式
 *
 * 扩展埋点:
 *   - 后续可切换为WebSocket通信
 *   - 后续可添加消息签名验证
 */
class StdioBridge(private val process: Process) {

    private val log = logger<StdioBridge>()
    private val writer = PrintWriter(process.outputStream, true)
    private val reader = BufferedReader(InputStreamReader(process.inputStream))
    private val errorReader = BufferedReader(InputStreamReader(process.errorStream))
    private val isRunning = AtomicBoolean(true)

    private val outputChannel = Channel<String>(Channel.UNLIMITED)
    private val errorChannel = Channel<String>(Channel.UNLIMITED)

    private val readScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        startReading()
    }

    /**
     * 发送消息到CLI进程
     */
    fun sendMessage(message: String) {
        if (!isRunning.get()) {
            log.warn("Bridge is not running, cannot send message")
            return
        }
        writer.println(message)
        log.info("Sent message to CLI: ${message.take(100)}...")
    }

    /**
     * 获取输出流（响应数据）
     */
    fun outputFlow(): Flow<String> = outputChannel.receiveAsFlow()

    /**
     * 获取错误流
     */
    fun errorFlow(): Flow<String> = errorChannel.receiveAsFlow()

    /**
     * 带回调的流式读取
     */
    suspend fun readWithCallback(callback: StreamCallback) {
        val lines = mutableListOf<String>()
        try {
            for (line in outputChannel) {
                lines.add(line)
                callback.onLineReceived(line)
            }
        } catch (e: CancellationException) {
            callback.onStreamComplete(lines)
        } catch (e: Exception) {
            callback.onStreamError(e.message ?: "Unknown error")
        }
    }

    /**
     * 关闭桥接
     */
    fun close() {
        if (isRunning.compareAndSet(true, false)) {
            readScope.cancel()
            writer.close()
            reader.close()
            errorReader.close()
            outputChannel.close()
            errorChannel.close()
        }
    }

    val isActive: Boolean get() = isRunning.get() && process.isAlive

    private fun startReading() {
        // stdout读取
        readScope.launch {
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    outputChannel.send(line!!)
                }
            } catch (e: Exception) {
                if (isRunning.get()) log.error("stdout read error", e)
            } finally {
                outputChannel.close()
            }
        }

        // stderr读取
        readScope.launch {
            try {
                var line: String?
                while (errorReader.readLine().also { line = it } != null) {
                    errorChannel.send(line!!)
                    log.warn("CLI stderr: $line")
                }
            } catch (e: Exception) {
                if (isRunning.get()) log.error("stderr read error", e)
            } finally {
                errorChannel.close()
            }
        }
    }
}
```

---

#### T2.2.2 `adaptation/bridge/ProcessPool.kt` — 进程池

**文件**: `src/main/kotlin/.../adaptation/bridge/ProcessPool.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.bridge

import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * CLI进程池
 * 管理多个Claude Code CLI进程实例
 *
 * 扩展埋点: 后续可支持进程预热、健康检查、自动重启
 */
class ProcessPool(
    private val maxPoolSize: Int = 5,
    private val cliCommand: String = "claude"
) : Disposable {

    private val log = logger<ProcessPool>()
    private val bridges = ConcurrentHashMap<String, StdioBridge>()
    private val activeCount = AtomicInteger(0)

    /**
     * 创建一个新的CLI进程并返回通信桥
     */
    fun createBridge(id: String): Result<StdioBridge> {
        if (activeCount.get() >= maxPoolSize) {
            return Result.failure(IllegalStateException("Process pool exhausted (max: $maxPoolSize)"))
        }

        return try {
            val process = ProcessBuilder(cliCommand)
                .redirectErrorStream(false)
                .start()

            val bridge = StdioBridge(process)
            bridges[id] = bridge
            activeCount.incrementAndGet()

            log.info("Created CLI bridge: $id, active: ${activeCount.get()}")
            Result.success(bridge)
        } catch (e: Exception) {
            log.error("Failed to create CLI bridge: $id", e)
            Result.failure(e)
        }
    }

    /**
     * 获取已有的通信桥
     */
    fun getBridge(id: String): StdioBridge? = bridges[id]

    /**
     * 销毁指定进程
     */
    fun destroyBridge(id: String) {
        bridges.remove(id)?.let { bridge ->
            bridge.close()
            activeCount.decrementAndGet()
            log.info("Destroyed CLI bridge: $id, active: ${activeCount.get()}")
        }
    }

    /**
     * 获取活跃进程数
     */
    fun activeProcessCount(): Int = activeCount.get()

    override fun dispose() {
        bridges.keys.toList().forEach { destroyBridge(it) }
        log.info("ProcessPool disposed, all bridges closed")
    }
}
```

---

#### T2.2.3 `adaptation/bridge/BridgeManager.kt` — 桥接管理器

**文件**: `src/main/kotlin/.../adaptation/bridge/BridgeManager.kt`

**关键**: 此文件在 `plugin.xml` 中已注册为ProjectService，必须与 `MyToolWindowFactory` 中的调用方式兼容。

```kotlin
package com.github.xingzhewa.ccgui.adaptation.bridge

import com.github.xingzhewa.ccgui.adaptation.parser.MessageParser
import com.github.xingzhewa.ccgui.infrastructure.error.PluginException
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * CLI桥接管理器
 * 项目级服务，管理CLI进程的创建、消息收发、生命周期
 *
 * 对外接口（与MyToolWindowFactory兼容）:
 *   - sendMessage(message: String, sessionId: String, callback: StreamCallback): Result<Unit>
 */
@Service(Service.Level.PROJECT)
class BridgeManager(private val project: Project) {

    private val log = logger<BridgeManager>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processPool = ProcessPool()
    private val messageParser = MessageParser()

    /** 当前活跃的bridge映射: sessionId -> StdioBridge */
    private val activeBridges = mutableMapOf<String, StdioBridge>()

    /** 连接状态 */
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    /**
     * 发送消息到CLI
     *
     * 兼容 MyToolWindowFactory.sendMessageToBridge() 的调用方式:
     *   val result = bridgeManager.sendMessage(message, sessionId, callback)
     */
    fun sendMessage(
        message: String,
        sessionId: String,
        callback: StreamCallback
    ): Result<Unit> {
        return try {
            val bridge = getOrCreateBridge(sessionId)

            scope.launch {
                try {
                    // 1. 构建CLI消息格式
                    val cliMessage = messageParser.buildCliMessage(message, sessionId)
                    bridge.sendMessage(cliMessage)

                    // 2. 读取流式响应
                    bridge.readWithCallback(callback)
                } catch (e: CancellationException) {
                    callback.onStreamComplete(emptyList())
                } catch (e: Exception) {
                    log.error("Bridge send failed", e)
                    callback.onStreamError(e.message ?: "Unknown error")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            log.error("Failed to send message", e)
            Result.failure(PluginException.BridgeError("Send failed: ${e.message}", e))
        }
    }

    /**
     * 取消流式输出
     */
    fun cancelStreaming(sessionId: String) {
        activeBridges[sessionId]?.close()
        activeBridges.remove(sessionId)
    }

    private fun getOrCreateBridge(sessionId: String): StdioBridge {
        return activeBridges.getOrPut(sessionId) {
            val result = processPool.createBridge(sessionId)
            if (result.isFailure) {
                throw PluginException.BridgeError("Failed to create bridge: ${result.exceptionOrNull()?.message}")
            }
            _connectionState.value = ConnectionState.CONNECTED
            result.getOrThrow()
        }
    }

    fun dispose() {
        scope.cancel()
        activeBridges.keys.toList().forEach { cancelStreaming(it) }
        processPool.dispose()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    companion object {
        fun getInstance(project: Project): BridgeManager =
            project.getService(BridgeManager::class.java)
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
```

---

### T2.3 消息解析器 (2人天)

#### T2.3.1 `adaptation/parser/MessageParser.kt`

**文件**: `src/main/kotlin/.../adaptation/parser/MessageParser.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.parser

import com.github.xingzhewa.ccgui.model.message.ChatMessage
import com.github.xingzhewa.ccgui.model.message.MessageRole
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.google.gson.JsonObject

/**
 * CLI消息解析器
 * 负责将用户消息构建为CLI格式，以及将CLI响应解析为结构化数据
 *
 * 扩展埋点:
 *   - 后续可支持多种CLI消息格式版本
 *   - 后续可添加自定义消息转换器
 */
class MessageParser {

    /**
     * 构建CLI消息格式
     * 将用户输入包装为CLI可识别的JSON格式
     */
    fun buildCliMessage(userMessage: String, sessionId: String): String {
        val json = JsonObject().apply {
            addProperty("type", "user_message")
            addProperty("session_id", sessionId)
            addProperty("content", userMessage)
            addProperty("timestamp", System.currentTimeMillis())
        }
        return json.toString()
    }

    /**
     * 解析CLI输出行
     * 返回解析后的消息，如果无法解析则返回null
     */
    fun parseLine(line: String): ParsedLine? {
        if (line.isBlank()) return null

        return try {
            val json = JsonUtils.parseObject(line)
            val type = json.get("type")?.asString

            when (type) {
                "content_delta" -> ParsedLine.ContentDelta(
                    content = json.get("content")?.asString ?: "",
                    sessionId = json.get("session_id")?.asString
                )
                "content_complete" -> ParsedLine.ContentComplete(
                    content = json.get("content")?.asString ?: "",
                    tokensUsed = json.get("tokens_used")?.asInt ?: 0
                )
                "error" -> ParsedLine.Error(
                    message = json.get("message")?.asString ?: "Unknown error",
                    code = json.get("code")?.asString
                )
                "progress" -> ParsedLine.Progress(
                    step = json.get("step")?.asInt ?: 0,
                    total = json.get("total")?.asInt ?: 0,
                    description = json.get("description")?.asString ?: ""
                )
                "question" -> ParsedLine.Question(
                    questionId = json.get("question_id")?.asString ?: "",
                    question = json.get("question")?.asString ?: "",
                    options = parseOptions(json)
                )
                else -> ParsedLine.RawText(line)
            }
        } catch (e: Exception) {
            // 非JSON格式，作为纯文本处理
            ParsedLine.RawText(line)
        }
    }

    private fun parseOptions(json: JsonObject): List<Pair<String, String>> {
        val optionsArray = json.getAsJsonArray("options") ?: return emptyList()
        return optionsArray.mapNotNull { element ->
            val obj = element.asJsonObject
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            val label = obj.get("label")?.asString ?: id
            id to label
        }
    }

    /**
     * 解析后的行类型
     */
    sealed class ParsedLine {
        data class ContentDelta(val content: String, val sessionId: String?) : ParsedLine()
        data class ContentComplete(val content: String, val tokensUsed: Int) : ParsedLine()
        data class Error(val message: String, val code: String?) : ParsedLine()
        data class Progress(val step: Int, val total: Int, val description: String) : ParsedLine()
        data class Question(val questionId: String, val question: String, val options: List<Pair<String, String>>) : ParsedLine()
        data class RawText(val text: String) : ParsedLine()
    }
}
```

---

#### T2.3.2 `adaptation/parser/StreamingResponseParser.kt`

**文件**: `src/main/kotlin/.../adaptation/parser/StreamingResponseParser.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.parser

import com.github.xingzhewa.ccgui.util.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform

/**
 * 流式响应解析器
 * 将CLI的逐行输出解析为结构化的流式事件
 *
 * 性能要求: 解析延迟 < 10ms/行
 */
class StreamingResponseParser {

    private val log = logger<StreamingResponseParser>()
    private val messageParser = MessageParser()

    /**
     * 将原始文本行流转换为解析后的流式事件
     */
    fun parse(lines: Flow<String>): Flow<MessageParser.ParsedLine> {
        return lines.transform { line ->
            val parsed = messageParser.parseLine(line)
            if (parsed != null) {
                emit(parsed)
            }
        }
    }

    /**
     * 将原始文本行流提取为纯文本内容（用于简单的流式显示）
     */
    fun extractContent(lines: Flow<String>): Flow<String> {
        return lines.transform { line ->
            when (val parsed = messageParser.parseLine(line)) {
                is MessageParser.ParsedLine.ContentDelta -> emit(parsed.content)
                is MessageParser.ParsedLine.RawText -> emit(parsed.text + "\n")
                else -> { /* 忽略其他类型 */ }
            }
        }
    }
}
```

---

### T2.4 版本检测 (1人天)

#### T2.4.1 `adaptation/version/VersionDetector.kt`

**文件**: `src/main/kotlin/.../adaptation/version/VersionDetector.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.version

import com.github.xingzhewa.ccgui.util.logger

/**
 * Claude Code CLI版本检测器
 * 检测CLI是否安装、版本是否兼容
 *
 * 扩展埋点: 后续可检测多个CLI版本和路径
 */
class VersionDetector {

    private val log = logger<VersionDetector>()

    data class VersionInfo(
        val raw: String,
        val major: Int,
        val minor: Int,
        val patch: Int,
        val isCompatible: Boolean
    )

    /**
     * 检测CLI版本
     */
    fun detectVersion(): VersionInfo? {
        return try {
            val process = ProcessBuilder("claude", "--version")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            val versionRegex = Regex("""(\d+)\.(\d+)\.(\d+)""")
            val match = versionRegex.find(output) ?: return null

            val major = match.groupValues[1].toInt()
            val minor = match.groupValues[2].toInt()
            val patch = match.groupValues[3].toInt()

            VersionInfo(
                raw = output,
                major = major,
                minor = minor,
                patch = patch,
                isCompatible = isCompatible(major, minor, patch)
            )
        } catch (e: Exception) {
            log.warn("Failed to detect CLI version: ${e.message}")
            null
        }
    }

    /**
     * 检查CLI是否已安装
     */
    fun isCliInstalled(): Boolean {
        return try {
            val process = ProcessBuilder("claude", "--version")
                .redirectErrorStream(true)
                .start()
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun isCompatible(major: Int, minor: Int, patch: Int): Boolean {
        // 兼容性策略: 最低支持 1.0.0
        return major >= 1
    }

    companion object {
        /** 最低兼容版本 */
        const val MIN_VERSION = "1.0.0"
    }
}
```

---

### T2.5 多供应商适配 (3人天)

#### T2.5.1 `adaptation/provider/MultiProviderAdapter.kt`

**文件**: `src/main/kotlin/.../adaptation/provider/MultiProviderAdapter.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.provider

import com.github.xingzhewa.ccgui.infrastructure.error.PluginException
import com.github.xingzhewa.ccgui.model.provider.AIProvider
import com.github.xingzhewa.ccgui.model.provider.ChatRequest
import com.github.xingzhewa.ccgui.model.provider.ChatResponse
import com.intellij.openapi.components.Service
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

/**
 * 多AI供应商适配器
 * 统一管理所有AI供应商，提供透明的供应商切换能力
 *
 * 扩展埋点:
 *   - 注册新供应商只需实现AIProvider接口并调用registerProvider
 *   - 后续可添加负载均衡、故障转移策略
 */
@Service(Service.Level.APP)
class MultiProviderAdapter {

    private val providers = ConcurrentHashMap<String, AIProvider>()

    fun registerProvider(provider: AIProvider) {
        providers[provider.name] = provider
    }

    fun unregisterProvider(name: String) {
        providers.remove(name)
    }

    fun getProvider(name: String): AIProvider =
        providers[name] ?: throw PluginException.ProviderNotFound(name)

    fun getAvailableProviders(): List<String> = providers.keys.toList()

    fun getAvailableModels(providerName: String): List<String> =
        getProvider(providerName).availableModels

    suspend fun chat(providerName: String, request: ChatRequest): ChatResponse =
        getProvider(providerName).chat(request)

    suspend fun streamChat(providerName: String, request: ChatRequest): Flow<com.github.xingzhewa.ccgui.model.provider.ChatChunk> =
        getProvider(providerName).streamChat(request)

    fun isProviderAvailable(name: String): Boolean = providers.containsKey(name)

    companion object {
        fun getInstance(): MultiProviderAdapter =
            ApplicationManager.getApplication().getService(MultiProviderAdapter::class.java)
    }
}
```

---

#### T2.5.2 `adaptation/provider/AnthropicProvider.kt`

**文件**: `src/main/kotlin/.../adaptation/provider/AnthropicProvider.kt`

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
 * Anthropic Claude API供应商实现
 *
 * API文档: https://docs.anthropic.com/en/docs/about-claude/models
 *
 * 扩展埋点: 后续可切换为Ktor HttpClient + SSE原生支持
 */
class AnthropicProvider : AIProvider {

    override val name = "anthropic"
    override val availableModels = listOf(
        "claude-sonnet-4-20250514",
        "claude-opus-4-20250514",
        "claude-haiku-4-20250514"
    )

    private val log = logger<AnthropicProvider>()
    private val baseUrl = "https://api.anthropic.com/v1/messages"
    private val storage get() = SecureStorage.getInstance()

    override suspend fun chat(request: ChatRequest): ChatResponse {
        val startTime = System.currentTimeMillis()
        val apiKey = storage.getApiKey(name)
            ?: throw IllegalStateException("Anthropic API key not configured")

        val connection = URL(baseUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("x-api-key", apiKey)
        connection.setRequestProperty("anthropic-version", "2023-06-01")
        connection.doOutput = true

        val body = buildRequestBody(request)
        connection.outputStream.write(body.toByteArray())

        val responseCode = connection.responseCode
        if (responseCode != 200) {
            val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw RuntimeException("Anthropic API error ($responseCode): $error")
        }

        val response = connection.inputStream.bufferedReader().readText()
        val json = JsonUtils.parseObject(response)

        val content = json.getAsJsonArray("content")
            ?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: ""

        val usage = json.getAsJsonObject("usage")
        val tokensUsed = TokenUsage(
            promptTokens = usage?.get("input_tokens")?.asInt ?: 0,
            completionTokens = usage?.get("output_tokens")?.asInt ?: 0
        )

        return ChatResponse(
            content = content,
            model = request.model,
            tokensUsed = TokenUsage(tokensUsed.promptTokens, tokensUsed.completionTokens,
                tokensUsed.promptTokens + tokensUsed.completionTokens),
            executionTimeMs = System.currentTimeMillis() - startTime,
            requestId = json.get("id")?.asString
        )
    }

    override suspend fun streamChat(request: ChatRequest): Flow<ChatChunk> = flow {
        val apiKey = storage.getApiKey(name)
            ?: throw IllegalStateException("Anthropic API key not configured")

        val connection = URL(baseUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("x-api-key", apiKey)
        connection.setRequestProperty("anthropic-version", "2023-06-01")
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
                val type = eventJson.get("type")?.asString
                when (type) {
                    "content_block_delta" -> {
                        val text = eventJson.getAsJsonObject("delta")?.get("text")?.asString ?: ""
                        emit(ChatChunk(content = text))
                    }
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

            request.systemPrompt?.let { addProperty("system", it) }
        }
        return json.toString()
    }
}
```

---

#### T2.5.3 `adaptation/provider/OpenAIProvider.kt`

**文件**: `src/main/kotlin/.../adaptation/provider/OpenAIProvider.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.provider

import com.github.xingzhewa.ccgui.model.provider.AIProvider
import com.github.xingzhewa.ccgui.model.provider.ChatRequest
import com.github.xingzhewa.ccgui.model.provider.ChatResponse
import com.github.xingzhewa.ccgui.model.provider.ChatChunk
// 结构与AnthropicProvider类似，API端点和请求格式不同
// 此处为骨架实现，后续阶段完善

class OpenAIProvider : AIProvider {
    override val name = "openai"
    override val availableModels = listOf("gpt-4", "gpt-4-turbo", "gpt-3.5-turbo")

    override suspend fun chat(request: ChatRequest): ChatResponse {
        TODO("Phase 4 实现")
    }

    override suspend fun streamChat(request: ChatRequest): kotlinx.coroutines.flow.Flow<ChatChunk> {
        TODO("Phase 4 实现")
    }
}
```

---

#### T2.5.4 `adaptation/provider/DeepSeekProvider.kt`

**文件**: `src/main/kotlin/.../adaptation/provider/DeepSeekProvider.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.provider

import com.github.xingzhewa.ccgui.model.provider.AIProvider
import com.github.xingzhewa.ccgui.model.provider.ChatRequest
import com.github.xingzhewa.ccgui.model.provider.ChatResponse
import com.github.xingzhewa.ccgui.model.provider.ChatChunk
// DeepSeek使用OpenAI兼容协议，可复用OpenAIProvider逻辑
// 此处为骨架实现

class DeepSeekProvider : AIProvider {
    override val name = "deepseek"
    override val availableModels = listOf("deepseek-chat", "deepseek-coder")

    override suspend fun chat(request: ChatRequest): ChatResponse {
        TODO("Phase 4 实现")
    }

    override suspend fun streamChat(request: ChatRequest): kotlinx.coroutines.flow.Flow<ChatChunk> {
        TODO("Phase 4 实现")
    }
}
```

---

## 3. plugin.xml 新增注册

```xml
<!-- Phase 2 新增 -->
<applicationService serviceImplementation="com.github.xingzhewa.ccgui.adaptation.provider.MultiProviderAdapter"/>
```

---

## 4. 任务依赖与执行顺序

```
T2.1 StreamCallback体系
  ↓
T2.2 StdioBridge ← 依赖 T2.1
  ↓
T2.2 ProcessPool ← 依赖 T2.2 StdioBridge
  ↓
T2.2 BridgeManager ← 依赖 T2.2 ProcessPool + T2.3 MessageParser
  ↓ (可并行)
T2.3 MessageParser ← 独立
T2.3 StreamingResponseParser ← 依赖 T2.3 MessageParser
  ↓ (可并行)
T2.4 VersionDetector ← 独立
T2.5 MultiProviderAdapter ← 独立
T2.5 AnthropicProvider ← 依赖 SecureStorage(Phase1)
```

---

## 5. 验收标准

| 验收项 | 标准 |
|--------|------|
| 编译 | 全部代码编译通过 |
| ToolWindow | 可正常打开，显示加载页面 |
| 消息发送 | `sendMessage()` 可构建CLI消息并发送 |
| 流式接收 | StreamCallback可接收流式输出 |
| 消息解析 | MessageParser可正确解析JSON/纯文本行 |
| Anthropic API | 可调用Claude API并收到响应 |
| 进程池 | 支持创建和管理多个CLI进程 |
| 版本检测 | 可检测CLI是否安装及版本信息 |

---

## 6. 文件清单汇总

| 序号 | 文件路径 | 类型 |
|------|----------|------|
| 1 | `adaptation/bridge/StreamCallback.kt` | 接口 |
| 2 | `adaptation/bridge/SimpleStreamCallback.kt` | 抽象类 |
| 3 | `adaptation/bridge/StdioBridge.kt` | 核心通信 |
| 4 | `adaptation/bridge/ProcessPool.kt` | 进程池 |
| 5 | `adaptation/bridge/BridgeManager.kt` | 管理器(服务) |
| 6 | `adaptation/parser/MessageParser.kt` | 解析器 |
| 7 | `adaptation/parser/StreamingResponseParser.kt` | 流式解析 |
| 8 | `adaptation/version/VersionDetector.kt` | 版本检测 |
| 9 | `adaptation/provider/MultiProviderAdapter.kt` | 适配器(服务) |
| 10 | `adaptation/provider/AnthropicProvider.kt` | Anthropic实现 |
| 11 | `adaptation/provider/OpenAIProvider.kt` | OpenAI骨架 |
| 12 | `adaptation/provider/DeepSeekProvider.kt` | DeepSeek骨架 |

**共计**: 12个文件
