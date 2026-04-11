package com.github.xingzhewa.ccgui.infrastructure.storage

import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 大消息内容文件管理器
 *
 * 基于技术架构文档 5.1 节要求实现
 * 大型消息内容存储为独立 JSON 文件，支持 gzip 压缩
 *
 * 存储结构：
 * ```
 * sessionDir/
 *   └── {sessionId}/
 *       └── {messageId}.json[.gz]
 * ```
 *
 * @param project 项目实例
 */
@State(name = "CCGuiMessageFileManager", storages = [Storage("ccgui-message-files.xml")])
@Service(Service.Level.PROJECT)
class MessageFileManager(private val project: Project) : PersistentStateComponent<MessageFileManager.State> {

    private val log = logger<MessageFileManager>()

    /**
     * 存储状态
     */
    data class State(
        /** 消息文件存储根目录路径 */
        var messageFilesDir: String = ""
    )

    private var state = State()

    /** 内存缓存：messageId -> content */
    private val memoryCache = ConcurrentHashMap<String, String>()

    /** 压缩内容缓存：messageId -> decompressed content */
    private val compressedCache = ConcurrentHashMap<String, String>()

    companion object {
        /** 小消息阈值：10KB */
        const val SMALL_MESSAGE_THRESHOLD = 10 * 1024

        /** 大消息阈值：100KB */
        const val LARGE_MESSAGE_THRESHOLD = 100 * 1024

        /** 压缩文件后缀 */
        private const val GZIP_EXTENSION = ".gz"

        /** 默认存储目录名 */
        private const val DEFAULT_DIR_NAME = "message-files"

        /** 消息文件引用前缀 */
        const val FILE_REFERENCE_PREFIX = "msgfile://"

        /** 最大缓存条目数 */
        private const val MAX_CACHE_SIZE = 200

        fun getInstance(project: Project): MessageFileManager =
            project.getService(MessageFileManager::class.java)

        /**
         * 判断内容是否为文件引用
         */
        fun isFileReference(content: String): Boolean {
            return content.startsWith(FILE_REFERENCE_PREFIX)
        }

        /**
         * 从文件引用中提取 sessionId 和 messageId
         * @return Pair(sessionId, messageId) 或 null
         */
        fun parseFileReference(fileRef: String): Pair<String, String>? {
            if (!isFileReference(fileRef)) return null
            val path = fileRef.substring(FILE_REFERENCE_PREFIX.length)
            val parts = path.split("/")
            if (parts.size != 2) return null
            return Pair(parts[0], parts[1].removeSuffix(".json").removeSuffix(".gz"))
        }
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        // 确保目录存在
        getSessionDir()?.let { Files.createDirectories(it) }
    }

    /**
     * 获取消息文件存储根目录
     */
    private fun getSessionDir(): Path? {
        val basePath = project.basePath ?: return null
        val rootDir = if (state.messageFilesDir.isNotEmpty()) {
            Paths.get(state.messageFilesDir)
        } else {
            Paths.get(basePath, ".idea", "ccgui", DEFAULT_DIR_NAME)
        }
        return rootDir
    }

    /**
     * 获取指定 session 的消息存储目录
     */
    private fun getSessionMessageDir(sessionId: String): Path? {
        return getSessionDir()?.resolve(sessionId)
    }

    /**
     * 获取消息文件路径
     */
    private fun getMessageFile(sessionId: String, messageId: String, compressed: Boolean): Path? {
        val sessionDir = getSessionMessageDir(sessionId) ?: return null
        val fileName = if (compressed) "$messageId.json$GZIP_EXTENSION" else "$messageId.json"
        return sessionDir.resolve(fileName)
    }

    /**
     * 保存消息内容
     *
     * 根据内容大小自动选择存储策略：
     * - 小内容（< 10KB）：直接存储为 JSON 文件
     * - 中等内容（10KB - 100KB）：gzip 压缩存储
     * - 大内容（>= 100KB）：gzip 压缩存储
     *
     * @param sessionId 会话 ID
     * @param messageId 消息 ID
     * @param content 消息内容
     * @return 文件引用路径，如果存储失败则返回 null
     */
    suspend fun saveMessage(sessionId: String, messageId: String, content: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val sessionDir = getSessionMessageDir(sessionId)
                    ?: run {
                        log.error("Cannot get session directory for sessionId: $sessionId")
                        return@withContext null
                    }

                // 创建会话目录
                Files.createDirectories(sessionDir)

                val contentSize = content.toByteArray(StandardCharsets.UTF_8).size
                val shouldCompress = contentSize >= SMALL_MESSAGE_THRESHOLD

                val file = getMessageFile(sessionId, messageId, shouldCompress)
                    ?: return@withContext null

                // 写入文件
                if (shouldCompress) {
                    val compressedBytes = gzipCompress(content.toByteArray(StandardCharsets.UTF_8))
                    Files.write(file, compressedBytes)
                    log.debug("Message $messageId saved with gzip compression (original: $contentSize bytes)")
                } else {
                    Files.write(file, content.toByteArray(StandardCharsets.UTF_8))
                    log.debug("Message $messageId saved without compression ($contentSize bytes)")
                }

                // 更新缓存
                addToCache(messageId, content)

                // 返回文件引用
                val compressedSuffix = if (shouldCompress) ".gz" else ""
                "$FILE_REFERENCE_PREFIX$sessionId/$messageId.json$compressedSuffix"
            } catch (e: Exception) {
                log.error("Failed to save message $messageId for session $sessionId", e)
                null
            }
        }

    /**
     * 加载消息内容
     *
     * @param sessionId 会话 ID
     * @param messageId 消息 ID
     * @return 消息内容，如果不存在则返回 null
     */
    suspend fun loadMessage(sessionId: String, messageId: String): String? =
        withContext(Dispatchers.IO) {
            // 先检查缓存
            memoryCache[messageId]?.let {
                log.debug("Message $messageId found in cache")
                return@withContext it
            }

            try {
                val sessionDir = getSessionMessageDir(sessionId) ?: return@withContext null

                // 尝试查找压缩或非压缩文件
                val compressedFile = sessionDir.resolve("$messageId.json$GZIP_EXTENSION")
                val normalFile = sessionDir.resolve("$messageId.json")

                val file = when {
                    Files.exists(compressedFile) -> compressedFile
                    Files.exists(normalFile) -> normalFile
                    else -> {
                        log.warn("Message file not found for $messageId in session $sessionId")
                        return@withContext null
                    }
                }

                val isCompressed = file.toString().endsWith(GZIP_EXTENSION)
                val content = if (isCompressed) {
                    val compressedBytes = Files.readAllBytes(file)
                    val decompressed = gzipDecompress(compressedBytes)
                    decompressed?.toString(StandardCharsets.UTF_8) ?: return@withContext null
                } else {
                    Files.readString(file)
                }

                // 更新缓存
                addToCache(messageId, content)

                log.debug("Message $messageId loaded from file (${content.length} chars)")
                content
            } catch (e: Exception) {
                log.error("Failed to load message $messageId for session $sessionId", e)
                null
            }
        }

    /**
     * 删除消息内容
     *
     * @param sessionId 会话 ID
     * @param messageId 消息 ID
     * @return true if deletion was successful
     */
    suspend fun deleteMessage(sessionId: String, messageId: String): Boolean =
        withContext(Dispatchers.IO) {
            // 从缓存移除
            memoryCache.remove(messageId)
            compressedCache.remove(messageId)

            try {
                val sessionDir = getSessionMessageDir(sessionId) ?: return@withContext false

                val compressedFile = sessionDir.resolve("$messageId.json$GZIP_EXTENSION")
                val normalFile = sessionDir.resolve("$messageId.json")

                var deleted = false
                if (Files.exists(compressedFile)) {
                    Files.delete(compressedFile)
                    deleted = true
                    log.debug("Deleted compressed message file: $compressedFile")
                }
                if (Files.exists(normalFile)) {
                    Files.delete(normalFile)
                    deleted = true
                    log.debug("Deleted message file: $normalFile")
                }

                if (!deleted) {
                    log.warn("No message file found to delete for $messageId in session $sessionId")
                }

                deleted
            } catch (e: Exception) {
                log.error("Failed to delete message $messageId for session $sessionId", e)
                false
            }
        }

    /**
     * 删除会话的所有消息文件
     *
     * @param sessionId 会话 ID
     */
    suspend fun deleteSessionMessages(sessionId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val sessionDir = getSessionMessageDir(sessionId) ?: return@withContext false

                if (!Files.exists(sessionDir)) {
                    log.debug("Session directory does not exist: $sessionDir")
                    return@withContext true
                }

                // 删除会话目录下所有文件
                Files.walk(sessionDir)
                    .filter { it != sessionDir }
                    .forEach { Files.deleteIfExists(it) }

                // 删除会话目录本身
                Files.deleteIfExists(sessionDir)

                log.info("Deleted all messages for session: $sessionId")
                true
            } catch (e: Exception) {
                log.error("Failed to delete session messages for $sessionId", e)
                false
            }
        }

    /**
     * 从内容解析并加载（当内容是文件引用时）
     *
     * @param content 消息内容（可能是文件引用）
     * @return 实际消息内容
     */
    suspend fun resolveContent(content: String): String {
        if (!isFileReference(content)) {
            return content
        }

        val parsed = parseFileReference(content) ?: return content
        return loadMessage(parsed.first, parsed.second) ?: content
    }

    /**
     * 清理会话目录中孤立的空目录
     */
    suspend fun cleanupEmptySessionDirs() = withContext(Dispatchers.IO) {
        try {
            val rootDir = getSessionDir() ?: return@withContext
            if (!Files.exists(rootDir)) return@withContext

            Files.list(rootDir).use { stream ->
                stream.filter { path ->
                    Files.isDirectory(path) && Files.list(path).use { it.count() == 0L }
                }.forEach { path ->
                    Files.delete(path)
                    log.debug("Deleted empty session directory: $path")
                }
            }
        } catch (e: Exception) {
            log.error("Failed to cleanup empty session directories", e)
        }
    }

    /**
     * 获取存储统计信息
     */
    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        var totalFiles = 0
        var totalSize = 0L
        var compressedFiles = 0
        var uncompressedFiles = 0

        try {
            val rootDir = getSessionDir() ?: return@withContext StorageStats(0, 0, 0, 0, 0)
            if (!Files.exists(rootDir)) {
                return@withContext StorageStats(0, 0, 0, 0, 0)
            }

            Files.walk(rootDir)
                .filter { Files.isRegularFile(it) }
                .forEach { path ->
                    totalFiles++
                    totalSize += Files.size(path)
                    if (path.toString().endsWith(GZIP_EXTENSION)) {
                        compressedFiles++
                    } else {
                        uncompressedFiles++
                    }
                }
        } catch (e: Exception) {
            log.error("Failed to calculate storage stats", e)
        }

        StorageStats(
            totalFiles = totalFiles,
            totalSize = totalSize,
            compressedFiles = compressedFiles,
            uncompressedFiles = uncompressedFiles,
            cachedMessages = memoryCache.size
        )
    }

    /**
     * GZIP 压缩
     */
    private fun gzipCompress(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream(data.size)
        GZIPOutputStream(baos).use { gzos ->
            ByteArrayInputStream(data).use { bais ->
                bais.copyTo(gzos)
            }
        }
        return baos.toByteArray()
    }

    /**
     * GZIP 解压
     */
    private fun gzipDecompress(data: ByteArray): ByteArray? {
        return try {
            val baos = ByteArrayOutputStream()
            GZIPInputStream(ByteArrayInputStream(data)).use { gzis ->
                gzis.copyTo(baos)
            }
            baos.toByteArray()
        } catch (e: Exception) {
            log.error("Failed to decompress data", e)
            null
        }
    }

    /**
     * 添加到缓存
     */
    private fun addToCache(messageId: String, content: String) {
        if (memoryCache.size >= MAX_CACHE_SIZE) {
            // 移除最早的条目
            val oldestKey = memoryCache.keys.firstOrNull()
            oldestKey?.let { memoryCache.remove(it) }
        }
        memoryCache[messageId] = content
    }

    /**
     * 清空内存缓存
     */
    fun clearCache() {
        memoryCache.clear()
        compressedCache.clear()
        log.debug("MessageFileManager cache cleared")
    }

    /**
     * 设置消息文件存储目录（用于测试或自定义配置）
     */
    fun setMessageFilesDir(dirPath: String) {
        state.messageFilesDir = dirPath
    }

    /**
     * 存储统计信息
     */
    data class StorageStats(
        val totalFiles: Int,
        val totalSize: Long,
        val compressedFiles: Int,
        val uncompressedFiles: Int,
        val cachedMessages: Int
    ) {
        val formattedSize: String get() {
            return when {
                totalSize < 1024 -> "$totalSize B"
                totalSize < 1024 * 1024 -> "${totalSize / 1024} KB"
                else -> "${totalSize / (1024 * 1024)} MB"
            }
        }
    }
}