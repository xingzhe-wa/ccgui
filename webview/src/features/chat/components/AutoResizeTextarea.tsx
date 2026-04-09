/**
 * AutoResizeTextarea - 自动调整高度的文本输入框
 */

import { memo, useRef, useEffect, useCallback, KeyboardEvent, forwardRef } from 'react';
import { cn } from '@/shared/utils/cn';

interface AutoResizeTextareaProps {
  value: string;
  onChange: (value: string) => void;
  onSubmit?: () => void;
  placeholder?: string;
  disabled?: boolean;
  maxRows?: number;
  minRows?: number;
  className?: string;
}

const AutoResizeTextareaInner = forwardRef<HTMLTextAreaElement, AutoResizeTextareaProps>(function AutoResizeTextarea({
  value,
  onChange,
  onSubmit,
  placeholder = 'Type a message...',
  disabled = false,
  maxRows = 10,
  minRows = 1,
  className
}, ref) {
  const internalRef = useRef<HTMLTextAreaElement>(null);
  const textareaRef = (ref as React.RefObject<HTMLTextAreaElement>) || internalRef;

  const adjustHeight = useCallback(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    textarea.style.height = 'auto';
    const lineHeight = parseInt(getComputedStyle(textarea).lineHeight) || 24;
    const maxHeight = lineHeight * maxRows;
    const minHeight = lineHeight * minRows;
    const scrollHeight = textarea.scrollHeight;

    textarea.style.height = `${Math.min(Math.max(scrollHeight, minHeight), maxHeight)}px`;
    textarea.style.overflowY = scrollHeight > maxHeight ? 'auto' : 'hidden';
  }, [maxRows, minRows]);

  useEffect(() => {
    adjustHeight();
  }, [value, adjustHeight]);

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
        e.preventDefault();
        if (value.trim() && onSubmit) {
          onSubmit();
        }
      }
    },
    [value, onSubmit]
  );

  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      onChange(e.target.value);
    },
    [onChange]
  );

  return (
    <textarea
      ref={textareaRef}
      value={value}
      onChange={handleChange}
      onKeyDown={handleKeyDown}
      placeholder={placeholder}
      disabled={disabled}
      rows={minRows}
      className={cn(
        'w-full resize-none bg-transparent',
        'text-foreground placeholder:text-foreground-muted',
        'focus:outline-none',
        'disabled:opacity-50 disabled:cursor-not-allowed',
        'py-2 px-3',
        className
      )}
      style={{ lineHeight: '24px' }}
    />
  );
});

export const AutoResizeTextarea = memo(AutoResizeTextareaInner);

export type { AutoResizeTextareaProps };
