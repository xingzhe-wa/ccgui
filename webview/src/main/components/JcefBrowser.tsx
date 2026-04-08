/**
 * JcefBrowser - JCEF 浏览器环境初始化组件
 *
 * 检测 JCEF 环境是否就绪，显示加载状态或友好提示
 */

import { useEffect, useState, type ReactNode } from 'react';
import { cn } from '@/shared/utils/cn';

interface JcefBrowserProps {
  className?: string;
  onReady?: () => void;
  onError?: (error: Error) => void;
  children?: ReactNode;
}

export function JcefBrowser({
  className,
  onReady,
  onError,
  children
}: JcefBrowserProps): JSX.Element {
  const [isReady, setIsReady] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    // 检测 JCEF 环境
    let intervalId: ReturnType<typeof setInterval> | null = null;
    let attempts = 0;

    const checkJcefReady = () => {
      // 在 JCEF 环境中，window.ccBackend 会被 Java 注入
      if (window.ccBackend && window.ccEvents) {
        setIsReady(true);
        setError(null);
        onReady?.();
        return true;
      }
      return false;
    };

    // 立即检查一次
    if (!checkJcefReady()) {
      // 每隔 100ms 检查一次，最多检查 50 次（5秒）
      intervalId = setInterval(() => {
        attempts++;
        if (attempts >= 50) {
          if (intervalId) clearInterval(intervalId);
          const err = new Error('JCEF environment initialization timeout');
          setError(err);
          onError?.(err);
          return;
        }

        if (checkJcefReady() && intervalId) {
          clearInterval(intervalId);
        }
      }, 100);
    }

    return () => {
      if (intervalId) clearInterval(intervalId);
    };
  }, [onReady, onError]);

  if (error) {
    return (
      <div className={cn('flex h-full items-center justify-center', className)}>
        <div className="text-center">
          <div className="text-destructive text-lg font-semibold">
            JCEF 环境初始化失败
          </div>
          <div className="text-muted-foreground mt-2">{error.message}</div>
          <div className="text-muted-foreground mt-4 text-sm">
            请确保在 IntelliJ IDEA 插件中运行此应用
          </div>
        </div>
      </div>
    );
  }

  if (!isReady) {
    return (
      <div className={cn('flex h-full items-center justify-center', className)}>
        <div className="text-center">
          <div className="animate-pulse text-primary text-lg">
            正在初始化 JCEF 环境...
          </div>
          <div className="text-muted-foreground mt-2 text-sm">
            等待 Java 后端连接...
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={cn('h-full w-full', className)}>
      {/* JCEF 环境已就绪，渲染应用内容 */}
      {children}
    </div>
  );
}
