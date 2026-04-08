/**
 * Button 组件
 *
 * 使用 class-variance-authority 实现变体
 */

import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/shared/utils/cn';

// ============== 变体定义 ==============

const buttonVariants = cva(
  [
    'inline-flex',
    'items-center',
    'justify-center',
    'rounded-md',
    'text-sm',
    'font-medium',
    'transition-colors',
    'focus-visible:outline-none',
    'focus-visible:ring-2',
    'focus-visible:ring-offset-2',
    'disabled:pointer-events-none',
    'disabled:opacity-50'
  ],
  {
    variants: {
      variant: {
        primary: [
          'bg-primary',
          'text-primary-foreground',
          'hover:bg-primary/90'
        ],
        secondary: [
          'bg-secondary',
          'text-secondary-foreground',
          'hover:bg-secondary/80'
        ],
        ghost: [
          'hover:bg-accent',
          'hover:text-accent-foreground'
        ],
        destructive: [
          'bg-destructive',
          'text-destructive-foreground',
          'hover:bg-destructive/90'
        ],
        outline: [
          'border',
          'border-input',
          'bg-background',
          'hover:bg-accent',
          'hover:text-accent-foreground'
        ],
        link: [
          'text-primary',
          'underline-offset-4',
          'hover:underline'
        ]
      },
      size: {
        sm: ['h-8', 'px-3', 'text-xs'],
        md: ['h-9', 'px-4', 'text-sm'],
        lg: ['h-10', 'px-6', 'text-base'],
        icon: ['h-9', 'w-9']
      }
    },
    defaultVariants: {
      variant: 'primary',
      size: 'md'
    }
  }
);

// ============== Props 接口 ==============

export interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  /**
   * 加载状态
   */
  isLoading?: boolean;

  /**
   * 加载文本
   */
  loadingText?: string;

  /**
   * 左侧图标
   */
  leftIcon?: React.ReactNode;

  /**
   * 右侧图标
   */
  rightIcon?: React.ReactNode;
}

// ============== 组件实现 ==============

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      className,
      variant,
      size,
      isLoading = false,
      loadingText,
      leftIcon,
      rightIcon,
      children,
      disabled,
      ...props
    },
    ref
  ) => {
    return (
      <button
        ref={ref}
        className={cn(buttonVariants({ variant, size }), className)}
        disabled={disabled || isLoading}
        {...props}
      >
        {isLoading && (
          <>
            <LoadingSpinner className="mr-2 h-4 w-4 animate-spin" />
            {loadingText ?? children}
          </>
        )}
        {!isLoading && (
          <>
            {leftIcon && <span className="mr-2">{leftIcon}</span>}
            {children}
            {rightIcon && <span className="ml-2">{rightIcon}</span>}
          </>
        )}
      </button>
    );
  }
);

Button.displayName = 'Button';

// ============== LoadingSpinner 子组件 ==============

interface LoadingSpinnerProps {
  className?: string;
}

const LoadingSpinner = ({ className }: LoadingSpinnerProps): JSX.Element => (
  <svg
    className={className}
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
  >
    <circle
      className="opacity-25"
      cx="12"
      cy="12"
      r="10"
      stroke="currentColor"
      strokeWidth="4"
    />
    <path
      className="opacity-75"
      fill="currentColor"
      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
    />
  </svg>
);
