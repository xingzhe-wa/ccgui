# ClaudeCodeJet 组件设计规范

**文档版本**: 1.0
**创建日期**: 2026-04-08
**维护者**: Frontend Team

---

## 📋 目录

- [1. 组件设计原则](#1-组件设计原则)
- [2. 组件分类体系](#2-组件分类体系)
- [3. 基础UI组件](#3-基础ui组件)
- [4. 复合组件](#4-复合组件)
- [5. 业务组件](#5-业务组件)
- [6. 组件通信模式](#6-组件通信模式)
- [7. 组件性能优化](#7-组件性能优化)

---

## 1. 组件设计原则

### 1.1 SOLID原则在React中的体现

```typescript
// ✅ 单一职责原则 (SRP)
// 错误示例：一个组件做太多事情
const BadComponent = () => {
  const [messages, setMessages] = useState([]);
  const [theme, setTheme] = useState(null);
  const [sessions, setSessions] = useState([]);
  // ... 太多职责
};

// 正确示例：每个组件只负责一件事
const MessageList = ({ messages, onMessageClick }) => { /* 只负责显示消息 */ };
const ThemeSwitcher = ({ theme, onThemeChange }) => { /* 只负责主题切换 */ };
const SessionTabs = ({ sessions, onSessionSwitch }) => { /* 只负责会话切换 */ };

// ✅ 开闭原则 (OCP)
// 对扩展开放，对修改关闭
interface BaseComponentProps {
  className?: string;
}

// 通过组合扩展功能
const withLoading = <P extends object>(Component: React.ComponentType<P>) => {
  return (props: P & { isLoading?: boolean }) => {
    if (props.isLoading) return <LoadingSpinner />;
    return <Component {...props} />;
  };
};

// ✅ 依赖倒置原则 (DIP)
// 依赖抽象而不是具体实现
interface MessageRenderer {
  render(message: ChatMessage): ReactNode;
}

const MessageItem = ({ message, renderer }: { message: ChatMessage; renderer: MessageRenderer }) => {
  return renderer.render(message);
};
```

### 1.2 组件设计检查清单

```typescript
// 组件设计自检清单
const ComponentChecklist = {
  // 职责单一
  singleResponsibility: '组件是否只做一件事？',

  // Props接口明确
  clearProps: 'Props是否有明确的类型定义？',

  // 默认值处理
  defaultValues: '所有可选Props是否有合理的默认值？',

  // 性能优化
  performance: [
    '是否使用了React.memo优化？',
    '事件处理是否使用了useCallback？',
    '复杂计算是否使用了useMemo？'
  ],

  // 副作用清理
  cleanup: 'useEffect返回的清理函数是否正确？',

  // 错误处理
  errorHandling: '是否有适当的错误边界和错误处理？',

  // 可访问性
  accessibility: [
    '是否有语义化的HTML标签？',
    '是否有ARIA属性？',
    '键盘导航是否支持？'
  ],

  // 测试友好
  testable: '组件是否易于测试？'
};
```

---

## 2. 组件分类体系

```
components/
├── ui/                      # 基础UI组件（无业务逻辑）
│   ├── button/
│   ├── input/
│   ├── dialog/
│   ├── dropdown/
│   ├── tabs/
│   └── ...
│
├── layout/                  # 布局组件
│   ├── ChatLayout.tsx
│   ├── ResponsiveLayout.tsx
│   └── SplitPane.tsx
│
├── shared/                  # 共享组件（跨功能复用）
│   ├── Avatar/
│   ├── Icon/
│   ├── Badge/
│   └── Tooltip/
│
└── features/                # 业务组件（特定功能）
    ├── chat/
    ├── session/
    ├── theme/
    └── ...
```

---

## 3. 基础UI组件

### 3.1 Button组件

```typescript
// ============ shared/components/ui/button/Button.tsx ============

import { forwardRef, ButtonHTMLAttributes } from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/shared/utils/cn';

/**
 * Button变体定义
 */
const buttonVariants = cva(
  // 基础样式
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
        // 主要按钮
        primary: [
          'bg-primary',
          'text-primary-foreground',
          'hover:bg-primary/90'
        ],
        // 次要按钮
        secondary: [
          'bg-muted',
          'text-muted-foreground',
          'hover:bg-muted/90'
        ],
        // 幽灵按钮
        ghost: [
          'hover:bg-accent',
          'hover:text-accent-foreground'
        ],
        // 危险按钮
        destructive: [
          'bg-destructive',
          'text-destructive-foreground',
          'hover:bg-destructive/90'
        ],
        // 链接按钮
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

/**
 * Button组件属性
 */
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

/**
 * Button组件
 */
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

// LoadingSpinner子组件
const LoadingSpinner = ({ className }: { className?: string }) => (
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
```

### 3.2 Input组件

```typescript
// ============ shared/components/ui/input/Input.tsx ============

import { forwardRef, useState } from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/shared/utils/cn';

const inputVariants = cva(
  [
    'flex',
    'w-full',
    'rounded-md',
    'border',
    'bg-transparent',
    'px-3',
    'py-2',
    'text-sm',
    'transition-colors',
    'file:border-0',
    'file:bg-transparent',
    'file:text-sm',
    'file:font-medium',
    'placeholder:text-muted-foreground',
    'focus-visible:outline-none',
    'focus-visible:ring-2',
    'focus-visible:ring-offset-2',
    'disabled:cursor-not-allowed',
    'disabled:opacity-50'
  ],
  {
    variants: {
      variant: {
        default: ['border-input', 'ring-offset-background'],
        error: ['border-destructive', 'focus-visible:ring-destructive']
      },
      size: {
        sm: ['h-8', 'text-xs'],
        md: ['h-9', 'text-sm'],
        lg: ['h-10', 'text-base']
      }
    },
    defaultVariants: {
      variant: 'default',
      size: 'md'
    }
  }
);

export interface InputProps
  extends React.InputHTMLAttributes<HTMLInputElement>,
    VariantProps<typeof inputVariants> {
  /**
   * 错误状态
   */
  error?: boolean;

  /**
   * 错误消息
   */
  errorMessage?: string;

  /**
   * 左侧图标
   */
  leftIcon?: React.ReactNode;

  /**
   * 右侧图标
   */
  rightIcon?: React.ReactNode;

  /**
   * 是否显示密码切换
   */
  showPasswordToggle?: boolean;

  /**
   * 标签
   */
  label?: string;

  /**
   * 是否必填
   */
  required?: boolean;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  (
    {
      className,
      type,
      variant,
      size,
      error,
      errorMessage,
      leftIcon,
      rightIcon,
      showPasswordToggle,
      label,
      required,
      ...props
    },
    ref
  ) => {
    const [showPassword, setShowPassword] = useState(false);
    const isPassword = type === 'password';

    return (
      <div className="w-full">
        {label && (
          <label className="mb-1.5 block text-sm font-medium">
            {label}
            {required && <span className="text-destructive ml-1">*</span>}
          </label>
        )}
        <div className="relative">
          {leftIcon && (
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
              {leftIcon}
            </div>
          )}
          <input
            ref={ref}
            type={isPassword && showPassword ? 'text' : type}
            className={cn(
              inputVariants({ variant: error ? 'error' : variant, size }),
              leftIcon && 'pl-9',
              (rightIcon || showPasswordToggle) && 'pr-9',
              className
            )}
            {...props}
          />
          {(rightIcon || showPasswordToggle) && (
            <div className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground">
              {showPasswordToggle ? (
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="hover:text-foreground"
                >
                  {showPassword ? <EyeOffIcon /> : <EyeIcon />}
                </button>
              ) : (
                rightIcon
              )}
            </div>
          )}
        </div>
        {error && errorMessage && (
          <p className="mt-1 text-xs text-destructive">{errorMessage}</p>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';
```

### 3.3 Dialog组件

```typescript
// ============ shared/components/ui/dialog/Dialog.tsx ============

import { Fragment, ReactNode } from 'react';
import { Dialog as RadixDialog } from '@radix-ui/react-dialog';
import { cn } from '@/shared/utils/cn';

export interface DialogProps {
  /**
   * 是否打开
   */
  open: boolean;

  /**
   * 打开状态变化回调
   */
  onOpenChange: (open: boolean) => void;

  /**
   * 标题
   */
  title?: string;

  /**
   * 描述
   */
  description?: string;

  /**
   * 触发器
   */
  trigger?: ReactNode;

  /**
   * 底部操作区
   */
  footer?: ReactNode;

  /**
   * 大小
   */
  size?: 'sm' | 'md' | 'lg' | 'xl' | 'full';

  /**
   * 子内容
   */
  children: ReactNode;
}

const sizeClasses = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-xl',
  full: 'max-w-full mx-4'
};

export const Dialog = ({
  open,
  onOpenChange,
  title,
  description,
  trigger,
  footer,
  size = 'md',
  children
}: DialogProps) => {
  return (
    <RadixDialog open={open} onOpenChange={onOpenChange}>
      {trigger && (
        <RadixDialog.Trigger asChild>{trigger}</RadixDialog.Trigger>
      )}
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0" />
        <RadixDialog.Content
          className={cn(
            'fixed left-1/2 top-1/2 z-50 -translate-x-1/2 -translate-y-1/2',
            'bg-background',
            'rounded-lg shadow-lg',
            'border',
            'p-6',
            'data-[state=open]:animate-in data-[state=closed]:animate-out',
            'data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0',
            'data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95',
            'data-[state=closed]:slide-out-to-left-1/2 data-[state=closed]:slide-out-to-top-[48%]',
            'data-[state=open]:slide-in-from-left-1/2 data-[state=open]:slide-in-from-top-[48%]',
            'w-full',
            sizeClasses[size]
          )}
        >
          {title && (
            <RadixDialog.Title className="text-lg font-semibold">
              {title}
            </RadixDialog.Title>
          )}
          {description && (
            <RadixDialog.Description className="text-sm text-muted-foreground">
              {description}
            </RadixDialog.Description>
          )}
          <div className="mt-4">{children}</div>
          {footer && (
            <div className="mt-6 flex justify-end gap-2">{footer}</div>
          )}
          <RadixDialog.Close className="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:pointer-events-none data-[state=open]:bg-accent data-[state=open]:text-muted-foreground">
            <CrossIcon className="h-4 w-4" />
            <span className="sr-only">关闭</span>
          </RadixDialog.Close>
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog>
  );
};
```

### 3.4 Icon 系统

项目使用 `lucide-react` 作为图标源库，通过统一的 Icon 通用组件对外暴露。

```typescript
// ============ shared/components/Icon/index.tsx ============

import { icons, type LucideProps } from 'lucide-react';

/**
 * 图标名称类型（从 lucide-react 导出的所有图标名称）
 */
export type IconName = keyof typeof icons;

/**
 * Icon 通用组件 Props
 */
export interface IconProps extends LucideProps {
  /**
   * 图标名称，对应 lucide-react 中的图标
   */
  name: IconName;
}

/**
 * Icon 通用组件
 * 通过 name prop 映射到具体图标，如 <Icon name="copy" />
 */
export const Icon = ({ name, ...props }: IconProps) => {
  const LucideIcon = icons[name];
  if (!LucideIcon) {
    console.warn(`Icon "${name}" not found in lucide-react`);
    return null;
  }
  return <LucideIcon {...props} />;
};

/**
 * 同时导出具体图标组件供直接使用
 * 示例：import { CopyIcon } from '@/shared/components/Icon'
 */
export { Copy as CopyIcon } from 'lucide-react';
```

**使用方式：**

```typescript
// 方式1：通过 name prop 使用（适用于动态图标场景）
<Icon name="copy" className="h-4 w-4" />

// 方式2：直接导入具体图标组件（适用于静态引用，类型安全）
import { CopyIcon } from '@/shared/components/Icon';
<CopyIcon className="h-4 w-4" />
```

**文件位置：** `src/shared/components/Icon/index.tsx`

---

## 4. 复合组件

### 4.1 Tabs组件

```typescript
// ============ shared/components/ui/tabs/Tabs.tsx ============

import { createContext, useContext } from 'react';
import { Tabs as RadixTabs } from '@radix-ui/react-tabs';
import { cn } from '@/shared/utils/cn';

// Context
interface TabsContextValue {
  variant: 'default' | 'pills';
}

const TabsContext = createContext<TabsContextValue | undefined>(undefined);

const useTabsContext = () => {
  const context = useContext(TabsContext);
  if (!context) {
    throw new Error('Tabs components must be used within a Tabs component');
  }
  return context;
};

// Root
export interface TabsProps {
  defaultValue: string;
  value?: string;
  onValueChange?: (value: string) => void;
  variant?: 'default' | 'pills';
  children: React.ReactNode;
  className?: string;
}

export const Tabs = ({
  defaultValue,
  value,
  onValueChange,
  variant = 'default',
  children,
  className
}: TabsProps) => {
  return (
    <TabsContext.Provider value={{ variant }}>
      <RadixTabs.Root
        defaultValue={defaultValue}
        value={value}
        onValueChange={onValueChange}
        className={cn('w-full', className)}
      >
        {children}
      </RadixTabs.Root>
    </TabsContext.Provider>
  );
};

// List
export const TabsList = ({ className, ...props }: React.ComponentProps<typeof RadixTabs.List>) => {
  const { variant } = useTabsContext();

  return (
    <RadixTabs.List
      className={cn(
        'inline-flex',
        variant === 'default' && 'border-b',
        variant === 'pills' && 'bg-muted p-1 rounded-lg',
        className
      )}
      {...props}
    />
  );
};

// Trigger
export const TabsTrigger = ({ className, ...props }: React.ComponentProps<typeof RadixTabs.Trigger>) => {
  const { variant } = useTabsContext();

  return (
    <RadixTabs.Trigger
      className={cn(
        'inline-flex items-center justify-center whitespace-nowrap px-4 py-2 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50',
        variant === 'default' && [
          'border-b-2 border-transparent data-[state=active]:border-primary data-[state=active]:text-foreground',
          'hover:text-foreground'
        ],
        variant === 'pills' && [
          'rounded-md data-[state=active]:bg-background data-[state=active]:text-foreground shadow-sm',
          'hover:bg-background/50'
        ],
        className
      )}
      {...props}
    />
  );
};

// Content
export const TabsContent = ({ className, ...props }: React.ComponentProps<typeof RadixTabs.Content>) => {
  return (
    <RadixTabs.Content
      className={cn('mt-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring', className)}
      {...props}
    />
  );
};
```

### 4.2 Dropdown组件

```typescript
// ============ shared/components/ui/dropdown/Dropdown.tsx ============

import {
  DropdownMenu as RadixDropdown,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuCheckboxItem,
  DropdownMenuRadioItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuShortcut,
  DropdownMenuGroup,
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuSubContent
} from '@radix-ui/react-dropdown-menu';
import { cn } from '@/shared/utils/cn';

// Root
export const DropdownMenu = RadixDropdown;

// Trigger
export const DropdownTrigger = DropdownMenuTrigger;

// Content
export const DropdownContent = ({ className, ...props }: React.ComponentProps<typeof DropdownMenuContent>) => (
  <DropdownMenuContent
    className={cn(
      'z-50 min-w-[8rem] overflow-hidden rounded-md border bg-popover p-1 text-popover-foreground shadow-md',
      'data-[state=open]:animate-in data-[state=closed]:animate-out',
      'data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0',
      'data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95',
      'data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2',
      'data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2',
      className
    )}
    {...props}
  />
);

// Item
export const DropdownItem = ({ className, ...props }: React.ComponentProps<typeof DropdownMenuItem>) => (
  <DropdownMenuItem
    className={cn(
      'relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors',
      'focus:bg-accent focus:text-accent-foreground',
      'data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
      className
    )}
    {...props}
  />
);

// Checkbox Item
export const DropdownCheckboxItem = ({ className, ...props }: React.ComponentProps<typeof DropdownMenuCheckboxItem>) => (
  <DropdownMenuCheckboxItem
    className={cn(
      'relative flex cursor-pointer select-none items-center rounded-sm py-1.5 pl-8 pr-2 text-sm outline-none transition-colors',
      'focus:bg-accent focus:text-accent-foreground',
      'data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
      className
    )}
    {...props}
  />
);

// Radio Item
export const DropdownRadioItem = ({ className, ...props }: React.ComponentProps<typeof DropdownMenuRadioItem>) => (
  <DropdownMenuRadioItem
    className={cn(
      'relative flex cursor-pointer select-none items-center rounded-sm py-1.5 pl-8 pr-2 text-sm outline-none transition-colors',
      'focus:bg-accent focus:text-accent-foreground',
      'data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
      className
    )}
    {...props}
  />
);

// Label
export const DropdownLabel = ({ className, ...props }: React.ComponentProps<typeof DropdownMenuLabel>) => (
  <DropdownMenuLabel className={cn('px-2 py-1.5 text-sm font-semibold', className)} {...props} />
);

// Separator
export const DropdownSeparator = ({ className, ...props }: React.ComponentProps<typeof DropdownMenuSeparator>) => (
  <DropdownMenuSeparator className={cn('-mx-1 my-1 h-px bg-muted', className)} {...props} />
);

// Shortcut
export const DropdownShortcut = ({ className, ...props }: React.ComponentProps<typeof DropdownMenuShortcut>) => (
  <DropdownMenuShortcut className={cn('ml-auto text-xs tracking-widest opacity-60', className)} {...props} />
);
```

---

## 5. 业务组件

### 5.1 MessageItem组件

```typescript
// ============ features/chat/components/MessageItem.tsx ============

import { memo } from 'react';
import { MessageRole } from '@/shared/types';
import { useTheme } from '@/shared/hooks/useTheme';
import { MarkdownRenderer } from '@/shared/components/markdown/MarkdownRenderer';
import { MessageActions } from './MessageActions';
import { MessageAvatar } from './MessageAvatar';
import { MessageTimestamp } from './MessageTimestamp';
import { MessageReference } from './MessageReference';
import { cn } from '@/shared/utils/cn';

export interface MessageItemProps {
  id: string;
  role: MessageRole;
  content: string;
  timestamp: number;
  references?: Array<{
    messageId: string;
    excerpt: string;
    timestamp: number;
  }>;
  isStreaming?: boolean;
  onReply?: (messageId: string) => void;
  onCopy?: (content: string) => void;
  onDelete?: (messageId: string) => void;
  onReferenceClick?: (messageId: string) => void;
  className?: string;
}

export const MessageItem = memo(({
  id,
  role,
  content,
  timestamp,
  references,
  isStreaming = false,
  onReply,
  onCopy,
  onDelete,
  onReferenceClick,
  className
}: MessageItemProps) => {
  const { theme } = useTheme();
  const isUser = role === MessageRole.USER;

  return (
    <div
      className={cn(
        'group flex gap-3 px-4 py-3 transition-colors',
        'hover:bg-muted/50',
        isUser && 'flex-row-reverse',
        className
      )}
    >
      {/* 头像 */}
      <MessageAvatar role={role} />

      {/* 消息内容 */}
      <div className={cn('flex-1 space-y-2', isUser && 'items-end')}>
        {/* 发送者信息 */}
        <div className={cn('flex items-center gap-2', isUser && 'flex-row-reverse')}>
          <span className="text-sm font-medium">
            {isUser ? '你' : 'Claude'}
          </span>
          <MessageTimestamp timestamp={timestamp} />
        </div>

        {/* 引用内容 */}
        {references && references.length > 0 && (
          <div className="space-y-1">
            {references.map((ref) => (
              <MessageReference
                key={ref.messageId}
                excerpt={ref.excerpt}
                timestamp={ref.timestamp}
                onClick={() => onReferenceClick?.(ref.messageId)}
              />
            ))}
          </div>
        )}

        {/* 消息气泡 */}
        <div
          className={cn(
            'inline-block rounded-lg px-4 py-2 max-w-[80%]',
            isUser
              ? 'bg-primary text-primary-foreground'
              : 'bg-muted text-foreground',
            isStreaming && 'animate-pulse'
          )}
        >
          <MarkdownRenderer content={content} />
          {isStreaming && <TypingCursor />}
        </div>

        {/* 操作按钮（悬浮显示） */}
        <MessageActions
          isVisible={!isUser}
          onReply={() => onReply?.(id)}
          onCopy={() => onCopy?.(content)}
          onDelete={() => onDelete?.(id)}
        />
      </div>
    </div>
  );
});

MessageItem.displayName = 'MessageItem';

// TypingCursor子组件
const TypingCursor = () => (
  <span className="ml-1 inline-block h-4 w-0.5 animate-pulse bg-current" />
);
```

### 5.2 补充组件接口定义

以下组件在业务代码中使用，此处仅定义其 Props 接口（完整实现代码省略）。

```typescript
// === 主题编辑器辅助组件 ===

interface ColorPickerProps {
  value: string;
  onChange: (color: string) => void;
  format?: 'hex' | 'hsl' | 'rgb';
}

interface FontSelectorProps {
  value: string;
  onChange: (font: string) => void;
  monospace?: boolean;
}

interface SliderProps {
  min: number;
  max: number;
  step: number;
  value: number;
  onChange: (value: number) => void;
  label?: string;
}

// === 消息子组件 ===

interface MessageAvatarProps {
  role: MessageRole;
  size?: 'sm' | 'md';
}

interface MessageTimestampProps {
  timestamp: number;
  format?: string;
}

interface MessageReferenceProps {
  excerpt: string;
  timestamp: number;
  onClick?: () => void;
}

interface MessageActionsProps {
  isVisible: boolean;
  onReply?: () => void;
  onCopy?: () => void;
  onDelete?: () => void;
}
```

### 5.3 InteractiveQuestionPanel组件

```typescript
// ============ features/interaction/components/InteractiveQuestionPanel.tsx ============

import { useState } from 'react';
import { QuestionType } from '@/shared/types';
import { Button } from '@/shared/components/ui/button';
import { Icon } from '@/shared/components/Icon';
import { cn } from '@/shared/utils/cn';

export interface InteractiveQuestionPanelProps {
  questionId: string;
  question: string;
  questionType: QuestionType;
  options?: Array<{
    id: string;
    label: string;
    description?: string;
    icon?: string;
  }>;
  allowMultiple?: boolean;
  required?: boolean;
  placeholder?: string;
  onAnswer: (answer: any) => void;
  onSkip?: () => void;
  className?: string;
}

export const InteractiveQuestionPanel = memo(({
  questionId,
  question,
  questionType,
  options = [],
  allowMultiple = false,
  required = true,
  placeholder,
  onAnswer,
  onSkip,
  className
}: InteractiveQuestionPanelProps) => {
  const [selectedAnswer, setSelectedAnswer] = useState<any>(null);
  const [textInput, setTextInput] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async () => {
    if (required && !selectedAnswer && !textInput) return;

    setIsSubmitting(true);
    try {
      await onAnswer(selectedAnswer ?? textInput);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSkip = () => {
    onSkip?.();
  };

  return (
    <div
      className={cn(
        'my-4 rounded-lg border border-primary/20 bg-primary/5 p-4',
        className
      )}
    >
      {/* 问题头部 */}
      <div className="mb-4 flex items-start gap-2">
        <Icon name="help-circle" className="mt-0.5 h-5 w-5 text-primary" />
        <div className="flex-1">
          <p className="font-medium">{question}</p>
          {required && <p className="mt-1 text-xs text-muted-foreground">* 必需回答</p>}
        </div>
      </div>

      {/* 问题内容 */}
      <div className="mb-4">
        {questionType === QuestionType.SINGLE_CHOICE && (
          <SingleChoiceOptions
            questionId={questionId}
            options={options}
            selected={selectedAnswer}
            onChange={setSelectedAnswer}
          />
        )}

        {questionType === QuestionType.MULTIPLE_CHOICE && (
          <MultipleChoiceOptions
            options={options}
            selected={selectedAnswer ?? []}
            onChange={setSelectedAnswer}
          />
        )}

        {questionType === QuestionType.TEXT_INPUT && (
          <TextInput
            value={textInput}
            onChange={setTextInput}
            placeholder={placeholder}
          />
        )}

        {questionType === QuestionType.CONFIRMATION && (
          <ConfirmationOptions
            selected={selectedAnswer}
            onChange={setSelectedAnswer}
          />
        )}
      </div>

      {/* 操作按钮 */}
      <div className="flex justify-end gap-2">
        {onSkip && (
          <Button variant="ghost" onClick={handleSkip}>
            跳过
          </Button>
        )}
        <Button
          onClick={handleSubmit}
          disabled={isSubmitting || (required && !selectedAnswer && !textInput)}
          isLoading={isSubmitting}
        >
          确认
        </Button>
      </div>
    </div>
  );
});

// SingleChoiceOptions子组件
const SingleChoiceOptions = ({
  questionId,
  options,
  selected,
  onChange
}: {
  questionId: string;
  options: Array<{ id: string; label: string; description?: string; icon?: string }>;
  selected: string;
  onChange: (value: string) => void;
}) => (
  <div className="space-y-2">
    {options.map((option) => (
      <label
        key={option.id}
        className={cn(
          'flex cursor-pointer items-start gap-3 rounded-md border p-3 transition-colors',
          'hover:bg-accent',
          selected === option.id && 'border-primary bg-primary/5'
        )}
      >
        <input
          type="radio"
          name={questionId}
          value={option.id}
          checked={selected === option.id}
          onChange={(e) => onChange(e.target.value)}
          className="mt-1"
        />
        <div className="flex-1">
          <div className="font-medium">{option.label}</div>
          {option.description && (
            <div className="mt-1 text-sm text-muted-foreground">
              {option.description}
            </div>
          )}
        </div>
        {option.icon && <Icon name={option.icon} className="h-5 w-5 text-muted-foreground" />}
      </label>
    ))}
  </div>
);

// MultipleChoiceOptions子组件
const MultipleChoiceOptions = ({
  options,
  selected,
  onChange
}: {
  options: Array<{ id: string; label: string; description?: string }>;
  selected: string[];
  onChange: (value: string[]) => void;
}) => {
  const handleToggle = (value: string) => {
    const newSelected = selected.includes(value)
      ? selected.filter((v) => v !== value)
      : [...selected, value];
    onChange(newSelected);
  };

  return (
    <div className="space-y-2">
      {options.map((option) => (
        <label
          key={option.id}
          className={cn(
            'flex cursor-pointer items-start gap-3 rounded-md border p-3 transition-colors',
            'hover:bg-accent',
            selected.includes(option.id) && 'border-primary bg-primary/5'
          )}
        >
          <input
            type="checkbox"
            checked={selected.includes(option.id)}
            onChange={() => handleToggle(option.id)}
            className="mt-1"
          />
          <div className="flex-1">
            <div className="font-medium">{option.label}</div>
            {option.description && (
              <div className="mt-1 text-sm text-muted-foreground">
                {option.description}
              </div>
            )}
          </div>
        </label>
      ))}
    </div>
  );
};

// TextInput子组件
const TextInput = ({
  value,
  onChange,
  placeholder
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}) => (
  <textarea
    value={value}
    onChange={(e) => onChange(e.target.value)}
    placeholder={placeholder}
    rows={3}
    className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
  />
);

// ConfirmationOptions子组件
const ConfirmationOptions = ({
  selected,
  onChange
}: {
  selected: boolean | null;
  onChange: (value: boolean) => void;
}) => (
  <div className="flex gap-2">
    <Button
      variant={selected === true ? 'primary' : 'secondary'}
      onClick={() => onChange(true)}
    >
      是
    </Button>
    <Button
      variant={selected === false ? 'destructive' : 'secondary'}
      onClick={() => onChange(false)}
    >
      否
    </Button>
  </div>
);
```

---

## 6. 组件通信模式

### 6.1 Props Drilling vs Context

```typescript
// ❌ Props Drilling（不推荐）
const App = () => {
  const [theme, setTheme] = useState('dark');
  return (
    <ChatWindow theme={theme} setTheme={setTheme} />
  );
};

const ChatWindow = ({ theme, setTheme }) => (
  <MessageList theme={theme} setTheme={setTheme} />
);

const MessageList = ({ theme, setTheme }) => (
  <MessageItem theme={theme} setTheme={setTheme} />
);

// ✅ 使用Context（推荐）
const ThemeContext = createContext({});

const App = () => {
  const [theme, setTheme] = useState('dark');
  return (
    <ThemeContext.Provider value={{ theme, setTheme }}>
      <ChatWindow />
    </ThemeContext.Provider>
  );
};

const ChatWindow = () => <MessageList />;
const MessageList = () => <MessageItem />;
const MessageItem = () => {
  const { theme } = useThemeContext();
  return <div className={theme} />;
};
```

### 6.2 自定义Events通信

```typescript
// ============ shared/utils/event-bus.ts ============
// 注意：此为简化版。完整实现（含 once/clear/clearEvent/getListenerCount）见 01-phase1-foundation.md

class EventBus {
  private listeners = new Map<string, Set<Function>>();

  on(event: string, handler: Function): () => void {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event)!.add(handler);

    return () => this.off(event, handler);
  }

  off(event: string, handler: Function): void {
    this.listeners.get(event)?.delete(handler);
  }

  emit(event: string, data?: any): void {
    this.listeners.get(event)?.forEach((handler) => handler(data));
  }
}

export const eventBus = new EventBus();

// 使用示例
eventBus.on('message:received', (message) => {
  console.log('New message:', message);
});

eventBus.emit('message:received', { content: 'Hello!' });
```

---

## 7. 组件性能优化

### 7.1 React.memo使用

```typescript
// ✅ 正确使用memo
const MessageItem = memo(({ message, onClick }) => {
  return <div onClick={onClick}>{message.content}</div>;
}, (prevProps, nextProps) => {
  // 自定义比较函数
  return (
    prevProps.message.id === nextProps.message.id &&
    prevProps.message.content === nextProps.message.content
  );
});

// ❌ 不当使用memo（每次都创建新对象）
const BadComponent = memo(({ data }) => {
  return <div style={{ color: 'red' }}>{data}</div>;
}); // style对象每次都是新的
```

### 7.2 useCallback和useMemo

```typescript
const MessageItem = ({ message, onReply, onDelete }) => {
  // ✅ useCallback缓存函数
  const handleReply = useCallback(() => {
    onReply(message.id);
  }, [message.id, onReply]);

  const handleDelete = useCallback(() => {
    onDelete(message.id);
  }, [message.id, onDelete]);

  // ✅ useMemo缓存计算结果
  const formattedTime = useMemo(() => {
    return formatTimestamp(message.timestamp);
  }, [message.timestamp]);

  // ✅ useMemo缓存JSX
  const content = useMemo(() => {
    return <MarkdownRenderer content={message.content} />;
  }, [message.content]);

  return (
    <div>
      <span>{formattedTime}</span>
      {content}
      <button onClick={handleReply}>回复</button>
      <button onClick={handleDelete}>删除</button>
    </div>
  );
};
```

### 7.3 虚拟滚动

```typescript
import { useVirtualizer } from '@tanstack/react-virtual';

const MessageList = ({ messages }) => {
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 100, // 预估消息高度
    overscan: 5 // 预渲染数量
  });

  return (
    <div ref={parentRef} className="h-full overflow-auto">
      <div
        style={{
          height: `${virtualizer.getTotalSize()}px`,
          position: 'relative'
        }}
      >
        {virtualizer.getVirtualItems().map((virtualItem) => (
          <div
            key={virtualItem.key}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              transform: `translateY(${virtualItem.start}px)`
            }}
          >
            <MessageItem message={messages[virtualItem.index]} />
          </div>
        ))}
      </div>
    </div>
  );
};
```

---

**相关文档**：
- [技术架构设计](./10-architecture.md)
- [类型定义规范](./11-types.md)
- [Phase 1: 基础架构](./01-phase1-foundation.md)
