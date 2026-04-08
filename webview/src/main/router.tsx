/**
 * React Router 路由配置
 *
 * 所有功能页面通过路由接入，懒加载管理类页面。
 */

import { lazy, Suspense } from 'react';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { ChatView } from './pages/ChatView';
import { SettingsView } from './pages/SettingsView';
import { LoadingFallback } from '@/shared/components/ui/LoadingFallback';

// 懒加载次要页面
const LazySessionHistoryView = lazy(() =>
  import('./pages/SessionHistoryView').then((m) => ({
    default: m.SessionHistoryView
  }))
);

const LazySkillsView = lazy(() =>
  import('./pages/SkillsView').then((m) => ({
    default: m.SkillsView
  }))
);

const LazyAgentsView = lazy(() =>
  import('./pages/AgentsView').then((m) => ({
    default: m.AgentsView
  }))
);

const LazyMcpView = lazy(() =>
  import('./pages/McpView').then((m) => ({
    default: m.McpView
  }))
);

const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      {
        index: true,
        element: <ChatView />
      },
      {
        path: 'history',
        element: (
          <Suspense fallback={<LoadingFallback />}>
            <LazySessionHistoryView />
          </Suspense>
        )
      },
      {
        path: 'skills',
        element: (
          <Suspense fallback={<LoadingFallback />}>
            <LazySkillsView />
          </Suspense>
        )
      },
      {
        path: 'agents',
        element: (
          <Suspense fallback={<LoadingFallback />}>
            <LazyAgentsView />
          </Suspense>
        )
      },
      {
        path: 'mcp',
        element: (
          <Suspense fallback={<LoadingFallback />}>
            <LazyMcpView />
          </Suspense>
        )
      },
      {
        path: 'settings',
        element: <SettingsView />
      }
    ]
  }
]);

export function AppRouter(): JSX.Element {
  return <RouterProvider router={router} />;
}
