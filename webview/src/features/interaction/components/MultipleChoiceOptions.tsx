/**
 * MultipleChoiceOptions - 多选题选项组件
 */

import { memo, useCallback } from 'react';
import { cn } from '@/shared/utils/cn';

export interface MultipleChoiceOptionsProps {
  /** 问题ID */
  questionId: string;
  /** 选项列表 */
  options: Array<{
    id: string;
    label: string;
    description?: string;
  }>;
  /** 当前选中的值列表 */
  selected: string[];
  /** 变更回调 */
  onChange: (value: string[]) => void;
  className?: string;
}

/**
 * 多选题选项组件
 *
 * 渲染多选列表，使用原生checkbox input实现无障碍支持。
 */
export const MultipleChoiceOptions = memo<MultipleChoiceOptionsProps>(function MultipleChoiceOptions({
  questionId: _questionId,
  options,
  selected,
  onChange,
  className
}: MultipleChoiceOptionsProps) {
  const handleToggle = useCallback(
    (value: string) => {
      const newSelected = selected.includes(value)
        ? selected.filter((v) => v !== value)
        : [...selected, value];
      onChange(newSelected);
    },
    [selected, onChange]
  );

  return (
    <div className={cn('space-y-2', className)} role="group" aria-label="多选选项列表">
      {options.map((option) => (
        <label
          key={option.id}
          className={cn(
            'flex cursor-pointer items-start gap-3 rounded-md border p-3 transition-colors',
            'hover:bg-accent/50',
            selected.includes(option.id) && 'border-primary bg-primary/5'
          )}
        >
          <div className="relative flex h-5 w-5 items-center justify-center">
            <input
              type="checkbox"
              checked={selected.includes(option.id)}
              onChange={() => handleToggle(option.id)}
              className="h-4 w-4 rounded border border-input text-primary accent-primary"
              aria-label={option.label}
            />
          </div>
          <div className="flex-1">
            <div className="font-medium">{option.label}</div>
            {option.description && (
              <div className="mt-1 text-sm text-muted-foreground">{option.description}</div>
            )}
          </div>
        </label>
      ))}
    </div>
  );
});
