package com.github.xingzhewa.ccgui.infrastructure.cache

import com.github.xingzhewa.ccgui.util.logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * LRU 缓存条目
 */
data class CacheEntry<K, V>(
    val key: K,
    var value: V,
    var accessTime: Long = System.currentTimeMillis(),
    var createTime: Long = System.currentTimeMillis()
)

/**
 * LRU 缓存管理器
 *
 * 提供线程安全的 LRU 缓存实现
 *
 * @param maxSize 最大缓存条目数
 * @param ttlMs 缓存条目过期时间（毫秒），0 表示不过期
 */
class CacheManager<K, V>(
    private val maxSize: Int = 100,
    private val ttlMs: Long = 0
) {

    private val logger = logger<CacheManager<K, V>>()
    private val cache = ConcurrentHashMap<K, CacheEntry<K, V>>()
    private val lock = ReentrantReadWriteLock()
    private val readLock = lock.readLock()
    private val writeLock = lock.writeLock()

    /**
     * 缓存条目数量
     */
    val size: Int get() = cache.size

    /**
     * 是否为空
     */
    val isEmpty: Boolean get() = cache.isEmpty()

    /**
     * 是否包含指定键
     */
    fun contains(key: K): Boolean {
        return cache.containsKey(key)
    }

    /**
     * 获取缓存值
     *
     * @param key 键
     * @return 缓存值，不存在或已过期返回 null
     */
    fun get(key: K): V? {
        readLock.lock()
        try {
            val entry = cache[key] ?: return null

            // 检查过期
            if (ttlMs > 0 && System.currentTimeMillis() - entry.createTime > ttlMs) {
                logger.debug("Cache entry expired: $key")
                return null
            }

            // 更新访问时间
            entry.accessTime = System.currentTimeMillis()
            return entry.value
        } finally {
            readLock.unlock()
        }
    }

    /**
     * 设置缓存值
     *
     * @param key 键
     * @param value 值
     */
    fun put(key: K, value: V) {
        writeLock.lock()
        try {
            // 如果已满，执行 LRU 淘汰
            if (cache.size >= maxSize && !cache.containsKey(key)) {
                evictLRU()
            }

            cache[key] = CacheEntry(key, value)
        } finally {
            writeLock.unlock()
        }
    }

    /**
     * 批量设置缓存
     */
    fun putAll(items: Map<K, V>) {
        items.forEach { (k, v) -> put(k, v) }
    }

    /**
     * 移除缓存
     */
    fun remove(key: K): Boolean {
        writeLock.lock()
        try {
            return cache.remove(key) != null
        } finally {
            writeLock.unlock()
        }
    }

    /**
     * 清空缓存
     */
    fun clear() {
        writeLock.lock()
        try {
            cache.clear()
        } finally {
            writeLock.unlock()
        }
        logger.debug("Cache cleared")
    }

    /**
     * 获取缓存值，如果不存在则计算并缓存
     */
    fun getOrCompute(key: K, compute: () -> V): V {
        get(key)?.let { return it }

        val value = compute()
        put(key, value)
        return value
    }

    /**
     * 获取缓存值（带过期检查），如果不存在或已过期则计算并缓存
     */
    fun getOrComputeWithTTL(key: K, compute: () -> V): V {
        readLock.lock()
        try {
            val entry = cache[key]
            if (entry != null && (ttlMs <= 0 || System.currentTimeMillis() - entry.createTime <= ttlMs)) {
                entry.accessTime = System.currentTimeMillis()
                return entry.value
            }
        } finally {
            readLock.unlock()
        }

        val value = compute()
        put(key, value)
        return value
    }

    /**
     * 淘汰最少使用的条目
     */
    private fun evictLRU() {
        val lruEntry = cache.values.minByOrNull { it.accessTime }
        lruEntry?.let {
            cache.remove(it.key)
            logger.debug("Evicted LRU entry: ${it.key}")
        }
    }

    /**
     * 清理过期条目
     */
    fun cleanup() {
        if (ttlMs <= 0) return

        writeLock.lock()
        try {
            val now = System.currentTimeMillis()
            val expiredKeys = cache.values
                .filter { now - it.createTime > ttlMs }
                .map { it.key }

            expiredKeys.forEach { cache.remove(it) }

            if (expiredKeys.isNotEmpty()) {
                logger.debug("Cleaned up ${expiredKeys.size} expired entries")
            }
        } finally {
            writeLock.unlock()
        }
    }

    /**
     * 获取缓存命中率统计
     */
    fun getStats(): CacheStats {
        return CacheStats(
            size = cache.size,
            maxSize = maxSize,
            ttlMs = ttlMs
        )
    }

    /**
     * 缓存统计信息
     */
    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val ttlMs: Long
    )
}

/**
 * 全局缓存管理器单例
 */
object GlobalCache {

    private val caches = ConcurrentHashMap<String, CacheManager<*, *>>()

    /**
     * 获取或创建缓存
     */
    @Suppress("UNCHECKED_CAST")
    fun <K, V> getOrCreate(name: String, maxSize: Int = 100, ttlMs: Long = 0): CacheManager<K, V> {
        return (caches.getOrPut(name) {
            CacheManager<K, V>(maxSize, ttlMs)
        } as CacheManager<K, V>)
    }

    /**
     * 清空所有缓存
     */
    fun clearAll() {
        caches.values.forEach { (it as CacheManager<*, *>).clear() }
        caches.clear()
    }

    /**
     * 清理所有过期条目
     */
    fun cleanupAll() {
        caches.values.forEach { (it as CacheManager<*, *>).cleanup() }
    }
}
