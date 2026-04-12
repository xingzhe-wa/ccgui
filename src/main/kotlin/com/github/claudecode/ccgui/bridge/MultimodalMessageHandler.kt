package com.github.claudecode.ccgui.bridge

import com.github.claudecode.ccgui.application.multimodal.MultimodalInputHandler
import com.github.claudecode.ccgui.util.logger
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import java.util.Base64

/**
 * 多模态消息处理器
 *
 * 负责处理前端发送的多模态消息：
 * - 附件类型验证（图片、文本、代码）
 * - 附件数量验证（最多10个）
 * - Base64 解码支持
 * - 图片压缩阈值检查（>500KB 标记需压缩）
 *
 * @param project IntelliJ 项目实例
 */
class MultimodalMessageHandler(private val project: Project) {

    private val log = logger<MultimodalMessageHandler>()
    private val multimodalInputHandler: MultimodalInputHandler by lazy {
        MultimodalInputHandler.getInstance(project)
    }

    companion object {
        /** 单条消息最大附件数量 */
        const val MAX_ATTACHMENTS = 10

        /** 图片压缩阈值：500KB */
        const val IMAGE_COMPRESSION_THRESHOLD = 500 * 1024

        /** 支持的图片类型 */
        val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

        /** 支持的文本类型 */
        val SUPPORTED_TEXT_TYPES = setOf(
            "text/plain",
            "text/html",
            "text/css",
            "text/javascript",
            "application/json",
            "application/xml",
            "text/markdown"
        )

        /** 支持的代码文件扩展名 */
        val SUPPORTED_CODE_EXTENSIONS = setOf(
            "kt", "java", "js", "ts", "tsx", "jsx", "py", "rb", "go", "rs",
            "cpp", "c", "h", "hpp", "cs", "swift", "kt", "scala", "php",
            "html", "css", "scss", "sass", "less",
            "json", "xml", "yaml", "yml", "toml", "ini", "conf", "cfg",
            "md", "txt", "log", "sql", "sh", "bash", "zsh", "ps1", "bat", "cmd"
        )

        @Volatile
        private var instance: MultimodalMessageHandler? = null

        fun getInstance(project: Project): MultimodalMessageHandler {
            return instance ?: synchronized(this) {
                instance ?: MultimodalMessageHandler(project).also { instance = it }
            }
        }
    }

    /**
     * 处理结果
     */
    data class ProcessingResult(
        val success: Boolean,
        val formattedContent: String,
        val attachmentWarnings: List<String> = emptyList(),
        val attachmentErrors: List<String> = emptyList(),
        val needsCompression: Boolean = false
    )

    /**
     * 验证并处理多模态消息
     *
     * @param sessionId 会话ID
     * @param content 文本内容
     * @param attachmentsJson 附件 JSON 数组
     * @return 处理结果
     */
    fun processMultimodalMessage(
        sessionId: String,
        content: String,
        attachmentsJson: JsonArray?
    ): ProcessingResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var needsCompression = false

        // 验证附件数量
        val attachments = attachmentsJson?.takeIf { it.isJsonArray }?.asJsonArray
        val attachmentCount = attachments?.size() ?: 0

        if (attachmentCount > MAX_ATTACHMENTS) {
            errors.add("Attachment count exceeds limit: $attachmentCount (max: $MAX_ATTACHMENTS)")
            return ProcessingResult(
                success = false,
                formattedContent = content,
                attachmentErrors = errors
            )
        }

        // 构建带附件的完整内容
        val attachmentText = buildString {
            if (attachmentCount > 0) {
                append("\n\n[Attachments]\n")

                for (i in 0 until attachmentCount) {
                    val attachment = attachments!!.get(i).asJsonObject
                    val type = attachment.get("type")?.asString ?: continue

                    when (type) {
                        "image" -> {
                            val mimeType = attachment.get("mimeType")?.asString ?: "image/png"
                            val data = attachment.get("data")?.asString ?: ""

                            // 验证图片类型
                            if (mimeType !in SUPPORTED_IMAGE_TYPES) {
                                warnings.add("Unsupported image type: $mimeType at index $i")
                            }

                            // 检查是否需要压缩
                            val dataSizeBytes = data.length * 3 / 4 // Base64 编码后大小约为原始的 4/3
                            if (dataSizeBytes > IMAGE_COMPRESSION_THRESHOLD) {
                                needsCompression = true
                                warnings.add("Image ${i + 1} (${dataSizeBytes / 1024}KB) exceeds 500KB compression threshold")
                            }

                            append("[Image #$i: data:$mimeType;base64,$data]\n")
                            append("<!-- Image $i (${dataSizeBytes / 1024}KB) -->\n")
                        }
                        "file" -> {
                            val name = attachment.get("name")?.asString ?: "file"
                            val fileContent = attachment.get("content")?.asString ?: ""
                            val fileMimeType = attachment.get("mimeType")?.asString ?: ""

                            // 验证文件类型
                            val extension = name.substringAfterLast('.', "").lowercase()
                            val isSupportedText = fileMimeType in SUPPORTED_TEXT_TYPES
                            val isSupportedCode = extension in SUPPORTED_CODE_EXTENSIONS

                            if (!isSupportedText && !isSupportedCode && !fileMimeType.startsWith("text/")) {
                                warnings.add("File '$name' may not be a supported text/code file (type: $fileMimeType)")
                            }

                            append("[$name]\n$fileContent\n[/$name]\n")
                        }
                        else -> {
                            warnings.add("Unknown attachment type '$type' at index $i, skipping")
                        }
                    }
                }

                append("[/Attachments]\n")
            }
        }

        val fullContent = content + attachmentText

        // 验证总内容大小（Claude CLI 输入限制约 200K tokens）
        val estimatedTokens = fullContent.length / 4
        if (estimatedTokens > 150000) {
            errors.add("Total content too large (~$estimatedTokens tokens), may exceed Claude CLI limits")
        }

        return ProcessingResult(
            success = errors.isEmpty(),
            formattedContent = fullContent,
            attachmentWarnings = warnings,
            attachmentErrors = errors,
            needsCompression = needsCompression
        )
    }

    /**
     * 解码 Base64 图片数据
     *
     * @param base64Data Base64 编码的图片数据
     * @param mimeType MIME 类型
     * @return 解码后的字节数组，null 表示解码失败
     */
    fun decodeBase64Image(base64Data: String): ByteArray? {
        return try {
            // 清理可能的 data URI 前缀
            val cleanedData = base64Data
                .replaceFirst("^data:image/[^;]+;base64,".toRegex(), "")

            Base64.getDecoder().decode(cleanedData)
        } catch (e: Exception) {
            log.error("Failed to decode base64 image data", e)
            null
        }
    }

    /**
     * 验证单个附件
     *
     * @param attachmentJson 附件 JSON 对象
     * @return 验证错误列表，空列表表示验证通过
     */
    fun validateAttachment(attachmentJson: JsonObject): List<String> {
        val errors = mutableListOf<String>()

        val type = attachmentJson.get("type")?.asString
        if (type == null) {
            errors.add("Attachment missing 'type' field")
            return errors
        }

        when (type) {
            "image" -> {
                val mimeType = attachmentJson.get("mimeType")?.asString
                if (mimeType == null) {
                    errors.add("Image attachment missing 'mimeType'")
                } else if (mimeType !in SUPPORTED_IMAGE_TYPES) {
                    errors.add("Unsupported image type: $mimeType")
                }

                val data = attachmentJson.get("data")?.asString
                if (data.isNullOrBlank()) {
                    errors.add("Image attachment missing 'data' field")
                }
            }
            "file" -> {
                val name = attachmentJson.get("name")?.asString
                if (name.isNullOrBlank()) {
                    errors.add("File attachment missing 'name' field")
                }

                val content = attachmentJson.get("content")?.asString
                if (content.isNullOrBlank()) {
                    errors.add("File attachment missing 'content' field")
                }
            }
            else -> {
                errors.add("Unknown attachment type: $type")
            }
        }

        return errors
    }

    /**
     * 从附件数组中提取图片附件
     *
     * @param attachmentsJson 附件 JSON 数组
     * @return 图片附件列表
     */
    fun extractImages(attachmentsJson: JsonArray?): List<Pair<Int, JsonObject>> {
        if (attachmentsJson == null) return emptyList()

        val images = mutableListOf<Pair<Int, JsonObject>>()
        for (i in 0 until attachmentsJson.size()) {
            val attachment = attachmentsJson.get(i).asJsonObject
            if (attachment.get("type")?.asString == "image") {
                images.add(i to attachment)
            }
        }
        return images
    }

    /**
     * 检查是否所有附件都有效
     *
     * @param attachmentsJson 附件 JSON 数组
     * @return 验证结果
     */
    fun validateAllAttachments(attachmentsJson: JsonArray?): Pair<Boolean, List<String>> {
        if (attachmentsJson == null || attachmentsJson.size() == 0) {
            return true to emptyList()
        }

        val allErrors = mutableListOf<String>()

        for (i in 0 until attachmentsJson.size()) {
            val attachment = attachmentsJson.get(i).asJsonObject
            val errors = validateAttachment(attachment)
            errors.forEach { allErrors.add("Attachment[$i]: $it") }
        }

        return allErrors.isEmpty() to allErrors
    }
}
