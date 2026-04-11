/**
 * TabItem - 会话标签组件
 *
 * 单个会话Tab的展示，支持激活状态、关闭按钮和双击重命名。
 */

import { memo, useCallback, useState } from 'react';
import { X, Edit2 } from 'lucide-react';
import type { ChatSession } from '@/shared/types';
import { SessionType } from '@/shared/types';
import { useAppStore } from '@/shared/stores/appStore';
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
  const [isEditing, setIsEditing] = useState(false);
  const [editName, setEditName] = useState(session.name);
  const updateSession = useAppStore((s) => s.updateSession);

  const handleClose = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onClose();
    },
    [onClose]
  );

  // 双击开始编辑
  const handleDoubleClick = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    setEditName(session.name);
    setIsEditing(true);
  }, [session.name]);

  // 保存编辑
  const handleSave = useCallback(() => {
    if (editName.trim() && editName !== session.name) {
      updateSession(session.id, { name: editName.trim() });
    }
    setIsEditing(false);
  }, [editName, session.id, session.name, updateSession]);

  // 失去焦点保存
  const handleBlur = useCallback(() => {
    handleSave();
  }, [handleSave]);

  // 按Enter保存，Escape取消
  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSave();
    } else if (e.key === 'Escape') {
      setEditName(session.name);
      setIsEditing(false);
    }
  }, [handleSave, session.name]);

  return (
    <button
      type="button"
      onClick={onClick}
      onDoubleClick={handleDoubleClick}
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

      {/* 会话名称 - 可编辑 */}
      {isEditing ? (
        <input
          type="text"
          value={editName}
          onChange={(e) => setEditName(e.target.value)}
          onBlur={handleBlur}
          onKeyDown={handleKeyDown}
          onClick={(e) => e.stopPropagation()}
          className="max-w-[100px] bg-transparent border-b border-primary outline-none text-sm"
          autoFocus
        />
      ) : (
        <span className="max-w-[120px] truncate">{session.name}</span>
      )}

      {/* 编辑指示器（悬停时显示） */}
      {!isEditing && (
        <Edit2 className="h-3 w-3 opacity-0 group-hover:opacity-50 shrink-0" />
      )}

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