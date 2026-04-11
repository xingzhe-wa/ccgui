/**
 * messageSync.test.ts - messageSync 单元测试
 *
 * 测试消息同步策略和规范化函数
 */

import { describe, it, expect } from 'vitest';
import {
  preserveMessageIdentity,
  preserveLastAssistantIdentity,
  preserveStreamingAssistantContent,
  appendOptimisticMessageIfMissing,
  stripDuplicateTrailingToolMessages,
  ensureStreamingAssistantInList,
  syncMessages,
  normalizeMessageContent
} from '../messageSync';
import type { ChatMessage } from '@/shared/types';
import { MessageRole, MessageStatus } from '@/shared/types';

function createTestMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    id: `msg-${Math.random().toString(36).substr(2, 9)}`,
    role: MessageRole.USER,
    content: 'Test message',
    timestamp: Date.now(),
    status: MessageStatus.COMPLETED,
    ...overrides
  };
}

describe('messageSync', () => {
  describe('preserveMessageIdentity', () => {
    it('应该保留时间戳当消息ID相同时', () => {
      const prevTimestamp = Date.now() - 1000;
      const prevMessage = createTestMessage({
        id: 'msg-1',
        timestamp: prevTimestamp,
        content: 'Old content'
      });
      const nextMessage = createTestMessage({
        id: 'msg-1',
        timestamp: Date.now(),
        content: 'New content'
      });

      const result = preserveMessageIdentity(prevMessage, nextMessage);

      expect(result.timestamp).toBe(prevTimestamp);
      expect(result.content).toBe('New content');
    });

    it('应该合并 metadata', () => {
      const prevMessage = createTestMessage({
        id: 'msg-1',
        metadata: { key1: 'value1', key2: 'old' }
      });
      const nextMessage = createTestMessage({
        id: 'msg-1',
        metadata: { key2: 'new', key3: 'value3' }
      });

      const result = preserveMessageIdentity(prevMessage, nextMessage);

      // prev metadata spreads last, so its values win (identity preservation)
      expect(result.metadata).toEqual({
        key1: 'value1',
        key2: 'old',
        key3: 'value3'
      });
    });

    it('当消息ID不同时应该返回新消息不变', () => {
      const prevMessage = createTestMessage({
        id: 'msg-1',
        timestamp: Date.now() - 1000
      });
      const nextMessage = createTestMessage({
        id: 'msg-2',
        timestamp: Date.now()
      });

      const result = preserveMessageIdentity(prevMessage, nextMessage);

      expect(result.id).toBe('msg-2');
      expect(result.timestamp).toBe(nextMessage.timestamp);
    });
  });

  describe('preserveLastAssistantIdentity', () => {
    it('当新列表为空时应该返回空', () => {
      const prevList = [createTestMessage({ role: MessageRole.ASSISTANT })];
      const result = preserveLastAssistantIdentity(prevList, []);
      expect(result).toEqual([]);
    });

    it('当旧列表为空时应该返回新列表', () => {
      const nextList = [createTestMessage({ role: MessageRole.ASSISTANT })];
      const result = preserveLastAssistantIdentity([], nextList);
      expect(result).toEqual(nextList);
    });

    it('应该保留最后一条 assistant 消息的身份', () => {
      const assistantMsg = createTestMessage({
        id: 'assistant-1',
        role: MessageRole.ASSISTANT,
        content: 'Hello',
        timestamp: 1000
      });
      const prevList = [
        createTestMessage({ role: MessageRole.USER }),
        assistantMsg
      ];

      const newAssistantMsg = createTestMessage({
        id: 'assistant-1',
        role: MessageRole.ASSISTANT,
        content: 'Hello updated',
        timestamp: 2000
      });
      const nextList = [
        createTestMessage({ role: MessageRole.USER }),
        newAssistantMsg
      ];

      const result = preserveLastAssistantIdentity(prevList, nextList);

      expect(result[1]!.timestamp).toBe(1000);
      expect(result[1]!.content).toBe('Hello updated');
    });

    it('应该保留最后一条 assistant 消息而非所有 assistant 消息', () => {
      const firstAssistant = createTestMessage({
        id: 'assistant-1',
        role: MessageRole.ASSISTANT,
        timestamp: 1000
      });
      const lastAssistant = createTestMessage({
        id: 'assistant-2',
        role: MessageRole.ASSISTANT,
        timestamp: 2000
      });
      const prevList = [firstAssistant, lastAssistant];

      const nextList = [
        createTestMessage({ id: 'assistant-1', role: MessageRole.ASSISTANT }),
        createTestMessage({ id: 'assistant-2', role: MessageRole.ASSISTANT })
      ];

      const result = preserveLastAssistantIdentity(prevList, nextList);

      // 只有最后一条 assistant 保留原时间戳
      expect(result[0]!.timestamp).not.toBe(1000);
      expect(result[1]!.timestamp).toBe(2000);
    });

    it('当没有 assistant 消息时应该返回新列表', () => {
      const prevList = [createTestMessage({ role: MessageRole.USER })];
      const nextList = [createTestMessage({ role: MessageRole.USER })];
      const result = preserveLastAssistantIdentity(prevList, nextList);
      expect(result).toEqual(nextList);
    });
  });

  describe('preserveStreamingAssistantContent', () => {
    it('当没有流式时应该返回新列表', () => {
      const prevList = [createTestMessage({ content: 'old' })];
      const nextList = [createTestMessage({ content: 'new' })];
      const isStreamingRef = { current: false };
      const streamingMessageIdRef = { current: null };

      const result = preserveStreamingAssistantContent(
        prevList,
        nextList,
        isStreamingRef,
        streamingMessageIdRef
      );

      expect(result).toEqual(nextList);
    });

    it('当流式消息ID不存在时应该返回新列表', () => {
      const prevList = [createTestMessage({ role: MessageRole.ASSISTANT, content: 'old' })];
      const nextList = [createTestMessage({ role: MessageRole.ASSISTANT, content: 'new' })];
      const isStreamingRef = { current: true };
      const streamingMessageIdRef = { current: 'non-existent' };

      const result = preserveStreamingAssistantContent(
        prevList,
        nextList,
        isStreamingRef,
        streamingMessageIdRef
      );

      expect(result).toEqual(nextList);
    });

    it('当新内容更短时应该保留旧内容', () => {
      const streamingId = 'streaming-msg';
      const prevList = [
        createTestMessage({
          id: streamingId,
          role: MessageRole.ASSISTANT,
          content: 'Longer content here'
        })
      ];
      const nextList = [
        createTestMessage({
          id: streamingId,
          role: MessageRole.ASSISTANT,
          content: 'Short'
        })
      ];
      const isStreamingRef = { current: true };
      const streamingMessageIdRef = { current: streamingId };

      const result = preserveStreamingAssistantContent(
        prevList,
        nextList,
        isStreamingRef,
        streamingMessageIdRef
      );

      expect(result[0]!.content).toBe('Longer content here');
    });

    it('当新内容更长时应该使用新内容', () => {
      const streamingId = 'streaming-msg';
      const prevList = [
        createTestMessage({
          id: streamingId,
          role: MessageRole.ASSISTANT,
          content: 'Short'
        })
      ];
      const nextList = [
        createTestMessage({
          id: streamingId,
          role: MessageRole.ASSISTANT,
          content: 'Longer content here'
        })
      ];
      const isStreamingRef = { current: true };
      const streamingMessageIdRef = { current: streamingId };

      const result = preserveStreamingAssistantContent(
        prevList,
        nextList,
        isStreamingRef,
        streamingMessageIdRef
      );

      expect(result[0]!.content).toBe('Longer content here');
    });
  });

  describe('appendOptimisticMessageIfMissing', () => {
    it('当没有乐观消息ID时应该返回新列表', () => {
      const prevList: ChatMessage[] = [];
      const nextList = [createTestMessage()];
      const result = appendOptimisticMessageIfMissing(prevList, nextList, null);
      expect(result).toEqual(nextList);
    });

    it('当乐观消息已存在时不应该追加', () => {
      const optimisticMsg = createTestMessage({ id: 'optimistic-1' });
      const nextList = [optimisticMsg];

      const result = appendOptimisticMessageIfMissing(
        [],
        nextList,
        'optimistic-1'
      );

      expect(result).toHaveLength(1);
    });

    it('当乐观消息不存在时应该追加', () => {
      const optimisticMsg = createTestMessage({ id: 'optimistic-1' });
      const prevList = [optimisticMsg];
      const nextList: ChatMessage[] = [];

      const result = appendOptimisticMessageIfMissing(
        prevList,
        nextList,
        'optimistic-1'
      );

      expect(result).toHaveLength(1);
      expect(result[0]!.id).toBe('optimistic-1');
    });

    it('应该保留新列表中已有的其他消息', () => {
      const optimisticMsg = createTestMessage({ id: 'optimistic-1', role: MessageRole.USER });
      const otherMsg = createTestMessage({ id: 'other', role: MessageRole.ASSISTANT });
      const prevList = [optimisticMsg];
      const nextList = [otherMsg];

      const result = appendOptimisticMessageIfMissing(
        prevList,
        nextList,
        'optimistic-1'
      );

      expect(result).toHaveLength(2);
      expect(result[0]!.id).toBe('other');
      expect(result[1]!.id).toBe('optimistic-1');
    });
  });

  describe('stripDuplicateTrailingToolMessages', () => {
    it('应该移除尾部重复的工具消息', () => {
      const msg1 = createTestMessage({
        id: '1',
        role: MessageRole.ASSISTANT,
        content: 'Using tool'
      });
      const duplicate = createTestMessage({
        id: '2',
        role: MessageRole.ASSISTANT,
        content: 'Using tool'
      });

      const result = stripDuplicateTrailingToolMessages([msg1, duplicate]);

      expect(result).toHaveLength(1);
      expect(result[0]!.id).toBe('1');
    });

    it('应该保留非重复的消息', () => {
      const msg1 = createTestMessage({
        role: MessageRole.ASSISTANT,
        content: 'First response'
      });
      const msg2 = createTestMessage({
        role: MessageRole.ASSISTANT,
        content: 'Second response'
      });

      const result = stripDuplicateTrailingToolMessages([msg1, msg2]);

      expect(result).toHaveLength(2);
    });

    it('应该保留非尾部重复消息', () => {
      const msg1 = createTestMessage({
        role: MessageRole.ASSISTANT,
        content: 'Using tool'
      });
      const msg2 = createTestMessage({
        role: MessageRole.USER,
        content: 'Continue'
      });
      const duplicate = createTestMessage({
        role: MessageRole.ASSISTANT,
        content: 'Using tool'
      });

      const result = stripDuplicateTrailingToolMessages([msg1, msg2, duplicate]);

      // msg2 不是 assistant 角色，所以不被认为是重复
      expect(result).toHaveLength(3);
    });

    it('应该处理单条消息', () => {
      const msg = createTestMessage();
      const result = stripDuplicateTrailingToolMessages([msg]);
      expect(result).toHaveLength(1);
    });

    it('应该处理空列表', () => {
      const result = stripDuplicateTrailingToolMessages([]);
      expect(result).toEqual([]);
    });
  });

  describe('ensureStreamingAssistantInList', () => {
    it('当不是流式时应该返回原列表', () => {
      const resultList = [createTestMessage()];
      const result = ensureStreamingAssistantInList(
        [],
        resultList,
        false,
        undefined
      );
      expect(result).toEqual(resultList);
    });

    it('当列表已有流式消息时应该返回原列表', () => {
      const streamingMsg = createTestMessage({
        role: MessageRole.ASSISTANT,
        isStreaming: true
      });
      const resultList = [streamingMsg];

      const result = ensureStreamingAssistantInList(
        [],
        resultList,
        true,
        1
      );

      expect(result).toEqual(resultList);
    });

    it('当需要添加流式消息时应该从旧列表恢复', () => {
      const prevStreamingMsg = createTestMessage({
        id: 'prev-streaming',
        role: MessageRole.ASSISTANT,
        isStreaming: true
      });
      const resultList: ChatMessage[] = [];

      const result = ensureStreamingAssistantInList(
        [prevStreamingMsg],
        resultList,
        true,
        1
      );

      expect(result).toHaveLength(1);
      expect(result[0]!.id).toBe('prev-streaming');
      expect((result[0] as any).__turnId).toBe(1);
    });
  });

  describe('syncMessages', () => {
    it('应该组合所有同步策略', () => {
      const prevList: ChatMessage[] = [];
      const nextList: ChatMessage[] = [];

      const result = syncMessages({
        prevList,
        nextList,
        isStreaming: false
      });

      expect(result).toEqual(nextList);
    });

    it('应该正确处理流式场景', () => {
      const streamingId = 'streaming-msg';
      const prevList = [
        createTestMessage({
          id: streamingId,
          role: MessageRole.ASSISTANT,
          content: 'Streaming content'
        })
      ];
      const nextList = [
        createTestMessage({
          id: streamingId,
          role: MessageRole.ASSISTANT,
          content: 'Shor'
        })
      ];

      const result = syncMessages({
        prevList,
        nextList,
        isStreaming: true,
        streamingMessageId: streamingId
      });

      // 应该保留旧内容因为更短
      expect(result[0]!.content).toBe('Streaming content');
    });
  });

  describe('normalizeMessageContent', () => {
    it('应该返回原始消息当没有 raw 字段时', () => {
      const message = createTestMessage({ content: 'Plain text' });
      const result = normalizeMessageContent(message);
      expect(result.content).toBe('Plain text');
    });

    it('应该解析 raw.content 数组', () => {
      const message = createTestMessage({
        content: '',
        raw: {
          content: [
            { type: 'text', text: 'Hello ' },
            { type: 'text', text: 'World' }
          ]
        } as any
      });

      const result = normalizeMessageContent(message);

      expect(result.content).toBe('Hello \n\nWorld');
    });

    it('应该处理 thinking 块', () => {
      const message = createTestMessage({
        content: '',
        raw: {
          content: [
            { type: 'thinking', thinking: 'I am thinking', text: 'Final answer' }
          ]
        } as any
      });

      const result = normalizeMessageContent(message);

      expect(result.content).toContain('[思考过程]');
      expect(result.content).toContain('I am thinking');
      expect(result.content).toContain('Final answer');
    });

    it('应该处理 tool_use 块', () => {
      const message = createTestMessage({
        content: '',
        raw: {
          content: [
            {
              type: 'tool_use',
              id: 'tool-1',
              name: 'bash',
              input: { command: 'ls' }
            }
          ]
        } as any
      });

      const result = normalizeMessageContent(message);

      expect(result.content).toContain('[调用工具: bash]');
      expect(result.content).toContain('"command": "ls"');
    });

    it('应该处理 image 块', () => {
      const message = createTestMessage({
        content: '',
        raw: {
          content: [
            {
              type: 'image',
              source: {
                type: 'base64',
                media_type: 'image/png',
                data: 'abc123'
              }
            }
          ]
        } as any
      });

      const result = normalizeMessageContent(message);

      expect(result.attachments).toBeDefined();
      expect(result.attachments).toHaveLength(1);
      expect(result.attachments![0]!.type).toBe('image');
    });
  });
});
