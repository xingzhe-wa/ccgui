/**
 * JsonConfigEditor - JSON 配置编辑器
 *
 * 支持 JSON 粘贴导入和一键导出
 */

import { memo, useState, useCallback } from 'react';
import { cn } from '@/shared/utils/cn';

interface JsonConfigEditorProps {
  value: string;
  onImport: (parsed: Record<string, unknown>) => void;
  onExport: () => string;
  className?: string;
}

export const JsonConfigEditor = memo<JsonConfigEditorProps>(function JsonConfigEditor({
  value,
  onImport,
  onExport,
  className
}) {
  const [localValue, setLocalValue] = useState(value);
  const [error, setError] = useState<string | null>(null);

  const handleImport = useCallback(() => {
    try {
      const parsed = JSON.parse(localValue);
      if (typeof parsed !== 'object' || parsed === null) {
        setError('JSON 必须是对象格式');
        return;
      }
      setError(null);
      onImport(parsed as Record<string, unknown>);
    } catch (e) {
      setError(`JSON 解析失败: ${(e as Error).message}`);
    }
  }, [localValue, onImport]);

  const handleExport = useCallback(() => {
    const exported = onExport();
    setLocalValue(exported);
    setError(null);
  }, [onExport]);

  return (
    <div className={cn('space-y-2', className)}>
      <div className="flex items-center justify-between">
        <label className="text-sm font-medium text-foreground">JSON 配置</label>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={handleExport}
            className="px-2 py-1 text-xs rounded bg-secondary text-secondary-foreground hover:bg-secondary/80"
          >
            导出当前
          </button>
          <button
            type="button"
            onClick={handleImport}
            className="px-2 py-1 text-xs rounded bg-primary text-primary-foreground hover:bg-primary/90"
          >
            导入
          </button>
        </div>
      </div>
      <textarea
        value={localValue}
        onChange={(e) => setLocalValue(e.target.value)}
        rows={8}
        placeholder='{"provider": "anthropic", "model": "claude-sonnet-4-20250514", ...}'
        className={cn(
          'w-full px-3 py-2 rounded-md text-xs font-mono',
          'bg-background border border-border',
          'text-foreground',
          'focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2',
          'resize-none'
        )}
      />
      {error && (
        <div className="text-xs text-destructive">{error}</div>
      )}
    </div>
  );
});
