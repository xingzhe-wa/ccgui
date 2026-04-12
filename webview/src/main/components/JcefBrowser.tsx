/**
 * JcefBrowser - JCEF 浏览器环境初始化组件
 *
 * 检测 JCEF 环境是否就绪，显示加载状态或友好提示
 *
 * 重构要点：
 * 1. 使用 Promise + timeout 机制替代 setInterval polling
 * 2. onReady 回调返回 Promise，确保初始化完成后再渲染
 * 3. 增加更详细的诊断信息
 */

import { useEffect, useState, type ReactNode } from 'react';
import { cn } from '@/shared/utils/cn';

interface JcefBrowserProps {
  className?: string;
  onReady?: () => void | Promise<void>;
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
  const [initMessage, setInitMessage] = useState('正在初始化 JCEF 环境...');

  useEffect(() => {
    let cancelled = false;

    /**
     * 等待 JCEF 环境就绪
     * 使用 Promise + 超时控制
     */
    const waitForJcef = async (): Promise<void> => {
      const CHECK_INTERVAL = 100;
      const MAX_ATTEMPTS = 50;  // 5秒超时

      // 检查 bridge 是否就绪（window.ccBackend && window.ccEvents 存在）
      const isBridgeInjected = (): boolean => {
        return !!(window.ccBackend && window.ccEvents);
      };

      // 等待 window.ccBackend.send 方法可用（确保注入完整）
      const isSendMethodAvailable = (): boolean => {
        return !!(window.ccBackend && typeof window.ccBackend.send === 'function');
      };

      // 第一阶段：等待 bridge 对象注入
      setInitMessage('等待 Java Bridge 注入...');
      for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
        if (cancelled) return;
        if (isBridgeInjected()) {
          console.log('[JcefBrowser] Bridge objects detected');
          break;
        }
        await new Promise(r => setTimeout(r, CHECK_INTERVAL));
      }

      if (cancelled) return;

      // 第二阶段：验证 send 方法可用
      if (!isSendMethodAvailable()) {
        console.warn('[JcefBrowser] Bridge injected but send method not available yet');
      }

      // 第三阶段：调用 onReady 回调并等待其完成
      setInitMessage('正在初始化应用状态...');
      try {
        const result = onReady?.();
        if (result instanceof Promise) {
          await result;
        }
      } catch (err) {
        console.error('[JcefBrowser] onReady callback failed:', err);
        if (!cancelled) {
          const error = err instanceof Error ? err : new Error(String(err));
          setError(error);
          onError?.(error);
          return;
        }
      }

      if (!cancelled) {
        console.log('[JcefBrowser] Initialization complete');
        setIsReady(true);
      }
    };

    // 启动初始化流程
    waitForJcef().catch((err) => {
      if (!cancelled) {
        console.error('[JcefBrowser] Initialization error:', err);
        const error = err instanceof Error ? err : new Error(String(err));
        setError(error);
        onError?.(error);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [onReady, onError]);

  if (error) {
    return (
      <div className={cn('flex h-full items-center justify-center', className)}>
        <div className="text-center max-w-md px-4">
          <div className="text-destructive text-lg font-semibold mb-2">
            JCEF 环境初始化失败
          </div>
          <div className="text-muted-foreground text-sm mb-4">{error.message}</div>
          <div className="text-muted-foreground text-xs">
            <div className="mb-2">可能的原因：</div>
            <ul className="text-left list-disc list-inside space-y-1">
              <li>IntelliJ IDEA 插件未正确加载</li>
              <li>Java 后端 Bridge 注入失败</li>
              <li>网络或权限问题</li>
            </ul>
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
            {initMessage}
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
