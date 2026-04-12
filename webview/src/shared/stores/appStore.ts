/**
 * 应用全局状态 Store
 */

import { create } from 'zustand';
import type {
  ChatSession,
  SessionType,
  TaskProgress,
  UIState
} from '@/shared/types';
import { useSessionStore } from './sessionStore';

interface AppState {
  // ========== 会话相关 ==========
  sessions: ChatSession[];
  currentSessionId: string;

  // ========== UI状态 ==========
  ui: UIState;

  // ========== 任务进度 ==========
  activeTaskProgress: TaskProgress | null;

  // ========== 错误状态 ==========
  error: Error | null;

  // ========== 操作 ==========
  // 会话操作
  switchSession: (sessionId: string) => void;
  createSession: (name: string, type: SessionType) => Promise<ChatSession | null>;
  deleteSession: (sessionId: string) => void;
  updateSession: (sessionId: string, updates: Partial<ChatSession>) => void;
  reorderSessions: (sessions: ChatSession[]) => void;
  markSessionInitialized: (sessionId: string) => void;

  // UI操作
  toggleSidebar: () => void;
  togglePreviewPanel: () => void;
  setSidebarOpen: (open: boolean) => void;
  setPreviewPanelOpen: (open: boolean) => void;
  openModal: (modalId: string, data?: any) => void;
  closeModal: () => void;

  // 任务进度操作
  setTaskProgress: (progress: TaskProgress | null) => void;

  // 错误操作
  setError: (error: Error | null) => void;

  // 初始化
  initializeSessions: () => Promise<void>;
}

const initialUIState: UIState = {
  sidebarOpen: true,
  sidebarWidth: 280,
  previewPanelOpen: false,
  previewPanelWidth: 400,
  activeModal: null,
  modalData: null,
  notifications: [],
  isLoading: false,
  loadingMessage: ''
};

export const useAppStore = create<AppState>((set, get) => ({
  // ========== 初始状态 ==========
  sessions: [],
  currentSessionId: '',
  ui: initialUIState,
  activeTaskProgress: null,
  error: null,

  // ========== 会话操作 ==========
  switchSession: (sessionId) => {
    set({ currentSessionId: sessionId });
    // 同步到 sessionStore，确保 sessionStates 访问正确的会话
    useSessionStore.getState().setCurrentSession(sessionId);
    // 如果 sessionStates 中还没有该会话的记录，初始化它
    useSessionStore.getState().initSessionState(sessionId);
    window.ccBackend?.switchSession(sessionId);
  },

  createSession: async (name, type) => {
    try {
      const session = await window.ccBackend?.createSession(name, type);
      if (session) {
        // 新会话不立即加入历史列表，等首次发送消息后才加入
        const uninitSession = { ...session, isInitialized: false };
        set((state) => ({ sessions: [...state.sessions, uninitSession] }));
        get().switchSession(session.id);
      }
      return session ?? null;
    } catch (error) {
      console.error('Failed to create session:', error);
      return null;
    }
  },

  markSessionInitialized: (sessionId) => {
    set((state) => ({
      sessions: state.sessions.map((s) =>
        s.id === sessionId ? { ...s, isInitialized: true } : s
      )
    }));
  },

  deleteSession: (sessionId) => {
    set((state) => ({
      sessions: state.sessions.filter((s) => s.id !== sessionId),
      currentSessionId: state.currentSessionId === sessionId ? '' : state.currentSessionId
    }));
    window.ccBackend?.deleteSession(sessionId);
  },

  updateSession: (sessionId, updates) => {
    set((state) => ({
      sessions: state.sessions.map((s) =>
        s.id === sessionId ? { ...s, ...updates } : s
      )
    }));
  },

  reorderSessions: (sessions) => {
    set({ sessions });
  },

  // ========== UI操作 ==========
  toggleSidebar: () => {
    set((state) => ({
      ui: { ...state.ui, sidebarOpen: !state.ui.sidebarOpen }
    }));
  },

  togglePreviewPanel: () => {
    set((state) => ({
      ui: { ...state.ui, previewPanelOpen: !state.ui.previewPanelOpen }
    }));
  },

  setSidebarOpen: (open) => {
    set((state) => ({
      ui: { ...state.ui, sidebarOpen: open }
    }));
  },

  setPreviewPanelOpen: (open) => {
    set((state) => ({
      ui: { ...state.ui, previewPanelOpen: open }
    }));
  },

  openModal: (modalId, data) => {
    set((state) => ({
      ui: { ...state.ui, activeModal: modalId, modalData: data }
    }));
  },

  closeModal: () => {
    set((state) => ({
      ui: { ...state.ui, activeModal: null, modalData: null }
    }));
  },

  // ========== 任务进度操作 ==========
  setTaskProgress: (progress) => {
    set({ activeTaskProgress: progress });
  },

  // ========== 错误操作 ==========
  setError: (error) => {
    set({ error });
  },

  // ========== 初始化 ==========
  initializeSessions: async () => {
    // 检查 bridge 是否就绪
    if (!window.ccBackend) {
      console.warn('[appStore] Bridge not ready, skipping session initialization');
      // 创建一个默认会话，确保 UI 可以正常显示
      const defaultSession: ChatSession = {
        id: 'default-' + Date.now(),
        name: '新会话',
        type: 'PROJECT' as SessionType,
        messages: [],
        context: {
          modelConfig: { provider: 'anthropic', model: 'claude-sonnet-4-6', maxTokens: 4096, temperature: 0.7, topP: 1.0 },
          enabledSkills: [],
          enabledMcpServers: [],
          metadata: {}
        },
        createdAt: Date.now(),
        updatedAt: Date.now(),
        isActive: true,
        status: 'IDLE' as any,
        isInitialized: false
      };
      set({ sessions: [defaultSession], currentSessionId: defaultSession.id });
      useSessionStore.getState().initSessionState(defaultSession.id);
      return;
    }

    try {
      // 从后端获取会话列表
      const sessions = await window.ccBackend.searchSessions('');
      if (sessions && sessions.length > 0) {
        set({ sessions });
        // 为每个会话初始化 sessionState，确保切换时 getSessionState 返回有效状态
        sessions.forEach((session: ChatSession) => {
          useSessionStore.getState().initSessionState(session.id);
        });
        console.log('[appStore] Loaded', sessions.length, 'sessions from backend');
      } else {
        // 没有会话，创建一个默认会话
        console.log('[appStore] No sessions found, creating default session');
        const defaultSession: ChatSession = {
          id: 'default-' + Date.now(),
          name: '新会话',
          type: 'PROJECT' as SessionType,
          messages: [],
          context: {
            modelConfig: { provider: 'anthropic', model: 'claude-sonnet-4-6', maxTokens: 4096, temperature: 0.7, topP: 1.0 },
            enabledSkills: [],
            enabledMcpServers: [],
            metadata: {}
          },
          createdAt: Date.now(),
          updatedAt: Date.now(),
          isActive: true,
          status: 'IDLE' as any,
          isInitialized: false
        };
        set({ sessions: [defaultSession], currentSessionId: defaultSession.id });
        useSessionStore.getState().initSessionState(defaultSession.id);
      }
    } catch (error) {
      console.error('[appStore] Failed to initialize sessions:', error);
      // 即使失败，也创建一个默认会话，确保 UI 可用
      const defaultSession: ChatSession = {
        id: 'default-' + Date.now(),
        name: '新会话',
        type: 'PROJECT' as SessionType,
        messages: [],
        context: {
          modelConfig: { provider: 'anthropic', model: 'claude-sonnet-4-6', maxTokens: 4096, temperature: 0.7, topP: 1.0 },
          enabledSkills: [],
          enabledMcpServers: [],
          metadata: {}
        },
        createdAt: Date.now(),
        updatedAt: Date.now(),
        isActive: true,
        status: 'IDLE' as any,
        isInitialized: false
      };
      set({ sessions: [defaultSession], currentSessionId: defaultSession.id });
      useSessionStore.getState().initSessionState(defaultSession.id);
    }
  }
}));
