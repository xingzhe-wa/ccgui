/**
 * ThinkingBlock - 思考过程展示组件
 *
 * 对应架构文档中的思考过程 (Thinking) UI
 * 支持折叠/展开，流式期间自动展开最新
 */

import { memo, useState, useEffect } from 'react';
import { cn } from '@/shared/utils/cn';
import { MarkdownRenderer } from './MarkdownRenderer';

export interface ThinkingBlockProps {
  /** 思考内容 */
  content: string;
  /** 是否正在流式输出 */
  isStreaming?: boolean;
  /** 是否是最后一条消息 */
  isLastMessage?: boolean;
  /** 初始展开状态 */
  defaultExpanded?: boolean;
  className?: string;
}

/**
 * 规范化思考内容
 * 对应架构文档中的 normalizeThinking 函数
 */
const normalizeThinking = (thinking: string): string => {
  return thinking
    .replace(/\r\n?/g, '\n')           // 统一换行符
    .replace(/\n[ \t]*\n+/g, '\n')    // 合并多个空行
    .replace(/^\n+/, '')              // 移除开头换行
    .replace(/\n+$/, '');             // 移除结尾换行
};

export const ThinkingBlock = memo<ThinkingBlockProps>(function ThinkingBlock({
  content,
  isStreaming = false,
  isLastMessage = false,
  defaultExpanded = false,
  className
}) {
  // 流式期间如果是最后一条消息，自动展开
  const [isExpanded, setIsExpanded] = useState(() =>
    isStreaming && isLastMessage ? true : defaultExpanded
  );
  const [manuallyExpanded, setManuallyExpanded] = useState(false);

  // 流式期间自动展开最新的 thinking 块
  useEffect(() => {
    if (isStreaming && isLastMessage && !manuallyExpanded) {
      setIsExpanded(true);
    }
  }, [isStreaming, isLastMessage, manuallyExpanded]);

  const handleToggle = () => {
    setIsExpanded(prev => !prev);
    setManuallyExpanded(true);
  };

  const normalizedContent = normalizeThinking(content);

  return (
    <div className={cn('thinking-block', className)}>
      {/* Thinking Header */}
      <div
        className={cn(
          'thinking-header flex items-center gap-2 cursor-pointer',
          'select-none transition-colors hover:bg-muted/50',
          'rounded-t-lg px-3 py-2 border border-border',
          'text-sm text-foreground-secondary'
        )}
        onClick={handleToggle}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            handleToggle();
          }
        }}
      >
        {/* 图标 */}
        <svg
          className={cn(
            'w-4 h-4 transition-transform duration-200',
            isExpanded ? 'rotate-90' : ''
          )}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        </svg>

        {/* 标题 */}
        <span className="font-medium">
          {isStreaming && isLastMessage
            ? '思考中...'
            : '思考过程'}
        </span>

        {/* 状态指示器 */}
        {isStreaming && isLastMessage && (
          <span className="ml-auto flex items-center gap-1">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2 w-2 bg-primary"></span>
            </span>
          </span>
        )}
      </div>

      {/* Thinking Content */}
      <div
        className={cn(
          'thinking-content transition-all duration-200 overflow-hidden',
          'border-x border-b border-border rounded-b-lg bg-muted/30',
          isExpanded ? 'max-h-none' : 'max-h-0'
        )}
        style={{
          maxHeight: isExpanded ? 'none' : '0px',
          opacity: isExpanded ? '1' : '0'
        }}
      >
        <div className="p-3">
          <MarkdownRenderer
            content={normalizedContent}
            className="text-sm leading-relaxed text-foreground"
          />
        </div>
      </div>
    </div>
  );
});
