/**
 * SkillsView - 技能管理页面
 */

import { lazy, Suspense } from 'react';
import { LoadingFallback } from '@/shared/components/ui/LoadingFallback';

const LazySkillsManager = lazy(() =>
  import('@/features/skills/components/SkillsManager').then((m) => ({
    default: m.SkillsManager
  }))
);

export function SkillsView(): JSX.Element {
  return (
    <div className="h-full overflow-hidden">
      <Suspense fallback={<LoadingFallback />}>
        <LazySkillsManager />
      </Suspense>
    </div>
  );
}
