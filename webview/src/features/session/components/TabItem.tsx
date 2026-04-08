/**
 * TabItem - 会话标签组件
 *
 * 单个会话Tab的展示，支持激活状态和关闭按钮。
 */

import { memo, useCallback } from 'react';
import { X } from 'lucide-react';
import type { ChatSession } from '@/shared/types';
import { SessionType } from '@/shared/types';
import { cn } from '@/shared/utils/cn';

export interface TabItemProps {
  /** 会话数据 */
  session: ChatSession;
  /** 是否激活 */
  isActive: boolean;
  /** 点击回调 */
  onClick: () => void;
  /** 关闭回调 */
  onClose: () => void;
}

/**
 * 会话标签项
 */
export const TabItem = memo<TabItemProps>(function TabItem({
  session,
  isActive,
  onClick,
  onClose
}: TabItemProps) {
  const handleClose = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onClose();
    },
    [onClose]
  );

  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'group relative flex items-center gap-1.5 rounded-t-md px-3 py-1.5 text-sm transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
        isActive
          ? 'bg-background text-foreground font-medium'
          : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
      )}
    >
      {/* 会话类型图标 */}
      <span
        className={cn('text-xs', isActive ? 'text-primary' : 'text-muted')}
        aria-hidden="true"
      >
        {session.type === SessionType.PROJECT ? '🤖' : '💬'}
      </span>

      {/* 会话名称 */}
      <span className="max-w-[120px] truncate">{session.name}</span>

      {/* 关闭按钮 */}
      <span
        onClick={handleClose}
        className={cn(
          'ml-1 rounded-sm opacity-0 transition-opacity',
          'hover:bg-destructive/20 hover:text-destructive',
          'group-hover:opacity-100',
          isActive && 'opacity-100'
        )}
      >
        <X className="h-3 w-3" />
      </span>

      {/* 激活指示器 */}
      {isActive && (
        <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary" />
      )}
    </button>
  );
});