/**
 * MCP 服务器状态 Store
 *
 * 管理 Model Context Protocol 服务器的状态，包括：
 * - MCP 服务器列表
 * - 连接状态
 * - CRUD 操作
 * - 启动/停止/测试连接
 */

import { create } from 'zustand';
import type { McpServer } from '@/shared/types';
import { McpServerStatus } from '@/shared/types';

interface McpStoreState {
  /** MCP 服务器列表 */
  servers: McpServer[];

  /** 各服务器连接状态 (serverId -> status) */
  connectionStatuses: Record<string, McpServerStatus>;

  /** 加载状态 */
  isLoading: boolean;

  /** 错误信息 */
  error: string | null;

  // ========== 操作 ==========

  /** 设置服务器列表 */
  setServers: (servers: McpServer[]) => void;

  /** 保存服务器 */
  saveServer: (server: McpServer) => void;

  /** 删除服务器 */
  deleteServer: (serverId: string) => void;

  /** 启动服务器（更新状态为 connected，可选指定状态） */
  startServer: (serverId: string, status?: McpServerStatus) => void;

  /** 停止服务器（更新状态为 disconnected） */
  stopServer: (serverId: string) => void;

  /** 测试连接（更新状态） */
  testConnection: (serverId: string, success: boolean) => void;

  /** 更新单个服务器连接状态 */
  setConnectionStatus: (serverId: string, status: McpServerStatus) => void;

  /** 刷新数据（从后端加载） */
  refreshServers: () => Promise<void>;
}

export const useMcpStore = create<McpStoreState>((set) => ({
  servers: [],
  connectionStatuses: {},
  isLoading: false,
  error: null,

  setServers: (servers) => set({ servers }),

  saveServer: (server) =>
    set((state) => {
      const exists = state.servers.some((s) => s.id === server.id);
      return {
        servers: exists
          ? state.servers.map((s) => (s.id === server.id ? server : s))
          : [...state.servers, server]
      };
    }),

  deleteServer: (serverId) =>
    set((state) => ({
      servers: state.servers.filter((s) => s.id !== serverId)
    })),

  startServer: (serverId) =>
    set((state) => ({
      connectionStatuses: {
        ...state.connectionStatuses,
        [serverId]: McpServerStatus.CONNECTED
      }
    })),

  stopServer: (serverId) =>
    set((state) => ({
      connectionStatuses: {
        ...state.connectionStatuses,
        [serverId]: McpServerStatus.DISCONNECTED
      }
    })),

  testConnection: (serverId, success) =>
    set((state) => ({
      connectionStatuses: {
        ...state.connectionStatuses,
        [serverId]: success ? McpServerStatus.CONNECTED : McpServerStatus.ERROR
      }
    })),

  setConnectionStatus: (serverId, status) =>
    set((state) => ({
      connectionStatuses: {
        ...state.connectionStatuses,
        [serverId]: status
      }
    })),

  refreshServers: async () => {
    set({ isLoading: true, error: null });
    try {
      const servers = (await window.ccBackend?.getMcpServers()) ?? [];
      set({ servers, isLoading: false });
    } catch (error) {
      set({ error: String(error), isLoading: false });
    }
  }
}));