/**
 * McpView - MCP服务器管理页面
 */

import { lazy, Suspense } from 'react';
import { LoadingFallback } from '@/shared/components/ui/LoadingFallback';

const LazyMcpServerManager = lazy(() =>
  import('@/features/mcp/components/McpServerManager').then((m) => ({
    default: m.McpServerManager
  }))
);

export function McpView(): JSX.Element {
  return (
    <div className="h-full overflow-hidden">
      <Suspense fallback={<LoadingFallback />}>
        <LazyMcpServerManager />
      </Suspense>
    </div>
  );
}
