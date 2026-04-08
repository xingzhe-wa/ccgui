/**
 * McpServerConfig - MCP 服务器配置表单组件
 *
 * 用于创建和编辑 MCP 服务器配置。
 */

import { memo, useState, useCallback, useEffect } from 'react';
import { X, Plus } from 'lucide-react';
import type { McpServer } from '@/shared/types';
import { McpScope, McpServerStatus } from '@/shared/types';
import { cn } from '@/shared/utils/cn';
import { Button } from '@/shared/components/ui/button/Button';

export interface McpServerConfigProps {
  /** MCP 服务器数据（null 表示创建新服务器） */
  server?: McpServer | null;
  /** 保存回调 */
  onSave: (server: McpServer) => void;
  /** 关闭回调 */
  onClose: () => void;
  className?: string;
}

const scopeOptions: { value: McpScope; label: string }[] = [
  { value: McpScope.GLOBAL, label: '全局' },
  { value: McpScope.PROJECT, label: '项目' }
];

const defaultServer: Partial<McpServer> = {
  name: '',
  description: '',
  command: '',
  args: [],
  env: {},
  enabled: true,
  capabilities: [],
  scope: McpScope.PROJECT
};

/**
 * MCP 服务器配置表单
 */
export const McpServerConfig = memo<McpServerConfigProps>(function McpServerConfig({
  server,
  onSave,
  onClose,
  className
}: McpServerConfigProps) {
  const [formData, setFormData] = useState<Partial<McpServer>>(() => {
    if (server) {
      return { ...server };
    }
    return { ...defaultServer };
  });

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [envEntries, setEnvEntries] = useState<Array<{ key: string; value: string }>>([]);

  useEffect(() => {
    if (server) {
      setFormData({ ...server });
      setEnvEntries(
        Object.entries(server.env || {}).map(([key, value]) => ({ key, value }))
      );
    } else {
      setFormData({ ...defaultServer });
      setEnvEntries([]);
    }
  }, [server]);

  const validate = useCallback(() => {
    const newErrors: Record<string, string> = {};

    if (!formData.name?.trim()) {
      newErrors.name = '名称不能为空';
    }
    if (!formData.command?.trim()) {
      newErrors.command = '命令不能为空';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [formData]);

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();

      if (!validate()) return;

      const env: Record<string, string> = {};
      envEntries.forEach(({ key, value }) => {
        if (key.trim()) {
          env[key.trim()] = value;
        }
      });

      const finalServer: McpServer = {
        id: server?.id || `mcp-${Date.now()}`,
        name: formData.name?.trim() || '',
        description: formData.description?.trim() || '',
        command: formData.command?.trim() || '',
        args: formData.args || [],
        env,
        enabled: formData.enabled ?? true,
        capabilities: formData.capabilities || [],
        scope: formData.scope || McpScope.PROJECT,
        status: server?.status ?? McpServerStatus.DISCONNECTED,
        lastConnected: server?.lastConnected
      };

      onSave(finalServer);
    },
    [formData, server, validate, onSave, envEntries]
  );

  const handleChange = useCallback(
    (field: keyof McpServer, value: unknown) => {
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

  const handleArgsChange = useCallback(
    (value: string) => {
      const args = value
        .split(' ')
        .map((arg) => arg.trim())
        .filter(Boolean);
      handleChange('args', args);
    },
    [handleChange]
  );

  const handleAddEnv = useCallback(() => {
    setEnvEntries((prev) => [...prev, { key: '', value: '' }]);
  }, []);

  const handleUpdateEnv = useCallback(
    (index: number, field: 'key' | 'value', value: string) => {
      setEnvEntries((prev) => {
        const current = prev[index];
        if (!current) return prev;
        const updated: typeof prev[number] = {
          key: field === 'key' ? value : current.key,
          value: field === 'value' ? value : current.value
        };
        const newEntries = [...prev];
        newEntries[index] = updated;
        return newEntries;
      });
    },
    []
  );

  const handleRemoveEnv = useCallback((index: number) => {
    setEnvEntries((prev) => prev.filter((_, i) => i !== index));
  }, []);

  return (
    <div className={cn('fixed inset-0 z-50 flex items-center justify-center bg-black/50', className)}>
      <div className="bg-background rounded-lg shadow-xl w-full max-w-2xl max-h-[90vh] overflow-hidden">
        {/* 头部 */}
        <div className="flex items-center justify-between px-6 py-4 border-b">
          <h2 className="text-lg font-semibold">
            {server?.id ? '编辑 MCP 服务器' : '新建 MCP 服务器'}
          </h2>
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
                placeholder="输入服务器名称"
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
                placeholder="输入服务器描述"
              />
            </div>

            {/* 命令 */}
            <div>
              <label className="block text-sm font-medium mb-1">命令</label>
              <input
                type="text"
                value={formData.command || ''}
                onChange={(e) => handleChange('command', e.target.value)}
                className={cn(
                  'w-full px-3 py-2 rounded-md border bg-background text-sm font-mono',
                  'focus:outline-none focus:ring-2 focus:ring-ring',
                  errors.command ? 'border-destructive' : 'border-input'
                )}
                placeholder="如: npx"
              />
              {errors.command && <p className="text-xs text-destructive mt-1">{errors.command}</p>}
            </div>

            {/* 参数 */}
            <div>
              <label className="block text-sm font-medium mb-1">参数</label>
              <input
                type="text"
                value={(formData.args || []).join(' ')}
                onChange={(e) => handleArgsChange(e.target.value)}
                className="w-full px-3 py-2 rounded-md border border-input bg-background text-sm font-mono"
                placeholder="如: -y @modelcontextprotocol/server-filesystem"
              />
              <p className="text-xs text-muted-foreground mt-1">多个参数用空格分隔</p>
            </div>

            {/* 环境变量 */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-sm font-medium">环境变量</label>
                <Button type="button" variant="outline" size="sm" onClick={handleAddEnv}>
                  <Plus className="h-3 w-3 mr-1" />
                  添加
                </Button>
              </div>
              {envEntries.map((entry, index) => (
                <div key={index} className="flex gap-2 mb-2 items-center">
                  <input
                    type="text"
                    value={entry.key}
                    onChange={(e) => handleUpdateEnv(index, 'key', e.target.value)}
                    className="flex-1 px-2 py-1 rounded-md border border-input bg-background text-sm"
                    placeholder="KEY"
                  />
                  <span className="text-muted-foreground">=</span>
                  <input
                    type="text"
                    value={entry.value}
                    onChange={(e) => handleUpdateEnv(index, 'value', e.target.value)}
                    className="flex-1 px-2 py-1 rounded-md border border-input bg-background text-sm"
                    placeholder="value"
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    onClick={() => handleRemoveEnv(index)}
                    className="hover:text-destructive"
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              ))}
              {envEntries.length === 0 && (
                <p className="text-xs text-muted-foreground">暂无环境变量</p>
              )}
            </div>

            {/* 作用域 */}
            <div>
              <label className="block text-sm font-medium mb-1">作用域</label>
              <select
                value={formData.scope || McpScope.PROJECT}
                onChange={(e) => handleChange('scope', e.target.value as McpScope)}
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
              <label className="block text-sm font-medium mb-1">能力（可选）</label>
              <input
                type="text"
                value={(formData.capabilities || []).join(', ')}
                onChange={(e) =>
                  handleChange(
                    'capabilities',
                    e.target.value.split(',').map((s) => s.trim()).filter(Boolean)
                  )
                }
                className="w-full px-3 py-2 rounded-md border border-input bg-background text-sm"
                placeholder="用逗号分隔，如: filesystem, memory"
              />
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
                启用此服务器
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
