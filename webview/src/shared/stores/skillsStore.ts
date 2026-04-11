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

  /** 从Markdown导入 */
  importFromMarkdown: (markdown: string) => { success: number; failed: number; errors: string[] };

  /** 导出为Markdown */
  exportToMarkdown: () => string;

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

  /**
   * 从Markdown导入Skills
   * 支持Claude Code原生格式：description和instructions字段
   */
  importFromMarkdown: (markdown: string) => {
    const errors: string[] = [];
    let success = 0;
    let failed = 0;

    // 按 --- 分割多个skill
    const skillParts = markdown.split(/^---$/m).filter(s => s.trim());

    for (const part of skillParts) {
      try {
        const lines = part.trim().split('\n');
        let name = '';
        let description = '';
        let instructions = '';
        let category = 'code_generation' as SkillCategory;
        let icon = '⚡';

        for (const line of lines) {
          const trimmed = line.trim();
          if (trimmed.startsWith('description:')) {
            description = trimmed.slice(12).trim();
          } else if (trimmed.startsWith('instructions:')) {
            instructions = trimmed.slice(13).trim();
          } else if (trimmed.startsWith('name:')) {
            name = trimmed.slice(5).trim();
          } else if (trimmed.startsWith('category:')) {
            const cat = trimmed.slice(9).trim();
            if (['code_generation', 'code_review', 'refactoring', 'testing', 'documentation', 'debugging', 'performance'].includes(cat)) {
              category = cat as SkillCategory;
            }
          }
        }

        if (!name || !instructions) {
          failed++;
          errors.push(`Skill缺少name或instructions字段`);
          continue;
        }

        const skill: Skill = {
          id: `skill-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
          name,
          description: description || name,
          icon,
          category,
          prompt: instructions,
          variables: [],
          scope: 'project' as any,
          createdAt: Date.now(),
          updatedAt: Date.now()
        };

        get().skills.push(skill);
        success++;
      } catch (e) {
        failed++;
        errors.push(`解析失败: ${e}`);
      }
    }

    set({ skills: [...get().skills] });
    return { success, failed, errors };
  },

  /**
   * 导出为Markdown格式
   * Claude Code原生格式：description + instructions
   */
  exportToMarkdown: () => {
    return get().skills.map(skill => {
      const lines = [
        '---',
        `name: ${skill.name}`,
        `description: ${skill.description}`,
        `category: ${skill.category}`,
        `instructions:`,
        skill.prompt,
        '---'
      ];
      return lines.join('\n');
    }).join('\n\n');
  },

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