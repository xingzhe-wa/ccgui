/**
 * MessageList - 虚拟滚动消息列表
 *
 * 性能优化：
 * - 使用 useRef 追踪滚动状态，避免不必要的自动滚动
 * - 只有当新消息追加到底部且用户没有主动滚动时才自动滚动
 */

import { memo, useRef, useEffect, useCallback } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { cn } from '@/shared/utils/cn';
import { MessageItem } from './MessageItem';
import type { ChatMessage } from '@/shared/types';

interface MessageListProps {
  messages: ChatMessage[];
  onReply?: (messageId: string) => void;
  onDelete?: (messageId: string) => void;
  onCopy?: (messageId: string, content: string) => void;
  onSelect?: (messageId: string) => void;
  onQuote?: (messageId: string, excerpt: string) => void;
  selectedMessageId?: string | null;
  className?: string;
}

const ESTIMATED_MESSAGE_HEIGHT = 120;
const OVERSCAN = 5;

export const MessageList = memo<MessageListProps>(function MessageList({
  messages,
  onReply,
  onDelete,
  onCopy,
  onSelect,
  onQuote,
  selectedMessageId,
  className
}) {
  const parentRef = useRef<HTMLDivElement>(null);
  const prevMessagesLengthRef = useRef<number>(messages.length);
  const isUserScrollingRef = useRef<boolean>(false);
  const scrollTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ESTIMATED_MESSAGE_HEIGHT,
    overscan: OVERSCAN,
    measureElement: (element) => element?.getBoundingClientRect().height ?? ESTIMATED_MESSAGE_HEIGHT
  });

  // 检查用户是否在主动滚动（通过监听 scroll 事件）
  const handleScroll = useCallback(() => {
    if (!parentRef.current) return;

    const { scrollTop, scrollHeight, clientHeight } = parentRef.current;
    const isAtBottom = scrollHeight - scrollTop - clientHeight < 50;

    // 如果用户向上滚动，设置标志
    if (!isAtBottom) {
      isUserScrollingRef.current = true;
    }

    // 清除之前的超时，重新设置
    if (scrollTimeoutRef.current) {
      clearTimeout(scrollTimeoutRef.current);
    }

    // 1秒后重置用户滚动标志
    scrollTimeoutRef.current = setTimeout(() => {
      isUserScrollingRef.current = false;
    }, 1000);
  }, []);

  // 清理超时
  useEffect(() => {
    return () => {
      if (scrollTimeoutRef.current) {
        clearTimeout(scrollTimeoutRef.current);
      }
    };
  }, []);

  // Auto-scroll to bottom on new messages
  // 只有当用户没有主动滚动时才自动滚动
  useEffect(() => {
    const prevLength = prevMessagesLengthRef.current;
    const newLength = messages.length;

    // 只有当新消息是追加（不是插入）且用户没有主动滚动时才滚动
    if (newLength > prevLength && !isUserScrollingRef.current) {
      virtualizer.scrollToIndex(newLength - 1, { align: 'end' });
    }

    prevMessagesLengthRef.current = newLength;
  }, [messages.length, virtualizer]);

  // 监听滚动事件
  useEffect(() => {
    const el = parentRef.current;
    if (!el) return;

    el.addEventListener('scroll', handleScroll);
    return () => el.removeEventListener('scroll', handleScroll);
  }, [handleScroll]);

  // 内容变化时重新测量
  useEffect(() => {
    virtualizer.measure?.();
  }, [messages, virtualizer.measure]);

  const items = virtualizer.getVirtualItems();

  if (messages.length === 0) {
    return (
      <div className={cn('flex flex-col items-center justify-center h-full', className)}>
        <div className="text-center text-foreground-muted">
          <svg
            className="w-16 h-16 mx-auto mb-4 opacity-30"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
            />
          </svg>
          <p className="text-lg font-medium mb-1">No messages yet</p>
          <p className="text-sm">Start a conversation with Claude</p>
        </div>
      </div>
    );
  }

  return (
    <div
      ref={parentRef}
      className={cn('h-full overflow-y-auto', className)}
    >
      <div
        style={{
          height: `${virtualizer.getTotalSize()}px`,
          width: '100%',
          position: 'relative'
        }}
      >
        {items.map((virtualItem) => {
          const message = messages[virtualItem.index];
          if (!message) return null;
          return (
            <div
              key={virtualItem.key}
              data-index={virtualItem.index}
              ref={virtualizer.measureElement}
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                transform: `translateY(${virtualItem.start}px)`
              }}
            >
              <MessageItem
                message={message}
                onReply={onReply}
                onDelete={onDelete}
                onCopy={onCopy}
                onSelect={onSelect}
                onQuote={onQuote}
                isSelected={selectedMessageId === message.id}
              />
            </div>
          );
        })}
      </div>
    </div>
  );
});

export type { MessageListProps };
