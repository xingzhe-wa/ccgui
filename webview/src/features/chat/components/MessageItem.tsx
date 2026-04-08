/**
 * MessageItem - 消息列表项组件
 */

import { memo, useState, useCallback, useMemo } from 'react';
import { cn } from '@/shared/utils/cn';
import { MarkdownRenderer } from './MarkdownRenderer';
import type { ChatMessage } from '@/shared/types';

interface MessageItemProps {
  message: ChatMessage;
  onReply?: (messageId: string) => void;
  onDelete?: (messageId: string) => void;
  onCopy?: (messageId: string, content: string) => void;
  className?: string;
}

export const MessageItem = memo<MessageItemProps>(function MessageItem({
  message,
  onReply,
  onDelete,
  onCopy,
  className
}) {
  const [isHovered, setIsHovered] = useState(false);

  const isUser = message.role === 'user';
  const isSystem = message.role === 'system';
  const isStreaming = message.status === 'streaming';

  const handleReply = useCallback(() => {
    onReply?.(message.id);
  }, [message.id, onReply]);

  const handleDelete = useCallback(() => {
    onDelete?.(message.id);
  }, [message.id, onDelete]);

  const handleCopy = useCallback(() => {
    onCopy?.(message.id, message.content);
  }, [message.id, message.content, onCopy]);

  const renderedContent = useMemo(() => {
    // Render text content
    const contentElement = (
      <MarkdownRenderer
        content={message.content}
        className="text-sm leading-relaxed"
      />
    );

    // Render attachments if any
    const attachmentsElement = message.attachments && message.attachments.length > 0 && (
      <div className="flex flex-wrap gap-2 mt-2">
        {message.attachments.map((attachment, index) => {
          if (attachment.type === 'image') {
            return (
              <img
                key={index}
                src={`data:${attachment.mimeType};base64,${attachment.data}`}
                alt={attachment.mimeType}
                className="max-w-[300px] rounded-lg"
                loading="lazy"
              />
            );
          }
          if (attachment.type === 'file') {
            return (
              <div
                key={index}
                className="flex items-center gap-2 p-2 rounded-md bg-background-secondary"
              >
                <svg
                  className="w-4 h-4 text-foreground-muted"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                  />
                </svg>
                <span className="text-sm text-foreground truncate flex-1">{attachment.name}</span>
                {attachment.size && (
                  <span className="text-xs text-foreground-muted">
                    {(attachment.size / 1024).toFixed(1)} KB
                  </span>
                )}
              </div>
            );
          }
          return null;
        })}
      </div>
    );

    return (
      <>
        {contentElement}
        {attachmentsElement}
      </>
    );
  }, [message.content, message.attachments]);

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
        'group relative flex gap-3 py-4 px-4',
        isUser ? 'flex-row-reverse' : 'flex-row',
        !isHovered && !isUser && 'opacity-70',
        className
      )}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      {/* Avatar */}
      <div
        className={cn(
          'flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center',
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
          'flex flex-col gap-1 max-w-[70%]',
          isUser ? 'items-end' : 'items-start'
        )}
      >
        {/* Message Content */}
        <div
          className={cn(
            'relative px-4 py-3 rounded-2xl',
            isUser
              ? 'bg-userMessage text-userMessage-foreground rounded-tr-md'
              : 'bg-aiMessage text-aiMessage-foreground rounded-tl-md',
            isStreaming && 'animate-pulse'
          )}
        >
          {renderedContent}

          {/* Streaming cursor */}
          {isStreaming && (
            <span className="inline-block w-2 h-4 ml-1 bg-primary animate-pulse" />
          )}
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
