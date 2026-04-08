/**
 * React 应用根组件
 */

import { Suspense } from 'react';
import { JcefBrowser } from './components/JcefBrowser';
import { AppRouter } from './router';

function App(): JSX.Element {
  return (
    <JcefBrowser>
      <Suspense fallback={<LoadingFallback />}>
        <AppRouter />
      </Suspense>
    </JcefBrowser>
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
