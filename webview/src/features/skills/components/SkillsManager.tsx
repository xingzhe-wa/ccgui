/**
 * SkillsManager - Skills 管理器组件
 *
 * 统一管理 Skills 的 CRUD 操作和列表展示。
 */

import { memo, useCallback, useState, useEffect } from 'react';
import { Plus, Search } from 'lucide-react';
import type { Skill } from '@/shared/types';
import { useSkillsStore } from '@/shared/stores/skillsStore';
import { SkillsList } from './SkillsList';
import { SkillEditor } from './SkillEditor';
import { cn } from '@/shared/utils/cn';
import { Button } from '@/shared/components/ui/button/Button';

export interface SkillsManagerProps {
  className?: string;
}

/**
 * Skills 管理器
 */
export const SkillsManager = memo<SkillsManagerProps>(function SkillsManager({
  className
}: SkillsManagerProps) {
  const { skills, saveSkill, deleteSkill, refreshSkills } = useSkillsStore();

  const [selectedSkillId, setSelectedSkillId] = useState<string | undefined>();
  const [editingSkill, setEditingSkill] = useState<Skill | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // 加载数据
  useEffect(() => {
    refreshSkills();
  }, [refreshSkills]);

  // 过滤后的 skills
  const filteredSkills = skills.filter(
    (skill) =>
      skill.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      skill.description.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // 创建
  const handleCreate = useCallback(() => {
    setEditingSkill(null);
    setIsCreating(true);
  }, []);

  // 编辑
  const handleEdit = useCallback((skill: Skill) => {
    setEditingSkill(skill);
    setIsCreating(false);
  }, []);

  // 保存
  const handleSave = useCallback(
    (skill: Skill) => {
      saveSkill(skill);
      setEditingSkill(null);
      setIsCreating(false);
    },
    [saveSkill]
  );

  // 删除
  const handleDelete = useCallback(
    (skillId: string) => {
      deleteSkill(skillId);
      if (selectedSkillId === skillId) {
        setSelectedSkillId(undefined);
      }
    },
    [deleteSkill, selectedSkillId]
  );

  // 关闭编辑器
  const handleCloseEditor = useCallback(() => {
    setEditingSkill(null);
    setIsCreating(false);
  }, []);

  // 复制
  const handleDuplicate = useCallback(
    (skill: Skill) => {
      const duplicated: Skill = {
        ...skill,
        id: `skill-${Date.now()}`,
        name: `${skill.name} (副本)`
      };
      saveSkill(duplicated);
    },
    [saveSkill]
  );

  return (
    <div className={cn('flex flex-col h-full', className)}>
      {/* 头部 */}
      <div className="flex items-center justify-between px-6 py-4 border-b">
        <div>
          <h1 className="text-xl font-semibold">Skills</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {skills.length} 个 Skills
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button onClick={handleCreate}>
            <Plus className="h-4 w-4 mr-2" />
            新建 Skill
          </Button>
        </div>
      </div>

      {/* 搜索栏 */}
      <div className="px-6 py-3 border-b">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <input
            type="search"
            placeholder="搜索 Skills..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-9 w-full max-w-md px-3 py-2 rounded-md border border-input bg-background text-sm"
          />
        </div>
      </div>

      {/* 列表 */}
      <div className="flex-1 overflow-y-auto p-6">
        <SkillsList
          skills={filteredSkills}
          selectedId={selectedSkillId}
          onSelect={(skill) => setSelectedSkillId(skill.id)}
          onEdit={handleEdit}
          onDelete={handleDelete}
          onDuplicate={handleDuplicate}
        />
      </div>

      {/* 编辑器弹窗 */}
      {(isCreating || editingSkill) && (
        <SkillEditor
          skill={editingSkill}
          onSave={handleSave}
          onClose={handleCloseEditor}
        />
      )}
    </div>
  );
});
