/**
 * ConfirmationOptions - 确认选项组件
 *
 * 用于是/否确认类型的问题。
 */

import { memo } from 'react';
import { cn } from '@/shared/utils/cn';

export interface ConfirmationOptionsProps {
  /** 当前选中的值 */
  selected: boolean | null;
  /** 变更回调 */
  onChange: (value: boolean) => void;
  className?: string;
}

/**
 * 确认选项组件
 *
 * 用于是/否确认类型的问题。
 */
export const ConfirmationOptions = memo<ConfirmationOptionsProps>(function ConfirmationOptions({
  selected,
  onChange,
  className
}: ConfirmationOptionsProps) {
  return (
    <div className={cn('flex gap-4', className)} role="radiogroup" aria-label="确认选项">
      <label
        className={cn(
          'flex flex-1 cursor-pointer items-center justify-center gap-2 rounded-md border p-4 transition-colors',
          'hover:bg-accent/50',
          selected === true && 'border-primary bg-primary/5'
        )}
      >
        <input
          type="radio"
          checked={selected === true}
          onChange={() => onChange(true)}
          className="h-4 w-4 text-primary accent-primary"
        />
        <span className="font-medium">是</span>
      </label>

      <label
        className={cn(
          'flex flex-1 cursor-pointer items-center justify-center gap-2 rounded-md border p-4 transition-colors',
          'hover:bg-accent/50',
          selected === false && 'border-primary bg-primary/5'
        )}
      >
        <input
          type="radio"
          checked={selected === false}
          onChange={() => onChange(false)}
          className="h-4 w-4 text-primary accent-primary"
        />
        <span className="font-medium">否</span>
      </label>
    </div>
  );
});
