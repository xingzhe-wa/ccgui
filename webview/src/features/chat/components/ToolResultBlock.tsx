/**
 * ToolResultBlock - 工具结果展示组件
 *
 * 对应架构文档中的工具结果展示
 * 支持成功/失败状态，不同内容格式的展示
 */

import { memo } from 'react';
import { cn } from '@/shared/utils/cn';
import { CodeBlock } from './CodeBlock';
import type { ToolResultContentPart } from '@/shared/types';

export interface ToolResultBlockProps {
  /** 工具结果内容 */
  content: ToolResultContentPart;
  /** 关联的工具调用 ID */
  toolUseId?: string;
  className?: string;
}

/**
 * 判断是否为错误结果
 */
const isErrorResult = (content: ToolResultContentPart): boolean => {
  return content.is_error === true;
};

/**
 * 格式化工具结果内容
 */
const formatToolResult = (content: unknown): string => {
  if (content === null || content === undefined) {
    return '(空结果)';
  }
  if (typeof content === 'string') {
    return content;
  }
  if (typeof content === 'number' || typeof content === 'boolean') {
    return String(content);
  }
  if (Array.isArray(content)) {
    // 如果是内容块数组，尝试格式化
    if (content.length > 0 && typeof content[0] === 'object' && content[0] !== null) {
      return JSON.stringify(content, null, 2);
    }
    return content.map(String).join('\n');
  }
  if (typeof content === 'object') {
    return JSON.stringify(content, null, 2);
  }
  return String(content);
};

/**
 * 判断内容是否为代码
 */
const isCodeContent = (str: string): boolean => {
  // 检查是否包含代码块标记
  if (str.includes('```')) return true;
  // 检查是否包含大量缩进（常见于代码）
  const lines = str.split('\n');
  const indentedLines = lines.filter(line => line.startsWith('  ') || line.startsWith('\t'));
  return indentedLines.length > lines.length * 0.3;
};

/**
 * 获取内容语言类型
 */
const getContentLanguage = (str: string): string => {
  // 简单的语言检测
  if (str.includes('function ') || str.includes('const ') || str.includes('let ')) return 'javascript';
  if (str.includes('def ') || str.includes('import ')) return 'python';
  if (str.includes('public class') || str.includes('private')) return 'java';
  if (str.includes('interface ') || str.includes('type ')) return 'typescript';
  return 'text';
};

export const ToolResultBlock = memo<ToolResultBlockProps>(function ToolResultBlock({
  content,
  toolUseId,
  className
}) {
  const isError = isErrorResult(content);
  const resultContent = formatToolResult(content.content);
  const isCode = isCodeContent(resultContent);
  const language = getContentLanguage(resultContent);

  return (
    <div
      className={cn(
        'tool-result-block rounded-lg border overflow-hidden',
        isError
          ? 'border-destructive/50 bg-destructive/5'
          : 'border-border bg-muted/20',
        className
      )}
    >
      {/* Result Header */}
      <div
        className={cn(
          'flex items-center gap-2 px-3 py-2 border-b',
          isError
            ? 'border-destructive/30 bg-destructive/10'
            : 'border-border bg-muted/30'
        )}
      >
        {/* Status Icon */}
        <div className={cn('flex items-center gap-1.5', isError ? 'text-destructive' : 'text-success')}>
          {isError ? (
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          ) : (
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          )}
          <span className="text-xs font-medium">
            {isError ? '错误' : '成功'}
          </span>
        </div>

        {/* Tool Use ID */}
        {toolUseId && (
          <span className="text-xs text-foreground-muted font-mono">
            → #{toolUseId.slice(0, 8)}
          </span>
        )}

        {/* Content Type Badge */}
        <div className="ml-auto">
          <span className="text-xs text-foreground-muted px-2 py-0.5 rounded-full bg-muted">
            {isCode ? '代码' : '文本'}
          </span>
        </div>
      </div>

      {/* Result Content */}
      <div className="p-3 max-h-[400px] overflow-auto">
        {isCode ? (
          <CodeBlock
            code={resultContent}
            language={language}
            className="text-xs"
          />
        ) : (
          <pre className="text-xs text-foreground whitespace-pre-wrap break-words font-mono">
            {resultContent}
          </pre>
        )}
      </div>
    </div>
  );
});
