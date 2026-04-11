/**
 * MessageItem - 消息列表项组件
 */

import { memo, useState, useCallback, useMemo } from 'react';
import { cn } from '@/shared/utils/cn';
import { ContentBlockRenderer } from './ContentBlockRenderer';
import type { ChatMessage, ContentPart, ClaudeRawContent } from '@/shared/types';

interface MessageItemProps {
  message: ChatMessage;
  onReply?: (messageId: string) => void;
  onDelete?: (messageId: string) => void;
  onCopy?: (messageId: string, content: string) => void;
  onSelect?: (messageId: string) => void;
  onQuote?: (messageId: string, excerpt: string) => void;
  isSelected?: boolean;
  className?: string;
}

export const MessageItem = memo<MessageItemProps>(function MessageItem({
  message,
  onReply,
  onDelete,
  onCopy,
  onSelect,
  onQuote,
  isSelected,
  className
}) {
  const [isHovered, setIsHovered] = useState(false);

  const isUser = message.role === 'user';
  const isSystem = message.role === 'system';
  const isStreaming = message.isStreaming === true;

  const handleReply = useCallback(() => {
    onReply?.(message.id);
  }, [message.id, onReply]);

  const handleDelete = useCallback(() => {
    onDelete?.(message.id);
  }, [message.id, onDelete]);

  const handleCopy = useCallback(() => {
    onCopy?.(message.id, message.content);
  }, [message.id, message.content, onCopy]);

  const handleSelect = useCallback(() => {
    onSelect?.(message.id);
  }, [message.id, onSelect]);

  const handleQuote = useCallback(() => {
    const excerpt = message.content.slice(0, 100);
    onQuote?.(message.id, excerpt);
  }, [message.id, message.content, onQuote]);

  /**
   * 解析 ClaudeRawContent 为 ContentPart
   */
  const parseRawContent = useCallback((raw: ClaudeRawContent[]): ContentPart[] => {
    return raw.map((block): ContentPart => {
      switch (block.type) {
        case 'text':
          return { type: 'text', text: block.text || '' };
        case 'thinking':
          return { type: 'thinking', thinking: block.thinking || '', text: block.text };
        case 'tool_use':
          return { type: 'tool_use', id: block.id, name: block.name || '', input: block.input };
        case 'tool_result': {
          const resultBlock: ContentPart = { type: 'tool_result', tool_use_id: block.tool_use_id, is_error: block.is_error };
          if (typeof block.content === 'string') {
            (resultBlock as any).content = block.content;
          } else if (Array.isArray(block.content)) {
            // 递归解析嵌套内容
            (resultBlock as any).content = parseRawContent(block.content);
          }
          return resultBlock;
        }
        case 'image':
          if (block.source) {
            return { type: 'image', mimeType: block.source.media_type || 'image/png', data: block.source.data };
          }
          return { type: 'text', text: '[Image]' };
        default:
          return { type: 'text', text: `[Unsupported block type: ${(block as any).type}]` };
      }
    });
  }, []);

  /**
   * 获取消息内容块
   * 支持从 raw 字段解析复杂内容
   */
  const contentBlocks = useMemo((): ContentPart[] => {
    // 如果有 raw 字段，解析为内容块
    if (message.raw && typeof message.raw !== 'string') {
      const raw = message.raw as { content?: ClaudeRawContent[] };
      if (raw.content && Array.isArray(raw.content)) {
        return parseRawContent(raw.content);
      }
    }
    // 如果有 attachments，直接使用
    if (message.attachments && message.attachments.length > 0) {
      return message.attachments;
    }
    // 否则作为文本处理
    return [{ type: 'text', text: message.content }];
  }, [message.raw, message.attachments, message.content, parseRawContent]);

  const renderedContent = useMemo(() => {
    // 使用 ContentBlockRenderer 渲染内容块
    return contentBlocks.map((block, index) => (
      <ContentBlockRenderer
        key={`${message.id}-block-${index}`}
        block={block}
        messageIndex={0}
        messageType={message.role}
        isStreaming={isStreaming && index === contentBlocks.length - 1}
        isLastMessage={true}
        isLastBlock={index === contentBlocks.length - 1}
      />
    ));
  }, [contentBlocks, message.id, message.role, isStreaming]);

  if (isSystem) {
    return (
      <div className={cn('flex justify-center my-4', className)}>
        <div className="px-4 py-2 rounded-full text-xs font-medium bg-systemMessage/10 text-systemMessage">
          {message.content}
        </div>
      </div>
    );
  }

  return (
    <div
      className={cn(
        'group relative flex gap-3 py-4 px-4 cursor-pointer transition-colors hover:bg-muted/30',
        isUser ? 'flex-row-reverse' : 'flex-row',
        !isHovered && !isUser && 'opacity-80',
        isSelected && !isUser && 'ring-2 ring-primary rounded-lg',
        className
      )}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onClick={handleSelect}
    >
      {/* Avatar */}
      <div
        className={cn(
          'flex-shrink-0 w-9 h-9 rounded-full flex items-center justify-center shadow-sm',
          isUser ? 'bg-userMessage text-userMessage-foreground' : 'bg-aiMessage text-aiMessage-foreground'
        )}
      >
        {isUser ? (
          <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
          </svg>
        ) : (
          <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
            <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9-2-2V4c0-1.1.9-2 2-2zm0 14H6l-2 2V4h16v12z" />
          </svg>
        )}
      </div>

      {/* Message Bubble */}
      <div
        className={cn(
          'flex flex-col gap-1 max-w-[80%]',
          isUser ? 'items-end' : 'items-start'
        )}
      >
        {/* Message Content */}
        <div
          className={cn(
            'relative px-4 py-3 rounded-2xl shadow-sm border',
            isUser
              ? 'bg-userMessage text-userMessage-foreground rounded-tr-md border-userMessage/20'
              : 'bg-aiMessage text-aiMessage-foreground rounded-tl-md border-aiMessage/10'
          )}
        >
          {renderedContent}
        </div>

        {/* Timestamp & Actions */}
        <div
          className={cn(
            'flex items-center gap-2 px-1',
            isUser ? 'flex-row-reverse' : 'flex-row'
          )}
        >
          <span className="text-xs text-foreground-muted">
            {new Date(message.timestamp).toLocaleTimeString()}
          </span>

          {/* Action buttons - visible on hover */}
          <div
            className={cn(
              'flex items-center gap-1 transition-opacity',
              isHovered ? 'opacity-100' : 'opacity-0'
            )}
          >
            {!isUser && (
              <button
                type="button"
                onClick={handleReply}
                className="p-1 rounded hover:bg-background-secondary transition-colors"
                aria-label="Reply"
              >
                <svg className="w-3.5 h-3.5 text-foreground-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h10a8 8 0 018 8v2M3 10l6 6m-6-6l6-6" />
                </svg>
              </button>
            )}

            <button
              type="button"
              onClick={handleCopy}
              className="p-1 rounded hover:bg-background-secondary transition-colors"
              aria-label="Copy"
            >
              <svg className="w-3.5 h-3.5 text-foreground-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
              </svg>
            </button>

            {!isUser && (
              <button
                type="button"
                onClick={handleQuote}
                className="p-1 rounded hover:bg-background-secondary transition-colors"
                aria-label="Quote"
                title="引用此消息"
              >
                <svg className="w-3.5 h-3.5 text-foreground-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
                </svg>
              </button>
            )}

            {isUser && (
              <button
                type="button"
                onClick={handleDelete}
                className="p-1 rounded hover:bg-background-secondary transition-colors"
                aria-label="Delete"
              >
                <svg className="w-3.5 h-3.5 text-foreground-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
});

export type { MessageItemProps };
