package com.github.claudecode.ccgui.application.multimodal

import com.github.claudecode.ccgui.model.message.ContentPart
import com.github.claudecode.ccgui.model.message.ChatMessage
import com.github.claudecode.ccgui.model.message.MessageRole
import com.github.claudecode.ccgui.util.IdGenerator
import com.github.claudecode.ccgui.util.JsonUtils
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64

/**
 * 多模态输入处理器
 *
 * 负责处理多种类型的输入内容：
 * - 文本输入
 * - 图片上传（拖拽、粘贴、文件选择）
 * - 文件引用
 * - 代码片段
 * - 语音输入（未来扩展）
 *
 * @param project IntelliJ项目实例
 */
@Service(Service.Level.PROJECT)
class MultimodalInputHandler(private val project: Project) {

    private val log = logger<MultimodalInputHandler>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 处理配置
     */
    data class ProcessingConfig(
        val maxImageSize: Long = 20 * 1024 * 1024,  // 20MB
        val maxFileSize: Long = 100 * 1024 * 1024,    // 100MB
        val maxTotalSize: Long = 200 * 1024 * 1024,   // 200MB
        val supportedImageFormats: List<String> = listOf("png", "jpg", "jpeg", "gif", "webp"),
        val supportedTextFormats: List<String> = listOf("txt", "md", "json", "xml", "yaml", "toml")
    )

    /**
     * 处理结果
     */
    data class ProcessingResult(
        val contentParts: List<ContentPart>,
        val totalSize: Long,
        val warnings: List<String> = emptyList(),
        val errors: List<String> = emptyList()
    )

    // ==================== 核心API ====================

    /**
     * 处理文本输入
     *
     * @param text 文本内容
     * @param language 代码语言（可选）
     * @return ContentPart
     */
    fun processText(text: String, language: String? = null): ContentPart.Text {
        return ContentPart.Text(text.trim(), language)
    }

    /**
     * 处理图片输入
     *
     * @param filePath 图片文件路径
     * @param config 处理配置
     * @return 处理结果
     */
    suspend fun processImage(filePath: String, config: ProcessingConfig = ProcessingConfig()): ProcessingResult {
        return withContext(Dispatchers.IO) {
            val warnings = mutableListOf<String>()
            val errors = mutableListOf<String>()

            try {
                val file = File(filePath)
                if (!file.exists()) {
                    errors.add("File not found: $filePath")
                    return@withContext ProcessingResult(emptyList(), 0, warnings, errors)
                }

                // 检查文件大小
                val fileSize = file.length()
                if (fileSize > config.maxImageSize) {
                    errors.add("Image too large: ${fileSize} bytes (max: ${config.maxImageSize})")
                    return@withContext ProcessingResult(emptyList(), fileSize, warnings, errors)
                }

                // 检查文件格式
                val extension = file.extension.lowercase()
                if (extension !in config.supportedImageFormats) {
                    warnings.add("Unsupported image format: $extension")
                    // 尝试处理
                }

                // 读取并编码图片
                val imageBytes = file.readBytes()
                val base64Data = Base64.getEncoder().encodeToString(imageBytes)
                val mimeType = "image/$extension"

                // 获取图片尺寸（如果可能）
                val (width, height) = getImageDimensions(file)

                val contentPart = ContentPart.Image(
                    mimeType = mimeType,
                    data = base64Data,
                    width = width,
                    height = height
                )

                ProcessingResult(
                    contentParts = listOf(contentPart),
                    totalSize = fileSize,
                    warnings = warnings,
                    errors = errors
                )
            } catch (e: Exception) {
                log.error("Failed to process image: $filePath", e)
                errors.add("Failed to process image: ${e.message}")
                ProcessingResult(emptyList(), 0, warnings, errors)
            }
        }
    }

    /**
     * 处理Base64图片数据
     *
     * @param base64Data Base64编码的图片数据
     * @param mimeType MIME类型
     * @param config 处理配置
     * @return 处理结果
     */
    fun processBase64Image(
        base64Data: String,
        mimeType: String = "image/png",
        config: ProcessingConfig = ProcessingConfig()
    ): ProcessingResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        try {
            // 清理Base64数据
            val cleanedData = base64Data.removePrefix("data:image/$mimeType;base64,")

            // 解码获取大小
            val imageBytes = Base64.getDecoder().decode(cleanedData)
            val dataSize = imageBytes.size.toLong()

            if (dataSize > config.maxImageSize) {
                errors.add("Image too large: $dataSize bytes (max: ${config.maxImageSize})")
                return ProcessingResult(emptyList(), dataSize, warnings, errors)
            }

            val contentPart = ContentPart.Image(
                mimeType = mimeType,
                data = cleanedData
            )

            return ProcessingResult(
                contentParts = listOf(contentPart),
                totalSize = dataSize,
                warnings = warnings,
                errors = errors
            )
        } catch (e: Exception) {
            log.error("Failed to process base64 image", e)
            errors.add("Failed to process base64 image: ${e.message}")
            return ProcessingResult(emptyList(), 0, warnings, errors)
        }
    }

    /**
     * 处理文件输入
     *
     * @param filePath 文件路径
     * @param maxSize 最大文件大小
     * @param config 处理配置
     * @return 处理结果
     */
    suspend fun processFile(
        filePath: String,
        maxSize: Long = 10 * 1024 * 1024,  // 默认10MB
        config: ProcessingConfig = ProcessingConfig()
    ): ProcessingResult {
        return withContext(Dispatchers.IO) {
            val warnings = mutableListOf<String>()
            val errors = mutableListOf<String>()

            try {
                val file = File(filePath)
                if (!file.exists()) {
                    errors.add("File not found: $filePath")
                    return@withContext ProcessingResult(emptyList(), 0, warnings, errors)
                }

                val fileSize = file.length()
                if (fileSize > maxSize) {
                    errors.add("File too large: $fileSize bytes (max: $maxSize)")
                    return@withContext ProcessingResult(emptyList(), fileSize, warnings, errors)
                }

                // 读取文件内容
                val content = file.readText()
                val extension = file.extension.lowercase()

                // 判断是否为文本文件
                val isTextFile = extension in config.supportedTextFormats ||
                                  isLikelyTextFile(content)

                val mimeType = when {
                    extension == "json" -> "application/json"
                    extension == "xml" -> "application/xml"
                    extension == "yaml" || extension == "yml" -> "application/x-yaml"
                    isTextFile -> "text/plain"
                    else -> "application/octet-stream"
                }

                val contentPart = ContentPart.File(
                    name = file.name,
                    content = content.take(500000),  // 限制内容长度
                    mimeType = mimeType,
                    size = fileSize
                )

                if (content.length > 500000) {
                    warnings.add("File content truncated (${content.length - 500000} chars)")
                }

                ProcessingResult(
                    contentParts = listOf(contentPart),
                    totalSize = fileSize,
                    warnings = warnings,
                    errors = errors
                )
            } catch (e: Exception) {
                log.error("Failed to process file: $filePath", e)
                errors.add("Failed to process file: ${e.message}")
                ProcessingResult(emptyList(), 0, warnings, errors)
            }
        }
    }

    /**
     * 处理代码片段
     *
     * @param code 代码内容
     * @param language 编程语言
     * @param filePath 文件路径（可选）
     * @return ContentPart
     */
    fun processCodeSnippet(code: String, language: String, filePath: String? = null): ContentPart.Text {
        return ContentPart.Text(
            text = code.trim(),
            language = language
        )
    }

    /**
     * 处理混合输入（文本 + 附件）
     *
     * @param text 文本内容
     * @param attachments 附件列表
     * @param config 处理配置
     * @return 处理结果
     */
    suspend fun processMixedInput(
        text: String,
        attachments: List<Attachment>,
        config: ProcessingConfig = ProcessingConfig()
    ): ProcessingResult {
        return withContext(Dispatchers.IO) {
            val contentParts = mutableListOf<ContentPart>()
            val warnings = mutableListOf<String>()
            val errors = mutableListOf<String>()
            var totalSize = 0L

            // 添加文本
            contentParts.add(processText(text))

            // 处理附件
            attachments.forEach { attachment ->
                when (attachment.type) {
                    AttachmentType.IMAGE_FILE -> {
                        val path = attachment.path
                        if (path != null) {
                            val result = processImage(path, config)
                            contentParts.addAll(result.contentParts)
                            totalSize += result.totalSize
                            warnings.addAll(result.warnings)
                            errors.addAll(result.errors)
                        } else {
                            errors.add("Image attachment has no path")
                        }
                    }
                    AttachmentType.TEXT_FILE -> {
                        val path = attachment.path
                        if (path != null) {
                            val result = processFile(path, config.maxFileSize, config)
                            contentParts.addAll(result.contentParts)
                            totalSize += result.totalSize
                            warnings.addAll(result.warnings)
                            errors.addAll(result.errors)
                        } else {
                            errors.add("Text file attachment has no path")
                        }
                    }
                    AttachmentType.CODE_SNIPPET -> {
                        contentParts.add(processCodeSnippet(
                            attachment.content ?: "",
                            attachment.metadata["language"] as? String ?: "text"
                        ))
                    }
                }

                // 检查总大小限制
                if (totalSize > config.maxTotalSize) {
                    warnings.add("Total attachment size exceeds limit: $totalSize (max: ${config.maxTotalSize})")
                }
            }

            ProcessingResult(
                contentParts = contentParts,
                totalSize = totalSize,
                warnings = warnings,
                errors = errors
            )
        }
    }

    /**
     * 创建包含多模态内容的ChatMessage
     *
     * @param text 主要文本内容
     * @param contentParts 附加内容部分
     * @return ChatMessage
     */
    fun createMultimodalMessage(
        text: String,
        contentParts: List<ContentPart>
    ): ChatMessage {
        val allParts = mutableListOf<ContentPart>()

        // 添加文本部分
        allParts.add(ContentPart.Text(text))

        // 添加其他部分
        allParts.addAll(contentParts)

        return ChatMessage(
            role = MessageRole.USER,
            content = text,
            attachments = allParts
        )
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取图片尺寸
     */
    private fun getImageDimensions(file: File): Pair<Int?, Int?> {
        return try {
            // 简化版：只检查常见图片格式
            // 实际应该使用ImageIO读取
            when (file.extension.lowercase()) {
                "png", "jpg", "jpeg" -> {
                    // TODO: 使用ImageIO读取实际尺寸
                    null to null
                }
                else -> null to null
            }
        } catch (e: Exception) {
            null to null
        }
    }

    /**
     * 判断是否为文本文件
     */
    private fun isLikelyTextFile(content: String): Boolean {
        // 简单启发式：检查是否包含过多非文本字符
        val nonPrintable = content.count { it.code < 32 || it.code > 126 }
        val ratio = nonPrintable.toDouble() / content.length
        return ratio < 0.1  // 少于10%非可打印字符
    }

    /**
     * 从剪贴板内容创建ContentPart
     */
    fun processClipboardContent(
        content: String,
        mimeType: String? = null
    ): ContentPart? {
        return when {
            // 检测是否为图片数据
            content.startsWith("data:image/") && content.contains(";base64,") -> {
                val parts = content.split(";", limit = 2)
                if (parts.size >= 2) {
                    val imageData = parts[1]
                    val imageType = parts[0].removePrefix("data:image/")
                    val result = processBase64Image(imageData, imageType)
                    if (result.errors.isEmpty()) {
                        result.contentParts.firstOrNull()
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            // 检测是否为代码片段
            isLikelyCodeSnippet(content) -> {
                val language = detectLanguage(content)
                ContentPart.Text(content, language)
            }
            // 普通文本
            else -> {
                ContentPart.Text(content)
            }
        }
    }

    /**
     * 判断是否为代码片段
     */
    private fun isLikelyCodeSnippet(content: String): Boolean {
        val lines = content.lines()
        if (lines.size < 3) return false

        // 检查是否包含代码特征
        val hasCodeIndicators = content.contains("{") && content.contains("}") ||
                                content.contains("function") || content.contains("def ") ||
                                content.contains("class ") || content.contains("import ")

        return hasCodeIndicators
    }

    /**
     * 检测编程语言
     */
    private fun detectLanguage(code: String): String {
        return when {
            code.contains("fun ") || code.contains("val ") || code.contains("var ") -> "kotlin"
            code.contains("def ") || code.contains("import ") || code.contains("class ") -> "python"
            code.contains("function ") || code.contains("const ") || code.contains("let ") -> "javascript"
            code.contains("public class") || code.contains("interface ") -> "java"
            else -> "text"
        }
    }

    companion object {
        fun getInstance(project: Project): MultimodalInputHandler =
            project.getService(MultimodalInputHandler::class.java)
    }
}

/**
 * 附件类型
 */
enum class AttachmentType {
    IMAGE_FILE,
    TEXT_FILE,
    CODE_SNIPPET
}

/**
 * 附件信息
 */
data class Attachment(
    val type: AttachmentType,
    val path: String? = null,
    val content: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)