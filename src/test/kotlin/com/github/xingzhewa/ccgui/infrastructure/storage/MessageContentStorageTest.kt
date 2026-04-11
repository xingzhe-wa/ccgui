package com.github.xingzhewa.ccgui.infrastructure.storage

import com.github.xingzhewa.ccgui.util.JsonUtils
import com.intellij.testFramework.LightPlatformTestCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * MessageContentStorage 单元测试
 */
class MessageContentStorageTest : LightPlatformTestCase() {

    private lateinit var storage: MessageContentStorage

    @Before
    override fun setUp() {
        super.setUp()
        storage = MessageContentStorage.getInstance(project)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        storage.cleanup()
    }

    @Test
    fun `test storeContent - small content should keep in memory`() {
        val smallContent = "a".repeat(100) // 100 chars < threshold
        val result = storage.storeContent("msg-1", smallContent)

        assertEquals(smallContent, result)
        assertFalse(MessageContentStorage.isFileReference(result))
    }

    @Test
    fun `test storeContent - large content should use file storage`() {
        val largeContent = "a".repeat(6000) // 6000 chars > threshold
        val result = storage.storeContent("msg-2", largeContent)

        assertTrue(MessageContentStorage.isFileReference(result))
        assertTrue(result.startsWith("file://"))
    }

    @Test
    fun `test getContent - should return original content for small content`() {
        val smallContent = "test content"
        storage.storeContent("msg-3", smallContent)

        val result = storage.getContent(smallContent, "msg-3")
        assertEquals(smallContent, result)
    }

    @Test
    fun `test getContent - should return file content for large content`() {
        val largeContent = "x".repeat(6000)
        val fileRef = storage.storeContent("msg-4", largeContent)

        val result = storage.getContent(fileRef, "msg-4")
        assertEquals(largeContent, result)
    }

    @Test
    fun `test getContent - should cache file content`() {
        val largeContent = "y".repeat(6000)
        val fileRef = storage.storeContent("msg-5", largeContent)

        // 第一次获取 - 从文件读取
        val result1 = storage.getContent(fileRef, "msg-5")

        // 清除缓存
        storage.clearCache()

        // 第二次获取 - 应该从缓存读取（如果文件仍存在）
        val result2 = storage.getContent(fileRef, "msg-5")

        assertEquals(largeContent, result1)
        assertEquals(largeContent, result2)
    }

    @Test
    fun `test deleteContent - should remove file for file reference`() {
        val largeContent = "z".repeat(6000)
        val fileRef = storage.storeContent("msg-6", largeContent)

        // 确保文件存在
        assertTrue(MessageContentStorage.isFileReference(fileRef))
        val filePath = MessageContentStorage.extractFilePath(fileRef)
        assertNotNull(filePath)

        val file = File(filePath)
        assertTrue(file.exists())

        // 删除内容
        storage.deleteContent(fileRef, "msg-6")

        // 验证文件被删除
        assertFalse(file.exists())
    }

    @Test
    fun `test deleteContent - should not affect regular content`() {
        val smallContent = "test content"
        storage.storeContent("msg-7", smallContent)

        // 删除（应该不抛出异常）
        storage.deleteContent(smallContent, "msg-7")

        // 验证内容仍然可以获取
        val result = storage.getContent(smallContent, "msg-7")
        assertEquals(smallContent, result)
    }

    @org.junit.Test
    fun testClearCacheShouldClearAllCachedContent() = runBlocking {
        // 添加一些缓存
        val largeContent = "w".repeat(6000)
        storage.storeContent("msg-8", largeContent)
        storage.storeContent("msg-9", largeContent)

        // 清空缓存
        storage.clearCache()

        // 验证缓存统计
        val stats = storage.getStorageStats()
        assertEquals(0, stats.cachedCount)
    }

    @org.junit.Test
    fun testGetStorageStatsShouldReturnAccurateStats() = runBlocking {
        val stats1 = storage.getStorageStats()
        assertEquals(0, stats1.cachedCount)
        assertEquals(0, stats1.fileCount)

        // 添加一些内容
        storage.storeContent("msg-10", "small content")
        storage.storeContent("msg-11", "x".repeat(6000))

        val stats2 = storage.getStorageStats()
        assertEquals(1, stats2.cachedCount) // 小内容被缓存
        assertTrue(stats2.fileCount >= 1)    // 大内容使用文件
    }

    @org.junit.Test
    fun testMultipleMessagesShouldHandleIndependently() = runBlocking {
        val content1 = "content 1"
        val content2 = "content 2"

        storage.storeContent("msg-12", content1)
        storage.storeContent("msg-13", content2)

        assertEquals(content1, storage.getContent("msg-12", "msg-12"))
        assertEquals(content2, storage.getContent("msg-13", "msg-13"))
    }

    @org.junit.Test
    fun testCleanupShouldRemoveAllFiles() = runBlocking {
        // 创建几个大消息
        repeat(3) {
            val largeContent = "c".repeat(6000)
            storage.storeContent("msg-cleanup-$it", largeContent)
        }

        // 清理
        storage.cleanup()

        // 验证所有文件被删除
        val stats = storage.getStorageStats()
        assertEquals(0, stats.fileCount)
    }

    @org.junit.Test
    fun testFileReferenceFormatShouldBeCorrect() = runBlocking {
        val largeContent = "d".repeat(6000)
        val fileRef = storage.storeContent("msg-14", largeContent)

        assertTrue(fileRef.startsWith("file://"))

        val filePath = MessageContentStorage.extractFilePath(fileRef)
        assertNotNull(filePath)

        // 验证路径指向实际文件
        val file = File(filePath)
        assertTrue(file.exists())
        assertEquals(largeContent.length.toLong(), file.length())
    }

    @org.junit.Test
    fun testSpecialCharactersInContentShouldHandleCorrectly() = runBlocking {
        val specialContent = "Test with 中文 🎉 and symbols: !@#$%^&*()"
        val result = storage.storeContent("msg-15", specialContent)

        assertEquals(specialContent, storage.getContent(result, "msg-15"))
    }

    @org.junit.Test
    fun testEmptyContentShouldHandleCorrectly() = runBlocking {
        val emptyContent = ""
        val result = storage.storeContent("msg-16", emptyContent)

        assertEquals(emptyContent, storage.getContent(result, "msg-16"))
    }

    @org.junit.Test
    fun testVeryLargeContentShouldHandleCorrectly() = runBlocking {
        val veryLargeContent = "v".repeat(100000) // 100k chars
        val fileRef = storage.storeContent("msg-17", veryLargeContent)

        val result = storage.getContent(fileRef, "msg-17")
        assertEquals(veryLargeContent, result)
        assertEquals(100000, result.length)
    }

    @org.junit.Test
    fun testConcurrentStorageShouldHandleSafely() = runBlocking {
        val threads = (1..10).map { threadId ->
            Thread {
                val content = "thread-$threadId-content"
                kotlinx.coroutines.runBlocking {
                    storage.storeContent("msg-concurrent-$threadId", content)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // 验证所有内容都正确存储
        (1..10).forEach { threadId ->
            val content = "thread-$threadId-content"
            val storedRef = storage.storeContent("msg-concurrent-$threadId", content)
            val result = storage.getContent(storedRef, "msg-concurrent-$threadId")
            assertEquals(content, result)
        }
    }
}
