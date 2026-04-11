package com.github.xingzhewa.ccgui.infrastructure.storage

import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * 消息内容存储服务
 *
 * 管理大消息内容的文件存储，当消息内容超过阈值时自动使用文件存储
 * 轻量化设计：使用项目本地目录，不依赖外部存储
 */
@Service(Service.Level.PROJECT)
class MessageContentStorage(private val project: Project) {

    private val log = logger<MessageContentStorage>()

    /** 内容缓存（内存中保留最近访问的内容） */
    private val contentCache = ConcurrentHashMap<String, String>()

    /** 内容长度阈值（字符数），超过此长度使用文件存储 */
    private val CONTENT_LENGTH_THRESHOLD = 5000

    /** 最大缓存条目数 */
    private val MAX_CACHE_SIZE = 100

    companion object {
        /** 内容存储目录 */
        private const val CONTENT_DIR = "message-content"

        /** 文件引用前缀 */
        private const val FILE_REF_PREFIX = "file://"

        /**
         * 获取消息内容存储服务实例
         */
        fun getInstance(project: Project): MessageContentStorage =
            project.getService(MessageContentStorage::class.java)

        /**
         * 判断是否为文件引用
         */
        fun isFileReference(content: String): Boolean {
            return content.startsWith(FILE_REF_PREFIX)
        }

        /**
         * 从文件引用中提取文件路径
         */
        fun extractFilePath(fileRef: String): String? {
            return if (isFileReference(fileRef)) {
                fileRef.substring(FILE_REF_PREFIX.length)
            } else {
                null
            }
        }
    }

    /**
     * 存储消息内容
     *
     * 根据内容长度自动选择存储策略：
     * - 小内容（< 阈值）：直接返回内容
     * - 大内容（>= 阈值）：存储到文件，返回文件引用
     *
     * @param messageId 消息 ID
     * @param content 消息内容
     * @return 存储后的内容（原内容或文件引用）
     */
    suspend fun storeContent(messageId: String, content: String): String = withContext(Dispatchers.IO) {
        if (content.length < CONTENT_LENGTH_THRESHOLD) {
            // 小内容直接返回
            log.debug("Message $messageId: small content (${content.length} chars), keeping in memory")
            return@withContext content
        }

        // 大内容存储到文件
        try {
            val contentDir = getContentDir()
            val fileName = "$messageId.txt"
            val filePath = contentDir.resolve(fileName)

            // 写入文件
            Files.write(filePath, content.toByteArray(StandardCharsets.UTF_8))

            // 添加到缓存
            addToCache(messageId, content)

            val fileRef = "$FILE_REF_PREFIX${filePath.toAbsolutePath()}"
            log.info("Message $messageId: large content (${content.length} chars) stored to file: $filePath")
            return@withContext fileRef
        } catch (e: Exception) {
            log.error("Failed to store message content to file: $messageId", e)
            // 降级：返回原内容
            return@withContext content
        }
    }

    /**
     * 获取消息内容
     *
     * @param content 存储的内容（可能是文件引用）
     * @param messageId 消息 ID（用于缓存查找）
     * @return 实际消息内容
     */
    suspend fun getContent(content: String, messageId: String): String = withContext(Dispatchers.IO) {
        // 检查是否为文件引用
        if (!isFileReference(content)) {
            // 直接内容
            return@withContext content
        }

        // 先检查缓存
        contentCache[messageId]?.let {
            log.debug("Message $messageId: content found in cache")
            return@withContext it
        }

        // 从文件读取
        try {
            val filePath = extractFilePath(content)
            if (filePath != null) {
                val file = File(filePath)
                if (file.exists()) {
                    val fileContent = file.readText(StandardCharsets.UTF_8)
                    // 添加到缓存
                    addToCache(messageId, fileContent)
                    log.debug("Message $messageId: loaded from file (${fileContent.length} chars)")
                    return@withContext fileContent
                } else {
                    log.warn("Message $messageId: file not found: $filePath")
                    return@withContext ""
                }
            } else {
                log.warn("Message $messageId: invalid file reference")
                return@withContext ""
            }
        } catch (e: Exception) {
            log.error("Failed to read message content from file: $messageId", e)
            return@withContext ""
        }
    }

    /**
     * 删除消息内容
     *
     * @param content 存储的内容（可能是文件引用）
     * @param messageId 消息 ID
     */
    suspend fun deleteContent(content: String, messageId: String) = withContext(Dispatchers.IO) {
        // 从缓存中移除
        contentCache.remove(messageId)

        // 如果是文件引用，删除文件
        if (isFileReference(content)) {
            try {
                val filePath = extractFilePath(content)
                if (filePath != null) {
                    val file = File(filePath)
                    if (file.exists()) {
                        file.delete()
                        log.debug("Message $messageId: deleted file: $filePath")
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to delete message content file: $messageId", e)
            }
        }
    }

    /**
     * 获取内容存储目录
     */
    private fun getContentDir(): Path {
        val systemDir = Paths.get(project.basePath, ".idea", "ccgui", CONTENT_DIR)
        Files.createDirectories(systemDir)
        return systemDir
    }

    /**
     * 添加到缓存
     */
    private fun addToCache(messageId: String, content: String) {
        // 如果缓存已满，移除最旧的条目
        if (contentCache.size >= MAX_CACHE_SIZE) {
            val oldestKey = contentCache.keys.first()
            contentCache.remove(oldestKey)
        }
        contentCache[messageId] = content
    }

    /**
     * 清空缓存
     */
    fun clearCache() {
        contentCache.clear()
        log.debug("Message content cache cleared")
    }

    /**
     * 清理所有存储文件
     */
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        try {
            val contentDir = getContentDir()
            if (Files.exists(contentDir)) {
                Files.walk(contentDir)
                    .filter { it != contentDir }
                    .forEach { Files.deleteIfExists(it) }
                log.info("Message content storage cleaned up")
            }
        } catch (e: Exception) {
            log.error("Failed to cleanup message content storage", e)
        }
    }

    /**
     * 获取存储统计信息
     */
    fun getStorageStats(): StorageStats {
        val cachedCount = contentCache.size
        val cachedSize = contentCache.values.sumOf { it.length }

        var fileCount = 0
        var totalFileSize = 0L

        try {
            val contentDir = getContentDir()
            if (Files.exists(contentDir)) {
                Files.list(contentDir).use { stream ->
                    stream.forEach { path ->
                        fileCount++
                        totalFileSize += Files.size(path)
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get storage stats", e)
        }

        return StorageStats(
            cachedCount = cachedCount,
            cachedSize = cachedSize,
            fileCount = fileCount,
            totalFileSize = totalFileSize
        )
    }

    /**
     * 存储统计信息
     */
    data class StorageStats(
        val cachedCount: Int,
        val cachedSize: Int,
        val fileCount: Int,
        val totalFileSize: Long
    ) {
        val totalSize: Long get() = cachedSize.toLong() + totalFileSize
    }
}
