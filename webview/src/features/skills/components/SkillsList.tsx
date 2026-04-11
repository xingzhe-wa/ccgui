/**
 * SkillsList - Skills 列表组件
 *
 * 展示 Skills 列表，支持网格布局。
 */

import { memo, useState, useCallback } from 'react';
import { FileX, Upload } from 'lucide-react';
import type { Skill } from '@/shared/types';
import { SkillCard } from './SkillCard';
import { SkillImportDialog } from './SkillImportDialog';
import { cn } from '@/shared/utils/cn';
import { Button } from '@/shared/components/ui/button/Button';

export interface SkillsListProps {
  /** Skills 列表 */
  skills: Skill[];
  /** 选中的 Skill ID */
  selectedId?: string;
  /** 点击 Skill 回调 */
  onSelect?: (skill: Skill) => void;
  /** 编辑 Skill 回调 */
  onEdit?: (skill: Skill) => void;
  /** 删除 Skill 回调 */
  onDelete?: (skillId: string) => void;
  /** 执行 Skill 回调 */
  onExecute?: (skill: Skill) => void;
  /** 复制 Skill 回调 */
  onDuplicate?: (skill: Skill) => void;
  /** 导出 Skill 回调 */
  onExport?: (skill: Skill) => void;
  /** 导入成功回调 */
  onImportSuccess?: (count: number) => void;
  className?: string;
}

/**
 * Skills 列表组件
 */
export const SkillsList = memo<SkillsListProps>(function SkillsList({
  skills,
  selectedId,
  onSelect,
  onEdit,
  onDelete,
  onExecute,
  onDuplicate,
  onExport,
  onImportSuccess,
  className
}: SkillsListProps) {
  const [showImportDialog, setShowImportDialog] = useState(false);

  const handleImportSuccess = useCallback(
    (count: number) => {
      onImportSuccess?.(count);
    },
    [onImportSuccess]
  );

  if (skills.length === 0) {
    return (
      <div className={cn('flex flex-col items-center justify-center py-12 text-center', className)}>
        <FileX className="h-12 w-12 text-muted-foreground/30 mb-4" />
        <p className="text-muted-foreground">暂无 Skills</p>
        <p className="text-xs text-muted-foreground mt-1">点击上方按钮创建第一个 Skill</p>
      </div>
    );
  }

  return (
    <>
      <div className={cn('grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4', className)}>
        {skills.map((skill) => (
          <SkillCard
            key={skill.id}
            skill={skill}
            isSelected={skill.id === selectedId}
            onClick={() => onSelect?.(skill)}
            onEdit={() => onEdit?.(skill)}
            onDelete={() => onDelete?.(skill.id)}
            onExecute={() => onExecute?.(skill)}
            onDuplicate={() => onDuplicate?.(skill)}
            onExport={() => onExport?.(skill)}
          />
        ))}
      </div>

      <SkillImportDialog
        isOpen={showImportDialog}
        onClose={() => setShowImportDialog(false)}
        onSuccess={handleImportSuccess}
      />
    </>
  );
});

// 导出导入对话框状态管理组件（供父组件使用）
export function useSkillsImportDialog() {
  const [showImportDialog, setShowImportDialog] = useState(false);

  const openImportDialog = useCallback(() => setShowImportDialog(true), []);
  const closeImportDialog = useCallback(() => setShowImportDialog(false), []);

  const ImportButton = useCallback(
    ({ className }: { className?: string }) => (
      <Button variant="outline" size="sm" onClick={openImportDialog} className={className}>
        <Upload className="h-4 w-4 mr-2" />
        导入
      </Button>
    ),
    [openImportDialog]
  );

  return {
    showImportDialog,
    openImportDialog,
    closeImportDialog,
    ImportButton
  };
}