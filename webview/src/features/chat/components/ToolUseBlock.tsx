/**
 * ToolUseBlock - 工具调用展示组件
 *
 * 对应架构文档中的工具调用 UI 分发
 * 支持多种工具类型的展示
 */

import { memo } from 'react';
import { cn } from '@/shared/utils/cn';
import type { ToolUseContentPart } from '@/shared/types';

export interface ToolUseBlockProps {
  /** 工具调用内容 */
  content: ToolUseContentPart;
  /** 是否正在执行 */
  isExecuting?: boolean;
  className?: string;
}

/**
 * 规范化工具名称
 * 对应架构文档中的 normalizeToolName
 */
const normalizeToolName = (name?: string): string => {
  if (!name) return 'unknown';
  return name.toLowerCase().replace(/[^a-z_]/g, '_');
};

/**
 * 获取工具图标
 */
const getToolIcon = (toolName: string): JSX.Element => {
  const iconMap: Record<string, JSX.Element> = {
    edit: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
      </svg>
    ),
    bash: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 9l3 3-3 3m5 0h3M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
      </svg>
    ),
    shell: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 9l3 3-3 3m5 0h3M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
      </svg>
    ),
    read: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
      </svg>
    ),
    glob: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
      </svg>
    ),
    grep: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
      </svg>
    ),
    task: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
      </svg>
    ),
    agent: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
      </svg>
    ),
  };

  return iconMap[toolName] || (
    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  );
};

/**
 * 格式化工具输入
 */
const formatToolInput = (input: unknown): string => {
  if (input === null || input === undefined) {
    return '';
  }
  if (typeof input === 'string') {
    return input;
  }
  if (typeof input === 'number' || typeof input === 'boolean') {
    return String(input);
  }
  if (Array.isArray(input)) {
    return JSON.stringify(input, null, 2);
  }
  if (typeof input === 'object') {
    return JSON.stringify(input, null, 2);
  }
  return String(input);
};

export const ToolUseBlock = memo<ToolUseBlockProps>(function ToolUseBlock({
  content,
  isExecuting = false,
  className
}) {
  const toolName = normalizeToolName(content.name);
  const icon = getToolIcon(toolName);
  const formattedInput = formatToolInput(content.input);

  return (
    <div
      className={cn(
        'tool-use-block rounded-lg border border-border bg-muted/30',
        'overflow-hidden',
        isExecuting && 'ring-2 ring-primary/50',
        className
      )}
    >
      {/* Tool Header */}
      <div className="flex items-center gap-2 px-3 py-2 border-b border-border bg-muted/50">
        <div className={cn('text-foreground-muted', isExecuting && 'animate-pulse')}>
          {icon}
        </div>
        <span className="font-mono text-sm font-medium text-foreground">
          {content.name}
        </span>
        {content.id && (
          <span className="text-xs text-foreground-muted font-mono">
            #{content.id.slice(0, 8)}
          </span>
        )}
        <div className="ml-auto flex items-center gap-1">
          {isExecuting && (
            <>
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-primary"></span>
              </span>
              <span className="text-xs text-foreground-muted">执行中</span>
            </>
          )}
        </div>
      </div>

      {/* Tool Input */}
      {formattedInput && (
        <div className="p-3">
          <pre className="text-xs font-mono text-foreground-secondary overflow-x-auto whitespace-pre-wrap break-words">
            {formattedInput}
          </pre>
        </div>
      )}
    </div>
  );
});
