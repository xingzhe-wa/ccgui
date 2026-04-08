package com.github.xingzhewa.ccgui.adaptation.sdk

import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

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
