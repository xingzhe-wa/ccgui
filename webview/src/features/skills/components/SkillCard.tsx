/**
 * SkillCard - Skill 卡片组件
 *
 * 展示单个 Skill 的基本信息，支持编辑和删除操作。
 */

import { memo, useCallback } from 'react';
import { Edit2, Trash2, Play, Copy } from 'lucide-react';
import type { Skill } from '@/shared/types';
import { cn } from '@/shared/utils/cn';
import { Button } from '@/shared/components/ui/button/Button';

export interface SkillCardProps {
  /** Skill 数据 */
  skill: Skill;
  /** 是否选中 */
  isSelected?: boolean;
  /** 点击回调 */
  onClick?: () => void;
  /** 编辑回调 */
  onEdit?: () => void;
  /** 删除回调 */
  onDelete?: () => void;
  /** 执行回调 */
  onExecute?: () => void;
  /** 复制回调 */
  onDuplicate?: () => void;
  className?: string;
}

const categoryLabels: Record<string, string> = {
  code_generation: '代码生成',
  code_review: '代码审查',
  refactoring: '重构',
  testing: '测试',
  documentation: '文档',
  debugging: '调试',
  performance: '性能优化'
};

/**
 * Skill 卡片组件
 */
export const SkillCard = memo<SkillCardProps>(function SkillCard({
  skill,
  isSelected = false,
  onClick,
  onEdit,
  onDelete,
  onExecute,
  onDuplicate,
  className
}: SkillCardProps) {
  const handleEdit = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onEdit?.();
    },
    [onEdit]
  );

  const handleDelete = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onDelete?.();
    },
    [onDelete]
  );

  const handleExecute = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onExecute?.();
    },
    [onExecute]
  );

  const handleDuplicate = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onDuplicate?.();
    },
    [onDuplicate]
  );

  return (
    <div
      onClick={onClick}
      className={cn(
        'group relative rounded-lg border bg-background p-4 transition-all cursor-pointer',
        'hover:shadow-md hover:border-primary/50',
        isSelected && 'border-primary bg-primary/5',
        className
      )}
    >
      {/* 头部 */}
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className="text-2xl">{skill.icon || '🔧'}</span>
          <div>
            <h3 className="font-medium text-sm">{skill.name}</h3>
            <span className="text-xs text-muted-foreground">
              {categoryLabels[skill.category] || skill.category}
            </span>
          </div>
        </div>
      </div>

      {/* 描述 */}
      <p className="text-xs text-muted-foreground line-clamp-2 mb-3">
        {skill.description || '暂无描述'}
      </p>

      {/* 快捷键 */}
      {skill.shortcut && (
        <div className="mb-3">
          <kbd className="px-2 py-0.5 text-xs bg-muted rounded border">{skill.shortcut}</kbd>
        </div>
      )}

      {/* 操作按钮 */}
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        {onExecute && (
          <Button variant="ghost" size="icon" onClick={handleExecute} title="执行">
            <Play className="h-4 w-4" />
          </Button>
        )}
        {onDuplicate && (
          <Button variant="ghost" size="icon" onClick={handleDuplicate} title="复制">
            <Copy className="h-4 w-4" />
          </Button>
        )}
        {onEdit && (
          <Button variant="ghost" size="icon" onClick={handleEdit} title="编辑">
            <Edit2 className="h-4 w-4" />
          </Button>
        )}
        {onDelete && (
          <Button
            variant="ghost"
            size="icon"
            onClick={handleDelete}
            title="删除"
            className="hover:text-destructive hover:bg-destructive/10"
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        )}
      </div>
    </div>
  );
});