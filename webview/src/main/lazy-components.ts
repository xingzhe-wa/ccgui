/**
 * lazy-components.ts - React.lazy 懒加载配置
 *
 * 使用 React.lazy 实现代码分割，仅在需要时加载组件。
 * 搭配 Suspense 使用，显示 loading 状态。
 *
 * @module lazy-components
 */

import { lazy } from 'react';

/**
 * 懒加载的 Skills 管理器
 */
export const LazySkillsManager = lazy(() =>
  import('@/features/skills/components/SkillsManager').then((m) => ({
    default: m.SkillsManager
  }))
);

/**
 * 懒加载的 Agents 管理器
 */
export const LazyAgentsManager = lazy(() =>
  import('@/features/agents/components/AgentsManager').then((m) => ({
    default: m.AgentsManager
  }))
);

/**
 * 懒加载的 MCP 服务器管理器
 */
export const LazyMcpServerManager = lazy(() =>
  import('@/features/mcp/components/McpServerManager').then((m) => ({
    default: m.McpServerManager
  }))
);

/**
 * 懒加载的会话搜索组件
 */
export const LazySessionSearch = lazy(() =>
  import('@/features/session/components/SessionSearch').then((m) => ({
    default: m.SessionSearch
  }))
);

/**
 * 懒加载的会话历史组件
 */
export const LazySessionHistory = lazy(() =>
  import('@/features/session/components/SessionHistory').then((m) => ({
    default: m.SessionHistory
  }))
);

/**
 * 懒加载的 Skill 编辑器
 */
export const LazySkillEditor = lazy(() =>
  import('@/features/skills/components/SkillEditor').then((m) => ({
    default: m.SkillEditor
  }))
);

/**
 * 懒加载的 Agent 编辑器
 */
export const LazyAgentEditor = lazy(() =>
  import('@/features/agents/components/AgentEditor').then((m) => ({
    default: m.AgentEditor
  }))
);

/**
 * 懒加载的 MCP 服务器配置
 */
export const LazyMcpServerConfig = lazy(() =>
  import('@/features/mcp/components/McpServerConfig').then((m) => ({
    default: m.McpServerConfig
  }))
);

/**
 * 预加载指定组件
 *
 * 在用户可能访问某组件之前提前加载，提升用户体验。
 *
 * @param componentName - 组件名称
 */
export const preloadComponent = (componentName: string): void => {
  switch (componentName) {
    case 'skills':
      import('@/features/skills/components/SkillsManager');
      break;
    case 'agents':
      import('@/features/agents/components/AgentsManager');
      break;
    case 'mcp':
      import('@/features/mcp/components/McpServerManager');
      break;
    case 'search':
      import('@/features/session/components/SessionSearch');
      break;
    default:
      break;
  }
};

/**
 * 预加载所有管理器组件
 *
 * 在空闲时间预加载，减少首次访问延迟。
 */
export const preloadAllManagers = (): void => {
  // 使用 requestIdleCallback 在浏览器空闲时预加载
  if (typeof requestIdleCallback !== 'undefined') {
    requestIdleCallback(() => {
      preloadComponent('skills');
      preloadComponent('agents');
      preloadComponent('mcp');
    });
  }
};
