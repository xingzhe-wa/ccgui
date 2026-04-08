/**
 * 会话状态 Store
 *
 * 注意：流式输出状态由独立的 streamingStore 管理（不在此Store中）
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { ChatMessage, InteractiveQuestion } from '@/shared/types';

interface SessionState {
  // ========== 当前会话消息 ==========
  messages: ChatMessage[];

  // ========== 交互式请求状态 ==========
  pendingQuestions: InteractiveQuestion[];

  // ========== 操作 ==========
  // 消息操作
  addMessage: (message: ChatMessage) => void;
  updateMessage: (messageId: string, updates: Partial<ChatMessage>) => void;
  removeMessage: (messageId: string) => void;
  clearMessages: () => void;

  // 交互式请求操作
  addQuestion: (question: InteractiveQuestion) => void;
  removeQuestion: (questionId: string) => void;
  submitAnswer: (questionId: string, answer: any) => void;
}

export const useSessionStore = create<SessionState>()(
  persist(
    (set, get) => ({
      // ========== 初始状态 ==========
      messages: [],
      pendingQuestions: [],

      // ========== 消息操作 ==========
      addMessage: (message) => {
        set((state) => ({ messages: [...state.messages, message] }));
      },

      updateMessage: (messageId, updates) => {
        set((state) => ({
          messages: state.messages.map((msg) =>
            msg.id === messageId ? { ...msg, ...updates } : msg
          )
        }));
      },

      removeMessage: (messageId) => {
        set((state) => ({
          messages: state.messages.filter((msg) => msg.id !== messageId)
        }));
      },

      clearMessages: () => {
        set({ messages: [] });
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
      name: 'ccgui-session-storage'
    }
  )
);
