/**
 * StreamingMessage - 流式消息组件
 *
 * 订阅EventBus的streaming事件，实时渲染AI回复。
 * 使用requestAnimationFrame避免高频chunk导致渲染阻塞。
 * 流式输出完成后自动取消订阅。
 */

import { memo, useEffect, useState, useRef } from 'react';
import { MarkdownRenderer } from '@/features/chat/components/MarkdownRenderer';
import { eventBus, Events } from '@/shared/utils/event-bus';
import { TypingCursor } from './TypingCursor';
import { cn } from '@/shared/utils/cn';

export interface StreamingMessageProps {
  /** 消息ID */
  messageId: string;
  /** 流式完成回调 */
  onComplete?: () => void;
  /** 错误回调 */
  onError?: (error: string) => void;
  className?: string;
}

/**
 * 流式消息组件
 *
 * 订阅EventBus的streaming事件，实时渲染AI回复。
 * 使用requestAnimationFrame避免高频chunk导致渲染阻塞。
 * 流式输出完成后自动取消订阅。
 */
export const StreamingMessage = memo<StreamingMessageProps>(function StreamingMessage({
  messageId,
  onComplete,
  onError,
  className
}: StreamingMessageProps) {
  const [content, setContent] = useState('');
  const [isStreaming, setIsStreaming] = useState(true);
  const contentRef = useRef('');
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let isMounted = true;
    let animationFrameId: number;

    const unsubscribeChunk = eventBus.on(
      Events.STREAMING_CHUNK,
      (data: { messageId: string; chunk: string }) => {
        if (data.messageId === messageId && isMounted) {
          cancelAnimationFrame(animationFrameId);
          animationFrameId = requestAnimationFrame(() => {
            if (!isMounted) return;
            contentRef.current += data.chunk;
            setContent(contentRef.current);

            // 自动滚动到底部
            if (containerRef.current) {
              containerRef.current.scrollTop = containerRef.current.scrollHeight;
            }
          });
        }
      }
    );

    const unsubscribeComplete = eventBus.on(
      Events.STREAMING_COMPLETE,
      (data: { messageId: string }) => {
        if (data.messageId === messageId && isMounted) {
          setIsStreaming(false);
          onComplete?.();
        }
      }
    );

    const unsubscribeError = eventBus.on(
      Events.STREAMING_ERROR,
      (data: { messageId: string; error: string }) => {
        if (data.messageId === messageId && isMounted) {
          setIsStreaming(false);
          setContent((prev) => prev + `\n\n[错误: ${data.error}]`);
          onError?.(data.error);
        }
      }
    );

    const unsubscribeCancel = eventBus.on(
      Events.STREAMING_CANCEL,
      (data: { messageId: string }) => {
        if (data.messageId === messageId && isMounted) {
          setIsStreaming(false);
        }
      }
    );

    return () => {
      isMounted = false;
      cancelAnimationFrame(animationFrameId);
      unsubscribeChunk();
      unsubscribeComplete();
      unsubscribeError();
      unsubscribeCancel();
    };
  }, [messageId, onComplete, onError]);

  return (
    <div
      ref={containerRef}
      className={cn('message ai-message', isStreaming && 'streaming', className)}
    >
      <MarkdownRenderer content={content} />
      {isStreaming && <TypingCursor />}
    </div>
  );
});
