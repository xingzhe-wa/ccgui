/**
 * SettingsView - 设置页面
 *
 * 组合 ThemeSwitcher + ThemeEditor + 偏好设置面板
 */

import { lazy, Suspense } from 'react';
import { ThemeSwitcher } from '@/features/theme/components/ThemeSwitcher';
import { LoadingFallback } from '@/shared/components/ui/LoadingFallback';

const LazyThemeEditor = lazy(() =>
  import('@/features/theme/components/ThemeEditor').then((m) => ({
    default: m.ThemeEditor
  }))
);

export function SettingsView(): JSX.Element {
  return (
    <div className="h-full overflow-y-auto p-6">
      <h1 className="mb-6 text-2xl font-bold">设置</h1>

      {/* 主题设置 */}
      <section className="mb-8">
        <h2 className="mb-4 text-lg font-semibold">主题</h2>
        <div className="rounded-lg border p-4">
          <ThemeSwitcher />
        </div>
      </section>

      {/* 主题编辑器 */}
      <section className="mb-8">
        <h2 className="mb-4 text-lg font-semibold">自定义主题</h2>
        <div className="rounded-lg border p-4">
          <Suspense fallback={<LoadingFallback />}>
            <LazyThemeEditor />
          </Suspense>
        </div>
      </section>

      {/* 关于 */}
      <section>
        <h2 className="mb-4 text-lg font-semibold">关于</h2>
        <div className="rounded-lg border p-4 text-sm text-muted-foreground">
          <p>ClaudeCodeJet v0.0.1</p>
          <p className="mt-1">AI-Powered Coding Assistant for IntelliJ IDEA</p>
        </div>
      </section>
    </div>
  );
}
