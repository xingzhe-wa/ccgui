/**
 * AppLayout - 应用主布局
 *
 * 简化版：无左侧边栏
 * 顶部栏（Logo + 会话标签 + 右上角工具）+ 主内容区
 */

import { memo } from 'react';
import { Outlet, useLocation, NavLink } from 'react-router-dom';
import { SessionTabs } from '@/features/session/components/SessionTabs';
import { cn } from '@/shared/utils/cn';

/** 需要显示 SessionTabs 的路由路径 */
const TAB_ROUTES = ['/', '/history', '/tools'];

/** 右上角图标按钮统一样式 */
const iconBtnClass = cn(
  'flex items-center justify-center w-8 h-8 rounded-md text-xs transition-colors',
  'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
  'active:scale-95'
);

export const AppLayout = memo(function AppLayout(): JSX.Element {
  const location = useLocation();
  const showTabs = TAB_ROUTES.includes(location.pathname);

  return (
    <div className="flex flex-col h-screen w-screen overflow-hidden bg-background text-foreground">
      {/* 顶部栏：Logo + 会话标签 + 右上角工具 */}
      <header className="flex h-12 w-full items-center border-b bg-background-secondary px-3">
        {/* Logo区域 */}
        <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary mr-3 shadow-sm">
          <span className="text-[10px] font-bold text-primary-foreground">CC</span>
        </div>

        {/* 会话标签区域 */}
        <div className="flex-1 min-w-0">
          {showTabs && <SessionTabs className="h-full border-b-0" />}
        </div>

        {/* 右上角工具：历史 + 工具箱 */}
        <div className="flex items-center gap-1 ml-2 shrink-0">
          {/* 历史快捷按钮 */}
          <NavLink
            to="/history"
            className={({ isActive }) =>
              cn(iconBtnClass, isActive && 'bg-accent text-accent-foreground')
            }
            title="会话历史"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-4 w-4">
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </NavLink>

          {/* 工具箱按钮 */}
          <NavLink
            to="/tools"
            className={({ isActive }) =>
              cn(iconBtnClass, isActive && 'bg-accent text-accent-foreground')
            }
            title="工具箱"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-4 w-4">
              <path strokeLinecap="round" strokeLinejoin="round" d="M11.42 15.17l-5.1-5.1a1 1 0 010-1.42l5.1-5.1a1 1 0 011.42 0l5.1 5.1a1 1 0 010 1.42l-5.1 5.1a1 1 0 01-1.42 0zM12 20h.01" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </NavLink>
        </div>
      </header>

      {/* 路由内容 */}
      <main className="flex-1 overflow-hidden">
        <Outlet />
      </main>
    </div>
  );
});
