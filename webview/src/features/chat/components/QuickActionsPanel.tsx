/**
 * QuickActionsPanel - 快捷操作面板
 *
 * 提供7种常用代码/对话操作：复制、解释、润色、翻译、代码审查、优化、调试。
 */

import { memo, useCallback } from 'react';
import { cn } from '@/shared/utils/cn';

// ============== 图标组件 ==============

const CopyIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
  </svg>
);

const ExplainIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <circle cx="12" cy="12" r="10" />
    <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
    <line x1="12" y1="17" x2="12.01" y2="17" />
  </svg>
);

const PolishIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M12 19l7-7 3 3-7 7-3-3z" />
    <path d="M18 13l-1.5-7.5L2 2l3.5 14.5L13 18l5-5z" />
    <path d="M2 2l7.586 7.586" />
    <circle cx="11" cy="11" r="2" />
  </svg>
);

const TranslateIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M5 8l6 6" />
    <path d="M4 14l6-6 2-2" />
    <path d="M2 5h12" />
    <path d="M7 2h1" />
    <path d="M22 22l-5-10-5 10" />
    <path d="M14 18h6" />
  </svg>
);

const ReviewIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M9 11l3 3L22 4" />
    <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
  </svg>
);

const OptimizeIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
  </svg>
);

const DebugIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z" />
    <path d="M12 8v4" />
    <path d="M12 16h.01" />
  </svg>
);

const ChevronIcon = ({ className }: { className?: string }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <polyline points="6 9 12 15 18 9" />
  </svg>
);

// ============== 类型定义 ==============

export interface QuickAction {
  /** 操作ID */
  id: string;
  /** 操作名称 */
  label: string;
  /** 操作描述 */
  description?: string;
  /** 图标 */
  icon: React.ReactNode;
  /** 是否禁用 */
  disabled?: boolean;
}

export interface QuickActionsPanelProps {
  /** 快捷操作列表 */
  actions?: QuickAction[];
  /** 操作点击回调 */
  onAction?: (actionId: string) => void;
  /** 是否展开 */
  expanded?: boolean;
  /** 展开状态切换回调 */
  onToggle?: () => void;
  className?: string;
}

/**
 * 默认快捷操作
 */
const DEFAULT_ACTIONS: QuickAction[] = [
  {
    id: 'copy',
    label: '复制',
    description: '复制到剪贴板',
    icon: <CopyIcon />
  },
  {
    id: 'explain',
    label: '解释',
    description: '解释代码逻辑',
    icon: <ExplainIcon />
  },
  {
    id: 'polish',
    label: '润色',
    description: '优化代码表达',
    icon: <PolishIcon />
  },
  {
    id: 'translate',
    label: '翻译',
    description: '中英文互转',
    icon: <TranslateIcon />
  },
  {
    id: 'review',
    label: '审查',
    description: '代码质量审查',
    icon: <ReviewIcon />
  },
  {
    id: 'optimize',
    label: '优化',
    description: '性能优化建议',
    icon: <OptimizeIcon />
  },
  {
    id: 'debug',
    label: '调试',
    description: '添加调试代码',
    icon: <DebugIcon />
  }
];

/**
 * 快捷操作面板
 *
 * 提供7种常用代码/对话操作。
 */
export const QuickActionsPanel = memo<QuickActionsPanelProps>(function QuickActionsPanel({
  actions = DEFAULT_ACTIONS,
  onAction,
  expanded = false,
  onToggle,
  className
}: QuickActionsPanelProps) {
  const handleAction = useCallback(
    (actionId: string) => {
      onAction?.(actionId);
    },
    [onAction]
  );

  return (
    <div className={cn('rounded-lg border border-border bg-background', className)}>
      {/* 头部 */}
      <button
        type="button"
        onClick={onToggle}
        className="w-full flex items-center justify-between px-4 py-2 hover:bg-muted/50 transition-colors"
        aria-expanded={expanded}
      >
        <span className="text-sm font-medium">快捷操作</span>
        <ChevronIcon className={cn('w-4 h-4 transition-transform', expanded && 'rotate-180')} />
      </button>

      {/* 操作列表 */}
      {expanded && (
        <div className="px-2 pb-2 grid grid-cols-2 gap-1">
          {actions.map((action) => (
            <button
              key={action.id}
              type="button"
              onClick={() => handleAction(action.id)}
              disabled={action.disabled}
              className={cn(
                'flex items-center gap-2 px-3 py-2 rounded-md text-left transition-colors',
                'hover:bg-accent',
                'disabled:opacity-50 disabled:cursor-not-allowed'
              )}
            >
              <span className="flex-shrink-0 w-5 h-5 text-muted-foreground">{action.icon}</span>
              <span className="flex-1 min-w-0">
                <span className="block text-sm font-medium truncate">{action.label}</span>
                {action.description && (
                  <span className="block text-xs text-muted-foreground truncate">
                    {action.description}
                  </span>
                )}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
});
