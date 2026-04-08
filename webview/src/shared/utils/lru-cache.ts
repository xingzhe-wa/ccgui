/**
 * lru-cache.ts - LRU (Least Recently Used) 缓存实现
 *
 * 通用的LRU缓存实现，支持TTL过期机制。
 * 当缓存满时自动淘汰最久未使用的条目。
 *
 * @module lru-cache
 */

interface LRUCacheEntry<T> {
  value: T;
  timestamp: number;
}

interface LRUCacheOptions {
  /** 最大缓存数量 */
  max: number;
  /** 过期时间（毫秒） */
  ttl: number;
}

/**
 * LRU 缓存类
 *
 * 使用Map实现，利用Map的迭代顺序来追踪访问顺序。
 * 最近访问的条目会被移到末尾，淘汰时删除第一个（最老的）。
 */
export class LRUCache<T> {
  private cache = new Map<string, LRUCacheEntry<T>>();
  private readonly maxSize: number;
  private readonly ttl: number;

  constructor(options: LRUCacheOptions) {
    this.maxSize = options.max;
    this.ttl = options.ttl;
  }

  /**
   * 获取缓存值
   *
   * 如果缓存存在且未过期，返回值并移到末尾（标记为最近访问）。
   * 如果缓存不存在或已过期，返回undefined。
   */
  get(key: string): T | undefined {
    const entry = this.cache.get(key);
    if (!entry) return undefined;

    // 检查过期
    if (Date.now() - entry.timestamp > this.ttl) {
      this.cache.delete(key);
      return undefined;
    }

    // 移到末尾（最近访问）
    this.cache.delete(key);
    this.cache.set(key, entry);
    return entry.value;
  }

  /**
   * 设置缓存值
   *
   * 如果键已存在，先删除再插入（更新时间戳）。
   * 如果超过最大容量，删除最老的条目。
   */
  set(key: string, value: T): void {
    // 如果已存在，先删除
    this.cache.delete(key);

    // 如果超过最大容量，删除最老的
    if (this.cache.size >= this.maxSize) {
      const firstKey = this.cache.keys().next().value;
      if (firstKey !== undefined) {
        this.cache.delete(firstKey);
      }
    }

    this.cache.set(key, { value, timestamp: Date.now() });
  }

  /**
   * 检查键是否存在（且未过期）
   */
  has(key: string): boolean {
    return this.get(key) !== undefined;
  }

  /**
   * 删除指定键
   */
  delete(key: string): boolean {
    return this.cache.delete(key);
  }

  /**
   * 清空所有缓存
   */
  clear(): void {
    this.cache.clear();
  }

  /**
   * 获取缓存大小
   */
  get size(): number {
    return this.cache.size;
  }

  /**
   * 获取所有键
   */
  keys(): string[] {
    return Array.from(this.cache.keys());
  }

  /**
   * 获取所有值
   */
  values(): T[] {
    return Array.from(this.cache.values()).map((entry) => entry.value);
  }

  /**
   * 清理过期条目
   *
   * 遍历缓存，删除所有过期的条目。
   */
  purgeExpired(): number {
    let removed = 0;
    const now = Date.now();
    const keysToDelete: string[] = [];

    for (const [key, entry] of this.cache.entries()) {
      if (now - entry.timestamp > this.ttl) {
        keysToDelete.push(key);
      }
    }

    for (const key of keysToDelete) {
      this.cache.delete(key);
      removed++;
    }

    return removed;
  }

  /**
   * 获取缓存统计信息
   */
  getStats(): {
    size: number;
    maxSize: number;
    ttl: number;
    keys: string[];
  } {
    return {
      size: this.cache.size,
      maxSize: this.maxSize,
      ttl: this.ttl,
      keys: this.keys()
    };
  }
}
