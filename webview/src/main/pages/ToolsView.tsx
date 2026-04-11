/**
 * ToolsView - 统一工具/设置页面
 *
 * 左右布局：左侧导航列表（6项），右侧详情面板
 * 合并了原 Skills/Agents/MCP 管理页面 + 模型配置/主题/关于
 */

import { lazy, Suspense, useState, useEffect, memo } from 'react';
import { ModelConfigPanel } from '@/features/model/components';
import { ThemeSwitcher } from '@/features/theme/components/ThemeSwitcher';
import { LoadingFallback } from '@/shared/components/ui/LoadingFallback';
import { cn } from '@/shared/utils/cn';

// 懒加载管理组件
const LazySkillsManager = lazy(() =>
  import('@/features/skills/components/SkillsManager').then((m) => ({
    default: m.SkillsManager
  }))
);
const LazyAgentsManager = lazy(() =>
  import('@/features/agents/components/AgentsManager').then((m) => ({
    default: m.AgentsManager
  }))
);
const LazyMcpServerManager = lazy(() =>
  import('@/features/mcp/components/McpServerManager').then((m) => ({
    default: m.McpServerManager
  }))
);
const LazyThemeEditor = lazy(() =>
  import('@/features/theme/components/ThemeEditor').then((m) => ({
    default: m.ThemeEditor
  }))
);

/** 左侧导航项 */
interface NavSection {
  id: string;
  label: string;
  icon: JSX.Element;
}

const SECTIONS: NavSection[] = [
  {
    id: 'skills',
    label: '技能管理',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-4 w-4">
        <path strokeLinecap="round" strokeLinejoin="round" d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
      </svg>
    )
  },
  {
    id: 'agents',
    label: 'Agent 管理',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-4 w-4">
        <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
      </svg>
    )
  },
  {
    id: 'mcp',
    label: 'MCP Server',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-4 w-4">
        <path strokeLinecap="round" strokeLinejoin="round" d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01" />
      </svg>
    )
  },
  {
    id: 'provider',
    label: '供应商配置',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-4 w-4">
        <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 18.75a1.5 1.5 0 01-1.5-1.5v-6a1.5 1.5 0 011.5-1.5h9a1.5 1.5 0 011.5 1.5v6a1.5 1.5 0 01-1.5 1.5H10.5z" />
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 15l-3 3m0 0l3 3m-3-3l3-3m-3 3h6" />
      </svg>
    )
  },
  {
    id: 'theme',
    label: '主题设置',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-4 w-4">
        <path strokeLinecap="round" strokeLinejoin="round" d="M9.53 16.122a3 3 0 00-5.78 1.128 2.25 2.25 0 01-2.4 2.245 4.5 4.5 0 008.4-2.245c0-.399-.078-.78-.22-1.128zm0 0a15.998 15.998 0 003.388-1.62m-5.043-.025a15.994 15.994 0 011.622-3.395m3.42 3.42a15.995 15.995 0 004.764-4.648l3.876-5.814a1.151 1.151 0 00-1.597-1.597L14.146 6.32a15.996 15.996 0 00-4.649 4.763m3.42 3.42a6.776 6.776 0 00-3.42-3.42" />
      </svg>
    )
  },
  {
    id: 'about',
    label: '关于',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-4 w-4">
        <path strokeLinecap="round" strokeLinejoin="round" d="M11.25 11.25l.041-.02a.75.75 0 011.063.852l-.708 2.836a.75.75 0 001.063.853l.041-.021M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9-3.75h.008v.008H12V8.25z" />
      </svg>
    )
  }
];

/** 左侧导航项样式 */
const navItemClass = cn(
  'flex items-center gap-2.5 w-full px-3 py-2 rounded-md text-sm transition-colors text-left',
  'hover:bg-accent/50'
);

export const ToolsView = memo(function ToolsView(): JSX.Element {
  const [activeSection, setActiveSection] = useState('skills');

  return (
    <div className="flex h-full">
      {/* 左侧导航 */}
      <aside className="w-48 shrink-0 border-r bg-background-secondary/50 p-3 flex flex-col gap-1">
        <h2 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider px-3 mb-2">
          工具箱
        </h2>
        {SECTIONS.map((section) => (
          <button
            key={section.id}
            onClick={() => setActiveSection(section.id)}
            className={cn(
              navItemClass,
              activeSection === section.id
                ? 'bg-accent text-accent-foreground font-medium'
                : 'text-muted-foreground'
            )}
          >
            <span className="shrink-0">{section.icon}</span>
            {section.label}
          </button>
        ))}
      </aside>

      {/* 右侧详情面板 */}
      <main className="flex-1 overflow-y-auto p-6">
        {activeSection === 'skills' && (
          <Suspense fallback={<LoadingFallback />}>
            <LazySkillsManager />
          </Suspense>
        )}
        {activeSection === 'agents' && (
          <Suspense fallback={<LoadingFallback />}>
            <LazyAgentsManager />
          </Suspense>
        )}
        {activeSection === 'mcp' && (
          <Suspense fallback={<LoadingFallback />}>
            <LazyMcpServerManager />
          </Suspense>
        )}
        {activeSection === 'provider' && (
          <div className="rounded-lg border">
            <ModelConfigPanel />
          </div>
        )}
        {activeSection === 'theme' && (
          <ThemeSection />
        )}
        {activeSection === 'about' && (
          <AboutSection />
        )}
      </main>
    </div>
  );
});

/** 主题设置 */
function ThemeSection(): JSX.Element {
  const [brightness, setBrightness] = useState(100);
  const [saturation, setSaturation] = useState(100);

  // 将亮度/饱和度应用到 DOM
  useEffect(() => {
    const b = brightness / 100;
    const s = saturation / 100;
    document.documentElement.style.filter = `brightness(${b}) saturate(${s})`;
    return () => {
      document.documentElement.style.filter = '';
    };
  }, [brightness, saturation]);

  const handleReset = () => {
    setBrightness(100);
    setSaturation(100);
  };

  return (
    <div className="space-y-6">
      <div className="rounded-lg border p-4">
        <h3 className="text-sm font-medium mb-4">预设主题</h3>
        <ThemeSwitcher />
      </div>

      <div className="rounded-lg border p-4">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-medium">亮度与饱和度</h3>
          <button
            onClick={handleReset}
            className="text-xs text-muted-foreground hover:text-foreground transition-colors"
          >
            重置
          </button>
        </div>
        <div className="space-y-4">
          <div>
            <div className="flex items-center justify-between mb-1">
              <span className="text-sm text-foreground">亮度</span>
              <span className="text-sm text-muted-foreground">{brightness}%</span>
            </div>
            <input
              type="range"
              min="0"
              max="100"
              value={brightness}
              onChange={(e) => setBrightness(Number(e.target.value))}
              className="w-full accent-primary cursor-pointer"
            />
          </div>
          <div>
            <div className="flex items-center justify-between mb-1">
              <span className="text-sm text-foreground">饱和度</span>
              <span className="text-sm text-muted-foreground">{saturation}%</span>
            </div>
            <input
              type="range"
              min="0"
              max="100"
              value={saturation}
              onChange={(e) => setSaturation(Number(e.target.value))}
              className="w-full accent-primary cursor-pointer"
            />
          </div>
        </div>
      </div>

      <div className="rounded-lg border p-4">
        <h3 className="text-sm font-medium mb-4">自定义主题</h3>
        <Suspense fallback={<LoadingFallback />}>
          <LazyThemeEditor />
        </Suspense>
      </div>
    </div>
  );
}

/** 关于 */
function AboutSection(): JSX.Element {
  return (
    <div className="rounded-lg border p-6">
      <div className="flex items-center gap-4 mb-4">
        <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-primary">
          <span className="text-lg font-bold text-primary-foreground">CC</span>
        </div>
        <div>
          <h2 className="text-lg font-semibold">CC Assistant</h2>
          <p className="text-sm text-muted-foreground">AI-Powered Coding Assistant for IntelliJ IDEA</p>
        </div>
      </div>
      <div className="text-sm text-muted-foreground space-y-1">
        <p>版本: v0.0.1</p>
        <p>基于 Claude Agent SDK 构建</p>
      </div>
    </div>
  );
}
