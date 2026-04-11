/**
 * messageSync - 消息同步策略
 *
 * 对应架构文档中的消息同步策略
 * 确保流式更新时消息列表的稳定性和一致性
 */

import type { ChatMessage } from '@/shared/types';

/**
 * 保留消息身份
 * 确保消息在更新时保持其 timestamp 和 uuid 不变
 */
export function preserveMessageIdentity(
  prevMessage: ChatMessage,
  nextMessage: ChatMessage
): ChatMessage {
  // 如果 ID 相同，保留原始的时间戳和元数据
  if (prevMessage.id === nextMessage.id) {
    return {
      ...nextMessage,
      timestamp: prevMessage.timestamp,
      metadata: {
        ...nextMessage.metadata,
        ...prevMessage.metadata
      }
    };
  }
  return nextMessage;
}

/**
 * 保留最后 assistant 消息的身份
 * 当消息列表更新时，确保最后一条 assistant 消息的身份不变
 */
export function preserveLastAssistantIdentity(
  prevList: ChatMessage[],
  nextList: ChatMessage[]
): ChatMessage[] {
  if (prevList.length === 0 || nextList.length === 0) {
    return nextList;
  }

  // 找到前一个列表的最后一条 assistant 消息
  const lastAssistant = [...prevList].reverse().find(msg => msg.role === 'assistant');

  if (!lastAssistant) {
    return nextList;
  }

  // 在新列表中找到对应的消息并保留身份
  return nextList.map(msg => {
    if (msg.role === 'assistant' && msg.id === lastAssistant.id) {
      return preserveMessageIdentity(lastAssistant, msg);
    }
    return msg;
  });
}

/**
 * 保留流式 assistant 内容
 * 在流式更新期间，保留正在流式输出的消息内容
 */
export function preserveStreamingAssistantContent(
  prevList: ChatMessage[],
  nextList: ChatMessage[],
  isStreamingRef: { current: boolean },
  streamingMessageIdRef: { current: string | null }
): ChatMessage[] {
  if (!isStreamingRef.current || !streamingMessageIdRef.current) {
    return nextList;
  }

  const streamingId = streamingMessageIdRef.current;

  // 在前一个列表中找到流式消息
  const prevStreamingMessage = prevList.find(msg => msg.id === streamingId);

  if (!prevStreamingMessage) {
    return nextList;
  }

  // 在新列表中保留流式消息的内容（如果新消息内容为空或更短）
  return nextList.map(msg => {
    if (msg.id === streamingId) {
      // 如果新消息内容更短（可能是中间状态），保留旧内容
      if (msg.content.length < prevStreamingMessage.content.length) {
        return {
          ...msg,
          content: prevStreamingMessage.content
        };
      }
    }
    return msg;
  });
}

/**
 * 追加乐观消息（如果缺失）
 * 当用户发送消息后，如果后端响应中没有该消息，追加到列表中
 */
export function appendOptimisticMessageIfMissing(
  prevList: ChatMessage[],
  nextList: ChatMessage[],
  optimisticMessageId: string | null
): ChatMessage[] {
  if (!optimisticMessageId) {
    return nextList;
  }

  // 检查乐观消息是否已存在于新列表中
  const exists = nextList.some(msg => msg.id === optimisticMessageId);

  if (exists) {
    return nextList;
  }

  // 从前一个列表中找到乐观消息并追加
  const optimisticMessage = prevList.find(msg => msg.id === optimisticMessageId);

  if (optimisticMessage) {
    return [...nextList, optimisticMessage];
  }

  return nextList;
}

/**
 * 去除重复的尾部工具消息
 * 移除连续重复的工具调用/结果消息
 */
export function stripDuplicateTrailingToolMessages(
  nextList: ChatMessage[]
): ChatMessage[] {
  if (nextList.length < 2) {
    return nextList;
  }

  const result: ChatMessage[] = [];
  let lastContent = '';

  for (const message of nextList) {
    // 获取消息内容的字符串表示
    const currentContent = JSON.stringify({
      role: message.role,
      content: message.content,
      attachments: message.attachments
    });

    // 如果与上一条消息内容完全相同，跳过
    if (currentContent === lastContent) {
      continue;
    }

    result.push(message);
    lastContent = currentContent;
  }

  return result;
}

/**
 * 确保流式 assistant 消息存在于列表中
 * 当流式开始时，确保有一条 assistant 消息用于接收流式内容
 */
export function ensureStreamingAssistantInList(
  prevList: ChatMessage[],
  resultList: ChatMessage[],
  isStreaming: boolean,
  turnId: number | undefined
): ChatMessage[] {
  if (!isStreaming) {
    return resultList;
  }

  // 检查是否已有流式 assistant 消息
  const hasStreamingAssistant = resultList.some(msg =>
    msg.role === 'assistant' && msg.isStreaming === true
  );

  if (hasStreamingAssistant) {
    return resultList;
  }

  // 如果前一个列表有流式消息但新列表没有，恢复它
  const prevStreamingMessage = prevList.find(msg =>
    msg.role === 'assistant' && msg.isStreaming === true
  );

  if (prevStreamingMessage) {
    // 检查是否需要更新 turnId
    const updatedMessage = {
      ...prevStreamingMessage,
      __turnId: turnId
    };

    // 追加到结果列表
    return [...resultList, updatedMessage];
  }

  return resultList;
}

/**
 * 完整的消息同步函数
 * 组合所有同步策略
 */
export interface SyncMessageOptions {
  prevList: ChatMessage[];
  nextList: ChatMessage[];
  isStreaming?: boolean;
  streamingMessageId?: string | null;
  optimisticMessageId?: string | null;
  turnId?: number;
}

export function syncMessages(options: SyncMessageOptions): ChatMessage[] {
  const {
    prevList,
    nextList,
    isStreaming = false,
    streamingMessageId = null,
    optimisticMessageId = null,
    turnId
  } = options;

  // 创建 ref 对象（模拟 React ref）
  const isStreamingRef = { current: isStreaming };
  const streamingMessageIdRef = { current: streamingMessageId };

  // 应用所有同步策略
  let result = nextList;

  result = preserveLastAssistantIdentity(prevList, result);
  result = preserveStreamingAssistantContent(prevList, result, isStreamingRef, streamingMessageIdRef);
  result = appendOptimisticMessageIfMissing(prevList, result, optimisticMessageId);
  result = stripDuplicateTrailingToolMessages(result);
  result = ensureStreamingAssistantInList(prevList, result, isStreaming, turnId);

  return result;
}

/**
 * 规范化消息内容块
 * 将原始消息格式转换为标准的内容块数组
 */
export function normalizeMessageContent(message: ChatMessage): ChatMessage {
  // 如果消息已经有 raw 字段，尝试解析
  if (message.raw && typeof message.raw === 'object') {
    const raw = message.raw as any;

    // 如果 raw.content 是数组，说明是 Claude 格式
    if (Array.isArray(raw.content)) {
      // 解析内容块
      const contentParts: string[] = [];
      const attachments: typeof message.attachments = [];

      for (const block of raw.content) {
        if (block.type === 'text') {
          contentParts.push(block.text || '');
        } else if (block.type === 'thinking') {
          // Thinking 块处理 - thinking 是内部推理过程，text 是最终回答
          contentParts.push(`[思考过程]\n${block.thinking || ''}\n[/思考过程]`);
          if (block.text) {
            contentParts.push(block.text);
          }
        } else if (block.type === 'tool_use') {
          // 工具调用处理
          contentParts.push(`[调用工具: ${block.name}]`);
          if (block.input) {
            contentParts.push(JSON.stringify(block.input, null, 2));
          }
        } else if (block.type === 'image' && block.source) {
          // 图片处理
          attachments?.push({
            type: 'image',
            mimeType: block.source.media_type || block.source.type,
            data: block.source.data
          });
        }
      }

      return {
        ...message,
        content: contentParts.join('\n\n'),
        attachments: attachments.length > 0 ? attachments : message.attachments
      };
    }
  }

  return message;
}
