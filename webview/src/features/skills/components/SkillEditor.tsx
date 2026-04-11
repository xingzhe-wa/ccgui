/**
 * SkillEditor - Skill 编辑表单组件
 *
 * 用于创建和编辑 Skill，包含表单验证。
 */

import { memo, useState, useCallback, useEffect } from 'react';
import { X } from 'lucide-react';
import type { Skill, SkillCategory } from '@/shared/types';
import { SkillCategory as SkillCategoryEnum, SkillScope } from '@/shared/types';
import { cn } from '@/shared/utils/cn';
import { Button } from '@/shared/components/ui/button/Button';

export interface SkillEditorProps {
  /** Skill 数据（null 表示创建新 Skill） */
  skill?: Skill | null;
  /** 保存回调 */
  onSave: (skill: Skill) => void;
  /** 关闭回调 */
  onClose: () => void;
  className?: string;
}

const categoryOptions: { value: SkillCategory; label: string }[] = [
  { value: SkillCategoryEnum.CODE_GENERATION, label: '代码生成' },
  { value: SkillCategoryEnum.CODE_REVIEW, label: '代码审查' },
  { value: SkillCategoryEnum.REFACTORING, label: '重构' },
  { value: SkillCategoryEnum.TESTING, label: '测试' },
  { value: SkillCategoryEnum.DOCUMENTATION, label: '文档' },
  { value: SkillCategoryEnum.DEBUGGING, label: '调试' },
  { value: SkillCategoryEnum.PERFORMANCE, label: '性能优化' }
];

const scopeOptions: { value: SkillScope; label: string }[] = [
  { value: SkillScope.GLOBAL, label: '全局' },
  { value: SkillScope.PROJECT, label: '项目' }
];

const defaultSkill: Partial<Skill> = {
  name: '',
  description: '',
  icon: '🔧',
  category: SkillCategoryEnum.CODE_GENERATION,
  prompt: '',
  variables: [],
  enabled: true,
  scope: SkillScope.PROJECT
};

/**
 * Skill 编辑表单
 */
export const SkillEditor = memo<SkillEditorProps>(function SkillEditor({
  skill,
  onSave,
  onClose,
  className
}: SkillEditorProps) {
  const [formData, setFormData] = useState<Partial<Skill>>(() => {
    if (skill) {
      return { ...skill };
    }
    return { ...defaultSkill };
  });

  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (skill) {
      setFormData({ ...skill });
    } else {
      setFormData({ ...defaultSkill });
    }
  }, [skill]);

  const validate = useCallback(() => {
    const newErrors: Record<string, string> = {};

    if (!formData.name?.trim()) {
      newErrors.name = '名称不能为空';
    }
    if (!formData.prompt?.trim()) {
      newErrors.prompt = 'Prompt 不能为空';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [formData]);

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();

      if (!validate()) return;

      const finalSkill: Skill = {
        id: skill?.id || `skill-${Date.now()}`,
        name: formData.name?.trim() || '',
        description: formData.description?.trim() || '',
        icon: formData.icon || '🔧',
        category: formData.category || SkillCategoryEnum.CODE_GENERATION,
        prompt: formData.prompt?.trim() || '',
        variables: formData.variables || [],
        shortcut: formData.shortcut,
        enabled: formData.enabled ?? true,
        scope: formData.scope || SkillScope.PROJECT,
        createdAt: skill?.createdAt || Date.now(),
        updatedAt: Date.now()
      };

      onSave(finalSkill);
    },
    [formData, skill, validate, onSave]
  );

  const handleChange = useCallback(
    (field: keyof Skill, value: unknown) => {
      setFormData((prev) => ({ ...prev, [field]: value }));
      if (errors[field]) {
        setErrors((prev) => {
          const newErrors = { ...prev };
          delete newErrors[field];
          return newErrors;
        });
      }
    },
    [errors]
  );

  return (
    <div className={cn('fixed inset-0 z-50 flex items-center justify-center bg-black/50', className)}>
      <div className="bg-background rounded-lg shadow-xl w-full max-w-2xl max-h-[90vh] overflow-hidden">
        {/* 头部 */}
        <div className="flex items-center justify-between px-6 py-4 border-b">
          <h2 className="text-lg font-semibold">{skill?.id ? '编辑 Skill' : '新建 Skill'}</h2>
          <Button variant="ghost" size="icon" onClick={onClose}>
            <X className="h-5 w-5" />
          </Button>
        </div>

        {/* 表单 */}
        <form onSubmit={handleSubmit} className="p-6 overflow-y-auto max-h-[calc(90vh-140px)]">
          <div className="space-y-4">
            {/* 名称 */}
            <div>
              <label className="block text-sm font-medium mb-1">名称</label>
              <input
                type="text"
                value={formData.name || ''}
                onChange={(e) => handleChange('name', e.target.value)}
                className={cn(
                  'w-full px-3 py-2 rounded-md border bg-background text-sm',
                  'focus:outline-none focus:ring-2 focus:ring-ring',
                  errors.name ? 'border-destructive' : 'border-input'
                )}
                placeholder="输入 Skill 名称"
              />
              {errors.name && <p className="text-xs text-destructive mt-1">{errors.name}</p>}
            </div>

            {/* 描述 */}
            <div>
              <label className="block text-sm font-medium mb-1">描述</label>
              <textarea
                value={formData.description || ''}
                onChange={(e) => handleChange('description', e.target.value)}
                className={cn(
                  'w-full px-3 py-2 rounded-md border bg-background text-sm',
                  'focus:outline-none focus:ring-2 focus:ring-ring resize-none'
                )}
                rows={2}
                placeholder="输入 Skill 描述"
              />
            </div>

            {/* 图标和分类 */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">图标</label>
                <input
                  type="text"
                  value={formData.icon || ''}
                  onChange={(e) => handleChange('icon', e.target.value)}
                  className="w-full px-3 py-2 rounded-md border border-input bg-background text-sm"
                  placeholder="🔧"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">分类</label>
                <select
                  value={formData.category || SkillCategoryEnum.CODE_GENERATION}
                  onChange={(e) => handleChange('category', e.target.value as SkillCategory)}
                  className="w-full px-3 py-2 rounded-md border border-input bg-background text-sm"
                >
                  {categoryOptions.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* 作用域 */}
            <div>
              <label className="block text-sm font-medium mb-1">作用域</label>
              <select
                value={formData.scope || SkillScope.PROJECT}
                onChange={(e) => handleChange('scope', e.target.value as SkillScope)}
                className="w-full px-3 py-2 rounded-md border border-input bg-background text-sm"
              >
                {scopeOptions.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>

            {/* Prompt */}
            <div>
              <label className="block text-sm font-medium mb-1">Prompt 模板</label>
              <textarea
                value={formData.prompt || ''}
                onChange={(e) => handleChange('prompt', e.target.value)}
                className={cn(
                  'w-full px-3 py-2 rounded-md border bg-background text-sm font-mono',
                  'focus:outline-none focus:ring-2 focus:ring-ring resize-none',
                  errors.prompt ? 'border-destructive' : 'border-input'
                )}
                rows={8}
                placeholder="输入 Skill 的 prompt 模板..."
              />
              {errors.prompt && <p className="text-xs text-destructive mt-1">{errors.prompt}</p>}
            </div>

            {/* 快捷键 */}
            <div>
              <label className="block text-sm font-medium mb-1">快捷键（可选）</label>
              <input
                type="text"
                value={formData.shortcut || ''}
                onChange={(e) => handleChange('shortcut', e.target.value)}
                className="w-full px-3 py-2 rounded-md border border-input bg-background text-sm"
                placeholder="如: Cmd+Shift+S"
              />
            </div>
          </div>
        </form>

        {/* 底部 */}
        <div className="flex items-center justify-end gap-2 px-6 py-4 border-t">
          <Button variant="outline" onClick={onClose}>
            取消
          </Button>
          <Button onClick={handleSubmit as unknown as React.MouseEventHandler<HTMLButtonElement>}>
            保存
          </Button>
        </div>
      </div>
    </div>
  );
});