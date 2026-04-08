/**
 * SendButton - 发送按钮组件
 */

import { memo } from 'react';
import { cn } from '@/shared/utils/cn';

interface SendButtonProps {
  onClick: () => void;
  disabled?: boolean;
  loading?: boolean;
  className?: string;
}

export const SendButton = memo<SendButtonProps>(function SendButton({
  onClick,
  disabled = false,
  loading = false,
  className
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled || loading}
      className={cn(
        'flex-shrink-0 w-10 h-10 rounded-full',
        'flex items-center justify-center',
        'transition-all duration-200',
        disabled || loading
          ? 'bg-background-secondary text-foreground-muted cursor-not-allowed'
          : 'bg-primary text-primary-foreground hover:bg-primary/90 active:scale-95',
        className
      )}
      aria-label="Send message"
    >
      {loading ? (
        <div className="w-5 h-5 border-2 border-primary-foreground border-t-transparent rounded-full animate-spin" />
      ) : (
        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"
          />
        </svg>
      )}
    </button>
  );
});

export type { SendButtonProps };
