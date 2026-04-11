/**
 * 流式输出状态 Store
 *
 * 管理流式输出的状态，包括当前流式输出的消息ID、
 * 内容缓冲、是否正在流式输出、错误状态等。
 */

import { create } from 'zustand';
import type { ID } from '@/shared/types';

interface StreamingState {
  /** 当前流式输出的消息ID */
  streamingMessageId: ID | null;

  /** 流式输出内容缓冲 */
  streamingBuffer: string;

  /** 是否正在流式输出 */
  isStreaming: boolean;

  /** 流式输出错误 */
  streamingError: string | null;

  // ========== 操作 ==========

  /**
   * 开始流式输出
   * @param messageId 消息ID
   */
  startStreaming: (messageId: ID) => void;

  /**
   * 追加内容块
   * @param chunk 增量内容
   */
  appendChunk: (chunk: string) => void;

  /**
   * 完成流式输出
   */
  finishStreaming: () => void;

  /**
   * 设置流式输出错误
   * @param error 错误信息
   */
  setStreamingError: (error: string) => void;

  /**
   * 取消流式输出
   */
  cancelStreaming: () => void;

  /**
   * 重置状态
   */
  reset: () => void;
}

export const useStreamingStore = create<StreamingState>((set, get) => ({
  // ========== 初始状态 ==========
  streamingMessageId: null,
  streamingBuffer: '',
  isStreaming: false,
  streamingError: null,

  // ========== 操作 ==========

  startStreaming: (messageId) => {
    set({
      streamingMessageId: messageId,
      streamingBuffer: '',
      isStreaming: true,
      streamingError: null
    });
  },

  appendChunk: (chunk) => {
    set((state) => ({
      streamingBuffer: state.streamingBuffer + chunk
    }));
  },

  finishStreaming: () => {
    set({
      streamingMessageId: null,
      streamingBuffer: '',
      isStreaming: false,
      streamingError: null
    });
  },

  setStreamingError: (error) => {
    set({
      streamingError: error,
      isStreaming: false
    });
  },

  cancelStreaming: () => {
    const { streamingMessageId } = get();
    if (streamingMessageId) {
      window.ccBackend?.cancelStreaming(streamingMessageId);
    }
    set({
      streamingMessageId: null,
      streamingBuffer: '',
      isStreaming: false,
      streamingError: null
    });
  },

  reset: () => {
    set({
      streamingMessageId: null,
      streamingBuffer: '',
      isStreaming: false,
      streamingError: null
    });
  }
}));

/**
 * 优化的流式状态选择器
 * 只选择需要的字段，避免不必要的后续渲染
 */
export const useStreamingState = () =>
  useStreamingStore((s) => ({
    streamingMessageId: s.streamingMessageId,
    isStreaming: s.isStreaming,
    streamingBuffer: s.streamingBuffer
  }));
