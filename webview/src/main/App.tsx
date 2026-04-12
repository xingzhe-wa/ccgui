/**
 * React 应用根组件
 */

import { Suspense, Component, type ReactNode, useEffect, useCallback } from 'react';
import { JcefBrowser } from './components/JcefBrowser';
import { AppRouter } from './router';
import { useAppStore } from '@/shared/stores';
import { useQuestionStore } from '@/shared/stores/questionStore';
import { useChatConfigStore } from '@/shared/stores/chatConfigStore';
import { t } from '@/shared/i18n';
import type { InteractiveQuestion } from '@/shared/types/interaction';

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

/**
 * React 错误边界组件
 * 防止子组件渲染错误导致整页空白
 */
class ErrorBoundary extends Component<{ children: ReactNode }, ErrorBoundaryState> {
  constructor(props: { children: ReactNode }) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo): void {
    console.error('[ErrorBoundary] Caught error:', error, errorInfo);
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <div className="flex h-screen w-screen items-center justify-center">
          <div className="text-center">
            <div className="text-destructive text-lg font-semibold">{t('app.renderError')}</div>
            <div className="text-muted-foreground mt-2 text-sm">
              {this.state.error?.message ?? t('app.unknownError')}
            </div>
            <button
              className="mt-4 rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90"
              onClick={() => this.setState({ hasError: false, error: null })}
            >
              {t('common.retry')}
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

function App(): JSX.Element {
  // 订阅来自 Java 后端的交互式问题推送
  const handleStreamingQuestion = useCallback((data: any) => {
    const question: InteractiveQuestion = {
      questionId: data.questionId,
      question: data.message,
      questionType: data.questionType,
      options: data.options?.map((opt: any) => ({
        id: opt.id,
        label: opt.label,
        description: opt.description ?? undefined,
        icon: opt.icon ?? undefined
      })),
      allowMultiple: false,
      required: true,
      context: {},
      createdAt: Date.now()
    };
    useQuestionStore.getState().setQuestion(question);
  }, []);

  useEffect(() => {
    const handler = (data: any) => handleStreamingQuestion(data);
    window.ccEvents?.on('streaming:question', handler);
    return () => {
      window.ccEvents?.off('streaming:question', handler);
    };
  }, [handleStreamingQuestion]);

  // 订阅 task:progress 事件
  useEffect(() => {
    const handler = (data: any) => {
      console.debug('[App] task:progress event:', data);
      // Task status will be handled by TaskStatusBar in Phase 4
    };
    window.ccEvents?.on('task:progress', handler);
    return () => {
      window.ccEvents?.off('task:progress', handler);
    };
  }, []);

  // 订阅 MCP server status 事件
  useEffect(() => {
    const handleMcpServerStatus = (data: any) => {
      console.debug('[App] mcp:serverStatus event:', data);
    };
    window.ccEvents?.on('mcp:serverStatus', handleMcpServerStatus);
    return () => {
      window.ccEvents?.off('mcp:serverStatus', handleMcpServerStatus);
    };
  }, []);

  useEffect(() => {
    const handleMcpTestResult = (data: any) => {
      console.debug('[App] mcp:testResult event:', data);
    };
    window.ccEvents?.on('mcp:testResult', handleMcpTestResult);
    return () => {
      window.ccEvents?.off('mcp:testResult', handleMcpTestResult);
    };
  }, []);

  /**
   * 初始化回调 - JcefBrowser 会等待这个 Promise 完成
   */
  const handleReady = useCallback(async (): Promise<void> => {
    console.log('[App] Starting initialization...');

    try {
      // 初始化 sessions（如果后端就绪）
      await useAppStore.getState().initializeSessions();
      console.log('[App] Sessions initialized');
    } catch (error) {
      console.error('[App] Failed to initialize sessions:', error);
      // 不阻塞初始化，即使 session 初始化失败也继续
    }

    try {
      // 加载 chat 配置
      await useChatConfigStore.getState().loadChatConfig();
      console.log('[App] Chat config loaded');
    } catch (error) {
      console.error('[App] Failed to load chat config:', error);
    }

    console.log('[App] Initialization complete');
  }, []);

  return (
    <ErrorBoundary>
      <JcefBrowser onReady={handleReady}>
        <Suspense fallback={<LoadingFallback />}>
          <AppRouter />
        </Suspense>
      </JcefBrowser>
    </ErrorBoundary>
  );
}

/**
 * 加载中占位组件
 */
function LoadingFallback(): JSX.Element {
  return (
    <div className="flex h-screen w-screen items-center justify-center">
      <div className="animate-pulse text-lg">{t('common.loading')}</div>
    </div>
  );
}

export default App;
