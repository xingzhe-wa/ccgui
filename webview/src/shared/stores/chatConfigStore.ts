/**
 * Chat 配置状态 Store
 *
 * 管理对话窗口的实时配置：模式、Agent、Streaming
 * 这些配置仅存在于内存，不持久化到文件
 */

import { create } from 'zustand';

export type ConversationMode = 'AUTO' | 'THINKING' | 'PLANNING';

export interface ChatConfig {
  conversationMode: ConversationMode;
  currentAgentId: string | null;
  streamingEnabled: boolean;
}

interface ChatConfigState extends ChatConfig {
  // Setters
  setConversationMode: (mode: ConversationMode) => void;
  setCurrentAgent: (agentId: string | null) => void;
  setStreamingEnabled: (enabled: boolean) => void;

  // 全量更新
  updateChatConfig: (config: Partial<ChatConfig>) => void;

  // 从后端加载配置
  loadChatConfig: () => Promise<void>;

  // 重置为默认值
  reset: () => void;
}

const DEFAULT_CHAT_CONFIG: ChatConfig = {
  conversationMode: 'AUTO',
  currentAgentId: null,
  streamingEnabled: true,
};

export const useChatConfigStore = create<ChatConfigState>((set) => ({
  // 初始状态
  ...DEFAULT_CHAT_CONFIG,

  setConversationMode: (mode) => {
    set({ conversationMode: mode });
    // 同步到后端
    window.ccBackend?.updateChatConfig?.({ conversationMode: mode });
  },

  setCurrentAgent: (agentId) => {
    set({ currentAgentId: agentId });
    window.ccBackend?.updateChatConfig?.({ currentAgentId: agentId });
  },

  setStreamingEnabled: (enabled) => {
    set({ streamingEnabled: enabled });
    window.ccBackend?.updateChatConfig?.({ streamingEnabled: enabled });
  },

  updateChatConfig: (config) => {
    set(config);
    window.ccBackend?.updateChatConfig?.(config);
  },

  loadChatConfig: async () => {
    try {
      const config = await window.ccBackend?.getChatConfig?.();
      if (config) {
        set({
          conversationMode: (config.conversationMode as ConversationMode) ?? 'AUTO',
          currentAgentId: config.currentAgentId ?? null,
          streamingEnabled: config.streamingEnabled ?? true,
        });
      }
    } catch (error) {
      console.error('[chatConfigStore] Failed to load chat config:', error);
    }
  },

  reset: () => {
    set({ ...DEFAULT_CHAT_CONFIG });
  },
}));
