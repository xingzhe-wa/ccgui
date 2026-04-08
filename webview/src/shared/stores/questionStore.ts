/**
 * 交互式问题状态 Store
 *
 * 管理交互式问题的状态，包括当前问题、答案、超时处理等。
 */

import { create } from 'zustand';
import type { ID } from '@/shared/types';
import type { InteractiveQuestion, QuestionAnswer } from '@/shared/types/interaction';

interface QuestionState {
  /** 当前问题 */
  currentQuestion: InteractiveQuestion | null;

  /** 用户当前答案 */
  currentAnswer: QuestionAnswer | null;

  /** 是否正在提交答案 */
  isSubmitting: boolean;

  /** 问题是否已过期 */
  isExpired: boolean;

  // ========== 操作 ==========

  /**
   * 设置当前问题
   * @param question 问题数据
   */
  setQuestion: (question: InteractiveQuestion) => void;

  /**
   * 设置当前答案
   * @param answer 答案
   */
  setAnswer: (answer: QuestionAnswer) => void;

  /**
   * 提交答案
   */
  submitAnswer: () => Promise<void>;

  /**
   * 跳过问题
   */
  skipQuestion: () => void;

  /**
   * 标记问题已过期
   */
  setExpired: () => void;

  /**
   * 重置状态
   */
  reset: () => void;
}

export const useQuestionStore = create<QuestionState>((set, get) => ({
  // ========== 初始状态 ==========
  currentQuestion: null,
  currentAnswer: null,
  isSubmitting: false,
  isExpired: false,

  // ========== 操作 ==========

  setQuestion: (question) => {
    set({
      currentQuestion: question,
      currentAnswer: null,
      isSubmitting: false,
      isExpired: false
    });
  },

  setAnswer: (answer) => {
    set({ currentAnswer: answer });
  },

  submitAnswer: async () => {
    const { currentQuestion, currentAnswer } = get();
    if (!currentQuestion) return;

    set({ isSubmitting: true });
    try {
      await window.ccBackend?.submitAnswer(currentQuestion.questionId, currentAnswer);
      set({
        currentQuestion: null,
        currentAnswer: null,
        isSubmitting: false
      });
    } catch (error) {
      set({ isSubmitting: false });
      throw error;
    }
  },

  skipQuestion: () => {
    set({
      currentQuestion: null,
      currentAnswer: null,
      isSubmitting: false,
      isExpired: false
    });
  },

  setExpired: () => {
    set({ isExpired: true });
  },

  reset: () => {
    set({
      currentQuestion: null,
      currentAnswer: null,
      isSubmitting: false,
      isExpired: false
    });
  }
}));
