/**
 * React Router 路由配置
 *
 * 使用 HashRouter 适配 JCEF 环境：
 * - JCEF 环境下 BrowserRouter 刷新后路由丢失
 * - createHashRouter 将路由信息存储在 URL hash 中
 * - hash 变化不会触发服务器请求，适合本地/插件环境
 *
 * 主聊天页 + 历史页 + 工具箱统一页面（包含 Skills/Agents/MCP/供应商/主题/关于）
 */

import { lazy, Suspense } from 'react';
import { createHashRouter, RouterProvider } from 'react-router-dom';
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

const router = createHashRouter([
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
