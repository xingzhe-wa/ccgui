/**
 * ScopeManager - 作用域管理器组件
 *
 * 用于管理不同作用域的资源。
 */

import { memo, useCallback } from 'react';
import { Globe, Folder } from 'lucide-react';
import type { McpScope } from '@/shared/types';
import { McpScope as ScopeEnum } from '@/shared/types';
import { cn } from '@/shared/utils/cn';

export interface ScopeManagerProps {
  /** 当前作用域 */
  scope: McpScope;
  /** 作用域变更回调 */
  onScopeChange?: (scope: McpScope) => void;
  /** 是否禁用 */
  disabled?: boolean;
  className?: string;
}

const scopeConfig: Record<
  McpScope,
  { icon: typeof Globe; label: string; description: string }
> = {
  [ScopeEnum.GLOBAL]: {
    icon: Globe,
    label: '全局',
    description: '所有项目都可使用此配置'
  },
  [ScopeEnum.PROJECT]: {
    icon: Folder,
    label: '项目',
    description: '仅当前项目可使用此配置'
  }
};

/**
 * 作用域管理器
 */
export const ScopeManager = memo<ScopeManagerProps>(function ScopeManager({
  scope,
  onScopeChange,
  disabled = false,
  className
}: ScopeManagerProps) {
  const handleScopeChange = useCallback(
    (newScope: McpScope) => {
      if (!disabled && onScopeChange) {
        onScopeChange(newScope);
      }
    },
    [disabled, onScopeChange]
  );

  return (
    <div className={cn('flex gap-2', className)}>
      {Object.entries(scopeConfig).map(([scopeValue, config]) => {
        const Icon = config.icon;
        const isActive = scope === scopeValue;

        return (
          <button
            key={scopeValue}
            type="button"
            onClick={() => handleScopeChange(scopeValue as McpScope)}
            disabled={disabled}
            className={cn(
              'flex flex-col items-center gap-1 p-3 rounded-lg border transition-colors',
              isActive
                ? 'border-primary bg-primary/10'
                : 'border-input hover:bg-muted',
              disabled && 'opacity-50 cursor-not-allowed'
            )}
          >
            <Icon className={cn('h-5 w-5', isActive ? 'text-primary' : 'text-muted-foreground')} />
            <span className={cn('text-sm font-medium', isActive && 'text-primary')}>
              {config.label}
            </span>
            <span className="text-xs text-muted-foreground text-center max-w-[100px]">
              {config.description}
            </span>
          </button>
        );
      })}
    </div>
  );
});
