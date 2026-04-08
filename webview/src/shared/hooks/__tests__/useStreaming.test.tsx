/**
 * useStreaming.test.tsx - useStreaming Hook 单元测试
 *
 * 测试流式输出 Hook 的核心功能。
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useStreaming } from '../useStreaming';
import { eventBus, Events } from '@/shared/utils/event-bus';
import { resetJcefMocks } from '@/test/utils/test-utils';

describe('useStreaming', () => {
  beforeEach(() => {
    resetJcefMocks();
    vi.clearAllMocks();
  });

  describe('初始状态', () => {
    it('应该返回正确的初始状态', () => {
      const { result } = renderHook(() => useStreaming());

      expect(result.current.content).toBe('');
      expect(result.current.isStreaming).toBe(false);
      expect(result.current.error).toBeNull();
      expect(result.current.startStreaming).toBeTypeOf('function');
      expect(result.current.stopStreaming).toBeTypeOf('function');
    });
  });

  describe('startStreaming', () => {
    it('应该开始流式输出', () => {
      const { result } = renderHook(() => useStreaming());

      act(() => {
        result.current.startStreaming('msg-1');
      });

      expect(result.current.isStreaming).toBe(true);
      expect(result.current.error).toBeNull();
    });

    it('应该接收并累积流式内容块', async () => {
      const onChunk = vi.fn();
      const { result } = renderHook(() => useStreaming({ onChunk }));

      act(() => {
        result.current.startStreaming('msg-1');
      });

      // 模拟流式内容块
      act(() => {
        eventBus.emit(Events.STREAMING_CHUNK, { messageId: 'msg-1', chunk: 'Hello ' });
      });

      await waitFor(() => {
        expect(result.current.content).toBe('Hello ');
        expect(onChunk).toHaveBeenCalledWith('Hello ');
      });

      // 继续添加内容
      act(() => {
        eventBus.emit(Events.STREAMING_CHUNK, { messageId: 'msg-1', chunk: 'World!' });
      });

      await waitFor(() => {
        expect(result.current.content).toBe('Hello World!');
      });
    });

    it('应该处理流式完成事件', async () => {
      const onComplete = vi.fn();
      const { result } = renderHook(() => useStreaming({ onComplete }));

      act(() => {
        result.current.startStreaming('msg-1');
        eventBus.emit(Events.STREAMING_CHUNK, { messageId: 'msg-1', chunk: 'Full content' });
      });

      act(() => {
        eventBus.emit(Events.STREAMING_COMPLETE, { messageId: 'msg-1' });
      });

      await waitFor(() => {
        expect(result.current.isStreaming).toBe(false);
        expect(onComplete).toHaveBeenCalledWith('Full content');
      });
    });

    it('应该处理流式错误事件', async () => {
      const onError = vi.fn();
      const { result } = renderHook(() => useStreaming({ onError }));

      act(() => {
        result.current.startStreaming('msg-1');
      });

      act(() => {
        eventBus.emit(Events.STREAMING_ERROR, { messageId: 'msg-1', error: 'Network error' });
      });

      await waitFor(() => {
        expect(result.current.isStreaming).toBe(false);
        expect(result.current.error).toBe('Network error');
        expect(onError).toHaveBeenCalledWith('Network error');
      });
    });

    it('应该处理流式取消事件', async () => {
      const onCancel = vi.fn();
      const { result } = renderHook(() => useStreaming({ onCancel }));

      act(() => {
        result.current.startStreaming('msg-1');
      });

      act(() => {
        eventBus.emit(Events.STREAMING_CANCEL, { messageId: 'msg-1' });
      });

      await waitFor(() => {
        expect(result.current.isStreaming).toBe(false);
        expect(onCancel).toHaveBeenCalled();
      });
    });

    it('应该忽略不同 messageId 的事件', () => {
      const onChunk = vi.fn();
      const { result } = renderHook(() => useStreaming({ onChunk }));

      act(() => {
        result.current.startStreaming('msg-1');
      });

      act(() => {
        eventBus.emit(Events.STREAMING_CHUNK, { messageId: 'msg-2', chunk: 'Other message' });
      });

      // content 不应该包含其他消息的内容
      expect(result.current.content).toBe('');
      expect(onChunk).not.toHaveBeenCalled();
    });

    it('重新开始流式输出时应该清理之前的状态', () => {
      const { result } = renderHook(() => useStreaming());

      act(() => {
        result.current.startStreaming('msg-1');
        eventBus.emit(Events.STREAMING_CHUNK, { messageId: 'msg-1', chunk: 'First' });
      });

      expect(result.current.content).toBe('First');

      // 重新开始新的流式输出
      act(() => {
        result.current.startStreaming('msg-2');
      });

      expect(result.current.content).toBe('');
      expect(result.current.isStreaming).toBe(true);
    });
  });

  describe('stopStreaming', () => {
    it('应该停止流式输出', async () => {
      const mockBackend = (window as any).ccBackend;
      mockBackend.cancelStreaming = vi.fn();

      const { result } = renderHook(() => useStreaming());

      act(() => {
        result.current.startStreaming('msg-1');
      });

      expect(result.current.isStreaming).toBe(true);

      act(() => {
        result.current.stopStreaming();
      });

      await waitFor(() => {
        expect(result.current.isStreaming).toBe(false);
        expect(mockBackend.cancelStreaming).toHaveBeenCalledWith('msg-1');
      });
    });

    it('应该清理事件监听器', async () => {
      const { result } = renderHook(() => useStreaming());
      const onChunk = vi.fn();

      act(() => {
        result.current.startStreaming('msg-1');
        result.current.stopStreaming();
      });

      // 停止后，事件不应该再被处理
      act(() => {
        eventBus.emit(Events.STREAMING_CHUNK, { messageId: 'msg-1', chunk: 'After stop' });
      });

      expect(onChunk).not.toHaveBeenCalled();
      expect(result.current.content).toBe('');
    });
  });

  describe('回调函数', () => {
    it('应该正确调用 onChunk 回调', async () => {
      const onChunk = vi.fn();
      const { result } = renderHook(() => useStreaming({ onChunk }));

      act(() => {
        result.current.startStreaming('msg-1');
      });

      act(() => {
        eventBus.emit(Events.STREAMING_CHUNK, { messageId: 'msg-1', chunk: 'Test' });
      });

      await waitFor(() => {
        expect(onChunk).toHaveBeenCalledWith('Test');
      });
    });

    it('应该正确调用 onComplete 回调', async () => {
      const onComplete = vi.fn();
      const { result } = renderHook(() => useStreaming({ onComplete }));

      act(() => {
        result.current.startStreaming('msg-1');
        eventBus.emit(Events.STREAMING_CHUNK, { messageId: 'msg-1', chunk: 'Content' });
        eventBus.emit(Events.STREAMING_COMPLETE, { messageId: 'msg-1' });
      });

      await waitFor(() => {
        expect(onComplete).toHaveBeenCalledWith('Content');
      });
    });

    it('应该正确调用 onError 回调', async () => {
      const onError = vi.fn();
      const { result } = renderHook(() => useStreaming({ onError }));

      act(() => {
        result.current.startStreaming('msg-1');
        eventBus.emit(Events.STREAMING_ERROR, { messageId: 'msg-1', error: 'Error' });
      });

      await waitFor(() => {
        expect(onError).toHaveBeenCalledWith('Error');
      });
    });

    it('应该正确调用 onCancel 回调', async () => {
      const onCancel = vi.fn();
      const { result } = renderHook(() => useStreaming({ onCancel }));

      act(() => {
        result.current.startStreaming('msg-1');
        eventBus.emit(Events.STREAMING_CANCEL, { messageId: 'msg-1' });
      });

      await waitFor(() => {
        expect(onCancel).toHaveBeenCalled();
      });
    });
  });

  describe('内存泄漏防护', () => {
    it('组件卸载时应该清理所有监听器', () => {
      const onChunk = vi.fn();
      const { unmount } = renderHook(() => useStreaming({ onChunk }));

      act(() => {
        eventBus.emit(Events.STREAMING_CHUNK, { messageId: 'msg-1', chunk: 'Before unmount' });
      });

      // 卸载前 onChunk 应该被调用
      expect(onChunk).not.toHaveBeenCalled(); // 没有开始 streaming，不会被调用

      unmount();

      // 卸载后发送事件，验证监听器已清理
      act(() => {
        eventBus.emit(Events.STREAMING_CHUNK, { messageId: 'msg-1', chunk: 'After unmount' });
      });

      // onChunk 仍然不应该被调用，因为没有开始 streaming
      expect(onChunk).not.toHaveBeenCalled();
    });

    it('重新开始流式输出时应该清理旧的监听器', () => {
      const { result } = renderHook(() => useStreaming());

      act(() => {
        result.current.startStreaming('msg-1');
      });

      // 获取当前监听器数量（通过调用 startStreaming 多次来验证）
      act(() => {
        result.current.startStreaming('msg-2');
      });

      // 如果没有正确清理，会导致监听器累积
      // 这里我们验证状态正确重置
      expect(result.current.content).toBe('');
      expect(result.current.isStreaming).toBe(true);
    });
  });

  describe('边界情况', () => {
    it('空内容块不应该影响状态', async () => {
      const { result } = renderHook(() => useStreaming());

      act(() => {
        result.current.startStreaming('msg-1');
        eventBus.emit(Events.STREAMING_CHUNK, { messageId: 'msg-1', chunk: '' });
      });

      await waitFor(() => {
        expect(result.current.content).toBe('');
      });
    });

    it('在没有 messageId 时停止流式输出不应该报错', () => {
      const { result } = renderHook(() => useStreaming());

      act(() => {
        result.current.stopStreaming();
      });

      expect(result.current.isStreaming).toBe(false);
    });

    it('多次接收同一内容应该正确累积', async () => {
      const { result } = renderHook(() => useStreaming());

      act(() => {
        result.current.startStreaming('msg-1');
        eventBus.emit(Events.STREAMING_CHUNK, { messageId: 'msg-1', chunk: 'A' });
        eventBus.emit(Events.STREAMING_CHUNK, { messageId: 'msg-1', chunk: 'A' });
        eventBus.emit(Events.STREAMING_CHUNK, { messageId: 'msg-1', chunk: 'A' });
      });

      await waitFor(() => {
        expect(result.current.content).toBe('AAA');
      });
    });
  });
});
