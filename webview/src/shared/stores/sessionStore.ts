/**
 * 会话状态 Store
 *
 * 注意：流式输出状态由独立的 streamingStore 管理（不在此Store中）
 *
 * Phase 4 增强：支持多会话状态隔离
 * 每个会话独立维护自己的消息、输入文本、滚动位置等状态
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { ChatMessage, InteractiveQuestion } from '@/shared/types';

/**
 * 单个会话的状态
 */
interface PerSessionState {
  /** 会话消息 */
  messages: ChatMessage[];
  /** 输入框文本 */
  inputText: string;
  /** 滚动位置 */
  scrollPosition: number;
  /** 待发送的附件 */
  attachments: File[];
}

/**
 * SessionStore 完整状态
 */
interface SessionState {
  /** 当前会话ID */
  currentSessionId: string | null;

  /** 所有会话的状态映射 */
  sessionStates: Record<string, PerSessionState>;

  /** 当前会话的交互式请求 */
  pendingQuestions: InteractiveQuestion[];

  // ========== 操作 ==========

  // 会话上下文操作
  setCurrentSession: (sessionId: string) => void;
  getSessionState: (sessionId: string) => PerSessionState;
  initSessionState: (sessionId: string) => void;
  clearSessionState: (sessionId: string) => void;

  // 消息操作
  addMessage: (message: ChatMessage) => void;
  updateMessage: (messageId: string, updates: Partial<ChatMessage>) => void;
  removeMessage: (messageId: string) => void;
  clearMessages: () => void;

  // 输入文本操作
  setInputText: (text: string) => void;
  getInputText: () => string;

  // 附件操作
  setAttachments: (files: File[]) => void;
  getAttachments: () => File[];
  addAttachment: (file: File) => void;
  removeAttachment: (index: number) => void;

  // 滚动位置操作
  setScrollPosition: (position: number) => void;
  getScrollPosition: () => number;

  // 交互式请求操作
  addQuestion: (question: InteractiveQuestion) => void;
  removeQuestion: (questionId: string) => void;
  submitAnswer: (questionId: string, answer: any) => void;
}

/**
 * 获取空会话状态
 */
const getEmptySessionState = (): PerSessionState => ({
  messages: [],
  inputText: '',
  scrollPosition: 0,
  attachments: []
});

export const useSessionStore = create<SessionState>()(
  persist(
    (set, get) => ({
      // ========== 初始状态 ==========
      currentSessionId: null,
      sessionStates: {},
      pendingQuestions: [],

      // ========== 会话上下文操作 ==========
      setCurrentSession: (sessionId) => {
        set({ currentSessionId: sessionId });
      },

      getSessionState: (sessionId) => {
        const { sessionStates } = get();
        return sessionStates[sessionId] ?? getEmptySessionState();
      },

      initSessionState: (sessionId) => {
        const { sessionStates } = get();
        if (!sessionStates[sessionId]) {
          set({
            sessionStates: {
              ...sessionStates,
              [sessionId]: getEmptySessionState()
            }
          });
        }
      },

      clearSessionState: (sessionId) => {
        const { sessionStates, currentSessionId } = get();
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const { [sessionId]: _removed, ...rest } = sessionStates;
        set({
          sessionStates: rest,
          currentSessionId: currentSessionId === sessionId ? null : currentSessionId
        });
      },

      // ========== 消息操作 ==========
      addMessage: (message) => {
        const { currentSessionId } = get();
        if (!currentSessionId) return;

        set((state) => {
          const sessionState = state.sessionStates[currentSessionId] ?? getEmptySessionState();
          return {
            sessionStates: {
              ...state.sessionStates,
              [currentSessionId]: {
                ...sessionState,
                messages: [...sessionState.messages, message]
              }
            }
          };
        });
      },

      updateMessage: (messageId, updates) => {
        const { currentSessionId } = get();
        if (!currentSessionId) return;

        set((state) => {
          const sessionState = state.sessionStates[currentSessionId] ?? getEmptySessionState();
          return {
            sessionStates: {
              ...state.sessionStates,
              [currentSessionId]: {
                ...sessionState,
                messages: sessionState.messages.map((msg) =>
                  msg.id === messageId ? { ...msg, ...updates } : msg
                )
              }
            }
          };
        });
      },

      removeMessage: (messageId) => {
        const { currentSessionId } = get();
        if (!currentSessionId) return;

        set((state) => {
          const sessionState = state.sessionStates[currentSessionId] ?? getEmptySessionState();
          return {
            sessionStates: {
              ...state.sessionStates,
              [currentSessionId]: {
                ...sessionState,
                messages: sessionState.messages.filter((msg) => msg.id !== messageId)
              }
            }
          };
        });
      },

      clearMessages: () => {
        const { currentSessionId } = get();
        if (!currentSessionId) return;

        set((state) => {
          const sessionState = state.sessionStates[currentSessionId] ?? getEmptySessionState();
          return {
            sessionStates: {
              ...state.sessionStates,
              [currentSessionId]: {
                ...sessionState,
                messages: []
              }
            }
          };
        });
      },

      // ========== 输入文本操作 ==========
      setInputText: (text) => {
        const { currentSessionId } = get();
        if (!currentSessionId) return;

        set((state) => {
          const sessionState = state.sessionStates[currentSessionId] ?? getEmptySessionState();
          return {
            sessionStates: {
              ...state.sessionStates,
              [currentSessionId]: {
                ...sessionState,
                inputText: text
              }
            }
          };
        });
      },

      getInputText: () => {
        const { currentSessionId, sessionStates } = get();
        if (!currentSessionId) return '';
        return sessionStates[currentSessionId]?.inputText ?? '';
      },

      // ========== 附件操作 ==========
      setAttachments: (files) => {
        const { currentSessionId } = get();
        if (!currentSessionId) return;

        set((state) => {
          const sessionState = state.sessionStates[currentSessionId] ?? getEmptySessionState();
          return {
            sessionStates: {
              ...state.sessionStates,
              [currentSessionId]: {
                ...sessionState,
                attachments: files
              }
            }
          };
        });
      },

      getAttachments: () => {
        const { currentSessionId, sessionStates } = get();
        if (!currentSessionId) return [];
        return sessionStates[currentSessionId]?.attachments ?? [];
      },

      addAttachment: (file) => {
        const { currentSessionId } = get();
        if (!currentSessionId) return;

        set((state) => {
          const sessionState = state.sessionStates[currentSessionId] ?? getEmptySessionState();
          return {
            sessionStates: {
              ...state.sessionStates,
              [currentSessionId]: {
                ...sessionState,
                attachments: [...sessionState.attachments, file].slice(0, 5) // 最多5个附件
              }
            }
          };
        });
      },

      removeAttachment: (index) => {
        const { currentSessionId } = get();
        if (!currentSessionId) return;

        set((state) => {
          const sessionState = state.sessionStates[currentSessionId] ?? getEmptySessionState();
          return {
            sessionStates: {
              ...state.sessionStates,
              [currentSessionId]: {
                ...sessionState,
                attachments: sessionState.attachments.filter((_, i) => i !== index)
              }
            }
          };
        });
      },

      // ========== 滚动位置操作 ==========
      setScrollPosition: (position) => {
        const { currentSessionId } = get();
        if (!currentSessionId) return;

        set((state) => {
          const sessionState = state.sessionStates[currentSessionId] ?? getEmptySessionState();
          return {
            sessionStates: {
              ...state.sessionStates,
              [currentSessionId]: {
                ...sessionState,
                scrollPosition: position
              }
            }
          };
        });
      },

      getScrollPosition: () => {
        const { currentSessionId, sessionStates } = get();
        if (!currentSessionId) return 0;
        return sessionStates[currentSessionId]?.scrollPosition ?? 0;
      },

      // ========== 交互式请求操作 ==========
      addQuestion: (question) => {
        set((state) => ({
          pendingQuestions: [...state.pendingQuestions, question]
        }));
      },

      removeQuestion: (questionId) => {
        set((state) => ({
          pendingQuestions: state.pendingQuestions.filter((q) => q.questionId !== questionId)
        }));
      },

      submitAnswer: (questionId, answer) => {
        window.ccBackend?.submitAnswer(questionId, answer);
        get().removeQuestion(questionId);
      }
    }),
    {
      name: 'ccgui-session-storage',
      partialize: (state) => ({
        // 只持久化会话状态映射，不持久化当前会话ID（由 appStore 管理）
        sessionStates: state.sessionStates
      })
    }
  )
);