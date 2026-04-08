/**
 * TypingCursor - 打字机光标组件
 *
 * 使用CSS animate-pulse实现闪烁效果，无需JavaScript定时器。
 * 颜色跟随主题，可通过color prop覆盖。
 */

import { memo } from 'react';
import { cn } from '@/shared/utils/cn';

export interface TypingCursorProps {
  className?: string;
  /** 光标颜色，默认使用当前文本颜色 */
  color?: string;
}

/**
 * 打字机光标组件
 *
 * 使用CSS animate-pulse实现闪烁效果，无需JavaScript定时器。
 * 颜色跟随主题，可通过color prop覆盖。
 */
export const TypingCursor = memo<TypingCursorProps>(function TypingCursor({
  className,
  color
}: TypingCursorProps) {
  return (
    <span
      className={cn(
        'ml-1 inline-block h-4 w-0.5 animate-pulse',
        className
      )}
      style={{
        backgroundColor: color || 'currentColor'
      }}
      aria-hidden="true"
    />
  );
});
