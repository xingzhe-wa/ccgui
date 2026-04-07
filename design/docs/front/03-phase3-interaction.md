# Phase 3: 交互增强系统 (Interaction)

**优先级**: P0
**预估工期**: 19人天 (4周)
**前置依赖**: Phase 1 全部完成 + Phase 2 全部完成（消息组件/Markdown渲染/输入组件可用）
**阶段目标**: 流式输出引擎可用，交互式请求引擎可用，多模态输入可用

---

## 1. 阶段概览

本阶段在Phase 2的UI组件基础之上，构建完整的AI交互体验层：

1. SSE流式输出引擎（SSEParser + useStreaming Hook + StreamingMessage组件）
2. 交互式请求引擎（4种问题类型 + 倒计时 + 提交回传）
3. 多模态输入处理（文件拖拽 + 图片粘贴 + 文件解析器）
4. 快捷键绑定系统（全局/会话/编辑器作用域）
5. 代码快捷操作面板（7种操作）

**完成标志**: AI回复打字机效果正常，可上传图片/附件，交互式请求正常应答

**与后端协作点**:
- 后端Phase 3的 `StreamingOutputEngine` 会通过 `window.ccEvents` 推送 `streaming:chunk`/`streaming:complete` 事件
- 后端Phase 4的 `InteractiveRequestEngine` 会推送 `question:show` 事件，前端通过 `submitAnswer()` 回传用户选择
- 前端的 `useStreaming` Hook 监听 EventBus 事件，不直接与SSE连接
- 多模态附件通过 `sendMultimodalMessage()` 发送，后端负责解析

---

## 2. 任务清单

### Week 7: 流式输出引擎

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T3-W7-01** | SSE事件解析器 | 1.5人天 | sse-parser.ts | SSE解析正常，支持data/event/id/retry字段 |
| **T3-W7-02** | useStreaming Hook | 1人天 | useStreaming.ts | Hook正常工作，事件监听自动清理 |
| **T3-W7-03** | StreamingMessage组件 | 1.5人天 | StreamingMessage.tsx | 流式显示正常，requestAnimationFrame优化 |
| **T3-W7-04** | 打字机效果动画 | 1人天 | TypingCursor.tsx | 动画流畅，CSS pulse无卡顿 |
| **T3-W7-05** | 停止生成按钮 | 0.5人天 | StopButton.tsx | 停止正常，调用cancelStreaming |
| **T3-W7-06** | 流式输出状态管理 | 1人天 | streamingStore.ts | 状态同步正常，Zustand实现 |

### Week 8: 交互式请求引擎

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T3-W8-01** | InteractiveQuestionPanel | 2人天 | InteractiveQuestionPanel.tsx | UI显示正常，支持4种问题类型 |
| **T3-W8-02** | SingleChoiceOptions | 1人天 | SingleChoiceOptions.tsx | 单选正常，radio样式 |
| **T3-W8-03** | MultipleChoiceOptions | 1人天 | MultipleChoiceOptions.tsx | 多选正常，checkbox样式 |
| **T3-W8-04** | TextInput选项 | 0.5人天 | TextInput.tsx | 输入正常，支持placeholder |
| **T3-W8-05** | Confirmation选项 | 0.5人天 | ConfirmationOptions.tsx | 确认/取消正常 |
| **T3-W8-06** | 问题状态管理 | 1人天 | questionStore.ts | 状态管理正常，超时处理 |

### Week 9: 多模态输入与快捷操作

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T3-W9-01** | 文件拖拽处理 | 1人天 | useFileDrop.ts | 拖拽正常，支持MIME过滤 |
| **T3-W9-02** | 图片粘贴处理 | 1人天 | useImagePaste.ts | Ctrl+V粘贴正常 |
| **T3-W9-03** | 文件解析器 | 1.5人天 | file-parser.ts | 解析图片/PDF/文本正常 |
| **T3-W9-04** | 附件预览管理 | 1人天 | AttachmentManager.tsx | 预览/删除/重排正常 |
| **T3-W9-05** | 快捷键绑定 | 1人天 | useHotkeys.ts | 快捷键正常，支持修饰键组合 |
| **T3-W9-06** | 快捷操作面板 | 1人天 | QuickActionsPanel.tsx | 7种操作面板正常 |

---

## 3. Week 7: 流式输出引擎

### T3-W7-01: SSE事件解析器

**任务描述**: 实现Server-Sent Events解析器，处理Java后端通过JCEF推送的流式数据块

**实现代码**:

```typescript
// src/shared/utils/sse-parser.ts

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
 * 处理Java后端通过CefJavaScriptExecutor推送的SSE格式数据。
 * 支持标准SSE字段：data, event, id, retry。
 * 内部维护buffer处理跨chunk的不完整行。
 */
export class SSEParser {
  private buffer = '';
  private eventQueue: SSEEvent[] = [];

  /**
   * 解析SSE数据块
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
 */
export const parseStreamingChunk = (chunk: string): { delta?: string; done?: boolean; error?: string } => {
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
    } catch (error) {
      console.error('Failed to parse SSE event:', error);
    }
  }
  
  return {};
};
```

**验收标准**:
- ✅ 支持标准SSE字段解析（data/event/id/retry）
- ✅ 正确处理跨chunk的不完整行（buffer机制）
- ✅ 注释行（`:` 开头）正确跳过
- ✅ 多data字段正确拼接（带换行符）

---

### T3-W7-02: useStreaming Hook

**实现代码**:

```typescript
// src/shared/hooks/useStreaming.ts
import { useState, useEffect, useRef, useCallback } from 'react';
import { eventBus, Events } from '@/shared/utils/event-bus';

export interface UseStreamingOptions {
  onChunk?: (chunk: string) => void;
  onComplete?: (fullContent: string) => void;
  onError?: (error: string) => void;
}

export interface UseStreamingReturn {
  content: string;
  isStreaming: boolean;
  error: string | null;
  startStreaming: (messageId: string) => void;
  stopStreaming: () => void;
}

/**
 * 流式输出Hook
 *
 * 监听EventBus上的streaming事件，将增量内容拼接为完整内容。
 * 组件卸载时自动清理事件监听器，防止内存泄漏。
 */
export const useStreaming = (options: UseStreamingOptions = {}): UseStreamingReturn => {
  const { onChunk, onComplete, onError } = options;
  const [content, setContent] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const messageIdRef = useRef<string | null>(null);
  const contentRef = useRef('');
  const cleanupRef = useRef<(() => void)[]>([]);

  const startStreaming = useCallback((messageId: string) => {
    messageIdRef.current = messageId;
    contentRef.current = '';
    setContent('');
    setIsStreaming(true);
    setError(null);

    // 监听流式输出事件
    const unsubscribeChunk = eventBus.on(Events.STREAMING_CHUNK, (data: { messageId: string; chunk: string }) => {
      if (data.messageId === messageId) {
        contentRef.current += data.chunk;
        setContent(contentRef.current);
        onChunk?.(data.chunk);
      }
    });

    const unsubscribeComplete = eventBus.on(Events.STREAMING_COMPLETE, (data: { messageId: string }) => {
      if (data.messageId === messageId) {
        setIsStreaming(false);
        onComplete?.(contentRef.current);
      }
    });

    const unsubscribeError = eventBus.on(Events.STREAMING_ERROR, (data: { messageId: string; error: string }) => {
      if (data.messageId === messageId) {
        setError(data.error);
        setIsStreaming(false);
        onError?.(data.error);
      }
    });

    cleanupRef.current = [unsubscribeChunk, unsubscribeComplete, unsubscribeError];
  }, [onChunk, onComplete, onError]);

  const stopStreaming = useCallback(() => {
    if (messageIdRef.current) {
      window.ccBackend?.cancelStreaming(messageIdRef.current);
    }
    setIsStreaming(false);
    
    cleanupRef.current.forEach(unsubscribe => unsubscribe());
    cleanupRef.current = [];
  }, []);

  // 组件卸载时清理
  useEffect(() => {
    return () => {
      cleanupRef.current.forEach(unsubscribe => unsubscribe());
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
```

**验收标准**:
- ✅ EventBus事件监听正确
- ✅ 组件卸载时自动清理监听器
- ✅ content使用ref累积，避免闭包陷阱
- ✅ stopStreaming调用后端cancelStreaming API

---

### T3-W7-03: StreamingMessage组件

**实现代码**:

```typescript
// src/features/streaming/components/StreamingMessage.tsx
import { memo, useEffect, useState, useRef } from 'react';
import { MarkdownRenderer } from '@/shared/components/markdown/MarkdownRenderer';
import { eventBus, Events } from '@/shared/utils/event-bus';
import { TypingCursor } from './TypingCursor';
import { cn } from '@/shared/utils/cn';

export interface StreamingMessageProps {
  messageId: string;
  onComplete?: () => void;
  className?: string;
}

/**
 * 流式消息组件
 *
 * 订阅EventBus的streaming事件，实时渲染AI回复。
 * 使用requestAnimationFrame避免高频chunk导致渲染阻塞。
 * 流式输出完成后自动取消订阅。
 */
export const StreamingMessage = memo(({ messageId, onComplete, className }: StreamingMessageProps) => {
  const [content, setContent] = useState('');
  const [isStreaming, setIsStreaming] = useState(true);
  const contentRef = useRef('');
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let isMounted = true;
    let animationFrameId: number;

    const unsubscribeChunk = eventBus.on(Events.STREAMING_CHUNK, (data: { messageId: string; chunk: string }) => {
      if (data.messageId === messageId && isMounted) {
        cancelAnimationFrame(animationFrameId);
        animationFrameId = requestAnimationFrame(() => {
          contentRef.current += data.chunk;
          setContent(contentRef.current);
          
          // 自动滚动到底部
          if (containerRef.current) {
            containerRef.current.scrollTop = containerRef.current.scrollHeight;
          }
        });
      }
    });

    const unsubscribeComplete = eventBus.on(Events.STREAMING_COMPLETE, (data: { messageId: string }) => {
      if (data.messageId === messageId && isMounted) {
        setIsStreaming(false);
        onComplete?.();
      }
    });

    const unsubscribeError = eventBus.on(Events.STREAMING_ERROR, (data: { messageId: string; error: string }) => {
      if (data.messageId === messageId && isMounted) {
        setIsStreaming(false);
        setContent((prev) => prev + `\n\n[错误: ${data.error}]`);
      }
    });

    return () => {
      isMounted = false;
      cancelAnimationFrame(animationFrameId);
      unsubscribeChunk();
      unsubscribeComplete();
      unsubscribeError();
    };
  }, [messageId, onComplete]);

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

StreamingMessage.displayName = 'StreamingMessage';
```

---

### T3-W7-04: 打字机效果动画

**实现代码**:

```typescript
// src/features/streaming/components/TypingCursor.tsx
import { memo } from 'react';
import { cn } from '@/shared/utils/cn';

export interface TypingCursorProps {
  className?: string;
  color?: string;
}

/**
 * 打字机光标组件
 *
 * 使用CSS animate-pulse实现闪烁效果，无需JavaScript定时器。
 * 颜色跟随主题，可通过color prop覆盖。
 */
export const TypingCursor = memo(({ className, color }: TypingCursorProps) => {
  return (
    <span
      className={cn(
        'ml-1 inline-block h-4 w-0.5 animate-pulse',
        className
      )}
      style={{
        backgroundColor: color || 'currentColor'
      }}
    />
  );
});

TypingCursor.displayName = 'TypingCursor';
```

---

### T3-W7-05: 停止生成按钮

**实现代码**:

```typescript
// src/features/streaming/components/StopButton.tsx
import { memo } from 'react';
import { Button } from '@/shared/components/ui/button';
import { SquareIcon } from '@/shared/components/Icon';
import { useStreamingStore } from '@/shared/stores/streamingStore';
import { cn } from '@/shared/utils/cn';

export interface StopButtonProps {
  messageId: string;
  onStop?: () => void;
  className?: string;
}

/**
 * 停止生成按钮
 *
 * 仅在当前消息正在流式输出时显示。
 * 点击后调用streamingStore.cancelStreaming()，同时通知后端停止。
 */
export const StopButton = memo(({ messageId, onStop, className }: StopButtonProps) => {
  const { streamingMessageId, cancelStreaming } = useStreamingStore();
  const isActive = streamingMessageId === messageId;

  const handleStop = () => {
    cancelStreaming();
    onStop?.();
  };

  if (!isActive) {
    return null;
  }

  return (
    <Button
      variant="destructive"
      size="sm"
      onClick={handleStop}
      className={cn('gap-2', className)}
    >
      <SquareIcon className="h-4 w-4" />
      停止生成
    </Button>
  );
});

StopButton.displayName = 'StopButton';
```

---

### T3-W7-06: 流式输出状态管理

**实现代码**:

```typescript
// src/shared/stores/streamingStore.ts
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
  
  // 操作
  startStreaming: (messageId: ID) => void;
  appendChunk: (chunk: string) => void;
  finishStreaming: () => void;
  setStreamingError: (error: string) => void;
  cancelStreaming: () => void;
}

export const useStreamingStore = create<StreamingState>((set, get) => ({
  streamingMessageId: null,
  streamingBuffer: '',
  isStreaming: false,
  streamingError: null,

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
  }
}));
```

---

## 4. Week 8: 交互式请求引擎

### T3-W8-01: InteractiveQuestionPanel组件

**实现代码**:

```typescript
// src/features/interaction/components/InteractiveQuestionPanel.tsx
import { useState, memo, useEffect } from 'react';
import { QuestionType } from '@/shared/types';
import { Button } from '@/shared/components/ui/button';
import { cn } from '@/shared/utils/cn';

export interface InteractiveQuestionPanelProps {
  questionId: string;
  question: string;
  questionType: QuestionType;
  options?: Array<{
    id: string;
    label: string;
    description?: string;
    icon?: string;
  }>;
  allowMultiple?: boolean;
  required?: boolean;
  placeholder?: string;
  onAnswer: (answer: any) => void;
  onSkip?: () => void;
  timeout?: number;
  className?: string;
}

/**
 * 交互式问题面板
 *
 * 根据后端InteractiveRequestEngine推送的问题类型，
 * 渲染对应的交互UI（单选/多选/文本输入/确认）。
 * 用户提交后通过submitAnswer()回传给后端。
 */
export const InteractiveQuestionPanel = memo(({
  questionId,
  question,
  questionType,
  options = [],
  allowMultiple = false,
  required = true,
  placeholder,
  onAnswer,
  onSkip,
  timeout,
  className
}: InteractiveQuestionPanelProps) => {
  const [selectedAnswer, setSelectedAnswer] = useState<any>(null);
  const [textInput, setTextInput] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async () => {
    if (required && !selectedAnswer && !textInput) return;

    setIsSubmitting(true);
    try {
      await window.ccBackend?.submitAnswer(questionId, selectedAnswer ?? textInput);
      onAnswer(selectedAnswer ?? textInput);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSkip = () => {
    onSkip?.();
  };

  return (
    <div
      className={cn(
        'my-4 rounded-lg border border-primary/20 bg-primary/5 p-4',
        className
      )}
    >
      {/* 问题头部 */}
      <div className="mb-4 flex items-start gap-2">
        <div className="mt-0.5 h-5 w-5 text-primary">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="10" />
            <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
        </div>
        <div className="flex-1">
          <p className="font-medium">{question}</p>
          {required && <p className="mt-1 text-xs text-muted-foreground">* 必需回答</p>}
        </div>
        {timeout && <CountdownTimer seconds={timeout} />}
      </div>

      {/* 问题内容 */}
      <div className="mb-4">
        {questionType === QuestionType.SINGLE_CHOICE && (
          <SingleChoiceOptions
            questionId={questionId}
            options={options}
            selected={selectedAnswer}
            onChange={setSelectedAnswer}
          />
        )}

        {questionType === QuestionType.MULTIPLE_CHOICE && (
          <MultipleChoiceOptions
            questionId={questionId}
            options={options}
            selected={selectedAnswer ?? []}
            onChange={setSelectedAnswer}
          />
        )}

        {questionType === QuestionType.TEXT_INPUT && (
          <div className="space-y-2">
            <input
              type="text"
              value={textInput}
              onChange={(e) => setTextInput(e.target.value)}
              placeholder={placeholder || '请输入...'}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            />
          </div>
        )}

        {questionType === QuestionType.CONFIRMATION && (
          <ConfirmationOptions
            selected={selectedAnswer}
            onChange={setSelectedAnswer}
          />
        )}
      </div>

      {/* 操作按钮 */}
      <div className="flex justify-end gap-2">
        {onSkip && (
          <Button variant="ghost" onClick={handleSkip} disabled={isSubmitting}>
            跳过
          </Button>
        )}
        <Button
          onClick={handleSubmit}
          disabled={isSubmitting || (required && !selectedAnswer && !textInput)}
        >
          确认
        </Button>
      </div>
    </div>
  );
});

InteractiveQuestionPanel.displayName = 'InteractiveQuestionPanel';

// 倒计时子组件
const CountdownTimer = ({ seconds }: { seconds: number }) => {
  const [remaining, setRemaining] = useState(seconds);

  useEffect(() => {
    if (remaining <= 0) return;

    const interval = setInterval(() => {
      setRemaining((prev) => prev - 1);
    }, 1000);

    return () => clearInterval(interval);
  }, [remaining]);

  return (
    <div className={cn(
      'text-xs font-mono',
      remaining > 10 ? 'text-muted-foreground' : 'text-destructive'
    )}>
      {Math.floor(remaining / 60)}:{(remaining % 60).toString().padStart(2, '0')}
    </div>
  );
};
```

---

### T3-W8-02: SingleChoiceOptions组件

**实现代码**:

```typescript
// src/features/interaction/components/SingleChoiceOptions.tsx
import { memo } from 'react';
import { cn } from '@/shared/utils/cn';

export interface SingleChoiceOptionsProps {
  questionId: string;
  options: Array<{
    id: string;
    label: string;
    description?: string;
    icon?: string;
  }>;
  selected: string;
  onChange: (value: string) => void;
}

export const SingleChoiceOptions = memo(({
  questionId,
  options,
  selected,
  onChange
}: SingleChoiceOptionsProps) => {
  return (
    <div className="space-y-2">
      {options.map((option) => (
        <label
          key={option.id}
          className={cn(
            'flex cursor-pointer items-start gap-3 rounded-md border p-3 transition-colors',
            'hover:bg-accent',
            selected === option.id && 'border-primary bg-primary/5'
          )}
        >
          <input
            type="radio"
            name={questionId}
            value={option.id}
            checked={selected === option.id}
            onChange={(e) => onChange(e.target.value)}
            className="mt-1"
          />
          <div className="flex-1">
            <div className="font-medium">{option.label}</div>
            {option.description && (
              <div className="mt-1 text-sm text-muted-foreground">
                {option.description}
              </div>
            )}
          </div>
        </label>
      ))}
    </div>
  );
});

SingleChoiceOptions.displayName = 'SingleChoiceOptions';
```

---

### T3-W8-03: MultipleChoiceOptions组件

**实现代码**:

```typescript
// src/features/interaction/components/MultipleChoiceOptions.tsx
import { memo } from 'react';
import { cn } from '@/shared/utils/cn';

export interface MultipleChoiceOptionsProps {
  questionId: string;
  options: Array<{
    id: string;
    label: string;
    description?: string;
  }>;
  selected: string[];
  onChange: (value: string[]) => void;
}

export const MultipleChoiceOptions = memo(({
  questionId,
  options,
  selected,
  onChange
}: MultipleChoiceOptionsProps) => {
  const handleToggle = (value: string) => {
    const newSelected = selected.includes(value)
      ? selected.filter((v) => v !== value)
      : [...selected, value];
    onChange(newSelected);
  };

  return (
    <div className="space-y-2">
      {options.map((option) => (
        <label
          key={option.id}
          className={cn(
            'flex cursor-pointer items-start gap-3 rounded-md border p-3 transition-colors',
            'hover:bg-accent',
            selected.includes(option.id) && 'border-primary bg-primary/5'
          )}
        >
          <div className="relative flex h-5 w-5 items-center justify-center">
            <input
              type="checkbox"
              checked={selected.includes(option.id)}
              onChange={() => handleToggle(option.id)}
              className="h-4 w-4 rounded border border-primary"
            />
          </div>
          <div className="flex-1">
            <div className="font-medium">{option.label}</div>
            {option.description && (
              <div className="mt-1 text-sm text-muted-foreground">
                {option.description}
              </div>
            )}
          </div>
        </label>
      ))}
    </div>
  );
});

MultipleChoiceOptions.displayName = 'MultipleChoiceOptions';
```

---

## 5. Week 9: 多模态输入与快捷操作

### T3-W9-01: 文件拖拽处理

**实现代码**:

```typescript
// src/shared/hooks/useFileDrop.ts
import { useCallback, useState } from 'react';

export interface UseFileDropOptions {
  onDrop?: (files: File[]) => void;
  onDragEnter?: () => void;
  onDragLeave?: () => void;
  accept?: string;
  multiple?: boolean;
}

/**
 * 文件拖拽Hook
 *
 * 使用dragCounter解决子元素触发dragEnter/dragLeave的问题。
 * 支持MIME类型和扩展名过滤。
 */
export const useFileDrop = (options: UseFileDropOptions = {}) => {
  const { onDrop, onDragEnter, onDragLeave, accept, multiple = true } = options;
  const [isDragging, setIsDragging] = useState(false);
  const [dragCounter, setDragCounter] = useState(0);

  const handleDragEnter = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragCounter((prev) => {
      const next = prev + 1;
      if (next === 1) {
        setIsDragging(true);
        onDragEnter?.();
      }
      return next;
    });
  }, [onDragEnter]);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragCounter((prev) => {
      const next = prev - 1;
      if (next === 0) {
        setIsDragging(false);
        onDragLeave?.();
      }
      return next;
    });
  }, [onDragLeave]);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragCounter(0);
    setIsDragging(false);

    const files = Array.from(e.dataTransfer.files);
    
    // 过滤文件类型
    let filteredFiles = files;
    if (accept) {
      const acceptedTypes = accept.split(',').map(t => t.trim());
      filteredFiles = files.filter(file => {
        return acceptedTypes.some(type => {
          if (type.startsWith('.')) {
            return file.name.toLowerCase().endsWith(type.toLowerCase());
          }
          return file.type === type;
        });
      });
    }

    if (!multiple && filteredFiles.length > 0) {
      filteredFiles = [filteredFiles[0]];
    }

    onDrop?.(filteredFiles);
  }, [accept, multiple, onDrop]);

  const dropProps = {
    onDragEnter: handleDragEnter,
    onDragLeave: handleDragLeave,
    onDragOver: handleDragOver,
    onDrop: handleDrop
  };

  return {
    isDragging,
    dropProps
  };
};
```

---

### T3-W9-02: 图片粘贴处理

**实现代码**:

```typescript
// src/shared/hooks/useImagePaste.ts
import { useEffect, useRef } from 'react';

export interface UseImagePasteOptions {
  onPaste?: (files: File[]) => void;
  accept?: string[];
}

/**
 * 图片粘贴Hook
 *
 * 监听paste事件，从clipboardData中提取图片文件。
 * 需要绑定到具体的DOM元素上使用。
 */
export const useImagePaste = (options: UseImagePasteOptions = {}) => {
  const { onPaste, accept = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'] } = options;
  const targetRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const element = targetRef.current;
    if (!element) return;

    const handlePaste = (e: ClipboardEvent) => {
      const clipboardData = e.clipboardData;
      if (!clipboardData) return;

      const items = Array.from(clipboardData.items);
      const imageFiles: File[] = [];

      for (const item of items) {
        if (item.type.startsWith('image/')) {
          const file = item.getAsFile();
          if (file && accept.includes(file.type)) {
            imageFiles.push(file);
          }
        }
      }

      if (imageFiles.length > 0) {
        e.preventDefault();
        onPaste?.(imageFiles);
      }
    };

    element.addEventListener('paste', handlePaste);
    return () => {
      element.removeEventListener('paste', handlePaste);
    };
  }, [onPaste, accept]);

  return { targetRef };
};
```

---

### T3-W9-05: 快捷键绑定

**实现代码**:

```typescript
// src/shared/hooks/useHotkeys.ts
import { useEffect, useRef } from 'react';

export type HotkeyCallback = (e: KeyboardEvent) => void;
export type HotkeyItem = {
  key: string;
  ctrlKey?: boolean;
  shiftKey?: boolean;
  altKey?: boolean;
  metaKey?: boolean;
  callback: HotkeyCallback;
  description?: string;
};

/**
 * 快捷键绑定Hook
 *
 * 使用ref存储hotkeys避免频繁绑定/解绑。
 * 支持修饰键组合（Ctrl/Shift/Alt/Meta）。
 * 组件卸载时自动清理事件监听。
 */
export const useHotkeys = (hotkeys: HotkeyItem[]) => {
  const hotkeysRef = useRef(hotkeys);

  useEffect(() => {
    hotkeysRef.current = hotkeys;
  }, [hotkeys]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      for (const hotkey of hotkeysRef.current) {
        const {
          key,
          ctrlKey = false,
          shiftKey = false,
          altKey = false,
          metaKey = false,
          callback
        } = hotkey;

        if (
          e.key.toLowerCase() === key.toLowerCase() &&
          e.ctrlKey === ctrlKey &&
          e.shiftKey === shiftKey &&
          e.altKey === altKey &&
          e.metaKey === metaKey
        ) {
          e.preventDefault();
          callback(e);
          break;
        }
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, []);
};
```

---

## 6. 任务依赖与执行顺序

```
T3.1 流式输出引擎 (Week 7)
├── T3-W7-01 SSE事件解析器            ← 依赖 Phase1的EventBus
├── T3-W7-02 useStreaming Hook        ← 依赖 T3-W7-01 + Phase1的sessionStore
├── T3-W7-03 StreamingMessage组件     ← 依赖 T3-W7-02 + Phase2的MarkdownRenderer
├── T3-W7-04 打字机效果动画           ← 依赖 T3-W7-03
├── T3-W7-05 停止生成按钮             ← 依赖 T3-W7-06
└── T3-W7-06 流式输出状态管理         ← 依赖 Phase1的Zustand框架

T3.2 交互式请求引擎 (Week 8)
├── T3-W8-01 InteractiveQuestionPanel ← 依赖 Phase2的UI组件 + Phase1的bridge类型
├── T3-W8-02 SingleChoiceOptions      ← 依赖 T3-W8-01
├── T3-W8-03 MultipleChoiceOptions    ← 依赖 T3-W8-01
├── T3-W8-04 TextInput选项            ← 依赖 T3-W8-01
├── T3-W8-05 Confirmation选项         ← 依赖 T3-W8-01
└── T3-W8-06 问题状态管理             ← 依赖 T3-W8-01 + Phase1的Zustand

T3.3 多模态输入与快捷操作 (Week 9)
├── T3-W9-01 文件拖拽处理             ← 依赖 Phase2的ChatInput
├── T3-W9-02 图片粘贴处理             ← 依赖 Phase2的ChatInput
├── T3-W9-03 文件解析器               ← 依赖 T3-W9-01
├── T3-W9-04 附件预览管理             ← 依赖 T3-W9-03
├── T3-W9-05 快捷键绑定               ← 依赖 Phase1的EventBus
└── T3-W9-06 快捷操作面板             ← 依赖 T3-W9-05
```

**关键路径**: T3-W7-01 → T3-W7-02 → T3-W7-03 → T3-W8-01 → T3-W9-01

---

## 7. 验收标准

### 功能验收
- [ ] SSE流式输出首字延迟 < 500ms
- [ ] 打字机效果流畅（CSS animate-pulse）
- [ ] 停止生成按钮正常工作
- [ ] 交互式请求4种问题类型都支持
- [ ] 文件拖拽支持MIME类型过滤
- [ ] 图片Ctrl+V粘贴正常
- [ ] 快捷键绑定支持修饰键组合

### 性能验收
- [ ] 流式输出CPU占用 < 30%
- [ ] requestAnimationFrame避免渲染阻塞
- [ ] 事件监听组件卸载时正确清理
- [ ] 虚拟滚动与流式渲染不冲突

### 代码质量验收
- [ ] 所有组件使用React.memo优化
- [ ] 事件监听器全部在useEffect cleanup中清理
- [ ] TypeScript类型100%覆盖
- [ ] 无内存泄漏风险

---

## 8. 文件清单汇总

### 新增文件

| 文件路径 | 说明 |
|----------|------|
| `src/shared/utils/sse-parser.ts` | SSE事件解析器 |
| `src/shared/hooks/useStreaming.ts` | 流式输出Hook |
| `src/shared/hooks/useFileDrop.ts` | 文件拖拽Hook |
| `src/shared/hooks/useImagePaste.ts` | 图片粘贴Hook |
| `src/shared/hooks/useHotkeys.ts` | 快捷键绑定Hook |
| `src/shared/stores/streamingStore.ts` | 流式输出状态Store |
| `src/shared/stores/questionStore.ts` | 交互式问题状态Store |
| `src/features/streaming/components/StreamingMessage.tsx` | 流式消息组件 |
| `src/features/streaming/components/TypingCursor.tsx` | 打字机光标组件 |
| `src/features/streaming/components/StopButton.tsx` | 停止生成按钮 |
| `src/features/interaction/components/InteractiveQuestionPanel.tsx` | 交互式问题面板 |
| `src/features/interaction/components/SingleChoiceOptions.tsx` | 单选选项组件 |
| `src/features/interaction/components/MultipleChoiceOptions.tsx` | 多选选项组件 |
| `src/features/interaction/components/ConfirmationOptions.tsx` | 确认选项组件 |
| `src/shared/utils/file-parser.ts` | 文件解析器 |
| `src/features/chat/components/AttachmentManager.tsx` | 附件预览管理 |
| `src/features/chat/components/QuickActionsPanel.tsx` | 快捷操作面板 |

---

## 9. 相关文档

- [总览](./00-overview.md)
- [技术架构设计](./10-architecture.md)
- [类型定义规范](./11-types.md)
- [组件设计规范](./12-components.md)
- [Phase 1: 基础架构](./01-phase1-foundation.md)
- [Phase 2: 核心UI](./02-phase2-core-ui.md)
- [Phase 4: 会话管理](./04-phase4-session.md)
- [后端Phase 3: 核心服务](../backend/03-phase3-core-services.md) ← StreamingOutputEngine推送streaming事件
- [后端Phase 4: 功能模块](../backend/04-phase4-features.md) ← InteractiveRequestEngine推送question事件
