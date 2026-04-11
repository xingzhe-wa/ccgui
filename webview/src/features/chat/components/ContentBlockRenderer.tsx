/**
 * ContentBlockRenderer - 内容块渲染器
 *
 * 对应架构文档中的 ContentBlockRenderer
 * 统一入口，分发到具体的内容块组件
 */

import { memo } from 'react';
import { cn } from '@/shared/utils/cn';
import { MarkdownRenderer } from './MarkdownRenderer';
import { ThinkingBlock } from './ThinkingBlock';
import { ToolUseBlock } from './ToolUseBlock';
import { ToolResultBlock } from './ToolResultBlock';
import type { ContentPart } from '@/shared/types';

export interface ContentBlockRendererProps {
  /** 内容块 */
  block: ContentPart;
  /** 消息索引 */
  messageIndex: number;
  /** 消息类型 */
  messageType: 'user' | 'assistant' | 'error' | 'system';
  /** 是否正在流式输出 */
  isStreaming?: boolean;
  /** 是否是最后一条消息 */
  isLastMessage?: boolean;
  /** 是否是最后一个内容块 */
  isLastBlock?: boolean;
  className?: string;
}

export const ContentBlockRenderer = memo<ContentBlockRendererProps>(function ContentBlockRenderer({
  block,
  messageType,
  isStreaming = false,
  isLastMessage = false,
  isLastBlock = false,
  className
}) {
  // User 和 System 消息不渲染特殊内容块
  if (messageType === 'user' || messageType === 'system') {
    if (block.type === 'text') {
      return (
        <MarkdownRenderer
          content={block.text}
          className={cn('text-sm leading-relaxed', className)}
        />
      );
    }
    return null;
  }

  // Assistant 消息根据内容块类型分发
  switch (block.type) {
    case 'text':
      return (
        <MarkdownRenderer
          content={block.text}
          className={cn('text-sm leading-relaxed', className)}
        />
      );

    case 'thinking':
      return (
        <ThinkingBlock
          content={block.thinking}
          isStreaming={isStreaming}
          isLastMessage={isLastMessage}
          className={cn('my-2', className)}
        />
      );

    case 'tool_use':
      return (
        <ToolUseBlock
          content={block}
          isExecuting={isStreaming && isLastBlock}
          className={cn('my-2', className)}
        />
      );

    case 'tool_result':
      return (
        <ToolResultBlock
          content={block}
          toolUseId={block.tool_use_id}
          className={cn('my-2', className)}
        />
      );

    case 'image':
      return (
        <div className={cn('my-2', className)}>
          <img
            src={`data:${block.mimeType};base64,${block.data}`}
            alt="Attachment"
            className="max-w-full rounded-lg"
            loading="lazy"
          />
        </div>
      );

    case 'file':
      return (
        <div
          className={cn(
            'flex items-center gap-2 p-3 rounded-lg bg-muted/30 border border-border',
            'my-2',
            className
          )}
        >
          <svg className="w-5 h-5 text-foreground-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-foreground truncate">{block.name}</p>
            {block.size && (
              <p className="text-xs text-foreground-muted">
                {(block.size / 1024).toFixed(1)} KB
              </p>
            )}
          </div>
        </div>
      );

    default:
      // 未知类型，尝试渲染为文本
      return (
        <pre className={cn('text-xs text-foreground-muted bg-muted/30 p-2 rounded', className)}>
          {JSON.stringify(block, null, 2)}
        </pre>
      );
  }
});
