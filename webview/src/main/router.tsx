/**
 * React Router 路由配置
 */

import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';

const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      {
        index: true,
        element: <div className="p-4">Welcome to ClaudeCodeJet</div>
      },
      {
        path: 'settings',
        element: <div className="p-4">Settings</div>
      }
    ]
  }
]);

export function AppRouter(): JSX.Element {
  return <RouterProvider router={router} />;
}
