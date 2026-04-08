/**
 * TabSwitchAnimation - Tab切换动画组件
 *
 * 提供平滑的Tab切换动画效果，支持：
 * - 淡入淡出过渡
 * - 滑入滑出效果
 * - 交叉淡入淡出（crossfade）
 */

import { memo, useEffect, useState, useRef, type ReactNode } from 'react';
import { cn } from '@/shared/utils/cn';

export interface TabSwitchAnimationProps {
  /** 当前活动的会话ID */
  activeId: string | null;
  /** Tab 内容 */
  children: (activeId: string | null) => ReactNode;
  /** 动画类型 */
  animationType?: 'fade' | 'slide' | 'crossfade';
  /** 动画持续时间（毫秒） */
  duration?: number;
  className?: string;
}

/**
 * Tab切换动画包装器
 *
 * 根据当前激活的Tab ID变化，触发相应的动画过渡
 */
export const TabSwitchAnimation = memo<TabSwitchAnimationProps>(function TabSwitchAnimation({
  activeId,
  children,
  animationType = 'fade',
  duration = 150,
  className
}: TabSwitchAnimationProps) {
  const [displayedId, setDisplayedId] = useState<string | null>(activeId);
  const [isAnimating, setIsAnimating] = useState(false);
  const previousIdRef = useRef<string | null>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const isMountedRef = useRef(true);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (activeId !== previousIdRef.current) {
      // 开始切换动画
      setIsAnimating(true);

      // 动画完成后切换内容
      const timer = setTimeout(() => {
        if (isMountedRef.current) {
          setDisplayedId(activeId);
          previousIdRef.current = activeId;
          setIsAnimating(false);
        }
      }, duration);

      return () => clearTimeout(timer);
    }
    return undefined;
  }, [activeId, duration]);

  // 恢复到顶部
  useEffect(() => {
    if (contentRef.current) {
      contentRef.current.scrollTop = 0;
    }
  }, [displayedId]);

  return (
    <div className={cn('relative overflow-hidden', className)}>
      {/* 内容区域 */}
      <div
        ref={contentRef}
        className={cn(
          'h-full transition-all',
          isAnimating && animationType === 'fade' && 'opacity-0',
          isAnimating && animationType === 'crossfade' && 'opacity-0',
          !isAnimating && 'opacity-100'
        )}
        style={{
          transitionDuration: `${duration}ms`
        }}
      >
        {children(displayedId)}
      </div>

      {/* 加载指示器（可选） */}
      {isAnimating && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <div className="w-5 h-5 border-2 border-primary border-t-transparent rounded-full animate-spin" />
        </div>
      )}
    </div>
  );
});

/**
 * 会话内容切换动画 Hook
 *
 * 用于在会话内容区域添加切换动画
 */
export function useTabSwitchAnimation(
  activeId: string | null,
  duration: number = 150
) {
  const [isVisible, setIsVisible] = useState(true);
  const previousIdRef = useRef<string | null>(null);

  useEffect(() => {
    if (activeId !== previousIdRef.current) {
      // 开始退出动画
      setIsVisible(false);

      const exitTimer = setTimeout(() => {
        // 开始进入动画
        setIsVisible(true);
        previousIdRef.current = activeId;
      }, duration);

      return () => clearTimeout(exitTimer);
    }
    return undefined;
  }, [activeId, duration]);

  return { isVisible };
}