/**
 * React Router 路由配置
 *
 * 主聊天页 + 历史页 + 工具箱统一页面（包含 Skills/Agents/MCP/供应商/主题/关于）
 */

import { lazy, Suspense } from 'react';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { ChatView } from './pages/ChatView';
import { ToolsView } from './pages/ToolsView';
import { LoadingFallback } from '@/shared/components/ui/LoadingFallback';

// 懒加载次要页面
const LazySessionHistoryView = lazy(() =>
  import('./pages/SessionHistoryView').then((m) => ({
    default: m.SessionHistoryView
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
        path: 'tools',
        element: <ToolsView />
      }
    ]
  }
]);

export function AppRouter(): JSX.Element {
  return <RouterProvider router={router} />;
}
