/**
 * Agents 状态 Store
 *
 * 管理 AI Agent 配置的状态，包括：
 * - Agents 列表
 * - 运行中的 Agent ID 集合
 * - CRUD 操作
 * - 启动/停止 Agent
 */

import { create } from 'zustand';
import type { Agent, AgentMode } from '@/shared/types';

interface AgentsStoreState {
  /** 所有 Agents 列表 */
  agents: Agent[];

  /** 正在运行的 Agent ID 集合 */
  runningAgentIds: Set<string>;

  /** 加载状态 */
  isLoading: boolean;

  /** 错误信息 */
  error: string | null;

  // ========== 操作 ==========

  /** 设置 Agents 列表 */
  setAgents: (agents: Agent[]) => void;

  /** 创建 Agent */
  createAgent: (agent: Agent) => void;

  /** 更新 Agent */
  updateAgent: (agent: Agent) => void;

  /** 删除 Agent */
  deleteAgent: (agentId: string) => void;

  /** 标记 Agent 为运行中 */
  startAgent: (agentId: string) => void;

  /** 标记 Agent 为已停止 */
  stopAgent: (agentId: string) => void;

  /** 按模式过滤 */
  getAgentsByMode: (mode: AgentMode) => Agent[];

  /** 刷新数据（从后端加载） */
  refreshAgents: () => Promise<void>;
}

export const useAgentsStore = create<AgentsStoreState>((set, get) => ({
  agents: [],
  runningAgentIds: new Set(),
  isLoading: false,
  error: null,

  setAgents: (agents) => set({ agents }),

  createAgent: (agent) =>
    set((state) => ({
      agents: [...state.agents, agent]
    })),

  updateAgent: (agent) =>
    set((state) => ({
      agents: state.agents.map((a) => (a.id === agent.id ? agent : a))
    })),

  deleteAgent: (agentId) =>
    set((state) => ({
      agents: state.agents.filter((a) => a.id !== agentId)
    })),

  startAgent: (agentId) =>
    set((state) => {
      const newSet = new Set(state.runningAgentIds);
      newSet.add(agentId);
      return { runningAgentIds: newSet };
    }),

  stopAgent: (agentId) =>
    set((state) => {
      const newSet = new Set(state.runningAgentIds);
      newSet.delete(agentId);
      return { runningAgentIds: newSet };
    }),

  getAgentsByMode: (mode) =>
    get().agents.filter((a) => a.mode === mode),

  refreshAgents: async () => {
    set({ isLoading: true, error: null });
    try {
      const agents = (await window.ccBackend?.getAgents()) ?? [];
      set({ agents, isLoading: false });
    } catch (error) {
      set({ error: String(error), isLoading: false });
    }
  }
}));