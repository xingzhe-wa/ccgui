/**
 * 事件处理器类型
 */
type EventHandler<T = any> = (data: T) => void;

/**
 * 事件总线 - 前端内部事件通道
 * 使用发布/订阅模式实现组件间通信
 */
class EventBus {
  private listeners = new Map<string, Set<EventHandler>>();

  /**
   * 订阅事件
   * @param event 事件名称
   * @param handler 事件处理函数
   * @returns 取消订阅函数
   */
  on<T = any>(event: string, handler: EventHandler<T>): () => void {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event)!.add(handler);

    // 返回取消订阅函数
    return () => {
      this.off(event, handler);
    };
  }

  /**
   * 取消订阅
   * @param event 事件名称
   * @param handler 事件处理函数
   */
  off<T = any>(event: string, handler: EventHandler<T>): void {
    const handlers = this.listeners.get(event);
    if (handlers) {
      handlers.delete(handler);
      if (handlers.size === 0) {
        this.listeners.delete(event);
      }
    }
  }

  /**
   * 触发事件
   * @param event 事件名称
   * @param data 事件数据
   */
  emit<T = any>(event: string, data: T): void {
    const handlers = this.listeners.get(event);
    if (handlers) {
      // 创建副本避免在遍历时修改
      Array.from(handlers).forEach((handler) => {
        try {
          handler(data);
        } catch (error) {
          console.error(`Error in event handler for ${event}:`, error);
        }
      });
    }
  }

  /**
   * 一次性订阅
   * @param event 事件名称
   * @param handler 事件处理函数
   * @returns 取消订阅函数
   */
  once<T = any>(event: string, handler: EventHandler<T>): () => void {
    const wrappedHandler: EventHandler<T> = (data) => {
      handler(data);
      this.off(event, wrappedHandler);
    };
    return this.on(event, wrappedHandler);
  }

  /**
   * 清空所有事件监听
   */
  clear(): void {
    this.listeners.clear();
  }

  /**
   * 清空指定事件的所有监听
   * @param event 事件名称
   */
  clearEvent(event: string): void {
    this.listeners.delete(event);
  }

  /**
   * 获取事件监听器数量
   * @param event 事件名称
   * @returns 监听器数量
   */
  getListenerCount(event: string): number {
    return this.listeners.get(event)?.size ?? 0;
  }
}

/**
 * 事件总线单例
 */
export const eventBus = new EventBus();

/**
 * 事件名称常量
 */
export const Events = {
  // 消息事件
  MESSAGE_RECEIVED: 'message:received',
  MESSAGE_SEND: 'message:send',

  // 流式输出事件
  STREAMING_CHUNK: 'streaming:chunk',
  STREAMING_COMPLETE: 'streaming:complete',
  STREAMING_ERROR: 'streaming:error',
  STREAMING_CANCEL: 'streaming:cancel',

  // 配置事件
  CONFIG_CHANGED: 'config:changed',
  THEME_CHANGED: 'theme:changed',

  // 会话事件
  SESSION_CHANGED: 'session:changed',
  SESSION_CREATED: 'session:created',
  SESSION_DELETED: 'session:deleted',
  SESSION_UPDATED: 'session:updated',

  // 任务事件
  TASK_PROGRESS: 'task:progress',
  TASK_STEP_COMPLETE: 'task:step:complete',
  TASK_COMPLETE: 'task:complete',
  TASK_ERROR: 'task:error',

  // 交互式请求事件
  QUESTION_ASKED: 'question:asked',
  QUESTION_ANSWERED: 'question:answered',

  // UI事件
  SIDEBAR_TOGGLE: 'ui:sidebar:toggle',
  PREVIEW_TOGGLE: 'ui:preview:toggle',
  MODAL_OPEN: 'ui:modal:open',
  MODAL_CLOSE: 'ui:modal:close'
} as const;

export type EventName = (typeof Events)[keyof typeof Events];
