/**
 * React 应用根组件
 */

import { Suspense, Component, type ReactNode } from 'react';
import { JcefBrowser } from './components/JcefBrowser';
import { AppRouter } from './router';
import { useAppStore } from '@/shared/stores';

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
            <div className="text-destructive text-lg font-semibold">应用渲染出错</div>
            <div className="text-muted-foreground mt-2 text-sm">
              {this.state.error?.message ?? '未知错误'}
            </div>
            <button
              className="mt-4 rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90"
              onClick={() => this.setState({ hasError: false, error: null })}
            >
              重试
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

function App(): JSX.Element {
  return (
    <ErrorBoundary>
      <JcefBrowser onReady={() => useAppStore.getState().initializeSessions()}>
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
      <div className="animate-pulse text-lg">加载中...</div>
    </div>
  );
}

export default App;
