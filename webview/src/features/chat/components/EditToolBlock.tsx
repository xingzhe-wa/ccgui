/**
 * EditToolBlock - 编辑工具块展示组件
 *
 * 对应架构文档中的 EditToolBlock（含 Diff）
 * 展示文件编辑操作的前后差异
 */

import { memo, useState, useMemo } from 'react';
import { cn } from '@/shared/utils/cn';
import { CodeBlock } from './CodeBlock';
import { computeDiff, formatUnifiedDiff, getDiffStats, DiffLineType } from '../utils/diffUtils';
import type { ToolUseContentPart } from '@/shared/types';

export interface EditToolBlockProps {
  /** 工具调用内容 */
  content: ToolUseContentPart;
  /** 工具结果内容 */
  result?: {
    oldContent?: string;
    newContent?: string;
    filePath?: string;
  };
  /** 是否正在执行 */
  isExecuting?: boolean;
  className?: string;
}

/**
 * 获取文件图标
 */
const getFileIcon = (filename: string): JSX.Element => {
  const ext = filename.split('.').pop()?.toLowerCase();
  const iconMap: Record<string, JSX.Element> = {
    js: <span className="text-xs font-bold text-yellow-500">JS</span>,
    jsx: <span className="text-xs font-bold text-yellow-500">JSX</span>,
    ts: <span className="text-xs font-bold text-blue-500">TS</span>,
    tsx: <span className="text-xs font-bold text-blue-500">TSX</span>,
    py: <span className="text-xs font-bold text-green-500">PY</span>,
    java: <span className="text-xs font-bold text-orange-500">JAVA</span>,
    kt: <span className="text-xs font-bold text-purple-500">KT</span>,
    go: <span className="text-xs font-bold text-cyan-500">GO</span>,
    rs: <span className="text-xs font-bold text-red-500">RS</span>,
    cpp: <span className="text-xs font-bold text-blue-400">CPP</span>,
    c: <span className="text-xs font-bold text-blue-400">C</span>,
  };

  return iconMap[ext || ''] || (
    <svg className="w-4 h-4 text-foreground-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
    </svg>
  );
};

/**
 * Diff 行渲染组件
 */
const DiffLine = memo<{ line: { type: DiffLineType; content: string }; index: number }>(
  function DiffLine({ line, index }) {
    return (
      <div
        className={cn(
          'flex items-start gap-2 px-3 py-0.5 font-mono text-xs',
          'hover:bg-muted/50 transition-colors',
          line.type === DiffLineType.ADDED && 'bg-diff-added/20',
          line.type === DiffLineType.DELETED && 'bg-diff-deleted/20'
        )}
      >
        {/* 行号 */}
        <span className="select-none text-foreground-muted w-8 text-right shrink-0">
          {index + 1}
        </span>

        {/* 标记 */}
        <span
          className={cn(
            'select-none w-4 shrink-0 font-bold',
            line.type === DiffLineType.ADDED && 'text-diff-added-accent',
            line.type === DiffLineType.DELETED && 'text-diff-deleted-accent',
            line.type === DiffLineType.UNCHANGED && 'text-foreground-muted'
          )}
        >
          {line.type === DiffLineType.ADDED && '+'}
          {line.type === DiffLineType.DELETED && '-'}
          {line.type === DiffLineType.UNCHANGED && ' '}
        </span>

        {/* 内容 */}
        <span
          className={cn(
            'flex-1 break-words',
            line.type === DiffLineType.ADDED && 'text-diff-added-foreground',
            line.type === DiffLineType.DELETED && 'text-diff-deleted-foreground'
          )}
        >
          {line.content || ' '}
        </span>
      </div>
    );
  }
);

export const EditToolBlock = memo<EditToolBlockProps>(function EditToolBlock({
  content,
  result,
  isExecuting = false,
  className
}) {
  const [viewMode, setViewMode] = useState<'diff' | 'unified' | 'side-by-side'>('diff');

  // 从工具输入中提取文件路径
  const filePath = useMemo(() => {
    if (typeof content.input === 'object' && content.input !== null) {
      const input = content.input as Record<string, unknown>;
      return input['path'] as string || input['file_path'] as string || result?.filePath || '';
    }
    return result?.filePath || '';
  }, [content.input, result?.filePath]);

  // 计算差异
  const diffResult = useMemo(() => {
    if (!result?.oldContent || !result?.newContent) {
      return null;
    }
    const oldLines = result.oldContent.split('\n');
    const newLines = result.newContent.split('\n');
    return computeDiff(oldLines, newLines);
  }, [result?.oldContent, result?.newContent]);

  // 计算统计信息
  const diffStats = useMemo(() => {
    if (!diffResult) return null;
    return getDiffStats(diffResult);
  }, [diffResult]);

  // 统一格式
  const unifiedDiff = useMemo(() => {
    if (!diffResult) return null;
    return formatUnifiedDiff(diffResult);
  }, [diffResult]);

  return (
    <div
      className={cn(
        'edit-tool-block rounded-lg border border-border bg-muted/20',
        'overflow-hidden',
        isExecuting && 'ring-2 ring-primary/50',
        className
      )}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 border-b border-border bg-muted/30">
        <div className="flex items-center gap-2">
          {/* 图标 */}
          <div className={cn('text-foreground-muted', isExecuting && 'animate-pulse')}>
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
            </svg>
          </div>

          {/* 文件名 */}
          <span className="font-mono text-sm font-medium text-foreground">
            {filePath || 'unknown file'}
          </span>

          {/* 文件类型图标 */}
          {filePath && <div className="ml-1">{getFileIcon(filePath)}</div>}
        </div>

        {/* 状态和统计 */}
        <div className="flex items-center gap-3">
          {isExecuting ? (
            <div className="flex items-center gap-1.5">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-primary"></span>
              </span>
              <span className="text-xs text-foreground-muted">编辑中</span>
            </div>
          ) : diffStats ? (
            <div className="flex items-center gap-2 text-xs">
              <span className="text-diff-added-accent">+{diffStats.additions}</span>
              <span className="text-diff-deleted-accent">-{diffStats.deletions}</span>
              <span className="text-foreground-muted">
                {diffStats.changePercentage.toFixed(1)}% changed
              </span>
            </div>
          ) : null}

          {/* 视图切换 */}
          {diffResult && (
            <div className="flex items-center gap-1 ml-2">
              <button
                type="button"
                onClick={() => setViewMode('diff')}
                className={cn(
                  'px-2 py-1 text-xs rounded transition-colors',
                  viewMode === 'diff'
                    ? 'bg-primary text-primary-foreground'
                    : 'hover:bg-muted'
                )}
              >
                Diff
              </button>
              <button
                type="button"
                onClick={() => setViewMode('unified')}
                className={cn(
                  'px-2 py-1 text-xs rounded transition-colors',
                  viewMode === 'unified'
                    ? 'bg-primary text-primary-foreground'
                    : 'hover:bg-muted'
                )}
              >
                Unified
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Content */}
      <div className="max-h-[500px] overflow-auto">
        {viewMode === 'diff' && diffResult ? (
          <div className="py-1">
            {diffResult.lines.map((line, index) => (
              <DiffLine key={index} line={line} index={index} />
            ))}
          </div>
        ) : viewMode === 'unified' && unifiedDiff ? (
          <div className="p-3">
            <CodeBlock code={unifiedDiff} language="diff" className="text-xs" />
          </div>
        ) : (
          <div className="p-3 text-sm text-foreground-muted">
            {isExecuting ? (
              <span>等待编辑结果...</span>
            ) : (
              <span>无差异内容</span>
            )}
          </div>
        )}
      </div>

      {/* Footer - 工具输入 */}
      {content.input && (
        <div className="px-3 py-2 border-t border-border bg-muted/20">
          <details className="text-xs">
            <summary className="cursor-pointer text-foreground-muted hover:text-foreground">
              查看工具输入
            </summary>
            <pre className="mt-2 p-2 rounded bg-background font-mono text-foreground-secondary overflow-x-auto">
              {JSON.stringify(content.input, null, 2)}
            </pre>
          </details>
        </div>
      )}
    </div>
  );
});
