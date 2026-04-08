/**
 * MessageList - 虚拟滚动消息列表
 */

import { memo, useRef, useEffect } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { cn } from '@/shared/utils/cn';
import { MessageItem } from './MessageItem';
import type { ChatMessage } from '@/shared/types';

interface MessageListProps {
  messages: ChatMessage[];
  onReply?: (messageId: string) => void;
  onDelete?: (messageId: string) => void;
  onCopy?: (messageId: string, content: string) => void;
  className?: string;
}

const ESTIMATED_MESSAGE_HEIGHT = 120;
const OVERSCAN = 5;

export const MessageList = memo<MessageListProps>(function MessageList({
  messages,
  onReply,
  onDelete,
  onCopy,
  className
}) {
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ESTIMATED_MESSAGE_HEIGHT,
    overscan: OVERSCAN
  });

  // Auto-scroll to bottom on new messages
  useEffect(() => {
    if (messages.length > 0) {
      virtualizer.scrollToIndex(messages.length - 1, { align: 'end' });
    }
  }, [messages.length, virtualizer]);

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
      style={{ contain: 'strict' }}
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
              />
            </div>
          );
        })}
      </div>
    </div>
  );
});

export type { MessageListProps };
