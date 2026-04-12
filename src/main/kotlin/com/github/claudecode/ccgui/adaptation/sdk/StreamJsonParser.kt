package com.github.claudecode.ccgui.adaptation.sdk

import com.github.claudecode.ccgui.util.JsonUtils
import com.github.claudecode.ccgui.util.logger
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

// ==================== SSE Stream Event Types ====================

/**
 * SSE 流式事件密封类
 *
 * 对应架构文档 5.2 节的 StreamEvent 定义
 * 解析 Claude Code SSE 协议中的所有事件类型
 */
sealed class StreamEvent {
    /**
     * 消息文本增量事件
     */
    data class TextDeltaEvent(
        val messageId: String,
        val delta: String
    ) : StreamEvent()

    /**
     * 消息开始事件
     */
    data class MessageStartEvent(
        val messageId: String,
        val role: String?
    ) : StreamEvent()

    /**
     * 消息结束事件
     */
    data class MessageStopEvent(
        val messageId: String,
        val stopReason: String?
    ) : StreamEvent()

    /**
     * 内容块开始事件
     */
    data class ContentBlockStartEvent(
        val index: Int,
        val type: String
    ) : StreamEvent()

    /**
     * 内容块增量事件
     */
    data class ContentBlockDeltaEvent(
        val index: Int,
        val delta: String
    ) : StreamEvent()

    /**
     * 内容块结束事件
     */
    data class ContentBlockStopEvent(
        val index: Int
    ) : StreamEvent()

    /**
     * 输入 JSON 块事件
     */
    data class InputJsonBlockEvent(
        val messageId: String,
        val json: String
    ) : StreamEvent()

    /**
     * Ping 心跳事件
     */
    data class PingEvent(
        val timestamp: Long
    ) : StreamEvent()

    /**
     * 错误事件
     */
    data class ErrorEvent(
        val message: String
    ) : StreamEvent()

    /**
     * 未知事件（未匹配的事件类型）
     */
    data class UnknownEvent(
        val eventType: String,
        val rawData: String
    ) : StreamEvent()
}

// ==================== SSE Stream Parser ====================

/**
 * SSE 流式协议解析器
 *
 * 对应架构文档 5.2 节的 StreamParser 定义
 * 解析 Claude Code SSE (Server-Sent Events) 格式的流式输出
 *
 * SSE 协议格式:
 * ```
 * event: message_start
 * data: {"type":"message_start","message":{"id":"msg_xxx","role":"assistant"}}
 *
 * event: content_block_delta
 * data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
 * ```
 *
 * 性能要求:
 * - 解析延迟 < 5ms/事件
 * - 支持粘包处理（不完整的 chunk）
 * - 流式推送不阻塞
 */
class StreamParser {

    private val logger = logger<StreamParser>()

    /** SSE 事件缓冲，用于处理粘包和不完整的 chunk */
    private val buffer = StringBuilder()

    companion object {
        /** 事件类型字段 */
        private const val FIELD_TYPE = "type"

        /** 事件行前缀 */
        private const val EVENT_PREFIX = "event:"

        /** 数据行前缀 */
        private const val DATA_PREFIX = "data:"

        /** SSE 消息结束标记 (双换行) */
        private const val EVENT_DELIMITER = "\n\n"

        /** 单换行符 */
        private const val NEWLINE = "\n"

        /** SSE 协议定义的事件类型 */
        private val KNOWN_EVENT_TYPES = setOf(
            "message_start",
            "message_delta",
            "message_stop",
            "content_block_start",
            "content_block_delta",
            "content_block_stop",
            "input_json_block",
            "ping"
        )
    }

    /**
     * 解析 SSE chunk，返回解析出的事件列表
     *
     * @param chunk 从 CLI stdout 读取的文本块
     * @return 解析后的 StreamEvent 列表
     */
    fun parse(chunk: String): List<StreamEvent> {
        buffer.append(chunk)
        val events = mutableListOf<StreamEvent>()

        while (true) {
            // 查找完整事件的结束位置（双换行）
            val eventEnd = buffer.indexOf(EVENT_DELIMITER)
            if (eventEnd == -1) {
                // 没有完整事件，检查是否需要保留不完整的行
                // 如果最后一行是 event: 开头但没有 data:，说明是不完整的
                handleIncompleteBuffer()
                break
            }

            // 提取完整事件数据
            val eventData = buffer.substring(0, eventEnd)
            // 从缓冲区移除已处理的事件（包括结尾的 \n\n）
            buffer.delete(0, eventEnd + EVENT_DELIMITER.length)

            // 解析单个事件
            parseEvent(eventData)?.let { events.add(it) }
        }

        return events
    }

    /**
     * 处理不完整的缓冲区
     * 如果缓冲区以 "event:" 结尾但没有对应的 "data:", 保留部分数据等待下一个 chunk
     */
    private fun handleIncompleteBuffer() {
        // 检查缓冲区是否以 "event:" 开头但没有完整的 data:
        val lastNewlineIndex = buffer.lastIndexOf(NEWLINE)
        if (lastNewlineIndex > 0) {
            val lastLine = buffer.substring(lastNewlineIndex + 1)
            // 如果最后一行是 event: 或 data: 开头的行，说明事件不完整
            if (lastLine.startsWith(EVENT_PREFIX) || lastLine.startsWith(DATA_PREFIX)) {
                // 保留不完整的事件数据，移除已完成的行
                buffer.delete(0, lastNewlineIndex + 1)
            }
        }
    }

    /**
     * 解析单个 SSE 事件
     *
     * @param data 事件数据（不包含结尾的 \n\n）
     * @return 解析后的 StreamEvent，解析失败返回 null
     */
    private fun parseEvent(data: String): StreamEvent? {
        if (data.isBlank()) return null

        try {
            // 解析 event: 和 data: 行
            val lines = data.split(NEWLINE)
            var eventType: String? = null
            var jsonData: String? = null

            for (line in lines) {
                val trimmedLine = line.trim()
                when {
                    trimmedLine.startsWith(EVENT_PREFIX) -> {
                        eventType = trimmedLine.removePrefix(EVENT_PREFIX).trim()
                    }
                    trimmedLine.startsWith(DATA_PREFIX) -> {
                        jsonData = trimmedLine.removePrefix(DATA_PREFIX).trim()
                    }
                }
            }

            // 如果没有 event: 行，尝试从 data: 中解析 type
            if (eventType == null && jsonData != null) {
                eventType = extractTypeFromJson(jsonData)
            }

            if (eventType == null) {
                logger.warn("Failed to extract event type from: ${data.take(200)}")
                return null
            }

            // 根据事件类型解析
            return when (eventType) {
                "message_start" -> parseMessageStart(jsonData)
                "message_delta" -> parseMessageDelta(jsonData)
                "message_stop" -> parseMessageStop(jsonData)
                "content_block_start" -> parseContentBlockStart(jsonData)
                "content_block_delta" -> parseContentBlockDelta(jsonData)
                "content_block_stop" -> parseContentBlockStop(jsonData)
                "input_json_block" -> parseInputJsonBlock(jsonData)
                "ping" -> parsePing(jsonData)
                else -> {
                    // 未知事件类型，尝试解析为通用错误或未知事件
                    logger.debug("Unknown SSE event type: $eventType")
                    StreamEvent.UnknownEvent(eventType, jsonData ?: data)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse SSE event: ${e.message}, data=${data.take(200)}")
            return StreamEvent.ErrorEvent("Parse error: ${e.message}")
        }
    }

    /**
     * 从 JSON 数据中提取 type 字段
     */
    private fun extractTypeFromJson(jsonData: String?): String? {
        if (jsonData == null) return null
        return try {
            val json = JsonUtils.parseObject(jsonData) ?: return null
            json.get(FIELD_TYPE)?.asString
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析 message_start 事件
     * 格式: event: message_start
     *       data: {"type":"message_start","message":{"id":"msg_xxx","role":"assistant"}}
     */
    private fun parseMessageStart(jsonData: String?): StreamEvent? {
        if (jsonData == null) return StreamEvent.ErrorEvent("Missing data for message_start")

        return try {
            val json = JsonUtils.parseObject(jsonData) ?: return StreamEvent.ErrorEvent("Invalid JSON")
            val messageObj = json.getAsJsonObject("message")
            val messageId = messageObj?.get("id")?.asString ?: ""
            val role = messageObj?.get("role")?.asString
            StreamEvent.MessageStartEvent(messageId, role)
        } catch (e: Exception) {
            StreamEvent.ErrorEvent("Failed to parse message_start: ${e.message}")
        }
    }

    /**
     * 解析 message_delta 事件
     * 格式: event: message_delta
     *       data: {"type":"message_delta","messageId":"msg_xxx","delta":{"type":"text_delta","text":"Hello"}}
     */
    private fun parseMessageDelta(jsonData: String?): StreamEvent? {
        if (jsonData == null) return StreamEvent.ErrorEvent("Missing data for message_delta")

        return try {
            val json = JsonUtils.parseObject(jsonData) ?: return StreamEvent.ErrorEvent("Invalid JSON")
            val messageId = json.get("messageId")?.asString ?: ""
            val deltaObj = json.getAsJsonObject("delta")
            val deltaType = deltaObj?.get("type")?.asString

            when (deltaType) {
                "text_delta" -> {
                    val text = deltaObj.get("text")?.asString ?: ""
                    StreamEvent.TextDeltaEvent(messageId, text)
                }
                else -> {
                    // 其他类型的 delta，序列化为字符串
                    val deltaJson = deltaObj?.toString() ?: "{}"
                    StreamEvent.TextDeltaEvent(messageId, deltaJson)
                }
            }
        } catch (e: Exception) {
            StreamEvent.ErrorEvent("Failed to parse message_delta: ${e.message}")
        }
    }

    /**
     * 解析 message_stop 事件
     * 格式: event: message_stop
     *       data: {"type":"message_stop","messageId":"msg_xxx","stopReason":"end_turn"}
     */
    private fun parseMessageStop(jsonData: String?): StreamEvent? {
        if (jsonData == null) return StreamEvent.ErrorEvent("Missing data for message_stop")

        return try {
            val json = JsonUtils.parseObject(jsonData) ?: return StreamEvent.ErrorEvent("Invalid JSON")
            val messageId = json.get("messageId")?.asString ?: ""
            val stopReason = json.get("stopReason")?.asString
            StreamEvent.MessageStopEvent(messageId, stopReason)
        } catch (e: Exception) {
            StreamEvent.ErrorEvent("Failed to parse message_stop: ${e.message}")
        }
    }

    /**
     * 解析 content_block_start 事件
     * 格式: event: content_block_start
     *       data: {"type":"content_block_start","index":0,"content_block":{"type":"text"}}
     */
    private fun parseContentBlockStart(jsonData: String?): StreamEvent? {
        if (jsonData == null) return StreamEvent.ErrorEvent("Missing data for content_block_start")

        return try {
            val json = JsonUtils.parseObject(jsonData) ?: return StreamEvent.ErrorEvent("Invalid JSON")
            val index = json.get("index")?.asInt ?: 0
            val contentBlockObj = json.getAsJsonObject("content_block")
            val type = contentBlockObj?.get("type")?.asString ?: "unknown"
            StreamEvent.ContentBlockStartEvent(index, type)
        } catch (e: Exception) {
            StreamEvent.ErrorEvent("Failed to parse content_block_start: ${e.message}")
        }
    }

    /**
     * 解析 content_block_delta 事件
     * 格式: event: content_block_delta
     *       data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
     */
    private fun parseContentBlockDelta(jsonData: String?): StreamEvent? {
        if (jsonData == null) return StreamEvent.ErrorEvent("Missing data for content_block_delta")

        return try {
            val json = JsonUtils.parseObject(jsonData) ?: return StreamEvent.ErrorEvent("Invalid JSON")
            val index = json.get("index")?.asInt ?: 0
            val deltaObj = json.getAsJsonObject("delta")
            val deltaType = deltaObj?.get("type")?.asString

            val deltaText = when (deltaType) {
                "text_delta" -> deltaObj.get("text")?.asString ?: ""
                else -> deltaObj?.toString() ?: ""
            }

            StreamEvent.ContentBlockDeltaEvent(index, deltaText)
        } catch (e: Exception) {
            StreamEvent.ErrorEvent("Failed to parse content_block_delta: ${e.message}")
        }
    }

    /**
     * 解析 content_block_stop 事件
     * 格式: event: content_block_stop
     *       data: {"type":"content_block_stop","index":0}
     */
    private fun parseContentBlockStop(jsonData: String?): StreamEvent? {
        if (jsonData == null) return StreamEvent.ErrorEvent("Missing data for content_block_stop")

        return try {
            val json = JsonUtils.parseObject(jsonData) ?: return StreamEvent.ErrorEvent("Invalid JSON")
            val index = json.get("index")?.asInt ?: 0
            StreamEvent.ContentBlockStopEvent(index)
        } catch (e: Exception) {
            StreamEvent.ErrorEvent("Failed to parse content_block_stop: ${e.message}")
        }
    }

    /**
     * 解析 input_json_block 事件
     * 格式: event: input_json_block
     *       data: {"type":"input_json_block","messageId":"msg_xxx","json":"..."}
     */
    private fun parseInputJsonBlock(jsonData: String?): StreamEvent? {
        if (jsonData == null) return StreamEvent.ErrorEvent("Missing data for input_json_block")

        return try {
            val json = JsonUtils.parseObject(jsonData) ?: return StreamEvent.ErrorEvent("Invalid JSON")
            val messageId = json.get("messageId")?.asString ?: ""
            val jsonContent = json.get("json")?.asString ?: "{}"
            StreamEvent.InputJsonBlockEvent(messageId, jsonContent)
        } catch (e: Exception) {
            StreamEvent.ErrorEvent("Failed to parse input_json_block: ${e.message}")
        }
    }

    /**
     * 解析 ping 事件
     * 格式: event: ping
     *       data: {"type":"ping","timestamp":1234567890}
     */
    private fun parsePing(jsonData: String?): StreamEvent? {
        val timestamp = try {
            if (jsonData != null) {
                val json = JsonUtils.parseObject(jsonData)
                json?.get("timestamp")?.asLong ?: System.currentTimeMillis()
            } else {
                System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        return StreamEvent.PingEvent(timestamp)
    }

    /**
     * 清空缓冲区
     * 用于流式输出完成或错误后重置状态
     */
    fun clearBuffer() {
        buffer.clear()
    }

    /**
     * 获取当前缓冲区大小
     */
    fun getBufferSize(): Int = buffer.length

    /**
     * 将 Flow<String> 转换为 Flow<StreamEvent>
     * 用于流式处理 CLI 输出
     */
    fun parseFlow(lines: Flow<String>): Flow<StreamEvent> {
        return lines.transform { line ->
            val events = parse(line)
            events.forEach { emit(it) }
        }
    }
}

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
 */
class StreamJsonParser {

    private val logger = logger<StreamJsonParser>()

    /**
     * 解析单行JSON为SDK消息
     *
     * @param line 从CLI stdout读取的一行文本
     * @return 解析后的SdkMessage，空行或解析失败返回null
     */
    fun parseLine(line: String): SdkMessageTypes.SdkMessage? {
        return parseLine(line, null)
    }

    /**
     * 解析单行JSON为SDK消息（支持权限请求回调）
     *
     * @param line 从CLI stdout读取的一行文本
     * @param permissionCallback 可选的权限请求回调，返回 true=允许, false=拒绝
     * @return 解析后的SdkMessage，空行或解析失败返回null
     */
    fun parseLine(
        line: String,
        permissionCallback: ((toolName: String, input: Map<String, Any>) -> Boolean)?
    ): SdkMessageTypes.SdkMessage? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        return try {
            val json = JsonUtils.parseObject(trimmed) ?: return handleUnknown(trimmed, "parse_error")
            val type = json.get("type")?.asString ?: return handleUnknown(trimmed, "missing_type")

            when (type) {
                "init" -> parseInitMessage(json, trimmed)
                "user" -> parseUserMessage(json, trimmed)
                "assistant" -> parseAssistantMessage(json, trimmed)
                "result" -> parseResultMessage(json, trimmed)
                "system" -> parseSystemMessage(json, trimmed, permissionCallback)
                else -> {
                    logger.warn("Unknown SDK message type: $type")
                    SdkMessageTypes.SdkUnknownMessage(type, trimmed)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse SDK line: ${e.message}, line=${trimmed.take(200)}")
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
    private fun parseSystemMessage(
        json: JsonObject,
        raw: String,
        permissionCallback: ((toolName: String, input: Map<String, Any>) -> Boolean)?
    ): SdkMessageTypes.SdkMessage {
        val subtype = json.get("subtype")?.asString
        if (subtype == "permission_request" && permissionCallback != null) {
            val toolName = json.get("tool")?.asString ?: "unknown"
            val inputMap = mutableMapOf<String, Any>()
            json.getAsJsonObject("input")?.entrySet()?.forEach { (k, v) ->
                inputMap[k] = v.toString()
            }
            logger.info("SDK permission request for tool: $toolName")
            val allowed = permissionCallback(toolName, inputMap)
            logger.info("Permission callback result: allowed=$allowed")
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
                logger.warn("Failed to parse content block: ${e.message}")
                null
            }
        }
    }

    private fun parseToolsArray(array: JsonArray?): List<String> {
        if (array == null) return emptyList()
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
        logger.debug("Unhandled SDK line ($reason): ${raw.take(200)}")
        return SdkMessageTypes.SdkUnknownMessage("unknown", raw)
    }
}
