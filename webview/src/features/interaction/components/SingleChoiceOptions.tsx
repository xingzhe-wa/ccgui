/**
 * SingleChoiceOptions - 单选题选项组件
 */

import { memo } from 'react';
import { cn } from '@/shared/utils/cn';

export interface SingleChoiceOptionsProps {
  /** 问题ID */
  questionId: string;
  /** 选项列表 */
  options: Array<{
    id: string;
    label: string;
    description?: string;
    icon?: string;
  }>;
  /** 当前选中的值 */
  selected: string;
  /** 变更回调 */
  onChange: (value: string) => void;
  className?: string;
}

/**
 * 单选题选项组件
 *
 * 渲染单选列表，使用原生radio input实现无障碍支持。
 */
export const SingleChoiceOptions = memo<SingleChoiceOptionsProps>(function SingleChoiceOptions({
  questionId,
  options,
  selected,
  onChange,
  className
}: SingleChoiceOptionsProps) {
  return (
    <div className={cn('space-y-2', className)} role="radiogroup" aria-label="选项列表">
      {options.map((option) => (
        <label
          key={option.id}
          className={cn(
            'flex cursor-pointer items-start gap-3 rounded-md border p-3 transition-colors',
            'hover:bg-accent/50',
            selected === option.id && 'border-primary bg-primary/5'
          )}
        >
          <input
            type="radio"
            name={questionId}
            value={option.id}
            checked={selected === option.id}
            onChange={(e) => onChange(e.target.value)}
            className="mt-1 h-4 w-4 text-primary accent-primary"
          />
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
