/**
 * AppLayout - 应用布局组件
 */

import { type ReactNode } from 'react';
import { cn } from '@/shared/utils/cn';
import { useAppStore } from '@/shared/stores';

interface AppLayoutProps {
  className?: string;
  children?: ReactNode;
}

export function AppLayout({ className, children }: AppLayoutProps): JSX.Element {
  const sidebarOpen = useAppStore((state) => state.ui.sidebarOpen);

  return (
    <div className={cn('flex h-screen w-screen overflow-hidden', className)}>
      {/* 侧边栏 */}
      <aside
        className={cn(
          'flex flex-col border-r transition-all duration-300',
          sidebarOpen ? 'w-[280px]' : 'w-0'
        )}
      >
        {/* 侧边栏内容 */}
        <div className="flex-1 overflow-auto p-4">
          <h2 className="text-lg font-semibold">ClaudeCodeJet</h2>
        </div>
      </aside>

      {/* 主内容区 */}
      <main className="flex flex-1 flex-col overflow-hidden">
        {/* 头部 */}
        <header className="flex h-12 items-center border-b px-4">
          <h1 className="text-sm font-medium">聊天</h1>
        </header>

        {/* 内容 */}
        <div className="flex-1 overflow-auto">{children}</div>
      </main>
    </div>
  );
}
