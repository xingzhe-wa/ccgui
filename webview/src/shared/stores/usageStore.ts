/**
 * Usage Store - Token用量追踪与预算管理
 *
 * 使用 localStorage 持久化预算设置和使用量
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface UsageState {
  /** 预算设置（每月） */
  monthlyBudget: number;

  /** 当前周期的使用量（Token） */
  currentUsage: number;

  /** 当前周期开始时间 */
  cycleStartTime: number;

  /** 预算警告阈值（百分比） */
  warningThreshold: number;

  /** 是否已显示过警告 */
  hasWarned: boolean;

  // ========== 操作 ==========

  /** 设置预算 */
  setMonthlyBudget: (budget: number) => void;

  /** 添加使用量 */
  addUsage: (tokens: number) => void;

  /** 重置周期 */
  resetCycle: () => void;

  /** 获取使用百分比 */
  getUsagePercent: () => number;

  /** 检查是否需要警告 */
  shouldWarn: () => boolean;

  /** 清除警告状态 */
  clearWarning: () => void;
}

const DEFAULT_BUDGET = 1_000_000; // 默认100万Token/月
const ONE_MONTH_MS = 30 * 24 * 60 * 60 * 1000;

export const useUsageStore = create<UsageState>()(
  persist(
    (set, get) => ({
      monthlyBudget: DEFAULT_BUDGET,
      currentUsage: 0,
      cycleStartTime: Date.now(),
      warningThreshold: 80,
      hasWarned: false,

      setMonthlyBudget: (budget) => set({ monthlyBudget: budget }),

      addUsage: (tokens) => {
        set((state) => ({ currentUsage: state.currentUsage + tokens }));
      },

      resetCycle: () =>
        set({
          currentUsage: 0,
          cycleStartTime: Date.now(),
          hasWarned: false
        }),

      /** 检查是否需要重置周期（每月） */
      checkAndResetCycle: () => {
        const { cycleStartTime } = get();
        const now = Date.now();
        if (now - cycleStartTime > ONE_MONTH_MS) {
          set({
            currentUsage: 0,
            cycleStartTime: now,
            hasWarned: false
          });
        }
      },

      getUsagePercent: () => {
        const { currentUsage, monthlyBudget } = get();
        if (monthlyBudget <= 0) return 0;
        return Math.round((currentUsage / monthlyBudget) * 100);
      },

      shouldWarn: () => {
        const { getUsagePercent, warningThreshold, hasWarned } = get();
        return getUsagePercent() >= warningThreshold && !hasWarned;
      },

      clearWarning: () => set({ hasWarned: false })
    }),
    {
      name: 'ccgui-usage-storage'
    }
  )
);