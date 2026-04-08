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
    window.ccBackend?.switchSession(sessionId);
  },

  createSession: async (name, type) => {
    try {
      const session = await window.ccBackend?.createSession(name, type);
      if (session) {
        set((state) => ({ sessions: [...state.sessions, session] }));
        get().switchSession(session.id);
      }
      return session ?? null;
    } catch (error) {
      console.error('Failed to create session:', error);
      return null;
    }
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
    try {
      // 从后端获取会话列表
      const sessions = await window.ccBackend?.searchSessions('');
      if (sessions) {
        set({ sessions });
      }
    } catch (error) {
      console.error('Failed to initialize sessions:', error);
    }
  }
}));
