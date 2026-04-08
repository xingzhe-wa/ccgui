/**
 * AgentEditor - Agent 编辑表单组件
 *
 * 用于创建和编辑 Agent，包含表单验证。
 */

import { memo, useState, useCallback, useEffect } from 'react';
import { X } from 'lucide-react';
import type { Agent, AgentMode, AgentScope, AgentConstraint } from '@/shared/types';
import { AgentCapability, AgentMode as ModeEnum, AgentScope as ScopeEnum, ConstraintType } from '@/shared/types';
import { cn } from '@/shared/utils/cn';
import { Button } from '@/shared/components/ui/button/Button';

export interface AgentEditorProps {
  /** Agent 数据（null 表示创建新 Agent） */
  agent?: Agent | null;
  /** 保存回调 */
  onSave: (agent: Agent) => void;
  /** 关闭回调 */
  onClose: () => void;
  className?: string;
}

const capabilityOptions: { value: AgentCapability; label: string }[] = [
  { value: AgentCapability.CODE_GENERATION, label: '代码生成' },
  { value: AgentCapability.CODE_REVIEW, label: '代码审查' },
  { value: AgentCapability.REFACTORING, label: '重构' },
  { value: AgentCapability.TESTING, label: '测试' },
  { value: AgentCapability.DOCUMENTATION, label: '文档' },
  { value: AgentCapability.DEBUGGING, label: '调试' },
  { value: AgentCapability.FILE_OPERATION, label: '文件操作' },
  { value: AgentCapability.TERMINAL_OPERATION, label: '终端操作' }
];

const modeOptions: { value: AgentMode; label: string }[] = [
  { value: ModeEnum.CAUTIOUS, label: '谨慎模式 - 仅提供建议' },
  { value: ModeEnum.BALANCED, label: '平衡模式 - 建议为主，低风险自动执行' },
  { value: ModeEnum.AGGRESSIVE, label: '激进模式 - 自动执行高风险操作' }
];

const scopeOptions: { value: AgentScope; label: string }[] = [
  { value: ScopeEnum.GLOBAL, label: '全局' },
  { value: ScopeEnum.PROJECT, label: '项目' },
  { value: ScopeEnum.SESSION, label: '会话' }
];

const defaultAgent: Partial<Agent> = {
  name: '',
  description: '',
  avatar: '🤖',
  systemPrompt: '',
  capabilities: [],
  constraints: [],
  tools: [],
  mode: ModeEnum.BALANCED,
  scope: ScopeEnum.PROJECT,
  enabled: true
};

/**
 * Agent 编辑表单
 */
export const AgentEditor = memo<AgentEditorProps>(function AgentEditor({
  agent,
  onSave,
  onClose,
  className
}: AgentEditorProps) {
  const [formData, setFormData] = useState<Partial<Agent>>(() => {
    if (agent) {
      return { ...agent };
    }
    return { ...defaultAgent };
  });

  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (agent) {
      setFormData({ ...agent });
    } else {
      setFormData({ ...defaultAgent });
    }
  }, [agent]);

  const validate = useCallback(() => {
    const newErrors: Record<string, string> = {};

    if (!formData.name?.trim()) {
      newErrors.name = '名称不能为空';
    }
    if (!formData.systemPrompt?.trim()) {
      newErrors.systemPrompt = '系统提示词不能为空';
    }
    if (!formData.capabilities || formData.capabilities.length === 0) {
      newErrors.capabilities = '请至少选择一个能力';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [formData]);

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();

      if (!validate()) return;

      const finalAgent: Agent = {
        id: agent?.id || `agent-${Date.now()}`,
        name: formData.name?.trim() || '',
        description: formData.description?.trim() || '',
        avatar: formData.avatar || '🤖',
        systemPrompt: formData.systemPrompt?.trim() || '',
        capabilities: formData.capabilities || [],
        constraints: formData.constraints || [],
        tools: formData.tools || [],
        mode: formData.mode || ModeEnum.BALANCED,
        scope: formData.scope || ScopeEnum.PROJECT,
        enabled: formData.enabled ?? true
      };

      onSave(finalAgent);
    },
    [formData, agent, validate, onSave]
  );

  const handleChange = useCallback(
    (field: keyof Agent, value: unknown) => {
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

  const handleCapabilityToggle = useCallback(
    (capability: AgentCapability) => {
      const current = formData.capabilities || [];
      const newCapabilities = current.includes(capability)
        ? current.filter((c) => c !== capability)
        : [...current, capability];
      handleChange('capabilities', newCapabilities);
    },
    [formData.capabilities, handleChange]
  );

  const handleAddConstraint = useCallback(() => {
    const newConstraint: AgentConstraint = {
      type: ConstraintType.MAX_TOKENS,
      description: '',
      parameters: {}
    };
    handleChange('constraints', [...(formData.constraints || []), newConstraint]);
  }, [formData.constraints, handleChange]);

  const handleUpdateConstraint = useCallback(
    (index: number, field: keyof AgentConstraint, value: unknown) => {
      const constraints = formData.constraints || [];
      const current = constraints[index];
      if (!current) return;
      const updatedConstraint: AgentConstraint = {
        type: field === 'type' ? (value as AgentConstraint['type']) : current.type,
        description: field === 'description' ? (value as string) : current.description,
        parameters: field === 'parameters' ? (value as Record<string, any>) : current.parameters
      };
      const updated = [...constraints];
      updated[index] = updatedConstraint;
      handleChange('constraints', updated);
    },
    [formData.constraints, handleChange]
  );

  const handleRemoveConstraint = useCallback(
    (index: number) => {
      const updated = [...(formData.constraints || [])];
      updated.splice(index, 1);
      handleChange('constraints', updated);
    },
    [formData.constraints, handleChange]
  );

  return (
    <div className={cn('fixed inset-0 z-50 flex items-center justify-center bg-black/50', className)}>
      <div className="bg-background rounded-lg shadow-xl w-full max-w-2xl max-h-[90vh] overflow-hidden">
        {/* 头部 */}
        <div className="flex items-center justify-between px-6 py-4 border-b">
          <h2 className="text-lg font-semibold">{agent?.id ? '编辑 Agent' : '新建 Agent'}</h2>
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
                placeholder="输入 Agent 名称"
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
                placeholder="输入 Agent 描述"
              />
            </div>

            {/* 头像 */}
            <div>
              <label className="block text-sm font-medium mb-1">头像</label>
              <input
                type="text"
                value={formData.avatar || ''}
                onChange={(e) => handleChange('avatar', e.target.value)}
                className="w-full px-3 py-2 rounded-md border border-input bg-background text-sm"
                placeholder="🤖"
              />
            </div>

            {/* 模式 */}
            <div>
              <label className="block text-sm font-medium mb-1">模式</label>
              <select
                value={formData.mode || ModeEnum.BALANCED}
                onChange={(e) => handleChange('mode', e.target.value as AgentMode)}
                className="w-full px-3 py-2 rounded-md border border-input bg-background text-sm"
              >
                {modeOptions.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>

            {/* 作用域 */}
            <div>
              <label className="block text-sm font-medium mb-1">作用域</label>
              <select
                value={formData.scope || ScopeEnum.PROJECT}
                onChange={(e) => handleChange('scope', e.target.value as AgentScope)}
                className="w-full px-3 py-2 rounded-md border border-input bg-background text-sm"
              >
                {scopeOptions.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>

            {/* 能力 */}
            <div>
              <label className="block text-sm font-medium mb-1">能力</label>
              <div className="grid grid-cols-2 gap-2">
                {capabilityOptions.map((opt) => (
                  <label
                    key={opt.value}
                    className={cn(
                      'flex items-center gap-2 px-3 py-2 rounded-md border cursor-pointer transition-colors',
                      (formData.capabilities || []).includes(opt.value)
                        ? 'border-primary bg-primary/10'
                        : 'border-input hover:bg-muted'
                    )}
                  >
                    <input
                      type="checkbox"
                      checked={(formData.capabilities || []).includes(opt.value)}
                      onChange={() => handleCapabilityToggle(opt.value)}
                      className="rounded"
                    />
                    <span className="text-sm">{opt.label}</span>
                  </label>
                ))}
              </div>
              {errors.capabilities && (
                <p className="text-xs text-destructive mt-1">{errors.capabilities}</p>
              )}
            </div>

            {/* 系统提示词 */}
            <div>
              <label className="block text-sm font-medium mb-1">系统提示词</label>
              <textarea
                value={formData.systemPrompt || ''}
                onChange={(e) => handleChange('systemPrompt', e.target.value)}
                className={cn(
                  'w-full px-3 py-2 rounded-md border bg-background text-sm font-mono',
                  'focus:outline-none focus:ring-2 focus:ring-ring resize-none',
                  errors.systemPrompt ? 'border-destructive' : 'border-input'
                )}
                rows={6}
                placeholder="输入 Agent 的系统提示词..."
              />
              {errors.systemPrompt && (
                <p className="text-xs text-destructive mt-1">{errors.systemPrompt}</p>
              )}
            </div>

            {/* 约束 */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-sm font-medium">约束条件</label>
                <Button type="button" variant="outline" size="sm" onClick={handleAddConstraint}>
                  添加约束
                </Button>
              </div>
              {(formData.constraints || []).map((constraint, index) => (
                <div key={index} className="flex gap-2 mb-2 items-start">
                  <select
                    value={constraint.type}
                    onChange={(e) =>
                      handleUpdateConstraint(index, 'type', e.target.value as ConstraintType)
                    }
                    className="px-2 py-1 rounded-md border border-input bg-background text-sm"
                  >
                    <option value={ConstraintType.MAX_TOKENS}>最大Token数</option>
                    <option value={ConstraintType.ALLOWED_FILE_TYPES}>允许的文件类型</option>
                    <option value={ConstraintType.FORBIDDEN_PATTERNS}>禁止的模式</option>
                    <option value={ConstraintType.RESOURCE_LIMITS}>资源限制</option>
                  </select>
                  <input
                    type="text"
                    value={constraint.description}
                    onChange={(e) =>
                      handleUpdateConstraint(index, 'description', e.target.value)
                    }
                    className="flex-1 px-2 py-1 rounded-md border border-input bg-background text-sm"
                    placeholder="约束描述"
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    onClick={() => handleRemoveConstraint(index)}
                    className="hover:text-destructive"
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              ))}
            </div>

            {/* 启用状态 */}
            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="enabled"
                checked={formData.enabled ?? true}
                onChange={(e) => handleChange('enabled', e.target.checked)}
                className="rounded"
              />
              <label htmlFor="enabled" className="text-sm">
                启用此 Agent
              </label>
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
