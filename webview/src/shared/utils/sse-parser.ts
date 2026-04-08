/**
 * SSE事件解析器
 *
 * 处理Java后端通过CefJavaScriptExecutor推送的SSE格式数据。
 * 支持标准SSE字段：data, event, id, retry。
 * 内部维护buffer处理跨chunk的不完整行。
 */

/**
 * SSE事件类型
 */
export interface SSEEvent {
  type?: string;
  data: string;
  id?: string;
  retry?: number;
}

/**
 * SSE解析器
 *
 * 处理Java后端通过CefJavaScriptExecutor推送的SSE格式数据块。
 * 支持标准SSE字段：data, event, id, retry。
 * 内部维护buffer处理跨chunk的不完整行。
 */
export class SSEParser {
  private buffer = '';
  private eventQueue: SSEEvent[] = [];

  /**
   * 解析SSE数据块
   * @param chunk 来自SSE流的数据块
   * @returns 解析后的事件数组
   */
  parse(chunk: string): SSEEvent[] {
    this.buffer += chunk;
    this.eventQueue = [];

    const lines = this.buffer.split('\n');

    // 保留最后一个不完整的行
    this.buffer = lines.pop() || '';

    let currentEvent: Partial<SSEEvent> = {};

    for (const line of lines) {
      // 空行表示事件结束
      if (line === '') {
        if (currentEvent.data !== undefined) {
          this.eventQueue.push({
            type: currentEvent.type,
            data: currentEvent.data,
            id: currentEvent.id,
            retry: currentEvent.retry
          });
        }
        currentEvent = {};
        continue;
      }

      // 注释行，跳过
      if (line.startsWith(':')) {
        continue;
      }

      const colonIndex = line.indexOf(':');
      if (colonIndex === -1) {
        continue;
      }

      const field = line.slice(0, colonIndex);
      let value = line.slice(colonIndex + 1);

      // 移除前导空格
      if (value.startsWith(' ')) {
        value = value.slice(1);
      }

      switch (field) {
        case 'data':
          currentEvent.data = (currentEvent.data || '') + value + '\n';
          break;
        case 'event':
          currentEvent.type = value;
          break;
        case 'id':
          currentEvent.id = value;
          break;
        case 'retry':
          currentEvent.retry = parseInt(value, 10);
          break;
      }
    }

    return this.eventQueue;
  }

  /**
   * 重置解析器状态
   */
  reset(): void {
    this.buffer = '';
    this.eventQueue = [];
  }
}

/**
 * 便捷函数：解析流式数据块并提取delta/done/error
 *
 * @param chunk SSE数据块
 * @returns 解析结果，包含 delta(增量文本)、done(是否完成)、error(错误信息)
 */
export const parseStreamingChunk = (
  chunk: string
): { delta?: string; done?: boolean; error?: string } => {
  const parser = new SSEParser();
  const events = parser.parse(chunk);

  for (const event of events) {
    try {
      const data = JSON.parse(event.data);

      if (event.type === 'delta' || event.type === 'content_block_delta') {
        return { delta: data.text || data.delta };
      } else if (event.type === 'done' || event.type === 'message_stop') {
        return { done: true };
      } else if (event.type === 'error') {
        return { error: data.error || data.message };
      }
    } catch {
      // 如果解析失败，可能是普通文本数据
      if (event.data && !event.type) {
        return { delta: event.data };
      }
    }
  }

  return {};
};

/**
 * 创建SSE解析器实例的工厂函数
 */
export const createSSEParser = (): SSEParser => {
  return new SSEParser();
};
