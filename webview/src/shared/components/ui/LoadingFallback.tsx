/**
 * LoadingFallback - 懒加载组件的 Loading 占位符
 *
 * 用于 React.lazy + Suspense 的加载状态显示。
 *
 * @module LoadingFallback
 */

import { memo } from 'react';
import { Loader2 } from 'lucide-react';
import { cn } from '@/shared/utils/cn';

export interface LoadingFallbackProps {
  /** 加载文本 */
  text?: string;
  /** 额外的类名 */
  className?: string;
  /** 尺寸 */
  size?: 'sm' | 'md' | 'lg';
  /** 是否全屏居中 */
  centered?: boolean;
}

/**
 * 加载中占位组件
 */
export const LoadingFallback = memo<LoadingFallbackProps>(function LoadingFallback({
  text = '加载中...',
  className,
  size = 'md',
  centered = false
}: LoadingFallbackProps) {
  const sizeClasses = {
    sm: 'h-4 w-4',
    md: 'h-6 w-6',
    lg: 'h-8 w-8'
  };

  const content = (
    <div className={cn('flex flex-col items-center gap-3', className)}>
      <Loader2 className={cn(sizeClasses[size], 'animate-spin text-primary')} />
      <p className="text-sm text-muted-foreground">{text}</p>
    </div>
  );

  if (centered) {
    return (
      <div className="flex items-center justify-center min-h-[200px]">
        {content}
      </div>
    );
  }

  return content;
});

/**
 * 骨架屏加载组件
 *
 * 用于卡片列表等场景的加载占位。
 */
export const SkeletonCard = memo<{ count?: number }>(function SkeletonCard({ count = 3 }) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          className="rounded-lg border bg-muted/20 p-4 space-y-3 animate-pulse"
        >
          <div className="flex items-center gap-2">
            <div className="h-8 w-8 rounded-full bg-muted" />
            <div className="space-y-1 flex-1">
              <div className="h-4 w-3/4 bg-muted rounded" />
              <div className="h-3 w-1/2 bg-muted rounded" />
            </div>
          </div>
          <div className="space-y-2">
            <div className="h-3 bg-muted rounded w-full" />
            <div className="h-3 bg-muted rounded w-5/6" />
          </div>
        </div>
      ))}
    </div>
  );
});

SkeletonCard.displayName = 'SkeletonCard';

/**
 * 列表骨架屏组件
 *
 * 用于列表的加载占位。
 */
export const SkeletonList = memo<{ count?: number; itemHeight?: string }>(function SkeletonList({
  count = 5,
  itemHeight = '60px'
}) {
  return (
    <div className="space-y-2">
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          className="animate-pulse bg-muted/20 rounded"
          style={{ height: itemHeight }}
        />
      ))}
    </div>
  );
});

SkeletonList.displayName = 'SkeletonList';
