/**
 * ThemeEditor - 主题编辑器组件
 */

import { memo, useState, useCallback } from 'react';
import { useTheme } from '@/shared/hooks';
import { cn } from '@/shared/utils/cn';
import type { ThemeConfig } from '@/shared/types';

interface ThemeEditorProps {
  className?: string;
  onSave?: (theme: ThemeConfig) => void;
  onCancel?: () => void;
}

interface ColorInputProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
}

const ColorInput = memo<ColorInputProps>(function ColorInput({ label, value, onChange }) {
  return (
    <div className="flex items-center gap-3">
      <input
        type="color"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-8 h-8 rounded cursor-pointer border border-border"
      />
      <div className="flex-1">
        <label className="text-xs text-foreground-muted">{label}</label>
        <div className="text-sm text-foreground font-mono">{value}</div>
      </div>
    </div>
  );
});

export const ThemeEditor = memo<ThemeEditorProps>(function ThemeEditor({ className, onSave, onCancel }) {
  const { theme, setTheme } = useTheme();
  const [editedTheme, setEditedTheme] = useState<ThemeConfig>(theme);

  const handleColorChange = useCallback((path: string, value: string) => {
    setEditedTheme((prev) => {
      const newTheme = { ...prev };
      const keys = path.split('.');
      let obj: any = newTheme;
      for (let i = 0; i < keys.length - 1; i++) {
        const key = keys[i];
        if (key !== undefined) {
          obj = obj[key];
        }
      }
      const lastKey = keys[keys.length - 1];
      if (lastKey !== undefined) {
        obj[lastKey] = value;
      }
      return newTheme;
    });
  }, []);

  const handleSave = useCallback(() => {
    setTheme(editedTheme);
    onSave?.(editedTheme);
  }, [editedTheme, setTheme, onSave]);

  const handleReset = useCallback(() => {
    setEditedTheme(theme);
  }, [theme]);

  return (
    <div className={cn('bg-background-secondary rounded-lg p-4', className)}>
      <div className="space-y-6">
        {/* 主色调 */}
        <section>
          <h3 className="text-sm font-medium text-foreground mb-3">Primary Colors</h3>
          <div className="grid grid-cols-2 gap-4">
            <ColorInput
              label="Primary"
              value={editedTheme.colors.primary}
              onChange={(v) => handleColorChange('colors.primary', v)}
            />
            <ColorInput
              label="Accent"
              value={editedTheme.colors.accent}
              onChange={(v) => handleColorChange('colors.accent', v)}
            />
          </div>
        </section>

        {/* 背景色 */}
        <section>
          <h3 className="text-sm font-medium text-foreground mb-3">Background</h3>
          <div className="grid grid-cols-2 gap-4">
            <ColorInput
              label="Background"
              value={editedTheme.colors.background}
              onChange={(v) => handleColorChange('colors.background', v)}
            />
            <ColorInput
              label="Muted"
              value={editedTheme.colors.muted}
              onChange={(v) => handleColorChange('colors.muted', v)}
            />
          </div>
        </section>

        {/* 消息气泡色 */}
        <section>
          <h3 className="text-sm font-medium text-foreground mb-3">Message Bubbles</h3>
          <div className="grid grid-cols-2 gap-4">
            <ColorInput
              label="User Message"
              value={editedTheme.colors.userMessage}
              onChange={(v) => handleColorChange('colors.userMessage', v)}
            />
            <ColorInput
              label="AI Message"
              value={editedTheme.colors.aiMessage}
              onChange={(v) => handleColorChange('colors.aiMessage', v)}
            />
          </div>
        </section>

        {/* 预览区域 */}
        <section>
          <h3 className="text-sm font-medium text-foreground mb-3">Preview</h3>
          <div
            className="p-4 rounded-lg space-y-2"
            style={{ backgroundColor: editedTheme.colors.background }}
          >
            <div
              className="px-3 py-2 rounded-lg max-w-[80%] text-sm"
              style={{
                backgroundColor: editedTheme.colors.userMessage,
                color: '#ffffff'
              }}
            >
              User message preview
            </div>
            <div
              className="px-3 py-2 rounded-lg max-w-[80%] text-sm ml-auto"
              style={{
                backgroundColor: editedTheme.colors.aiMessage,
                color: editedTheme.colors.foreground
              }}
            >
              AI message preview
            </div>
          </div>
        </section>

        {/* 操作按钮 */}
        <div className="flex items-center justify-end gap-2 pt-2 border-t border-border">
          <button
            type="button"
            onClick={handleReset}
            className={cn(
              'px-4 py-2 text-sm rounded-md',
              'bg-background hover:bg-background-elevated',
              'border border-border transition-colors'
            )}
          >
            Reset
          </button>
          <button
            type="button"
            onClick={onCancel}
            className={cn(
              'px-4 py-2 text-sm rounded-md',
              'hover:bg-background-elevated',
              'border border-border transition-colors'
            )}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSave}
            className={cn(
              'px-4 py-2 text-sm rounded-md',
              'bg-primary text-primary-foreground',
              'hover:opacity-90 transition-colors'
            )}
          >
            Apply
          </button>
        </div>
      </div>
    </div>
  );
});

export type { ThemeEditorProps };
