/**
 * useStreaming - 流式输出 Hook
 *
 * 监听EventBus上的streaming事件，将增量内容拼接为完整内容。
 * 组件卸载时自动清理事件监听器，防止内存泄漏。
 */

import { useState, useEffect, useRef, useCallback } from 'react';
import { eventBus, Events } from '@/shared/utils/event-bus';

export interface UseStreamingOptions {
  /** 流式内容块回调 */
  onChunk?: (chunk: string) => void;
  /** 流式完成回调 */
  onComplete?: (fullContent: string) => void;
  /** 流式错误回调 */
  onError?: (error: string) => void;
  /** 流式取消回调 */
  onCancel?: () => void;
}

export interface UseStreamingReturn {
  /** 当前累积的内容 */
  content: string;
  /** 是否正在流式输出 */
  isStreaming: boolean;
  /** 错误信息 */
  error: string | null;
  /** 开始监听指定消息的流式输出 */
  startStreaming: (messageId: string) => void;
  /** 停止流式输出 */
  stopStreaming: () => void;
}

/**
 * 流式输出Hook
 *
 * 监听EventBus上的streaming事件，将增量内容拼接为完整内容。
 * 组件卸载时自动清理事件监听器，防止内存泄漏。
 *
 * @param options - 配置选项
 * @returns 流式输出状态和操作方法
 */
export const useStreaming = (
  options: UseStreamingOptions = {}
): UseStreamingReturn => {
  const { onChunk, onComplete, onError, onCancel } = options;
  const [content, setContent] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const messageIdRef = useRef<string | null>(null);
  const contentRef = useRef('');
  const cleanupRef = useRef<(() => void)[]>([]);

  const startStreaming = useCallback(
    (messageId: string) => {
      // 清理之前的监听器
      cleanupRef.current.forEach((unsubscribe) => unsubscribe());
      cleanupRef.current = [];

      messageIdRef.current = messageId;
      contentRef.current = '';
      setContent('');
      setIsStreaming(true);
      setError(null);

      // 监听流式输出事件
      const unsubscribeChunk = eventBus.on(
        Events.STREAMING_CHUNK,
        (data: { messageId: string; chunk: string }) => {
          if (data.messageId === messageId) {
            contentRef.current += data.chunk;
            setContent(contentRef.current);
            onChunk?.(data.chunk);
          }
        }
      );

      const unsubscribeComplete = eventBus.on(
        Events.STREAMING_COMPLETE,
        (data: { messageId: string }) => {
          if (data.messageId === messageId) {
            setIsStreaming(false);
            onComplete?.(contentRef.current);
          }
        }
      );

      const unsubscribeError = eventBus.on(
        Events.STREAMING_ERROR,
        (data: { messageId: string; error: string }) => {
          if (data.messageId === messageId) {
            setError(data.error);
            setIsStreaming(false);
            onError?.(data.error);
          }
        }
      );

      const unsubscribeCancel = eventBus.on(
        Events.STREAMING_CANCEL,
        (data: { messageId: string }) => {
          if (data.messageId === messageId) {
            setIsStreaming(false);
            onCancel?.();
          }
        }
      );

      cleanupRef.current = [
        unsubscribeChunk,
        unsubscribeComplete,
        unsubscribeError,
        unsubscribeCancel
      ];
    },
    [onChunk, onComplete, onError, onCancel]
  );

  const stopStreaming = useCallback(() => {
    if (messageIdRef.current) {
      window.ccBackend?.cancelStreaming(messageIdRef.current);
    }
    setIsStreaming(false);

    cleanupRef.current.forEach((unsubscribe) => unsubscribe());
    cleanupRef.current = [];
  }, []);

  // 组件卸载时清理
  useEffect(() => {
    return () => {
      cleanupRef.current.forEach((unsubscribe) => unsubscribe());
      cleanupRef.current = [];
    };
  }, []);

  return {
    content,
    isStreaming,
    error,
    startStreaming,
    stopStreaming
  };
};
