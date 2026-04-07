# Phase 2.5: Claude Code SDK 集成 (Claude Agent SDK Integration)

**优先级**: P0 (核心关键路径)
**预估工期**: 8人天
**前置依赖**: Phase 1 (所有model和infrastructure组件)
**阶段目标**: 以Claude Code CLI子进程模式集成SDK，实现真正的Claude Code能力
**最后修订**: 2026-04-08（代码审查修复）

---

## 1. 阶段概览

### 1.1 为什么需要这个阶段？

**Phase 2 存在严重设计缺陷**：

| 错误方案 (Phase 2) | 正确方案 (本阶段) |
|-----|-----|
| `AnthropicProvider` 直接HTTP调用REST API | 通过 `claude` CLI 子进程通信 |
| `MultiProviderAdapter` 管理多个HTTP供应商 | Claude Code SDK 统一管理所有模型 |
| `StdioBridge` 自定义JSON协议 | 使用SDK标准 `stream-json` 协议 |
| `OpenAIProvider` / `DeepSeekProvider` 独立HTTP实现 | SDK通过环境变量支持多后端（Bedrock/Vertex） |
| 自行处理API Key认证 | SDK自行管理认证（ANTHROPIC_API_KEY等） |

**Claude Code SDK核心特性**：
- **子进程模型**: `claude -p "query" --output-format stream-json`
- **流式JSON输出**: 每行一个JSON对象，类型包括 init/user/assistant/result
- **会话恢复**: `--resume <sessionId>` 恢复已有会话
- **MCP配置**: `--mcp-config <file>` 加载MCP服务器配置
- **权限控制**: `--permission-prompt-tool` 自动处理权限请求
- **系统提示**: `--system-prompt` 自定义系统行为
- **工具限制**: `--allowedTools` 控制可用工具集
- **预编译启动**: TypeScript SDK的 `startup()` 可预热进程

### 1.2 核心数据流

```
┌──────────────────────────────────────────────────────────────────────────┐
│ 正确的数据流 (SDK集成)                                                     │
│                                                                          │
│  User Input                                                              │
│     │                                                                    │
│     ▼                                                                    │
│  ChatOrchestrator                                                        │
│     │                                                                    │
│     ▼                                                                    │
│  ClaudeCodeClient ────────────────────────────────────┐                 │
│     │                                                  │                 │
│     │  spawn subprocess                                │                 │
│     ▼                                                  │                 │
│  ProcessBuilder("claude", "-p", query,                │                 │
│    "--output-format", "stream-json",                   │                 │
│    "--resume", sessionId,                              │                 │
│    "--mcp-config", mcpConfigPath,                      │                 │
│    "--system-prompt", systemPrompt)                    │                 │
│     │                                                  │                 │
│     │  stdout (stream-json)                            │                 │
│     ▼                                                  │                 │
│  StreamJsonParser                                      │                 │
│     │  parse each line → SdkMessage                    │                 │
│     │                                                  │                 │
│     ├── SdkInitMessage      → 通知前端连接建立          │                 │
│     ├── SdkAssistantMessage → 流式推送到JCEF            │                 │
│     │   ├── content_block_delta  → appendChunk()       │                 │
│     │   └── tool_use            → InteractiveEngine    │                 │
│     ├── SdkResultMessage    → 完成通知                  │                 │
│     └── SdkErrorMessage     → 错误处理                  │                 │
│     │                                                  │                 │
│     ▼                                                  │                 │
│  StreamingOutputEngine → JCEF Frontend                 │                 │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 任务清单

### T2.5.1 SDK消息类型定义 (1人天)

#### `adaptation/sdk/SdkMessageTypes.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.sdk

import com.google.gson.JsonObject

/**
 * Claude Code SDK stream-json 消息类型定义
 *
 * stream-json 协议: 每行一个JSON对象，通过 "type" 字段区分消息类型
 *
 * 参考文档:
 *   - https://docs.anthropic.com/en/docs/claude-code/sdk
 *   - claude --help
 *
 * 扩展埋点:
 *   - 后续SDK版本可能新增消息类型，在此文件扩展即可
 */
object SdkMessageTypes {

    /**
     * SDK消息基类
     * 所有从CLI stdout解析出的消息都继承此接口
     */
    sealed class SdkMessage {
        abstract val rawJson: String
        abstract val timestamp: Long
    }

    /**
     * Init消息 — CLI进程启动时发送
     * 包含会话ID和工具列表
     *
     * 示例:
     * {"type":"init","session_id":"sess_abc123","tools":["Bash","Read","Write",...]}
     */
    data class SdkInitMessage(
        val sessionId: String,
        val tools: List<String>,
        val model: String? = null,
        override val rawJson: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SdkMessage()

    /**
     * User消息 — 用户发送的消息回显
     *
     * 示例:
     * {"type":"user","content":"请帮我优化这段代码","session_id":"sess_abc123"}
     */
    data class SdkUserMessage(
        val content: String,
        val sessionId: String?,
        override val rawJson: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SdkMessage()

    /**
     * Assistant消息 — AI的流式回复
     * 包含多种content block类型
     *
     * 示例 (text):
     * {"type":"assistant","message":{"content":[{"type":"text","text":"Hello"}]}}
     *
     * 示例 (tool_use):
     * {"type":"assistant","message":{"content":[{"type":"tool_use","name":"Read","input":{"file":"..."}]}]}
     */
    data class SdkAssistantMessage(
        val contentBlocks: List<ContentBlock>,
        val stopReason: String? = null,
        val model: String? = null,
        val sessionId: String? = null,
        override val rawJson: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SdkMessage() {

        /**
         * 提取纯文本内容
         */
        fun extractText(): String = contentBlocks
            .filterIsInstance<ContentBlock.Text>()
            .joinToString("") { it.text }

        /**
         * 提取工具调用
         */
        fun extractToolUses(): List<ContentBlock.ToolUse> = contentBlocks
            .filterIsInstance<ContentBlock.ToolUse>()

        /**
         * 是否包含工具调用
         */
        fun hasToolUse(): Boolean = contentBlocks.any { it is ContentBlock.ToolUse }
    }

    /**
     * Result消息 — 对话回合结束
     *
     * 示例:
     * {"type":"result","subtype":"success","cost_usd":0.003,"duration_ms":1500,"duration_api_ms":1200,"num_turns":1,"session_id":"sess_abc123"}
     */
    data class SdkResultMessage(
        val subtype: String,         // success, error, cancelled
        val costUsd: Double?,
        val durationMs: Long?,
        val durationApiMs: Long?,
        val numTurns: Int?,
        val sessionId: String?,
        val result: String?,         // 最终文本结果
        override val rawJson: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SdkMessage() {

        val isSuccess: Boolean get() = subtype == "success"
        val isError: Boolean get() = subtype == "error"
        val isCancelled: Boolean get() = subtype == "cancelled"
    }

    /**
     * Content Block 类型 — assistant消息中的内容块
     */
    sealed class ContentBlock {
        /** 文本内容块 */
        data class Text(
            val text: String
        ) : ContentBlock()

        /** 工具使用内容块 */
        data class ToolUse(
            val id: String,
            val name: String,
            val input: JsonObject
        ) : ContentBlock()

        /** 工具执行结果 */
        data class ToolResult(
            val toolUseId: String,
            val content: String,
            val isError: Boolean = false
        ) : ContentBlock()

        /** 思考内容块 (extended thinking) */
        data class Thinking(
            val text: String
        ) : ContentBlock()
    }

    /**
     * 未知消息类型 — 兜底处理
     */
    data class SdkUnknownMessage(
        val type: String,
        override val rawJson: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SdkMessage()
}
```

---

### T2.5.2 Stream-JSON协议解析器 (1.5人天)

#### `adaptation/sdk/StreamJsonParser.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.sdk

import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform

/**
 * Claude Code SDK stream-json 协议解析器
 *
 * 协议规范:
 *   - 每行一个JSON对象 (NDJSON - Newline Delimited JSON)
 *   - 空行忽略
 *   - 通过 "type" 字段区分消息类型: init, user, assistant, result
 *
 * 性能要求:
 *   - 解析延迟 < 5ms/行
 *   - 流式推送不阻塞
 *
 * 扩展埋点:
 *   - 后续可添加消息校验和版本兼容性检查
 *   - 后续可添加消息过滤（如过滤tool_use详情）
 */
class StreamJsonParser {

    private val log = logger<StreamJsonParser>()

    /**
     * 解析单行JSON为SDK消息
     *
     * @param line 从CLI stdout读取的一行文本
     * @return 解析后的SdkMessage，空行或解析失败返回null
     */
    fun parseLine(line: String): SdkMessageTypes.SdkMessage? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        return try {
            val json = JsonUtils.parseObject(trimmed)
            val type = json.get("type")?.asString ?: return handleUnknown(trimmed, "missing_type")

            when (type) {
                "init" -> parseInitMessage(json, trimmed)
                "user" -> parseUserMessage(json, trimmed)
                "assistant" -> parseAssistantMessage(json, trimmed)
                "result" -> parseResultMessage(json, trimmed)
                "system" -> parseSystemMessage(json, trimmed)
                else -> {
                    log.warn("Unknown SDK message type: $type")
                    SdkMessageTypes.SdkUnknownMessage(type, trimmed)
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to parse SDK line: ${e.message}, line=${trimmed.take(200)}")
            null
        }
    }

    /**
     * 将Flow<String>转换为Flow<SdkMessage>
     * 用于流式处理CLI输出
     */
    fun parseFlow(lines: Flow<String>): Flow<SdkMessageTypes.SdkMessage> {
        return lines.transform { line ->
            parseLine(line)?.let { emit(it) }
        }
    }

    /**
     * 从assistant消息中增量提取文本
     * 用于流式推送到前端
     */
    fun extractTextDelta(message: SdkMessageTypes.SdkAssistantMessage): String {
        return message.extractText()
    }

    // ---- 内部解析方法 ----

    private fun parseInitMessage(json: JsonObject, raw: String): SdkMessageTypes.SdkInitMessage {
        return SdkMessageTypes.SdkInitMessage(
            sessionId = json.get("session_id")?.asString ?: "",
            tools = parseToolsList(json.getAsJsonArray("tools")),
            model = json.get("model")?.asString,
            rawJson = raw
        )
    }

    private fun parseUserMessage(json: JsonObject, raw: String): SdkMessageTypes.SdkUserMessage {
        return SdkMessageTypes.SdkUserMessage(
            content = json.get("content")?.asString ?: "",
            sessionId = json.get("session_id")?.asString,
            rawJson = raw
        )
    }

    private fun parseAssistantMessage(json: JsonObject, raw: String): SdkMessageTypes.SdkAssistantMessage {
        val messageObj = json.getAsJsonObject("message") ?: json
        val contentArray = messageObj.getAsJsonArray("content") ?: JsonArray()

        val contentBlocks = parseContentBlocks(contentArray)

        return SdkMessageTypes.SdkAssistantMessage(
            contentBlocks = contentBlocks,
            stopReason = messageObj.get("stop_reason")?.asString,
            model = messageObj.get("model")?.asString,
            sessionId = json.get("session_id")?.asString,
            rawJson = raw
        )
    }

    private fun parseResultMessage(json: JsonObject, raw: String): SdkMessageTypes.SdkResultMessage {
        return SdkMessageTypes.SdkResultMessage(
            subtype = json.get("subtype")?.asString ?: "unknown",
            costUsd = json.get("cost_usd")?.asDouble,
            durationMs = json.get("duration_ms")?.asLong,
            durationApiMs = json.get("duration_api_ms")?.asLong,
            numTurns = json.get("num_turns")?.asInt,
            sessionId = json.get("session_id")?.asString,
            result = json.get("result")?.asString,
            rawJson = raw
        )
    }

    /**
     * system消息 — SDK内部系统通知
     * 如权限请求、警告等
     */
    private fun parseSystemMessage(json: JsonObject, raw: String): SdkMessageTypes.SdkMessage {
        // system消息可能是权限请求，需要交互处理
        val subtype = json.get("subtype")?.asString
        if (subtype == "permission_request") {
            // 权限请求会通过 SdkPermissionHandler 处理
            log.info("SDK permission request received")
        }
        return SdkMessageTypes.SdkUnknownMessage("system", raw)
    }

    /**
     * 解析content blocks数组
     */
    private fun parseContentBlocks(contentArray: JsonArray): List<SdkMessageTypes.ContentBlock> {
        return contentArray.mapNotNull { element ->
            try {
                val obj = element.asJsonObject
                val type = obj.get("type")?.asString ?: return@mapNotNull null

                when (type) {
                    "text" -> SdkMessageTypes.ContentBlock.Text(
                        text = obj.get("text")?.asString ?: ""
                    )
                    "tool_use" -> SdkMessageTypes.ContentBlock.ToolUse(
                        id = obj.get("id")?.asString ?: "",
                        name = obj.get("name")?.asString ?: "",
                        input = obj.getAsJsonObject("input") ?: JsonObject()
                    )
                    "tool_result" -> SdkMessageTypes.ContentBlock.ToolResult(
                        toolUseId = obj.get("tool_use_id")?.asString ?: "",
                        content = obj.get("content")?.asString ?: "",
                        isError = obj.get("is_error")?.asBoolean ?: false
                    )
                    "thinking" -> SdkMessageTypes.ContentBlock.Thinking(
                        text = obj.get("text")?.asString ?: ""
                    )
                    else -> null
                }
            } catch (e: Exception) {
                log.warn("Failed to parse content block: ${e.message}")
                null
            }
        }
    }

    private fun parseToolsArray(array: JsonArray?): List<String> {
        if (array == null) return emptyList()
        // tools可能是字符串数组或对象数组
        return array.mapNotNull { element ->
            if (element.isJsonPrimitive) element.asString
            else element.asJsonObject?.get("name")?.asString
        }
    }

    private fun parseToolsList(array: JsonArray?): List<String> {
        if (array == null) return emptyList()
        return parseToolsArray(array)
    }

    private fun handleUnknown(raw: String, reason: String): SdkMessageTypes.SdkMessage {
        log.debug("Unhandled SDK line ($reason): ${raw.take(200)}")
        return SdkMessageTypes.SdkUnknownMessage("unknown", raw)
    }
}
```

---

### T2.5.3 SDK核心客户端 (2人天) — 核心关键路径

#### `adaptation/sdk/ClaudeCodeClient.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.sdk

import com.github.xingzhewa.ccgui.adaptation.sdk.SdkMessageTypes.*
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.*
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Claude Code SDK 核心客户端
 *
 * 通过ProcessBuilder启动 `claude` CLI子进程，使用 stream-json 协议通信。
 * 这是整个插件与Claude Code交互的唯一入口。
 *
 * CLI命令格式:
 *   claude -p <prompt> --output-format stream-json [options]
 *
 * 关键选项:
 *   --resume <sessionId>      恢复已有会话
 *   --continue                 继续最近一次对话
 *   --system-prompt <text>     自定义系统提示
 *   --mcp-config <file>        MCP服务器配置文件路径
 *   --allowedTools <list>      限制可用工具
 *   --permission-prompt-tool   权限提示工具
 *   --max-turns <n>            最大对话轮次
 *
 * 环境变量:
 *   ANTHROPIC_API_KEY          API密钥
 *   CLAUDE_CODE_USE_BEDROCK    使用Amazon Bedrock
 *   CLAUDE_CODE_USE_VERTEX     使用Google Vertex AI
 *
 * 扩展埋点:
 *   - 后续可添加进程预热池
 *   - 后续可添加多SDK版本支持
 */
@Service(Service.Level.PROJECT)
class ClaudeCodeClient(private val project: Project) : Disposable {

    private val log = logger<ClaudeCodeClient>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val parser = StreamJsonParser()
    private val configBuilder = SdkConfigBuilder(project)

    /** 活跃的CLI进程: sessionId -> CliSession */
    private val sessions = ConcurrentHashMap<String, CliSession>()

    /** 客户端状态 */
    private val _state = MutableStateFlow(ClientState.IDLE)
    val state: StateFlow<ClientState> = _state.asStateFlow()

    /** 当前会话ID (由SDK init消息返回) */
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    /** 请求锁：同一时间只允许一个CLI请求 */
    private val requestMutex = Mutex()

    enum class ClientState {
        IDLE,           // 空闲
        STARTING,       // CLI进程启动中
        STREAMING,      // 流式输出中
        WAITING_INPUT,  // 等待用户输入（权限请求等）
        ERROR           // 错误状态
    }

    /**
     * CLI会话封装
     */
    data class CliSession(
        val processId: String,
        val process: Process,
        val sdkSessionId: String?,  // SDK返回的session_id（用于resume）
        val reader: BufferedReader,
        val scope: CoroutineScope,
        val isAlive: AtomicBoolean = AtomicBoolean(true)
    )

    // ---- 消息回调接口 ----

    /**
     * SDK事件监听器
     * 上层（ChatOrchestrator/StreamingOutputEngine）通过此接口接收SDK事件
     */
    interface SdkEventListener {
        /** 收到init消息 */
        fun onInit(message: SdkInitMessage) {}
        /** 收到assistant消息（流式） */
        fun onAssistant(message: SdkAssistantMessage) {}
        /** 收到result消息（完成） */
        fun onResult(message: SdkResultMessage) {}
        /** 收到错误 */
        fun onError(error: String) {}
        /** 流式文本增量 — 用于直接推送到前端 */
        fun onTextDelta(text: String) {}
        /** 工具使用通知 */
        fun onToolUse(toolName: String, input: com.google.gson.JsonObject) {}
    }

    // ---- 核心API ----

    /**
     * 发送消息并接收流式回复
     *
     * 核心方法：
     *   1. 构建CLI命令和参数
     *   2. 启动子进程
     *   3. 读取stream-json输出
     *   4. 解析并回调
     *   5. 清理进程
     *
     * @param prompt 用户输入的提示文本
     * @param options SDK选项（会话恢复、MCP配置等）
     * @param listener 事件监听器
     * @return Result<SdkResultMessage> 最终结果
     */
    suspend fun sendMessage(
        prompt: String,
        options: SdkOptions = SdkOptions(),
        listener: SdkEventListener? = null
    ): Result<SdkResultMessage> = requestMutex.withLock {
        if (_state.value == ClientState.STREAMING) {
            return Result.failure(IllegalStateException("Another request is in progress"))
        }

        _state.value = ClientState.STARTING

        var process: Process? = null
        try {
            // 1. 构建CLI命令
            val command = configBuilder.buildCommand(prompt, options)
            log.info("Starting Claude CLI: ${command.joinToString(" ").take(200)}")

            // 2. 写入临时MCP配置文件（如有）
            val mcpConfigFile = options.mcpConfig?.let {
                configBuilder.writeMcpConfigTempFile(it)
            }

            val finalCommand = if (mcpConfigFile != null) {
                command + listOf("--mcp-config", mcpConfigFile.absolutePath)
            } else command

            // 3. 启动子进程
            val pb = ProcessBuilder(finalCommand)
                .redirectErrorStream(false)
                .directory(project.basePath?.let { File(it) })

            // 设置环境变量
            options.env.forEach { (k, v) -> pb.environment()[k] = v }

            process = pb.start()
            _state.value = ClientState.STREAMING

            // 4. 读取并解析stdout
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var resultMessage: SdkResultMessage? = null
            var sdkSessionId: String? = null

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parsed = parser.parseLine(line!!)
                if (parsed == null) continue

                when (parsed) {
                    is SdkInitMessage -> {
                        sdkSessionId = parsed.sessionId
                        _currentSessionId.value = parsed.sessionId
                        // 注册到会话映射
                        sessions[parsed.sessionId] = CliSession(
                            processId = parsed.sessionId,
                            process = process,
                            sdkSessionId = parsed.sessionId,
                            reader = reader,
                            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                        )
                        listener?.onInit(parsed)
                        EventBus.publish(SdkSessionInitEvent(parsed.sessionId, parsed.tools))
                    }
                    is SdkAssistantMessage -> {
                        listener?.onAssistant(parsed)
                        // 提取文本增量
                        val text = parsed.extractText()
                        if (text.isNotEmpty()) {
                            listener?.onTextDelta(text)
                        }
                        // 提取工具使用
                        parsed.extractToolUses().forEach { toolUse ->
                            listener?.onToolUse(toolUse.name, toolUse.input)
                        }
                    }
                    is SdkResultMessage -> {
                        resultMessage = parsed
                        listener?.onResult(parsed)
                    }
                    else -> {
                        // 未知消息类型，忽略
                        log.debug("Unhandled SDK message type: ${parsed::class.simpleName}")
                    }
                }
            }

            // 5. 读取stderr（用于调试）
            val stderr = process.errorStream.bufferedReader().readText()
            if (stderr.isNotBlank()) {
                log.debug("CLI stderr: ${stderr.take(500)}")
            }

            // 6. 等待进程结束
            process.waitFor()

            // 7. 更新SDK会话使用信息
            sdkSessionId?.let { sid ->
                SdkSessionManager.getInstance(project).updateSessionUsage(
                    sid, costUsd = resultMessage?.costUsd, messageCount = 1
                )
            }

            val finalState = if (resultMessage?.isError == true) ClientState.ERROR else ClientState.IDLE
            _state.value = finalState

            if (resultMessage != null) {
                log.info("CLI completed: subtype=${resultMessage.subtype}, " +
                    "cost=${resultMessage.costUsd}, duration=${resultMessage.durationMs}ms")
                Result.success(resultMessage)
            } else {
                log.warn("CLI completed without result message")
                Result.failure(RuntimeException("CLI completed without result message"))
            }

        } catch (e: CancellationException) {
            _state.value = ClientState.IDLE
            throw e
        } catch (e: Exception) {
            _state.value = ClientState.ERROR
            log.error("ClaudeCodeClient.sendMessage failed", e)
            listener?.onError(e.message ?: "Unknown error")
            Result.failure(e)
        } finally {
            // 清理当前会话的进程引用（不销毁进程映射，供cancelCurrentRequest使用）
            _currentSessionId.value?.let { sessions.remove(it) }
            process?.destroyForcibly()
        }
    }

    /**
     * 流式发送消息（返回Flow）
     * 适用于需要细粒度控制流式输出的场景
     */
    fun streamMessage(
        prompt: String,
        options: SdkOptions = SdkOptions()
    ): kotlinx.coroutines.flow.Flow<SdkMessage> = kotlinx.coroutines.flow.flow {
        val command = configBuilder.buildCommand(prompt, options)
        log.info("Streaming Claude CLI: ${command.joinToString(" ").take(200)}")

        val mcpConfigFile = options.mcpConfig?.let {
            configBuilder.writeMcpConfigTempFile(it)
        }
        val finalCommand = if (mcpConfigFile != null) {
            command + listOf("--mcp-config", mcpConfigFile.absolutePath)
        } else command

        val pb = ProcessBuilder(finalCommand)
            .redirectErrorStream(false)
            .directory(project.basePath?.let { File(it) })
        options.env.forEach { (k, v) -> pb.environment()[k] = v }

        val process = pb.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parsed = parser.parseLine(line!!)
                if (parsed != null) {
                    emit(parsed)
                }
            }
            process.waitFor()
        } finally {
            process.destroyForcibly()
        }
    }

    /**
     * 取消当前请求
     */
    fun cancelCurrentRequest() {
        sessions.values.forEach { session ->
            if (session.isAlive.get()) {
                session.process.destroyForcibly()
                session.isAlive.set(false)
            }
        }
        _state.value = ClientState.IDLE
        log.info("Current request cancelled")
    }

    /**
     * 检查CLI是否可用
     */
    fun isCliAvailable(): Boolean {
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

    /**
     * 获取CLI版本信息
     */
    fun getCliVersion(): String? {
        return try {
            val process = ProcessBuilder("claude", "--version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            null
        }
    }

    override fun dispose() {
        scope.cancel()
        sessions.values.forEach { session ->
            session.process.destroyForcibly()
        }
        sessions.clear()
        _state.value = ClientState.IDLE
    }

    companion object {
        fun getInstance(project: Project): ClaudeCodeClient =
            project.getService(ClaudeCodeClient::class.java)
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
```

---

### T2.5.4 SDK配置构建器 (1人天)

#### `adaptation/sdk/SdkConfigBuilder.kt`

```kotlin
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
 *
 * 扩展埋点:
 *   - 后续可添加更多CLI参数支持
 *   - 后续可添加配置模板预设
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
            append("You are Claude, an AI assistant integrated into JetBrains IDE via ClaudeCodeJet (ccgui) plugin. ")
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
```

---

### T2.5.5 SDK会话管理 (1.5人天)

#### `adaptation/sdk/SdkSessionManager.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.sdk

import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * SDK会话管理器
 *
 * 管理Claude Code CLI的会话生命周期。
 * SDK通过 --resume <session_id> 恢复会话，session_id 由 init 消息返回。
 *
 * 与 SessionManager（应用层会话管理）的关系:
 *   - SessionManager: 管理UI层的会话（名称、消息列表、切换）
 *   - SdkSessionManager: 管理SDK层的会话（CLI session_id映射、恢复）
 *   - 一个应用层会话可对应一个SDK会话
 *
 * 扩展埋点:
 *   - 后续可添加会话预热（提前spawn CLI进程）
 *   - 后续可添加会话状态持久化
 */
@Service(Service.Level.PROJECT)
class SdkSessionManager(private val project: Project) : Disposable {

    private val log = logger<SdkSessionManager>()

    /**
     * 应用层会话ID → SDK会话ID 的映射
     */
    private val sessionMapping = ConcurrentHashMap<String, String>()

    /**
     * SDK会话元数据
     */
    private val sessionMeta = ConcurrentHashMap<String, SdkSessionMeta>()

    data class SdkSessionMeta(
        val sdkSessionId: String,
        val appSessionId: String,
        val createdAt: Long,
        val lastUsedAt: Long,
        val totalMessages: Int = 0,
        val totalCostUsd: Double = 0.0,
        val tools: List<String> = emptyList()
    )

    private val _activeSdkSession = MutableStateFlow<String?>(null)
    val activeSdkSession: StateFlow<String?> = _activeSdkSession.asStateFlow()

    /**
     * 注册SDK会话映射
     * 当CLI返回init消息时调用
     */
    fun registerSession(appSessionId: String, sdkSessionId: String, tools: List<String>) {
        sessionMapping[appSessionId] = sdkSessionId
        sessionMeta[sdkSessionId] = SdkSessionMeta(
            sdkSessionId = sdkSessionId,
            appSessionId = appSessionId,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            tools = tools
        )
        _activeSdkSession.value = sdkSessionId

        log.info("SDK session registered: app=$appSessionId → sdk=$sdkSessionId, tools=${tools.size}")
    }

    /**
     * 获取SDK会话ID（用于 --resume 参数）
     */
    fun getSdkSessionId(appSessionId: String): String? = sessionMapping[appSessionId]

    /**
     * 获取当前活跃的SDK会话ID
     */
    fun getCurrentSdkSessionId(): String? = _activeSdkSession.value

    /**
     * 更新会话使用信息
     */
    fun updateSessionUsage(sdkSessionId: String, costUsd: Double? = null, messageCount: Int? = null) {
        sessionMeta[sdkSessionId]?.let { meta ->
            sessionMeta[sdkSessionId] = meta.copy(
                lastUsedAt = System.currentTimeMillis(),
                totalCostUsd = meta.totalCostUsd + (costUsd ?: 0.0),
                totalMessages = meta.totalMessages + (messageCount ?: 0)
            )
        }
    }

    /**
     * 构建恢复会话的SdkOptions
     */
    fun buildResumeOptions(appSessionId: String, extraOptions: SdkOptions = SdkOptions()): SdkOptions {
        val sdkSessionId = sessionMapping[appSessionId]
        return extraOptions.copy(
            resumeSessionId = sdkSessionId
        )
    }

    /**
     * 移除会话映射
     */
    fun removeSession(appSessionId: String) {
        sessionMapping.remove(appSessionId)?.let { sdkSessionId ->
            sessionMeta.remove(sdkSessionId)
        }
    }

    /**
     * 获取所有SDK会话信息
     */
    fun getAllSessionMeta(): List<SdkSessionMeta> = sessionMeta.values.toList()

    override fun dispose() {
        sessionMapping.clear()
        sessionMeta.clear()
    }

    companion object {
        fun getInstance(project: Project): SdkSessionManager =
            project.getService(SdkSessionManager::class.java)
    }
}
```

---

### T2.5.6 SDK权限处理器 (1人天)

#### `adaptation/sdk/SdkPermissionHandler.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.sdk

import com.github.xingzhewa.ccgui.application.interaction.InteractiveRequestEngine
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.model.interaction.InteractiveQuestion
import com.github.xingzhewa.ccgui.model.interaction.QuestionType
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * SDK权限处理器
 *
 * Claude Code SDK在执行工具调用时可能需要用户确认权限：
 *   - 文件读写权限
 *   - 命令执行权限
 *   - 网络访问权限
 *
 * 处理流程:
 *   1. SDK通过 --permission-prompt-tool 发出权限请求
 *   2. 本处理器解析权限请求并展示给用户
 *   3. 用户确认/拒绝后，将结果返回给SDK
 *
 * 与 InteractiveRequestEngine 的关系:
 *   - InteractiveRequestEngine 是通用的AI交互请求引擎
 *   - SdkPermissionHandler 是SDK特有的权限处理，将权限请求转化为交互式问题
 *   - 权限请求通过 InteractiveRequestEngine 展示给用户
 *
 * 扩展埋点:
 *   - 后续可添加权限策略配置（自动允许/拒绝特定工具）
 *   - 后续可添加权限历史记录
 */
class SdkPermissionHandler(private val project: Project) {

    private val log = logger<SdkPermissionHandler>()

    /**
     * 权限策略
     */
    enum class PermissionPolicy {
        ALWAYS_ASK,      // 每次都询问
        AUTO_ALLOW_SAFE,  // 安全操作自动允许（如Read）
        AUTO_ALLOW_ALL    // 全部自动允许（危险）
    }

    /** 安全工具列表（自动允许） */
    private val safeTools = setOf("Read", "Glob", "Grep", "LS")

    /** 当前权限策略 */
    private val _policy = MutableStateFlow(PermissionPolicy.AUTO_ALLOW_SAFE)
    val policy: StateFlow<PermissionPolicy> = _policy.asStateFlow()

    /** 已缓存的权限决策: toolName → 允许 */
    private val cachedDecisions = ConcurrentHashMap<String, Boolean>()

    /**
     * 处理权限请求
     *
     * @param toolName 工具名称（如 "Bash", "Write", "Edit"）
     * @param input 工具输入参数
     * @return true=允许, false=拒绝
     */
    suspend fun handlePermissionRequest(
        toolName: String,
        input: Map<String, Any>
    ): Boolean {
        // 1. 检查策略
        if (_policy.value == PermissionPolicy.AUTO_ALLOW_ALL) {
            log.info("Auto-allowed (policy): $toolName")
            return true
        }

        if (_policy.value == PermissionPolicy.AUTO_ALLOW_SAFE && toolName in safeTools) {
            log.info("Auto-allowed (safe tool): $toolName")
            return true
        }

        // 2. 检查缓存决策
        cachedDecisions[toolName]?.let {
            log.info("Cached decision for $toolName: $it")
            return it
        }

        // 3. 向用户展示权限请求
        val description = buildPermissionDescription(toolName, input)

        val question = InteractiveQuestion(
            questionId = "perm_${toolName}_${System.currentTimeMillis()}",
            question = description,
            questionType = QuestionType.CONFIRMATION,
            options = listOf(
                com.github.xingzhewa.ccgui.model.interaction.QuestionOption(
                    id = "allow", label = "Allow"
                ),
                com.github.xingzhewa.ccgui.model.interaction.QuestionOption(
                    id = "deny", label = "Deny"
                ),
                com.github.xingzhewa.ccgui.model.interaction.QuestionOption(
                    id = "always", label = "Always Allow"
                )
            )
        )

        // 通过EventBus通知UI展示权限对话框
        EventBus.publish(com.github.xingzhewa.ccgui.infrastructure.eventbus.PermissionRequestEvent(
            toolName = toolName,
            description = description,
            question = question
        ))

        // 实际实现中，这里应该挂起等待用户回答
        // 目前返回安全默认值
        log.info("Permission request for $toolName: showing to user")
        return false
    }

    /**
     * 设置权限策略
     */
    fun setPolicy(policy: PermissionPolicy) {
        _policy.value = policy
        log.info("Permission policy set to: $policy")
    }

    /**
     * 缓存权限决策
     */
    fun cacheDecision(toolName: String, allow: Boolean) {
        cachedDecisions[toolName] = allow
    }

    /**
     * 清除缓存的权限决策
     */
    fun clearCachedDecisions() {
        cachedDecisions.clear()
    }

    private fun buildPermissionDescription(toolName: String, input: Map<String, Any>): String {
        return when (toolName) {
            "Bash" -> "Execute command: `${input["command"]}`"
            "Write" -> "Write file: ${input["file_path"]}"
            "Edit" -> "Edit file: ${input["file_path"]}"
            "MultiEdit" -> "Edit multiple files"
            else -> "Use tool: $toolName"
        }
    }
}
```

---

## 3. 对Phase 2的修正

### 3.1 废弃的组件

以下 Phase 2 组件应**废弃或降级**：

| 组件 | 处理方式 | 原因 |
|------|----------|------|
| `AnthropicProvider.kt` | **废弃** | SDK统一管理API调用，插件不需要直接调用Anthropic REST API |
| `OpenAIProvider.kt` | **废弃** | SDK通过环境变量支持多后端 |
| `DeepSeekProvider.kt` | **废弃** | 同上 |
| `MultiProviderAdapter.kt` | **重构为模型信息查询器** | 不再负责API调用，只负责查询可用模型列表 |
| `StdioBridge.kt` | **重构** | 协议从自定义JSON改为 stream-json |
| `BridgeManager.kt` | **重构** | 委托给 ClaudeCodeClient |

### 3.2 保留并适配的组件

| 组件 | 适配方式 |
|------|----------|
| `StreamCallback.kt` / `SimpleStreamCallback.kt` | 保留，作为SDK消息的回调接口 |
| `ProcessPool.kt` | 保留，用于管理CLI子进程（预热等） |
| `MessageParser.kt` | 保留用于非SDK场景（纯文本输出等），但主路径使用 StreamJsonParser |
| `VersionDetector.kt` | 保留，用于检测CLI版本兼容性 |

### 3.3 重构后的 MultiProviderAdapter

```kotlin
// 重构方向：从"API调用适配器"变为"模型信息注册表"
// 不再直接调用HTTP API，而是记录SDK支持的模型信息

@Service(Service.Level.APP)
class ModelInfoRegistry {

    data class ModelInfo(
        val id: String,
        val provider: String,      // anthropic, openai-compatible
        val displayName: String,
        val maxTokens: Int,
        val supportsStreaming: Boolean = true,
        val supportsVision: Boolean = false
    )

    private val models = ConcurrentHashMap<String, ModelInfo>()

    init {
        // 注册SDK默认支持的模型
        registerDefaults()
    }

    fun registerModel(info: ModelInfo) { models[info.id] = info }
    fun getModel(modelId: String): ModelInfo? = models[modelId]
    fun getAllModels(): List<ModelInfo> = models.values.toList()
    fun getModelsByProvider(provider: String): List<ModelInfo> =
        models.values.filter { it.provider == provider }

    private fun registerDefaults() {
        listOf(
            ModelInfo("claude-sonnet-4-20250514", "anthropic", "Claude Sonnet 4", 8192),
            ModelInfo("claude-opus-4-20250514", "anthropic", "Claude Opus 4", 8192),
            ModelInfo("claude-haiku-4-20250514", "anthropic", "Claude Haiku 4", 8192)
        ).forEach { registerModel(it) }
    }
}
```

---

## 4. 重构后的BridgeManager

BridgeManager 需要适配为 ClaudeCodeClient 的上层封装：

```kotlin
// 重构方向：BridgeManager → 委托 ClaudeCodeClient
// 保持与 MyToolWindowFactory 的兼容接口

@Service(Service.Level.PROJECT)
class BridgeManager(private val project: Project) {

    private val log = logger<BridgeManager>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val claudeClient: ClaudeCodeClient by lazy { ClaudeCodeClient.getInstance(project) }
    private val sdkSessionManager: SdkSessionManager by lazy { SdkSessionManager.getInstance(project) }

    /** 连接状态 — 与MyToolWindowFactory兼容 */
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * 发送消息 — 与 MyToolWindowFactory 兼容的接口
     *
     * 内部委托给 ClaudeCodeClient
     */
    fun sendMessage(
        message: String,
        sessionId: String,
        callback: StreamCallback
    ): Result<Unit> {
        val options = sdkSessionManager.buildResumeOptions(sessionId)

        scope.launch {
            _connectionState.value = ConnectionState.CONNECTED
            claudeClient.sendMessage(message, options, object : ClaudeCodeClient.SdkEventListener {
                override fun onTextDelta(text: String) {
                    callback.onLineReceived(text)
                }
                override fun onResult(message: SdkMessageTypes.SdkResultMessage) {
                    callback.onStreamComplete(emptyList())
                }
                override fun onError(error: String) {
                    callback.onStreamError(error)
                }
            })
            _connectionState.value = ConnectionState.DISCONNECTED
        }

        return Result.success(Unit)
    }

    /**
     * 取消流式输出 — 与MyToolWindowFactory兼容
     */
    fun cancelStreaming(sessionId: String) {
        claudeClient.cancelCurrentRequest()
    }

    fun dispose() {
        scope.cancel()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    companion object {
        fun getInstance(project: Project): BridgeManager =
            project.getService(BridgeManager::class.java)
    }
}

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}
```

---

## 5. ChatOrchestrator 适配

Phase 3 的 ChatOrchestrator 需要从直接调用 MultiProviderAdapter 改为调用 ClaudeCodeClient：

```kotlin
// ChatOrchestrator 核心方法重构

/**
 * 发送消息（流式） — 重构后
 * 通过 ClaudeCodeClient 与 Claude Code SDK 通信
 */
suspend fun streamMessage(message: ChatMessage): Flow<ChatChunk> = flow {
    val session = sessionManager.getCurrentSession()
        ?: throw IllegalStateException("No active session")

    // 1. 添加用户消息
    session.addMessage(message)

    // 2. 构建SDK选项
    val sdkSession = SdkSessionManager.getInstance(project)
    val options = sdkSession.buildResumeOptions(session.id, SdkOptions(
        systemPrompt = buildSystemPrompt(session.context)
    ))

    // 3. 通过SDK发送消息
    val client = ClaudeCodeClient.getInstance(project)

    client.streamMessage(message.content, options).collect { sdkMessage ->
        when (sdkMessage) {
            is SdkMessageTypes.SdkInitMessage -> {
                sdkSession.registerSession(session.id, sdkMessage.sessionId, sdkMessage.tools)
            }
            is SdkMessageTypes.SdkAssistantMessage -> {
                val text = sdkMessage.extractText()
                if (text.isNotEmpty()) {
                    emit(ChatChunk(content = text))
                }
            }
            is SdkMessageTypes.SdkResultMessage -> {
                emit(ChatChunk(content = "", isComplete = true))
            }
            else -> { /* 忽略 */ }
        }
    }
}
```

---

## 6. SDK事件定义

### 6.1 新增 EventBus 事件

在 `infrastructure/eventbus/Events.kt` 中新增：

```kotlin
// ---- SDK Events ----

/**
 * SDK会话初始化事件
 * CLI进程启动并返回session_id时触发
 */
data class SdkSessionInitEvent(
    val sessionId: String,
    val tools: List<String>
) : Event("sdk.session.init")

/**
 * SDK权限请求事件
 * CLI请求用户确认权限时触发
 */
data class PermissionRequestEvent(
    val toolName: String,
    val description: String,
    val question: InteractiveQuestion
) : Event("sdk.permission.request")

/**
 * SDK流式文本增量事件
 * 每收到一个assistant文本块时触发
 */
data class SdkTextDeltaEvent(
    val sessionId: String,
    val text: String
) : Event("sdk.text.delta")

/**
 * SDK工具使用事件
 */
data class SdkToolUseEvent(
    val sessionId: String,
    val toolName: String,
    val input: com.google.gson.JsonObject
) : Event("sdk.tool.use")

/**
 * SDK结果事件
 */
data class SdkResultEvent(
    val sessionId: String,
    val subtype: String,
    val costUsd: Double?,
    val durationMs: Long?
) : Event("sdk.result")
```

---

## 7. plugin.xml 新增注册

```xml
<!-- Phase 2.5 新增 — SDK集成 -->
<projectService serviceImplementation="com.github.xingzhewa.ccgui.adaptation.sdk.ClaudeCodeClient"/>
<projectService serviceImplementation="com.github.xingzhewa.ccgui.adaptation.sdk.SdkSessionManager"/>

<!-- Phase 2.5 修改 — 替换原有的 MultiProviderAdapter -->
<!-- 旧: <applicationService serviceImplementation="...adaptation.provider.MultiProviderAdapter"/> -->
<!-- 新: -->
<applicationService serviceImplementation="com.github.xingzhewa.ccgui.adaptation.sdk.ModelInfoRegistry"/>
```

---

## 8. 任务依赖

```
T2.5.1 SdkMessageTypes ← 独立（仅需Phase1的JsonUtils）
  ↓
T2.5.2 StreamJsonParser ← 依赖 T2.5.1 SdkMessageTypes
  ↓
T2.5.3 ClaudeCodeClient ← 依赖 T2.5.2 StreamJsonParser + T2.5.4 SdkConfigBuilder
  ↓ (可并行)
T2.5.4 SdkConfigBuilder ← 独立
T2.5.5 SdkSessionManager ← 独立
T2.5.6 SdkPermissionHandler ← 依赖 InteractiveRequestEngine(Phase4)
```

---

## 9. 与其他Phase的交互

```
Phase 1 (Foundation)
  │
  ├── JsonUtils, logger, EventBus ← SDK模块使用
  │
Phase 2 (Adaptation) — 需要修正
  │
  ├── StreamCallback ← 保留，SDK通过此接口回调
  ├── ProcessPool ← 保留，可用于CLI进程预热
  ├── MessageParser ← 保留降级为辅助
  ├── VersionDetector ← 保留，检测CLI可用性
  ├── StdioBridge ← 重构为 stream-json 协议
  ├── BridgeManager ← 重构为 ClaudeCodeClient 委托
  │
Phase 2.5 (SDK Integration) ← 本阶段
  │
  ├── ClaudeCodeClient ← 替代 AnthropicProvider/OpenAIProvider
  ├── StreamJsonParser ← 替代自定义JSON协议
  │
Phase 3 (Core Services) — 需要适配
  │
  ├── ChatOrchestrator ← 从调用 MultiProviderAdapter 改为调用 ClaudeCodeClient
  ├── StreamingOutputEngine ← 接收SDK流式数据
  ├── ConfigManager ← 管理SDK配置（system-prompt、tools等）
  │
Phase 4 (Features) — 需要适配
  │
  ├── InteractiveRequestEngine ← 处理SDK权限请求
  ├── PromptOptimizer ← 通过SDK发送优化请求
  │
Phase 5 (Ecosystem) — 直接受益
  │
  ├── McpServerManager ← 直接映射到 --mcp-config
  ├── ScopeManager ← 管理不同作用域的SDK配置
```

---

## 10. 验收标准

| 验收项 | 标准 |
|--------|------|
| CLI可用性检测 | `isCliAvailable()` 正确检测 `claude` 是否安装 |
| stream-json解析 | `StreamJsonParser` 可解析 init/user/assistant/result 四种消息类型 |
| 消息发送 | `ClaudeCodeClient.sendMessage()` 可启动CLI进程并收到回复 |
| 流式输出 | `streamMessage()` 返回的Flow可逐字推送到JCEF |
| 会话恢复 | `--resume` 参数可恢复已有会话，上下文不丢失 |
| MCP配置 | `--mcp-config` 可正确生成临时配置文件并传给CLI |
| 系统提示 | `--system-prompt` 可自定义系统行为 |
| 权限处理 | SDK权限请求可展示给用户并接收决策 |
| 进程生命周期 | CLI进程正确启动和销毁，无资源泄漏 |
| 错误恢复 | CLI进程崩溃后可检测并通知用户 |
| 费用追踪 | result消息中的 cost_usd 可正确记录 |
| BridgeManager兼容 | 重构后的BridgeManager与MyToolWindowFactory接口兼容 |

---

## 11. 文件清单汇总

| 序号 | 文件路径 | 类型 | 优先级 |
|------|----------|------|--------|
| 1 | `adaptation/sdk/SdkMessageTypes.kt` | 消息类型 | P0 |
| 2 | `adaptation/sdk/StreamJsonParser.kt` | 协议解析 | P0 |
| 3 | `adaptation/sdk/ClaudeCodeClient.kt` | 核心客户端 | P0 |
| 4 | `adaptation/sdk/SdkConfigBuilder.kt` | 配置构建 | P0 |
| 5 | `adaptation/sdk/SdkSessionManager.kt` | 会话管理 | P1 |
| 6 | `adaptation/sdk/SdkPermissionHandler.kt` | 权限处理 | P1 |

**共计**: 6个新文件 + Phase 2/3 中的多个文件需要重构

---

## 12. 执行建议

### 12.1 开发顺序

1. **Day 1-2**: `SdkMessageTypes.kt` + `StreamJsonParser.kt` + 单元测试
2. **Day 3-4**: `SdkConfigBuilder.kt` + `ClaudeCodeClient.kt` 核心逻辑
3. **Day 5**: `ClaudeCodeClient.kt` 完善错误处理 + 集成测试
4. **Day 6**: `SdkSessionManager.kt` + 重构 `BridgeManager.kt`
5. **Day 7**: `SdkPermissionHandler.kt` + 适配 `ChatOrchestrator.kt`
6. **Day 8**: 端到端测试 + 清理废弃代码

### 12.2 测试要点

```kotlin
// StreamJsonParser 单元测试示例
class StreamJsonParserTest {

    private val parser = StreamJsonParser()

    @Test
    fun `parseLine should parse init message`() {
        val line = """{"type":"init","session_id":"sess_123","tools":["Bash","Read","Write"]}"""
        val result = parser.parseLine(line)

        assertNotNull(result)
        assertTrue(result is SdkMessageTypes.SdkInitMessage)
        assertEquals("sess_123", (result as SdkMessageTypes.SdkInitMessage).sessionId)
        assertEquals(3, result.tools.size)
    }

    @Test
    fun `parseLine should parse assistant text message`() {
        val line = """{"type":"assistant","message":{"content":[{"type":"text","text":"Hello!"}]}}"""
        val result = parser.parseLine(line)

        assertNotNull(result)
        assertTrue(result is SdkMessageTypes.SdkAssistantMessage)
        assertEquals("Hello!", (result as SdkMessageTypes.SdkAssistantMessage).extractText())
    }

    @Test
    fun `parseLine should parse assistant tool_use message`() {
        val line = """{"type":"assistant","message":{"content":[{"type":"tool_use","id":"tu_1","name":"Read","input":{"file":"test.kt"}}]}}"""
        val result = parser.parseLine(line)

        assertNotNull(result)
        assertTrue(result is SdkMessageTypes.SdkAssistantMessage)
        val toolUses = (result as SdkMessageTypes.SdkAssistantMessage).extractToolUses()
        assertEquals(1, toolUses.size)
        assertEquals("Read", toolUses[0].name)
    }

    @Test
    fun `parseLine should parse result message`() {
        val line = """{"type":"result","subtype":"success","cost_usd":0.003,"duration_ms":1500,"session_id":"sess_123"}"""
        val result = parser.parseLine(line)

        assertNotNull(result)
        assertTrue(result is SdkMessageTypes.SdkResultMessage)
        val r = result as SdkMessageTypes.SdkResultMessage
        assertTrue(r.isSuccess)
        assertEquals(0.003, r.costUsd!!, 0.001)
        assertEquals(1500L, r.durationMs!!)
    }

    @Test
    fun `parseLine should return null for blank line`() {
        assertNull(parser.parseLine(""))
        assertNull(parser.parseLine("   "))
    }

    @Test
    fun `parseLine should handle unknown type`() {
        val line = """{"type":"future_type","data":"something"}"""
        val result = parser.parseLine(line)

        assertNotNull(result)
        assertTrue(result is SdkMessageTypes.SdkUnknownMessage)
    }
}
```

### 12.3 关键风险

| 风险 | 影响 | 缓解策略 |
|------|------|----------|
| CLI未安装 | 插件不可用 | 启动时检测 + 友好提示安装步骤 |
| stream-json协议变更 | 解析失败 | 版本检测 + 兜底为纯文本模式 |
| CLI进程挂起 | 资源泄漏 | 超时机制 + 强制销毁 |
| 大量输出导致内存溢出 | 性能问题 | Flow流式处理 + 背压控制 |
| Windows路径问题 | MCP配置文件路径 | 使用绝对路径 + 测试覆盖 |

---

## 13. 全量文件统计（修正后）

| Phase | 新增文件数 | 累计文件数 | 修正说明 |
|-------|-----------|-----------|----------|
| Phase 1 | 38 | 38 | 不变 |
| Phase 2 | 12 | 50 | 废弃3个Provider，保留9个 |
| **Phase 2.5** | **6** | **56** | **新增SDK集成** |
| Phase 3 | 10 | 66 | ChatOrchestrator需适配 |
| Phase 4 | 6 | 72 | InteractiveRequestEngine需适配 |
| Phase 5 | 6 | 78 | McpServerManager受益于SDK |
| Phase 6 | 8 + 14测试 | 100 | 新增SDK相关测试 |

**后端总计**: ~100个Kotlin文件（含测试），废弃3个Provider
