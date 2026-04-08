/**
 * markdown-cache.ts - Markdown 渲染缓存
 *
 * 使用 LRU (Least Recently Used) 缓存策略缓存 Markdown 渲染结果。
 * 避免重复解析相同内容，提升渲染性能。
 *
 * @module markdown-cache
 */

import { LRUCache } from './lru-cache';

/**
 * 生成缓存键
 *
 * 考虑内容、主题、代码高亮语言等因素。
 * 使用 FNV-1a 哈希算法，具有更好的分布性和更低的冲突率。
 *
 * @param content - Markdown 内容
 * @param theme - 主题名称
 * @returns 缓存键
 */
const generateCacheKey = (content: string, theme: string = 'default'): string => {
  // FNV-1a 32-bit hash algorithm
  // 具有更好的哈希分布性和更低的冲突率
  const str = content + theme;
  let hash = 0x811c9dc5; // FNV offset basis

  for (let i = 0; i < str.length; i++) {
    hash ^= str.charCodeAt(i);
    // 32-bit multiplication with overflow handling
    hash = Math.imul(hash, 0x01000193); // FNV prime
  }

  // 转换为无符号 32 位整数的十六进制字符串
  return `md-${hash >>> 0}`;
};

/**
 * Markdown 缓存配置
 */
const MARKDOWN_CACHE_CONFIG = {
  max: 100, // 最多缓存100条
  ttl: 5 * 60 * 1000 // 5分钟过期
} as const;

/**
 * Markdown渲染缓存实例
 *
 * 使用LRU策略，当缓存满时自动淘汰最久未使用的条目。
 * TTL机制确保缓存数据不会过期太久。
 */
export const markdownCache = new LRUCache<React.ReactNode>({
  max: MARKDOWN_CACHE_CONFIG.max,
  ttl: MARKDOWN_CACHE_CONFIG.ttl
});

/**
 * Markdown 缓存帮助类
 *
 * 提供更友好的API用于Markdown渲染缓存。
 */
export class MarkdownCacheHelper {
  private theme: string;

  constructor(theme: string = 'default') {
    this.theme = theme;
  }

  /**
   * 设置当前主题
   */
  setTheme(theme: string): void {
    this.theme = theme;
  }

  /**
   * 获取缓存的渲染结果
   */
  get(content: string): React.ReactNode | undefined {
    const key = generateCacheKey(content, this.theme);
    return markdownCache.get(key);
  }

  /**
   * 缓存渲染结果
   */
  set(content: string, rendered: React.ReactNode): void {
    const key = generateCacheKey(content, this.theme);
    markdownCache.set(key, rendered);
  }

  /**
   * 检查是否有缓存
   */
  has(content: string): boolean {
    const key = generateCacheKey(content, this.theme);
    return markdownCache.has(key);
  }

  /**
   * 清除所有缓存
   */
  clear(): void {
    markdownCache.clear();
  }

  /**
   * 获取缓存统计信息
   */
  getStats(): { size: number; maxSize: number } {
    return {
      size: markdownCache.size,
      maxSize: MARKDOWN_CACHE_CONFIG.max
    };
  }
}

/**
 * 默认缓存实例
 */
export const defaultMarkdownCache = new MarkdownCacheHelper();
