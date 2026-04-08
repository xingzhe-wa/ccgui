/**
 * Skills 状态 Store
 *
 * 管理 AI 技能模板的状态，包括：
 * - Skills 列表
 * - CRUD 操作
 * - 分类过滤
 * - 导入/导出
 */

import { create } from 'zustand';
import type { Skill, SkillCategory } from '@/shared/types';

interface SkillsStoreState {
  /** 所有 Skills 列表 */
  skills: Skill[];

  /** 加载状态 */
  isLoading: boolean;

  /** 错误信息 */
  error: string | null;

  // ========== 操作 ==========

  /** 设置 Skills 列表 */
  setSkills: (skills: Skill[]) => void;

  /** 创建 Skill */
  createSkill: (skill: Skill) => void;

  /** 更新 Skill */
  updateSkill: (skill: Skill) => void;

  /** 删除 Skill */
  deleteSkill: (skillId: string) => void;

  /** 保存 Skill（创建或更新） */
  saveSkill: (skill: Skill) => void;

  /** 导入 Skills */
  importSkills: (skills: Skill[]) => void;

  /** 导出 Skills */
  exportSkills: () => Skill[];

  /** 按分类过滤 */
  getSkillsByCategory: (category: SkillCategory) => Skill[];

  /** 刷新数据（从后端加载） */
  refreshSkills: () => Promise<void>;
}

export const useSkillsStore = create<SkillsStoreState>((set, get) => ({
  skills: [],
  isLoading: false,
  error: null,

  setSkills: (skills) => set({ skills }),

  createSkill: (skill) =>
    set((state) => ({
      skills: [...state.skills, skill]
    })),

  updateSkill: (skill) =>
    set((state) => ({
      skills: state.skills.map((s) => (s.id === skill.id ? skill : s))
    })),

  deleteSkill: (skillId) =>
    set((state) => ({
      skills: state.skills.filter((s) => s.id !== skillId)
    })),

  saveSkill: (skill) =>
    set((state) => {
      const exists = state.skills.some((s) => s.id === skill.id);
      return {
        skills: exists
          ? state.skills.map((s) => (s.id === skill.id ? skill : s))
          : [...state.skills, skill]
      };
    }),

  importSkills: (imported) =>
    set((state) => ({
      skills: [...state.skills, ...imported]
    })),

  exportSkills: () => get().skills,

  getSkillsByCategory: (category) =>
    get().skills.filter((s) => s.category === category),

  refreshSkills: async () => {
    set({ isLoading: true, error: null });
    try {
      const skills = (await window.ccBackend?.getSkills()) ?? [];
      set({ skills, isLoading: false });
    } catch (error) {
      set({ error: String(error), isLoading: false });
    }
  }
}));