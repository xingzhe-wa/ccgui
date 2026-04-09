/**
 * InputToolbar - 输入工具栏组件
 */

import { memo } from 'react';
import { cn } from '@/shared/utils/cn';

interface ToolbarButtonProps {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
  active?: boolean;
  disabled?: boolean;
}

const ToolbarButton = memo<ToolbarButtonProps>(function ToolbarButton({
  icon,
  label,
  onClick,
  active = false,
  disabled = false
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={cn(
        'p-2 rounded-lg transition-colors',
        'hover:bg-background-secondary',
        'disabled:opacity-50 disabled:cursor-not-allowed',
        active ? 'text-primary bg-primary/10' : 'text-foreground-muted hover:text-foreground'
      )}
      aria-label={label}
      title={label}
    >
      {icon}
    </button>
  );
});

interface InputToolbarProps {
  onAttach?: () => void;
  onStop?: () => void;
  onOptimize?: () => void;
  isStreaming?: boolean;
  disabled?: boolean;
  className?: string;
}

export const InputToolbar = memo<InputToolbarProps>(function InputToolbar({
  onAttach,
  onStop,
  onOptimize,
  isStreaming = false,
  disabled = false,
  className
}) {
  return (
    <div className={cn('flex items-center gap-1 px-2', className)}>
      <ToolbarButton
        icon={
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13"
            />
          </svg>
        }
        label="Attach files"
        onClick={onAttach || (() => {})}
        disabled={disabled || isStreaming}
      />

      <ToolbarButton
        icon={
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
            />
          </svg>
        }
        label="Attach image"
        onClick={onAttach || (() => {})}
        disabled={disabled || isStreaming}
      />

      <div className="flex-1" />

      {onOptimize && (
        <ToolbarButton
          icon={
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" />
            </svg>
          }
          label="Optimize prompt"
          onClick={onOptimize}
          disabled={disabled || isStreaming}
        />
      )}

      {isStreaming && (
        <ToolbarButton
          icon={
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M9 10a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1h-4a1 1 0 01-1-1v-4z"
              />
            </svg>
          }
          label="Stop generating"
          onClick={onStop || (() => {})}
        />
      )}
    </div>
  );
});

export type { InputToolbarProps };
