/**
 * AgentsView - Agent管理页面
 */

import { lazy, Suspense } from 'react';
import { LoadingFallback } from '@/shared/components/ui/LoadingFallback';

const LazyAgentsManager = lazy(() =>
  import('@/features/agents/components/AgentsManager').then((m) => ({
    default: m.AgentsManager
  }))
);

export function AgentsView(): JSX.Element {
  return (
    <div className="h-full overflow-hidden">
      <Suspense fallback={<LoadingFallback />}>
        <LazyAgentsManager />
      </Suspense>
    </div>
  );
}
