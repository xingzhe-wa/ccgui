package com.github.xingzhewa.ccgui.model.message

import com.google.gson.JsonObject

/**
 * 内容部分类型
 *
 * 支持多种内容格式：文本、图片、文件
 * 使用 sealed class 实现 discriminated union
 */
sealed class ContentPart {

    /**
     * 获取内容类型标识
     */
    abstract val type: String

    /**
     * 文本内容
     */
    data class Text(
        val text: String,
        val language: String? = null  // 代码语言标识
    ) : ContentPart() {
        override val type: String = "text"
    }

    /**
     * 图片内容
     */
    data class Image(
        val mimeType: String,
        val data: String,  // Base64 编码
        val width: Int? = null,
        val height: Int? = null
    ) : ContentPart() {
        override val type: String = "image"
    }

    /**
     * 文件内容
     */
    data class File(
        val name: String,
        val content: String,
        val mimeType: String,
        val size: Long? = null
    ) : ContentPart() {
        override val type: String = "file"
    }

    companion object {
        /**
         * 从 JSON 反序列化
         */
        fun fromJson(json: JsonObject): ContentPart? {
            return try {
                when (json.get("type")?.asString) {
                    "text" -> Text(
                        text = json.get("text")?.asString ?: "",
                        language = json.get("language")?.asString
                    )
                    "image" -> Image(
                        mimeType = json.get("mimeType")?.asString ?: "image/png",
                        data = json.get("data")?.asString ?: "",
                        width = json.get("width")?.asInt,
                        height = json.get("height")?.asInt
                    )
                    "file" -> File(
                        name = json.get("name")?.asString ?: "",
                        content = json.get("content")?.asString ?: "",
                        mimeType = json.get("mimeType")?.asString ?: "text/plain",
                        size = json.get("size")?.asLong
                    )
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 序列化为 JSON
     */
    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("type", type)
            when (this@ContentPart) {
                is Text -> {
                    addProperty("text", text)
                    language?.let { addProperty("language", it) }
                }
                is Image -> {
                    addProperty("mimeType", mimeType)
                    addProperty("data", data)
                    width?.let { addProperty("width", it) }
                    height?.let { addProperty("height", it) }
                }
                is File -> {
                    addProperty("name", name)
                    addProperty("content", content)
                    addProperty("mimeType", mimeType)
                    size?.let { addProperty("size", it) }
                }
            }
        }
    }
}
