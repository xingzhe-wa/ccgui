/**
 * useOptimizedVirtualList - 优化的虚拟滚动 Hook
 *
 * 使用 @tanstack/react-virtual 实现高性能虚拟滚动。
 * 支持动态高度计算和测量缓存。
 *
 * @module useOptimizedVirtualList
 */

import { useRef } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import type { ChatMessage } from '@/shared/types';

/**
 * 根据消息内容估算高度
 *
 * 考虑因素：
 * - 基础高度（头像+间距）
 * - 文本行数
 * - 代码块
 * - 附件
 * - 表格
 */
const estimateMessageSize = (message: ChatMessage): number => {
  const baseHeight = 80;
  const contentLength = message.content?.length || 0;
  const contentLines = message.content?.split('\n').length || 0;
  const lineHeight = 22;
  const hasCodeBlock = message.content?.includes('```') ?? false;
  const hasImage = message.attachments?.some((a) => a.type?.startsWith('image/')) ?? false;
  const hasTable = message.content?.includes('|') ?? false;

  let height = baseHeight + (contentLines * lineHeight);

  // 代码块额外高度
  if (hasCodeBlock) {
    const codeBlockCount = ((message.content?.match(/```/g) || []).length / 2);
    height += codeBlockCount * 200;
  }

  // 图片附件
  if (hasImage) {
    height += 200;
  }

  // 表格
  if (hasTable) {
    height += 100;
  }

  // 长文本额外高度
  if (contentLength > 500) {
    height += Math.floor(contentLength / 500) * 20;
  }

  return Math.max(height, 60);
};

interface UseOptimizedVirtualListOptions {
  /** overscan 预渲染数量 */
  overscan?: number;
}

/**
 * 优化的虚拟滚动 Hook
 *
 * @param messages - 消息列表
 * @param scrollElement - 滚动容器元素
 * @param options - 配置选项
 * @returns virtualizer 实例
 */
export const useOptimizedVirtualList = (
  messages: ChatMessage[],
  scrollElement: HTMLElement | null,
  options: UseOptimizedVirtualListOptions = {}
) => {
  const { overscan = 5 } = options;

  // 缓存已测量的元素高度
  const measuredSizes = useRef<Map<number, number>>(new Map());

  const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => scrollElement,
    estimateSize: (index) => {
      // 优先使用已测量的高度
      const measured = measuredSizes.current.get(index);
      if (measured !== undefined) return measured;
      const message = messages[index];
      if (!message) return 80;
      return estimateMessageSize(message);
    },
    overscan,
    measureElement: (el) => {
      // 测量实际高度并缓存
      const height = el.getBoundingClientRect().height;
      const htmlElement = el as HTMLElement;
      const indexStr = htmlElement.dataset?.index;
      if (indexStr !== undefined) {
        const index = Number(indexStr);
        if (!isNaN(index)) {
          measuredSizes.current.set(index, height);
        }
      }
      return height;
    }
  });

  return virtualizer;
};
